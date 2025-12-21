package be.uzleuven.ihe.dicom.validator.validation.tid1600;

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
            result.addError("Target Region code has no CodeValue", path);
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
        return sopClassUID.equals("1.2.840.10008.5.1.4.1.1.77.1.6") ||  // VL Whole Slide Microscopy
                sopClassUID.equals("1.2.840.10008.5.1.4.1.1.7.1") ||     // Multi-frame Single Bit
                sopClassUID.equals("1.2.840.10008.5.1.4.1.1.7.2") ||     // Multi-frame Grayscale Byte
                sopClassUID.equals("1.2.840.10008.5.1.4.1.1.7.3") ||     // Multi-frame Grayscale Word
                sopClassUID.equals("1.2.840.10008.5.1.4.1.1.7.4") ||     // Multi-frame True Color
                sopClassUID.equals("1.2.840.10008.5.1.4.1.1.2.1") ||     // Enhanced CT
                sopClassUID.equals("1.2.840.10008.5.1.4.1.1.4.1") ||     // Enhanced MR
                sopClassUID.equals("1.2.840.10008.5.1.4.1.1.130");       // Enhanced PET
    }
}

