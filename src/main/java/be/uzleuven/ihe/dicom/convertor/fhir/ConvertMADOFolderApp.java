package be.uzleuven.ihe.dicom.convertor.fhir;

/**
 * Simple application to convert all DICOM MADO files from MADO_FROM_SCU folder
 * to FHIR JSON bundles in FHIR_FROM_DICOM_MADO folder.
 *
 * This is a convenience wrapper around MADOBatchConverter with predefined paths.
 *
 * Usage: java ConvertMADOFolderApp
 */
public class ConvertMADOFolderApp {

    private static final String INPUT_FOLDER = "MADO_FROM_SCU";
    private static final String OUTPUT_FOLDER = "FHIR_FROM_DICOM_MADO";

    public static void main(String[] args) {
        System.out.println("╔═══════════════════════════════════════════════════════╗");
        System.out.println("║   MADO DICOM to FHIR Batch Conversion Application   ║");
        System.out.println("╚═══════════════════════════════════════════════════════╝");
        System.out.println();

        try {
            // Create batch converter with pretty printing enabled
            MADOBatchConverter converter = new MADOBatchConverter(true);

            // Convert all files in the directory
            MADOBatchConverter.ConversionResult result =
                converter.convertDirectory(INPUT_FOLDER, OUTPUT_FOLDER);

            // Print final status
            if (result.hasFailures()) {
                System.err.println("⚠ WARNING: " + result.failureCount + " file(s) failed to convert");
                System.err.println("Please review the errors above for details.");
                System.exit(1);
            } else {
                System.out.println("✓ SUCCESS: All " + result.successCount + " file(s) converted successfully!");
                System.out.println();
                System.out.println("FHIR JSON files are available in: " + OUTPUT_FOLDER);
            }

        } catch (Exception e) {
            System.err.println("✗ ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(2);
        }
    }
}

