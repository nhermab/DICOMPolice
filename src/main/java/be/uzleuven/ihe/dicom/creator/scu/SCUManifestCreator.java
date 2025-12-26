package be.uzleuven.ihe.dicom.creator.scu;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.*;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.PresentationContext;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static be.uzleuven.ihe.dicom.creator.utils.DicomCreatorUtils.writeDicomFile;
import be.uzleuven.ihe.dicom.creator.scu.streaming.ManifestStreamWriter;

/**
 * Base class for SCU (Service Class User) manifest creators.
 * Provides C-FIND functionality to query DICOM archives and retrieve metadata
 * that can be used to construct KOS or MADO manifests.
 */
public abstract class SCUManifestCreator {

    /**
     * Configuration for default metadata values when C-FIND responses lack certain attributes.
     * These defaults are essential for IHE XDS-I.b compliance.
     */
    public static class DefaultMetadata {
        /** Default Patient ID Issuer OID (for IssuerOfPatientIDQualifiersSequence) */
        public String patientIdIssuerOid = "1.2.3.4.5.6.7.8.9";

        /** Default Accession Number Issuer OID (for IssuerOfAccessionNumberSequence) */
        public String accessionNumberIssuerOid = "1.2.3.4.5.6.7.8.10";

        /** Default Retrieve Location UID (Repository UID for XDS-I.b) */
        public String retrieveLocationUid = "1.2.3.4.5.6.7.8.9.10";

        /** Default WADO-RS base URL for retrieval */
        public String wadoRsBaseUrl = "https://pacs.example.org/dicom-web/studies";

        /** Default Institution Name */
        public String institutionName = "IHE Demo Hospital";

        /** Default Local Namespace (if IssuerOfPatientID is just a text value) */
        public String patientIdIssuerLocalNamespace = "HOSPITAL_A";

        /** AE Title of the SCU (this application) */
        public String callingAET = "DICOMPOLICE";

        /** AE Title of the SCP (remote PACS) */
        public String calledAET = "ORTHANC";

        /** Hostname or IP of the remote SCP */
        public String remoteHost = "localhost";

        /** Port of the remote SCP */
        public int remotePort = 4242;

        /** Connection timeout in milliseconds */
        public int connectTimeout = 5000;

        /** Response timeout in milliseconds */
        public int responseTimeout = 10000;

        public DefaultMetadata withPatientIdIssuerOid(String oid) {
            this.patientIdIssuerOid = oid;
            return this;
        }

        public DefaultMetadata withAccessionNumberIssuerOid(String oid) {
            this.accessionNumberIssuerOid = oid;
            return this;
        }

        public DefaultMetadata withRetrieveLocationUid(String uid) {
            this.retrieveLocationUid = uid;
            return this;
        }

        public DefaultMetadata withWadoRsBaseUrl(String url) {
            this.wadoRsBaseUrl = url;
            return this;
        }

        public DefaultMetadata withInstitutionName(String name) {
            this.institutionName = name;
            return this;
        }

        public DefaultMetadata withPatientIdIssuerLocalNamespace(String namespace) {
            this.patientIdIssuerLocalNamespace = namespace;
            return this;
        }

        public DefaultMetadata withCallingAET(String aet) {
            this.callingAET = aet;
            return this;
        }

        public DefaultMetadata withCalledAET(String aet) {
            this.calledAET = aet;
            return this;
        }

        public DefaultMetadata withRemoteHost(String host) {
            this.remoteHost = host;
            return this;
        }

        public DefaultMetadata withRemotePort(int port) {
            this.remotePort = port;
            return this;
        }

        public DefaultMetadata withConnectTimeout(int timeout) {
            this.connectTimeout = timeout;
            return this;
        }

        public DefaultMetadata withResponseTimeout(int timeout) {
            this.responseTimeout = timeout;
            return this;
        }
    }

    /**
     * Represents the result of a C-FIND query.
     */
    public static class CFindResult {
        private final List<Attributes> matches = new ArrayList<>();
        private boolean success = false;
        private String errorMessage = null;

        public List<Attributes> getMatches() {
            return matches;
        }

        public void addMatch(Attributes attrs) {
            matches.add(attrs);
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
    }

    protected final DefaultMetadata defaults;

    public SCUManifestCreator() {
        this(new DefaultMetadata());
    }

    public SCUManifestCreator(DefaultMetadata defaults) {
        this.defaults = defaults;
    }

    /**
     * Performs a C-FIND query at STUDY level.
     *
     * @param studyInstanceUid The Study Instance UID to query
     * @param patientId Optional Patient ID (may be null)
     * @return CFindResult containing matched studies
     * @throws IOException if network communication fails
     */
    public CFindResult findStudy(String studyInstanceUid, String patientId) throws IOException {
        Attributes keys = new Attributes();
        keys.setString(Tag.QueryRetrieveLevel, VR.CS, "STUDY");
        keys.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUid);

        if (patientId != null && !patientId.trim().isEmpty()) {
            keys.setString(Tag.PatientID, VR.LO, patientId);
        } else {
            keys.setNull(Tag.PatientID, VR.LO);
        }

        // Return attributes we need for manifest creation
        keys.setNull(Tag.PatientName, VR.PN);
        keys.setNull(Tag.PatientBirthDate, VR.DA);
        keys.setNull(Tag.PatientSex, VR.CS);
        keys.setNull(Tag.StudyDate, VR.DA);
        keys.setNull(Tag.StudyTime, VR.TM);
        keys.setNull(Tag.StudyDescription, VR.LO);
        keys.setNull(Tag.AccessionNumber, VR.SH);
        keys.setNull(Tag.ReferringPhysicianName, VR.PN);
        keys.setNull(Tag.StudyID, VR.SH);

        return performCFind(keys);
    }

    /**
     * Performs a C-FIND query at SERIES level.
     *
     * @param studyInstanceUid The Study Instance UID
     * @param seriesInstanceUid Optional Series Instance UID (may be null to get all series)
     * @return CFindResult containing matched series
     * @throws IOException if network communication fails
     */
    public CFindResult findSeries(String studyInstanceUid, String seriesInstanceUid) throws IOException {
        Attributes keys = new Attributes();
        keys.setString(Tag.QueryRetrieveLevel, VR.CS, "SERIES");
        keys.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUid);

        if (seriesInstanceUid != null && !seriesInstanceUid.trim().isEmpty()) {
            keys.setString(Tag.SeriesInstanceUID, VR.UI, seriesInstanceUid);
        } else {
            keys.setNull(Tag.SeriesInstanceUID, VR.UI);
        }

        // Return attributes for series
        keys.setNull(Tag.Modality, VR.CS);
        keys.setNull(Tag.SeriesNumber, VR.IS);
        keys.setNull(Tag.SeriesDescription, VR.LO);
        keys.setNull(Tag.SeriesDate, VR.DA);
        keys.setNull(Tag.SeriesTime, VR.TM);

        return performCFind(keys);
    }

    /**
     * Performs a C-FIND query at IMAGE (INSTANCE) level.
     *
     * @param studyInstanceUid The Study Instance UID
     * @param seriesInstanceUid The Series Instance UID
     * @return CFindResult containing matched instances
     * @throws IOException if network communication fails
     */
    public CFindResult findInstances(String studyInstanceUid, String seriesInstanceUid) throws IOException {
        Attributes keys = new Attributes();
        keys.setString(Tag.QueryRetrieveLevel, VR.CS, "IMAGE");
        keys.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUid);
        keys.setString(Tag.SeriesInstanceUID, VR.UI, seriesInstanceUid);

        // Return attributes for instances
        keys.setNull(Tag.SOPInstanceUID, VR.UI);
        keys.setNull(Tag.SOPClassUID, VR.UI);
        keys.setNull(Tag.InstanceNumber, VR.IS);
        keys.setNull(Tag.NumberOfFrames, VR.IS);
        keys.setNull(Tag.Rows, VR.US);
        keys.setNull(Tag.Columns, VR.US);

        return performCFind(keys);
    }

    /**
     * Performs the actual C-FIND network operation.
     */
    private CFindResult performCFind(Attributes keys) throws IOException {
        CFindResult result = new CFindResult();

        Device device = new Device("dicompolice-scu");
        Connection conn = new Connection();
        device.addConnection(conn);

        ApplicationEntity ae = new ApplicationEntity(defaults.callingAET);
        device.addApplicationEntity(ae);
        ae.addConnection(conn);

        // Create executor services
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        device.setExecutor(executorService);
        device.setScheduledExecutor(scheduledExecutorService);

        try {
            // Configure remote connection
            Connection remote = new Connection();
            remote.setHostname(defaults.remoteHost);
            remote.setPort(defaults.remotePort);

            // Configure timeouts
            conn.setConnectTimeout(defaults.connectTimeout);
            conn.setResponseTimeout(defaults.responseTimeout);

            // Create association request
            AAssociateRQ rq = new AAssociateRQ();
            rq.setCallingAET(defaults.callingAET);
            rq.setCalledAET(defaults.calledAET);

            // Add presentation context for Study Root Query/Retrieve
            rq.addPresentationContext(
                    new PresentationContext(1,
                            org.dcm4che3.data.UID.StudyRootQueryRetrieveInformationModelFind,
                            org.dcm4che3.data.UID.ImplicitVRLittleEndian));

            // Open association
            Association as = ae.connect(remote, rq);

            // Perform C-FIND
            DimseRSPHandler rspHandler = new DimseRSPHandler(as.nextMessageID()) {
                @Override
                public void onDimseRSP(Association as, Attributes cmd, Attributes data) {
                    super.onDimseRSP(as, cmd, data);
                    int status = cmd.getInt(Tag.Status, -1);

                    // Pending status means we have data
                    if (status == Status.Pending && data != null) {
                        result.addMatch(new Attributes(data));
                    }
                }
            };

            as.cfind(org.dcm4che3.data.UID.StudyRootQueryRetrieveInformationModelFind,
                    0, // priority
                    keys,
                    null, // TSuid
                    rspHandler);

            // Wait for response
            as.waitForOutstandingRSP();

            // Release association
            as.release();

            result.setSuccess(true);

        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage("C-FIND failed: " + e.getMessage());
            throw new IOException("C-FIND operation failed", e);
        } finally {
            executorService.shutdown();
            scheduledExecutorService.shutdown();
        }

        return result;
    }

    /**
     * Public/CLI-friendly entrypoint for performing an arbitrary C-FIND with custom keys.
     *
     * This is intentionally a thin wrapper around the internal implementation so higher-level
     * query services can add search modes (accessionNumber, patientID, studyDate, ...)
     * without duplicating DIMSE boilerplate.
     */
    public CFindResult performCFindPublic(Attributes keys) throws IOException {
        return performCFind(keys);
    }

    /**
     * Apply default metadata to attributes that are missing from C-FIND response.
     * Subclasses should call this to ensure compliance with IHE requirements.
     */
    protected void applyDefaults(Attributes attrs) {
        // Patient ID Issuer - essential for IHE XDS-I.b
        if (!attrs.contains(Tag.IssuerOfPatientID) ||
            attrs.getString(Tag.IssuerOfPatientID, "").trim().isEmpty()) {
            attrs.setString(Tag.IssuerOfPatientID, VR.LO, defaults.patientIdIssuerLocalNamespace);
        }

        // Institution Name
        if (!attrs.contains(Tag.InstitutionName) ||
            attrs.getString(Tag.InstitutionName, "").trim().isEmpty()) {
            attrs.setString(Tag.InstitutionName, VR.LO, defaults.institutionName);
        }

        // Study Date - if missing, use current date
        if (!attrs.contains(Tag.StudyDate) ||
            attrs.getString(Tag.StudyDate, "").trim().isEmpty()) {
            String currentDate = new java.text.SimpleDateFormat("yyyyMMdd")
                    .format(new java.util.Date());
            attrs.setString(Tag.StudyDate, VR.DA, currentDate);
        }

        // Study Time - if missing, use current time
        if (!attrs.contains(Tag.StudyTime) ||
            attrs.getString(Tag.StudyTime, "").trim().isEmpty()) {
            String currentTime = new java.text.SimpleDateFormat("HHmmss.SSSSSS")
                    .format(new java.util.Date());
            attrs.setString(Tag.StudyTime, VR.TM, currentTime);
        }
    }

    /**
     * Normalizes PatientSex (0010,0040) to valid DICOM enumerated values.
     *
     * Valid values are: M, F, O, or empty.
     * Some upstream systems use non-standard values (e.g. 'W').
     */
    protected static String normalizePatientSex(String patientSex) {
        if (patientSex == null) {
            return "";
        }
        String s = patientSex.trim().toUpperCase();
        if (s.isEmpty()) {
            return "";
        }
        if ("M".equals(s) || "F".equals(s) || "O".equals(s)) {
            return s;
        }
        // Common non-standard mappings
        if ("W".equals(s)) { // woman
            return "F";
        }
        if ("MAN".equals(s) || "MALE".equals(s)) {
            return "M";
        }
        if ("WOMAN".equals(s) || "FEMALE".equals(s)) {
            return "F";
        }
        // Fallback for unknown/unsupported values
        return "O";
    }

    /**
     * Saves a generated manifest to a DICOM Part-10 file.
     *
     * Subclasses previously duplicated this method; keeping it here avoids CLI downcasts.
     */
    public void saveToFile(Attributes manifest, File outputFile) throws IOException {
        writeDicomFile(outputFile, manifest);
    }

    /**
     * Creates a manifest (KOS or MADO) from C-FIND results.
     * To be implemented by subclasses.
     */
    public abstract Attributes createManifest(String studyInstanceUid, String patientId) throws IOException;

    /**
     * Stream study/series/instance metadata to a writer, avoiding large in-memory lists.
     *
     * This is intended for huge PACS crawls where building an entire manifest dataset in memory
     * per study could exhaust heap.
     */
    public void streamStudy(String studyInstanceUid, String patientId, ManifestStreamWriter writer) throws IOException {
        // Step 1: Query study
        CFindResult studyResult = findStudy(studyInstanceUid, patientId);
        if (!studyResult.isSuccess() || studyResult.getMatches().isEmpty()) {
            throw new IOException("Study not found: " + studyInstanceUid +
                ". Error: " + studyResult.getErrorMessage());
        }

        Attributes studyAttrs = studyResult.getMatches().get(0);
        applyDefaults(studyAttrs);
        writer.openStudy(studyAttrs);

        // Step 2: Query series
        CFindResult seriesResult = findSeries(studyInstanceUid, null);
        if (!seriesResult.isSuccess() || seriesResult.getMatches().isEmpty()) {
            throw new IOException("No series found for study: " + studyInstanceUid);
        }

        // Step 3: For each series, query instances and stream them immediately
        for (Attributes seriesAttrs : seriesResult.getMatches()) {
            // Series-level responses from most PACS won't echo StudyInstanceUID, but our writer
            // benefits from it for indexing.
            seriesAttrs.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUid);

            writer.openSeries(seriesAttrs);

            String seriesInstanceUid = seriesAttrs.getString(Tag.SeriesInstanceUID);
            if (seriesInstanceUid == null || seriesInstanceUid.trim().isEmpty()) {
                writer.closeSeries();
                continue;
            }

            CFindResult instanceResult = findInstances(studyInstanceUid, seriesInstanceUid);
            if (instanceResult.isSuccess() && !instanceResult.getMatches().isEmpty()) {
                for (Attributes instAttrs : instanceResult.getMatches()) {
                    // Ensure identifiers are present in the instance record.
                    instAttrs.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUid);
                    instAttrs.setString(Tag.SeriesInstanceUID, VR.UI, seriesInstanceUid);
                    writer.writeInstance(instAttrs);
                }
            }

            writer.closeSeries();
        }

        writer.closeStudy();
    }
}
