package be.uzleuven.ihe.dicom.validator.tools;

import be.uzleuven.ihe.dicom.validator.validation.iod.MADOManifestValidator;
import be.uzleuven.ihe.dicom.validator.model.ValidationResult;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.io.DicomInputStream;

import java.io.File;

/**
 * Tiny local smoke runner for MADO validation.
 * Not part of the CLI; intended for quick dev verification.
 */
public class MADOCheckSmoke {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: MADOCheckSmoke <path-to-dcm>");
            System.exit(2);
        }

        File f = new File(args[0]);
        if (!f.exists()) {
            System.err.println("File not found: " + f.getAbsolutePath());
            System.exit(2);
        }

        Attributes ds;
        try (DicomInputStream dis = new DicomInputStream(f)) {
            // Using readDataset keeps this tool compatible with the dcm4che version pinned in the project.
            @SuppressWarnings("deprecation")
            Attributes tmp = dis.readDataset(-1, -1);
            ds = tmp;
        }

        MADOManifestValidator v = new MADOManifestValidator();
        ValidationResult res = v.validate(ds, true, "IHEMADO");

        for (ValidationResult.ValidationMessage msg : res.getMessages()) {
            System.out.println(msg);
        }

        if (!res.isValid()) {
            System.exit(1);
        }
    }
}
