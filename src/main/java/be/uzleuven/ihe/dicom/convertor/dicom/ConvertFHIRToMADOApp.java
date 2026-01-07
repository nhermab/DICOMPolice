package be.uzleuven.ihe.dicom.convertor.dicom;

import org.dcm4che3.data.Attributes;

import java.io.File;

/**
 * Command-line application for converting FHIR MADO Bundles to DICOM MADO files.
 *
 * Usage:
 *   java ConvertFHIRToMADOApp <input.json> <output.dcm>
 *
 * Example:
 *   java ConvertFHIRToMADOApp mado_bundle.json IHE_MADO_ROUNDTRIP.dcm
 */
public class ConvertFHIRToMADOApp {

    public static void main(String[] args) {
        if (args.length < 2) {
            printUsage();
            System.exit(1);
        }

        String inputPath = args[0];
        String outputPath = args[1];

        File inputFile = new File(inputPath);
        if (!inputFile.exists()) {
            System.err.println("Error: Input file not found: " + inputPath);
            System.exit(1);
        }

        try {
            System.out.println("Converting FHIR MADO to DICOM MADO...");
            System.out.println("  Input:  " + inputPath);
            System.out.println("  Output: " + outputPath);

            FHIRToMADOConverter converter = new FHIRToMADOConverter();
            Attributes dicom = converter.convertFromJsonFile(inputFile);

            // Save to file
            be.uzleuven.ihe.dicom.creator.utils.DicomCreatorUtils.writeDicomFile(
                new File(outputPath), dicom);

            System.out.println("\nConversion successful!");
            System.out.println("  SOP Instance UID: " + dicom.getString(org.dcm4che3.data.Tag.SOPInstanceUID));
            System.out.println("  Study Instance UID: " + dicom.getString(org.dcm4che3.data.Tag.StudyInstanceUID));
            System.out.println("  Patient ID: " + dicom.getString(org.dcm4che3.data.Tag.PatientID));

        } catch (Exception e) {
            System.err.println("Error during conversion: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("ConvertFHIRToMADOApp - Convert FHIR MADO Bundle to DICOM MADO file");
        System.out.println();
        System.out.println("Usage: java ConvertFHIRToMADOApp <input.json> <output.dcm>");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  input.json   Path to input FHIR Bundle JSON file");
        System.out.println("  output.dcm   Path for output DICOM MADO file");
        System.out.println();
        System.out.println("Example:");
        System.out.println("  java ConvertFHIRToMADOApp mado_bundle.json IHE_MADO_ROUNDTRIP.dcm");
    }
}

