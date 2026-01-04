package be.uzleuven.ihe.dicom.creator.samples;

import be.uzleuven.ihe.dicom.creator.options.MADOOptionsUtils;
import org.dcm4che3.data.Attributes;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import static be.uzleuven.ihe.dicom.creator.utils.DicomCreatorUtils.writeDicomFile;

/**
 * Bulk sample producer.
 * Contract:
 * - Creates N files for each category:
 *   - GOOD KOS  -> ./GOODKOS
 *   - GOOD MADO -> ./GOODMADO
 * - Keeps the existing random-generation behavior by delegating to the existing creators.
 * - Guarantees unique filenames (timestamp + counter).
 * Usage:
 *   java be.uzleuven.ihe.dicom.creator.samples.ExampleProducer 2000
 *   java be.uzleuven.ihe.dicom.creator.samples.ExampleProducer 2000 C:\\path\\to\\workspace
 */
public class ExampleProducer {

    public static void main(String[] args) throws Exception {
        int perTypeCount = parseCount(args, 100);
        File baseDir = parseBaseDir(args);

        File goodKosDir = new File(baseDir, "GOODKOS");
        File goodMadoDir = new File(baseDir, "GOODMADO");

        ensureDir(goodKosDir);
        ensureDir(goodMadoDir);

        String runId = Long.toString(System.currentTimeMillis(), 36).toUpperCase(Locale.ROOT);

        produceGoodKos(perTypeCount, goodKosDir, runId);
        produceGoodMado(perTypeCount, goodMadoDir, runId);


        System.out.println("Done. Produced " + perTypeCount + " files each into:\n" +
                "  " + goodKosDir.getAbsolutePath() + "\n" +
                "  " + goodMadoDir.getAbsolutePath());
    }

    private static void produceGoodKos(int count, File outDir, String runId) throws IOException {
        for (int i = 0; i < count; i++) {
            Attributes attrs = IHEKOSSampleCreator.createRandomIHEKOS(IHEKOSSampleCreator.generateRandomOptions());
            File out = new File(outDir, uniqueName("IHEKOS", runId, i));
            writeDicomFile(out, attrs);
        }
        System.out.println("Produced GOOD KOS: " + count);
    }

    private static void produceGoodMado(int count, File outDir, String runId) throws IOException {
        for (int i = 0; i < count; i++) {
            // MADO creator randomizes study structure in its random options main path.
            // Use the non-deprecated utility to generate options.
            Attributes attrs = IHEMADOSampleCreator.createMADOFromOptions(MADOOptionsUtils.generateRandomOptions());
            File out = new File(outDir, uniqueName("IHE_MADO", runId, i));
            writeDicomFile(out, attrs);
        }
        System.out.println("Produced GOOD MADO: " + count);
    }

    private static String uniqueName(String prefix, String runId, int i) {
        // Example: IHEKOS_R1Z2M3_000123.dcm
        return String.format(Locale.ROOT, "%s_%s_%06d.dcm", prefix, runId, i);
    }

    private static int parseCount(String[] args, int defaultValue) {
        if (args == null || args.length == 0) return defaultValue;
        try {
            return Math.max(1, Integer.parseInt(args[0]));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static File parseBaseDir(String[] args) {
        if (args != null && args.length >= 2) {
            return new File(args[1]);
        }
        //return new File(System.getProperty("user.dir"));
        return new File("D:\\IHE_SAMPLES");
    }

    private static void ensureDir(File dir) {
        if (dir.isDirectory()) return;
        if (dir.exists() && !dir.isDirectory()) {
            throw new IllegalStateException("Path exists but is not a directory: " + dir.getAbsolutePath());
        }
        if (!dir.mkdirs()) {
            throw new IllegalStateException("Unable to create directory: " + dir.getAbsolutePath());
        }
    }
}
