package be.uzleuven.ihe.dicom.validator.validation;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import be.uzleuven.ihe.dicom.validator.model.ValidationResult;
import be.uzleuven.ihe.dicom.constants.ValidationMessages;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

/**
 * Validates retrieval information in MADO manifests.
 * MADO supports two retrieval modes: Retrieve URL (WADO-RS) and Retrieve Location UID.
 */
public final class MADORetrievalValidator {

    private MADORetrievalValidator() {
    }

    public static void validateRetrievalInformation(Attributes dataset, ValidationResult result,
                                                   String modulePath, boolean verbose) {
        // Check Evidence sequence for retrieval information
        Sequence evidenceSeq = dataset.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence);
        if (evidenceSeq == null || evidenceSeq.isEmpty()) {
            result.addWarning("CurrentRequestedProcedureEvidenceSequence is empty. " +
                            "Cannot validate retrieval information.", modulePath);
            return;
        }

        boolean foundRetrievalURL = false;
        boolean foundRetrievalLocationUID = false;
        boolean foundRetrieveURI = false;
        boolean mixedCommunityMode = false;

        Set<String> baseURLs = new HashSet<>();
        Set<String> locationUIDs = new HashSet<>();

        for (int studyIdx = 0; studyIdx < evidenceSeq.size(); studyIdx++) {
            Attributes studyItem = evidenceSeq.get(studyIdx);
            String studyPath = modulePath + ".Evidence.Study[" + studyIdx + "]";

            Sequence seriesSeq = studyItem.getSequence(Tag.ReferencedSeriesSequence);
            if (seriesSeq == null || seriesSeq.isEmpty()) {
                continue;
            }

            for (int seriesIdx = 0; seriesIdx < seriesSeq.size(); seriesIdx++) {
                Attributes seriesItem = seriesSeq.get(seriesIdx);
                String seriesPath = studyPath + ".Series[" + seriesIdx + "]";

                boolean seriesHasURL = false;
                boolean seriesHasLoc = false;

                // Check for Retrieve URL
                String retrieveURL = seriesItem.getString(Tag.RetrieveURL);
                if (retrieveURL != null && !retrieveURL.trim().isEmpty()) {
                    seriesHasURL = true;
                    foundRetrievalURL = true;
                    baseURLs.add(retrieveURL);
                    validateRetrieveURL(retrieveURL, result, seriesPath);
                }

                // Check for Retrieve Location UID
                String locationUID = seriesItem.getString(Tag.RetrieveLocationUID);
                if (locationUID != null && !locationUID.trim().isEmpty()) {
                    seriesHasLoc = true;
                    foundRetrievalLocationUID = true;
                    locationUIDs.add(locationUID);
                    validateRetrieveLocationUID(locationUID, result, seriesPath);
                }

                // (Optional) Retrieve URI (Rendered Instances / remote viewer launch)
                String retrieveURI = seriesItem.getString(Tag.RetrieveURI);
                if (retrieveURI != null && !retrieveURI.trim().isEmpty()) {
                    foundRetrieveURI = true;
                    validateRetrieveURI(retrieveURI, result, seriesPath);
                }

                // Enforce: per series, at least one retrieval method must be present
                if (!seriesHasURL && !seriesHasLoc) {
                    result.addError(ValidationMessages.RETRIEVE_LOCATION_UID_MISSING, seriesPath);
                }

                // Enforce: do not mix addressing modes across series (community must be consistent)
                if (seriesHasURL && !seriesHasLoc) {
                    if (foundRetrievalLocationUID) {
                        mixedCommunityMode = true;
                    }
                }
                if (seriesHasLoc && !seriesHasURL) {
                    if (foundRetrievalURL) {
                        mixedCommunityMode = true;
                    }
                }

                // Check at instance level too
                Sequence sopSeq = seriesItem.getSequence(Tag.ReferencedSOPSequence);
                if (sopSeq != null) {
                    for (int sopIdx = 0; sopIdx < sopSeq.size(); sopIdx++) {
                        Attributes sopItem = sopSeq.get(sopIdx);
                        String sopPath = seriesPath + ".SOP[" + sopIdx + "]";

                        String sopRetrieveURL = sopItem.getString(Tag.RetrieveURL);
                        if (sopRetrieveURL != null && !sopRetrieveURL.trim().isEmpty()) {
                            foundRetrievalURL = true;
                            validateRetrieveURL(sopRetrieveURL, result, sopPath);
                        }

                        String sopLocationUID = sopItem.getString(Tag.RetrieveLocationUID);
                        if (sopLocationUID != null && !sopLocationUID.trim().isEmpty()) {
                            foundRetrievalLocationUID = true;
                            validateRetrieveLocationUID(sopLocationUID, result, sopPath);
                        }

                        String sopRetrieveURI = sopItem.getString(Tag.RetrieveURI);
                        if (sopRetrieveURI != null && !sopRetrieveURI.trim().isEmpty()) {
                            foundRetrieveURI = true;
                            validateRetrieveURI(sopRetrieveURI, result, sopPath);
                        }
                    }
                }
            }
        }

        // Report retrieval method findings
        if (!foundRetrievalURL && !foundRetrievalLocationUID) {
            result.addError("No retrieval information found (neither Retrieve URL nor Retrieve Location UID). " +
                            "MADO manifest must include at least one retrieval method per series.", modulePath);
        } else {
            if (mixedCommunityMode) {
                result.addWarning("Both Retrieve URL and Retrieve Location UID addressing modes are used across different series. " +
                        "MADO communities should be consistent and use a single addressing mode.", modulePath);
            }

            if (foundRetrievalURL && foundRetrievalLocationUID) {
                result.addInfo("Both Retrieve URL and Retrieve Location UID are present. " +
                        "Consumer should prefer one based on deployment configuration.", modulePath);
            } else if (foundRetrievalURL) {
                result.addInfo("Using Retrieve URL (WADO-RS) addressing mode. Found " + baseURLs.size() +
                        " unique base URL(s).", modulePath);
            } else {
                result.addInfo("Using Retrieve Location UID addressing mode. Found " + locationUIDs.size() +
                        " unique location UID(s).", modulePath);
            }
        }

        if (foundRetrieveURI) {
            result.addInfo("RetrieveURI (0040,E010) present for remote viewer/server-side rendering option.", modulePath);
        }
    }

    /**
     * Public method for validating Retrieve URL format (used by Appendix B validator).
     * Requirement V-LOC-04: If Retrieve URL is present, it MUST be syntactically valid.
     */
    public static void validateRetrieveURLFormat(String url, ValidationResult result, String path) {
        validateRetrieveURL(url, result, path);
    }

    /**
     * Public method for validating Retrieve Location UID format (used by Appendix B validator).
     * Requirement V-LOC-01: Value must be valid UID in OID format.
     */
    public static void validateRetrieveLocationUIDFormat(String uid, ValidationResult result, String path) {
        validateRetrieveLocationUID(uid, result, path);
    }

    private static void validateRetrieveURL(String url, ValidationResult result, String path) {
        if (url == null || url.trim().isEmpty()) {
            return;
        }

        // Basic URL validation
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();

            if (scheme == null) {
                result.addError("Retrieve URL has no scheme: " + url, path);
                return;
            }

            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                result.addWarning("Retrieve URL uses non-HTTP(S) scheme '" + scheme + "': " + url +
                                ". Expected http or https for WADO-RS.", path);
            }

            if (!"https".equalsIgnoreCase(scheme)) {
                result.addWarning("Retrieve URL uses insecure 'http' scheme: " + url +
                                ". Consider using https for secure retrieval.", path);
            }

            String host = uri.getHost();
            if (host == null || host.trim().isEmpty()) {
                result.addError("Retrieve URL has no host: " + url, path);
            }

            // Check for valid WADO-RS format (typically includes /studies/...)
            String uriPath = uri.getPath();
            if (uriPath != null && !uriPath.contains("/studies")) {
                result.addInfo("Retrieve URL does not appear to follow WADO-RS pattern (/studies/...): " + url, path);
            }

        } catch (Exception e) {
            result.addError("Invalid Retrieve URL format: " + url + " - " + e.getMessage(), path);
        }
    }

    private static void validateRetrieveLocationUID(String uid, ValidationResult result, String path) {
        if (uid == null || uid.trim().isEmpty()) {
            return;
        }

        // Validate UID format (must be valid DICOM UID/OID)
        if (!isValidUID(uid)) {
            result.addError("Retrieve Location UID has invalid format: " + uid +
                          ". Must be valid DICOM UID (OID format).", path);
        }

        if (uid.length() > 64) {
            result.addError("Retrieve Location UID exceeds 64 character limit: " + uid.length() + " characters", path);
        }
    }

    private static void validateRetrieveURI(String url, ValidationResult result, String path) {
        if (url == null || url.trim().isEmpty()) {
            return;
        }

        // Treat Retrieve URI as a full launch URL for the specific study/series/instance
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (scheme == null) {
                result.addError("Retrieve URI has no scheme: " + url, path);
                return;
            }

            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                result.addWarning("Retrieve URI uses non-HTTP(S) scheme '" + scheme + "': " + url,
                        path);
            }

            String host = uri.getHost();
            if (host == null || host.trim().isEmpty()) {
                result.addError("Retrieve URI has no host: " + url, path);
            }

            // Best-effort: the URL should embed a study UID somewhere (check for typical token)
            String raw = url.toLowerCase();
            if (!(raw.contains("study") || raw.contains("studies") || raw.contains("studyinstanceuid"))) {
                result.addInfo("Retrieve URI does not obviously include Study Instance UID parameter/path. " +
                        "Ensure it launches a viewer for the specific study.", path);
            }
        } catch (Exception e) {
            result.addError("Invalid Retrieve URI format: " + url + " - " + e.getMessage(), path);
        }
    }

    private static boolean isValidUID(String uid) {
        if (uid == null || uid.trim().isEmpty()) {
            return false;
        }

        // Basic DICOM UID validation: numeric components separated by dots
        // Must start and end with digit, no consecutive dots
        if (!uid.matches("^[0-9]+(\\.[0-9]+)*$")) {
            return false;
        }

        // No leading zeros in components (except "0" itself)
        String[] parts = uid.split("\\.");
        for (String part : parts) {
            if (part.length() > 1 && part.startsWith("0")) {
                return false;
            }
        }

        return true;
    }
}

