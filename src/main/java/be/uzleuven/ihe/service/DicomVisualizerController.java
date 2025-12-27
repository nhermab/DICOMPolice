package be.uzleuven.ihe.service;

import be.uzleuven.ihe.dicom.validator.CLIDICOMVerify;
import be.uzleuven.ihe.dicom.validator.model.ValidationResult;

import org.dcm4che3.data.*;
import org.dcm4che3.io.DicomInputStream;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.util.*;

/**
 * REST Controller for DICOM KOS/MADO Visualization
 */
@RestController
@RequestMapping("/api/visualizer")
@CrossOrigin(origins = "*")
public class DicomVisualizerController {

    @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> parseDicomFile(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "No file provided");
                return ResponseEntity.badRequest().body(error);
            }

            // Save to temp file
            File tempFile = File.createTempFile("dicom_parse_", ".dcm");
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(file.getBytes());
            }

            // Parse DICOM file
            Attributes dataset;
            try (DicomInputStream dis = new DicomInputStream(tempFile)) {
                dataset = dis.readDataset();
            }

            // Determine file type
            String sopClassUID = dataset.getString(Tag.SOPClassUID, "");
            String fileType = determineFileType(sopClassUID, dataset);

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("fileName", file.getOriginalFilename());
            response.put("fileSize", file.getSize());
            response.put("fileType", fileType);
            response.put("sopClassUID", sopClassUID);
            response.put("sopClassUIDName", getSopClassName(sopClassUID));

            // Extract all DICOM tags
            response.put("tags", extractAllTags(dataset));

            // Extract structured content based on type
            if ("KOS".equals(fileType)) {
                response.put("content", extractKOSContent(dataset));
            } else if ("MADO".equals(fileType)) {
                response.put("content", extractMADOContent(dataset));
            }

            if (!tempFile.delete()) {
                System.err.println("Failed to delete temp file: " + tempFile.getAbsolutePath());
            }
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("Error parsing DICOM file: " + e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to parse DICOM file: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    @PostMapping(value = "/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> validateDicomFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "profile", required = false) String profile) {
        try {
            if (file.isEmpty()) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "No file provided");
                return ResponseEntity.badRequest().body(error);
            }

            // Save to temp file
            File tempFile = File.createTempFile("dicom_validate_", ".dcm");
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(file.getBytes());
            }

            // Auto-detect profile if not provided
            if (profile == null || profile.isEmpty()) {
                Attributes dataset;
                try (DicomInputStream dis = new DicomInputStream(tempFile)) {
                    dataset = dis.readDataset();
                }
                String sopClassUID = dataset.getString(Tag.SOPClassUID, "");
                String fileType = determineFileType(sopClassUID, dataset);

                if ("KOS".equals(fileType)) {
                    profile = "IHEXDSIManifest";
                } else if ("MADO".equals(fileType)) {
                    profile = "IHEMADO";
                } else {
                    if (!tempFile.delete()) {
                        System.err.println("Failed to delete temp file: " + tempFile.getAbsolutePath());
                    }
                    Map<String, String> error = new HashMap<>();
                    error.put("error", "Unknown file type, cannot validate");
                    return ResponseEntity.badRequest().body(error);
                }
            }

            // Validate
            ValidationResult validationResult = CLIDICOMVerify.validateFile(tempFile, profile);
            if (!tempFile.delete()) {
                System.err.println("Failed to delete temp file: " + tempFile.getAbsolutePath());
            }

            // Convert to JSON-friendly format
            Map<String, Object> response = new HashMap<>();
            response.put("valid", validationResult.isValid());
            response.put("profile", profile);
            response.put("messages", convertValidationMessages(validationResult.getMessages()));

            Map<String, Integer> summary = getStringIntegerMap(validationResult);
            response.put("summary", summary);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("Error validating DICOM file: " + e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to validate DICOM file: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    @NonNull
    private static Map<String, Integer> getStringIntegerMap(ValidationResult validationResult) {
        Map<String, Integer> summary = new HashMap<>();
        summary.put("totalMessages", validationResult.getMessages().size());
        summary.put("errors", validationResult.getErrors().size());
        summary.put("warnings", validationResult.getWarnings().size());
        // Count info messages manually
        int infoCount = 0;
        for (ValidationResult.ValidationMessage msg : validationResult.getMessages()) {
            if (msg.getSeverity() == ValidationResult.Severity.INFO) {
                infoCount++;
            }
        }
        summary.put("infos", infoCount);
        return summary;
    }

    private String determineFileType(String sopClassUID, Attributes dataset) {
        if (sopClassUID == null) {
            return "UNKNOWN";
        }

        // Key Object Selection Document
        if ("1.2.840.10008.5.1.4.1.1.88.59".equals(sopClassUID)) {
            // Both KOS and MADO use the same SOP Class UID
            // Distinguish by checking the ConceptNameCodeSequence (Document Title)
            return isMADODocument(dataset) ? "MADO" : "KOS";
        }

        return "UNKNOWN";
    }

    private boolean isMADODocument(Attributes dataset) {
        // Check ConceptNameCodeSequence for MADO-specific document titles
        Sequence seq = dataset.getSequence(Tag.ConceptNameCodeSequence);
        if (seq == null || seq.isEmpty()) {
            return false;
        }

        Attributes item = seq.get(0);
        String codeValue = item.getString(Tag.CodeValue);
        String csd = item.getString(Tag.CodingSchemeDesignator);

        // MADO uses:
        // (113030, DCM, "Manifest") - Standard manifest title
        // (ddd001, DCM, "Manifest with Description") - MADO-specific title
        boolean isManifest = "113030".equals(codeValue) && "DCM".equals(csd);
        boolean isManifestWithDesc = "ddd001".equals(codeValue) && "DCM".equals(csd);

        // If it's one of these, check for MADO-specific content (TID 1600)
        if (isManifest || isManifestWithDesc) {
            // Additional check: MADO should have TID 1600 Image Library content
            // We can detect this by looking for specific content items
            return hasImageLibraryContent(dataset) || isManifestWithDesc;
        }

        return false;
    }

    private boolean hasImageLibraryContent(Attributes dataset) {
        // Check for TID 1600 Image Library template indicators
        // This is a heuristic: look for content structure that suggests MADO
        Sequence contentSeq = dataset.getSequence(Tag.ContentSequence);
        if (contentSeq == null || contentSeq.isEmpty()) {
            return false;
        }

        // MADO typically has nested CONTAINER items with specific structure
        // Look for CONTAINER with "Image Library" or descriptors
        for (Attributes contentItem : contentSeq) {
            String valueType = contentItem.getString(Tag.ValueType);
            if ("CONTAINER".equals(valueType)) {
                Sequence conceptSeq = contentItem.getSequence(Tag.ConceptNameCodeSequence);
                if (conceptSeq != null && !conceptSeq.isEmpty()) {
                    Attributes concept = conceptSeq.get(0);
                    String conceptCode = concept.getString(Tag.CodeValue);
                    // Check for TID 1600 root or descriptors (126200, 121070, etc.)
                    if ("126200".equals(conceptCode) || "121070".equals(conceptCode) ||
                        "121071".equals(conceptCode) || "121072".equals(conceptCode)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private String getSopClassName(String sopClassUID) {
        if (sopClassUID == null) {
            return "Unknown";
        }

        if ("1.2.840.10008.5.1.4.1.1.88.59".equals(sopClassUID)) {
            return "Key Object Selection Document";
        }
        return UID.nameOf(sopClassUID);
    }

    private List<Map<String, Object>> extractAllTags(Attributes dataset) {
        List<Map<String, Object>> tags = new ArrayList<>();
        extractTagsRecursive(dataset, tags, "");
        return tags;
    }

    private void extractTagsRecursive(Attributes dataset, List<Map<String, Object>> tags, String path) {
        int[] tagArray = dataset.tags();
        Arrays.sort(tagArray);

        for (int tag : tagArray) {
            VR vr = dataset.getVR(tag);
            String tagName = ElementDictionary.keywordOf(tag, null);
            if (tagName == null) {
                tagName = "Unknown";
            }

            Map<String, Object> tagInfo = new LinkedHashMap<>();
            tagInfo.put("tag", String.format("(%04X,%04X)", (tag >> 16) & 0xFFFF, tag & 0xFFFF));
            tagInfo.put("tagHex", String.format("%08X", tag));
            tagInfo.put("name", tagName);
            tagInfo.put("vr", vr.toString());
            tagInfo.put("path", path);

            // Get value
            Object value = extractTagValue(dataset, tag, vr);
            tagInfo.put("value", value);

            // Check if it's a sequence
            if (vr == VR.SQ) {
                Sequence seq = dataset.getSequence(tag);
                if (seq != null) {
                    tagInfo.put("sequenceLength", seq.size());
                    List<List<Map<String, Object>>> sequenceItems = new ArrayList<>();

                    for (int i = 0; i < seq.size(); i++) {
                        Attributes item = seq.get(i);
                        List<Map<String, Object>> itemTags = new ArrayList<>();
                        extractTagsRecursive(item, itemTags, path + tagName + "[" + i + "].");
                        sequenceItems.add(itemTags);
                    }

                    tagInfo.put("sequenceItems", sequenceItems);
                }
            }

            tags.add(tagInfo);
        }
    }

    private Object extractTagValue(Attributes dataset, int tag, VR vr) {
        try {
            if (vr == VR.SQ) {
                return null; // Sequences handled separately
            }

            // Handle different VR types
            switch (vr) {
                case AE:
                case AS:
                case CS:
                case DA:
                case DS:
                case DT:
                case IS:
                case LO:
                case LT:
                case PN:
                case SH:
                case ST:
                case TM:
                case UC:
                case UI:
                case UR:
                case UT:
                    String[] strings = dataset.getStrings(tag);
                    if (strings == null || strings.length == 0) {
                        return null;
                    }
                    if (strings.length == 1) {
                        return strings[0];
                    }
                    return Arrays.asList(strings);

                case UL:

                case US:
                case SS:
                    int[] ints = dataset.getInts(tag);
                    if (ints == null || ints.length == 0) {
                        return null;
                    }
                    if (ints.length == 1) {
                        return ints[0];
                    }
                    return Arrays.stream(ints).boxed().toArray();

                case FL:
                case FD:
                    double[] doubles = dataset.getDoubles(tag);
                    if (doubles == null || doubles.length == 0) {
                        return null;
                    }
                    if (doubles.length == 1) {
                        return doubles[0];
                    }
                    return Arrays.stream(doubles).boxed().toArray();

                case OB:
                case OW:
                case OD:
                case OF:
                case OL:
                    byte[] bytes = dataset.getBytes(tag);
                    if (bytes == null) {
                        return null;
                    }
                    return String.format("[Binary Data: %d bytes]", bytes.length);

                default:
                    return dataset.getString(tag, "");
            }
        } catch (Exception e) {
            return "[Error extracting value: " + e.getMessage() + "]";
        }
    }

    private Map<String, Object> extractKOSContent(Attributes dataset) {
        Map<String, Object> content = new LinkedHashMap<>();

        try {
            // Extract basic KOS information
            content.put("studyInstanceUID", dataset.getString(Tag.StudyInstanceUID, ""));
            content.put("seriesInstanceUID", dataset.getString(Tag.SeriesInstanceUID, ""));
            content.put("instanceNumber", dataset.getString(Tag.InstanceNumber, ""));

            // Extract evidence
            List<Map<String, Object>> evidenceList = new ArrayList<>();
            Sequence currentReqEvidenceSeq = dataset.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence);

            if (currentReqEvidenceSeq != null) {
                for (Attributes evidenceItem : currentReqEvidenceSeq) {
                    Map<String, Object> evidence = new LinkedHashMap<>();
                    evidence.put("studyInstanceUID", evidenceItem.getString(Tag.StudyInstanceUID, ""));

                    List<Map<String, Object>> seriesList = new ArrayList<>();
                    Sequence referencedSeriesSeq = evidenceItem.getSequence(Tag.ReferencedSeriesSequence);

                    if (referencedSeriesSeq != null) {
                        for (Attributes seriesItem : referencedSeriesSeq) {
                            Map<String, Object> series = new LinkedHashMap<>();
                            series.put("seriesInstanceUID", seriesItem.getString(Tag.SeriesInstanceUID, ""));

                            List<String> sopInstanceUIDs = new ArrayList<>();
                            Sequence referencedSOPSeq = seriesItem.getSequence(Tag.ReferencedSOPSequence);

                            if (referencedSOPSeq != null) {
                                for (Attributes sopItem : referencedSOPSeq) {
                                    sopInstanceUIDs.add(sopItem.getString(Tag.ReferencedSOPInstanceUID, ""));
                                }
                            }

                            series.put("sopInstances", sopInstanceUIDs);
                            series.put("count", sopInstanceUIDs.size());
                            seriesList.add(series);
                        }
                    }

                    evidence.put("series", seriesList);
                    evidenceList.add(evidence);
                }
            }

            content.put("evidence", evidenceList);
            content.put("totalEvidenceStudies", evidenceList.size());

            // Extract content sequence (SR tree)
            Sequence contentSeq = dataset.getSequence(Tag.ContentSequence);
            if (contentSeq != null) {
                content.put("contentTree", extractContentTree(contentSeq));
            }

        } catch (Exception e) {
            content.put("error", "Failed to extract KOS content: " + e.getMessage());
        }

        return content;
    }

    private Map<String, Object> extractMADOContent(Attributes dataset) {
        Map<String, Object> content = new LinkedHashMap<>();

        try {
            // First extract KOS content (MADO is based on KOS)
            content.putAll(extractKOSContent(dataset));

            // Add MADO-specific information
            content.put("isMADO", true);

            // Extract descriptor information from content tree
            Sequence contentSeq = dataset.getSequence(Tag.ContentSequence);
            if (contentSeq != null) {
                Map<String, Object> descriptors = extractMADODescriptors(contentSeq);
                content.put("descriptors", descriptors);
            }

        } catch (Exception e) {
            content.put("error", "Failed to extract MADO content: " + e.getMessage());
        }

        return content;
    }

    private List<Map<String, Object>> extractContentTree(Sequence contentSeq) {
        List<Map<String, Object>> tree = new ArrayList<>();

        for (Attributes item : contentSeq) {
            Map<String, Object> node = new LinkedHashMap<>();

            node.put("valueType", item.getString(Tag.ValueType, ""));
            node.put("relationshipType", item.getString(Tag.RelationshipType, ""));

            // Extract concept name
            Sequence conceptNameSeq = item.getSequence(Tag.ConceptNameCodeSequence);
            if (conceptNameSeq != null && !conceptNameSeq.isEmpty()) {
                Attributes concept = conceptNameSeq.get(0);
                Map<String, String> conceptName = new LinkedHashMap<>();
                conceptName.put("codeValue", concept.getString(Tag.CodeValue, ""));
                conceptName.put("codingSchemeDesignator", concept.getString(Tag.CodingSchemeDesignator, ""));
                conceptName.put("codeMeaning", concept.getString(Tag.CodeMeaning, ""));
                node.put("conceptName", conceptName);
            }

            // Extract value based on value type
            String valueType = item.getString(Tag.ValueType, "");
            switch (valueType) {
                case "CODE":
                    Sequence conceptCodeSeq = item.getSequence(Tag.ConceptCodeSequence);
                    if (conceptCodeSeq != null && !conceptCodeSeq.isEmpty()) {
                        Attributes code = conceptCodeSeq.get(0);
                        Map<String, String> codeValue = new LinkedHashMap<>();
                        codeValue.put("codeValue", code.getString(Tag.CodeValue, ""));
                        codeValue.put("codingSchemeDesignator", code.getString(Tag.CodingSchemeDesignator, ""));
                        codeValue.put("codeMeaning", code.getString(Tag.CodeMeaning, ""));
                        node.put("code", codeValue);
                    }
                    break;
                case "TEXT":
                    node.put("textValue", item.getString(Tag.TextValue, ""));
                    break;
                case "NUM":
                    node.put("numericValue", item.getString(Tag.NumericValue, ""));
                    break;
                case "IMAGE":
                case "COMPOSITE":
                    Sequence refSOPSeq = item.getSequence(Tag.ReferencedSOPSequence);
                    if (refSOPSeq != null && !refSOPSeq.isEmpty()) {
                        Attributes refSOP = refSOPSeq.get(0);
                        Map<String, String> ref = new LinkedHashMap<>();
                        ref.put("sopClassUID", refSOP.getString(Tag.ReferencedSOPClassUID, ""));
                        ref.put("sopInstanceUID", refSOP.getString(Tag.ReferencedSOPInstanceUID, ""));
                        node.put("referencedSOP", ref);
                    }
                    break;
            }

            // Recursively extract children
            Sequence childContentSeq = item.getSequence(Tag.ContentSequence);
            if (childContentSeq != null && !childContentSeq.isEmpty()) {
                node.put("children", extractContentTree(childContentSeq));
            }

            tree.add(node);
        }

        return tree;
    }

    private Map<String, Object> extractMADODescriptors(Sequence contentSeq) {
        Map<String, Object> descriptors = new LinkedHashMap<>();

        // This is a simplified extraction - full MADO descriptor extraction
        // would require walking the entire TID 1600 template structure
        List<String> descriptorTypes = new ArrayList<>();

        for (Attributes item : contentSeq) {
            Sequence conceptNameSeq = item.getSequence(Tag.ConceptNameCodeSequence);
            if (conceptNameSeq != null && !conceptNameSeq.isEmpty()) {
                Attributes concept = conceptNameSeq.get(0);
                String codeMeaning = concept.getString(Tag.CodeMeaning, "");
                if (codeMeaning != null && !codeMeaning.isEmpty()) {
                    descriptorTypes.add(codeMeaning);
                }
            }
        }

        descriptors.put("descriptorTypes", descriptorTypes);
        descriptors.put("count", descriptorTypes.size());

        return descriptors;
    }

    private List<Map<String, Object>> convertValidationMessages(List<ValidationResult.ValidationMessage> messages) {
        List<Map<String, Object>> result = new ArrayList<>();

        for (ValidationResult.ValidationMessage msg : messages) {
            Map<String, Object> msgMap = new LinkedHashMap<>();
            msgMap.put("severity", msg.getSeverity().toString());
            msgMap.put("message", msg.getMessage());
            msgMap.put("path", msg.getPath());
            result.add(msgMap);
        }

        return result;
    }
}

