package be.uzleuven.ihe.service.scp;

import be.uzleuven.ihe.dicom.constants.CodeConstants;
import be.uzleuven.ihe.dicom.validator.utils.SRContentTreeUtils;
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
        String[] studyDateRange = parseStudyDateRange(keys.getString(Tag.StudyDate));

        LOG.info("Find studies from MADO (MHD): patientId={}, accession={}, studyUID={}, modality={}, date={}-{}",
                patientId, accessionNumber, studyInstanceUID, modality, studyDateRange[0], studyDateRange[1]);

        List<DocumentReference> docRefs = mhdFhirClient.searchDocumentReferences(
                patientId, accessionNumber, studyInstanceUID, modality, studyDateRange[0], studyDateRange[1]);

        LOG.info("MHD returned {} DocumentReferences", docRefs.size());
        if (docRefs.isEmpty() && modality != null) {
            LOG.warn("No results returned for modality filter: {}", modality);
        }

        List<StudyMetadata> results = new ArrayList<>();
        for (DocumentReference docRef : docRefs) {
            StudyMetadata metadata = convertDocRefToStudyMetadata(docRef);
            if (metadata != null && matchesModalityFilter(metadata.modalitiesInStudy, modality)) {
                results.add(metadata);
            }
        }

        LOG.info("After modality filtering: {} studies", results.size());
        return results;
    }

    /**
     * Parse a DICOM study date string into a [from, to] range array.
     * Returns [null, null] if the date string is null or empty.
     */
    private String[] parseStudyDateRange(String studyDate) {
        if (studyDate == null || studyDate.isEmpty()) {
            return new String[]{null, null};
        }
        if (studyDate.contains("-")) {
            String[] parts = studyDate.split("-", 2);
            return new String[]{
                parts[0].isEmpty() ? null : parts[0],
                parts.length > 1 && !parts[1].isEmpty() ? parts[1] : null
            };
        }
        return new String[]{studyDate, studyDate};
    }

    /**
     * Returns true if no modality filter is active, or if the study's modalities include the requested one.
     */
    private boolean matchesModalityFilter(String modalitiesInStudy, String requestedModality) {
        if (requestedModality == null || requestedModality.isEmpty() || requestedModality.equals("*")) {
            return true;
        }
        return matchesModality(modalitiesInStudy, requestedModality);
    }

    /**
     * Find series matching the given DICOM query keys.
     * Retrieves MADO from MHD to get series-level detail.
     */
    public List<SeriesMetadata> findSeries(Attributes keys) throws IOException {
        String studyInstanceUID = keys.getString(Tag.StudyInstanceUID);
        String seriesInstanceUID = keys.getString(Tag.SeriesInstanceUID);
        String modality = keys.getString(Tag.Modality);

        LOG.info("Find series from MADO (MHD): studyUID={}, seriesUID={}, modality={}",
                studyInstanceUID, seriesInstanceUID, modality);

        if (isSpecified(studyInstanceUID)) {
            return findSeriesInStudy(studyInstanceUID, seriesInstanceUID, modality);
        }
        if (isSpecified(seriesInstanceUID)) {
            return findSeriesAcrossCache(seriesInstanceUID);
        }
        return Collections.emptyList();
    }

    /** Fetch the given study and return its series that match the optional series/modality filters. */
    private List<SeriesMetadata> findSeriesInStudy(String studyInstanceUID, String seriesInstanceUID, String modality) throws IOException {
        StudyMetadata studyMeta = getOrFetchStudyMetadata(studyInstanceUID);
        if (studyMeta == null) {
            return Collections.emptyList();
        }
        List<SeriesMetadata> results = new ArrayList<>();
        for (SeriesMetadata series : studyMeta.series) {
            if (matchesSeries(series, seriesInstanceUID, modality)) {
                results.add(series);
            }
        }
        return results;
    }

    /** Search all cached studies for a series with the given series instance UID. */
    private List<SeriesMetadata> findSeriesAcrossCache(String seriesInstanceUID) {
        List<SeriesMetadata> results = new ArrayList<>();
        for (StudyMetadata study : metadataCache.values()) {
            for (SeriesMetadata series : study.series) {
                if (seriesInstanceUID.equals(series.seriesInstanceUID)) {
                    results.add(series);
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

        LOG.info("Find instances from MADO (MHD): studyUID={}, seriesUID={}, sopUID={}",
                studyInstanceUID, seriesInstanceUID, sopInstanceUID);

        if (!isSpecified(studyInstanceUID)) {
            return Collections.emptyList();
        }

        StudyMetadata studyMeta = getOrFetchStudyMetadata(studyInstanceUID);
        if (studyMeta == null) {
            return Collections.emptyList();
        }

        return collectInstances(studyMeta, seriesInstanceUID, sopInstanceUID);
    }

    /** Collect instances from a study, optionally filtered by series and SOP instance UID. */
    private List<InstanceMetadata> collectInstances(StudyMetadata studyMeta, String seriesInstanceUID, String sopInstanceUID) {
        List<InstanceMetadata> results = new ArrayList<>();
        for (SeriesMetadata series : studyMeta.series) {
            if (!isSpecified(seriesInstanceUID) || seriesInstanceUID.equals(series.seriesInstanceUID)) {
                for (InstanceMetadata instance : series.instances) {
                    if (!isSpecified(sopInstanceUID) || sopInstanceUID.equals(instance.sopInstanceUID)) {
                        results.add(instance);
                    }
                }
            }
        }
        return results;
    }

    /** Returns true when a UID filter value is present and not a wildcard. */
    private boolean isSpecified(String uid) {
        return uid != null && !uid.isEmpty() && !uid.equals("*");
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

        extractSeriesFromEvidenceSequence(attrs, study);
        addMetadataFromTID1600(attrs, study);

        study.numberOfStudyRelatedSeries = study.series.size();
        deriveStudyRetrieveURL(study);

        return study;
    }

    /**
     * Populate study.series (and related counts/modalities) from
     * CurrentRequestedProcedureEvidenceSequence → ReferencedSeriesSequence.
     */
    private void extractSeriesFromEvidenceSequence(Attributes attrs, StudyMetadata study) {
        Sequence evidenceSeq = attrs.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence);
        if (evidenceSeq == null) return;

        Set<String> modalities = new HashSet<>();
        for (Attributes studyItem : evidenceSeq) {
            extractSeriesFromReferencedSeriesSequence(studyItem, study, modalities);
        }
        study.modalitiesInStudy = modalities.isEmpty() ? null : String.join("\\", modalities);
    }

    /** Extract all series from one study item in the evidence sequence. */
    private void extractSeriesFromReferencedSeriesSequence(Attributes studyItem, StudyMetadata study, Set<String> modalities) {
        Sequence refSeriesSeq = studyItem.getSequence(Tag.ReferencedSeriesSequence);
        if (refSeriesSeq == null) return;

        for (Attributes seriesItem : refSeriesSeq) {
            SeriesMetadata series = extractMetadataFromReferencedSeriesSeqItem(seriesItem, study.studyInstanceUID);
            study.series.add(series);
            study.numberOfStudyRelatedInstances += series.instances.size();
            if (series.modality != null) {
                modalities.add(series.modality);
            }
        }
    }

    /**
     * Derive study-level Retrieve URL from first series URL and register with the proxy registry.
     */
    private void deriveStudyRetrieveURL(StudyMetadata study) {
        if (study.series.isEmpty()) return;

        String seriesUrl = study.series.get(0).retrieveURL;
        if (seriesUrl == null) return;

        int seriesIndex = seriesUrl.lastIndexOf("/series/");
        if (seriesIndex > 0) {
            study.retrieveURL = seriesUrl.substring(0, seriesIndex);
        }

        if (proxyRegistry != null && study.studyInstanceUID != null) {
            proxyRegistry.registerStudy(study.studyInstanceUID, seriesUrl);
        }
    }


    /**
     * Extract some series and instance metadata from CurrentRequestedProcedureEvidenceSequence / ReferencedSeriesSequence item
     */
    private SeriesMetadata extractMetadataFromReferencedSeriesSeqItem(Attributes seriesItem, String studyInstanceUID) {
        SeriesMetadata series = new SeriesMetadata();
        series.studyInstanceUID = studyInstanceUID;
        series.seriesInstanceUID = seriesItem.getString(Tag.SeriesInstanceUID);
        series.modality = seriesItem.getString(Tag.Modality);
        // the following 2 tags should not be present in ReferencedSeriesSequence items?
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
     * Extract instance metadata from MADO ReferencedSOPSequence item
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
        // the following 4 tags should not be present in a ReferencedSOPSequence
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

    private void addMetadataFromTID1600(Attributes root, StudyMetadata studyMetadata) {
        Sequence contentSeq = root.getSequence(Tag.ContentSequence);
        if (contentSeq == null) return;

        for (Attributes topLevelItem : contentSeq) {
            // There is only one Image Library Container TID 1600 according to MADO spec
            Attributes imageLibraryContainer = SRContentTreeUtils.findContainerByConcept(
                    topLevelItem, CodeConstants.CODE_IMAGE_LIBRARY, CodeConstants.SCHEME_DCM);
            if (imageLibraryContainer != null) {
                addMetadataFromImageLibraryContainer(imageLibraryContainer, studyMetadata);
            }
        }
    }

    /**
     * Process an Image Library Container (TID 1600) and enrich series/instance metadata.
     * There may be several Image Library Group Containers (TID 1601), one per series, according to MADO spec.
     * Each image library group corresponds to one series.
     */
    private void addMetadataFromImageLibraryContainer(Attributes imageLibraryContainer, StudyMetadata studyMetadata) {
        List<Attributes> imageGroupLibraryContainers = SRContentTreeUtils.findDirectChildContainersByConcept(
                imageLibraryContainer, CodeConstants.CODE_IMAGE_LIBRARY_GROUP, CodeConstants.SCHEME_DCM);

        for (Attributes group : imageGroupLibraryContainers) {
            Sequence groupContentSeq = group.getSequence(Tag.ContentSequence);
            if (groupContentSeq != null) {
                addMetadataFromImageLibraryGroup(groupContentSeq, studyMetadata);
            }
        }
    }

    /**
     * Process a single Image Library Group (TID 1601) and enrich the matching series and its instances.
     * It would be more efficient to iterate through the sequence of items and identify different items;
     * finding items is less efficient (may iterate through items several times).
     */
    private void addMetadataFromImageLibraryGroup(Sequence groupContentSeq, StudyMetadata studyMetadata) {
        String seriesInstanceUID = SRContentTreeUtils.findValueByConceptNameAndValueTag(
                groupContentSeq, CodeConstants.CODE_SERIES_INSTANCE_UID, CodeConstants.SCHEME_DCM, Tag.UID);
        if (seriesInstanceUID == null || seriesInstanceUID.isEmpty()) return;

        // Add these attributes to the existing series metadata (must find the matching series first)
        studyMetadata.series.stream()
                .filter(s -> s.seriesInstanceUID.equals(seriesInstanceUID))
                .findFirst()
                .ifPresent(series -> enrichSeriesFromTID1601(groupContentSeq, series));
    }

    /**
     * Enrich a series with description/number from TID 1601 (attributes added by MADO spec),
     * and look into Image Library Entry TID 1602 (instance level) for each image in this series.
     */
    private void enrichSeriesFromTID1601(Sequence groupContentSeq, SeriesMetadata series) {
        // Attributes added by MADO spec
        series.seriesDescription = SRContentTreeUtils.findValueByConceptNameAndValueTag(
                groupContentSeq, CodeConstants.CODE_SERIES_DESCRIPTION, CodeConstants.SCHEME_DCM, Tag.TextValue);
        series.seriesNumber = SRContentTreeUtils.findValueByConceptNameAndValueTag(
                groupContentSeq, CodeConstants.CODE_SERIES_NUMBER, CodeConstants.SCHEME_DCM, Tag.TextValue);

        // Look into Image Library Entry TID 1602 (instance level) for this series and
        // add them to the existing instance metadata (must find the matching instance first)
        List<Attributes> images = SRContentTreeUtils.findItemsByValueType(groupContentSeq, "IMAGE");
        for (Attributes image : images) {
            enrichInstanceFromTID1602(image, series);
        }
    }

    /**
     * Enrich a single instance with instance-level attributes added by MADO spec (TID 1602):
     * instance number and frame count.
     */
    private void enrichInstanceFromTID1602(Attributes image, SeriesMetadata series) {
        String sopInstanceUID = SRContentTreeUtils.firstItem(image.getSequence(Tag.ReferencedSOPSequence))
                .getString(Tag.ReferencedSOPInstanceUID);
        if (sopInstanceUID == null || sopInstanceUID.isEmpty()) return;

        // Instance level attributes added by MADO spec
        String instanceNumber = SRContentTreeUtils.findValueByConceptNameAndValueTag(
                image.getSequence(Tag.ContentSequence), CodeConstants.CODE_INSTANCE_NUMBER, CodeConstants.SCHEME_DCM, Tag.TextValue);
        String numberOfFrames = SRContentTreeUtils.findValueByConceptNameAndValueTag(
                image.getSequence(Tag.ContentSequence), CodeConstants.CODE_NUMBER_OF_FRAMES, CodeConstants.SCHEME_DCM, Tag.TextValue);

        // Add them to the existing instance metadata (must find the matching instance first)
        series.instances.stream()
                .filter(in -> sopInstanceUID.equals(in.sopInstanceUID))
                .findFirst()
                .ifPresent(in -> {
                    in.instanceNumber = instanceNumber;
                    in.numberOfFrames = numberOfFrames;
                });
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    private StudyMetadata convertDocRefToStudyMetadata(DocumentReference docRef) {
        StudyMetadata study = new StudyMetadata();
        study.studyInstanceUID = extractStudyInstanceUID(docRef);
        study.accessionNumber = extractAccessionNumber(docRef);
        study.studyDescription = docRef.getDescription();
        study.modalitiesInStudy = extractModalitiesInStudy(docRef);
        study.referringPhysicianName = extractReferringPhysicianName(docRef);
        extractPatientInfo(docRef, study);
        extractStudyDateTime(docRef, study);
        return study;
    }

    private String extractStudyInstanceUID(DocumentReference docRef) {
        return docRef.getMasterIdentifier() != null ? docRef.getMasterIdentifier().getValue() : null;
    }

    /** Extracts accession number from identifiers or from context.related ServiceRequest. */
    private String extractAccessionNumber(DocumentReference docRef) {
        // Check direct identifiers first
        for (org.hl7.fhir.r4.model.Identifier id : docRef.getIdentifier()) {
            if (id.getSystem() != null && id.getSystem().contains("accession")) {
                return id.getValue();
            }
        }
        // Fall back to context.related ServiceRequest
        if (docRef.hasContext() && docRef.getContext().hasRelated()) {
            for (org.hl7.fhir.r4.model.Reference related : docRef.getContext().getRelated()) {
                if ("ServiceRequest".equals(related.getType()) && related.hasIdentifier()) {
                    return related.getIdentifier().getValue();
                }
            }
        }
        return null;
    }

    /** Populates patient ID and name from docRef.subject. */
    private void extractPatientInfo(DocumentReference docRef, StudyMetadata study) {
        org.hl7.fhir.r4.model.Reference subject = docRef.getSubject();
        if (subject == null) return;

        if (subject.getIdentifier() != null) {
            study.patientId = subject.getIdentifier().getValue();
        }
        if (subject.getDisplay() != null && !subject.getDisplay().isEmpty()) {
            study.patientName = subject.getDisplay();
        }
    }

    /** Populates study date and time (DICOM DA/TM format) from docRef.date. */
    private void extractStudyDateTime(DocumentReference docRef, StudyMetadata study) {
        if (docRef.getDate() == null) return;

        study.studyDate = new java.text.SimpleDateFormat("yyyyMMdd").format(docRef.getDate());
        study.studyTime = new java.text.SimpleDateFormat("HHmmss").format(docRef.getDate());
    }

    /**
     * Extracts all modalities from context.event codings and joins them with backslash
     * as per the DICOM standard for multi-valued CS fields.
     */
    private String extractModalitiesInStudy(DocumentReference docRef) {
        if (!docRef.hasContext() || !docRef.getContext().hasEvent()) return null;

        List<String> modalities = new ArrayList<>();
        for (org.hl7.fhir.r4.model.CodeableConcept event : docRef.getContext().getEvent()) {
            for (org.hl7.fhir.r4.model.Coding coding : event.getCoding()) {
                if (coding.hasCode() && !modalities.contains(coding.getCode())) {
                    modalities.add(coding.getCode());
                }
            }
        }
        return modalities.isEmpty() ? null : String.join("\\", modalities);
    }

    /**
     * Extracts the referring physician from the first author of the DocumentReference.
     * Returns null if no author is present or if the author is the generic "Unknown Author".
     */
    private String extractReferringPhysicianName(DocumentReference docRef) {
        if (!docRef.hasAuthor() || docRef.getAuthor().isEmpty()) return null;

        org.hl7.fhir.r4.model.Reference author = docRef.getAuthor().get(0);
        if (!author.hasDisplay()) return null;

        String display = author.getDisplay();
        return "Unknown Author".equals(display) ? null : display;
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

