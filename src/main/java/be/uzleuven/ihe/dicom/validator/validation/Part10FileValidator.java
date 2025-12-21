package be.uzleuven.ihe.dicom.validator.validation;

import be.uzleuven.ihe.dicom.validator.model.ValidationResult;
import be.uzleuven.ihe.dicom.constants.DicomConstants;

import java.io.File;
import java.io.RandomAccessFile;
import java.io.IOException;

/**
 * Validator for DICOM Part 10 File Format compliance.
 * Validates the 128-byte preamble, "DICM" prefix, and file structure.
 *
 * Per MADO Requirements V-STR-01:
 * - File MUST begin with 128-byte preamble (typically null bytes)
 * - Bytes 128-131 MUST contain ASCII "DICM"
 *
 * This validator operates directly on the file before dataset parsing.
 */
public final class Part10FileValidator {

    private Part10FileValidator() {
    }

    /**
     * Validate DICOM Part 10 file format.
     *
     * @param file The DICOM file to validate
     * @param result The validation result to append findings to
     * @param modulePath The module path for error reporting
     */
    public static void validatePart10FileFormat(File file, ValidationResult result, String modulePath) {
        if (file == null || !file.exists() || !file.isFile()) {
            result.addError("File does not exist or is not a valid file", modulePath);
            return;
        }

        // File must be at least 132 bytes (128 preamble + 4 for "DICM")
        long fileSize = file.length();
        if (fileSize < 132) {
            result.addError("File is too small to be a valid DICOM Part 10 file. " +
                          "Must be at least 132 bytes (128-byte preamble + 'DICM' prefix). " +
                          "Actual size: " + fileSize + " bytes", modulePath);
            return;
        }

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            // Read first 132 bytes
            byte[] header = new byte[132];
            int bytesRead = raf.read(header);

            if (bytesRead < 132) {
                result.addError("Unable to read 132-byte file header. Read only " + bytesRead + " bytes", modulePath);
                return;
            }

            // Check "DICM" prefix at bytes 128-131
            if (header[128] != 'D' || header[129] != 'I' ||
                header[130] != 'C' || header[131] != 'M') {

                // Show what was found instead
                String found = String.format("0x%02X 0x%02X 0x%02X 0x%02X",
                    header[128] & 0xFF, header[129] & 0xFF,
                    header[130] & 0xFF, header[131] & 0xFF);

                result.addError("DICOM Part 10 File Format Violation (V-STR-01): " +
                              "Bytes 128-131 MUST contain ASCII 'DICM' but found: " + found + ". " +
                              "This file cannot be recognized as a valid DICOM Part 10 file.", modulePath);
                return;
            }

            // Check preamble (first 128 bytes)
            // Per standard, content is not constrained but typically null bytes
            boolean allZero = true;
            for (int i = 0; i < 128; i++) {
                if (header[i] != 0) {
                    allZero = false;
                    break;
                }
            }

            if (allZero) {
                result.addInfo("DICOM Part 10 File Preamble: 128 bytes of 0x00 (standard compliant)", modulePath);
            } else {
                result.addInfo("DICOM Part 10 File Preamble: Contains non-zero data (allowed by standard)", modulePath);
            }

            result.addInfo("DICOM Part 10 File Format: 'DICM' prefix verified at bytes 128-131", modulePath);

        } catch (IOException e) {
            result.addError("Unable to read file for Part 10 validation: " + e.getMessage(), modulePath);
        }
    }

    /**
     * Validate that File Meta Information Group (0002) is present and correct.
     * This is called after dataset parsing but checks File Meta compliance.
     *
     * Per MADO Requirements V-STR-02:
     * - Media Storage SOP Class UID (0002,0002) MUST equal Key Object Selection UID
     * - Transfer Syntax UID (0002,0010) SHOULD be Explicit VR Little Endian
     *
     * @param dataset The parsed DICOM dataset (may contain File Meta if included)
     * @param result The validation result
     * @param modulePath The module path
     */
    public static void validateFileMetaInformation(org.dcm4che3.data.Attributes dataset,
                                                   ValidationResult result, String modulePath) {
        // Check for File Meta Information presence
        // Note: dcm4che3 may store File Meta separately, so we check common tags

        String mediaStorageSOPClassUID = dataset.getString(org.dcm4che3.data.Tag.MediaStorageSOPClassUID);
        String mediaStorageSOPInstanceUID = dataset.getString(org.dcm4che3.data.Tag.MediaStorageSOPInstanceUID);
        String transferSyntaxUID = dataset.getString(org.dcm4che3.data.Tag.TransferSyntaxUID);

        boolean hasFileMeta = (mediaStorageSOPClassUID != null ||
                              mediaStorageSOPInstanceUID != null ||
                              transferSyntaxUID != null);

        if (!hasFileMeta) {
            // File Meta might have been stripped during parsing - this is common
            result.addInfo("File Meta Information (Group 0002) not present in parsed dataset. " +
                         "dcm4che3 may have processed it separately. Cannot validate V-STR-02.", modulePath);
            return;
        }

        // Validate Media Storage SOP Class UID
        if (mediaStorageSOPClassUID != null) {
            String sopClassUID = dataset.getString(org.dcm4che3.data.Tag.SOPClassUID);
            if (sopClassUID != null && !mediaStorageSOPClassUID.equals(sopClassUID)) {
                result.addError("File Meta Information: MediaStorageSOPClassUID (0002,0002) does not match " +
                              "SOPClassUID (0008,0016). Found: " + mediaStorageSOPClassUID +
                              " vs " + sopClassUID, modulePath);
            }

            // For MADO, must be Key Object Selection
            if (!DicomConstants.KEY_OBJECT_SELECTION_SOP_CLASS_UID.equals(mediaStorageSOPClassUID)) {
                result.addWarning("MediaStorageSOPClassUID is not Key Object Selection Document Storage " +
                                "(" + DicomConstants.KEY_OBJECT_SELECTION_SOP_CLASS_UID + "). Found: " + mediaStorageSOPClassUID, modulePath);
            }
        }

        // Validate Media Storage SOP Instance UID
        if (mediaStorageSOPInstanceUID != null) {
            String sopInstanceUID = dataset.getString(org.dcm4che3.data.Tag.SOPInstanceUID);
            if (sopInstanceUID != null && !mediaStorageSOPInstanceUID.equals(sopInstanceUID)) {
                result.addError("File Meta Information: MediaStorageSOPInstanceUID (0002,0003) does not match " +
                              "SOPInstanceUID (0008,0018). Found: " + mediaStorageSOPInstanceUID +
                              " vs " + sopInstanceUID, modulePath);
            }
        }

        // Validate Transfer Syntax
        validateTransferSyntax(transferSyntaxUID, result, modulePath);
    }

    /**
     * Validate Transfer Syntax UID.
     * MADO strongly recommends Explicit VR Little Endian for maximum interoperability.
     */
    private static void validateTransferSyntax(String transferSyntaxUID,
                                              ValidationResult result, String modulePath) {
        if (transferSyntaxUID == null) {
            result.addInfo("Transfer Syntax UID not available for validation", modulePath);
            return;
        }

        switch (transferSyntaxUID) {
            case DicomConstants.TRANSFER_SYNTAX_EXPLICIT_VR_LITTLE_ENDIAN: // Explicit VR Little Endian
                result.addInfo("Transfer Syntax: Explicit VR Little Endian (recommended for MADO)", modulePath);
                break;

            case DicomConstants.TRANSFER_SYNTAX_IMPLICIT_VR_LITTLE_ENDIAN: // Implicit VR Little Endian
                result.addWarning("Transfer Syntax: Implicit VR Little Endian. " +
                                "While permitted by DICOM standard, MADO strongly recommends " +
                                "Explicit VR Little Endian (" + DicomConstants.TRANSFER_SYNTAX_EXPLICIT_VR_LITTLE_ENDIAN + ") for maximum interoperability " +
                                "due to ambiguity in VR handling.", modulePath);
                break;

            case DicomConstants.TRANSFER_SYNTAX_EXPLICIT_VR_BIG_ENDIAN: // Explicit VR Big Endian
                result.addWarning("Transfer Syntax: Explicit VR Big Endian (retired). " +
                                "Use Explicit VR Little Endian for better compatibility.", modulePath);
                break;

            default:
                // Compressed or other transfer syntaxes
                if (transferSyntaxUID.startsWith(DicomConstants.TRANSFER_SYNTAX_COMPRESSED_PREFIX)) {
                    result.addWarning("Transfer Syntax: Compressed format (" + transferSyntaxUID + "). " +
                                    "MADO manifests are metadata documents and should use uncompressed " +
                                    "Explicit VR Little Endian for maximum interoperability.", modulePath);
                } else {
                    result.addInfo("Transfer Syntax: " + transferSyntaxUID, modulePath);
                }
                break;
        }
    }
}

