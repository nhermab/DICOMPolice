package be.uzleuven.ihe.dicom.constants;

/**
 * Code constants used by MADO TID 1600 Image Library validation.
 * Kept in one place to make rules easier to maintain.
 */
public final class TID1600Codes {

    private TID1600Codes() {
    }

    // Reuse common DICOM codes from centralized constants where applicable
    public static final String CODE_MODALITY = DicomConstants.CODE_MODALITY;
    public static final String CODE_NUMBER_OF_FRAMES = DicomConstants.CODE_NUMBER_OF_FRAMES;

    // TID 1600 specific / placeholder codes (keep local)
    public static final String CODE_STUDY_INSTANCE_UID = "ddd011";
    public static final String CODE_TARGET_REGION = "123014";
    public static final String CODE_SERIES_DATE = "ddd003";
    public static final String CODE_SERIES_TIME = "ddd004";
    public static final String CODE_SERIES_DESCRIPTION = "ddd002";
    public static final String CODE_SERIES_NUMBER = "ddd005";
    public static final String CODE_SERIES_INSTANCE_UID = "ddd006";
    public static final String CODE_NUMBER_OF_SERIES_RELATED_INSTANCES = "ddd013";

    public static final String CODE_KOS_TITLE = "ddd008";

    /** Instance Number for an Image Library Entry, value type TEXT, required by MADO (R+). */
    public static final String CODE_INSTANCE_NUMBER = "ddd012";

    /** Currently reserved for KOS description rules that may be added later. */
    @SuppressWarnings("unused")
    public static final String CODE_KOS_DESCRIPTION = "ddd009";

    public static final String CODE_SOP_INSTANCE_UID = "ddd007";

    // Reuse shared concept codes and coding scheme designator
    public static final String CODE_MANIFEST = DicomConstants.CODE_MANIFEST;  // DCM "Manifest"
    public static final String CODE_IMAGE_LIBRARY = DicomConstants.CODE_IMAGE_LIBRARY;  // DCM "Image Library"
    public static final String CODE_KEY_OBJECT_DESC = DicomConstants.CODE_KEY_OBJECT_DESCRIPTION;  // DCM "Key Object Description"

    public static final String SCHEME_DCM = DicomConstants.SCHEME_DCM;
}
