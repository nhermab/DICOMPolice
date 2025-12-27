package be.uzleuven.ihe.dicom.validator.validation;

import org.dcm4che3.data.Attributes;
import be.uzleuven.ihe.dicom.validator.model.ValidationResult;
import be.uzleuven.ihe.dicom.validator.validation.tid1600.TID1600Validator;


/**
 * Backwards-compatible facade for validating TID 1600 Image Library template structure.
 * Implementation was extracted into the {@code uz.hupa.validator.validation.tid1600} package
 * to keep this class small and improve maintainability.
 */
public final class MADOTemplateValidator {

    private MADOTemplateValidator() {
    }

    public static void validateTID1600Structure(Attributes dataset, ValidationResult result,
                                                String modulePath, boolean verbose) {
        TID1600Validator.validateTID1600Structure(dataset, result, modulePath, verbose);
    }
}
