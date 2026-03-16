package be.uzleuven.ihe.dicom.validator.validation.tid1600;

import be.uzleuven.ihe.dicom.constants.DicomConstants;
import be.uzleuven.ihe.dicom.constants.ValidationMessages;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import be.uzleuven.ihe.dicom.validator.model.ValidationResult;
import be.uzleuven.ihe.dicom.validator.utils.SRContentTreeUtils;

import static be.uzleuven.ihe.dicom.constants.CodeConstants.CODE_KOS_MANIFEST;
import static be.uzleuven.ihe.dicom.constants.CodeConstants.CODE_MANIFEST_WITH_DESCRIPTION;
import static be.uzleuven.ihe.dicom.constants.CodeConstants.SCHEME_99IHE;
import static be.uzleuven.ihe.dicom.constants.DicomConstants.SCHEME_DCM;

/**
 * Root container checks for MADO TID 1600 manifests.
 * Per CP-2595 Trial Implementation, the root node (TID 2010) uses
 * (MADOTEMP001, 99IHE, "Manifest with Description").
 */
public final class TID1600RootValidator {

    private TID1600RootValidator() {
    }

    /**
     * V-ROOT-01: Root Content Item MUST have ValueType of CONTAINER
     * V-ROOT-02: ConceptNameCodeSequence must convey document title
     *            CP-2595: (MADOTEMP001, 99IHE, "Manifest with Description")
     *            Also accepts: (113030, DCM, "Manifest")
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

        if (CODE_KOS_MANIFEST.equals(codeValue) && SCHEME_DCM.equals(codingScheme)) {
            result.addInfo("Root container uses MADO title: (113030, DCM, 'Manifest')", modulePath);
        } else if (CODE_MANIFEST_WITH_DESCRIPTION.equals(codeValue) && SCHEME_99IHE.equals(codingScheme)) {
            result.addInfo("Root container uses MADO CP-2595 title: (" + CODE_MANIFEST_WITH_DESCRIPTION
                    + ", " + SCHEME_99IHE + ", 'Manifest with Description')", modulePath);
        } else if (CODE_MANIFEST_WITH_DESCRIPTION.equals(codeValue) && SCHEME_DCM.equals(codingScheme)) {
            // Backward compatibility: accept MADOTEMP001 with DCM scheme but warn
            result.addWarning("Root container uses MADOTEMP code with wrong scheme: ("
                    + CODE_MANIFEST_WITH_DESCRIPTION + ", DCM). "
                    + "CP-2595 requires scheme '" + SCHEME_99IHE + "' for MADOTEMP codes.", modulePath);
        } else if ("ddd001".equals(codeValue)) {
            // Legacy ddd001 code — warn about deprecation
            result.addWarning(String.format(ValidationMessages.MADO_DEPRECATED_DDD_CODE, codeValue, codingScheme),
                    modulePath);
        } else {
            result.addWarning("MADO Root Container Requirement V-ROOT-02: ConceptNameCodeSequence "
                    + "should use (" + CODE_MANIFEST_WITH_DESCRIPTION + ", " + SCHEME_99IHE
                    + ", 'Manifest with Description') or (113030, DCM, 'Manifest') for MADO context, but found: "
                    + "(" + codeValue + ", " + codingScheme + ")", modulePath);
        }

        String continuity = dataset.getString(Tag.ContinuityOfContent);
        if (!DicomConstants.CONTINUITY_SEPARATE.equals(continuity)) {
            result.addError(ValidationMessages.TID1600_ROOT_CONTINUITY_WRONG, modulePath);
        }

        return true;
    }
}
