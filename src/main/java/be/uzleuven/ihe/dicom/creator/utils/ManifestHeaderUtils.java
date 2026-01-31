package be.uzleuven.ihe.dicom.creator.utils;

import be.uzleuven.ihe.dicom.constants.DicomConstants;
import org.dcm4che3.data.*;

import static be.uzleuven.ihe.dicom.creator.utils.DicomCreatorUtils.*;
import static be.uzleuven.ihe.dicom.creator.utils.DicomSequenceUtils.*;

/**
 * Utility class for building common DICOM manifest headers (Patient, Study, Series modules).
 * Extracts common code from KOS and MADO creators to reduce duplication.
 */
public class ManifestHeaderUtils {

    private ManifestHeaderUtils() {
        // Utility class
    }

    /**
     * Configuration for manifest header attributes.
     */
    public static class HeaderConfig {
        public String sopClassUID = UID.KeyObjectSelectionDocumentStorage;
        public String sopInstanceUID;
        public String studyInstanceUID;
        public String seriesInstanceUID;
        public String modality = "KO";
        public int seriesNumber = 1;
        public int instanceNumber = 1;

        public String patientName;
        public String patientID;
        public String issuerOfPatientID;
        public String patientBirthDate;
        public String patientSex;
        public String patientIdIssuerOid;

        public String studyDate;
        public String studyTime;
        public String studyID;
        public String studyDescription;
        public String referringPhysicianName;
        public String accessionNumber;
        public String accessionNumberIssuerOid;

        public String seriesDate;
        public String seriesTime;
        public String seriesDescription;

        public String contentDate;
        public String contentTime;

        public String manufacturer = "DICOMPolice";
        public String manufacturerModelName;
        public String softwareVersions = "1.0";
        public String institutionName = "IHE Demo Hospital";

        public String timezoneOffset;
        public String specificCharacterSet = "ISO_IR 192"; // UTF-8

        public boolean includeTypeOfPatientID = false; // MADO-specific
    }

    /**
     * Populates SOP Common module attributes.
     */
    public static void populateSOPCommonModule(Attributes attrs, HeaderConfig config) {
        attrs.setString(Tag.SOPClassUID, VR.UI, config.sopClassUID);
        attrs.setString(Tag.SOPInstanceUID, VR.UI,
            config.sopInstanceUID != null ? config.sopInstanceUID : createNormalizedUid());

        if (config.specificCharacterSet != null) {
            attrs.setString(Tag.SpecificCharacterSet, VR.CS, config.specificCharacterSet);
        }
    }

    /**
     * Populates Patient module attributes with IHE XDS-I.b qualifiers.
     */
    public static void populatePatientModule(Attributes attrs, HeaderConfig config) {
        attrs.setString(Tag.PatientID, VR.LO,
            config.patientID != null ? config.patientID : "UNKNOWN");

        // Format person name to fix retired formats
        String formattedPatientName = formatPersonName(
            config.patientName != null ? config.patientName : "UNKNOWN^PATIENT");
        attrs.setString(Tag.PatientName, VR.PN, formattedPatientName);

        if (config.patientBirthDate != null) {
            attrs.setString(Tag.PatientBirthDate, VR.DA, config.patientBirthDate);
        }

        if (config.patientSex != null) {
            attrs.setString(Tag.PatientSex, VR.CS, normalizePatientSex(config.patientSex));
        }

        // IssuerOfPatientID - Local namespace
        if (config.issuerOfPatientID != null) {
            attrs.setString(Tag.IssuerOfPatientID, VR.LO, config.issuerOfPatientID);
        }

        // IHE XDS-I.b requirement: IssuerOfPatientIDQualifiersSequence with OID
        if (config.patientIdIssuerOid != null) {
            addPatientIDQualifiers(attrs, config.patientIdIssuerOid);
        }

        // MADO-specific attribute
        if (config.includeTypeOfPatientID) {
            attrs.setString(Tag.TypeOfPatientID, VR.CS, DicomConstants.VALUE_TYPE_TEXT);
        }
    }

    /**
     * Populates Study module attributes.
     */
    public static void populateStudyModule(Attributes attrs, HeaderConfig config) {
        attrs.setString(Tag.StudyInstanceUID, VR.UI, config.studyInstanceUID);

        if (config.studyDate != null) {
            attrs.setString(Tag.StudyDate, VR.DA, config.studyDate);
        }

        if (config.studyTime != null) {
            attrs.setString(Tag.StudyTime, VR.TM, config.studyTime);
        }

        if (config.referringPhysicianName != null) {
            // Format person name to fix retired formats
            String formattedPhysicianName = formatPersonName(config.referringPhysicianName);
            attrs.setString(Tag.ReferringPhysicianName, VR.PN, formattedPhysicianName);
        }

        if (config.studyID != null) {
            attrs.setString(Tag.StudyID, VR.SH, config.studyID);
        }

        if (config.accessionNumber != null) {
            attrs.setString(Tag.AccessionNumber, VR.SH, config.accessionNumber);
        }

        if (config.studyDescription != null) {
            attrs.setString(Tag.StudyDescription, VR.LO, config.studyDescription);
        }

        // Add Accession Number Issuer if accession number present and OID provided
        if (config.accessionNumber != null && !config.accessionNumber.trim().isEmpty()
            && config.accessionNumberIssuerOid != null) {
            addAccessionNumberIssuer(attrs, config.accessionNumberIssuerOid);
        }
    }

    /**
     * Populates Series module attributes for SR/KO documents.
     */
    public static void populateSeriesModule(Attributes attrs, HeaderConfig config) {
        attrs.setString(Tag.SeriesInstanceUID, VR.UI,
            config.seriesInstanceUID != null ? config.seriesInstanceUID : createNormalizedUid());
        attrs.setString(Tag.Modality, VR.CS, config.modality);
        attrs.setString(Tag.SeriesNumber, VR.IS, String.valueOf(config.seriesNumber));

        if (config.seriesDate != null) {
            attrs.setString(Tag.SeriesDate, VR.DA, config.seriesDate);
        }

        if (config.seriesTime != null) {
            attrs.setString(Tag.SeriesTime, VR.TM, config.seriesTime);
        }

        if (config.seriesDescription != null) {
            attrs.setString(Tag.SeriesDescription, VR.LO, config.seriesDescription);
        }

        // ReferencedPerformedProcedureStepSequence - Type 2 (can be empty)
        attrs.newSequence(Tag.ReferencedPerformedProcedureStepSequence, 0);
    }

    /**
     * Populates Equipment module attributes.
     */
    public static void populateEquipmentModule(Attributes attrs, HeaderConfig config) {
        attrs.setString(Tag.Manufacturer, VR.LO, config.manufacturer);

        if (config.manufacturerModelName != null) {
            attrs.setString(Tag.ManufacturerModelName, VR.LO, config.manufacturerModelName);
        }

        if (config.softwareVersions != null) {
            attrs.setString(Tag.SoftwareVersions, VR.LO, config.softwareVersions);
        }

        if (config.institutionName != null) {
            attrs.setString(Tag.InstitutionName, VR.LO, config.institutionName);
        }
    }

    /**
     * Populates SR Document General module attributes.
     */
    public static void populateSRDocumentModule(Attributes attrs, HeaderConfig config) {
        attrs.setString(Tag.InstanceNumber, VR.IS, String.valueOf(config.instanceNumber));

        if (config.contentDate != null) {
            attrs.setString(Tag.ContentDate, VR.DA, config.contentDate);
        }

        if (config.contentTime != null) {
            attrs.setString(Tag.ContentTime, VR.TM, config.contentTime);
        }

        // CompletionFlag and VerificationFlag - Required for SR
        /*attrs.setString(Tag.CompletionFlag, VR.CS, "COMPLETE");
        attrs.setString(Tag.VerificationFlag, VR.CS, "UNVERIFIED");
        */

        // TimezoneOffsetFromUTC - Highly recommended for XDS-I.b
        if (config.timezoneOffset != null) {
            attrs.setString(Tag.TimezoneOffsetFromUTC, VR.SH, config.timezoneOffset);
        }
    }

    /**
     * Populates Referenced Study Sequence (Type 2).
     * Can be empty but must exist for IHE XDS-I.b compliance.
     */
    public static void populateReferencedStudySequence(Attributes target) {
        // Type 2 - create empty sequence
        target.newSequence(Tag.ReferencedStudySequence, 0);
    }

    /**
     * Populates Referenced Request Sequence with all required Type 2 attributes.
     * Called by both KOS and MADO creators to ensure IHE XDS-I.b compliance.
     */
    public static void populateReferencedRequestSequence(
        Attributes target,
        String studyInstanceUID,
        String accessionNumber,
        String accessionNumberIssuerOid
    ) {
        Sequence refRequestSeq = target.newSequence(Tag.ReferencedRequestSequence, 1);
        Attributes reqItem = new Attributes();

        // Accession Number (Type 2)
        reqItem.setString(Tag.AccessionNumber, VR.SH, accessionNumber != null ? accessionNumber : "");

        // Accession Number Issuer
        if (accessionNumberIssuerOid != null && !accessionNumberIssuerOid.isEmpty()) {
            Sequence issuerAccSeq = reqItem.newSequence(Tag.IssuerOfAccessionNumberSequence, 1);
            Attributes issuerAcc = new Attributes();
            issuerAcc.setString(Tag.UniversalEntityID, VR.UT, accessionNumberIssuerOid);
            issuerAcc.setString(Tag.UniversalEntityIDType, VR.CS, "ISO");
            issuerAccSeq.add(issuerAcc);
        }

        // Study Instance UID (Type 1)
        reqItem.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUID);

        // Type 2 attributes - must exist even if empty
        reqItem.setString(Tag.RequestedProcedureID, VR.SH, "");
        reqItem.setString(Tag.RequestedProcedureDescription, VR.LO, "");
        reqItem.setString(Tag.FillerOrderNumberImagingServiceRequest, VR.LO, "");

        // Requested Procedure Code Sequence (Type 2 - empty sequence OK)
        reqItem.newSequence(Tag.RequestedProcedureCodeSequence, 0);

        refRequestSeq.add(reqItem);
    }

    /**
     * Normalizes Patient Sex to valid DICOM values (M, F, O).
     */
    private static String normalizePatientSex(String sex) {
        if (sex == null || sex.trim().isEmpty()) {
            return "O";
        }
        String normalized = sex.trim().toUpperCase();
        if (normalized.equals("M") || normalized.equals("F") || normalized.equals("O")) {
            return normalized;
        }
        return "O"; // Default to Other
    }
}
