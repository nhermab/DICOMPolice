package be.uzleuven.ihe.service.qido;

import be.uzleuven.ihe.service.scp.MHDBackedMetadataService;
import be.uzleuven.ihe.service.scp.MHDBackedMetadataService.InstanceMetadata;
import be.uzleuven.ihe.service.scp.MHDBackedMetadataService.SeriesMetadata;
import be.uzleuven.ihe.service.scp.MHDBackedMetadataService.StudyMetadata;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * QIDO-RS (Query based on ID for DICOM Objects) REST Controller.
 *
 * Implements DICOMweb QIDO-RS search services as specified in DICOM PS3.18:
 * - SearchForStudies: GET /studies
 * - SearchForSeries: GET /studies/{StudyUID}/series
 * - SearchForInstances: GET /studies/{StudyUID}/series/{SeriesUID}/instances
 *
 * This controller bridges HTTP requests to the existing MHD/MADO backend
 * via MHDBackedMetadataService.
 */
@RestController
@RequestMapping("/dicomweb")
public class QIDORestController implements DisposableBean {

    private static final Logger LOG = LoggerFactory.getLogger(QIDORestController.class);

    private static final String DICOM_JSON_MEDIA_TYPE = "application/dicom+json";
    private static final String DICOM_XML_MEDIA_TYPE = "application/dicom+xml";

    private final MHDBackedMetadataService metadataService;
    private final QIDOConfiguration configuration;

    // Thread pool for parallel MADO downloads (10 concurrent downloads)
    private final ExecutorService madoDownloadExecutor;

    @Autowired
    public QIDORestController(MHDBackedMetadataService metadataService, QIDOConfiguration configuration) {
        this.metadataService = metadataService;
        this.configuration = configuration;
        this.madoDownloadExecutor = Executors.newFixedThreadPool(10);

        LOG.info("QIDO-RS Controller initialized:");
        LOG.info("  - WADO Proxy Mode: {}", configuration.isWadoProxyEnabled());
        LOG.info("  - Base URL: {}", configuration.getBaseUrl());
    }

    @Override
    public void destroy() {
        LOG.info("Shutting down MADO download executor service");
        madoDownloadExecutor.shutdown();
        try {
            if (!madoDownloadExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                madoDownloadExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            madoDownloadExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ============================================================================
    // SearchForStudies - GET /studies
    // ============================================================================

    /**
     * Search for DICOM Studies.
     *
     * Supported query parameters:
     * - PatientID, PatientName, StudyDate, AccessionNumber, ModalitiesInStudy, etc.
     * - includefield: Additional fields to return (or "all")
     * - limit: Maximum number of results
     * - offset: Skip results for pagination
     * - fuzzymatching: Enable fuzzy matching for Patient Name
     */
    @GetMapping(value = "/studies", produces = {DICOM_JSON_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<List<Map<String, Object>>> searchStudies(
            @RequestParam MultiValueMap<String, String> allParams) {

        LOG.info("QIDO-RS SearchForStudies: {}", allParams);

        try {
            // Parse query parameters to DICOM Attributes
            Attributes queryKeys = QIDOUtils.parseQueryParams(allParams);

            // Query MHD backend
            List<StudyMetadata> studies = metadataService.findStudies(queryKeys);

            LOG.info("Found {} matching studies", studies.size());

            // Apply pagination
            int offset = QIDOUtils.parseOffset(allParams);
            int limit = QIDOUtils.parseLimit(allParams, configuration.getDefaultLimit(), configuration.getMaxLimit());
            studies = applyPagination(studies, offset, limit);

            // Fetch full metadata (with Retrieve URLs) in parallel
            List<StudyMetadata> fullStudies = fetchStudiesInParallel(studies);

            // Rewrite URLs to local proxy if proxy mode is enabled
            if (configuration.isWadoProxyEnabled()) {
                LOG.info("WADO Proxy mode is ENABLED - rewriting URLs to: {}", configuration.getBaseUrl());
                for (StudyMetadata study : fullStudies) {
                    LOG.debug("Rewriting URLs for study: {}", study.studyInstanceUID);
                    study.rewriteUrlsToProxy(configuration.getBaseUrl());
                }
            } else {
                LOG.debug("WADO Proxy mode is DISABLED - using remote URLs");
            }

            // Parse includefield parameter
            Set<Integer> includeFields = QIDOUtils.parseIncludeFields(allParams);

            // Convert to DICOM JSON (Retrieve URLs are now in toAttributes())
            List<Map<String, Object>> response = fullStudies.stream()
                    .map(study -> DICOMJSONConverter.toJSON(study.toAttributes(), includeFields))
                    .collect(Collectors.toList());

            return buildResponse(response, fullStudies.size(), offset, limit);

        } catch (IOException e) {
            LOG.error("Error searching for studies: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ============================================================================
    // SearchForSeries - GET /studies/{StudyUID}/series
    // ============================================================================

    /**
     * Search for DICOM Series within a specific Study.
     *
     * Supported query parameters:
     * - Modality, SeriesNumber, SeriesDescription, etc.
     * - includefield, limit, offset
     */
    @GetMapping(value = "/studies/{studyUID}/series", produces = {DICOM_JSON_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<List<Map<String, Object>>> searchSeries(
            @PathVariable("studyUID") String studyUID,
            @RequestParam MultiValueMap<String, String> allParams) {

        LOG.info("QIDO-RS SearchForSeries: studyUID={}, params={}", studyUID, allParams);

        try {
            // Build query keys with Study Instance UID
            Attributes queryKeys = QIDOUtils.parseQueryParams(allParams);
            queryKeys.setString(Tag.StudyInstanceUID, VR.UI, studyUID);

            // Query MHD backend
            List<SeriesMetadata> seriesList = metadataService.findSeries(queryKeys);

            LOG.info("Found {} matching series", seriesList.size());

            // Rewrite URLs to local proxy if proxy mode is enabled
            if (configuration.isWadoProxyEnabled() && !seriesList.isEmpty()) {
                LOG.info("WADO Proxy mode is ENABLED - rewriting series URLs to: {}", configuration.getBaseUrl());
                for (SeriesMetadata series : seriesList) {
                    series.rewriteUrlsToProxy(configuration.getBaseUrl());
                }
            }

            // Apply pagination
            int offset = QIDOUtils.parseOffset(allParams);
            int limit = QIDOUtils.parseLimit(allParams, configuration.getDefaultLimit(), configuration.getMaxLimit());
            seriesList = applyPagination(seriesList, offset, limit);

            // Parse includefield parameter
            Set<Integer> includeFields = QIDOUtils.parseIncludeFields(allParams);

            // Convert to DICOM JSON (Retrieve URLs are now in toAttributes())
            List<Map<String, Object>> response = seriesList.stream()
                    .map(series -> DICOMJSONConverter.toJSON(series.toAttributes(), includeFields))
                    .collect(Collectors.toList());

            return buildResponse(response, seriesList.size(), offset, limit);

        } catch (IOException e) {
            LOG.error("Error searching for series: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ============================================================================
    // SearchForInstances - GET /studies/{StudyUID}/series/{SeriesUID}/instances
    // ============================================================================

    /**
     * Search for DICOM Instances within a specific Series.
     *
     * Supported query parameters:
     * - SOPClassUID, InstanceNumber, etc.
     * - includefield, limit, offset
     */
    @GetMapping(value = "/studies/{studyUID}/series/{seriesUID}/instances",
                produces = {DICOM_JSON_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<List<Map<String, Object>>> searchInstances(
            @PathVariable("studyUID") String studyUID,
            @PathVariable("seriesUID") String seriesUID,
            @RequestParam MultiValueMap<String, String> allParams) {

        LOG.info("QIDO-RS SearchForInstances: studyUID={}, seriesUID={}, params={}", studyUID, seriesUID, allParams);

        try {
            // Build query keys with Study and Series Instance UIDs
            Attributes queryKeys = QIDOUtils.parseQueryParams(allParams);
            queryKeys.setString(Tag.StudyInstanceUID, VR.UI, studyUID);
            queryKeys.setString(Tag.SeriesInstanceUID, VR.UI, seriesUID);

            // Query MHD backend
            List<InstanceMetadata> instances = metadataService.findInstances(queryKeys);

            LOG.info("Found {} matching instances", instances.size());

            // Rewrite URLs to local proxy if proxy mode is enabled
            if (configuration.isWadoProxyEnabled() && !instances.isEmpty()) {
                LOG.info("WADO Proxy mode is ENABLED - rewriting instance URLs to: {}", configuration.getBaseUrl());
                // Rewrite URLs for each instance
                for (InstanceMetadata instance : instances) {
                    instance.rewriteUrlsToProxy(configuration.getBaseUrl(), studyUID, seriesUID);
                }
            }

            // Apply pagination
            int offset = QIDOUtils.parseOffset(allParams);
            int limit = QIDOUtils.parseLimit(allParams, configuration.getDefaultLimit(), configuration.getMaxLimit());
            instances = applyPagination(instances, offset, limit);

            // Parse includefield parameter
            Set<Integer> includeFields = QIDOUtils.parseIncludeFields(allParams);

            // Convert to DICOM JSON (Retrieve URLs are now in toAttributes())
            List<Map<String, Object>> response = instances.stream()
                    .map(instance -> DICOMJSONConverter.toJSON(instance.toAttributes(), includeFields))
                    .collect(Collectors.toList());

            return buildResponse(response, instances.size(), offset, limit);

        } catch (IOException e) {
            LOG.error("Error searching for instances: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ============================================================================
    // Additional Search Endpoints (All Series / All Instances)
    // ============================================================================

    /**
     * Search for all Series across all Studies.
     * GET /series
     */
    @GetMapping(value = "/series", produces = {DICOM_JSON_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<List<Map<String, Object>>> searchAllSeries(
            @RequestParam MultiValueMap<String, String> allParams) {

        LOG.info("QIDO-RS SearchForAllSeries: {}", allParams);

        try {
            // First find matching studies
            Attributes queryKeys = QIDOUtils.parseQueryParams(allParams);
            List<StudyMetadata> studies = metadataService.findStudies(queryKeys);

            // Fetch full metadata in parallel for all studies
            List<StudyMetadata> fullStudies = fetchStudiesInParallel(studies);

            // Rewrite URLs to local proxy if proxy mode is enabled
            if (configuration.isWadoProxyEnabled()) {
                for (StudyMetadata study : fullStudies) {
                    study.rewriteUrlsToProxy(configuration.getBaseUrl());
                }
            }

            // Collect all series from matching studies
            List<SeriesMetadata> allSeries = new ArrayList<>();
            String modalityFilter = queryKeys.getString(Tag.Modality);

            for (StudyMetadata fullStudy : fullStudies) {
                if (fullStudy != null) {
                    for (SeriesMetadata series : fullStudy.series) {
                        // Apply modality filter if specified
                        if (modalityFilter == null || modalityFilter.isEmpty() ||
                            modalityFilter.equals("*") || modalityFilter.equalsIgnoreCase(series.modality)) {
                            allSeries.add(series);
                        }
                    }
                }
            }

            LOG.info("Found {} matching series across {} studies", allSeries.size(), studies.size());

            // Apply pagination
            int offset = QIDOUtils.parseOffset(allParams);
            int limit = QIDOUtils.parseLimit(allParams, configuration.getDefaultLimit(), configuration.getMaxLimit());
            allSeries = applyPagination(allSeries, offset, limit);

            // Parse includefield parameter
            Set<Integer> includeFields = QIDOUtils.parseIncludeFields(allParams);

            // Convert to DICOM JSON (Retrieve URLs are now in toAttributes())
            List<Map<String, Object>> response = allSeries.stream()
                    .map(series -> DICOMJSONConverter.toJSON(series.toAttributes(), includeFields))
                    .collect(Collectors.toList());

            return buildResponse(response, allSeries.size(), offset, limit);

        } catch (IOException e) {
            LOG.error("Error searching for all series: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Search for all Instances within a Study (across all Series).
     * GET /studies/{StudyUID}/instances
     */
    @GetMapping(value = "/studies/{studyUID}/instances",
                produces = {DICOM_JSON_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<List<Map<String, Object>>> searchStudyInstances(
            @PathVariable("studyUID") String studyUID,
            @RequestParam MultiValueMap<String, String> allParams) {

        LOG.info("QIDO-RS SearchForStudyInstances: studyUID={}, params={}", studyUID, allParams);

        try {
            // Build query keys with Study Instance UID only
            Attributes queryKeys = QIDOUtils.parseQueryParams(allParams);
            queryKeys.setString(Tag.StudyInstanceUID, VR.UI, studyUID);

            // Query MHD backend for all instances in study
            List<InstanceMetadata> instances = metadataService.findInstances(queryKeys);

            LOG.info("Found {} matching instances in study", instances.size());

            // Rewrite URLs to local proxy if proxy mode is enabled
            if (configuration.isWadoProxyEnabled() && !instances.isEmpty()) {
                LOG.info("WADO Proxy mode is ENABLED - rewriting instance URLs to: {}", configuration.getBaseUrl());
                for (InstanceMetadata instance : instances) {
                    instance.rewriteUrlsToProxy(configuration.getBaseUrl(),
                            instance.studyInstanceUID, instance.seriesInstanceUID);
                }
            }

            // Apply pagination
            int offset = QIDOUtils.parseOffset(allParams);
            int limit = QIDOUtils.parseLimit(allParams, configuration.getDefaultLimit(), configuration.getMaxLimit());
            instances = applyPagination(instances, offset, limit);

            // Parse includefield parameter
            Set<Integer> includeFields = QIDOUtils.parseIncludeFields(allParams);

            // Convert to DICOM JSON with Retrieve URLs from MADO manifest
            // Convert to DICOM JSON (Retrieve URLs are now in toAttributes())
            List<Map<String, Object>> response = instances.stream()
                    .map(instance -> DICOMJSONConverter.toJSON(instance.toAttributes(), includeFields))
                    .collect(Collectors.toList());

            return buildResponse(response, instances.size(), offset, limit);

        } catch (IOException e) {
            LOG.error("Error searching for study instances: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Search for all Instances across all Studies.
     * GET /instances
     */
    @GetMapping(value = "/instances", produces = {DICOM_JSON_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<List<Map<String, Object>>> searchAllInstances(
            @RequestParam MultiValueMap<String, String> allParams) {

        LOG.info("QIDO-RS SearchForAllInstances: {}", allParams);

        try {
            // First find matching studies
            Attributes queryKeys = QIDOUtils.parseQueryParams(allParams);
            List<StudyMetadata> studies = metadataService.findStudies(queryKeys);

            // Fetch full metadata in parallel for all studies
            List<StudyMetadata> fullStudies = fetchStudiesInParallel(studies);

            // Rewrite URLs to local proxy if proxy mode is enabled
            if (configuration.isWadoProxyEnabled()) {
                for (StudyMetadata study : fullStudies) {
                    study.rewriteUrlsToProxy(configuration.getBaseUrl());
                }
            }

            // Collect all instances from matching studies
            List<InstanceMetadata> allInstances = new ArrayList<>();
            String sopClassFilter = queryKeys.getString(Tag.SOPClassUID);

            for (StudyMetadata fullStudy : fullStudies) {
                if (fullStudy != null) {
                    for (SeriesMetadata series : fullStudy.series) {
                        for (InstanceMetadata instance : series.instances) {
                            // Apply SOP Class UID filter if specified
                            if (sopClassFilter == null || sopClassFilter.isEmpty() ||
                                sopClassFilter.equals("*") || sopClassFilter.equals(instance.sopClassUID)) {
                                allInstances.add(instance);
                            }
                        }
                    }
                }
            }

            LOG.info("Found {} matching instances across {} studies", allInstances.size(), studies.size());

            // Apply pagination
            int offset = QIDOUtils.parseOffset(allParams);
            int limit = QIDOUtils.parseLimit(allParams, configuration.getDefaultLimit(), configuration.getMaxLimit());
            allInstances = applyPagination(allInstances, offset, limit);

            // Parse includefield parameter
            Set<Integer> includeFields = QIDOUtils.parseIncludeFields(allParams);

            // Convert to DICOM JSON (Retrieve URLs are now in toAttributes())
            List<Map<String, Object>> response = allInstances.stream()
                    .map(instance -> DICOMJSONConverter.toJSON(instance.toAttributes(), includeFields))
                    .collect(Collectors.toList());

            return buildResponse(response, allInstances.size(), offset, limit);

        } catch (IOException e) {
            LOG.error("Error searching for all instances: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    /**
     * Fetch study metadata in parallel for multiple studies.
     * Downloads up to 10 MADO files concurrently.
     *
     * @param studies List of studies to fetch metadata for
     * @return List of full study metadata (preserving order)
     */
    private List<StudyMetadata> fetchStudiesInParallel(List<StudyMetadata> studies) {
        if (studies.isEmpty()) {
            return studies;
        }

        LOG.info("Fetching {} MADO files in parallel (max 10 concurrent downloads)", studies.size());

        // Create tasks for each study
        List<CompletableFuture<StudyMetadata>> futures = studies.stream()
                .map(study -> CompletableFuture.supplyAsync(() -> {
                    try {
                        LOG.debug("Fetching MADO for study: {}", study.studyInstanceUID);
                        return metadataService.getOrFetchStudyMetadata(study.studyInstanceUID);
                    } catch (IOException e) {
                        LOG.warn("Failed to fetch metadata for study {}: {}", study.studyInstanceUID, e.getMessage());
                        return null;
                    }
                }, madoDownloadExecutor))
                .collect(Collectors.toList());

        // Wait for all downloads to complete
        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

        try {
            // Wait up to 60 seconds for all downloads
            allOf.get(60, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            LOG.error("Timeout waiting for MADO downloads to complete");
        } catch (Exception e) {
            LOG.error("Error during parallel MADO downloads: {}", e.getMessage());
        }

        // Collect results (maintaining order)
        List<StudyMetadata> results = new ArrayList<>();
        for (CompletableFuture<StudyMetadata> future : futures) {
            try {
                StudyMetadata metadata = future.getNow(null);
                if (metadata != null) {
                    results.add(metadata);
                }
            } catch (Exception e) {
                LOG.warn("Error retrieving future result: {}", e.getMessage());
            }
        }

        LOG.info("Successfully fetched {} out of {} MADO files", results.size(), studies.size());
        return results;
    }

    /**
     * Apply pagination (offset and limit) to a result list.
     */
    private <T> List<T> applyPagination(List<T> list, int offset, int limit) {
        if (offset >= list.size()) {
            return Collections.emptyList();
        }

        int end = (limit <= 0) ? list.size() : Math.min(offset + limit, list.size());
        return list.subList(offset, end);
    }

    /**
     * Build the response with appropriate headers.
     */
    private ResponseEntity<List<Map<String, Object>>> buildResponse(
            List<Map<String, Object>> data, int totalSize, int offset, int limit) {

        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.ok();

        // Add warning header if results were limited
        if (limit > 0 && offset + limit < totalSize) {
            responseBuilder.header("Warning", "299 " + configuration.getBaseUrl() + " \"The number of results exceeded the limit\"");
        }

        return responseBuilder.body(data);
    }
}

