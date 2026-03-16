package be.uzleuven.ihe.dicom.validator.validation.tid1600;

import be.uzleuven.ihe.dicom.constants.CodeConstants;
import be.uzleuven.ihe.dicom.constants.DicomConstants;
import be.uzleuven.ihe.dicom.constants.ValidationMessages;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import be.uzleuven.ihe.dicom.validator.model.ValidationResult;
import be.uzleuven.ihe.dicom.validator.utils.SRContentTreeUtils;

import static be.uzleuven.ihe.dicom.constants.CodeConstants.*;

/**
 * Validates the TID 1600 "Image Library" container and its nested groups/entries.
 *
 * <p>Per DICOM CP-2595 / IHE MADO Trial Implementation:</p>
 * <ul>
 *   <li>TID 1600: Image Library (study-level context, HAS ACQ CONTEXT children)</li>
 *   <li>TID 1602: Image Library Group (series-level, CONTAINS children of Image Library)</li>
 *   <li>TID 1601/1602: Image Library Entry (instance-level, IMAGE/COMPOSITE/WAVEFORM)</li>
 *   <li>TID 16XX: KOS Descriptors (when referenced instance is a KOS)</li>
 * </ul>
 *
 * <p>MADOTEMP codes (MADOTEMP001-009) use scheme designator '99IHE'.
 * Standard DCM codes (121139, 112002, 113607, 113609, 121140, 123014, 121144, 113012)
 * continue to use 'DCM'.</p>
 */
public final class TID1600ImageLibraryValidator {

    private TID1600ImageLibraryValidator() {
    }

    public static void validateImageLibraryContainer(Attributes container, ValidationResult result,
                                                     String path, boolean verbose) {
        Sequence contentSeq = container.getSequence(Tag.ContentSequence);
        if (contentSeq == null || contentSeq.isEmpty()) {
            result.addError(ValidationMessages.TID1600_CONTAINER_NO_CONTENT, path);
            return;
        }

        int groupCount = 0;
        int entryCount = 0;

        for (int i = 0; i < contentSeq.size(); i++) {
            Attributes item = contentSeq.get(i);
            String valueType = item.getString(Tag.ValueType);

            if ("CONTAINER".equals(valueType)) {
                // Only validate as a group if it is explicitly an Image Library Group container.
                if (isImageLibraryGroupContainer(item)) {
                    groupCount++;
                    validateImageLibraryGroup(item, result, path + ".Group[" + i + "]", verbose);
                } else {
                    // Other containers at this level are allowed by extensions (e.g., KOS descriptors, context blocks).
                    result.addInfo("TID 1600 Image Library: non-group CONTAINER encountered at top level; skipping group validation.",
                            path + "[" + i + "]");
                }
            } else if ("IMAGE".equals(valueType) || "COMPOSITE".equals(valueType) || "WAVEFORM".equals(valueType)) {
                // Top-level IMAGE/COMPOSITE/WAVEFORM items in the Image Library container are entries.
                entryCount++;
                validateImageLibraryEntry(item, result, path + ".Entry[" + i + "]", verbose);
                checkForKeyImageFlagging(item, result, path + ".Entry[" + i + "]", verbose);
            } else {
                // TID 1600 allows additional content items at the top level (e.g., Acquisition Context).
                // Per CP-2595 and the MADO profile, this can be CODE/UIDREF/TEXT/NUM items such as
                // Modality, Target Region, Number of Study Related Series, etc.
                result.addInfo("TID 1600 Image Library: top-level ValueType '" + valueType +
                                "' encountered (allowed for Acquisition Context / extensions).",
                        path + "[" + i + "]");
            }
        }

        if (groupCount == 0 && entryCount == 0) {
            result.addError(ValidationMessages.TID1600_IMAGE_LIBRARY_EMPTY, path);
        }

        if (verbose && groupCount > 0) {
            result.addInfo("Found " + groupCount + " Image Library Group(s) in TID 1600 hierarchy", path);
        }
    }

    private static boolean isImageLibraryGroupContainer(Attributes container) {
        if (container == null) {
            return false;
        }
        if (!"CONTAINER".equals(container.getString(Tag.ValueType))) {
            return false;
        }

        // TID 1600 group container: (126200, DCM, "Image Library Group")
        return SRContentTreeUtils.isContainerWithConcept(container, CodeConstants.CODE_IMAGE_LIBRARY_GROUP, CodeConstants.SCHEME_DCM);
    }

    // ========================================================================
    // TID 1602: Image Library Group (Series-Level Context)
    // ========================================================================

    /**
     * Validates an Image Library Group container (TID 1602).
     *
     * Per CP-2595 / MADO:
     * <pre>
     *   Image Library Group (CONTAINER, 126200, DCM)
     *     ├── HAS ACQ CONTEXT -> Modality (121139, DCM) R+
     *     ├── HAS ACQ CONTEXT -> Series Date (MADOTEMP003, 99IHE) RC+
     *     ├── HAS ACQ CONTEXT -> Series Time (MADOTEMP004, 99IHE) RC+
     *     ├── HAS ACQ CONTEXT -> Series Description (MADOTEMP002, 99IHE) RC+
     *     ├── HAS ACQ CONTEXT -> Series Number (113607, DCM) RC+
     *     ├── HAS ACQ CONTEXT -> Series Instance UID (112002, DCM) R+
     *     ├── HAS ACQ CONTEXT -> Target Region (123014, DCM) RC+
     *     ├── HAS ACQ CONTEXT -> Number of Series Related Instances (MADOTEMP007, 99IHE) R+
     *     └── CONTAINS -> IMAGE/COMPOSITE/WAVEFORM (instance entries)
     * </pre>
     */
    private static void validateImageLibraryGroup(Attributes group, ValidationResult result,
                                                  String path, boolean verbose) {
        Sequence contentSeq = group.getSequence(Tag.ContentSequence);
        if (contentSeq == null || contentSeq.isEmpty()) {
            result.addError(ValidationMessages.TID1600_GROUP_NO_CONTENT, path);
            return;
        }

        // Series-level metadata flags
        boolean hasModality = false;
        boolean hasSeriesDate = false;
        boolean hasSeriesTime = false;
        boolean hasSeriesDescription = false;
        boolean hasSeriesNumber = false;
        boolean hasSeriesUID = false;
        boolean hasTargetRegion = false;
        boolean hasNumberOfSeriesRelatedInstances = false;

        // Instance-level tracking
        int imageEntryCount = 0;

        for (int i = 0; i < contentSeq.size(); i++) {
            Attributes item = contentSeq.get(i);
            Attributes concept = SRContentTreeUtils.firstItem(item.getSequence(Tag.ConceptNameCodeSequence));

            if (concept != null) {
                String codeValue = concept.getString(Tag.CodeValue);
                String codingScheme = concept.getString(Tag.CodingSchemeDesignator);

                // Check for deprecated ddd codes
                if (TID1600Rules.isDeprecatedDddCode(codeValue)) {
                    result.addWarning(String.format(ValidationMessages.MADO_DEPRECATED_DDD_CODE,
                            codeValue, codingScheme), path);
                }

                if (CODE_MODALITY.equals(codeValue)) {
                    hasModality = true;
                } else if (CODE_SERIES_DATE.equals(codeValue)) {
                    hasSeriesDate = true;
                    TID1600Rules.validateMADOTEMPScheme(concept, CODE_SERIES_DATE, result, path);
                } else if (CODE_SERIES_TIME.equals(codeValue)) {
                    hasSeriesTime = true;
                    TID1600Rules.validateMADOTEMPScheme(concept, CODE_SERIES_TIME, result, path);
                } else if (CODE_SERIES_DESCRIPTION.equals(codeValue)) {
                    hasSeriesDescription = true;
                    TID1600Rules.validateMADOTEMPScheme(concept, CODE_SERIES_DESCRIPTION, result, path);
                } else if (CODE_SERIES_NUMBER.equals(codeValue)) {
                    hasSeriesNumber = true;
                    TID1600Rules.validateSeriesNumberConsistency(item, result, path);
                } else if (CODE_SERIES_INSTANCE_UID.equals(codeValue)) {
                    hasSeriesUID = true;
                } else if (CODE_TARGET_REGION.equals(codeValue)) {
                    hasTargetRegion = true;
                    TID1600Rules.validateAnatomicRegion(item, result, path);
                } else if (CODE_NUM_SERIES_RELATED_INSTANCES.equals(codeValue)) {
                    hasNumberOfSeriesRelatedInstances = true;
                    TID1600Rules.validateMADOTEMPScheme(concept, CODE_NUM_SERIES_RELATED_INSTANCES, result, path);
                    // Validate UCUM unit is {instances}
                    TID1600Rules.validateUCUMUnit(item, "{instances}", "Number of Series Related Instances", result, path);
                }
            }

            String valueType = item.getString(Tag.ValueType);
            if ("IMAGE".equals(valueType) || "COMPOSITE".equals(valueType) || "WAVEFORM".equals(valueType)) {
                imageEntryCount++;
                validateImageLibraryEntry(item, result, path + ".Entry[" + imageEntryCount + "]", verbose);
            }
        }

        // Report missing required series-level metadata (R+)
        if (!hasModality) {
            result.addError(ValidationMessages.TID1600_GROUP_MISSING_MODALITY, path);
        }
        if (!hasSeriesUID) {
            result.addError(ValidationMessages.TID1600_GROUP_MISSING_SERIES_UID, path);
        }
        if (!hasNumberOfSeriesRelatedInstances) {
            result.addError(ValidationMessages.TID1600_GROUP_MISSING_SERIES_RELATED_INSTANCES, path);
        }

        // Report missing conditional series-level metadata (RC+)
        if (!hasSeriesDate) {
            result.addWarning(ValidationMessages.TID1600_GROUP_MISSING_SERIES_DATE, path);
        }
        if (!hasSeriesTime) {
            result.addWarning(ValidationMessages.TID1600_GROUP_MISSING_SERIES_TIME, path);
        }
        if (!hasSeriesDescription) {
            result.addWarning(ValidationMessages.TID1600_GROUP_MISSING_SERIES_DESCRIPTION, path);
        }
        if (!hasSeriesNumber) {
            result.addWarning(ValidationMessages.TID1600_GROUP_MISSING_SERIES_NUMBER, path);
        }
        if (!hasTargetRegion) {
            result.addWarning(ValidationMessages.TID1602_GROUP_MISSING_TARGET_REGION, path);
        }

        // Validate instance-level metadata presence
        if (imageEntryCount == 0) {
            result.addWarning("TID 1602 Image Library Group has no IMAGE/COMPOSITE/WAVEFORM entries. " +
                            "Expected at least one Image Library Entry per instance in the series.", path);
        }
    }

    // ========================================================================
    // TID 1601/1602: Image Library Entry (Instance-Level Context)
    // ========================================================================

    /**
     * Validates an IMAGE/COMPOSITE/WAVEFORM entry in the Image Library.
     *
     * Per CP-2595 / MADO:
     * <pre>
     *   IMAGE/COMPOSITE/WAVEFORM (instance reference)
     *     ├── ReferencedSOPSequence (exactly 1 item)
     *     ├── HAS ACQ CONTEXT -> Number of Frames (121140, DCM) RC+
     *     ├── HAS ACQ CONTEXT -> Instance Number (113609, DCM) RC+
     *     └── [if KOS] -> TID 16XX KOS Descriptors
     * </pre>
     */
    private static void validateImageLibraryEntry(Attributes entry, ValidationResult result,
                                                  String path, boolean verbose) {
        // Check value type
        String valueType = entry.getString(Tag.ValueType);
        if ("COMPOSITE".equals(valueType)) {
            result.addWarning("TID 1601 Image Library Entry uses ValueType 'COMPOSITE'. " +
                            "MADO profile recommends 'IMAGE' for image references (COMPOSITE is generic KOS style).", path);
        }

        Sequence refSOPSeq = entry.getSequence(Tag.ReferencedSOPSequence);
        if (refSOPSeq == null || refSOPSeq.isEmpty()) {
            result.addError(ValidationMessages.TID1600_ENTRY_NO_REFERENCED_SOP, path);
            return;
        }
        if (refSOPSeq.size() != 1) {
            result.addError(String.format(ValidationMessages.TID1600_ENTRY_REFSOPMULTIPLE, refSOPSeq.size()), path);
        }

        Attributes refSOP = SRContentTreeUtils.firstItem(refSOPSeq);
        String sopClassUID = refSOP.getString(Tag.ReferencedSOPClassUID);
        String sopInstanceUID = refSOP.getString(Tag.ReferencedSOPInstanceUID);

        if (sopClassUID == null || sopClassUID.trim().isEmpty()) {
            result.addError(ValidationMessages.TID1600_ENTRY_MISSING_SOP_CLASS_UID, path);
        }
        if (sopInstanceUID == null || sopInstanceUID.trim().isEmpty()) {
            result.addError(ValidationMessages.TID1600_ENTRY_MISSING_SOP_INSTANCE_UID, path);
        }

        // Validate nested instance-level content
        Sequence contentSeq = entry.getSequence(Tag.ContentSequence);
        if (contentSeq != null && !contentSeq.isEmpty()) {
            validateIMAGENestedContent(contentSeq, sopClassUID, result, path, verbose);
        }
        // Note: We do NOT report an error for missing ContentSequence - this is the CORRECT behavior per TID 1601
    }

    // ========================================================================
    // Instance-Level Nested Content Validation
    // ========================================================================

    /**
     * Validates nested content inside IMAGE items (instance-level metadata).
     *
     * Per CP-2595:
     * - Number of Frames (121140, DCM) - RC+ if SOP Class is multiframe
     * - Instance Number (113609, DCM) - RC+ if present in SOP Instance
     * - If the referenced instance is a KOS, TID 16XX descriptors apply
     */
    private static void validateIMAGENestedContent(Sequence contentSeq, String sopClassUID,
                                                   ValidationResult result, String path, boolean verbose) {
        boolean hasNumberOfFrames = false;
        boolean hasInstanceNumber = false;

        for (Attributes item : contentSeq) {
            Attributes concept = SRContentTreeUtils.firstItem(item.getSequence(Tag.ConceptNameCodeSequence));
            if (concept != null) {
                String codeValue = concept.getString(Tag.CodeValue);

                // Check for deprecated ddd codes
                if (TID1600Rules.isDeprecatedDddCode(codeValue)) {
                    result.addWarning(String.format(ValidationMessages.MADO_DEPRECATED_DDD_CODE,
                            codeValue, concept.getString(Tag.CodingSchemeDesignator)), path);
                }

                if (CODE_NUMBER_OF_FRAMES.equals(codeValue)) {
                    hasNumberOfFrames = true;
                } else if (CODE_INSTANCE_NUMBER.equals(codeValue)) {
                    hasInstanceNumber = true;

                    String vt = item.getString(Tag.ValueType);
                    if (vt != null && !(DicomConstants.VALUE_TYPE_TEXT.equals(vt) || "UT".equals(vt))) {
                        result.addError(String.format(ValidationMessages.TID1600_ENTRY_INSTANCE_NUMBER_WRONG_VT, vt), path);
                    }
                } else if (CODE_KOS_DOCUMENT_TITLE.equals(codeValue)) {
                    // TID 16XX: KOS descriptor - Document Title (121144, DCM)
                    validateKOSDescriptors(item, contentSeq, result, path);
                }
            }
        }

        // validate the nested metadata
        if (!hasInstanceNumber && verbose) {
            result.addInfo("IMAGE structure: Instance Number (113609, DCM) not found in nested content", path);
        }

        if (TID1600Rules.isMultiframeSOP(sopClassUID) && !hasNumberOfFrames) {
            result.addWarning("IMAGE structure: Number of Frames (121140, DCM) expected for multiframe SOP Class " + sopClassUID, path);
        }
    }

    // ========================================================================
    // TID 16XX: Descriptors for Key Object Selection
    // ========================================================================

    /**
     * Validates KOS descriptors when the referenced instance is itself a KOS.
     *
     * Per CP-2595:
     * - Document Title (121144, DCM) - R+, extracted from the referenced KOS
     * - Key Object Description (113012, DCM) - RC+, required if present in the referenced KOS
     */
    private static void validateKOSDescriptors(Attributes titleItem, Sequence allContent,
                                               ValidationResult result, String path) {
        // Document Title (121144, DCM) is present (we got called because it was found)
        // Validate it has a proper CODE value
        String vt = titleItem.getString(Tag.ValueType);
        if (!"CODE".equals(vt)) {
            result.addWarning("TID 16XX: Document Title (121144, DCM) should use ValueType CODE, found: " + vt, path);
        }

        // Check for Key Object Description (113012, DCM) in the same content sequence
        boolean hasKeyObjectDescription = false;
        for (Attributes item : allContent) {
            Attributes concept = SRContentTreeUtils.firstItem(item.getSequence(Tag.ConceptNameCodeSequence));
            if (concept != null) {
                String codeValue = concept.getString(Tag.CodeValue);
                if (CODE_KOS_OBJECT_DESCRIPTION.equals(codeValue)) {
                    hasKeyObjectDescription = true;
                    break;
                }
            }
        }

        if (!hasKeyObjectDescription) {
            // RC+ - only required if present in the referenced KOS, so this is a warning
            result.addInfo("TID 16XX: Key Object Description (113012, DCM) not found. " +
                    "Required if present in the referenced KOS instance.", path);
        }
    }

    /**
     * Legacy KOS reference validation (now handled by TID 16XX descriptors).
     * Checks for the old-style KOS reference structure.
     */
    private static void validateKOSReference(Attributes entry, ValidationResult result, String path) {
        Sequence contentSeq = entry.getSequence(Tag.ContentSequence);
        if (contentSeq == null) {
            return;
        }

        boolean hasKOSTitle = false;

        for (Attributes item : contentSeq) {
            Attributes concept = SRContentTreeUtils.firstItem(item.getSequence(Tag.ConceptNameCodeSequence));
            if (concept != null) {
                String codeValue = concept.getString(Tag.CodeValue);

                if (CODE_KOS_DOCUMENT_TITLE.equals(codeValue)) {
                    hasKOSTitle = true;
                }
            }
        }

        if (!hasKOSTitle) {
            result.addError(ValidationMessages.TID1600_KOS_MISSING_TITLE_CODE, path);
        }
    }

    /**
     * Validate TID 16XX Key Object Description (V-DESC-02).
     * Checks if the referenced instance in the entry is a KOS and validates
     * that appropriate descriptors are present.
     */
    private static void checkForKeyImageFlagging(Attributes entry, ValidationResult result,
                                                 String path, boolean verbose) {
        Sequence contentSeq = entry.getSequence(Tag.ContentSequence);
        if (contentSeq == null || contentSeq.isEmpty()) {
            return;
        }

        boolean hasKeyObjectDesc = false;
        boolean hasDocumentTitle = false;
        String referencedUID = null;

        Sequence refSOPSeq = entry.getSequence(Tag.ReferencedSOPSequence);
        if (refSOPSeq != null && !refSOPSeq.isEmpty()) {
            Attributes refSOP = SRContentTreeUtils.firstItem(refSOPSeq);
            referencedUID = refSOP.getString(Tag.ReferencedSOPInstanceUID);
        }

        for (Attributes item : contentSeq) {
            Attributes concept = SRContentTreeUtils.firstItem(item.getSequence(Tag.ConceptNameCodeSequence));
            if (concept == null) {
                continue;
            }

            String codeValue = concept.getString(Tag.CodeValue);
            if (DicomConstants.CODE_KEY_OBJECT_DESCRIPTION.equals(codeValue)) {
                hasKeyObjectDesc = true;

                Sequence nestedContent = item.getSequence(Tag.ContentSequence);
                if (nestedContent != null && !nestedContent.isEmpty()) {
                    for (Attributes nested : nestedContent) {
                        String nestedValueType = nested.getString(Tag.ValueType);
                        if ("CODE".equals(nestedValueType)) {
                            hasDocumentTitle = true;

                            Sequence titleCodeSeq = nested.getSequence(Tag.ConceptCodeSequence);
                            if (titleCodeSeq != null && !titleCodeSeq.isEmpty() && verbose) {
                                Attributes titleCode = SRContentTreeUtils.firstItem(titleCodeSeq);
                                String titleValue = titleCode.getString(Tag.CodeValue);
                                String titleMeaning = titleCode.getString(Tag.CodeMeaning);
                                result.addInfo("Key Image flagged with title: (" + titleValue
                                        + ", DCM, '" + titleMeaning + "')", path);
                            }
                            break;
                        }
                    }
                }
            } else if (CODE_KOS_DOCUMENT_TITLE.equals(codeValue)) {
                hasDocumentTitle = true;
            }
        }

        if (hasKeyObjectDesc) {
            if (!hasDocumentTitle) {
                result.addError(String.format(ValidationMessages.TID1600_KEY_DESCRIPTION_MISMATCH,
                        "missing required Document Title (121144, DCM) from CID 7010"), path);
            }

            if (referencedUID == null) {
                result.addWarning("Key Object Description present but cannot verify UID consistency "
                        + "(no ReferencedSOPInstanceUID in entry)", path);
            } else if (verbose) {
                result.addInfo("Key Image: SOP Instance UID " + referencedUID + " flagged as significant", path);
            }
        }
    }
}
