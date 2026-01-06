package be.uzleuven.ihe.dicom.convertor.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r4.model.*;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static be.uzleuven.ihe.dicom.constants.CodeConstants.*;

/**
 * Test and demonstration class for the MADO to FHIR converter.
 * Run this class to test the converter against sample MADO files.
 */
public class MADOToFHIRConverterTest {

    private static final FhirContext FHIR_CONTEXT = FhirContext.forR4();

    public static void main(String[] args) {
        System.out.println("MADO to FHIR Converter Test (MADO IG Compliant)");
        System.out.println("================================================\n");

        // Find MADO files to test
        String[] testDirs = {
            "MADO_FROM_SCU",
            "."
        };

        File testFile = null;
        for (String dir : testDirs) {
            File dirFile = new File(dir);
            if (dirFile.exists() && dirFile.isDirectory()) {
                File[] dcmFiles = dirFile.listFiles((d, name) ->
                    name.endsWith(".dcm") && name.toLowerCase().contains("mado"));
                if (dcmFiles != null && dcmFiles.length > 0) {
                    testFile = dcmFiles[0];
                    break;
                }
            }
        }

        // If specific file provided as argument, use that
        if (args.length > 0) {
            testFile = new File(args[0]);
        }

        if (testFile == null || !testFile.exists()) {
            System.out.println("No MADO DICOM files found to test.");
            System.out.println("Usage: java MADOToFHIRConverterTest [path/to/mado.dcm]");
            System.out.println("\nPlace MADO .dcm files in MADO_FROM_SCU directory or specify path directly.");
            return;
        }

        System.out.println("Testing with file: " + testFile.getAbsolutePath() + "\n");

        try {
            runConversionTest(testFile);
        } catch (Exception e) {
            System.err.println("Test failed with error: " + e.getMessage());
            System.err.println("Error type: " + e.getClass().getName());
            if (e.getCause() != null) {
                System.err.println("Caused by: " + e.getCause().getMessage());
            }
        }
    }

    private static void runConversionTest(File dicomFile) throws IOException {
        MADOToFHIRConverter converter = new MADOToFHIRConverter();

        // Convert
        System.out.println("Converting DICOM MADO to FHIR Document Bundle...\n");
        Bundle bundle = converter.convert(dicomFile);

        // Analyze results
        System.out.println("Conversion Results:");
        System.out.println("-------------------");
        System.out.println("Bundle Type: " + bundle.getType() + " (MADO requirement: DOCUMENT)");
        System.out.println("Bundle Identifier: " +
            (bundle.hasIdentifier() ? bundle.getIdentifier().getValue() : "MISSING"));
        System.out.println("Bundle Timestamp: " +
            (bundle.hasTimestamp() ? bundle.getTimestamp().toString() : "MISSING"));
        System.out.println();

        // Verify profile
        if (bundle.hasMeta() && bundle.getMeta().hasProfile()) {
            System.out.println("Bundle Profile: " + bundle.getMeta().getProfile().get(0).getValue());
        }
        System.out.println();

        // Count resource types
        int compositionCount = 0;
        int patientCount = 0;
        int deviceCount = 0;
        int practitionerCount = 0;
        int imagingStudyCount = 0;
        int endpointCount = 0;
        int basicCount = 0;
        int otherCount = 0;

        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            Resource resource = entry.getResource();
            if (resource instanceof Composition) compositionCount++;
            else if (resource instanceof Patient) patientCount++;
            else if (resource instanceof Device) deviceCount++;
            else if (resource instanceof Practitioner) practitionerCount++;
            else if (resource instanceof ImagingStudy) imagingStudyCount++;
            else if (resource instanceof Endpoint) endpointCount++;
            else if (resource instanceof Basic) basicCount++;
            else otherCount++;
        }

        System.out.println("Resource Breakdown:");
        System.out.println("  - Composition (document header): " + compositionCount);
        System.out.println("  - Patient: " + patientCount);
        System.out.println("  - Device (author system): " + deviceCount);
        System.out.println("  - Practitioner: " + practitionerCount);
        System.out.println("  - ImagingStudy: " + imagingStudyCount);
        System.out.println("  - Endpoint: " + endpointCount);
        System.out.println("  - Basic (ImagingSelection backport): " + basicCount);
        if (otherCount > 0) {
            System.out.println("  - Other: " + otherCount);
        }
        System.out.println();

        // Verify first entry is Composition (MADO requirement)
        Resource firstResource = bundle.getEntry().get(0).getResource();
        if (firstResource instanceof Composition) {
            System.out.println("✓ First entry is Composition (MADO compliant)");
        } else {
            System.out.println("✗ First entry is NOT Composition (MADO violation!)");
        }
        System.out.println();

        // Print details for each resource
        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            Resource resource = entry.getResource();

            if (resource instanceof Composition) {
                printCompositionDetails((Composition) resource);
            } else if (resource instanceof Patient) {
                printPatientDetails((Patient) resource);
            } else if (resource instanceof Device) {
                printDeviceDetails((Device) resource);
            } else if (resource instanceof ImagingStudy) {
                printImagingStudyDetails((ImagingStudy) resource);
            } else if (resource instanceof Endpoint) {
                printEndpointDetails((Endpoint) resource);
            } else if (resource instanceof Basic) {
                printBasicDetails((Basic) resource);
            }
        }

        // Output full JSON
        System.out.println("\n" + "=".repeat(60));
        System.out.println("FHIR JSON Output:");
        System.out.println("=".repeat(60) + "\n");

        IParser parser = FHIR_CONTEXT.newJsonParser();
        parser.setPrettyPrint(true);
        String json = parser.encodeResourceToString(bundle);
        System.out.println(json);
    }

    private static void printCompositionDetails(Composition composition) {
        System.out.println("Composition Resource (Document Header):");
        System.out.println("---------------------------------------");

        System.out.println("  Status: " + composition.getStatus());
        System.out.println("  Title: " + composition.getTitle());

        if (composition.hasIdentifier()) {
            System.out.println("  Identifier: " + composition.getIdentifier().getValue());
        }

        if (composition.hasDate()) {
            System.out.println("  Date: " + composition.getDate());
        }

        if (composition.hasAuthor()) {
            System.out.println("  Authors: " + composition.getAuthor().size());
        }

        if (composition.hasSection()) {
            System.out.println("  Sections: " + composition.getSection().size());
            for (Composition.SectionComponent section : composition.getSection()) {
                System.out.println("    - " + section.getTitle());
            }
        }

        System.out.println();
    }

    private static void printPatientDetails(Patient patient) {
        System.out.println("Patient Resource:");
        System.out.println("-----------------");

        // Check profile
        if (patient.hasMeta() && patient.getMeta().hasProfile()) {
            System.out.println("  Profile: " + patient.getMeta().getProfile().get(0).getValue());
        }

        if (patient.hasName()) {
            HumanName name = patient.getName().get(0);
            System.out.println("  Name: " + name.getNameAsSingleString());
        }

        if (patient.hasIdentifier()) {
            for (Identifier id : patient.getIdentifier()) {
                System.out.println("  ID: " + id.getValue() +
                    (id.hasSystem() ? " (system: " + id.getSystem() + ")" : ""));
            }
        }

        if (patient.hasBirthDate()) {
            System.out.println("  Birth Date: " + patient.getBirthDateElement().getValueAsString());
        }

        if (patient.hasGender()) {
            System.out.println("  Gender: " + patient.getGender().getDisplay());
        }

        System.out.println();
    }

    private static void printDeviceDetails(Device device) {
        System.out.println("Device Resource (Author System):");
        System.out.println("--------------------------------");

        // Check profile
        if (device.hasMeta() && device.getMeta().hasProfile()) {
            System.out.println("  Profile: " + device.getMeta().getProfile().get(0).getValue());
        }

        System.out.println("  Status: " + device.getStatus());

        if (device.hasManufacturer()) {
            System.out.println("  Manufacturer: " + device.getManufacturer());
        }

        if (device.hasDeviceName()) {
            System.out.println("  Device Name: " + device.getDeviceName().get(0).getName());
        }

        if (device.hasVersion()) {
            System.out.println("  Software Version: " + device.getVersion().get(0).getValue());
        }

        System.out.println();
    }

    private static void printImagingStudyDetails(ImagingStudy study) {
        System.out.println("ImagingStudy Resource:");
        System.out.println("----------------------");

        // Check profile
        if (study.hasMeta() && study.getMeta().hasProfile()) {
            System.out.println("  Profile: " + study.getMeta().getProfile().get(0).getValue());
        }

        if (study.hasIdentifier()) {
            for (Identifier id : study.getIdentifier()) {
                System.out.println("  Study UID: " + id.getValue());
                // Check for IHE prefix compliance
                if (id.getValue().startsWith("ihe:urn:oid:")) {
                    System.out.println("    ✓ Uses IHE UID prefix (MADO compliant)");
                }
            }
        }

        System.out.println("  Status: " + study.getStatus());

        if (study.hasDescription()) {
            System.out.println("  Description: " + study.getDescription());
        }

        if (study.hasStarted()) {
            System.out.println("  Started: " + study.getStartedElement().getValueAsString());
        }

        if (study.hasBasedOn()) {
            System.out.println("  Accession Numbers (basedOn): " + study.getBasedOn().size());
            for (Reference ref : study.getBasedOn()) {
                if (ref.hasIdentifier()) {
                    System.out.println("    - " + ref.getIdentifier().getValue());
                }
            }
        }

        System.out.println("  Endpoints: " + study.getEndpoint().size());

        // Series details
        List<ImagingStudy.ImagingStudySeriesComponent> series = study.getSeries();
        System.out.println("  Series Count: " + series.size());

        for (int i = 0; i < series.size(); i++) {
            ImagingStudy.ImagingStudySeriesComponent s = series.get(i);
            System.out.println("    Series " + (i + 1) + ":");
            System.out.println("      UID: " + s.getUid());
            if (s.hasModality()) {
                System.out.println("      Modality: " + s.getModality().getCode());
            }
            if (s.hasBodySite()) {
                System.out.println("      Body Site: " + s.getBodySite().getCode() +
                    " (" + s.getBodySite().getDisplay() + ")");
            }
            if (s.hasDescription()) {
                System.out.println("      Description: " + s.getDescription());
            }
            System.out.println("      Instances: " + s.getInstance().size());

            for (ImagingStudy.ImagingStudySeriesInstanceComponent inst : s.getInstance()) {
                String frameInfo = "";
                Extension framesExt = inst.getExtensionByUrl(
                    EXT_NUMBER_OF_FRAMES);
                if (framesExt != null && framesExt.hasValue()) {
                    frameInfo = " (" + framesExt.getValue().primitiveValue() + " frames)";
                }
                System.out.println("        - " + inst.getUid() + frameInfo);
            }
        }

        System.out.println();
    }

    private static void printEndpointDetails(Endpoint endpoint) {
        System.out.println("Endpoint Resource:");
        System.out.println("------------------");

        // Check profile
        if (endpoint.hasMeta() && endpoint.getMeta().hasProfile()) {
            System.out.println("  Profile: " + endpoint.getMeta().getProfile().get(0).getValue());
        }

        System.out.println("  Status: " + endpoint.getStatus());

        if (endpoint.hasConnectionType()) {
            Coding type = endpoint.getConnectionType();
            System.out.println("  Connection Type: " + type.getCode());
            if ("ihe-iid".equals(type.getCode())) {
                System.out.println("    ✓ IHE-IID endpoint (MADO requirement)");
            }
        }

        System.out.println("  Address: " + endpoint.getAddress());

        if (endpoint.hasIdentifier()) {
            for (Identifier id : endpoint.getIdentifier()) {
                System.out.println("  Identifier (Location UID): " + id.getValue());
            }
        }

        System.out.println();
    }

    private static void printBasicDetails(Basic basic) {
        System.out.println("Basic Resource (ImagingSelection backport):");
        System.out.println("--------------------------------------------");

        // Check profile
        if (basic.hasMeta() && basic.getMeta().hasProfile()) {
            System.out.println("  Profile: " + basic.getMeta().getProfile().get(0).getValue());
            System.out.println("    ✓ Uses ImImagingSelection profile (MADO compliant)");
        }

        if (basic.hasCode()) {
            System.out.println("  Type: " + basic.getCode().getText());
        }

        if (basic.hasCreated()) {
            System.out.println("  Created: " + basic.getCreated());
        }

        // Get selection code from extension
        Extension selectionCodeExt = basic.getExtensionByUrl(
            EXT_SELECTION_CODE);
        if (selectionCodeExt != null && selectionCodeExt.hasValue()) {
            CodeableConcept selCode = (CodeableConcept) selectionCodeExt.getValue();
            if (selCode.hasCoding()) {
                Coding coding = selCode.getCodingFirstRep();
                System.out.println("  Selection Code (KOS Title): " + coding.getCode() +
                    (coding.hasDisplay() ? " - " + coding.getDisplay() : ""));
            }
        }

        // Count selected instances
        int instanceCount = 0;
        for (Extension ext : basic.getExtension()) {
            if (EXT_SELECTED_INSTANCE.equals(ext.getUrl())) {
                instanceCount++;
            }
        }
        System.out.println("  Selected Instances: " + instanceCount);

        System.out.println();
    }
}
