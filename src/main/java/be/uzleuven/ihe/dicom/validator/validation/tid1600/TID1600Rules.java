package be.uzleuven.ihe.dicom.validator.validation.tid1600;

import be.uzleuven.ihe.dicom.constants.SopClassLists;
import be.uzleuven.ihe.dicom.constants.ValidationMessages;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import be.uzleuven.ihe.dicom.validator.model.ValidationResult;
import be.uzleuven.ihe.dicom.validator.utils.SRContentTreeUtils;

/**
 * Small reusable rule helpers used by TID 1600 validation.
 */
public final class TID1600Rules {

    private TID1600Rules() {
    }

    public static void validateAnatomicRegion(Attributes item, ValidationResult result, String path) {
        Sequence codeSeq = item.getSequence(Tag.ConceptCodeSequence);
        if (codeSeq == null || codeSeq.isEmpty()) {
            result.addWarning("Target Region content item has no ConceptCodeSequence value", path);
            return;
        }

        Attributes code = SRContentTreeUtils.firstItem(codeSeq);
        String codeValue = code.getString(Tag.CodeValue);
        String csd = code.getString(Tag.CodingSchemeDesignator);

        if (codeValue == null || codeValue.trim().isEmpty()) {
            result.addError(ValidationMessages.TID1600_TARGET_REGION_NO_CODE_VALUE, path);
        }
        if (!"SNM".equals(csd) && !"FMA".equals(csd) && !"SCT".equals(csd)) {
            result.addInfo("Target Region uses CodingSchemeDesignator: " + csd
                    + ". Expected SNM, FMA, or SCT for CID 403X anatomic regions.", path);
        }
    }

    public static void validateSeriesNumberConsistency(Attributes item, ValidationResult result, String path) {
        // Reserved for future enhancement.
        result.addInfo("Series Number content item present. Ensure consistency with referenced series (0020,0011).", path);
    }

    public static boolean isMultiframeSOP(String sopClassUID) {
        if (sopClassUID == null) {
            return false;
        }
        return SopClassLists.MULTIFRAME_SOP_CLASSES.contains(sopClassUID);
    }
}
