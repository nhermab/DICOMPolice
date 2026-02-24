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

    public Map<String, AEInfo> getEntries() {
        return entries;
    }

    public void setEntries(Map<String, AEInfo> entries) {
        this.entries = entries;
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
     * @return AEInfo if found, otherwise null
     */
    public AEInfo lookup(String aeTitle) {
        return entries.get(aeTitle.toUpperCase());
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

