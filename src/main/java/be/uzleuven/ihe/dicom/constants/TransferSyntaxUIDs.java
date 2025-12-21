package be.uzleuven.ihe.dicom.constants;

import org.dcm4che3.data.UID;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Common Transfer Syntax UIDs that are often mistaken for SOP Class UIDs.
 * Centralized so validators can reuse the same list.
 */
public final class TransferSyntaxUIDs {

    private TransferSyntaxUIDs() {
    }

    public static final Set<String> COMMON_TRANSFER_SYNTAX_UIDS;

    static {
        Set<String> s = new HashSet<>(Arrays.asList(
            // Core transfer syntaxes (dcm4che constants)
            UID.ImplicitVRLittleEndian,
            UID.ExplicitVRLittleEndian,
            UID.ExplicitVRBigEndian,
            UID.DeflatedExplicitVRLittleEndian,
            UID.JPEGBaseline8Bit,
            UID.JPEGExtended12Bit,
            UID.JPEGLossless,
            UID.JPEGLosslessSV1,
            UID.JPEGLSLossless,
            UID.JPEG2000,
            UID.RLELossless,

            // Additional JPEG processes and other syntaxes (literals)
            "1.2.840.10008.1.2.4.52",
            "1.2.840.10008.1.2.4.53",
            "1.2.840.10008.1.2.4.54",
            "1.2.840.10008.1.2.4.55",
            "1.2.840.10008.1.2.4.56",
            "1.2.840.10008.1.2.4.58",
            "1.2.840.10008.1.2.4.59",
            "1.2.840.10008.1.2.4.60",
            "1.2.840.10008.1.2.4.61",
            "1.2.840.10008.1.2.4.62",
            "1.2.840.10008.1.2.4.63",
            "1.2.840.10008.1.2.4.64",
            "1.2.840.10008.1.2.4.65",
            "1.2.840.10008.1.2.4.66",
            "1.2.840.10008.1.2.4.81",
            "1.2.840.10008.1.2.4.90",
            "1.2.840.10008.1.2.4.92",
            "1.2.840.10008.1.2.4.93",
            "1.2.840.10008.1.2.4.94",
            "1.2.840.10008.1.2.4.95",
            "1.2.840.10008.1.2.6.1", // RFC 2557 MIME Encapsulation
            "1.2.840.10008.1.2.4.100",
            "1.2.840.10008.1.2.4.102",
            "1.2.840.10008.1.2.4.103",
            "1.2.840.10008.1.2.4.201",
            "1.2.840.10008.1.2.4.202",
            "1.2.840.10008.1.2.4.203"
        ));

        COMMON_TRANSFER_SYNTAX_UIDS = Collections.unmodifiableSet(s);
    }
}

