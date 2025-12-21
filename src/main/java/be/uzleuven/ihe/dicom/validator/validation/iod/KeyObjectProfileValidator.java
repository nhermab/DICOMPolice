package be.uzleuven.ihe.dicom.validator.validation.iod;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import be.uzleuven.ihe.dicom.validator.model.ValidationResult;
import be.uzleuven.ihe.dicom.validator.utils.XDSIManifestProfileUtils;

/**
 * Profile-aware validation logic extracted from KeyObjectSelectionValidator.
 */
public class KeyObjectProfileValidator {

    private static final String KOS_SOP_CLASS_UID = UID.KeyObjectSelectionDocumentStorage;
    private static final String PROFILE_IHE_XDSI_MANIFEST = "IHEXDSIManifest";

    public static ValidationResult validateProfile(Attributes dataset, boolean verbose, String profile, AbstractIODValidator validator) {
        if (PROFILE_IHE_XDSI_MANIFEST.equalsIgnoreCase(profile)) {
            ValidationResult result = new ValidationResult();
            if (verbose) result.addInfo("Validating Key Object Selection Document with profile IHEXDSIManifest");

            // Verify SOPClassUID
            validator.checkStringValue(dataset, Tag.SOPClassUID, "SOPClassUID", KOS_SOP_CLASS_UID, result, null);

            // Profile-level checks (stubs / minimal implementations):
            // - File meta information module (Usage C, conditional NeedModuleFileMetaInformation)
            //   We can't access File Meta in Attributes read via DicomInputStream.readDataset() directly here,
            //   so add a warning or conditional check: if the dataset contains FileMetaInformation elements (0x0002 group)
            //   then require specific tags; otherwise add an informational note. We'll implement a conservative check.

            // Check for presence of group 0x0002 attributes (File Meta Information) if present in dataset
            boolean hasFileMeta = false;
            for (int tag : new int[]{Tag.FileMetaInformationGroupLength, Tag.FileMetaInformationVersion, Tag.MediaStorageSOPClassUID, Tag.MediaStorageSOPInstanceUID, Tag.TransferSyntaxUID}) {
                if (dataset.contains(tag)) {
                    hasFileMeta = true;
                    break;
                }
            }

            if (hasFileMeta) {
                // Require key file meta tags (stub enforcement)
                validator.checkType2Attribute(dataset, Tag.MediaStorageSOPClassUID, "MediaStorageSOPClassUID", result, "FileMetaInformation");
                validator.checkType2Attribute(dataset, Tag.MediaStorageSOPInstanceUID, "MediaStorageSOPInstanceUID", result, "FileMetaInformation");
                validator.checkType2Attribute(dataset, Tag.TransferSyntaxUID, "TransferSyntaxUID", result, "FileMetaInformation");
            } else {
                // If file meta is not present in the dataset, add an informational message that FileMetaInformation
                // should be present in the DICOM file header when the actual file is available.
                result.addInfo("File Meta Information not present in the dataset read; if validating a file container, ensure FileMetaInformation is present", "FileMetaInformation");
            }

            // Patient IE must be Mandatory
            KeyObjectModuleValidator.validatePatientIE(dataset, result, verbose, validator);

            // ClinicalTrialSubject module - Usage U (conditional)
            // Stub: If ClinicalTrialSubject-related tags are present require them; otherwise it's optional.
            if (dataset.contains(Tag.ClinicalTrialSubjectID) || dataset.contains(Tag.ClinicalTrialTimePointID)) {
                validator.checkType2Attribute(dataset, Tag.ClinicalTrialSubjectID, "ClinicalTrialSubjectID", result, "Patient");
                validator.checkType2Attribute(dataset, Tag.ClinicalTrialTimePointID, "ClinicalTrialTimePointID", result, "Patient");
            }

            // Study IE must be Mandatory
            KeyObjectModuleValidator.validateStudyIE(dataset, result, verbose, validator);
            // PatientStudy module - Usage U (stub)
            // ClinicalTrialStudy module - Usage U (conditional)
            if (dataset.contains(Tag.ClinicalTrialTimePointID) || dataset.contains(Tag.ClinicalTrialSponsorName)) {
                // Stub checks - real implementation would validate all ClinicalTrialStudy tags
                result.addInfo("ClinicalTrialStudy module present (stub check)", "Study");
            }

            // Series IE must be Mandatory
            KeyObjectModuleValidator.validateSeriesIE(dataset, result, verbose, validator);
            // ClinicalTrialSeries - Usage U (conditional stub)
            if (dataset.contains(Tag.ClinicalTrialTimePointID)) {
                result.addInfo("ClinicalTrialSeries module present (stub check)", "Series");
            }

            // Equipment IE mandatory
            KeyObjectModuleValidator.validateEquipmentIE(dataset, result, verbose, validator);

            // Document IE modules mandatory
            KeyObjectModuleValidator.validateDocumentIE(dataset, result, verbose, validator);

            // IHEXDSIManifestProfile module - Usage M for this profile
            XDSIManifestProfileUtils.validateIHEXDSIManifestProfile(dataset, result, verbose, validator);

            // Advanced validation for Connectathon compliance
            // We need to call performAdvancedValidation from KeyObjectSelectionValidator.
            // Since we don't have access to it directly if it's protected/private in KeyObjectSelectionValidator,
            // we might need to move it to a utility or expose it.
            // Looking at KeyObjectSelectionValidator, performAdvancedValidation is protected.
            // But KeyObjectSelectionValidator is the 'validator' passed here.
            // However, AbstractIODValidator doesn't have performAdvancedValidation.
            // So we need to cast validator to KeyObjectSelectionValidator or move performAdvancedValidation to a utility.

            if (validator instanceof KeyObjectSelectionValidator) {
                 ((KeyObjectSelectionValidator) validator).performAdvancedValidation(dataset, result, verbose);
            }

            if (verbose) result.addInfo("Profile validation complete: IHEXDSIManifest");
            return result;
        }

        return null; // Profile not handled
    }
}

