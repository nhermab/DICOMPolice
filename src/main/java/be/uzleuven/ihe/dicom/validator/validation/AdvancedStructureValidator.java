package be.uzleuven.ihe.dicom.validator.validation;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import be.uzleuven.ihe.dicom.validator.model.ValidationResult;
import be.uzleuven.ihe.dicom.constants.SopClassLists;
import be.uzleuven.ihe.dicom.constants.TransferSyntaxUIDs;

import java.util.*;
import java.util.stream.IntStream;

/**
 * Advanced structure validation for DICOM objects.
 * Validates SOP Class UIDs, private attributes, empty sequences, and template identification.
 */
public class AdvancedStructureValidator {

    /**
     * Validate Referenced SOP Class UID consistency and sanity checks.
     * Detect if Transfer Syntax UIDs are mistakenly used as SOP Class UIDs.
     */
    public static void validateReferencedSOPClasses(Attributes dataset, ValidationResult result, String path) {
        validateEvidenceSequenceSOPClasses(dataset, result, path);
        validateContentSequenceSOPClasses(dataset, result, path);
    }

    /**
     * Validate SOP Class UIDs in Evidence Sequence.
     */
    private static void validateEvidenceSequenceSOPClasses(Attributes dataset, ValidationResult result, String path) {
        org.dcm4che3.data.Sequence evidenceSeq = dataset.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence);
        if (evidenceSeq == null) {
            return;
        }

        int studyNum = 0;
        for (Attributes study : evidenceSeq) {
            String studyPath = path + ">Evidence[" + studyNum + "]";
            validateStudySeriesSOPClasses(study, result, studyPath);
            studyNum++;
        }
    }

    /**
     * Validate SOP Class UIDs in a study's series.
     */
    private static void validateStudySeriesSOPClasses(Attributes study, ValidationResult result, String studyPath) {
        org.dcm4che3.data.Sequence seriesSeq = study.getSequence(Tag.ReferencedSeriesSequence);
        if (seriesSeq == null) {
            return;
        }

        int seriesNum = 0;
        for (Attributes series : seriesSeq) {
            String seriesPath = studyPath + ">Series[" + seriesNum + "]";
            validateSeriesSOPClasses(series, result, seriesPath);
            seriesNum++;
        }
    }

    /**
     * Validate SOP Class UIDs in a series.
     */
    private static void validateSeriesSOPClasses(Attributes series, ValidationResult result, String seriesPath) {
        org.dcm4che3.data.Sequence sopSeq = series.getSequence(Tag.ReferencedSOPSequence);
        if (sopSeq == null) {
            return;
        }

        int sopNum = 0;
        for (Attributes sop : sopSeq) {
            String sopPath = seriesPath + ">ReferencedSOP[" + sopNum + "]";
            String sopClassUID = sop.getString(Tag.ReferencedSOPClassUID);
            validateSOPClassUID(sopClassUID, "ReferencedSOPClassUID", result, sopPath);
            sopNum++;
        }
    }

    /**
     * Recursively validate SOP Classes in Content Sequence.
     */
    private static void validateContentSequenceSOPClasses(Attributes dataset, ValidationResult result, String path) {
        org.dcm4che3.data.Sequence contentSeq = dataset.getSequence(Tag.ContentSequence);
        if (contentSeq == null) {
            return;
        }

        int itemNum = 0;
        for (Attributes item : contentSeq) {
            String itemPath = path + ">ContentSequence[" + itemNum + "]";
            validateContentItemSOPClasses(item, result, itemPath);
            itemNum++;
        }
    }

    /**
     * Validate SOP Classes within a single content item and recurse into nested content.
     */
    private static void validateContentItemSOPClasses(Attributes item, ValidationResult result, String itemPath) {
        validateItemReferencedSOPSequence(item, result, itemPath);
        validateContentSequenceSOPClasses(item, result, itemPath);
    }

    /**
     * Validate referenced SOPs within a content item.
     */
    private static void validateItemReferencedSOPSequence(Attributes item, ValidationResult result, String itemPath) {
        org.dcm4che3.data.Sequence refSOPSeq = item.getSequence(Tag.ReferencedSOPSequence);
        if (refSOPSeq == null) {
            return;
        }

        int sopNum = 0;
        for (Attributes sop : refSOPSeq) {
            String sopPath = itemPath + ">ReferencedSOP[" + sopNum + "]";
            String sopClassUID = sop.getString(Tag.ReferencedSOPClassUID);
            validateSOPClassUID(sopClassUID, "ReferencedSOPClassUID", result, sopPath);
            sopNum++;
        }
    }


    /**
     * Validate a single SOP Class UID for common errors.
     */
    private static void validateSOPClassUID(String sopClassUID, String attributeName,
                                           ValidationResult result, String path) {
        if (isNullOrEmpty(sopClassUID)) {
            return;
        }

        if (isTransferSyntaxUID(sopClassUID, attributeName, result, path)) {
            return;
        }

        if (isVerificationSOPClass(sopClassUID, attributeName, result, path)) {
            return;
        }

        reportKnownOrUnknownSOPClass(sopClassUID, attributeName, result, path);
    }

    /**
     * Check if the string is null or empty.
     */
    private static boolean isNullOrEmpty(String value) {
        return value == null || value.isEmpty();
    }

    /**
     * Check if UID is a Transfer Syntax (common error) and report it.
     */
    private static boolean isTransferSyntaxUID(String uid, String attributeName,
                                               ValidationResult result, String path) {
        if (!TransferSyntaxUIDs.COMMON_TRANSFER_SYNTAX_UIDS.contains(uid)) {
            return false;
        }

        result.addError("CRITICAL: " + attributeName + " contains a Transfer Syntax UID (" + uid +
                      ") instead of a SOP Class UID. This is a common copy-paste error where " +
                      "Transfer Syntax UID was used where SOP Class UID belongs.", path);
        return true;
    }

    /**
     * Check if UID is Verification SOP Class (unusual) and report it.
     */
    private static boolean isVerificationSOPClass(String uid, String attributeName,
                                                  ValidationResult result, String path) {
        if (!UID.Verification.equals(uid)) {
            return false;
        }

        result.addWarning(attributeName + " references Verification SOP Class (1.2.840.10008.1.1). " +
                        "This is unusual in a KOS and might indicate a copy-paste error.", path);
        return true;
    }

    /**
     * Report whether SOP Class UID is known or unknown.
     */
    private static void reportKnownOrUnknownSOPClass(String sopClassUID, String attributeName,
                                                      ValidationResult result, String path) {
        if (SopClassLists.KNOWN_SOP_CLASSES.containsKey(sopClassUID)) {
            String sopClassName = SopClassLists.KNOWN_SOP_CLASSES.get(sopClassUID);
            result.addInfo(attributeName + " references known SOP Class: " + sopClassName, path);
        } else {
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

        if (!hasValidTemplateSequence(templateSeq, result, path)) {
            return;
        }

        boolean foundTID2010 = validateTemplateItems(templateSeq, result, path);

        if (!foundTID2010) {
            result.addWarning("ContentTemplateSequence does not contain TemplateIdentifier='2010'. " +
                            "XDS-I.b Key Object Selection should use TID 2010.", path);
        }
    }

    /**
     * Check if template sequence exists and is not empty.
     */
    private static boolean hasValidTemplateSequence(org.dcm4che3.data.Sequence templateSeq,
                                                     ValidationResult result, String path) {
        if (templateSeq != null && !templateSeq.isEmpty()) {
            return true;
        }

        result.addWarning("ContentTemplateSequence (0040,A504) is missing. " +
                        "For XDS-I.b compliance, this should explicitly identify TID 2010.", path);
        return false;
    }

    /**
     * Validate each template item and return true if TID 2010 was found.
     */
    private static boolean validateTemplateItems(org.dcm4che3.data.Sequence templateSeq,
                                                  ValidationResult result, String path) {
        boolean foundTID2010 = false;
        for (Attributes item : templateSeq) {
            if (isTID2010(item)) {
                foundTID2010 = true;
                validateTID2010Mapping(item, result, path);
            }
        }
        return foundTID2010;
    }

    /**
     * Check if item represents TID 2010.
     */
    private static boolean isTID2010(Attributes item) {
        String templateID = item.getString(Tag.TemplateIdentifier);
        return templateID != null && templateID.equals("2010");
    }

    /**
     * Validate that TID 2010 has correct mapping resource.
     */
    private static void validateTID2010Mapping(Attributes item, ValidationResult result, String path) {
        String mappingResource = item.getString(Tag.MappingResource);
        if (mappingResource == null || !mappingResource.equals("DCMR")) {
            result.addError("ContentTemplateSequence has TemplateIdentifier=2010 but " +
                          "MappingResource is not 'DCMR'. Expected MappingResource='DCMR'.", path);
        } else {
            result.addInfo("ContentTemplateSequence correctly identifies TID 2010 (DCMR)", path);
        }
    }

    /**
     * Check for zero-length sequences where Type 1 or Type 2 sequences should have items.
     */
    public static void validateEmptySequences(Attributes dataset, ValidationResult result, String path) {
        validateRootRequiredSequences(dataset, result, path);
        validateEmptySequencesInContent(dataset, result, path);
    }

    /**
     * Validate Type 1 sequences at root level that must not be empty.
     */
    private static void validateRootRequiredSequences(Attributes dataset, ValidationResult result, String path) {
        checkSequenceNotEmpty(dataset, Tag.CurrentRequestedProcedureEvidenceSequence,
                             "CurrentRequestedProcedureEvidenceSequence", "Type 1", result, path);
        checkSequenceNotEmpty(dataset, Tag.ConceptNameCodeSequence,
                             "ConceptNameCodeSequence", "Type 1", result, path);
        checkSequenceNotEmpty(dataset, Tag.ContentTemplateSequence,
                             "ContentTemplateSequence", "Type 1", result, path);
    }

    /**
     * Recursively check for empty sequences in ContentSequence.
     */
    private static void validateEmptySequencesInContent(Attributes dataset, ValidationResult result, String path) {
        org.dcm4che3.data.Sequence contentSeq = dataset.getSequence(Tag.ContentSequence);
        if (contentSeq == null) {
            return;
        }

        int itemNum = 0;
        for (Attributes item : contentSeq) {
            String itemPath = path + ">ContentSequence[" + itemNum + "]";
            validateContentItemEmptySequences(item, result, itemPath);
            itemNum++;
        }
    }

    /**
     * Validate empty sequences within a content item.
     */
    private static void validateContentItemEmptySequences(Attributes item, ValidationResult result, String itemPath) {
        checkSequenceNotEmpty(item, Tag.ConceptNameCodeSequence,
                             "ConceptNameCodeSequence", "Type 1", result, itemPath);
        validateEmptySequencesInContent(item, result, itemPath);
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

        reportPrivateAttributesFound(privateGroupsFound, hasCreatorTag, result, path);
    }

    /**
     * Report all private attributes found and validation issues.
     */
    private static void reportPrivateAttributesFound(Map<Integer, List<Integer>> privateGroupsFound,
                                                      Map<Integer, Boolean> hasCreatorTag,
                                                      ValidationResult result, String path) {
        for (Map.Entry<Integer, List<Integer>> entry : privateGroupsFound.entrySet()) {
            int group = entry.getKey();
            List<Integer> elements = entry.getValue();
            reportPrivateGroup(group, elements, hasCreatorTag, result, path);
        }
    }

    /**
     * Report a single private group and its issues.
     */
    private static void reportPrivateGroup(int group, List<Integer> elements,
                                          Map<Integer, Boolean> hasCreatorTag,
                                          ValidationResult result, String path) {
        result.addWarning(String.format("Private attributes found in group %04X (%d elements). " +
                        "XDS-I.b KOS objects should ideally be free of private tags for broad interoperability.",
                        group, elements.size()), path);

        if (!hasCreatorTag.getOrDefault(group, false)) {
            result.addError(String.format("Private group %04X has private elements but no " +
                          "Private Creator Data Element (e.g., %04X,0010). " +
                          "The file structure may be corrupt.", group, group), path);
        }
    }

    /**
     * Recursively scan for private attributes.
     */
    private static void scanPrivateAttributes(Attributes dataset, Map<Integer, List<Integer>> privateGroups,
                                             Map<Integer, Boolean> hasCreator) {
        int[] tags = dataset.tags();
        for (int tag : tags) {
            processTag(tag, dataset, privateGroups, hasCreator);
        }
    }

    /**
     * Process a single tag for private attributes and recurse into sequences.
     */
    private static void processTag(int tag, Attributes dataset, Map<Integer, List<Integer>> privateGroups,
                                  Map<Integer, Boolean> hasCreator) {
        int group = (tag >>> 16) & 0xFFFF;
        int element = tag & 0xFFFF;

        if (isPrivateTag(group)) {
            recordPrivateTag(group, element, privateGroups, hasCreator);
        }

        if (isSequenceTag(dataset, tag)) {
            recurseIntoSequence(tag, dataset, privateGroups, hasCreator);
        }
    }

    /**
     * Check if a group is private (odd group number).
     */
    private static boolean isPrivateTag(int group) {
        return (group & 1) == 1;
    }

    /**
     * Record a private tag and mark creator if applicable.
     */
    private static void recordPrivateTag(int group, int element, Map<Integer, List<Integer>> privateGroups,
                                        Map<Integer, Boolean> hasCreator) {
        privateGroups.computeIfAbsent(group, k -> new ArrayList<>()).add(element);

        if (isPrivateCreatorDataElement(element)) {
            hasCreator.put(group, true);
        }
    }

    /**
     * Check if element is a Private Creator Data Element (gggg,0010-00FF).
     */
    private static boolean isPrivateCreatorDataElement(int element) {
        return element >= 0x0010 && element <= 0x00FF;
    }

    /**
     * Check if tag is a sequence.
     */
    private static boolean isSequenceTag(Attributes dataset, int tag) {
        VR vr = dataset.getVR(tag);
        return vr == VR.SQ;
    }

    /**
     * Recurse into sequence items.
     */
    private static void recurseIntoSequence(int tag, Attributes dataset, Map<Integer, List<Integer>> privateGroups,
                                           Map<Integer, Boolean> hasCreator) {
        org.dcm4che3.data.Sequence seq = dataset.getSequence(tag);
        if (seq != null) {
            for (Attributes item : seq) {
                scanPrivateAttributes(item, privateGroups, hasCreator);
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
