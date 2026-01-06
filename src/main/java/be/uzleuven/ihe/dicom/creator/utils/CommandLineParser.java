package be.uzleuven.ihe.dicom.creator.utils;

import be.uzleuven.ihe.dicom.creator.scu.DefaultMetadata;

/**
 * Utility class for parsing common command-line arguments for SCU manifest creators.
 * Reduces duplication in KOS and MADO SCU creator main methods.
 */
public class CommandLineParser {

    private CommandLineParser() {
        // Utility class
    }

    /**
     * Result of parsing command-line arguments.
     */
    public static class ParsedArgs {
        public DefaultMetadata defaults = new DefaultMetadata();
        public String studyUID;
        public String patientID;
        public String outputFile;

        public boolean isValid() {
            return studyUID != null;
        }

        public boolean isInvalid() {
            return studyUID == null;
        }

        public String getUsageMessage(String programName) {
            return "Usage: " + programName + " -study <StudyInstanceUID> " +
                   "[-pid <PatientID>] [-aec <CalledAET>] [-aet <CallingAET>] " +
                   "[-host <hostname>] [-port <port>] [-out <output.dcm>] " +
                   "[-issuer <PatientIDIssuerOID>] [-accissuer <AccessionIssuerOID>] " +
                   "[-repouid <RetrieveLocationUID>] [-wado <WADOBaseURL>]";
        }
    }

    /**
     * Parses command-line arguments into a structured result.
     *
     * @param args Command-line arguments
     * @param defaultOutputFile Default output filename if not specified
     * @return Parsed arguments
     */
    public static ParsedArgs parseArgs(String[] args, String defaultOutputFile) {
        ParsedArgs result = new ParsedArgs();
        result.outputFile = defaultOutputFile;

        for (int i = 0; i < args.length; i++) {
            if (i >= args.length - 1 && !isFlag(args[i])) {
                continue; // Skip if no value follows
            }

            switch (args[i]) {
                case "-aec":
                    result.defaults.withCalledAET(args[++i]);
                    break;
                case "-aet":
                    result.defaults.withCallingAET(args[++i]);
                    break;
                case "-host":
                    result.defaults.withRemoteHost(args[++i]);
                    break;
                case "-port":
                    result.defaults.withRemotePort(Integer.parseInt(args[++i]));
                    break;
                case "-study":
                    result.studyUID = args[++i];
                    break;
                case "-pid":
                    result.patientID = args[++i];
                    break;
                case "-out":
                    result.outputFile = args[++i];
                    break;
                case "-issuer":
                    result.defaults.withPatientIdIssuerOid(args[++i]);
                    break;
                case "-accissuer":
                    result.defaults.withAccessionNumberIssuerOid(args[++i]);
                    break;
                case "-repouid":
                    result.defaults.withRetrieveLocationUid(args[++i]);
                    break;
                case "-wado":
                    result.defaults.withWadoRsBaseUrl(args[++i]);
                    break;
                case "-timeout":
                    result.defaults.withConnectTimeout(Integer.parseInt(args[++i]));
                    break;
                case "-response-timeout":
                    result.defaults.withResponseTimeout(Integer.parseInt(args[++i]));
                    break;
                case "-institution":
                    result.defaults.withInstitutionName(args[++i]);
                    break;
                default:
                    // Unknown argument, skip
                    break;
            }
        }

        return result;
    }

    /**
     * Checks if a string is a flag (starts with '-').
     */
    private static boolean isFlag(String arg) {
        return arg != null && arg.startsWith("-");
    }

    /**
     * Prints status information for manifest creation.
     */
    public static void printStartupInfo(ParsedArgs args, String manifestType) {
        System.out.println("Creating " + manifestType + " manifest from C-FIND query...");
        System.out.println("Study UID: " + args.studyUID);
        System.out.println("Patient ID: " + (args.patientID != null ? args.patientID : "(will query)"));
        System.out.println("Remote: " + args.defaults.calledAET + " @ " +
            args.defaults.remoteHost + ":" + args.defaults.remotePort);
    }

    /**
     * Prints success information after manifest creation.
     */
    public static void printSuccessInfo(String outputFile, String sopInstanceUID, String manifestType) {
        System.out.println(manifestType + " manifest created successfully: " + outputFile);
        System.out.println("SOP Instance UID: " + sopInstanceUID);
    }

    /**
     * Prints error information and exits.
     */
    public static void printErrorAndExit(String message, Throwable error) {
        System.err.println("Error: " + message);
        if (error != null) {
            System.err.println("Cause: " + error.getClass().getName() + ": " + error.getMessage());
            if (error.getCause() != null) {
                System.err.println("Root cause: " + error.getCause().getClass().getName() + ": " + error.getCause().getMessage());
            }
        }
        System.exit(1);
    }
}

