# 🩻 IHE MHD Document Responder & MADO REST APIs
### Developer Integration & App Development Guide

Welcome! This workspace hosts a fully compliant **IHE MHD (Mobile access to Health Documents) Document Responder** that acts as a secure, high-performance FHIR R4 API facade over a traditional DIMSE-based DICOM PACS. On top of standard MHD, it provides native support for **MADO (Manifest for Access to DICOM Objects)** and Key Object Selection (KOS) manifests, making PACS data accessible to web apps, native mobile apps, and clinical portals.

---

## 🌐 Hosted Base URLs

You can deploy and access these services locally or interface with the reference public deployment:

* **Hosted Base URL:** `https://ihebelgium.ehealthhub.be/TheDICOMPolice/fhir`
* **Local Base URL:** `http://localhost:8080/fhir`

*All endpoints support standard CORS HEADERS, enabling direct browser integrations.*

---

## ⚡ App Workflow Integration

Modern frontend apps (such as the built-in [MHD MADO Viewer](/xtehdsMADO) or [DICOM Downloader](/dicom-downloader)) leverage this server in a highly efficient three-step sequence:

```
┌─────────────────┐       (1) Search (ITI-67)        ┌─────────────────────────┐
│                 ├─────────────────────────────────▶│                         │
│  Client App /   │   e.g., patient.identifier=123   │  MHD Document Responder │
│  Web Portal     │◀─────────────────────────────────┤   (FHIR R4 Facade)      │
│                 │      Returns DocumentRefs        │                         │
└────────┬────────┘                                  └────────────┬────────────┘
         │                                                        │
         │ (2) Read Manifest                                      │ (1.1) Query PACS
         │     (ITI-68) Binary                                    │       via C-FIND (DIMSE)
         │     or FHIR Bundle                                     ▼
         ├───────────────────────────────────────────▶┌─────────────────────────┐
         │                                            │       DICOM PACS        │
         │◀───────────────────────────────────────────┤   (e.g., dcm4che/Orthanc│
         │     Generates DICOM KOS/MADO or            └─────────────────────────┘
         │     FHIR R5 composition on-the-fly
         ▼
┌──────────────────┐
│   OHIF Viewer /  │      (3) Download Frames (WADO-RS)
│ DICOM Downloader ├─────────────────────────────────────────────────┐
└──────────────────┘                                                 ▼
                                                      ┌─────────────────────────┐
                                                      │  WADO-RS Media Endpoint │
                                                      └─────────────────────────┘
```

---

## ⚙️ Resource ID Generation Mechanics

To avoid maintaining local synchronized databases, study IDs are entirely stateless and generated from the **DICOM Study Instance UID** using **URL-Safe Base64 encoding** (with padding removed, and character `_` replaced by `.`).

* **DICOM Study Instance UID:** `1.3.6.1.4.1.5962.1.2.80.1166562673.14401`
* **FHIR ID (stable):** `MS4zLjYuMS40LjEuNTk2Mi4xLjIuODAuMTE2NjU2MjY3My4xNDQwMQ`

Endpoints prepend standard type-prefixes to ensure unique namespace mapping:
* **FHIR Imaging Manifest:** `mado-MS4zLjYu...`
* **DICOM KOS Manifest:** `kos-MS4zLjYu...`
* **SubmissionSet (List):** `ss-MS4zLjYu...`

---

## 🗺️ Mapping & Profiles Support

The server adheres to the official HL7 and IHE profiles:
1. **`MadoFhirDocumentReference`** (`https://profiles.ihe.net/RAD/MADO/StructureDefinition/MadoFhirDocumentReference`): Represents the manifest in native FHIR JSON.
2. **`MadoDicomKosDocumentReference`** (`https://profiles.ihe.net/RAD/MADO/StructureDefinition/MadoDicomKosDocumentReference`): References the binary DICOM Key Object Selection (KOS) file.
3. **`IHE.MHD.Minimal.SubmissionSet`** (`https://profiles.ihe.net/ITI/MHD/StructureDefinition/IHE.MHD.Minimal.SubmissionSet`): Wraps both manifests in a standard transaction grouping.

---

## 📡 API Reference: Request & Response Examples

### 1. CapabilityStatement (`GET /metadata`)
Retrieve the machine-readable capability constraints, supported format types, and active endpoints.

* **Protocol & Endpoint:** `GET /fhir/metadata`
* **Headers:** `Accept: application/fhir+json`

#### cURL Request
```bash
curl -X GET "https://ihebelgium.ehealthhub.be/TheDICOMPolice/fhir/metadata" \
  -H "Accept: application/fhir+json"
```

#### Example Response Body
```json
{
  "resourceType": "CapabilityStatement",
  "id": "MHD-DocumentResponder-MADO",
  "url": "https://ihebelgium.ehealthhub.be/TheDICOMPolice/fhir/metadata",
  "version": "1.0.0",
  "name": "MHDDocumentResponderMADO",
  "title": "IHE MHD Document Responder with MADO Support",
  "status": "active",
  "experimental": false,
  "date": "2026-06-01T12:00:00Z",
  "publisher": "IHE Demo Hospital",
  "kind": "instance",
  "software": {
    "name": "DICOM Police MHD Responder",
    "version": "1.0.0"
  },
  "implementation": {
    "description": "IHE MHD Document Responder MADO Facade",
    "url": "https://ihebelgium.ehealthhub.be/TheDICOMPolice/fhir"
  },
  "fhirVersion": "4.0.1",
  "format": [
    "application/fhir+json",
    "application/fhir+xml"
  ],
  "implementationGuide": [
    "https://profiles.ihe.net/ITI/MHD/ImplementationGuide/ihe.iti.mhd",
    "https://profiles.ihe.net/RAD/MADO/ImplementationGuide/ihe.rad.mado"
  ],
  "rest": [
    {
      "mode": "server",
      "documentation": "MADO Document Responder (server) extending MHD Document Responder. Supports ITI-66, ITI-67, and ITI-68 transactions.",
      "security": {
        "cors": true,
        "description": "TLS 1.2+ recommended. CORS is fully configured."
      },
      "resource": [
        {
          "type": "DocumentReference",
          "supportedProfile": [
            "https://profiles.ihe.net/RAD/MADO/StructureDefinition/MadoFhirDocumentReference",
            "https://profiles.ihe.net/RAD/MADO/StructureDefinition/MadoDicomKosDocumentReference"
          ],
          "interaction": [
            { "code": "read" },
            { "code": "search-type" }
          ],
          "searchParam": [
            { "name": "patient", "type": "reference" },
            { "name": "patient.identifier", "type": "token" },
            { "name": "status", "type": "token" },
            { "name": "date", "type": "date" },
            { "name": "study-instance-uid", "type": "token" },
            { "name": "accession-number", "type": "token" },
            { "name": "modality", "type": "token" }
          ]
        },
        {
          "type": "List",
          "supportedProfile": [
            "https://profiles.ihe.net/ITI/MHD/StructureDefinition/IHE.MHD.Minimal.SubmissionSet"
          ],
          "interaction": [
            { "code": "read" },
            { "code": "search-type" }
          ]
        },
        {
          "type": "Binary",
          "interaction": [
            { "code": "read" }
          ]
        }
      ]
    }
  ]
}
```

---

### 2. Search Document References (`GET /DocumentReference`) [ITI-67]
Queries the backend PACS using C-FIND on-the-fly and returns a searchset `Bundle` containing **paired** resources (one high-fidelity FHIR JSON `mado-` document reference and one legacy-compatible binary PACS `kos-` document reference).

* **Protocol & Endpoint:** `GET /fhir/DocumentReference`
* **Query Parameters:**
  * `patient.identifier` (Required: SSN, patient ID, etc.)
  * `modality` (Optional: CT, MR, DX, etc.)
  * `date` (Optional: e.g. `ge2010-01-01`)
  * `accession-number` (Optional: matches PACS Accession)
* **Headers:** `Accept: application/fhir+json`

#### cURL Request
```bash
curl -X GET "https://ihebelgium.ehealthhub.be/TheDICOMPolice/fhir/DocumentReference?patient.identifier=HOSPITAL_A|12345&modality=CT" \
  -H "Accept: application/fhir+json"
```

#### Example Response Body
```json
{
  "resourceType": "Bundle",
  "id": "8ec5dc46-4a4b-4b14-87cf-45e3f4e19515",
  "type": "searchset",
  "total": 2,
  "entry": [
    {
      "fullUrl": "https://ihebelgium.ehealthhub.be/TheDICOMPolice/fhir/DocumentReference/mado-MS4zLjYuMS40LjEuNTk2Mi4xLjIuODAuMTE2NjU2MjY3My4xNDQwMQ",
      "resource": {
        "resourceType": "DocumentReference",
        "id": "mado-MS4zLjYuMS40LjEuNTk2Mi4xLjIuODAuMTE2NjU2MjY3My4xNDQwMQ",
        "meta": {
          "profile": [
            "https://profiles.ihe.net/RAD/MADO/StructureDefinition/MadoFhirDocumentReference"
          ]
        },
        "extension": [
          {
            "url": "http://hl7.org/fhir/5.0/StructureDefinition/extension-DocumentReference.modality",
            "valueCodeableConcept": {
              "coding": [
                {
                  "system": "http://dicom.nema.org/resources/ontology/DCM",
                  "code": "CT",
                  "display": "Computed Tomography"
                }
              ]
            }
          }
        ],
        "status": "current",
        "type": {
          "coding": [
            {
              "system": "http://loinc.org",
              "code": "18748-4",
              "display": "Diagnostic imaging Study"
            }
          ]
        },
        "subject": {
          "identifier": {
            "system": "urn:oid:1.2.840.113619.6.197",
            "value": "12345"
          },
          "display": "PEETERS Jonas"
        },
        "date": "2006-12-19T10:11:13Z",
        "content": [
          {
            "attachment": {
              "contentType": "application/fhir+json",
              "url": "https://ihebelgium.ehealthhub.be/TheDICOMPolice/fhir/Bundle/MS4zLjYuMS40LjEuNTk2Mi4xLjIuODAuMTE2NjU2MjY3My4xNDQwMQ",
              "title": "FHIR Imaging Manifest"
            },
            "format": {
              "system": "urn:oid:1.3.6.1.4.1.19376.1.2.3",
              "code": "urn:ihe:rad:MADO:fhir-manifest:2026",
              "display": "MADO FHIR Manifest"
            }
          }
        ]
      }
    },
    {
      "fullUrl": "https://ihebelgium.ehealthhub.be/TheDICOMPolice/fhir/DocumentReference/kos-MS4zLjYuMS40LjEuNTk2Mi4xLjIuODAuMTE2NjU2MjY3My4xNDQwMQ",
      "resource": {
        "resourceType": "DocumentReference",
        "id": "kos-MS4zLjYuMS40LjEuNTk2Mi4xLjIuODAuMTE2NjU2MjY3My4xNDQwMQ",
        "meta": {
          "profile": [
            "https://profiles.ihe.net/RAD/MADO/StructureDefinition/MadoDicomKosDocumentReference"
          ]
        },
        "extension": [
          {
            "url": "http://hl7.org/fhir/5.0/StructureDefinition/extension-DocumentReference.modality",
            "valueCodeableConcept": {
              "coding": [
                {
                  "system": "http://dicom.nema.org/resources/ontology/DCM",
                  "code": "CT",
                  "display": "Computed Tomography"
                }
              ]
            }
          }
        ],
        "status": "current",
        "type": {
          "coding": [
            {
              "system": "http://loinc.org",
              "code": "18748-4"
            }
          ]
        },
        "subject": {
          "identifier": {
            "system": "urn:oid:1.2.840.113619.6.197",
            "value": "12345"
          }
        },
        "date": "2006-12-19T10:11:13Z",
        "content": [
          {
            "attachment": {
              "contentType": "application/dicom",
              "url": "https://ihebelgium.ehealthhub.be/TheDICOMPolice/fhir/Binary/MS4zLjYuMS40LjEuNTk2Mi4xLjIuODAuMTE2NjU2MjY3My4xNDQwMQ.dcm",
              "title": "DICOM KOS Imaging Manifest"
            },
            "format": {
              "system": "http://dicom.nema.org/resources/ontology/DCMUID",
              "code": "1.2.840.10008.5.1.4.1.1.88.59",
              "display": "Key Object Selection Document"
            }
          }
        ]
      }
    }
  ]
}
```

---

### 3. Read Single Document Reference (`GET /DocumentReference/{id}`) [ITI-67]
Fetches a single referenced document metadata record. This generates the manifest on-the-fly and includes the strict cryptographic hash and sizes for downloads.

* **Protocol & Endpoint:** `GET /fhir/DocumentReference/{id}`
* **Headers:** `Accept: application/fhir+json`

#### cURL Request
```bash
curl -X GET "https://ihebelgium.ehealthhub.be/TheDICOMPolice/fhir/DocumentReference/mado-MS4zLjYuMS40LjEuNTk2Mi4xLjIuODAuMTE2NjU2MjY3My4xNDQwMQ" \
  -H "Accept: application/fhir+json"
```

---

### 4. Find Document Lists / SubmissionSets (`GET /List`) [ITI-66]
Groups the generated documents into logical SubmissionSets per patient file.

* **Protocol & Endpoint:** `GET /fhir/List`
* **Query Parameters:** `patient.identifier=12345`, `status=current`

#### cURL Request
```bash
curl -X GET "https://ihebelgium.ehealthhub.be/TheDICOMPolice/fhir/List?patient.identifier=12345&status=current" \
  -H "Accept: application/fhir+json"
```

#### Example Response Body
```json
{
  "resourceType": "Bundle",
  "id": "e67dbfbf-b3cc-4cc9-b7b5-77ad9eaf4882",
  "type": "searchset",
  "total": 1,
  "entry": [
    {
      "fullUrl": "https://ihebelgium.ehealthhub.be/TheDICOMPolice/fhir/List/ss-MS4zLjYuMS40LjEuNTk2Mi4xLjIuODAuMTE2NjU2MjY3My4xNDQwMQ",
      "resource": {
        "resourceType": "List",
        "id": "ss-MS4zLjYuMS40LjEuNTk2Mi4xLjIuODAuMTE2NjU2MjY3My4xNDQwMQ",
        "meta": {
          "profile": [
            "https://profiles.ihe.net/ITI/MHD/StructureDefinition/IHE.MHD.Minimal.SubmissionSet"
          ]
        },
        "identifier": [
          {
            "use": "official",
            "system": "urn:ietf:rfc:3986",
            "value": "urn:uuid:7179069d-7db0-47b8-bba9-1a76efb34335"
          }
        ],
        "status": "current",
        "mode": "working",
        "title": "SubmissionSet: CT Scan study of Brain",
        "code": {
          "coding": [
            {
              "system": "https://profiles.ihe.net/ITI/MHD/CodeSystem/MHDlistTypes",
              "code": "submissionset",
              "display": "SubmissionSet as a FHIR List"
            }
          ]
        },
        "subject": {
          "identifier": {
            "system": "urn:oid:1.2.840.113619.6.197",
            "value": "12345"
          }
        },
        "date": "2026-06-01T12:00:00Z",
        "entry": [
          {
            "item": {
              "reference": "DocumentReference/mado-MS4zLjYuMS40LjEuNTk2Mi4xLjIuODAuMTE2NjU2MjY3My4xNDQwMQ"
            }
          },
          {
            "item": {
              "reference": "DocumentReference/kos-MS4zLjYuMS40LjEuNTk2Mi4xLjIuODAuMTE2NjU2MjY3My4xNDQwMQ"
            }
          }
        ]
      }
    }
  ]
}
```

---

### 5. Retrieve DICOM KOS Manifest (`GET /Binary/{id}`) [ITI-68]
Downloads the on-the-fly created DICOM Key Object Selection Part 10 compliant binary file. This file contains complete hierarchical TID-1600 image library elements and reference indexes of SOP instances.

* **Protocol & Endpoint:** `GET /fhir/Binary/{id}.dcm` or `GET /fhir/Binary/{id}`
* **Headers:** `Accept: application/dicom`

#### cURL Request
```bash
curl -X GET "https://ihebelgium.ehealthhub.be/TheDICOMPolice/fhir/Binary/MS4zLjYuMS40LjEuNTk2Mi4xLjIuODAuMTE2NjU2MjY3My4xNDQwMQ.dcm" \
  -H "Accept: application/dicom" \
  --output my_manifest.dcm
```

*Response headers configured:*
* `Content-Type: application/dicom`
* `Content-Disposition: attachment; filename="mado_1.3.6.1.4.1.5962.1.2.80.1166562673.14401.dcm"`

---

### 6. Retrieve converted FHIR R5/R4 Document Bundle (`GET /Bundle/{id}`)
Fetches the full study structure mapped cleanly out of the PACS DICOM tag metadata into an R5 compatible, hierarchically aligned clinical `Composition` bundle.

* **Protocol & Endpoint:** `GET /fhir/Bundle/{id}`
* **Headers:** `Accept: application/fhir+json`

#### cURL Request
```bash
curl -X GET "https://ihebelgium.ehealthhub.be/TheDICOMPolice/fhir/Bundle/MS4zLjYuMS40LjEuNTk2Mi4xLjIuODAuMTE2NjU2MjY3My4xNDQwMQ" \
  -H "Accept: application/fhir+json"
```

---

## 💻 App Development Code Examples

### 1. Browser Application Quickstart (JavaScript - ESM)
Search for a patient's imaging manifests and parse them dynamically to display action items for launch or download.

```javascript
async function searchPatientImaging(patientId, targetContainerId) {
  const fhirBase = "https://ihebelgium.ehealthhub.be/TheDICOMPolice/fhir";
  const url = `${fhirBase}/DocumentReference?patient.identifier=${encodeURIComponent(patientId)}`;
  
  try {
    const response = await fetch(url, {
      method: 'GET',
      headers: { 'Accept': 'application/fhir+json' }
    });
    
    if (!response.ok) throw new Error(`HTTP Error: ${response.status}`);
    const bundle = await response.json();
    
    const targetDiv = document.getElementById(targetContainerId);
    targetDiv.innerHTML = ""; // Clear loader
    
    if (!bundle.entry || bundle.entry.length === 0) {
      targetDiv.innerHTML = "<p>No imaging studies found for this patient.</p>";
      return;
    }

    // Process paired entries
    const entries = bundle.entry.map(e => e.resource);
    
    // Group references by shared study identifier
    const studyGroupMap = {};
    for (const doc of entries) {
      const isKos = doc.id.startsWith("kos-");
      const baseId = doc.id.replace(/^(kos|mado)-/, "");
      
      if (!studyGroupMap[baseId]) {
        studyGroupMap[baseId] = { id: baseId, date: doc.date, desc: doc.description || "Imaging Study" };
      }
      
      const contentUrl = doc.content[0].attachment.url;
      if (isKos) {
        studyGroupMap[baseId].dicomManifestUrl = contentUrl;
      } else {
        studyGroupMap[baseId].fhirManifestUrl = contentUrl;
      }
    }

    // Render list of studies with options
    Object.values(studyGroupMap).forEach(study => {
      const studyEl = document.createElement("div");
      studyEl.className = "study-card";
      studyEl.innerHTML = `
        <h4>${study.desc}</h4>
        <p><strong>Date:</strong> ${new Date(study.date).toLocaleDateString()}</p>
        <div class="actions">
          <a href="https://ihebelgium.ehealthhub.be/ohif/mado?manifestUrl=${encodeURIComponent(study.dicomManifestUrl)}" target="_blank" class="btn btn-primary">
            🖥️ Launch in OHIF Viewer
          </a>
          <a href="${study.dicomManifestUrl}" class="btn btn-secondary">
            🩻 Download KOS (.dcm)
          </a>
          <a href="${study.fhirManifestUrl}" target="_blank" class="btn btn-info">
            🔥 Inspect FHIR Manifest
          </a>
        </div>
      `;
      targetDiv.appendChild(studyEl);
    });

  } catch (error) {
    console.error("Failed to query MHD service:", error);
    document.getElementById(targetContainerId).innerHTML = `<div class="error">Error loading studies: ${error.message}</div>`;
  }
}
```

---

### 2. Batch Script / Server Integration (Python - Requests)
Programmatically scrape PACS studies based on accession number filters, then automate download pipelines.

```python
import requests
import base64

FHIR_BASE = "https://ihebelgium.ehealthhub.be/TheDICOMPolice/fhir"

def download_manifest_by_accession(accession_num, output_filename="manifest.dcm"):
    # 1. Search ITI-67 endpoint
    search_url = f"{FHIR_BASE}/DocumentReference"
    params = {"accession-number": accession_num}
    
    response = requests.get(search_url, params=params, headers={"Accept": "application/fhir+json"})
    response.raise_for_status()
    bundle = response.json()
    
    if "entry" not in bundle or len(bundle["entry"]) == 0:
        print(f"No records found for Accession: {accession_num}")
        return False
        
    # 2. Extract the DICOM KOS binary download URL
    binary_url = None
    for entry in bundle["entry"]:
        resource = entry["resource"]
        if resource["id"].startswith("kos-"):
            binary_url = resource["content"][0]["attachment"]["url"]
            break
            
    if not binary_url:
        print("MADO service has no KOS attachment registered for this PACS study.")
        return False
        
    # 3. Stream binary file
    print(f"Loading MADO DICOM Manifest from: {binary_url}")
    bin_response = requests.get(binary_url, headers={"Accept": "application/dicom"}, stream=True)
    bin_response.raise_for_status()
    
    with open(output_filename, "wb") as fd:
        for chunk in bin_response.iter_content(chunk_size=8192):
            fd.write(chunk)
            
    print(f"✅ Download complete! Document saved to {output_filename}")
    return True

# Example invocation
# download_manifest_by_accession("ACC1707204481", "case_study.dcm")
```

---

### 3. Programmatic Java client (HAPI FHIR R4 Client SDK)
Queries active DocumentReferences using strongly-typed model parameters.

```java
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DocumentReference;

public class MhdIntegrationClient {
    public static void main(String[] args) {
        FhirContext ctx = FhirContext.forR4();
        IGenericClient client = ctx.newRestfulGenericClient("https://ihebelgium.ehealthhub.be/TheDICOMPolice/fhir");

        // Execute ITI-67 Search
        Bundle results = client.search()
                .forResource(DocumentReference.class)
                .where(DocumentReference.PATIENT.hasId("12345"))
                .and(DocumentReference.STATUS.exactly().code("current"))
                .returnBundle(Bundle.class)
                .execute();

        System.out.println("Discovered " + results.getTotal() + " entries.");
        for (Bundle.BundleEntryComponent entry : results.getEntry()) {
            DocumentReference doc = (DocumentReference) entry.getResource();
            System.out.println("ID: " + doc.getId() + " | Format: " 
                + doc.getContentFirstRep().getFormat().getCode()
                + " | URL: " + doc.getContentFirstRep().getAttachment().getUrl());
        }
    }
}
```

---

## 🔒 Security Configuration Recommendations

The public hosted demo acts as an open facade. For hospital production:
1. **OAuth2 / IUA Authorization:** Secure queries with JSON Web Tokens (JWT) carried in `Authorization: Bearer <token>` headers.
2. **ATNA Audit Logging:** Map standard access events (queries, reads) onto explicit Syslog-based audit event messages.
3. **mTLS Encryption:** Enforce Mutual TLS between the web app backend nodes and the PACS networks.

````
<userPrompt>
Provide the fully rewritten file, incorporating the suggested code change. You must produce the complete file.
</userPrompt>
