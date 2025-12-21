package be.uzleuven.ihe.dicom.validator.validation.tid1600;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import be.uzleuven.ihe.dicom.validator.model.ValidationResult;
import be.uzleuven.ihe.dicom.validator.utils.SRContentTreeUtils;

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

            if (TID1600Codes.CODE_MODALITY.equals(codeValue)) {
                hasModality = true;
            } else if (TID1600Codes.CODE_STUDY_INSTANCE_UID.equals(codeValue)) {
                hasStudyUID = true;
                String uidValue = item.getString(Tag.UID);
                if (uidValue == null || uidValue.trim().isEmpty()) {
                    result.addError("Study Instance UID content item has no UID value", path);
                }
            } else if (TID1600Codes.CODE_TARGET_REGION.equals(codeValue)) {
                hasTargetRegion = true;
                TID1600Rules.validateAnatomicRegion(item, result, path);
            }
        }

        if (!hasModality) {
            result.addError("TID 1600 Requirement: Modality (121139, DCM, 'Modality') missing at study level (Type R+)", path);
        }
        if (!hasStudyUID) {
            result.addError("TID 1600 Requirement: Study Instance UID (ddd011, DCM, 'Study Instance UID') missing at study level (Type R+)", path);
        }
        if (!hasTargetRegion) {
            result.addError("TID 1600 Requirement: Target Region (123014, DCM, 'Target Region') missing at study level (Type R+)", path);
        }
    }
}
