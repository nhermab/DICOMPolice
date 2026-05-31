# Integration Reference: MHD, MADO, and WADO-RS Specifications

This document outlines the layout of modern, RESTful medical imaging architectures, explaining how **MHD**, **MADO**, and **WADO-RS** work together inside the DICOMPolice ecosystem. It serves as a central reference manual for any team designing microservices, clinical viewers, or automated queues targeting our server.

---

## 1. Protocol Walkthrough

Instead of combining old-school TCP sockets (DIMSE C-MOVE, C-GET, C-FIND) with heavy database layers, our modernized environment decomposes radiological volumes into **Documents (MHD)**, **Manifests (MADO)**, and **Object Resources (WADO-RS)**.

### A. Phase 1: Search via MHD (Mobile Access to Health Documents)
In the MHD profile, study indices are translated into HL7 FHIR **DocumentReference** resources.
- **REST Request**:
  ```http
  GET /fhir/DocumentReference?patient=10928374
  Accept: application/fhir+json
  ```
- **What is returned**: A FHIR `Bundle` of type `searchset` containing entries which conform to the **MADO Manifest Profile** (`MadoFhirDocumentReference` or `MadoDicomKosDocumentReference`).

Key fields inside a DocumentReference instance:
- `id`: The unique system ID of this specific registry entry.
- `masterIdentifier.value`: The Study Instance UID of the imaging volume.
- `extension[...extension-DocumentReference.modality]`: Lists the clinical modality (e.g. `CT`, `MR`, `US`).
- `content[0].attachment.url`: A relative or absolute path pointing to the Binary outline manifest (the **MADO Manifest**).
- `content[0].attachment.contentType`: Mime-type indicating the outline's encoding format (`application/fhir+json` or `application/dicom`).

---

### B. Phase 2: Resolve Outline with MADO (Medical Application Document Outline)
A MADO manifest represents the detailed "table of contents" for an entire imaging study. Once the client extracts the `content[0].attachment.url` from the `DocumentReference` search result, it performs a second HTTP request to load the manifest.

The returned JSON manifest typically matches a **FHIR ImagingStudy Bundle**. Below is a high-level representation:

```json
{
  "resourceType": "Bundle",
  "type": "collection",
  "entry": [
    {
      "resource": {
        "resourceType": "Patient",
        "name": [{ "text": "Jane Doe" }]
      }
    },
    {
      "resource": {
        "resourceType": "ImagingStudy",
        "uid": "1.2.826.0.1.3680043.9.4245.30553298636267069919800",
        "status": "available",
        "series": [
          {
            "uid": "1.2.826.0.1.3680043.9.4245.30553298636267069919800.1",
            "number": 1,
            "modality": { "coding": [{ "code": "MR" }] },
            "instance": [
              {
                "uid": "1.2.826.0.1.3680043.9.4245.30553298636267069919800.1.101",
                "number": 1,
                "sopClass": { "code": "1.2.840.10008.5.1.4.1.1.4" }
              }
            ]
          }
        ]
      }
    }
  ]
}
```

By extracting the `ImagingStudy` series list, the client application compiles the precise target coordinate tree: `(StudyInstanceUID, SeriesInstanceUID, SOPInstanceUID)`.

---

### C. Phase 3: Binary Download via WADO-RS
Rather than triggering slow sequential connections, the client downloads individual SOP Instances in parallel over pure HTTP connections.

- **Target URL Pattern**:
  ```http
  GET /dicomweb/studies/{studyUID}/series/{seriesUID}/instances/{instanceUID}
  ```
- **Required Header**:
  ```http
  Accept: multipart/related; type="application/dicom"
  ```

#### Decoding WADO-RS Multipart Streams
WADO-RS responses package raw `.dcm` files inside a standard HTTP MIME multipart wrapper. The raw response stream looks like this:

```http
HTTP/1.1 200 OK
Content-Type: multipart/related; boundary=mado_boundary_xyz; type="application/dicom"

--mado_boundary_xyz
Content-Type: application/dicom
Content-Transfer-Encoding: binary

[RAW DICOM BINARY DATA STARTS HERE (starts with 128-byte preamble + "DICM" prefix)]
--mado_boundary_xyz--
```

**Client Responsibilities**:
1. Read the `Content-Type` header from the response to identify the `boundary=` string parameter.
2. Locate the first occurrence of `\r\n\r\n` (double CRLF) inside the body stream to skip past the inner MIME headers.
3. Locate the terminating delimiter matching `--boundary`.
4. Slice and save the binary data inside those two indexes. This reveals the pure un-obscured DICOM file (`.dcm`).

---

## 2. General-Purpose Prompting Cookbook (for LLMs)

When generating other pipelines (Go, Rust, C#, PHP), use the unified blueprint prompt below to configure an LLM to build a DICOMPolice-compatible client module.

### Complete Architectural LLM Blueprint Prompt

```text
You are a Staff Systems Architect and Software Engineer specialized in modernized clinical informatics. I need you to write a clean, thread-safe, high-performance integration module in [INSERT YOUR LANGUAGE HERE] to download clinical imaging volumes from my modern healthcare backend.

Architectural Stack constraints:
- DO NOT use dcm4che, pynetdicom or DIMSE services (Reject C-STORE, C-GET, C-FIND, C-MOVE etc.).
- Communication with the server operates entirely via standard REST endpoints utilizing MHD, MADO, and WADO-RS pathways.

System Tasks to Implement:
1. Search Registry (MHD):
   Implement a function to query the FHIR Server's Documents Registry using:
   `GET [fhirBaseBase]/DocumentReference?patient=[patientID]` or standard identifiers.
   Set headers to: `Accept: application/fhir+json`.
   
2. Resolve DocumentReference:
   Scan the output FHIR Bundle. Identify the correct DocumentReference resource supporting MADO.
   Follow the attachment element (`content[0].attachment.url`) to fetch the manifest.

3. Load MADO Folder Outline:
   Download the JSON manifest from the parsed URL. Traverse the file to extract critical structured properties:
   - Patient info (subject identifiers)
   - ImagingStudy details (StudyInstanceUID, SeriesInstanceUIDs, Modalities, and SOPInstanceUIDs).

4. Fetch Instances via WADO-RS:
   Launch a parallel/concurrent thread pool to pull each instance individually.
   Target Endpoint: `GET [wadoRsBase]/studies/{studyUID}/series/{seriesUID}/instances/{sopUID}`.
   Set active Request Headers to: `Accept: multipart/related; type="application/dicom"`.

5. Decode Multipart Envelopes:
   Write a lightweight byte scanner/decoder.
   Identify 'boundary=' strings from the Response headers.
   Find the index separating internal MIME tags (demoted after '\r\n\r\n' CRLFs) from the actual payload start.
   Locate the matching trailing bound delimiter, crop out boundaries/trailing metadata, and return the clean, uncorrupted binary array.

6. File Writer:
   Persist the output byte array directly as a standard DICOM file format: `./output_folder/{sopUID}.dcm`.

Ensure complete implementation, standard dependency choices, robust logging, connection pooling, and proper thread/concurrency management.
```

