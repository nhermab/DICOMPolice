package be.uzleuven.ihe.service.wado;

import be.uzleuven.ihe.service.scp.MHDBackedMetadataService;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/dicomweb")
public class WadoURIProxyController {

    private static final Logger LOGGER = LoggerFactory.getLogger(WadoURIProxyController.class);
    private final MHDBackedMetadataService metadataService;

    private static final int MAX_REDIRECTS = 5;

    public WadoURIProxyController(MHDBackedMetadataService metadataService) {
        this.metadataService = metadataService;
    }

    /**
     * Proxy WADO-URI requests to WADO-RS.
     * GET /dicomweb/wadouri?requestType=WADO&studyUID={studyUID}&seriesUID={seriesUID}&objectUID={instanceUID}
     */
    @GetMapping("/wadouri")
    public ResponseEntity<byte[]> proxyWADOURIInstanceRequest(
            @RequestParam("studyUID") String studyUID,
            @RequestParam("seriesUID") String seriesUID,
            @RequestParam("objectUID") String instanceUID,
            @RequestParam("requestType") String requestType,
            @RequestParam MultiValueMap<String, String> params,
            @RequestHeader Map<String, String> requestHeaders) throws IOException {

        if (!"WADO".equalsIgnoreCase(requestType)) {
            //LOG.warn("Invalid requestType for WADO-URI request: {}", requestType);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(("Invalid requestType: " + requestType).getBytes());
        }

        String wadoRsPath = "/studies/" + studyUID + "/series/" + seriesUID + "/instances/" + instanceUID;

        // look up the wado-rs URL from metadata service
        Attributes instanceAttrs = new Attributes();
        instanceAttrs.setString(Tag.StudyInstanceUID, VR.UI, studyUID);
        instanceAttrs.setString(Tag.SeriesInstanceUID, VR.UI, seriesUID);
        instanceAttrs.setString(Tag.SOPInstanceUID, VR.UI, instanceUID);
        List<MHDBackedMetadataService.InstanceMetadata> instanceMetadataList = metadataService.findInstances(instanceAttrs);
        if (instanceMetadataList.isEmpty() || instanceMetadataList.size() > 1 || instanceMetadataList.get(0).effectiveRetrieveURL == null || instanceMetadataList.get(0).effectiveRetrieveURL.isEmpty()) {
            LOGGER.warn("Instance not found in metadata service: {}/{}/{}", studyUID, seriesUID, instanceUID);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(("Instance not found: " + studyUID + "/" + seriesUID + "/" + instanceUID).getBytes());
        }
        String remoteUrl = instanceMetadataList.get(0).effectiveRetrieveURL;

        // Additional WADO-URI and WADO-RS URL parameters are not identical
        // must convert if possible, not supported yet
        // contentType param in URL not standard? but sent by Xero
        if (params.size() > 4 && !params.keySet().contains("contentType")) {
            LOGGER.warn("Received WADO-URI request with additional {} unsupported query parameters: {} ... ", params.size()-4, params.toString());
        }
        /*
        if (params != null && !params.isEmpty()) {
            StringBuilder queryString = new StringBuilder();
            params.forEach((key, values) -> {
                for (String value : values) {
                    if (queryString.length() > 0) {
                        queryString.append("&");
                    }
                    queryString.append(key).append("=").append(value);
                }
            });
            if (queryString.length() > 0) {
                remoteUrl += "?" + queryString;
            }
        }
        */

        LOGGER.info("Proxying to: {}", remoteUrl);

        // Build request headers
        HttpHeaders headers = new HttpHeaders();

        // currently accept only the most common multipart application/dicom
        headers.setAccept(java.util.Arrays.asList(
                MediaType.parseMediaType("multipart/related;type=\"application/dicom\"")//,
                // not supported yet
                //MediaType.parseMediaType("application/dicom"),
                //MediaType.APPLICATION_OCTET_STREAM
        ));

        try {
            // Make the proxied request, following redirects (301/302/307/308) manually
            // to support cross-protocol redirects (e.g. HTTP→HTTPS)
            ResponseEntity<byte[]> wadoRSResponse = executeWithRedirects(remoteUrl, headers);

            return convertWadoRSToWadoURIResponse(wadoRSResponse);

        } catch (Exception e) {
            LOGGER.error("Error proxying WADO-URI request to WADO-RS: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(("Error proxying: " + e.getMessage()).getBytes());
        }
    }

    /**
     * Execute an HTTP GET request following 301/302/307/308 redirects (including cross-protocol).
     */
    private ResponseEntity<byte[]> executeWithRedirects(String targetUrl, HttpHeaders requestHeaders) throws IOException {
        String currentUrl = targetUrl;
        int redirectCount = 0;

        while (redirectCount <= MAX_REDIRECTS) {
            URL url = new URL(currentUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(120000);

            // Forward relevant headers
            if (requestHeaders.getAccept() != null && !requestHeaders.getAccept().isEmpty()) {
                conn.setRequestProperty("Accept",
                        requestHeaders.getAccept().stream()
                                .map(MediaType::toString)
                                .reduce((a, b) -> a + ", " + b)
                                .orElse("multipart/related;type=\"application/dicom\""));
            }

            int responseCode = conn.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                    || responseCode == 307 || responseCode == 308) {
                String location = conn.getHeaderField("Location");
                if (location == null || location.isEmpty()) {
                    throw new IOException("WADO-RS redirect (" + responseCode + ") with no Location header for URL: " + currentUrl);
                }
                URL base = new URL(currentUrl);
                URL resolved = new URL(base, location);
                String redirectUrl = resolved.toString();
                LOGGER.info("WADO-RS redirect ({}) from {} to {}", responseCode, currentUrl, redirectUrl);
                conn.disconnect();
                currentUrl = redirectUrl;
                redirectCount++;
                continue;
            }

            // Read the response body
            HttpHeaders responseHeaders = new HttpHeaders();
            conn.getHeaderFields().forEach((key, values) -> {
                if (key != null && values != null) {
                    values.forEach(v -> responseHeaders.add(key, v));
                }
            });

            byte[] body;
            try (InputStream is = (responseCode >= 200 && responseCode < 300)
                    ? conn.getInputStream() : conn.getErrorStream()) {
                body = (is != null) ? readAllBytesFromStream(is) : new byte[0];
            }

            return ResponseEntity.status(responseCode)
                    .headers(responseHeaders)
                    .body(body);
        }

        throw new IOException("WADO-RS too many redirects (>" + MAX_REDIRECTS + ") starting from URL: " + targetUrl);
    }

    private byte[] readAllBytesFromStream(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int len;
        while ((len = is.read(buffer)) > 0) {
            baos.write(buffer, 0, len);
        }
        return baos.toByteArray();
    }

    ResponseEntity<byte[]> convertWadoRSToWadoURIResponse(ResponseEntity<byte[]> wadoRSResponse) throws IOException {
        // convert WADO-RS Response to WADO-URI Response
        if (wadoRSResponse.getStatusCode().is2xxSuccessful()) {
            // Get content type to check if it's multipart
            String contentType = wadoRSResponse.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);

            if (contentType == null || !contentType.startsWith("multipart/related; type=\"application/dicom\"")) {
                //LOGGER.error("Unsupported content type: {}", contentType);
                return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body(("Unsupported content type: " + contentType).getBytes());
            }

            // Handle multipart response - extract boundary and DICOM content
            String boundary = extractBoundary(contentType);
            if (boundary == null) {
                //LOGGER.error("No boundary for multipart/related");
                return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body(("No boundary for multipart/related").getBytes());
            }

            if (wadoRSResponse.getBody() == null) {
                return ResponseEntity.status(HttpStatus.NO_CONTENT).body(null);
            }

            InputStream inputStream = new BufferedInputStream(new ByteArrayInputStream(wadoRSResponse.getBody()));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            OutputStream outputStream = new BufferedOutputStream(baos);
            processMultipartContent(inputStream, outputStream, boundary);
            outputStream.flush();
            return ResponseEntity
                    .status(wadoRSResponse.getStatusCode())
                    .contentType(MediaType.parseMediaType("application/dicom"))
                    .body(baos.toByteArray());
        } else {
            return ResponseEntity
                    .status(wadoRSResponse.getStatusCode())
                    .contentType(MediaType.parseMediaType("application/dicom"))
                    .body(wadoRSResponse.getBody());
        }
    }

    /**
     * Extract boundary from Content-Type header
     */
    private String extractBoundary(String contentType) {
        if (contentType == null) {
            return null;
        }

        int boundaryIndex = contentType.indexOf("boundary=");
        if (boundaryIndex != -1) {
            String boundary = contentType.substring(boundaryIndex + 9); // 9 = "boundary=".length()

            // Handle quoted boundary
            if (boundary.startsWith("\"") && boundary.endsWith("\"")) {
                boundary = boundary.substring(1, boundary.length() - 1);
            }

            // If there are additional parameters after boundary
            int endIndex = boundary.indexOf(";");
            if (endIndex != -1) {
                boundary = boundary.substring(0, endIndex);
            }

            return boundary;
        }
        return null;
    }

    /**
     * Process multipart content to extract the DICOM file
     * This implementation focuses on extracting a single DICOM file from multipart response
     * and preserves all binary data exactly as received
     */
    private void processMultipartContent(InputStream inputStream, OutputStream outputStream, String boundary)
            throws IOException {
        String boundaryMarker = "--" + boundary;
        byte[] boundaryBytes = boundaryMarker.getBytes();
        byte[] buffer = new byte[8192];

        // Find first boundary
        if (!findBoundary(inputStream, boundaryBytes)) {
            //LOGGER.error("Could not find starting boundary");
            return;
        }

        // Skip headers until empty line (CRLFCRLF)
        if (!skipHeaders(inputStream)) {
            //LOGGER.error("Could not find end of headers");
            return;
        }

        // Now we're at the start of DICOM data
        // Read data directly to output until we find the next boundary
        ByteArrayOutputStream bufferStream = new ByteArrayOutputStream();
        int bytesRead;
        int boundarySize = boundaryMarker.length() + 2; // +2 for CRLF
        boolean foundEndBoundary = false;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            // Add newly read data to our buffer
            bufferStream.write(buffer, 0, bytesRead);

            // Check if we have enough data to safely process
            byte[] currentData = bufferStream.toByteArray();

            // Keep a sliding window approach - write data safely and retain buffer for boundary detection
            if (currentData.length > boundarySize * 2) {
                // Search for boundary in current buffer
                int boundaryPos = indexOfBytes(currentData, ("\r\n" + boundaryMarker).getBytes());

                if (boundaryPos >= 0) {
                    // Found boundary - write data up to boundary
                    outputStream.write(currentData, 0, boundaryPos);
                    foundEndBoundary = true;
                    break;
                }

                // No boundary found - write majority of buffer and keep just enough for boundary detection
                int safeWriteLength = currentData.length - boundarySize * 2;
                outputStream.write(currentData, 0, safeWriteLength);

                // Keep remaining bytes for next check
                bufferStream.reset();
                bufferStream.write(currentData, safeWriteLength, currentData.length - safeWriteLength);
            }
        }

        // If we've reached the end without finding a boundary, write the remaining buffer
        if (!foundEndBoundary && bufferStream.size() > 0) {
            outputStream.write(bufferStream.toByteArray());
        }
    }

    /**
     * Skip headers until an empty line (CRLFCRLF sequence)
     */
    private boolean skipHeaders(InputStream inputStream) throws IOException {
        int state = 0; // 0=normal, 1=CR, 2=CRLF, 3=CRLFCR
        int b;

        while ((b = inputStream.read()) != -1) {
            switch (state) {
                case 0: // Normal state
                    if (b == '\r') {
                        state = 1;
                    }
                    break;
                case 1: // Saw CR
                    if (b == '\n') {
                        state = 2;
                    } else {
                        state = 0;
                    }
                    break;
                case 2: // Saw CRLF
                    if (b == '\r') {
                        state = 3;
                    } else {
                        state = 0;
                    }
                    break;
                case 3: // Saw CRLFCR
                    if (b == '\n') {
                        return true; // Found CRLFCRLF
                    } else {
                        state = 0;
                    }
                    break;
            }
        }

        return false; // Reached end without finding empty line
    }

    /**
     * Helper method to find byte sequence within another byte array
     */
    private int indexOfBytes(byte[] data, byte[] pattern) {
        outer:
        for (int i = 0; i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            return i; // Found a match
        }
        return -1; // No match
    }

    /**
     * Find a boundary marker in the input stream
     */
    private boolean findBoundary(InputStream inputStream, byte[] boundaryBytes) throws IOException {
        int matchedPos = 0;
        int b;

        while ((b = inputStream.read()) != -1) {
            if (b == boundaryBytes[matchedPos]) {
                matchedPos++;
                if (matchedPos == boundaryBytes.length) {
                    return true; // Found complete boundary
                }
            } else {
                matchedPos = 0; // Reset match position
            }
        }

        return false; // Reached end without finding boundary
    }

}
