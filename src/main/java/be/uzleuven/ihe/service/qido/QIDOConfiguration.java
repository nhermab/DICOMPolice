package be.uzleuven.ihe.service.qido;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for QIDO-RS (Query based on ID for DICOM Objects) DICOMweb service.
 *
 * QIDO-RS provides RESTful DICOM metadata search capabilities as defined in PS3.18.
 */
@Component
@ConfigurationProperties(prefix = "qido.rs")
public class QIDOConfiguration {

    /** Base URL for this QIDO-RS service (used for generating Retrieve URLs) */
    private String baseUrl = "http://localhost:8080/dicomweb";

    /** Whether QIDO-RS endpoint is enabled */
    private boolean enabled = true;

    /** Default limit for search results (0 = no limit) */
    private int defaultLimit = 100;

    /** Maximum allowed limit for search results */
    private int maxLimit = 1000;

    /** Whether to support fuzzy matching for Patient Name */
    private boolean fuzzyMatchingSupported = false;

    /** Default content type for responses */
    private String defaultContentType = "application/dicom+json";

    /**
     * Enable WADO-RS proxy mode.
     * When true, QIDO-RS responses contain local proxy URLs and this service proxies WADO-RS requests.
     * When false (default), QIDO-RS responses contain remote WADO-RS URLs directly.
     */
    private boolean wadoProxyEnabled = false;

    // Getters and Setters

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getDefaultLimit() {
        return defaultLimit;
    }

    public void setDefaultLimit(int defaultLimit) {
        this.defaultLimit = defaultLimit;
    }

    public int getMaxLimit() {
        return maxLimit;
    }

    public void setMaxLimit(int maxLimit) {
        this.maxLimit = maxLimit;
    }

    public boolean isFuzzyMatchingSupported() {
        return fuzzyMatchingSupported;
    }

    public void setFuzzyMatchingSupported(boolean fuzzyMatchingSupported) {
        this.fuzzyMatchingSupported = fuzzyMatchingSupported;
    }

    public String getDefaultContentType() {
        return defaultContentType;
    }

    public void setDefaultContentType(String defaultContentType) {
        this.defaultContentType = defaultContentType;
    }

    public boolean isWadoProxyEnabled() {
        return wadoProxyEnabled;
    }

    public void setWadoProxyEnabled(boolean wadoProxyEnabled) {
        this.wadoProxyEnabled = wadoProxyEnabled;
    }
}

