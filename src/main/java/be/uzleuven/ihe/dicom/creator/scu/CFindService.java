package be.uzleuven.ihe.dicom.creator.scu;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.*;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.PresentationContext;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Handles DICOM C-FIND network operations.
 * Encapsulates the complexity of dcm4che3 networking logic.
 */
public class CFindService {

    private final DefaultMetadata config;

    public CFindService(DefaultMetadata config) {
        this.config = config;
    }

    /**
     * Performs a C-FIND query with the provided search keys.
     *
     * @param keys DICOM attributes containing the search criteria
     * @return CFindResult containing matched DICOM objects
     * @throws IOException if network communication fails
     */
    public CFindResult performCFind(Attributes keys) throws IOException {
        CFindResult result = new CFindResult();

        Device device = new Device("dicompolice-scu");
        Connection conn = new Connection();
        device.addConnection(conn);

        ApplicationEntity ae = new ApplicationEntity(config.callingAET);
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
            remote.setHostname(config.remoteHost);
            remote.setPort(config.remotePort);

            // Configure timeouts
            conn.setConnectTimeout(config.connectTimeout);
            conn.setResponseTimeout(config.responseTimeout);

            // Create association request
            AAssociateRQ rq = new AAssociateRQ();
            rq.setCallingAET(config.callingAET);
            rq.setCalledAET(config.calledAET);

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
}

