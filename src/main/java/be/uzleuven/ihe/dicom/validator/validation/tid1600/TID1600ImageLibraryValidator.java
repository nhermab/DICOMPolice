package be.uzleuven.ihe.dicom.validator.validation.tid1600;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import be.uzleuven.ihe.dicom.validator.model.ValidationResult;
import be.uzleuven.ihe.dicom.validator.utils.SRContentTreeUtils;

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
            result.addWarning("Image Library container has no content items", path);
            return;
        }

        for (int i = 0; i < contentSeq.size(); i++) {
            Attributes item = contentSeq.get(i);
            String valueType = item.getString(Tag.ValueType);

            if ("CONTAINER".equals(valueType)) {
                validateImageLibraryGroup(item, result, path + ".Group[" + i + "]", verbose);
            } else if ("IMAGE".equals(valueType) || "COMPOSITE".equals(valueType)) {
                validateImageLibraryEntry(item, result, path + ".Entry[" + i + "]", verbose);
                checkForKeyImageFlagging(item, result, path + ".Entry[" + i + "]", verbose);
            }
        }
    }

    private static void validateImageLibraryGroup(Attributes group, ValidationResult result,
                                                  String path, boolean verbose) {
        Sequence contentSeq = group.getSequence(Tag.ContentSequence);
        if (contentSeq == null || contentSeq.isEmpty()) {
            result.addWarning("Image Library Group has no content items", path);
            return;
        }

        boolean hasModality = false;
        boolean hasSeriesDate = false;
        boolean hasSeriesTime = false;
        boolean hasSeriesDescription = false;
        boolean hasSeriesNumber = false;
        boolean hasSeriesUID = false;

        for (Attributes item : contentSeq) {
            Attributes concept = SRContentTreeUtils.firstItem(item.getSequence(Tag.ConceptNameCodeSequence));
            if (concept != null) {
                String codeValue = concept.getString(Tag.CodeValue);

                if (TID1600Codes.CODE_MODALITY.equals(codeValue)) {
                    hasModality = true;
                } else if (TID1600Codes.CODE_SERIES_DATE.equals(codeValue)) {
                    hasSeriesDate = true;
                } else if (TID1600Codes.CODE_SERIES_TIME.equals(codeValue)) {
                    hasSeriesTime = true;
                } else if (TID1600Codes.CODE_SERIES_DESCRIPTION.equals(codeValue)) {
                    hasSeriesDescription = true;
                } else if (TID1600Codes.CODE_SERIES_NUMBER.equals(codeValue)) {
                    hasSeriesNumber = true;
                    TID1600Rules.validateSeriesNumberConsistency(item, result, path);
                } else if (TID1600Codes.CODE_SERIES_INSTANCE_UID.equals(codeValue)) {
                    hasSeriesUID = true;
                }
            }

            String valueType = item.getString(Tag.ValueType);
            if ("IMAGE".equals(valueType) || "COMPOSITE".equals(valueType)) {
                validateImageLibraryEntry(item, result, path + ".Entry", verbose);
            }
        }

        if (!hasModality) {
            result.addError("Image Library Group missing Modality (121139, DCM). Type R+", path);
        }
        if (!hasSeriesDate) {
            result.addError("Image Library Group missing Series Date (ddd003, DCM). Type R+", path);
        }
        if (!hasSeriesTime) {
            result.addError("Image Library Group missing Series Time (ddd004, DCM). Type R+", path);
        }
        if (!hasSeriesDescription) {
            result.addError("Image Library Group missing Series Description (ddd002, DCM). Type R+", path);
        }
        if (!hasSeriesNumber) {
            result.addError("Image Library Group missing Series Number (ddd005, DCM). Type R+", path);
        }
        if (!hasSeriesUID) {
            result.addError("Image Library Group missing Series Instance UID (ddd006, DCM). Type R+", path);
        }
    }

    private static void validateImageLibraryEntry(Attributes entry, ValidationResult result,
                                                  String path, boolean verbose) {
        Sequence refSOPSeq = entry.getSequence(Tag.ReferencedSOPSequence);
        if (refSOPSeq == null || refSOPSeq.isEmpty()) {
            result.addWarning("Image Library Entry has no ReferencedSOPSequence", path);
            return;
        }

        Attributes refSOP = SRContentTreeUtils.firstItem(refSOPSeq);
        String sopClassUID = refSOP.getString(Tag.ReferencedSOPClassUID);

        Sequence contentSeq = entry.getSequence(Tag.ContentSequence);
        if (contentSeq != null && !contentSeq.isEmpty()) {
            boolean hasNumberOfFrames = false;

            for (Attributes item : contentSeq) {
                Attributes concept = SRContentTreeUtils.firstItem(item.getSequence(Tag.ConceptNameCodeSequence));
                if (concept != null) {
                    String codeValue = concept.getString(Tag.CodeValue);

                    if (TID1600Codes.CODE_NUMBER_OF_FRAMES.equals(codeValue)) {
                        hasNumberOfFrames = true;
                    } else if (TID1600Codes.CODE_KOS_TITLE.equals(codeValue)) {
                        validateKOSReference(entry, result, path);
                    }
                }
            }

            if (TID1600Rules.isMultiframeSOP(sopClassUID) && !hasNumberOfFrames) {
                result.addWarning("Number of Frames (121140, DCM) missing for multiframe SOP Class. Type RC+", path);
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

                if (TID1600Codes.CODE_KOS_TITLE.equals(codeValue)) {
                    hasKOSTitle = true;
                } else if (TID1600Codes.CODE_SOP_INSTANCE_UID.equals(codeValue)) {
                    hasSOPInstanceUIDs = true;
                }
            }
        }

        if (!hasKOSTitle) {
            result.addError("KOS reference missing KOS Title Code (ddd008, DCM). Type R+", path);
        }
        if (!hasSOPInstanceUIDs) {
            result.addError("KOS reference missing SOP Instance UIDs (ddd007, DCM). Type R+", path);
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
            if (TID1600Codes.CODE_KEY_OBJECT_DESC.equals(codeValue)) {
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
                result.addError("TID 16XX Requirement V-DESC-02: Key Object Description present but "
                        + "missing required Title Code from CID 7010 (e.g., 'For Surgery', 'Tumor Tracking')", path);
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

