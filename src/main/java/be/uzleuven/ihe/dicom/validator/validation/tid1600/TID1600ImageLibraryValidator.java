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
            } else if ("IMAGE".equals(valueType) || "COMPOSITE".equals(valueType)) {
                // Top-level IMAGE/COMPOSITE items in the Image Library container are entries.
                entryCount++;
                validateImageLibraryEntry(item, result, path + ".Entry[" + i + "]", verbose);
                checkForKeyImageFlagging(item, result, path + ".Entry[" + i + "]", verbose);
            } else {
                // TID 1600 allows additional content items at the top level (e.g., Acquisition Context).
                // Per CP-1920 and the MADO profile, this can be CODE/UIDREF/TEXT items such as Study UID,
                // Modality, Target Region, etc.
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

    /**
     * Validates an Image Library Group container (TID 1601).
     *
     * Per TID 1601:
     * - Series-level metadata (Modality, Series UID, etc.) should use HAS ACQ CONTEXT
     * - contains IMAGE items with Referenced SOP Sequence and possible nested instance-level metadata (Instance Number, Number of Frames)
     *
     * Structure:
     *   Image Library Group (CONTAINER)
     *     ├── HAS ACQ CONTEXT -> Modality (series)
     *     ├── HAS ACQ CONTEXT -> Series Instance UID
     *     ├── HAS ACQ CONTEXT -> Series Description
     *     └── CONTAINS -> IMAGE
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
        boolean hasNumberOfSeriesRelatedInstances = false;

        // Instance-level tracking
        int imageEntryCount = 0;
        int instanceNumberCount = 0;

        for (int i = 0; i < contentSeq.size(); i++) {
            Attributes item = contentSeq.get(i);
            Attributes concept = SRContentTreeUtils.firstItem(item.getSequence(Tag.ConceptNameCodeSequence));

            if (concept != null) {
                String codeValue = concept.getString(Tag.CodeValue);

                if (CODE_MODALITY.equals(codeValue)) {
                    hasModality = true;
                } else if (CODE_SERIES_DATE.equals(codeValue)) {
                    hasSeriesDate = true;
                } else if (CODE_SERIES_TIME.equals(codeValue)) {
                    hasSeriesTime = true;
                } else if (CODE_SERIES_DESCRIPTION.equals(codeValue)) {
                    hasSeriesDescription = true;
                } else if (CODE_SERIES_NUMBER.equals(codeValue)) {
                    hasSeriesNumber = true;
                    TID1600Rules.validateSeriesNumberConsistency(item, result, path);
                } else if (CODE_SERIES_INSTANCE_UID.equals(codeValue)) {
                    hasSeriesUID = true;
                } else if (CODE_NUM_SERIES_RELATED_INSTANCES.equals(codeValue)) {
                    hasNumberOfSeriesRelatedInstances = true;
                }
            }

            String valueType = item.getString(Tag.ValueType);
            if ("IMAGE".equals(valueType) || "COMPOSITE".equals(valueType)) {
                imageEntryCount++;
                // Validate the IMAGE entry (now a leaf node check)
                validateImageLibraryEntry(item, result, path + ".Entry[" + imageEntryCount + "]", verbose);
            }
        }

        // Report missing required series-level metadata
        if (!hasModality) {
            result.addError(ValidationMessages.TID1600_GROUP_MISSING_MODALITY, path);
        }
        if (!hasSeriesDate) {
            result.addError(ValidationMessages.TID1600_GROUP_MISSING_SERIES_DATE, path);
        }
        if (!hasSeriesTime) {
            result.addError(ValidationMessages.TID1600_GROUP_MISSING_SERIES_TIME, path);
        }
        if (!hasSeriesDescription) {
            result.addError(ValidationMessages.TID1600_GROUP_MISSING_SERIES_DESCRIPTION, path);
        }
        if (!hasSeriesNumber) {
            result.addError(ValidationMessages.TID1600_GROUP_MISSING_SERIES_NUMBER, path);
        }
        if (!hasSeriesUID) {
            result.addError(ValidationMessages.TID1600_GROUP_MISSING_SERIES_UID, path);
        }
        if (!hasNumberOfSeriesRelatedInstances) {
            result.addError(ValidationMessages.TID1600_GROUP_MISSING_SERIES_RELATED_INSTANCES, path);
        }

        // Validate instance-level metadata presence
        if (imageEntryCount == 0) {
            result.addWarning("TID 1600 Image Library Group has no IMAGE/COMPOSITE entries. " +
                            "Expected at least one Image Library Entry per instance in the series.", path);
        }
    }

    /**
     * Validates an IMAGE/COMPOSITE entry in the Image Library.
     *
     * Per TID 1601, IMAGE items contain Referenced SOP Sequence
     * Per MADO spec, IMAGE items contains also children instance-level metadata if available/relevant (Instance Number, Number of Frames)
     */
    private static void validateImageLibraryEntry(Attributes entry, ValidationResult result,
                                                  String path, boolean verbose) {
        // Check value type
        String valueType = entry.getString(Tag.ValueType);
        if ("COMPOSITE".equals(valueType)) {
            result.addWarning("TID 1600 Image Library Entry uses ValueType 'COMPOSITE'. " +
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

        Sequence contentSeq = entry.getSequence(Tag.ContentSequence);
        if (contentSeq != null && !contentSeq.isEmpty()) {
            // Still validate the nested content for backward compatibility
            validateIMAGENestedContent(contentSeq, sopClassUID, result, path, verbose);
        }
        // Note: We do NOT report an error for missing ContentSequence - this is the CORRECT behavior per TID 1601
    }

    /**
     * Validates nested content inside IMAGE items
     */
    private static void validateIMAGENestedContent(Sequence contentSeq, String sopClassUID,
                                                   ValidationResult result, String path, boolean verbose) {
        boolean hasNumberOfFrames = false;
        boolean hasInstanceNumber = false;

        for (Attributes item : contentSeq) {
            Attributes concept = SRContentTreeUtils.firstItem(item.getSequence(Tag.ConceptNameCodeSequence));
            if (concept != null) {
                String codeValue = concept.getString(Tag.CodeValue);

                if (CODE_NUMBER_OF_FRAMES.equals(codeValue)) {
                    hasNumberOfFrames = true;
                } else if (CODE_INSTANCE_NUMBER.equals(codeValue)) {
                    hasInstanceNumber = true;

                    String vt = item.getString(Tag.ValueType);
                    if (vt != null && !(DicomConstants.VALUE_TYPE_TEXT.equals(vt) || "UT".equals(vt))) {
                        result.addError(String.format(ValidationMessages.TID1600_ENTRY_INSTANCE_NUMBER_WRONG_VT, vt), path);
                    }
                } else if (CODE_KOS_TITLE.equals(codeValue)) {
                    validateKOSReference(item, result, path);
                }
            }
        }

        // validate the nested metadata
        if (!hasInstanceNumber && verbose) {
            result.addInfo("IMAGE structure: Instance Number not found in nested content", path);
        }

        if (TID1600Rules.isMultiframeSOP(sopClassUID) && !hasNumberOfFrames) {
            result.addWarning("IMAGE structure: Number of Frames expected for multiframe SOP Class " + sopClassUID, path);
        }
    }

    private static void validateKOSReference(Attributes entry, ValidationResult result, String path) {
        Sequence contentSeq = entry.getSequence(Tag.ContentSequence);
        if (contentSeq == null) {
            return;
        }

        boolean hasKOSTitle = false;
        boolean hasSOPInstanceUIDs = false;

        for (Attributes item : contentSeq) {
            Attributes concept = SRContentTreeUtils.firstItem(item.getSequence(Tag.ConceptNameCodeSequence));
            if (concept != null) {
                String codeValue = concept.getString(Tag.CodeValue);

                if (CODE_KOS_TITLE.equals(codeValue)) {
                    hasKOSTitle = true;
                } else if (CODE_SOP_INSTANCE_UID.equals(codeValue)) {
                    hasSOPInstanceUIDs = true;
                }
            }
        }

        if (!hasKOSTitle) {
            result.addError(ValidationMessages.TID1600_KOS_MISSING_TITLE_CODE, path);
        }
        if (!hasSOPInstanceUIDs) {
            result.addError(ValidationMessages.TID1600_KOS_MISSING_SOP_UIDS, path);
        }
    }

    /**
     * Validate TID 16XX Key Object Description (V-DESC-02).
     */
    private static void checkForKeyImageFlagging(Attributes entry, ValidationResult result,
                                                 String path, boolean verbose) {
        Sequence contentSeq = entry.getSequence(Tag.ContentSequence);
        if (contentSeq == null || contentSeq.isEmpty()) {
            return;
        }

        boolean hasKeyObjectDesc = false;
        boolean hasKeyTitleCode = false;
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
                            hasKeyTitleCode = true;

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
            }
        }

        if (hasKeyObjectDesc) {
            if (!hasKeyTitleCode) {
                result.addError(String.format(ValidationMessages.TID1600_KEY_DESCRIPTION_MISMATCH,
                        "missing required Title Code from CID 7010 (e.g., 'For Surgery', 'Tumor Tracking')"), path);
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

