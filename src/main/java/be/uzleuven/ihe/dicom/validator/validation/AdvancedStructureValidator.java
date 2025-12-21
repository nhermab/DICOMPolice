package be.uzleuven.ihe.dicom.validator.validation;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import be.uzleuven.ihe.dicom.validator.model.ValidationResult;

import java.util.*;

/**
 * Advanced structure validation for DICOM objects.
 * Validates SOP Class UIDs, private attributes, empty sequences, and template identification.
 */
public class AdvancedStructureValidator {

    // Known DICOM SOP Classes (sample - extend as needed)
    private static final Map<String, String> KNOWN_SOP_CLASSES = new HashMap<>();

    // Transfer Syntax UIDs that are commonly confused with SOP Classes
    private static final Set<String> TRANSFER_SYNTAX_UIDS = new HashSet<>();

    // Special UIDs that are not SOP Classes

    static {
        // Storage SOP Classes
        KNOWN_SOP_CLASSES.put(UID.CTImageStorage, "CT Image Storage");
        KNOWN_SOP_CLASSES.put(UID.MRImageStorage, "MR Image Storage");
        KNOWN_SOP_CLASSES.put(UID.UltrasoundImageStorage, "Ultrasound Image Storage");
        KNOWN_SOP_CLASSES.put(UID.SecondaryCaptureImageStorage, "Secondary Capture Image Storage");
        KNOWN_SOP_CLASSES.put(UID.XRayAngiographicImageStorage, "X-Ray Angiographic Image Storage");
        KNOWN_SOP_CLASSES.put(UID.XRayRadiofluoroscopicImageStorage, "X-Ray Radiofluoroscopic Image Storage");
        KNOWN_SOP_CLASSES.put(UID.DigitalXRayImageStorageForPresentation, "Digital X-Ray Image Storage - For Presentation");
        KNOWN_SOP_CLASSES.put(UID.DigitalXRayImageStorageForProcessing, "Digital X-Ray Image Storage - For Processing");
        KNOWN_SOP_CLASSES.put(UID.KeyObjectSelectionDocumentStorage, "Key Object Selection Document Storage");
        KNOWN_SOP_CLASSES.put(UID.GrayscaleSoftcopyPresentationStateStorage, "Grayscale Softcopy Presentation State Storage");
        KNOWN_SOP_CLASSES.put(UID.EncapsulatedPDFStorage, "Encapsulated PDF Storage");
        KNOWN_SOP_CLASSES.put(UID.BasicTextSRStorage, "Basic Text SR Storage");
        KNOWN_SOP_CLASSES.put(UID.EnhancedSRStorage, "Enhanced SR Storage");
        KNOWN_SOP_CLASSES.put(UID.ComprehensiveSRStorage, "Comprehensive SR Storage");
        KNOWN_SOP_CLASSES.put(UID.Comprehensive3DSRStorage, "Comprehensive 3D SR Storage");

        // Transfer Syntaxes (NOT SOP Classes)
        TRANSFER_SYNTAX_UIDS.add(UID.ImplicitVRLittleEndian);
        TRANSFER_SYNTAX_UIDS.add(UID.ExplicitVRLittleEndian);
        TRANSFER_SYNTAX_UIDS.add(UID.ExplicitVRBigEndian);
        TRANSFER_SYNTAX_UIDS.add(UID.DeflatedExplicitVRLittleEndian);
        TRANSFER_SYNTAX_UIDS.add(UID.JPEGBaseline8Bit);
        TRANSFER_SYNTAX_UIDS.add(UID.JPEGExtended12Bit);
        TRANSFER_SYNTAX_UIDS.add(UID.JPEGLossless);
        TRANSFER_SYNTAX_UIDS.add(UID.JPEGLosslessSV1);
        TRANSFER_SYNTAX_UIDS.add(UID.JPEGLSLossless);
        TRANSFER_SYNTAX_UIDS.add(UID.JPEG2000);
        TRANSFER_SYNTAX_UIDS.add(UID.RLELossless);

        // Other special UIDs
    }

    /**
     * Validate Referenced SOP Class UID consistency and sanity checks.
     * Detect if Transfer Syntax UIDs are mistakenly used as SOP Class UIDs.
     */
    public static void validateReferencedSOPClasses(Attributes dataset, ValidationResult result, String path) {
        // Check in CurrentRequestedProcedureEvidenceSequence
        org.dcm4che3.data.Sequence evidenceSeq = dataset.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence);
        if (evidenceSeq != null) {
            for (Attributes study : evidenceSeq) {
                org.dcm4che3.data.Sequence seriesSeq = study.getSequence(Tag.ReferencedSeriesSequence);
                if (seriesSeq != null) {
                    for (Attributes series : seriesSeq) {
                        org.dcm4che3.data.Sequence sopSeq = series.getSequence(Tag.ReferencedSOPSequence);
                        if (sopSeq != null) {
                            int itemNum = 0;
                            for (Attributes sop : sopSeq) {
                                String sopClassUID = sop.getString(Tag.ReferencedSOPClassUID);
                                validateSOPClassUID(sopClassUID, "ReferencedSOPClassUID", result,
                                                   path + ">Evidence>ReferencedSOP[" + itemNum + "]");
                                itemNum++;
                            }
                        }
                    }
                }
            }
        }

        // Check in ContentSequence
        validateContentSequenceSOPClasses(dataset, result, path);
    }

    /**
     * Recursively validate SOP Classes in Content Sequence.
     */
    private static void validateContentSequenceSOPClasses(Attributes dataset, ValidationResult result, String path) {
        org.dcm4che3.data.Sequence contentSeq = dataset.getSequence(Tag.ContentSequence);
        if (contentSeq != null) {
            int itemNum = 0;
            for (Attributes item : contentSeq) {
                String itemPath = path + ">ContentSequence[" + itemNum + "]";

                // Check Referenced SOP Sequence
                org.dcm4che3.data.Sequence refSOPSeq = item.getSequence(Tag.ReferencedSOPSequence);
                if (refSOPSeq != null) {
                    int sopNum = 0;
                    for (Attributes sop : refSOPSeq) {
                        String sopClassUID = sop.getString(Tag.ReferencedSOPClassUID);
                        validateSOPClassUID(sopClassUID, "ReferencedSOPClassUID", result,
                                          itemPath + ">ReferencedSOP[" + sopNum + "]");
                        sopNum++;
                    }
                }

                // Recurse into nested content
                validateContentSequenceSOPClasses(item, result, itemPath);
                itemNum++;
            }
        }
    }

    /**
     * Validate a single SOP Class UID for common errors.
     */
    private static void validateSOPClassUID(String sopClassUID, String attributeName,
                                           ValidationResult result, String path) {
        if (sopClassUID == null || sopClassUID.isEmpty()) {
            return; // Already checked by other validators
        }

        // Check if it's a Transfer Syntax UID (common error)
        if (TRANSFER_SYNTAX_UIDS.contains(sopClassUID)) {
            result.addError("CRITICAL: " + attributeName + " contains a Transfer Syntax UID (" + sopClassUID +
                          ") instead of a SOP Class UID. This is a common copy-paste error where " +
                          "Transfer Syntax UID was used where SOP Class UID belongs.", path);
            return;
        }

        // Check if it's Verification SOP Class (unusual in references)
        if (UID.Verification.equals(sopClassUID)) {
            result.addWarning(attributeName + " references Verification SOP Class (1.2.840.10008.1.1). " +
                            "This is unusual in a KOS and might indicate a copy-paste error.", path);
            return;
        }

        // Check if it's a known SOP Class
        if (KNOWN_SOP_CLASSES.containsKey(sopClassUID)) {
            String sopClassName = KNOWN_SOP_CLASSES.get(sopClassUID);
            result.addInfo(attributeName + " references known SOP Class: " + sopClassName, path);
        } else {
            // Unknown SOP Class - might be valid but worth noting
            result.addWarning(attributeName + " references unknown or non-standard SOP Class UID: " +
                            sopClassUID + ". Verify this is a valid SOP Class UID.", path);
        }
    }

    /**
     * Validate Template Identification Sequence for TID 2010 compliance.
     * XDS-I.b implies the use of TID 2010.
     */
    public static void validateTemplateIdentification(Attributes dataset, ValidationResult result, String path) {
        org.dcm4che3.data.Sequence templateSeq = dataset.getSequence(Tag.ContentTemplateSequence);

        if (templateSeq == null || templateSeq.isEmpty()) {
            result.addWarning("ContentTemplateSequence (0040,A504) is missing. " +
                            "For XDS-I.b compliance, this should explicitly identify TID 2010.", path);
            return;
        }

        boolean foundTID2010 = false;
        for (Attributes item : templateSeq) {
            String templateID = item.getString(Tag.TemplateIdentifier);
            String mappingResource = item.getString(Tag.MappingResource);

            if (templateID != null && templateID.equals("2010")) {
                foundTID2010 = true;

                if (mappingResource == null || !mappingResource.equals("DCMR")) {
                    result.addError("ContentTemplateSequence has TemplateIdentifier=2010 but " +
                                  "MappingResource is not 'DCMR'. Expected MappingResource='DCMR'.", path);
                } else {
                    result.addInfo("ContentTemplateSequence correctly identifies TID 2010 (DCMR)", path);
                }
            }
        }

        if (!foundTID2010) {
            result.addWarning("ContentTemplateSequence does not contain TemplateIdentifier='2010'. " +
                            "XDS-I.b Key Object Selection should use TID 2010.", path);
        }
    }

    /**
     * Check for zero-length sequences where Type 1 or Type 2 sequences should have items.
     */
    public static void validateEmptySequences(Attributes dataset, ValidationResult result, String path) {
        // Type 1 sequences that must not be empty
        checkSequenceNotEmpty(dataset, Tag.CurrentRequestedProcedureEvidenceSequence,
                             "CurrentRequestedProcedureEvidenceSequence", "Type 1", result, path);

        // Check ConceptNameCodeSequence in root
        checkSequenceNotEmpty(dataset, Tag.ConceptNameCodeSequence,
                             "ConceptNameCodeSequence", "Type 1", result, path);

        // Check ContentTemplateSequence
        checkSequenceNotEmpty(dataset, Tag.ContentTemplateSequence,
                             "ContentTemplateSequence", "Type 1", result, path);

        // Recursively check ContentSequence items
        validateEmptySequencesInContent(dataset, result, path);
    }

    /**
     * Recursively check for empty sequences in ContentSequence.
     */
    private static void validateEmptySequencesInContent(Attributes dataset, ValidationResult result, String path) {
        org.dcm4che3.data.Sequence contentSeq = dataset.getSequence(Tag.ContentSequence);
        if (contentSeq != null) {
            int itemNum = 0;
            for (Attributes item : contentSeq) {
                String itemPath = path + ">ContentSequence[" + itemNum + "]";

                // Check Concept Name Code Sequence in content items
                checkSequenceNotEmpty(item, Tag.ConceptNameCodeSequence,
                                     "ConceptNameCodeSequence", "Type 1", result, itemPath);

                // Recurse
                validateEmptySequencesInContent(item, result, itemPath);
                itemNum++;
            }
        }
    }

    /**
     * Helper to check if a sequence is present but empty.
     */
    private static void checkSequenceNotEmpty(Attributes dataset, int tag, String name,
                                             String type, ValidationResult result, String path) {
        if (dataset.contains(tag)) {
            org.dcm4che3.data.Sequence seq = dataset.getSequence(tag);
            if (seq != null && seq.isEmpty()) {
                result.addError(name + " (" + tagString(tag) + ") is present but has zero length. " +
                              type + " sequences must contain at least one item when present.", path);
            }
        }
    }

    /**
     * Validate private attributes (odd group numbers).
     * Clean KOS for XDS sharing should ideally be free of private tags.
     */
    public static void validatePrivateAttributes(Attributes dataset, ValidationResult result, String path) {
        Map<Integer, List<Integer>> privateGroupsFound = new HashMap<>();
        Map<Integer, Boolean> hasCreatorTag = new HashMap<>();

        scanPrivateAttributes(dataset, privateGroupsFound, hasCreatorTag);

        if (privateGroupsFound.isEmpty()) {
            result.addInfo("No private attributes found - clean for XDS sharing", path);
            return;
        }

        // Report on private attributes found
        for (Map.Entry<Integer, List<Integer>> entry : privateGroupsFound.entrySet()) {
            int group = entry.getKey();
            List<Integer> elements = entry.getValue();

            result.addWarning(String.format("Private attributes found in group %04X (%d elements). " +
                            "XDS-I.b KOS objects should ideally be free of private tags for broad interoperability.",
                            group, elements.size()), path);

            // Check for Private Creator Data Element
            if (!hasCreatorTag.getOrDefault(group, false)) {
                result.addError(String.format("Private group %04X has private elements but no " +
                              "Private Creator Data Element (e.g., %04X,0010). " +
                              "The file structure may be corrupt.", group, group), path);
            }
        }
    }

    /**
     * Recursively scan for private attributes.
     */
    private static void scanPrivateAttributes(Attributes dataset, Map<Integer, List<Integer>> privateGroups,
                                             Map<Integer, Boolean> hasCreator) {
        int[] tags = dataset.tags();
        for (int tag : tags) {
            int group = (tag >>> 16) & 0xFFFF;
            int element = tag & 0xFFFF;

            // Check if odd group (private)
            if ((group & 1) == 1) {
                privateGroups.computeIfAbsent(group, k -> new ArrayList<>()).add(element);

                // Check if this is a Private Creator Data Element (gggg,0010-00FF)
                if (element >= 0x0010 && element <= 0x00FF) {
                    hasCreator.put(group, true);
                }
            }

            // Recurse into sequences
            VR vr = dataset.getVR(tag);
            if (vr == VR.SQ) {
                org.dcm4che3.data.Sequence seq = dataset.getSequence(tag);
                if (seq != null) {
                    for (Attributes item : seq) {
                        scanPrivateAttributes(item, privateGroups, hasCreator);
                    }
                }
            }
        }
    }

    /**
     * Format tag as string (gggg,eeee).
     */
    private static String tagString(int tag) {
        int group = (tag >>> 16) & 0xFFFF;
        int element = tag & 0xFFFF;
        return String.format("(%04X,%04X)", group, element);
    }
}

