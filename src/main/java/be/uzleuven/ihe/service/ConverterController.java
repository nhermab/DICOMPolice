package be.uzleuven.ihe.service;

import be.uzleuven.ihe.dicom.convertor.dicom.FHIRToMADOConverter;
import be.uzleuven.ihe.dicom.convertor.fhir.MADOToFHIRConverter;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomOutputStream;
import org.hl7.fhir.r5.model.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static be.uzleuven.ihe.singletons.HAPI.FHIR_R5_CONTEXT;

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

    private static final Logger LOG = LoggerFactory.getLogger(ConverterController.class);

    private final MADOToFHIRConverter matoToFhirConverter = new MADOToFHIRConverter();
    private final FHIRToMADOConverter fhirToMadoConverter = new FHIRToMADOConverter();

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

            LOG.info("Converting {} file: {}, size: {} bytes", sourceType, file.getOriginalFilename(), file.getSize());

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
            LOG.error("Conversion failed: {}", e.getMessage(), e);
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

            LOG.info("Round-trip conversion for {} file: {}, size: {} bytes", sourceType, file.getOriginalFilename(), file.getSize());

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
            LOG.error("Round-trip conversion failed: {}", e.getMessage(), e);
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
            IParser parser = FHIR_R5_CONTEXT.newJsonParser();
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
                LOG.warn("Failed to delete temp file: {}", tempFile.getAbsolutePath());
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
            IParser fhirParser = FHIR_R5_CONTEXT.newJsonParser();
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
                LOG.warn("Failed to delete temp file: {}", tempFile.getAbsolutePath());
            }
        }
    }

    // =========================
    // Round-trip: FHIR → DICOM → FHIR
    // =========================

    private Map<String, Object> roundtripFhir(MultipartFile file) throws IOException {
        String originalFhirJson = new String(file.getBytes(), StandardCharsets.UTF_8);

        // Step 1: Parse and re-format original to ensure consistent formatting for comparison
        Bundle originalBundle = (Bundle) FHIR_R5_CONTEXT.newJsonParser().parseResource(originalFhirJson);
        IParser originalParser = FHIR_R5_CONTEXT.newJsonParser();
        originalParser.setPrettyPrint(true);
        originalParser.setOverrideResourceIdWithBundleEntryFullUrl(false);
        String prettyOriginalJson = originalParser.encodeResourceToString(originalBundle);

        // Step 2: Convert to DICOM
        Attributes dicomAttrs = fhirToMadoConverter.convertFromJson(originalFhirJson);
        String dcmdump = generateDcmdump(dicomAttrs);
        byte[] dicomBytes = generateDicomBytes(dicomAttrs);
        String base64Dicom = Base64.getEncoder().encodeToString(dicomBytes);

        // Step 3: Convert back to FHIR (this is where we apply our ID preservation settings)
        Bundle roundtripBundle = matoToFhirConverter.convert(dicomAttrs);
        IParser roundtripParser = FHIR_R5_CONTEXT.newJsonParser();
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

    /**
     * Generate DICOM bytes in Part 10 format (with 128-byte preamble, "DICM" prefix, and File Meta Information).
     * 
     * @param attrs The DICOM dataset to serialize
     * @return DICOM Part 10 formatted bytes
     * @throws IOException if writing fails
     */
    private byte[] generateDicomBytes(Attributes attrs) throws IOException {
        // Determine transfer syntax, defaulting to Explicit VR Little Endian
        String transferSyntax = attrs.getString(Tag.TransferSyntaxUID, UID.ExplicitVRLittleEndian);
        
        LOG.debug("Generating DICOM Part 10 bytes with transfer syntax: {}", transferSyntax);
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DicomOutputStream dos = new DicomOutputStream(baos, transferSyntax)) {
            // Write File Meta Information + Dataset in Part 10 format
            // This creates the 128-byte preamble, "DICM" prefix, and File Meta tags
            Attributes fmi = attrs.createFileMetaInformation(transferSyntax);
            dos.writeDataset(fmi, attrs);
            
            LOG.info("Generated DICOM Part 10 file: {} bytes, SOP Instance UID: {}", 
                    baos.size(), attrs.getString(Tag.SOPInstanceUID, "unknown"));
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

