package be.uzleuven.ihe.dicom.convertor.dicom;

import be.uzleuven.ihe.dicom.convertor.fhir.MADOToFHIRConverter;
import org.dcm4che3.data.*;
import org.dcm4che3.io.DicomInputStream;
import org.hl7.fhir.r5.model.*;

import java.io.File;
import java.util.*;

/**
 * Test and demonstration class for the FHIRToMADO converter.
 * Run this class to test the converter and verify round-trip fidelity.
 *
 * Tests verify that:
 * 1. DICOM MADO -> FHIR -> DICOM MADO preserves critical information
 * 2. Patient, Study, Series, and Instance data is maintained
 * 3. Evidence and Content sequences are properly reconstructed
 */
public class FHIRToMADOConverterTest {

    private final MADOToFHIRConverter toFhirConverter = new MADOToFHIRConverter();
    private final FHIRToMADOConverter toDicomConverter = new FHIRToMADOConverter();

    private int passCount = 0;
    private int failCount = 0;
    private final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("FHIRToMADO Converter Test Suite");
        System.out.println("=".repeat(60));
        System.out.println();

        FHIRToMADOConverterTest test = new FHIRToMADOConverterTest();

        // Run all tests
        test.runTests();

        // Print summary
        System.out.println();
        System.out.println("=".repeat(60));
        System.out.println("Test Results: " + test.passCount + " passed, " + test.failCount + " failed");
        if (!test.failures.isEmpty()) {
            System.out.println("\nFailed tests:");
            for (String failure : test.failures) {
                System.out.println("  - " + failure);
            }
        }
        System.out.println("=".repeat(60));

        System.exit(test.failCount > 0 ? 1 : 0);
    }

    public void runTests() {
        testConvertValidFhirBundle();
        testRejectNullBundle();
        testRejectNonDocumentBundle();
        testRejectBundleWithoutComposition();
        testRoundTripPatientData();
        testRoundTripStudyData();
        testRoundTripSeriesAndInstances();
        testRoundTripIssuerQualifiers();
        testRoundTripReferencedRequestSequence();
    }

    // ============================================================================
    // BASIC CONVERSION TESTS
    // ============================================================================

    private void testConvertValidFhirBundle() {
        try {
            Bundle bundle = createSampleBundle();
            Attributes dicom = toDicomConverter.convert(bundle);

            assertNotNull("DICOM result", dicom);
            assertEquals("SOP Class UID", "1.2.840.10008.5.1.4.1.1.88.59",
                dicom.getString(Tag.SOPClassUID));
            assertNotNull("SOP Instance UID", dicom.getString(Tag.SOPInstanceUID));
            assertNotNull("Study Instance UID", dicom.getString(Tag.StudyInstanceUID));
            assertNotNull("Patient ID", dicom.getString(Tag.PatientID));
            assertNotNull("Patient Name", dicom.getString(Tag.PatientName));

            assertEquals("Value Type", "CONTAINER", dicom.getString(Tag.ValueType));
            assertEquals("Continuity Of Content", "SEPARATE", dicom.getString(Tag.ContinuityOfContent));

            Sequence evidenceSeq = dicom.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence);
            assertNotNull("Evidence Sequence", evidenceSeq);
            assertTrue("Evidence Sequence not empty", !evidenceSeq.isEmpty());

            Sequence contentSeq = dicom.getSequence(Tag.ContentSequence);
            assertNotNull("Content Sequence", contentSeq);
            assertTrue("Content Sequence not empty", !contentSeq.isEmpty());

            pass("testConvertValidFhirBundle");
        } catch (Exception e) {
            fail("testConvertValidFhirBundle", e);
        }
    }

    private void testRejectNullBundle() {
        try {
            toDicomConverter.convert(null);
            fail("testRejectNullBundle", "Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            pass("testRejectNullBundle");
        } catch (Exception e) {
            fail("testRejectNullBundle", e);
        }
    }

    private void testRejectNonDocumentBundle() {
        try {
            Bundle bundle = new Bundle();
            bundle.setType(Bundle.BundleType.COLLECTION);
            toDicomConverter.convert(bundle);
            fail("testRejectNonDocumentBundle", "Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            pass("testRejectNonDocumentBundle");
        } catch (Exception e) {
            fail("testRejectNonDocumentBundle", e);
        }
    }

    private void testRejectBundleWithoutComposition() {
        try {
            Bundle bundle = new Bundle();
            bundle.setType(Bundle.BundleType.DOCUMENT);
            bundle.addEntry().setResource(new Patient());
            toDicomConverter.convert(bundle);
            fail("testRejectBundleWithoutComposition", "Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            pass("testRejectBundleWithoutComposition");
        } catch (Exception e) {
            fail("testRejectBundleWithoutComposition", e);
        }
    }

    // ============================================================================
    // ROUND-TRIP TESTS WITH SYNTHETIC DATA
    // ============================================================================

    private void testRoundTripPatientData() {
        try {
            Attributes original = createMinimalMADO();
            original.setString(Tag.PatientID, VR.LO, "TEST-PATIENT-123");
            original.setString(Tag.PatientName, VR.PN, "Doe^John^Q");
            original.setString(Tag.PatientBirthDate, VR.DA, "19800515");
            original.setString(Tag.PatientSex, VR.CS, "M");

            Bundle fhir = toFhirConverter.convert(original);
            Attributes roundTripped = toDicomConverter.convert(fhir);

            assertEquals("Patient ID", "TEST-PATIENT-123", roundTripped.getString(Tag.PatientID));
            assertTrue("Patient Name starts with Doe^John",
                roundTripped.getString(Tag.PatientName).startsWith("Doe^John"));
            assertEquals("Patient Birth Date", "19800515", roundTripped.getString(Tag.PatientBirthDate));
            assertEquals("Patient Sex", "M", roundTripped.getString(Tag.PatientSex));

            pass("testRoundTripPatientData");
        } catch (Exception e) {
            fail("testRoundTripPatientData", e);
        }
    }

    private void testRoundTripStudyData() {
        try {
            Attributes original = createMinimalMADO();
            String studyUID = "1.2.3.4.5.6.7.8.9";
            original.setString(Tag.StudyInstanceUID, VR.UI, studyUID);
            original.setString(Tag.StudyDate, VR.DA, "20250107");
            original.setString(Tag.StudyTime, VR.TM, "143022");
            original.setString(Tag.StudyDescription, VR.LO, "CT Abdomen with Contrast");
            original.setString(Tag.AccessionNumber, VR.SH, "ACC-98765");

            Bundle fhir = toFhirConverter.convert(original);
            Attributes roundTripped = toDicomConverter.convert(fhir);

            assertEquals("Study Instance UID", studyUID, roundTripped.getString(Tag.StudyInstanceUID));
            assertEquals("Study Date", "20250107", roundTripped.getString(Tag.StudyDate));
            assertEquals("Study Description", "CT Abdomen with Contrast",
                roundTripped.getString(Tag.StudyDescription));
            assertEquals("Accession Number", "ACC-98765", roundTripped.getString(Tag.AccessionNumber));

            pass("testRoundTripStudyData");
        } catch (Exception e) {
            fail("testRoundTripStudyData", e);
        }
    }

    private void testRoundTripSeriesAndInstances() {
        try {
            Attributes original = createMADOWithEvidence();

            Bundle fhir = toFhirConverter.convert(original);
            Attributes roundTripped = toDicomConverter.convert(fhir);

            Sequence origEvidence = original.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence);
            Sequence rtEvidence = roundTripped.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence);

            assertNotNull("Round-tripped Evidence Sequence", rtEvidence);
            assertEquals("Evidence Sequence size", origEvidence.size(), rtEvidence.size());

            Set<String> origSeriesUIDs = extractSeriesUIDs(origEvidence);
            Set<String> rtSeriesUIDs = extractSeriesUIDs(rtEvidence);
            assertEquals("Series UIDs", origSeriesUIDs.toString(), rtSeriesUIDs.toString());

            Set<String> origInstanceUIDs = extractInstanceUIDs(origEvidence);
            Set<String> rtInstanceUIDs = extractInstanceUIDs(rtEvidence);
            assertEquals("Instance UIDs", origInstanceUIDs.toString(), rtInstanceUIDs.toString());

            pass("testRoundTripSeriesAndInstances");
        } catch (Exception e) {
            fail("testRoundTripSeriesAndInstances", e);
        }
    }

    private void testRoundTripIssuerQualifiers() {
        try {
            Attributes original = createMinimalMADO();
            String patientOID = "1.2.3.4.5.6.7.8.9.10";

            Sequence qualSeq = original.newSequence(Tag.IssuerOfPatientIDQualifiersSequence, 1);
            Attributes qual = new Attributes();
            qual.setString(Tag.UniversalEntityID, VR.UT, patientOID);
            qual.setString(Tag.UniversalEntityIDType, VR.CS, "ISO");
            qualSeq.add(qual);

            Bundle fhir = toFhirConverter.convert(original);
            Attributes roundTripped = toDicomConverter.convert(fhir);

            Sequence rtQualSeq = roundTripped.getSequence(Tag.IssuerOfPatientIDQualifiersSequence);
            assertNotNull("Issuer Qualifiers Sequence", rtQualSeq);
            assertTrue("Issuer Qualifiers not empty", !rtQualSeq.isEmpty());
            assertEquals("Patient OID", patientOID, rtQualSeq.get(0).getString(Tag.UniversalEntityID));

            pass("testRoundTripIssuerQualifiers");
        } catch (Exception e) {
            fail("testRoundTripIssuerQualifiers", e);
        }
    }

    private void testRoundTripReferencedRequestSequence() {
        try {
            Attributes original = createMinimalMADO();
            String accessionNumber = "ACC-12345";
            String issuerOID = "1.2.3.4.5.6.7.8.9.11";

            Sequence refReqSeq = original.newSequence(Tag.ReferencedRequestSequence, 1);
            Attributes reqItem = new Attributes();
            reqItem.setString(Tag.AccessionNumber, VR.SH, accessionNumber);
            reqItem.setString(Tag.StudyInstanceUID, VR.UI, original.getString(Tag.StudyInstanceUID));

            Sequence issuerSeq = reqItem.newSequence(Tag.IssuerOfAccessionNumberSequence, 1);
            Attributes issuer = new Attributes();
            issuer.setString(Tag.UniversalEntityID, VR.UT, issuerOID);
            issuer.setString(Tag.UniversalEntityIDType, VR.CS, "ISO");
            issuerSeq.add(issuer);

            refReqSeq.add(reqItem);

            Bundle fhir = toFhirConverter.convert(original);
            Attributes roundTripped = toDicomConverter.convert(fhir);

            Sequence rtRefReqSeq = roundTripped.getSequence(Tag.ReferencedRequestSequence);
            assertNotNull("Referenced Request Sequence", rtRefReqSeq);
            assertTrue("Referenced Request Sequence not empty", !rtRefReqSeq.isEmpty());

            boolean foundAccession = false;
            for (Attributes req : rtRefReqSeq) {
                if (accessionNumber.equals(req.getString(Tag.AccessionNumber))) {
                    foundAccession = true;
                    break;
                }
            }
            assertTrue("Accession number found", foundAccession);

            pass("testRoundTripReferencedRequestSequence");
        } catch (Exception e) {
            fail("testRoundTripReferencedRequestSequence", e);
        }
    }

    // ============================================================================
    // ASSERTION HELPERS
    // ============================================================================

    private void pass(String testName) {
        passCount++;
        System.out.println("✓ " + testName);
    }

    private void fail(String testName, Exception e) {
        failCount++;
        failures.add(testName + ": " + e.getMessage());
        System.out.println("✗ " + testName + ": " + e.getMessage());
    }

    private void fail(String testName, String message) {
        failCount++;
        failures.add(testName + ": " + message);
        System.out.println("✗ " + testName + ": " + message);
    }

    private void assertNotNull(String name, Object value) {
        if (value == null) {
            throw new AssertionError(name + " should not be null");
        }
    }

    private void assertEquals(String name, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(name + ": expected " + expected + " but got " + actual);
        }
    }

    private void assertEquals(String name, int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError(name + ": expected " + expected + " but got " + actual);
        }
    }

    private void assertTrue(String message, boolean condition) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    private Bundle createSampleBundle() {
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.DOCUMENT);
        bundle.setIdentifier(new Identifier()
            .setSystem("urn:dicom:uid")
            .setValue("ihe:urn:oid:1.2.3.4.5.6.7.8.9"));
        bundle.setTimestamp(new Date());

        // Composition (required, must be first)
        Composition composition = new Composition();
        composition.setId(UUID.randomUUID().toString());
        composition.setStatus(Enumerations.CompositionStatus.FINAL);
        composition.setType(new CodeableConcept()
            .addCoding(new Coding()
                .setSystem("http://loinc.org")
                .setCode("18748-4")
                .setDisplay("Diagnostic imaging study")));
        composition.addIdentifier(new Identifier()
            .setSystem("urn:dicom:uid")
            .setValue("ihe:urn:oid:1.2.3.4.5.6.7.8.9"));
        composition.setDate(new Date());
        composition.setTitle("Test MADO Manifest");

        bundle.addEntry()
            .setFullUrl("urn:uuid:" + composition.getId())
            .setResource(composition);

        // Patient (required)
        Patient patient = new Patient();
        patient.setId(UUID.randomUUID().toString());
        patient.addIdentifier()
            .setSystem("urn:oid:1.2.3.4.5.6.7")
            .setValue("PAT-001");
        patient.addName()
            .setFamily("Test")
            .addGiven("Patient");

        bundle.addEntry()
            .setFullUrl("urn:uuid:" + patient.getId())
            .setResource(patient);

        composition.addSubject(new Reference("urn:uuid:" + patient.getId()));

        // ImagingStudy (required)
        ImagingStudy study = new ImagingStudy();
        study.setId(UUID.randomUUID().toString());
        study.setStatus(ImagingStudy.ImagingStudyStatus.AVAILABLE);
        study.addIdentifier()
            .setSystem("urn:dicom:uid")
            .setValue("ihe:urn:oid:1.2.3.4.5.6.7.8.10");
        study.setSubject(new Reference("urn:uuid:" + patient.getId()));
        study.setStarted(new Date());

        // Add a series
        ImagingStudy.ImagingStudySeriesComponent series = study.addSeries();
        series.setUid("1.2.3.4.5.6.7.8.11");
        series.setModality(new CodeableConcept()
            .addCoding(new Coding()
                .setSystem("http://dicom.nema.org/resources/ontology/DCM")
                .setCode("CT")));
        series.setNumber(1);

        // Add an instance
        ImagingStudy.ImagingStudySeriesInstanceComponent instance = series.addInstance();
        instance.setUid("1.2.3.4.5.6.7.8.12");
        instance.setSopClass(new Coding()
            .setSystem("urn:ietf:rfc:3986")
            .setCode("ihe:urn:oid:1.2.840.10008.5.1.4.1.1.2"));
        instance.setNumber(1);

        bundle.addEntry()
            .setFullUrl("urn:uuid:" + study.getId())
            .setResource(study);

        return bundle;
    }

    private Attributes createMinimalMADO() {
        Attributes mado = new Attributes();

        // SOP Common
        mado.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.88.59");
        mado.setString(Tag.SOPInstanceUID, VR.UI, "1.2.3.4.5.6.7.8.1");
        mado.setString(Tag.SpecificCharacterSet, VR.CS, "ISO_IR 192");

        // Patient
        mado.setString(Tag.PatientID, VR.LO, "PAT-001");
        mado.setString(Tag.PatientName, VR.PN, "Test^Patient");
        mado.setString(Tag.PatientBirthDate, VR.DA, "19900101");
        mado.setString(Tag.PatientSex, VR.CS, "O");

        // Study
        mado.setString(Tag.StudyInstanceUID, VR.UI, "1.2.3.4.5.6.7.8.2");
        mado.setString(Tag.StudyDate, VR.DA, "20250107");
        mado.setString(Tag.StudyTime, VR.TM, "120000");
        mado.setString(Tag.StudyID, VR.SH, "1");
        mado.setString(Tag.AccessionNumber, VR.SH, "ACC-001");

        // Series
        mado.setString(Tag.SeriesInstanceUID, VR.UI, "1.2.3.4.5.6.7.8.3");
        mado.setString(Tag.Modality, VR.CS, "KO");
        mado.setString(Tag.SeriesNumber, VR.IS, "1");

        // Equipment
        mado.setString(Tag.Manufacturer, VR.LO, "Test Manufacturer");

        // SR Document
        mado.setString(Tag.InstanceNumber, VR.IS, "1");
        mado.setString(Tag.ContentDate, VR.DA, "20250107");
        mado.setString(Tag.ContentTime, VR.TM, "120000");
        mado.setString(Tag.ValueType, VR.CS, "CONTAINER");
        mado.setString(Tag.ContinuityOfContent, VR.CS, "SEPARATE");

        // Concept Name (Manifest)
        Sequence conceptNameSeq = mado.newSequence(Tag.ConceptNameCodeSequence, 1);
        Attributes conceptName = new Attributes();
        conceptName.setString(Tag.CodeValue, VR.SH, "ddd001");
        conceptName.setString(Tag.CodingSchemeDesignator, VR.SH, "DCM");
        conceptName.setString(Tag.CodeMeaning, VR.LO, "Manifest with Description");
        conceptNameSeq.add(conceptName);

        // Evidence Sequence (minimal)
        Sequence evidenceSeq = mado.newSequence(Tag.CurrentRequestedProcedureEvidenceSequence, 1);
        Attributes studyItem = new Attributes();
        studyItem.setString(Tag.StudyInstanceUID, VR.UI, "1.2.3.4.5.6.7.8.2");
        Sequence refSeriesSeq = studyItem.newSequence(Tag.ReferencedSeriesSequence, 1);
        Attributes seriesItem = new Attributes();
        seriesItem.setString(Tag.SeriesInstanceUID, VR.UI, "1.2.3.4.5.6.7.8.4");
        seriesItem.setString(Tag.Modality, VR.CS, "CT");
        Sequence refSopSeq = seriesItem.newSequence(Tag.ReferencedSOPSequence, 1);
        Attributes sopItem = new Attributes();
        sopItem.setString(Tag.ReferencedSOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.2");
        sopItem.setString(Tag.ReferencedSOPInstanceUID, VR.UI, "1.2.3.4.5.6.7.8.5");
        refSopSeq.add(sopItem);
        refSeriesSeq.add(seriesItem);
        evidenceSeq.add(studyItem);

        // Content Sequence (minimal)
        Sequence contentSeq = mado.newSequence(Tag.ContentSequence, 3);

        // Modality code item
        Attributes modalityItem = new Attributes();
        modalityItem.setString(Tag.RelationshipType, VR.CS, "CONTAINS");
        modalityItem.setString(Tag.ValueType, VR.CS, "CODE");
        Sequence modalityConceptSeq = modalityItem.newSequence(Tag.ConceptNameCodeSequence, 1);
        Attributes modalityConcept = new Attributes();
        modalityConcept.setString(Tag.CodeValue, VR.SH, "121139");
        modalityConcept.setString(Tag.CodingSchemeDesignator, VR.SH, "DCM");
        modalityConcept.setString(Tag.CodeMeaning, VR.LO, "Modality");
        modalityConceptSeq.add(modalityConcept);
        Sequence modalityCodeSeq = modalityItem.newSequence(Tag.ConceptCodeSequence, 1);
        Attributes modalityCode = new Attributes();
        modalityCode.setString(Tag.CodeValue, VR.SH, "CT");
        modalityCode.setString(Tag.CodingSchemeDesignator, VR.SH, "DCM");
        modalityCode.setString(Tag.CodeMeaning, VR.LO, "CT");
        modalityCodeSeq.add(modalityCode);
        contentSeq.add(modalityItem);

        // Study Instance UID item
        Attributes studyUidItem = new Attributes();
        studyUidItem.setString(Tag.RelationshipType, VR.CS, "CONTAINS");
        studyUidItem.setString(Tag.ValueType, VR.CS, "UIDREF");
        Sequence studyUidConceptSeq = studyUidItem.newSequence(Tag.ConceptNameCodeSequence, 1);
        Attributes studyUidConcept = new Attributes();
        studyUidConcept.setString(Tag.CodeValue, VR.SH, "ddd011");
        studyUidConcept.setString(Tag.CodingSchemeDesignator, VR.SH, "DCM");
        studyUidConcept.setString(Tag.CodeMeaning, VR.LO, "Study Instance UID");
        studyUidConceptSeq.add(studyUidConcept);
        studyUidItem.setString(Tag.UID, VR.UI, "1.2.3.4.5.6.7.8.2");
        contentSeq.add(studyUidItem);

        // Image Library container (minimal)
        Attributes imageLibrary = new Attributes();
        imageLibrary.setString(Tag.RelationshipType, VR.CS, "CONTAINS");
        imageLibrary.setString(Tag.ValueType, VR.CS, "CONTAINER");
        Sequence imageLibConceptSeq = imageLibrary.newSequence(Tag.ConceptNameCodeSequence, 1);
        Attributes imageLibConcept = new Attributes();
        imageLibConcept.setString(Tag.CodeValue, VR.SH, "111028");
        imageLibConcept.setString(Tag.CodingSchemeDesignator, VR.SH, "DCM");
        imageLibConcept.setString(Tag.CodeMeaning, VR.LO, "Image Library");
        imageLibConceptSeq.add(imageLibConcept);
        imageLibrary.newSequence(Tag.ContentSequence, 0);
        contentSeq.add(imageLibrary);

        return mado;
    }

    private Attributes createMADOWithEvidence() {
        Attributes mado = createMinimalMADO();

        Sequence evidenceSeq = mado.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence);
        if (evidenceSeq != null && !evidenceSeq.isEmpty()) {
            Attributes studyItem = evidenceSeq.get(0);
            Sequence refSeriesSeq = studyItem.getSequence(Tag.ReferencedSeriesSequence);

            // Add a second series
            Attributes series2 = new Attributes();
            series2.setString(Tag.SeriesInstanceUID, VR.UI, "1.2.3.4.5.6.7.8.100");
            series2.setString(Tag.Modality, VR.CS, "MR");

            Sequence refSopSeq2 = series2.newSequence(Tag.ReferencedSOPSequence, 2);

            Attributes sop1 = new Attributes();
            sop1.setString(Tag.ReferencedSOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.4");
            sop1.setString(Tag.ReferencedSOPInstanceUID, VR.UI, "1.2.3.4.5.6.7.8.101");
            refSopSeq2.add(sop1);

            Attributes sop2 = new Attributes();
            sop2.setString(Tag.ReferencedSOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.4");
            sop2.setString(Tag.ReferencedSOPInstanceUID, VR.UI, "1.2.3.4.5.6.7.8.102");
            refSopSeq2.add(sop2);

            refSeriesSeq.add(series2);
        }

        return mado;
    }

    private Set<String> extractSeriesUIDs(Sequence evidenceSeq) {
        Set<String> uids = new HashSet<>();
        for (Attributes studyItem : evidenceSeq) {
            Sequence refSeriesSeq = studyItem.getSequence(Tag.ReferencedSeriesSequence);
            if (refSeriesSeq != null) {
                for (Attributes seriesItem : refSeriesSeq) {
                    String uid = seriesItem.getString(Tag.SeriesInstanceUID);
                    if (uid != null) uids.add(uid);
                }
            }
        }
        return uids;
    }

    private Set<String> extractInstanceUIDs(Sequence evidenceSeq) {
        Set<String> uids = new HashSet<>();
        for (Attributes studyItem : evidenceSeq) {
            Sequence refSeriesSeq = studyItem.getSequence(Tag.ReferencedSeriesSequence);
            if (refSeriesSeq != null) {
                for (Attributes seriesItem : refSeriesSeq) {
                    Sequence refSopSeq = seriesItem.getSequence(Tag.ReferencedSOPSequence);
                    if (refSopSeq != null) {
                        for (Attributes sopItem : refSopSeq) {
                            String uid = sopItem.getString(Tag.ReferencedSOPInstanceUID);
                            if (uid != null) uids.add(uid);
                        }
                    }
                }
            }
        }
        return uids;
    }
}



