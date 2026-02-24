package be.uzleuven.ihe.service.scp;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.*;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicCEchoSCP;
import org.dcm4che3.net.service.BasicCFindSCP;
import org.dcm4che3.net.service.BasicCMoveSCP;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.DicomServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * DICOM SCP (Service Class Provider) for MHD-backed Query/Retrieve.
 *
 * This SCP provides DIMSE services (C-FIND, C-MOVE) that are backed by:
 * - MHD (ITI-67/ITI-68) for metadata queries and MADO retrieval
 * - WADO-RS for actual DICOM data retrieval during C-MOVE
 *
 * Data flow:
 * 1. C-FIND queries the MHD service (which does C-FIND to backend PACS)
 * 2. MHD returns DocumentReferences; SCP retrieves MADO for detailed metadata
 * 3. C-MOVE uses WADO-RS URLs from MADO to retrieve DICOM and forward via C-STORE
 *
 * This allows legacy DICOM clients to access data through standard Q/R protocols
 * while the backend uses modern FHIR/DICOMweb interfaces.
 */
@Service
public class MADOSCP {

    private static final Logger LOG = LoggerFactory.getLogger(MADOSCP.class);

    private final MADOSCPConfiguration config;
    private final MHDBackedMetadataService metadataService;
    private final MHDBackedCMoveExecutor moveExecutor;
    private final AEDirectory aeDirectory;

    private Device device;
    private ApplicationEntity ae;
    private Connection conn;
    private ExecutorService executorService;
    private ScheduledExecutorService scheduledExecutorService;
    private boolean running = false;

    @Autowired
    public MADOSCP(MADOSCPConfiguration config,
                   MHDBackedMetadataService metadataService,
                   MHDBackedCMoveExecutor moveExecutor,
                   AEDirectory aeDirectory) {
        this.config = config;
        this.metadataService = metadataService;
        this.moveExecutor = moveExecutor;
        this.aeDirectory = aeDirectory;
    }

    @PostConstruct
    public void init() {
        if (config.isAutoStart()) {
            try {
                start();
            } catch (Exception e) {
                LOG.error("Failed to auto-start MADO SCP: {}", e.getMessage(), e);
            }
        }
    }

    @PreDestroy
    public void destroy() {
        stop();
    }

    /**
     * Start the SCP server.
     */
    public synchronized void start() throws Exception {
        if (running) {
            LOG.warn("MADO SCP is already running");
            return;
        }

        LOG.info("Starting MHD-backed MADO SCP on port {} with AE Title: {}",
                config.getPort(), config.getAeTitle());

        // Create device
        device = new Device("MADOSCP");

        // Create connection
        conn = new Connection();
        conn.setHostname("0.0.0.0"); // Bind to all interfaces
        conn.setPort(config.getPort());
        conn.setReceivePDULength(config.getMaxPduLength());
        conn.setSendPDULength(config.getMaxPduLength());
        device.addConnection(conn);

        // Create Application Entity
        ae = new ApplicationEntity(config.getAeTitle());
        ae.setAssociationAcceptor(true);
        ae.addConnection(conn);
        device.addApplicationEntity(ae);

        // Configure transfer syntaxes
        String[] transferSyntaxes = {
            UID.ImplicitVRLittleEndian,
            UID.ExplicitVRLittleEndian,
            UID.ExplicitVRBigEndian
        };

        // Add presentation contexts for C-FIND
        ae.addTransferCapability(new TransferCapability(null,
            UID.PatientRootQueryRetrieveInformationModelFind,
            TransferCapability.Role.SCP, transferSyntaxes));
        ae.addTransferCapability(new TransferCapability(null,
            UID.StudyRootQueryRetrieveInformationModelFind,
            TransferCapability.Role.SCP, transferSyntaxes));

        // Add presentation contexts for C-MOVE
        ae.addTransferCapability(new TransferCapability(null,
            UID.PatientRootQueryRetrieveInformationModelMove,
            TransferCapability.Role.SCP, transferSyntaxes));
        ae.addTransferCapability(new TransferCapability(null,
            UID.StudyRootQueryRetrieveInformationModelMove,
            TransferCapability.Role.SCP, transferSyntaxes));

        // Add C-ECHO (Verification)
        ae.addTransferCapability(new TransferCapability(null,
            UID.Verification, TransferCapability.Role.SCP, transferSyntaxes));

        // Create service registry
        DicomServiceRegistry serviceRegistry = new DicomServiceRegistry();

        // Register C-ECHO handler
        serviceRegistry.addDicomService(new BasicCEchoSCP());

        // Register MHD-backed C-FIND handler
        MHDBackedCFindHandler cfindHandler = new MHDBackedCFindHandler(metadataService);
        serviceRegistry.addDicomService(cfindHandler);

        // Register MHD-backed C-MOVE handler
        MHDBackedCMoveHandler cmoveHandler = new MHDBackedCMoveHandler(
                config, metadataService, moveExecutor, aeDirectory);
        serviceRegistry.addDicomService(cmoveHandler);

        ae.setDimseRQHandler(serviceRegistry);

        // Create executor services
        executorService = Executors.newCachedThreadPool();
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        device.setExecutor(executorService);
        device.setScheduledExecutor(scheduledExecutorService);

        // Bind and start
        try {
            device.bindConnections();
        } catch (IOException e) {
            // Clean up on bind failure
            if (executorService != null) {
                executorService.shutdownNow();
            }
            if (scheduledExecutorService != null) {
                scheduledExecutorService.shutdownNow();
            }

            if (e.getMessage() != null && e.getMessage().contains("Address already in use")) {
                throw new Exception("Port " + config.getPort() + " is already in use. " +
                        "Another DICOM SCP may be running, or change the port in configuration (mado.scp.port).", e);
            }
            throw e;
        }

        running = true;

        LOG.info("MHD-backed MADO SCP started successfully - listening on port {}", config.getPort());
        LOG.info("MHD endpoint: {}", config.getMhdFhirBaseUrl());
        LOG.info("WADO-RS endpoint: {}", config.getWadoRsBaseUrl());
    }

    /**
     * Stop the SCP server.
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }

        LOG.info("Stopping MADO SCP...");

        try {
            if (device != null) {
                device.unbindConnections();
            }
        } catch (Exception e) {
            LOG.warn("Error unbinding connections: {}", e.getMessage());
        }

        if (executorService != null) {
            executorService.shutdownNow();
        }
        if (scheduledExecutorService != null) {
            scheduledExecutorService.shutdownNow();
        }

        running = false;
        LOG.info("MADO SCP stopped");
    }

    /**
     * Check if the SCP is running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Clear metadata cache.
     */
    public void clearCache() {
        metadataService.clearCache();
        LOG.info("Metadata cache cleared");
    }

    /**
     * Get cache statistics.
     */
    public int getCacheSize() {
        return metadataService.getCacheSize();
    }

    // ============================================================================
    // MHD-Backed C-FIND SCP Handler
    // ============================================================================

    /**
     * C-FIND handler that queries MHD for metadata.
     */
    private static class MHDBackedCFindHandler extends BasicCFindSCP {

        private final MHDBackedMetadataService metadataService;

        MHDBackedCFindHandler(MHDBackedMetadataService metadataService) {
            super(UID.PatientRootQueryRetrieveInformationModelFind,
                  UID.StudyRootQueryRetrieveInformationModelFind);
            this.metadataService = metadataService;
        }

        @Override
        public void onDimseRQ(Association as, org.dcm4che3.net.pdu.PresentationContext pc,
                             Dimse dimse, Attributes cmd, Attributes keys) throws IOException {
            if (dimse != Dimse.C_FIND_RQ) {
                throw new DicomServiceException(Status.UnrecognizedOperation);
            }

            LOG.info("C-FIND Request from {} (MHD-backed)", as.getCallingAET());
            LOG.info("C-FIND Query keys: {}", keys);

            String queryLevel = keys.getString(Tag.QueryRetrieveLevel);
            if (queryLevel == null) {
                queryLevel = "STUDY";
            }

            LOG.info("C-FIND Query level: {}", queryLevel);

            try {
                List<?> results;
                switch (queryLevel.toUpperCase()) {
                    case "PATIENT":
                    case "STUDY":
                        results = metadataService.findStudies(keys);
                        break;
                    case "SERIES":
                        results = metadataService.findSeries(keys);
                        break;
                    case "IMAGE":
                    case "INSTANCE":
                        results = metadataService.findInstances(keys);
                        break;
                    default:
                        LOG.warn("Unknown query level: {}", queryLevel);
                        throw new DicomServiceException(Status.UnrecognizedOperation);
                }

                LOG.info("C-FIND: Found {} matches at {} level via MHD", results.size(), queryLevel);

                // Send each result as a pending response
                int responseNum = 0;
                for (Object result : results) {
                    responseNum++;
                    Attributes responseAttrs;
                    if (result instanceof MHDBackedMetadataService.StudyMetadata) {
                        responseAttrs = ((MHDBackedMetadataService.StudyMetadata) result).toAttributes();
                    } else if (result instanceof MHDBackedMetadataService.SeriesMetadata) {
                        responseAttrs = ((MHDBackedMetadataService.SeriesMetadata) result).toAttributes();
                    } else if (result instanceof MHDBackedMetadataService.InstanceMetadata) {
                        responseAttrs = ((MHDBackedMetadataService.InstanceMetadata) result).toAttributes();
                    } else {
                        continue;
                    }

                    responseAttrs.setString(Tag.QueryRetrieveLevel, VR.CS, queryLevel);

                    // Filter to requested keys
                    Attributes filtered = new Attributes();
                    filtered.setString(Tag.QueryRetrieveLevel, VR.CS, queryLevel);
                    for (int tag : keys.tags()) {
                        if (responseAttrs.contains(tag)) {
                            filtered.addSelected(responseAttrs, tag);
                        }
                    }

                    // INFO: Log summary of C-FIND response
                    String studyUID = filtered.getString(Tag.StudyInstanceUID, "N/A");
                    String seriesUID = filtered.getString(Tag.SeriesInstanceUID, "");
                    String sopUID = filtered.getString(Tag.SOPInstanceUID, "");
                    String patientID = filtered.getString(Tag.PatientID, "");
                    String modality = filtered.getString(Tag.Modality, "");

                    StringBuilder summary = new StringBuilder();
                    summary.append("C-FIND Response #").append(responseNum).append(" (").append(queryLevel).append("): ");
                    summary.append("StudyUID=").append(studyUID);
                    if (!seriesUID.isEmpty()) summary.append(", SeriesUID=").append(seriesUID);
                    if (!sopUID.isEmpty()) summary.append(", SOPUID=").append(sopUID);
                    if (!patientID.isEmpty()) summary.append(", PatientID=").append(patientID);
                    if (!modality.isEmpty()) summary.append(", Modality=").append(modality);
                    LOG.info(summary.toString());

                    // DEBUG: Log detailed tag information
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("C-FIND Response #{} detailed tags ({} total):", responseNum, filtered.size());
                        for (int tag : filtered.tags()) {
                            String tagName = ElementDictionary.keywordOf(tag, null);
                            String tagHex = String.format("(%04X,%04X)", tag >>> 16, tag & 0xFFFF);
                            VR vr = filtered.getVR(tag);
                            Object value = filtered.getValue(tag);
                            LOG.debug("  {} {} {}: {}", tagHex, tagName != null ? tagName : "Unknown", vr, value);
                        }
                    }

                    as.writeDimseRSP(pc, Commands.mkCFindRSP(cmd, Status.Pending), filtered);
                }

                // Send final success response
                as.writeDimseRSP(pc, Commands.mkCFindRSP(cmd, Status.Success));

            } catch (DicomServiceException e) {
                throw e;
            } catch (Exception e) {
                LOG.error("C-FIND failed: {}", e.getMessage(), e);
                throw new DicomServiceException(Status.ProcessingFailure, e);
            }
        }
    }

    // ============================================================================
    // MHD-Backed C-MOVE SCP Handler
    // ============================================================================

    /**
     * C-MOVE handler that retrieves via WADO-RS (using URLs from MADO) and forwards to destination.
     */
    private static class MHDBackedCMoveHandler extends BasicCMoveSCP {

        private final MHDBackedMetadataService metadataService;
        private final MHDBackedCMoveExecutor moveExecutor;
        private final AEDirectory aeDirectory;

        MHDBackedCMoveHandler(MADOSCPConfiguration config,
                              MHDBackedMetadataService metadataService,
                              MHDBackedCMoveExecutor moveExecutor,
                              AEDirectory aeDirectory) {
            super(UID.PatientRootQueryRetrieveInformationModelMove,
                  UID.StudyRootQueryRetrieveInformationModelMove);
            this.metadataService = metadataService;
            this.moveExecutor = moveExecutor;
            this.aeDirectory = aeDirectory;
        }

        @Override
        public void onDimseRQ(Association as, org.dcm4che3.net.pdu.PresentationContext pc,
                             Dimse dimse, Attributes cmd, Attributes keys) throws IOException {
            if (dimse != Dimse.C_MOVE_RQ) {
                throw new DicomServiceException(Status.UnrecognizedOperation);
            }

            String moveDestination = cmd.getString(Tag.MoveDestination);
            LOG.info("C-MOVE Request from {} to destination {} (MHD-backed)",
                    as.getCallingAET(), moveDestination);
            LOG.debug("Move keys: {}", keys);

            if (moveDestination == null || moveDestination.isEmpty()) {
                LOG.error("No move destination specified");
                throw new DicomServiceException(Status.InvalidArgumentValue, "No move destination");
            }

            String studyInstanceUID = keys.getString(Tag.StudyInstanceUID);
            String seriesInstanceUID = keys.getString(Tag.SeriesInstanceUID);
            String sopInstanceUID = keys.getString(Tag.SOPInstanceUID);

            if (studyInstanceUID == null || studyInstanceUID.isEmpty()) {
                LOG.error("No Study Instance UID specified");
                throw new DicomServiceException(Status.IdentifierDoesNotMatchSOPClass, "Missing Study Instance UID");
            }

            // Look up move destination
            AEDirectory.AEInfo destInfo = aeDirectory.lookup(moveDestination);
            if (destInfo == null) {
                LOG.error("Unknown move destination AE Title: {}", moveDestination);
                throw new DicomServiceException(Status.MoveDestinationUnknown, "Unknown move destination: " + moveDestination);
            }
            String destHost = destInfo.getHost();
            int destPort = destInfo.getPort();

            LOG.info("C-MOVE: Resolved destination {} to {}:{}", moveDestination, destHost, destPort);

            // Calculate expected instances
            int expectedInstances = countExpectedInstances(studyInstanceUID, seriesInstanceUID, sopInstanceUID);

            // Send initial pending response
            sendPendingResponse(as, pc, cmd, expectedInstances, 0, 0, 0);

            // Execute C-MOVE with progress callback
            MHDBackedCMoveExecutor.CMoveResult result = moveExecutor.executeCMove(
                studyInstanceUID, seriesInstanceUID, sopInstanceUID,
                moveDestination, destHost, destPort,
                (remaining, completed, failed, warning) -> {
                    try {
                        sendPendingResponse(as, pc, cmd, remaining, completed, failed, warning);
                    } catch (Exception e) {
                        LOG.warn("Failed to send progress: {}", e.getMessage());
                    }
                });

            // Send completion response
            if (result.success) {
                Attributes rsp = Commands.mkCMoveRSP(cmd, Status.Success);
                rsp.setInt(Tag.NumberOfRemainingSuboperations, VR.US, 0);
                rsp.setInt(Tag.NumberOfCompletedSuboperations, VR.US, result.completedInstances);
                rsp.setInt(Tag.NumberOfFailedSuboperations, VR.US, result.failedInstances);
                rsp.setInt(Tag.NumberOfWarningSuboperations, VR.US, 0);
                as.writeDimseRSP(pc, rsp);
            } else {
                LOG.error("C-MOVE failed: {}", result.errorMessage);
                Attributes rsp = Commands.mkCMoveRSP(cmd, Status.UnableToProcess);
                rsp.setInt(Tag.NumberOfRemainingSuboperations, VR.US, 0);
                rsp.setInt(Tag.NumberOfCompletedSuboperations, VR.US, result.completedInstances);
                rsp.setInt(Tag.NumberOfFailedSuboperations, VR.US, result.failedInstances);
                rsp.setInt(Tag.NumberOfWarningSuboperations, VR.US, 0);
                as.writeDimseRSP(pc, rsp);
            }
        }

        private void sendPendingResponse(Association as, org.dcm4che3.net.pdu.PresentationContext pc,
                                         Attributes cmd, int remaining, int completed, int failed, int warning) {
            try {
                Attributes rsp = Commands.mkCMoveRSP(cmd, Status.Pending);
                rsp.setInt(Tag.NumberOfRemainingSuboperations, VR.US, remaining);
                rsp.setInt(Tag.NumberOfCompletedSuboperations, VR.US, completed);
                rsp.setInt(Tag.NumberOfFailedSuboperations, VR.US, failed);
                rsp.setInt(Tag.NumberOfWarningSuboperations, VR.US, warning);
                as.writeDimseRSP(pc, rsp);
            } catch (IOException e) {
                LOG.error("Failed to send pending response: {}", e.getMessage());
            }
        }

        private int countExpectedInstances(String studyUID, String seriesUID, String sopUID) {
            if (sopUID != null && !sopUID.isEmpty()) {
                return 1;
            }

            try {
                MHDBackedMetadataService.StudyMetadata study = metadataService.getOrFetchStudyMetadata(studyUID);
                if (study == null) {
                    return 0;
                }

                if (seriesUID != null && !seriesUID.isEmpty()) {
                    for (MHDBackedMetadataService.SeriesMetadata series : study.series) {
                        if (series.seriesInstanceUID.equals(seriesUID)) {
                            return series.instances.size();
                        }
                    }
                    return 0;
                }

                return study.numberOfStudyRelatedInstances;
            } catch (Exception e) {
                LOG.warn("Failed to count expected instances: {}", e.getMessage());
                return 0;
            }
        }
    }
}
