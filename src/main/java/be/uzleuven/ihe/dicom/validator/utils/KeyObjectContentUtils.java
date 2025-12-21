package be.uzleuven.ihe.dicom.validator.utils;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import be.uzleuven.ihe.dicom.validator.validation.iod.AbstractIODValidator;
import be.uzleuven.ihe.dicom.validator.model.ValidationResult;
import be.uzleuven.ihe.dicom.constants.DicomConstants;

import java.util.HashSet;
import java.util.Set;

public class KeyObjectContentUtils {

    private static final Set<String> ALLOWED_KOS_VALUE_TYPES = new HashSet<>();

    static {
        // DICOM PS3.3 KOS TID 2010 allowed value types
        ALLOWED_KOS_VALUE_TYPES.add(DicomConstants.VALUE_TYPE_CONTAINER);
        ALLOWED_KOS_VALUE_TYPES.add(DicomConstants.VALUE_TYPE_TEXT);
        ALLOWED_KOS_VALUE_TYPES.add(DicomConstants.VALUE_TYPE_CODE);
        ALLOWED_KOS_VALUE_TYPES.add(DicomConstants.VALUE_TYPE_UIDREF);
        ALLOWED_KOS_VALUE_TYPES.add(DicomConstants.VALUE_TYPE_PNAME);
        ALLOWED_KOS_VALUE_TYPES.add(DicomConstants.VALUE_TYPE_COMPOSITE);
        ALLOWED_KOS_VALUE_TYPES.add(DicomConstants.VALUE_TYPE_IMAGE);
        ALLOWED_KOS_VALUE_TYPES.add(DicomConstants.VALUE_TYPE_WAVEFORM);
    }

    /**
     * KOS root content requirements:
     * - Root dataset is a CONTAINER
     * - ConceptNameCodeSequence identifies the document title
     * - ContentSequence present and contains at least one IMAGE/COMPOSITE/WAVEFORM reference item
     */
    public static void validateKOSRootContentConstraints(Attributes dataset, ValidationResult result) {
        String modulePath = DicomConstants.MODULE_SR_DOCUMENT_CONTENT;

        Sequence root = dataset.getSequence(Tag.ContentSequence);
        if (root == null || root.isEmpty()) {
            result.addError("ContentSequence is missing or empty; KOS must contain at least one referenced object", modulePath);
            return;
        }

        boolean hasReference = false;
        for (Attributes item : root) {
            hasReference |= containsAnyReferenceItem(item);
        }

        if (!hasReference) {
            result.addError("KOS ContentSequence has no IMAGE/COMPOSITE/WAVEFORM reference items (empty manifest)", modulePath);
        }

        // Validate Document Title Modifiers if applicable
        validateDocumentTitleModifiers(dataset, result, modulePath);

        // Validate Key Object Description cardinality (at most one)
        validateKeyObjectDescriptionCardinality(dataset, result, modulePath);
    }

    private static boolean containsAnyReferenceItem(Attributes item) {
        if (item == null) return false;
        String vt = item.getString(Tag.ValueType);
        if (DicomConstants.VALUE_TYPE_IMAGE.equals(vt) || DicomConstants.VALUE_TYPE_COMPOSITE.equals(vt) || DicomConstants.VALUE_TYPE_WAVEFORM.equals(vt)) {
            return true;
        }
        Sequence nested = item.getSequence(Tag.ContentSequence);
        if (nested != null) {
            for (Attributes child : nested) {
                if (containsAnyReferenceItem(child)) return true;
            }
        }
        return false;
    }

    public static void validateContentSequence(Attributes dataset, ValidationResult result, AbstractIODValidator ctx) {
        Sequence seq = dataset.getSequence(Tag.ContentSequence);
        if (seq == null) {
            return;
        }

        for (int i = 0; i < seq.size(); i++) {
            Attributes item = seq.get(i);
            String itemPath = ctx.buildPath(DicomConstants.MODULE_SR_DOCUMENT_CONTENT, "ContentSequence", i);

            validateContentItem(item, result, itemPath, ctx);
        }
    }

    private static void validateContentItem(Attributes item, ValidationResult result, String itemPath, AbstractIODValidator ctx) {
        // RelationshipType - Type 1
        ctx.checkRequiredAttribute(item, Tag.RelationshipType, "RelationshipType", result, itemPath);
        if (item.contains(Tag.RelationshipType)) {
            ctx.checkEnumeratedValue(item, Tag.RelationshipType, "RelationshipType",
                new String[]{
                    DicomConstants.RELATIONSHIP_CONTAINS,
                    DicomConstants.RELATIONSHIP_HAS_CONCEPT_MOD,
                    DicomConstants.RELATIONSHIP_HAS_OBS_CONTEXT,
                    DicomConstants.RELATIONSHIP_HAS_ACQ_CONTEXT,
                    DicomConstants.RELATIONSHIP_INFERRED_FROM,
                    DicomConstants.RELATIONSHIP_SELECTED_FROM
                },
                result, itemPath);
        }

        // ValueType - Type 1
        ctx.checkRequiredAttribute(item, Tag.ValueType, "ValueType", result, itemPath);
        String valueType = item.getString(Tag.ValueType);
        if (valueType != null) {
            if (!ALLOWED_KOS_VALUE_TYPES.contains(valueType)) {
                result.addError("Disallowed ValueType for KOS (TID 2010): " + valueType, itemPath);
            }
        }

        if (valueType != null) {
            switch (valueType) {
                case DicomConstants.VALUE_TYPE_CODE:
                    if (ctx.checkSequenceAttribute(item, Tag.ConceptCodeSequence, "ConceptCodeSequence", true, result, itemPath)) {
                        validateCodeSequenceSingleItem(item.getSequence(Tag.ConceptCodeSequence), result,
                            itemPath + " > ConceptCodeSequence[1]", ctx);
                    }
                    break;
                case DicomConstants.VALUE_TYPE_TEXT:
                    ctx.checkRequiredAttribute(item, Tag.TextValue, "TextValue", result, itemPath);
                    break;
                case DicomConstants.VALUE_TYPE_UIDREF:
                    ctx.checkRequiredAttribute(item, Tag.UID, "UID", result, itemPath);
                    if (item.contains(Tag.UID)) {
                        ctx.checkUID(item, Tag.UID, "UID", result, itemPath);
                    }
                    break;
                case DicomConstants.VALUE_TYPE_PNAME:
                    ctx.checkRequiredAttribute(item, Tag.PersonName, "PersonName", result, itemPath);
                    break;
                case DicomConstants.VALUE_TYPE_IMAGE:
                case DicomConstants.VALUE_TYPE_COMPOSITE:
                case DicomConstants.VALUE_TYPE_WAVEFORM:
                    // PurposeOfReferenceCodeSequence is forbidden in KOS TID 2010
                    if (item.contains(Tag.PurposeOfReferenceCodeSequence)) {
                        result.addError("PurposeOfReferenceCodeSequence (0040,A170) must not be present in KOS references", itemPath);
                    }

                    if (ctx.checkSequenceAttribute(item, Tag.ReferencedSOPSequence, "ReferencedSOPSequence", true, result, itemPath)) {
                        SRReferenceUtils.validateReferencedSOPSequence(item, result, itemPath, ctx);
                    }
                    break;
                case DicomConstants.VALUE_TYPE_CONTAINER:
                    if (!item.contains(Tag.ContentSequence)) {
                        result.addWarning("CONTAINER content item has no nested ContentSequence", itemPath);
                    }
                    break;
                default:
                    // structural validation only
                    break;
            }
        }

        // Recurse
        if (item.contains(Tag.ContentSequence)) {
            validateContentSequenceItem(item, result, itemPath, ctx);
        }
    }

    private static void validateCodeSequenceSingleItem(Sequence seq, ValidationResult result, String itemPath, AbstractIODValidator ctx) {
        if (seq == null || seq.isEmpty()) {
            result.addError("Code sequence is empty", itemPath);
            return;
        }
        Attributes codeItem = seq.get(0);
        ctx.checkRequiredAttribute(codeItem, Tag.CodeValue, "CodeValue", result, itemPath);
        ctx.checkRequiredAttribute(codeItem, Tag.CodingSchemeDesignator, "CodingSchemeDesignator", result, itemPath);
        ctx.checkRequiredAttribute(codeItem, Tag.CodeMeaning, "CodeMeaning", result, itemPath);
    }

    private static void validateContentSequenceItem(Attributes parent, ValidationResult result,
                                                    String parentPath, AbstractIODValidator ctx) {
        Sequence seq = parent.getSequence(Tag.ContentSequence);
        if (seq == null) {
            return;
        }

        for (int i = 0; i < seq.size(); i++) {
            Attributes item = seq.get(i);
            String itemPath = ctx.buildPath(parentPath, "ContentSequence", i);

            validateContentItem(item, result, itemPath, ctx);
        }
    }

    public static void validateConceptNameCodeSequence(Attributes dataset, ValidationResult result, AbstractIODValidator ctx) {
        Sequence seq = dataset.getSequence(Tag.ConceptNameCodeSequence);
        if (seq == null || seq.isEmpty()) {
            result.addError("ConceptNameCodeSequence is empty", DicomConstants.MODULE_SR_DOCUMENT_CONTENT);
            return;
        }

        Attributes item = seq.get(0);
        String itemPath = DicomConstants.MODULE_SR_DOCUMENT_CONTENT + " > ConceptNameCodeSequence[1]";

        // Code Value - Type 1
        ctx.checkRequiredAttribute(item, Tag.CodeValue, "CodeValue", result, itemPath);

        // Coding Scheme Designator - Type 1
        ctx.checkRequiredAttribute(item, Tag.CodingSchemeDesignator, "CodingSchemeDesignator",
            result, itemPath);

        // Code Meaning - Type 1
        ctx.checkRequiredAttribute(item, Tag.CodeMeaning, "CodeMeaning", result, itemPath);
    }

    public static void validateContentTemplateSequence(Attributes dataset, ValidationResult result, AbstractIODValidator ctx) {
        Sequence seq = dataset.getSequence(Tag.ContentTemplateSequence);
        if (seq == null || seq.isEmpty()) {
            result.addError("ContentTemplateSequence is empty", DicomConstants.MODULE_SR_DOCUMENT_CONTENT);
            return;
        }

        Attributes item = seq.get(0);
        String itemPath = DicomConstants.MODULE_SR_DOCUMENT_CONTENT + " > ContentTemplateSequence[1]";

        // Template Identifier - Type 1
        ctx.checkRequiredAttribute(item, Tag.TemplateIdentifier, "TemplateIdentifier", result, itemPath);

        // Mapping Resource - Type 1
        ctx.checkRequiredAttribute(item, Tag.MappingResource, "MappingResource", result, itemPath);
    }

    /**
     * Validate Document Title Modifiers per TID 2010.
     * Certain document titles require specific modifiers:
     * - "Rejected for Quality Reasons" (113001) or "Quality Issue" (113010) require modifiers from CID 7011
     * - "Best In Set" (113013) requires modifiers from CID 7012
     */
    private static void validateDocumentTitleModifiers(Attributes dataset, ValidationResult result, String modulePath) {
        // Get document title code
        Sequence titleSeq = dataset.getSequence(Tag.ConceptNameCodeSequence);
        if (titleSeq == null || titleSeq.isEmpty()) {
            return; // Already validated elsewhere
        }

        Attributes titleItem = titleSeq.get(0);
        String codeValue = titleItem.getString(Tag.CodeValue);

        if (codeValue == null) {
            return;
        }

        boolean requiresQualityModifier = DicomConstants.IOCM_REJECTED_QUALITY.equals(codeValue) || DicomConstants.CODE_QUALITY_ISSUE.equals(codeValue);
        boolean requiresBestInSetModifier = DicomConstants.CODE_BEST_IN_SET.equals(codeValue);

        if (!requiresQualityModifier && !requiresBestInSetModifier) {
            return; // No modifiers required
        }

        // Check ContentSequence for Document Title Modifier items
        Sequence contentSeq = dataset.getSequence(Tag.ContentSequence);
        if (contentSeq == null) {
            result.addError("Document Title (" + codeValue + ") requires Document Title Modifier but ContentSequence is missing", modulePath);
            return;
        }

        boolean foundModifier = false;
        for (Attributes contentItem : contentSeq) {
            String relationshipType = contentItem.getString(Tag.RelationshipType);
            String valueType = contentItem.getString(Tag.ValueType);

            if (DicomConstants.RELATIONSHIP_HAS_CONCEPT_MOD.equals(relationshipType) && DicomConstants.VALUE_TYPE_CODE.equals(valueType)) {
                Sequence conceptNameSeq = contentItem.getSequence(Tag.ConceptNameCodeSequence);
                if (conceptNameSeq != null && !conceptNameSeq.isEmpty()) {
                    Attributes conceptName = conceptNameSeq.get(0);
                    String conceptCode = conceptName.getString(Tag.CodeValue);

                    if (DicomConstants.CODE_DOCUMENT_TITLE_MODIFIER.equals(conceptCode)) { // Document Title Modifier
                        foundModifier = true;

                        // Validate the modifier code itself
                        Sequence conceptCodeSeq = contentItem.getSequence(Tag.ConceptCodeSequence);
                        if (conceptCodeSeq == null || conceptCodeSeq.isEmpty()) {
                            result.addError("Document Title Modifier found but ConceptCodeSequence is missing", modulePath);
                        }
                        // Note: Full validation of CID 7011/7012 codes would require a code dictionary
                        break;
                    }
                }
            }
        }

        if (!foundModifier) {
            if (requiresQualityModifier) {
                result.addError("Document Title 'Rejected for Quality Reasons' or 'Quality Issue' requires " +
                              "at least one Document Title Modifier (" + DicomConstants.CODE_DOCUMENT_TITLE_MODIFIER + ", DCM) with a code from CID 7011", modulePath);
            } else {
                result.addError("Document Title 'Best In Set' requires at least one Document Title Modifier " +
                              "(" + DicomConstants.CODE_DOCUMENT_TITLE_MODIFIER + ", DCM) with a code from CID 7012", modulePath);
            }
        }
    }

    /**
     * Validate Key Object Description cardinality.
     * Per TID 2010, at most one TEXT content item with concept name "Key Object Description" (113012, DCM)
     * may be present as a child of the root CONTAINER.
     */
    private static void validateKeyObjectDescriptionCardinality(Attributes dataset, ValidationResult result, String modulePath) {
        Sequence contentSeq = dataset.getSequence(Tag.ContentSequence);
        if (contentSeq == null) {
            return;
        }

        int descriptionCount = 0;
        for (Attributes contentItem : contentSeq) {
            String valueType = contentItem.getString(Tag.ValueType);

            if (DicomConstants.VALUE_TYPE_TEXT.equals(valueType)) {
                Sequence conceptNameSeq = contentItem.getSequence(Tag.ConceptNameCodeSequence);
                if (conceptNameSeq != null && !conceptNameSeq.isEmpty()) {
                    Attributes conceptName = conceptNameSeq.get(0);
                    String conceptCode = conceptName.getString(Tag.CodeValue);

                    if (DicomConstants.CODE_KEY_OBJECT_DESCRIPTION.equals(conceptCode)) { // Key Object Description
                        descriptionCount++;
                    }
                }
            }
        }

        if (descriptionCount > 1) {
            result.addError("Found " + descriptionCount + " Key Object Description items. " +
                          "TID 2010 allows at most one Key Object Description (" + DicomConstants.CODE_KEY_OBJECT_DESCRIPTION + ", DCM) text item.", modulePath);
        }
    }
}
