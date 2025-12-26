package be.uzleuven.ihe.dicom.creator.scu.cli;

import be.uzleuven.ihe.dicom.creator.scu.KOSSCUManifestCreator;
import be.uzleuven.ihe.dicom.creator.scu.MADOSCUManifestCreator;
import be.uzleuven.ihe.dicom.creator.scu.SCUManifestCreator;
import be.uzleuven.ihe.dicom.creator.scu.streaming.NdjsonManifestStreamWriter;
import be.uzleuven.ihe.dicom.creator.scu.streaming.StreamingMode;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unified CLI for generating KOS or MADO manifests via SCU (C-FIND) queries.
 *
 * Design goals:
 * - One entrypoint for both KOS and MADO: use --type kos|mado
 * - Flexible search keys that can resolve to one or many studies
 * - Bulk-friendly output: --out for single, --out-dir/--pattern for many
 */
public class SCUManifestCli {

    public static void main(String[] args) {
        try {
            int exit = run(args);
            System.exit(exit);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(2);
        }
    }

    static int run(String[] args) throws Exception {
        if (args.length == 0 || hasArg(args, "--help", "-h")) {
            printUsage();
            return 0;
        }

        // --- Parse args ---
        SCUManifestCreator.DefaultMetadata defaults = new SCUManifestCreator.DefaultMetadata();

        ManifestType type = null;
        String accession = null;
        String studyUid = null;
        List<String> patientIds = new ArrayList<>();
        List<String> studyDates = new ArrayList<>();

        LocalDate beginDate = null;
        LocalDate endDate = null;
        int windowDays = 7;

        File outFile = null;
        File outDir = new File(".");
        String outPattern = "{type}_{studyuid}.dcm";
        boolean overwrite = false;
        int maxResults = 100;

        StreamingMode streamingMode = StreamingMode.DICOM;

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--type":
                    type = ManifestType.fromString(requireValue(args, ++i, a));
                    break;

                case "--called-aet":
                case "-aec":
                    defaults.withCalledAET(requireValue(args, ++i, a));
                    break;
                case "--calling-aet":
                case "-aet":
                    defaults.withCallingAET(requireValue(args, ++i, a));
                    break;
                case "--host":
                    defaults.withRemoteHost(requireValue(args, ++i, a));
                    break;
                case "--port":
                    defaults.withRemotePort(Integer.parseInt(requireValue(args, ++i, a)));
                    break;
                case "--connect-timeout-ms":
                    defaults.withConnectTimeout(Integer.parseInt(requireValue(args, ++i, a)));
                    break;
                case "--response-timeout-ms":
                    defaults.withResponseTimeout(Integer.parseInt(requireValue(args, ++i, a)));
                    break;

                // defaults/IHE metadata
                case "--patient-issuer-oid":
                case "--issuer": // alias
                    defaults.withPatientIdIssuerOid(requireValue(args, ++i, a));
                    break;
                case "--patient-issuer-namespace":
                    defaults.withPatientIdIssuerLocalNamespace(requireValue(args, ++i, a));
                    break;
                case "--accession-issuer-oid":
                case "--accissuer": // alias
                    defaults.withAccessionNumberIssuerOid(requireValue(args, ++i, a));
                    break;
                case "--retrieve-location-uid":
                case "--repouid":
                case "-repouid":
                    defaults.withRetrieveLocationUid(requireValue(args, ++i, a));
                    break;
                case "--wado-base-url":
                case "--wado": // alias
                    defaults.withWadoRsBaseUrl(requireValue(args, ++i, a));
                    break;
                case "--institution-name":
                    defaults.withInstitutionName(requireValue(args, ++i, a));
                    break;

                // criteria
                case "--accession":
                    accession = requireValue(args, ++i, a);
                    break;
                case "--study-uid":
                    studyUid = requireValue(args, ++i, a);
                    break;
                case "--patient-id":
                    patientIds.add(requireValue(args, ++i, a));
                    break;
                case "--study-date":
                    studyDates.add(requireValue(args, ++i, a));
                    break;
                case "--begin-date":
                    beginDate = parseIsoDate(requireValue(args, ++i, a), a);
                    break;
                case "--end-date":
                    endDate = parseIsoDate(requireValue(args, ++i, a), a);
                    break;
                case "--window-days":
                    windowDays = Integer.parseInt(requireValue(args, ++i, a));
                    break;

                // output
                case "--out": {
                    String v = requireValue(args, ++i, a);
                    File candidate = new File(v);

                    // If it exists and is a directory -> treat as out-dir.
                    // If it ends with a path separator -> treat as out-dir (even if doesn't exist yet).
                    boolean looksLikeDir = (candidate.exists() && candidate.isDirectory())
                        || v.endsWith("\\") || v.endsWith("/");

                    if (looksLikeDir) {
                        outDir = candidate;
                        outFile = null;
                    } else {
                        outFile = candidate;
                    }
                    break;
                }
                case "--out-dir":
                    outDir = new File(requireValue(args, ++i, a));
                    break;
                case "--pattern":
                    outPattern = requireValue(args, ++i, a);
                    break;
                case "--overwrite":
                    overwrite = true;
                    break;
                case "--max-results":
                    maxResults = Integer.parseInt(requireValue(args, ++i, a));
                    break;
                case "--stream": {
                    String v = requireValue(args, ++i, a);
                    if ("dicom".equalsIgnoreCase(v)) {
                        streamingMode = StreamingMode.DICOM;
                    } else if ("ndjson".equalsIgnoreCase(v)) {
                        streamingMode = StreamingMode.NDJSON;
                    } else {
                        throw new IllegalArgumentException("Invalid --stream value: " + v + " (expected dicom|ndjson)");
                    }
                    break;
                }

                default:
                    throw new IllegalArgumentException("Unknown argument: " + a + " (use --help)");
            }
        }

        if (type == null) {
            throw new IllegalArgumentException("Missing required --type kos|mado");
        }

        QueryCriteria.Builder cb = QueryCriteria.builder()
            .accessionNumber(accession)
            .studyInstanceUID(studyUid)
            .beginDate(beginDate)
            .endDate(endDate)
            .windowDays(windowDays);
        for (String pid : patientIds) cb.addPatientId(pid);
        for (String d : studyDates) cb.addStudyDate(d);
        QueryCriteria criteria = cb.build();
        criteria.validate();

        OutputOptions outputOptions = new OutputOptions(outFile, outDir, outPattern, overwrite, streamingMode);

        // --- Resolve studies + write incrementally ---
        SCUManifestCreator resolver = new KOSSCUManifestCreator(defaults); // any subclass works for querying
        StudyQueryService queryService = new StudyQueryService(resolver);

        if (outputOptions.getOutDir() != null && !outputOptions.getOutDir().exists()) {
            if (!outputOptions.getOutDir().mkdirs()) {
                throw new IOException("Failed to create out dir: " + outputOptions.getOutDir().getAbsolutePath());
            }
        }

        // --- Build + write manifests ---
        SCUManifestCreator creator = (type == ManifestType.KOS)
            ? new KOSSCUManifestCreator(defaults)
            : new MADOSCUManifestCreator(defaults);

        // Capture values used inside lambda (must be effectively final)
        final OutputOptions o = outputOptions;
        final ManifestType typeFinal = type;
        final SCUManifestCreator creatorFinal = creator;
        final int maxResultsFinal = maxResults;

        final AtomicInteger ok = new AtomicInteger();
        final AtomicInteger failed = new AtomicInteger();
        final AtomicInteger matched = new AtomicInteger();

        queryService.resolveStudiesStreaming(criteria, maxResultsFinal, s -> {
            int m = matched.incrementAndGet();
            try {
                if (o.getOutFile() != null && m != 1) {
                    throw new IllegalArgumentException("--out requires exactly 1 matched study, but matched " + m);
                }

                if (o.getStreamingMode() == StreamingMode.NDJSON) {
                    File out = resolveOutputFile(o, typeFinal, s, null);
                    ensureWritable(out, o.isOverwrite());

                    try (NdjsonManifestStreamWriter writer = new NdjsonManifestStreamWriter(out, o.isOverwrite())) {
                        creatorFinal.streamStudy(s.getStudyInstanceUID(), s.getPatientId(), writer);
                    }

                    System.out.println("Wrote " + typeFinal + " stream -> " + out.getAbsolutePath());
                } else {
                    Attributes manifest = creatorFinal.createManifest(s.getStudyInstanceUID(), s.getPatientId());
                    File out = resolveOutputFile(o, typeFinal, s, manifest);
                    ensureWritable(out, o.isOverwrite());

                    creatorFinal.saveToFile(manifest, out);

                    String sop = manifest.getString(Tag.SOPInstanceUID);
                    System.out.println("Wrote " + typeFinal + " -> " + out.getAbsolutePath() + (sop != null ? (" (SOPInstanceUID=" + sop + ")") : ""));
                }

                ok.incrementAndGet();
            } catch (Exception e) {
                failed.incrementAndGet();
                System.err.println("Failed for StudyInstanceUID=" + s.getStudyInstanceUID() + ": " + e.getMessage());
            }
        });

        if (matched.get() == 0) {
            System.err.println("No studies matched criteria.");
            return 3;
        }

        System.out.println("Done. matched=" + matched.get() + " ok=" + ok.get() + " failed=" + failed.get());
        return failed.get() == 0 ? 0 : 4;
    }

    private static LocalDate parseIsoDate(String v, String flag) {
        try {
            return LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date for " + flag + ": '" + v + "'. Expected ISO format YYYY-MM-DD");
        }
    }

    private static File resolveOutputFile(OutputOptions o, ManifestType type, StudyDescriptor s, Attributes manifest) {
        if (o.getOutFile() != null) {
            return o.getOutFile();
        }

        String name = o.getOutPattern();
        name = name.replace("{type}", type.name().toLowerCase());
        name = name.replace("{studyuid}", safeFileToken(s.getStudyInstanceUID()));
        if (s.getAccessionNumber() != null) {
            name = name.replace("{accession}", safeFileToken(s.getAccessionNumber()));
        }
        if (s.getPatientId() != null) {
            name = name.replace("{patientid}", safeFileToken(s.getPatientId()));
        }

        if (manifest != null) {
            String sop = manifest.getString(Tag.SOPInstanceUID);
            if (sop != null) {
                name = name.replace("{sopuid}", safeFileToken(sop));
            }
        } else {
            // streaming outputs don't have an SOPInstanceUID
            name = name.replace("{sopuid}", "");
        }

        // If user didn't include extension, keep as-is. Default pattern includes .dcm.
        // If streaming mode is NDJSON and pattern ends with .dcm, rewrite to .ndjson for convenience.
        if (o.getStreamingMode() == StreamingMode.NDJSON && name.toLowerCase().endsWith(".dcm")) {
            name = name.substring(0, name.length() - 4) + ".ndjson";
        }

        return new File(o.getOutDir(), name);
    }

    private static void ensureWritable(File out, boolean overwrite) throws IOException {
        if (out.exists() && !overwrite) {
            throw new IOException("Output exists (use --overwrite): " + out.getAbsolutePath());
        }
        File parent = out.getParentFile();
        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs()) {
                throw new IOException("Failed to create directory: " + parent.getAbsolutePath());
            }
        }
    }

    private static boolean hasArg(String[] args, String... names) {
        for (String a : args) {
            for (String n : names) {
                if (n.equalsIgnoreCase(a)) return true;
            }
        }
        return false;
    }

    private static String requireValue(String[] args, int index, String flag) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + flag);
        }
        return args[index];
    }

    private static String safeFileToken(String s) {
        if (s == null) return "";
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static void printUsage() {
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
