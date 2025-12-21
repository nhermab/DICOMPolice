package be.uzleuven.ihe.dicom.constants;

/**
 * Constants for validation messages used in DICOM structure validation.
 * Centralizes all error, warning, and info messages for the validator.
 */
public class ValidationMessages {

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

    public static final String TID2010_ERROR = "ContentTemplateSequence has TemplateIdentifier=2010 but " +
            "MappingResource is not 'DCMR'. Expected MappingResource='DCMR'.";

    public static final String TID2010_INFO = "ContentTemplateSequence correctly identifies TID 2010 (DCMR)";

    // ========== Empty Sequences ==========
    public static final String EMPTY_SEQUENCE_MSG = " (%s) is present but has zero length. " +
            "%s sequences must contain at least one item when present.";

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

