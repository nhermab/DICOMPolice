package be.uzleuven.ihe.dicom.constants;

/**
 * Constants for validation messages used in DICOM structure validation.
 * Centralizes all error, warning, and info messages for the validator.
 * References IHE MADO Supplement (Rev 1.0) and DICOM PS3 standards.
 */
public class ValidationMessages {

    // ========== Specification References ==========
    public static final String REF_MADO_6_X_1_2_2_5_2 = "IHE MADO Suppl. 6.X.1.2.2.5.2";
    public static final String REF_MADO_6_X_1_2_3_4 = "IHE MADO Suppl. 6.X.1.2.3.4";
    public static final String REF_MADO_6_X_1_2_3_5 = "IHE MADO Suppl. 6.X.1.2.3.5";
    public static final String REF_MADO_6_X_1_2_2_3_2 = "IHE MADO Suppl. 6.X.1.2.2.3.2";
    public static final String REF_MADO_6_X_1_2_2_6 = "IHE MADO Suppl. 6.X.1.2.2.6";
    public static final String REF_MADO_6_X_1_2_1_2 = "IHE MADO Suppl. 6.X.1.2.1.2";
    public static final String REF_MADO_6_X_1_2_2_1_2 = "IHE MADO Suppl. 6.X.1.2.2.1.2";
    public static final String REF_MADO_6_X_1_2_2_2_2 = "IHE MADO Suppl. 6.X.1.2.2.2.2";
    public static final String REF_MADO_APPENDIX_A = "IHE MADO Suppl. Appx A";
    public static final String REF_MADO_APPENDIX_B = "IHE MADO Suppl. Appx B";
    public static final String REF_MADO_TABLE_B_1_2_1 = "IHE MADO Suppl. Table B.1.2-1";
    public static final String REF_MADO_TABLE_B_2_2_1 = "IHE MADO Suppl. Table B.2.2-1";
    public static final String REF_MADO_TABLE_B_3_2_1 = "IHE MADO Suppl. Table B.3.2-1";
    public static final String REF_MADO_TABLE_B_4_2_1 = "IHE MADO Suppl. Table B.4.2-1";
    public static final String REF_MADO_X_1_1_1 = "IHE MADO Suppl. X.1.1.1";
    public static final String REF_MADO_X_2 = "IHE MADO Suppl. X.2";
    public static final String REF_DICOM_PS3_3 = "DICOM PS3.3";
    public static final String REF_DICOM_PS3_5 = "DICOM PS3.5";
    public static final String REF_DICOM_PS3_10_SEC_7 = "DICOM PS3.10 Section 7";
    public static final String REF_DICOM_PS3_10_TABLE_7_1_1 = "DICOM PS3.10 Table 7.1-1";
    public static final String REF_DICOM_C_12_1_1_6 = "DICOM PS3.3 C.12.1.1.6";

    // ========== SOP Class Validation ==========
    public static final String TRANSFER_SYNTAX_ERROR = "CRITICAL: %s contains a Transfer Syntax UID (%s) instead of a SOP Class UID. " +
            "This is a common copy-paste error where Transfer Syntax UID was used where SOP Class UID belongs.";

    public static final String VERIFICATION_WARNING = "%s references Verification SOP Class (%s). " +
            "This is unusual in a KOS and might indicate a copy-paste error.";

    public static final String KNOWN_SOP_MSG = "%s references known SOP Class: %s";

    public static final String UNKNOWN_SOP_MSG = "%s references unknown or non-standard SOP Class UID: %s. " +
            "Verify this is a valid SOP Class UID.";

    // ========== Template Identification ==========
    public static final String MISSING_TEMPLATE_MSG = "ContentTemplateSequence (0040,A504) is missing. " +
            "For XDS-I.b compliance, this should explicitly identify TID 2010.";

    public static final String NO_TID2010_MSG = "ContentTemplateSequence does not contain TemplateIdentifier='2010'. " +
            "XDS-I.b Key Object Selection should use TID 2010.";

    public static final String TID2010_ERROR = "ContentTemplateSequence has TemplateIdentifier=2010 but MappingResource is not 'DCMR'. " +
            "Expected MappingResource='DCMR'. [" + REF_MADO_6_X_1_2_2_5_2 +
            ": 'The TID 2010 'Key Object Selection' Template shall include the TID 1600 'Image Library' Template...']";

    public static final String TID2010_INFO = "ContentTemplateSequence correctly identifies TID 2010 (DCMR)";

    // ========== Document Title / Concept Name ==========
    public static final String CONCEPT_NAME_MISSING = "ConceptNameCodeSequence is missing. [" + REF_MADO_6_X_1_2_2_5_2 +
            ": 'The CID 7010 Key Object Selection Document Title shall be set to 'Manifest with Description'...']";

    public static final String DOCUMENT_TITLE_WRONG = "Document Title must be 'Manifest with Description' for MADO manifests. [" +
            REF_MADO_6_X_1_2_2_5_2 + ": 'The SR Document Content Module shall be constructed from TID 2010... " +
            "The CID 7010 Key Object Selection Document Title shall be set to 'Manifest with Description'...']";

    // ========== Content Item Constraints ==========
    public static final String CONTENT_ITEM_MISSING = "In TID 1600 'Image Library' required content items are missing: %s. [" +
            REF_MADO_6_X_1_2_2_5_2 + ": 'In the TID 1600 'Image Library' the following content Items shall be present... " +
            "[Modality R+, Study Instance UID R+, Target Region R+]']";

    public static final String CODE_SEQUENCE_SINGLE_ITEM = "Code Sequence %s must contain exactly one item. [" + REF_DICOM_PS3_3 +
            ": 'Only a single Item shall be included in this Sequence.']";

    // ========== UID Hierarchy ==========
    public static final String STUDY_UID_MISSING_EVIDENCE = "Study Instance UID missing in Evidence Sequence. [" +
            REF_MADO_6_X_1_2_3_4 + ", " + REF_MADO_TABLE_B_1_2_1 +
            ": 'This sequence shall contain as many Related Series Sequences (0008,1250) as there are Series...']";

    public static final String SERIES_HIERARCHY_ERROR = "Series hierarchy error: %s. [" + REF_MADO_6_X_1_2_3_4 +
            ": '[Referenced SOP Sequence] shall contain as many instance UID as there are instances...']";

    // ========== MADO Profile Detection ==========
    public static final String MADO_APPROACH_DETECTION = "Detecting MADO approach (KOS vs FHIR). [" + REF_MADO_X_2 +
            ": 'The Manifest Content Creator shall support both formats... The Imaging Document Consumer shall support at least one of the two formats.']";

    // ========== Forbidden Elements ==========
    public static final String FORBIDDEN_ELEMENT = "Element %s (Purpose of reference) shall not be present in MADO manifests. [" +
            REF_MADO_APPENDIX_A + " (TID 2010 Mod): 'Row 11 Purpose of reference shall not be present.']";

    // ========== Referenced Request Sequence ==========
    public static final String REFERENCED_REQUEST_MISSING = "Referenced Request Sequence (0040,A370) is missing or empty. [" +
            REF_MADO_6_X_1_2_2_3_2 + ": 'Referenced Request Sequence (0040,A370) ... R+ ... One or more Items shall be included in this Sequence.']";

    public static final String REFERENCED_REQUEST_EMPTY = "Referenced Request Sequence (0040,A370) must contain at least one item. [" +
            REF_MADO_6_X_1_2_2_3_2 + ": 'One or more Items shall be included in this Sequence.']";

    // ========== Retrieval Information ==========
    public static final String RETRIEVE_LOCATION_UID_MISSING = "Expected RetrieveLocationUID (0040,E011) for MADO manifest. [" +
            REF_MADO_6_X_1_2_3_5 + ", " + REF_MADO_TABLE_B_2_2_1 +
            ": 'Retrieve Location UID (0040,E011) ... R+ ... The value of this attribute is an OID that may be used as a reference to obtain the endpoint...']";

    public static final String RETRIEVE_URL_INFO = "Retrieve URL (0008,1190) is optional but recommended for simplified access. [" +
            REF_MADO_6_X_1_2_3_5 + ": 'Retrieve URL (0008,1190) ... O']";

    // ========== Character Set Validation ==========
    public static final String SPECIFIC_CHARACTER_SET_REQUIRED = "Specific Character Set (0008,0005) is required when using non-ASCII characters. [" +
            REF_DICOM_PS3_5 + ": 'Specific Character Set (0008,0005) ... shall be present if the Data Set contains characters " +
            "that are not part of the Default Character Repertoire.']";

    // ========== Empty Sequences ==========
    public static final String EMPTY_SEQUENCE_MSG = "%s (%s) is present but has zero length. [" + REF_DICOM_PS3_5 +
            ": 'A Sequence of Items shall be encoded as... Sequence Items.' (General DICOM encoding rule; Sequences marked R/R+ must not be empty).]";

    // ========== Digital Signatures ==========
    public static final String SIGNED_MANIFEST_SIGNATURE_MISSING = "Document Title indicates 'Signed Manifest' but Digital Signatures Sequence is missing or empty. [" +
            REF_MADO_APPENDIX_A + " (CID 7010): 'Code Meaning: Signed Manifest... Definition: A signed list of objects... " +
            "referenced securely with either Digital Signatures or MACs.']";

    public static final String DIGITAL_SIGNATURES_EMPTY = "Digital Signatures Sequence (FFFA,FFFA) is present but empty. [" +
            REF_DICOM_C_12_1_1_6 + ": 'Digital Signatures Sequence (FFFA,FFFA)... One or more Items shall be included in this Sequence.']";

    // ========== Evidence Orphan Validation ==========
    public static final String ORPHAN_REFERENCES_DETECTED = "ORPHAN REFERENCES DETECTED: Instances referenced in content but missing from Evidence Sequence. [" +
            REF_MADO_X_1_1_1 + ": 'The Content Creator and the Imaging Document Source are expected to ensure that the imaging study " +
            "DICOM instances referenced by the Imaging Study Manifest are consistent.']";

    // ========== Evidence Sequence ==========
    public static final String EVIDENCE_SEQUENCE_MISSING = "Evidence Sequence (CurrentRequestedProcedureEvidenceSequence 0040,A375) is missing. [" +
            REF_MADO_APPENDIX_A + ": 'Current Requested Procedure Evidence Sequence (0040,A375)... R' (Modified KOS definition in MADO).]";

    public static final String EVIDENCE_SEQUENCE_EMPTY = "Evidence Sequence is present but empty. [" + REF_MADO_6_X_1_2_1_2 +
            ": 'Evidence Sequence ... Usage M ... IHE Usage M' (Table 6.X.1.2.1.2-1).]";

    // ========== Timezone ==========
    public static final String TIMEZONE_OFFSET_MANDATORY = "TimezoneOffsetFromUTC (0008,0201) is mandatory in MADO manifests. [" +
            REF_MADO_6_X_1_2_2_6 + ": 'Timezone Offset From UTC (0008,0201) ... R+ ... Contains the offset from UTC to the timezone for all DA and TM Attributes...']";

    public static final String TIMEZONE_OFFSET_INCONSISTENT = "TimezoneOffsetFromUTC values are inconsistent across sequences: %s. [" +
            REF_MADO_6_X_1_2_2_6 + ": Timezone must be consistent throughout the manifest.]";

    // ========== Part 10 File Format ==========
    public static final String PART10_FILE_FORMAT_VIOLATION = "DICOM Part 10 File Format Violation: %s. [" +
            REF_DICOM_PS3_10_SEC_7 + ": 'The File Meta Information... shall be present in every DICOM file.']";

    public static final String MEDIA_STORAGE_SOP_CLASS_MISMATCH = "MediaStorageSOPClassUID (0002,0002) does not match SOPClassUID (0008,0016). [" +
            REF_DICOM_PS3_10_TABLE_7_1_1 + ": 'Media Storage SOP Class UID (0002,0002) ... Uniquely identifies the SOP Class associated with the Data Set.']";

    // ========== Patient IE ==========
    public static final String PATIENT_ID_REQUIRED = "Patient ID (0010,0020) is required (R+) in MADO manifests. [" +
            REF_MADO_6_X_1_2_2_1_2 + ": 'Patient ID (0010,0020) ... R+']";

    public static final String ISSUER_OF_PATIENT_ID_QUALIFIERS = "Issuer of Patient ID Qualifiers Sequence (0010,0024) is required (R+). [" +
            REF_MADO_6_X_1_2_2_1_2 + ": 'Issuer of Patient ID Qualifiers Sequence (0010,0024) ... R+ ... Only a single Item shall be included in this Sequence.']";

    // ========== Study IE ==========
    public static final String ACCESSION_NUMBER_CONSTRAINT = "Accession Number (0008,0050) shall be empty when there are multiple accession numbers for the study. [" +
            REF_MADO_6_X_1_2_2_2_2 + ": 'Accession Number (0008,0050) ... Shall be empty when there are multiple accession numbers for the study']";

    public static final String ISSUER_OF_ACCESSION_NUMBER_REQUIRED = "Issuer of Accession Number Sequence is required (RC+) when Accession Number is not empty. [" +
            REF_MADO_6_X_1_2_2_2_2 + ": 'Issuer of Accession Number Sequence ... RC+ Required if Accession Number is not empty.']";

    // ========== Equipment IE ==========
    public static final String MANUFACTURER_REQUIRED = "Manufacturer (0008,0070) is required (R+) in MADO manifests. [" +
            REF_MADO_6_X_1_2_2_3_2 + ": 'Manufacturer (0008,0070) ... R+']";

    public static final String INSTITUTION_NAME_REQUIRED = "Institution Name (0008,0080) is required (R+) in MADO manifests. [" +
            REF_MADO_6_X_1_2_2_3_2 + ": 'Institution Name (0008,0080) ... R+']";

    // ========== TID 1600 Image Library ==========
    public static final String TID1600_IMAGE_LIBRARY_EMPTY = "TID 1600 Image Library container is present but empty. [" +
            REF_MADO_6_X_1_2_2_5_2 + ": 'In the TID 1600 'Image Library' the following content Items shall be present... [TID 1602 Group R+]']";

    public static final String TID1600_GROUP_MISSING_ATTRIBUTES = "Image Library Group is missing required attributes: %s. [" +
            REF_MADO_6_X_1_2_2_5_2 + ": 'The following content Items shall be present in the 'Image Library Group'... " +
            "Modality [121139] R+... Series Date [ddd003] R+... Series Instance UID [ddd006] R+']";

    public static final String TID1600_ENTRY_INVALID = "Image Library Entry validation failed: %s. [" +
            REF_MADO_6_X_1_2_2_5_2 + ": 'The following content Items shall be present in the 'Image Library Entry'... " +
            "Instance Number [ddd005] RC+ ... Number of Frames [121140] RC+']";

    public static final String TID1600_KOS_REFERENCE_MISSING = "KOS reference is missing required KOS Title Code. [" +
            REF_MADO_6_X_1_2_2_5_2 + ": 'The following content Items shall be present... when the related instance is a KOS... " +
            "KOS Title Code [ddd008] R+... SOP Instance UID [ddd007] R+']";

    public static final String TID1600_STUDY_UID_MISSING = "TID 1600 Requirement: Study Instance UID is missing. [" +
            REF_MADO_6_X_1_2_2_5_2 + ": 'In the TID 1600 'Image Library' the following content Items shall be present... Study Instance UID [ddd011] R+']";

    // ========== Appendix B Validation ==========
    public static final String APPENDIX_B_EVIDENCE_MISSING = "CurrentRequestedProcedureEvidenceSequence is missing (Appendix B format). [" +
            REF_MADO_6_X_1_2_2_3_2 + ": 'Current Requested Procedure Evidence Sequence (0040,A375) ... R']";

    public static final String APPENDIX_B_STUDY_UID_MISSING = "StudyInstanceUID missing in Evidence (Appendix B format). [" +
            REF_MADO_APPENDIX_B + ".1: 'Study Instance UID (0020,000D) ... R ... Copy of the referenced study's Study Instance UID.']";

    public static final String APPENDIX_B_SERIES_MISSING = "ReferencedSeriesSequence is missing in Evidence study item. [" +
            REF_MADO_APPENDIX_B + ".2: 'Referenced Series Sequence (0008,1115) ... R ... Contains references to Series.']";

    public static final String APPENDIX_B_MODALITY_MISSING = "Appendix B Requirement V-ALT-01: Modality (0008,0060) is Type 1 (Required) in Referenced Series Sequence. [" +
            REF_MADO_TABLE_B_3_2_1 + ": 'Modality (0008,0060) ... R+']";

    public static final String APPENDIX_B_SERIES_UID_MISSING = "Appendix B Requirement V-ALT-01: SeriesInstanceUID (0020,000E) is Type 1 (Required) in Referenced Series Sequence. [" +
            REF_MADO_TABLE_B_3_2_1 + ": 'Series Instance UID (ddd006) ... R+']";

    public static final String APPENDIX_B_SOP_SEQUENCE_MISSING = "ReferencedSOPSequence is missing in series item. [" +
            REF_MADO_APPENDIX_B + ".3: 'Referenced SOP Sequence (0008,1199) ... R ... Contains references to SOP Instances.']";

    public static final String APPENDIX_B_SOP_CLASS_MISSING = "Appendix B Requirement V-ALT-02: ReferencedSOPClassUID (0008,1150) is Type 1 (Required) in Referenced SOP Sequence. [" +
            REF_MADO_TABLE_B_4_2_1 + ": 'Referenced SOP Class UID (0008,1150) ... R']";

    public static final String APPENDIX_B_SOP_INSTANCE_MISSING = "Appendix B Requirement V-ALT-02: ReferencedSOPInstanceUID (0008,1155) is Type 1 (Required) in Referenced SOP Sequence. [" +
            REF_MADO_TABLE_B_4_2_1 + ": 'Referenced SOP Instance UID (0008,1155) ... R']";

    public static final String APPENDIX_B_NUMBER_OF_FRAMES_INVALID = "Number of Frames (0028,0008) has invalid format: %s. [" +
            REF_MADO_TABLE_B_4_2_1 + ": 'Number of Frames (0028,0008) ... RC+ Required if the instance contains multi-frame pixel data.']";

    // ========== SOP Class / Header ==========
    public static final String SOP_CLASS_UID_MISSING = "SOPClassUID (0008,0016) is missing/empty. MADO manifest must be a KOS document. [" +
            REF_DICOM_PS3_3 + " A.35.3: 'SOP Class UID (0008,0016) ... M ... Uniquely identifies the SOP Class.']";

    public static final String SOP_CLASS_UID_WRONG = "SOPClassUID (0008,0016) must be Key Object Selection Document Storage (%s) but found: %s. [" +
            REF_MADO_6_X_1_2_1_2 + ": 'The SOP Class UID shall be Key Object Selection Document Storage (1.2.840.10008.5.1.4.1.1.88.59).']";

    // ========== Study IE (enhanced) ==========
    public static final String STUDY_DATE_REQUIRED = "StudyDate (0008,0020) is missing/empty. MADO requires Study Date. [" +
            REF_MADO_6_X_1_2_2_2_2 + ": 'Study Date (0008,0020) ... R+']";

    public static final String STUDY_TIME_REQUIRED = "StudyTime (0008,0030) is missing/empty. MADO requires Study Time. [" +
            REF_MADO_6_X_1_2_2_2_2 + ": 'Study Time (0008,0030) ... R+']";

    public static final String ACCESSION_NUMBER_MISSING = "AccessionNumber (0008,0050) is missing. MADO requires Accession Number (or empty if multiple via ReferencedRequestSequence). [" +
            REF_MADO_6_X_1_2_2_2_2 + ": 'Accession Number (0008,0050) ... R+']";

    public static final String ACCESSION_NUMBER_NOT_EMPTY_WITH_MULTIPLE = "AccessionNumber (0008,0050) must be empty when multiple requests/accessions are present (ReferencedRequestSequence has %d items). [" +
            REF_MADO_6_X_1_2_2_2_2 + ": 'Shall be empty when there are multiple accession numbers for the study']";

    public static final String ISSUER_ACCESSION_INCONSISTENT = "AccessionNumber is present but IssuerOfAccessionNumberSequence (0008,0051) is inconsistent (should match first Referenced Request issuer). [" +
            REF_MADO_6_X_1_2_2_2_2 + ": 'Issuer of Accession Number Sequence ... RC+ Required if Accession Number is not empty.']";

    public static final String ISSUER_ACCESSION_MISSING = "AccessionNumber attribute is present but IssuerOfAccessionNumberSequence (0008,0051) is missing. [" +
            REF_MADO_6_X_1_2_2_2_2 + ": 'Issuer of Accession Number Sequence ... RC+ Required if Accession Number is not empty.']";

    // ========== Private Attributes ==========
    public static final String PRIVATE_ATTRS_MSG = "Private attributes found in group %04X (%d elements). " +
            "XDS-I.b KOS objects should ideally be free of private tags for broad interoperability.";

    public static final String PRIVATE_NO_CREATOR_MSG = "Private group %04X has private elements but no " +
            "Private Creator Data Element (e.g., %04X,0010). The file structure may be corrupt.";

    public static final String NO_PRIVATE_ATTRS = "No private attributes found - clean for XDS sharing";

    // ========== Standard Values ==========
    public static final String DCMR = "DCMR";
    public static final String TID_2010 = "2010";
    public static final String VERIFICATION_SOP = "1.2.840.10008.1.1";

    private ValidationMessages() {
        // Utility class - prevent instantiation
    }
}

