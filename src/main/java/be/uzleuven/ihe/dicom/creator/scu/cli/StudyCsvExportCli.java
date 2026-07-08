package be.uzleuven.ihe.dicom.creator.scu.cli;

import be.uzleuven.ihe.dicom.creator.scu.DefaultMetadata;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Command-line app that exports study-level C-FIND results to CSV.
 * It queries one day at a time and one modality at a time, then de-duplicates by StudyInstanceUID.
 */
public class StudyCsvExportCli {

    public static void main(String[] args) {
        try {
            int exit = run(args);
            System.exit(exit);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println("Error type: " + e.getClass().getName());
            if (e.getCause() != null) {
                System.err.println("Caused by: " + e.getCause().getMessage());
            }
            System.exit(2);
        }
    }

    static int run(String[] args) throws Exception {
        if (args.length == 0 || hasArg(args, "--help", "-h")) {
            printUsage();
            return 0;
        }

        Options options = parseArgs(args);
        options.validate();

        System.out.println("Starting CSV export");
        System.out.println("  Remote: " + options.defaults.calledAET + " @ " + options.defaults.remoteHost + ":" + options.defaults.remotePort);
        System.out.println("  Calling AE: " + options.defaults.callingAET);
        System.out.println("  Study date range: " + options.beginDate + " -> " + options.endDate);
        System.out.println("  Modalities: " + String.join(",", options.getModalities()));
        System.out.println("  Output: " + options.outputCsv.getAbsolutePath());
        System.out.println("  Mode: incremental CSV write (file is created immediately and filled after each day's modality sweep completes)");

        StudyCsvExportService service = new StudyCsvExportService(options.defaults);
        int rows = service.exportStudies(options.beginDate, options.endDate, options.getModalities(), options.outputCsv, options.overwrite);

        if (rows == 0) {
            System.out.println("No studies matched the requested criteria. Header-only CSV written to " + options.outputCsv.getAbsolutePath());
            return 3;
        }

        System.out.println("Export complete. Unique studies written: " + rows);
        return 0;
    }

    private static Options parseArgs(String[] args) {
        Options options = new Options();

        int i = 0;
        while (i < args.length) {
            String arg = args[i];
            switch (arg) {
                case "--begin-date":
                case "--date-from":
                case "--from":
                    options.beginDate = parseDate(requireValue(args, i + 1, arg));
                    i += 2;
                    break;
                case "--end-date":
                case "--date-to":
                case "--to":
                    options.endDate = parseDate(requireValue(args, i + 1, arg));
                    i += 2;
                    break;
                case "--modalities":
                case "--modality":
                    options.addModalities(requireValue(args, i + 1, arg));
                    i += 2;
                    break;
                case "--out-csv":
                case "--csv":
                case "--out":
                    options.outputCsv = new File(requireValue(args, i + 1, arg));
                    i += 2;
                    break;
                case "--overwrite":
                    options.overwrite = true;
                    i++;
                    break;

                // Connection options
                case "--called-aet":
                case "--ae":
                    options.defaults.withCalledAET(requireValue(args, i + 1, arg));
                    i += 2;
                    break;
                case "--calling-aet":
                case "--my-ae":
                    options.defaults.withCallingAET(requireValue(args, i + 1, arg));
                    i += 2;
                    break;
                case "--host":
                case "--ip":
                    options.defaults.withRemoteHost(requireValue(args, i + 1, arg));
                    i += 2;
                    break;
                case "--port":
                    options.defaults.withRemotePort(requireIntValue(args, i + 1, arg));
                    i += 2;
                    break;
                case "--connect-timeout-ms":
                    options.defaults.withConnectTimeout(requireIntValue(args, i + 1, arg));
                    i += 2;
                    break;
                case "--response-timeout-ms":
                    options.defaults.withResponseTimeout(requireIntValue(args, i + 1, arg));
                    i += 2;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown argument: " + arg + " (use --help)");
            }
        }

        return options;
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date: " + value + " (expected YYYY-MM-DD)", e);
        }
    }

    private static String requireValue(String[] args, int index, String flag) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + flag);
        }
        return args[index];
    }

    private static int requireIntValue(String[] args, int index, String flag) {
        String value = requireValue(args, index, flag);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer for " + flag + ": " + value, e);
        }
    }

    private static boolean hasArg(String[] args, String... names) {
        for (String a : args) {
            for (String n : names) {
                if (n.equalsIgnoreCase(a)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void printUsage() {
        System.out.println("Study CSV export CLI\n");
        System.out.println("This app runs STUDY-level C-FIND queries one day at a time and one modality at a time.");
        System.out.println("It exports unique studies to CSV with these columns:");
        System.out.println("  STUDYINSTANCEUID, STUDYDATE, ACCESSIONNUMBER, PATIENTID, NUMBEROFSTUDYRELATEDINSTANCES\n");

        System.out.println("Primary options:");
        System.out.println("  --begin-date <YYYY-MM-DD>");
        System.out.println("  --end-date <YYYY-MM-DD>");
        System.out.println("  --out-csv <file.csv> | --csv <file.csv>   default=study-export.csv");
        System.out.println();

        System.out.println("Modalities:");
        System.out.println("  --modalities CT,US,CR,DX,MR,MG   Comma-separated list (defaults to CT,US,CR,DX,MR,MG if omitted)");
        System.out.println("  --modality <MOD>                 Repeatable alias for a single modality");
        System.out.println();

        System.out.println("Connection:");
        System.out.println("  --called-aet <AET>   (or --ae)     default=ORTHANC");
        System.out.println("  --calling-aet <AET>  (or --my-ae)  default=DICOMPOLICE");
        System.out.println("  --host <host>        (or --ip)     default=localhost");
        System.out.println("  --port <port>                     default=4242");
        System.out.println("  --connect-timeout-ms <ms>         default=5000");
        System.out.println("  --response-timeout-ms <ms>        default=10000");
        System.out.println();

        System.out.println("Other:");
        System.out.println("  --overwrite   Allow overwriting an existing CSV file");
        System.out.println();

        System.out.println("Examples:");
        System.out.println("  java -cp target\\DICOMPolice-0.1.0-SNAPSHOT.jar be.uzleuven.ihe.dicom.creator.scu.cli.StudyCsvExportCli " +
            "--begin-date 2024-01-01 --end-date 2024-01-31 --out-csv studies.csv");
        System.out.println();
        System.out.println("  java -cp target\\DICOMPolice-0.1.0-SNAPSHOT.jar be.uzleuven.ihe.dicom.creator.scu.cli.StudyCsvExportCli " +
            "--begin-date 2024-01-01 --end-date 2024-01-31 --modalities CT,MR --ae ORTHANC --my-ae DICOMPOLICE --host 127.0.0.1 --port 4242 --out-csv studies.csv --overwrite");
    }

    private static final class Options {
        private final DefaultMetadata defaults = new DefaultMetadata();
        private LocalDate beginDate;
        private LocalDate endDate;
        private File outputCsv = new File("study-export.csv");
        private boolean overwrite;
        private final List<String> modalities = new ArrayList<>();

        private void addModalities(String rawValue) {
            if (rawValue == null) {
                return;
            }
            for (String token : rawValue.split(",")) {
                String trimmed = token.trim();
                if (!trimmed.isEmpty()) {
                    modalities.add(trimmed.toUpperCase(Locale.ROOT));
                }
            }
        }

        private List<String> getModalities() {
            if (modalities.isEmpty()) {
                return Arrays.asList("CT", "US", "CR", "DX", "MR", "MG");
            }
            return modalities;
        }

        private void validate() {
            if (beginDate == null || endDate == null) {
                throw new IllegalArgumentException("Both --begin-date and --end-date are required");
            }
            if (beginDate.isAfter(endDate)) {
                throw new IllegalArgumentException("--begin-date must be <= --end-date");
            }
            if (outputCsv == null) {
                throw new IllegalArgumentException("--out-csv is required");
            }
            if (outputCsv.exists() && outputCsv.isDirectory()) {
                throw new IllegalArgumentException("Output path is a directory: " + outputCsv.getAbsolutePath());
            }
        }
    }
}

