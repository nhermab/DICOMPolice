package be.uzleuven.ihe.dicom.creator.kin;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static be.uzleuven.ihe.dicom.creator.utils.DicomCreatorUtils.writeDicomFile;

/** Command-line example that adds one synthetic KIN to an existing DICOM MADO. */
public final class MadoKinUpgradeCli {

    private MadoKinUpgradeCli() {
    }

    public static void main(String[] args) {
        try {
            int status = run(args);
            if (status != 0) System.exit(status);
        } catch (Exception e) {
            System.err.println("MADO KIN upgrade failed: " + e.getMessage());
            System.exit(2);
        }
    }

    public static int run(String[] args) throws IOException {
        if (args.length == 0 || hasFlag(args, "--help", "-h")) {
            printUsage();
            return 0;
        }

        Path input = null;
        Path madoOutput = null;
        Path kinOutput = null;
        String description = MadoKinUpgrader.DEFAULT_DESCRIPTION;
        String seriesNumber = MadoKinUpgrader.DEFAULT_KIN_SERIES_NUMBER;
        String instanceNumber = MadoKinUpgrader.DEFAULT_KIN_INSTANCE_NUMBER;
        String retrieveLocationUID = null;
        String retrieveURL = null;
        long seed = 12345L;
        boolean overwrite = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--input", "-i" -> input = Path.of(value(args, ++i, args[i - 1]));
                case "--mado-out" -> madoOutput = Path.of(value(args, ++i, "--mado-out"));
                case "--kin-out" -> kinOutput = Path.of(value(args, ++i, "--kin-out"));
                case "--description" -> description = value(args, ++i, "--description");
                case "--series-number" -> seriesNumber = value(args, ++i, "--series-number");
                case "--instance-number" -> instanceNumber = value(args, ++i, "--instance-number");
                case "--retrieve-location-uid" -> retrieveLocationUID = value(args, ++i, "--retrieve-location-uid");
                case "--retrieve-url" -> retrieveURL = value(args, ++i, "--retrieve-url");
                case "--seed" -> seed = Long.parseLong(value(args, ++i, "--seed"));
                case "--overwrite" -> overwrite = true;
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }

        if (input == null) throw new IllegalArgumentException("Missing required --input");
        input = input.toAbsolutePath().normalize();
        if (!Files.isRegularFile(input)) throw new IllegalArgumentException("Input file does not exist: " + input);

        String baseName = stripExtension(input.getFileName().toString());
        Path parent = input.getParent();
        if (madoOutput == null) madoOutput = parent.resolve(baseName + "-with-kin.dcm");
        if (kinOutput == null) kinOutput = parent.resolve(baseName + "-kin.dcm");
        madoOutput = madoOutput.toAbsolutePath().normalize();
        kinOutput = kinOutput.toAbsolutePath().normalize();
        ensureWritable(madoOutput, overwrite);
        ensureWritable(kinOutput, overwrite);
        if (madoOutput.equals(kinOutput) || madoOutput.equals(input) || kinOutput.equals(input)) {
            throw new IllegalArgumentException("Input, KIN output, and revised MADO output must be different files");
        }

        Attributes original;
        try (DicomInputStream inputStream = new DicomInputStream(input.toFile())) {
            original = inputStream.readDataset();
        }

        MadoKinUpgrader.Options options = new MadoKinUpgrader.Options(seed, description,
                seriesNumber, instanceNumber, retrieveLocationUID, retrieveURL);
        MadoKinUpgrader.UpgradeResult result = MadoKinUpgrader.upgrade(original, options);

        createParentDirectories(kinOutput);
        createParentDirectories(madoOutput);
        // The KIN must be stored/published before the MADO revision that advertises it.
        writeDicomFile(kinOutput.toFile(), result.kin());
        writeDicomFile(madoOutput.toFile(), result.revisedMado());

        System.out.println("Selected image SOP Instance UID: " + result.selectedImage().sopInstanceUID());
        System.out.println("Wrote KIN: " + kinOutput + " (SOPInstanceUID="
                + result.kin().getString(Tag.SOPInstanceUID) + ")");
        System.out.println("Wrote revised MADO: " + madoOutput + " (SOPInstanceUID="
                + result.revisedMado().getString(Tag.SOPInstanceUID) + ")");
        System.out.println("Note: publish/store the KIN at the Imaging Document Source identified by Retrieve Location UID "
                + result.revisedMado().getSequence(Tag.CurrentRequestedProcedureEvidenceSequence)
                .get(0).getSequence(Tag.ReferencedSeriesSequence)
                .get(result.revisedMado().getSequence(Tag.CurrentRequestedProcedureEvidenceSequence)
                        .get(0).getSequence(Tag.ReferencedSeriesSequence).size() - 1)
                .getString(Tag.RetrieveLocationUID) + ".");
        return 0;
    }

    private static void ensureWritable(Path path, boolean overwrite) {
        if (Files.exists(path) && !overwrite) {
            throw new IllegalArgumentException("Output already exists (use --overwrite): " + path);
        }
    }

    private static void createParentDirectories(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
    }

    private static String value(String[] args, int index, String option) {
        if (index >= args.length) throw new IllegalArgumentException("Missing value for " + option);
        return args[index];
    }

    private static boolean hasFlag(String[] args, String... flags) {
        for (String arg : args) {
            for (String flag : flags) {
                if (flag.equals(arg)) return true;
            }
        }
        return false;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static void printUsage() {
        System.out.println("Usage: MadoKinUpgradeCli --input <mado.dcm> [options]");
        System.out.println("  --mado-out <file>              Revised MADO (default: <input>-with-kin.dcm)");
        System.out.println("  --kin-out <file>               Separate KIN/KOS (default: <input>-kin.dcm)");
        System.out.println("  --description <text>           Key Object Description");
        System.out.println("  --seed <long>                  Reproducible random selection (default: 12345)");
        System.out.println("  --series-number <IS>           New KO series number (default: 9001)");
        System.out.println("  --instance-number <IS>         KIN instance number (default: 1)");
        System.out.println("  --retrieve-location-uid <UID>  IDS storing the KIN; otherwise copied from selected series");
        System.out.println("  --retrieve-url <URL>           Optional KIN retrieval base URL");
        System.out.println("  --overwrite                    Replace existing output files");
    }
}
