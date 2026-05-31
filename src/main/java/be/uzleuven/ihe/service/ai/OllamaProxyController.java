package be.uzleuven.ihe.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Proxy controller that bridges the browser UI to a <b>local Ollama instance</b>
 * (default {@code http://localhost:11434}) running a Qwen-2.5-Instruct model.
 *
 * <p>It powers the "Smart Clinical Context Prefetcher &amp; Sorter": given a
 * patient's <em>Reason for Visit</em> and a slimmed-down list of historical
 * imaging study metadata (derived from MHD {@code DocumentReference} resources),
 * it asks the LLM to rank each prior study into a clinical relevance Tier
 * (1 = highly relevant, 2 = moderately relevant, 3 = low/no relevance) and
 * returns a strict JSON response.</p>
 *
 * <p>Running the call server-side avoids browser CORS issues with the Ollama
 * daemon and keeps the model endpoint internal.</p>
 */
@RestController
@RequestMapping("/api/ollama")
public class OllamaProxyController {

    private static final Logger LOGGER = LoggerFactory.getLogger(OllamaProxyController.class);

    private static final String SYSTEM_PROMPT = """
            You are an expert clinical radiology triage assistant. Your job is to analyze a list of \
            historical patient imaging study metadata and determine how relevant each historical study \
            is to the patient's current "Reason for Visit".

            Categorize relevance into three Tiers:
            - Tier 1: Highly Relevant (Direct prior comparison or highly critical background context).
            - Tier 2: Moderately Relevant (Same body region, adjacent structures, or systemic conditions \
            that could impact the diagnosis).
            - Tier 3: Low/No Relevance (Unrelated anatomy, unrelated clinical indications).

            Response constraints (CRITICAL FOR speed/throughput, keep responses as short as possible):
            1. Output your response strictly as a JSON object containing an array named "ranked_studies".
            2. For each element, "clinical_reasoning" MUST be extremely concise (MAXIMUM 3 to 5 words, e.g. \
            "unrelated anatomy", "direct prior comparison").
            3. Do not include any markdown formatting (like ```json), no chat/conversational filler, \
            no <think> tags, and no chain of thought. If you are a reasoning model like DeepSeek-R1, skip thinking/reasoning and start your response directly with the JSON object.
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.default-model:qwen2.5:7b-instruct}")
    private String defaultModel;

    @Value("${ollama.timeout-seconds:120}")
    private long timeoutSeconds;

    /**
     * Report whether the local Ollama instance is reachable and which models are installed.
     * GET /api/ollama/status
     */
    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> status() {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("baseUrl", ollamaBaseUrl);
        result.put("defaultModel", defaultModel);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(ollamaBaseUrl) + "/api/tags"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode body = objectMapper.readTree(response.body());
                ArrayNode models = objectMapper.createArrayNode();
                JsonNode modelsNode = body.path("models");
                if (modelsNode.isArray()) {
                    for (JsonNode m : modelsNode) {
                        String name = m.path("name").asText();
                        if (!name.isEmpty()) {
                            models.add(name);
                        }
                    }
                }
                result.put("available", true);
                result.set("models", models);
                return ResponseEntity.ok(result);
            }
            result.put("available", false);
            result.put("error", "Ollama responded with HTTP " + response.statusCode());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LOGGER.warn("Ollama status check failed: {}", e.getMessage());
            result.put("available", false);
            result.put("error", "Cannot reach Ollama at " + ollamaBaseUrl + " (" + e.getMessage() + ")");
            return ResponseEntity.ok(result);
        }
    }

    /**
     * Rank a list of historical imaging studies by clinical relevance to the reason for visit.
     * POST /api/ollama/rank
     * Body: { "reasonForVisit": "...", "model": "optional override", "studies": [ {normalized metadata} ] }
     */
    @PostMapping(value = "/rank", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> rank(@RequestBody JsonNode requestBody) {
        String reasonForVisit = requestBody.path("reasonForVisit").asText().trim();
        JsonNode studies = requestBody.path("studies");
        String model = requestBody.path("model").asText().trim();
        if (model.isEmpty()) {
            model = defaultModel;
        }

        LOGGER.info("Starting clinical prefetch ranking task. Model: '{}', Reason for Visit: '{}', Number of studies: {}",
                model, reasonForVisit, (studies.isArray() ? studies.size() : "not an array"));

        if (reasonForVisit.isEmpty()) {
            LOGGER.warn("Ranking aborted: reasonForVisit is empty/missing.");
            return errorResponse(HttpStatus.BAD_REQUEST, "reasonForVisit is required");
        }
        if (!studies.isArray() || studies.isEmpty()) {
            LOGGER.warn("Ranking aborted: studies is not a non-empty array.");
            return errorResponse(HttpStatus.BAD_REQUEST, "studies must be a non-empty array");
        }

        String userPrompt;
        try {
            String metadataJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(studies);
            userPrompt = "Reason for Visit: " + reasonForVisit + "\n\n"
                    + "Historical Study Metadata List:\n" + metadataJson + "\n\n"
                    + "Analyze the items above and output the JSON object with the \"ranked_studies\" array.\n"
                    + "Remember:\n"
                    + "- Every ranked study must have \"id\", \"tier\" (1, 2 or 3), and \"clinical_reasoning\" (MAX 3 to 5 words).\n"
                    + "- DO NOT think, DO NOT output any <think> tags or reasoning steps. Output ONLY raw JSON.";
            LOGGER.debug("Serialized user prompt generated successfully. Length: {} chars.", userPrompt.length());
        } catch (Exception e) {
            LOGGER.error("Failed to serialise study metadata to JSON string.", e);
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialise study metadata: " + e.getMessage());
        }

        // Build the JSON schema for structured output to ensure reliability
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);

        ObjectNode props = schema.putObject("properties");

        ObjectNode rankedStudiesSchema = props.putObject("ranked_studies");
        rankedStudiesSchema.put("type", "array");

        ObjectNode itemSchema = rankedStudiesSchema.putObject("items");
        itemSchema.put("type", "object");
        itemSchema.put("additionalProperties", false);

        ObjectNode itemProps = itemSchema.putObject("properties");

        ObjectNode idSchema = itemProps.putObject("id");
        idSchema.put("type", "string");

        ObjectNode tierSchema = itemProps.putObject("tier");
        tierSchema.put("type", "integer");
        tierSchema.putArray("enum").add(1).add(2).add(3);

        ObjectNode reasoningSchema = itemProps.putObject("clinical_reasoning");
        reasoningSchema.put("type", "string");
        reasoningSchema.put("maxLength", 80);

        itemSchema.putArray("required")
                .add("id")
                .add("tier")
                .add("clinical_reasoning");

        schema.putArray("required").add("ranked_studies");

        // Build the Ollama /api/chat request body with schema-based format and think disabled.
        ObjectNode chatRequest = objectMapper.createObjectNode();
        chatRequest.put("model", model);
        chatRequest.put("stream", false);
        chatRequest.put("think", false);
        chatRequest.set("format", schema);

        // Dynamically size output budget based on studies size
        int outputBudget = Math.min(8192, Math.max(1024, 128 + studies.size() * 80));
        LOGGER.info("Calculated dynamic output budget for Ollama (num_predict): {} based on {} studies.", outputBudget, studies.size());

        ObjectNode options = chatRequest.putObject("options");
        options.put("temperature", 0.0);
        options.put("num_predict", outputBudget);

        ArrayNode messages = chatRequest.putArray("messages");
        ObjectNode systemMsg = messages.addObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", SYSTEM_PROMPT);
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);

        try {
            String requestUrl = trimTrailingSlash(ollamaBaseUrl) + "/api/chat";
            LOGGER.info("Sending request to Ollama chat endpoint: {} with model: {}", requestUrl, model);
            LOGGER.debug("Ollama request payload: {}", chatRequest);

            long startTime = System.currentTimeMillis();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(chatRequest)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long duration = System.currentTimeMillis() - startTime;
            LOGGER.info("Ollama responded in {} ms with HTTP status code: {}", duration, response.statusCode());

            if (response.statusCode() != 200) {
                LOGGER.warn("Ollama /api/chat returned HTTP error {}: {}", response.statusCode(), response.body());
                return errorResponse(HttpStatus.BAD_GATEWAY,
                        "Ollama returned HTTP " + response.statusCode() + ": " + response.body());
            }

            JsonNode ollamaResponse = objectMapper.readTree(response.body());
            String content = ollamaResponse.path("message").path("content").asText();
            LOGGER.info("Model response received. Content character length: {}", (content != null ? content.length() : 0));
            LOGGER.debug("Model raw content: {}", content);

            JsonNode parsed = parseLenientJson(content);
            if (parsed == null) {
                LOGGER.info("Structured JSON output failed. Retrying with same schema and larger num_predict...");
                ((ObjectNode) chatRequest.get("options")).put("num_predict", 2048);

                LOGGER.info("Sending retry request to Ollama chat endpoint: {} (num_predict=2048)", requestUrl);
                long retryStartTime = System.currentTimeMillis();
                HttpRequest retryRequest = HttpRequest.newBuilder()
                        .uri(URI.create(requestUrl))
                        .timeout(Duration.ofSeconds(timeoutSeconds))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(chatRequest)))
                        .build();

                HttpResponse<String> retryResponse = httpClient.send(retryRequest, HttpResponse.BodyHandlers.ofString());
                long retryDuration = System.currentTimeMillis() - retryStartTime;
                LOGGER.info("Ollama retry responded in {} ms with HTTP status code: {}", retryDuration, retryResponse.statusCode());

                if (retryResponse.statusCode() == 200) {
                    JsonNode retryOllamaResponse = objectMapper.readTree(retryResponse.body());
                    content = retryOllamaResponse.path("message").path("content").asText();
                    LOGGER.info("Retry response received. Content character length: {}", (content != null ? content.length() : 0));
                    LOGGER.debug("Retry content: {}", content);
                    parsed = parseLenientJson(content);
                } else {
                    LOGGER.warn("Ollama /api/chat retry returned HTTP error {}: {}", retryResponse.statusCode(), retryResponse.body());
                }
            }

            if (parsed == null) {
                LOGGER.error("Model did not return valid JSON even after retry. Raw response content: {}", content);
                return errorResponse(HttpStatus.BAD_GATEWAY,
                        "Model did not return valid JSON. Raw output: " + (content != null ? content : ""));
            }

            ObjectNode result = objectMapper.createObjectNode();
            result.put("model", model);

            // Stricter schema validation of the parsed content
            LOGGER.info("Performing post-parsing validation on parsed model response...");
            JsonNode ranked = parsed.path("ranked_studies");

            if (!ranked.isArray()) {
                LOGGER.error("Validation failed: Model returned JSON but it is missing the 'ranked_studies' array. parsed content: {}", parsed);
                return errorResponse(HttpStatus.BAD_GATEWAY,
                        "Model returned JSON but missing ranked_studies array. Raw output: " + content);
            }

            for (JsonNode item : ranked) {
                if (!item.hasNonNull("id")
                        || !item.hasNonNull("tier")
                        || !item.hasNonNull("clinical_reasoning")) {
                    LOGGER.error("Validation failed: 'ranked_studies' item is missing critical fields. Item: {}", item);
                    return errorResponse(HttpStatus.BAD_GATEWAY,
                            "Model returned malformed ranked_studies item. Raw output: " + content);
                }

                int tier = item.path("tier").asInt(-1);
                if (tier < 1 || tier > 3) {
                    LOGGER.error("Validation failed: 'ranked_studies' item has an invalid tier hierarchy: {}. Item: {}", tier, item);
                    return errorResponse(HttpStatus.BAD_GATEWAY,
                            "Model returned invalid tier. Raw output: " + content);
                }
            }

            LOGGER.info("Post-parsing validation passed successfully on all {} ranked studies.", ranked.size());
            result.set("ranked_studies", ranked);
            return ResponseEntity.ok(result);

        } catch (java.net.http.HttpTimeoutException e) {
            LOGGER.error("Ollama HTTP request timed out after {} seconds.", timeoutSeconds, e);
            return errorResponse(HttpStatus.GATEWAY_TIMEOUT,
                    "Ollama timed out after " + timeoutSeconds + "s. Try a smaller model or fewer studies.");
        } catch (Exception e) {
            LOGGER.error("Ollama ranking task encountered an exception.", e);
            return errorResponse(HttpStatus.BAD_GATEWAY,
                    "Cannot reach Ollama at " + ollamaBaseUrl + " (" + e.getMessage() + ")");
        }
    }

    /**
     * Attempt to parse model output as JSON, tolerating a stray markdown code fence
     * or leading/trailing prose around the JSON payload.
     */
    private JsonNode parseLenientJson(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String trimmed = content.trim();

        // Strip <think>...</think> blocks from reasoning models to prevent JSON parse errors
        String temp = trimmed;
        while (temp.contains("<think>")) {
            int thinkStart = temp.indexOf("<think>");
            int thinkEnd = temp.indexOf("</think>");
            if (thinkEnd > thinkStart) {
                temp = temp.substring(0, thinkStart) + temp.substring(thinkEnd + 8);
            } else {
                temp = temp.substring(0, thinkStart);
                break;
            }
        }
        trimmed = temp.trim();

        try {
            return objectMapper.readTree(trimmed);
        } catch (Exception ignored) {
            // fall through to substring extraction
        }
        int objStart = trimmed.indexOf('{');
        int arrStart = trimmed.indexOf('[');
        int start = (objStart < 0) ? arrStart : (arrStart < 0 ? objStart : Math.min(objStart, arrStart));
        char open = (start >= 0) ? trimmed.charAt(start) : '{';
        char close = (open == '[') ? ']' : '}';
        int end = trimmed.lastIndexOf(close);
        if (start >= 0 && end > start) {
            try {
                return objectMapper.readTree(trimmed.substring(start, end + 1));
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private ResponseEntity<JsonNode> errorResponse(HttpStatus statusCode, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        return ResponseEntity.status(statusCode).body(objectMapper.valueToTree(body));
    }

    private static String trimTrailingSlash(String url) {
        return (url != null && url.endsWith("/")) ? url.substring(0, url.length() - 1) : url;
    }
}

