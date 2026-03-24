package be.uzleuven.ihe.service.scp;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DocumentReference;
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
            //for sure
            params.add("patient.identifier=" + urlEncode(patientId));
        }

        if (accessionNumber != null && !accessionNumber.isEmpty()) {
            // not specified in the trial standard, tentative example
            params.add("accession=" + urlEncode(accessionNumber));
        }

        if (studyInstanceUid != null && !studyInstanceUid.isEmpty()) {
            // not specified in the trial standard, tentative example
            params.add("study-instance-uid=" + urlEncode(studyInstanceUid));
        }

        if (modality != null && !modality.isEmpty()) {
            //not specified in the trial standard, likely not correct
            params.add("modality=" + urlEncode(modality));
        }

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
        return results;
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
        String manifestUrl = document.getUrl();

        if ("application/dicom".equals(document.getContentType())) {
            LOG.debug("Retrieving DICOM MADO manifest from: {}", manifestUrl);

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
                    LOG.debug("Retrieved {} bytes from document reference {}", data.length, madoDocRef.getMasterIdentifier());
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
            LOG.debug("Retrieving FHIR MADO manifest from: {}", manifestUrl);

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

