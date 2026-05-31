package be.uzleuven.ihe.service.ai;

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
 * Controller to serve the "Smart Clinical Context Prefetcher &amp; Sorter" page.
 *
 * <p>The page queries the MHD {@code DocumentReference} registry for a patient's
 * historical imaging studies, hands the slimmed-down metadata to a local Ollama
 * (Qwen) model for clinical relevance ranking, then surfaces the studies sorted
 * by Tier with a one-click MADO manifest handoff.</p>
 */
@Controller
public class ClinicalPrefetcherController {

    @GetMapping("/clinical-prefetcher")
    public ResponseEntity<String> clinicalPrefetcher() throws IOException {
        Resource resource = new ClassPathResource("static/clinical-prefetcher.html");
        String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .body(content);
    }
}

