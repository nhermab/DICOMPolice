package be.uzleuven.ihe.dicom.constants;

import java.util.Map;

/**
 * Constants for DICOM code values, schemes, and meanings used in creator classes.
 * These constants provide reusable definitions for code(value, scheme, meaning) calls.
 */
public final class CodeConstants {

    private CodeConstants() {
    }

    // ============================================================================
    // CODE VALUES (first parameter of code(...))
    // ============================================================================

    // Manifest and Manifest-related codes
    public static final String CODE_KOS_MANIFEST = "113030";

    // Image Library codes
    public static final String CODE_IMAGE_LIBRARY = "111028";
    public static final String CODE_IMAGE_LIBRARY_GROUP = "126200";

    // Image-related codes
    public static final String CODE_IMAGE = "111030";
    public static final String CODE_OF_INTEREST = "113000";

    // Key Object Selection codes
    public static final String CODE_KOS_DESCRIPTION = "113012";

    // Modality and region codes
    public static final String CODE_MODALITY_CT = "CT";

    // TID 1600 / Custom placeholder codes
    public static final String CODE_MODALITY = "121139";
    public static final String CODE_NUMBER_OF_FRAMES = "121140";


    public static final String CODE_TARGET_REGION = "123014";

    // CP-2595 (Page 8, Row 4b): Procedure Code allowed as HAS CONCEPT MOD in TID 2010 root
    public static final String CODE_PROCEDURE_CODE = "121023";
    public static final String MEANING_PROCEDURE_CODE = "Procedure Code";

    // Default anatomical region for MADO (SNOMED CT: Upper trunk - 67734004)
    public static final String CODE_REGION_UPPER_TRUNK = "67734004";
    public static final String MEANING_REGION_UPPER_TRUNK = "Upper trunk";



    // ============================================================================
    // MADO Trial Implementation Codes (CP-2595 / 99IHE scheme)
    // ============================================================================
    // Per DICOM CP-2595 Trial Implementation: temporary codes using the 99IHE
    // coding scheme designator. These will be replaced with final DCM codes once
    // CP-2595 is published. Do NOT use "DCM" as the coding scheme for these codes.
    //
    // See also: SCHEME_99IHE constant below.
    // ============================================================================


    /** Root Node (TID 2010) document title: "Manifest with Description" */
    public static final String CODE_MANIFEST_WITH_DESCRIPTION = "MADOTEMP001";
    /** Series Description content item (TID 1602, VT=TEXT) */
    public static final String CODE_SERIES_DESCRIPTION = "MADOTEMP002";
    /** Series Date content item (TID 1602, VT=DATE) */
    public static final String CODE_SERIES_DATE = "MADOTEMP003";
    /** Series Time content item (TID 1602, VT=TIME) */
    public static final String CODE_SERIES_TIME = "MADOTEMP004";
    /** Series Number content item – note: uses standard DCM code 113607 per spec, but kept here for mapping */
    public static final String CODE_SERIES_NUMBER = "113607";
    /** Series Instance UID content item – note: uses standard DCM code 112002 per spec */
    public static final String CODE_SERIES_INSTANCE_UID = "112002";
    /** Number of Series Related Instances (TID 1602, VT=NUM, units={instances}) */
    public static final String CODE_NUM_SERIES_RELATED_INSTANCES = "MADOTEMP007";
    /** Instance Number content item (TID 1601/1602, VT=TEXT) – note: uses standard DCM code 113609 per spec */
    public static final String CODE_INSTANCE_NUMBER = "113609";
    /** Number of Study Related Series (TID 1600 study-level, VT=NUM, units={series}) */
    public static final String CODE_NUM_STUDY_RELATED_SERIES = "MADOTEMP009";

    // ============================================================================
    // TID 16XX KOS Descriptor Codes (standard DCM codes for KOS references)
    // ============================================================================
    /** Document Title extracted from referenced KOS (TID 16XX, VT=CODE) */
    public static final String CODE_KOS_DOCUMENT_TITLE = "121144";
    /** Key Object Description from referenced KOS (TID 16XX, VT=TEXT) */
    public static final String CODE_KOS_OBJECT_DESCRIPTION = "113012";

    // Legacy aliases – kept for backward compatibility in validation messages
    /** @deprecated Use CODE_KOS_DOCUMENT_TITLE instead */
    @Deprecated
    public static final String CODE_KOS_TITLE = CODE_KOS_DOCUMENT_TITLE;

    /**
     * Study Instance UID content item – no longer a MADOTEMP code (not in CP-2595 study-level items).
     * The Study Instance UID is conveyed via the top-level DICOM tag (0020,000D), not as a TID 1600 content item.
     * Kept for backward compatibility with creator code.
     * @deprecated Study Instance UID is not a TID 1600 content item in CP-2595
     */
    @Deprecated
    public static final String CODE_STUDY_INSTANCE_UID = "110180";

    /**
     * SOP Instance UID content item code – no longer used as a MADOTEMP code.
     * In CP-2595, SOP Instance UIDs are conveyed via ReferencedSOPSequence, not content items.
     * Kept for backward compatibility with creator code.
     * @deprecated SOP Instance UIDs are conveyed via ReferencedSOPSequence in CP-2595
     */
    @Deprecated
    public static final String CODE_SOP_INSTANCE_UID = "110181";




    // ============================================================================
    // FHIR TERMINOLOGY SYSTEM URLs
    // ============================================================================

    /** HL7 Endpoint Connection Type Code System URL */
    public static final String ENDPOINT_CONNECTION_TYPE_SYSTEM = "http://terminology.hl7.org/CodeSystem/endpoint-connection-type";

    /** IHE MADO IG Endpoint Connection Type Code System URL (uses standard HL7 system in MADO IG R4) */
    public static final String IHE_ENDPOINT_CONNECTION_TYPE_SYSTEM = "http://terminology.hl7.org/CodeSystem/endpoint-connection-type";

    /** SNOMED CT System URL for body sites */
    public static final String SNOMED_SYSTEM = "http://snomed.info/sct";

    // ============================================================================
    // SNOMED CT BODY SITE CODES - MADO Allowed Values (http://snomed.info/sct)
    // https://hl7.eu/fhir/imaging-manifest-r5/0.2.0-snapshot1/ValueSet-im-anatomical-region-valueset.html
    // ============================================================================

    /**
     * MADO-compliant SNOMED CT codes for anatomical regions.
     * ONLY these codes are allowed in MADO manifests.
     * Use with coding scheme designator "SCT".
     */
    public static final String SNOMED_LOWER_TRUNK =             "63337009";
    public static final String SNOMED_ENTIRE_BODY =             "38266002";
    public static final String SNOMED_UPPER_EXTREMITY =         "53120007";
    public static final String SNOMED_LOWER_EXTREMITY =         "61685007";
    public static final String SNOMED_UPPER_TRUNK =             "67734004";
    public static final String SNOMED_HEAD_AND_NECK =           "774007";
    public static final String SNOMED_CARDIOVASCULAR_SYSTEM =   "113257007";
    public static final String SNOMED_HEART =                   "80891009";
    public static final String SNOMED_BREAST =                  "76752008";
    public static final String SNOMED_VERTEBRAL_COLUMN =        "737561001";

    /**
     * Display names for MADO-compliant SNOMED CT body site codes.
     * Format: SNOMED CT code -> display name
     */
    public static final Map<String, String> BODY_SITE_DISPLAY_MAP = Map.ofEntries(
            Map.entry(SNOMED_LOWER_TRUNK,               "Lower trunk"),
            Map.entry(SNOMED_ENTIRE_BODY,               "Entire body as a whole"),
            Map.entry(SNOMED_UPPER_EXTREMITY,           "Upper extremity"),
            Map.entry(SNOMED_LOWER_EXTREMITY,           "Lower extremity"),
            Map.entry(SNOMED_UPPER_TRUNK,               "Upper trunk"),
            Map.entry(SNOMED_HEAD_AND_NECK,             "Head and neck structure"),
            Map.entry(SNOMED_CARDIOVASCULAR_SYSTEM,     "Cardiovascular system"),
            Map.entry(SNOMED_HEART,                     "Heart"),
            Map.entry(SNOMED_BREAST,                    "Breast"),
            Map.entry(SNOMED_VERTEBRAL_COLUMN,          "Structure of vertebral column and/or spinal cord (body structure)")
    );

    // ============================================================================
    // CODING SCHEME DESIGNATORS (second parameter of code(...))
    // ============================================================================

    public static final String SCHEME_DCM = "DCM";
    /** IHE Trial Implementation coding scheme designator for MADO CP-2595 codes */
    public static final String SCHEME_99IHE = "99IHE";
    public static final String SCHEME_SRT = "SRT";
    public static final String SCHEME_SCT = "SCT"; // SNOMED CT

    // ============================================================================
    // CODE MEANINGS (third parameter of code(...))
    // ============================================================================

    // Manifest-related meanings
    public static final String MEANING_MANIFEST = "Manifest";
    public static final String MEANING_MANIFEST_WITH_DESCRIPTION = "Manifest with Description";

    // Image Library meanings
    public static final String MEANING_IMAGE_LIBRARY = "Image Library";
    public static final String MEANING_IMAGE_LIBRARY_GROUP = "Image Library Group";

    // Image-related meanings
    public static final String MEANING_IMAGE = "Image";
    public static final String MEANING_OF_INTEREST = "Of Interest";

    // Key Object Selection meanings
    public static final String MEANING_KOS_DESCRIPTION = "Key Object Description";
    public static final String MEANING_KOS_DOCUMENT_TITLE = "Document Title";
    /** @deprecated Use MEANING_KOS_DOCUMENT_TITLE instead */
    @Deprecated
    public static final String MEANING_KOS_TITLE = "KOS Title Code";

    // Modality and region meanings
    public static final String MEANING_MODALITY_CT = "CT";

    // TID 1600 meanings
    public static final String MEANING_MODALITY = "Modality";
    public static final String MEANING_STUDY_INSTANCE_UID = "Study Instance UID";
    public static final String MEANING_TARGET_REGION = "Target Region";
    public static final String MEANING_SERIES_DATE = "Series Date";
    public static final String MEANING_SERIES_TIME = "Series Time";
    public static final String MEANING_SERIES_NUMBER = "Series Number";
    public static final String MEANING_SERIES_DESCRIPTION = "Series Description";
    public static final String MEANING_SERIES_INSTANCE_UID = "Series Instance UID";
    public static final String MEANING_SOP_INSTANCE_UID = "SOP Instance UIDs";
    public static final String MEANING_INSTANCE_NUMBER = "Instance Number";
    public static final String MEANING_NUMBER_OF_FRAMES = "Number of Frames";
    public static final String MEANING_NUM_SERIES_RELATED_INSTANCES = "Number of Series Related Instances";
    public static final String MEANING_NUM_STUDY_RELATED_SERIES = "Number of Study Related Series";

    // Reuse shared concept codes and coding scheme designator
    public static final String KOS_SOP_CLASS_UID = "1.2.840.10008.5.1.4.1.1.88.59";

    // MADO IG R4 Extension URLs (https://profiles.ihe.net/RAD/MADO/)
    public static final String EXT_ANATOMICAL_REGION = "https://profiles.ihe.net/RAD/MADO/StructureDefinition/MadoAnatomicalRegionExtension";
    public static final String EXT_KEY_OBJECT_DOCUMENT_TITLE = "https://profiles.ihe.net/RAD/MADO/StructureDefinition/MadoKeyObjectDocumentTitle";
    public static final String EXT_NUMBER_OF_FRAMES = "https://profiles.ihe.net/RAD/MADO/StructureDefinition/MadoNumberOfFrames";
    public static final String EXT_RETRIEVE_LOCATION_UID = "https://profiles.ihe.net/RAD/MADO/StructureDefinition/MadoRetrieveLocationUIDExtension";
    // Cross-version extensions for R5 fields in R4 DocumentReference
    public static final String EXT_DOCREF_MODALITY = "http://hl7.org/fhir/5.0/StructureDefinition/extension-DocumentReference.modality";
    public static final String EXT_DOCREF_BODY_SITE = "http://hl7.org/fhir/5.0/StructureDefinition/extension-DocumentReference.bodySite";
    public static final String EXT_DOCREF_CONTENT_PROFILE = "http://hl7.org/fhir/5.0/StructureDefinition/extension-DocumentReference.content.profile";
    // Legacy extension URLs (kept for backward compatibility)
    public static final String EXT_INSTANCE_DESCRIPTION = "https://profiles.ihe.net/RAD/MADO/StructureDefinition/instance-description";
    public static final String EXT_SELECTION_CODE = "https://profiles.ihe.net/RAD/MADO/StructureDefinition/selection-code";
    public static final String EXT_DERIVED_FROM = "https://profiles.ihe.net/RAD/MADO/StructureDefinition/derived-from";
    public static final String EXT_SELECTED_INSTANCE = "https://profiles.ihe.net/RAD/MADO/StructureDefinition/selected-instance";
    // MADO Device Type Code System
    public static final String MADO_DEVICE_TYPE_SYSTEM = "https://profiles.ihe.net/RAD/MADO/CodeSystem/MadoDeviceType";
    public static final String MADO_DEVICE_TYPE_CODE = "mado-creator";
    public static final String MADO_DEVICE_TYPE_DISPLAY = "MADO Creator";
    // MADO Format Codes
    public static final String MADO_FHIR_FORMAT_CODE = "urn:ihe:rad:MADO:fhir-manifest:2026";
    public static final String MADO_FHIR_FORMAT_SYSTEM = "http://ihe.net/fhir/ihe.formatcode.fhir/CodeSystem/formatcode";
    public static final String MADO_KOS_FORMAT_CODE = "1.2.840.10008.5.1.4.1.1.88.59";
    public static final String MADO_KOS_FORMAT_SYSTEM = "http://dicom.nema.org/resources/ontology/DCMUID";
    // MADO Document Type and Category
    public static final String MADO_TYPE_CODE = "18748-4";
    public static final String MADO_TYPE_SYSTEM = "http://loinc.org";
    public static final String MADO_TYPE_DISPLAY = "Diagnostic imaging Study";
    public static final String MADO_CATEGORY_CODE = "Medical-Imaging";
    public static final String MADO_CATEGORY_SYSTEM = "http://hl7.eu/fhir/eu-health-data-api/CodeSystem/eehrxf-document-priority-category-cs";
    public static final String MADO_CATEGORY_DISPLAY = "Medical-Imaging";

    // Custom extensions for DICOM round-trip preservation
    // NOTE: These use a local namespace to avoid confusion with official DICOM/MADO IG extensions.
    // They are implementation-specific and used only for lossless DICOM-to-FHIR-to-DICOM round-tripping.
    private static final String EXT_ROUNDTRIP_BASE = "http://uzleuven.be/fhir/StructureDefinition/dicom-roundtrip/";
    public static final String EXT_LOCAL_NAMESPACE = EXT_ROUNDTRIP_BASE + "local-namespace";
    public static final String EXT_TYPE_OF_PATIENT_ID = EXT_ROUNDTRIP_BASE + "type-of-patient-id";
    public static final String EXT_STUDY_ID = EXT_ROUNDTRIP_BASE + "study-id";
    public static final String EXT_SERIES_DATE = EXT_ROUNDTRIP_BASE + "series-date";
    public static final String EXT_SERIES_TIME = EXT_ROUNDTRIP_BASE + "series-time";
    public static final String EXT_STUDY_DATE = EXT_ROUNDTRIP_BASE + "study-date";
    public static final String EXT_STUDY_TIME = EXT_ROUNDTRIP_BASE + "study-time";
    public static final String EXT_CONTENT_DATE = EXT_ROUNDTRIP_BASE + "content-date";
    public static final String EXT_CONTENT_TIME = EXT_ROUNDTRIP_BASE + "content-time";
    public static final String EXT_SOP_INSTANCE_UID = EXT_ROUNDTRIP_BASE + "sop-instance-uid";
    public static final String EXT_SERIES_INSTANCE_UID = EXT_ROUNDTRIP_BASE + "series-instance-uid";
    public static final String EXT_REFERRING_PHYSICIAN = EXT_ROUNDTRIP_BASE + "referring-physician";
    public static final String EXT_ORIGINAL_MANUFACTURER = EXT_ROUNDTRIP_BASE + "original-manufacturer";
    /** Original DICOM ManufacturerModelName (preserves model name for round-trip) */
    public static final String EXT_ORIGINAL_MODEL_NAME = EXT_ROUNDTRIP_BASE + "original-model-name";
    // Extensions for series-level metadata (within ImagingStudy.series)
    public static final String EXT_IMAGING_SERIES_DATE = EXT_ROUNDTRIP_BASE + "imaging-series-date";
    public static final String EXT_IMAGING_SERIES_TIME = EXT_ROUNDTRIP_BASE + "imaging-series-time";
    // Additional round-trip extensions
    /** Raw DICOM patient name string (preserves empty/special names like "^") */
    public static final String EXT_DICOM_PATIENT_NAME = EXT_ROUNDTRIP_BASE + "dicom-patient-name";
    /** Empty study description flag (preserves empty StudyDescription tag) */
    public static final String EXT_STUDY_DESCRIPTION = EXT_ROUNDTRIP_BASE + "study-description";
    /** ReferencedStudySequence JSON (preserves referenced study UIDs) */
    public static final String EXT_REFERENCED_STUDY_SEQUENCE = EXT_ROUNDTRIP_BASE + "referenced-study-sequence";
    /** OtherPatientIDsSequence JSON (preserves additional patient identifiers) */
    public static final String EXT_OTHER_PATIENT_IDS = EXT_ROUNDTRIP_BASE + "other-patient-ids";
    /** RequestedProcedureDescription per referenced request entry */
    public static final String EXT_REQ_PROCEDURE_DESCRIPTION = EXT_ROUNDTRIP_BASE + "requested-procedure-description";
    /** RequestedProcedureCodeSequence JSON per referenced request entry */
    public static final String EXT_REQ_PROCEDURE_CODE_SEQ = EXT_ROUNDTRIP_BASE + "requested-procedure-code-sequence";
    /** OrderPlacerIdentifierSequence OID per referenced request entry */
    public static final String EXT_ORDER_PLACER_ID_SEQ = EXT_ROUNDTRIP_BASE + "order-placer-identifier-sequence";
    /** RequestedProcedureID per referenced request entry */
    public static final String EXT_REQ_PROCEDURE_ID = EXT_ROUNDTRIP_BASE + "requested-procedure-id";
    /** FillerOrderNumberImagingServiceRequest per referenced request entry */
    public static final String EXT_FILLER_ORDER_NUMBER = EXT_ROUNDTRIP_BASE + "filler-order-number";
    /** PlacerOrderNumberImagingServiceRequest per referenced request entry */
    public static final String EXT_PLACER_ORDER_NUMBER = EXT_ROUNDTRIP_BASE + "placer-order-number";
    /** RetrieveAETitle per referenced series item in evidence sequence */
    public static final String EXT_RETRIEVE_AE_TITLE = EXT_ROUNDTRIP_BASE + "retrieve-ae-title";
    /** Header-level AccessionNumber (0008,0050) – distinct from ReferencedRequestSequence accession */
    public static final String EXT_ACCESSION_NUMBER = EXT_ROUNDTRIP_BASE + "accession-number";
    /** Header-level IssuerOfAccessionNumber OID */
    public static final String EXT_ACCESSION_NUMBER_ISSUER = EXT_ROUNDTRIP_BASE + "accession-number-issuer";

    // MADO IG R4 Profile URLs (https://profiles.ihe.net/RAD/MADO/)
    public static final String PROFILE_IMAGING_STUDY_MANIFEST = "https://profiles.ihe.net/RAD/MADO/StructureDefinition/MadoFhirBundle";
    public static final String PROFILE_IMAGING_PATIENT = "https://profiles.ihe.net/RAD/MADO/StructureDefinition/MadoPatient";
    public static final String PROFILE_IMAGING_STUDY = "https://profiles.ihe.net/RAD/MADO/StructureDefinition/MadoImagingStudy";
    public static final String PROFILE_IMAGING_SELECTION = "https://profiles.ihe.net/RAD/MADO/StructureDefinition/MadoImagingSelection";
    public static final String PROFILE_WADO_RS_ENDPOINT = "https://profiles.ihe.net/RAD/MADO/StructureDefinition/MadoWadoEndpoint";
    public static final String PROFILE_IMAGE_VIEWER_ENDPOINT = "https://profiles.ihe.net/RAD/MADO/StructureDefinition/MadoWebViewerEndpoint";
    public static final String PROFILE_IID_ENDPOINT = "https://profiles.ihe.net/RAD/MADO/StructureDefinition/MadoWebViewerEndpoint";
    public static final String PROFILE_IMAGING_DEVICE = "https://profiles.ihe.net/RAD/MADO/StructureDefinition/MadoCreator";
    public static final String PROFILE_CREATOR_ORGANIZATION = "https://profiles.ihe.net/RAD/MADO/StructureDefinition/MadoCreatorOrganization";
    public static final String PROFILE_REQUESTED_PROCEDURE = "https://profiles.ihe.net/RAD/MADO/StructureDefinition/MadoRequestedProcedure";
    public static final String PROFILE_PROVENANCE = "https://profiles.ihe.net/RAD/MADO/StructureDefinition/MadoProvenance";
    public static final String PROFILE_DOCREF_FHIR = "https://profiles.ihe.net/RAD/MADO/StructureDefinition/MadoFhirDocumentReference";
    public static final String PROFILE_DOCREF_KOS = "https://profiles.ihe.net/RAD/MADO/StructureDefinition/MadoDicomKosDocumentReference";

}
