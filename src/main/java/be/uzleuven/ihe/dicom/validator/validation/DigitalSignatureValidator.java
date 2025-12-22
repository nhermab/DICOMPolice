package be.uzleuven.ihe.dicom.validator.validation;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import be.uzleuven.ihe.dicom.validator.model.ValidationResult;
import be.uzleuven.ihe.dicom.constants.ValidationMessages;

/**
 * Validator for DICOM Digital Signatures.
 * Performs structural validation of Digital Signatures Sequence (FFFA,FFFA).
 * Does NOT perform cryptographic validation - only checks presence and structure.
 *
 * Per DICOM PS3.15 and IHE XDS-I.b requirements.
 */
public final class DigitalSignatureValidator {

    private DigitalSignatureValidator() {
    }

    /**
     * Validate Digital Signature requirements based on Document Title.
     * If the KOS is labeled as "Signed Manifest" (113031, DCM), it must contain a valid signature.
     *
     * @param dataset The DICOM dataset to validate
     * @param result The validation result to append findings to
     * @param modulePath The module path for error reporting
     */
    public static void validateSignatureRequirement(Attributes dataset, ValidationResult result, String modulePath) {
        // Check if document title indicates a signed manifest
        boolean requiresSignature = isSignedManifest(dataset);

        boolean hasSignature = hasDigitalSignature(dataset);

        if (requiresSignature && !hasSignature) {
            result.addError("Document Title indicates 'Signed Manifest' (113031, DCM) but no Digital Signatures Sequence (FFFA,FFFA) found. " +
                          "A signed manifest must include at least one digital signature.", modulePath);
            return;
        }

        if (hasSignature) {
            validateDigitalSignatureStructure(dataset, result, modulePath);
        }
    }

    /**
     * Check if the document title indicates this is a signed manifest.
     */
    private static boolean isSignedManifest(Attributes dataset) {
        Sequence conceptNameSeq = dataset.getSequence(Tag.ConceptNameCodeSequence);
        if (conceptNameSeq == null || conceptNameSeq.isEmpty()) {
            return false;
        }

        Attributes titleCode = conceptNameSeq.get(0);
        String codeValue = titleCode.getString(Tag.CodeValue);

        return "113031".equals(codeValue); // Signed Manifest
    }

    /**
     * Check if dataset contains Digital Signatures Sequence.
     * Note: Tag.DigitalSignaturesSequence is (FFFA,FFFA)
     */
    private static boolean hasDigitalSignature(Attributes dataset) {
        return dataset.contains(Tag.DigitalSignaturesSequence);
    }

    /**
     * Validate Digital Signatures Sequence structure.
     * This is a basic structural check - cryptographic validation is out of scope.
     */
    private static void validateDigitalSignatureStructure(Attributes dataset, ValidationResult result, String modulePath) {
        Sequence sigSeq = dataset.getSequence(Tag.DigitalSignaturesSequence);

        if (sigSeq == null || sigSeq.isEmpty()) {
            result.addError(ValidationMessages.DIGITAL_SIGNATURES_EMPTY, modulePath);
            return;
        }

        result.addInfo("Digital Signatures Sequence found with " + sigSeq.size() + " signature(s)", modulePath);

        for (int i = 0; i < sigSeq.size(); i++) {
            Attributes sigItem = sigSeq.get(i);
            String sigPath = modulePath + " > DigitalSignaturesSequence[" + (i + 1) + "]";

            validateSignatureItem(sigItem, result, sigPath);
        }
    }

    /**
     * Validate individual Digital Signature item structure.
     */
    private static void validateSignatureItem(Attributes sigItem, ValidationResult result, String sigPath) {
        // MAC ID Number (0400,0005) - Type 1
        if (!sigItem.contains(Tag.MACIDNumber)) {
            result.addError("MAC ID Number (0400,0005) is missing from signature item", sigPath);
        }

        // Digital Signature UID (0400,0100) - Type 1
        if (!sigItem.contains(Tag.DigitalSignatureUID)) {
            result.addError("Digital Signature UID (0400,0100) is missing from signature item", sigPath);
        } else {
            String sigUID = sigItem.getString(Tag.DigitalSignatureUID);
            if (sigUID == null || sigUID.isEmpty()) {
                result.addError("Digital Signature UID (0400,0100) is empty", sigPath);
            } else if (!sigUID.matches("^[0-9.]+$") || sigUID.length() > 64) {
                result.addError("Digital Signature UID (0400,0100) is not a valid UID format", sigPath);
            }
        }

        // Digital Signature DateTime (0400,0105) - Type 1
        if (!sigItem.contains(Tag.DigitalSignatureDateTime)) {
            result.addError("Digital Signature DateTime (0400,0105) is missing from signature item", sigPath);
        }

        // Certificate Type (0400,0110) - Type 1
        if (!sigItem.contains(Tag.CertificateType)) {
            result.addError("Certificate Type (0400,0110) is missing from signature item", sigPath);
        } else {
            String certType = sigItem.getString(Tag.CertificateType);
            // Common values: "X509_1363_PEM", "X509_DER"
            if (certType == null || certType.isEmpty()) {
                result.addError("Certificate Type (0400,0110) is empty", sigPath);
            }
        }

        // Certificate of Signer (0400,0115) - Type 1
        if (!sigItem.contains(Tag.CertificateOfSigner)) {
            result.addError("Certificate of Signer (0400,0115) is missing from signature item", sigPath);
        } else {
            byte[] cert = sigItem.getSafeBytes(Tag.CertificateOfSigner);
            if (cert == null || cert.length == 0) {
                result.addError("Certificate of Signer (0400,0115) is empty", sigPath);
            }
        }

        // Signature (0400,0120) - Type 1
        if (!sigItem.contains(Tag.Signature)) {
            result.addError("Signature (0400,0120) is missing from signature item", sigPath);
        } else {
            byte[] sig = sigItem.getSafeBytes(Tag.Signature);
            if (sig == null || sig.length == 0) {
                result.addError("Signature (0400,0120) is empty", sigPath);
            }
        }

        // MAC Algorithm (0400,0015) - Type 1
        if (!sigItem.contains(Tag.MACAlgorithm)) {
            result.addError("MAC Algorithm (0400,0015) is missing from signature item", sigPath);
        } else {
            String macAlgo = sigItem.getString(Tag.MACAlgorithm);
            // Common values: "RIPEMD160", "SHA1", "SHA256", "SHA384", "SHA512", "MD5"
            if (macAlgo == null || macAlgo.isEmpty()) {
                result.addError("MAC Algorithm (0400,0015) is empty", sigPath);
            }
        }

        // Data Elements Signed (0400,0020) - Type 1
        if (!sigItem.contains(Tag.DataElementsSigned)) {
            result.addError("Data Elements Signed (0400,0020) is missing from signature item", sigPath);
        } else {
            int[] elementsSigned = sigItem.getInts(Tag.DataElementsSigned);
            if (elementsSigned == null || elementsSigned.length == 0) {
                result.addError("Data Elements Signed (0400,0020) is empty - signature must cover at least some elements", sigPath);
            }
        }

        // If all required elements present, signature structure is valid
        boolean structureValid = sigItem.contains(Tag.MACIDNumber) &&
                                sigItem.contains(Tag.DigitalSignatureUID) &&
                                sigItem.contains(Tag.DigitalSignatureDateTime) &&
                                sigItem.contains(Tag.CertificateType) &&
                                sigItem.contains(Tag.CertificateOfSigner) &&
                                sigItem.contains(Tag.Signature) &&
                                sigItem.contains(Tag.MACAlgorithm) &&
                                sigItem.contains(Tag.DataElementsSigned);

        if (structureValid) {
            result.addInfo("Digital Signature structure is valid (cryptographic verification not performed)", sigPath);
        }
    }

    /**
     * Validate MAC Parameters Sequence if present.
     * This is related to the Digital Signatures Sequence.
     */
    public static void validateMACParameters(Attributes dataset, ValidationResult result, String modulePath) {
        if (!dataset.contains(Tag.MACParametersSequence)) {
            return; // Optional
        }

        Sequence macSeq = dataset.getSequence(Tag.MACParametersSequence);
        if (macSeq == null || macSeq.isEmpty()) {
            result.addWarning("MAC Parameters Sequence (0400,0550) is present but empty", modulePath);
            return;
        }

        for (int i = 0; i < macSeq.size(); i++) {
            Attributes macItem = macSeq.get(i);
            String macPath = modulePath + " > MACParametersSequence[" + (i + 1) + "]";

            // MAC ID Number (0400,0005) - Type 1
            if (!macItem.contains(Tag.MACIDNumber)) {
                result.addError("MAC ID Number (0400,0005) is missing from MAC Parameters item", macPath);
            }

            // MAC Calculation Transfer Syntax UID (0400,0010) - Type 1
            if (!macItem.contains(Tag.MACCalculationTransferSyntaxUID)) {
                result.addError("MAC Calculation Transfer Syntax UID (0400,0010) is missing from MAC Parameters item", macPath);
            }

            // MAC Algorithm (0400,0015) - Type 1
            if (!macItem.contains(Tag.MACAlgorithm)) {
                result.addError("MAC Algorithm (0400,0015) is missing from MAC Parameters item", macPath);
            }

            // Data Elements Signed (0400,0020) - Type 1
            if (!macItem.contains(Tag.DataElementsSigned)) {
                result.addError("Data Elements Signed (0400,0020) is missing from MAC Parameters item", macPath);
            }
        }
    }
}

