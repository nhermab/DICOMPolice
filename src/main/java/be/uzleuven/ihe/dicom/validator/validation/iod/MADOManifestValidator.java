package be.uzleuven.ihe.dicom.validator.validation.iod;

import be.uzleuven.ihe.dicom.constants.CodeConstants;
import be.uzleuven.ihe.dicom.constants.DicomConstants;
import be.uzleuven.ihe.dicom.constants.ValidationMessages;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import be.uzleuven.ihe.dicom.validator.utils.MADOProfileUtils;
import be.uzleuven.ihe.dicom.validator.model.ValidationResult;
import be.uzleuven.ihe.dicom.validator.validation.MADOComplianceChecker;

import static be.uzleuven.ihe.dicom.constants.CodeConstants.CODE_MANIFEST_WITH_DESCRIPTION;

/**
 * Validator for MADO (Manifest-based Access to DICOM Objects) profile.
 * MADO extends KOS with TID 1600 Image Library template for enhanced metadata.
 * Used in XDS and MHD deployments with richer study/series/instance descriptors.
 */
public class MADOManifestValidator extends KeyObjectSelectionValidator {

    // MADO uses different document title than XDS-I.b
    private static final String MADO_MANIFEST_CSD = "DCM";
    private static final String MADO_MANIFEST_CODE_MEANING = "Manifest with Description";

    private static final String PROFILE_IHE_MADO = "IHEMADO";

    public MADOManifestValidator() {
        super("MADO Manifest (Manifest-based Access to DICOM Objects)");
    }

    @Override
    public boolean canValidate(Attributes dataset) {
        // MADO uses same SOP Class as KOS but distinguished by document title
        // We accept it here and validate the specific title later
        return super.canValidate(dataset);
    }

    /**
     * Profile-aware validation for MADO.
     * If profile is null or not recognized, falls back to standard validation.
     */
    @Override
    public ValidationResult validate(Attributes dataset, boolean verbose, String profile) {
        if (profile == null || profile.isEmpty()) {
            return validate(dataset, verbose);
        }

        if (PROFILE_IHE_MADO.equalsIgnoreCase(profile)) {
            if (verbose) {
                ValidationResult result = new ValidationResult();
                result.addInfo("Validating MADO Manifest with profile IHEMADO");
                ValidationResult base = validate(dataset, verbose);
                result.merge(base);
                return result;
            }
            return validate(dataset, verbose);
        }

        // Unknown profile - fall back and add warning
        ValidationResult res = validate(dataset, verbose);
        res.addWarning("Unknown profile requested: " + profile + ". Performed standard MADO validation.", "Profile");
        return res;
    }

    @Override
    public ValidationResult validate(Attributes dataset, boolean verbose) {
        ValidationResult result = new ValidationResult();

        if (verbose) {
            result.addInfo("Starting MADO Manifest validation");
        }

        // Header constraints (MADO-specific)
        validateMADOHeaderConstraints(dataset, result);

        // MADO Compliance Check - High-level check of critical requirements
        MADOComplianceChecker.checkMADOCompliance(dataset, result, verbose);

        // Basic KOS validation + MADO overrides via polymorphism
        ValidationResult baseResult = super.validate(dataset, verbose);
        result.merge(baseResult);

        // MADO-specific IE validations
        validateMADOPatientIE(dataset, result, verbose);
        validateMADOStudyIE(dataset, result);
        validateMADOEquipmentIE(dataset, result, verbose);
        validateMADOSRDocumentContentModule(dataset, result, verbose);
        validateMADOSOPCommonModule(dataset, result, verbose);

        // MADO-specific validations (call after basic validation)
        MADOProfileUtils.validateMADOProfile(dataset, result, verbose, this);

        if (verbose) {
            result.addInfo("MADO Manifest validation complete");
        }

        return result;
    }

    private void validateMADOHeaderConstraints(Attributes dataset, ValidationResult result) {
        String modulePath = "Header";

        // SOP Class UID (0008,0016) must be KOS document
        String sopClassUID = normalizedUID(dataset.getString(Tag.SOPClassUID));
        if (sopClassUID == null || sopClassUID.isEmpty()) {
            result.addError(ValidationMessages.MADO_SOP_CLASS_UID_MISSING, modulePath);
        } else if (!DicomConstants.KEY_OBJECT_SELECTION_SOP_CLASS_UID.equals(sopClassUID)) {
            result.addError(String.format(ValidationMessages.MADO_SOP_CLASS_UID_WRONG,
                    DicomConstants.KEY_OBJECT_SELECTION_SOP_CLASS_UID, sopClassUID), modulePath);
        }
    }

    private void validateMADOPatientIE(Attributes dataset, ValidationResult result, boolean verbose) {
        if (verbose) {
            result.addInfo("Validating Patient IE for MADO");
        }

        String modulePath = "Patient";

        // Patient ID SHALL be present (global uniqueness achieved with Issuer qualifiers)
        String patientId = dataset.getString(Tag.PatientID);
        if (patientId == null || patientId.trim().isEmpty()) {
            result.addError(ValidationMessages.PATIENT_ID_REQUIRED, modulePath);
        }

        // MADO requires robust patient identification
        Sequence qualifiers = dataset.getSequence(Tag.IssuerOfPatientIDQualifiersSequence);
        if (qualifiers == null || qualifiers.isEmpty()) {
            result.addError(ValidationMessages.MADO_ISSUER_PATIENT_ID_QUALIFIERS_MISSING, modulePath);
        } else {
            // Ensure Universal Entity ID (0010,0032) and type ISO (0010,0033) are present
            Attributes first = qualifiers.get(0);
            String universalEntityId = first.getString(Tag.UniversalEntityID);
            String universalEntityIdType = first.getString(Tag.UniversalEntityIDType);

            if (universalEntityId == null || universalEntityId.trim().isEmpty()) {
                result.addError(ValidationMessages.MADO_UNIVERSAL_ENTITY_ID_MISSING, modulePath);
            }
            if (universalEntityIdType == null || universalEntityIdType.trim().isEmpty()) {
                result.addError(ValidationMessages.MADO_UNIVERSAL_ENTITY_ID_TYPE_MISSING, modulePath);
            } else if (!"ISO".equalsIgnoreCase(universalEntityIdType.trim())) {
                result.addError(String.format(ValidationMessages.MADO_UNIVERSAL_ENTITY_ID_TYPE_WRONG,
                        universalEntityIdType), modulePath);
            }
        }

        // Type of Patient ID validation
        if (dataset.contains(Tag.TypeOfPatientID)) {
            String typeOfPatientID = dataset.getString(Tag.TypeOfPatientID);
            if (!DicomConstants.VALUE_TYPE_TEXT.equals(typeOfPatientID)) {
                result.addWarning("TypeOfPatientID (0010,0022) should be 'TEXT' in MADO profile, found: " +
                        typeOfPatientID, modulePath);
            }
        }

        // Other Patient IDs Sequence recommended for regional/national identifiers
        if (!dataset.contains(Tag.OtherPatientIDsSequence)) {
            result.addInfo("OtherPatientIDsSequence (0010,1002) not present. Consider including " +
                    "national/regional/local patient identifiers for cross-border use.", modulePath);
        }
    }

    private void validateMADOStudyIE(Attributes dataset, ValidationResult result) {
        String modulePath = "Study";

        // Study Date & Time SHALL be present
        String studyDate = dataset.getString(Tag.StudyDate);
        if (studyDate == null || studyDate.trim().isEmpty()) {
            result.addError(ValidationMessages.MADO_STUDY_DATE_MISSING, modulePath);
        }
        String studyTime = dataset.getString(Tag.StudyTime);
        if (studyTime == null || studyTime.trim().isEmpty()) {
            result.addError(ValidationMessages.MADO_STUDY_TIME_MISSING, modulePath);
        }

        // Accession Number SHALL be present; if multiple accession numbers exist, it SHALL be empty.
        String accessionNumber = dataset.getString(Tag.AccessionNumber);
        if (accessionNumber == null) {
            // Type 2 in DICOM, but the MADO checklist says SHALL be present
            result.addError(ValidationMessages.MADO_ACCESSION_NUMBER_MISSING, modulePath);
        }

        // If multiple ReferencedRequestSequence items exist, accession should be empty
        Sequence refRequestSeq = dataset.getSequence(Tag.ReferencedRequestSequence);
        if (refRequestSeq != null && refRequestSeq.size() > 1) {
            if (accessionNumber != null && !accessionNumber.trim().isEmpty()) {
                result.addError(String.format(ValidationMessages.MADO_ACCESSION_NUMBER_NOT_EMPTY_WITH_MULTIPLE_REQUESTS,
                        refRequestSeq.size()), modulePath);
            }
        }

        // If Accession Number present and non-empty, require Issuer
        if (accessionNumber != null && !accessionNumber.trim().isEmpty()) {
            if (!dataset.contains(Tag.IssuerOfAccessionNumberSequence)) {
                result.addError(ValidationMessages.MADO_ISSUER_ACCESSION_INCONSISTENT, modulePath);
            }
        }

        // If AccessionNumber attribute is present (even blank), issuer is conditionally required (RC+).
        // Note: ReferencedRequestSequence has its own stricter issuer checks per item.
        if (dataset.contains(Tag.AccessionNumber) && !dataset.contains(Tag.IssuerOfAccessionNumberSequence)) {
            result.addError(ValidationMessages.MADO_ISSUER_ACCESSION_MISSING, modulePath);
        }
    }

    private void validateMADOEquipmentIE(Attributes dataset, ValidationResult result, boolean verbose) {
        String modulePath = "Equipment";

        // Manufacturer SHALL be present (base module check allows empty)
        String manufacturer = dataset.getString(Tag.Manufacturer);
        if (manufacturer == null || manufacturer.trim().isEmpty()) {
            result.addError(ValidationMessages.MANUFACTURER_REQUIRED, modulePath);
        }

        // Institution Name SHALL be present OR Institution Code Sequence should be provided
        String institutionName = dataset.getString(Tag.InstitutionName);
        Sequence institutionCodeSeq = dataset.getSequence(Tag.InstitutionCodeSequence);
        boolean hasInstitutionName = institutionName != null && !institutionName.trim().isEmpty();
        boolean hasInstitutionCode = institutionCodeSeq != null && !institutionCodeSeq.isEmpty();

        if (!hasInstitutionName && !hasInstitutionCode) {
            result.addError(ValidationMessages.INSTITUTION_NAME_REQUIRED, modulePath);
        }

        if (verbose) {
            result.addInfo("Validated MADO equipment creator identification (Manufacturer/InstitutionName/InstitutionCodeSequence)", modulePath);
        }
    }

    private void validateMADOSRDocumentContentModule(Attributes dataset, ValidationResult result, boolean verbose) {
        String modulePath = "SRDocumentContent";
        validateMADODocumentTitle(dataset, result, modulePath);
        if (verbose) {
            result.addInfo("Validated SR Document Content module for MADO (document title)", modulePath);
        }
    }

    private void validateMADOSOPCommonModule(Attributes dataset, ValidationResult result, boolean verbose) {
        String modulePath = "SOPCommon";
        // Timezone Offset From UTC - MANDATORY in MADO
        checkRequiredAttribute(dataset, Tag.TimezoneOffsetFromUTC, "TimezoneOffsetFromUTC", result, modulePath);
        if (verbose) {
            result.addInfo("Validated SOP Common module for MADO (TimezoneOffsetFromUTC)", modulePath);
        }
    }

    private void validateMADODocumentTitle(Attributes dataset, ValidationResult result, String modulePath) {
        Sequence seq = dataset.getSequence(Tag.ConceptNameCodeSequence);
        if (seq == null || seq.isEmpty()) {
            result.addError(ValidationMessages.MADO_CONCEPT_NAME_SEQUENCE_MISSING, modulePath);
            return;
        }

        Attributes item = seq.get(0);
        String codeValue = item.getString(Tag.CodeValue);
        String csd = item.getString(Tag.CodingSchemeDesignator);
        String meaning = item.getString(Tag.CodeMeaning);

        // MADO requires either (113030, DCM, "Manifest") OR (ddd001, DCM, "Manifest with Description")
        boolean isManifest = CodeConstants.CODE_KOS_MANIFEST.equals(codeValue) && "DCM".equals(csd);
        boolean isManifestWithDesc = CODE_MANIFEST_WITH_DESCRIPTION.equals(codeValue) && MADO_MANIFEST_CSD.equals(csd);

        if (!isManifest && !isManifestWithDesc) {
            result.addError(
                "MADO ConceptNameCodeSequence (Document Title) must be either:\n" +
                "  (113030, DCM, \"Manifest\") OR\n" +
                "  (" + CODE_MANIFEST_WITH_DESCRIPTION + ", " + MADO_MANIFEST_CSD + ", \"" + MADO_MANIFEST_CODE_MEANING + "\")\n" +
                "Found: (" + codeValue + ", " + csd + ", \"" + meaning + "\")\n" +
                "Note: Generic KOS titles like (113000, DCM, \"Of Interest\") are NOT valid for MADO.", modulePath);
        }
    }
}
