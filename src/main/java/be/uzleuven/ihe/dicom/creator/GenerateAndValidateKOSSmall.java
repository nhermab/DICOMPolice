package be.uzleuven.ihe.dicom.creator;

import be.uzleuven.ihe.dicom.validator.CLIDICOMVerify;

import java.io.File;

/**
 * Small helper runner to generate an IHE XDS-I.b KOS manifest (IHEKOS_0.dcm)
 * and validate it using the in-repo validator.
 */
public class GenerateAndValidateKOSSmall {

    public static void main(String[] args) throws Exception {
        // 1) Generate a single KOS
        IHEKOSSampleCreator.main(new String[]{"1", "--default-sizes"});

        // 2) Validate
        File f = new File(System.getProperty("user.dir"), "IHEKOS_0.dcm");
        if (!f.exists()) {
            throw new IllegalStateException("Expected generated file not found: " + f.getAbsolutePath());
        }

        CLIDICOMVerify.main(new String[]{"--profile", "IHEXDSIManifest", f.getAbsolutePath()});
    }
}
