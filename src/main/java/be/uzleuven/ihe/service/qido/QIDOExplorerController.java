package be.uzleuven.ihe.service.qido;

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
 * Controller to serve the QIDO-RS MADO Explorer HTML page.
 */
@Controller
public class QIDOExplorerController {

    /**
     * Serve the QIDO-RS Explorer page
     */
    @GetMapping("/qido-explorer")
    public ResponseEntity<String> qidoExplorer() throws IOException {
        Resource resource = new ClassPathResource("static/qido-explorer.html");
        String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .body(content);
    }
}

