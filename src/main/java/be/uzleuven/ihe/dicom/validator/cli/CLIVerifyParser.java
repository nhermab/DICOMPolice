package be.uzleuven.ihe.dicom.validator.cli;

import be.uzleuven.ihe.dicom.commons.cli.ArgumentParser;

/**
 * Parser for CLIDICOMVerify command-line arguments.
 * Extends ArgumentParser for common CLI utilities.
 */
public class CLIVerifyParser extends ArgumentParser {

    /**
     * Parse command-line arguments into CLIVerifyOptions.
     *
     * @param args Command-line arguments
     * @return Parsed options
     */
    public static CLIVerifyOptions parse(String[] args) {
        CLIVerifyOptions options = new CLIVerifyOptions();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            if ("-h".equals(arg) || "--help".equals(arg)) {
                options.setShowHelp(true);
                break;
            } else if ("-v".equals(arg) || "--verbose".equals(arg)) {
                options.setVerbose(true);
            } else if ("--new-format".equals(arg)) {
                options.setNewFormat(true);
            } else if (arg.startsWith("--profile=")) {
                options.setProfile(arg.substring("--profile=".length()));
            } else if ("--profile".equals(arg)) {
                options.setProfile(requireValue(args, ++i, arg));
            } else if (arg.startsWith("-")) {
                throw new IllegalArgumentException("Unknown option: " + arg);
            } else {
                options.addFile(arg);
            }
        }

        return options;
    }

    /**
     * Print help message for CLIDICOMVerify.
     */
    public static void printHelp() {
        System.out.println("Usage: CLIDICOMVerify [options] <dicom-file> [<dicom-file> ...]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -h, --help           Display this help message");
        System.out.println("  -v, --verbose        Verbose output with detailed validation messages");
        System.out.println("      --new-format     Use new format for error messages");
        System.out.println("      --profile <name> Validation profile:");
        System.out.println("                         IHEXDSIManifest - XDS-I.b KOS Manifest");
        System.out.println("                         IHEMADO         - MADO Manifest with Description");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  CLIDICOMVerify kos.dcm");
        System.out.println("  CLIDICOMVerify -v kos.dcm");
        System.out.println("  CLIDICOMVerify --profile IHEXDSIManifest kos.dcm");
        System.out.println("  CLIDICOMVerify --profile IHEMADO mado_manifest.dcm");
        System.out.println();
        System.out.println("Exit codes:");
        System.out.println("  0 - All validations passed");
        System.out.println("  1 - Validation errors found or file access errors");
    }
}

