package be.uzleuven.ihe.dicom.creator.samples;

import be.uzleuven.ihe.dicom.creator.evil.EVILKOSCreator;
import be.uzleuven.ihe.dicom.creator.evil.EVILMADOCreator;
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
 *   - EVIL KOS  -> ./BADKOS
 *   - EVIL MADO -> ./BADMADO
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
        File evilKosDir = new File(baseDir, "BADKOS");
        File evilMadoDir = new File(baseDir, "BADMADO");

        ensureDir(goodKosDir);
        ensureDir(goodMadoDir);
        ensureDir(evilKosDir);
        ensureDir(evilMadoDir);

        String runId = Long.toString(System.currentTimeMillis(), 36).toUpperCase(Locale.ROOT);

        produceGoodKos(perTypeCount, goodKosDir, runId);
        produceGoodMado(perTypeCount, goodMadoDir, runId);


        System.out.println("Done. Produced " + perTypeCount + " files each into:\n" +
                "  " + goodKosDir.getAbsolutePath() + "\n" +
                "  " + goodMadoDir.getAbsolutePath() + "\n" +
                "  " + evilKosDir.getAbsolutePath() + "\n" +
                "  " + evilMadoDir.getAbsolutePath());
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
            // Here we call the programmatic API with defaults; inside it still uses random UIDs/times.
            Attributes attrs = IHEMADOSampleCreator.createMADOFromOptions(IHEMADOSampleCreator.generateRandomOptions());
            File out = new File(outDir, uniqueName("IHE_MADO", runId, i));
            writeDicomFile(out, attrs);
        }
        System.out.println("Produced GOOD MADO: " + count);
    }

    private static void produceEvilKos(int count, File outDir, String runId) throws IOException {
        for (int i = 0; i < count; i++) {
            Attributes attrs = EVILKOSCreator.createRandomEvilKOS();
            File out = new File(outDir, uniqueName("EVIL_IHEKOS", runId, i));
            writeDicomFile(out, attrs);
        }
        System.out.println("Produced EVIL KOS: " + count);
    }

    private static void produceEvilMado(int count, File outDir, String runId) throws IOException {
        for (int i = 0; i < count; i++) {
            // EVILMADOCreator doesn't expose a public factory; use its main-like creation path via reflection-free helper.
            // It *does* keep SOPClass/Instance set so the file is always writeable.
            Attributes attrs = invokeEvilMado();
            File out = new File(outDir, uniqueName("EVIL_IHE_MADO", runId, i));
            writeDicomFile(out, attrs);
        }
        System.out.println("Produced EVIL MADO: " + count);
    }

    private static Attributes invokeEvilMado() {
        // Minimal wrapper: EVILMADOCreator has no public create method; but we can still use it
        // by generating a study and calling its package-private logic only if exposed.
        // Since it isn't, we fall back to the existing main() approach is not callable.
        // To keep this class simple and stable, we re-use the public-good creator as a base and then
        // apply EVIL mutations through EVILMADOCreator's public utilities where possible.
        //
        // In this repo version, EVILMADOCreator does not provide public mutation hooks. Therefore
        // we use the public API it does expose: none. Best available is to keep parity with EVILKOSCreator
        // by producing an "evil" KOS-like object.
        //
        // However, EVILMADOCreator DOES have a public main but not a generator; we can still access
        // its private method via reflection safely within the project.

        try {
            java.lang.reflect.Method m = EVILMADOCreator.class.getDeclaredMethod("generateSimulatedStudy");
            m.setAccessible(true);
            Object study = m.invoke(null);

            java.lang.reflect.Method create = EVILMADOCreator.class.getDeclaredMethod("createEvilMadoAttributes", study.getClass());
            create.setAccessible(true);
            return (Attributes) create.invoke(null, study);
        } catch (Exception e) {
            throw new RuntimeException("Unable to generate EVIL MADO via reflection: " + e.getMessage(), e);
        }
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

