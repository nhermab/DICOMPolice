package be.uzleuven.ihe.service.qido;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.util.TagUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.MultiValueMap;

import java.util.*;

/**
 * Utility class for QIDO-RS query parameter parsing.
 *
 * Converts HTTP query parameters to DICOM Attributes for use with
 * the existing MHDBackedMetadataService C-FIND implementation.
 *
 * Supports both DICOM Tag format (00100020) and Keyword format (PatientID).
 */
public class QIDOUtils {

    private static final Logger LOG = LoggerFactory.getLogger(QIDOUtils.class);

    // Reserved QIDO-RS query parameters (not DICOM attribute filters)
    private static final Set<String> RESERVED_PARAMS = new HashSet<>(Arrays.asList(
            "includefield", "limit", "offset", "fuzzymatching", "orderby"
    ));

    // Mapping from DICOM keywords to Tag values
    private static final Map<String, Integer> KEYWORD_TO_TAG = new HashMap<>();

    static {
        // Patient-level attributes
        KEYWORD_TO_TAG.put("PatientID", Tag.PatientID);
        KEYWORD_TO_TAG.put("PatientName", Tag.PatientName);
        KEYWORD_TO_TAG.put("PatientBirthDate", Tag.PatientBirthDate);
        KEYWORD_TO_TAG.put("PatientSex", Tag.PatientSex);

        // Study-level attributes
        KEYWORD_TO_TAG.put("StudyInstanceUID", Tag.StudyInstanceUID);
        KEYWORD_TO_TAG.put("StudyDate", Tag.StudyDate);
        KEYWORD_TO_TAG.put("StudyTime", Tag.StudyTime);
        KEYWORD_TO_TAG.put("StudyDescription", Tag.StudyDescription);
        KEYWORD_TO_TAG.put("StudyID", Tag.StudyID);
        KEYWORD_TO_TAG.put("AccessionNumber", Tag.AccessionNumber);
        KEYWORD_TO_TAG.put("ReferringPhysicianName", Tag.ReferringPhysicianName);
        KEYWORD_TO_TAG.put("ModalitiesInStudy", Tag.ModalitiesInStudy);
        KEYWORD_TO_TAG.put("NumberOfStudyRelatedSeries", Tag.NumberOfStudyRelatedSeries);
        KEYWORD_TO_TAG.put("NumberOfStudyRelatedInstances", Tag.NumberOfStudyRelatedInstances);

        // Series-level attributes
        KEYWORD_TO_TAG.put("SeriesInstanceUID", Tag.SeriesInstanceUID);
        KEYWORD_TO_TAG.put("Modality", Tag.Modality);
        KEYWORD_TO_TAG.put("SeriesNumber", Tag.SeriesNumber);
        KEYWORD_TO_TAG.put("SeriesDescription", Tag.SeriesDescription);
        KEYWORD_TO_TAG.put("NumberOfSeriesRelatedInstances", Tag.NumberOfSeriesRelatedInstances);

        // Instance-level attributes
        KEYWORD_TO_TAG.put("SOPInstanceUID", Tag.SOPInstanceUID);
        KEYWORD_TO_TAG.put("SOPClassUID", Tag.SOPClassUID);
        KEYWORD_TO_TAG.put("InstanceNumber", Tag.InstanceNumber);
        KEYWORD_TO_TAG.put("Rows", Tag.Rows);
        KEYWORD_TO_TAG.put("Columns", Tag.Columns);
        KEYWORD_TO_TAG.put("NumberOfFrames", Tag.NumberOfFrames);
    }

    // Mapping from Tag to VR for proper attribute construction
    private static final Map<Integer, VR> TAG_TO_VR = new HashMap<>();

    static {
        TAG_TO_VR.put(Tag.PatientID, VR.LO);
        TAG_TO_VR.put(Tag.PatientName, VR.PN);
        TAG_TO_VR.put(Tag.PatientBirthDate, VR.DA);
        TAG_TO_VR.put(Tag.PatientSex, VR.CS);
        TAG_TO_VR.put(Tag.StudyInstanceUID, VR.UI);
        TAG_TO_VR.put(Tag.StudyDate, VR.DA);
        TAG_TO_VR.put(Tag.StudyTime, VR.TM);
        TAG_TO_VR.put(Tag.StudyDescription, VR.LO);
        TAG_TO_VR.put(Tag.StudyID, VR.SH);
        TAG_TO_VR.put(Tag.AccessionNumber, VR.SH);
        TAG_TO_VR.put(Tag.ReferringPhysicianName, VR.PN);
        TAG_TO_VR.put(Tag.ModalitiesInStudy, VR.CS);
        TAG_TO_VR.put(Tag.NumberOfStudyRelatedSeries, VR.IS);
        TAG_TO_VR.put(Tag.NumberOfStudyRelatedInstances, VR.IS);
        TAG_TO_VR.put(Tag.SeriesInstanceUID, VR.UI);
        TAG_TO_VR.put(Tag.Modality, VR.CS);
        TAG_TO_VR.put(Tag.SeriesNumber, VR.IS);
        TAG_TO_VR.put(Tag.SeriesDescription, VR.LO);
        TAG_TO_VR.put(Tag.NumberOfSeriesRelatedInstances, VR.IS);
        TAG_TO_VR.put(Tag.SOPInstanceUID, VR.UI);
        TAG_TO_VR.put(Tag.SOPClassUID, VR.UI);
        TAG_TO_VR.put(Tag.InstanceNumber, VR.IS);
        TAG_TO_VR.put(Tag.Rows, VR.US);
        TAG_TO_VR.put(Tag.Columns, VR.US);
        TAG_TO_VR.put(Tag.NumberOfFrames, VR.IS);
    }

    private QIDOUtils() {
        // Utility class - no instantiation
    }

    /**
     * Parse HTTP query parameters into DICOM Attributes for C-FIND.
     *
     * @param params MultiValueMap of query parameters from HTTP request
     * @return Attributes object suitable for MHDBackedMetadataService queries
     */
    public static Attributes parseQueryParams(MultiValueMap<String, String> params) {
        Attributes attrs = new Attributes();

        for (Map.Entry<String, List<String>> entry : params.entrySet()) {
            String key = entry.getKey();
            List<String> values = entry.getValue();

            // Skip reserved parameters
            if (RESERVED_PARAMS.contains(key.toLowerCase())) {
                continue;
            }

            // Get the first value (QIDO-RS typically uses single values)
            String value = values.isEmpty() ? null : values.get(0);
            if (value == null || value.isEmpty()) {
                continue;
            }

            // Convert key to DICOM tag
            int tag = parseTagKey(key);
            if (tag != -1) {
                VR vr = TAG_TO_VR.getOrDefault(tag, VR.LO);
                attrs.setString(tag, vr, value);
                LOG.debug("Parsed query param: {} -> {} = {}", key, TagUtils.toString(tag), value);
            } else {
                LOG.warn("Unknown QIDO-RS query parameter: {}", key);
            }
        }

        return attrs;
    }

    /**
     * Parse a key string to a DICOM tag.
     * Supports both 8-digit hex format (00100020) and keyword format (PatientID).
     *
     * @param key The query parameter key
     * @return DICOM tag integer, or -1 if not recognized
     */
    public static int parseTagKey(String key) {
        // Try keyword lookup first
        Integer tag = KEYWORD_TO_TAG.get(key);
        if (tag != null) {
            return tag;
        }

        // Try 8-digit hex format (e.g., 00100020)
        if (key.length() == 8 && key.matches("[0-9A-Fa-f]+")) {
            try {
                return Integer.parseInt(key, 16);
            } catch (NumberFormatException e) {
                LOG.warn("Invalid hex tag format: {}", key);
            }
        }

        // Try (gggg,eeee) format
        if (key.matches("\\([0-9A-Fa-f]{4},[0-9A-Fa-f]{4}\\)")) {
            try {
                String hex = key.substring(1, 5) + key.substring(6, 10);
                return Integer.parseInt(hex, 16);
            } catch (NumberFormatException e) {
                LOG.warn("Invalid tag format: {}", key);
            }
        }

        return -1;
    }

    /**
     * Parse limit parameter from query params.
     *
     * @param params Query parameters
     * @param defaultLimit Default limit if not specified
     * @param maxLimit Maximum allowed limit
     * @return Parsed limit value
     */
    public static int parseLimit(MultiValueMap<String, String> params, int defaultLimit, int maxLimit) {
        String limitStr = params.getFirst("limit");
        if (limitStr == null || limitStr.isEmpty()) {
            return defaultLimit;
        }
        try {
            int limit = Integer.parseInt(limitStr);
            return Math.min(Math.max(limit, 0), maxLimit);
        } catch (NumberFormatException e) {
            LOG.warn("Invalid limit parameter: {}", limitStr);
            return defaultLimit;
        }
    }

    /**
     * Parse offset parameter from query params.
     *
     * @param params Query parameters
     * @return Parsed offset value (0 if not specified or invalid)
     */
    public static int parseOffset(MultiValueMap<String, String> params) {
        String offsetStr = params.getFirst("offset");
        if (offsetStr == null || offsetStr.isEmpty()) {
            return 0;
        }
        try {
            return Math.max(Integer.parseInt(offsetStr), 0);
        } catch (NumberFormatException e) {
            LOG.warn("Invalid offset parameter: {}", offsetStr);
            return 0;
        }
    }

    /**
     * Parse includefield parameter from query params.
     *
     * @param params Query parameters
     * @return Set of DICOM tags to include, or null if "all" or not specified
     */
    public static Set<Integer> parseIncludeFields(MultiValueMap<String, String> params) {
        List<String> includeFields = params.get("includefield");
        if (includeFields == null || includeFields.isEmpty()) {
            return null;
        }

        // Check for "all"
        for (String field : includeFields) {
            if ("all".equalsIgnoreCase(field)) {
                return null; // null means include all fields
            }
        }

        Set<Integer> tags = new HashSet<>();
        for (String field : includeFields) {
            int tag = parseTagKey(field);
            if (tag != -1) {
                tags.add(tag);
            }
        }

        return tags.isEmpty() ? null : tags;
    }

    /**
     * Parse fuzzymatching parameter from query params.
     *
     * @param params Query parameters
     * @return true if fuzzy matching is requested
     */
    public static boolean parseFuzzyMatching(MultiValueMap<String, String> params) {
        String fuzzy = params.getFirst("fuzzymatching");
        return "true".equalsIgnoreCase(fuzzy);
    }

    /**
     * Get the keyword for a DICOM tag if known.
     *
     * @param tag DICOM tag
     * @return Keyword string or null if not known
     */
    public static String getKeywordForTag(int tag) {
        for (Map.Entry<String, Integer> entry : KEYWORD_TO_TAG.entrySet()) {
            if (entry.getValue() == tag) {
                return entry.getKey();
            }
        }
        return null;
    }
}

