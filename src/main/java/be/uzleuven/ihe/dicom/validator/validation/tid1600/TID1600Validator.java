package be.uzleuven.ihe.dicom.validator.validation.tid1600;

import be.uzleuven.ihe.dicom.constants.TID1600Codes;
import be.uzleuven.ihe.dicom.constants.ValidationMessages;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import be.uzleuven.ihe.dicom.validator.model.ValidationResult;
import be.uzleuven.ihe.dicom.validator.utils.SRContentTreeUtils;

/**
 * Facade validator for TID 1600 Image Library structure.
 */
public final class TID1600Validator {

    private TID1600Validator() {
    }

    public static void validateTID1600Structure(Attributes dataset, ValidationResult result,
                                                String modulePath, boolean verbose) {
        if (!TID1600RootValidator.validateRootContainer(dataset, result, modulePath)) {
            return;
        }

        Sequence contentSeq = dataset.getSequence(Tag.ContentSequence);
        if (contentSeq == null || contentSeq.isEmpty()) {
            result.addError(ValidationMessages.TID1600_IMAGE_LIBRARY_EMPTY, modulePath);
            return;
        }

        boolean foundImageLibrary = false;
        for (Attributes item : contentSeq) {
            if (SRContentTreeUtils.isContainerWithConcept(item, TID1600Codes.CODE_IMAGE_LIBRARY, TID1600Codes.SCHEME_DCM)) {
                foundImageLibrary = true;
                result.addInfo("MADO Approach 2: TID 1600 Image Library (111028, DCM) detected", modulePath);
                TID1600ImageLibraryValidator.validateImageLibraryContainer(item, result, modulePath + ".ImageLibrary", verbose);
            }
        }

        if (!foundImageLibrary) {
            result.addError(ValidationMessages.TID1600_APPROACH2_MISSING_IMAGE_LIBRARY, modulePath);
        }

        TID1600StudyValidator.validateStudyLevelAttributes(contentSeq, result, modulePath, verbose);
    }
}
