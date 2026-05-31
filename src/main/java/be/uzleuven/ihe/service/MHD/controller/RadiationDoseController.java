package be.uzleuven.ihe.service.MHD.controller;

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
 * Controller to serve the Radiation Dose Passport HTML page.
 * This client-side app estimates a patient's cumulative radiation exposure
 * context from DICOM Radiation Dose Structured Reports (RDSR / RRDSR / P-RDSR)
 * retrieved via QIDO-RS + WADO-RS metadata.
 */
@Controller
public class RadiationDoseController {

    /**
     * Serve the Radiation Dose page
     */
    @GetMapping("/radiation-dose")
    public ResponseEntity<String> radiationDose() throws IOException {
        Resource resource = new ClassPathResource("static/radiation-dose.html");
        String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .body(content);
    }
}

