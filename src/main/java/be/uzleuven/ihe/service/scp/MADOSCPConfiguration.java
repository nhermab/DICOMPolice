package be.uzleuven.ihe.service.scp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for MADO SCP (Service Class Provider).
 *
 * This SCP provides DICOM C-FIND/C-MOVE services backed by:
 * - MHD (ITI-67/ITI-68) for metadata queries and MADO retrieval
 * - WADO-RS for actual DICOM data retrieval during C-MOVE
 */
@Component
@ConfigurationProperties(prefix = "mado.scp")
public class MADOSCPConfiguration {

    /** AE Title of this SCP */
    private String aeTitle = "MADOSCP";

    /** Port to listen on for DICOM associations */
    private int port = 11112;

    /** Maximum PDU length for DICOM associations */
    private int maxPduLength = 16384;

    /** Whether to start the SCP automatically on application startup */
    private boolean autoStart = true;

    /** Connection timeout in milliseconds */
    private int connectionTimeout = 30000;

    /** Association timeout in milliseconds */
    private int associationTimeout = 60000;

    /** Maximum number of concurrent associations */
    private int maxAssociations = 10;

    /** FHIR base URL for MHD queries (ITI-67 Find, ITI-68 Retrieve MADO) */
    private String mhdFhirBaseUrl = "http://localhost:8080/fhir";

    /** WADO-RS base URL for retrieving DICOM files during C-MOVE */
    private String wadoRsBaseUrl = "https://ihebelgium.ehealthhub.be/orthanc/dicom-web/wado-rs/studies";

    /** @deprecated No longer used - metadata comes from MHD, not local files */
    @Deprecated
    private String madoFilesDirectory = "MADO_FROM_SCU";

    /** Number of parallel downloads from WADO-RS during C-MOVE */
    private int maxParallelDownloads = 5;

    /** Number of parallel C-STORE operations during C-MOVE */
    private int maxParallelStores = 3;

    // Getters and Setters

    public String getAeTitle() {
        return aeTitle;
    }

    public void setAeTitle(String aeTitle) {
        this.aeTitle = aeTitle;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getMaxPduLength() {
        return maxPduLength;
    }

    public void setMaxPduLength(int maxPduLength) {
        this.maxPduLength = maxPduLength;
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public int getAssociationTimeout() {
        return associationTimeout;
    }

    public void setAssociationTimeout(int associationTimeout) {
        this.associationTimeout = associationTimeout;
    }

    public int getMaxAssociations() {
        return maxAssociations;
    }

    public void setMaxAssociations(int maxAssociations) {
        this.maxAssociations = maxAssociations;
    }

    public String getMhdFhirBaseUrl() {
        return mhdFhirBaseUrl;
    }

    public void setMhdFhirBaseUrl(String mhdFhirBaseUrl) {
        this.mhdFhirBaseUrl = mhdFhirBaseUrl;
    }

    public String getWadoRsBaseUrl() {
        return wadoRsBaseUrl;
    }

    public void setWadoRsBaseUrl(String wadoRsBaseUrl) {
        this.wadoRsBaseUrl = wadoRsBaseUrl;
    }

    public String getMadoFilesDirectory() {
        return madoFilesDirectory;
    }

    public void setMadoFilesDirectory(String madoFilesDirectory) {
        this.madoFilesDirectory = madoFilesDirectory;
    }

    public int getMaxParallelDownloads() {
        return maxParallelDownloads;
    }

    public void setMaxParallelDownloads(int maxParallelDownloads) {
        this.maxParallelDownloads = maxParallelDownloads;
    }

    public int getMaxParallelStores() {
        return maxParallelStores;
    }

    public void setMaxParallelStores(int maxParallelStores) {
        this.maxParallelStores = maxParallelStores;
    }
}

