package be.uzleuven.ihe.dicom.creator.scu;

/**
 * Configuration for default metadata values when C-FIND responses lack certain attributes.
 * These defaults are essential for IHE XDS-I.b compliance.
 */
public class DefaultMetadata {
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

