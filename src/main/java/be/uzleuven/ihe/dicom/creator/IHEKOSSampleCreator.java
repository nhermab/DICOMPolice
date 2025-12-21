package be.uzleuven.ihe.dicom.creator;

import be.uzleuven.ihe.dicom.constants.DicomConstants;
import be.uzleuven.ihe.dicom.constants.CodeConstants;
import org.dcm4che3.data.*;
import org.dcm4che3.util.UIDUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static be.uzleuven.ihe.dicom.creator.DicomCreatorUtils.*;
import static be.uzleuven.ihe.dicom.creator.DicomSequenceUtils.*;
import static be.uzleuven.ihe.dicom.creator.SRContentItemUtils.*;
import static be.uzleuven.ihe.dicom.constants.CodeConstants.*;

public class IHEKOSSampleCreator {


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

            File out = new File(outDir, "IHEKOS_" + i + ".dcm");
            writeDicomFile(out, kos);
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
        d.setString(Tag.StudyID, VR.SH, String.format("%04d", 1 + randomInt(9999)));
        d.setString(Tag.StudyDate, VR.DA, todayYYYYMMDD());
        d.setString(Tag.StudyTime, VR.TM, nowHHMMSS());
        String accessionNumber = randomAccession();
        d.setString(Tag.AccessionNumber, VR.SH, accessionNumber);
        d.setString(Tag.ReferringPhysicianName, VR.PN, randomPersonName());

        // --- Series IE (Key Object Document Series) ---
        d.setString(Tag.Modality, VR.CS, "KO");
        d.setString(Tag.SeriesInstanceUID, VR.UI, UIDUtils.createUID());
        d.setInt(Tag.SeriesNumber, VR.IS, 1 + randomInt(99));
        // Type 2 sequence: include empty item to satisfy "present" semantics.
        d.newSequence(Tag.ReferencedPerformedProcedureStepSequence, 1).add(new Attributes());

        // --- Equipment IE ---
        d.setString(Tag.Manufacturer, VR.LO, randomFrom("ACME Imaging", "Globex Medical", "Initech", "Umbrella Radiology"));

        // --- Key Object Document Module ---
        d.setInt(Tag.InstanceNumber, VR.IS, 1 + randomInt(999));
        d.setString(Tag.ContentDate, VR.DA, todayYYYYMMDD());
        d.setString(Tag.ContentTime, VR.TM, nowHHMMSS());

        // SR General / XDS-I profile requirements
        d.setString(Tag.CompletionFlag, VR.CS, DicomConstants.COMPLETION_FLAG_COMPLETE);
        d.setString(Tag.VerificationFlag, VR.CS, DicomConstants.VERIFICATION_FLAG_UNVERIFIED);
        d.setString(Tag.TimezoneOffsetFromUTC, VR.SH, timezoneOffsetFromUTC());

        // ReferencedRequestSequence is Type 2 (must be present) and required by this validator
        populateReferencedRequestSequence(d, studyInstanceUid, accessionNumber);

        // --- SR Document Content Module (Root container) ---
        d.setString(Tag.ValueType, VR.CS, "CONTAINER");
        d.setString(Tag.ContinuityOfContent, VR.CS, DicomConstants.CONTINUITY_SEPARATE);

        // Document Title: IHE XDS-I Imaging Manifest is typically (113030, DCM, "Manifest")
        // (Your validator accepts different titles for MADO vs XDS-I; for IHEKOS we use 113030.
        Sequence conceptName = d.newSequence(Tag.ConceptNameCodeSequence, 1);
        conceptName.add(code(CODE_MANIFEST, SCHEME_DCM, MEANING_MANIFEST));

        // ContentTemplateSequence identifies TID 2010 / DCMR
        Sequence cts = d.newSequence(Tag.ContentTemplateSequence, 1);
        cts.add(createTemplateItem("2010"));

        // Build a single shared reference list so Evidence and ContentSequence stay consistent.
        List<Attributes> referencedSops = buildReferencedSops();

        // Evidence (Current Requested Procedure Evidence Sequence)
        populateEvidence(d, studyInstanceUid, referencedSops);

        // Content tree: build only from the same SOPs that appear in Evidence.
        populateContentTree(d, referencedSops);

        // Small extras that are commonly present
        d.setString(Tag.SpecificCharacterSet, VR.CS, "ISO_IR 100");

        return d;
    }

    private static void populateContentTree(Attributes d, List<Attributes> referencedSops) {
        Sequence content = d.newSequence(Tag.ContentSequence, 50);

        // NOTE: XDS-I manifest profile validation may enforce strict template conformance.
        // Keep this minimal with only IMAGE references.

        for (Attributes sop : referencedSops) {
            Attributes img = createImageItem(DicomConstants.RELATIONSHIP_CONTAINS,
                                            code(CODE_IMAGE, SCHEME_DCM, MEANING_IMAGE),
                                            sop.getString(Tag.ReferencedSOPClassUID),
                                            sop.getString(Tag.ReferencedSOPInstanceUID));
            content.add(img);
        }
    }

    private static List<Attributes> buildReferencedSops() {
        int sopCount = 6; // keep stable for repeatable validation output
        List<Attributes> sops = new ArrayList<>(sopCount);
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
}
