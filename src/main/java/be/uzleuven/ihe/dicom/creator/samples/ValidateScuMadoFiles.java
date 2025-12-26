package be.uzleuven.ihe.dicom.creator.samples;

import be.uzleuven.ihe.dicom.validator.CLIDICOMVerify;
import be.uzleuven.ihe.dicom.validator.model.ValidationResult;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Helper runner to validate the checked-in SCU-generated examples under {@code KOS_FROM_SCU/}
 * and {@code MADO_FROM_SCU/}.
 * This is meant as a quick regression check when changing the SCU manifest builders.
 * Output is intentionally quiet: it prints the filename and only WARNING/ERROR messages.
 */
public class ValidateScuMadoFiles {

    private static final String DIR_KOS = "KOS_FROM_SCU";
    private static final String DIR_MADO = "MADO_FROM_SCU";

    private static final String PROFILE_KOS = "IHEXDSIManifest";
    private static final String PROFILE_MADO = "IHEMADO";

    /**
     * Direct programmatic entry point.
     * Validates:
     * - {@code KOS_FROM_SCU/} using profile {@code IHEXDSIManifest}
     * - {@code MADO_FROM_SCU/} using profile {@code IHEMADO}
     * Output is intentionally minimal: it prints the filename and only WARNING/ERROR messages.
     *
     * @return number of invalid files (0 when all pass)
     */
    public static int validateScuKOSAndMADO() throws IOException {
        int failures = 0;

        failures += validateDirectory(new File(System.getProperty("user.dir"), DIR_KOS), PROFILE_KOS);
        failures += validateDirectory(new File(System.getProperty("user.dir"), DIR_MADO), PROFILE_MADO);

        return failures;
    }

    public static void main(String[] args) throws Exception {
        // Keep this as an IDE convenience runner, but do not call any CLI main() that System.exit()s.
        int failures = validateScuKOSAndMADO();
        if (failures > 0) {
            throw new IllegalStateException("Validation failed for " + failures + " file(s)");
        }
        System.out.println("\nAll SCU KOS & MADO files validated successfully.");
    }

    private static int validateDirectory(File dir, String profile) throws IOException {
        if (!dir.exists() || !dir.isDirectory()) {
            throw new IllegalStateException("Directory not found: " + dir.getAbsolutePath());
        }

        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".dcm"));
        if (files == null || files.length == 0) {
            throw new IllegalStateException("No .dcm files found under: " + dir.getAbsolutePath());
        }

        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

        int failures = 0;
        for (File f : files) {
            failures += validateOneFile(f, profile);
        }

        return failures;
    }

    private static int validateOneFile(File f, String profile) throws IOException {
        // Always show which file is being checked.
        System.out.println("\nValidating: " + f.getName());

        ValidationResult result = CLIDICOMVerify.validateFile(f, profile);

        // Only print WARNING/ERROR. Suppress INFO completely.
        for (ValidationResult.ValidationMessage msg : result.getWarnings()) {
            System.out.println("  " + msg);
        }
        for (ValidationResult.ValidationMessage msg : result.getErrors()) {
            System.out.println("  " + msg);
        }

        return result.isValid() ? 0 : 1;
    }
}
