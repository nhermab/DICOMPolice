package be.uzleuven.ihe.service.qido;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.VR;

import java.util.*;

/**
 * Converter for DICOM Attributes to DICOM JSON format as specified in PS3.18 Annex F.
 *
 * The DICOM JSON Model represents DICOM data using:
 * - 8-digit uppercase hexadecimal tag keys (e.g., "00100020")
 * - Objects with "vr" (Value Representation) and "Value" properties
 *
 * Example output:
 * {
 *   "0020000D": { "vr": "UI", "Value": ["1.2.3.4.5.6.7.8"] },
 *   "00100010": { "vr": "PN", "Value": [{ "Alphabetic": "Doe^John" }] }
 * }
 */
public class DICOMJSONConverter {


    private DICOMJSONConverter() {
        // Utility class - no instantiation
    }

    /**
     * Convert DICOM Attributes to DICOM JSON format.
     *
     * @param attrs DICOM Attributes to convert
     * @return Map representation of DICOM JSON
     */
    public static Map<String, Object> toJSON(Attributes attrs) {
        return toJSON(attrs, null);
    }

    /**
     * Convert DICOM Attributes to DICOM JSON format with optional field filtering.
     *
     * @param attrs DICOM Attributes to convert
     * @param includeFields Set of tags to include (null = include all)
     * @return Map representation of DICOM JSON
     */
    public static Map<String, Object> toJSON(Attributes attrs, Set<Integer> includeFields) {
        Map<String, Object> json = new LinkedHashMap<>();

        if (attrs == null) {
            return json;
        }

        int[] tags = attrs.tags();
        for (int tag : tags) {
            // Skip if filtering and tag not in include list
            if (includeFields != null && !includeFields.contains(tag)) {
                continue;
            }

            VR vr = attrs.getVR(tag);
            if (vr == null) {
                continue;
            }

            String tagKey = formatTagKey(tag);
            Map<String, Object> element = createDICOMJSONElement(attrs, tag, vr);
            json.put(tagKey, element);
        }

        return json;
    }

    /**
     * Convert DICOM Attributes to DICOM JSON format and add a Retrieve URL.
     *
     * @param attrs DICOM Attributes to convert
     * @param retrieveUrl The Retrieve URL (0008,1190) to include
     * @param includeFields Set of tags to include (null = include all)
     * @return Map representation of DICOM JSON with Retrieve URL
     */
    public static Map<String, Object> toJSONWithRetrieveURL(Attributes attrs, String retrieveUrl, Set<Integer> includeFields) {
        Map<String, Object> json = toJSON(attrs, includeFields);

        // Add Retrieve URL (0008,1190) if provided
        if (retrieveUrl != null && !retrieveUrl.isEmpty()) {
            Map<String, Object> urlElement = new LinkedHashMap<>();
            urlElement.put("vr", "UR");
            urlElement.put("Value", Collections.singletonList(retrieveUrl));
            json.put("00081190", urlElement);
        }

        return json;
    }

    /**
     * Format a DICOM tag as an 8-digit uppercase hex string.
     *
     * @param tag DICOM tag integer
     * @return 8-digit hex string (e.g., "00100020")
     */
    public static String formatTagKey(int tag) {
        return String.format("%08X", tag);
    }

    /**
     * Create a DICOM JSON element for a single attribute.
     *
     * @param attrs Source attributes
     * @param tag Tag to extract
     * @param vr Value Representation
     * @return Map with "vr" and "Value" properties
     */
    private static Map<String, Object> createDICOMJSONElement(Attributes attrs, int tag, VR vr) {
        Map<String, Object> element = new LinkedHashMap<>();
        element.put("vr", vr.name());

        // Handle different VR types
        switch (vr) {
            case PN:
                // Person Name - special format with Alphabetic component
                String[] pnValues = attrs.getStrings(tag);
                if (pnValues != null && pnValues.length > 0) {
                    List<Map<String, String>> pnList = new ArrayList<>();
                    for (String pn : pnValues) {
                        if (pn != null && !pn.isEmpty()) {
                            Map<String, String> pnMap = new LinkedHashMap<>();
                            pnMap.put("Alphabetic", pn);
                            pnList.add(pnMap);
                        }
                    }
                    if (!pnList.isEmpty()) {
                        element.put("Value", pnList);
                    }
                }
                break;

            case IS:
            case DS:
                // Integer/Decimal strings - convert to numbers
                String[] numStrings = attrs.getStrings(tag);
                if (numStrings != null && numStrings.length > 0) {
                    List<Object> numList = new ArrayList<>();
                    for (String numStr : numStrings) {
                        if (numStr != null && !numStr.isEmpty()) {
                            try {
                                if (vr == VR.IS) {
                                    numList.add(Integer.parseInt(numStr.trim()));
                                } else {
                                    numList.add(Double.parseDouble(numStr.trim()));
                                }
                            } catch (NumberFormatException e) {
                                // Keep as string if parsing fails
                                numList.add(numStr);
                            }
                        }
                    }
                    if (!numList.isEmpty()) {
                        element.put("Value", numList);
                    }
                }
                break;

            case US:
            case SS:
            case UL:
            case SL:
                // Integer types
                int[] intValues = attrs.getInts(tag);
                if (intValues != null && intValues.length > 0) {
                    List<Integer> intList = new ArrayList<>();
                    for (int val : intValues) {
                        intList.add(val);
                    }
                    element.put("Value", intList);
                }
                break;

            case FL:
            case FD:
                // Float types
                double[] doubleValues = attrs.getDoubles(tag);
                if (doubleValues != null && doubleValues.length > 0) {
                    List<Double> doubleList = new ArrayList<>();
                    for (double val : doubleValues) {
                        doubleList.add(val);
                    }
                    element.put("Value", doubleList);
                }
                break;

            case SQ:
                // Sequence - recursively convert
                org.dcm4che3.data.Sequence seq = attrs.getSequence(tag);
                if (seq != null && !seq.isEmpty()) {
                    List<Map<String, Object>> seqList = new ArrayList<>();
                    for (Attributes seqItem : seq) {
                        seqList.add(toJSON(seqItem));
                    }
                    element.put("Value", seqList);
                }
                break;

            case OB:
            case OD:
            case OF:
            case OL:
            case OW:
            case UN:
                // Binary data - encode as InlineBinary (Base64) or BulkDataURI
                // For QIDO-RS, we typically don't return bulk data
                try {
                    byte[] bytes = attrs.getBytes(tag);
                    if (bytes != null && bytes.length > 0) {
                        // For small data, inline as Base64
                        if (bytes.length <= 1024) {
                            String base64 = Base64.getEncoder().encodeToString(bytes);
                            element.put("InlineBinary", base64);
                        }
                        // For large data, we would typically use BulkDataURI
                    }
                } catch (java.io.IOException e) {
                    // Skip binary data that cannot be read
                }
                break;

            default:
                // String types (UI, LO, SH, CS, DA, TM, etc.)
                String[] strValues = attrs.getStrings(tag);
                if (strValues != null && strValues.length > 0) {
                    List<String> strList = new ArrayList<>();
                    for (String str : strValues) {
                        if (str != null) {
                            strList.add(str);
                        }
                    }
                    if (!strList.isEmpty()) {
                        element.put("Value", strList);
                    }
                }
                break;
        }

        return element;
    }

    /**
     * Convert a list of DICOM Attributes to a DICOM JSON array.
     *
     * @param attrsList List of Attributes to convert
     * @return List of DICOM JSON objects
     */
    public static List<Map<String, Object>> toJSONArray(List<Attributes> attrsList) {
        return toJSONArray(attrsList, null);
    }

    /**
     * Convert a list of DICOM Attributes to a DICOM JSON array with optional field filtering.
     *
     * @param attrsList List of Attributes to convert
     * @param includeFields Set of tags to include (null = include all)
     * @return List of DICOM JSON objects
     */
    public static List<Map<String, Object>> toJSONArray(List<Attributes> attrsList, Set<Integer> includeFields) {
        List<Map<String, Object>> jsonArray = new ArrayList<>();
        for (Attributes attrs : attrsList) {
            jsonArray.add(toJSON(attrs, includeFields));
        }
        return jsonArray;
    }
}

