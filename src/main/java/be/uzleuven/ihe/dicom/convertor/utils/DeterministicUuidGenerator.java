package be.uzleuven.ihe.dicom.convertor.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Generates deterministic UUIDs based on input strings, allowing for reproducible
 * UUID generation across multiple conversions of the same DICOM data.
 * <p>
 * This is useful for comparing conversion outputs when converting the same DICOM
 * file multiple times - the UUIDs will remain constant if the input data is the same.
 * <p>
 * Uses UUID v5 (SHA-1 based, RFC 4122) for deterministic generation.
 */
public class DeterministicUuidGenerator {

    // Namespace UUID for DICOM-to-FHIR conversions (randomly generated, fixed)
    private static final UUID NAMESPACE_DICOM_FHIR = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");

    /**
     * Generates a deterministic UUID v5 from a string input.
     * Same input will always produce the same UUID.
     *
     * @param name The input string to generate UUID from
     * @return A deterministic UUID
     */
    public static UUID generateUuidV5(String name) {
        return generateUuidV5(NAMESPACE_DICOM_FHIR, name);
    }

    /**
     * Generates a deterministic UUID v5 from a namespace UUID and a name.
     * Implementation of RFC 4122 UUID v5 generation.
     *
     * @param namespace The namespace UUID
     * @param name The name to generate UUID from
     * @return A deterministic UUID v5
     */
    public static UUID generateUuidV5(UUID namespace, String name) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");

            // Add namespace UUID bytes
            md.update(toBytes(namespace));

            // Add name bytes
            md.update(name.getBytes(StandardCharsets.UTF_8));

            byte[] hash = md.digest();

            // Set version to 5 (UUID v5 = SHA-1)
            hash[6] &= 0x0f;  // Clear version
            hash[6] |= 0x50;  // Set to version 5

            // Set variant to RFC 4122
            hash[8] &= 0x3f;  // Clear variant
            hash[8] |= 0x80;  // Set to RFC 4122 variant

            return fromBytes(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 algorithm not available", e);
        }
    }

    /**
     * Converts UUID to bytes for hashing.
     */
    private static byte[] toBytes(UUID uuid) {
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        byte[] buffer = new byte[16];

        for (int i = 0; i < 8; i++) {
            buffer[i] = (byte) (msb >>> 8 * (7 - i));
        }
        for (int i = 8; i < 16; i++) {
            buffer[i] = (byte) (lsb >>> 8 * (7 - i));
        }

        return buffer;
    }

    /**
     * Converts first 16 bytes to UUID.
     */
    private static UUID fromBytes(byte[] data) {
        long msb = 0;
        long lsb = 0;

        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (data[i] & 0xff);
        }
        for (int i = 8; i < 16; i++) {
            lsb = (lsb << 8) | (data[i] & 0xff);
        }

        return new UUID(msb, lsb);
    }

    /**
     * Generates a deterministic UUID for a FHIR Composition resource.
     *
     * @param sopInstanceUID The SOP Instance UID from the DICOM manifest
     * @return A deterministic UUID
     */
    public static String generateCompositionUuid(String sopInstanceUID) {
        return generateUuidV5("composition:" + sopInstanceUID).toString();
    }

    /**
     * Generates a deterministic UUID for a FHIR Patient resource.
     *
     * @param patientID The Patient ID from DICOM
     * @param issuerOfPatientID The Issuer of Patient ID (can be null)
     * @return A deterministic UUID
     */
    public static String generatePatientUuid(String patientID, String issuerOfPatientID) {
        String key = "patient:" + patientID;
        if (issuerOfPatientID != null && !issuerOfPatientID.isEmpty()) {
            key += ":" + issuerOfPatientID;
        }
        return generateUuidV5(key).toString();
    }

    /**
     * Generates a deterministic UUID for a FHIR ImagingStudy resource.
     *
     * @param studyInstanceUID The Study Instance UID from DICOM
     * @return A deterministic UUID
     */
    public static String generateStudyUuid(String studyInstanceUID) {
        return generateUuidV5("study:" + studyInstanceUID).toString();
    }

    /**
     * Generates a deterministic UUID for a FHIR Device resource.
     *
     * @param manufacturer The manufacturer from DICOM (can be null or empty)
     * @param sopInstanceUID The SOP Instance UID (for uniqueness per document)
     * @return A deterministic UUID
     */
    public static String generateDeviceUuid(String manufacturer, String sopInstanceUID) {
        // Use empty string for null manufacturer to ensure consistency
        String mfr = (manufacturer != null) ? manufacturer : "";
        String key = "device:" + mfr + ":" + sopInstanceUID;
        return generateUuidV5(key).toString();
    }

    /**
     * Generates a deterministic UUID for a FHIR Practitioner resource.
     *
     * @param referringPhysicianName The referring physician name from DICOM
     * @return A deterministic UUID
     */
    public static String generatePractitionerUuid(String referringPhysicianName) {
        return generateUuidV5("practitioner:" + referringPhysicianName).toString();
    }

    /**
     * Generates a deterministic UUID for a FHIR Endpoint resource.
     *
     * @param endpointAddress The endpoint address/URL
     * @return A deterministic UUID
     */
    public static String generateEndpointUuid(String endpointAddress) {
        return generateUuidV5("endpoint:" + endpointAddress).toString();
    }

    /**
     * Generates a deterministic UUID for a FHIR ImagingSelection resource.
     *
     * @param studyInstanceUID The Study Instance UID
     * @param seriesInstanceUID The Series Instance UID
     * @param sopInstanceUID The SOP Instance UID
     * @return A deterministic UUID
     */
    public static String generateImagingSelectionUuid(String studyInstanceUID, String seriesInstanceUID, String sopInstanceUID) {
        return generateUuidV5("imagingselection:" + studyInstanceUID + ":" + seriesInstanceUID + ":" + sopInstanceUID).toString();
    }
}

