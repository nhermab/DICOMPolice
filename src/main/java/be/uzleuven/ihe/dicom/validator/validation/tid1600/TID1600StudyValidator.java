package be.uzleuven.ihe.dicom.validator.validation.tid1600;


import be.uzleuven.ihe.dicom.constants.ValidationMessages;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import be.uzleuven.ihe.dicom.validator.model.ValidationResult;
import be.uzleuven.ihe.dicom.validator.utils.SRContentTreeUtils;

import static be.uzleuven.ihe.dicom.constants.CodeConstants.CODE_STUDY_INSTANCE_UID;
import static be.uzleuven.ihe.dicom.constants.CodeConstants.CODE_TARGET_REGION;
import static be.uzleuven.ihe.dicom.constants.DicomConstants.CODE_MODALITY;

/**
 * Validates study-level attributes used by TID 1600 Acquisition Context.
 */
public final class TID1600StudyValidator {

    private TID1600StudyValidator() {
    }

    public static void validateStudyLevelAttributes(Sequence contentSeq, ValidationResult result,
                                                    String path, boolean verbose) {
        boolean hasModality = false;
        boolean hasStudyUID = false;
        boolean hasTargetRegion = false;

        for (Attributes item : contentSeq) {
            Attributes concept = SRContentTreeUtils.firstItem(item.getSequence(Tag.ConceptNameCodeSequence));
            if (concept == null) {
                continue;
            }

            String codeValue = concept.getString(Tag.CodeValue);

            if (CODE_MODALITY.equals(codeValue)) {
                hasModality = true;
            } else if (CODE_STUDY_INSTANCE_UID.equals(codeValue)) {
                hasStudyUID = true;
                String uidValue = item.getString(Tag.UID);
                if (uidValue == null || uidValue.trim().isEmpty()) {
                    result.addError(ValidationMessages.TID1600_STUDY_UID_NO_VALUE, path);
                }
            } else if (CODE_TARGET_REGION.equals(codeValue)) {
                hasTargetRegion = true;
                TID1600Rules.validateAnatomicRegion(item, result, path);
            }
        }

        if (!hasModality) {
            result.addError(ValidationMessages.TID1600_STUDY_MISSING_MODALITY, path);
        }
        if (!hasStudyUID) {
            result.addError(ValidationMessages.TID1600_STUDY_MISSING_STUDY_UID, path);
        }
        if (!hasTargetRegion) {
            result.addError(ValidationMessages.TID1600_STUDY_MISSING_TARGET_REGION, path);
        }
    }
}
