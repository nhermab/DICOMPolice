package be.uzleuven.ihe.dicom.constants;

/**
 * XDS/XDS-I related constants (format codes, mime types, URNs) used across the project.
 */
public final class XDSConstants {
    private XDSConstants() {
    }

    // MIME types
    public static final String MIME_TYPE_APPLICATION_DICOM = "application/dicom";

    // Common XDS formatCode for KOS manifests - per some IHE docs the SOP Class UID is used
    public static final String XDS_FORMAT_CODE_KOS = DicomConstants.KEY_OBJECT_SELECTION_SOP_CLASS_UID;

    // Example coding scheme OID used for formatCode when using DICOM codes
    public static final String CODING_SCHEME_OID_DCM = "1.2.840.10008.2.6.1";

    // Suggested URN to indicate the MADO flavor (may be used by validators). Keep configurable elsewhere if needed.
    public static final String URN_IHE_RAD_MADO = "urn:ihe:rad:MADO";

    // Content Template sequence expectations for XDS-I manifests
    public static final String EXPECTED_MAPPING_RESOURCE_DCMR = "DCMR";
    public static final String EXPECTED_TEMPLATE_IDENTIFIER_TID_2010 = "2010";
}
