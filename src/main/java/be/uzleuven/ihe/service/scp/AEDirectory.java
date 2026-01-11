package be.uzleuven.ihe.service.scp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * AE (Application Entity) Directory for DICOM networking.
 * Maps AE Titles to their host/port information for C-MOVE operations.
 */
@Component
@ConfigurationProperties(prefix = "dicom.ae-directory")
public class AEDirectory {

    /**
     * Map of AE Title to AE configuration.
     * Format: "AETITLE" -> AEInfo{host, port}
     */
    private Map<String, AEInfo> entries = new HashMap<>();

    /**
     * Default host for unknown AE titles (fallback).
     */
    private String defaultHost = "localhost";

    /**
     * Default port for unknown AE titles (fallback).
     */
    private int defaultPort = 104;

    @PostConstruct
    public void init() {
        // Add some default entries if none configured
        if (entries.isEmpty()) {
            // Common PACS systems
            addEntry("ORTHANC", "localhost", 4242);
            addEntry("DCM4CHEE", "localhost", 11112);
            addEntry("HOROS", "localhost", 11112);
            addEntry("OSIRIX", "localhost", 11112);
        }
    }

    public Map<String, AEInfo> getEntries() {
        return entries;
    }

    public void setEntries(Map<String, AEInfo> entries) {
        this.entries = entries;
    }

    public String getDefaultHost() {
        return defaultHost;
    }

    public void setDefaultHost(String defaultHost) {
        this.defaultHost = defaultHost;
    }

    public int getDefaultPort() {
        return defaultPort;
    }

    public void setDefaultPort(int defaultPort) {
        this.defaultPort = defaultPort;
    }

    /**
     * Add or update an AE entry.
     */
    public void addEntry(String aeTitle, String host, int port) {
        AEInfo info = new AEInfo();
        info.setHost(host);
        info.setPort(port);
        entries.put(aeTitle.toUpperCase(), info);
    }

    /**
     * Lookup an AE by title.
     * @param aeTitle The AE Title to look up
     * @return AEInfo if found, or a default entry if not found
     */
    public AEInfo lookup(String aeTitle) {
        AEInfo info = entries.get(aeTitle.toUpperCase());
        if (info != null) {
            return info;
        }

        // Return default
        AEInfo defaultInfo = new AEInfo();
        defaultInfo.setHost(defaultHost);
        defaultInfo.setPort(defaultPort);
        return defaultInfo;
    }

    /**
     * Check if an AE is known.
     */
    public boolean isKnown(String aeTitle) {
        return entries.containsKey(aeTitle.toUpperCase());
    }

    /**
     * AE Information holder.
     */
    public static class AEInfo {
        private String host;
        private int port;
        private String description;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}

