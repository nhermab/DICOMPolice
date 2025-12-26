package be.uzleuven.ihe.dicom.validator.validation.iod;

import be.uzleuven.ihe.dicom.validator.model.ValidationResult;
import be.uzleuven.ihe.dicom.validator.validation.AdvancedEncodingValidator;
import be.uzleuven.ihe.dicom.validator.validation.TimezoneValidator;
import be.uzleuven.ihe.dicom.validator.validation.AdvancedStructureValidator;
import be.uzleuven.ihe.dicom.validator.validation.EvidenceOrphanValidator;
import be.uzleuven.ihe.dicom.validator.validation.KOSComplianceChecker;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;

/**
 * Validator for Key Object Selection Document IOD.
 * Implements validation rules for DICOM Key Object Selection as defined in PS3.3.
 */
public class KeyObjectSelectionValidator extends AbstractIODValidator {

    private static final String KOS_SOP_CLASS_UID = UID.KeyObjectSelectionDocumentStorage;


    public KeyObjectSelectionValidator() {
        this("KeyObjectSelectionDocument");
    }

    protected KeyObjectSelectionValidator(String iodName) {
        super(iodName);
    }

    @Override
    public boolean canValidate(Attributes dataset) {
        String sopClassUID = normalizedUID(dataset.getString(Tag.SOPClassUID));
        return KOS_SOP_CLASS_UID.equals(sopClassUID);
    }

    @Override
    public ValidationResult validate(Attributes dataset, boolean verbose) {
        ValidationResult result = new ValidationResult();

        if (verbose) {
            result.addInfo("Validating Key Object Selection Document");
        }

        // KOS Compliance Check - High-level check of critical requirements
        // If a MADO profile is active, skip generic KOS compliance warnings (MADO uses TID 1600 structure).
        String activeProfile = AbstractIODValidator.getActiveProfile();
        if (!"IHEMADO".equalsIgnoreCase(activeProfile)) {
            KOSComplianceChecker.checkKOSCompliance(dataset, result, verbose);
        } else if (verbose) {
            result.addInfo("Skipping generic KOS compliance check because active profile is IHEMADO", "KOSCompliance");
        }

        // Verify SOPClassUID
        checkStringValue(dataset, Tag.SOPClassUID, "SOPClassUID", KOS_SOP_CLASS_UID, result, null);

        // Validate all IE modules
        KeyObjectModuleValidator.validatePatientIE(dataset, result, verbose, this);
        KeyObjectModuleValidator.validateStudyIE(dataset, result, verbose, this);
        KeyObjectModuleValidator.validateSeriesIE(dataset, result, verbose, this);
        KeyObjectModuleValidator.validateEquipmentIE(dataset, result, verbose, this);
        KeyObjectModuleValidator.validateDocumentIE(dataset, result, verbose, this);

        // Advanced validation for Connectathon compliance
        performAdvancedValidation(dataset, result, verbose);

        if (verbose) {
            result.addInfo("Validation complete for Key Object Selection Document");
        }

        return result;
    }

    /**
     * Profile-aware validation. Supports profile "IHEXDSIManifest" per user request.
     * If profile is null or not recognized falls back to the standard validate(dataset, verbose).
     */
    @Override
    public ValidationResult validate(Attributes dataset, boolean verbose, String profile) {
        if (profile == null || profile.isEmpty()) {
            return validate(dataset, verbose);
        }

        ValidationResult profileResult = KeyObjectProfileValidator.validateProfile(dataset, verbose, profile, this);
        if (profileResult != null) {
            return profileResult;
        }

        // Unknown profile - fall back and add a warning
        ValidationResult res = validate(dataset, verbose);
        res.addWarning("Unknown profile requested: " + profile + ". Performed standard validation.", "Profile");
        return res;
    }

    /**
     * Perform advanced validation checks for IHE XDS-I Connectathon compliance.
     * These are byte-level and structural checks that go beyond basic DICOM validation.
     */
    public void performAdvancedValidation(Attributes dataset, ValidationResult result, boolean verbose) {
        if (verbose) {
            result.addInfo("Performing advanced Connectathon validation checks");
        }

        // 1. Character Set & Encoding Validation
        AdvancedEncodingValidator.validateCharacterSetEncoding(dataset, result, "Encoding");
        AdvancedEncodingValidator.validateUIPadding(dataset, result, "Padding");
        AdvancedEncodingValidator.validateTextPadding(dataset, result, "Padding");

        // 2. Timezone Synchronization
        TimezoneValidator.validateTimezoneOffset(dataset, result, "Timezone");
        TimezoneValidator.validateContentTimeConsistency(dataset, result, "Timezone");
        TimezoneValidator.checkTimezoneConsistency(dataset, result, "Timezone");

        // 3. SOP Class Validation
        AdvancedStructureValidator.validateReferencedSOPClasses(dataset, result, "SOPClass");

        // 4. Template Identification (TID 2010)
        AdvancedStructureValidator.validateTemplateIdentification(dataset, result, "Template");

        // 5. Empty Sequence Validation
        AdvancedStructureValidator.validateEmptySequences(dataset, result, "Structure");

        // 6. Private Attributes Check
        AdvancedStructureValidator.validatePrivateAttributes(dataset, result, "PrivateAttributes");

        // 7. Evidence "Orphan" Check
        EvidenceOrphanValidator.validateNoOrphanReferences(dataset, result, "Evidence");
        if (verbose) {
            EvidenceOrphanValidator.reportEvidenceStructure(dataset, result, "Evidence");
        }

        if (verbose) {
            result.addInfo("Advanced validation checks complete");
        }
    }
}
