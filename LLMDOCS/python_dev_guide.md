# Developer Guide: Building Python Desktop Apps & Scripts using MHD, MADO, and WADO-RS

This guide demonstrates how Python developers (building desktop GUIs with PySide/PyQt, clinical integration scripts, or AI/ML pipelines) can query MHD registries, retrieve MADO manifests, and stream raw DICOM files in parallel using REST services.

## Architecture Benefits
- **No pynetdicom dependency**: Communication is powered by standard REST libraries (`requests` or `httpx`).
- **No QIDO / DIMSE C-MOVE**: Search endpoints are REST FHIR APIs, and data downloading is lightweight HTTP GET chunking.
- **Ready for AI pipelines**: Allows fast, parallel file downloads straight into numpy/pydicom buffers without creating temporary stores or legacy associations.

---

## 1. Python Core Implementation

This utility utilizes the `requests` library and Python's standard `concurrent.futures` to fetch DICOM objects in parallel. It includes custom decapsulation to unwrap standard DICOM WADO-RS multipart payloads.

```python
import os
import re
import logging
from concurrent.futures import ThreadPoolExecutor, as_completed
import requests

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger(__name__)

class DicomPoliceClient:
    def __init__(self, fhir_base_url: str, wado_base_url: str, max_workers: int = 5):
        self.fhir_base = fhir_base_url
        self.wado_base = wado_base_url
        self.max_workers = max_workers
        self.session = requests.Session()

    def search_mhd_documents(self, search_params: dict) -> dict:
        """
        1. Search MHD DocumentRegistry
        Queries the FHIR server for DocumentReferences mapping to search parameters.
        """
        url = f"{self.fhir_base}/DocumentReference"
        headers = {
            "Accept": "application/fhir+json"
        }
        
        resp = self.session.get(url, params=search_params, headers=headers)
        resp.raise_for_status()
        return resp.json()

    def fetch_mado_manifest(self, binary_url: str) -> dict:
        """
        2. Fetch the MADO Clinical Manifest JSON file
        Processes the raw JSON attachment containing detailed series layouts.
        """
        # Parse relative vs absolute URLs
        url = binary_url
        if not binary_url.startswith("http"):
            url = f"{self.fhir_base}/{binary_url.lstrip('./')}"

        headers = {
            "Accept": "application/fhir+json, application/json"
        }
        resp = self.session.get(url, headers=headers)
        resp.raise_for_status()
        return resp.json()

    def download_instance_wado(self, study_uid: str, series_uid: str, instance_uid: str) -> bytes:
        """
        3. Retrieve raw DICOM from WADO-RS
        Fetches an instance and strips the multipart wrap.
        """
        url = f"{self.wado_base}/studies/{study_uid}/series/{series_uid}/instances/{instance_uid}"
        headers = {
            "Accept": 'multipart/related; type="application/dicom"'
        }

        resp = self.session.get(url, headers=headers)
        resp.raise_for_status()

        content_type = resp.headers.get("content-type", "")
        body = resp.content

        if "multipart" in content_type:
            return self._strip_multipart(body, content_type)
        return body

    def _strip_multipart(self, body: bytes, content_type: str) -> bytes:
        """
        Strips MIME headers and boundaries from multipart responses to extract raw DICOM data.
        """
        boundary_match = re.search(r'boundary=([^;]+)', content_type)
        if not boundary_match:
            return body

        boundary = b"--" + boundary_match.group(1).replace(b'"', b'').strip()

        # Find double CRLF (\r\n\r\n) signaling the end of the multipart headers
        header_end = body.find(b"\r\n\r\n")
        if header_end == -1:
            return body

        # Locate the terminating boundary
        payload_start = header_end + 4
        payload_end = body.find(boundary, payload_start)

        if payload_end != -1:
            # Exclude the trailing \r\n preceding the boundary
            return body[payload_start:payload_end - 2]
        
        return body[payload_start:]

    def download_study(self, mado_manifest_json: dict, dest_dir: str):
        """
        Traverses a MADO manifest to download all specified instances concurrently.
        """
        os.makedirs(dest_dir, exist_ok=True)
        jobs = []

        # Parse fhir resources inside MADO manifest Bundle
        for entry in mado_manifest_json.get("entry", []):
            resource = entry.get("resource", {})
            if resource.get("resourceType") == "ImagingStudy":
                study_uid = resource.get("uid")
                for series in resource.get("series", []):
                    series_uid = series.get("uid")
                    for instance in series.get("instance", []):
                        jobs.append({
                            "study_uid": study_uid,
                            "series_uid": series_uid,
                            "instance_uid": instance.get("uid")
                        })

        logger.info(f"Loaded {len(jobs)} download jobs from MADO manifest. Commencing...")

        with ThreadPoolExecutor(max_workers=self.max_workers) as executor:
            futures = {
                executor.submit(
                    self.download_instance_wado, 
                    job["study_uid"], 
                    job["series_uid"], 
                    job["instance_uid"]
                ): job for job in jobs
            }

            for future in as_completed(futures):
                job = futures[future]
                try:
                    dicom_data = future.result()
                    file_path = os.path.join(dest_dir, f"{job['instance_uid']}.dcm")
                    with open(file_path, "wb") as f:
                        f.write(dicom_data)
                    logger.info(f"Successfully downloaded instance: {job['instance_uid']}")
                except Exception as e:
                    logger.error(f"Failed download for instance {job['instance_uid']}: {e}")
```

---

## 2. LLM Prompt Checklist for Python Developers

Use this specification when asking an LLM to build a full PyQt/PySide desktop UI, an AI training prefetch pipeline, or a medical image routing script:

### Copy-Paste LLM Prompt Template

```text
You are an expert Python Medical Systems Developer. I want you to write a clean, production-ready Python utility or desktop script to integrate with my RESTful medical imaging backend.

Constraints:
- DO NOT use dcm4che, pynetdicom, DIMSE services, or QIDO-RS.
- Communication with the server operates entirely via standard HTTP REST & FHIR specifications.

Write a complete, thread-safe Python application containing:
1. Search Registry: Query an MHD FHIR Directory base: `GET [fhirBaseUrl]/DocumentReference?patient=[patientID]` or search by study identifier.
2. Locate Manifest: Traverse response bundles to parse high-level schemas and locate the JSON-based MADO clinical manifest URL.
3. Fetch Manifest: Load the clinical manifest (contains a FHIR ImagingStudy) specifying Study, Series, and SOP Instance UIDs.
4. Download Instances: Fetch files in parallel using `concurrent.futures.ThreadPoolExecutor` via standard WADO-RS:
   `GET [wadoRsUrl]/studies/{studyUID}/series/{seriesUID}/instances/{sopUID}`
5. Multipart Boundary Extraction: Decode `multipart/related; type="application/dicom"` HTTP responses. Parse byte arrays, locate headers utilizing MIME boundaries, strip away multi-part metadata blocks, and write pure raw binary arrays to disk as `.dcm` files.
6. Optional: Provide a clean UI layout built using PySide6 (or PyQt5) displaying the downloaded studies list, a series tree explorer, and a preview canvas powered by PyDicom and matplotlib.

Provide clean, modular code with exception handlers, detailed logging, and usage syntax examples.
```

