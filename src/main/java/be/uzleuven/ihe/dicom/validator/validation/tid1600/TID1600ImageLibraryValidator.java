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

    private static void validateImageLibraryGroup(Attributes group, ValidationResult result,
                                                  String path, boolean verbose) {
        Sequence contentSeq = group.getSequence(Tag.ContentSequence);
        if (contentSeq == null || contentSeq.isEmpty()) {
            result.addError(ValidationMessages.TID1600_GROUP_NO_CONTENT, path);
            return;
        }

        boolean hasModality = false;
        boolean hasSeriesDate = false;
        boolean hasSeriesTime = false;
        boolean hasSeriesDescription = false;
        boolean hasSeriesNumber = false;
        boolean hasSeriesUID = false;
        boolean hasNumberOfSeriesRelatedInstances = false;
        int imageEntryCount = 0;

        for (Attributes item : contentSeq) {
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
                    // Some generators encode Series Number using ddd010 (see CodeConstants.CODE_SERIES_NUMBER).
                    // Accept both to avoid false positives when validating existing dumps.
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
                // In a group, IMAGE/COMPOSITE items are entries.
                validateImageLibraryEntry(item, result, path + ".Entry[" + imageEntryCount + "]", verbose);
            }
        }

        // Report missing required metadata
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

        if (imageEntryCount == 0) {
            result.addWarning("TID 1600 Image Library Group has no IMAGE/COMPOSITE entries. " +
                            "Expected at least one Image Library Entry per instance in the series.", path);
        } else if (verbose) {
            result.addInfo("Found " + imageEntryCount + " Image Library Entry items in this group", path);
        }
    }

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
            boolean hasNumberOfFrames = false;
            boolean hasInstanceNumber = false;

            for (Attributes item : contentSeq) {
                Attributes concept = SRContentTreeUtils.firstItem(item.getSequence(Tag.ConceptNameCodeSequence));
                if (concept != null) {
                    String codeValue = concept.getString(Tag.CodeValue);

                    if (CODE_NUMBER_OF_FRAMES.equals(codeValue)) {
                        hasNumberOfFrames = true;
                    } else if (CODE_INSTANCE_NUMBER.equals(codeValue)
                            || CODE_SERIES_NUMBER.equals(codeValue)) {
                        // Some generators (and older dumps) use ddd005 for the instance number concept.
                        // Accept both ddd012 (preferred) and ddd005 (legacy mapping) to avoid false positives.
                        hasInstanceNumber = true;

                        // ValueType can be TEXT in our validator, but dumps often show UT for TextValue.
                        // Treat TEXT/UT as acceptable string carriers.
                        String vt = item.getString(Tag.ValueType);
                        if (vt != null && !(DicomConstants.VALUE_TYPE_TEXT.equals(vt) || "UT".equals(vt))) {
                            result.addError(String.format(ValidationMessages.TID1600_ENTRY_INSTANCE_NUMBER_WRONG_VT, vt), path);
                        }
                    } else if (
                            CODE_KOS_TITLE.equals(codeValue)) {
                        validateKOSReference(entry, result, path);
                    }
                }
            }

            if (!hasInstanceNumber) {
                result.addError(ValidationMessages.TID1600_ENTRY_MISSING_INSTANCE_NUMBER_REQUIRED, path);
            }

            if (TID1600Rules.isMultiframeSOP(sopClassUID) && !hasNumberOfFrames) {
                result.addError(String.format(ValidationMessages.TID1600_ENTRY_MISSING_NUMBER_OF_FRAMES_MULTIFRAME, sopClassUID), path);
            }
        } else {
            // For MADO, instance-level metadata is expected (R+). Treat empty nested content as an error.
            result.addError(ValidationMessages.TID1600_ENTRY_MISSING_METADATA_CONTENT, path);
            if (verbose) {
                result.addInfo("Image Library Entry has no nested metadata (this is standard KOS style, but not sufficient for MADO)", path);
            }
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

