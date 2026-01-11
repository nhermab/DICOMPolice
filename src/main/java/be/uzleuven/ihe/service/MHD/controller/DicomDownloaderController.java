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
 * Controller to serve the DICOM Downloader HTML page.
 * This page allows users to load MADO manifests and download DICOM images
 * referenced in the manifest using WADO-RS endpoints.
 */
@Controller
public class DicomDownloaderController {

    /**
     * Serve the DICOM Downloader page
     */
    @GetMapping("/dicom-downloader")
    public ResponseEntity<String> dicomDownloader() throws IOException {
        Resource resource = new ClassPathResource("static/dicom-downloader.html");
        String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .body(content);
    }
}

