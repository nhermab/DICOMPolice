package be.uzleuven.ihe.service.MHD.config;

import be.uzleuven.ihe.dicom.constants.DicomConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Configuration for MHD Document Responder.
 * Contains MADO-required constants and FHIR server configuration.
 */
@Primary
@Component
@ConfigurationProperties(prefix = "mhd")
public class MHDConfiguration {

    // Institution Information
    private String institutionName = "IHE Demo Hospital";
    private String manufacturerModelName = "DICOM Police MHD Responder";
    private String softwareVersion = "1.0.0";

    // Patient ID Issuer
    private String patientIdIssuerOid = DicomConstants.DEMO_PATIENT_ID_ISSUER_OID;
    private String patientIdIssuerLocalNamespace = "HOSPITAL_A";

    // Accession Number Issuer
    private String accessionNumberIssuerOid = DicomConstants.DEMO_ACCESSION_NUMBER_ISSUER_OID;

    // Repository Information
    private String repositoryUniqueId = DicomConstants.DEMO_REPOSITORY_UNIQUE_ID;
    private String retrieveLocationUid = DicomConstants.DEMO_RETRIEVE_LOCATION_UID;

    // FHIR Server Base URL (used to construct absolute URLs)
    private String fhirBaseUrl = "http://localhost:8080/fhir";

    // WADO-RS Base URL for DICOM retrieval
    private String wadoRsBaseUrl = "https://ihebelgium.ehealthhub.be/orthanc/dicom-web/wado-rs/studies";

    // DICOM Connection Settings
    private String callingAet = "DICOMPOLICE";
    private String calledAet = "ORTHANC";
    private String remoteHost = "172.20.240.184";
    private int remotePort = 4242;
    private int connectTimeout = 5000;
    private int responseTimeout = 10000;

    // Document Responder settings
    private String formatCode = "urn:ihe:rad:MADO";
    private String formatCodeSystem = "http://ihe.net/fhir/ihe.formatcode.fhir/CodeSystem/formatcodes";
    private String typeCode = "55115-0"; // LOINC for Imaging study manifest
    private String typeCodeSystem = "http://loinc.org";
    private String classCode = "IMAGES";
    private String classCodeSystem = "urn:oid:1.3.6.1.4.1.19376.1.2.6.1";

    // MADO Manifest Generation Settings
    // If true, include extended instance-level metadata (Rows, Columns, Bits Allocated, etc.)
    // If false (default), only include standard MADO metadata (Instance Number, Number of Frames)
    private boolean includeExtendedInstanceMetadata = false;

    // Getters and Setters

    public String getInstitutionName() {
        return institutionName;
    }

    public void setInstitutionName(String institutionName) {
        this.institutionName = institutionName;
    }

    public String getManufacturerModelName() {
        return manufacturerModelName;
    }

    public void setManufacturerModelName(String manufacturerModelName) {
        this.manufacturerModelName = manufacturerModelName;
    }

    public String getSoftwareVersion() {
        return softwareVersion;
    }

    public void setSoftwareVersion(String softwareVersion) {
        this.softwareVersion = softwareVersion;
    }

    public String getPatientIdIssuerOid() {
        return patientIdIssuerOid;
    }

    public void setPatientIdIssuerOid(String patientIdIssuerOid) {
        this.patientIdIssuerOid = patientIdIssuerOid;
    }

    public String getPatientIdIssuerLocalNamespace() {
        return patientIdIssuerLocalNamespace;
    }

    public void setPatientIdIssuerLocalNamespace(String patientIdIssuerLocalNamespace) {
        this.patientIdIssuerLocalNamespace = patientIdIssuerLocalNamespace;
    }

    public String getAccessionNumberIssuerOid() {
        return accessionNumberIssuerOid;
    }

    public void setAccessionNumberIssuerOid(String accessionNumberIssuerOid) {
        this.accessionNumberIssuerOid = accessionNumberIssuerOid;
    }

    public String getRepositoryUniqueId() {
        return repositoryUniqueId;
    }

    public void setRepositoryUniqueId(String repositoryUniqueId) {
        this.repositoryUniqueId = repositoryUniqueId;
    }

    public String getRetrieveLocationUid() {
        return retrieveLocationUid;
    }

    public void setRetrieveLocationUid(String retrieveLocationUid) {
        this.retrieveLocationUid = retrieveLocationUid;
    }

    public String getFhirBaseUrl() {
        return fhirBaseUrl;
    }

    public void setFhirBaseUrl(String fhirBaseUrl) {
        this.fhirBaseUrl = fhirBaseUrl;
    }

    public String getWadoRsBaseUrl() {
        return wadoRsBaseUrl;
    }

    public void setWadoRsBaseUrl(String wadoRsBaseUrl) {
        this.wadoRsBaseUrl = wadoRsBaseUrl;
    }

    public String getCallingAet() {
        return callingAet;
    }

    public void setCallingAet(String callingAet) {
        this.callingAet = callingAet;
    }

    public String getCalledAet() {
        return calledAet;
    }

    public void setCalledAet(String calledAet) {
        this.calledAet = calledAet;
    }

    public String getRemoteHost() {
        return remoteHost;
    }

    public void setRemoteHost(String remoteHost) {
        this.remoteHost = remoteHost;
    }

    public int getRemotePort() {
        return remotePort;
    }

    public void setRemotePort(int remotePort) {
        this.remotePort = remotePort;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getResponseTimeout() {
        return responseTimeout;
    }

    public void setResponseTimeout(int responseTimeout) {
        this.responseTimeout = responseTimeout;
    }

    public String getFormatCode() {
        return formatCode;
    }

    public void setFormatCode(String formatCode) {
        this.formatCode = formatCode;
    }

    public String getFormatCodeSystem() {
        return formatCodeSystem;
    }

    public void setFormatCodeSystem(String formatCodeSystem) {
        this.formatCodeSystem = formatCodeSystem;
    }

    public String getTypeCode() {
        return typeCode;
    }

    public void setTypeCode(String typeCode) {
        this.typeCode = typeCode;
    }

    public String getTypeCodeSystem() {
        return typeCodeSystem;
    }

    public void setTypeCodeSystem(String typeCodeSystem) {
        this.typeCodeSystem = typeCodeSystem;
    }

    public String getClassCode() {
        return classCode;
    }

    public void setClassCode(String classCode) {
        this.classCode = classCode;
    }

    public String getClassCodeSystem() {
        return classCodeSystem;
    }

    public void setClassCodeSystem(String classCodeSystem) {
        this.classCodeSystem = classCodeSystem;
    }

    public boolean isIncludeExtendedInstanceMetadata() {
        return includeExtendedInstanceMetadata;
    }

    public void setIncludeExtendedInstanceMetadata(boolean includeExtendedInstanceMetadata) {
        this.includeExtendedInstanceMetadata = includeExtendedInstanceMetadata;
    }

    /**
     * Converts this configuration to a DefaultMetadata object for use with existing DICOM creators.
     */
    public be.uzleuven.ihe.dicom.creator.scu.DefaultMetadata toDefaultMetadata() {
        return new be.uzleuven.ihe.dicom.creator.scu.DefaultMetadata()
                .withPatientIdIssuerOid(patientIdIssuerOid)
                .withPatientIdIssuerLocalNamespace(patientIdIssuerLocalNamespace)
                .withAccessionNumberIssuerOid(accessionNumberIssuerOid)
                .withRetrieveLocationUid(retrieveLocationUid)
                .withWadoRsBaseUrl(wadoRsBaseUrl)
                .withInstitutionName(institutionName)
                .withCallingAET(callingAet)
                .withCalledAET(calledAet)
                .withRemoteHost(remoteHost)
                .withRemotePort(remotePort)
                .withConnectTimeout(connectTimeout)
                .withResponseTimeout(responseTimeout);
    }
}
