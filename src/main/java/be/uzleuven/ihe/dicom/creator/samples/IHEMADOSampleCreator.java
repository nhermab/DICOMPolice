package be.uzleuven.ihe.dicom.creator.samples;

import be.uzleuven.ihe.dicom.creator.model.SimulatedStudy;
import be.uzleuven.ihe.dicom.creator.model.MADOOptions;
import be.uzleuven.ihe.dicom.creator.options.MADOOptionsUtils;
import org.dcm4che3.data.Attributes;

import java.io.File;

import static be.uzleuven.ihe.dicom.creator.utils.DicomCreatorUtils.writeDicomFile;
import static be.uzleuven.ihe.dicom.creator.utils.MADOAttributesUtils.createMADOAttributes;
import static be.uzleuven.ihe.dicom.creator.utils.StudyGeneratorUtils.generateSimulatedStudy;

/**
 * Sample creator for IHE MADO (Manifest with Description) documents.
 * This class has been refactored to extract utility functions into separate classes
 * following Java coding standards.
 * The main responsibilities of this class are:
 * - Command-line interface and argument parsing
 * - High-level orchestration of MADO file generation
 * Business logic has been extracted to:
 * - MADOOptions: Configuration options
 * - MADOOptionsUtils: Random generation and KOS conversion
 * - StudyGeneratorUtils: Simulated study generation
 * - MADOAttributesUtils: MADO DICOM attributes creation
 * - MADOContentUtils: MADO content items and descriptors
 * - SimulatedStudy/Series/Instance: Data model classes
 */
public class IHEMADOSampleCreator {

    /**
     * @deprecated Use {@link MADOOptions} instead. Kept for backwards compatibility.
     */
    @Deprecated
    public static class Options extends MADOOptions {
    }

    /**
     * @deprecated Use {@link MADOOptions} constructor instead. Kept for backwards compatibility.
     */
    @Deprecated
    public static MADOOptions defaultOptions() {
        return new MADOOptions();
    }

    public static void main(String[] args) throws Exception {
        CommandLineArgs cmdArgs = parseCommandLineArgs(args);

        if (cmdArgs.showHelp) {
            printUsage();
            return;
        }

        File outDir = new File(System.getProperty("user.dir"));

        for (int i = 0; i < cmdArgs.count; i++) {
            MADOOptions options = determineOptions(cmdArgs);
            SimulatedStudy study = generateSimulatedStudy(options);
            Attributes madoKos = createMADOAttributes(study);

            String filename = "IHE_MADO_" + i + ".dcm";
            File outFile = new File(outDir, filename);
            writeDicomFile(outFile, madoKos);

            printGenerationSummary(outFile, options);
        }
    }

    /**
     * Create MADO attributes from options (convenience method).
     */
    public static Attributes createMADOFromOptions(MADOOptions options) {
        SimulatedStudy study = generateSimulatedStudy(options);
        return createMADOAttributes(study);
    }

    /**
     * Write a MADO file with the given options.
     */
    public static void writeMADO(MADOOptions options, File outFile) throws java.io.IOException {
        if (options == null) {
            options = new MADOOptions();
        }
        Attributes attrs = createMADOFromOptions(options);
        writeDicomFile(outFile, attrs);
    }

    /**
     * Generate random options for testing.
     * @deprecated Use {@link MADOOptionsUtils#generateRandomOptions()} instead
     */
    @Deprecated
    public static MADOOptions generateRandomOptions() {
        return MADOOptionsUtils.generateRandomOptions();
    }

    // --- Private Helper Methods ---

    /**
     * Parse command-line arguments into a structured object.
     */
    private static CommandLineArgs parseCommandLineArgs(String[] args) {
        CommandLineArgs cmdArgs = new CommandLineArgs();

        if (args == null) {
            return cmdArgs;
        }

        for (String arg : args) {
            if ("--help".equalsIgnoreCase(arg) || "-h".equalsIgnoreCase(arg)) {
                cmdArgs.showHelp = true;
            } else if ("--default-sizes".equalsIgnoreCase(arg)) {
                cmdArgs.useRandomSizes = false;
                cmdArgs.useKOSDefaultSizes = true;
            } else if ("--random-sizes".equalsIgnoreCase(arg)) {
                cmdArgs.useRandomSizes = true;
                cmdArgs.useKOSDefaultSizes = false;
            } else {
                try {
                    cmdArgs.count = Math.max(1, Integer.parseInt(arg));
                } catch (NumberFormatException ignore) {
                    // Ignore unknown token
                }
            }
        }

        return cmdArgs;
    }

    /**
     * Determine which options to use based on command-line arguments.
     */
    private static MADOOptions determineOptions(CommandLineArgs cmdArgs) {
        if (cmdArgs.useKOSDefaultSizes) {
            IHEKOSSampleCreator.Options kos = IHEKOSSampleCreator.defaultOptions();
            return MADOOptionsUtils.fromKOSOptions(kos);
        } else if (cmdArgs.useRandomSizes) {
            return MADOOptionsUtils.generateRandomOptions();
        } else {
            return new MADOOptions();
        }
    }

    /**
     * Print usage information.
     */
    private static void printUsage() {
        System.out.println("Usage: IHEMADOSampleCreator [count] [--default-sizes] [--random-sizes] [--help]");
        System.out.println("  count           : number of MADO files to generate (default 1)");
        System.out.println("  --default-sizes : use deterministic default size/shape options instead of random sizes");
        System.out.println("  --random-sizes  : explicitly use random sizes (default behavior)");
    }

    /**
     * Print a summary of what was generated.
     */
    private static void printGenerationSummary(File outFile, MADOOptions options) {
        System.out.println("Created MADO Manifest: " + outFile.getAbsolutePath() +
            " (series=" + options.getSeriesCount() +
            ", totalInstances=" + options.getTotalInstanceCount() +
            ", modalities=" + options.getModalityPool().length +
            ", multiframe=" + options.getMultiframeInstanceCount() +
            ", keyImages=" + options.getKeyImageCount() + ")");
    }

    /**
     * Internal class to hold parsed command-line arguments.
     */
    private static class CommandLineArgs {
        int count = 1;
        boolean useRandomSizes = true;
        boolean useKOSDefaultSizes = false;
        boolean showHelp = false;
    }
}
