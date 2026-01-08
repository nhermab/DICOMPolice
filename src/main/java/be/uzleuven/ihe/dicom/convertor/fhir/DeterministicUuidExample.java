package be.uzleuven.ihe.dicom.convertor.fhir;

import org.hl7.fhir.r5.model.Bundle;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

/**
 * Example demonstrating deterministic UUID generation in MADO-to-FHIR conversion.
 */
public class DeterministicUuidExample {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java DeterministicUuidExample <mado-file.dcm>");
            System.exit(1);
        }

        String dicomFile = args[0];

        // Example 1: Convert with deterministic UUIDs (default)
        System.out.println("=== Conversion 1 (Deterministic UUIDs - Default) ===");
        MADOToFHIRConverter converter1 = new MADOToFHIRConverter();
        Bundle bundle1 = converter1.convert(dicomFile);
        printUuids(bundle1, "Conversion 1");

        // Example 2: Convert same file again - UUIDs should be identical
        System.out.println("\n=== Conversion 2 (Same file, should have identical UUIDs) ===");
        MADOToFHIRConverter converter2 = new MADOToFHIRConverter();
        Bundle bundle2 = converter2.convert(dicomFile);
        printUuids(bundle2, "Conversion 2");

        // Example 3: Convert with random UUIDs
        System.out.println("\n=== Conversion 3 (Random UUIDs) ===");
        MADOToFHIRConverter converter3 = new MADOToFHIRConverter();
        converter3.setUseDeterministicUuids(false); // Disable deterministic UUIDs
        Bundle bundle3 = converter3.convert(dicomFile);
        printUuids(bundle3, "Conversion 3");

        // Compare outputs
        System.out.println("\n=== Comparison ===");
        System.out.println("Conversion 1 and 2 UUIDs match: " + compareUuids(bundle1, bundle2));
        System.out.println("Conversion 1 and 3 UUIDs match: " + compareUuids(bundle1, bundle3));
    }

    private static void printUuids(Bundle bundle, String label) {
        System.out.println(label + ":");
        if (bundle.hasEntry()) {
            for (int i = 0; i < Math.min(5, bundle.getEntry().size()); i++) {
                Bundle.BundleEntryComponent entry = bundle.getEntry().get(i);
                String resourceType = entry.hasResource() ? entry.getResource().getResourceType().name() : "Unknown";
                System.out.println("  " + resourceType + ": " + entry.getFullUrl());
            }
        }
    }

    private static boolean compareUuids(Bundle bundle1, Bundle bundle2) {
        if (bundle1.getEntry().size() != bundle2.getEntry().size()) {
            return false;
        }

        for (int i = 0; i < bundle1.getEntry().size(); i++) {
            String url1 = bundle1.getEntry().get(i).getFullUrl();
            String url2 = bundle2.getEntry().get(i).getFullUrl();
            if (!url1.equals(url2)) {
                return false;
            }
        }

        return true;
    }
}

