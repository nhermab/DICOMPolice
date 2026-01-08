package be.uzleuven.ihe.dicom.convertor.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DeterministicUuidGenerator.
 */
public class DeterministicUuidGeneratorTest {

    @Test
    public void testDeterministicGeneration() {
        String sopInstanceUID = "1.2.3.4.5";

        // Generate UUID twice with same input
        String uuid1 = DeterministicUuidGenerator.generateCompositionUuid(sopInstanceUID);
        String uuid2 = DeterministicUuidGenerator.generateCompositionUuid(sopInstanceUID);

        // Should be identical
        assertEquals(uuid1, uuid2, "UUIDs should be identical for same input");

        // Should be valid UUID format
        assertTrue(uuid1.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
            "Should be valid UUID format");
    }

    @Test
    public void testDifferentInputsProduceDifferentUuids() {
        String uuid1 = DeterministicUuidGenerator.generateCompositionUuid("1.2.3.4.5");
        String uuid2 = DeterministicUuidGenerator.generateCompositionUuid("1.2.3.4.6");

        assertNotEquals(uuid1, uuid2, "Different inputs should produce different UUIDs");
    }

    @Test
    public void testPatientUuidWithIssuer() {
        String patientId = "12345";
        String issuer = "HOSPITAL_A";

        String uuid1 = DeterministicUuidGenerator.generatePatientUuid(patientId, issuer);
        String uuid2 = DeterministicUuidGenerator.generatePatientUuid(patientId, issuer);

        assertEquals(uuid1, uuid2, "Patient UUIDs should be identical for same input");
    }

    @Test
    public void testPatientUuidWithoutIssuer() {
        String patientId = "12345";

        String uuid1 = DeterministicUuidGenerator.generatePatientUuid(patientId, null);
        String uuid2 = DeterministicUuidGenerator.generatePatientUuid(patientId, "");

        assertEquals(uuid1, uuid2, "Patient UUIDs with null and empty issuer should match");
    }

    @Test
    public void testStudyUuid() {
        String studyInstanceUID = "1.2.840.113619.2.5.1762583153.215519.978957063.78";

        String uuid1 = DeterministicUuidGenerator.generateStudyUuid(studyInstanceUID);
        String uuid2 = DeterministicUuidGenerator.generateStudyUuid(studyInstanceUID);

        assertEquals(uuid1, uuid2, "Study UUIDs should be identical for same input");
    }

    @Test
    public void testEndpointUuid() {
        String endpoint = "https://pacs.example.org/dicom-web";

        String uuid1 = DeterministicUuidGenerator.generateEndpointUuid(endpoint);
        String uuid2 = DeterministicUuidGenerator.generateEndpointUuid(endpoint);

        assertEquals(uuid1, uuid2, "Endpoint UUIDs should be identical for same input");
    }

    @Test
    public void testImagingSelectionUuid() {
        String studyUID = "1.2.3";
        String seriesUID = "1.2.3.4";
        String sopUID = "1.2.3.4.5";

        String uuid1 = DeterministicUuidGenerator.generateImagingSelectionUuid(studyUID, seriesUID, sopUID);
        String uuid2 = DeterministicUuidGenerator.generateImagingSelectionUuid(studyUID, seriesUID, sopUID);

        assertEquals(uuid1, uuid2, "ImagingSelection UUIDs should be identical for same input");
    }
}

