package be.uzleuven.ihe.service.qido;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry that maps Study Instance UIDs to their remote WADO-RS base URLs.
 *
 * When WADO-RS proxy mode is enabled, this tracks which remote server each study
 * comes from so that WADO-RS requests can be proxied correctly.
 */
@Service
public class WadoRsProxyRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(WadoRsProxyRegistry.class);

    // Map: Study Instance UID -> Remote WADO-RS base URL
    private final ConcurrentHashMap<String, String> studyToWadoBaseUrl = new ConcurrentHashMap<>();

    /**
     * Register a study's remote WADO-RS base URL.
     *
     * Extracts the base URL from a full retrieve URL like:
     * https://remote-server/wado-rs/studies/1.2.3.../series/4.5.6...
     *
     * @param studyInstanceUID The study instance UID
     * @param retrieveURL Full retrieve URL from MADO manifest
     */
    public void registerStudy(String studyInstanceUID, String retrieveURL) {
        if (studyInstanceUID == null || retrieveURL == null) {
            return;
        }

        String baseUrl = extractWadoBaseUrl(retrieveURL);
        if (baseUrl != null) {
            studyToWadoBaseUrl.put(studyInstanceUID, baseUrl);
            LOG.debug("Registered study {} -> WADO-RS base URL: {}", studyInstanceUID, baseUrl);
        }
    }

    /**
     * Get the remote WADO-RS base URL for a study.
     *
     * @param studyInstanceUID The study instance UID
     * @return Remote WADO-RS base URL, or null if not registered
     */
    public String getWadoBaseUrl(String studyInstanceUID) {
        return studyToWadoBaseUrl.get(studyInstanceUID);
    }

    /**
     * Extract WADO-RS base URL from a full retrieve URL.
     *
     * Examples:
     * - https://server/wado-rs/studies/1.2.3... -> https://server/wado-rs
     * - https://server:8080/dicomweb/studies/... -> https://server:8080/dicomweb
     *
     * @param retrieveURL Full retrieve URL
     * @return WADO-RS base URL, or null if cannot parse
     */
    private String extractWadoBaseUrl(String retrieveURL) {
        if (retrieveURL == null || retrieveURL.isEmpty()) {
            return null;
        }

        // Find "/studies/" in the URL
        int studiesIndex = retrieveURL.indexOf("/studies/");
        if (studiesIndex > 0) {
            return retrieveURL.substring(0, studiesIndex);
        }

        LOG.warn("Cannot extract WADO-RS base URL from: {}", retrieveURL);
        return null;
    }

    /**
     * Build the full remote WADO-RS URL for a specific path.
     *
     * @param studyInstanceUID Study instance UID
     * @param wadoPath Path after the base URL (e.g., "/studies/1.2.3.../series/...")
     * @return Full remote URL, or null if study not registered
     */
    public String buildRemoteUrl(String studyInstanceUID, String wadoPath) {
        String baseUrl = getWadoBaseUrl(studyInstanceUID);
        if (baseUrl == null) {
            return null;
        }

        // Ensure no double slashes
        if (wadoPath.startsWith("/")) {
            return baseUrl + wadoPath;
        } else {
            return baseUrl + "/" + wadoPath;
        }
    }

    /**
     * Check if a study is registered in the proxy.
     *
     * @param studyInstanceUID Study instance UID
     * @return true if registered
     */
    public boolean isStudyRegistered(String studyInstanceUID) {
        return studyToWadoBaseUrl.containsKey(studyInstanceUID);
    }

    /**
     * Clear all registered studies (for testing/cleanup).
     */
    public void clear() {
        studyToWadoBaseUrl.clear();
        LOG.info("Cleared WADO-RS proxy registry");
    }

    /**
     * Get the number of registered studies.
     *
     * @return Number of studies in registry
     */
    public int size() {
        return studyToWadoBaseUrl.size();
    }
}

