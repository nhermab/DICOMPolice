package be.uzleuven.ihe.service.MHD;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * Server-side reverse proxy for the MHD MADO Viewer's authenticated FHIR connection.
 *
 * <p>Secured FHIR endpoints (e.g. Abrumet+) and their Keycloak token endpoints do not
 * send CORS headers, so the browser cannot call them directly. This controller performs
 * those calls server-side (no CORS) on behalf of the web viewer:</p>
 *
 * <ul>
 *   <li>{@code POST /api/mhd-proxy/token} &mdash; runs the OAuth2 {@code client_credentials}
 *       grant against the supplied token URL and returns the raw token JSON.</li>
 *   <li>{@code GET /api/mhd-proxy/fhir?target=<absolute-url>} &mdash; forwards a GET request
 *       to the target FHIR URL, passing through the {@code Authorization}, {@code X-Patient},
 *       {@code X-Provider} and {@code Accept} headers, and returns the response verbatim.</li>
 * </ul>
 *
 * <p>An optional allow-list ({@code mhd.proxy.allowed-hosts}) restricts which remote hosts
 * may be contacted to mitigate SSRF. When empty (default), any http(s) host is permitted.</p>
 */
@RestController
@RequestMapping("/api/mhd-proxy")
public class MhdAuthProxyController {

    private static final Logger LOG = LoggerFactory.getLogger(MhdAuthProxyController.class);

    /** Headers that are forwarded verbatim to the upstream FHIR server. */
    private static final List<String> FORWARDED_HEADERS =
            Arrays.asList("authorization", "x-patient", "x-provider", "accept");

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** Comma-separated allow-list of upstream hostnames. Empty = allow any http(s) host. */
    @Value("${mhd.proxy.allowed-hosts:}")
    private String allowedHosts;

    @Value("${mhd.proxy.timeout-seconds:30}")
    private long timeoutSeconds;

    /**
     * Exchange client credentials for an OAuth2 access token, server-side.
     * POST /api/mhd-proxy/token
     * Body: { "tokenUrl", "clientId", "clientSecret", "scope", "audience", "resource" }
     */
    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> token(@RequestBody JsonNode body) {
        String tokenUrl = textValue(body, "tokenUrl").trim();
        if (tokenUrl.isEmpty()) {
            return errorResponse(HttpStatus.BAD_REQUEST, "tokenUrl is required");
        }
        if (!isAllowed(tokenUrl)) {
            LOG.warn("Token proxy rejected disallowed target: {}", tokenUrl);
            return errorResponse(HttpStatus.FORBIDDEN, "Target host is not allowed");
        }

        StringJoiner form = new StringJoiner("&");
        form.add("grant_type=client_credentials");
        appendIfPresent(form, "client_id", textValue(body, "clientId"));
        appendIfPresent(form, "client_secret", textValue(body, "clientSecret"));
        appendIfPresent(form, "scope", textValue(body, "scope"));
        appendIfPresent(form, "audience", textValue(body, "audience"));
        appendIfPresent(form, "resource", textValue(body, "resource"));

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(tokenUrl))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            LOG.info("Token proxy -> {} returned HTTP {}", tokenUrl, response.statusCode());

            return ResponseEntity.status(response.statusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.body());
        } catch (Exception e) {
            LOG.error("Token proxy request to {} failed: {}", tokenUrl, e.getMessage(), e);
            return errorResponse(HttpStatus.BAD_GATEWAY, "Token request failed: " + e.getMessage());
        }
    }

    /**
     * Forward a GET request to a secured FHIR endpoint, passing through auth headers.
     * GET /api/mhd-proxy/fhir?target=<absolute-url>
     */
    @GetMapping("/fhir")
    public ResponseEntity<byte[]> fhir(@RequestParam("target") String target,
                                       @RequestHeader Map<String, String> headers) {
        if (target == null || target.isBlank()) {
            return ResponseEntity.badRequest().body("target query parameter is required".getBytes(StandardCharsets.UTF_8));
        }
        if (!isAllowed(target)) {
            LOG.warn("FHIR proxy rejected disallowed target: {}", target);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Target host is not allowed".getBytes(StandardCharsets.UTF_8));
        }

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(target))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .GET();

            // Forward only the relevant auth/content headers.
            if (headers != null) {
                headers.forEach((name, value) -> {
                    if (name != null && FORWARDED_HEADERS.contains(name.toLowerCase()) && value != null) {
                        builder.header(name, value);
                    }
                });
            }
            if (headers == null || headers.keySet().stream().noneMatch("accept"::equalsIgnoreCase)) {
                builder.header("Accept", "application/fhir+json");
            }

            HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            LOG.info("FHIR proxy -> {} returned HTTP {} ({} bytes)",
                    target, response.statusCode(),
                    response.body() != null ? response.body().length : 0);

            String contentType = response.headers().firstValue("content-type")
                    .orElse(MediaType.APPLICATION_JSON_VALUE);

            ResponseEntity.BodyBuilder respBuilder = ResponseEntity.status(response.statusCode());
            try {
                respBuilder.contentType(MediaType.parseMediaType(contentType));
            } catch (Exception ignored) {
                respBuilder.contentType(MediaType.APPLICATION_OCTET_STREAM);
            }
            return respBuilder.body(response.body());
        } catch (Exception e) {
            LOG.error("FHIR proxy request to {} failed: {}", target, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(("Proxy error: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }

    // ---------------------------------------------------------------------

    private void appendIfPresent(StringJoiner form, String key, String value) {
        if (value != null && !value.isEmpty()) {
            form.add(key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
        }
    }

    /** Read a string field from a JSON node, returning "" when missing or null. */
    private static String textValue(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isTextual() ? v.textValue() : (v.isMissingNode() || v.isNull() ? "" : v.asText());
    }

    /**
     * Validate that the URL is http(s) and, when an allow-list is configured,
     * that the host is on it.
     */
    private boolean isAllowed(String url) {
        final URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception e) {
            return false;
        }
        String scheme = uri.getScheme();
        if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            return false;
        }
        if (uri.getHost() == null) {
            return false;
        }
        if (allowedHosts == null || allowedHosts.isBlank()) {
            return true; // no allow-list configured: permit any http(s) host
        }
        List<String> hosts = Arrays.stream(allowedHosts.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toList());
        return hosts.contains(uri.getHost().toLowerCase());
    }

    private ResponseEntity<String> errorResponse(HttpStatus status, String message) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("error", message);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body.toString());
    }
}

