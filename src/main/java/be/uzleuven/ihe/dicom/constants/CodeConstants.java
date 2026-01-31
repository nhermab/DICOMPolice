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

    // Default anatomical region for MADO (SNOMED CT: Upper trunk - 67734004)
    public static final String CODE_REGION_UPPER_TRUNK = "67734004";
    public static final String MEANING_REGION_UPPER_TRUNK = "Upper trunk";



    //TODO: these codes are placeholders and need proper DCM codes
    /*
    * Since IHE MADO is using an unfinished DICOM CP, the new codes  are not yet assigned,
    * so be wary of taking this beyond a proof-of-concept
    * (normally for a trial of not-yet-finished changes, private codes will be assigned,
    *  e.g., 99IHERADTF or similar ... please do NOT use "DCM" as the coding scheme when they are not valid codes )
    *
    * */
    public static final String CODE_MANIFEST_WITH_DESCRIPTION = "ddd001";
    public static final String CODE_SERIES_DESCRIPTION = "ddd002";
    public static final String CODE_SERIES_DATE = "ddd003";
    public static final String CODE_SERIES_TIME = "ddd004";
    public static final String CODE_SERIES_NUMBER = "ddd005";
    public static final String CODE_SERIES_INSTANCE_UID = "ddd006";
    public static final String CODE_NUM_SERIES_RELATED_INSTANCES = "ddd007";
    public static final String CODE_INSTANCE_NUMBER = "ddd008";
    public static final String CODE_STUDY_INSTANCE_UID = "ddd011";
    /** Instance Number for an Image Library Entry, value type TEXT, required by MADO (R+). */
    public static final String CODE_KOS_TITLE = "ddd061";
    public static final String CODE_KOS_OBJECT_DESCRIPTION =  "ddd061";

    public static final String CODE_SOP_INSTANCE_UID = "ddd060";




    // ============================================================================
    // FHIR TERMINOLOGY SYSTEM URLs
    // ============================================================================

    /** HL7 Endpoint Connection Type Code System URL */
    public static final String ENDPOINT_CONNECTION_TYPE_SYSTEM = "http://terminology.hl7.org/CodeSystem/endpoint-connection-type";

    /** IHE MADO IG Endpoint Connection Type Code System URL */
    public static final String IHE_ENDPOINT_CONNECTION_TYPE_SYSTEM = "http://hl7.eu/fhir/imaging-manifest-r5/CodeSystem/codesystem-endpoint-terminology";

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
    //PLACEHOLDER FOR PUBLIC COMMEND IHE MADO SPEC
    public static final String SCHEME_IHE_PC_PH_SCEME_DDCM = "DCM";
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

    // Reuse shared concept codes and coding scheme designator
    public static final String KOS_SOP_CLASS_UID = "1.2.840.10008.5.1.4.1.1.88.59";

    // MADO IG Extension URLs
    public static final String EXT_INSTANCE_DESCRIPTION = "http://hl7.eu/fhir/imaging-manifest-r5/StructureDefinition/instance-description";
    public static final String EXT_SELECTION_CODE = "http://hl7.eu/fhir/imaging-manifest-r5/StructureDefinition/selection-code";
    public static final String EXT_DERIVED_FROM = "http://hl7.eu/fhir/imaging-manifest-r5/StructureDefinition/derived-from";
    public static final String EXT_SELECTED_INSTANCE = "http://hl7.eu/fhir/imaging-manifest-r5/StructureDefinition/selected-instance";

    // Custom extensions for DICOM round-trip preservation
    public static final String EXT_LOCAL_NAMESPACE = "http://dicom.nema.org/fhir/StructureDefinition/local-namespace";
    public static final String EXT_TYPE_OF_PATIENT_ID = "http://dicom.nema.org/fhir/StructureDefinition/type-of-patient-id";
    public static final String EXT_STUDY_ID = "http://dicom.nema.org/fhir/StructureDefinition/study-id";
    public static final String EXT_SERIES_DATE = "http://dicom.nema.org/fhir/StructureDefinition/series-date";
    public static final String EXT_SERIES_TIME = "http://dicom.nema.org/fhir/StructureDefinition/series-time";
    public static final String EXT_CONTENT_DATE = "http://dicom.nema.org/fhir/StructureDefinition/content-date";
    public static final String EXT_CONTENT_TIME = "http://dicom.nema.org/fhir/StructureDefinition/content-time";
    public static final String EXT_SOP_INSTANCE_UID = "http://dicom.nema.org/fhir/StructureDefinition/sop-instance-uid";
    public static final String EXT_SERIES_INSTANCE_UID = "http://dicom.nema.org/fhir/StructureDefinition/series-instance-uid";
    public static final String EXT_REFERRING_PHYSICIAN = "http://dicom.nema.org/fhir/StructureDefinition/referring-physician";
    public static final String EXT_ORIGINAL_MANUFACTURER = "http://dicom.nema.org/fhir/StructureDefinition/original-manufacturer";
    // Extensions for series-level metadata (within ImagingStudy.series)
    public static final String EXT_IMAGING_SERIES_DATE = "http://dicom.nema.org/fhir/StructureDefinition/imaging-series-date";
    public static final String EXT_IMAGING_SERIES_TIME = "http://dicom.nema.org/fhir/StructureDefinition/imaging-series-time";

    // MADO IG Profile URLs
    public static final String PROFILE_IMAGING_STUDY_MANIFEST = "http://hl7.eu/fhir/imaging-manifest-r5/StructureDefinition/ImImagingStudyManifest";
    public static final String PROFILE_IMAGING_PATIENT = "http://hl7.eu/fhir/imaging-manifest-r5/StructureDefinition/ImPatient";
    public static final String PROFILE_IMAGING_STUDY = "http://hl7.eu/fhir/imaging-manifest-r5/StructureDefinition/ImImagingStudy";
    public static final String PROFILE_IMAGING_SELECTION = "http://hl7.eu/fhir/imaging-manifest-r5/StructureDefinition/ImImagingSelection";
    public static final String PROFILE_WADO_RS_ENDPOINT = "http://hl7.eu/fhir/imaging-manifest-r5/StructureDefinition/ImWadoRsEndpoint";
    public static final String PROFILE_IMAGE_VIEWER_ENDPOINT = "http://hl7.eu/fhir/imaging-manifest-r5/StructureDefinition/ImImageViewerEndpoint";
    public static final String PROFILE_IID_ENDPOINT = "http://hl7.eu/fhir/imaging-manifest-r5/StructureDefinition/ImIheIidViewerEndpoint";
    public static final String PROFILE_IMAGING_DEVICE = "http://hl7.eu/fhir/imaging-manifest-r5/StructureDefinition/ImImagingDevice";

}
