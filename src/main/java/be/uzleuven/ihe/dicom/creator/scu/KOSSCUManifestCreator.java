package be.uzleuven.ihe.dicom.creator.scu;

import org.dcm4che3.data.*;
import org.dcm4che3.util.UIDUtils;

import java.io.File;
import java.io.IOException;

import static be.uzleuven.ihe.dicom.creator.utils.DicomCreatorUtils.*;
import static be.uzleuven.ihe.dicom.creator.utils.DicomSequenceUtils.*;
import static be.uzleuven.ihe.dicom.creator.utils.SRContentItemUtils.*;
import static be.uzleuven.ihe.dicom.constants.CodeConstants.*;

/**
 * SCU client for creating IHE XDS-I.b KOS (Key Object Selection) manifests
 * from C-FIND query results.
 *
 * This class performs C-FIND queries against a DICOM archive (PACS) and
 * constructs valid KOS manifests with proper metadata, including defaults
 * for missing attributes that are required by IHE profiles.
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
 * KOSSCUManifestCreator creator = new KOSSCUManifestCreator(defaults);
 * Attributes kosManifest = creator.createManifest(
 *     "1.3.46.670589.11.0.1.1996082307380006",
 *     "7");
 *
 * creator.saveToFile(kosManifest, new File("KOS_FROM_SCU.dcm"));
 * </pre>
 */
public class KOSSCUManifestCreator extends SCUManifestCreator {

    public KOSSCUManifestCreator() {
        super();
    }

    public KOSSCUManifestCreator(DefaultMetadata defaults) {
        super(defaults);
    }

    /**
     * Creates a KOS manifest by querying the PACS for study/series/instance information.
     *
     * @param studyInstanceUid The Study Instance UID to create a manifest for
     * @param patientId The Patient ID (can be null, will be queried from PACS)
     * @return Attributes object containing the complete KOS manifest
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

        // Step 4: Build the KOS manifest
        return buildKOSManifest(studyAttrs, allSeries);
    }

    /**
     * Internal class to hold series and its instances.
     */
    private static class SeriesData {
        Attributes seriesAttrs;
        java.util.List<Attributes> instances;
    }

    /**
     * Builds the complete KOS manifest from queried data.
     */
    private Attributes buildKOSManifest(Attributes studyAttrs, java.util.List<SeriesData> allSeries) {
        Attributes kos = new Attributes();

        // File Meta Information
        String sopInstanceUID = UIDUtils.createUID();
        kos.setString(Tag.SOPClassUID, VR.UI, UID.KeyObjectSelectionDocumentStorage);
        kos.setString(Tag.SOPInstanceUID, VR.UI, sopInstanceUID);

        // SpecificCharacterSet
        kos.setString(Tag.SpecificCharacterSet, VR.CS, "ISO_IR 192"); // UTF-8

        // Patient Module - from study query
        String patientID = studyAttrs.getString(Tag.PatientID, "UNKNOWN");
        String patientName = studyAttrs.getString(Tag.PatientName, "UNKNOWN^PATIENT");
        kos.setString(Tag.PatientID, VR.LO, patientID);
        kos.setString(Tag.PatientName, VR.PN, patientName);
        kos.setString(Tag.PatientBirthDate, VR.DA, studyAttrs.getString(Tag.PatientBirthDate, ""));
        kos.setString(Tag.PatientSex, VR.CS, studyAttrs.getString(Tag.PatientSex, "O"));

        // Add Patient ID Issuer with OID (IHE requirement)
        String issuerOfPatientID = studyAttrs.getString(Tag.IssuerOfPatientID, defaults.patientIdIssuerLocalNamespace);
        kos.setString(Tag.IssuerOfPatientID, VR.LO, issuerOfPatientID);
        addPatientIDQualifiers(kos, defaults.patientIdIssuerOid);

        // Study Module - from study query
        String studyInstanceUID = studyAttrs.getString(Tag.StudyInstanceUID);
        String studyDate = studyAttrs.getString(Tag.StudyDate, "");
        String studyTime = studyAttrs.getString(Tag.StudyTime, "");
        String accessionNumber = studyAttrs.getString(Tag.AccessionNumber, "");

        kos.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUID);
        kos.setString(Tag.StudyDate, VR.DA, studyDate);
        kos.setString(Tag.StudyTime, VR.TM, studyTime);
        kos.setString(Tag.ReferringPhysicianName, VR.PN,
            studyAttrs.getString(Tag.ReferringPhysicianName, ""));
        kos.setString(Tag.StudyID, VR.SH, studyAttrs.getString(Tag.StudyID, "1"));
        kos.setString(Tag.AccessionNumber, VR.SH, accessionNumber);
        kos.setString(Tag.StudyDescription, VR.LO,
            studyAttrs.getString(Tag.StudyDescription, ""));

        // Add Accession Number Issuer if accession number present
        if (accessionNumber != null && !accessionNumber.trim().isEmpty()) {
            addAccessionNumberIssuer(kos, defaults.accessionNumberIssuerOid);
        }

        // SR Document Series Module
        String seriesInstanceUID = UIDUtils.createUID();
        kos.setString(Tag.SeriesInstanceUID, VR.UI, seriesInstanceUID);
        kos.setString(Tag.SeriesNumber, VR.IS, "1");
        kos.setString(Tag.Modality, VR.CS, "KO");

        // Use study date/time for series if available
        kos.setString(Tag.SeriesDate, VR.DA, studyDate);
        kos.setString(Tag.SeriesTime, VR.TM, studyTime);

        // ReferencedPerformedProcedureStepSequence - Type 2 (can be empty)
        kos.newSequence(Tag.ReferencedPerformedProcedureStepSequence, 0);

        // General Equipment Module
        kos.setString(Tag.Manufacturer, VR.LO, "DICOMPolice");
        kos.setString(Tag.ManufacturerModelName, VR.LO, "KOS SCU Creator");
        kos.setString(Tag.SoftwareVersions, VR.LO, "1.0");
        kos.setString(Tag.InstitutionName, VR.LO, defaults.institutionName);

        // SR Document General Module
        String contentDate = new java.text.SimpleDateFormat("yyyyMMdd").format(new java.util.Date());
        String contentTime = new java.text.SimpleDateFormat("HHmmss.SSSSSS").format(new java.util.Date());

        kos.setString(Tag.InstanceNumber, VR.IS, "1");
        kos.setString(Tag.ContentDate, VR.DA, contentDate);
        kos.setString(Tag.ContentTime, VR.TM, contentTime);

        // CompletionFlag and VerificationFlag - Required for SR
        kos.setString(Tag.CompletionFlag, VR.CS, "COMPLETE");
        kos.setString(Tag.VerificationFlag, VR.CS, "UNVERIFIED");

        // TimezoneOffsetFromUTC - Highly recommended for XDS-I.b
        kos.setString(Tag.TimezoneOffsetFromUTC, VR.SH, timezoneOffsetFromUTC());

        // Document Title: Manifest (Key Object Selection)
        Sequence conceptNameCodeSeq = kos.newSequence(Tag.ConceptNameCodeSequence, 1);
        conceptNameCodeSeq.add(code(CODE_MANIFEST, SCHEME_DCM, MEANING_MANIFEST));

        // ReferencedRequestSequence - Type 2 (required to be present, can be empty)
        populateReferencedRequestSequenceWithIssuer(kos, studyInstanceUID, accessionNumber, defaults.accessionNumberIssuerOid);

        // SR Document Content Module - Root level attributes
        kos.setString(Tag.ValueType, VR.CS, "CONTAINER");
        kos.setString(Tag.ContinuityOfContent, VR.CS, "SEPARATE");

        // ContentTemplateSequence - identifies TID 2010
        Sequence contentTemplateSeq = kos.newSequence(Tag.ContentTemplateSequence, 1);
        contentTemplateSeq.add(createTemplateItem("2010"));

        // Evidence Sequence (CurrentRequestedProcedureEvidenceSequence)
        Sequence evidenceSeq = kos.newSequence(Tag.CurrentRequestedProcedureEvidenceSequence, 1);
        Attributes studyItem = new Attributes();
        studyItem.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUID);

        Sequence refSeriesSeq = studyItem.newSequence(Tag.ReferencedSeriesSequence, allSeries.size());

        for (SeriesData sd : allSeries) {
            Attributes seriesItem = new Attributes();
            String serUID = sd.seriesAttrs.getString(Tag.SeriesInstanceUID);
            seriesItem.setString(Tag.SeriesInstanceUID, VR.UI, serUID);

            // Add retrieval information (XDS-I.b requirement)
            seriesItem.setString(Tag.RetrieveLocationUID, VR.UI, defaults.retrieveLocationUid);

            // Build WADO-RS URL
            String wadoUrl = defaults.wadoRsBaseUrl + "/" + studyInstanceUID +
                "/series/" + serUID;
            seriesItem.setString(Tag.RetrieveURL, VR.UR, wadoUrl);

            Sequence refSOPSeq = seriesItem.newSequence(Tag.ReferencedSOPSequence, sd.instances.size());

            for (Attributes instAttrs : sd.instances) {
                Attributes sopItem = new Attributes();
                sopItem.setString(Tag.ReferencedSOPClassUID, VR.UI,
                    instAttrs.getString(Tag.SOPClassUID));
                sopItem.setString(Tag.ReferencedSOPInstanceUID, VR.UI,
                    instAttrs.getString(Tag.SOPInstanceUID));
                refSOPSeq.add(sopItem);
            }

            refSeriesSeq.add(seriesItem);
        }

        evidenceSeq.add(studyItem);

        // SR Document Content Module - build content tree
        Sequence contentSeq = kos.newSequence(Tag.ContentSequence, 1);

        // All IMAGE references directly in content (KOS doesn't require container)
        for (SeriesData sd : allSeries) {
            for (Attributes instAttrs : sd.instances) {
                Attributes imageRef = createImageItem("CONTAINS",
                    code(CODE_IMAGE, SCHEME_DCM, MEANING_IMAGE),
                    instAttrs.getString(Tag.SOPClassUID),
                    instAttrs.getString(Tag.SOPInstanceUID)
                );
                contentSeq.add(imageRef);
            }
        }

        return kos;
    }

    /**
     * Saves the manifest to a DICOM file.
     */
    public void saveToFile(Attributes manifest, File outputFile) throws IOException {
        writeDicomFile(outputFile, manifest);
    }

    /**
     * Main method for command-line usage.
     * Example: java KOSSCUManifestCreator -aec ORTHANC -aet NICK -host 172.20.240.184 -port 4242
     *          -study 1.3.46.670589.11.0.1.1996082307380006 -pid 7 -out KOS_SCU.dcm
     */
    public static void main(String[] args) {
        try {
            // Parse command line arguments
            DefaultMetadata defaults = new DefaultMetadata();
            String studyUID = null;
            String patientID = null;
            String outputFile = "KOS_FROM_SCU.dcm";

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
                System.err.println("Usage: KOSSCUManifestCreator -study <StudyInstanceUID> " +
                    "[-pid <PatientID>] [-aec <CalledAET>] [-aet <CallingAET>] " +
                    "[-host <hostname>] [-port <port>] [-out <output.dcm>] " +
                    "[-issuer <PatientIDIssuerOID>] [-accissuer <AccessionIssuerOID>] " +
                    "[-repouid <RetrieveLocationUID>] [-wado <WADOBaseURL>]");
                System.exit(1);
            }

            System.out.println("Creating KOS manifest from C-FIND query...");
            System.out.println("Study UID: " + studyUID);
            System.out.println("Patient ID: " + (patientID != null ? patientID : "(will query)"));
            System.out.println("Remote: " + defaults.calledAET + " @ " +
                defaults.remoteHost + ":" + defaults.remotePort);

            KOSSCUManifestCreator creator = new KOSSCUManifestCreator(defaults);
            Attributes kos = creator.createManifest(studyUID, patientID);

            creator.saveToFile(kos, new File(outputFile));

            System.out.println("KOS manifest created successfully: " + outputFile);
            System.out.println("SOP Instance UID: " + kos.getString(Tag.SOPInstanceUID));

        } catch (Exception e) {
            System.err.println("Error creating KOS manifest: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
