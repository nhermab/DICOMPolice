package be.uzleuven.ihe.dicom.constants;

/**
 * Constants for validation messages used in DICOM structure validation.
 * Centralizes all error, warning, and info messages for the validator.
 * References IHE MADO Supplement (Rev 1.0) and DICOM PS3 standards.
 */
public class ValidationMessages {

    // Import code constants for use in message strings
    private static final String CODE_MANIFEST_WITH_DESCRIPTION = CodeConstants.CODE_MANIFEST_WITH_DESCRIPTION;
    private static final String CODE_SERIES_DESCRIPTION = CodeConstants.CODE_SERIES_DESCRIPTION;
    private static final String CODE_SERIES_DATE = CodeConstants.CODE_SERIES_DATE;
    private static final String CODE_SERIES_TIME = CodeConstants.CODE_SERIES_TIME;
    private static final String CODE_SERIES_NUMBER = CodeConstants.CODE_SERIES_NUMBER;
    private static final String CODE_SERIES_INSTANCE_UID = CodeConstants.CODE_SERIES_INSTANCE_UID;
    private static final String CODE_NUM_SERIES_RELATED_INSTANCES = CodeConstants.CODE_NUM_SERIES_RELATED_INSTANCES;
    private static final String CODE_INSTANCE_NUMBER = CodeConstants.CODE_INSTANCE_NUMBER;
    private static final String CODE_STUDY_INSTANCE_UID = CodeConstants.CODE_STUDY_INSTANCE_UID;
    private static final String CODE_SOP_INSTANCE_UID = CodeConstants.CODE_SOP_INSTANCE_UID;
    private static final String CODE_KOS_TITLE = CodeConstants.CODE_KOS_TITLE;
    // ddd009 not yet defined in CodeConstants, using literal for now
    private static final String CODE_KEY_OBJECT_DESCRIPTION = CodeConstants.CODE_KOS_OBJECT_DESCRIPTION;

    // ========== Specification References ==========
    public static final String REF_MADO_6_X_1_2_2_5_2 = "IHE MADO Suppl. 6.X.1.2.2.5.2";
    public static final String REF_MADO_6_X_1_2_3_4 = "IHE MADO Suppl. 6.X.1.2.3.4";
    public static final String REF_MADO_6_X_1_2_3_4_2 = "IHE MADO Suppl. 6.X.1.2.3.4.2";
    public static final String REF_MADO_6_X_1_2_3_5 = "IHE MADO Suppl. 6.X.1.2.3.5";
    public static final String REF_MADO_6_X_1_2_2_3_2 = "IHE MADO Suppl. 6.X.1.2.2.3.2";
    public static final String REF_MADO_TABLE_6_X_1_2_3_4_2_1 = "IHE MADO Suppl. Table 6.X.1.2.3.4.2-1";
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
    public static final String REF_DICOM_PS3_5_SEC_6_2 = "DICOM PS3.5 Section 6.2";
    public static final String REF_DICOM_PS3_5_SEC_7_4 = "DICOM PS3.5 Section 7.4";
    public static final String REF_DICOM_PS3_5_SEC_7_5 = "DICOM PS3.5 Section 7.5";
    public static final String REF_DICOM_PS3_3_C_17_3 = "DICOM PS3.3 C.17.3";
    public static final String REF_DICOM_PS3_3_C_17_6_2 = "DICOM PS3.3 C.17.6.2";
    public static final String REF_MADO_TABLE_6_X_1_2_2_1_1 = "IHE MADO Suppl. Table 6.X.1.2.2.1-1";
    public static final String REF_MADO_TABLE_6_X_1_2_2_2_1 = "IHE MADO Suppl. Table 6.X.1.2.2.2-1";

    // ========== IOD Attribute Validation (AbstractIODValidator) ==========
    public static final String IOD_MISSING_REQUIRED_ATTRIBUTE = "Missing required attribute: %s %s. [" +
            REF_DICOM_PS3_5_SEC_7_4 + ": 'Type 1 Data Elements... shall be present and shall have a valid value. " +
            "Type 1C... shall be present [under specified conditions]... and shall have a valid value.']";

    public static final String IOD_REQUIRED_ATTRIBUTE_EMPTY = "Required attribute is empty: %s %s. [" +
            REF_DICOM_PS3_5_SEC_7_4 + ": 'Type 1 Data Elements... shall be present and shall have a valid value.']";

    public static final String IOD_MISSING_TYPE2_ATTRIBUTE = "Missing Type 2 attribute: %s %s. [" +
            REF_DICOM_PS3_5_SEC_7_4 + ": 'Type 2 Data Elements... shall be present... and may be zero length.']";

    public static final String IOD_MISSING_CONDITIONAL_ATTRIBUTE = "Missing conditional attribute: %s %s. [" +
            REF_DICOM_PS3_3 + " (Module Definitions): 'Type 1C [or 2C]... Required if [Condition]...']";

    public static final String IOD_ATTRIBUTE_NULL = "Attribute %s %s is null. [" +
            REF_DICOM_PS3_5 + " (VR Definitions): 'The Value Multiplicity (VM)... specifies the number of Data Element values... [Must match VR constraints].']";

    public static final String IOD_STRING_VALUE_MISMATCH = "Attribute %s %s has value '%s' but expected '%s'. [" +
            REF_DICOM_PS3_3 + " Section C (Attribute Definitions): 'The Enumerated Value shall be one of the values defined in the list.']";

    public static final String IOD_ENUMERATED_VALUE_INVALID = "Attribute %s %s has invalid value '%s'. Expected one of: %s. [" +
            REF_DICOM_PS3_3 + " Section C (Attribute Definitions): 'The Enumerated Value shall be one of the values defined in the list.']";

    public static final String IOD_UID_FORMAT_INVALID = "Invalid UID format in %s %s: '%s'. [" +
            REF_DICOM_PS3_5_SEC_6_2 + ": 'Value Representation UI (Unique Identifier)... Character Repertoire: '0'-'9', '.' ... Length of Value: 64 bytes maximum.']";

    public static final String IOD_UID_STARTS_WITH_ZERO = "Invalid UID in %s %s: '%s' (starts with '0' which is reserved for OSI). [" +
            REF_DICOM_PS3_5_SEC_6_2 + ": 'UIDs starting with '0' are reserved for specific purposes.']";

    public static final String IOD_UID_COMPONENT_STARTS_WITH_ZERO = "Invalid UID in %s %s: '%s' (component starts with '0'). [" +
            REF_DICOM_PS3_5_SEC_6_2 + ": 'Each UID component must not start with zero unless it is zero itself.']";

    public static final String IOD_UID_EMPTY_COMPONENT = "Invalid UID in %s %s: '%s' (contains empty component). [" +
            REF_DICOM_PS3_5_SEC_6_2 + ": 'UID components must be non-empty.']";

    public static final String IOD_UID_EXCEEDS_64_CHARS = "Invalid UID in %s %s: '%s' (exceeds 64 character limit: %d chars). [" +
            REF_DICOM_PS3_5_SEC_6_2 + ": 'Length of Value: 64 bytes maximum.']";

    public static final String IOD_MISSING_REQUIRED_SEQUENCE = "Missing required sequence: %s %s. [" +
            REF_DICOM_PS3_5_SEC_7_5 + ": 'A Sequence of Items shall be encoded as... Sequence Items.' (Type 1 Sequences must be present).]";

    public static final String IOD_SEQUENCE_WRONG_VR = "Attribute %s %s has VR %s but expected SQ (Sequence). [" +
            REF_DICOM_PS3_5_SEC_7_5 + ": 'Sequences shall use VR of SQ.']";

    // ========== SOP Class Validation =========="
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

    // ========== KOS Content Validation ==========
    public static final String KOS_CONTENT_SEQUENCE_MISSING = "ContentSequence is missing or empty; KOS must contain at least one referenced object. [" +
            REF_DICOM_PS3_3 + " C.17.3: 'The Content Sequence (0040,A730) ... shall be present.']";

    public static final String KOS_NO_REFERENCES = "KOS ContentSequence has no IMAGE/COMPOSITE/WAVEFORM reference items (empty manifest). [" +
            REF_DICOM_PS3_3 + " C.17.6.2: 'The Key Object Selection Document Instance... shall contain references to one or more Composite SOP Instances.']";

    public static final String KOS_DISALLOWED_VALUE_TYPE = "Disallowed ValueType for KOS (TID 2010): %s. [" +
            REF_DICOM_PS3_3 + " TID 2010: 'The Value Type of the Content Item... shall be consistent with the constraints of TID 2010.']";

    public static final String KOS_PURPOSE_OF_REFERENCE_FORBIDDEN = "PurposeOfReferenceCodeSequence (0040,A170) must not be present in KOS references. [" +
            REF_MADO_APPENDIX_A + " (TID 2010 Mod): 'Row 11 Purpose of reference shall not be present.']";

    public static final String KOS_CONCEPT_NAME_SEQUENCE_EMPTY = "ConceptNameCodeSequence is empty. [" +
            REF_DICOM_PS3_3 + " C.17.3: 'Concept Name Code Sequence (0040,A043) ... Type 1 ... Shall be present.']";

    public static final String KOS_CONTENT_TEMPLATE_SEQUENCE_EMPTY = "ContentTemplateSequence is empty. [" +
            REF_DICOM_PS3_3 + " C.17.3: 'Content Template Sequence (0040,A504) ... Type 1C ... Required if the Content Item is constructed from a Template.']";

    public static final String KOS_DOCUMENT_TITLE_REQUIRES_MODIFIER = "Document Title (%s) requires Document Title Modifier but ContentSequence is missing. [" +
            REF_DICOM_PS3_3 + " CID 7010: 'If the Key Object Selection Document Title is 'Rejected for Quality Reasons', a modifier shall be present.']";

    public static final String KOS_MODIFIER_MISSING_CONCEPT_CODE = "Document Title Modifier found but ConceptCodeSequence is missing. [" +
            REF_DICOM_PS3_3 + " C.17.3: 'Concept Name Code Sequence... Type 1... Shall be present for content items that convey a coded concept.']";

    public static final String KOS_REJECTED_QUALITY_REQUIRES_MODIFIER = "Document Title 'Rejected for Quality Reasons' or 'Quality Issue' requires at least one Document Title Modifier. [" +
            REF_DICOM_PS3_3 + " CID 7010: 'Rejection codes shall include at least one modifier to specify the reason for rejection.']";

    public static final String KOS_BEST_IN_SET_REQUIRES_MODIFIER = "Document Title 'Best In Set' requires at least one Document Title Modifier indicating the selection criterion. [" +
            REF_DICOM_PS3_3 + " CID 7010: 'Best In Set shall be accompanied by modifiers describing the selection criteria.']";

    public static final String KOS_KEY_OBJECT_DESCRIPTION_CARDINALITY = "Found %d Key Object Description items. TID 2010 allows 0 or 1 Key Object Description (not multiple). [" +
            REF_DICOM_PS3_3 + " TID 2010: 'Key Object Description... cardinality 0-1.']";

    // ========== XDS-I.b Manifest Profile ==========
    public static final String XDSI_TEMPLATE_MAPPING_RESOURCE_WRONG = "ContentTemplateSequence.MappingResource must be 'DCMR' for XDS-I manifest. [" +
            "IHE RAD TF-3 (XDS-I.b): 'The Key Object Selection Document... shall use TID 2010 with MappingResource DCMR.']";

    public static final String XDSI_TEMPLATE_IDENTIFIER_WRONG = "ContentTemplateSequence.TemplateIdentifier must be '2010' for XDS-I manifest. [" +
            "IHE RAD TF-3 (XDS-I.b): 'The Key Object Selection Document... shall use TID 2010.']";

    public static final String XDSI_CONTENT_SEQUENCE_MISSING = "XDS-I manifest must contain a non-empty SR ContentSequence (0040,A730). [" +
            "IHE RAD TF-3 (XDS-I.b): 'The Content Sequence shall be present.']";

    public static final String XDSI_NO_INSTANCE_REFERENCES = "XDS-I manifest does not reference any SOP instances (no SR IMAGE/COMPOSITE refs and no Evidence refs). [" +
            "IHE RAD TF-3 (XDS-I.b): 'The manifest shall reference at least one SOP Instance.']";

    public static final String XDSI_NO_SR_REFERENCES = "XDS-I manifest has no SR IMAGE/COMPOSITE referenced SOP instances. [" +
            "IHE RAD TF-3 (XDS-I.b): 'The manifest shall include image references in the Content Sequence.']";

    public static final String XDSI_EVIDENCE_MISSING = "XDS-I manifest has SR references but CurrentRequestedProcedureEvidenceSequence is empty/missing. [" +
            REF_DICOM_PS3_3 + " C.17.2: 'All SOP Instances referenced in the Content Sequence shall be included in the Current Requested Procedure Evidence Sequence.']";

    public static final String XDSI_ORPHAN_REFERENCE = "Referenced SOP Instance UID present in SR content but missing from Evidence: %s. [" +
            REF_DICOM_PS3_3 + " C.17.2: 'All SOP Instances referenced in the Content Sequence shall be included in the Current Requested Procedure Evidence Sequence.']";

    public static final String XDSI_PIXEL_DATA_FORBIDDEN = "Pixel Data (7FE0,0010) is present. A KOS Imaging Manifest must not contain Pixel Data. [" +
            REF_DICOM_PS3_3 + " A.35.4: 'The Key Object Selection Document IOD... does not include the Image Pixel Module.']";

    public static final String XDSI_FLOAT_PIXEL_DATA_FORBIDDEN = "Float Pixel Data (7FE0,0008) is present. KOS must not contain pixel data. [" +
            REF_DICOM_PS3_3 + " A.35.4: 'The Key Object Selection Document IOD... does not include the Image Pixel Module.']";

    public static final String XDSI_DOUBLE_FLOAT_PIXEL_DATA_FORBIDDEN = "Double Float Pixel Data (7FE0,0009) is present. KOS must not contain pixel data. [" +
            REF_DICOM_PS3_3 + " A.35.4: 'The Key Object Selection Document IOD... does not include the Image Pixel Module.']";

    public static final String XDSI_WAVEFORM_DATA_FORBIDDEN = "Waveform Data (5400,1010) is present. KOS must not contain waveform data. [" +
            REF_DICOM_PS3_3 + " A.35.4: 'The Key Object Selection Document IOD... does not include the Waveform Module.']";

    public static final String XDSI_AUDIO_DATA_FORBIDDEN = "Audio Sample Data (003A,0200) is present. KOS must not contain audio data. [" +
            REF_DICOM_PS3_3 + " A.35.4: 'The Key Object Selection Document IOD... does not include audio data.']";

    public static final String XDSI_SPECTROSCOPY_DATA_FORBIDDEN = "Spectroscopy Data (5600,0020) is present. KOS must not contain spectroscopy data. [" +
            REF_DICOM_PS3_3 + " A.35.4: 'The Key Object Selection Document IOD... does not include spectroscopy data.']";

    public static final String XDSI_CONCEPT_NAME_MISSING = "ConceptNameCodeSequence is missing/empty; cannot verify XDS-I Manifest title. [" +
            REF_DICOM_PS3_3 + " C.17.3: 'Concept Name Code Sequence... Type 1... Shall be present.']";

    public static final String XDSI_IOCM_REJECTION_FORBIDDEN = "ConceptNameCodeSequence title code indicates an IOCM Rejection Note (CodeValue=%s); this is not allowed for an XDS-I Imaging Manifest. [" +
            "IHE RAD TF-3 (XDS-I.b): 'If the Key Object Selection Document is used as a Manifest... it shall not use codes associated with Rejection Notes.']";

    // ========== SR Reference Validation ==========
    public static final String SR_RELATIONSHIP_TYPE_MUST_BE_CONTAINS = "Top-level ContentSequence item RelationshipType must be CONTAINS for XDS-I manifest; found: %s. [" +
            REF_DICOM_PS3_3 + " TID 2010: 'Row 1... Relationship Type: CONTAINS.']";

    public static final String SR_SELF_REFERENCE_FORBIDDEN = "Manifest references itself (ReferencedSOPInstanceUID equals this KOS SOPInstanceUID). [" +
            REF_DICOM_PS3_3 + " C.17.2: 'Circular references are not permitted within the content tree.']";

    public static final String SR_DUPLICATE_REFERENCE = "Duplicate referenced SOP Instance UID in SR ContentSequence: %s. [" +
            REF_DICOM_PS3_3 + " C.17.2: 'Each SOP Instance should typically be referenced only once in the content tree.']";

    public static final String SR_RETRIEVE_URL_INVALID = "Retrieve URL (0008,1190) must be a valid absolute URL starting with http:// or https://. Found: %s. [" +
            REF_MADO_6_X_1_2_3_5 + ": 'Retrieve URL (0008,1190)... shall contain a URL that can be used to retrieve the instance.']";

    public static final String SR_RETRIEVE_URL_CONTAINS_SPACES = "Retrieve URL (0008,1190) contains spaces which are not allowed in URLs. [" +
            REF_MADO_6_X_1_2_3_5 + ": 'Retrieve URL shall conform to RFC 3986 URL format.']";

    public static final String SR_RETRIEVE_LOCATION_UID_INVALID = "Retrieve Location UID (0040,E011) is invalid. Must be a valid UID format (digits and dots, max 64 chars). [" +
            REF_MADO_6_X_1_2_3_4_2 + " (" + REF_MADO_TABLE_6_X_1_2_3_4_2_1 + "); " + REF_DICOM_PS3_5 + " Section 6.2: " +
            "'Retrieve Location UID (0040,E011) ... Type R+ ... The value of this attribute is an OID... " +
            "Value Representation UI (Unique Identifier)... Character Repertoire: '0'-'9', '.' ... Length of Value: 64 bytes maximum.']";

    // ========== UID Hierarchy =========="
    public static final String STUDY_UID_MISSING_EVIDENCE = "Study Instance UID missing in Evidence Sequence item %s. [" +
            REF_MADO_6_X_1_2_3_4 + ", " + REF_MADO_TABLE_B_1_2_1 +
            ": 'This sequence shall contain as many Related Series Sequences (0008,1250) as there are Series... " +
            "Study Instance UID (0020,000D) is required to establish the study-series-instance hierarchy.']";

    public static final String SERIES_UID_MISSING_EVIDENCE = "Series Instance UID missing in Evidence Sequence study %s, series %s. [" +
            REF_MADO_6_X_1_2_3_4 + ", " + REF_MADO_TABLE_B_2_2_1 +
            ": '[Referenced SOP Sequence] shall contain as many instance UID as there are instances... " +
            "Series Instance UID (0020,000E) is required to establish the series-instance relationship.']";

    public static final String SOP_INSTANCE_UID_MISSING_EVIDENCE = "SOP Instance UID (Referenced SOP Instance UID) missing in Evidence Sequence. [" +
            REF_MADO_6_X_1_2_3_4 + ", " + REF_MADO_TABLE_B_3_2_1 +
            ": 'Referenced SOP Instance UID (0008,1155) is Type 1 (Required) in Referenced SOP Sequence. " +
            "Each instance reference must include the SOP Instance UID to uniquely identify the DICOM object.']";

    public static final String SERIES_HIERARCHY_ERROR = "Series hierarchy error: %s. [" + REF_MADO_6_X_1_2_3_4 +
            ": '[Referenced SOP Sequence] shall contain as many instance UID as there are instances...']";

    // ========== MADO Profile Detection ==========
    public static final String MADO_APPROACH_DETECTION = "Detecting MADO approach (KOS vs FHIR). [" + REF_MADO_X_2 +
            ": 'The Manifest Content Creator shall support both formats... The Imaging Document Consumer shall support at least one of the two formats.']";

    public static final String MADO_APPENDIX_B_NOT_SUPPORTED = "MADO Format: Appendix B style attributes detected, but Approach 2 (TID 1600 Image Library) is required for this manifest. [" +
            REF_MADO_6_X_1_2_2_5_2 + ": 'The SR Document Content Module shall be constructed from TID 2010... " +
            "The TID 2010 'Key Object Selection' Template shall include the TID 1600 'Image Library' Template...']";

    public static final String MADO_APPROACH_2_NOT_DETECTED = "MADO Format: Unable to identify Approach 2 (TID 1600 Image Library). " +
            "This KOS does not meet MADO KOS-Based Imaging Study Manifest requirements. [" +
            REF_MADO_6_X_1_2_2_5_2 + ": 'In the TID 1600 'Image Library' the following content Items shall be present...']";

    public static final String MADO_CONTENT_SEQUENCE_MISSING_APPROACH = "ContentSequence is missing/empty. MADO requires TID 1600 Image Library in content tree. [" +
            REF_MADO_6_X_1_2_2_5_2 + ": 'The SR Document Content Module shall be constructed from TID 2010... " +
            "The TID 2010 template shall include the TID 1600 'Image Library' Template.']";

    public static final String MADO_FORMAT_DETECTION_FAILED = "MADO Format Detection Failed:\n" +
            "  - Expected: TID 1600 Image Library container (111028, DCM, \"Image Library\") in ContentSequence\n" +
            "  - Alternative: Appendix B extended attributes in Evidence sequence\n" +
            "  - Found: Neither. This appears to be a generic KOS, not a MADO manifest.\n" +
            "  Note: Flat ContentSequence with COMPOSITE references is NOT valid MADO structure. [" +
            REF_MADO_6_X_1_2_2_5_2 + ", " + REF_MADO_APPENDIX_B + "]";

    public static final String MADO_CONTENT_SEQUENCE_MISSING = "ContentSequence is missing/empty. MADO manifest must contain TID 1600 Image Library. [" +
            REF_MADO_6_X_1_2_2_5_2 + ": 'The SR Document Content Module shall be constructed from TID 2010... " +
            "ContentSequence (0040,A730) shall contain the TID 1600 Image Library structure.']";

    public static final String MADO_NO_INSTANCE_REFERENCES = "MADO manifest does not reference any instances (no content refs and no evidence refs). [" +
            REF_MADO_X_1_1_1 + ": 'The Content Creator and the Imaging Document Source are expected to ensure that the imaging study " +
            "DICOM instances referenced by the Imaging Study Manifest are consistent.']";

    public static final String MADO_CONTENT_MISSING_FROM_EVIDENCE = "SOP Instance UID present in Content but MISSING from Evidence: %s. " +
            "DICOM KOS standard requires ALL referenced instances to be listed in Evidence Sequence. [" +
            REF_DICOM_PS3_3 + " A.35.3.2: 'Current Requested Procedure Evidence Sequence (0040,A375)... " +
            "This Sequence shall reference all SOP Instances that compose the Key Object Selection.']";

    // ========== Forbidden Elements ==========
    public static final String FORBIDDEN_ELEMENT = "Element %s (Purpose of reference) shall not be present in MADO manifests. [" +
            REF_MADO_APPENDIX_A + " (TID 2010 Mod): 'Row 11 Purpose of reference shall not be present.']";

    public static final String FORBIDDEN_IOCM_TITLE = "Document title code indicates an IOCM Rejection Note (CodeValue=%s); " +
            "this is not allowed for a MADO sharing manifest. [" +
            REF_MADO_APPENDIX_A + " (CID 7010): 'MADO manifests shall use 'Manifest with Description' " +
            "or 'Signed Manifest'. IOCM rejection note codes are prohibited in cross-enterprise sharing contexts.']";

    // ========== Referenced Request Sequence ==========
    public static final String REFERENCED_REQUEST_MISSING = "Referenced Request Sequence (0040,A370) is missing or empty. [" +
            REF_MADO_6_X_1_2_2_3_2 + ": 'Referenced Request Sequence (0040,A370) ... R+ ... One or more Items shall be included in this Sequence.']";

    public static final String REFERENCED_REQUEST_EMPTY = "Referenced Request Sequence (0040,A370) must contain at least one item. [" +
            REF_MADO_6_X_1_2_2_3_2 + ": 'One or more Items shall be included in this Sequence.']";

    public static final String REFERENCED_REQUEST_MISSING_MADO = "ReferencedRequestSequence (0040,A370) is missing/empty. MADO requires request information " +
            "for each unique Accession Number/Placer Order combination. [" +
            REF_MADO_6_X_1_2_2_3_2 + ": 'Referenced Request Sequence (0040,A370) ... R+ ... One or more Items shall be included in this Sequence.']";

    public static final String REFERENCED_REQUEST_STUDY_UID_MISSING = "StudyInstanceUID missing/empty in ReferencedRequestSequence item %d. [" +
            REF_MADO_6_X_1_2_2_3_2 + ": 'Study Instance UID (0020,000D) ... R+ ... " +
            "Shall match the Study Instance UID of the Key Object Selection Document.']";

    public static final String REFERENCED_REQUEST_STUDY_UID_MISMATCH = "ReferencedRequestSequence item %d has StudyInstanceUID %s " +
            "which does not match manifest StudyInstanceUID %s. [" +
            REF_MADO_6_X_1_2_2_3_2 + ": 'Study Instance UID (0020,000D) ... R+ ... " +
            "Shall match the Study Instance UID of the Key Object Selection Document.']";

    public static final String REFERENCED_REQUEST_ACCESSION_MISSING = "AccessionNumber missing in ReferencedRequestSequence item %d. [" +
            REF_MADO_6_X_1_2_2_3_2 + ": 'Accession Number (0008,0050) ... R+ ... " +
            "Required to link the manifest to the originating imaging request.']";

    public static final String REFERENCED_REQUEST_ISSUER_MISSING = "IssuerOfAccessionNumberSequence (0008,0051) missing/empty in ReferencedRequestSequence item %d. [" +
            REF_MADO_6_X_1_2_2_3_2 + ": 'Issuer of Accession Number Sequence (0008,0051) ... RC+ ... " +
            "Required if Accession Number is present. Shall contain UniversalEntityID and UniversalEntityIDType.']";

    public static final String REFERENCED_REQUEST_UNIVERSAL_ENTITY_ID_MISSING = "IssuerOfAccessionNumberSequence[0].UniversalEntityID (0040,0032) missing/empty in ReferencedRequestSequence item %d. [" +
            REF_MADO_6_X_1_2_2_3_2 + ": 'Universal Entity ID (0040,0032) ... R+ ... " +
            "Required to uniquely identify the assigning authority using an OID, UUID, or other registered identifier.']";

    public static final String REFERENCED_REQUEST_UNIVERSAL_ENTITY_ID_TYPE_MISSING = "IssuerOfAccessionNumberSequence[0].UniversalEntityIDType (0040,0033) missing/empty in ReferencedRequestSequence item %d. [" +
            REF_MADO_6_X_1_2_2_3_2 + ": 'Universal Entity ID Type (0040,0033) ... R+ ... " +
            "Required. Shall be 'ISO' for OID-based identifiers.']";

    public static final String REFERENCED_REQUEST_UNIVERSAL_ENTITY_ID_TYPE_WRONG = "IssuerOfAccessionNumberSequence[0].UniversalEntityIDType (0040,0033) must be 'ISO' but found: %s. [" +
            REF_MADO_6_X_1_2_2_3_2 + ": 'Universal Entity ID Type (0040,0033) ... R+ ... " +
            "Shall be 'ISO' when UniversalEntityID contains an OID.']";

    public static final String REFERENCED_REQUEST_PLACER_ORDER_MISSING = "PlacerOrderNumber (0040,2016) missing/empty in ReferencedRequestSequence item %d. [" +
            REF_MADO_6_X_1_2_2_3_2 + ": 'Placer Order Number / Imaging Service Request (0040,2016) ... R+ ... " +
            "Required to link the manifest to the specific imaging order that requested the study.']";

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

    // ========== Advanced Encoding Validation ==========
    public static final String UTF8_COMBINED_WITH_OTHER_CHARSETS = "SpecificCharacterSet (0008,0005) contains ISO_IR 192 (UTF-8) combined with other character sets. UTF-8 must be the only value. [" +
            REF_MADO_6_X_1_2_1_2 + ": 'Specific Character Set (0008,0005)... MADO Actors shall support ISO_IR 192 (UTF-8).' " +
            "(Implies if UTF-8 is used, it should be the sole value for interoperability in this profile).]";

    public static final String ESCAPE_SEQUENCES_WITHOUT_ISO2022 = "Attribute %s %s contains escape sequences (0x1B) but no ISO 2022 character set is declared in SpecificCharacterSet (0008,0005). [" +
            REF_DICOM_PS3_5 + " Section 6.1.2.5.3: 'If the Specific Character Set attribute is absent... escape sequences shall not be used.']";

    public static final String UID_PADDED_WITH_SPACE = "UID attribute %s %s is incorrectly padded with SPACE (0x20). UIDs must be padded with NULL (0x00) if odd length. [" +
            REF_DICOM_PS3_5 + " Section 6.2: 'Value Representation UI (Unique Identifier)... If the length... is odd, the Value shall be padded with a single trailing NULL (00H) character.']";

    public static final String UID_HAS_SPACE_PADDING = "UID attribute %s %s has SPACE byte (0x20) padding. UIDs must be padded with NULL (0x00). [" +
            REF_DICOM_PS3_5 + " Section 6.2: 'Value Representation UI... shall be padded with a single trailing NULL (00H) character.']";

    public static final String TEXT_PADDED_WITH_NULL = "Text attribute %s %s (%s) is padded with NULL (0x00). Text VRs must be padded with SPACE (0x20). [" +
            REF_DICOM_PS3_5 + " Section 6.2: (For text VRs like LO, SH, ST, UT, etc.) 'If the length... is odd, the Value shall be padded with a single trailing SPACE (20H) character.']";

    // ========== Empty Sequences =========="
    public static final String EMPTY_SEQUENCE_MSG = "%s (%s) is present but has zero length. [" + REF_DICOM_PS3_5 +
            ": 'A Sequence of Items shall be encoded as... Sequence Items.' (General DICOM encoding rule; Sequences marked R/R+ must not be empty).]";

    // ========== Digital Signatures ==========
    public static final String SIGNED_MANIFEST_SIGNATURE_MISSING = "Document Title indicates 'Signed Manifest' but Digital Signatures Sequence is missing or empty. [" +
            REF_MADO_APPENDIX_A + " (CID 7010): 'Code Meaning: Signed Manifest... Definition: A signed list of objects... " +
            "referenced securely with either Digital Signatures or MACs.']";

    public static final String DIGITAL_SIGNATURES_EMPTY = "Digital Signatures Sequence (FFFA,FFFA) is present but empty. [" +
            REF_DICOM_C_12_1_1_6 + ": 'Digital Signatures Sequence (FFFA,FFFA)... One or more Items shall be included in this Sequence.']";

    public static final String SIGNATURE_MAC_ID_MISSING = "MAC ID Number (0400,0005) is missing. [" +
            REF_DICOM_C_12_1_1_6 + " (Table C.12-6): 'MAC ID Number (0400,0005) ... Type 1 ... Shall be present.']";

    public static final String SIGNATURE_UID_MISSING = "Digital Signature UID (0400,0100) is missing/empty/invalid. [" +
            REF_DICOM_C_12_1_1_6 + " (Table C.12-5): 'Digital Signature UID (0400,0100) ... Type 1 ... Shall be present.']";

    public static final String SIGNATURE_DATETIME_MISSING = "Digital Signature DateTime (0400,0105) is missing. [" +
            REF_DICOM_C_12_1_1_6 + ": 'Digital Signature DateTime (0400,0105) ... Type 1.']";

    public static final String SIGNATURE_CERTIFICATE_TYPE_MISSING = "Certificate Type (0400,0110) is missing/empty. [" +
            REF_DICOM_C_12_1_1_6 + ": 'Certificate Type (0400,0110) ... Type 1.']";

    public static final String SIGNATURE_CERTIFICATE_MISSING = "Certificate of Signer (0400,0115) is missing/empty. [" +
            REF_DICOM_C_12_1_1_6 + ": 'Certificate of Signer (0400,0115) ... Type 1.']";

    public static final String SIGNATURE_DATA_MISSING = "Signature (0400,0120) is missing/empty. [" +
            REF_DICOM_C_12_1_1_6 + ": 'Signature (0400,0120) ... Type 1.']";

    public static final String SIGNATURE_MAC_ALGORITHM_MISSING = "MAC Algorithm (0400,0015) is missing/empty. [" +
            REF_DICOM_C_12_1_1_6 + " (Table C.12-6): 'MAC Algorithm (0400,0015) ... Type 1.']";

    public static final String SIGNATURE_DATA_ELEMENTS_SIGNED_MISSING = "Data Elements Signed (0400,0020) is missing/empty. [" +
            REF_DICOM_C_12_1_1_6 + " (Table C.12-6): 'Data Elements Signed (0400,0020) ... Type 1.']";

    public static final String MAC_PARAMS_MAC_ID_MISSING = "MAC ID Number (0400,0005) is missing in MAC Parameters. [" +
            REF_DICOM_C_12_1_1_6 + " (Table C.12-6): 'MAC ID Number (0400,0005) ... Type 1.']";

    public static final String MAC_PARAMS_TRANSFER_SYNTAX_MISSING = "MAC Calculation Transfer Syntax UID (0400,0010) is missing. [" +
            REF_DICOM_C_12_1_1_6 + ": 'MAC Calculation Transfer Syntax UID (0400,0010) ... Type 1.']";

    public static final String MAC_PARAMS_ALGORITHM_MISSING = "MAC Algorithm (0400,0015) is missing. [" +
            REF_DICOM_C_12_1_1_6 + ": 'MAC Algorithm (0400,0015) ... Type 1.']";

    public static final String MAC_PARAMS_DATA_ELEMENTS_MISSING = "Data Elements Signed (0400,0020) is missing. [" +
            REF_DICOM_C_12_1_1_6 + ": 'Data Elements Signed (0400,0020) ... Type 1.']";

    // ========== Evidence Orphan Validation ==========
    public static final String ORPHAN_REFERENCES_DETECTED = "ORPHAN REFERENCES DETECTED: Instances referenced in content but missing from Evidence Sequence. [" +
            REF_MADO_X_1_1_1 + ": 'The Content Creator and the Imaging Document Source are expected to ensure that the imaging study " +
            "DICOM instances referenced by the Imaging Study Manifest are consistent.']";

    // ========== KOS Compliance Checker ==========
    public static final String KOS_COMPLIANCE_EVIDENCE_MISSING = "CRITICAL KOS COMPLIANCE FAILURE: CurrentRequestedProcedureEvidenceSequence is missing. [" +
            REF_DICOM_PS3_3 + " C.17.2: 'Current Requested Procedure Evidence Sequence (0040,A375)... Type 1... " +
            "One or more Items shall be included in this Sequence.']";

    public static final String KOS_COMPLIANCE_DOCUMENT_TITLE_MISSING = "ConceptNameCodeSequence is missing. [" +
            REF_DICOM_PS3_3 + " C.17.3: 'Concept Name Code Sequence (0040,A043)... Type 1... Shall be present.']";

    public static final String KOS_COMPLIANCE_DOCUMENT_TITLE_CODE_MISSING = "Document Title: CodeValue, CodeMeaning, or CodingSchemeDesignator is missing. [" +
            REF_DICOM_PS3_3 + " Section 8 (Code Sequence Macro): 'Code Value (0008,0100)... Type 1'; " +
            "'Code Meaning (0008,0104)... Type 1'; 'Coding Scheme Designator (0008,0102)... Type 1.']";

    // ========== MADO Appendix B Validation ==========
    public static final String APPENDIX_B_EVIDENCE_MISSING_CRITICAL = "CurrentRequestedProcedureEvidenceSequence is missing (Appendix B format). [" +
            REF_MADO_APPENDIX_B + ": 'Current Requested Procedure Evidence Sequence (0040,A375) ... Type R' " +
            "(MADO Appendix B relies on this sequence).]";

    public static final String APPENDIX_B_SERIES_MODALITY_MISSING = "Appendix B Requirement V-ALT-01: Modality (0008,0060) is missing in Referenced Series Sequence. [" +
            REF_MADO_APPENDIX_B + " Table B.3.2-1: 'Modality (0008,0060)... Type R+' (Added to Hierarchical SOP Instance Ref Macro).]";

    public static final String APPENDIX_B_SERIES_UID_MISSING_ERROR = "Appendix B Requirement V-ALT-01: SeriesInstanceUID (0020,000E) is missing in Referenced Series Sequence. [" +
            REF_MADO_APPENDIX_B + " Table B.3.2-1: 'Series Instance UID... Type R+' (Added to Hierarchical SOP Instance Ref Macro).]";

    public static final String APPENDIX_B_SOP_SEQUENCE_MISSING_ERROR = "ReferencedSOPSequence is missing in series item. [" +
            REF_MADO_APPENDIX_B + ".1: 'Referenced SOP Sequence... Type R' (Required by Hierarchical SOP Instance Reference Macro).]";

    public static final String APPENDIX_B_SOP_CLASS_MISSING_ERROR = "Appendix B Requirement V-ALT-02: ReferencedSOPClassUID (0008,1150) is missing. [" +
            REF_MADO_APPENDIX_B + ".1: 'Referenced SOP Class UID... Type 1' (Standard DICOM macro attribute).]";

    public static final String APPENDIX_B_SOP_INSTANCE_UID_MISSING = "Appendix B Requirement V-ALT-02: ReferencedSOPInstanceUID (0008,1155) is missing. [" +
            REF_MADO_APPENDIX_B + ".1: 'Referenced SOP Instance UID... Type 1.']";

    // ========== MADO Compliance Checker ==========
    public static final String MADO_COMPLIANCE_EVIDENCE_MISSING = "CRITICAL MADO COMPLIANCE FAILURE: Evidence Sequence is missing. [" +
            REF_MADO_6_X_1_2_1_2 + " (Table 6.X.1.2.1.2-1): 'Evidence Sequence ... Usage M ... IHE Usage M' (Required Module).]";

    public static final String MADO_COMPLIANCE_CONTENT_SEQUENCE_MISSING = "MADO COMPLIANCE FAILURE: ContentSequence is missing/empty. [" +
            REF_MADO_6_X_1_2_2_5_2 + ": 'The SR Document Content Module shall be constructed from TID 2010... including TID 1600.']";

    public static final String MADO_COMPLIANCE_DOCUMENT_TITLE_OF_INTEREST = "Document Title is (113000, DCM, 'Of Interest'). MADO requires more specific document title. [" +
            REF_MADO_6_X_1_2_2_5_2 + ": 'The CID 7010 Key Object Selection Document Title shall be set to 'Manifest with Description' (" + CODE_MANIFEST_WITH_DESCRIPTION + ")...' " +
            "(Not generic 'Of Interest').]";

    public static final String MADO_COMPLIANCE_TIMEZONE_MISSING = "TimezoneOffsetFromUTC (0008,0201) is missing. [" +
            REF_MADO_6_X_1_2_2_6 + ": 'Timezone Offset From UTC (0008,0201) ... Type R+.']";

    public static final String MADO_COMPLIANCE_INSTITUTION_NAME_MISSING = "InstitutionName (0008,0080) is missing. [" +
            "IHE MADO Suppl. Table 6.X.1.2.2.3.2-1: 'Institution Name (0008,0080) ... Type R+.']";

    public static final String MADO_COMPLIANCE_MANUFACTURER_MISSING = "Manufacturer (0008,0070) is missing. [" +
            "IHE MADO Suppl. Table 6.X.1.2.2.3.2-1: 'Manufacturer (0008,0070) ... Type R+.']";

    public static final String MADO_COMPLIANCE_ISSUER_PATIENT_ID_MISSING = "IssuerOfPatientIDQualifiersSequence (0010,0024) is MISSING. [" +
            REF_MADO_TABLE_6_X_1_2_2_1_1 + ": 'Issuer of Patient ID Qualifiers Sequence (0010,0024) ... Type R+.']";

    // ========== MADO Retrieval Validation ==========
    public static final String MADO_RETRIEVAL_NO_INFO = "No retrieval information found (no Retrieve URL, Retrieve Location UID, or Retrieve URI). [" +
            REF_MADO_6_X_1_2_3_5 + ": 'At least one of Retrieve URL (0008,1190) or Retrieve Location UID (0040,E011) shall be present.']";

    public static final String MADO_RETRIEVAL_URL_NO_SCHEME = "Retrieve URL has no scheme (e.g., http://, https://). [" +
            REF_DICOM_PS3_3 + " C.17.2: 'The value shall be a URL... adhering to RFC 3986.']";

    public static final String MADO_RETRIEVAL_URL_NO_HOST = "Retrieve URL has no host component. [" +
            REF_DICOM_PS3_3 + " C.17.2: 'The value shall be a URL... adhering to RFC 3986.']";

    public static final String MADO_RETRIEVAL_LOCATION_UID_INVALID_FORMAT = "Retrieve Location UID has invalid format (must be digits and dots, max 64 chars). [" +
            REF_DICOM_PS3_5 + " Section 6.2: 'Value Representation UI (Unique Identifier)... Character Repertoire: '0'-'9', '.' ... " +
            "Length of Value: 64 bytes maximum.']";

    public static final String MADO_RETRIEVAL_URI_NO_SCHEME = "Retrieve URI has no scheme. [" +
            "IHE MADO Suppl. 6.X.1.2.3.3.2: 'Retrieve URI (0040,E010)... The value of this attribute is a complete URL...']";

    // ========== MADO Timezone Validation ==========
    public static final String MADO_TIMEZONE_INVALID_FORMAT = "TimezoneOffsetFromUTC has invalid format (must be ±HHMM). [" +
            REF_DICOM_PS3_5 + " Section 6.2: 'Timezone Offset From UTC... format SHHmm... + or - followed by 4 digits.']";

    // ========== Part 10 File Format Validation ==========
    public static final String PART10_FILE_DOES_NOT_EXIST = "File does not exist. [" +
            REF_DICOM_PS3_10_SEC_7 + ": 'The DICOM File Format... shall consist of a 128-byte Preamble...']";

    public static final String PART10_FILE_TOO_SMALL = "File is too small to be a valid DICOM Part 10 file (minimum 132 bytes required). [" +
            REF_DICOM_PS3_10_SEC_7 + ": 'The DICOM File Format... shall consist of a 128-byte Preamble... followed by the File Meta Information...']";

    public static final String PART10_FORMAT_VIOLATION = "DICOM Part 10 File Format Violation: %s. [" +
            REF_DICOM_PS3_10_SEC_7 + ": 'Bytes 128-131 shall contain the character string 'DICM'.']";

    public static final String PART10_META_SOP_INSTANCE_MISMATCH = "File Meta Information: MediaStorageSOPInstanceUID (0002,0003) does not match SOPInstanceUID (0008,0018) in Data Set. [" +
            "DICOM PS3.10 Section 7.1: 'Media Storage SOP Instance UID (0002,0003) ... Uniquely identifies the SOP Instance... " +
            "shall be equal to the SOP Instance UID in the Data Set.']";

    // ========== Timezone Validation ==========
    public static final String TIMEZONE_INVALID_FORMAT_ERROR = "TimezoneOffsetFromUTC (%s) has invalid format (must be ±HHMM). [" +
            REF_DICOM_PS3_5 + " Section 6.2 (TM/DT VR): 'Format: &SHHmm... HH is in the range 00-13... mm is in the range 00-59.' " +
            "(Note: Range is nominally -12 to +14, strictly HHMM format).]";

    public static final String TIMEZONE_OFFSET_OUT_OF_RANGE = "TimezoneOffsetFromUTC (%s) has offset out of range (hours must be 00-14). [" +
            REF_DICOM_PS3_5 + " Section 6.2: 'HH is in the range 00-13' (nominally -12 to +14 for timezone offsets).]";

    public static final String TIMEZONE_MINUTES_OUT_OF_RANGE = "TimezoneOffsetFromUTC (%s) has minutes out of range (must be 00-59). [" +
            REF_DICOM_PS3_5 + " Section 6.2: 'mm is in the range 00-59.']";

    // ========== Evidence Sequence ==========
    public static final String EVIDENCE_SEQUENCE_MISSING = "Evidence Sequence (CurrentRequestedProcedureEvidenceSequence 0040,A375) is missing. [" +
            REF_MADO_APPENDIX_A + ": 'Current Requested Procedure Evidence Sequence (0040,A375)... R' (Modified KOS definition in MADO).]";

    public static final String EVIDENCE_SEQUENCE_EMPTY = "Evidence Sequence is present but empty. [" + REF_MADO_6_X_1_2_1_2 +
            ": 'Evidence Sequence ... Usage M ... IHE Usage M' (Table 6.X.1.2.1.2-1).]";

    // ========== Key Object Module Validation ==========
    public static final String KOS_EVIDENCE_SEQUENCE_MISSING = "CurrentRequestedProcedureEvidenceSequence (0040,A375) is MISSING. " +
            "Key Object Selection Document SHALL reference at least one SOP Instance. [" +
            REF_MADO_APPENDIX_A + "; " + REF_DICOM_PS3_3_C_17_6_2 + ": " +
            "'Current Requested Procedure Evidence Sequence (0040,A375)... Type 1... One or more Items shall be included in this Sequence.' (MADO enforces presence).]";

    public static final String KOS_EVIDENCE_SEQUENCE_EMPTY_ERROR = "CurrentRequestedProcedureEvidenceSequence is present but empty. " +
            "The sequence must contain at least one study reference. [" +
            REF_DICOM_PS3_3_C_17_6_2 + ": 'One or more Items shall be included in this Sequence.']";

    public static final String KOS_MULTI_STUDY_MISSING_IDENTICAL_DOCS = "KOS references instances from %d studies, but IdenticalDocumentsSequence (0040,A525) is missing. [" +
            REF_DICOM_PS3_3_C_17_6_2 + ": 'Identical Documents Sequence (0040,A525)... Required if the Key Object Selection Document " +
            "references SOP Instances from more than one Study.']";

    public static final String KOS_IDENTICAL_DOCS_SEQUENCE_EMPTY = "IdenticalDocumentsSequence is present but empty for multi-study KOS. [" +
            REF_DICOM_PS3_3_C_17_6_2 + ": 'Identical Documents Sequence (0040,A525)... Required if the Key Object Selection Document " +
            "references SOP Instances from more than one Study.']";

    public static final String KOS_VERIFICATION_FLAG_MISSING_OBSERVER = "VerificationFlag is 'VERIFIED' but VerifyingObserverSequence (0040,A073) is missing. [" +
            REF_DICOM_PS3_3_C_17_3 + ": 'Verifying Observer Sequence (0040,A073)... Required if Verification Flag (0040,A000) is VERIFIED.']";

    public static final String KOS_VERIFYING_OBSERVER_SEQUENCE_EMPTY = "VerifyingObserverSequence is present but empty when VerificationFlag is 'VERIFIED'. [" +
            REF_DICOM_PS3_3_C_17_3 + ": 'Verifying Observer Sequence shall contain at least one item when Verification Flag is VERIFIED.']";

    public static final String KOS_VERIFICATION_FLAG_MISSING_DATETIME = "VerificationFlag is 'VERIFIED' but VerificationDateTime (0040,A030) is missing. [" +
            REF_DICOM_PS3_3_C_17_3 + ": 'Verification DateTime (0040,A030)... Required if Verification Flag (0040,A000) is VERIFIED.']";

    // ========== MADO Manifest Validation ==========
    public static final String MADO_SOP_CLASS_UID_MISSING = "SOPClassUID (0008,0016) is missing/empty. MADO manifest must be a KOS document. [" +
            "IHE MADO Suppl. 6.X.1.2.1.1: 'DICOM PS 3.3: A.35.4 Key Object Selection Document IOD' " +
            "(Implicit requirement: SOP Class UID must match the IOD used).]";

    public static final String MADO_SOP_CLASS_UID_WRONG = "SOPClassUID (0008,0016) must be Key Object Selection Document Storage (%s) but found: %s. [" +
            "IHE MADO Suppl. 6.X.1.2.1.1: 'SOP Class UID shall be 1.2.840.10008.5.1.4.1.1.88.59 (Key Object Selection Document Storage).']";

    public static final String MADO_ISSUER_PATIENT_ID_QUALIFIERS_MISSING = "IssuerOfPatientIDQualifiersSequence (0010,0024) is missing/empty. " +
            "MADO requires robust patient identification. [" +
            REF_MADO_TABLE_6_X_1_2_2_1_1 + ": 'Issuer of Patient ID Qualifiers Sequence (0010,0024)... Type R+... " +
            "Only a single Item shall be included in this Sequence.']";

    public static final String MADO_UNIVERSAL_ENTITY_ID_MISSING = "IssuerOfPatientIDQualifiersSequence[0].UniversalEntityID (0010,0032) is missing/empty. " +
            "Required for robust patient ID matching across systems. [" +
            REF_MADO_TABLE_6_X_1_2_2_1_1 + ": 'Universal Entity ID (0010,0032)... Type R+... " +
            "Globally unique identifier (OID) for the Patient ID Assigning Authority.']";

    public static final String MADO_UNIVERSAL_ENTITY_ID_TYPE_MISSING = "IssuerOfPatientIDQualifiersSequence[0].UniversalEntityIDType (0010,0033) is missing/empty. " +
            "Required to specify the identifier type. [" +
            REF_MADO_TABLE_6_X_1_2_2_1_1 + ": 'Universal Entity ID Type (0010,0033)... Type R+... Fixed value: 'ISO'.']";

    public static final String MADO_UNIVERSAL_ENTITY_ID_TYPE_WRONG = "IssuerOfPatientIDQualifiersSequence[0].UniversalEntityIDType (0010,0033) must be 'ISO' but found: %s. [" +
            REF_MADO_TABLE_6_X_1_2_2_1_1 + ": 'Universal Entity ID Type (0010,0033)... Fixed value: 'ISO'.']";

    public static final String MADO_STUDY_DATE_MISSING = "StudyDate (0008,0020) is missing/empty. MADO requires Study Date. [" +
            REF_MADO_TABLE_6_X_1_2_2_2_1 + ": 'Study Date (0008,0020)... Type R+']";

    public static final String MADO_STUDY_TIME_MISSING = "StudyTime (0008,0030) is missing/empty. MADO requires Study Time. [" +
            REF_MADO_TABLE_6_X_1_2_2_2_1 + ": 'Study Time (0008,0030)... Type R+']";

    public static final String MADO_ACCESSION_NUMBER_MISSING = "AccessionNumber (0008,0050) is missing. MADO requires Accession Number (or empty if multiple via ReferencedRequestSequence). [" +
            REF_MADO_TABLE_6_X_1_2_2_2_1 + ": 'Accession Number (0008,0050)... Type R+']";

    public static final String MADO_ACCESSION_NUMBER_NOT_EMPTY_WITH_MULTIPLE_REQUESTS = "AccessionNumber (0008,0050) must be empty when multiple requests/accessions are present (ReferencedRequestSequence has %d items). [" +
            REF_MADO_TABLE_6_X_1_2_2_2_1 + ": 'Accession Number (0008,0050)... Shall be empty when there are multiple accession numbers for the study " +
            "(see Referenced Request Sequence).']";

    public static final String MADO_ISSUER_ACCESSION_INCONSISTENT = "AccessionNumber is present but IssuerOfAccessionNumberSequence (0008,0051) is inconsistent (should match first Referenced Request issuer). [" +
            REF_MADO_TABLE_6_X_1_2_2_2_1 + ": 'Issuer of Accession Number Sequence (0008,0051)... Required if Accession Number (0008,0050) is not empty.']";

    public static final String MADO_ISSUER_ACCESSION_MISSING = "AccessionNumber attribute is present but IssuerOfAccessionNumberSequence (0008,0051) is missing. " +
            "MADO requires issuer information for accession numbers. [" +
            REF_MADO_TABLE_6_X_1_2_2_2_1 + ": 'Issuer of Accession Number Sequence (0008,0051)... Required if Accession Number (0008,0050) is not empty.']";

    public static final String MADO_CONCEPT_NAME_SEQUENCE_MISSING = "ConceptNameCodeSequence is missing. MADO requires document title. [" +
            REF_MADO_6_X_1_2_2_5_2 + ": 'The CID 7010 Key Object Selection Document Title shall be set to 'Manifest with Description'...']";

    // ========== Timezone =========="
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
    public static final String TID1600_CONTAINER_NO_CONTENT = "TID 1600 Image Library container has no content items. [" +
            REF_MADO_6_X_1_2_2_5_2 + ": 'In the TID 1600 'Image Library' the following content Items shall be present...' (Implies the container must be populated).]";

    public static final String TID1600_IMAGE_LIBRARY_EMPTY = "TID 1600 Image Library is empty. Must contain at least one Image Library Group (TID 1602). [" +
            REF_MADO_6_X_1_2_2_5_2 + ": 'In the TID 1600 'Image Library' the following content Items shall be present... [TID 1602 Group R+]']";

    public static final String TID1600_GROUP_NO_CONTENT = "TID 1600 Image Library Group has no content items. [" +
            REF_MADO_6_X_1_2_2_5_2 + ": 'The following content Items shall be present in the 'Image Library Group'...']";

    public static final String TID1600_GROUP_MISSING_MODALITY = "TID 1600 Image Library Group missing Modality (121139, DCM, \"Modality\"). [" +
            REF_MADO_6_X_1_2_2_5_2 + ": 'The following content Items shall be present in the 'Image Library Group'... Modality (121139, DCM) R+']";

    public static final String TID1600_GROUP_MISSING_SERIES_DATE = "TID 1600 Image Library Group missing Series Date (" + CODE_SERIES_DATE + ", DCM, \"Series Date\"). [" +
            REF_MADO_6_X_1_2_2_5_2 + ": 'The following content Items shall be present... Series Date (" + CODE_SERIES_DATE + ", DCM) R+']";

    public static final String TID1600_GROUP_MISSING_SERIES_TIME = "TID 1600 Image Library Group missing Series Time (" + CODE_SERIES_TIME + ", DCM, \"Series Time\"). [" +
            REF_MADO_6_X_1_2_2_5_2 + ": 'The following content Items shall be present... Series Time (" + CODE_SERIES_TIME + ", DCM) R+']";

    public static final String TID1600_GROUP_MISSING_SERIES_DESCRIPTION = "TID 1600 Image Library Group missing Series Description (" + CODE_SERIES_DESCRIPTION + ", DCM, \"Series Description\"). [" +
            REF_MADO_6_X_1_2_2_5_2 + ": 'The following content Items shall be present... Series Description (" + CODE_SERIES_DESCRIPTION + ", DCM) R+']";

    public static final String TID1600_GROUP_MISSING_SERIES_NUMBER = "TID 1600 Image Library Group missing Series Number (" + CODE_SERIES_NUMBER + ", DCM, \"Series Number\"). [" +
            REF_MADO_6_X_1_2_2_5_2 + ": 'The following content Items shall be present... Series Number (" + CODE_SERIES_NUMBER + ", DCM) R+']";

    public static final String TID1600_GROUP_MISSING_SERIES_UID = "TID 1600 Image Library Group missing Series Instance UID (" + CODE_SERIES_INSTANCE_UID + ", DCM, \"Series Instance UID\"). [" +
            REF_MADO_6_X_1_2_2_5_2 + ": 'The following content Items shall be present... Series Instance UID (" + CODE_SERIES_INSTANCE_UID + ", DCM) R+']";

    public static final String TID1600_GROUP_MISSING_SERIES_RELATED_INSTANCES = "TID 1600 Image Library Group missing Number of Series Related Instances (" + CODE_NUM_SERIES_RELATED_INSTANCES + ", DCM). [" +
            "IHE MADO Suppl. Appx A (TID 1602, Row 12f): 'Number of Series Related Instances (" + CODE_NUM_SERIES_RELATED_INSTANCES + ", DCM)... Usage U' " +
            "(Note: MADO Validator enforces this as R+ based on profile consistency requirements).]";

    public static final String TID1600_ENTRY_NO_REFERENCED_SOP = "TID 1600 Image Library Entry has no ReferencedSOPSequence. [" +
            REF_DICOM_PS3_3 + " (Ref SOP Seq): 'Referenced SOP Sequence (0008,1199)... One or more Items shall be included in this Sequence.' " +
            "(For a specific entry pointing to a single instance, exactly one is expected).]";

    public static final String TID1600_ENTRY_MISSING_INSTANCE_NUMBER = "TID 1600 Image Library Entry missing Instance Number (" + CODE_INSTANCE_NUMBER + ", DCM). [" +
            REF_MADO_6_X_1_2_2_5_2 + ": 'The following content Items shall be present in the 'Image Library Entry'... " +
            "Instance Number (" + CODE_INSTANCE_NUMBER + ", DCM)... RC+ Required when present in the referenced SOP Instance.']";

    public static final String TID1600_ENTRY_MISSING_NUMBER_OF_FRAMES = "TID 1600 Image Library Entry missing Number of Frames (121140, DCM). [" +
            REF_MADO_6_X_1_2_2_5_2 + ": 'Number of Frames (121140, DCM)... RC+ Required when the SOP Class is multiframe.']";

    public static final String TID1600_KOS_REFERENCE_MISSING_TITLE_CODE = "KOS reference missing KOS Title Code or SOP Instance UIDs. [" +
            REF_MADO_6_X_1_2_2_5_2 + ": 'The following content Items shall be present in a container... when the related instance is a KOS... " +
            "KOS Title Code (" + CODE_KOS_TITLE + ", DCM) R+... SOP Instance UID (" + CODE_SOP_INSTANCE_UID + ", DCM) R+.']";

    public static final String TID1600_KEY_IMAGE_FLAGGING_REQUIREMENT = "TID 16XX Requirement V-DESC-02: Key Image Description required. [" +
            REF_MADO_6_X_1_2_2_5_2 + ": 'Key Object Description (" + CODE_KEY_OBJECT_DESCRIPTION + ", DCM)... RC+ Required when present in the referenced KOS instance.']";

    // ========== TID 1600 Root Validator ==========
    public static final String TID1600_ROOT_VALUE_TYPE_WRONG = "MADO Root Container Requirement V-ROOT-01: ValueType (0040,A040) must be 'CONTAINER'. [" +
            "IHE MADO Suppl. Appx A (TID 1600): 'Row 1: CONTAINER... Concept Name: (111028, DCM, 'Image Library')... Presence of Content Item: Mandatory.']";

    public static final String TID1600_ROOT_CONCEPT_NAME_WRONG = "MADO Root Container Requirement V-ROOT-02: ConceptNameCodeSequence must be (111028, DCM, 'Image Library'). [" +
            "IHE MADO Suppl. Appx A (TID 1600): 'Row 1: CONTAINER... Concept Name: (111028, DCM, 'Image Library')... Presence of Content Item: Mandatory.']";

    public static final String TID1600_ROOT_CONTINUITY_WRONG = "MADO Root Container Requirement V-ROOT-03: ContinuityOfContent (0040,A050) must be 'SEPARATE'. [" +
            "IHE MADO Suppl. Appx A (TID 1600): 'Continuity of Content: SEPARATE (Container is not part of the same temporal or spatial context as the parent).']";

    // ========== TID 1600 Rules ==========
    public static final String TID1600_TARGET_REGION_NO_CODE_VALUE = "Target Region code has no CodeValue. [" +
            REF_MADO_6_X_1_2_2_5_2 + ": 'Target Region (123014, DCM)... R+... Value Set Constraint: DCID 403X High-level anatomic regions...']";

    // ========== TID 1600 Study Validator ==========
    public static final String TID1600_STUDY_UID_NO_VALUE = "Study Instance UID content item has no UID value. [" +
            REF_MADO_6_X_1_2_2_5_2 + ": 'Study Instance UID (" + CODE_STUDY_INSTANCE_UID + ", DCM) R+... shall contain a valid UID value.']";

    public static final String TID1600_STUDY_MISSING_MODALITY = "TID 1600 Requirement: Modality (121139, DCM, 'Modality') missing at study level (Type R+). [" +
            REF_MADO_6_X_1_2_2_5_2 + ": 'In the TID 1600 'Image Library' the following content Items shall be present... Modality (121139, DCM) R+']";

    public static final String TID1600_STUDY_MISSING_STUDY_UID = "TID 1600 Requirement: Study Instance UID (" + CODE_STUDY_INSTANCE_UID + ", DCM, 'Study Instance UID') missing at study level (Type R+). [" +
            REF_MADO_6_X_1_2_2_5_2 + ": 'In the TID 1600 'Image Library' the following content Items shall be present... Study Instance UID (" + CODE_STUDY_INSTANCE_UID + ", DCM) R+']";

    public static final String TID1600_STUDY_MISSING_TARGET_REGION = "TID 1600 Requirement: Target Region (123014, DCM, 'Target Region') missing at study level (Type R+). [" +
            REF_MADO_6_X_1_2_2_5_2 + ": 'In the TID 1600 'Image Library' the following content Items shall be present... Target Region (123014, DCM) R+']";

    // ========== TID 1600 Main Validator ==========
    public static final String TID1600_APPROACH2_MISSING_IMAGE_LIBRARY = "MADO Approach 2 Requirement: Image Library container (111028, DCM, 'Image Library') missing or not found in ContentSequence. [" +
            REF_MADO_6_X_1_2_2_5_2 + ": 'The TID 2010 'Key Object Selection' Template shall include the TID 1600 'Image Library' Template.']";

    public static final String TID1600_ENTRY_REFSOPMULTIPLE = "TID 1600 Image Library Entry ReferencedSOPSequence must contain exactly 1 item, found %d. [" +
            REF_DICOM_PS3_3 + " (Ref SOP Seq): 'For a specific entry pointing to a single instance, exactly one is expected.']";

    public static final String TID1600_ENTRY_MISSING_SOP_CLASS_UID = "ReferencedSOPSequence missing ReferencedSOPClassUID. [" +
            REF_DICOM_PS3_3 + ": 'Referenced SOP Class UID (0008,1150)... Type 1... Shall be present.']";

    public static final String TID1600_ENTRY_MISSING_SOP_INSTANCE_UID = "ReferencedSOPSequence missing ReferencedSOPInstanceUID. [" +
            REF_DICOM_PS3_3 + ": 'Referenced SOP Instance UID (0008,1155)... Type 1... Shall be present.']";

    public static final String TID1600_ENTRY_INSTANCE_NUMBER_WRONG_VT = "Instance Number concept item must use ValueType TEXT (or UT in dumps), found: %s. [" +
            REF_MADO_6_X_1_2_2_5_2 + ": 'Instance Number content item shall use ValueType TEXT.']";

    public static final String TID1600_ENTRY_MISSING_INSTANCE_NUMBER_REQUIRED = "Instance Number (" + CODE_INSTANCE_NUMBER + ", DCM) missing for Image Library Entry. Type R+ (required by MADO). [" +
            REF_MADO_6_X_1_2_2_5_2 + ": 'Instance Number (" + CODE_INSTANCE_NUMBER + ", DCM)... RC+ Required when present in the referenced SOP Instance.']";

    public static final String TID1600_ENTRY_MISSING_NUMBER_OF_FRAMES_MULTIFRAME = "Number of Frames (121140, DCM) missing for multiframe SOP Class: %s. Type C+ (conditionally required by MADO). [" +
            REF_MADO_6_X_1_2_2_5_2 + ": 'Number of Frames (121140, DCM)... RC+ Required when the SOP Class is multiframe.']";

    public static final String TID1600_ENTRY_MISSING_METADATA_CONTENT = "TID 1600 Image Library Entry missing required instance-level metadata (ContentSequence). MADO requires at least Instance Number (" + CODE_INSTANCE_NUMBER + ", DCM) and conditionally Number of Frames. [" +
            REF_MADO_6_X_1_2_2_5_2 + ": 'The following content Items shall be present in the 'Image Library Entry'...']";

    public static final String TID1600_KOS_MISSING_TITLE_CODE = "KOS reference missing KOS Title Code (" + CODE_KOS_TITLE + ", DCM). Type R+. [" +
            REF_MADO_6_X_1_2_2_5_2 + ": 'KOS Title Code (" + CODE_KOS_TITLE + ", DCM) R+... when the related instance is a KOS.']";

    public static final String TID1600_KOS_MISSING_SOP_UIDS = "KOS reference missing SOP Instance UIDs (" + CODE_SOP_INSTANCE_UID + ", DCM). Type R+. [" +
            REF_MADO_6_X_1_2_2_5_2 + ": 'SOP Instance UID (" + CODE_SOP_INSTANCE_UID + ", DCM) R+... when the related instance is a KOS.']";

    public static final String TID1600_KEY_DESCRIPTION_MISMATCH = "TID 16XX Requirement V-DESC-02: Key Object Description present but %s. [" +
            REF_MADO_6_X_1_2_2_5_2 + ": 'Key Object Description (" + CODE_KEY_OBJECT_DESCRIPTION + ", DCM)... RC+ Required when present in the referenced KOS instance.']";

    // ========== Appendix B Validation =========="
    public static final String APPENDIX_B_EVIDENCE_MISSING = "CurrentRequestedProcedureEvidenceSequence is missing (Appendix B format). [" +
            REF_MADO_6_X_1_2_2_3_2 + ": 'Current Requested Procedure Evidence Sequence (0040,A375) ... R']";

    public static final String APPENDIX_B_STUDY_UID_MISSING = "StudyInstanceUID missing in Evidence (Appendix B format). [" +
            REF_MADO_APPENDIX_B + ".1: 'Study Instance UID (0020,000D) ... R ... Copy of the referenced study's Study Instance UID.']";

    public static final String APPENDIX_B_SERIES_MISSING = "ReferencedSeriesSequence is missing in Evidence study item. [" +
            REF_MADO_APPENDIX_B + ".2: 'Referenced Series Sequence (0008,1115) ... R ... Contains references to Series.']";

    public static final String APPENDIX_B_MODALITY_MISSING = "Appendix B Requirement V-ALT-01: Modality (0008,0060) is Type 1 (Required) in Referenced Series Sequence. [" +
            REF_MADO_TABLE_B_3_2_1 + ": 'Modality (0008,0060) ... R+']";

    public static final String APPENDIX_B_SERIES_UID_MISSING = "Appendix B Requirement V-ALT-01: SeriesInstanceUID (0020,000E) is Type 1 (Required) in Referenced Series Sequence. [" +
            REF_MADO_TABLE_B_3_2_1 + ": 'Series Instance UID (" + CODE_SERIES_INSTANCE_UID + ") ... R+']";

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

    // =========================================================================
    // ADDITIONAL VALIDATION CHECKS FOR IHE XDS-I.b COMPLIANCE
    // =========================================================================

    // ========== Key Object Document Module (Root Level Type 2) ==========
    // Reference: DICOM PS3.3 Table C.17-2 (Key Object Document Module)

    public static final String KOS_MISSING_REFERENCED_STUDY_SEQUENCE =
        "Referenced Study Sequence (0008,1110) is missing from Root. " +
        "Type 2 attribute in Key Object Document Module (must be present, may be empty). [" +
        REF_DICOM_PS3_3 + " Table C.17-2]";

    public static final String KOS_MISSING_FILLER_ORDER_NUMBER =
        "Filler Order Number / Imaging Service Request (0040,2017) is missing from Root. " +
        "Type 2 attribute in Key Object Document Module. [" +
        REF_DICOM_PS3_3 + " Table C.17-2]";

    public static final String KOS_MISSING_REQUESTED_PROCEDURE_ID =
        "Requested Procedure ID (0040,1001) is missing from Root. " +
        "Type 2 attribute in Key Object Document Module. [" +
        REF_DICOM_PS3_3 + " Table C.17-2]";

    public static final String KOS_MISSING_REQUESTED_PROCEDURE_DESC =
        "Requested Procedure Description (0032,1060) is missing from Root. " +
        "Type 2 attribute in Key Object Document Module. [" +
        REF_DICOM_PS3_3 + " Table C.17-2]";

    public static final String KOS_MISSING_REQUESTED_PROCEDURE_CODE_SEQ =
        "Requested Procedure Code Sequence (0032,1064) is missing from Root. " +
        "Type 2 attribute in Key Object Document Module. [" +
        REF_DICOM_PS3_3 + " Table C.17-2]";

    // ========== IHE XDS-I.b Manifest Profile (Nested Attributes) ==========

    public static final String XDSI_RETRIEVE_AE_TITLE_MISSING =
        "Retrieve AE Title (0008,0054) is missing in Referenced Series Sequence item #%d. " +
        "IHE XDS-I.b Hierarchical Series Reference requires the AE Title to identify the retrieval source. [" +
        "IHE RAD TF-3 Table 4.16.4.1.3.3-1: 'Retrieve AE Title... Type 1']";

    // ========== UID Syntax & Validity ==========

    public static final String UID_INVALID_ROOT =
        "Illegal root for UID in %s %s: '%s'. " +
        "UIDs starting with '999.' are not standard ISO/ITU roots. " +
        "Valid UIDs typically start with '1.2' (ISO member body), '1.3' (ISO identified org), or '2.25' (UUID). [" +
        REF_DICOM_PS3_5 + " Section 6.2 and ISO/IEC 9834-8]";

    public static final String UID_CONTAINS_LEADING_ZEROS =
        "Illegal UID component in %s %s: '%s'. " +
        "Numeric components in a UID must not have leading zeros (e.g., '.01' is illegal, must be '.1'). [" +
        REF_DICOM_PS3_5 + " Section 6.2]";

    // ========== Standard Values ==========
    public static final String DCMR = "DCMR";
    public static final String TID_2010 = "2010";
    public static final String VERIFICATION_SOP = "1.2.840.10008.1.1";

    private ValidationMessages() {
        // Utility class - prevent instantiation
    }
}

