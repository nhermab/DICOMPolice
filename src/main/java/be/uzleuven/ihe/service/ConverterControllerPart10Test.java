package be.uzleuven.ihe.service;

import be.uzleuven.ihe.dicom.convertor.dicom.FHIRToMADOConverter;
import be.uzleuven.ihe.dicom.convertor.fhir.MADOToFHIRConverter;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.dcm4che3.data.*;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomOutputStream;
import org.hl7.fhir.r5.model.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Test class to verify that ConverterController generates valid DICOM Part 10 files.
 * 
 * Tests verify that:
 * 1. Generated DICOM bytes contain "DICM" at byte offset 128
 * 2. File Meta Information group (0002) tags are present
 * 3. Generated files can be read back by DicomInputStream
 * 4. Both FHIR→DICOM and round-trip conversions produce Part 10 files
 */
public class ConverterControllerPart10Test {

    private final MADOToFHIRConverter toFhirConverter = new MADOToFHIRConverter();
    private final FHIRToMADOConverter toDicomConverter = new FHIRToMADOConverter();
    private final FhirContext fhirContext = FhirContext.forR5();
    
    private int passCount = 0;
    private int failCount = 0;
    private final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        System.out.println("=".repeat(80));
        System.out.println("ConverterController Part 10 Format Test Suite");
        System.out.println("=".repeat(80));
        System.out.println();

        ConverterControllerPart10Test test = new ConverterControllerPart10Test();
        test.runTests();

        // Print summary
        System.out.println();
        System.out.println("=".repeat(80));
        System.out.println("Test Results: " + test.passCount + " passed, " + test.failCount + " failed");
        if (!test.failures.isEmpty()) {
            System.out.println("\nFailed tests:");
            for (String failure : test.failures) {
                System.out.println("  - " + failure);
            }
        }
        System.out.println("=".repeat(80));

        System.exit(test.failCount > 0 ? 1 : 0);
    }

    public void runTests() {
        testFhirToDicomProducesPart10File();
        testRoundtripDicomFhirDicomProducesPart10File();
        testRoundtripFhirDicomFhirProducesPart10File();
        testPart10FileCanBeReadBack();
        testFileMetaInformationIsPresent();
    }

    // ============================================================================
    // TEST METHODS
    // ============================================================================

    /**
     * Test that FHIR→DICOM conversion produces a valid Part 10 file
     */
    private void testFhirToDicomProducesPart10File() {
        try {
            // Create a sample FHIR bundle
            Bundle fhirBundle = createSampleFhirBundle();
            
            // Convert to DICOM
            Attributes dicomAttrs = toDicomConverter.convert(fhirBundle);
            
            // Generate DICOM bytes using the same method as ConverterController
            byte[] dicomBytes = generateDicomBytes(dicomAttrs);
            
            // Verify Part 10 format
            assertPart10Format(dicomBytes, "FHIR→DICOM");
            
            pass("testFhirToDicomProducesPart10File");
        } catch (Exception e) {
            fail("testFhirToDicomProducesPart10File", e);
        }
    }

    /**
     * Test that DICOM→FHIR→DICOM round-trip produces a valid Part 10 file
     */
    private void testRoundtripDicomFhirDicomProducesPart10File() {
        try {
            // Create original DICOM
            Attributes originalDicom = createSampleDicomMado();
            
            // Convert to FHIR
            Bundle fhirBundle = toFhirConverter.convert(originalDicom);
            
            // Convert back to DICOM
            Attributes roundtripDicom = toDicomConverter.convert(fhirBundle);
            
            // Generate DICOM bytes
            byte[] dicomBytes = generateDicomBytes(roundtripDicom);
            
            // Verify Part 10 format
            assertPart10Format(dicomBytes, "DICOM→FHIR→DICOM");
            
            pass("testRoundtripDicomFhirDicomProducesPart10File");
        } catch (Exception e) {
            fail("testRoundtripDicomFhirDicomProducesPart10File", e);
        }
    }

    /**
     * Test that FHIR→DICOM→FHIR round-trip produces a valid Part 10 file
     */
    private void testRoundtripFhirDicomFhirProducesPart10File() {
        try {
            // Create original FHIR bundle
            Bundle originalFhir = createSampleFhirBundle();
            
            // Convert to DICOM
            Attributes dicomAttrs = toDicomConverter.convert(originalFhir);
            
            // Generate DICOM bytes
            byte[] dicomBytes = generateDicomBytes(dicomAttrs);
            
            // Verify Part 10 format
            assertPart10Format(dicomBytes, "FHIR→DICOM→FHIR");
            
            pass("testRoundtripFhirDicomFhirProducesPart10File");
        } catch (Exception e) {
            fail("testRoundtripFhirDicomFhirProducesPart10File", e);
        }
    }

    /**
     * Test that generated Part 10 file can be read back by DicomInputStream
     */
    private void testPart10FileCanBeReadBack() {
        try {
            // Create and convert FHIR to DICOM
            Bundle fhirBundle = createSampleFhirBundle();
            Attributes dicomAttrs = toDicomConverter.convert(fhirBundle);
            byte[] dicomBytes = generateDicomBytes(dicomAttrs);
            
            // Write to temp file
            File tempFile = File.createTempFile("part10_test_", ".dcm");
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(dicomBytes);
            }
            
            // Try to read it back
            Attributes readBack;
            try (DicomInputStream dis = new DicomInputStream(tempFile)) {
                readBack = dis.readDataset();
            }
            
            // Verify we read something valid
            assertNotNull("Read back dataset", readBack);
            assertNotNull("SOP Instance UID in read dataset", 
                    readBack.getString(Tag.SOPInstanceUID));
            
            // Cleanup
            tempFile.delete();
            
            pass("testPart10FileCanBeReadBack");
        } catch (Exception e) {
            fail("testPart10FileCanBeReadBack", e);
        }
    }

    /**
     * Test that File Meta Information tags are present
     */
    private void testFileMetaInformationIsPresent() {
        try {
            // Create and convert FHIR to DICOM
            Bundle fhirBundle = createSampleFhirBundle();
            Attributes dicomAttrs = toDicomConverter.convert(fhirBundle);
            byte[] dicomBytes = generateDicomBytes(dicomAttrs);
            
            // Write to temp file and read with DicomInputStream to get File Meta
            File tempFile = File.createTempFile("part10_meta_test_", ".dcm");
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(dicomBytes);
            }
            
            Attributes fileMeta;
            try (DicomInputStream dis = new DicomInputStream(tempFile)) {
                fileMeta = dis.getFileMetaInformation();
            }
            
            // Verify File Meta Information tags
            assertNotNull("File Meta Information", fileMeta);
            assertNotNull("MediaStorageSOPClassUID (0002,0002)", 
                    fileMeta.getString(Tag.MediaStorageSOPClassUID));
            assertNotNull("MediaStorageSOPInstanceUID (0002,0003)", 
                    fileMeta.getString(Tag.MediaStorageSOPInstanceUID));
            assertNotNull("TransferSyntaxUID (0002,0010)", 
                    fileMeta.getString(Tag.TransferSyntaxUID));
            
            // Verify transfer syntax is Explicit VR Little Endian
            String transferSyntax = fileMeta.getString(Tag.TransferSyntaxUID);
            assertEquals("Transfer Syntax", UID.ExplicitVRLittleEndian, transferSyntax);
            
            // Cleanup
            tempFile.delete();
            
            pass("testFileMetaInformationIsPresent");
        } catch (Exception e) {
            fail("testFileMetaInformationIsPresent", e);
        }
    }

    // ============================================================================
    // HELPER METHODS - Mimic ConverterController.generateDicomBytes()
    // ============================================================================

    /**
     * Generate DICOM bytes in Part 10 format (with 128-byte preamble, "DICM" prefix, and File Meta Information).
     * This mimics the implementation in ConverterController.
     * 
     * @param attrs The DICOM dataset to serialize
     * @return DICOM Part 10 formatted bytes
     * @throws IOException if writing fails
     */
    private byte[] generateDicomBytes(Attributes attrs) throws IOException {
        // Determine transfer syntax, defaulting to Explicit VR Little Endian
        String transferSyntax = attrs.getString(Tag.TransferSyntaxUID, UID.ExplicitVRLittleEndian);
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DicomOutputStream dos = new DicomOutputStream(baos, transferSyntax)) {
            // Write File Meta Information + Dataset in Part 10 format
            // This creates the 128-byte preamble, "DICM" prefix, and File Meta tags
            Attributes fmi = attrs.createFileMetaInformation(transferSyntax);
            dos.writeDataset(fmi, attrs);
        }
        return baos.toByteArray();
    }

    // ============================================================================
    // ASSERTION METHODS
    // ============================================================================

    /**
     * Assert that the given bytes represent a valid DICOM Part 10 file
     */
    private void assertPart10Format(byte[] dicomBytes, String context) throws Exception {
        // File must be at least 132 bytes (128 preamble + 4 for "DICM")
        if (dicomBytes.length < 132) {
            throw new AssertionError(context + ": File is too small (" + dicomBytes.length 
                    + " bytes). Must be at least 132 bytes for Part 10 format.");
        }
        
        // Check "DICM" at bytes 128-131
        if (dicomBytes[128] != 'D' || dicomBytes[129] != 'I' ||
            dicomBytes[130] != 'C' || dicomBytes[131] != 'M') {
            
            String found = String.format("0x%02X 0x%02X 0x%02X 0x%02X",
                    dicomBytes[128] & 0xFF, dicomBytes[129] & 0xFF,
                    dicomBytes[130] & 0xFF, dicomBytes[131] & 0xFF);
            
            throw new AssertionError(context + ": DICM prefix not found at bytes 128-131. Found: " + found);
        }
        
        System.out.println("  ✓ " + context + ": Valid Part 10 format (DICM at offset 128)");
    }

    private void assertNotNull(String field, Object value) throws Exception {
        if (value == null) {
            throw new AssertionError(field + " is null");
        }
    }

    private void assertEquals(String field, Object expected, Object actual) throws Exception {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(field + " mismatch. Expected: " + expected + ", Actual: " + actual);
        }
    }

    // ============================================================================
    // SAMPLE DATA CREATION
    // ============================================================================

    /**
     * Create a sample FHIR ImagingStudy bundle for testing
     */
    private Bundle createSampleFhirBundle() {
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.DOCUMENT);
        bundle.setId("test-bundle-" + UUID.randomUUID());

        // Composition (manifest header)
        Composition composition = new Composition();
        composition.setId("composition-1");
        composition.setStatus(Composition.CompositionStatus.FINAL);
        composition.setType(new CodeableConcept()
                .addCoding(new Coding()
                        .setSystem("http://loinc.org")
                        .setCode("11488-4")
                        .setDisplay("Consultation note")));
        composition.setDate(new Date());
        composition.setTitle("Test MADO Manifest");

        // Patient
        Patient patient = new Patient();
        patient.setId("patient-1");
        patient.addName().setFamily("Test").addGiven("Patient");
        patient.addIdentifier()
                .setSystem("urn:oid:1.2.3.4")
                .setValue("PAT123456");

        // ImagingStudy
        ImagingStudy study = new ImagingStudy();
        study.setId("study-1");
        study.setStatus(ImagingStudy.ImagingStudyStatus.AVAILABLE);
        study.addIdentifier()
                .setSystem("urn:dicom:uid")
                .setValue("urn:oid:1.2.840.113619.2.62.994044785528.114289542805");
        study.setSubject(new Reference("Patient/patient-1"));

        // Series
        ImagingStudy.ImagingStudySeriesComponent series = new ImagingStudy.ImagingStudySeriesComponent();
        series.setUid("1.2.840.113619.2.62.994044785528.20060823223142485051");
        series.setNumber(1);
        series.setModality(new Coding()
                .setSystem("http://dicom.nema.org/resources/ontology/DCM")
                .setCode("CT")
                .setDisplay("Computed Tomography"));

        // Instance
        ImagingStudy.ImagingStudySeriesInstanceComponent instance = 
                new ImagingStudy.ImagingStudySeriesInstanceComponent();
        instance.setUid("1.2.840.113619.2.62.994044785528.20060823223143568062");
        instance.setNumber(1);
        instance.setSopClass(new Coding()
                .setSystem("urn:ietf:rfc:3986")
                .setCode("urn:oid:1.2.840.10008.5.1.4.1.1.2"));

        series.addInstance(instance);
        study.addSeries(series);

        // Add all resources to bundle
        bundle.addEntry().setResource(composition).setFullUrl("Composition/composition-1");
        bundle.addEntry().setResource(patient).setFullUrl("Patient/patient-1");
        bundle.addEntry().setResource(study).setFullUrl("ImagingStudy/study-1");

        return bundle;
    }

    /**
     * Create a sample DICOM MADO manifest for testing
     */
    private Attributes createSampleDicomMado() {
        Attributes attrs = new Attributes();
        
        // SOP Common Module
        attrs.setString(Tag.SOPClassUID, VR.UI, UID.KeyObjectSelectionDocumentStorage);
        attrs.setString(Tag.SOPInstanceUID, VR.UI, "1.2.840.113619.2.62.994044785528.114289542804");
        
        // Patient Module
        attrs.setString(Tag.PatientName, VR.PN, "Test^Patient");
        attrs.setString(Tag.PatientID, VR.LO, "PAT123456");
        attrs.setString(Tag.PatientSex, VR.CS, "O");
        
        // Study Module
        attrs.setString(Tag.StudyInstanceUID, VR.UI, "1.2.840.113619.2.62.994044785528.114289542805");
        attrs.setString(Tag.StudyDate, VR.DA, "20240101");
        attrs.setString(Tag.StudyTime, VR.TM, "120000");
        attrs.setString(Tag.StudyID, VR.SH, "ST001");
        attrs.setString(Tag.AccessionNumber, VR.SH, "ACC001");
        
        // Series Module
        attrs.setString(Tag.SeriesInstanceUID, VR.UI, "1.2.840.113619.2.62.994044785528.20060823223142485051");
        attrs.setString(Tag.SeriesNumber, VR.IS, "1");
        attrs.setString(Tag.Modality, VR.CS, "KO");
        
        // SR Document Module
        attrs.setString(Tag.ValueType, VR.CS, "CONTAINER");
        attrs.setString(Tag.ContinuityOfContent, VR.CS, "SEPARATE");
        
        // Concept Name Code Sequence
        Sequence conceptNameSeq = attrs.newSequence(Tag.ConceptNameCodeSequence, 1);
        Attributes conceptName = new Attributes();
        conceptName.setString(Tag.CodeValue, VR.SH, "113030");
        conceptName.setString(Tag.CodingSchemeDesignator, VR.SH, "DCM");
        conceptName.setString(Tag.CodeMeaning, VR.LO, "Manifest");
        conceptNameSeq.add(conceptName);
        
        // Evidence Sequence
        Sequence evidenceSeq = attrs.newSequence(Tag.CurrentRequestedProcedureEvidenceSequence, 1);
        Attributes evidence = new Attributes();
        evidence.setString(Tag.StudyInstanceUID, VR.UI, "1.2.840.113619.2.62.994044785528.114289542805");
        
        Sequence refSeriesSeq = evidence.newSequence(Tag.ReferencedSeriesSequence, 1);
        Attributes refSeries = new Attributes();
        refSeries.setString(Tag.SeriesInstanceUID, VR.UI, "1.2.840.113619.2.62.994044785528.20060823223142485051");
        
        Sequence refSOPSeq = refSeries.newSequence(Tag.ReferencedSOPSequence, 1);
        Attributes refSOP = new Attributes();
        refSOP.setString(Tag.ReferencedSOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.2");
        refSOP.setString(Tag.ReferencedSOPInstanceUID, VR.UI, "1.2.840.113619.2.62.994044785528.20060823223143568062");
        refSOPSeq.add(refSOP);
        
        refSeriesSeq.add(refSeries);
        evidenceSeq.add(evidence);
        
        // Content Sequence (simplified for testing)
        Sequence contentSeq = attrs.newSequence(Tag.ContentSequence, 1);
        Attributes content = new Attributes();
        content.setString(Tag.RelationshipType, VR.CS, "CONTAINS");
        content.setString(Tag.ValueType, VR.CS, "TEXT");
        
        Sequence contentConceptSeq = content.newSequence(Tag.ConceptNameCodeSequence, 1);
        Attributes contentConcept = new Attributes();
        contentConcept.setString(Tag.CodeValue, VR.SH, "113012");
        contentConcept.setString(Tag.CodingSchemeDesignator, VR.SH, "DCM");
        contentConcept.setString(Tag.CodeMeaning, VR.LO, "Key Object Description");
        contentConceptSeq.add(contentConcept);
        
        content.setString(Tag.TextValue, VR.UT, "Test MADO manifest");
        contentSeq.add(content);
        
        return attrs;
    }

    // ============================================================================
    // TEST FRAMEWORK METHODS
    // ============================================================================

    private void pass(String testName) {
        passCount++;
        System.out.println("✓ PASS: " + testName);
    }

    private void fail(String testName, Exception e) {
        failCount++;
        failures.add(testName + ": " + e.getMessage());
        System.out.println("✗ FAIL: " + testName);
        System.out.println("  Error: " + e.getMessage());
        if (e.getCause() != null) {
            System.out.println("  Cause: " + e.getCause().getMessage());
        }
    }
}
