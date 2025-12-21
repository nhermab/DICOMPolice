package be.uzleuven.ihe.dicom.creator;

import be.uzleuven.ihe.dicom.validator.CLIDICOMVerify;

import java.io.File;

/**
 * Small helper runner to generate IHE_MADO.dcm and validate it using the in-repo validator.
 * This avoids relying on external scripts during development.
 */
public class GenerateAndValidateMado {

    public static void main(String[] args) throws Exception {
        // 1) Generate
        IHEMADOSampleCreator.main(new String[]{"1"});

        // 2) Validate
        File f = new File(System.getProperty("user.dir"), "IHE_MADO_0.dcm");
        if (!f.exists()) {
            throw new IllegalStateException("Expected generated file not found: " + f.getAbsolutePath());
        }

        CLIDICOMVerify.main(new String[]{"--profile", "IHEMADO", "-v",f.getAbsolutePath()});
    }
}

