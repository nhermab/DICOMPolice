/*
 * Based on PixelMed's dciodvfy.cc (DICOM IOD validation utility).
 *
 * PixelMed Copyright (c) 1993-2024, David A. Clunie DBA PixelMed Publishing.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 *    conditions and the following disclaimers.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 *    conditions and the following disclaimers in the documentation and/or other materials
 *    provided with the distribution.
 *
 * 3. Neither the name of PixelMed Publishing nor the names of its contributors may
 *    be used to endorse or promote products derived from this software.
 *
 * This software is provided by the copyright holders and contributors "as is" and any
 * express or implied warranties, including, but not limited to, the implied warranties
 * of merchantability and fitness for a particular purpose are disclaimed. In no event
 * shall the copyright owner or contributors be liable for any direct, indirect, incidental,
 * special, exemplary, or consequential damages (including, but not limited to, procurement
 * of substitute goods or services; loss of use, data or profits; or business interruption)
 * however caused and on any theory of liability, whether in contract, strict liability, or
 * tort (including negligence or otherwise) arising in any way out of the use of this software,
 * even if advised of the possibility of such damage.
 *
 * This software has neither been tested nor approved for clinical use or for incorporation in
 * a medical device. It is the redistributor's or user's responsibility to comply with any
 * applicable local, state, national or international regulations.
 *
 * Note: This project as a whole is distributed under GPLv2 (to comply with DCM4CHE).
 * The PixelMed-derived portions in this file remain under the PixelMed license above;
 * when redistributing, you must satisfy both the GPLv2 terms and the PixelMed notice.
 */

package be.uzleuven.ihe.dicom.validator;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;
import be.uzleuven.ihe.dicom.validator.validation.iod.IODValidator;
import be.uzleuven.ihe.dicom.validator.model.ValidationResult;
import be.uzleuven.ihe.dicom.validator.validation.Part10FileValidator;
import be.uzleuven.ihe.dicom.validator.validation.iod.IODValidatorFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Command-line interface for DICOM IOD validation.
 * Similar to dciodvfy main application.
 */
public class CLIDICOMVerify {

    public static void main(String[] args) {
        // Manual, dependency-free argument parsing
        boolean showHelp = false;
        boolean verbose = false;
        boolean newFormat = false;
        String profile = null;
        List<String> files = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("-h".equals(a) || "--help".equals(a)) {
                showHelp = true;
                break;
            } else if ("-v".equals(a) || "--verbose".equals(a)) {
                verbose = true;
            } else if ("--new-format".equals(a)) {
                newFormat = true;
            } else if (a.startsWith("--profile=")) {
                profile = a.substring("--profile=".length());
            } else if ("--profile".equals(a)) {
                if (i + 1 < args.length) {
                    profile = args[++i];
                } else {
                    System.err.println("Error: --profile requires a value");
                    printHelp();
                    System.exit(1);
                }
            } else if (a.startsWith("-")) {
                System.err.println("Unknown option: " + a);
                printHelp();
                System.exit(1);
            } else {
                files.add(a);
            }
        }

        if (showHelp || files.isEmpty()) {
            printHelp();
            System.exit(0);
        }

        int exitCode = 0;
        for (String filePath : files) {
            File file = new File(filePath);

            if (!file.exists()) {
                System.err.println("Error: File not found: " + filePath);
                exitCode = 1;
                continue;
            }

            if (!file.isFile()) {
                System.err.println("Error: Not a file: " + filePath);
                exitCode = 1;
                continue;
            }

            try {
                int result = validateFile(file, verbose, newFormat, profile);
                if (result != 0) {
                    exitCode = result;
                }
            } catch (Exception e) {
                System.err.println("Error validating file " + filePath + ": " + e.getMessage());
                if (verbose) {
                    e.printStackTrace(System.err);
                }
                exitCode = 1;
            }
        }

        System.exit(exitCode);
    }

    private static void printHelp() {
        System.out.println("Usage: CLIDICOMVerify [options] <dicom-file> [<dicom-file> ...]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -h, --help           Display this help message");
        System.out.println("  -v, --verbose        Verbose output with detailed validation messages");
        System.out.println("      --new-format     Use new format for error messages");
        System.out.println("      --profile <name> Validation profile:");
        System.out.println("                         IHEXDSIManifest - XDS-I.b KOS Manifest");
        System.out.println("                         IHEMADO         - MADO Manifest with Description");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  CLIDICOMVerify kos.dcm");
        System.out.println("  CLIDICOMVerify -v kos.dcm");
        System.out.println("  CLIDICOMVerify --profile IHEXDSIManifest kos.dcm");
        System.out.println("  CLIDICOMVerify --profile IHEMADO mado_manifest.dcm");
        System.out.println();
        System.out.println("Exit codes:");
        System.out.println("  0 - All validations passed");
        System.out.println("  1 - Validation errors found or file access errors");
    }

    private static int validateFile(File file, boolean verbose, boolean newFormat, String profile)
            throws IOException {

        System.out.println("\n" + repeat('=', 80));
        System.out.println("Validating: " + file.getAbsolutePath());
        System.out.println(repeat('=', 80));

        if (profile != null) {
            System.out.println("Profile: " + profile);
        }

        // Phase 1: Validate DICOM Part 10 File Format (before parsing)
        ValidationResult fileFormatResult = new ValidationResult();
        Part10FileValidator.validatePart10FileFormat(file, fileFormatResult, "Part10FileFormat");

        if (!fileFormatResult.isValid()) {
            System.out.println("\n✗ DICOM PART 10 FILE FORMAT VALIDATION FAILED");
            System.out.println("\nErrors:");
            for (ValidationResult.ValidationMessage msg : fileFormatResult.getErrors()) {
                System.out.println("  " + formatMessage(msg, newFormat));
            }
            return 1;
        }

        if (verbose) {
            System.out.println("\n✓ DICOM Part 10 File Format: Valid");
            for (ValidationResult.ValidationMessage msg : fileFormatResult.getMessages()) {
                if (msg.getSeverity() == ValidationResult.Severity.INFO) {
                    System.out.println("  " + formatMessage(msg, newFormat));
                }
            }
        }

        // Phase 2: Read DICOM file dataset
        Attributes dataset;
        try (DicomInputStream dis = new DicomInputStream(file)) {
            dataset = dis.readDataset();
        }

        // Phase 3: Validate File Meta Information (if present in dataset)
        Part10FileValidator.validateFileMetaInformation(dataset, fileFormatResult, "FileMetaInformation");

        if (verbose && !fileFormatResult.getMessages().isEmpty()) {
            for (ValidationResult.ValidationMessage msg : fileFormatResult.getMessages()) {
                if (msg.getSeverity() == ValidationResult.Severity.INFO ||
                    msg.getSeverity() == ValidationResult.Severity.WARNING) {
                    System.out.println("  " + formatMessage(msg, newFormat));
                }
            }
        }

        // Display basic information
        String sopClassUID = dataset.getString(Tag.SOPClassUID);
        String sopInstanceUID = dataset.getString(Tag.SOPInstanceUID);
        String modality = dataset.getString(Tag.Modality);

        System.out.println("\nDICOM Object Information:");
        System.out.println("  SOP Class UID:     " + (sopClassUID != null ? sopClassUID : "N/A"));
        System.out.println("  SOP Instance UID:  " + (sopInstanceUID != null ? sopInstanceUID : "N/A"));
        System.out.println("  Modality:          " + (modality != null ? modality : "N/A"));

        // Select appropriate validator with profile awareness
        IODValidator validator = IODValidatorFactory.selectValidator(dataset, profile);

        if (validator == null) {
            System.err.println("\nError: No validator found for SOP Class UID: " + sopClassUID);
            return 1;
        }

        System.out.println("  IOD Type:          " + validator.getIODName());

        if (validator.isRetired()) {
            System.out.println("  WARNING: This IOD is retired");
        }

        // Perform validation
        System.out.println("\nValidation Results:");
        System.out.println(repeat('-', 80));

        ValidationResult result;
        if (validator instanceof be.uzleuven.ihe.dicom.validator.validation.iod.AbstractIODValidator) {
            // Set the active profile for this validation run so static module validators can be profile-aware.
            be.uzleuven.ihe.dicom.validator.validation.iod.AbstractIODValidator.setActiveProfile(profile);
            try {
                result = validator.validate(dataset, verbose, profile);
            } finally {
                be.uzleuven.ihe.dicom.validator.validation.iod.AbstractIODValidator.setActiveProfile(null);
            }
        } else {
            result = validator.validate(dataset, verbose, profile);
        }

        // Display results
        if (result.isValid()) {
            System.out.println("\n✓ VALIDATION PASSED");

            if (verbose && !result.getMessages().isEmpty()) {
                System.out.println("\nInformation messages:");
                for (ValidationResult.ValidationMessage msg : result.getMessages()) {
                    System.out.println("  " + formatMessage(msg, newFormat));
                }
            }

            return 0;
        } else {
            System.out.println("\n✗ VALIDATION FAILED");

            // Display errors
            if (!result.getErrors().isEmpty()) {
                System.out.println("\nErrors (" + result.getErrors().size() + "): ");
                for (ValidationResult.ValidationMessage msg : result.getErrors()) {
                    System.out.println("  " + formatMessage(msg, newFormat));
                }
            }

            // Display warnings
            if (!result.getWarnings().isEmpty()) {
                System.out.println("\nWarnings (" + result.getWarnings().size() + "): ");
                for (ValidationResult.ValidationMessage msg : result.getWarnings()) {
                    System.out.println("  " + formatMessage(msg, newFormat));
                }
            }

            // Display info messages in verbose mode
            List<ValidationResult.ValidationMessage> infoMessages = new ArrayList<>();
            for (ValidationResult.ValidationMessage m : result.getMessages()) {
                if (m.getSeverity() == ValidationResult.Severity.INFO) {
                    infoMessages.add(m);
                }
            }

            if (verbose && !infoMessages.isEmpty()) {
                System.out.println("\nInformation:");
                for (ValidationResult.ValidationMessage msg : infoMessages) {
                    System.out.println("  " + formatMessage(msg, newFormat));
                }
            }

            return 1;
        }
    }

    private static String formatMessage(ValidationResult.ValidationMessage msg, boolean newFormat) {
        if (newFormat) {
            // New format: [SEVERITY] path: message
            return msg.toString();
        } else {
            // Old format: just the message
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(msg.getSeverity()).append("] ");
            sb.append(msg.getMessage());
            if (msg.getPath() != null && !msg.getPath().isEmpty()) {
                sb.append(" (").append(msg.getPath()).append(")");
            }
            return sb.toString();
        }
    }

    // Helper for Java 8 compatibility instead of String.repeat
    private static String repeat(char c, int count) {
        if (count <= 0) return "";
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) sb.append(c);
        return sb.toString();
    }

    /**
     * Public API method for programmatic validation (used by REST API).
     * @param file DICOM file to validate
     * @param profile Validation profile (IHEXDSIManifest, IHEMADO, etc.)
     * @return ValidationResult containing validation messages
     * @throws IOException if file cannot be read
     */
    public static ValidationResult validateFile(File file, String profile) throws IOException {
        ValidationResult combinedResult = new ValidationResult();

        // Phase 1: Validate DICOM Part 10 File Format
        Part10FileValidator.validatePart10FileFormat(file, combinedResult, "Part10FileFormat");

        if (!combinedResult.isValid()) {
            return combinedResult;
        }

        // Phase 2: Read DICOM dataset
        Attributes dataset;
        try (DicomInputStream dis = new DicomInputStream(file)) {
            dataset = dis.readDataset();
        }

        // Phase 3: Validate File Meta Information
        Part10FileValidator.validateFileMetaInformation(dataset, combinedResult, "FileMetaInformation");

        // Phase 4: Validate IOD
        IODValidator validator = IODValidatorFactory.selectValidator(dataset, profile);

        if (validator == null) {
            combinedResult.addError("No validator found for SOP Class UID: " + dataset.getString(Tag.SOPClassUID), "IODValidator");
            return combinedResult;
        }

        // For the webapp/API we always want the "checks performed" (INFO) entries.
        // So we run the validator in verbose mode here.
        boolean verbose = true;

        ValidationResult iodResult;
        if (validator instanceof be.uzleuven.ihe.dicom.validator.validation.iod.AbstractIODValidator) {
            be.uzleuven.ihe.dicom.validator.validation.iod.AbstractIODValidator.setActiveProfile(profile);
            try {
                iodResult = validator.validate(dataset, verbose, profile);
            } finally {
                be.uzleuven.ihe.dicom.validator.validation.iod.AbstractIODValidator.setActiveProfile(null);
            }
        } else {
            iodResult = validator.validate(dataset, verbose, profile);
        }

        combinedResult.merge(iodResult);
        return combinedResult;
    }
}
