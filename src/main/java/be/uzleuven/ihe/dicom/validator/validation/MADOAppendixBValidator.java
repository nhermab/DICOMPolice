package be.uzleuven.ihe.dicom.validator.validation;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import be.uzleuven.ihe.dicom.validator.model.ValidationResult;

/**
 * Validator for MADO Appendix B format (Alternative Approach 1).
 *
 * Appendix B extends the standard Hierarchical SOP Instance Reference Macro
 * with additional Data Elements at Series and Instance levels, avoiding
 * the deep nesting of SR templates (TID 1600).
 *
 * Per MADO Requirements Section 3.3:
 * - Extension to Hierarchical Series Reference Macro (Section B.2)
 * - Extension to Hierarchical SOP Instance Reference Macro (Section B.1)
 * - Validation of Retrieve Location UID and Retrieve URL at appropriate levels
 */
public final class MADOAppendixBValidator {

    private MADOAppendixBValidator() {
    }

    /**
     * Validate MADO Appendix B format compliance.
     * This is the alternative to TID 1600 approach.
     *
     * @param dataset The DICOM dataset
     * @param result The validation result
     * @param modulePath The module path for error reporting
     * @param verbose Whether to include verbose output
     */
    public static void validateAppendixBFormat(Attributes dataset, ValidationResult result,
                                               String modulePath, boolean verbose) {
        if (verbose) {
            result.addInfo("Validating MADO Appendix B format (Approach 1: Extended Hierarchical Macro)", modulePath);
        }

        Sequence evidenceSeq = dataset.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence);
        if (evidenceSeq == null || evidenceSeq.isEmpty()) {
            result.addError("CurrentRequestedProcedureEvidenceSequence is missing. " +
                          "Appendix B format requires evidence sequence with extended attributes.", modulePath);
            return;
        }

        int studyCount = 0;
        for (Attributes studyItem : evidenceSeq) {
            String studyPath = modulePath + ".Evidence.Study[" + studyCount + "]";
            validateStudyLevel(studyItem, result, studyPath, verbose);
            studyCount++;
        }

        if (verbose) {
            result.addInfo("Appendix B validation complete. Validated " + studyCount + " study reference(s).", modulePath);
        }
    }

    /**
     * Validate study-level attributes in evidence sequence.
     */
    private static void validateStudyLevel(Attributes studyItem, ValidationResult result,
                                          String path, boolean verbose) {
        String studyUID = studyItem.getString(Tag.StudyInstanceUID);
        if (studyUID == null || studyUID.trim().isEmpty()) {
            result.addError("StudyInstanceUID missing in Evidence study item", path);
        }

        Sequence seriesSeq = studyItem.getSequence(Tag.ReferencedSeriesSequence);
        if (seriesSeq == null || seriesSeq.isEmpty()) {
            result.addError("ReferencedSeriesSequence is missing in Evidence study item", path);
            return;
        }

        int seriesCount = 0;
        for (Attributes seriesItem : seriesSeq) {
            String seriesPath = path + ".Series[" + seriesCount + "]";
            validateSeriesLevel(seriesItem, result, seriesPath, verbose);
            seriesCount++;
        }
    }

    /**
     * Validate series-level attributes per Appendix B Section B.2.
     *
     * Requirement V-ALT-01: The following attributes MUST be present:
     * - Modality (0008,0060): Type 1 (Required)
     * - Series Instance UID (0020,000E): Type 1 (Required)
     * - Retrieve Location UID (0040,E011): Type R+ (Required if available)
     * - Retrieve URL (0008,1190): Type O (Optional but validated if present)
     */
    private static void validateSeriesLevel(Attributes seriesItem, ValidationResult result,
                                           String path, boolean verbose) {
        // V-ALT-01: Modality - Type 1 (Required)
        String modality = seriesItem.getString(Tag.Modality);
        if (modality == null || modality.trim().isEmpty()) {
            result.addError("Appendix B Requirement V-ALT-01: Modality (0008,0060) is Type 1 (Required) " +
                          "in ReferencedSeriesSequence but is missing", path);
        } else if (verbose) {
            result.addInfo("Series Modality: " + modality, path);
        }

        // V-ALT-01: Series Instance UID - Type 1 (Required)
        String seriesUID = seriesItem.getString(Tag.SeriesInstanceUID);
        if (seriesUID == null || seriesUID.trim().isEmpty()) {
            result.addError("Appendix B Requirement V-ALT-01: SeriesInstanceUID (0020,000E) is Type 1 (Required) " +
                          "in ReferencedSeriesSequence but is missing", path);
        }

        // V-ALT-01: Retrieve Location UID - Type R+ (Required if available)
        String retrieveLocationUID = seriesItem.getString(Tag.RetrieveLocationUID);
        if (retrieveLocationUID != null && !retrieveLocationUID.trim().isEmpty()) {
            MADORetrievalValidator.validateRetrieveLocationUIDFormat(retrieveLocationUID, result, path);
            if (verbose) {
                result.addInfo("Series Retrieve Location UID: " + retrieveLocationUID, path);
            }
        }

        // V-ALT-01: Retrieve URL - Type O (Optional)
        String retrieveURL = seriesItem.getString(Tag.RetrieveURL);
        if (retrieveURL != null && !retrieveURL.trim().isEmpty()) {
            MADORetrievalValidator.validateRetrieveURLFormat(retrieveURL, result, path);
            if (verbose) {
                result.addInfo("Series Retrieve URL: " + retrieveURL, path);
            }
        }

        // Check that at least one retrieval method is present
        if ((retrieveLocationUID == null || retrieveLocationUID.trim().isEmpty()) &&
            (retrieveURL == null || retrieveURL.trim().isEmpty())) {
            result.addWarning("Appendix B: Neither Retrieve Location UID nor Retrieve URL present at series level. " +
                            "MADO requires at least one retrieval method (Type R+).", path);
        }

        // V-ALT-01: Optional but recommended attributes
        String seriesDescription = seriesItem.getString(Tag.SeriesDescription);
        if (seriesDescription == null || seriesDescription.trim().isEmpty()) {
            if (verbose) {
                result.addInfo("SeriesDescription (0008,103E) not present (Type 2/3 - optional)", path);
            }
        }

        String seriesDate = seriesItem.getString(Tag.SeriesDate);
        String seriesTime = seriesItem.getString(Tag.SeriesTime);
        if (verbose && (seriesDate != null || seriesTime != null)) {
            result.addInfo("Series Date/Time present (Type 3 - optional)", path);
        }

        // Validate instance level
        Sequence sopSeq = seriesItem.getSequence(Tag.ReferencedSOPSequence);
        if (sopSeq == null || sopSeq.isEmpty()) {
            result.addError("ReferencedSOPSequence is missing in series item", path);
            return;
        }

        int instanceCount = 0;
        for (Attributes sopItem : sopSeq) {
            String sopPath = path + ".SOP[" + instanceCount + "]";
            validateInstanceLevel(sopItem, seriesItem, result, sopPath, verbose);
            instanceCount++;
        }
    }

    /**
     * Validate instance-level attributes per Appendix B Section B.1.
     *
     * Requirement V-ALT-02: The following attributes MUST be validated:
     * - Referenced SOP Class UID (0008,1150): Type 1 (Required)
     * - Referenced SOP Instance UID (0008,1155): Type 1 (Required)
     * - Instance Number (0020,0013): Type 3 (Optional)
     * - Number of Frames (0028,0008): Type 3 but strongly recommended for multi-frame
     * - Retrieve Location UID (0040,E011): Type R+ at instance level if differs from series
     */
    private static void validateInstanceLevel(Attributes sopItem, Attributes seriesItem,
                                             ValidationResult result, String path, boolean verbose) {
        // V-ALT-02: Referenced SOP Class UID - Type 1 (Required)
        String sopClassUID = sopItem.getString(Tag.ReferencedSOPClassUID);
        if (sopClassUID == null || sopClassUID.trim().isEmpty()) {
            result.addError("Appendix B Requirement V-ALT-02: ReferencedSOPClassUID (0008,1150) is Type 1 (Required) " +
                          "in ReferencedSOPSequence but is missing", path);
        }

        // V-ALT-02: Referenced SOP Instance UID - Type 1 (Required)
        String sopInstanceUID = sopItem.getString(Tag.ReferencedSOPInstanceUID);
        if (sopInstanceUID == null || sopInstanceUID.trim().isEmpty()) {
            result.addError("Appendix B Requirement V-ALT-02: ReferencedSOPInstanceUID (0008,1155) is Type 1 (Required) " +
                          "in ReferencedSOPSequence but is missing", path);
        }

        // V-ALT-02: Instance Number - Type 3 (Optional)
        String instanceNumber = sopItem.getString(Tag.InstanceNumber);
        if (verbose && instanceNumber != null) {
            result.addInfo("Instance Number: " + instanceNumber, path);
        }

        // V-ALT-02: Number of Frames - Type 3 but critical for multi-frame objects
        String numberOfFrames = sopItem.getString(Tag.NumberOfFrames);
        if (numberOfFrames != null && !numberOfFrames.trim().isEmpty()) {
            try {
                int frames = Integer.parseInt(numberOfFrames.trim());
                if (frames > 1 && verbose) {
                    result.addInfo("Multi-frame object: " + frames + " frames", path);
                }
            } catch (NumberFormatException e) {
                result.addError("Number of Frames (0028,0008) has invalid format: " + numberOfFrames, path);
            }
        } else if (isMultiframeSOPClass(sopClassUID)) {
            result.addWarning("Appendix B: Number of Frames (0028,0008) is missing for likely multi-frame SOP Class. " +
                            "While Type 3 in macro, MADO logic strongly recommends this for bandwidth estimation.", path);
        }

        // V-ALT-02: Retrieve Location UID at instance level (if different from series)
        String instanceRetrieveUID = sopItem.getString(Tag.RetrieveLocationUID);
        if (instanceRetrieveUID != null && !instanceRetrieveUID.trim().isEmpty()) {
            // Instance-level retrieval location overrides series-level
            MADORetrievalValidator.validateRetrieveLocationUIDFormat(instanceRetrieveUID, result, path);

            String seriesRetrieveUID = seriesItem.getString(Tag.RetrieveLocationUID);
            if (seriesRetrieveUID != null && !seriesRetrieveUID.equals(instanceRetrieveUID)) {
                if (verbose) {
                    result.addInfo("Instance has different Retrieve Location UID than series " +
                                 "(instance-level overrides series-level)", path);
                }
            }
        }

        // Check Retrieve URL at instance level
        String instanceRetrieveURL = sopItem.getString(Tag.RetrieveURL);
        if (instanceRetrieveURL != null && !instanceRetrieveURL.trim().isEmpty()) {
            MADORetrievalValidator.validateRetrieveURLFormat(instanceRetrieveURL, result, path);
        }
    }

    /**
     * Check if a SOP Class UID represents a multi-frame image.
     */
    private static boolean isMultiframeSOPClass(String sopClassUID) {
        if (sopClassUID == null) {
            return false;
        }

        // Common multi-frame SOP Classes
        return sopClassUID.equals("1.2.840.10008.5.1.4.1.1.77.1.6") ||  // VL Whole Slide Microscopy
               sopClassUID.equals("1.2.840.10008.5.1.4.1.1.7.1") ||     // Multi-frame Single Bit
               sopClassUID.equals("1.2.840.10008.5.1.4.1.1.7.2") ||     // Multi-frame Grayscale Byte
               sopClassUID.equals("1.2.840.10008.5.1.4.1.1.7.3") ||     // Multi-frame Grayscale Word
               sopClassUID.equals("1.2.840.10008.5.1.4.1.1.7.4") ||     // Multi-frame True Color
               sopClassUID.equals("1.2.840.10008.5.1.4.1.1.2.1") ||     // Enhanced CT
               sopClassUID.equals("1.2.840.10008.5.1.4.1.1.4.1") ||     // Enhanced MR
               sopClassUID.equals("1.2.840.10008.5.1.4.1.1.130") ||     // Enhanced PET
               sopClassUID.contains("Enhanced");                         // Generic enhanced IOD check
    }
}

