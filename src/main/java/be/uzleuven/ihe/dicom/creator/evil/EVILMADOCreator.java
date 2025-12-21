package be.uzleuven.ihe.dicom.creator.evil;

import org.dcm4che3.data.*;
import org.dcm4che3.util.UIDUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static be.uzleuven.ihe.dicom.creator.DicomCreatorUtils.*;
import static be.uzleuven.ihe.dicom.creator.DicomSequenceUtils.*;
import static be.uzleuven.ihe.dicom.creator.SRContentItemUtils.*;

/**
 * EVIL generator: creates MADO (manifest with description) documents that randomly omit creation steps
 * or inject wrong tags/values.
 *
 * This is intentionally isolated from the normal generators.
 */
public class EVILMADOCreator {

    private static final double SKIP_STEP_P = 0.20;
    private static final double CORRUPT_P = 0.05;

    // MADO / DICOM CP Codes (same placeholders as the normal creator)
    private static final String CODE_DCM_MANIFEST_DESC = "ddd001";
    private static final String CODE_DCM_IMAGE_LIBRARY = "111028";
    private static final String CODE_DCM_LIB_GROUP = "126200";
    private static final String CODE_DCM_KOS_DESC = "113012";

    private static final String CODE_MODALITY = "121139";
    private static final String CODE_STUDY_INSTANCE_UID = "ddd011";
    private static final String CODE_TARGET_REGION = "123014";
    private static final String CODE_SERIES_DATE = "ddd003";
    private static final String CODE_SERIES_TIME = "ddd004";
    private static final String CODE_SERIES_NUMBER = "ddd005";
    private static final String CODE_SERIES_DESCRIPTION = "ddd002";
    private static final String CODE_SERIES_INSTANCE_UID = "ddd006";
    private static final String CODE_KOS_TITLE = "ddd008";
    private static final String CODE_SOP_INSTANCE_UID = "ddd007";

    private static final String DEFAULT_INSTITUTION_NAME = "IHE Demo Hospital";
    private static final String DEFAULT_PATIENT_ID_ISSUER = "HUPA";
    private static final String DEFAULT_PATIENT_ID_ISSUER_OID = "1.2.3.4.5.6.7.8.9";
    private static final String DEFAULT_ACCESSION_ISSUER_OID = "2.16.840.1.113883.3.72.5.9.1";
    private static final String DEFAULT_RETRIEVE_LOCATION_UID = "1.2.3.4.5.6.7.8.9.10";

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
            SimulatedStudy study = generateSimulatedStudy();
            Attributes mado = createEvilMadoAttributes(study);
            File out = new File(outDir, "EVIL_IHE_MADO_" + i + "_" + EvilDice.token(6) + ".dcm");
            writeDicomFile(out, mado);
            System.out.println("Wrote: " + out.getAbsolutePath());
        }
    }

    private static Attributes createEvilMadoAttributes(SimulatedStudy study) {
        Attributes d = new Attributes();
        String manifestSOPUid = UIDUtils.createUID();

        String studyDate = now("yyyyMMdd");
        String studyTime = now("HHmmss");

        // Always writeable Part 10: keep SOP Class/Instance set.
        d.setString(Tag.SOPClassUID, VR.UI, UID.KeyObjectSelectionDocumentStorage);
        d.setString(Tag.SOPInstanceUID, VR.UI, manifestSOPUid);

        // --- Standard Headers ---
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.StudyInstanceUID, VR.UI, study.studyInstanceUID);
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.SeriesInstanceUID, VR.UI, UIDUtils.createUID());
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.Modality, VR.CS, "KO");
        if (!EvilDice.chance(SKIP_STEP_P)) d.setInt(Tag.SeriesNumber, VR.IS, 999);
        if (!EvilDice.chance(SKIP_STEP_P)) d.setInt(Tag.InstanceNumber, VR.IS, 1);

        if (!EvilDice.chance(SKIP_STEP_P)) {
            // type-2 present even if empty; EVIL may omit it
            d.newSequence(Tag.ReferencedPerformedProcedureStepSequence, 0);
        }

        // --- Study IE ---
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.StudyDate, VR.DA, studyDate);
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.StudyTime, VR.TM, studyTime);
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.ReferringPhysicianName, VR.PN, "^");
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.StudyID, VR.SH, "STUDY-001");

        // --- Patient IE ---
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.PatientName, VR.PN, "MADO^TEST^PATIENT");
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.PatientID, VR.LO, "MADO-12345");
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.IssuerOfPatientID, VR.LO, DEFAULT_PATIENT_ID_ISSUER);
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.PatientBirthDate, VR.DA, "19870828");
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.PatientSex, VR.CS, "O");

        if (!EvilDice.chance(SKIP_STEP_P)) {
            addPatientIDQualifiers(d, DEFAULT_PATIENT_ID_ISSUER_OID);
        }

        // --- Accession + Issuer ---
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.AccessionNumber, VR.SH, "ACC-MADO-001");
        if (!EvilDice.chance(SKIP_STEP_P)) addAccessionNumberIssuer(d, DEFAULT_ACCESSION_ISSUER_OID);

        // --- Equipment ---
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.Manufacturer, VR.LO, "IHE_EVIL_MADO_CREATOR");
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.InstitutionName, VR.LO, DEFAULT_INSTITUTION_NAME);

        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.TimezoneOffsetFromUTC, VR.SH, "+0000");

        // --- Key Object Document Module ---
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.ContentDate, VR.DA, studyDate);
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.ContentTime, VR.TM, studyTime);

        if (!EvilDice.chance(SKIP_STEP_P)) {
            populateReferencedRequestSequenceWithIssuer(d, study.studyInstanceUID,
                    d.getString(Tag.AccessionNumber),
                    DEFAULT_ACCESSION_ISSUER_OID);
        }

        if (!EvilDice.chance(SKIP_STEP_P)) {
            populateEvidence(d, study);
        }

        // --- SR root ---
        if (!EvilDice.chance(SKIP_STEP_P)) {
            d.setString(Tag.ValueType, VR.CS, "CONTAINER");
            d.setString(Tag.ContinuityOfContent, VR.CS, "SEPARATE");
        }

        if (!EvilDice.chance(SKIP_STEP_P)) {
            d.newSequence(Tag.ConceptNameCodeSequence, 1)
                    .add(code(CODE_DCM_MANIFEST_DESC, "DCM", "Manifest with Description"));
        }

        if (!EvilDice.chance(SKIP_STEP_P)) {
            d.newSequence(Tag.ContentTemplateSequence, 1)
                    .add(createTemplateItem("2010"));
        }

        if (!EvilDice.chance(SKIP_STEP_P)) {
            d.setString(Tag.CompletionFlag, VR.CS, "COMPLETE");
            d.setString(Tag.VerificationFlag, VR.CS, "UNVERIFIED");
        }

        if (!EvilDice.chance(SKIP_STEP_P)) {
            Sequence contentSeq = d.newSequence(Tag.ContentSequence, 10);

            // TID 1600-ish, top-level
            if (!EvilDice.chance(SKIP_STEP_P)) {
                contentSeq.add(createCodeItem("CONTAINS", CODE_MODALITY, "DCM", "Modality",
                        code("CT", "DCM", "CT")));
            }
            if (!EvilDice.chance(SKIP_STEP_P)) {
                contentSeq.add(createUIDRefItem("CONTAINS", CODE_STUDY_INSTANCE_UID, "DCM", "Study Instance UID",
                        study.studyInstanceUID));
            }
            if (!EvilDice.chance(SKIP_STEP_P)) {
                contentSeq.add(createCodeItem("CONTAINS", CODE_TARGET_REGION, "DCM", "Target Region",
                        code("T-D4000", "SRT", "Abdomen")));
            }

            // Image Library
            if (!EvilDice.chance(SKIP_STEP_P)) {
                Attributes libContainer = new Attributes();
                libContainer.setString(Tag.RelationshipType, VR.CS, "CONTAINS");
                libContainer.setString(Tag.ValueType, VR.CS, "CONTAINER");
                libContainer.newSequence(Tag.ConceptNameCodeSequence, 1)
                        .add(code(CODE_DCM_IMAGE_LIBRARY, "DCM", "Image Library"));

                Sequence libContent = libContainer.newSequence(Tag.ContentSequence, 50);

                // acquisition context under lib
                if (!EvilDice.chance(SKIP_STEP_P)) {
                    libContent.add(createCodeItem("HAS ACQ CONTEXT", CODE_MODALITY, "DCM", "Modality",
                            code("CT", "DCM", "CT")));
                }
                if (!EvilDice.chance(SKIP_STEP_P)) {
                    libContent.add(createUIDRefItem("HAS ACQ CONTEXT", CODE_STUDY_INSTANCE_UID, "DCM", "Study Instance UID",
                            study.studyInstanceUID));
                }
                if (!EvilDice.chance(SKIP_STEP_P)) {
                    libContent.add(createCodeItem("HAS ACQ CONTEXT", CODE_TARGET_REGION, "DCM", "Target Region",
                            code("T-D4000", "SRT", "Abdomen")));
                }

                int seriesNumber = 1;
                for (SimulatedSeries series : study.seriesList) {
                    series.seriesNumber = seriesNumber++;
                    series.seriesDate = studyDate;
                    series.seriesTime = randomSeriesTime(studyTime);

                    // series group sometimes skipped entirely
                    if (EvilDice.chance(SKIP_STEP_P)) {
                        continue;
                    }

                    Attributes group = new Attributes();
                    group.setString(Tag.RelationshipType, VR.CS, "CONTAINS");
                    group.setString(Tag.ValueType, VR.CS, "CONTAINER");
                    group.newSequence(Tag.ConceptNameCodeSequence, 1)
                            .add(code(CODE_DCM_LIB_GROUP, "DCM", "Image Library Group"));

                    Sequence groupSeq = group.newSequence(Tag.ContentSequence, 50);

                    if (!EvilDice.chance(SKIP_STEP_P)) {
                        groupSeq.add(createCodeItem("HAS ACQ CONTEXT", CODE_MODALITY, "DCM", "Modality",
                                code(series.modality, "DCM", series.modality)));
                    }
                    if (!EvilDice.chance(SKIP_STEP_P)) {
                        groupSeq.add(createUIDRefItem("HAS ACQ CONTEXT", CODE_SERIES_INSTANCE_UID, "DCM", "Series Instance UID", series.seriesUID));
                    }
                    if (!EvilDice.chance(SKIP_STEP_P)) {
                        groupSeq.add(createTextItem("HAS ACQ CONTEXT", CODE_SERIES_DESCRIPTION, "DCM", "Series Description", series.description));
                    }
                    if (!EvilDice.chance(SKIP_STEP_P)) {
                        groupSeq.add(createTextItem("HAS ACQ CONTEXT", CODE_SERIES_DATE, "DCM", "Series Date", series.seriesDate));
                    }
                    if (!EvilDice.chance(SKIP_STEP_P)) {
                        groupSeq.add(createTextItem("HAS ACQ CONTEXT", CODE_SERIES_TIME, "DCM", "Series Time", series.seriesTime));
                    }
                    if (!EvilDice.chance(SKIP_STEP_P)) {
                        groupSeq.add(createTextItem("HAS ACQ CONTEXT", CODE_SERIES_NUMBER, "DCM", "Series Number", Integer.toString(series.seriesNumber)));
                    }

                    for (SimulatedInstance inst : series.instances) {
                        if (EvilDice.chance(SKIP_STEP_P)) {
                            continue;
                        }

                        Attributes entry = new Attributes();
                        entry.setString(Tag.RelationshipType, VR.CS, "CONTAINS");
                        entry.setString(Tag.ValueType, VR.CS, "IMAGE");

                        if (!EvilDice.chance(SKIP_STEP_P)) {
                            Sequence refSop = entry.newSequence(Tag.ReferencedSOPSequence, 1);
                            Attributes refItem = new Attributes();
                            refItem.setString(Tag.ReferencedSOPClassUID, VR.UI, inst.sopClassUID);
                            refItem.setString(Tag.ReferencedSOPInstanceUID, VR.UI, inst.sopInstanceUID);
                            refSop.add(refItem);
                        }

                        if (inst.isKIN && !EvilDice.chance(SKIP_STEP_P)) {
                            Sequence entryContent = entry.newSequence(Tag.ContentSequence, 10);
                            addKinDescriptors(entryContent, study);
                        }

                        groupSeq.add(entry);
                    }

                    libContent.add(group);
                }

                contentSeq.add(libContainer);
            }
        }

        // 5% chance: inject a corruption
        if (EvilDice.chance(CORRUPT_P)) {
            EvilMutator.corruptOne(d);
        }

        // 30% chance: add forbidden tags (makes it more evil!)
        ForbiddenTags.addRandomForbiddenTags(d, 0.30);

        return d;
    }

    private static void addKinDescriptors(Sequence entryContent, SimulatedStudy study) {
        Attributes kosDesc = new Attributes();
        kosDesc.setString(Tag.RelationshipType, VR.CS, "CONTAINS");
        kosDesc.setString(Tag.ValueType, VR.CS, "CONTAINER");
        kosDesc.newSequence(Tag.ConceptNameCodeSequence, 1)
                .add(code(CODE_DCM_KOS_DESC, "DCM", "Key Object Description"));

        Sequence descSeq = kosDesc.newSequence(Tag.ContentSequence, 10);

        if (!EvilDice.chance(SKIP_STEP_P)) {
            descSeq.add(createCodeItem("CONTAINS", CODE_KOS_TITLE, "DCM", "KOS Title Code",
                    code("113000", "DCM", "Of Interest")));
        }

        if (!EvilDice.chance(SKIP_STEP_P)) {
            descSeq.add(createTextItem("CONTAINS", "ddd009", "DCM", "KOS Object Description", "Key Objects for Surgery"));
        }

        // Add some UIDREFs describing what the KIN references (may be skipped)
        if (!EvilDice.chance(SKIP_STEP_P)) {
            SimulatedInstance multiFrameObj = study.seriesList.get(1).instances.get(0);
            descSeq.add(createUIDRefItem("CONTAINS", CODE_SOP_INSTANCE_UID, "DCM", "SOP Instance UIDs", multiFrameObj.sopInstanceUID));
        }
        if (!EvilDice.chance(SKIP_STEP_P)) {
            SimulatedInstance singleFrame1 = study.seriesList.get(0).instances.get(1);
            descSeq.add(createUIDRefItem("CONTAINS", CODE_SOP_INSTANCE_UID, "DCM", "SOP Instance UIDs", singleFrame1.sopInstanceUID));
        }

        entryContent.add(kosDesc);
    }

    // Evidence sequence helper (copied structure from IHEMADOSampleCreator)
    private static void populateEvidence(Attributes d, SimulatedStudy study) {
        Sequence evidence = d.newSequence(Tag.CurrentRequestedProcedureEvidenceSequence, 1);
        Attributes studyItem = new Attributes();
        studyItem.setString(Tag.StudyInstanceUID, VR.UI, study.studyInstanceUID);

        Sequence seriesSeq = studyItem.newSequence(Tag.ReferencedSeriesSequence, 5);

        for (SimulatedSeries s : study.seriesList) {
            if (EvilDice.chance(SKIP_STEP_P)) {
                continue;
            }

            Attributes seriesItem = new Attributes();
            seriesItem.setString(Tag.SeriesInstanceUID, VR.UI, s.seriesUID);

            if (!EvilDice.chance(SKIP_STEP_P)) {
                seriesItem.setString(Tag.RetrieveLocationUID, VR.UI, DEFAULT_RETRIEVE_LOCATION_UID);
            }

            Sequence sopSeq = seriesItem.newSequence(Tag.ReferencedSOPSequence, Math.max(1, s.instances.size()));
            for (SimulatedInstance i : s.instances) {
                if (EvilDice.chance(SKIP_STEP_P)) {
                    continue;
                }
                Attributes sopItem = new Attributes();
                sopItem.setString(Tag.ReferencedSOPClassUID, VR.UI, i.sopClassUID);
                sopItem.setString(Tag.ReferencedSOPInstanceUID, VR.UI, i.sopInstanceUID);
                sopSeq.add(sopItem);
            }
            seriesSeq.add(seriesItem);
        }
        evidence.add(studyItem);
    }

    // --- Simulation Data Generation (same spirit as normal creator) ---
    private static SimulatedStudy generateSimulatedStudy() {
        SimulatedStudy study = new SimulatedStudy();
        study.studyInstanceUID = UIDUtils.createUID();

        SimulatedSeries s1 = new SimulatedSeries(UIDUtils.createUID(), "CT", "Routine Abdomen");
        for (int i = 0; i < 5; i++) {
            s1.addInstance(UID.CTImageStorage, UIDUtils.createUID(), false);
        }
        study.addSeries(s1);

        SimulatedSeries s2 = new SimulatedSeries(UIDUtils.createUID(), "CT", "Enhanced Recons");
        s2.addInstance(UID.EnhancedCTImageStorage, UIDUtils.createUID(), false);
        study.addSeries(s2);

        SimulatedSeries s3 = new SimulatedSeries(UIDUtils.createUID(), "OT", "Scanned Docs");
        s3.addInstance(UID.SecondaryCaptureImageStorage, UIDUtils.createUID(), false);
        study.addSeries(s3);

        SimulatedSeries s4 = new SimulatedSeries(UIDUtils.createUID(), "US", "Ultrasound Preview");
        s4.addInstance(UID.UltrasoundImageStorage, UIDUtils.createUID(), false);
        study.addSeries(s4);

        SimulatedSeries s5 = new SimulatedSeries(UIDUtils.createUID(), "KO", "Key Images");
        s5.addInstance(UID.KeyObjectSelectionDocumentStorage, UIDUtils.createUID(), true);
        study.addSeries(s5);
        study.kinSeries = s5;

        return study;
    }

    static class SimulatedStudy {
        String studyInstanceUID;
        List<SimulatedSeries> seriesList = new ArrayList<>();
        SimulatedSeries kinSeries;

        void addSeries(SimulatedSeries s) {
            seriesList.add(s);
        }
    }

    static class SimulatedSeries {
        String seriesUID;
        String modality;
        String description;
        int seriesNumber;
        String seriesDate;
        String seriesTime;
        List<SimulatedInstance> instances = new ArrayList<>();

        SimulatedSeries(String uid, String mod, String desc) {
            this.seriesUID = uid;
            this.modality = mod;
            this.description = desc;
        }

        void addInstance(String cls, String uid, boolean isKin) {
            instances.add(new SimulatedInstance(cls, uid, isKin));
        }
    }

    static class SimulatedInstance {
        String sopClassUID;
        String sopInstanceUID;
        boolean isKIN;

        SimulatedInstance(String cls, String uid, boolean isKin) {
            this.sopClassUID = cls;
            this.sopInstanceUID = uid;
            this.isKIN = isKin;
        }
    }
}
