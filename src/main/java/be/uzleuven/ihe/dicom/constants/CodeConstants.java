package be.uzleuven.ihe.dicom.constants;

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
    public static final String CODE_MANIFEST = "113030";
    public static final String CODE_MANIFEST_WITH_DESCRIPTION = "ddd001";

    // Image Library codes
    public static final String CODE_IMAGE_LIBRARY = "111028";
    public static final String CODE_IMAGE_LIBRARY_GROUP = "126200";

    // Image-related codes
    public static final String CODE_IMAGE = "111030";
    public static final String CODE_OF_INTEREST = "113000";

    // Key Object Selection codes
    public static final String CODE_KOS_DESCRIPTION = "113012";
    public static final String CODE_KOS_TITLE = "ddd008";

    // Modality and region codes
    public static final String CODE_MODALITY_CT = "CT";
    public static final String CODE_REGION_ABDOMEN = "T-D4000";

    // TID 1600 / Custom placeholder codes
    public static final String CODE_MODALITY = "121139";
    public static final String CODE_STUDY_INSTANCE_UID = "ddd011";
    public static final String CODE_TARGET_REGION = "123014";
    public static final String CODE_SERIES_DATE = "ddd003";
    public static final String CODE_SERIES_TIME = "ddd004";
    public static final String CODE_SERIES_NUMBER = "ddd010";
    public static final String CODE_SERIES_DESCRIPTION = "ddd002";
    public static final String CODE_SERIES_INSTANCE_UID = "ddd006";
    public static final String CODE_SOP_INSTANCE_UID = "ddd007";
    public static final String CODE_INSTANCE_NUMBER = "ddd005";
    public static final String CODE_NUMBER_OF_FRAMES = "121140";
    public static final String CODE_NUM_SERIES_RELATED_INSTANCES = "ddd013";

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
}

