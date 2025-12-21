package be.uzleuven.ihe.dicom.creator.evil;

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

/**
 * EVIL generator: creates KOS documents that randomly omit creation steps or inject wrong tags/values.
 *
 * This is intentionally isolated from the normal generators.
 */
public class EVILKOSCreator {

    private static final double SKIP_STEP_P = 0.20;
    private static final double CORRUPT_P = 0.05;

    public static void main(String[] args) throws Exception {
        int count = 1;
        if (args != null && args.length > 0) {
            try {
                count = Math.max(1, Integer.parseInt(args[0]));
            } catch (NumberFormatException ignore) {
            }
        }

        File outDir = new File(System.getProperty("user.dir"));
        for (int i = 0; i < count; i++) {
            Attributes kos = createRandomEvilKOS();
            File out = new File(outDir, "EVIL_IHEKOS_" + i + "_" + EvilDice.token(6) + ".dcm");
            writeDicomFile(out, kos);
            System.out.println("Wrote: " + out.getAbsolutePath());
        }
    }

    public static Attributes createRandomEvilKOS() {
        Attributes d = new Attributes();

        // Always writeable Part 10: keep SOP Class/Instance set.
        d.setString(Tag.SOPClassUID, VR.UI, UID.KeyObjectSelectionDocumentStorage);
        d.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());

        // --- Patient IE ---
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.PatientName, VR.PN, randomPersonName());
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.PatientID, VR.LO, randomPatientId());
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.IssuerOfPatientID, VR.LO, randomIssuer());
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.PatientBirthDate, VR.DA, randomDateYYYYMMDD(1940, 2015));
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.PatientSex, VR.CS, randomFrom("M", "F", "O"));

        // --- Study IE ---
        String studyInstanceUid = UIDUtils.createUID();
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUid);
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.StudyID, VR.SH, String.format("%04d", 1 + randomInt(9999)));
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.StudyDate, VR.DA, todayYYYYMMDD());
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.StudyTime, VR.TM, nowHHMMSS());
        String accessionNumber = randomAccession();
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.AccessionNumber, VR.SH, accessionNumber);
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.ReferringPhysicianName, VR.PN, randomPersonName());

        // --- Series IE ---
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.Modality, VR.CS, "KO");
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.SeriesInstanceUID, VR.UI, UIDUtils.createUID());
        if (!EvilDice.chance(SKIP_STEP_P)) d.setInt(Tag.SeriesNumber, VR.IS, 1 + randomInt(99));
        if (!EvilDice.chance(SKIP_STEP_P)) {
            d.newSequence(Tag.ReferencedPerformedProcedureStepSequence, 1).add(new Attributes());
        }

        // --- Equipment ---
        if (!EvilDice.chance(SKIP_STEP_P)) {
            d.setString(Tag.Manufacturer, VR.LO, randomFrom("ACME Imaging", "Globex Medical", "Initech", "Umbrella Radiology"));
        }

        // --- Key Object Document Module / SR general ---
        if (!EvilDice.chance(SKIP_STEP_P)) d.setInt(Tag.InstanceNumber, VR.IS, 1 + randomInt(999));
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.ContentDate, VR.DA, todayYYYYMMDD());
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.ContentTime, VR.TM, nowHHMMSS());
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.CompletionFlag, VR.CS, DicomConstants.COMPLETION_FLAG_COMPLETE);
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.VerificationFlag, VR.CS, DicomConstants.VERIFICATION_FLAG_UNVERIFIED);
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.TimezoneOffsetFromUTC, VR.SH, timezoneOffsetFromUTC());

        if (!EvilDice.chance(SKIP_STEP_P)) {
            // ReferencedRequestSequence is Type 2; EVIL may omit it.
            populateReferencedRequestSequence(d, studyInstanceUid, accessionNumber);
        }

        // --- SR Document Content Module (Root container) ---
        if (!EvilDice.chance(SKIP_STEP_P)) {
            d.setString(Tag.ValueType, VR.CS, "CONTAINER");
            d.setString(Tag.ContinuityOfContent, VR.CS, DicomConstants.CONTINUITY_SEPARATE);
        }

        if (!EvilDice.chance(SKIP_STEP_P)) {
            Sequence conceptName = d.newSequence(Tag.ConceptNameCodeSequence, 1);
            conceptName.add(code(CODE_MANIFEST, SCHEME_DCM, MEANING_MANIFEST));
        }

        if (!EvilDice.chance(SKIP_STEP_P)) {
            Sequence cts = d.newSequence(Tag.ContentTemplateSequence, 1);
            cts.add(createTemplateItem("2010"));
        }

        // Build reference list
        List<Attributes> referencedSops = buildReferencedSops();

        if (!EvilDice.chance(SKIP_STEP_P)) {
            populateEvidence(d, studyInstanceUid, referencedSops);
        }

        if (!EvilDice.chance(SKIP_STEP_P)) {
            populateContentTree(d, referencedSops);
        }

        if (!EvilDice.chance(SKIP_STEP_P)) {
            d.setString(Tag.SpecificCharacterSet, VR.CS, "ISO_IR 100");
        }

        // 5% chance: inject a corruption
        if (EvilDice.chance(CORRUPT_P)) {
            EvilMutator.corruptOne(d);
        }

        // 30% chance: add forbidden tags (makes it more evil!)
        ForbiddenTags.addRandomForbiddenTags(d, 0.30);

        return d;
    }

    private static void populateContentTree(Attributes d, List<Attributes> referencedSops) {
        Sequence content = d.newSequence(Tag.ContentSequence, 50);
        for (Attributes sop : referencedSops) {
            Attributes img = createImageItem(DicomConstants.RELATIONSHIP_CONTAINS,
                    code(CODE_IMAGE, SCHEME_DCM, MEANING_IMAGE),
                    sop.getString(Tag.ReferencedSOPClassUID),
                    sop.getString(Tag.ReferencedSOPInstanceUID));
            content.add(img);
        }
    }

    private static List<Attributes> buildReferencedSops() {
        int sopCount = 6;
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
