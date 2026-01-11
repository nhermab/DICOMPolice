package be.uzleuven.ihe.service.scp;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.net.*;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.PresentationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Executes C-MOVE operations by retrieving DICOM data via WADO-RS
 * and sending to the move destination via C-STORE.
 *
 * This executor uses WADO-RS URLs extracted from MADO manifests
 * (via MHDBackedMetadataService) rather than local file storage.
 */
@Component
public class MHDBackedCMoveExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(MHDBackedCMoveExecutor.class);

    private final MADOSCPConfiguration config;
    private final MHDBackedMetadataService metadataService;
    private final DicomCache dicomCache;

    // Thread pool for parallel downloads and C-STORE operations
    private final ExecutorService downloadExecutor;

    private final int maxParallelDownloads;
    private final int maxParallelStores;

    @Autowired
    public MHDBackedCMoveExecutor(MADOSCPConfiguration config,
                                   MHDBackedMetadataService metadataService,
                                   DicomCache dicomCache) {
        this.config = config;
        this.metadataService = metadataService;
        this.dicomCache = dicomCache;

        // Configure thread pools from config (with defaults)
        this.maxParallelDownloads = config.getMaxParallelDownloads();
        this.maxParallelStores = config.getMaxParallelStores();

        this.downloadExecutor = Executors.newFixedThreadPool(
            maxParallelDownloads,
            r -> {
                Thread t = new Thread(r, "cmove-download");
                t.setDaemon(true);
                return t;
            }
        );

        LOG.info("C-MOVE Executor initialized: parallel downloads={}, parallel stores={} (using same pool), cache enabled={}",
                maxParallelDownloads, maxParallelStores, dicomCache != null);
    }

    // ============================================================================
    // Data Classes
    // ============================================================================

    /**
     * Key for grouping instances by series and SOP Class for association reuse.
     */
    private static class AssociationKey {
        final String seriesInstanceUID;
        final String sopClassUID;

        AssociationKey(String seriesInstanceUID, String sopClassUID) {
            this.seriesInstanceUID = seriesInstanceUID;
            this.sopClassUID = sopClassUID;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AssociationKey)) return false;
            AssociationKey that = (AssociationKey) o;
            return Objects.equals(seriesInstanceUID, that.seriesInstanceUID) &&
                   Objects.equals(sopClassUID, that.sopClassUID);
        }

        @Override
        public int hashCode() {
            return Objects.hash(seriesInstanceUID, sopClassUID);
        }

        @Override
        public String toString() {
            return seriesInstanceUID + "/" + sopClassUID;
        }
    }

    /**
     * Represents a downloaded DICOM instance ready to be sent.
     */
    private static class DicomInstance {
        final Attributes attributes;
        final String sopClassUID;
        final String sopInstanceUID;
        final String seriesInstanceUID;
        final String transferSyntax;

        DicomInstance(Attributes attributes, String sopClassUID,
                      String sopInstanceUID, String seriesInstanceUID, String transferSyntax) {
            this.attributes = attributes;
            this.sopClassUID = sopClassUID;
            this.sopInstanceUID = sopInstanceUID;
            this.seriesInstanceUID = seriesInstanceUID;
            this.transferSyntax = transferSyntax;
        }
    }

    /**
     * Task for retrieving a DICOM instance from WADO-RS.
     */
    private static class RetrievalTask {
        final String wadoRsUrl;
        final int expectedInstanceCount;
        final String sopInstanceUID;
        final String sopClassUID;
        final String seriesInstanceUID;

        RetrievalTask(String url, int count, String sopInstanceUID,
                      String sopClassUID, String seriesInstanceUID) {
            this.wadoRsUrl = url;
            this.expectedInstanceCount = count;
            this.sopInstanceUID = sopInstanceUID;
            this.sopClassUID = sopClassUID;
            this.seriesInstanceUID = seriesInstanceUID;
        }
    }

    // ============================================================================
    // C-MOVE Execution
    // ============================================================================

    /**
     * Execute a C-MOVE operation by retrieving from WADO-RS and forwarding via C-STORE.
     * Groups instances by series/SOPClass and reuses associations for efficiency.
     * Downloads from WADO-RS in parallel but sends via C-STORE using shared associations.
     *
     * @param studyInstanceUID Study Instance UID
     * @param seriesInstanceUID Series Instance UID (optional, null = entire study)
     * @param sopInstanceUID SOP Instance UID (optional, null = entire series/study)
     * @param moveDestination AE Title of move destination
     * @param destHost Hostname of move destination
     * @param destPort Port of move destination
     * @param progressCallback Callback for progress updates
     * @return CMoveResult with status and counts
     */
    public CMoveResult executeCMove(String studyInstanceUID, String seriesInstanceUID,
                                     String sopInstanceUID, String moveDestination,
                                     String destHost, int destPort,
                                     CMoveProgressCallback progressCallback) {
        CMoveResult result = new CMoveResult();

        // Thread-safe counters for parallel processing
        AtomicInteger completed = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        AtomicInteger cached = new AtomicInteger(0);

        try {
            // Get study metadata from MHD (includes WADO-RS URLs from MADO)
            MHDBackedMetadataService.StudyMetadata studyMeta =
                    metadataService.getOrFetchStudyMetadata(studyInstanceUID);

            if (studyMeta == null) {
                result.success = false;
                result.errorMessage = "Study not found in MHD: " + studyInstanceUID;
                return result;
            }

            // Collect retrieval tasks grouped by series and SOP Class
            Map<AssociationKey, List<RetrievalTask>> tasksByAssociation = buildGroupedRetrievalTasks(
                    studyMeta, seriesInstanceUID, sopInstanceUID);

            result.totalInstances = tasksByAssociation.values().stream()
                    .mapToInt(List::size)
                    .sum();

            LOG.info("C-MOVE: {} instance(s) grouped into {} associations (series/SOPClass combinations) for {}",
                    result.totalInstances, tasksByAssociation.size(), moveDestination);

            if (progressCallback != null) {
                progressCallback.onProgress(result.totalInstances, 0, 0, 0);
            }

            // Process each association group
            for (Map.Entry<AssociationKey, List<RetrievalTask>> entry : tasksByAssociation.entrySet()) {
                AssociationKey assocKey = entry.getKey();
                List<RetrievalTask> tasks = entry.getValue();

                LOG.info("C-MOVE: Processing association for series={}, sopClass={}, instances={}",
                        assocKey.seriesInstanceUID, assocKey.sopClassUID, tasks.size());

                try {
                    processAssociationGroup(assocKey, tasks, moveDestination, destHost, destPort,
                            result, completed, failed, cached, progressCallback);
                } catch (Exception e) {
                    LOG.error("C-MOVE: Failed to process association group {}: {}", assocKey, e.getMessage());
                    failed.addAndGet(tasks.size());
                    result.addWarning("Failed association group " + assocKey + ": " + e.getMessage());
                }
            }

            result.completedInstances = completed.get();
            result.failedInstances = failed.get();
            result.success = result.failedInstances == 0;

            if (cached.get() > 0) {
                LOG.info("C-MOVE: {} instances served from cache", cached.get());
            }

        } catch (Exception e) {
            LOG.error("C-MOVE failed: {}", e.getMessage(), e);
            result.success = false;
            result.errorMessage = e.getMessage();
        }

        return result;
    }

    /**
     * Build retrieval tasks grouped by series and SOP Class for association reuse.
     */
    private Map<AssociationKey, List<RetrievalTask>> buildGroupedRetrievalTasks(
            MHDBackedMetadataService.StudyMetadata study,
            String seriesFilter, String instanceFilter) {
        Map<AssociationKey, List<RetrievalTask>> tasksByAssociation = new LinkedHashMap<>();

        for (MHDBackedMetadataService.SeriesMetadata series : study.series) {
            // Apply series filter
            if (seriesFilter != null && !seriesFilter.isEmpty() &&
                    !seriesFilter.equals("*") && !series.seriesInstanceUID.equals(seriesFilter)) {
                continue;
            }

            // Check if specific instance requested
            if (instanceFilter != null && !instanceFilter.isEmpty() && !instanceFilter.equals("*")) {
                // Find specific instance
                for (MHDBackedMetadataService.InstanceMetadata inst : series.instances) {
                    if (inst.sopInstanceUID.equals(instanceFilter)) {
                        String url = buildInstanceUrl(study.studyInstanceUID, series, inst);
                        RetrievalTask task = new RetrievalTask(url, 1, inst.sopInstanceUID,
                                inst.sopClassUID, series.seriesInstanceUID);

                        AssociationKey key = new AssociationKey(series.seriesInstanceUID, inst.sopClassUID);
                        tasksByAssociation.computeIfAbsent(key, k -> new ArrayList<>()).add(task);
                        break;
                    }
                }
            } else {
                // Retrieve all instances in series, grouped by SOP Class
                for (MHDBackedMetadataService.InstanceMetadata inst : series.instances) {
                    String url = buildInstanceUrl(study.studyInstanceUID, series, inst);
                    RetrievalTask task = new RetrievalTask(url, 1, inst.sopInstanceUID,
                            inst.sopClassUID, series.seriesInstanceUID);

                    AssociationKey key = new AssociationKey(series.seriesInstanceUID, inst.sopClassUID);
                    tasksByAssociation.computeIfAbsent(key, k -> new ArrayList<>()).add(task);
                }
            }
        }

        return tasksByAssociation;
    }

    /**
     * Process all instances for one association group (series/SOPClass combination).
     * STREAMING VERSION: Downloads and C-STOREs happen in parallel pipeline.
     * Uses a bounded blocking queue to prevent memory bloat.
     */
    private void processAssociationGroup(AssociationKey assocKey, List<RetrievalTask> tasks,
                                         String moveDestination, String destHost, int destPort,
                                         CMoveResult result, AtomicInteger completed,
                                         AtomicInteger failed, AtomicInteger cached,
                                         CMoveProgressCallback progressCallback) throws Exception {

        // Bounded queue prevents loading all instances into memory
        // Size = 2x parallel stores to allow prefetching without bloat
        int queueSize = maxParallelStores * 2;
        BlockingQueue<DicomInstance> instanceQueue = new LinkedBlockingQueue<>(queueSize);

        // Poison pill to signal download completion
        DicomInstance POISON_PILL = new DicomInstance(null, null, null, null, null);

        // Start download producer threads
        ExecutorService downloadPool = Executors.newFixedThreadPool(
                Math.min(maxParallelDownloads, tasks.size()));

        AtomicInteger downloadedCount = new AtomicInteger(0);

        for (RetrievalTask task : tasks) {
            downloadPool.submit(() -> {
                try {
                    DicomInstance instance = downloadInstance(task, cached);
                    if (instance != null) {
                        // Block if queue is full - backpressure!
                        instanceQueue.put(instance);
                        downloadedCount.incrementAndGet();
                        LOG.debug("Downloaded {}/{} for series {}",
                                downloadedCount.get(), tasks.size(), assocKey.seriesInstanceUID);
                    } else {
                        failed.incrementAndGet();
                    }
                } catch (Exception e) {
                    LOG.error("Download failed: {}", e.getMessage());
                    failed.incrementAndGet();
                }
            });
        }

        // Shutdown download pool and send poison pill when done
        downloadPool.shutdown();
        new Thread(() -> {
            try {
                downloadPool.awaitTermination(5, TimeUnit.MINUTES);
                instanceQueue.put(POISON_PILL);
                LOG.info("All downloads complete for series {}", assocKey.seriesInstanceUID);
            } catch (InterruptedException e) {
                LOG.error("Download pool interrupted", e);
            }
        }, "download-sentinel").start();

        // Wait for first instance to determine transfer syntax for negotiation
        LOG.debug("Waiting for first instance to determine transfer syntax...");
        DicomInstance firstInstance = instanceQueue.poll(60, TimeUnit.SECONDS);

        if (firstInstance == null) {
            LOG.error("Timeout waiting for first instance in series {}", assocKey.seriesInstanceUID);
            failed.addAndGet(tasks.size());
            return;
        }

        if (firstInstance == POISON_PILL) {
            LOG.warn("No instances downloaded for series {}", assocKey.seriesInstanceUID);
            return;
        }

        // Get the ORIGINAL transfer syntax from the first instance
        String originalTransferSyntax = firstInstance.transferSyntax;
        LOG.info("C-MOVE: Detected original transfer syntax: {} for series {}",
                originalTransferSyntax, assocKey.seriesInstanceUID);

        // Create association for this series/SOPClass
        Association as = null;
        Device device = null;
        ExecutorService executorService = null;
        ScheduledExecutorService scheduledExecutorService = null;

        try {
            // Create device and association
            device = new Device("dicompolice-cmove-" + System.currentTimeMillis());
            Connection conn = new Connection();
            device.addConnection(conn);

            ApplicationEntity ae = new ApplicationEntity(config.getAeTitle());
            device.addApplicationEntity(ae);
            ae.addConnection(conn);

            executorService = Executors.newSingleThreadExecutor();
            scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
            device.setExecutor(executorService);
            device.setScheduledExecutor(scheduledExecutorService);

            Connection remote = new Connection();
            remote.setHostname(destHost);
            remote.setPort(destPort);

            AAssociateRQ rq = new AAssociateRQ();
            rq.setCallingAET(config.getAeTitle());
            rq.setCalledAET(moveDestination);

            // CRITICAL: Negotiate ONLY the original transfer syntax
            // This eliminates ALL transcoding overhead
            LOG.info("C-MOVE: Negotiating ONLY original transfer syntax: {}", originalTransferSyntax);
            rq.addPresentationContext(new PresentationContext(1, assocKey.sopClassUID,
                    originalTransferSyntax));

            as = ae.connect(remote, rq);

            // Verify the transfer syntax was accepted
            // We proposed only ONE presentation context (ID=1) with the exact TS
            // If the association was accepted, check if it's ready for data transfer
            if (as != null && !as.isReadyForDataTransfer()) {
                // Association was rejected or not ready
                LOG.error("C-MOVE: Association not ready for data transfer!");
                LOG.error("C-MOVE: Destination likely rejected transfer syntax {}",
                        originalTransferSyntax);
                failed.addAndGet(tasks.size());
                result.addWarning("Destination rejected TS " + originalTransferSyntax + " for series " +
                        assocKey.seriesInstanceUID);
                return;
            }

            // If we got here, association is ready - our single PC was accepted

            LOG.info("C-MOVE: SUCCESS! Destination accepted {}. Streaming {} instances with ZERO transcoding.",
                    originalTransferSyntax, tasks.size());
            LOG.info("C-MOVE: Streaming {} instances (series={}, sopClass={}, TS={})",
                    tasks.size(), assocKey.seriesInstanceUID, assocKey.sopClassUID, originalTransferSyntax);

            // Consumer: Send instances as they arrive from download queue
            // Use parallel C-STORE operations (up to maxParallelStores)
            ExecutorService storePool = Executors.newFixedThreadPool(maxParallelStores);
            List<Future<?>> storeFutures = new ArrayList<>();

            int sent = 0;

            // First, send the instance we already retrieved (used for TS detection)
            sent++;
            final Association association = as;
            Future<?> firstStoreFuture = storePool.submit(() -> {
                try {
                    sendInstanceViaAssociation(association, firstInstance);
                    completed.incrementAndGet();

                    if (progressCallback != null) {
                        int remaining = result.totalInstances - completed.get() - failed.get();
                        progressCallback.onProgress(remaining, completed.get(), failed.get(), 0);
                    }
                } catch (Exception e) {
                    LOG.error("C-MOVE: Failed to send instance {}: {}", firstInstance.sopInstanceUID, e.getMessage());
                    failed.incrementAndGet();
                    result.addWarning("Failed to send " + firstInstance.sopInstanceUID + ": " + e.getMessage());

                    if (progressCallback != null) {
                        int remaining = result.totalInstances - completed.get() - failed.get();
                        progressCallback.onProgress(remaining, completed.get(), failed.get(), 0);
                    }
                }
            });
            storeFutures.add(firstStoreFuture);

            // Now consume remaining instances from queue
            while (true) {
                DicomInstance instance = instanceQueue.poll(30, TimeUnit.SECONDS);

                if (instance == null) {
                    LOG.warn("C-MOVE: Timeout waiting for instance in queue");
                    break;
                }

                if (instance == POISON_PILL) {
                    LOG.debug("C-MOVE: Received completion signal");
                    break;
                }

                sent++;

                // Submit C-STORE in parallel (non-blocking)
                Future<?> storeFuture = storePool.submit(() -> {
                    try {
                        sendInstanceViaAssociation(association, instance);
                        completed.incrementAndGet();

                        if (progressCallback != null) {
                            int remaining = result.totalInstances - completed.get() - failed.get();
                            progressCallback.onProgress(remaining, completed.get(), failed.get(), 0);
                        }
                    } catch (Exception e) {
                        LOG.error("C-MOVE: Failed to send instance {}: {}", instance.sopInstanceUID, e.getMessage());
                        failed.incrementAndGet();
                        result.addWarning("Failed to send " + instance.sopInstanceUID + ": " + e.getMessage());

                        if (progressCallback != null) {
                            int remaining = result.totalInstances - completed.get() - failed.get();
                            progressCallback.onProgress(remaining, completed.get(), failed.get(), 0);
                        }
                    }
                });

                storeFutures.add(storeFuture);
            }

            LOG.info("C-MOVE: Sent {} instances, waiting for C-STORE confirmations", sent);

            // Wait for all C-STORE operations to complete
            storePool.shutdown();
            storePool.awaitTermination(2, TimeUnit.MINUTES);

            // Wait for all responses
            as.waitForOutstandingRSP();

        } finally {
            if (as != null) {
                try {
                    as.release();
                    LOG.debug("C-MOVE: Released association for {}", assocKey);
                } catch (Exception e) {
                    LOG.warn("C-MOVE: Error releasing association: {}", e.getMessage());
                }
            }
            if (executorService != null) {
                executorService.shutdown();
            }
            if (scheduledExecutorService != null) {
                scheduledExecutorService.shutdown();
            }
        }
    }


    /**
     * Download a DICOM instance from WADO-RS with caching support.
     */
    private DicomInstance downloadInstance(RetrievalTask task, AtomicInteger cached) {
        try {
            // Try to get from cache first
            byte[] dicomData = null;
            if (dicomCache != null && task.sopInstanceUID != null) {
                dicomData = dicomCache.get(task.sopInstanceUID);
                if (dicomData != null) {
                    cached.incrementAndGet();
                    LOG.debug("C-MOVE: Retrieved {} from cache", task.sopInstanceUID);
                }
            }

            // Download from WADO-RS if not cached
            if (dicomData == null) {
                LOG.debug("C-MOVE: Downloading from {}", task.wadoRsUrl);
                List<byte[]> dicomFiles = downloadFromWadoRs(task.wadoRsUrl);

                if (dicomFiles.isEmpty()) {
                    LOG.warn("C-MOVE: No DICOM data received from WADO-RS for {}", task.sopInstanceUID);
                    return null;
                }

                dicomData = dicomFiles.get(0);

                // Cache the downloaded data
                if (dicomCache != null && task.sopInstanceUID != null) {
                    dicomCache.put(task.sopInstanceUID, dicomData);
                }
            }

            // Parse DICOM data
            Attributes fmi;
            Attributes attrs;
            try (DicomInputStream dis = new DicomInputStream(new ByteArrayInputStream(dicomData))) {
                fmi = dis.readFileMetaInformation();
                attrs = dis.readDataset();
            }

            String transferSyntax = fmi != null ? fmi.getString(Tag.TransferSyntaxUID) :
                    UID.ImplicitVRLittleEndian;

            return new DicomInstance(attrs, task.sopClassUID, task.sopInstanceUID,
                    task.seriesInstanceUID, transferSyntax);

        } catch (Exception e) {
            LOG.error("C-MOVE: Failed to download instance {}: {}", task.sopInstanceUID, e.getMessage());
            return null;
        }
    }

    /**
     * Send a DICOM instance using an existing association.
     */
    private void sendInstanceViaAssociation(Association as, DicomInstance instance) throws Exception {
        final int[] responseStatus = {-1};

        DataWriter dataWriter = (out, tsuid) -> {
            try (DicomOutputStream dos = new DicomOutputStream(out, tsuid)) {
                dos.writeDataset(null, instance.attributes);
            }
        };

        as.cstore(instance.sopClassUID, instance.sopInstanceUID, 0, dataWriter,
                instance.transferSyntax,
                new DimseRSPHandler(as.nextMessageID()) {
                    @Override
                    public void onDimseRSP(Association as, Attributes cmd, Attributes data) {
                        responseStatus[0] = cmd.getInt(Tag.Status, -1);
                        if (responseStatus[0] != Status.Success) {
                            LOG.warn("C-STORE response status for {}: 0x{}",
                                    instance.sopInstanceUID, Integer.toHexString(responseStatus[0]));
                        }
                    }
                });

        // Note: We don't wait here - waitForOutstandingRSP() is called after all instances are sent
    }


    /**
     * Build WADO-RS URL for a specific instance.
     */
    private String buildInstanceUrl(String studyUID,
                                     MHDBackedMetadataService.SeriesMetadata series,
                                     MHDBackedMetadataService.InstanceMetadata instance) {
        // Use instance-level URL if present
        if (instance.retrieveURL != null && !instance.retrieveURL.isEmpty()) {
            return instance.retrieveURL;
        }

        // Use series URL as base and append instance
        String baseUrl = series.retrieveURL;
        if (baseUrl != null && !baseUrl.isEmpty()) {
            return baseUrl + "/instances/" + instance.sopInstanceUID;
        }

        // Fall back to configured base URL
        return config.getWadoRsBaseUrl() + "/" + studyUID +
                "/series/" + series.seriesInstanceUID +
                "/instances/" + instance.sopInstanceUID;
    }


    private int countTotalInstances(List<RetrievalTask> tasks) {
        return tasks.stream().mapToInt(t -> t.expectedInstanceCount).sum();
    }

    /**
     * Download DICOM files from WADO-RS endpoint.
     */
    private List<byte[]> downloadFromWadoRs(String wadoUrl) throws IOException {
        List<byte[]> dicomFiles = new ArrayList<>();

        URL url = new URL(wadoUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "multipart/related; type=application/dicom");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(120000);

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("WADO-RS request failed with status " + responseCode + " for URL: " + wadoUrl);
        }

        String contentType = conn.getContentType();
        LOG.debug("WADO-RS Content-Type: {}", contentType);

        try (InputStream is = conn.getInputStream()) {
            if (contentType != null && contentType.contains("multipart")) {
                dicomFiles = parseMultipartResponse(is, contentType);
            } else if (contentType != null && contentType.contains("application/zip")) {
                dicomFiles = parseZipResponse(is);
            } else {
                // Single DICOM file
                byte[] data = readAllBytes(is);
                if (data.length > 0 && isDicomData(data)) {
                    dicomFiles.add(data);
                }
            }
        }

        LOG.debug("WADO-RS returned {} DICOM files", dicomFiles.size());
        return dicomFiles;
    }

    /**
     * Parse multipart DICOM response from WADO-RS.
     */
    private List<byte[]> parseMultipartResponse(InputStream is, String contentType) throws IOException {
        List<byte[]> parts = new ArrayList<>();

        String boundary = extractBoundary(contentType);
        if (boundary == null) {
            LOG.warn("Could not extract boundary from Content-Type: {}", contentType);
            byte[] data = readAllBytes(is);
            if (data.length > 0 && isDicomData(data)) {
                parts.add(data);
            }
            return parts;
        }

        byte[] boundaryBytes = ("--" + boundary).getBytes();
        byte[] allData = readAllBytes(is);

        int start = 0;
        while (start < allData.length) {
            int boundaryStart = indexOf(allData, boundaryBytes, start);
            if (boundaryStart < 0) break;

            int headersEnd = indexOf(allData, "\r\n\r\n".getBytes(), boundaryStart);
            if (headersEnd < 0) break;

            int dataStart = headersEnd + 4;

            int nextBoundary = indexOf(allData, boundaryBytes, dataStart);
            int dataEnd = (nextBoundary > 0) ? nextBoundary - 2 : allData.length;

            if (dataEnd > dataStart) {
                byte[] partData = new byte[dataEnd - dataStart];
                System.arraycopy(allData, dataStart, partData, 0, partData.length);

                if (isDicomData(partData)) {
                    parts.add(partData);
                }
            }

            start = nextBoundary > 0 ? nextBoundary : allData.length;
        }

        return parts;
    }

    /**
     * Parse ZIP response from WADO-RS.
     */
    private List<byte[]> parseZipResponse(InputStream is) throws IOException {
        List<byte[]> files = new ArrayList<>();

        try (ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        baos.write(buffer, 0, len);
                    }
                    byte[] data = baos.toByteArray();
                    if (isDicomData(data)) {
                        files.add(data);
                    }
                }
                zis.closeEntry();
            }
        }

        return files;
    }

    /**
     * Send a DICOM file to the move destination using C-STORE.
     */
    private void sendToDestination(byte[] dicomData, String destAeTitle,
                                    String destHost, int destPort) throws Exception {
        Attributes attrs;
        Attributes fmi;
        try (DicomInputStream dis = new DicomInputStream(new ByteArrayInputStream(dicomData))) {
            fmi = dis.readFileMetaInformation();
            attrs = dis.readDataset();
        }

        String sopClassUID = attrs.getString(Tag.SOPClassUID);
        String sopInstanceUID = attrs.getString(Tag.SOPInstanceUID);
        String transferSyntax = fmi != null ? fmi.getString(Tag.TransferSyntaxUID) : UID.ImplicitVRLittleEndian;

        if (sopClassUID == null || sopInstanceUID == null) {
            throw new IOException("Missing SOP Class UID or SOP Instance UID in DICOM data");
        }

        Device device = new Device("dicompolice-cmove");
        Connection conn = new Connection();
        device.addConnection(conn);

        ApplicationEntity ae = new ApplicationEntity(config.getAeTitle());
        device.addApplicationEntity(ae);
        ae.addConnection(conn);

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        device.setExecutor(executorService);
        device.setScheduledExecutor(scheduledExecutorService);

        try {
            Connection remote = new Connection();
            remote.setHostname(destHost);
            remote.setPort(destPort);

            AAssociateRQ rq = new AAssociateRQ();
            rq.setCallingAET(config.getAeTitle());
            rq.setCalledAET(destAeTitle);

            rq.addPresentationContext(new PresentationContext(1, sopClassUID, transferSyntax));
            if (!transferSyntax.equals(UID.ExplicitVRLittleEndian)) {
                rq.addPresentationContext(new PresentationContext(3, sopClassUID, UID.ExplicitVRLittleEndian));
            }
            if (!transferSyntax.equals(UID.ImplicitVRLittleEndian)) {
                rq.addPresentationContext(new PresentationContext(5, sopClassUID, UID.ImplicitVRLittleEndian));
            }

            Association as = ae.connect(remote, rq);

            try {
                final int[] responseStatus = {-1};

                final Attributes datasetToSend = attrs;
                DataWriter dataWriter = (out, tsuid) -> {
                    try (DicomOutputStream dos = new DicomOutputStream(out, tsuid)) {
                        dos.writeDataset(null, datasetToSend);
                    }
                };

                as.cstore(sopClassUID, sopInstanceUID, 0, dataWriter, transferSyntax,
                        new DimseRSPHandler(as.nextMessageID()) {
                            @Override
                            public void onDimseRSP(Association as, Attributes cmd, Attributes data) {
                                responseStatus[0] = cmd.getInt(Tag.Status, -1);
                                if (responseStatus[0] != Status.Success) {
                                    LOG.warn("C-STORE response status: 0x{}", Integer.toHexString(responseStatus[0]));
                                }
                            }
                        });

                as.waitForOutstandingRSP();

                if (responseStatus[0] != Status.Success && responseStatus[0] != -1) {
                    throw new IOException("C-STORE failed with status: 0x" + Integer.toHexString(responseStatus[0]));
                }

            } finally {
                as.release();
            }

        } finally {
            executorService.shutdown();
            scheduledExecutorService.shutdown();
        }
    }

    // Utility methods

    private String extractBoundary(String contentType) {
        if (contentType == null) return null;
        String[] parts = contentType.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.startsWith("boundary=")) {
                String boundary = trimmed.substring(9);
                if (boundary.startsWith("\"") && boundary.endsWith("\"")) {
                    boundary = boundary.substring(1, boundary.length() - 1);
                }
                return boundary;
            }
        }
        return null;
    }

    private int indexOf(byte[] source, byte[] target, int start) {
        outer:
        for (int i = start; i <= source.length - target.length; i++) {
            for (int j = 0; j < target.length; j++) {
                if (source[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int len;
        while ((len = is.read(buffer)) > 0) {
            baos.write(buffer, 0, len);
        }
        return baos.toByteArray();
    }

    private boolean isDicomData(byte[] data) {
        if (data == null || data.length < 132) {
            return false;
        }
        return data[128] == 'D' && data[129] == 'I' && data[130] == 'C' && data[131] == 'M';
    }

    // ============================================================================
    // Callback and Result Classes
    // ============================================================================

    /**
     * Callback interface for C-MOVE progress updates.
     */
    public interface CMoveProgressCallback {
        void onProgress(int remaining, int completed, int failed, int warning);
    }

    /**
     * Result of a C-MOVE operation.
     */
    public static class CMoveResult {
        public boolean success;
        public int totalInstances;
        public int completedInstances;
        public int failedInstances;
        public String errorMessage;
        public List<String> warnings = new ArrayList<>();

        public void addWarning(String warning) {
            warnings.add(warning);
        }
    }
}

