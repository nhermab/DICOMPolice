package be.uzleuven.ihe.dicom.creator.evil;

import be.uzleuven.ihe.dicom.constants.DicomConstants;
import org.dcm4che3.data.*;
import org.dcm4che3.util.UIDUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static be.uzleuven.ihe.dicom.creator.utils.DicomCreatorUtils.*;
import static be.uzleuven.ihe.dicom.creator.utils.DicomSequenceUtils.*;
import static be.uzleuven.ihe.dicom.creator.utils.SRContentItemUtils.*;
import static be.uzleuven.ihe.dicom.constants.CodeConstants.*;

/**
 * EVIL generator: creates MADO (manifest with description) documents that randomly omit creation steps
 * or inject wrong tags/values.
 * This is intentionally isolated from the normal generators.
 */
public class EVILMADOCreator {

    private static final double SKIP_STEP_P = 0.20;
    private static final double CORRUPT_P = 0.05;
    private static final double MADO_VIOLATION_P = 0.35;
    private static final double EVIDENCE_MISMATCH_P = 0.40; // 40% chance to create Evidence/ContentTree mismatch

    // Note: Code constants are now imported from CodeConstants class

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

        // MADO-specific Patient violations
        if (EvilDice.chance(MADO_VIOLATION_P)) {
            violateMADOPatient(d);
        }

        // --- Accession + Issuer ---
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.AccessionNumber, VR.SH, "ACC-MADO-001");
        if (!EvilDice.chance(SKIP_STEP_P)) addAccessionNumberIssuer(d, DEFAULT_ACCESSION_ISSUER_OID);

        // MADO-specific Study violations (includes accession issues)
        if (EvilDice.chance(MADO_VIOLATION_P)) {
            violateMADOStudy(d);
        }

        // --- Equipment ---
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.Manufacturer, VR.LO, "IHE_EVIL_MADO_CREATOR");
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.InstitutionName, VR.LO, DEFAULT_INSTITUTION_NAME);

        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.TimezoneOffsetFromUTC, VR.SH, "+0000");

        // MADO-specific Equipment violations
        if (EvilDice.chance(MADO_VIOLATION_P)) {
            violateMADOEquipment(d);
        }

        // MADO-specific Timezone violations
        if (EvilDice.chance(MADO_VIOLATION_P)) {
            violateMADOTimezone(d);
        }

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

        // MADO-specific Evidence violations (retrieval info, series attributes)
        if (EvilDice.chance(MADO_VIOLATION_P)) {
            violateMADOEvidence(d);
        }

        // --- SR root ---
        if (!EvilDice.chance(SKIP_STEP_P)) {
            d.setString(Tag.ValueType, VR.CS, "CONTAINER");
            d.setString(Tag.ContinuityOfContent, VR.CS, DicomConstants.CONTINUITY_SEPARATE);
        }

        if (!EvilDice.chance(SKIP_STEP_P)) {
            d.newSequence(Tag.ConceptNameCodeSequence, 1)
                    .add(code(CODE_MANIFEST_WITH_DESCRIPTION, SCHEME_DCM, MEANING_MANIFEST_WITH_DESCRIPTION));
        }

        // MADO-specific Document Title violations
        if (EvilDice.chance(MADO_VIOLATION_P)) {
            violateMADODocumentTitle(d);
        }

        if (!EvilDice.chance(SKIP_STEP_P)) {
            d.newSequence(Tag.ContentTemplateSequence, 1)
                    .add(createTemplateItem("2010"));
        }

        // MADO-specific Template violations (TID 1600 vs 2010)
        if (EvilDice.chance(MADO_VIOLATION_P)) {
            violateMADOTemplate(d);
        }

        if (!EvilDice.chance(SKIP_STEP_P)) {
            d.setString(Tag.CompletionFlag, VR.CS, DicomConstants.COMPLETION_FLAG_COMPLETE);
            d.setString(Tag.VerificationFlag, VR.CS, DicomConstants.VERIFICATION_FLAG_UNVERIFIED);
        }

        if (!EvilDice.chance(SKIP_STEP_P)) {
            Sequence contentSeq = d.newSequence(Tag.ContentSequence, 10);

            // TID 1600-ish, top-level
            if (!EvilDice.chance(SKIP_STEP_P)) {
                contentSeq.add(createCodeItem(DicomConstants.RELATIONSHIP_CONTAINS, CODE_MODALITY, SCHEME_DCM, MEANING_MODALITY,
                        code(CODE_MODALITY_CT, SCHEME_DCM, MEANING_MODALITY_CT)));
            }
            if (!EvilDice.chance(SKIP_STEP_P)) {
                contentSeq.add(createUIDRefItem(DicomConstants.RELATIONSHIP_CONTAINS, CODE_STUDY_INSTANCE_UID, SCHEME_DCM, MEANING_STUDY_INSTANCE_UID,
                        study.studyInstanceUID));
            }
            if (!EvilDice.chance(SKIP_STEP_P)) {
                contentSeq.add(createCodeItem(DicomConstants.RELATIONSHIP_CONTAINS, CODE_TARGET_REGION, SCHEME_DCM, MEANING_TARGET_REGION,
                        code(CODE_REGION_ABDOMEN, SCHEME_SRT, MEANING_REGION_ABDOMEN)));
            }

            // Image Library
            if (!EvilDice.chance(SKIP_STEP_P)) {
                Attributes libContainer = new Attributes();
                libContainer.setString(Tag.RelationshipType, VR.CS, DicomConstants.RELATIONSHIP_CONTAINS);
                libContainer.setString(Tag.ValueType, VR.CS, "CONTAINER");
                libContainer.newSequence(Tag.ConceptNameCodeSequence, 1)
                        .add(code(CODE_IMAGE_LIBRARY, SCHEME_DCM, MEANING_IMAGE_LIBRARY));

                Sequence libContent = libContainer.newSequence(Tag.ContentSequence, 50);

                // acquisition context under lib
                if (!EvilDice.chance(SKIP_STEP_P)) {
                    libContent.add(createCodeItem("HAS ACQ CONTEXT", CODE_MODALITY, SCHEME_DCM, MEANING_MODALITY,
                            code(CODE_MODALITY_CT, SCHEME_DCM, MEANING_MODALITY_CT)));
                }
                if (!EvilDice.chance(SKIP_STEP_P)) {
                    libContent.add(createUIDRefItem("HAS ACQ CONTEXT", CODE_STUDY_INSTANCE_UID, SCHEME_DCM, MEANING_STUDY_INSTANCE_UID,
                            study.studyInstanceUID));
                }
                if (!EvilDice.chance(SKIP_STEP_P)) {
                    libContent.add(createCodeItem("HAS ACQ CONTEXT", CODE_TARGET_REGION, SCHEME_DCM, MEANING_TARGET_REGION,
                            code(CODE_REGION_ABDOMEN, SCHEME_SRT, MEANING_REGION_ABDOMEN)));
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
                    group.setString(Tag.RelationshipType, VR.CS, DicomConstants.RELATIONSHIP_CONTAINS);
                    group.setString(Tag.ValueType, VR.CS, "CONTAINER");
                    group.newSequence(Tag.ConceptNameCodeSequence, 1)
                            .add(code(CODE_IMAGE_LIBRARY_GROUP, SCHEME_DCM, MEANING_IMAGE_LIBRARY_GROUP));

                    Sequence groupSeq = group.newSequence(Tag.ContentSequence, 50);

                    if (!EvilDice.chance(SKIP_STEP_P)) {
                        groupSeq.add(createCodeItem("HAS ACQ CONTEXT", CODE_MODALITY, SCHEME_DCM, MEANING_MODALITY,
                                code(series.modality, SCHEME_DCM, series.modality)));
                    }
                    if (!EvilDice.chance(SKIP_STEP_P)) {
                        groupSeq.add(createUIDRefItem("HAS ACQ CONTEXT", CODE_SERIES_INSTANCE_UID, SCHEME_DCM, MEANING_SERIES_INSTANCE_UID, series.seriesUID));
                    }
                    if (!EvilDice.chance(SKIP_STEP_P)) {
                        groupSeq.add(createTextItem("HAS ACQ CONTEXT", CODE_SERIES_DESCRIPTION, SCHEME_DCM, MEANING_SERIES_DESCRIPTION, series.description));
                    }
                    if (!EvilDice.chance(SKIP_STEP_P)) {
                        groupSeq.add(createTextItem("HAS ACQ CONTEXT", CODE_SERIES_DATE, SCHEME_DCM, MEANING_SERIES_DATE, series.seriesDate));
                    }
                    if (!EvilDice.chance(SKIP_STEP_P)) {
                        groupSeq.add(createTextItem("HAS ACQ CONTEXT", CODE_SERIES_TIME, SCHEME_DCM, MEANING_SERIES_TIME, series.seriesTime));
                    }
                    if (!EvilDice.chance(SKIP_STEP_P)) {
                        groupSeq.add(createTextItem("HAS ACQ CONTEXT", CODE_SERIES_NUMBER, SCHEME_DCM, MEANING_SERIES_NUMBER, Integer.toString(series.seriesNumber)));
                    }

                    for (SimulatedInstance inst : series.instances) {
                        if (EvilDice.chance(SKIP_STEP_P)) {
                            continue;
                        }

                        // EVIL: Sometimes skip instances in Content Tree that ARE in Evidence (mismatch violation)
                        if (EvilDice.chance(EVIDENCE_MISMATCH_P)) {
                            continue; // Skip this instance - creates Evidence/ContentTree mismatch!
                        }

                        Attributes entry = new Attributes();
                        entry.setString(Tag.RelationshipType, VR.CS, DicomConstants.RELATIONSHIP_CONTAINS);
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

                    // EVIL: Sometimes add phantom instances to Content Tree that are NOT in Evidence (mismatch violation)
                    if (EvilDice.chance(EVIDENCE_MISMATCH_P / 2)) { // 20% chance to add phantoms
                        int phantomCount = 1 + EvilDice.randomInt(3);
                        for (int p = 0; p < phantomCount; p++) {
                            Attributes phantomEntry = new Attributes();
                            phantomEntry.setString(Tag.RelationshipType, VR.CS, DicomConstants.RELATIONSHIP_CONTAINS);
                            phantomEntry.setString(Tag.ValueType, VR.CS, "IMAGE");

                            Sequence refSop = phantomEntry.newSequence(Tag.ReferencedSOPSequence, 1);
                            Attributes refItem = new Attributes();
                            refItem.setString(Tag.ReferencedSOPClassUID, VR.UI, randomFrom(
                                    UID.CTImageStorage,
                                    UID.MRImageStorage,
                                    UID.EnhancedCTImageStorage
                            ));
                            refItem.setString(Tag.ReferencedSOPInstanceUID, VR.UI, UIDUtils.createUID());
                            refSop.add(refItem);

                            groupSeq.add(phantomEntry);
                        }
                    }

                    libContent.add(group);
                }

                contentSeq.add(libContainer);
            }
        }

        // MADO-specific Content Tree violations (TID 1600 structure issues)
        if (EvilDice.chance(MADO_VIOLATION_P)) {
            violateMADOContentTree(d);
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
        kosDesc.setString(Tag.RelationshipType, VR.CS, DicomConstants.RELATIONSHIP_CONTAINS);
        kosDesc.setString(Tag.ValueType, VR.CS, "CONTAINER");
        kosDesc.newSequence(Tag.ConceptNameCodeSequence, 1)
                .add(code(CODE_KOS_DESCRIPTION, SCHEME_DCM, MEANING_KOS_DESCRIPTION));

        Sequence descSeq = kosDesc.newSequence(Tag.ContentSequence, 10);

        if (!EvilDice.chance(SKIP_STEP_P)) {
            descSeq.add(createCodeItem(DicomConstants.RELATIONSHIP_CONTAINS, CODE_KOS_TITLE, SCHEME_DCM, MEANING_KOS_TITLE,
                    code(CODE_OF_INTEREST, SCHEME_DCM, MEANING_OF_INTEREST)));
        }

        if (!EvilDice.chance(SKIP_STEP_P)) {
            descSeq.add(createTextItem(DicomConstants.RELATIONSHIP_CONTAINS, "ddd009", "DCM", "KOS Object Description", "Key Objects for Surgery"));
        }

        // Add some UIDREFs describing what the KIN references (may be skipped)
        if (!EvilDice.chance(SKIP_STEP_P)) {
            SimulatedInstance multiFrameObj = study.seriesList.get(1).instances.get(0);
            descSeq.add(createUIDRefItem(DicomConstants.RELATIONSHIP_CONTAINS, CODE_SOP_INSTANCE_UID, SCHEME_DCM, MEANING_SOP_INSTANCE_UID, multiFrameObj.sopInstanceUID));
        }
        if (!EvilDice.chance(SKIP_STEP_P)) {
            SimulatedInstance singleFrame1 = study.seriesList.get(0).instances.get(1);
            descSeq.add(createUIDRefItem(DicomConstants.RELATIONSHIP_CONTAINS, CODE_SOP_INSTANCE_UID, SCHEME_DCM, MEANING_SOP_INSTANCE_UID, singleFrame1.sopInstanceUID));
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

    // ========== MADO-specific violation methods ==========

    /**
     * Violate MADO Patient IE requirements
     */
    private static void violateMADOPatient(Attributes d) {
        int violation = EvilDice.randomInt(8);
        switch (violation) {
            case 0:
                // Remove IssuerOfPatientIDQualifiersSequence completely
                d.remove(Tag.IssuerOfPatientIDQualifiersSequence);
                break;
            case 1:
                // Add IssuerOfPatientIDQualifiersSequence but empty
                d.newSequence(Tag.IssuerOfPatientIDQualifiersSequence, 0);
                break;
            case 2:
                // Add IssuerOfPatientIDQualifiersSequence but missing UniversalEntityID
                Sequence issuerSeq = d.newSequence(Tag.IssuerOfPatientIDQualifiersSequence, 1);
                Attributes issuer = new Attributes();
                issuer.setString(Tag.UniversalEntityIDType, VR.CS, "ISO");
                issuerSeq.add(issuer);
                break;
            case 3:
                // Add IssuerOfPatientIDQualifiersSequence but wrong UniversalEntityIDType
                Sequence issuerSeq2 = d.newSequence(Tag.IssuerOfPatientIDQualifiersSequence, 1);
                Attributes issuer2 = new Attributes();
                issuer2.setString(Tag.UniversalEntityID, VR.UT, "1.2.3.4.5");
                issuer2.setString(Tag.UniversalEntityIDType, VR.CS, "DNS"); // Wrong! Should be ISO
                issuerSeq2.add(issuer2);
                break;
            case 4:
                // Wrong TypeOfPatientID
                d.setString(Tag.TypeOfPatientID, VR.CS, randomFrom("MRN", "ANON", "TEST"));
                break;
            case 5:
                // Empty PatientID
                d.setString(Tag.PatientID, VR.LO, "");
                break;
            case 6:
                // Remove PatientID completely
                d.remove(Tag.PatientID);
                break;
            case 7:
                // Invalid UniversalEntityID (not a valid OID)
                Sequence issuerSeq3 = d.newSequence(Tag.IssuerOfPatientIDQualifiersSequence, 1);
                Attributes issuer3 = new Attributes();
                issuer3.setString(Tag.UniversalEntityID, VR.UT, "not.a.valid.oid");
                issuer3.setString(Tag.UniversalEntityIDType, VR.CS, "ISO");
                issuerSeq3.add(issuer3);
                break;
        }
    }

    /**
     * Violate MADO Study IE requirements
     */
    private static void violateMADOStudy(Attributes d) {
        int violation = EvilDice.randomInt(7);
        switch (violation) {
            case 0:
                // Remove StudyDate
                d.remove(Tag.StudyDate);
                break;
            case 1:
                // Remove StudyTime
                d.remove(Tag.StudyTime);
                break;
            case 2:
                // Empty StudyDate
                d.setString(Tag.StudyDate, VR.DA, "");
                break;
            case 3:
                // Empty StudyTime
                d.setString(Tag.StudyTime, VR.TM, "");
                break;
            case 4:
                // Remove AccessionNumber
                d.remove(Tag.AccessionNumber);
                break;
            case 5:
                // AccessionNumber present but missing issuer
                String acc = d.getString(Tag.AccessionNumber);
                if (acc != null && !acc.trim().isEmpty()) {
                    d.remove(Tag.IssuerOfAccessionNumberSequence);
                }
                break;
            case 6:
                // Multiple accession numbers but AccessionNumber not empty
                d.setString(Tag.AccessionNumber, VR.SH, randomAccession());
                // Add ReferencedRequestSequence with multiple items
                Sequence refReq = d.newSequence(Tag.ReferencedRequestSequence, 2);
                for (int i = 0; i < 2; i++) {
                    Attributes req = new Attributes();
                    req.setString(Tag.AccessionNumber, VR.SH, randomAccession());
                    req.setString(Tag.StudyInstanceUID, VR.UI, UIDUtils.createUID());
                    refReq.add(req);
                }
                break;
        }
    }

    /**
     * Violate MADO Equipment IE requirements
     */
    private static void violateMADOEquipment(Attributes d) {
        int violation = EvilDice.randomInt(4);
        switch (violation) {
            case 0:
                // Remove Manufacturer
                d.remove(Tag.Manufacturer);
                break;
            case 1:
                // Empty Manufacturer
                d.setString(Tag.Manufacturer, VR.LO, "");
                break;
            case 2:
                // Remove InstitutionName
                d.remove(Tag.InstitutionName);
                break;
            case 3:
                // Empty InstitutionName
                d.setString(Tag.InstitutionName, VR.LO, "");
                break;
        }
    }

    /**
     * Violate MADO Timezone requirements
     */
    private static void violateMADOTimezone(Attributes d) {
        int violation = EvilDice.randomInt(4);
        switch (violation) {
            case 0:
                // Remove TimezoneOffsetFromUTC
                d.remove(Tag.TimezoneOffsetFromUTC);
                break;
            case 1:
                // Invalid format - no sign
                d.setString(Tag.TimezoneOffsetFromUTC, VR.SH, "0100");
                break;
            case 2:
                // Invalid format - wrong length
                d.setString(Tag.TimezoneOffsetFromUTC, VR.SH, "+1");
                break;
            case 3:
                // Invalid format - out of range
                d.setString(Tag.TimezoneOffsetFromUTC, VR.SH, "+9999");
                break;
        }
    }

    /**
     * Violate MADO Document Title requirements (should be ddd001, not 113030)
     */
    private static void violateMADODocumentTitle(Attributes d) {
        int violation = EvilDice.randomInt(7);
        switch (violation) {
            case 0:
                // Wrong code value (use XDS-I.b/KOS code instead of MADO)
                Sequence conceptName = d.newSequence(Tag.ConceptNameCodeSequence, 1);
                conceptName.add(code("113030", "DCM", "Manifest")); // KOS, not MADO
                break;
            case 1:
                // Wrong coding scheme
                Sequence conceptName2 = d.newSequence(Tag.ConceptNameCodeSequence, 1);
                conceptName2.add(code("ddd001", "SCT", "Manifest with Description"));
                break;
            case 2:
                // Wrong meaning
                Sequence conceptName3 = d.newSequence(Tag.ConceptNameCodeSequence, 1);
                conceptName3.add(code("ddd001", "DCM", "Wrong Meaning"));
                break;
            case 3:
                // Remove ConceptNameCodeSequence
                d.remove(Tag.ConceptNameCodeSequence);
                break;
            case 4:
                // Empty ConceptNameCodeSequence
                d.newSequence(Tag.ConceptNameCodeSequence, 0);
                break;
            case 5:
                // Multiple items in ConceptNameCodeSequence
                Sequence conceptName4 = d.newSequence(Tag.ConceptNameCodeSequence, 2);
                conceptName4.add(code("ddd001", "DCM", "Manifest with Description"));
                conceptName4.add(code("113030", "DCM", "Manifest"));
                break;
            case 6:
                // Missing code attributes
                Sequence conceptName5 = d.newSequence(Tag.ConceptNameCodeSequence, 1);
                conceptName5.add(new Attributes());
                break;
        }
    }

    /**
     * Violate MADO Template requirements (should use TID 1600, not just 2010)
     */
    private static void violateMADOTemplate(Attributes d) {
        int violation = EvilDice.randomInt(5);
        switch (violation) {
            case 0:
                // Missing ContentTemplateSequence
                d.remove(Tag.ContentTemplateSequence);
                break;
            case 1:
                // Empty ContentTemplateSequence
                d.newSequence(Tag.ContentTemplateSequence, 0);
                break;
            case 2:
                // Wrong template ID - use wrong TID
                Sequence cts = d.newSequence(Tag.ContentTemplateSequence, 1);
                cts.add(createTemplateItem("1500")); // Wrong TID
                break;
            case 3:
                // Wrong mapping resource
                Sequence cts2 = d.newSequence(Tag.ContentTemplateSequence, 1);
                Attributes template = new Attributes();
                template.setString(Tag.TemplateIdentifier, VR.CS, "1600");
                template.setString(Tag.MappingResource, VR.CS, "WRONG"); // Should be DCMR
                cts2.add(template);
                break;
            case 4:
                // Multiple conflicting template items
                Sequence cts3 = d.newSequence(Tag.ContentTemplateSequence, 2);
                cts3.add(createTemplateItem("2010"));
                cts3.add(createTemplateItem("1500"));
                break;
        }
    }

    /**
     * Violate MADO Evidence sequence requirements
     */
    private static void violateMADOEvidence(Attributes d) {
        Sequence evidenceSeq = d.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence);
        if (evidenceSeq == null || evidenceSeq.isEmpty()) {
            return;
        }

        int violation = EvilDice.randomInt(12);

        for (Attributes studyItem : evidenceSeq) {
            Sequence seriesSeq = studyItem.getSequence(Tag.ReferencedSeriesSequence);
            if (seriesSeq == null || seriesSeq.isEmpty()) {
                continue;
            }

            for (Attributes seriesItem : seriesSeq) {
                switch (violation) {
                    case 0:
                        // Remove all retrieval information
                        seriesItem.remove(Tag.RetrieveURL);
                        seriesItem.remove(Tag.RetrieveLocationUID);
                        break;
                    case 1:
                        // Invalid Retrieve URL
                        seriesItem.setString(Tag.RetrieveURL, VR.UR, "not-a-valid-url");
                        break;
                    case 2:
                        // Retrieve URL without protocol
                        seriesItem.setString(Tag.RetrieveURL, VR.UR, "example.com/wado");
                        break;
                    case 3:
                        // Invalid Retrieve Location UID
                        seriesItem.setString(Tag.RetrieveLocationUID, VR.UI, "invalid.uid..123");
                        break;
                    case 4:
                        // Missing Modality at series level (Appendix B requirement)
                        seriesItem.remove(Tag.Modality);
                        break;
                    case 5:
                        // Empty Modality
                        seriesItem.setString(Tag.Modality, VR.CS, "");
                        break;
                    case 6:
                        // Missing Series Instance UID
                        seriesItem.remove(Tag.SeriesInstanceUID);
                        break;
                    case 7:
                        // Mix both retrieval addressing modes
                        seriesItem.setString(Tag.RetrieveURL, VR.UR, "https://example.com/wado");
                        seriesItem.setString(Tag.RetrieveLocationUID, VR.UI, UIDUtils.createUID());
                        break;
                    case 8:
                        // Invalid RetrieveURI
                        seriesItem.setString(Tag.RetrieveURI, VR.UR, "javascript:alert('xss')");
                        break;
                    case 9:
                        // Missing ReferencedSOPSequence
                        seriesItem.remove(Tag.ReferencedSOPSequence);
                        break;
                    case 10:
                        // Empty ReferencedSOPSequence
                        seriesItem.newSequence(Tag.ReferencedSOPSequence, 0);
                        break;
                    case 11:
                        // Add Appendix B attributes but with wrong values
                        seriesItem.setString(Tag.Modality, VR.CS, "INVALID_MOD");
                        seriesItem.setString(Tag.SeriesInstanceUID, VR.UI, "not.a.valid.uid");
                        break;
                }
            }
        }
    }

    /**
     * Violate MADO Content Tree (TID 1600) requirements
     */
    private static void violateMADOContentTree(Attributes d) {
        Sequence contentSeq = d.getSequence(Tag.ContentSequence);
        if (contentSeq == null || contentSeq.isEmpty()) {
            return;
        }

        int violation = EvilDice.randomInt(18);

        switch (violation) {
            case 0:
                // Remove entire ContentSequence
                d.remove(Tag.ContentSequence);
                break;
            case 1:
                // Empty ContentSequence
                d.newSequence(Tag.ContentSequence, 0);
                break;
            case 2:
                // Remove Image Library container (111028, DCM)
                for (int i = contentSeq.size() - 1; i >= 0; i--) {
                    Attributes item = contentSeq.get(i);
                    Sequence concept = item.getSequence(Tag.ConceptNameCodeSequence);
                    if (concept != null && !concept.isEmpty()) {
                        String codeValue = concept.get(0).getString(Tag.CodeValue);
                        if ("111028".equals(codeValue)) {
                            contentSeq.remove(i);
                        }
                    }
                }
                break;
            case 3:
                // Remove Modality from study level
                for (int i = contentSeq.size() - 1; i >= 0; i--) {
                    Attributes item = contentSeq.get(i);
                    Sequence concept = item.getSequence(Tag.ConceptNameCodeSequence);
                    if (concept != null && !concept.isEmpty()) {
                        String codeValue = concept.get(0).getString(Tag.CodeValue);
                        if ("121139".equals(codeValue)) {
                            contentSeq.remove(i);
                        }
                    }
                }
                break;
            case 4:
                // Remove Study Instance UID from study level
                for (int i = contentSeq.size() - 1; i >= 0; i--) {
                    Attributes item = contentSeq.get(i);
                    Sequence concept = item.getSequence(Tag.ConceptNameCodeSequence);
                    if (concept != null && !concept.isEmpty()) {
                        String codeValue = concept.get(0).getString(Tag.CodeValue);
                        if ("ddd011".equals(codeValue)) {
                            contentSeq.remove(i);
                        }
                    }
                }
                break;
            case 5:
                // Remove Target Region from study level
                for (int i = contentSeq.size() - 1; i >= 0; i--) {
                    Attributes item = contentSeq.get(i);
                    Sequence concept = item.getSequence(Tag.ConceptNameCodeSequence);
                    if (concept != null && !concept.isEmpty()) {
                        String codeValue = concept.get(0).getString(Tag.CodeValue);
                        if ("123014".equals(codeValue)) {
                            contentSeq.remove(i);
                        }
                    }
                }
                break;
            case 6:
                // Find Image Library and remove required series attributes from groups
                for (Attributes item : contentSeq) {
                    Sequence concept = item.getSequence(Tag.ConceptNameCodeSequence);
                    if (concept != null && !concept.isEmpty()) {
                        String codeValue = concept.get(0).getString(Tag.CodeValue);
                        if ("111028".equals(codeValue)) {
                            // Found Image Library - corrupt its groups
                            Sequence libContent = item.getSequence(Tag.ContentSequence);
                            if (libContent != null) {
                                for (Attributes group : libContent) {
                                    if ("CONTAINER".equals(group.getString(Tag.ValueType))) {
                                        Sequence groupContent = group.getSequence(Tag.ContentSequence);
                                        if (groupContent != null) {
                                            // Remove series-level required attributes
                                            for (int j = groupContent.size() - 1; j >= 0; j--) {
                                                Attributes attr = groupContent.get(j);
                                                Sequence attrConcept = attr.getSequence(Tag.ConceptNameCodeSequence);
                                                if (attrConcept != null && !attrConcept.isEmpty()) {
                                                    String attrCode = attrConcept.get(0).getString(Tag.CodeValue);
                                                    // Remove Series Date (ddd003)
                                                    if ("ddd003".equals(attrCode)) {
                                                        groupContent.remove(j);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                break;
            case 7:
                // Wrong ValueType for Image Library
                for (Attributes item : contentSeq) {
                    Sequence concept = item.getSequence(Tag.ConceptNameCodeSequence);
                    if (concept != null && !concept.isEmpty()) {
                        String codeValue = concept.get(0).getString(Tag.CodeValue);
                        if ("111028".equals(codeValue)) {
                            item.setString(Tag.ValueType, VR.CS, "TEXT"); // Wrong!
                        }
                    }
                }
                break;
            case 8:
                // Missing ContinuityOfContent in containers
                for (Attributes item : contentSeq) {
                    if ("CONTAINER".equals(item.getString(Tag.ValueType))) {
                        item.remove(Tag.ContinuityOfContent);
                    }
                }
                break;
            case 9:
                // Wrong RelationshipType
                for (Attributes item : contentSeq) {
                    item.setString(Tag.RelationshipType, VR.CS, "HAS PROPERTIES"); // Wrong for top-level
                }
                break;
            case 10:
                // IMAGE items without ReferencedSOPSequence
                for (Attributes item : contentSeq) {
                    if ("IMAGE".equals(item.getString(Tag.ValueType))) {
                        item.remove(Tag.ReferencedSOPSequence);
                    }
                }
                break;
            case 11:
                // Add conflicting Modality values
                contentSeq.add(createCodeItem(DicomConstants.RELATIONSHIP_CONTAINS, CODE_MODALITY, SCHEME_DCM, MEANING_MODALITY,
                        code("CT", "DCM", "Computed Tomography")));
                contentSeq.add(createCodeItem(DicomConstants.RELATIONSHIP_CONTAINS, CODE_MODALITY, SCHEME_DCM, MEANING_MODALITY,
                        code("MR", "DCM", "Magnetic Resonance")));
                break;
            case 12:
                // Missing ConceptNameCodeSequence in content items
                for (Attributes item : contentSeq) {
                    item.remove(Tag.ConceptNameCodeSequence);
                }
                break;
            case 13:
                // Add orphan IMAGE references not in Evidence
                Attributes orphanImg = new Attributes();
                orphanImg.setString(Tag.ValueType, VR.CS, "IMAGE");
                orphanImg.setString(Tag.RelationshipType, VR.CS, DicomConstants.RELATIONSHIP_CONTAINS);
                Sequence orphanSOP = orphanImg.newSequence(Tag.ReferencedSOPSequence, 1);
                Attributes orphanSOPItem = new Attributes();
                orphanSOPItem.setString(Tag.ReferencedSOPClassUID, VR.UI, UID.CTImageStorage);
                orphanSOPItem.setString(Tag.ReferencedSOPInstanceUID, VR.UI, UIDUtils.createUID());
                orphanSOP.add(orphanSOPItem);
                contentSeq.add(orphanImg);
                break;
            case 14:
                // Wrong relationship type in Image Library nested items
                for (Attributes item : contentSeq) {
                    Sequence concept = item.getSequence(Tag.ConceptNameCodeSequence);
                    if (concept != null && !concept.isEmpty()) {
                        String codeValue = concept.get(0).getString(Tag.CodeValue);
                        if ("111028".equals(codeValue)) {
                            Sequence libContent = item.getSequence(Tag.ContentSequence);
                            if (libContent != null) {
                                for (Attributes nested : libContent) {
                                    nested.setString(Tag.RelationshipType, VR.CS, "INFERRED FROM");
                                }
                            }
                        }
                    }
                }
                break;
            case 15:
                // Missing required series attributes in Image Library Group
                for (Attributes item : contentSeq) {
                    Sequence concept = item.getSequence(Tag.ConceptNameCodeSequence);
                    if (concept != null && !concept.isEmpty()) {
                        String codeValue = concept.get(0).getString(Tag.CodeValue);
                        if ("111028".equals(codeValue)) {
                            Sequence libContent = item.getSequence(Tag.ContentSequence);
                            if (libContent != null) {
                                for (Attributes group : libContent) {
                                    Sequence groupConcept = group.getSequence(Tag.ConceptNameCodeSequence);
                                    if (groupConcept != null && !groupConcept.isEmpty()) {
                                        String groupCode = groupConcept.get(0).getString(Tag.CodeValue);
                                        if ("126200".equals(groupCode)) { // Image Library Group
                                            // Remove all series attributes
                                            group.remove(Tag.ContentSequence);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                break;
            case 16:
                // Empty Image Library container
                for (Attributes item : contentSeq) {
                    Sequence concept = item.getSequence(Tag.ConceptNameCodeSequence);
                    if (concept != null && !concept.isEmpty()) {
                        String codeValue = concept.get(0).getString(Tag.CodeValue);
                        if ("111028".equals(codeValue)) {
                            item.remove(Tag.ContentSequence);
                        }
                    }
                }
                break;
            case 17:
                // Invalid UID value in Study Instance UID content item
                for (Attributes item : contentSeq) {
                    Sequence concept = item.getSequence(Tag.ConceptNameCodeSequence);
                    if (concept != null && !concept.isEmpty()) {
                        String codeValue = concept.get(0).getString(Tag.CodeValue);
                        if ("ddd011".equals(codeValue)) {
                            item.setString(Tag.UID, VR.UI, "not.a.valid.uid");
                        }
                    }
                }
                break;
        }
    }
}
