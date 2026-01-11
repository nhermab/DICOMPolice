package be.uzleuven.ihe.service.qido;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

/**
 * WADO-RS Proxy Controller.
 *
 * Proxies WADO-RS requests to remote servers based on Study Instance UID.
 * Only active when qido.rs.wado-proxy-enabled=true.
 *
 * Handles all WADO-RS endpoints:
 * - GET /dicomweb/studies/{studyUID}
 * - GET /dicomweb/studies/{studyUID}/series/{seriesUID}
 * - GET /dicomweb/studies/{studyUID}/series/{seriesUID}/instances/{instanceUID}
 * - GET /dicomweb/studies/{studyUID}/series/{seriesUID}/instances/{instanceUID}/frames/{frameNumber}
 */
@RestController
@RequestMapping("/dicomweb")
public class WadoRsProxyController {

    private static final Logger LOG = LoggerFactory.getLogger(WadoRsProxyController.class);

    private final WadoRsProxyRegistry registry;
    private final QIDOConfiguration configuration;
    private final RestTemplate restTemplate;

    @Autowired
    public WadoRsProxyController(WadoRsProxyRegistry registry, QIDOConfiguration configuration) {
        this.registry = registry;
        this.configuration = configuration;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Proxy WADO-RS requests for entire studies.
     * GET /dicomweb/studies/{studyUID}
     */
    @GetMapping("/studies/{studyUID}")
    public ResponseEntity<byte[]> proxyStudyRequest(
            @PathVariable("studyUID") String studyUID,
            @RequestParam MultiValueMap<String, String> params,
            @RequestHeader Map<String, String> headers) throws IOException {

        return proxyRequest(studyUID, "/studies/" + studyUID, params, headers);
    }

    /**
     * Proxy WADO-RS requests for series.
     * GET /dicomweb/studies/{studyUID}/series/{seriesUID}
     */
    @GetMapping("/studies/{studyUID}/series/{seriesUID}")
    public ResponseEntity<byte[]> proxySeriesRequest(
            @PathVariable("studyUID") String studyUID,
            @PathVariable("seriesUID") String seriesUID,
            @RequestParam MultiValueMap<String, String> params,
            @RequestHeader Map<String, String> headers) throws IOException {

        String wadoPath = "/studies/" + studyUID + "/series/" + seriesUID;
        return proxyRequest(studyUID, wadoPath, params, headers);
    }

    /**
     * Proxy WADO-RS requests for instances.
     * GET /dicomweb/studies/{studyUID}/series/{seriesUID}/instances/{instanceUID}
     */
    @GetMapping("/studies/{studyUID}/series/{seriesUID}/instances/{instanceUID}")
    public ResponseEntity<byte[]> proxyInstanceRequest(
            @PathVariable("studyUID") String studyUID,
            @PathVariable("seriesUID") String seriesUID,
            @PathVariable("instanceUID") String instanceUID,
            @RequestParam MultiValueMap<String, String> params,
            @RequestHeader Map<String, String> headers) throws IOException {

        String wadoPath = "/studies/" + studyUID + "/series/" + seriesUID + "/instances/" + instanceUID;
        return proxyRequest(studyUID, wadoPath, params, headers);
    }

    /**
     * Proxy WADO-RS requests for frames.
     * GET /dicomweb/studies/{studyUID}/series/{seriesUID}/instances/{instanceUID}/frames/{frameNumber}
     */
    @GetMapping("/studies/{studyUID}/series/{seriesUID}/instances/{instanceUID}/frames/{frameNumber}")
    public ResponseEntity<byte[]> proxyFrameRequest(
            @PathVariable("studyUID") String studyUID,
            @PathVariable("seriesUID") String seriesUID,
            @PathVariable("instanceUID") String instanceUID,
            @PathVariable("frameNumber") String frameNumber,
            @RequestParam MultiValueMap<String, String> params,
            @RequestHeader Map<String, String> headers) throws IOException {

        String wadoPath = "/studies/" + studyUID + "/series/" + seriesUID +
                          "/instances/" + instanceUID + "/frames/" + frameNumber;
        return proxyRequest(studyUID, wadoPath, params, headers);
    }

    /**
     * Common proxy logic for all WADO-RS requests.
     */
    private ResponseEntity<byte[]> proxyRequest(
            String studyUID,
            String wadoPath,
            MultiValueMap<String, String> params,
            Map<String, String> requestHeaders) throws IOException {

        // Check if proxy mode is enabled
        if (!configuration.isWadoProxyEnabled()) {
            LOG.warn("WADO-RS proxy request received but proxy mode is disabled");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        LOG.info("WADO-RS proxy request: {} for study {}", wadoPath, studyUID);

        // Get remote URL from registry
        String remoteUrl = registry.buildRemoteUrl(studyUID, wadoPath);
        if (remoteUrl == null) {
            LOG.error("Study {} not registered in WADO-RS proxy registry", studyUID);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(("Study not found in proxy registry: " + studyUID).getBytes());
        }

        // Add query parameters if present
        if (params != null && !params.isEmpty()) {
            StringBuilder queryString = new StringBuilder();
            params.forEach((key, values) -> {
                for (String value : values) {
                    if (queryString.length() > 0) {
                        queryString.append("&");
                    }
                    queryString.append(key).append("=").append(value);
                }
            });
            if (queryString.length() > 0) {
                remoteUrl += "?" + queryString;
            }
        }

        LOG.info("Proxying to: {}", remoteUrl);

        try {
            // Build request headers
            HttpHeaders headers = new HttpHeaders();

            // Copy relevant headers from original request
            if (requestHeaders != null) {
                requestHeaders.forEach((name, value) -> {
                    // Skip host and other connection-specific headers
                    if (!name.equalsIgnoreCase("host") &&
                        !name.equalsIgnoreCase("connection") &&
                        !name.equalsIgnoreCase("content-length")) {
                        headers.add(name, value);
                    }
                });
            }

            // Ensure Accept header for DICOM
            if (!headers.containsKey(HttpHeaders.ACCEPT)) {
                headers.setAccept(java.util.Arrays.asList(
                    MediaType.parseMediaType("multipart/related;type=\"application/dicom\""),
                    MediaType.parseMediaType("application/dicom"),
                    MediaType.APPLICATION_OCTET_STREAM
                ));
            }

            // Make the proxied request
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    URI.create(remoteUrl),
                    HttpMethod.GET,
                    entity,
                    byte[].class
            );

            LOG.info("Proxied response: {} bytes, status: {}",
                    response.getBody() != null ? response.getBody().length : 0,
                    response.getStatusCode());

            // Forward the response
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.putAll(response.getHeaders());

            return ResponseEntity.status(response.getStatusCode())
                    .headers(responseHeaders)
                    .body(response.getBody());

        } catch (Exception e) {
            LOG.error("Error proxying WADO-RS request to {}: {}", remoteUrl, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(("Proxy error: " + e.getMessage()).getBytes());
        }
    }
}

