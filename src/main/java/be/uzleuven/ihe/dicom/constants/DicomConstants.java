package be.uzleuven.ihe.dicom.constants;

/**
 * Central repository for commonly used DICOM/U* constants used across the project.
 * Extracted from scattered literals so they can be reused elsewhere.
 */
public final class DicomConstants {

    private DicomConstants() {
    }

    // Key Object Selection (KOS) SOP Class UID
    public static final String KEY_OBJECT_SELECTION_SOP_CLASS_UID = "1.2.840.10008.5.1.4.1.1.88.59";

    // Common Transfer Syntax UIDs
    public static final String TRANSFER_SYNTAX_EXPLICIT_VR_LITTLE_ENDIAN = "1.2.840.10008.1.2.1";
    public static final String TRANSFER_SYNTAX_IMPLICIT_VR_LITTLE_ENDIAN = "1.2.840.10008.1.2";
    public static final String TRANSFER_SYNTAX_EXPLICIT_VR_BIG_ENDIAN = "1.2.840.10008.1.2.2";
    // Prefix used for most compressed image transfer syntaxes (1.2.840.10008.1.2.4.x)
    public static final String TRANSFER_SYNTAX_COMPRESSED_PREFIX = "1.2.840.10008.1.2.4";

    // Common SR / KOS code values (DCM coding scheme)
    public static final String CODE_MANIFEST = "113030";       // DCM "Manifest"
    public static final String CODE_IMAGE_LIBRARY = "111028"; // DCM "Image Library"
    public static final String CODE_KEY_OBJECT_DESCRIPTION = "113012"; // DCM "Key Object Description"

    // TID 1600 specific codes
    public static final String CODE_MODALITY = "121139";
    public static final String CODE_NUMBER_OF_FRAMES = "121140";

    // Generic coding scheme designator for DICOM concept codes
    public static final String SCHEME_DCM = "DCM";

    // SR content/value constants
    public static final String VALUE_TYPE_CONTAINER = "CONTAINER";
    public static final String VALUE_TYPE_IMAGE = "IMAGE";
    public static final String VALUE_TYPE_COMPOSITE = "COMPOSITE";
    public static final String VALUE_TYPE_UIDREF = "UIDREF";
    public static final String VALUE_TYPE_CODE = "CODE";
    public static final String VALUE_TYPE_WAVEFORM = "WAVEFORM";
    public static final String VALUE_TYPE_TEXT = "TEXT";
    public static final String CONTINUITY_SEPARATE = "SEPARATE";

    // Relationship types used in SR trees
    public static final String RELATIONSHIP_CONTAINS = "CONTAINS";

    // Completion/verification flags commonly used in KOS/XDS-I
    public static final String COMPLETION_FLAG_COMPLETE = "COMPLETE";
    public static final String VERIFICATION_FLAG_VERIFIED = "VERIFIED";
    public static final String VERIFICATION_FLAG_UNVERIFIED = "UNVERIFIED";

    // Common character set used in examples
    public static final String DEFAULT_CHARACTER_SET_ISO_IR_100 = "ISO_IR 100";

    // Common DICOM code for "Image" concept used in examples
    public static final String CODE_IMAGE = "111030"; // DCM "Image"

    // Code meaning constants (convenience)
    public static final String CODE_MANIFEST_MEANING = "Manifest";
    public static final String CODE_IMAGE_MEANING = "Image";

    // IOCM (rejection) title codes commonly forbidden for XDS-I manifests
    public static final String IOCM_REJECTED_QUALITY = "113001";
    public static final String IOCM_REJECTED_PATIENT_SAFETY = "113037";
    public static final String IOCM_DATA_RETENTION_EXPIRED = "113039";
}
