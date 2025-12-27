package be.uzleuven.ihe.dicom.creator.scu.cli;

import be.uzleuven.ihe.dicom.commons.cli.ArgumentParser;
import be.uzleuven.ihe.dicom.creator.scu.streaming.StreamingMode;

import java.io.File;

/**
 * Parser for SCU Manifest CLI arguments.
 * Extracts the large switch statement into a dedicated parser class.
 */
public class SCUManifestOptionsParser extends ArgumentParser {

    /**
     * Parse command-line arguments into SCUManifestOptions.
     *
     * @param args Command-line arguments
     * @return Parsed options
     * @throws IllegalArgumentException if arguments are invalid
     */
    public static SCUManifestOptions parse(String[] args) {
        SCUManifestOptions options = new SCUManifestOptions();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            switch (arg) {
                case "--type":
                    options.setType(ManifestType.fromString(requireValue(args, ++i, arg)));
                    break;

                // Connection options
                case "--called-aet":
                case "-aec":
                    options.getDefaults().withCalledAET(requireValue(args, ++i, arg));
                    break;
                case "--calling-aet":
                case "-aet":
                    options.getDefaults().withCallingAET(requireValue(args, ++i, arg));
                    break;
                case "--host":
                    options.getDefaults().withRemoteHost(requireValue(args, ++i, arg));
                    break;
                case "--port":
                    options.getDefaults().withRemotePort(requireIntValue(args, ++i, arg));
                    break;
                case "--connect-timeout-ms":
                    options.getDefaults().withConnectTimeout(requireIntValue(args, ++i, arg));
                    break;
                case "--response-timeout-ms":
                    options.getDefaults().withResponseTimeout(requireIntValue(args, ++i, arg));
                    break;

                // IHE metadata defaults
                case "--patient-issuer-oid":
                case "--issuer":
                    options.getDefaults().withPatientIdIssuerOid(requireValue(args, ++i, arg));
                    break;
                case "--patient-issuer-namespace":
                    options.getDefaults().withPatientIdIssuerLocalNamespace(requireValue(args, ++i, arg));
                    break;
                case "--accession-issuer-oid":
                case "--accissuer":
                    options.getDefaults().withAccessionNumberIssuerOid(requireValue(args, ++i, arg));
                    break;
                case "--retrieve-location-uid":
                case "--repouid":
                case "-repouid":
                    options.getDefaults().withRetrieveLocationUid(requireValue(args, ++i, arg));
                    break;
                case "--wado-base-url":
                case "--wado":
                    options.getDefaults().withWadoRsBaseUrl(requireValue(args, ++i, arg));
                    break;
                case "--institution-name":
                    options.getDefaults().withInstitutionName(requireValue(args, ++i, arg));
                    break;

                // Query criteria
                case "--accession":
                    options.setAccession(requireValue(args, ++i, arg));
                    break;
                case "--study-uid":
                    options.setStudyUid(requireValue(args, ++i, arg));
                    break;
                case "--patient-id":
                    options.addPatientId(requireValue(args, ++i, arg));
                    break;
                case "--study-date":
                    options.addStudyDate(requireValue(args, ++i, arg));
                    break;
                case "--begin-date":
                    options.setBeginDate(requireDateValue(args, ++i, arg));
                    break;
                case "--end-date":
                    options.setEndDate(requireDateValue(args, ++i, arg));
                    break;
                case "--window-days":
                    options.setWindowDays(requireIntValue(args, ++i, arg));
                    break;

                // Output options
                case "--out":
                    parseOutOption(args, ++i, arg, options);
                    break;
                case "--out-dir":
                    options.setOutDir(new File(requireValue(args, ++i, arg)));
                    break;
                case "--pattern":
                    options.setOutPattern(requireValue(args, ++i, arg));
                    break;
                case "--overwrite":
                    options.setOverwrite(true);
                    break;
                case "--max-results":
                    options.setMaxResults(requireIntValue(args, ++i, arg));
                    break;
                case "--stream":
                    parseStreamingMode(args, ++i, arg, options);
                    break;

                default:
                    throw new IllegalArgumentException("Unknown argument: " + arg + " (use --help)");
            }
        }

        return options;
    }

    /**
     * Parse --out option, which can be either a file or directory.
     */
    private static void parseOutOption(String[] args, int index, String flag, SCUManifestOptions options) {
        String value = requireValue(args, index, flag);
        File candidate = new File(value);

        // If it exists and is a directory, or ends with path separator, treat as out-dir
        boolean looksLikeDir = (candidate.exists() && candidate.isDirectory())
            || value.endsWith("\\") || value.endsWith("/");

        if (looksLikeDir) {
            options.setOutDir(candidate);
            options.setOutFile(null);
        } else {
            options.setOutFile(candidate);
        }
    }

    /**
     * Parse --stream option (dicom|ndjson).
     */
    private static void parseStreamingMode(String[] args, int index, String flag, SCUManifestOptions options) {
        String value = requireValue(args, index, flag);
        if ("dicom".equalsIgnoreCase(value)) {
            options.setStreamingMode(StreamingMode.DICOM);
        } else if ("ndjson".equalsIgnoreCase(value)) {
            options.setStreamingMode(StreamingMode.NDJSON);
        } else {
            throw new IllegalArgumentException("Invalid --stream value: " + value + " (expected dicom|ndjson)");
        }
    }

    /**
     * Print usage/help message.
     */
    public static void printUsage() {
        System.out.println("SCU manifest CLI (KOS/MADO)\n");
        System.out.println("Required:");
        System.out.println("  --type kos|mado");
        System.out.println();
        System.out.println("Connection:");
        System.out.println("  --called-aet <AET>   (or -aec)  default=ORTHANC");
        System.out.println("  --calling-aet <AET>  (or -aet)  default=DICOMPOLICE");
        System.out.println("  --host <host>        default=localhost");
        System.out.println("  --port <port>        default=4242");
        System.out.println("  --connect-timeout-ms <ms>  default=5000");
        System.out.println("  --response-timeout-ms <ms> default=10000");
        System.out.println();
        System.out.println("Criteria (provide at least one):");
        System.out.println("  --study-uid <StudyInstanceUID>           (1 manifest expected)");
        System.out.println("  --accession <AccessionNumber>            (usually 1, can be many)");
        System.out.println("  --patient-id <PatientID>                 (repeatable; many manifests)");
        System.out.println("  --study-date <YYYYMMDD>                  (repeatable; many manifests)");
        System.out.println();
        System.out.println("Crawler (crawl entire PACS by StudyDate range):");
        System.out.println("  --begin-date <YYYY-MM-DD>   ISO date");
        System.out.println("  --end-date <YYYY-MM-DD>     ISO date");
        System.out.println("  --window-days <1..7>        Query window size (default=7)");
        System.out.println("  (date range crawling can't be combined with other criteria)");
        System.out.println();
        System.out.println("Output:");
        System.out.println("  --out <file.dcm>           Single output file (requires exactly one matched study)");
        System.out.println("  --out <dir\\>              If path ends with \\\\ or /, treated as output directory");
        System.out.println("  --out-dir <dir>            Output directory (default=.)");
        System.out.println("  --pattern <pattern>        Default={type}_{studyuid}.dcm");
        System.out.println("      tokens: {type} {studyuid} {patientid} {accession} {sopuid}");
        System.out.println("  --overwrite                Allow overwriting existing files");
        System.out.println("  --max-results <N>          Safety limit for broad queries (default=100)");
        System.out.println("  --stream dicom|ndjson      DICOM=build full manifest in memory (default). NDJSON=stream results to disk per instance.\n");

        System.out.println("IHE/XDS-I metadata defaults (optional):");
        System.out.println("  --patient-issuer-oid <OID>        (alias: --issuer)");
        System.out.println("  --patient-issuer-namespace <NS>");
        System.out.println("  --accession-issuer-oid <OID>      (alias: --accissuer)");
        System.out.println("  --retrieve-location-uid <UID>     (aliases: --repouid, -repouid)");
        System.out.println("  --wado-base-url <URL>             (alias: --wado)");
        System.out.println("  --institution-name <Name>\n");

        System.out.println("Examples:\n");
        System.out.println("  Create single KOS by StudyInstanceUID:\n" +
            "    java -cp target\\DICOMPolice-0.1.0-SNAPSHOT.jar be.uzleuven.ihe.dicom.creator.scu.cli.SCUManifestCli " +
            "--type kos --host localhost --port 4242 --called-aet ORTHANC --study-uid 1.2.3 --out out\\kos_1.2.3.dcm\n");

        System.out.println("  Create single MADO by accession number:\n" +
            "    java -cp target\\DICOMPolice-0.1.0-SNAPSHOT.jar be.uzleuven.ihe.dicom.creator.scu.cli.SCUManifestCli " +
            "--type mado --accession ACC123 --out out\\mado_ACC123.dcm\n");

        System.out.println("  Crawl a PACS by StudyDate from 1992-01-01 to 1994-01-01 (7-day windows), writing many manifests:\n" +
            "    java -cp target\\DICOMPolice-0.1.0-SNAPSHOT.jar be.uzleuven.ihe.dicom.creator.scu.cli.SCUManifestCli " +
            "--type mado --begin-date 1992-01-01 --end-date 1994-01-01 --window-days 7 --out-dir out\\crawl_1992_1994 --pattern {type}_{studyuid}.dcm --overwrite\n");

        System.out.println("  Create many KOS for a patientID into a directory:\n" +
            "    java -cp target\\DICOMPolice-0.1.0-SNAPSHOT.jar be.uzleuven.ihe.dicom.creator.scu.cli.SCUManifestCli " +
            "--type kos --patient-id 7 --out-dir out\\patient7 --pattern {type}_{studyuid}.dcm --overwrite\n");

        System.out.println("  Create many MADO for a given study date:\n" +
            "    java -cp target\\DICOMPolice-0.1.0-SNAPSHOT.jar be.uzleuven.ihe.dicom.creator.scu.cli.SCUManifestCli " +
            "--type mado --study-date 20251226 --out-dir out\\20251226 --pattern {type}_{accession}_{studyuid}.dcm\n");
    }
}

