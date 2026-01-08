package be.uzleuven.ihe.service;

import be.uzleuven.ihe.dicom.convertor.dicom.FHIRToMADOConverter;
import be.uzleuven.ihe.dicom.convertor.fhir.MADOToFHIRConverter;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomOutputStream;
import org.hl7.fhir.r5.model.Bundle;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for DICOM↔FHIR conversion.
 * Provides endpoints for:
 * - Converting DICOM MADO to FHIR ImagingStudy bundles
 * - Converting FHIR ImagingStudy bundles back to DICOM MADO
 * - Round-trip conversion with comparison support
 */
@RestController
@RequestMapping("/api/converter")
@CrossOrigin(origins = "*")
public class ConverterController {

    private final MADOToFHIRConverter matoToFhirConverter = new MADOToFHIRConverter();
    private final FHIRToMADOConverter fhirToMadoConverter = new FHIRToMADOConverter();
    private final FhirContext fhirContext = FhirContext.forR5();

    /**
     * Convert a file from one format to another.
     * DICOM → FHIR or FHIR → DICOM
     */
    @PostMapping(value = "/convert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> convert(
            @RequestParam("file") MultipartFile file,
            @RequestParam("sourceType") String sourceType) {
        try {
            if (file.isEmpty()) {
                return badRequest("No file provided");
            }

            Map<String, Object> response;

            if ("dicom".equalsIgnoreCase(sourceType)) {
                // DICOM → FHIR
                response = convertDicomToFhir(file);
            } else if ("fhir".equalsIgnoreCase(sourceType)) {
                // FHIR → DICOM
                response = convertFhirToDicom(file);
            } else {
                return badRequest("Invalid source type. Must be 'dicom' or 'fhir'");
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("Conversion failed: " + e.getMessage());
            return serverError("Conversion failed: " + e.getMessage());
        }
    }

    /**
     * Perform round-trip conversion and return both original and converted data for comparison.
     * DICOM → FHIR → DICOM or FHIR → DICOM → FHIR
     */
    @PostMapping(value = "/roundtrip", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> roundtrip(
            @RequestParam("file") MultipartFile file,
            @RequestParam("sourceType") String sourceType) {
        try {
            if (file.isEmpty()) {
                return badRequest("No file provided");
            }

            Map<String, Object> response;

            if ("dicom".equalsIgnoreCase(sourceType)) {
                // DICOM → FHIR → DICOM
                response = roundtripDicom(file);
            } else if ("fhir".equalsIgnoreCase(sourceType)) {
                // FHIR → DICOM → FHIR
                response = roundtripFhir(file);
            } else {
                return badRequest("Invalid source type. Must be 'dicom' or 'fhir'");
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("Round-trip conversion failed: " + e.getMessage());
            return serverError("Round-trip conversion failed: " + e.getMessage());
        }
    }

    // =========================
    // DICOM → FHIR Conversion
    // =========================

    private Map<String, Object> convertDicomToFhir(MultipartFile file) throws IOException {
        File tempFile = createTempFile(file);
        try {
            Attributes attrs;
            try (DicomInputStream dis = new DicomInputStream(tempFile)) {
                attrs = dis.readDataset();
            }

            Bundle fhirBundle = matoToFhirConverter.convert(attrs);
            IParser parser = fhirContext.newJsonParser();
            parser.setPrettyPrint(true);
            parser.setOverrideResourceIdWithBundleEntryFullUrl(false);
            String fhirJson = parser.encodeResourceToString(fhirBundle);

            Map<String, Object> response = new HashMap<>();
            response.put("sourceType", "dicom");
            response.put("targetType", "fhir");
            response.put("converted", parseJsonToMap(fhirJson));
            response.put("fhirJson", fhirJson);

            return response;
        } finally {
            if (!tempFile.delete()) {
                System.err.println("Failed to delete temp file: " + tempFile.getAbsolutePath());
            }
        }
    }

    // =========================
    // FHIR → DICOM Conversion
    // =========================

    private Map<String, Object> convertFhirToDicom(MultipartFile file) throws IOException {
        String fhirJson = new String(file.getBytes(), StandardCharsets.UTF_8);

        Attributes dicomAttrs = fhirToMadoConverter.convertFromJson(fhirJson);

        // Generate dcmdump output
        String dcmdump = generateDcmdump(dicomAttrs);

        // Generate binary DICOM
        byte[] dicomBytes = generateDicomBytes(dicomAttrs);
        String base64Dicom = Base64.getEncoder().encodeToString(dicomBytes);

        // Suggest filename from SOP Instance UID
        String sopInstanceUid = dicomAttrs.getString(Tag.SOPInstanceUID, "output");
        String suggestedFilename = sopInstanceUid + ".dcm";

        Map<String, Object> response = new HashMap<>();
        response.put("sourceType", "fhir");
        response.put("targetType", "dicom");
        response.put("dcmdump", dcmdump);
        response.put("convertedBase64", base64Dicom);
        response.put("suggestedFilename", suggestedFilename);

        return response;
    }

    // =========================
    // Round-trip: DICOM → FHIR → DICOM
    // =========================

    private Map<String, Object> roundtripDicom(MultipartFile file) throws IOException {
        File tempFile = createTempFile(file);
        try {
            // Step 1: Read original DICOM
            Attributes originalAttrs;
            try (DicomInputStream dis = new DicomInputStream(tempFile)) {
                originalAttrs = dis.readDataset();
            }
            String originalDcmdump = generateDcmdump(originalAttrs);

            // Step 2: Convert to FHIR
            Bundle fhirBundle = matoToFhirConverter.convert(originalAttrs);
            IParser fhirParser = fhirContext.newJsonParser();
            fhirParser.setPrettyPrint(true);
            fhirParser.setOverrideResourceIdWithBundleEntryFullUrl(false);
            String fhirJson = fhirParser.encodeResourceToString(fhirBundle);

            // Step 3: Convert back to DICOM
            Attributes roundtripAttrs = fhirToMadoConverter.convertFromJson(fhirJson);
            String roundtripDcmdump = generateDcmdump(roundtripAttrs);

            // Generate binary for download
            byte[] dicomBytes = generateDicomBytes(roundtripAttrs);
            String base64Dicom = Base64.getEncoder().encodeToString(dicomBytes);

            String sopInstanceUid = roundtripAttrs.getString(Tag.SOPInstanceUID, "roundtrip");

            Map<String, Object> response = new HashMap<>();
            response.put("sourceType", "dicom");
            response.put("targetType", "fhir");

            // Intermediate result (FHIR)
            response.put("converted", parseJsonToMap(fhirJson));
            response.put("fhirJson", fhirJson);

            // Round-trip comparison data
            response.put("original", originalDcmdump);
            response.put("roundtrip", roundtripDcmdump);

            // Download data for round-trip DICOM
            response.put("convertedBase64", base64Dicom);
            response.put("dcmdump", roundtripDcmdump);
            response.put("suggestedFilename", sopInstanceUid + ".dcm");

            return response;
        } finally {
            if (!tempFile.delete()) {
                System.err.println("Failed to delete temp file: " + tempFile.getAbsolutePath());
            }
        }
    }

    // =========================
    // Round-trip: FHIR → DICOM → FHIR
    // =========================

    private Map<String, Object> roundtripFhir(MultipartFile file) throws IOException {
        String originalFhirJson = new String(file.getBytes(), StandardCharsets.UTF_8);

        // Step 1: Use original file content as-is (don't re-parse to preserve exact content)
        String prettyOriginalJson = originalFhirJson;

        // Step 2: Convert to DICOM
        Attributes dicomAttrs = fhirToMadoConverter.convertFromJson(originalFhirJson);
        String dcmdump = generateDcmdump(dicomAttrs);
        byte[] dicomBytes = generateDicomBytes(dicomAttrs);
        String base64Dicom = Base64.getEncoder().encodeToString(dicomBytes);

        // Step 3: Convert back to FHIR (this is where we apply our ID preservation settings)
        Bundle roundtripBundle = matoToFhirConverter.convert(dicomAttrs);
        IParser roundtripParser = fhirContext.newJsonParser();
        roundtripParser.setPrettyPrint(true);
        roundtripParser.setOverrideResourceIdWithBundleEntryFullUrl(false);
        String roundtripFhirJson = roundtripParser.encodeResourceToString(roundtripBundle);

        String sopInstanceUid = dicomAttrs.getString(Tag.SOPInstanceUID, "roundtrip");

        Map<String, Object> response = new HashMap<>();
        response.put("sourceType", "fhir");
        response.put("targetType", "dicom");

        // Intermediate result (DICOM)
        response.put("dcmdump", dcmdump);
        response.put("convertedBase64", base64Dicom);
        response.put("suggestedFilename", sopInstanceUid + ".dcm");

        // Round-trip comparison data (send as JSON strings to preserve exact formatting)
        response.put("original", prettyOriginalJson);
        response.put("roundtrip", roundtripFhirJson);
        response.put("converted", parseJsonToMap(roundtripFhirJson));

        return response;
    }

    // =========================
    // Helper Methods
    // =========================

    private File createTempFile(MultipartFile file) throws IOException {
        File tempFile = File.createTempFile("converter_", ".dcm");
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(file.getBytes());
        }
        return tempFile;
    }

    private String generateDcmdump(Attributes attrs) {
        // Use the toString method which provides dcmdump-like output
        // Parameters: maxWidth for each attribute value, maxLines per attribute
        return attrs.toString(Integer.MAX_VALUE, 120);
    }

    private byte[] generateDicomBytes(Attributes attrs) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DicomOutputStream dos = new DicomOutputStream(baos, attrs.getString(Tag.TransferSyntaxUID, "1.2.840.10008.1.2.1"))) {
            dos.writeDataset(null, attrs);
        }
        return baos.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonToMap(String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("raw", json);
            return fallback;
        }
    }

    private ResponseEntity<?> badRequest(String message) {
        Map<String, String> error = new HashMap<>();
        error.put("error", message);
        return ResponseEntity.badRequest().body(error);
    }

    private ResponseEntity<?> serverError(String message) {
        Map<String, String> error = new HashMap<>();
        error.put("error", message);
        return ResponseEntity.status(500).body(error);
    }
}

