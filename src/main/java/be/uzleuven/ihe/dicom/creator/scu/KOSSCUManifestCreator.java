package be.uzleuven.ihe.dicom.creator.scu;

import org.dcm4che3.data.*;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static be.uzleuven.ihe.dicom.creator.utils.DicomCreatorUtils.*;
import static be.uzleuven.ihe.dicom.creator.utils.SRContentItemUtils.*;
import static be.uzleuven.ihe.dicom.constants.CodeConstants.*;
import be.uzleuven.ihe.dicom.creator.utils.ManifestHeaderUtils;
import be.uzleuven.ihe.dicom.creator.utils.ManifestHeaderUtils.HeaderConfig;
import be.uzleuven.ihe.dicom.creator.utils.CommandLineParser;
import org.springframework.lang.NonNull;

/**
 * SCU client for creating IHE XDS-I.b KOS (Key Object Selection) manifests
 * from C-FIND query results.
 * This class performs C-FIND queries against a DICOM archive (PACS) and
 * constructs valid KOS manifests with proper metadata, including defaults
 * for missing attributes that are required by IHE profiles.
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
        StudyAttrSeries result = getStudyAttrSeries(studyInstanceUid, patientId);

        // Step 4: Build the KOS manifest
        return buildKOSManifest(result.studyAttrs, result.allSeries);
    }

    @NonNull
    private StudyAttrSeries getStudyAttrSeries(String studyInstanceUid, String patientId) throws IOException {
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
        List<SeriesData> allSeries = new java.util.ArrayList<>();

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
        return new StudyAttrSeries(studyAttrs, allSeries);
    }

    private static class StudyAttrSeries {
        public final Attributes studyAttrs;
        public final List<SeriesData> allSeries;

        public StudyAttrSeries(Attributes studyAttrs, List<SeriesData> allSeries) {
            this.studyAttrs = studyAttrs;
            this.allSeries = allSeries;
        }
    }


    /**
     * Builds the complete KOS manifest from queried data.
     */
    private Attributes buildKOSManifest(Attributes studyAttrs, java.util.List<SeriesData> allSeries) {
        Attributes kos = new Attributes();

        // Extract data from study query
        String studyInstanceUID = studyAttrs.getString(Tag.StudyInstanceUID);
        String normalizedStudyInstanceUID = normalizeUidNoLeadingZeros(studyInstanceUID);
        String studyDate = studyAttrs.getString(Tag.StudyDate, "");
        String studyTime = studyAttrs.getString(Tag.StudyTime, "");
        String accessionNumber = studyAttrs.getString(Tag.AccessionNumber, "");
        String contentDate = new java.text.SimpleDateFormat("yyyyMMdd").format(new java.util.Date());
        String contentTime = new java.text.SimpleDateFormat("HHmmss.SSSSSS").format(new java.util.Date());

        // Build header configuration
        HeaderConfig config = new HeaderConfig();
        config.sopClassUID = UID.KeyObjectSelectionDocumentStorage;
        config.sopInstanceUID = createNormalizedUid();
        config.specificCharacterSet = "ISO_IR 192"; // UTF-8

        // Patient module
        config.patientID = studyAttrs.getString(Tag.PatientID, "UNKNOWN");
        config.patientName = studyAttrs.getString(Tag.PatientName, "UNKNOWN^PATIENT");
        config.patientBirthDate = studyAttrs.getString(Tag.PatientBirthDate, "");
        config.patientSex = studyAttrs.getString(Tag.PatientSex, "O");
        config.issuerOfPatientID = studyAttrs.getString(Tag.IssuerOfPatientID, defaults.patientIdIssuerLocalNamespace);
        config.patientIdIssuerOid = defaults.patientIdIssuerOid;

        // Study module
        config.studyInstanceUID = normalizedStudyInstanceUID;
        config.studyDate = studyDate;
        config.studyTime = studyTime;
        config.studyID = studyAttrs.getString(Tag.StudyID, "1");
        config.studyDescription = studyAttrs.getString(Tag.StudyDescription, "");
        config.referringPhysicianName = studyAttrs.getString(Tag.ReferringPhysicianName, "");
        config.accessionNumber = accessionNumber;
        config.accessionNumberIssuerOid = defaults.accessionNumberIssuerOid;

        // Series module
        config.seriesInstanceUID = createNormalizedUid();
        config.seriesNumber = 1;
        config.modality = "KO";
        config.seriesDate = studyDate;
        config.seriesTime = studyTime;

        // Equipment module
        config.manufacturer = "DICOMPolice";
        config.manufacturerModelName = "KOS SCU Creator";
        config.softwareVersions = "1.0";
        config.institutionName = defaults.institutionName;

        // SR Document module
        config.instanceNumber = 1;
        config.contentDate = contentDate;
        config.contentTime = contentTime;
        config.timezoneOffset = timezoneOffsetFromUTC();

        // Populate all common modules using utility
        ManifestHeaderUtils.populateSOPCommonModule(kos, config);
        ManifestHeaderUtils.populatePatientModule(kos, config);
        ManifestHeaderUtils.populateStudyModule(kos, config);
        ManifestHeaderUtils.populateSeriesModule(kos, config);
        ManifestHeaderUtils.populateEquipmentModule(kos, config);
        ManifestHeaderUtils.populateSRDocumentModule(kos, config);

        // Add missing Type 2 attributes for IHE XDS-I.b compliance
        ManifestHeaderUtils.populateReferencedStudySequence(kos);
        ManifestHeaderUtils.populateReferencedRequestSequence(kos, normalizedStudyInstanceUID,
            accessionNumber, defaults.accessionNumberIssuerOid);

        // Document Title: Manifest (Key Object Selection)
        Sequence conceptNameCodeSeq = kos.newSequence(Tag.ConceptNameCodeSequence, 1);
        conceptNameCodeSeq.add(code(CODE_KOS_MANIFEST, SCHEME_DCM, MEANING_MANIFEST));


        // SR Document Content Module - Root level attributes
        kos.setString(Tag.ValueType, VR.CS, "CONTAINER");
        kos.setString(Tag.ContinuityOfContent, VR.CS, "SEPARATE");

        // ContentTemplateSequence - identifies TID 2010
        Sequence contentTemplateSeq = kos.newSequence(Tag.ContentTemplateSequence, 1);
        contentTemplateSeq.add(createTemplateItem("2010"));

        // Evidence Sequence (CurrentRequestedProcedureEvidenceSequence)
        Sequence evidenceSeq = kos.newSequence(Tag.CurrentRequestedProcedureEvidenceSequence, 1);
        Attributes studyItem = new Attributes();
        studyItem.setString(Tag.StudyInstanceUID, VR.UI, normalizedStudyInstanceUID);

        Sequence refSeriesSeq = studyItem.newSequence(Tag.ReferencedSeriesSequence, allSeries.size());

        for (SeriesData sd : allSeries) {
            Attributes seriesItem = new Attributes();
            String serUID = sd.seriesAttrs.getString(Tag.SeriesInstanceUID);
            String normalizedSerUID = normalizeUidNoLeadingZeros(serUID);
            seriesItem.setString(Tag.SeriesInstanceUID, VR.UI, normalizedSerUID);

            // Add Retrieve AE Title (Type 1 for IHE XDS-I.b)
            seriesItem.setString(Tag.RetrieveAETitle, VR.AE, defaults.calledAET);

            // Add retrieval information (XDS-I.b requirement)
            seriesItem.setString(Tag.RetrieveLocationUID, VR.UI, defaults.retrieveLocationUid);

            // Build WADO-RS URL
            String wadoUrl = defaults.wadoRsBaseUrl + "/" + normalizedStudyInstanceUID +
                "/series/" + normalizedSerUID;
            seriesItem.setString(Tag.RetrieveURL, VR.UR, wadoUrl.trim());

            // Sort instances before adding to ensure correct order
            java.util.List<Attributes> sortedInstances = sortInstances(sd.instances);

            Sequence refSOPSeq = seriesItem.newSequence(Tag.ReferencedSOPSequence, sortedInstances.size());

            for (Attributes instAttrs : sortedInstances) {
                Attributes sopItem = new Attributes();
                sopItem.setString(Tag.ReferencedSOPClassUID, VR.UI,
                    instAttrs.getString(Tag.SOPClassUID));
                sopItem.setString(Tag.ReferencedSOPInstanceUID, VR.UI,
                    normalizeUidNoLeadingZeros(instAttrs.getString(Tag.SOPInstanceUID)));
                refSOPSeq.add(sopItem);
            }

            refSeriesSeq.add(seriesItem);
        }

        evidenceSeq.add(studyItem);

        // SR Document Content Module - build content tree
        Sequence contentSeq = kos.newSequence(Tag.ContentSequence, 1);

        // TID 2010 requires Key Object Description (113012, DCM) as first item before images
        Attributes keyObjDesc = createTextItem("CONTAINS",
            CODE_KOS_DESCRIPTION, SCHEME_DCM, MEANING_KOS_DESCRIPTION, "Manifest");
        contentSeq.add(keyObjDesc);

        // All IMAGE references directly in content (KOS doesn't require container)
        // TID 2010: IMAGE items should NOT have ConceptNameCodeSequence (pass null)
        // Sort instances to maintain correct order
        for (SeriesData sd : allSeries) {
            java.util.List<Attributes> sortedInstances = sortInstances(sd.instances);
            for (Attributes instAttrs : sortedInstances) {
                Attributes imageRef = createImageItem("CONTAINS",
                    null,  // No concept name for TID 2010 IMAGE items
                    instAttrs.getString(Tag.SOPClassUID),
                    normalizeUidNoLeadingZeros(instAttrs.getString(Tag.SOPInstanceUID))
                );
                contentSeq.add(imageRef);
            }
        }

        return kos;
    }

    /**
     * Sorts instances using multiple criteria for robust ordering.
     * Priority: InstanceNumber > ImagePositionPatient Z > AcquisitionNumber > SOPInstanceUID
     */
    private java.util.List<Attributes> sortInstances(java.util.List<Attributes> instances) {
        if (instances == null) return java.util.Collections.emptyList();

        java.util.List<Attributes> sorted = new java.util.ArrayList<>(instances);
        sorted.sort((a, b) -> {
            // Primary: InstanceNumber
            int instNumA = getInstanceNumber(a);
            int instNumB = getInstanceNumber(b);

            if (instNumA != Integer.MAX_VALUE || instNumB != Integer.MAX_VALUE) {
                int cmp = Integer.compare(instNumA, instNumB);
                if (cmp != 0) return cmp;
            }

            // Secondary: ImagePositionPatient Z-coordinate (for spatial ordering)
            double zPosA = getImagePositionZ(a);
            double zPosB = getImagePositionZ(b);

            if (!Double.isNaN(zPosA) || !Double.isNaN(zPosB)) {
                int cmp = Double.compare(zPosA, zPosB);
                if (cmp != 0) return cmp;
            }

            // Tertiary: AcquisitionNumber
            int acqNumA = getAcquisitionNumber(a);
            int acqNumB = getAcquisitionNumber(b);

            if (acqNumA != Integer.MAX_VALUE || acqNumB != Integer.MAX_VALUE) {
                int cmp = Integer.compare(acqNumA, acqNumB);
                if (cmp != 0) return cmp;
            }

            // Final fallback: SOPInstanceUID (deterministic ordering)
            String sopA = a.getString(Tag.SOPInstanceUID, "");
            String sopB = b.getString(Tag.SOPInstanceUID, "");
            return sopA.compareTo(sopB);
        });

        return sorted;
    }

    private int getInstanceNumber(Attributes attrs) {
        String instNumStr = attrs.getString(Tag.InstanceNumber);
        if (instNumStr != null && !instNumStr.trim().isEmpty()) {
            try {
                return Integer.parseInt(instNumStr.trim());
            } catch (NumberFormatException ignore) {
            }
        }
        return Integer.MAX_VALUE;
    }

    private double getImagePositionZ(Attributes attrs) {
        double[] ipp = attrs.getDoubles(Tag.ImagePositionPatient);
        if (ipp != null && ipp.length >= 3) {
            return ipp[2]; // Z coordinate
        }
        return Double.NaN;
    }

    private int getAcquisitionNumber(Attributes attrs) {
        String acqNumStr = attrs.getString(Tag.AcquisitionNumber);
        if (acqNumStr != null && !acqNumStr.trim().isEmpty()) {
            try {
                return Integer.parseInt(acqNumStr.trim());
            } catch (NumberFormatException ignore) {
            }
        }
        return Integer.MAX_VALUE;
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
            CommandLineParser.ParsedArgs parsed = CommandLineParser.parseArgs(args, "KOS_FROM_SCU.dcm");

            if (parsed.isInvalid()) {
                System.err.println(parsed.getUsageMessage("KOSSCUManifestCreator"));
                System.exit(1);
            }

            CommandLineParser.printStartupInfo(parsed, "KOS");

            KOSSCUManifestCreator creator = new KOSSCUManifestCreator(parsed.defaults);
            Attributes kos = creator.createManifest(parsed.studyUID, parsed.patientID);

            creator.saveToFile(kos, new File(parsed.outputFile));

            CommandLineParser.printSuccessInfo(parsed.outputFile, kos.getString(Tag.SOPInstanceUID), "KOS");

        } catch (Exception e) {
            CommandLineParser.printErrorAndExit("Error creating KOS manifest: " + e.getMessage(), e);
        }
    }
}
