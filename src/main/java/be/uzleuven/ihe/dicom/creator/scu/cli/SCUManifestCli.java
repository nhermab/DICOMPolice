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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unified CLI for generating KOS or MADO manifests via SCU (C-FIND) queries.
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
            SCUManifestOptionsParser.printUsage();
            return 0;
        }

        // Parse arguments
        SCUManifestOptions options = SCUManifestOptionsParser.parse(args);

        if (options.getType() == null) {
            throw new IllegalArgumentException("Missing required --type kos|mado");
        }

        // Build query criteria
        QueryCriteria.Builder cb = QueryCriteria.builder()
            .accessionNumber(options.getAccession())
            .studyInstanceUID(options.getStudyUid())
            .beginDate(options.getBeginDate())
            .endDate(options.getEndDate())
            .windowDays(options.getWindowDays());
        for (String pid : options.getPatientIds()) cb.addPatientId(pid);
        for (String d : options.getStudyDates()) cb.addStudyDate(d);
        QueryCriteria criteria = cb.build();
        criteria.validate();

        // Build output options
        OutputOptions outputOptions = new OutputOptions(
            options.getOutFile(),
            options.getOutDir(),
            options.getOutPattern(),
            options.isOverwrite(),
            options.getStreamingMode()
        );

        // Resolve studies + write incrementally
        SCUManifestCreator resolver = new KOSSCUManifestCreator(options.getDefaults());
        StudyQueryService queryService = new StudyQueryService(resolver);

        if (outputOptions.getOutDir() != null && !outputOptions.getOutDir().exists()) {
            if (!outputOptions.getOutDir().mkdirs()) {
                throw new IOException("Failed to create out dir: " + outputOptions.getOutDir().getAbsolutePath());
            }
        }

        // Build + write manifests
        SCUManifestCreator creator = (options.getType() == ManifestType.KOS)
            ? new KOSSCUManifestCreator(options.getDefaults())
            : new MADOSCUManifestCreator(options.getDefaults());

        return executeManifestCreation(creator, queryService, criteria, options, outputOptions);
    }

    private static int executeManifestCreation(
            SCUManifestCreator creator,
            StudyQueryService queryService,
            QueryCriteria criteria,
            SCUManifestOptions options,
            OutputOptions outputOptions) throws Exception {

        final AtomicInteger ok = new AtomicInteger();
        final AtomicInteger failed = new AtomicInteger();
        final AtomicInteger matched = new AtomicInteger();

        queryService.resolveStudiesStreaming(criteria, options.getMaxResults(), s -> {
            int m = matched.incrementAndGet();
            try {
                if (outputOptions.getOutFile() != null && m != 1) {
                    throw new IllegalArgumentException("--out requires exactly 1 matched study, but matched " + m);
                }

                if (outputOptions.getStreamingMode() == StreamingMode.NDJSON) {
                    File out = resolveOutputFile(outputOptions, options.getType(), s, null);
                    ensureWritable(out, outputOptions.isOverwrite());

                    try (NdjsonManifestStreamWriter writer = new NdjsonManifestStreamWriter(out, outputOptions.isOverwrite())) {
                        creator.streamStudy(s.getStudyInstanceUID(), s.getPatientId(), writer);
                    }

                    System.out.println("Wrote " + options.getType() + " stream -> " + out.getAbsolutePath());
                } else {
                    Attributes manifest = creator.createManifest(s.getStudyInstanceUID(), s.getPatientId());
                    File out = resolveOutputFile(outputOptions, options.getType(), s, manifest);
                    ensureWritable(out, outputOptions.isOverwrite());

                    creator.saveToFile(manifest, out);

                    String sop = manifest.getString(Tag.SOPInstanceUID);
                    System.out.println("Wrote " + options.getType() + " -> " + out.getAbsolutePath() + (sop != null ? (" (SOPInstanceUID=" + sop + ")") : ""));
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

    private static String safeFileToken(String s) {
        if (s == null) return "";
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
