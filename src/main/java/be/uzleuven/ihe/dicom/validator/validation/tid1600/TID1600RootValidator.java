package be.uzleuven.ihe.dicom.validator.validation.tid1600;

import be.uzleuven.ihe.dicom.constants.DicomConstants;
import be.uzleuven.ihe.dicom.constants.TID1600Codes;
import be.uzleuven.ihe.dicom.constants.ValidationMessages;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import be.uzleuven.ihe.dicom.validator.model.ValidationResult;
import be.uzleuven.ihe.dicom.validator.utils.SRContentTreeUtils;

/**
 * Root container checks for MADO TID 1600 manifests.
 */
public final class TID1600RootValidator {

    private TID1600RootValidator() {
    }

    /**
     * V-ROOT-01: Root Content Item MUST have ValueType of CONTAINER
     * V-ROOT-02: ConceptNameCodeSequence must convey document title (prefer "Manifest" for MADO)
     * V-ROOT-03: ContinuityOfContent MUST be SEPARATE
     *
     * @return true if root is valid enough to continue validating ContentSequence.
     */
    public static boolean validateRootContainer(Attributes dataset, ValidationResult result, String modulePath) {
        String valueType = dataset.getString(Tag.ValueType);
        if (!"CONTAINER".equals(valueType)) {
            result.addError(ValidationMessages.TID1600_ROOT_VALUE_TYPE_WRONG, modulePath);
            return false;
        }

        Sequence conceptSeq = dataset.getSequence(Tag.ConceptNameCodeSequence);
        if (conceptSeq == null || conceptSeq.isEmpty()) {
            result.addError(ValidationMessages.TID1600_ROOT_CONCEPT_NAME_WRONG, modulePath);
            return false;
        }

        Attributes concept = SRContentTreeUtils.firstItem(conceptSeq);
        String codeValue = concept.getString(Tag.CodeValue);
        String codingScheme = concept.getString(Tag.CodingSchemeDesignator);

        if (TID1600Codes.CODE_MANIFEST.equals(codeValue) && TID1600Codes.SCHEME_DCM.equals(codingScheme)) {
            result.addInfo("Root container uses MADO preferred title: (113030, DCM, 'Manifest')", modulePath);
        } else if ("ddd001".equals(codeValue) && TID1600Codes.SCHEME_DCM.equals(codingScheme)) {
            result.addInfo("Root container uses MADO title: (ddd001, DCM, 'Manifest with Description')", modulePath);
        } else {
            result.addWarning("MADO Root Container Requirement V-ROOT-02: ConceptNameCodeSequence "
                    + "should use (113030, DCM, 'Manifest') for MADO context, but found: "
                    + "(" + codeValue + ", " + codingScheme + ")", modulePath);
        }

        String continuity = dataset.getString(Tag.ContinuityOfContent);
        if (!DicomConstants.CONTINUITY_SEPARATE.equals(continuity)) {
            result.addError(ValidationMessages.TID1600_ROOT_CONTINUITY_WRONG, modulePath);
        }

        return true;
    }
}
