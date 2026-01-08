package be.uzleuven.ihe.dicom.convertor.dicom;

import be.uzleuven.ihe.dicom.convertor.fhir.MADOToFHIRConverter;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.io.DicomInputStream;
import org.hl7.fhir.r5.model.Bundle;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Example application demonstrating FHIR to DICOM MADO conversion.
 *
 * This example shows:
 * 1. How to convert a DICOM MADO file to FHIR Bundle
 * 2. How to convert the FHIR Bundle back to DICOM MADO
 * 3. Round-trip verification
 *
 * Usage:
 *   java FHIRToMADOExample <input.dcm> [output-prefix]
 *
 * Example:
 *   java FHIRToMADOExample IHE_MADO_0.dcm roundtrip
 *
 * This will create:
 *   - roundtrip.json (FHIR Bundle in JSON format)
 *   - roundtrip.dcm (DICOM MADO file from round-trip conversion)
 */
public class FHIRToMADOExample {

    private static final FhirContext FHIR_CONTEXT = FhirContext.forR5();

    public static void main(String[] args) {
        if (args.length < 1) {
            printUsage();
            System.exit(1);
        }

        String inputPath = args[0];
        String outputPrefix = args.length > 1 ? args[1] : "roundtrip_output";

        File inputFile = new File(inputPath);
        if (!inputFile.exists()) {
            System.err.println("Error: Input file not found: " + inputPath);
            System.exit(1);
        }

        try {
            runExample(inputFile, outputPrefix);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void runExample(File inputFile, String outputPrefix) throws IOException {
        System.out.println("=".repeat(60));
        System.out.println("FHIR <-> DICOM MADO Conversion Example");
        System.out.println("=".repeat(60));
        System.out.println();

        // Step 1: Read original DICOM MADO
        System.out.println("Step 1: Reading original DICOM MADO file...");
        System.out.println("  Input: " + inputFile.getAbsolutePath());

        Attributes originalDicom;
        try (DicomInputStream dis = new DicomInputStream(inputFile)) {
            originalDicom = dis.readDataset();
        }

        printDicomSummary(originalDicom, "Original");

        // Step 2: Convert DICOM to FHIR
        System.out.println("\nStep 2: Converting DICOM MADO to FHIR Bundle...");

        MADOToFHIRConverter toFhirConverter = new MADOToFHIRConverter();
        Bundle fhirBundle = toFhirConverter.convert(originalDicom);

        printFhirSummary(fhirBundle);

        // Step 3: Save FHIR Bundle as JSON
        String fhirJsonPath = outputPrefix + ".json";
        System.out.println("\nStep 3: Saving FHIR Bundle to JSON...");
        System.out.println("  Output: " + fhirJsonPath);

        IParser parser = FHIR_CONTEXT.newJsonParser().setPrettyPrint(true);
        String fhirJson = parser.encodeResourceToString(fhirBundle);

        try (FileWriter writer = new FileWriter(fhirJsonPath)) {
            writer.write(fhirJson);
        }

        System.out.println("  FHIR JSON size: " + fhirJson.length() + " bytes");

        // Step 4: Convert FHIR back to DICOM
        System.out.println("\nStep 4: Converting FHIR Bundle back to DICOM MADO...");

        FHIRToMADOConverter toDicomConverter = new FHIRToMADOConverter();
        Attributes roundTrippedDicom = toDicomConverter.convert(fhirBundle);

        printDicomSummary(roundTrippedDicom, "Round-tripped");

        // Step 5: Save round-tripped DICOM
        String dicomOutputPath = outputPrefix + ".dcm";
        System.out.println("\nStep 5: Saving round-tripped DICOM MADO...");
        System.out.println("  Output: " + dicomOutputPath);

        toDicomConverter.convertAndSave(fhirBundle, new File(dicomOutputPath));

        // Step 6: Compare original and round-tripped
        System.out.println("\nStep 6: Comparing original and round-tripped DICOM...");

        // Run full comparison
        FHIRToMADORoundTripTest.ComparisonResult comparison =
            new FHIRToMADORoundTripTest().testRoundTrip(inputFile);
        comparison.print();

        System.out.println("=".repeat(60));
        System.out.println("Conversion complete!");
        System.out.println("  FHIR Bundle: " + fhirJsonPath);
        System.out.println("  Round-tripped DICOM: " + dicomOutputPath);
        System.out.println("=".repeat(60));
    }

    private static void printDicomSummary(Attributes attrs, String label) {
        System.out.println("\n  " + label + " DICOM Summary:");
        System.out.println("  " + "-".repeat(40));
        System.out.println("  SOP Instance UID: " + attrs.getString(org.dcm4che3.data.Tag.SOPInstanceUID));
        System.out.println("  Study Instance UID: " + attrs.getString(org.dcm4che3.data.Tag.StudyInstanceUID));
        System.out.println("  Patient ID: " + attrs.getString(org.dcm4che3.data.Tag.PatientID));
        System.out.println("  Patient Name: " + attrs.getString(org.dcm4che3.data.Tag.PatientName));
        System.out.println("  Study Date: " + attrs.getString(org.dcm4che3.data.Tag.StudyDate));
        System.out.println("  Accession Number: " + attrs.getString(org.dcm4che3.data.Tag.AccessionNumber));

        // Count series and instances from evidence sequence
        org.dcm4che3.data.Sequence evidenceSeq =
            attrs.getSequence(org.dcm4che3.data.Tag.CurrentRequestedProcedureEvidenceSequence);
        int seriesCount = 0;
        int instanceCount = 0;
        if (evidenceSeq != null) {
            for (Attributes studyItem : evidenceSeq) {
                org.dcm4che3.data.Sequence refSeriesSeq =
                    studyItem.getSequence(org.dcm4che3.data.Tag.ReferencedSeriesSequence);
                if (refSeriesSeq != null) {
                    seriesCount += refSeriesSeq.size();
                    for (Attributes seriesItem : refSeriesSeq) {
                        org.dcm4che3.data.Sequence refSopSeq =
                            seriesItem.getSequence(org.dcm4che3.data.Tag.ReferencedSOPSequence);
                        if (refSopSeq != null) {
                            instanceCount += refSopSeq.size();
                        }
                    }
                }
            }
        }
        System.out.println("  Series Count: " + seriesCount);
        System.out.println("  Instance Count: " + instanceCount);
    }

    private static void printFhirSummary(Bundle bundle) {
        System.out.println("\n  FHIR Bundle Summary:");
        System.out.println("  " + "-".repeat(40));
        System.out.println("  Bundle Type: " + bundle.getType());
        System.out.println("  Total Entries: " + bundle.getEntry().size());

        // Count by resource type
        java.util.Map<String, Integer> typeCounts = new java.util.HashMap<>();
        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            if (entry.getResource() != null) {
                String typeName = entry.getResource().getResourceType().name();
                typeCounts.merge(typeName, 1, Integer::sum);
            }
        }

        System.out.println("  Resources by type:");
        for (java.util.Map.Entry<String, Integer> e : typeCounts.entrySet()) {
            System.out.println("    - " + e.getKey() + ": " + e.getValue());
        }
    }

    private static void printUsage() {
        System.out.println("Usage: java FHIRToMADOExample <input.dcm> [output-prefix]");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  input.dcm      Path to input DICOM MADO file");
        System.out.println("  output-prefix  Prefix for output files (default: roundtrip_output)");
        System.out.println();
        System.out.println("Output files:");
        System.out.println("  <prefix>.json  FHIR Bundle in JSON format");
        System.out.println("  <prefix>.dcm   Round-tripped DICOM MADO file");
        System.out.println();
        System.out.println("Example:");
        System.out.println("  java FHIRToMADOExample IHE_MADO_0.dcm roundtrip");
    }
}

