package be.uzleuven.ihe.service;

import be.uzleuven.ihe.dicom.creator.samples.IHEKOSSampleCreator;
import be.uzleuven.ihe.dicom.creator.samples.IHEMADOSampleCreator;
import be.uzleuven.ihe.dicom.creator.model.MADOOptions;
import be.uzleuven.ihe.dicom.validator.CLIDICOMVerify;
import be.uzleuven.ihe.dicom.validator.model.ValidationResult;
import be.uzleuven.ihe.service.models.*;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.io.DicomOutputStream;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.*;

@RestController
@RequestMapping("/validation/v2")
public class GazelleValidatorAPIController {

    private static final String SERVICE_NAME = "DICOM IHE Validation Service";
    private static final String SERVICE_VERSION = "1.0.0";

    @GetMapping(value = "/profiles", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<ValidationProfile>> getProfiles() {
        List<ValidationProfile> profiles = new ArrayList<>();

        ValidationProfile kosProfile = new ValidationProfile();
        kosProfile.setProfileID("IHE.RAD.XDSI.KOS");
        kosProfile.setProfileName("IHE XDS-I.b Key Object Selection");
        kosProfile.setDomain("RAD");
        kosProfile.setCoveredItems(Arrays.asList("DICOM KOS", "Key Object Selection Document"));
        kosProfile.setStandards(Arrays.asList("DICOM", "IHE RAD TF-2", "IHE ITI TF-3"));
        kosProfile.setVersion("1.0");
        profiles.add(kosProfile);

        ValidationProfile madoProfile = new ValidationProfile();
        madoProfile.setProfileID("IHE.RAD.MADO");
        madoProfile.setProfileName("IHE MADO Manifest based Access to DICOM Objects");
        madoProfile.setDomain("RAD");
        madoProfile.setCoveredItems(Arrays.asList("DICOM MADO Manifest", "MADO Manifest based Access to DICOM Objects"));
        madoProfile.setStandards(Arrays.asList("DICOM", "IHE RAD TF-2", "IHE ITI TF-3"));
        madoProfile.setVersion("1.0");
        profiles.add(madoProfile);

        return ResponseEntity.ok(profiles);
    }

    @PostMapping(value = "/validate",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> validate(@RequestBody ValidationRequest request) {
        try {
            if (request == null || request.getValidationProfileId() == null) {
                return ResponseEntity.badRequest().body("Invalid request: validationProfileId is required");
            }

            String profileId = request.getValidationProfileId();
            ValidationReport report;

            if ("IHE.RAD.XDSI.KOS".equals(profileId)) {
                report = validateKOS(request);
            } else if ("IHE.RAD.MADO".equals(profileId)) {
                report = validateMADO(request);
            } else {
                return ResponseEntity.status(404).body("Unknown validation profile: " + profileId);
            }

            return ResponseEntity.ok(report);

        } catch (Exception e) {
            return ResponseEntity.status(500).body("Validation error: " + e.getMessage());
        }
    }

    private ValidationReport validateKOS(ValidationRequest request) throws Exception {
        ValidationReport report = baseReport("IHE.RAD.XDSI.KOS", "IHE XDS-I.b Key Object Selection", "1.0");

        Attributes attrs;
        if (request.getInputs() == null || request.getInputs().isEmpty()) {
            IHEKOSSampleCreator.Options options = new IHEKOSSampleCreator.Options();
            attrs = IHEKOSSampleCreator.createRandomIHEKOS(options);

            Input generatedInput = new Input();
            generatedInput.setId("generated_kos");
            generatedInput.setItemId("generated");
            report.getInputs().add(generatedInput);
        } else {
            Input input = request.getInputs().get(0);
            byte[] dicomBytes = Base64.getDecoder().decode(input.getContent());

            File tempFile = File.createTempFile("kos_validate_", ".dcm");
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(dicomBytes);
            }

            ValidationResult validationResult = CLIDICOMVerify.validateFile(tempFile, "IHEXDSIManifest");
            //noinspection ResultOfMethodCallIgnored
            tempFile.delete();

            convertValidationResultToReport(validationResult, report);
            report.getInputs().add(input);
            return report;
        }

        File tempFile = File.createTempFile("kos_generated_", ".dcm");
        try (DicomOutputStream dos = new DicomOutputStream(tempFile)) {
            dos.writeDataset(null, attrs);
        }

        ValidationResult validationResult = CLIDICOMVerify.validateFile(tempFile, "IHEXDSIManifest");
        //noinspection ResultOfMethodCallIgnored
        tempFile.delete();

        convertValidationResultToReport(validationResult, report);
        return report;
    }

    private ValidationReport validateMADO(ValidationRequest request) throws Exception {
        ValidationReport report = baseReport("IHE.RAD.MADO", "IHE Manifest with Descriptors", "1.0");

        Attributes attrs;
        if (request.getInputs() == null || request.getInputs().isEmpty()) {
            MADOOptions options = new MADOOptions();
            attrs = IHEMADOSampleCreator.createMADOFromOptions(options);

            Input generatedInput = new Input();
            generatedInput.setId("generated_mado");
            generatedInput.setItemId("generated");
            report.getInputs().add(generatedInput);
        } else {
            Input input = request.getInputs().get(0);
            byte[] dicomBytes = Base64.getDecoder().decode(input.getContent());

            File tempFile = File.createTempFile("mado_validate_", ".dcm");
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(dicomBytes);
            }

            ValidationResult validationResult = CLIDICOMVerify.validateFile(tempFile, "IHEMADO");
            //noinspection ResultOfMethodCallIgnored
            tempFile.delete();

            convertValidationResultToReport(validationResult, report);
            report.getInputs().add(input);
            return report;
        }

        File tempFile = File.createTempFile("mado_generated_", ".dcm");
        try (DicomOutputStream dos = new DicomOutputStream(tempFile)) {
            dos.writeDataset(null, attrs);
        }

        ValidationResult validationResult = CLIDICOMVerify.validateFile(tempFile, "IHEMADO");
        //noinspection ResultOfMethodCallIgnored
        tempFile.delete();

        convertValidationResultToReport(validationResult, report);
        return report;
    }

    private ValidationReport baseReport(String profileId, String profileName, String profileVersion) {
        ValidationReport report = new ValidationReport();
        report.setUuid(UUID.randomUUID().toString());
        report.setDateTime(getCurrentTimestamp());
        report.setDisclaimer("This validation report is generated by " + SERVICE_NAME + " and is provided as-is.");

        ValidationMethod method = new ValidationMethod();
        method.setValidationServiceName(SERVICE_NAME);
        method.setValidationServiceVersion(SERVICE_VERSION);
        method.setValidationProfileID(profileId);
        method.setValidationProfileName(profileName);
        method.setValidationProfileVersion(profileVersion);
        report.setValidationMethod(method);

        return report;
    }

    private void convertValidationResultToReport(ValidationResult validationResult, ValidationReport report) {
        ValidationSubReport subReport = new ValidationSubReport();
        subReport.setName("DICOM IOD Validation");
        subReport.setStandards(Arrays.asList("DICOM PS3.3", "IHE RAD TF-2"));

        int failedWithErrors = 0;
        int failedWithWarnings = 0;
        int totalAssertions = 0;

        if (validationResult.getMessages() != null) {
            for (be.uzleuven.ihe.dicom.validator.model.ValidationResult.ValidationMessage msg : validationResult.getMessages()) {
                AssertionReport assertion = new AssertionReport();

                String path = msg.getPath();
                if (path != null && !path.trim().isEmpty()) {
                    assertion.setDescription(path + ": " + msg.getMessage());
                } else {
                    assertion.setDescription(msg.getMessage());
                }

                be.uzleuven.ihe.dicom.validator.model.ValidationResult.Severity severity = msg.getSeverity();
                if (severity == be.uzleuven.ihe.dicom.validator.model.ValidationResult.Severity.ERROR) {
                    assertion.setResult("FAILED");
                    assertion.setSeverity("ERROR");
                    assertion.setPriority("MANDATORY");
                    failedWithErrors++;
                } else if (severity == be.uzleuven.ihe.dicom.validator.model.ValidationResult.Severity.WARNING) {
                    assertion.setResult("FAILED");
                    assertion.setSeverity("WARNING");
                    assertion.setPriority("RECOMMENDED");
                    failedWithWarnings++;
                } else {
                    assertion.setResult("PASSED");
                    assertion.setSeverity("INFO");
                    assertion.setPriority("MANDATORY");
                }

                totalAssertions++;
                subReport.getAssertionReports().add(assertion);
            }
        }

        if (totalAssertions == 0) {
            AssertionReport assertion = new AssertionReport();
            assertion.setDescription("DICOM file structure is valid");
            assertion.setResult("PASSED");
            assertion.setSeverity("INFO");
            assertion.setPriority("MANDATORY");
            subReport.getAssertionReports().add(assertion);
            totalAssertions++;
        }

        if (failedWithErrors > 0) {
            subReport.setSubReportResult("FAILED");
            report.setOverallResult("FAILED");
        } else {
            subReport.setSubReportResult("PASSED");
            report.setOverallResult("PASSED");
        }

        ValidationCounters subCounters = subReport.getSubCounters();
        subCounters.setNumberOfAssertions(totalAssertions);
        subCounters.setNumberOfFailedWithErrors(failedWithErrors);
        subCounters.setNumberOfFailedWithWarnings(failedWithWarnings);

        ValidationCounters mainCounters = report.getCounters();
        mainCounters.setNumberOfAssertions(totalAssertions);
        mainCounters.setNumberOfFailedWithErrors(failedWithErrors);
        mainCounters.setNumberOfFailedWithWarnings(failedWithWarnings);

        report.getReports().add(subReport);
    }

    private String getCurrentTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        return sdf.format(new Date());
    }
}
