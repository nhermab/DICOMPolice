# Developer Guide: Building Java Desktop & Backend Apps using MHD, MADO, and WADO-RS

This guide demonstrates how Java developers can build standalone backend workers, routing scripts, or desktop client apps (JavaFX / Swing) to lookup, fetch, and parse medical images using the DICOMPolice modernized RESTful stack.

## Architecture Guidelines
- **No QIDO-RS**: Queries are handled purely via lightweight FHIR MHD search endpoints.
- **No DIMSE (C-MOVE/C-GET)**: Real-time image retrieval works entirely via HTTP WADO-RS.
- **MADO Manifest Parsing**: Clients download the MADO manifest JSON which outlines the precise layout of Study, Series, and Instances to fetch.

---

## 1. Java Core Client Implementation

This component uses standard native **Java 11+ HttpClient** and provides high-performance, zero-dependency streaming of multipart payloads.

```java
package be.uzleuven.ihe.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Modern Java Client for MHD registries and WADO-RS endpoints.
 * Tailored for high-throughput clinical routing or desktop applications.
 */
public class DicomPoliceClient {

    private final HttpClient httpClient;
    private final ExecutorService downloadExecutor;

    public DicomPoliceClient(int maxParallelThreads) {
        this.downloadExecutor = Executors.newFixedThreadPool(maxParallelThreads);
        this.httpClient = HttpClient.newBuilder()
                .executor(downloadExecutor)
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * 1. MHD Query
     * Query the FHIR registry for a Patient's DocumentReference files.
     */
    public String searchMhdRegistry(String fhirBaseUrl, String patientId) throws IOException, InterruptedException {
        String url = fhirBaseUrl + "/DocumentReference?patient=" + patientId;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/fhir+json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Failed to search MHD: HTTP " + response.statusCode());
        }
        return response.body();
    }

    /**
     * 2. Download MADO Outline Manifest
     * Downloads the raw JSON/Bundle content outlining the clinical image study.
     */
    public String downloadMadoManifest(String binaryUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(binaryUrl))
                .header("Accept", "application/fhir+json, application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Failed to retrieve MADO manifest: HTTP " + response.statusCode());
        }
        return response.body();
    }

    /**
     * 3. Download raw DICOM instance using WADO-RS
     * Handles multipart boundaries efficiently by extracting raw .dcm bytes.
     */
    public CompletableFuture<Path> downloadInstanceAsync(
            String wadoBaseUrl, 
            String studyUid, 
            String seriesUid, 
            String instanceUid, 
            Path outputDir
    ) {
        String url = String.format("%s/studies/%s/series/%s/instances/%s", 
                wadoBaseUrl, studyUid, seriesUid, instanceUid);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "multipart/related; type=\"application/dicom\"")
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new RuntimeException("WADO-RS request failed: HTTP " + response.statusCode());
                    }

                    byte[] body = response.body();
                    String contentType = response.headers().firstValue("Content-Type").orElse("");

                    byte[] dicomBytes = body;
                    if (contentType.toLowerCase().contains("multipart")) {
                        dicomBytes = extractDicomFromMultipart(body, contentType);
                    }

                    try {
                        Path outputPath = outputDir.resolve(instanceUid + ".dcm");
                        Files.write(outputPath, dicomBytes);
                        return outputPath;
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to write DICOM file to filesystem", e);
                    }
                });
    }

    /**
     * Decapsulates multipart/related envelopes to isolate the raw DICOM byte payload.
     */
    private byte[] extractDicomFromMultipart(byte[] rawPayload, String contentType) {
        int boundIdx = contentType.indexOf("boundary=");
        if (boundIdx == -1) {
            return rawPayload;
        }

        String boundary = "--" + contentType.substring(boundIdx + 9).split(";")[0].replace("\"", "").trim();
        byte[] boundaryBytes = boundary.getBytes();

        // 1. Scan for the double CRLF (\r\n\r\n) marking the completion of internal MIME headers
        int headerEnd = -1;
        for (int i = 0; i < rawPayload.length - 4; i++) {
            if (rawPayload[i] == 0x0D && rawPayload[i+1] == 0x0A && 
                rawPayload[i+2] == 0x0D && rawPayload[i+3] == 0x0A) {
                headerEnd = i + 4;
                break;
            }
        }
        if (headerEnd == -1) {
            return rawPayload;
        }

        // 2. Discover the terminating multipart boundary bytes
        int endOfContent = rawPayload.length;
        for (int i = headerEnd; i < rawPayload.length - boundaryBytes.length; i++) {
            boolean match = true;
            for (int j = 0; j < boundaryBytes.length; j++) {
                if (rawPayload[i+j] != boundaryBytes[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                endOfContent = i - 2; // Exclude preceding CRLF characters
                break;
            }
        }

        return Arrays.copyOfRange(rawPayload, headerEnd, endOfContent);
    }

    /**
     * Proper shutdown of the client's internal resources.
     */
    public void shutdown() {
        downloadExecutor.shutdown();
    }
}
```

---

## 2. LLM Prompt Checklist for Java Developers

Use this specification when generating fully featured Java backend frameworks, desktop applications, or image ingestion microservices:

### Copy-Paste LLM Prompt Template

```text
You are a Staff Software Engineer specialized in Java Medical Imaging Backends. I need you to write a clean, high-performance, and lightweight Java library/utility leveraging Java 11's HttpClient to download DICOM studies using MADO and WADO-RS (no QIDO-RS or legacy C-MOVE/DIMSE transactions).

The system should accomplish the following:
1. Fetch modern MHD directories: Query standard FHIR DocumentReference endpoints:
   `GET [fhirBaseUrl]/DocumentReference?patient=[patientID]` or `study-instance-uid=[studyUID]`.
2. Parse the FHIR Bundle (JSON) using standard org.json or lightweight Jackson. Extract the Binary content URL mapping the outline of the specific DICOM series.
3. Access the remote MADO JSON attachment representing a detailed ImagingStudy resource folder (extracting patient information, SeriesInstanceUIDs, and SOPInstanceUIDs).
4. Download each SOP instance concurrently (using a Java thread-poolExecutor like `Executors.newFixedThreadPool` or Java Virtal Threads) by sending WADO-RS GET requests containing headers:
   `Accept: multipart/related; type="application/dicom"`
5. Include a fast, zero-copy byte array scanner method to isolate and parse the actual DICOM binary payload by stripping out individual HTTP multipart headers and trailing boundary flags.
6. Save the extracted `.dcm` files safely onto the local computer directory filesystem.

Write the code as a self-contained, highly optimized Java class, avoiding third-party dependency creep (only use native Java SE HTTP clients, concurrency locks, and basic Jackson or standard JSON parser structures if needed).
```

