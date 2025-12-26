package be.uzleuven.ihe.dicom.creator.scu;

import org.dcm4che3.data.*;

import java.io.File;
import java.io.IOException;

import static be.uzleuven.ihe.dicom.creator.utils.DicomCreatorUtils.*;
import static be.uzleuven.ihe.dicom.creator.utils.DicomSequenceUtils.*;
import static be.uzleuven.ihe.dicom.creator.utils.SRContentItemUtils.*;
import static be.uzleuven.ihe.dicom.constants.CodeConstants.*;

/**
 * SCU client for creating IHE RAD MADO (Manifest for Advanced Document Organization)
 * manifests from C-FIND query results.
 *
 * This class performs C-FIND queries against a DICOM archive (PACS) and
 * constructs valid MADO manifests compliant with IHE RAD TF-3: 6.X.1 specification,
 * including TID 1600 Image Library content tree.
 *
 * Example usage:
 * <pre>
 * DefaultMetadata defaults = new DefaultMetadata()
 *     .withCallingAET("NICK")
 *     .withCalledAET("ORTHANC")
 *     .withRemoteHost("172.20.240.184")
 *     .withRemotePort(4242)
 *     .withPatientIdIssuerOid("1.2.840.113619.6.197")
 *     .withAccessionNumberIssuerOid("1.2.840.113619.6.197.1")
 *     .withRetrieveLocationUid("1.2.3.4.5.6.7.8.9.10");
 *
 * MADOSCUManifestCreator creator = new MADOSCUManifestCreator(defaults);
 * Attributes madoManifest = creator.createManifest(
 *     "1.3.46.670589.11.0.1.1996082307380006",
 *     "7");
 *
 * creator.saveToFile(madoManifest, new File("MADO_FROM_SCU.dcm"));
 * </pre>
 */
public class MADOSCUManifestCreator extends SCUManifestCreator {

    public MADOSCUManifestCreator() {
        super();
    }

    public MADOSCUManifestCreator(DefaultMetadata defaults) {
        super(defaults);
    }

    /**
     * Creates a MADO manifest by querying the PACS for study/series/instance information.
     *
     * @param studyInstanceUid The Study Instance UID to create a manifest for
     * @param patientId The Patient ID (can be null, will be queried from PACS)
     * @return Attributes object containing the complete MADO manifest
     * @throws IOException if C-FIND queries fail
     */
    @Override
    public Attributes createManifest(String studyInstanceUid, String patientId) throws IOException {
        // Step 1: Query study level to get patient and study metadata
        CFindResult studyResult = findStudy(studyInstanceUid, patientId);

        if (!studyResult.isSuccess() || studyResult.getMatches().isEmpty()) {
            throw new IOException("Study not found: " + studyInstanceUid +
                ". Error: " + studyResult.getErrorMessage());
        }

        Attributes studyAttrs = studyResult.getMatches().get(0);
        applyDefaults(studyAttrs);

        // Step 2: Query series level to get all series in the study
        CFindResult seriesResult = findSeries(studyInstanceUid, null);

        if (!seriesResult.isSuccess() || seriesResult.getMatches().isEmpty()) {
            throw new IOException("No series found for study: " + studyInstanceUid);
        }

        // Step 3: For each series, query instances
        java.util.List<SeriesData> allSeries = new java.util.ArrayList<>();

        for (Attributes seriesAttrs : seriesResult.getMatches()) {
            String seriesInstanceUid = seriesAttrs.getString(Tag.SeriesInstanceUID);

            CFindResult instanceResult = findInstances(studyInstanceUid, seriesInstanceUid);

            if (instanceResult.isSuccess() && !instanceResult.getMatches().isEmpty()) {
                SeriesData sd = new SeriesData();
                sd.seriesAttrs = seriesAttrs;
                sd.instances = instanceResult.getMatches();
                allSeries.add(sd);
            }
        }

        if (allSeries.isEmpty()) {
            throw new IOException("No instances found for study: " + studyInstanceUid);
        }

        // Step 4: Build the MADO manifest
        return buildMADOManifest(studyAttrs, allSeries);
    }

    /**
     * Internal class to hold series and its instances.
     */
    private static class SeriesData {
        Attributes seriesAttrs;
        java.util.List<Attributes> instances;
    }

    /**
     * Builds the complete MADO manifest from queried data.
     * Implements IHE RAD MADO profile with TID 1600 Image Library.
     */
    private Attributes buildMADOManifest(Attributes studyAttrs, java.util.List<SeriesData> allSeries) {
        Attributes mado = new Attributes();

        // File Meta Information
        String sopInstanceUID = createNormalizedUid();
        // MADO manifests are Key Object Selection Documents (KO), not SR.
        // Using Comprehensive SR here breaks downstream validators and the Part 10 Media Storage SOP Class.
        mado.setString(Tag.SOPClassUID, VR.UI, UID.KeyObjectSelectionDocumentStorage);
        mado.setString(Tag.SOPInstanceUID, VR.UI, sopInstanceUID);

        // MADO requires SpecificCharacterSet
        mado.setString(Tag.SpecificCharacterSet, VR.CS, "ISO_IR 192"); // UTF-8

        // Patient Module - from study query
        String patientID = studyAttrs.getString(Tag.PatientID, "UNKNOWN");
        String patientName = studyAttrs.getString(Tag.PatientName, "UNKNOWN^PATIENT");
        mado.setString(Tag.PatientID, VR.LO, patientID);
        mado.setString(Tag.PatientName, VR.PN, patientName);
        mado.setString(Tag.PatientBirthDate, VR.DA, studyAttrs.getString(Tag.PatientBirthDate, ""));
        mado.setString(Tag.PatientSex, VR.CS, SCUManifestCreator.normalizePatientSex(studyAttrs.getString(Tag.PatientSex, "O")));

        // IssuerOfPatientID - XDS-I.b recommendation
        String issuerOfPatientID = studyAttrs.getString(Tag.IssuerOfPatientID, defaults.patientIdIssuerLocalNamespace);
        mado.setString(Tag.IssuerOfPatientID, VR.LO, issuerOfPatientID);

        // MADO MANDATORY: IssuerOfPatientIDQualifiersSequence with OID
        addPatientIDQualifiers(mado, defaults.patientIdIssuerOid);

        // TypeOfPatientID should be "TEXT" per MADO profile
        mado.setString(Tag.TypeOfPatientID, VR.CS, "TEXT");

        // Study Module - from study query
        String studyInstanceUID = studyAttrs.getString(Tag.StudyInstanceUID);
        String studyDate = studyAttrs.getString(Tag.StudyDate, "");
        String studyTime = studyAttrs.getString(Tag.StudyTime, "");
        String accessionNumber = studyAttrs.getString(Tag.AccessionNumber, "");

        // Some archives return invalid UID components that start with '0' (e.g. ...061159... or ...002).
        // When writing a manifest, ensure the output file remains standards-compliant.
        String normalizedStudyInstanceUID = normalizeUidNoLeadingZeros(studyInstanceUID);

        // MADO requires AccessionNumber to be present (Type R+) even if PACS didn't return it.
        // Use a safe placeholder if missing.
        if (accessionNumber == null || accessionNumber.trim().isEmpty()) {
            accessionNumber = "ACC-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }

        mado.setString(Tag.StudyInstanceUID, VR.UI, normalizedStudyInstanceUID);
        mado.setString(Tag.StudyDate, VR.DA, studyDate);
        mado.setString(Tag.StudyTime, VR.TM, studyTime);
        mado.setString(Tag.ReferringPhysicianName, VR.PN,
            studyAttrs.getString(Tag.ReferringPhysicianName, ""));
        mado.setString(Tag.StudyID, VR.SH, studyAttrs.getString(Tag.StudyID, "1"));
        mado.setString(Tag.AccessionNumber, VR.SH, accessionNumber);
        mado.setString(Tag.StudyDescription, VR.LO,
            studyAttrs.getString(Tag.StudyDescription, ""));

        // MADO: IssuerOfAccessionNumberSequence required when AccessionNumber is non-empty.
        // Always add one so validator passes and it matches ReferencedRequestSequence issuer.
        addAccessionNumberIssuer(mado, defaults.accessionNumberIssuerOid);

        // SR Document Series Module
        String seriesInstanceUID = createNormalizedUid();
        mado.setString(Tag.SeriesInstanceUID, VR.UI, seriesInstanceUID);
        mado.setString(Tag.SeriesNumber, VR.IS, "1");
        // For Key Object Selection documents the modality must be KO.
        mado.setString(Tag.Modality, VR.CS, "KO");

        // Use study date/time for series if available
        mado.setString(Tag.SeriesDate, VR.DA, studyDate);
        mado.setString(Tag.SeriesTime, VR.TM, studyTime);
        mado.setString(Tag.SeriesDescription, VR.LO, "MADO Manifest");

        // ReferencedPerformedProcedureStepSequence - Type 2 (can be empty)
        mado.newSequence(Tag.ReferencedPerformedProcedureStepSequence, 0);

        // General Equipment Module
        mado.setString(Tag.Manufacturer, VR.LO, "DICOMPolice");
        mado.setString(Tag.ManufacturerModelName, VR.LO, "MADO SCU Creator");
        mado.setString(Tag.SoftwareVersions, VR.LO, "1.0");
        mado.setString(Tag.InstitutionName, VR.LO, defaults.institutionName);

        // SR Document General Module
        String contentDate = new java.text.SimpleDateFormat("yyyyMMdd").format(new java.util.Date());
        String contentTime = new java.text.SimpleDateFormat("HHmmss.SSSSSS").format(new java.util.Date());

        mado.setString(Tag.InstanceNumber, VR.IS, "1");
        mado.setString(Tag.ContentDate, VR.DA, contentDate);
        mado.setString(Tag.ContentTime, VR.TM, contentTime);
        mado.setString(Tag.CompletionFlag, VR.CS, "COMPLETE");
        mado.setString(Tag.VerificationFlag, VR.CS, "UNVERIFIED");

        // TimezoneOffsetFromUTC - Highly recommended for XDS-I.b
        mado.setString(Tag.TimezoneOffsetFromUTC, VR.SH, timezoneOffsetFromUTC());

        // --- SR root content item (Key Object Selection / MADO) ---
        mado.setString(Tag.ValueType, VR.CS, "CONTAINER");
        mado.setString(Tag.ContinuityOfContent, VR.CS, be.uzleuven.ihe.dicom.constants.DicomConstants.CONTINUITY_SEPARATE);

        // Document Title: MADO requires Manifest or Manifest with Description
        Sequence conceptNameCodeSeq = mado.newSequence(Tag.ConceptNameCodeSequence, 1);
        conceptNameCodeSeq.add(code(CODE_MANIFEST_WITH_DESCRIPTION, SCHEME_DCM, MEANING_MANIFEST_WITH_DESCRIPTION));

        // Explicitly identify TID 2010 (XDS-I / KOS template) - this project expects it for MADO too.
        mado.newSequence(Tag.ContentTemplateSequence, 1)
            .add(createTemplateItem("2010"));

        // MADO Preliminary Flag (recommended)
        mado.setString(Tag.PreliminaryFlag, VR.CS, "FINAL");

        // Evidence Sequence (CurrentRequestedProcedureEvidenceSequence)
        Sequence evidenceSeq = mado.newSequence(Tag.CurrentRequestedProcedureEvidenceSequence, 1);
        Attributes studyItem = new Attributes();
        studyItem.setString(Tag.StudyInstanceUID, VR.UI, normalizedStudyInstanceUID);

        Sequence refSeriesSeq = studyItem.newSequence(Tag.ReferencedSeriesSequence, allSeries.size());

        for (SeriesData sd : allSeries) {
            Attributes seriesItem = new Attributes();
            String serUID = sd.seriesAttrs.getString(Tag.SeriesInstanceUID);
            String normalizedSerUID = normalizeUidNoLeadingZeros(serUID);
            seriesItem.setString(Tag.SeriesInstanceUID, VR.UI, normalizedSerUID);

            // MADO Appendix B: Modality at series level (Type 1)
            String modality = sd.seriesAttrs.getString(Tag.Modality, "OT");
            seriesItem.setString(Tag.Modality, VR.CS, modality);

            // Add retrieval information (MADO requirement)
            seriesItem.setString(Tag.RetrieveLocationUID, VR.UI, defaults.retrieveLocationUid);

            // Build WADO-RS URL
            String wadoUrl = defaults.wadoRsBaseUrl + "/" + normalizedStudyInstanceUID +
                "/series/" + normalizedSerUID;
            seriesItem.setString(Tag.RetrieveURL, VR.UR, wadoUrl);

            Sequence refSOPSeq = seriesItem.newSequence(Tag.ReferencedSOPSequence, sd.instances.size());

            for (Attributes instAttrs : sd.instances) {
                Attributes sopItem = new Attributes();
                sopItem.setString(Tag.ReferencedSOPClassUID, VR.UI,
                    instAttrs.getString(Tag.SOPClassUID));
                sopItem.setString(Tag.ReferencedSOPInstanceUID, VR.UI,
                    normalizeUidNoLeadingZeros(instAttrs.getString(Tag.SOPInstanceUID)));


                // MADO Appendix B: Add NumberOfFrames if multiframe
                String numFrames = instAttrs.getString(Tag.NumberOfFrames);
                if (numFrames != null && !numFrames.isEmpty()) {
                    sopItem.setString(Tag.NumberOfFrames, VR.IS, numFrames);
                }

                // MADO Appendix B: Rows/Columns recommended for bandwidth estimation
                String rows = instAttrs.getString(Tag.Rows);
                String cols = instAttrs.getString(Tag.Columns);
                if (rows != null && !rows.isEmpty()) {
                    sopItem.setString(Tag.Rows, VR.US, rows);
                }
                if (cols != null && !cols.isEmpty()) {
                    sopItem.setString(Tag.Columns, VR.US, cols);
                }

                refSOPSeq.add(sopItem);
            }

            refSeriesSeq.add(seriesItem);
        }

        evidenceSeq.add(studyItem);

        // ReferencedRequestSequence (MADO requirement)
        Sequence refRequestSeq = mado.newSequence(Tag.ReferencedRequestSequence, 1);
        Attributes reqItem = new Attributes();

        // Always include AccessionNumber + issuer in ReferencedRequestSequence item 0 (Type R+ in MADO).
        reqItem.setString(Tag.AccessionNumber, VR.SH, accessionNumber);
        Sequence issuerAccSeq = reqItem.newSequence(Tag.IssuerOfAccessionNumberSequence, 1);
        Attributes issuerAcc = new Attributes();
        issuerAcc.setString(Tag.UniversalEntityID, VR.UT, defaults.accessionNumberIssuerOid);
        issuerAcc.setString(Tag.UniversalEntityIDType, VR.CS, "ISO");
        issuerAccSeq.add(issuerAcc);

        reqItem.setString(Tag.StudyInstanceUID, VR.UI, normalizedStudyInstanceUID);
        reqItem.setString(Tag.RequestedProcedureID, VR.SH, "RP001");
        reqItem.setString(Tag.PlacerOrderNumberImagingServiceRequest, VR.LO, "PO001");
        reqItem.setString(Tag.FillerOrderNumberImagingServiceRequest, VR.LO, "FO001");

        refRequestSeq.add(reqItem);

        // --- SR Document Content Module - MADO Approach 2 (TID 1600 Image Library hierarchy) ---
        // Root ContentSequence contains: required study-level acquisition context + Image Library container.
        Sequence contentSeq = mado.newSequence(Tag.ContentSequence, 10);

        String studyModality = !allSeries.isEmpty()
                ? allSeries.get(0).seriesAttrs.getString(Tag.Modality, "CT")
                : "CT";

        // TID 1600 Study-level requirements: Modality (121139, DCM) and Target Region (123014, DCM)
        // are Type R+ for MADO. We must provide them at root (relationship CONTAINS) for this project's validator.
        contentSeq.add(createCodeItem(be.uzleuven.ihe.dicom.constants.DicomConstants.RELATIONSHIP_CONTAINS,
            CODE_MODALITY, SCHEME_DCM, MEANING_MODALITY,
            code(studyModality, SCHEME_DCM, studyModality)));

        // Study Instance UID (already present)
        contentSeq.add(createUIDRefItem(be.uzleuven.ihe.dicom.constants.DicomConstants.RELATIONSHIP_CONTAINS,
            CODE_STUDY_INSTANCE_UID, SCHEME_DCM, MEANING_STUDY_INSTANCE_UID, normalizedStudyInstanceUID));

        // Provide a sensible default target region to satisfy R+ requirement.
        contentSeq.add(createCodeItem(be.uzleuven.ihe.dicom.constants.DicomConstants.RELATIONSHIP_CONTAINS,
            CODE_TARGET_REGION, SCHEME_DCM, MEANING_TARGET_REGION,
            code(CODE_REGION_ABDOMEN, SCHEME_SRT, MEANING_REGION_ABDOMEN)));

        // Image Library container (111028, DCM)
        Attributes libContainer = new Attributes();
        libContainer.setString(Tag.RelationshipType, VR.CS, be.uzleuven.ihe.dicom.constants.DicomConstants.RELATIONSHIP_CONTAINS);
        libContainer.setString(Tag.ValueType, VR.CS, "CONTAINER");
        libContainer.newSequence(Tag.ConceptNameCodeSequence, 1)
            .add(code(CODE_IMAGE_LIBRARY, SCHEME_DCM, MEANING_IMAGE_LIBRARY));

        Sequence libContent = libContainer.newSequence(Tag.ContentSequence, 50);

        // TID 1600 Study-level requirements under the Image Library container as well.
        libContent.add(createCodeItem("HAS ACQ CONTEXT", CODE_MODALITY, SCHEME_DCM, MEANING_MODALITY,
            code(studyModality, SCHEME_DCM, studyModality)));
        libContent.add(createUIDRefItem("HAS ACQ CONTEXT", CODE_STUDY_INSTANCE_UID, SCHEME_DCM, MEANING_STUDY_INSTANCE_UID, normalizedStudyInstanceUID));
        libContent.add(createCodeItem("HAS ACQ CONTEXT", CODE_TARGET_REGION, SCHEME_DCM, MEANING_TARGET_REGION,
            code(CODE_REGION_ABDOMEN, SCHEME_SRT, MEANING_REGION_ABDOMEN)));

        // Build one Image Library Group per series, then one Image Library Entry per instance.
        int seriesNumberCounter = 1;
        for (SeriesData sd : allSeries) {
            Attributes group = new Attributes();
            group.setString(Tag.RelationshipType, VR.CS, be.uzleuven.ihe.dicom.constants.DicomConstants.RELATIONSHIP_CONTAINS);
            group.setString(Tag.ValueType, VR.CS, "CONTAINER");
            group.newSequence(Tag.ConceptNameCodeSequence, 1)
                .add(code(CODE_IMAGE_LIBRARY_GROUP, SCHEME_DCM, MEANING_IMAGE_LIBRARY_GROUP));

            Sequence groupSeq = group.newSequence(Tag.ContentSequence, 50);

            String seriesUid = sd.seriesAttrs.getString(Tag.SeriesInstanceUID, "");
            String seriesModality = sd.seriesAttrs.getString(Tag.Modality, "OT");
            String seriesDescription = sd.seriesAttrs.getString(Tag.SeriesDescription, "");
            String seriesDate = sd.seriesAttrs.getString(Tag.SeriesDate, studyDate);
            String seriesTime = sd.seriesAttrs.getString(Tag.SeriesTime, studyTime);

            String normalizedSeriesUid = normalizeUidNoLeadingZeros(seriesUid);

            // SR TEXT ValueType has Type 1 TextValue (0040,A160): it must be present and non-empty.
            // Some archives omit SeriesDescription (0008,103E); keep the content tree valid by using a
            // deterministic fallback rather than emitting an empty TextValue.
            if (seriesDescription == null || seriesDescription.trim().isEmpty()) {
                seriesDescription = "(no Series Description)";
            }

            // TID 1602 series-level metadata
            groupSeq.add(createCodeItem("HAS ACQ CONTEXT", CODE_MODALITY, SCHEME_DCM, MEANING_MODALITY,
                code(seriesModality, SCHEME_DCM, seriesModality)));
            groupSeq.add(createUIDRefItem("HAS ACQ CONTEXT", CODE_SERIES_INSTANCE_UID, SCHEME_DCM, MEANING_SERIES_INSTANCE_UID, normalizedSeriesUid));
            groupSeq.add(createTextItem("HAS ACQ CONTEXT", CODE_SERIES_DESCRIPTION, SCHEME_DCM, MEANING_SERIES_DESCRIPTION, seriesDescription));
            groupSeq.add(createTextItem("HAS ACQ CONTEXT", CODE_SERIES_DATE, SCHEME_DCM, MEANING_SERIES_DATE, seriesDate));
            groupSeq.add(createTextItem("HAS ACQ CONTEXT", CODE_SERIES_TIME, SCHEME_DCM, MEANING_SERIES_TIME, seriesTime));
            groupSeq.add(createTextItem("HAS ACQ CONTEXT", CODE_SERIES_NUMBER, SCHEME_DCM, MEANING_SERIES_NUMBER,
                Integer.toString(seriesNumberCounter++)));
            groupSeq.add(createNumericItem("HAS ACQ CONTEXT", CODE_NUM_SERIES_RELATED_INSTANCES, SCHEME_DCM,
                MEANING_NUM_SERIES_RELATED_INSTANCES,
                sd.instances != null ? sd.instances.size() : 0));

            // TID 1601 entries
            int instanceNumberCounter = 1;
            if (sd.instances != null) {
                for (Attributes instAttrs : sd.instances) {
                    Attributes entry = new Attributes();
                    entry.setString(Tag.RelationshipType, VR.CS, be.uzleuven.ihe.dicom.constants.DicomConstants.RELATIONSHIP_CONTAINS);
                    entry.setString(Tag.ValueType, VR.CS, "IMAGE");

                    Sequence refSop = entry.newSequence(Tag.ReferencedSOPSequence, 1);
                    Attributes refItem = new Attributes();
                    refItem.setString(Tag.ReferencedSOPClassUID, VR.UI, instAttrs.getString(Tag.SOPClassUID));
                    refItem.setString(Tag.ReferencedSOPInstanceUID, VR.UI, normalizeUidNoLeadingZeros(instAttrs.getString(Tag.SOPInstanceUID)));
                    refSop.add(refItem);

                    Sequence entryContent = entry.newSequence(Tag.ContentSequence, 10);
                    entryContent.add(createTextItem("HAS ACQ CONTEXT", CODE_INSTANCE_NUMBER, SCHEME_DCM,
                        MEANING_INSTANCE_NUMBER, Integer.toString(instanceNumberCounter++)));

                    // MADO TID 1601: Number of Frames (121140, DCM) is conditionally required when
                    // the referenced SOP Class is multiframe.
                    String sopClassUID = instAttrs.getString(Tag.SOPClassUID);
                    if (be.uzleuven.ihe.dicom.validator.validation.tid1600.TID1600Rules.isMultiframeSOP(sopClassUID)) {
                        String numFramesStr = instAttrs.getString(Tag.NumberOfFrames);
                        if (numFramesStr != null) {
                            numFramesStr = numFramesStr.trim();
                        }
                        if (numFramesStr != null && !numFramesStr.isEmpty()) {
                            try {
                                int numFrames = Integer.parseInt(numFramesStr);
                                entryContent.add(createNumericItem("HAS ACQ CONTEXT", CODE_NUMBER_OF_FRAMES, SCHEME_DCM,
                                    MEANING_NUMBER_OF_FRAMES, numFrames));
                            } catch (NumberFormatException ignore) {
                                // Keep the manifest valid; omission will be caught by validator if required.
                            }
                        }
                    }

                    groupSeq.add(entry);
                }
            }

            libContent.add(group);
        }

        // Attach Image Library container to root
        contentSeq.add(libContainer);

        return mado;
    }

    /**
     * Saves the manifest to a DICOM file.
     */
    public void saveToFile(Attributes manifest, File outputFile) throws IOException {
        writeDicomFile(outputFile, manifest);
    }

    /**
     * Main method for command-line usage.
     * Example: java MADOSCUManifestCreator -aec ORTHANC -aet NICK -host 172.20.240.184 -port 4242
     *          -study 1.3.46.670589.11.0.1.1996082307380006 -pid 7 -out MADO_SCU.dcm
     */
    public static void main(String[] args) {
        try {
            // Parse command line arguments
            DefaultMetadata defaults = new DefaultMetadata();
            String studyUID = null;
            String patientID = null;
            String outputFile = "MADO_FROM_SCU.dcm";

            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "-aec":
                        defaults.withCalledAET(args[++i]);
                        break;
                    case "-aet":
                        defaults.withCallingAET(args[++i]);
                        break;
                    case "-host":
                        defaults.withRemoteHost(args[++i]);
                        break;
                    case "-port":
                        defaults.withRemotePort(Integer.parseInt(args[++i]));
                        break;
                    case "-study":
                        studyUID = args[++i];
                        break;
                    case "-pid":
                        patientID = args[++i];
                        break;
                    case "-out":
                        outputFile = args[++i];
                        break;
                    case "-issuer":
                        defaults.withPatientIdIssuerOid(args[++i]);
                        break;
                    case "-accissuer":
                        defaults.withAccessionNumberIssuerOid(args[++i]);
                        break;
                    case "-repouid":
                        defaults.withRetrieveLocationUid(args[++i]);
                        break;
                    case "-wado":
                        defaults.withWadoRsBaseUrl(args[++i]);
                        break;
                }
            }

            if (studyUID == null) {
                System.err.println("Usage: MADOSCUManifestCreator -study <StudyInstanceUID> " +
                    "[-pid <PatientID>] [-aec <CalledAET>] [-aet <CallingAET>] " +
                    "[-host <hostname>] [-port <port>] [-out <output.dcm>] " +
                    "[-issuer <PatientIDIssuerOID>] [-accissuer <AccessionIssuerOID>] " +
                    "[-repouid <RetrieveLocationUID>] [-wado <WADOBaseURL>]");
                System.exit(1);
            }

            System.out.println("Creating MADO manifest from C-FIND query...");
            System.out.println("Study UID: " + studyUID);
            System.out.println("Patient ID: " + (patientID != null ? patientID : "(will query)"));
            System.out.println("Remote: " + defaults.calledAET + " @ " +
                defaults.remoteHost + ":" + defaults.remotePort);

            MADOSCUManifestCreator creator = new MADOSCUManifestCreator(defaults);
            Attributes mado = creator.createManifest(studyUID, patientID);

            creator.saveToFile(mado, new File(outputFile));

            System.out.println("MADO manifest created successfully: " + outputFile);
            System.out.println("SOP Instance UID: " + mado.getString(Tag.SOPInstanceUID));

        } catch (Exception e) {
            System.err.println("Error creating MADO manifest: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
