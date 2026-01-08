package be.uzleuven.ihe.dicom.convertor.dicom;

import be.uzleuven.ihe.dicom.convertor.fhir.MADOToFHIRConverter;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.io.DicomInputStream;
import org.hl7.fhir.r5.model.Bundle;

import java.io.File;

/**
 * Demonstrates that deterministic UUIDs remain consistent across round-trip conversions.
 *
 * Usage: java RoundTripUuidConsistencyTest <input.dcm>
 *
 * This test performs:
 * 1. DICOM -> FHIR (first conversion)
 * 2. FHIR -> DICOM (convert back)
 * 3. DICOM -> FHIR (second conversion)
 * 4. Compares the UUIDs between first and second FHIR to verify consistency
 */
public class RoundTripUuidConsistencyTest {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java RoundTripUuidConsistencyTest <input.dcm>");
            System.exit(1);
        }

        File inputFile = new File(args[0]);
        if (!inputFile.exists()) {
            System.err.println("Input file not found: " + args[0]);
            System.exit(1);
        }

        try {
            System.out.println("=================================================");
            System.out.println("Round-Trip UUID Consistency Test");
            System.out.println("=================================================");
            System.out.println("Input file: " + inputFile.getAbsolutePath());
            System.out.println();

            // Step 1: Read original DICOM
            System.out.println("Step 1: Reading original DICOM...");
            Attributes originalDicom;
            try (DicomInputStream dis = new DicomInputStream(inputFile)) {
                originalDicom = dis.readDataset();
            }
            System.out.println("  ✓ Loaded DICOM");

            // Step 2: Convert to FHIR (first time)
            System.out.println("\nStep 2: Converting DICOM -> FHIR (first conversion)...");
            MADOToFHIRConverter toFhir = new MADOToFHIRConverter();
            Bundle firstFhir = toFhir.convert(originalDicom);
            System.out.println("  ✓ First FHIR bundle created");
            System.out.println("  Resources in bundle: " + firstFhir.getEntry().size());

            // Extract UUIDs from first conversion
            System.out.println("\n  First conversion UUIDs:");
            for (int i = 0; i < Math.min(10, firstFhir.getEntry().size()); i++) {
                Bundle.BundleEntryComponent entry = firstFhir.getEntry().get(i);
                String resourceType = entry.hasResource() ?
                    entry.getResource().getResourceType().name() : "Unknown";
                String fullUrl = entry.getFullUrl();
                String resourceId = entry.hasResource() && entry.getResource().hasId() ?
                    entry.getResource().getId() : "NO ID";
                System.out.println("    " + resourceType + ": fullUrl=" + fullUrl + ", id=" + resourceId);
            }

            // Step 3: Convert back to DICOM
            System.out.println("\nStep 3: Converting FHIR -> DICOM...");
            FHIRToMADOConverter toDicom = new FHIRToMADOConverter();
            Attributes roundTrippedDicom = toDicom.convert(firstFhir);
            System.out.println("  ✓ Converted back to DICOM");

            // Step 4: Convert to FHIR again (second time)
            System.out.println("\nStep 4: Converting DICOM -> FHIR (second conversion)...");
            MADOToFHIRConverter toFhir2 = new MADOToFHIRConverter();
            Bundle secondFhir = toFhir2.convert(roundTrippedDicom);
            System.out.println("  ✓ Second FHIR bundle created");
            System.out.println("  Resources in bundle: " + secondFhir.getEntry().size());

            // Extract UUIDs from second conversion
            System.out.println("\n  Second conversion UUIDs:");
            for (int i = 0; i < Math.min(10, secondFhir.getEntry().size()); i++) {
                Bundle.BundleEntryComponent entry = secondFhir.getEntry().get(i);
                String resourceType = entry.hasResource() ?
                    entry.getResource().getResourceType().name() : "Unknown";
                String fullUrl = entry.getFullUrl();
                String resourceId = entry.hasResource() && entry.getResource().hasId() ?
                    entry.getResource().getId() : "NO ID";
                System.out.println("    " + resourceType + ": fullUrl=" + fullUrl + ", id=" + resourceId);
            }

            // Step 5: Compare UUIDs
            System.out.println("\n=================================================");
            System.out.println("UUID Comparison");
            System.out.println("=================================================");

            boolean allMatch = true;
            int matchCount = 0;
            int mismatchCount = 0;

            int maxEntries = Math.min(firstFhir.getEntry().size(), secondFhir.getEntry().size());

            for (int i = 0; i < maxEntries; i++) {
                String url1 = firstFhir.getEntry().get(i).getFullUrl();
                String url2 = secondFhir.getEntry().get(i).getFullUrl();
                String id1 = firstFhir.getEntry().get(i).hasResource() && firstFhir.getEntry().get(i).getResource().hasId() ?
                    firstFhir.getEntry().get(i).getResource().getId() : "NONE";
                String id2 = secondFhir.getEntry().get(i).hasResource() && secondFhir.getEntry().get(i).getResource().hasId() ?
                    secondFhir.getEntry().get(i).getResource().getId() : "NONE";
                String type = firstFhir.getEntry().get(i).hasResource() ?
                    firstFhir.getEntry().get(i).getResource().getResourceType().name() : "Unknown";

                boolean urlMatch = url1.equals(url2);
                boolean idMatch = id1.equals(id2);

                if (urlMatch && idMatch) {
                    matchCount++;
                    System.out.println("✓ " + type + " - fullUrl and id both match");
                } else {
                    mismatchCount++;
                    allMatch = false;
                    System.out.println("✗ " + type + " MISMATCH!");
                    if (!urlMatch) {
                        System.out.println("  fullUrl First:  " + url1);
                        System.out.println("  fullUrl Second: " + url2);
                    }
                    if (!idMatch) {
                        System.out.println("  id First:  " + id1);
                        System.out.println("  id Second: " + id2);
                    }
                }
            }

            System.out.println("\n=================================================");
            System.out.println("Results");
            System.out.println("=================================================");
            System.out.println("Matching UUIDs: " + matchCount);
            System.out.println("Mismatched UUIDs: " + mismatchCount);

            if (allMatch) {
                System.out.println("\n✓✓✓ SUCCESS! All UUIDs are consistent across round-trip! ✓✓✓");
                System.exit(0);
            } else {
                System.out.println("\n✗✗✗ FAILURE! UUIDs changed during round-trip! ✗✗✗");
                System.exit(1);
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

