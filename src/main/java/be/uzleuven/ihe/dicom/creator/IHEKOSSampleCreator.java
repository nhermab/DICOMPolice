package be.uzleuven.ihe.dicom.creator;

import org.dcm4che3.data.*;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.util.UIDUtils;

import java.io.File;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;

public class IHEKOSSampleCreator {

    private static final SecureRandom RND = new SecureRandom();

    public static void main(String[] args) throws Exception {
        int count = 1;
        if (args != null && args.length > 0) {
            try {
                count = Math.max(1, Integer.parseInt(args[0]));
            } catch (NumberFormatException ignore) {
                // ignore
            }
        }

        File outDir = new File(System.getProperty("user.dir"));
        for (int i = 0; i < count; i++) {
            Attributes kos = createRandomIHEKOS();
            // Keep ourselves honest: never write a KOS with empty required sequences.
            assertNotEmptySequence(kos, Tag.CurrentRequestedProcedureEvidenceSequence, "CurrentRequestedProcedureEvidenceSequence");
            assertNotEmptySequence(kos, Tag.ConceptNameCodeSequence, "ConceptNameCodeSequence");
            assertNotEmptySequence(kos, Tag.ContentTemplateSequence, "ContentTemplateSequence");

            File out = new File(outDir, "IHEKOS_" +  ".dcm");
            writePart10(out, kos);
            System.out.println("Wrote: " + out.getAbsolutePath());
        }
    }

    private static Attributes createRandomIHEKOS() {
        Attributes d = new Attributes();

        // --- SOP Common ---
        d.setString(Tag.SOPClassUID, VR.UI, UID.KeyObjectSelectionDocumentStorage);
        String kosSopInstanceUid = UIDUtils.createUID();
        d.setString(Tag.SOPInstanceUID, VR.UI, kosSopInstanceUid);

        // --- Patient IE (Patient Module: Type 2 in DICOM, but we populate) ---
        d.setString(Tag.PatientName, VR.PN, randomPersonName());
        d.setString(Tag.PatientID, VR.LO, randomPatientId());
        d.setString(Tag.IssuerOfPatientID, VR.LO, randomIssuer());
        d.setString(Tag.PatientBirthDate, VR.DA, randomDateYYYYMMDD(1940, 2015));
        d.setString(Tag.PatientSex, VR.CS, randomFrom("M", "F", "O"));

        // --- Study IE (General Study) ---
        String studyInstanceUid = UIDUtils.createUID();
        d.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUid);
        d.setString(Tag.StudyID, VR.SH, String.format("%04d", 1 + RND.nextInt(9999)));
        d.setString(Tag.StudyDate, VR.DA, todayYYYYMMDD());
        d.setString(Tag.StudyTime, VR.TM, nowHHMMSS());
        String accessionNumber = randomAccession();
        d.setString(Tag.AccessionNumber, VR.SH, accessionNumber);
        d.setString(Tag.ReferringPhysicianName, VR.PN, randomPersonName());

        // --- Series IE (Key Object Document Series) ---
        d.setString(Tag.Modality, VR.CS, "KO");
        d.setString(Tag.SeriesInstanceUID, VR.UI, UIDUtils.createUID());
        d.setInt(Tag.SeriesNumber, VR.IS, 1 + RND.nextInt(99));
        // Type 2 sequence: include empty item to satisfy "present" semantics.
        d.newSequence(Tag.ReferencedPerformedProcedureStepSequence, 1).add(new Attributes());

        // --- Equipment IE ---
        d.setString(Tag.Manufacturer, VR.LO, randomFrom("ACME Imaging", "Globex Medical", "Initech", "Umbrella Radiology"));

        // --- Key Object Document Module ---
        d.setInt(Tag.InstanceNumber, VR.IS, 1 + RND.nextInt(999));
        d.setString(Tag.ContentDate, VR.DA, todayYYYYMMDD());
        d.setString(Tag.ContentTime, VR.TM, nowHHMMSS());

        // SR General / XDS-I profile requirements
        d.setString(Tag.CompletionFlag, VR.CS, "COMPLETE");
        d.setString(Tag.VerificationFlag, VR.CS, "UNVERIFIED");
        d.setString(Tag.TimezoneOffsetFromUTC, VR.SH, timezoneOffsetFromUTC());

        // ReferencedRequestSequence is Type 2 (must be present) and required by this validator
        populateReferencedRequest(d, studyInstanceUid, accessionNumber);

        // --- SR Document Content Module (Root container) ---
        d.setString(Tag.ValueType, VR.CS, "CONTAINER");
        d.setString(Tag.ContinuityOfContent, VR.CS, "SEPARATE");

        // Document Title: IHE XDS-I Imaging Manifest is typically (113030, DCM, "Manifest")
        // (Your validator accepts different titles for MADO vs XDS-I; for IHEKOS we use 113030.
        Sequence conceptName = d.newSequence(Tag.ConceptNameCodeSequence, 1);
        conceptName.add(code("113030", "DCM", "Manifest"));

        // ContentTemplateSequence identifies TID 2010 / DCMR
        Sequence cts = d.newSequence(Tag.ContentTemplateSequence, 1);
        Attributes ctsItem = new Attributes();
        ctsItem.setString(Tag.MappingResource, VR.CS, "DCMR");
        ctsItem.setString(Tag.TemplateIdentifier, VR.CS, "2010");
        // Defensive: some checkers treat missing Version as suspicious; provide a sane value.
        ctsItem.setString(Tag.TemplateVersion, VR.LO, "1");
        cts.add(ctsItem);

        // Build a single shared reference list so Evidence and ContentSequence stay consistent.
        java.util.List<Attributes> referencedSops = buildReferencedSops();

        // Evidence (Current Requested Procedure Evidence Sequence)
        populateEvidence(d, studyInstanceUid, referencedSops);

        // Content tree: build only from the same SOPs that appear in Evidence.
        populateContentTree(d, referencedSops);

        // Small extras that are commonly present
        d.setString(Tag.SpecificCharacterSet, VR.CS, "ISO_IR 100");

        return d;
    }

    private static void populateEvidence(Attributes d, String studyInstanceUid, java.util.List<Attributes> referencedSops) {
        Sequence evidence = d.newSequence(Tag.CurrentRequestedProcedureEvidenceSequence, 1);

        Attributes studyItem = new Attributes();
        studyItem.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUid);

        // Put everything into a single series to keep the sample simple and deterministic.
        Sequence refSeries = studyItem.newSequence(Tag.ReferencedSeriesSequence, 1);
        Attributes seriesItem = new Attributes();
        seriesItem.setString(Tag.SeriesInstanceUID, VR.UI, UIDUtils.createUID());

        Sequence refSops = seriesItem.newSequence(Tag.ReferencedSOPSequence, Math.max(1, referencedSops.size()));
        for (Attributes sop : referencedSops) {
            Attributes ref = new Attributes();
            // SRReferenceUtils expects ReferencedSOPClassUID + ReferencedSOPInstanceUID
            ref.setString(Tag.ReferencedSOPClassUID, VR.UI, sop.getString(Tag.ReferencedSOPClassUID));
            ref.setString(Tag.ReferencedSOPInstanceUID, VR.UI, sop.getString(Tag.ReferencedSOPInstanceUID));
            refSops.add(ref);
        }

        refSeries.add(seriesItem);
        evidence.add(studyItem);
    }

    private static void populateContentTree(Attributes d, java.util.List<Attributes> referencedSops) {
        Sequence content = d.newSequence(Tag.ContentSequence, 50);

        // NOTE: XDS-I manifest profile validation may enforce strict template conformance.
        // Keep this minimal with only IMAGE references.

        for (Attributes sop : referencedSops) {
            Attributes img = new Attributes();
            img.setString(Tag.RelationshipType, VR.CS, "CONTAINS");
            img.setString(Tag.ValueType, VR.CS, "IMAGE");

            // Concept name is required by structural validator (it checks not empty)
            img.newSequence(Tag.ConceptNameCodeSequence, 1).add(code("111030", "DCM", "Image"));

            Sequence ref = img.newSequence(Tag.ReferencedSOPSequence, 1);
            Attributes refItem = new Attributes();
            refItem.setString(Tag.ReferencedSOPClassUID, VR.UI, sop.getString(Tag.ReferencedSOPClassUID));
            refItem.setString(Tag.ReferencedSOPInstanceUID, VR.UI, sop.getString(Tag.ReferencedSOPInstanceUID));
            ref.add(refItem);

            content.add(img);
        }
    }

    private static void populateReferencedRequest(Attributes d, String studyInstanceUid, String accessionNumber) {
        Sequence rrs = d.newSequence(Tag.ReferencedRequestSequence, 1);
        Attributes item = new Attributes();
        item.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUid);
        item.setString(Tag.AccessionNumber, VR.SH, accessionNumber);
        // Common order numbers used by validators/profiles.
        item.setString(Tag.PlacerOrderNumberImagingServiceRequest, VR.LO, "PLC" + (100000 + RND.nextInt(900000)));
        item.setString(Tag.FillerOrderNumberImagingServiceRequest, VR.LO, "FIL" + (100000 + RND.nextInt(900000)));
        rrs.add(item);
    }

    private static java.util.List<Attributes> buildReferencedSops() {
        int sopCount = 6; // keep stable for repeatable validation output
        java.util.List<Attributes> sops = new java.util.ArrayList<>(sopCount);
        for (int i = 0; i < sopCount; i++) {
            Attributes sop = new Attributes();
            sop.setString(Tag.ReferencedSOPClassUID, VR.UI, randomFrom(
                    UID.CTImageStorage,
                    UID.MRImageStorage,
                    UID.DigitalXRayImageStorageForPresentation,
                    UID.UltrasoundImageStorage,
                    UID.SecondaryCaptureImageStorage
            ));
            sop.setString(Tag.ReferencedSOPInstanceUID, VR.UI, UIDUtils.createUID());
            sops.add(sop);
        }
        return sops;
    }

    private static void writePart10(File out, Attributes dataset) throws Exception {
        String tsuid = UID.ExplicitVRLittleEndian;

        try (DicomOutputStream dos = new DicomOutputStream(out)) {
            dos.writeDataset(dataset.createFileMetaInformation(tsuid), dataset);
        }
    }

    private static Attributes code(String value, String scheme, String meaning) {
        Attributes a = new Attributes();
        a.setString(Tag.CodeValue, VR.SH, value);
        a.setString(Tag.CodingSchemeDesignator, VR.SH, scheme);
        a.setString(Tag.CodeMeaning, VR.LO, meaning);
        return a;
    }

    private static String uniqueId() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new Date()) + "_" + (1000 + RND.nextInt(9000));
    }

    private static String randomPersonName() {
        String family = randomFrom("Smith", "Johnson", "Brown", "Taylor", "Anderson", "Thomas", "Jackson", "White", "Harris", "Martin");
        String given = randomFrom("Alex", "Sam", "Jordan", "Casey", "Taylor", "Morgan", "Riley", "Jamie", "Cameron", "Avery");
        return family + "^" + given;
    }

    private static String randomPatientId() {
        return "P" + (100000 + RND.nextInt(900000)) + "-" + (10 + RND.nextInt(90));
    }

    private static String randomIssuer() {
        return randomFrom("HOSPITAL_A", "HOSPITAL_B", "REGION_X", "NATIONAL_ID", "IHE_TEST");
    }

    private static String todayYYYYMMDD() {
        return new SimpleDateFormat("yyyyMMdd").format(new Date());
    }

    private static String nowHHMMSS() {
        return new SimpleDateFormat("HHmmss").format(new Date());
    }

    private static String randomDateYYYYMMDD(int startYearInclusive, int endYearInclusive) {
        int year = startYearInclusive + RND.nextInt(Math.max(1, endYearInclusive - startYearInclusive + 1));
        int month = 1 + RND.nextInt(12);
        int day = 1 + RND.nextInt(28);
        return String.format("%04d%02d%02d", year, month, day);
    }

    private static String randomAccession() {
        return "ACC" + (100000 + RND.nextInt(900000));
    }

    private static String randomParagraph() {
        return randomFrom(
                "Randomly generated IHE KOS manifest for testing. Contains a synthetic set of referenced instances.",
                "Synthetic manifest: references a randomized selection of objects from a single study for validation and pipeline testing.",
                "IHE KOS sample generated for validator checks. Use only for development and integration testing.",
                "This KOS document was generated with randomized header fields and referenced instances to simulate production diversity."
        );
    }

    private static String randomFrom(String... values) {
        return values[RND.nextInt(values.length)];
    }

    private static String timezoneOffsetFromUTC() {
        java.util.TimeZone tz = java.util.TimeZone.getDefault();
        int offsetMinutes = tz.getOffset(System.currentTimeMillis()) / 60000;
        char sign = offsetMinutes >= 0 ? '+' : '-';
        int abs = Math.abs(offsetMinutes);
        int hh = abs / 60;
        int mm = abs % 60;
        return String.format("%c%02d%02d", sign, hh, mm);
    }

    private static void assertNotEmptySequence(Attributes d, int tag, String name) {
        Sequence seq = d.getSequence(tag);
        if (seq == null || seq.isEmpty()) {
            throw new IllegalStateException(name + " is missing/empty in generated dataset");
        }
    }
}
