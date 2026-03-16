package be.uzleuven.ihe.dicom.validator.validation.tid1600;

import be.uzleuven.ihe.dicom.constants.CodeConstants;
import be.uzleuven.ihe.dicom.constants.ValidationMessages;
import be.uzleuven.ihe.dicom.validator.model.ValidationResult;
import be.uzleuven.ihe.dicom.validator.utils.SRContentTreeUtils;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;

import static be.uzleuven.ihe.dicom.constants.CodeConstants.CODE_NUM_STUDY_RELATED_SERIES;
import static be.uzleuven.ihe.dicom.constants.CodeConstants.CODE_TARGET_REGION;
import static be.uzleuven.ihe.dicom.constants.DicomConstants.CODE_MODALITY;

/**
 * Validates study-level attributes used by TID 1600 Acquisition Context.
 * Per CP-2595 / MADO Trial Implementation, TID 1600 study-level items are
 * HAS ACQ CONTEXT children of the Image Library container
 * (111028, DCM, "Image Library"):
 * <ul>
 *   <li>Modality (121139, DCM) - R+, DCID 29 / DCID 32</li>
 *   <li>Target Region (123014, DCM) - R+, high-level anatomic regions</li>
 *   <li>Number of Study Related Series (MADOTEMP009, 99IHE) - R+, units: {series}</li>
 * </ul>
 */
public final class TID1600StudyValidator {

    private TID1600StudyValidator() {
    }

    public static void validateStudyLevelAttributes(Sequence contentSeq, ValidationResult result,
                                                    String path, boolean verbose) {
        boolean hasModality = false;
        boolean hasTargetRegion = false;
        boolean hasNumStudyRelatedSeries = false;

        for (Attributes item : contentSeq) {
            Attributes concept = SRContentTreeUtils.firstItem(item.getSequence(Tag.ConceptNameCodeSequence));
            if (concept == null) {
                continue;
            }

            String codeValue = concept.getString(Tag.CodeValue);

            if (CODE_MODALITY.equals(codeValue)) {
                hasModality = true;
            } else if (CODE_TARGET_REGION.equals(codeValue)) {
                hasTargetRegion = true;
                TID1600Rules.validateAnatomicRegion(item, result, path);
            } else if (CODE_NUM_STUDY_RELATED_SERIES.equals(codeValue)) {
                hasNumStudyRelatedSeries = true;
                // Validate scheme designator is 99IHE for MADOTEMP codes
                String codingScheme = concept.getString(Tag.CodingSchemeDesignator);
                if (!CodeConstants.SCHEME_99IHE.equals(codingScheme)) {
                    result.addError(String.format(ValidationMessages.MADO_WRONG_SCHEME_FOR_MADOTEMP,
                            CODE_NUM_STUDY_RELATED_SERIES, codingScheme != null ? codingScheme : "(null)"), path);
                }
                // Validate UCUM unit is {series}
                TID1600Rules.validateUCUMUnit(item, "{series}", "Number of Study Related Series", result, path);
            }
        }

        if (!hasModality) {
            result.addError(ValidationMessages.TID1600_STUDY_MISSING_MODALITY, path);
        }
        if (!hasTargetRegion) {
            result.addError(ValidationMessages.TID1600_STUDY_MISSING_TARGET_REGION, path);
        }
        if (!hasNumStudyRelatedSeries) {
            result.addError(ValidationMessages.TID1600_STUDY_MISSING_NUM_SERIES, path);
        }
    }
}
