package be.uzleuven.ihe.dicom.convertor.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r5.model.Bundle;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Batch converter for processing multiple DICOM MADO files to FHIR format.
 *
 * Processes all .dcm files in a source directory and converts them to FHIR JSON bundles
 * in a target directory.
 *
 * Usage: java MADOBatchConverter <input-directory> <output-directory>
 */
public class MADOBatchConverter {

    private static final FhirContext FHIR_CONTEXT = FhirContext.forR5();
    private final MADOToFHIRConverter converter;
    private final boolean prettyPrint;

    public MADOBatchConverter() {
        this(true);
    }

    public MADOBatchConverter(boolean prettyPrint) {
        this.converter = new MADOToFHIRConverter();
        this.prettyPrint = prettyPrint;
    }

    /**
     * Convert all DICOM files in a directory to FHIR bundles.
     *
     * @param inputDir Source directory containing .dcm files
     * @param outputDir Target directory for FHIR JSON files
     * @return ConversionResult containing success and failure counts
     * @throws IOException If directories cannot be accessed
     */
    public ConversionResult convertDirectory(String inputDir, String outputDir) throws IOException {
        Path inputPath = Paths.get(inputDir);
        Path outputPath = Paths.get(outputDir);

        // Validate input directory
        if (!Files.exists(inputPath)) {
            throw new IOException("Input directory does not exist: " + inputDir);
        }
        if (!Files.isDirectory(inputPath)) {
            throw new IOException("Input path is not a directory: " + inputDir);
        }

        // Create output directory if it doesn't exist
        if (!Files.exists(outputPath)) {
            Files.createDirectories(outputPath);
            System.out.println("Created output directory: " + outputPath);
        }

        // Find all .dcm files
        List<Path> dicomFiles = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(inputPath)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().toLowerCase().endsWith(".dcm"))
                 .forEach(dicomFiles::add);
        }

        if (dicomFiles.isEmpty()) {
            System.out.println("No DICOM files (.dcm) found in: " + inputDir);
            return new ConversionResult(0, 0, new ArrayList<>());
        }

        System.out.println("Found " + dicomFiles.size() + " DICOM file(s) to convert");
        System.out.println("Input directory: " + inputPath.toAbsolutePath());
        System.out.println("Output directory: " + outputPath.toAbsolutePath());
        System.out.println();

        // Process each file
        int successCount = 0;
        int failureCount = 0;
        List<ConversionError> errors = new ArrayList<>();

        for (Path dicomFile : dicomFiles) {
            String fileName = dicomFile.getFileName().toString();
            String outputFileName = fileName.replaceAll("\\.dcm$", ".json");
            Path outputFile = outputPath.resolve(outputFileName);

            try {
                System.out.print("Converting: " + fileName + " ... ");

                // Convert DICOM to FHIR
                Bundle bundle = converter.convert(dicomFile.toFile());

                // Write FHIR bundle to file
                IParser parser = FHIR_CONTEXT.newJsonParser();
                parser.setPrettyPrint(prettyPrint);
                String json = parser.encodeResourceToString(bundle);

                try (OutputStreamWriter writer = new OutputStreamWriter(
                        new FileOutputStream(outputFile.toFile()), StandardCharsets.UTF_8)) {
                    writer.write(json);
                }

                System.out.println("SUCCESS (" + bundle.getEntry().size() + " resources)");
                successCount++;

            } catch (Exception e) {
                System.out.println("FAILED: " + e.getMessage());
                failureCount++;
                errors.add(new ConversionError(fileName, e));
            }
        }

        // Print summary
        System.out.println();
        System.out.println("=".repeat(60));
        System.out.println("Conversion Summary");
        System.out.println("=".repeat(60));
        System.out.println("Total files:    " + dicomFiles.size());
        System.out.println("Successful:     " + successCount);
        System.out.println("Failed:         " + failureCount);
        System.out.println();

        if (!errors.isEmpty()) {
            System.out.println("Errors:");
            for (ConversionError error : errors) {
                System.out.println("  - " + error.fileName + ": " + error.exception.getMessage());
            }
            System.out.println();
        }

        return new ConversionResult(successCount, failureCount, errors);
    }

    /**
     * Main entry point for batch conversion.
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            printUsage();
            System.exit(1);
        }

        String inputDir = args[0];
        String outputDir = args[1];
        boolean prettyPrint = true;

        // Check for flags
        for (int i = 2; i < args.length; i++) {
            if ("--compact".equals(args[i])) {
                prettyPrint = false;
            }
        }

        try {
            MADOBatchConverter batchConverter = new MADOBatchConverter(prettyPrint);
            ConversionResult result = batchConverter.convertDirectory(inputDir, outputDir);

            // Exit with error code if any conversions failed
            if (result.failureCount > 0) {
                System.err.println("Warning: " + result.failureCount + " file(s) failed to convert");
                System.exit(2);
            }

            System.out.println("All conversions completed successfully!");

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(3);
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
            System.exit(4);
        }
    }

    private static void printUsage() {
        System.out.println("MADO DICOM to FHIR Batch Converter");
        System.out.println("===================================");
        System.out.println();
        System.out.println("Converts all DICOM MADO KOS files in a directory to FHIR Bundles.");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java " + MADOBatchConverter.class.getName() + " <input-dir> <output-dir> [options]");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  input-dir     Directory containing DICOM MADO KOS files (.dcm)");
        System.out.println("  output-dir    Directory where FHIR JSON files will be written");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --compact     Output compact JSON (no pretty printing)");
        System.out.println();
        System.out.println("Example:");
        System.out.println("  java " + MADOBatchConverter.class.getName() + " MADO_FROM_SCU MADO_FHIR_FROM_DICOM");
    }

    /**
     * Result of a batch conversion operation.
     */
    public static class ConversionResult {
        public final int successCount;
        public final int failureCount;
        public final List<ConversionError> errors;

        public ConversionResult(int successCount, int failureCount, List<ConversionError> errors) {
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.errors = errors;
        }

        public boolean hasFailures() {
            return failureCount > 0;
        }
    }

    /**
     * Details about a conversion error.
     */
    public static class ConversionError {
        public final String fileName;
        public final Exception exception;

        public ConversionError(String fileName, Exception exception) {
            this.fileName = fileName;
            this.exception = exception;
        }
    }
}

