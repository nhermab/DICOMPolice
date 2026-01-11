package be.uzleuven.ihe.service.scp;

import be.uzleuven.ihe.service.qido.WadoRsProxyRegistry;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.hl7.fhir.r4.model.DocumentReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Metadata service backed by MHD queries and MADO file parsing.
 *
 * This service acts as an MHD Document Consumer:
 * 1. Queries a remote MHD FHIR server (ITI-67) for study-level metadata
 * 2. Retrieves MADO manifests (ITI-68) for detailed series/instance info
 * 3. Caches parsed MADO data for efficient C-FIND responses
 * 4. Provides WADO-RS URLs extracted from MADO for C-MOVE operations
 */
@Service
public class MHDBackedMetadataService {

    private static final Logger LOG = LoggerFactory.getLogger(MHDBackedMetadataService.class);

    private final MHDFhirClient mhdFhirClient;
    private final WadoRsProxyRegistry proxyRegistry;

    // In-memory cache for parsed MADO data (Study Instance UID -> StudyMetadata)
    private final Map<String, StudyMetadata> metadataCache = new ConcurrentHashMap<>();

    // Cache expiration time (e.g., 5 minutes)
    private static final long CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(5);

    @Autowired
    public MHDBackedMetadataService(MHDFhirClient mhdFhirClient,
                                     @Autowired(required = false) WadoRsProxyRegistry proxyRegistry) {
        this.mhdFhirClient = mhdFhirClient;
        this.proxyRegistry = proxyRegistry;
    }

    // ============================================================================
    // C-FIND Query Methods
    // ============================================================================

    /**
     * Find studies matching the given DICOM query keys.
     * Queries MHD and returns study-level metadata.
     */
    public List<StudyMetadata> findStudies(Attributes keys) throws IOException {
        String patientId = keys.getString(Tag.PatientID);
        String accessionNumber = keys.getString(Tag.AccessionNumber);
        String studyInstanceUID = keys.getString(Tag.StudyInstanceUID);
        String modality = keys.getString(Tag.ModalitiesInStudy);
        String studyDateFrom = null;
        String studyDateTo = null;

        // Parse date range if provided
        String studyDate = keys.getString(Tag.StudyDate);
        if (studyDate != null && !studyDate.isEmpty()) {
            if (studyDate.contains("-")) {
                String[] parts = studyDate.split("-", 2);
                studyDateFrom = parts[0].isEmpty() ? null : parts[0];
                studyDateTo = parts.length > 1 && !parts[1].isEmpty() ? parts[1] : null;
            } else {
                studyDateFrom = studyDate;
                studyDateTo = studyDate;
            }
        }

        LOG.info("C-FIND STUDY query: patientId={}, accession={}, studyUID={}, modality={}, date={}-{}",
                patientId, accessionNumber, studyInstanceUID, modality, studyDateFrom, studyDateTo);

        // Query remote MHD FHIR server for DocumentReferences
        List<DocumentReference> docRefs = mhdFhirClient.searchDocumentReferences(
                patientId, accessionNumber, studyInstanceUID, modality, studyDateFrom, studyDateTo);

        LOG.info("MHD returned {} DocumentReferences", docRefs.size());
        if (docRefs.isEmpty() && modality != null) {
            LOG.warn("No results returned for modality filter: {}", modality);
        }

        List<StudyMetadata> results = new ArrayList<>();
        for (DocumentReference docRef : docRefs) {
            StudyMetadata metadata = convertDocRefToStudyMetadata(docRef);
            if (metadata != null) {
                // Client-side modality filtering (in case MHD endpoint doesn't support it)
                if (modality != null && !modality.isEmpty() && !modality.equals("*")) {
                    if (metadata.modalitiesInStudy == null ||
                        !matchesModality(metadata.modalitiesInStudy, modality)) {
                        continue; // Skip studies that don't match the modality filter
                    }
                }
                results.add(metadata);
            }
        }

        LOG.info("After modality filtering: {} studies", results.size());
        return results;
    }

    /**
     * Find series matching the given DICOM query keys.
     * Retrieves MADO from MHD to get series-level detail.
     */
    public List<SeriesMetadata> findSeries(Attributes keys) throws IOException {
        String studyInstanceUID = keys.getString(Tag.StudyInstanceUID);
        String seriesInstanceUID = keys.getString(Tag.SeriesInstanceUID);
        String modality = keys.getString(Tag.Modality);

        LOG.info("C-FIND SERIES query: studyUID={}, seriesUID={}, modality={}",
                studyInstanceUID, seriesInstanceUID, modality);

        List<SeriesMetadata> results = new ArrayList<>();

        // If specific study requested, get MADO for that study
        if (studyInstanceUID != null && !studyInstanceUID.isEmpty() && !studyInstanceUID.equals("*")) {
            StudyMetadata studyMeta = getOrFetchStudyMetadata(studyInstanceUID);
            if (studyMeta != null) {
                for (SeriesMetadata series : studyMeta.series) {
                    if (matchesSeries(series, seriesInstanceUID, modality)) {
                        results.add(series);
                    }
                }
            }
        } else if (seriesInstanceUID != null && !seriesInstanceUID.isEmpty() && !seriesInstanceUID.equals("*")) {
            // Search all cached studies for this series
            for (StudyMetadata study : metadataCache.values()) {
                for (SeriesMetadata series : study.series) {
                    if (series.seriesInstanceUID.equals(seriesInstanceUID)) {
                        results.add(series);
                    }
                }
            }
        }

        return results;
    }

    /**
     * Find instances matching the given DICOM query keys.
     * Retrieves MADO from MHD to get instance-level detail.
     */
    public List<InstanceMetadata> findInstances(Attributes keys) throws IOException {
        String studyInstanceUID = keys.getString(Tag.StudyInstanceUID);
        String seriesInstanceUID = keys.getString(Tag.SeriesInstanceUID);
        String sopInstanceUID = keys.getString(Tag.SOPInstanceUID);

        LOG.info("C-FIND INSTANCE query: studyUID={}, seriesUID={}, sopUID={}",
                studyInstanceUID, seriesInstanceUID, sopInstanceUID);

        List<InstanceMetadata> results = new ArrayList<>();

        if (studyInstanceUID != null && !studyInstanceUID.isEmpty() && !studyInstanceUID.equals("*")) {
            StudyMetadata studyMeta = getOrFetchStudyMetadata(studyInstanceUID);
            if (studyMeta != null) {
                for (SeriesMetadata series : studyMeta.series) {
                    if (seriesInstanceUID == null || seriesInstanceUID.isEmpty() ||
                            seriesInstanceUID.equals("*") || series.seriesInstanceUID.equals(seriesInstanceUID)) {
                        for (InstanceMetadata instance : series.instances) {
                            if (sopInstanceUID == null || sopInstanceUID.isEmpty() ||
                                    sopInstanceUID.equals("*") || instance.sopInstanceUID.equals(sopInstanceUID)) {
                                results.add(instance);
                            }
                        }
                    }
                }
            }
        }

        return results;
    }

    // ============================================================================
    // MADO Retrieval and Parsing
    // ============================================================================

    /**
     * Get study metadata from cache or fetch from MHD.
     */
    public StudyMetadata getOrFetchStudyMetadata(String studyInstanceUID) throws IOException {
        // Check cache first
        StudyMetadata cached = metadataCache.get(studyInstanceUID);
        if (cached != null && !isCacheExpired(cached)) {
            LOG.debug("Using cached metadata for study {}", studyInstanceUID);
            return cached;
        }

        // Fetch MADO from MHD
        LOG.info("Fetching MADO from MHD for study {}", studyInstanceUID);
        byte[] madoBytes = mhdFhirClient.retrieveDocumentRaw(studyInstanceUID);
        if (madoBytes == null) {
            LOG.warn("No MADO found for study {}", studyInstanceUID);
            return null;
        }

        // Parse MADO
        StudyMetadata metadata = parseMADO(madoBytes, studyInstanceUID);
        if (metadata != null) {
            metadata.fetchedAt = System.currentTimeMillis();
            metadataCache.put(studyInstanceUID, metadata);
        }

        return metadata;
    }

    /**
     * Parse a MADO manifest into structured metadata.
     */
    private StudyMetadata parseMADO(byte[] madoBytes, String studyInstanceUID) {
        try (DicomInputStream dis = new DicomInputStream(new ByteArrayInputStream(madoBytes))) {
            Attributes attrs = dis.readDataset();
            return extractStudyMetadata(attrs);
        } catch (IOException e) {
            LOG.error("Failed to parse MADO for study {}: {}", studyInstanceUID, e.getMessage());
            return null;
        }
    }

    /**
     * Extract full study metadata from MADO DICOM attributes.
     */
    private StudyMetadata extractStudyMetadata(Attributes attrs) {
        StudyMetadata study = new StudyMetadata();

        // Study-level attributes
        study.studyInstanceUID = attrs.getString(Tag.StudyInstanceUID);
        study.patientId = attrs.getString(Tag.PatientID);
        study.patientName = attrs.getString(Tag.PatientName);
        study.patientBirthDate = attrs.getString(Tag.PatientBirthDate);
        study.patientSex = attrs.getString(Tag.PatientSex);
        study.studyDate = attrs.getString(Tag.StudyDate);
        study.studyTime = attrs.getString(Tag.StudyTime);
        study.studyDescription = attrs.getString(Tag.StudyDescription);
        study.studyID = attrs.getString(Tag.StudyID);
        study.accessionNumber = attrs.getString(Tag.AccessionNumber);
        study.referringPhysicianName = attrs.getString(Tag.ReferringPhysicianName);

        // Extract series/instance from Evidence Sequence
        Set<String> modalities = new HashSet<>();
        Sequence evidenceSeq = attrs.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence);
        if (evidenceSeq != null) {
            for (Attributes studyItem : evidenceSeq) {
                Sequence refSeriesSeq = studyItem.getSequence(Tag.ReferencedSeriesSequence);
                if (refSeriesSeq != null) {
                    for (Attributes seriesItem : refSeriesSeq) {
                        SeriesMetadata series = extractSeriesMetadata(seriesItem, study.studyInstanceUID);
                        study.series.add(series);
                        study.numberOfStudyRelatedInstances += series.instances.size();
                        if (series.modality != null) {
                            modalities.add(series.modality);
                        }
                    }
                }
            }
        }

        study.numberOfStudyRelatedSeries = study.series.size();
        study.modalitiesInStudy = modalities.isEmpty() ? null : String.join("\\", modalities);

        // Derive study-level Retrieve URL from first series URL
        if (!study.series.isEmpty() && study.series.get(0).retrieveURL != null) {
            String seriesUrl = study.series.get(0).retrieveURL;
            int seriesIndex = seriesUrl.lastIndexOf("/series/");
            if (seriesIndex > 0) {
                study.retrieveURL = seriesUrl.substring(0, seriesIndex);
            }

            // Register study in WADO-RS proxy registry if available
            if (proxyRegistry != null && study.studyInstanceUID != null) {
                proxyRegistry.registerStudy(study.studyInstanceUID, seriesUrl);
            }
        }

        return study;
    }

    /**
     * Extract series metadata from MADO series item.
     */
    private SeriesMetadata extractSeriesMetadata(Attributes seriesItem, String studyInstanceUID) {
        SeriesMetadata series = new SeriesMetadata();
        series.studyInstanceUID = studyInstanceUID;
        series.seriesInstanceUID = seriesItem.getString(Tag.SeriesInstanceUID);
        series.modality = seriesItem.getString(Tag.Modality);
        series.seriesNumber = seriesItem.getString(Tag.SeriesNumber);
        series.seriesDescription = seriesItem.getString(Tag.SeriesDescription);

        // IMPORTANT: Extract WADO-RS URL for C-MOVE
        series.retrieveURL = seriesItem.getString(Tag.RetrieveURL);
        series.retrieveLocationUID = seriesItem.getString(Tag.RetrieveLocationUID);

        // Extract instances
        Sequence refSopSeq = seriesItem.getSequence(Tag.ReferencedSOPSequence);
        if (refSopSeq != null) {
            for (Attributes sopItem : refSopSeq) {
                InstanceMetadata instance = extractInstanceMetadata(sopItem, studyInstanceUID, series.seriesInstanceUID, series.retrieveURL);
                series.instances.add(instance);
            }
        }

        return series;
    }

    /**
     * Extract instance metadata from MADO SOP item.
     *
     * @param sopItem The SOP item from the MADO Referenced SOP Sequence
     * @param studyInstanceUID The study instance UID
     * @param seriesInstanceUID The series instance UID
     * @param seriesRetrieveURL The series-level retrieve URL from which to construct instance URL
     */
    private InstanceMetadata extractInstanceMetadata(Attributes sopItem, String studyInstanceUID, String seriesInstanceUID, String seriesRetrieveURL) {
        InstanceMetadata instance = new InstanceMetadata();
        instance.studyInstanceUID = studyInstanceUID;
        instance.seriesInstanceUID = seriesInstanceUID;
        instance.sopInstanceUID = sopItem.getString(Tag.ReferencedSOPInstanceUID);
        instance.sopClassUID = sopItem.getString(Tag.ReferencedSOPClassUID);
        instance.instanceNumber = sopItem.getString(Tag.InstanceNumber);
        instance.numberOfFrames = sopItem.getString(Tag.NumberOfFrames);
        instance.rows = sopItem.getString(Tag.Rows);
        instance.columns = sopItem.getString(Tag.Columns);

        // Construct instance-level Retrieve URL from series-level URL
        // MADO stores Retrieve URL at series level, so we build instance URL by appending /instances/{sopUID}
        instance.retrieveURL = sopItem.getString(Tag.RetrieveURL);  // Check if explicitly present first
        if (instance.retrieveURL == null && seriesRetrieveURL != null && instance.sopInstanceUID != null) {
            // Construct: {seriesURL}/instances/{sopInstanceUID}
            instance.retrieveURL = seriesRetrieveURL + "/instances/" + instance.sopInstanceUID;
        }

        return instance;
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    private StudyMetadata convertDocRefToStudyMetadata(DocumentReference docRef) {
        // DocumentReference contains study-level info from C-FIND
        // We'll need to parse identifiers and metadata from the FHIR resource
        StudyMetadata study = new StudyMetadata();

        // Extract Study Instance UID from identifier
        if (docRef.getMasterIdentifier() != null) {
            study.studyInstanceUID = docRef.getMasterIdentifier().getValue();
        }

        // Extract other identifiers
        for (org.hl7.fhir.r4.model.Identifier id : docRef.getIdentifier()) {
            String system = id.getSystem();
            String value = id.getValue();
            if (system != null && system.contains("accession")) {
                study.accessionNumber = value;
            }
        }

        // Extract patient reference
        if (docRef.getSubject() != null) {
            if (docRef.getSubject().getIdentifier() != null) {
                study.patientId = docRef.getSubject().getIdentifier().getValue();
            }
            // Extract patient name from subject display
            if (docRef.getSubject().getDisplay() != null && !docRef.getSubject().getDisplay().isEmpty()) {
                study.patientName = docRef.getSubject().getDisplay();
            }
        }

        // Extract description
        study.studyDescription = docRef.getDescription();

        // Extract date and time
        if (docRef.getDate() != null) {
            // Format date as DICOM DA
            java.text.SimpleDateFormat sdfDate = new java.text.SimpleDateFormat("yyyyMMdd");
            study.studyDate = sdfDate.format(docRef.getDate());
            // Format time as DICOM TM
            java.text.SimpleDateFormat sdfTime = new java.text.SimpleDateFormat("HHmmss");
            study.studyTime = sdfTime.format(docRef.getDate());
        }

        // Extract modality from context.event (collect all modalities)
        if (docRef.hasContext() && docRef.getContext().hasEvent()) {
            List<String> modalities = new ArrayList<>();
            for (org.hl7.fhir.r4.model.CodeableConcept event : docRef.getContext().getEvent()) {
                if (event.hasCoding()) {
                    for (org.hl7.fhir.r4.model.Coding coding : event.getCoding()) {
                        if (coding.hasCode()) {
                            String modalityCode = coding.getCode();
                            if (!modalities.contains(modalityCode)) {
                                modalities.add(modalityCode);
                            }
                        }
                    }
                }
            }
            if (!modalities.isEmpty()) {
                // Join with backslash as per DICOM standard for multi-valued CS
                study.modalitiesInStudy = String.join("\\", modalities);
            }
        }

        // Extract accession number from context.related (ServiceRequest)
        if (docRef.hasContext() && docRef.getContext().hasRelated()) {
            for (org.hl7.fhir.r4.model.Reference related : docRef.getContext().getRelated()) {
                if ("ServiceRequest".equals(related.getType()) && related.hasIdentifier()) {
                    if (study.accessionNumber == null || study.accessionNumber.isEmpty()) {
                        study.accessionNumber = related.getIdentifier().getValue();
                    }
                }
            }
        }

        // Extract referring physician from author
        if (docRef.hasAuthor() && !docRef.getAuthor().isEmpty()) {
            org.hl7.fhir.r4.model.Reference author = docRef.getAuthor().get(0);
            if (author.hasDisplay()) {
                String authorDisplay = author.getDisplay();
                // Don't use generic "Unknown Author" as referring physician
                if (!"Unknown Author".equals(authorDisplay)) {
                    study.referringPhysicianName = authorDisplay;
                }
            }
        }

        return study;
    }

    private boolean matchesSeries(SeriesMetadata series, String seriesInstanceUID, String modality) {
        if (seriesInstanceUID != null && !seriesInstanceUID.isEmpty() && !seriesInstanceUID.equals("*")) {
            if (!series.seriesInstanceUID.equals(seriesInstanceUID)) {
                return false;
            }
        }
        if (modality != null && !modality.isEmpty() && !modality.equals("*")) {
            if (!modality.equalsIgnoreCase(series.modality)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if a study's modalities match the requested modality.
     * Handles both single modality and multi-valued (backslash-separated) modalities.
     */
    private boolean matchesModality(String modalitiesInStudy, String requestedModality) {
        if (modalitiesInStudy == null || modalitiesInStudy.isEmpty()) {
            return false;
        }

        // Split by backslash to handle multi-valued modalities
        String[] modalities = modalitiesInStudy.split("\\\\");
        for (String modality : modalities) {
            if (modality.trim().equalsIgnoreCase(requestedModality.trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean isCacheExpired(StudyMetadata metadata) {
        return System.currentTimeMillis() - metadata.fetchedAt > CACHE_TTL_MS;
    }

    /**
     * Clear the metadata cache.
     */
    public void clearCache() {
        metadataCache.clear();
        LOG.info("Metadata cache cleared");
    }

    /**
     * Get cache statistics.
     */
    public int getCacheSize() {
        return metadataCache.size();
    }

    // ============================================================================
    // Data Classes
    // ============================================================================

    public static class StudyMetadata {
        public String studyInstanceUID;
        public String patientId;
        public String patientName;
        public String patientBirthDate;
        public String patientSex;
        public String studyDate;
        public String studyTime;
        public String studyDescription;
        public String studyID;
        public String accessionNumber;
        public String referringPhysicianName;
        public String modalitiesInStudy;
        public int numberOfStudyRelatedSeries;
        public int numberOfStudyRelatedInstances;
        public String retrieveURL;  // Study-level WADO-RS URL (derived from series)
        public List<SeriesMetadata> series = new ArrayList<>();
        public long fetchedAt;

        public Attributes toAttributes() {
            Attributes attrs = new Attributes();
            setIfNotNull(attrs, Tag.StudyInstanceUID, VR.UI, studyInstanceUID);
            setIfNotNull(attrs, Tag.PatientID, VR.LO, patientId);
            setIfNotNull(attrs, Tag.PatientName, VR.PN, patientName);
            setIfNotNull(attrs, Tag.PatientBirthDate, VR.DA, patientBirthDate);
            setIfNotNull(attrs, Tag.PatientSex, VR.CS, patientSex);
            setIfNotNull(attrs, Tag.StudyDate, VR.DA, studyDate);
            setIfNotNull(attrs, Tag.StudyTime, VR.TM, studyTime);
            setIfNotNull(attrs, Tag.StudyDescription, VR.LO, studyDescription);
            setIfNotNull(attrs, Tag.StudyID, VR.SH, studyID);
            setIfNotNull(attrs, Tag.AccessionNumber, VR.SH, accessionNumber);
            setIfNotNull(attrs, Tag.ReferringPhysicianName, VR.PN, referringPhysicianName);
            setIfNotNull(attrs, Tag.ModalitiesInStudy, VR.CS, modalitiesInStudy);
            attrs.setInt(Tag.NumberOfStudyRelatedSeries, VR.IS, numberOfStudyRelatedSeries);
            attrs.setInt(Tag.NumberOfStudyRelatedInstances, VR.IS, numberOfStudyRelatedInstances);
            setIfNotNull(attrs, Tag.RetrieveURL, VR.UR, retrieveURL);  // Study-level WADO-RS URL
            return attrs;
        }

        /**
         * Rewrite retrieve URL to point to local WADO-RS proxy.
         * @param localBaseUrl Base URL of this service (e.g., http://localhost:8080/dicomweb)
         */
        public void rewriteUrlsToProxy(String localBaseUrl) {
            if (retrieveURL != null && studyInstanceUID != null) {
                retrieveURL = localBaseUrl + "/studies/" + studyInstanceUID;
            }
            for (SeriesMetadata series : series) {
                series.rewriteUrlsToProxy(localBaseUrl);
            }
        }

        private void setIfNotNull(Attributes attrs, int tag, VR vr, String value) {
            if (value != null && !value.isEmpty()) {
                attrs.setString(tag, vr, value);
            }
        }
    }

    public static class SeriesMetadata {
        public String studyInstanceUID;
        public String seriesInstanceUID;
        public String modality;
        public String seriesNumber;
        public String seriesDescription;
        public String retrieveURL;  // WADO-RS URL from MADO
        public String retrieveLocationUID;
        public List<InstanceMetadata> instances = new ArrayList<>();

        public Attributes toAttributes() {
            Attributes attrs = new Attributes();
            setIfNotNull(attrs, Tag.StudyInstanceUID, VR.UI, studyInstanceUID);
            setIfNotNull(attrs, Tag.SeriesInstanceUID, VR.UI, seriesInstanceUID);
            setIfNotNull(attrs, Tag.Modality, VR.CS, modality);
            setIfNotNull(attrs, Tag.SeriesNumber, VR.IS, seriesNumber);
            setIfNotNull(attrs, Tag.SeriesDescription, VR.LO, seriesDescription);
            attrs.setInt(Tag.NumberOfSeriesRelatedInstances, VR.IS, instances.size());
            setIfNotNull(attrs, Tag.RetrieveURL, VR.UR, retrieveURL);  // WADO-RS URL from MADO
            return attrs;
        }

        /**
         * Rewrite retrieve URL to point to local WADO-RS proxy.
         * @param localBaseUrl Base URL of this service (e.g., http://localhost:8080/dicomweb)
         */
        public void rewriteUrlsToProxy(String localBaseUrl) {
            if (retrieveURL != null && studyInstanceUID != null && seriesInstanceUID != null) {
                retrieveURL = localBaseUrl + "/studies/" + studyInstanceUID + "/series/" + seriesInstanceUID;
            }
            for (InstanceMetadata instance : instances) {
                instance.rewriteUrlsToProxy(localBaseUrl, studyInstanceUID, seriesInstanceUID);
            }
        }

        private void setIfNotNull(Attributes attrs, int tag, VR vr, String value) {
            if (value != null && !value.isEmpty()) {
                attrs.setString(tag, vr, value);
            }
        }
    }

    public static class InstanceMetadata {
        public String studyInstanceUID;
        public String seriesInstanceUID;
        public String sopInstanceUID;
        public String sopClassUID;
        public String instanceNumber;
        public String numberOfFrames;
        public String rows;
        public String columns;
        public String retrieveURL;  // Instance-level WADO-RS URL if present

        public Attributes toAttributes() {
            Attributes attrs = new Attributes();
            setIfNotNull(attrs, Tag.StudyInstanceUID, VR.UI, studyInstanceUID);
            setIfNotNull(attrs, Tag.SeriesInstanceUID, VR.UI, seriesInstanceUID);
            setIfNotNull(attrs, Tag.SOPInstanceUID, VR.UI, sopInstanceUID);
            setIfNotNull(attrs, Tag.SOPClassUID, VR.UI, sopClassUID);
            setIfNotNull(attrs, Tag.InstanceNumber, VR.IS, instanceNumber);
            setIfNotNull(attrs, Tag.NumberOfFrames, VR.IS, numberOfFrames);
            setIfNotNull(attrs, Tag.Rows, VR.US, rows);
            setIfNotNull(attrs, Tag.Columns, VR.US, columns);
            setIfNotNull(attrs, Tag.RetrieveURL, VR.UR, retrieveURL);  // WADO-RS URL from MADO
            return attrs;
        }

        /**
         * Rewrite retrieve URL to point to local WADO-RS proxy.
         * @param localBaseUrl Base URL of this service (e.g., http://localhost:8080/dicomweb)
         * @param studyUID Study Instance UID
         * @param seriesUID Series Instance UID
         */
        public void rewriteUrlsToProxy(String localBaseUrl, String studyUID, String seriesUID) {
            if (retrieveURL != null && sopInstanceUID != null) {
                retrieveURL = localBaseUrl + "/studies/" + studyUID + "/series/" + seriesUID + "/instances/" + sopInstanceUID;
            }
        }

        private void setIfNotNull(Attributes attrs, int tag, VR vr, String value) {
            if (value != null && !value.isEmpty()) {
                attrs.setString(tag, vr, value);
            }
        }
    }
}

