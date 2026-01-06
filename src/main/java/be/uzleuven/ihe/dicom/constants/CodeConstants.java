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
    public static final String CODE_REGION_ABDOMEN = "T-D4000";

    // TID 1600 / Custom placeholder codes
    public static final String CODE_MODALITY = "121139";
    public static final String CODE_NUMBER_OF_FRAMES = "121140";


    public static final String CODE_TARGET_REGION = "123014";
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
    // BODY SITE CODES (SRT Anatomical Region Codes)
    // ============================================================================

    // SRT (SNOMED Radiology Template) codes for anatomical regions
    public static final String SRT_REGION_HEAD_AND_NECK = "T-D1000";
    public static final String SRT_REGION_HEAD = "T-D1100";
    public static final String SRT_REGION_THORAX = "T-D3000";
    public static final String SRT_REGION_ENTIRE_BODY = "T-D0010";
    public static final String SRT_REGION_LOWER_LIMB = "T-D8000";
    public static final String SRT_REGION_LOWER_LEG = "T-D8810";
    public static final String SRT_REGION_UPPER_LIMB = "T-D9000";
    public static final String SRT_REGION_BREAST = "T-D2000";
    public static final String SRT_REGION_PELVIS = "T-D6000";

    // ============================================================================
    // SNOMED CT BODY SITE CODES (mapped from SRT)
    // ============================================================================

    // SNOMED CT codes for corresponding anatomical regions
    public static final String SNOMED_ABDOMEN = "818981001";
    public static final String SNOMED_HEAD = "69536005";
    public static final String SNOMED_HEAD_AND_NECK = "774007";
    public static final String SNOMED_THORAX = "51185008";
    public static final String SNOMED_ENTIRE_BODY = "38266002";
    public static final String SNOMED_LOWER_LIMB = "61685007";
    public static final String SNOMED_LOWER_LEG = "30021000";
    public static final String SNOMED_UPPER_LIMB = "53120007";
    public static final String SNOMED_BREAST_REGION = "722567006";
    public static final String SNOMED_PELVIS = "62413002";

    public static final Map<String, String[]> BODY_SITE_MAP = Map.ofEntries(
            Map.entry(CodeConstants.CODE_REGION_ABDOMEN, new String[]{CodeConstants.SNOMED_ABDOMEN, CodeConstants.MEANING_REGION_ABDOMEN}),
            Map.entry(CodeConstants.SRT_REGION_HEAD, new String[]{CodeConstants.SNOMED_HEAD, "Head"}),
            Map.entry(CodeConstants.SRT_REGION_HEAD_AND_NECK, new String[]{CodeConstants.SNOMED_HEAD_AND_NECK, "Head and neck"}),
            Map.entry(CodeConstants.SRT_REGION_THORAX, new String[]{CodeConstants.SNOMED_THORAX, "Thorax"}),
            Map.entry(CodeConstants.SRT_REGION_ENTIRE_BODY, new String[]{CodeConstants.SNOMED_ENTIRE_BODY, "Entire body"}),
            Map.entry(CodeConstants.SRT_REGION_LOWER_LIMB, new String[]{CodeConstants.SNOMED_LOWER_LIMB, "Lower limb"}),
            Map.entry(CodeConstants.SRT_REGION_LOWER_LEG, new String[]{CodeConstants.SNOMED_LOWER_LEG, "Lower leg"}),
            Map.entry(CodeConstants.SRT_REGION_UPPER_LIMB, new String[]{CodeConstants.SNOMED_UPPER_LIMB, "Upper limb"}),
            Map.entry(CodeConstants.SRT_REGION_BREAST, new String[]{CodeConstants.SNOMED_BREAST_REGION, "Breast region"}),
            Map.entry(CodeConstants.SRT_REGION_PELVIS, new String[]{CodeConstants.SNOMED_PELVIS, "Pelvis"})
    );

    // ============================================================================
    // CODING SCHEME DESIGNATORS (second parameter of code(...))
    // ============================================================================

    public static final String SCHEME_DCM = "DCM";
    public static final String SCHEME_SRT = "SRT";

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
    public static final String MEANING_REGION_ABDOMEN = "Abdomen";

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
    public static final String EXT_NUMBER_OF_FRAMES = "http://hl7.eu/fhir/imaging-manifest-r5/StructureDefinition/number-of-frames";
    public static final String EXT_SELECTION_CODE = "http://hl7.eu/fhir/imaging-manifest-r5/StructureDefinition/selection-code";
    public static final String EXT_DERIVED_FROM = "http://hl7.eu/fhir/imaging-manifest-r5/StructureDefinition/derived-from";
    public static final String EXT_SELECTED_INSTANCE = "http://hl7.eu/fhir/imaging-manifest-r5/StructureDefinition/selected-instance";

    // MADO IG Profile URLs
    public static final String PROFILE_IMAGING_STUDY_MANIFEST = "http://hl7.eu/fhir/imaging-manifest-r5/StructureDefinition/ImImagingStudyManifest";
    public static final String PROFILE_IMAGING_PATIENT = "http://hl7.eu/fhir/imaging-manifest-r5/StructureDefinition/ImImagingPatient";
    public static final String PROFILE_IMAGING_STUDY = "http://hl7.eu/fhir/imaging-manifest-r5/StructureDefinition/ImManifestImagingStudy";
    public static final String PROFILE_IMAGING_SELECTION = "http://hl7.eu/fhir/imaging-manifest-r5/StructureDefinition/ImImagingSelection";
    public static final String PROFILE_WADO_ENDPOINT = "http://hl7.eu/fhir/imaging-manifest-r5/StructureDefinition/ImWadoEndpoint";
    public static final String PROFILE_IID_ENDPOINT = "http://hl7.eu/fhir/imaging-manifest-r5/StructureDefinition/ImIheIidViewerEndpoint";
    public static final String PROFILE_IMAGING_DEVICE = "http://hl7.eu/fhir/imaging-manifest-r5/StructureDefinition/ImImagingDevice";

}
