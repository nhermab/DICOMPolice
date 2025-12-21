package be.uzleuven.ihe.dicom.validator.validation.iod;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import be.uzleuven.ihe.dicom.validator.utils.MADOProfileUtils;
import be.uzleuven.ihe.dicom.validator.model.ValidationResult;

/**
 * Validator for MADO (Manifest with Descriptive Objects) profile.
 * MADO extends KOS with TID 1600 Image Library template for enhanced metadata.
 * Used in XDS and MHD deployments with richer study/series/instance descriptors.
 */
public class MADOManifestValidator extends KeyObjectSelectionValidator {

    // MADO uses different document title than XDS-I.b
    private static final String MADO_MANIFEST_CODE_VALUE = "ddd001";
    private static final String MADO_MANIFEST_CSD = "DCM";
    private static final String MADO_MANIFEST_CODE_MEANING = "Manifest with Description";

    private static final String PROFILE_IHE_MADO = "IHEMADO";

    public MADOManifestValidator() {
        super("MADO Manifest (Manifest with Descriptive Objects)");
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

        // Basic KOS validation + MADO overrides via polymorphism
        ValidationResult baseResult = super.validate(dataset, verbose);
        result.merge(baseResult);

        // MADO-specific IE validations
        validateMADOPatientIE(dataset, result, verbose);
        validateMADOStudyIE(dataset, result, verbose);
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

    private void validateMADOPatientIE(Attributes dataset, ValidationResult result, boolean verbose) {
        if (verbose) {
            result.addInfo("Validating Patient IE for MADO");
        }

        String modulePath = "Patient";

        // Patient ID SHALL be present (global uniqueness achieved with Issuer qualifiers)
        String patientId = dataset.getString(Tag.PatientID);
        if (patientId == null || patientId.trim().isEmpty()) {
            result.addError("PatientID (0010,0020) is missing/empty. MADO requires robust patient identification.", modulePath);
        }

        // MADO requires robust patient identification
        Sequence qualifiers = dataset.getSequence(Tag.IssuerOfPatientIDQualifiersSequence);
        if (qualifiers == null || qualifiers.isEmpty()) {
            result.addError("IssuerOfPatientIDQualifiersSequence (0010,0024) is missing/empty. " +
                    "MADO requires Universal Entity ID for global patient identification.", modulePath);
        } else {
            // Ensure Universal Entity ID (0010,0032) and type ISO (0010,0033) are present
            Attributes first = qualifiers.get(0);
            String universalEntityId = first.getString(Tag.UniversalEntityID);
            String universalEntityIdType = first.getString(Tag.UniversalEntityIDType);

            if (universalEntityId == null || universalEntityId.trim().isEmpty()) {
                result.addError("IssuerOfPatientIDQualifiersSequence[0].UniversalEntityID (0010,0032) is missing/empty. " +
                        "Expected an OID for global uniqueness.", modulePath);
            }
            if (universalEntityIdType == null || universalEntityIdType.trim().isEmpty()) {
                result.addError("IssuerOfPatientIDQualifiersSequence[0].UniversalEntityIDType (0010,0033) is missing/empty. " +
                        "Expected 'ISO'.", modulePath);
            } else if (!"ISO".equalsIgnoreCase(universalEntityIdType.trim())) {
                result.addError("IssuerOfPatientIDQualifiersSequence[0].UniversalEntityIDType (0010,0033) must be 'ISO' but found: " +
                        universalEntityIdType, modulePath);
            }
        }

        // Type of Patient ID validation
        if (dataset.contains(Tag.TypeOfPatientID)) {
            String typeOfPatientID = dataset.getString(Tag.TypeOfPatientID);
            if (!"TEXT".equals(typeOfPatientID)) {
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

    private void validateMADOStudyIE(Attributes dataset, ValidationResult result, boolean verbose) {
        if (verbose) {
            result.addInfo("Validating Study IE for MADO");
        }

        String modulePath = "Study";

        // Study Date & Time SHALL be present
        String studyDate = dataset.getString(Tag.StudyDate);
        if (studyDate == null || studyDate.trim().isEmpty()) {
            result.addError("StudyDate (0008,0020) is missing/empty. MADO requires Study Date.", modulePath);
        }
        String studyTime = dataset.getString(Tag.StudyTime);
        if (studyTime == null || studyTime.trim().isEmpty()) {
            result.addError("StudyTime (0008,0030) is missing/empty. MADO requires Study Time.", modulePath);
        }

        // Accession Number SHALL be present; if multiple accession numbers exist, it SHALL be empty.
        String accessionNumber = dataset.getString(Tag.AccessionNumber);
        if (accessionNumber == null) {
            // Type 2 in DICOM, but the MADO checklist says SHALL be present
            result.addError("AccessionNumber (0008,0050) is missing. MADO requires Accession Number (or empty if multiple via ReferencedRequestSequence).", modulePath);
        }

        // If multiple ReferencedRequestSequence items exist, accession should be empty
        Sequence refRequestSeq = dataset.getSequence(Tag.ReferencedRequestSequence);
        if (refRequestSeq != null && refRequestSeq.size() > 1) {
            if (accessionNumber != null && !accessionNumber.trim().isEmpty()) {
                result.addError("AccessionNumber (0008,0050) must be empty when multiple requests/accessions are present (ReferencedRequestSequence has " +
                        refRequestSeq.size() + " items).", modulePath);
            }
        }

        // If Accession Number present and non-empty, require Issuer
        if (accessionNumber != null && !accessionNumber.trim().isEmpty()) {
            if (!dataset.contains(Tag.IssuerOfAccessionNumberSequence)) {
                result.addError("AccessionNumber is present but IssuerOfAccessionNumberSequence (0008,0051) " +
                        "is missing. MADO requires issuer for accession numbers.", modulePath);
            }
        }
    }

    private void validateMADOEquipmentIE(Attributes dataset, ValidationResult result, boolean verbose) {
        String modulePath = "Equipment";

        // Manufacturer SHALL be present (base module check allows empty)
        String manufacturer = dataset.getString(Tag.Manufacturer);
        if (manufacturer == null || manufacturer.trim().isEmpty()) {
            result.addError("Manufacturer (0008,0070) is missing/empty. MADO requires it to identify the manifest creator.", modulePath);
        }

        // Institution Name SHALL be present
        String institutionName = dataset.getString(Tag.InstitutionName);
        if (institutionName == null || institutionName.trim().isEmpty()) {
            result.addError("InstitutionName (0008,0080) is missing/empty. MADO requires it.", modulePath);
        }

        if (verbose) {
            result.addInfo("Validated MADO equipment creator identification (Manufacturer/InstitutionName)", modulePath);
        }
    }

    private void validateMADOSRDocumentContentModule(Attributes dataset, ValidationResult result, boolean verbose) {
        String modulePath = "SRDocumentContent";
        validateMADODocumentTitle(dataset, result, modulePath);
    }

    private void validateMADOSOPCommonModule(Attributes dataset, ValidationResult result, boolean verbose) {
        String modulePath = "SOPCommon";
        // Timezone Offset From UTC - MANDATORY in MADO
        checkRequiredAttribute(dataset, Tag.TimezoneOffsetFromUTC, "TimezoneOffsetFromUTC", result, modulePath);
    }

    private void validateMADODocumentTitle(Attributes dataset, ValidationResult result, String modulePath) {
        Sequence seq = dataset.getSequence(Tag.ConceptNameCodeSequence);
        if (seq == null || seq.isEmpty()) {
            // Already reported by super
            return;
        }

        Attributes item = seq.get(0);
        String codeValue = item.getString(Tag.CodeValue);
        String csd = item.getString(Tag.CodingSchemeDesignator);
        String meaning = item.getString(Tag.CodeMeaning);

        boolean ok = MADO_MANIFEST_CODE_VALUE.equals(codeValue) &&
                     MADO_MANIFEST_CSD.equals(csd) &&
                     MADO_MANIFEST_CODE_MEANING.equalsIgnoreCase(meaning);

        if (!ok) {
            result.addError(
                "ConceptNameCodeSequence must be (" + MADO_MANIFEST_CODE_VALUE + ", " +
                MADO_MANIFEST_CSD + ", \"" + MADO_MANIFEST_CODE_MEANING + "\") for MADO manifest; found (" +
                codeValue + ", " + csd + ", \"" + meaning + "\")", modulePath);
        }
    }
}
