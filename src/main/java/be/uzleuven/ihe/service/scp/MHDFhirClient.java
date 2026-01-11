package be.uzleuven.ihe.service.scp;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
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

    public MHDFhirClient(@Value("${mado.scp.mhd-fhir-base-url}") String mhdBaseUrl) {
        this.mhdBaseUrl = mhdBaseUrl;
        this.fhirContext = FhirContext.forR4();
        this.jsonParser = fhirContext.newJsonParser();
        LOG.info("MHD FHIR Client initialized with base URL: {}", mhdBaseUrl);
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
            params.add("patient.identifier=" + urlEncode(patientId));
        }

        if (accessionNumber != null && !accessionNumber.isEmpty()) {
            params.add("accession=" + urlEncode(accessionNumber));
        }

        if (studyInstanceUid != null && !studyInstanceUid.isEmpty()) {
            params.add("study-instance-uid=" + urlEncode(studyInstanceUid));
        }

        if (modality != null && !modality.isEmpty()) {
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
     * Retrieve a single DocumentReference by Study Instance UID.
     */
    public DocumentReference getDocumentReference(String studyInstanceUid) throws IOException {
        List<DocumentReference> results = searchDocumentReferences(null, null, studyInstanceUid, null, null, null);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Retrieve MADO manifest (Binary resource) for a study.
     * Implements ITI-68 (Retrieve Document) transaction.
     *
     * Optimized to directly construct the MADO manifest URL without querying DocumentReference.
     * This saves an unnecessary FHIR query since the manifest URL follows a predictable pattern.
     *
     * @param studyInstanceUid Study Instance UID
     * @return Raw bytes of the MADO manifest, or null if not found
     */
    public byte[] retrieveDocumentRaw(String studyInstanceUid) throws IOException {
        // Directly construct the MADO manifest URL
        // Pattern: {mhdBaseUrl}/studies/{studyUID}/manifest
        // This skips the unnecessary DocumentReference lookup
        String manifestUrl = buildManifestUrl(studyInstanceUid);

        LOG.debug("Retrieving MADO manifest from: {}", manifestUrl);

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
                LOG.debug("Retrieved {} bytes for study {}", data.length, studyInstanceUid);
                return data;
            }
        } else if (responseCode == 404) {
            LOG.warn("MADO manifest not found for study {}", studyInstanceUid);
            return null;
        } else {
            LOG.error("HTTP {} retrieving MADO manifest from: {}", responseCode, manifestUrl);
            throw new IOException("HTTP " + responseCode + " retrieving MADO manifest: " + manifestUrl);
        }
    }

    /**
     * Build the direct URL to the MADO manifest for a study.
     * Uses the MHD alternative endpoint that returns raw DICOM instead of FHIR Binary.
     *
     * @param studyInstanceUid Study Instance UID
     * @return Direct URL to MADO manifest
     */
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

