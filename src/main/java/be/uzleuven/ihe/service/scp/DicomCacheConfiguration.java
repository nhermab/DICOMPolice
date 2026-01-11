package be.uzleuven.ihe.service.scp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Configuration for DICOM instance caching.
 */
@Component
@ConfigurationProperties(prefix = "dicom.cache")
public class DicomCacheConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(DicomCacheConfiguration.class);

    private boolean enabled = true;
    private long maxSizeMb = 500;
    private long ttlMinutes = 5;

    @Autowired
    private DicomCache dicomCache;

    @PostConstruct
    public void init() {
        if (dicomCache != null) {
            dicomCache.configure(maxSizeMb, ttlMinutes, enabled);
            LOG.info("DICOM Cache configured: enabled={}, maxSize={}MB, ttl={}min",
                    enabled, maxSizeMb, ttlMinutes);
        }
    }

    // Getters and setters

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getMaxSizeMb() {
        return maxSizeMb;
    }

    public void setMaxSizeMb(long maxSizeMb) {
        this.maxSizeMb = maxSizeMb;
    }

    public long getTtlMinutes() {
        return ttlMinutes;
    }

    public void setTtlMinutes(long ttlMinutes) {
        this.ttlMinutes = ttlMinutes;
    }
}

