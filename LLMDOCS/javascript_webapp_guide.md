# Developer Guide: Building JS/TS Web Applications using MHD, MADO, and WADO-RS

This guide demonstrates how web developers can build clinical viewer applications to query, parse, and download medical imaging volumes directly from the DICOMPolice modernized clinical backend.

## Why this stack?
To avoid old legacy DICOM complexity, security rules, and heavyweight DIMSE protocol sockets (like QIDO, C-FIND, C-MOVE, or C-STORE), this design relies purely on RESTful, lightweight HTTP endpoints:
1. **MHD (Mobile Access to Health Documents)**: A FHIR-based profile for searching and retrieving clinical imaging folders via `DocumentReference` resources.
2. **MADO (Medical Application Document Outline)**: An outline manifest of a study (encoded either as a **FHIR ImagingStudy Bundle** or a **DICOM KOS** file) that specifies the exact Series Instance UIDs and SOP Instance UIDs.
3. **WADO-RS (Web Access to DICOM Objects)**: A RESTful standard to download specific raw DICOM instances (`.dcm` files) using identifiers obtained from the MADO manifest.

---

## 1. Web Application Core Implementation

Here is standard ES6 / TypeScript code to interact with the backend services.

```typescript
/**
 * API Client for MHD, MADO & WADO-RS
 */
export class DICOMWebClient {
  private fhirBaseUrl: string;
  private wadoBaseUrl: string;

  constructor(fhirBaseUrl: string, wadoBaseUrl: string) {
    this.fhirBaseUrl = fhirBaseUrl;
    this.wadoBaseUrl = wadoBaseUrl;
  }

  /**
   * 1. Search DocumentReference (MHD)
   * Query matching manifest folders for a patient or a specific study.
   */
  async searchMhdDocuments(queryParams: Record<string, string>): Promise<any> {
    const url = new URL(`${this.fhirBaseUrl}/DocumentReference`);
    Object.entries(queryParams).forEach(([key, val]) => url.searchParams.append(key, val));

    const response = await fetch(url.toString(), {
      method: 'GET',
      headers: { 
        'Accept': 'application/fhir+json' 
      }
    });

    if (!response.ok) {
      throw new Error(`MHD Search failed: ${response.status} ${response.statusText}`);
    }
    return await response.json();
  }

  /**
   * 2. Fetch the MADO Clinical JSON Manifest
   * Downloads the raw JSON attachment containing the study maps and configurations.
   */
  async fetchMadoManifest(binaryUrl: string): Promise<any> {
    // Resolve relative URLs if needed
    const resolvedUrl = binaryUrl.startsWith('http') 
      ? binaryUrl 
      : `${window.location.origin}/${binaryUrl.replace(/^\.\//, '')}`;

    const response = await fetch(resolvedUrl, {
      method: 'GET',
      headers: { 
        'Accept': 'application/fhir+json, application/json' 
      }
    });

    if (!response.ok) {
      throw new Error(`Failed to load MADO manifest: ${response.status} ${response.statusText}`);
    }
    return await response.json();
  }

  /**
   * 3. Download a DICOM Instance via WADO-RS
   * Retrieves single DICOM file. Handles multipart decoding if required.
   */
  async downloadDicomInstance(studyUid: string, seriesUid: string, instanceUid: string): Promise<Blob> {
    const url = `${this.wadoBaseUrl}/studies/${studyUid}/series/${seriesUid}/instances/${instanceUid}`;
    
    const response = await fetch(url, {
      method: 'GET',
      headers: {
        'Accept': 'multipart/related; type="application/dicom"'
      }
    });

    if (!response.ok) {
      throw new Error(`WADO retrieve failed: ${response.status} ${response.statusText}`);
    }

    const contentType = response.headers.get('content-type') || '';
    const arrayBuffer = await response.arrayBuffer();

    // WADO-RS responses are typically wrapped in multipart/related envelopes
    if (contentType.includes('multipart')) {
      return this.extractDicomFromMultipart(arrayBuffer, contentType);
    }

    // Direct octet-stream fallback
    return new Blob([arrayBuffer], { type: 'application/dicom' });
  }

  /**
   * Helper to strip MIME/HTTP boundary wraps from a raw multipart arrayBuffer
   */
  private extractDicomFromMultipart(arrayBuffer: ArrayBuffer, contentType: string): Blob {
    const boundaryMatch = contentType.match(/boundary=([^;]+)/);
    if (!boundaryMatch) {
      return new Blob([arrayBuffer], { type: 'application/dicom' });
    }

    const boundary = boundaryMatch[1].replace(/"/g, '');
    const uint8Array = new Uint8Array(arrayBuffer);
    const boundaryBytes = new TextEncoder().encode('--' + boundary);

    // Locate double CRLF (\r\n\r\n) indicating the start of actual binary content
    let headerEnd = -1;
    for (let i = 0; i < uint8Array.length - 4; i++) {
      if (
        uint8Array[i] === 0x0D &&
        uint8Array[i + 1] === 0x0A &&
        uint8Array[i + 2] === 0x0D &&
        uint8Array[i + 3] === 0x0A
      ) {
        headerEnd = i + 4;
        break;
      }
    }

    if (headerEnd === -1) {
      return new Blob([arrayBuffer], { type: 'application/dicom' });
    }

    // Locate the terminating boundary
    let endOfContent = uint8Array.length;
    for (let i = headerEnd; i < uint8Array.length - boundaryBytes.length; i++) {
      let match = true;
      for (let j = 0; j < boundaryBytes.length; j++) {
        if (uint8Array[i + j] !== boundaryBytes[j]) {
          match = false;
          break;
        }
      }
      if (match) {
        endOfContent = i - 2; // Offset CRLF
        break;
      }
    }

    const dicomData = arrayBuffer.slice(headerEnd, endOfContent);
    return new Blob([dicomData], { type: 'application/dicom' });
  }
}
```

---

## 2. LLM Prompt Checklist for JS/TS Developers

When prompting an LLM to generate fully functional web clients (React, Vue, Angular, Svelte, or vanilla JS) to interface with this backend, use the template below:

### Copy-Paste LLM Prompt Template

```text
You are an expert Frontend Medical Imaging Developer. I want you to write a clean, single-page Single Page Application (SPA) in modern TypeScript/HTML5 to interact with my clinical imaging backend. 

Key constraints:
- DO NOT use DIMSE (C-FIND, C-MOVE) or DICOM QIDO-RS. 
- You must interact purely with the following standard REST/FHIR APIs:
  1. MHD (FHIR HTTP GET /DocumentReference) to search and retrieve clinical folders.
  2. FHIR Binary retrieval to pull MADO (Medical Application Document Outline) manifests.
  3. WADO-RS RESTful endpoints to download instances.

Provide a production-ready Web client that:
1. Has a search panel querying MHD `/DocumentReference` base URL with parameters like `patient`, `accession`, or `study-instance-uid`.
2. Parses the retrieved FHIR DocumentReference Bundle, recognizes the profiles (e.g. 'MadoFhirDocumentReference' or 'MadoDicomKosDocumentReference'), and lists matching studies.
3. Downloads the MADO manifest JSON file from the corresponding DocumentReference's attachment URL.
4. Parses the downloaded MADO manifest (a FHIR Bundle containing an `ImagingStudy` resource), enumerating its detailed patient, series, and SOP instance UIDs.
5. Implements a parallel download queue that retrieves raw DICOM files via WADO-RS: `/studies/{studyUID}/series/{seriesUID}/instances/{instanceUID}`.
6. Handles a `multipart/related; type="application/dicom"` MIME payload response correctly, including a robust byte-level stream parser that strips out the multipart boundary and header tags, exposing the pure target `.dcm` binary arrays.
7. Offers option to download the retrieved instance(s) as a zip file locally (using JSZip) or visualize them using a dummy canvas layout or integrates with cornerstoneJS / DWV.

Write well-structured, production-grade TypeScript with no missing imports, and clear comments. Add styling using embedded CSS variables for a clean clinical dark-mode theme.
```

