package be.uzleuven.ihe.dicom.creator.scu;

import org.dcm4che3.data.Attributes;

import java.io.File;

/**
 * Example usage of SCU manifest creators.
 *
 * This class demonstrates how to query a DICOM archive and create
 * KOS or MADO manifests from the retrieved metadata.
 */
public class SCUExample {

    /**
     * Example: Create a KOS manifest from a PACS query.
     */
    public static void createKOSFromPACS() throws Exception {
        System.out.println("=== Creating KOS Manifest from PACS Query ===\n");

        // Configure connection and defaults
        SCUManifestCreator.DefaultMetadata defaults = new SCUManifestCreator.DefaultMetadata()
            .withCallingAET("DICOMPOLICE")
            .withCalledAET("ORTHANC")
            .withRemoteHost("172.20.240.184")
            .withRemotePort(4242)
            .withPatientIdIssuerOid("1.2.840.113619.6.197")
            .withAccessionNumberIssuerOid("1.2.840.113619.6.197.1")
            .withRetrieveLocationUid("1.2.3.4.5.6.7.8.9.10")
            .withWadoRsBaseUrl("https://pacs.example.org/dicom-web/studies");

        // Create KOS manifest
        KOSSCUManifestCreator kosCreator = new KOSSCUManifestCreator(defaults);

        String studyUID = "1.3.46.670589.11.0.1.1996082307380006";
        String patientID = "7";

        System.out.println("Querying PACS for study: " + studyUID);
        System.out.println("Patient ID: " + patientID);
        System.out.println("Remote: " + defaults.calledAET + " @ " +
            defaults.remoteHost + ":" + defaults.remotePort);
        System.out.println();

        Attributes kosManifest = kosCreator.createManifest(studyUID, patientID);

        // Save to file
        File outputFile = new File("KOS_FROM_SCU_EXAMPLE.dcm");
        kosCreator.saveToFile(kosManifest, outputFile);

        System.out.println("✓ KOS manifest created: " + outputFile.getAbsolutePath());
        System.out.println("  SOP Instance UID: " + kosManifest.getString(0x00080018)); // Tag.SOPInstanceUID
        System.out.println();
    }

    /**
     * Example: Create a MADO manifest from a PACS query.
     */
    public static void createMADOFromPACS() throws Exception {
        System.out.println("=== Creating MADO Manifest from PACS Query ===\n");

        // Configure connection and defaults
        SCUManifestCreator.DefaultMetadata defaults = new SCUManifestCreator.DefaultMetadata()
            .withCallingAET("DICOMPOLICE")
            .withCalledAET("ORTHANC")
            .withRemoteHost("172.20.240.184")
            .withRemotePort(4242)
            .withPatientIdIssuerOid("1.2.840.113619.6.197")
            .withAccessionNumberIssuerOid("1.2.840.113619.6.197.1")
            .withRetrieveLocationUid("1.2.3.4.5.6.7.8.9.10")
            .withWadoRsBaseUrl("https://pacs.example.org/dicom-web/studies");

        // Create MADO manifest
        MADOSCUManifestCreator madoCreator = new MADOSCUManifestCreator(defaults);

        String studyUID = "1.3.46.670589.11.0.1.1996082307380006";
        String patientID = "7";

        System.out.println("Querying PACS for study: " + studyUID);
        System.out.println("Patient ID: " + patientID);
        System.out.println("Remote: " + defaults.calledAET + " @ " +
            defaults.remoteHost + ":" + defaults.remotePort);
        System.out.println();

        Attributes madoManifest = madoCreator.createManifest(studyUID, patientID);

        // Save to file
        File outputFile = new File("MADO_FROM_SCU_EXAMPLE.dcm");
        madoCreator.saveToFile(madoManifest, outputFile);

        System.out.println("✓ MADO manifest created: " + outputFile.getAbsolutePath());
        System.out.println("  SOP Instance UID: " + madoManifest.getString(0x00080018)); // Tag.SOPInstanceUID
        System.out.println();
    }

    /**
     * Main method - runs both examples if PACS is available.
     *
     * Usage:
     *   java -cp DICOMPolice.jar be.uzleuven.ihe.dicom.creator.scu.SCUExample
     *
     * Or run individual examples:
     *   java -cp DICOMPolice.jar be.uzleuven.ihe.dicom.creator.scu.SCUExample kos
     *   java -cp DICOMPolice.jar be.uzleuven.ihe.dicom.creator.scu.SCUExample mado
     */
    public static void main(String[] args) {
        try {
            if (args.length > 0) {
                String mode = args[0].toLowerCase();
                if ("kos".equals(mode)) {
                    createKOSFromPACS();
                } else if ("mado".equals(mode)) {
                    createMADOFromPACS();
                } else {
                    System.err.println("Unknown mode: " + mode);
                    System.err.println("Usage: SCUExample [kos|mado]");
                    System.exit(1);
                }
            } else {
                // Run both examples
                createKOSFromPACS();
                createMADOFromPACS();
            }

            System.out.println("=== All examples completed successfully ===");

        } catch (Exception e) {
            System.err.println("Error running example: " + e.getMessage());
            System.err.println("\nTroubleshooting:");
            System.err.println("1. Check that PACS is running and accessible");
            System.err.println("2. Verify AE Title configuration matches PACS");
            System.err.println("3. Confirm Study Instance UID exists in PACS");
            System.err.println("4. Check network connectivity and firewall settings");
            e.printStackTrace();
            System.exit(1);
        }
    }
}

