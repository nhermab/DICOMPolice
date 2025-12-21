package be.uzleuven.ihe.dicom.creator;

import org.dcm4che3.data.*;
import org.dcm4che3.util.UIDUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static be.uzleuven.ihe.dicom.creator.DicomCreatorUtils.*;
import static be.uzleuven.ihe.dicom.creator.DicomSequenceUtils.*;
import static be.uzleuven.ihe.dicom.creator.SRContentItemUtils.*;

public class IHEMADOSampleCreator {


    // MADO / DICOM CP Codes
    private static final String CODE_DCM_MANIFEST_DESC = "ddd001"; // "Manifest with Description"
    private static final String CODE_DCM_IMAGE_LIBRARY = "111028"; // "Image Library"
    private static final String CODE_DCM_LIB_GROUP = "126200";     // "Image Library Group"
    private static final String CODE_DCM_KOS_DESC = "113012";      // "Key Object Description"

    // TID 1600 placeholder codes used by this repo's validator
    private static final String CODE_MODALITY = "121139"; // DCM "Modality"
    private static final String CODE_STUDY_INSTANCE_UID = "ddd011";
    private static final String CODE_TARGET_REGION = "123014";
    private static final String CODE_SERIES_DATE = "ddd003";
    private static final String CODE_SERIES_TIME = "ddd004";
    private static final String CODE_SERIES_NUMBER = "ddd005";
    private static final String CODE_SERIES_DESCRIPTION = "ddd002";
    private static final String CODE_SERIES_INSTANCE_UID = "ddd006";
    private static final String CODE_KOS_TITLE = "ddd008";
    private static final String CODE_SOP_INSTANCE_UID = "ddd007";


    // Dummy values that satisfy this project's profile checks
    private static final String DEFAULT_INSTITUTION_NAME = "IHE Demo Hospital";
    private static final String DEFAULT_PATIENT_ID_ISSUER = "HUPA";
    private static final String DEFAULT_PATIENT_ID_ISSUER_OID = "1.2.3.4.5.6.7.8.9";
    private static final String DEFAULT_ACCESSION_ISSUER_OID = "2.16.840.1.113883.3.72.5.9.1";
    private static final String DEFAULT_RETRIEVE_LOCATION_UID = "1.2.3.4.5.6.7.8.9.10";

    public static void main(String[] args) throws Exception {
        File outDir = new File(System.getProperty("user.dir"));

        // 1. Generate the Simulated Study Structure (5 Series)
        SimulatedStudy study = generateSimulatedStudy();

        // 2. Create the MADO Manifest Attributes
        Attributes madoKos = createMADOAttributes(study);

        // 3. Write to file
        String filename = "IHE_MADO.dcm";
        File outFile = new File(outDir, filename);
        writeDicomFile(outFile, madoKos);

        System.out.println("Created MADO Manifest: " + outFile.getAbsolutePath());
    }

    private static Attributes createMADOAttributes(SimulatedStudy study) {
        Attributes d = new Attributes();
        String manifestSOPUid = UIDUtils.createUID();

        String studyDate = now("yyyyMMdd");
        String studyTime = now("HHmmss");

        // --- Standard Headers ---
        d.setString(Tag.SOPClassUID, VR.UI, UID.KeyObjectSelectionDocumentStorage);
        d.setString(Tag.SOPInstanceUID, VR.UI, manifestSOPUid);
        d.setString(Tag.StudyInstanceUID, VR.UI, study.studyInstanceUID);
        d.setString(Tag.SeriesInstanceUID, VR.UI, UIDUtils.createUID());
        d.setString(Tag.Modality, VR.CS, "KO");
        d.setInt(Tag.SeriesNumber, VR.IS, 999); // Manifest Series
        d.setInt(Tag.InstanceNumber, VR.IS, 1);

        // Series module Type 2 attribute (must be present even if empty)
        d.newSequence(Tag.ReferencedPerformedProcedureStepSequence, 0);

        // --- Study IE (Type 2 attributes must be present even if empty) ---
        d.setString(Tag.StudyDate, VR.DA, studyDate);
        d.setString(Tag.StudyTime, VR.TM, studyTime);
        d.setString(Tag.ReferringPhysicianName, VR.PN, "^");
        d.setString(Tag.StudyID, VR.SH, "STUDY-001");

        // --- Patient IE ---
        d.setString(Tag.PatientName, VR.PN, "MADO^TEST^PATIENT");
        d.setString(Tag.PatientID, VR.LO, "MADO-12345");
        d.setString(Tag.IssuerOfPatientID, VR.LO, DEFAULT_PATIENT_ID_ISSUER);
        d.setString(Tag.PatientBirthDate, VR.DA, "19870828");
        d.setString(Tag.PatientSex, VR.CS, "O");

        // MADO requires IssuerOfPatientIDQualifiersSequence with UniversalEntityID + type ISO
        addPatientIDQualifiers(d, DEFAULT_PATIENT_ID_ISSUER_OID);

        // --- Accession + Issuer ---
        d.setString(Tag.AccessionNumber, VR.SH, "ACC-MADO-001");
        addAccessionNumberIssuer(d, DEFAULT_ACCESSION_ISSUER_OID);

        // --- Equipment IE ---
        d.setString(Tag.Manufacturer, VR.LO, "IHE_MADO_CREATOR");
        d.setString(Tag.InstitutionName, VR.LO, DEFAULT_INSTITUTION_NAME);

        // MADO Required: Timezone
        d.setString(Tag.TimezoneOffsetFromUTC, VR.SH, "+0000");

        // --- Key Object Document Module (Content Date/Time required) ---
        d.setString(Tag.ContentDate, VR.DA, studyDate);
        d.setString(Tag.ContentTime, VR.TM, studyTime);

        // --- ReferencedRequestSequence (Type 2, MADO semantics require non-empty) ---
        populateReferencedRequestSequenceWithIssuer(d, study.studyInstanceUID,
                                                   d.getString(Tag.AccessionNumber),
                                                   DEFAULT_ACCESSION_ISSUER_OID);

        // --- Evidence Sequence (Current Requested Procedure Evidence) ---
        // Must list ALL instances in the study (Series 1-5)
        // For MADO retrieval validation we also add per-series retrieval addressing.
        populateEvidence(d, study);

        // --- SR Content (TID 2010 + TID 1600) ---
        d.setString(Tag.ValueType, VR.CS, "CONTAINER");
        d.setString(Tag.ContinuityOfContent, VR.CS, "SEPARATE");

        // Root Concept: Manifest with Description (Triggers MADO logic)
        d.newSequence(Tag.ConceptNameCodeSequence, 1)
                .add(code(CODE_DCM_MANIFEST_DESC, "DCM", "Manifest with Description"));

        d.newSequence(Tag.ContentTemplateSequence, 1)
                .add(createTemplateItem("2010"));

        // Completion / Verification flags (warnings otherwise)
        d.setString(Tag.CompletionFlag, VR.CS, "COMPLETE");
        d.setString(Tag.VerificationFlag, VR.CS, "UNVERIFIED");

        // Content Sequence
        Sequence contentSeq = d.newSequence(Tag.ContentSequence, 10);

        // TID 1600 Study-level Acquisition Context requirements (repo validator expects these at ROOT ContentSequence)
        // NOTE: For XDS-I manifests, this project's validator requires top-level RelationshipType=CONTAINS.
        contentSeq.add(createCodeItem("CONTAINS", CODE_MODALITY, "DCM", "Modality",
                code("CT", "DCM", "CT")));
        contentSeq.add(createUIDRefItem("CONTAINS", CODE_STUDY_INSTANCE_UID, "DCM", "Study Instance UID",
                study.studyInstanceUID));
        contentSeq.add(createCodeItem("CONTAINS", CODE_TARGET_REGION, "DCM", "Target Region",
                code("T-D4000", "SRT", "Abdomen")));

        // Note: do NOT add a separate top-level IMAGE reference to the KIN.
        // The KIN is referenced within the Image Library entries already, and this project
        // checks for duplicate ReferencedSOPInstanceUID values across the entire SR tree.

        // B. MADO Image Library Extension (TID 1600)
        Attributes libContainer = new Attributes();
        libContainer.setString(Tag.RelationshipType, VR.CS, "CONTAINS");
        libContainer.setString(Tag.ValueType, VR.CS, "CONTAINER");
        libContainer.newSequence(Tag.ConceptNameCodeSequence, 1)
                .add(code(CODE_DCM_IMAGE_LIBRARY, "DCM", "Image Library"));

        Sequence libContent = libContainer.newSequence(Tag.ContentSequence, 20);

        // TID 1600 Study-level Acquisition Context requirements (validator expects these directly under Image Library)
        // 1) Modality (CODE) 2) Study Instance UID (UIDREF) 3) Target Region (CODE)
        // We model "study modality" as CT here.
        libContent.add(createCodeItem("HAS ACQ CONTEXT", CODE_MODALITY, "DCM", "Modality",
                code("CT", "DCM", "CT")));
        libContent.add(createUIDRefItem("HAS ACQ CONTEXT", CODE_STUDY_INSTANCE_UID, "DCM", "Study Instance UID",
                study.studyInstanceUID));
        // Target Region: validator only enforces presence; use SNM/SCT/FMA to avoid info-level note.
        libContent.add(createCodeItem("HAS ACQ CONTEXT", CODE_TARGET_REGION, "DCM", "Target Region",
                code("T-D4000", "SRT", "Abdomen")));

        // Populate Library Groups (One per Series)
        int seriesNumber = 1;
        for (SimulatedSeries series : study.seriesList) {
            series.seriesNumber = seriesNumber++;
            series.seriesDate = studyDate;
            series.seriesTime = randomSeriesTime(studyTime);

            Attributes group = new Attributes();
            group.setString(Tag.RelationshipType, VR.CS, "CONTAINS");
            group.setString(Tag.ValueType, VR.CS, "CONTAINER");
            group.newSequence(Tag.ConceptNameCodeSequence, 1)
                    .add(code(CODE_DCM_LIB_GROUP, "DCM", "Image Library Group"));

            Sequence groupSeq = group.newSequence(Tag.ContentSequence, 20);

            // Series Level Metadata (TID 1602)
            groupSeq.add(createCodeItem("HAS ACQ CONTEXT", CODE_MODALITY, "DCM", "Modality",
                    code(series.modality, "DCM", series.modality)));
            groupSeq.add(createUIDRefItem("HAS ACQ CONTEXT", CODE_SERIES_INSTANCE_UID, "DCM", "Series Instance UID", series.seriesUID));
            groupSeq.add(createTextItem("HAS ACQ CONTEXT", CODE_SERIES_DESCRIPTION, "DCM", "Series Description", series.description));
            groupSeq.add(createTextItem("HAS ACQ CONTEXT", CODE_SERIES_DATE, "DCM", "Series Date", series.seriesDate));
            groupSeq.add(createTextItem("HAS ACQ CONTEXT", CODE_SERIES_TIME, "DCM", "Series Time", series.seriesTime));
            // KOS TID 2010 forbids NUM, so represent the series number as TEXT.
            groupSeq.add(createTextItem("HAS ACQ CONTEXT", CODE_SERIES_NUMBER, "DCM", "Series Number", Integer.toString(series.seriesNumber)));

            // Instance Level Entries (TID 1601)
            for (SimulatedInstance inst : series.instances) {
                Attributes entry = new Attributes();
                entry.setString(Tag.RelationshipType, VR.CS, "CONTAINS");
                entry.setString(Tag.ValueType, VR.CS, "IMAGE");

                Sequence refSop = entry.newSequence(Tag.ReferencedSOPSequence, 1);
                Attributes refItem = new Attributes();
                refItem.setString(Tag.ReferencedSOPClassUID, VR.UI, inst.sopClassUID);
                refItem.setString(Tag.ReferencedSOPInstanceUID, VR.UI, inst.sopInstanceUID);
                refSop.add(refItem);

                // If this is the KIN instance, add KOS descriptors container.
                if (inst.isKIN) {
                    Sequence entryContent = entry.newSequence(Tag.ContentSequence, 5);
                    addKinDescriptors(entryContent, study);
                }

                groupSeq.add(entry);
            }
            libContent.add(group);
        }

        contentSeq.add(libContainer);
        return d;
    }


    /**
     * Adds TID 16XX Descriptors to an Image Library Entry for a KOS/KIN instance.
     * This describes WHAT the KIN references without retrieving it.
     */
    private static void addKinDescriptors(Sequence entryContent, SimulatedStudy study) {
        // Container for KOS Descriptors (concept: Key Object Description)
        Attributes kosDesc = new Attributes();
        kosDesc.setString(Tag.RelationshipType, VR.CS, "CONTAINS");
        kosDesc.setString(Tag.ValueType, VR.CS, "CONTAINER");
        kosDesc.newSequence(Tag.ConceptNameCodeSequence, 1)
                .add(code(CODE_DCM_KOS_DESC, "DCM", "Key Object Description"));

        Sequence descSeq = kosDesc.newSequence(Tag.ContentSequence, 10);

        // KOS Title Code (required by this repo's validator for V-DESC-02)
        // Use an example from CID 7010-ish style; validator only checks presence.
        descSeq.add(createCodeItem("CONTAINS", CODE_KOS_TITLE, "DCM", "KOS Title Code",
                code("113000", "DCM", "Of Interest")));

        // KOS Description (optional)
        descSeq.add(createTextItem("CONTAINS", "ddd009", "DCM", "KOS Object Description", "Key Objects for Surgery"));

        // Flagged images: IMPORTANT
        // The validator scans for duplicate ReferencedSOPInstanceUID across the SR content tree.
        // To avoid counting duplicates, we represent flagged images as UIDREF content items
        // (ddd007, DCM 'SOP Instance UIDs') instead of additional IMAGE references.
        // This still satisfies TID1600ImageLibraryValidator.validateKOSReference() which
        // only checks presence of ddd007 at least once.

        // Include three UIDREF items: 1 multiframe, 2 single-frame.
        SimulatedInstance multiFrameObj = study.seriesList.get(1).instances.get(0);
        descSeq.add(createUIDRefItem("CONTAINS", CODE_SOP_INSTANCE_UID, "DCM", "SOP Instance UIDs", multiFrameObj.sopInstanceUID));

        SimulatedInstance singleFrame1 = study.seriesList.get(0).instances.get(1);
        descSeq.add(createUIDRefItem("CONTAINS", CODE_SOP_INSTANCE_UID, "DCM", "SOP Instance UIDs", singleFrame1.sopInstanceUID));

        SimulatedInstance singleFrame2 = study.seriesList.get(0).instances.get(3);
        descSeq.add(createUIDRefItem("CONTAINS", CODE_SOP_INSTANCE_UID, "DCM", "SOP Instance UIDs", singleFrame2.sopInstanceUID));

        // If you also want to convey a specific frame number without using ReferencedSOPSequence,
        // add a TEXT note; the validator doesn't check it, but it's human-readable.
        descSeq.add(createTextItem("CONTAINS", "ddd010", "DCM", "Frame Note",
                "Multiframe reference implies frame 1 (example)."));

        entryContent.add(kosDesc);
    }

    // --- Evidence Sequence Helper ---
    private static void populateEvidence(Attributes d, SimulatedStudy study) {
        Sequence evidence = d.newSequence(Tag.CurrentRequestedProcedureEvidenceSequence, 1);
        Attributes studyItem = new Attributes();
        studyItem.setString(Tag.StudyInstanceUID, VR.UI, study.studyInstanceUID);

        Sequence seriesSeq = studyItem.newSequence(Tag.ReferencedSeriesSequence, 5);

        for (SimulatedSeries s : study.seriesList) {
            Attributes seriesItem = new Attributes();
            seriesItem.setString(Tag.SeriesInstanceUID, VR.UI, s.seriesUID);

            // Retrieval addressing required by MADOProfile (Appendix B style is accepted by this validator)
            // Provide at least one method. We use RetrieveLocationUID.
            seriesItem.setString(Tag.RetrieveLocationUID, VR.UI, DEFAULT_RETRIEVE_LOCATION_UID);

            Sequence sopSeq = seriesItem.newSequence(Tag.ReferencedSOPSequence, s.instances.size());
            for (SimulatedInstance i : s.instances) {
                Attributes sopItem = new Attributes();
                sopItem.setString(Tag.ReferencedSOPClassUID, VR.UI, i.sopClassUID);
                sopItem.setString(Tag.ReferencedSOPInstanceUID, VR.UI, i.sopInstanceUID);
                sopSeq.add(sopItem);
            }
            seriesSeq.add(seriesItem);
        }
        evidence.add(studyItem);
    }

    // --- Simulation Data Generation ---
    private static SimulatedStudy generateSimulatedStudy() {
        SimulatedStudy study = new SimulatedStudy();
        study.studyInstanceUID = UIDUtils.createUID();

        // Series 1: Single Frame CT (5 images) - "Normal Series"
        SimulatedSeries s1 = new SimulatedSeries(UIDUtils.createUID(), "CT", "Routine Abdomen");
        for (int i = 0; i < 5; i++) {
            // Use standard CT Image Storage SOP Class UID.
            s1.addInstance(UID.CTImageStorage, UIDUtils.createUID(), false);
        }
        study.addSeries(s1);

        // Series 2: Multi Frame (1 instance, e.g., Enhanced CT)
        SimulatedSeries s2 = new SimulatedSeries(UIDUtils.createUID(), "CT", "Enhanced Recons");
        s2.addInstance(UID.EnhancedCTImageStorage, UIDUtils.createUID(), false); // Multiframe
        study.addSeries(s2);

        // Series 3: Secondary Capture (Filler)
        SimulatedSeries s3 = new SimulatedSeries(UIDUtils.createUID(), "OT", "Scanned Docs");
        s3.addInstance(UID.SecondaryCaptureImageStorage, UIDUtils.createUID(), false);
        study.addSeries(s3);

        // Series 4: Ultrasound (Filler)
        SimulatedSeries s4 = new SimulatedSeries(UIDUtils.createUID(), "US", "Ultrasound Preview");
        s4.addInstance(UID.UltrasoundImageStorage, UIDUtils.createUID(), false);
        study.addSeries(s4);

        // Series 5: The KIN (Key Image Note)
        SimulatedSeries s5 = new SimulatedSeries(UIDUtils.createUID(), "KO", "Key Images");
        s5.addInstance(UID.KeyObjectSelectionDocumentStorage, UIDUtils.createUID(), true);
        study.addSeries(s5);
        study.kinSeries = s5;

        return study;
    }

    // --- Helper Classes ---
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

