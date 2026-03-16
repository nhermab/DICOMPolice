package be.uzleuven.ihe.dicom.validator.validation.tid1600;

import be.uzleuven.ihe.dicom.constants.CodeConstants;
import be.uzleuven.ihe.dicom.constants.SopClassLists;
import be.uzleuven.ihe.dicom.constants.ValidationMessages;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import be.uzleuven.ihe.dicom.validator.model.ValidationResult;
import be.uzleuven.ihe.dicom.validator.utils.SRContentTreeUtils;

import java.util.Set;

/**
 * Small reusable rule helpers used by TID 1600 validation.
 */
public final class TID1600Rules {

    private TID1600Rules() {
    }

    /**
     * Set of MADOTEMP code values that MUST use 99IHE as CodingSchemeDesignator.
     */
    private static final Set<String> MADOTEMP_CODES = Set.of(
            CodeConstants.CODE_MANIFEST_WITH_DESCRIPTION,  // MADOTEMP001
            CodeConstants.CODE_SERIES_DESCRIPTION,          // MADOTEMP002
            CodeConstants.CODE_SERIES_DATE,                 // MADOTEMP003
            CodeConstants.CODE_SERIES_TIME,                 // MADOTEMP004
            CodeConstants.CODE_NUM_SERIES_RELATED_INSTANCES, // MADOTEMP007
            CodeConstants.CODE_NUM_STUDY_RELATED_SERIES     // MADOTEMP009
    );

    /**
     * Validates that a MADOTEMP code uses the correct 99IHE scheme designator.
     * Reports an error if a MADOTEMP code is paired with the wrong scheme (e.g., DCM).
     *
     * @param concept  the ConceptNameCodeSequence item containing CodeValue and CodingSchemeDesignator
     * @param expectedCode the expected MADOTEMP code value
     * @param result   validation result to add errors to
     * @param path     module path for error reporting
     */
    public static void validateMADOTEMPScheme(Attributes concept, String expectedCode,
                                              ValidationResult result, String path) {
        if (concept == null) {
            return;
        }
        String codingScheme = concept.getString(Tag.CodingSchemeDesignator);
        if (!CodeConstants.SCHEME_99IHE.equals(codingScheme) && isMadoTempCode(expectedCode)) {
            result.addError(String.format(ValidationMessages.MADO_WRONG_SCHEME_FOR_MADOTEMP,
                    expectedCode, codingScheme != null ? codingScheme : "(null)"), path);
        }
    }

    /**
     * Returns true if the given code value is a MADOTEMP trial implementation code.
     */
    public static boolean isMadoTempCode(String codeValue) {
        if (codeValue == null) {
            return false;
        }
        return MADOTEMP_CODES.contains(codeValue) || codeValue.startsWith("MADOTEMP");
    }

    /**
     * Returns true if the given code value is a deprecated provisional 'ddd' code.
     */
    public static boolean isDeprecatedDddCode(String codeValue) {
        return codeValue != null && codeValue.startsWith("ddd");
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

    /**
     * Validates UCUM measurement unit on a NUM content item.
     * Per MADO, Number of Study Related Series uses {series} and
     * Number of Series Related Instances uses {instances}.
     *
     * @param item         the NUM content item (must have MeasuredValueSequence)
     * @param expectedUnit the expected UCUM unit string
     * @param conceptLabel human-readable label for error messages
     * @param result       validation result
     * @param path         module path
     */
    public static void validateUCUMUnit(Attributes item, String expectedUnit,
                                        String conceptLabel, ValidationResult result, String path) {
        if (item == null) return;
        Sequence measuredValueSeq = item.getSequence(Tag.MeasuredValueSequence);
        if (measuredValueSeq == null || measuredValueSeq.isEmpty()) {
            // NUM without MeasuredValueSequence — may use NumericValue directly
            return;
        }
        Attributes mv = measuredValueSeq.get(0);
        Sequence unitsSeq = mv.getSequence(Tag.MeasurementUnitsCodeSequence);
        if (unitsSeq == null || unitsSeq.isEmpty()) {
            result.addWarning("NUM content item '" + conceptLabel + "' has no MeasurementUnitsCodeSequence.", path);
            return;
        }
        Attributes units = unitsSeq.get(0);
        String unitValue = units.getString(Tag.CodeValue);
        if (unitValue != null && !expectedUnit.equals(unitValue)) {
            result.addError(String.format(ValidationMessages.MADO_UCUM_UNIT_WRONG,
                    conceptLabel, unitValue, expectedUnit), path);
        }
    }

    public static boolean isMultiframeSOP(String sopClassUID) {
        if (sopClassUID == null) {
            return false;
        }
        return SopClassLists.MULTIFRAME_SOP_CLASSES.contains(sopClassUID);
    }
}
