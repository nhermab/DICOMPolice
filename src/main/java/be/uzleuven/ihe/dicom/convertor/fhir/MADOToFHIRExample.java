package be.uzleuven.ihe.dicom.convertor.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r5.model.Bundle;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * Command-line interface for converting DICOM MADO KOS manifests to FHIR Bundles.
 *
 * Usage: java MADOToFHIRExample &lt;input.dcm&gt; [output.json]
 *
 * If output file is not specified, outputs to stdout.
 *
 * @see MADOToFHIRConverter
 */
public class MADOToFHIRExample {

    private static final FhirContext FHIR_CONTEXT = FhirContext.forR5();

    public static void main(String[] args) {
        if (args.length < 1) {
            printUsage();
            System.exit(1);
        }

        String inputFile = args[0];
        String outputFile = args.length > 1 ? args[1] : null;
        boolean prettyPrint = true;

        // Check for flags
        for (int i = 1; i < args.length; i++) {
            if ("--compact".equals(args[i])) {
                prettyPrint = false;
            } else if (!args[i].startsWith("--")) {
                outputFile = args[i];
            }
        }

        try {
            // Validate input file
            File dicomFile = new File(inputFile);
            if (!dicomFile.exists()) {
                System.err.println("Error: Input file not found: " + inputFile);
                System.exit(1);
            }

            System.err.println("Converting: " + dicomFile.getAbsolutePath());

            // Perform conversion
            MADOToFHIRConverter converter = new MADOToFHIRConverter();
            Bundle bundle = converter.convert(dicomFile);

            // Serialize to JSON
            IParser parser = FHIR_CONTEXT.newJsonParser();
            if (prettyPrint) {
                parser.setPrettyPrint(true);
            }

            String json = parser.encodeResourceToString(bundle);

            // Output
            if (outputFile != null) {
                try (OutputStreamWriter writer = new OutputStreamWriter(
                        new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {
                    writer.write(json);
                }
                System.err.println("Output written to: " + outputFile);
            } else {
                System.out.println(json);
            }

            System.err.println("Conversion completed successfully.");
            System.err.println("Bundle contains " + bundle.getEntry().size() + " resources.");

        } catch (IOException e) {
            System.err.println("Error reading DICOM file: " + e.getMessage());
            System.err.println("Check that the file exists and is a valid DICOM file.");
            System.exit(2);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(3);
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            System.err.println("Error type: " + e.getClass().getName());
            System.exit(4);
        }
    }

    private static void printUsage() {
        System.out.println("MADO DICOM to FHIR Converter");
        System.out.println("============================");
        System.out.println();
        System.out.println("Converts DICOM MADO KOS (Key Object Selection) manifests to FHIR Bundles");
        System.out.println("following the IHE MADO profile specification.");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java " + MADOToFHIRExample.class.getName() + " <input.dcm> [output.json] [options]");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  input.dcm     Path to the DICOM MADO KOS file");
        System.out.println("  output.json   Optional output file path (default: stdout)");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --compact     Output compact JSON (no pretty printing)");
        System.out.println();
        System.out.println("Output:");
        System.out.println("  A FHIR Bundle (type: collection) containing:");
        System.out.println("    - Patient resource");
        System.out.println("    - ImagingStudy resource with series and instances");
        System.out.println("    - Endpoint resources for WADO-RS access");
        System.out.println("    - ImagingSelection resources (if key images are flagged)");
        System.out.println();
        System.out.println("Example:");
        System.out.println("  java " + MADOToFHIRExample.class.getName() + " MADO_FROM_SCU/mado_example.dcm output.json");
    }

    /**
     * Converts a DICOM MADO file to a FHIR JSON string.
     *
     * @param dicomFilePath Path to the DICOM file
     * @return JSON string representation of the FHIR Bundle
     * @throws IOException If file cannot be read
     */
    public static String convertToJson(String dicomFilePath) throws IOException {
        return convertToJson(dicomFilePath, true);
    }

    /**
     * Converts a DICOM MADO file to a FHIR JSON string.
     *
     * @param dicomFilePath Path to the DICOM file
     * @param prettyPrint Whether to format the JSON output
     * @return JSON string representation of the FHIR Bundle
     * @throws IOException If file cannot be read
     */
    public static String convertToJson(String dicomFilePath, boolean prettyPrint) throws IOException {
        MADOToFHIRConverter converter = new MADOToFHIRConverter();
        Bundle bundle = converter.convert(dicomFilePath);

        IParser parser = FHIR_CONTEXT.newJsonParser();
        parser.setPrettyPrint(prettyPrint);

        return parser.encodeResourceToString(bundle);
    }

    /**
     * Converts a DICOM MADO file to a FHIR Bundle.
     *
     * @param dicomFilePath Path to the DICOM file
     * @return FHIR Bundle
     * @throws IOException If file cannot be read
     */
    public static Bundle convertToBundle(String dicomFilePath) throws IOException {
        MADOToFHIRConverter converter = new MADOToFHIRConverter();
        return converter.convert(dicomFilePath);
    }
}

