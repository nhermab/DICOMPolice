package be.uzleuven.ihe.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Controller to serve the Lab Observations Explorer HTML page.
 *
 * <p>The Lab Observations Explorer searches the configured MHD FHIR endpoint for
 * {@code Observation} resources by LOINC code and renders both the raw JSON and a
 * laboratory-oriented view (value, unit, interpretation flag, reference-range
 * gauge, specimen, notes). The endpoint and OAuth2 settings are shared with the
 * MHD MADO Viewer (same browser {@code madoViewerConfig} storage key).</p>
 */
@Controller
public class LabObservationsController {

    /**
     * Serve the Lab Observations page.
     */
    @GetMapping("/lab-observations")
    public ResponseEntity<String> labObservations() throws IOException {
        Resource resource = new ClassPathResource("static/lab-observations.html");
        String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .body(content);
    }
}

