package be.uzleuven.ihe.dicom.creator.evil;

import be.uzleuven.ihe.dicom.validator.CLIDICOMVerify;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Small helper runner to generate IHE_MADO.dcm and validate it using the in-repo validator.
 * This avoids relying on external scripts during development.
 */
public class GenerateAndValidateEvilKOS {

    public static void main(String[] args) throws Exception {
        // 1) Generate
        EVILKOSCreator.main(new String[0]);

        // 2) Find the generated EVIL KOS file
        File outDir = new File(System.getProperty("user.dir"));
        File[] matches = outDir.listFiles((dir, name) -> name.startsWith("EVIL_IHEKOS_") && name.endsWith(".dcm"));

        File f = null;
        if (matches != null && matches.length > 0) {
            f = Arrays.stream(matches).max(Comparator.comparingLong(File::lastModified)).get();
        } else {
            // fall back to legacy name
            File legacy = new File(outDir, "IHEKOS_0.dcm");
            if (legacy.exists()) {
                f = legacy;
            }
        }

        if (f == null || !f.exists()) {
            throw new IllegalStateException("Expected generated file not found in " + outDir.getAbsolutePath());
        }

        System.out.println("Validating: " + f.getAbsolutePath());
        // KOS manifests use the IHEXDSIManifest profile
        CLIDICOMVerify.main(new String[]{"--profile", "IHEXDSIManifest", "-v", f.getAbsolutePath()});
    }
}
