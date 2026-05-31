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
 * Controller to serve the Cross-Community Imaging Timeline HTML page.
 *
 * <p>The Imaging Timeline merges study metadata from multiple communities
 * (hospitals, regions, networks) into a single longitudinal chronology and
 * automatically flags "missing priors" while a current study is being read.</p>
 */
@Controller
public class ImagingTimelineController {

    /**
     * Serve the Imaging Timeline page
     */
    @GetMapping("/imaging-timeline")
    public ResponseEntity<String> imagingTimeline() throws IOException {
        Resource resource = new ClassPathResource("static/imaging-timeline.html");
        String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .body(content);
    }
}

