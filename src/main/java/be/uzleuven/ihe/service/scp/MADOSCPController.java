package be.uzleuven.ihe.service.scp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for managing the MHD-backed MADO SCP server.
 */
@RestController
@RequestMapping("/api/scp")
public class MADOSCPController {

    private final MADOSCP scpServer;
    private final MADOSCPConfiguration config;
    private final DicomCache dicomCache;

    @Autowired
    public MADOSCPController(MADOSCP scpServer, MADOSCPConfiguration config, DicomCache dicomCache) {
        this.scpServer = scpServer;
        this.config = config;
        this.dicomCache = dicomCache;
    }

    /**
     * Get the status of the SCP server.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("running", scpServer.isRunning());
        status.put("aeTitle", config.getAeTitle());
        status.put("port", config.getPort());
        status.put("mhdEndpoint", config.getMhdFhirBaseUrl());
        status.put("wadoRsEndpoint", config.getWadoRsBaseUrl());
        status.put("cacheSize", scpServer.getCacheSize());
        return ResponseEntity.ok(status);
    }

    /**
     * Start the SCP server.
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> start() {
        Map<String, Object> response = new HashMap<>();
        try {
            scpServer.start();
            response.put("success", true);
            response.put("message", "MHD-backed MADO SCP started on port " + config.getPort());
            response.put("mhdEndpoint", config.getMhdFhirBaseUrl());
            response.put("wadoRsEndpoint", config.getWadoRsBaseUrl());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Stop the SCP server.
     */
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stop() {
        Map<String, Object> response = new HashMap<>();
        scpServer.stop();
        response.put("success", true);
        response.put("message", "MADO SCP stopped");
        return ResponseEntity.ok(response);
    }

    /**
     * Clear the metadata cache.
     */
    @PostMapping("/cache/clear")
    public ResponseEntity<Map<String, Object>> clearCache() {
        Map<String, Object> response = new HashMap<>();
        scpServer.clearCache();
        response.put("success", true);
        response.put("message", "Metadata cache cleared");
        response.put("cacheSize", scpServer.getCacheSize());
        return ResponseEntity.ok(response);
    }

    /**
     * Get cache statistics.
     */
    @GetMapping("/cache/stats")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("metadataCacheSize", scpServer.getCacheSize());

        if (dicomCache != null) {
            DicomCache.CacheStats dicomStats = dicomCache.getStats();
            Map<String, Object> dicomCacheStats = new HashMap<>();
            dicomCacheStats.put("entries", dicomStats.entries);
            dicomCacheStats.put("currentSizeMB", dicomStats.currentSizeBytes / (1024 * 1024));
            dicomCacheStats.put("maxSizeMB", dicomStats.maxSizeBytes / (1024 * 1024));
            dicomCacheStats.put("hits", dicomStats.hits);
            dicomCacheStats.put("misses", dicomStats.misses);
            dicomCacheStats.put("evictions", dicomStats.evictions);
            dicomCacheStats.put("hitRate", String.format("%.2f%%", dicomStats.getHitRate() * 100));
            stats.put("dicomInstanceCache", dicomCacheStats);
        }

        return ResponseEntity.ok(stats);
    }

    /**
     * Clear DICOM instance cache.
     */
    @PostMapping("/cache/dicom/clear")
    public ResponseEntity<Map<String, Object>> clearDicomCache() {
        Map<String, Object> response = new HashMap<>();
        if (dicomCache != null) {
            dicomCache.clear();
            response.put("success", true);
            response.put("message", "DICOM instance cache cleared");
        } else {
            response.put("success", false);
            response.put("message", "DICOM cache not available");
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Get current configuration.
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("aeTitle", config.getAeTitle());
        configMap.put("port", config.getPort());
        configMap.put("autoStart", config.isAutoStart());
        configMap.put("maxPduLength", config.getMaxPduLength());
        configMap.put("connectionTimeout", config.getConnectionTimeout());
        configMap.put("associationTimeout", config.getAssociationTimeout());
        configMap.put("maxAssociations", config.getMaxAssociations());
        configMap.put("mhdFhirBaseUrl", config.getMhdFhirBaseUrl());
        configMap.put("wadoRsBaseUrl", config.getWadoRsBaseUrl());
        configMap.put("maxParallelDownloads", config.getMaxParallelDownloads());
        configMap.put("maxParallelStores", config.getMaxParallelStores());
        return ResponseEntity.ok(configMap);
    }
}
