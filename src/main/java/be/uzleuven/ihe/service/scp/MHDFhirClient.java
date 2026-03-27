package be.uzleuven.ihe.service.scp;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.dcm4che3.data.Attributes;
import be.uzleuven.ihe.dicom.convertor.dicom.FHIRToMADOConverter;

import static be.uzleuven.ihe.service.MHD.dicom.DicomBackendService.attributesToDicomBytes;

/**
 * HTTP client for querying a remote MHD Document Responder via FHIR REST API.
 *
 * Implements ITI-67 (Find Document References) as an MHD Document Consumer.
 * Queries a remote MHD server over HTTP/HTTPS and returns DocumentReference resources.
 */
@Service
public class MHDFhirClient {

    private static final Logger LOG = LoggerFactory.getLogger(MHDFhirClient.class);

    private final FhirContext fhirContext;
    private final IParser jsonParser;
    private final String mhdBaseUrl;
    private final String localHomeCommunityID;
    private final String xcWadoGateway;
    private final FHIRToMADOConverter fhirToMADOConverter = new FHIRToMADOConverter();

    // Cache: studyInstanceUID -> DocumentReference (populated from broader searches like patient-based)
    private final Map<String, DocumentReference> docRefCache = new ConcurrentHashMap<>();

    public MHDFhirClient(@Value("${mado.scp.mhd-fhir-base-url}") String mhdBaseUrl,
                         @Value("${mhd.home-community-id}") String localHomeCommunityID,
                         @Value("${mhd.xc-wado-gateway}") String xcWadoGateway) {
        this.mhdBaseUrl = mhdBaseUrl;
        this.localHomeCommunityID = localHomeCommunityID;
        this.xcWadoGateway = xcWadoGateway;
        this.fhirContext = FhirContext.forR4();
        this.jsonParser = fhirContext.newJsonParser();
        LOG.info("MHD FHIR Client initialized with base URL: {}", mhdBaseUrl);
    }

    public String getLocalHomeCommunityID() {
        return localHomeCommunityID;
    }

    public String getXcWadoGateway() {
        return xcWadoGateway;
    }

    /**
     * Search for DocumentReferences on the remote MHD server.
     * Implements ITI-67 Find Document References transaction.
     *
     * @param patientId Patient ID
     * @param accessionNumber Accession Number
     * @param studyInstanceUid Study Instance UID
     * @param modality Modality
     * @param dateFrom Study date from (YYYYMMDD)
     * @param dateTo Study date to (YYYYMMDD)
     * @return List of DocumentReference resources
     */
    /** DICOM UID for Key Object Selection Document – the only format we care about for MADO. */
    private static final String KOS_FORMAT_CODE = "1.2.840.10008.5.1.4.1.1.88.59";
    private static final String KOS_FORMAT_SYSTEM = "http://dicom.nema.org/resources/ontology/DCMUID";

    public List<DocumentReference> searchDocumentReferences(
            String patientId,
            String accessionNumber,
            String studyInstanceUid,
            String modality,
            String dateFrom,
            String dateTo) throws IOException {

        StringBuilder urlBuilder = new StringBuilder(mhdBaseUrl);
        if (!mhdBaseUrl.endsWith("/")) {
            urlBuilder.append("/");
        }
        urlBuilder.append("DocumentReference");

        List<String> params = new ArrayList<>();

        if (patientId != null && !patientId.isEmpty()) {
            // DICOM C-FIND allows wildcard matching (*/?), but FHIR token search does not.
            // Strip all wildcard characters and only forward a clean, non-empty value.
            String cleanPatientId = patientId.replace("*", "").replace("?", "").trim();
            if (!cleanPatientId.isEmpty()) {
                params.add("patient.identifier=" + urlEncode(cleanPatientId));
            } else {
                LOG.info("Patient ID '{}' is wildcard-only – omitting patient.identifier filter from MHD query", patientId);
            }
        }

        if (accessionNumber != null && !accessionNumber.isEmpty()) {
            // not specified in the trial standard, tentative example
            params.add("accession=" + urlEncode(accessionNumber));
        }

        if (studyInstanceUid != null && !studyInstanceUid.isEmpty()) {
            // not specified in the trial standard, tentative example
            params.add("identifier=" + urlEncode(studyInstanceUid));
        }

        if (modality != null && !modality.isEmpty()) {
            //not specified in the trial standard, likely not correct
            params.add("modality=" + urlEncode(modality));
        }

        // KOS-only filtering is done client-side in fetchDocumentReferences()
        // (the formatcode search param is not supported by all MHD servers)

        // Date range - convert DICOM format (YYYYMMDD) to FHIR format (YYYY-MM-DD)
        if (dateFrom != null || dateTo != null) {
            StringBuilder dateRange = new StringBuilder();
            if (dateFrom != null && !dateFrom.isEmpty()) {
                dateRange.append("ge").append(formatDate(dateFrom));
            }
            if (dateTo != null && !dateTo.isEmpty()) {
                if (dateRange.length() > 0) {
                    dateRange.append("&date=");
                }
                dateRange.append("le").append(formatDate(dateTo));
            }
            if (dateRange.length() > 0) {
                params.add("date=" + dateRange.toString());
            }
        }

        if (!params.isEmpty()) {
            urlBuilder.append("?").append(String.join("&", params));
        }

        String url = urlBuilder.toString();
        LOG.info("Querying MHD endpoint: {}", url);

        return fetchDocumentReferences(url);
    }

    /**
     * Fetch and parse DocumentReferences from the MHD endpoint.
     */
    private List<DocumentReference> fetchDocumentReferences(String url) throws IOException {
        List<DocumentReference> results = new ArrayList<>();

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/fhir+json");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);

        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            try (InputStream is = conn.getInputStream()) {
                Bundle bundle = jsonParser.parseResource(Bundle.class, is);
                LOG.info("Received Bundle with {} entries (total: {})",
                        bundle.getEntry().size(), bundle.getTotal());

                for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                    if (entry.hasResource() && entry.getResource() instanceof DocumentReference) {
                        results.add((DocumentReference) entry.getResource());
                    }
                }

                // Handle pagination if there are more results
                while (bundle.getLink(Bundle.LINK_NEXT) != null) {
                    String nextUrl = bundle.getLink(Bundle.LINK_NEXT).getUrl();
                    LOG.info("Fetching next page: {}", nextUrl);
                    bundle = fetchNextPage(nextUrl);
                    for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                        if (entry.hasResource() && entry.getResource() instanceof DocumentReference) {
                            results.add((DocumentReference) entry.getResource());
                        }
                    }
                }
            }
        } else {
            String errorMsg = "HTTP " + responseCode + " from MHD endpoint: " + url;
            LOG.error(errorMsg);
            throw new IOException(errorMsg);
        }

        LOG.info("Retrieved {} DocumentReferences from MHD endpoint", results.size());

        // Client-side safety filter: only keep KOS (Key Object Selection) documents.
        // The server may not support the formatcode search parameter, so we filter here as well.
        int beforeFilter = results.size();
        // TODO detect and keep also FHIR MADO Manifest Document References
        results.removeIf(docRef -> !isKosDocumentReference(docRef));
        if (results.size() < beforeFilter) {
            LOG.info("Filtered out {} non-KOS DocumentReferences (kept {})", beforeFilter - results.size(), results.size());
        }

        return results;
    }

    /**
     * Fetch a Patient resource from a known URL.
     *
     * @param patientId The patient ID (e.g., "12345")
     * @return Patient resource, or null if not found
     * @throws IOException if the HTTP request fails
     */
    public Patient fetchPatientResource(String patientResourceUrl) throws IOException {
        // Build the full URL
        StringBuilder urlBuilder = new StringBuilder(mhdBaseUrl);
        if (!mhdBaseUrl.endsWith("/")) {
            urlBuilder.append("/");
        }
        urlBuilder.append(patientResourceUrl);
        String url = urlBuilder.toString();

        LOG.info("Fetching Patient from: {}", url);

        // Make HTTP request
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/fhir+json");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);

        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            try (InputStream is = conn.getInputStream()) {
                // Parse as Patient resource (NOT a Bundle)
                Patient patient = jsonParser.parseResource(Patient.class, is);
                LOG.info("Retrieved Patient with ID: {}", patient.getId());
                return patient;
            }
        } else if (responseCode == 404) {
            LOG.warn("Patient not found from url: {}", patientResourceUrl);
            return null;
        } else {
            String errorMsg = "HTTP " + responseCode + " from FHIR endpoint: " + url;
            LOG.error(errorMsg);
            throw new IOException(errorMsg);
        }
    }


    /**
     * Fetch next page of results using pagination link.
     */
    private Bundle fetchNextPage(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/fhir+json");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);

        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            try (InputStream is = conn.getInputStream()) {
                return jsonParser.parseResource(Bundle.class, is);
            }
        } else {
            throw new IOException("HTTP " + responseCode + " fetching next page: " + url);
        }
    }

    /**
     * Retrieve a single (first in the list) DocumentReference by Study Instance UID.
     */
    public DocumentReference getDocumentReference(String studyInstanceUid) throws IOException {
        List<DocumentReference> results = searchDocumentReferences(null, null, studyInstanceUid, null, null, null);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Cache a DocumentReference by its Study Instance UID (from context.related, code 110180).
     * Call this when DocumentReferences are retrieved from broader searches (e.g., by patient ID)
     * so that subsequent lookups by studyInstanceUID can bypass the MHD search.
     */
    public void cacheDocumentReference(DocumentReference docRef) {
        if (docRef == null) return;
        String studyUid = extractStudyInstanceUIDFromDocRef(docRef);
        if (studyUid != null && !studyUid.isEmpty()) {
            docRefCache.put(studyUid, docRef);
            LOG.debug("Cached DocumentReference for study {}", studyUid);
        }
    }

    /**
     * Get a cached DocumentReference by Study Instance UID, or null if not cached.
     */
    public DocumentReference getCachedDocumentReference(String studyInstanceUid) {
        return docRefCache.get(studyInstanceUid);
    }

    /**
     * Extract Study Instance UID from a DocumentReference.
     * Looks in context.related for identifier with type coding code "110180" (Study Instance UID),
     * falls back to masterIdentifier.
     */
    public static String extractStudyInstanceUIDFromDocRef(DocumentReference docRef) {
        if (docRef.hasContext() && docRef.getContext().hasRelated()) {
            for (org.hl7.fhir.r4.model.Reference related : docRef.getContext().getRelated()) {
                if (related.hasIdentifier()) {
                    org.hl7.fhir.r4.model.Identifier id = related.getIdentifier();
                    if (id.hasType() && id.getType().hasCoding()) {
                        for (org.hl7.fhir.r4.model.Coding coding : id.getType().getCoding()) {
                            if ("110180".equals(coding.getCode())) {
                                return id.getValue();
                            }
                        }
                    }
                }
            }
        }
        return docRef.getMasterIdentifier() != null ? docRef.getMasterIdentifier().getValue() : null;
    }

    /**
     * Retrieve DICOM MADO manifest (Binary resource) from a study DocumentReference.
     * Implements ITI-68 (Retrieve Document) transaction?
     *
     * Currently the FHIR MADO is also fetched if provided by MHD, but it gets converted to DICOM MADO
     *
     * // TODO: add separate function retrieveDocumentFHIRMADO
     *
     * @param madoDocRef MADO Document Reference instance
     * @return Raw bytes of the DICOM MADO manifest, or null if not found
     */
    public byte[] retrieveDocumentRawDICOM(DocumentReference madoDocRef) throws IOException {

        // then fetch the URL to the content attachment raw document
        Attachment document = madoDocRef.getContent().get(0).getAttachment();
        String manifestUrl = resolveManifestUrl(document.getUrl());

        if ("application/dicom".equals(document.getContentType())) {
            LOG.info("Retrieving DICOM MADO manifest from: {}", manifestUrl);

            // Download the Binary resource
            HttpURLConnection conn = (HttpURLConnection) new URL(manifestUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/dicom");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(60000);  // Longer timeout for binary data

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                try (InputStream is = conn.getInputStream()) {
                    byte[] data = is.readAllBytes();
                    LOG.info("Retrieved {} bytes from document reference {}", data.length, madoDocRef.getMasterIdentifier());
                    // Probe: server may return a FHIR Binary JSON wrapper instead of raw DICOM
                    data = unwrapFhirBinaryIfNeeded(data);
                    return data;
                }
            } else if (responseCode == 404) {
                LOG.warn("MADO manifest not found in document reference {}", madoDocRef.getMasterIdentifier());
                return null;
            } else {
                LOG.error("HTTP {} retrieving MADO manifest from: {}", responseCode, manifestUrl);
                throw new IOException("HTTP " + responseCode + " retrieving MADO manifest: " + manifestUrl);
            }
        }
        // not yet fully tested because currently no MHD with FHIR MADO available but should work
        // TODO: implement direct parsing of the FHIR MADO JSON, without converting to DICOM MADO
        else if ("application/fhir+json".equals(document.getContentType())) {
            LOG.info("Retrieving FHIR MADO manifest from: {}", manifestUrl);

            // Download the FHIR JSON resource
            HttpURLConnection conn = (HttpURLConnection) new URL(manifestUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/fhir+json");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(60000);  // Longer timeout for possibly large json

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                try (InputStream is = conn.getInputStream()) {
                    String jsonContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    Attributes attr = fhirToMADOConverter.convertFromJson(jsonContent);
                    return attributesToDicomBytes(attr);
                }
            } else if (responseCode == 404) {
                LOG.warn("MADO manifest not found in document reference {}", madoDocRef.getMasterIdentifier());
                return null;
            } else {
                LOG.error("HTTP {} retrieving MADO manifest from: {}", responseCode, manifestUrl);
                throw new IOException("HTTP " + responseCode + " retrieving MADO manifest: " + manifestUrl);
            }
        }
        else {
            LOG.error("Unsupported content type for MADO manifest: {}", document.getContentType());
            throw new IOException("Unsupported content type for MADO manifest: " + document.getContentType());
        }


    }

    /**
     * Check whether a DocumentReference represents a KOS (Key Object Selection) document.
     * Matches on the format coding ({@value KOS_FORMAT_CODE}) or, as a fallback,
     * on {@code application/dicom} content type with a KOS SOP Class UID.
     */
    private static boolean isKosDocumentReference(DocumentReference docRef) {
        if (docRef == null || !docRef.hasContent()) return false;
        for (DocumentReference.DocumentReferenceContentComponent content : docRef.getContent()) {
            // Primary check: format coding
            if (content.hasFormat()) {
                Coding format = content.getFormat();
                if (KOS_FORMAT_CODE.equals(format.getCode())) {
                    return true;
                }
            }
            // Fallback: application/dicom content type (may include non-KOS, but better than nothing)
            if (content.hasAttachment() && "application/dicom".equals(content.getAttachment().getContentType())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Build the direct URL to the MADO manifest for a study.
     * Uses the MHD alternative endpoint that returns raw DICOM instead of FHIR Binary.
     *
     * @param studyInstanceUid Study Instance UID
     * @return Direct URL to MADO manifest
     */
    /*
    private String buildManifestUrl(String studyInstanceUid) {
        // Use the /mhd/studies/{studyUID}/manifest endpoint
        // This is more efficient than /DocumentReference query + Binary retrieval
        String baseUrl = mhdBaseUrl;
        if (baseUrl.endsWith("/fhir")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 5);
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + "/mhd/studies/" + studyInstanceUid + "/manifest";
    }

     */

    /**
     * Resolve a document attachment URL that may be relative (e.g. "Binary/6909") against
     * the configured FHIR base URL, so that it becomes an absolute HTTP(S) URL.
     *
     * @param rawUrl The URL as it appears in the DocumentReference content attachment
     * @return An absolute URL suitable for use with {@link java.net.URL}
     */
    private String resolveManifestUrl(String rawUrl) {
        if (rawUrl == null) return null;
        if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            return rawUrl;
        }
        // Relative path – resolve against the FHIR base URL
        String base = mhdBaseUrl.endsWith("/") ? mhdBaseUrl : mhdBaseUrl + "/";
        String resolved = base + rawUrl;
        LOG.debug("Resolved relative manifest URL '{}' to '{}'", rawUrl, resolved);
        return resolved;
    }

    /**
     * Detect if the response bytes are a FHIR Binary JSON wrapper instead of raw DICOM,
     * and if so extract and base64-decode the "data" field.
     * Some FHIR servers return: {"resourceType":"Binary","contentType":"application/dicom","data":"...base64..."}
     */
    private byte[] unwrapFhirBinaryIfNeeded(byte[] data) {
        if (data == null || data.length == 0) return data;
        // Quick probe: raw DICOM never starts with '{', JSON does
        if (data[0] != '{') return data;

        try {
            String json = new String(data, StandardCharsets.UTF_8).trim();
            if (json.contains("\"resourceType\"") && json.contains("\"Binary\"") && json.contains("\"data\"")) {
                LOG.info("Response is a FHIR Binary JSON wrapper, extracting base64 data");
                Binary binary = jsonParser.parseResource(Binary.class, json);
                if (binary.hasData()) {
                    return binary.getData();
                }
            }
        } catch (Exception e) {
            LOG.debug("Response is not a FHIR Binary JSON, treating as raw DICOM: {}", e.getMessage());
        }
        return data;
    }

    /**
     * URL encode a parameter value.
     */
    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
        } catch (Exception e) {
            return value;
        }
    }

    /**
     * Convert DICOM date format (YYYYMMDD) to FHIR date format (YYYY-MM-DD).
     */
    private String formatDate(String dicomDate) {
        if (dicomDate == null || dicomDate.length() != 8) {
            return dicomDate;
        }
        return dicomDate.substring(0, 4) + "-" + dicomDate.substring(4, 6) + "-" + dicomDate.substring(6, 8);
    }
}

