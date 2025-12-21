package be.uzleuven.ihe.dicom.validator.validation.iod;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import be.uzleuven.ihe.dicom.validator.model.ValidationResult;
import be.uzleuven.ihe.dicom.validator.utils.KeyObjectContentUtils;
import be.uzleuven.ihe.dicom.validator.utils.SRReferenceUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * Module validation logic extracted from KeyObjectSelectionValidator.
 */
public class KeyObjectModuleValidator {

    /**
     * Validate Patient IE - Module: Patient (Mandatory)
     */
    public static void validatePatientIE(Attributes dataset, ValidationResult result, boolean verbose, AbstractIODValidator validator) {
        if (verbose) {
            result.addInfo("Validating Patient IE");
        }

        // Patient Module - Type 2 and Type 1 attributes
        validator.checkType2Attribute(dataset, Tag.PatientName, "PatientName", result, "Patient");
        validator.checkType2Attribute(dataset, Tag.PatientID, "PatientID", result, "Patient");
        validator.checkType2Attribute(dataset, Tag.PatientBirthDate, "PatientBirthDate", result, "Patient");
        validator.checkType2Attribute(dataset, Tag.PatientSex, "PatientSex", result, "Patient");

        // IssuerOfPatientID - Type 3 in DICOM, but recommended for XDS-I.b federated environments
        if (!dataset.contains(Tag.IssuerOfPatientID)) {
            result.addWarning("IssuerOfPatientID (0010,0021) is missing. " +
                             "In XDS-I.b federated environments, this attribute is critical for " +
                             "patient identification across different domains.", "Patient");
        } else {
            String issuer = dataset.getString(Tag.IssuerOfPatientID);
            if (issuer == null || issuer.trim().isEmpty()) {
                result.addWarning("IssuerOfPatientID (0010,0021) is present but empty. " +
                                 "A valid issuer/assigning authority should be specified.", "Patient");
            }
        }

        // PatientSex enumerated values
        if (dataset.contains(Tag.PatientSex)) {
            validator.checkEnumeratedValue(dataset, Tag.PatientSex, "PatientSex",
                               new String[]{"M", "F", "O", ""}, result, "Patient");
        }
    }

    /**
     * Validate Study IE - Module: General Study (Mandatory)
     */
    public static void validateStudyIE(Attributes dataset, ValidationResult result, boolean verbose, AbstractIODValidator validator) {
        if (verbose) {
            result.addInfo("Validating Study IE");
        }

        // General Study Module
        validator.checkRequiredAttribute(dataset, Tag.StudyInstanceUID, "StudyInstanceUID", result, "Study");
        validator.checkUID(dataset, Tag.StudyInstanceUID, "StudyInstanceUID", result, "Study");

        validator.checkType2Attribute(dataset, Tag.StudyDate, "StudyDate", result, "Study");
        validator.checkType2Attribute(dataset, Tag.StudyTime, "StudyTime", result, "Study");
        validator.checkType2Attribute(dataset, Tag.ReferringPhysicianName, "ReferringPhysicianName", result, "Study");
        validator.checkType2Attribute(dataset, Tag.StudyID, "StudyID", result, "Study");
        validator.checkType2Attribute(dataset, Tag.AccessionNumber, "AccessionNumber", result, "Study");
    }

    /**
     * Validate Series IE - Module: Key Object Document Series (Mandatory)
     */
    public static void validateSeriesIE(Attributes dataset, ValidationResult result, boolean verbose, AbstractIODValidator validator) {
        if (verbose) {
            result.addInfo("Validating Series IE - Key Object Document Series Module");
        }

        // Key Object Document Series Module
        validator.checkRequiredAttribute(dataset, Tag.Modality, "Modality", result, "Series");
        validator.checkStringValue(dataset, Tag.Modality, "Modality", "KO", result, "Series");

        validator.checkRequiredAttribute(dataset, Tag.SeriesInstanceUID, "SeriesInstanceUID", result, "Series");
        validator.checkUID(dataset, Tag.SeriesInstanceUID, "SeriesInstanceUID", result, "Series");

        validator.checkRequiredAttribute(dataset, Tag.SeriesNumber, "SeriesNumber", result, "Series");

        // ReferencedPerformedProcedureStepSequence - Type 2
        validator.checkType2Attribute(dataset, Tag.ReferencedPerformedProcedureStepSequence,
                          "ReferencedPerformedProcedureStepSequence", result, "Series");
    }

    /**
     * Validate Equipment IE - Module: General Equipment (Mandatory)
     */
    public static void validateEquipmentIE(Attributes dataset, ValidationResult result, boolean verbose, AbstractIODValidator validator) {
        if (verbose) {
            result.addInfo("Validating Equipment IE - General Equipment Module");
        }

        // General Equipment Module - Type 2 attributes
        validator.checkType2Attribute(dataset, Tag.Manufacturer, "Manufacturer", result, "Equipment");
    }

    /**
     * Validate Document IE - Multiple modules
     */
    public static void validateDocumentIE(Attributes dataset, ValidationResult result, boolean verbose, AbstractIODValidator validator) {
        if (verbose) {
            result.addInfo("Validating Document IE");
        }

        validateKeyObjectDocumentModule(dataset, result, verbose, validator);
        validateSRDocumentContentModule(dataset, result, verbose, validator);
        validateSOPCommonModule(dataset, result, verbose, validator);
    }

    /**
     * Validate Key Object Document Module (Mandatory)
     */
    public static void validateKeyObjectDocumentModule(Attributes dataset, ValidationResult result, boolean verbose, AbstractIODValidator validator) {
        if (verbose) {
            result.addInfo("Validating Key Object Document Module");
        }

        String modulePath = "KeyObjectDocument";

        // Instance Number - Type 1
        validator.checkRequiredAttribute(dataset, Tag.InstanceNumber, "InstanceNumber", result, modulePath);

        // Content Date and Time - Type 1
        validator.checkRequiredAttribute(dataset, Tag.ContentDate, "ContentDate", result, modulePath);
        validator.checkRequiredAttribute(dataset, Tag.ContentTime, "ContentTime", result, modulePath);

        // ReferencedRequestSequence - Type 2
        validator.checkType2Attribute(dataset, Tag.ReferencedRequestSequence, "ReferencedRequestSequence",
                          result, modulePath);

        // CurrentRequestedProcedureEvidenceSequence - Type 1C
        // Required if there are referenced instances
        if (hasReferencedInstances(dataset)) {
            validator.checkRequiredAttribute(dataset, Tag.CurrentRequestedProcedureEvidenceSequence,
                                 "CurrentRequestedProcedureEvidenceSequence", result, modulePath);

            if (dataset.contains(Tag.CurrentRequestedProcedureEvidenceSequence)) {
                SRReferenceUtils.validateCurrentRequestedProcedureEvidenceSequence(dataset, result, modulePath, verbose, validator);
            }
        }

        // IdenticalDocumentsSequence - Type 1C
        // Required if KOS references instances from multiple studies
        validateIdenticalDocumentsSequence(dataset, result, modulePath, validator);

        // PertinentOtherEvidenceSequence - Type 1C (not always required)
    }

    /**
     * Validate IdenticalDocumentsSequence (Type 1C).
     * Required when KOS references instances from multiple studies - per DICOM PS3.3,
     * a KOS spanning studies must be duplicated into each study with cross-references.
     */
    public static void validateIdenticalDocumentsSequence(Attributes dataset, ValidationResult result, String modulePath, AbstractIODValidator validator) {
        Set<String> studyUIDs = collectStudyUIDsFromEvidence(dataset);

        if (studyUIDs.size() > 1) {
            // Multi-study KOS requires IdenticalDocumentsSequence
            if (!dataset.contains(Tag.IdenticalDocumentsSequence)) {
                result.addError("KOS references instances from " + studyUIDs.size() +
                              " studies but IdenticalDocumentsSequence (0040,A525) is missing. " +
                              "DICOM requires multi-study KOS to list duplicate instances for each study.", modulePath);
                return;
            }

            Sequence seq = dataset.getSequence(Tag.IdenticalDocumentsSequence);
            if (seq == null || seq.isEmpty()) {
                result.addError("IdenticalDocumentsSequence is present but empty for multi-study KOS", modulePath);
                return;
            }

            // Validate each item has required Study and SOP Instance UIDs
            for (int i = 0; i < seq.size(); i++) {
                Attributes item = seq.get(i);
                String itemPath = validator.buildPath(modulePath, "IdenticalDocumentsSequence", i);

                validator.checkRequiredAttribute(item, Tag.StudyInstanceUID, "StudyInstanceUID", result, itemPath);
                validator.checkUID(item, Tag.StudyInstanceUID, "StudyInstanceUID", result, itemPath);

                validator.checkRequiredAttribute(item, Tag.SeriesInstanceUID, "SeriesInstanceUID", result, itemPath);
                validator.checkUID(item, Tag.SeriesInstanceUID, "SeriesInstanceUID", result, itemPath);

                validator.checkRequiredAttribute(item, Tag.ReferencedSOPClassUID, "ReferencedSOPClassUID", result, itemPath);
                validator.checkUID(item, Tag.ReferencedSOPClassUID, "ReferencedSOPClassUID", result, itemPath);

                validator.checkRequiredAttribute(item, Tag.ReferencedSOPInstanceUID, "ReferencedSOPInstanceUID", result, itemPath);
                validator.checkUID(item, Tag.ReferencedSOPInstanceUID, "ReferencedSOPInstanceUID", result, itemPath);
            }
        } else if (dataset.contains(Tag.IdenticalDocumentsSequence)) {
            // Single-study KOS should not have IdenticalDocumentsSequence
            result.addWarning("IdenticalDocumentsSequence is present but KOS only references " +
                            "a single study. This sequence is only needed for multi-study KOS.", modulePath);
        }
    }

    /**
     * Collect all unique Study Instance UIDs from CurrentRequestedProcedureEvidenceSequence.
     */
    private static Set<String> collectStudyUIDsFromEvidence(Attributes dataset) {
        Set<String> studyUIDs = new HashSet<>();
        Sequence evidence = dataset.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence);
        if (evidence != null) {
            for (Attributes studyItem : evidence) {
                String studyUID = studyItem.getString(Tag.StudyInstanceUID);
                if (studyUID != null && !studyUID.trim().isEmpty()) {
                    studyUIDs.add(studyUID.trim());
                }
            }
        }
        return studyUIDs;
    }

    /**
     * Validate SR Document Content Module (Mandatory)
     */
    public static void validateSRDocumentContentModule(Attributes dataset, ValidationResult result, boolean verbose, AbstractIODValidator validator) {
        if (verbose) {
            result.addInfo("Validating SR Document Content Module");
        }

        String modulePath = "SRDocumentContent";

        // ValueType - Type 1
        validator.checkRequiredAttribute(dataset, Tag.ValueType, "ValueType", result, modulePath);
        validator.checkStringValue(dataset, Tag.ValueType, "ValueType", "CONTAINER", result, modulePath);

        // ConceptNameCodeSequence - Type 1
        validator.checkRequiredAttribute(dataset, Tag.ConceptNameCodeSequence, "ConceptNameCodeSequence",
                             result, modulePath);

        if (validator.checkSequenceAttribute(dataset, Tag.ConceptNameCodeSequence, "ConceptNameCodeSequence",
                                   true, result, modulePath)) {
            KeyObjectContentUtils.validateConceptNameCodeSequence(dataset, result, validator);
        }

        // ContinuityOfContent - Type 1
        validator.checkRequiredAttribute(dataset, Tag.ContinuityOfContent, "ContinuityOfContent", result, modulePath);
        validator.checkEnumeratedValue(dataset, Tag.ContinuityOfContent, "ContinuityOfContent",
                           new String[]{"SEPARATE"}, result, modulePath);

        // CompletionFlag / VerificationFlag (SR General)
        // In the wild, some KOS objects are missing these; keep generic validation permissive.
        if (!dataset.contains(Tag.CompletionFlag)) {
            result.addWarning("CompletionFlag is missing", modulePath);
        }
        if (dataset.contains(Tag.CompletionFlag)) {
            validator.checkEnumeratedValue(dataset, Tag.CompletionFlag, "CompletionFlag",
                new String[]{"COMPLETE", "PARTIAL"}, result, modulePath);
        }

        if (!dataset.contains(Tag.VerificationFlag)) {
            result.addWarning("VerificationFlag is missing", modulePath);
        }
        if (dataset.contains(Tag.VerificationFlag)) {
            validator.checkEnumeratedValue(dataset, Tag.VerificationFlag, "VerificationFlag",
                new String[]{"VERIFIED", "UNVERIFIED"}, result, modulePath);

            // If VERIFIED, must have Verifying Observer Sequence and Verification DateTime
            String verificationFlag = dataset.getString(Tag.VerificationFlag);
            if ("VERIFIED".equals(verificationFlag)) {
                if (!dataset.contains(Tag.VerifyingObserverSequence)) {
                    result.addError("VerificationFlag is 'VERIFIED' but VerifyingObserverSequence (0040,A073) is missing. " +
                                  "A verified KOS must include at least one verifying observer.", modulePath);
                } else {
                    Sequence seq = dataset.getSequence(Tag.VerifyingObserverSequence);
                    if (seq == null || seq.isEmpty()) {
                        result.addError("VerifyingObserverSequence is present but empty when VerificationFlag is 'VERIFIED'", modulePath);
                    } else {
                        validateVerifyingObserverSequence(seq, result, modulePath, validator);
                    }
                }

                if (!dataset.contains(Tag.VerificationDateTime)) {
                    result.addError("VerificationFlag is 'VERIFIED' but VerificationDateTime (0040,A030) is missing", modulePath);
                }
            }
        }

        // ContentTemplateSequence - Type 1C (required for Key Object)
        validator.checkRequiredAttribute(dataset, Tag.ContentTemplateSequence, "ContentTemplateSequence",
                             result, modulePath);

        if (validator.checkSequenceAttribute(dataset, Tag.ContentTemplateSequence, "ContentTemplateSequence",
                                   true, result, modulePath)) {
            KeyObjectContentUtils.validateContentTemplateSequence(dataset, result, validator);
        }

        // ContentSequence - Type 1C (typically required for KOS)
        if (dataset.contains(Tag.ContentSequence)) {
            KeyObjectContentUtils.validateContentSequence(dataset, result, validator);
            KeyObjectContentUtils.validateKOSRootContentConstraints(dataset, result);
        }
    }


    /**
     * Validate SOP Common Module (Mandatory)
     */
    public static void validateSOPCommonModule(Attributes dataset, ValidationResult result, boolean verbose, AbstractIODValidator validator) {
        if (verbose) {
            result.addInfo("Validating SOP Common Module");
        }

        String modulePath = "SOPCommon";

        // SOP Class UID - Type 1
        validator.checkRequiredAttribute(dataset, Tag.SOPClassUID, "SOPClassUID", result, modulePath);
        validator.checkUID(dataset, Tag.SOPClassUID, "SOPClassUID", result, modulePath);

        // SOP Instance UID - Type 1
        validator.checkRequiredAttribute(dataset, Tag.SOPInstanceUID, "SOPInstanceUID", result, modulePath);
        validator.checkUID(dataset, Tag.SOPInstanceUID, "SOPInstanceUID", result, modulePath);

        // Instance Creator UID - Type 3 (optional)
        if (dataset.contains(Tag.InstanceCreatorUID)) {
            validator.checkUID(dataset, Tag.InstanceCreatorUID, "InstanceCreatorUID", result, modulePath);
        }
    }

    /**
     * Validate Verifying Observer Sequence structure.
     * Required when VerificationFlag = VERIFIED.
     */
    public static void validateVerifyingObserverSequence(Sequence seq, ValidationResult result, String modulePath, AbstractIODValidator validator) {
        for (int i = 0; i < seq.size(); i++) {
            Attributes item = seq.get(i);
            String itemPath = validator.buildPath(modulePath, "VerifyingObserverSequence", i);

            // Verifying Observer Name - Type 2
            validator.checkType2Attribute(item, Tag.VerifyingObserverName, "VerifyingObserverName", result, itemPath);

            // Verification DateTime - Type 1 (at sequence level, but should be in each item or root)
            if (!item.contains(Tag.VerificationDateTime)) {
                result.addWarning("VerifyingObserverSequence item missing VerificationDateTime (0040,A030). " +
                                "This should be present at root or in each observer item.", itemPath);
            }

            // Verifying Organization - Type 2
            validator.checkType2Attribute(item, Tag.VerifyingOrganization, "VerifyingOrganization", result, itemPath);

            // Verifying Observer Identification Code Sequence - Type 3 (optional but validate if present)
            if (item.contains(Tag.VerifyingObserverIdentificationCodeSequence)) {
                Sequence codeSeq = item.getSequence(Tag.VerifyingObserverIdentificationCodeSequence);
                if (codeSeq != null && !codeSeq.isEmpty()) {
                    Attributes codeItem = codeSeq.get(0);
                    String codeItemPath = itemPath + " > VerifyingObserverIdentificationCodeSequence[1]";
                    validator.checkRequiredAttribute(codeItem, Tag.CodeValue, "CodeValue", result, codeItemPath);
                    validator.checkRequiredAttribute(codeItem, Tag.CodingSchemeDesignator, "CodingSchemeDesignator", result, codeItemPath);
                }
            }
        }
    }

    /**
     * Check if dataset references any instances
     */
    private static boolean hasReferencedInstances(Attributes dataset) {
        return dataset.contains(Tag.CurrentRequestedProcedureEvidenceSequence) ||
               dataset.contains(Tag.ContentSequence);
    }
}

