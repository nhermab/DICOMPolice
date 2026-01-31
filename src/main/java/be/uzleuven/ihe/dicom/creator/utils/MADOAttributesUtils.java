package be.uzleuven.ihe.dicom.creator.utils;

import be.uzleuven.ihe.dicom.creator.model.SimulatedInstance;
import be.uzleuven.ihe.dicom.creator.model.SimulatedSeries;
import be.uzleuven.ihe.dicom.creator.model.SimulatedStudy;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.data.UID;

import static be.uzleuven.ihe.dicom.constants.CodeConstants.*;
import static be.uzleuven.ihe.dicom.creator.utils.DicomCreatorUtils.*;
import static be.uzleuven.ihe.dicom.creator.utils.SRContentItemUtils.*;

/**
 * Utility class for creating MADO manifest attributes.
 */
public class MADOAttributesUtils {

    private static final String DEFAULT_INSTITUTION_NAME = "IHE Demo Hospital";
    private static final String DEFAULT_PATIENT_ID_ISSUER = "HUPA";
    private static final String DEFAULT_PATIENT_ID_ISSUER_OID = "1.2.3.4.5.6.7.8.9";
    private static final String DEFAULT_ACCESSION_ISSUER_OID = "2.16.840.1.113883.3.72.5.9.1";
    private static final String DEFAULT_RETRIEVE_LOCATION_UID = "1.2.3.4.5.6.7.8.9.10";

    private MADOAttributesUtils() {
        // Utility class
    }

    /**
     * Create MADO manifest attributes from a simulated study.
     */
    public static Attributes createMADOAttributes(SimulatedStudy study) {
        Attributes d = new Attributes();
        String manifestSOPUid = createNormalizedUid();

        String studyDate = now("yyyyMMdd");
        String studyTime = now("HHmmss");

        // --- Standard Headers ---
        d.setString(Tag.SOPClassUID, VR.UI, UID.KeyObjectSelectionDocumentStorage);
        d.setString(Tag.SOPInstanceUID, VR.UI, manifestSOPUid);
        d.setString(Tag.StudyInstanceUID, VR.UI, study.getStudyInstanceUID());
        d.setString(Tag.SeriesInstanceUID, VR.UI, createNormalizedUid());
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
        DicomSequenceUtils.addPatientIDQualifiers(d, DEFAULT_PATIENT_ID_ISSUER_OID);

        // --- Accession + Issuer ---
        d.setString(Tag.AccessionNumber, VR.SH, "ACC-MADO-001");
        DicomSequenceUtils.addAccessionNumberIssuer(d, DEFAULT_ACCESSION_ISSUER_OID);

        // --- Equipment IE ---
        d.setString(Tag.Manufacturer, VR.LO, "IHE_MADO_CREATOR");
        d.setString(Tag.InstitutionName, VR.LO, DEFAULT_INSTITUTION_NAME);

        // MADO Required: Timezone
        d.setString(Tag.TimezoneOffsetFromUTC, VR.SH, "+0000");

        // --- Key Object Document Module (Content Date/Time required) ---
        d.setString(Tag.ContentDate, VR.DA, studyDate);
        d.setString(Tag.ContentTime, VR.TM, studyTime);

        // --- ReferencedRequestSequence (Type 2, MADO semantics require non-empty) ---
        DicomSequenceUtils.populateReferencedRequestSequenceWithIssuer(d, study.getStudyInstanceUID(),
                                                   d.getString(Tag.AccessionNumber),
                                                   DEFAULT_ACCESSION_ISSUER_OID);

        // --- Evidence Sequence (Current Requested Procedure Evidence) ---
        populateEvidence(d, study);

        // --- SR Content (TID 2010 + TID 1600) ---
        populateSRContent(d, study, studyDate, studyTime);

        return d;
    }

    /**
     * Populate the Evidence Sequence with all instances in the study.
     */
    private static void populateEvidence(Attributes d, SimulatedStudy study) {
        Sequence evidence = d.newSequence(Tag.CurrentRequestedProcedureEvidenceSequence, 1);
        Attributes studyItem = new Attributes();
        studyItem.setString(Tag.StudyInstanceUID, VR.UI, study.getStudyInstanceUID());

        int seriesSize = Math.max(1, study.getSeriesList().size());
        Sequence seriesSeq = studyItem.newSequence(Tag.ReferencedSeriesSequence, seriesSize);

        for (SimulatedSeries s : study.getSeriesList()) {
            Attributes seriesItem = new Attributes();
            seriesItem.setString(Tag.SeriesInstanceUID, VR.UI, s.getSeriesUID());

            // Retrieval addressing required by MADOProfile (Appendix B style is accepted by this validator)
            seriesItem.setString(Tag.RetrieveLocationUID, VR.UI, DEFAULT_RETRIEVE_LOCATION_UID);

            Sequence sopSeq = seriesItem.newSequence(Tag.ReferencedSOPSequence, Math.max(1, s.getInstances().size()));
            for (SimulatedInstance i : s.getInstances()) {
                Attributes sopItem = new Attributes();
                sopItem.setString(Tag.ReferencedSOPClassUID, VR.UI, i.getSopClassUID());
                sopItem.setString(Tag.ReferencedSOPInstanceUID, VR.UI, i.getSopInstanceUID());
                sopSeq.add(sopItem);
            }
            seriesSeq.add(seriesItem);
        }
        evidence.add(studyItem);
    }

    /**
     * Populate the SR Content Sequence with TID 2010 and TID 1600 content.
     */
    private static void populateSRContent(Attributes d, SimulatedStudy study, String studyDate, String studyTime) {
        d.setString(Tag.ValueType, VR.CS, "CONTAINER");
        d.setString(Tag.ContinuityOfContent, VR.CS, be.uzleuven.ihe.dicom.constants.DicomConstants.CONTINUITY_SEPARATE);

        // Root Concept: Manifest with Description (Triggers MADO logic)
        d.newSequence(Tag.ConceptNameCodeSequence, 1)
                .add(code(CODE_MANIFEST_WITH_DESCRIPTION, SCHEME_DCM, MEANING_MANIFEST_WITH_DESCRIPTION));

        d.newSequence(Tag.ContentTemplateSequence, 1)
                .add(createTemplateItem("2010"));

        // Completion / Verification flags
        /*d.setString(Tag.CompletionFlag, VR.CS, be.uzleuven.ihe.dicom.constants.DicomConstants.COMPLETION_FLAG_COMPLETE);
        d.setString(Tag.VerificationFlag, VR.CS, be.uzleuven.ihe.dicom.constants.DicomConstants.VERIFICATION_FLAG_UNVERIFIED);
        */
        // Content Sequence
        Sequence contentSeq = d.newSequence(Tag.ContentSequence, 10);

        // TID 2010 requires Key Object Description (113012, DCM) as first item
        Attributes keyObjDesc = createTextItem(be.uzleuven.ihe.dicom.constants.DicomConstants.RELATIONSHIP_CONTAINS,
            CODE_KOS_DESCRIPTION, SCHEME_DCM, MEANING_KOS_DESCRIPTION, "Manifest with Description");
        contentSeq.add(keyObjDesc);

        // TID 1600 Study-level Acquisition Context requirements
        addStudyLevelContext(contentSeq, study);

        // MADO Image Library Extension (TID 1600)
        Attributes libContainer = createImageLibraryContainer(study, studyDate, studyTime);
        contentSeq.add(libContainer);
    }

    /**
     * Add study-level acquisition context items.
     */
    private static void addStudyLevelContext(Sequence contentSeq, SimulatedStudy study) {
        contentSeq.add(createCodeItem(be.uzleuven.ihe.dicom.constants.DicomConstants.RELATIONSHIP_CONTAINS,
            CODE_MODALITY, SCHEME_DCM, MEANING_MODALITY,
            code(CODE_MODALITY_CT, SCHEME_DCM, MEANING_MODALITY_CT)));
        contentSeq.add(createUIDRefItem(be.uzleuven.ihe.dicom.constants.DicomConstants.RELATIONSHIP_CONTAINS,
            CODE_STUDY_INSTANCE_UID, SCHEME_DCM, MEANING_STUDY_INSTANCE_UID,
            study.getStudyInstanceUID()));
        contentSeq.add(createCodeItem(be.uzleuven.ihe.dicom.constants.DicomConstants.RELATIONSHIP_CONTAINS,
            CODE_TARGET_REGION, SCHEME_DCM, MEANING_TARGET_REGION,
            code(CODE_REGION_ABDOMEN, SCHEME_SRT, MEANING_REGION_ABDOMEN)));
    }

    /**
     * Create the Image Library container with all series groups.
     */
    private static Attributes createImageLibraryContainer(SimulatedStudy study, String studyDate, String studyTime) {
        Attributes libContainer = new Attributes();
        libContainer.setString(Tag.RelationshipType, VR.CS, be.uzleuven.ihe.dicom.constants.DicomConstants.RELATIONSHIP_CONTAINS);
        libContainer.setString(Tag.ValueType, VR.CS, "CONTAINER");
        libContainer.newSequence(Tag.ConceptNameCodeSequence, 1)
                .add(code(CODE_IMAGE_LIBRARY, SCHEME_DCM, MEANING_IMAGE_LIBRARY));

        Sequence libContent = libContainer.newSequence(Tag.ContentSequence, 20);

        // TID 1600 Study-level Acquisition Context requirements
        libContent.add(createCodeItem("HAS ACQ CONTEXT", CODE_MODALITY, SCHEME_DCM, MEANING_MODALITY,
                code(CODE_MODALITY_CT, SCHEME_DCM, MEANING_MODALITY_CT)));
        libContent.add(createUIDRefItem("HAS ACQ CONTEXT", CODE_STUDY_INSTANCE_UID, SCHEME_DCM, MEANING_STUDY_INSTANCE_UID,
                study.getStudyInstanceUID()));
        libContent.add(createCodeItem("HAS ACQ CONTEXT", CODE_TARGET_REGION, SCHEME_DCM, MEANING_TARGET_REGION,
                code(CODE_REGION_ABDOMEN, SCHEME_SRT, MEANING_REGION_ABDOMEN)));

        // Populate Library Groups (One per Series)
        int seriesNumber = 1;
        for (SimulatedSeries series : study.getSeriesList()) {
            series.setSeriesNumber(seriesNumber++);
            series.setSeriesDate(studyDate);
            series.setSeriesTime(randomSeriesTime(studyTime));

            Attributes group = MADOContentUtils.createImageLibraryGroup(series, study);
            libContent.add(group);
        }

        return libContainer;
    }
}
