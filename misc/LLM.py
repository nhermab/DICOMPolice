import base64
import csv
import json
import logging
import os
import re
from typing import Any, Optional

import requests

# Setup logging
logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s")

# Configuration Options
FHIR_DOCUMENT_REFERENCE_URL = os.getenv(
    "FHIR_DOCUMENT_REFERENCE_URL",
    "http://ihebelgium.ehealthhub.be/TheDICOMPolice/fhir/DocumentReference?_format=json",
)

# Ollama exposes the API under /api. Keep the host configurable and build endpoint URLs from it.
OLLAMA_HOST = os.getenv("OLLAMA_HOST", "http://192.168.1.6:11434").rstrip("/")
OLLAMA_CHAT_URL = f"{OLLAMA_HOST}/api/chat"
OLLAMA_TAGS_URL = f"{OLLAMA_HOST}/api/tags"
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "qwen3.6")

# Output file requested by the user.
OUTPUT_CSV = os.getenv("OUTPUT_CSV", "LLMCLASSIFIED.csv")
CSV_COLUMNS = ["STUDYUID", "STUDYDESCRIPTION", "BODYPART"]


def query_fhir_endpoint(url: str) -> Optional[dict[str, Any]]:
    """Fetch the search bundle from the DocumentReference endpoint."""
    logging.info("Querying FHIR DocumentReference endpoint: %s", url)
    try:
        response = requests.get(url, timeout=30)
        response.raise_for_status()
        return response.json()
    except Exception as e:
        logging.error("Failed to query FHIR endpoint: %s", e)
        return None


def fetch_json_bundle(bundle_url: str) -> Optional[dict[str, Any]]:
    """Retrieve the contained JSON bundle target linked inside DocumentReference content."""
    logging.info("Fetching targeted study JSON Bundle from: %s", bundle_url)
    try:
        response = requests.get(bundle_url, timeout=15)
        response.raise_for_status()
        return response.json()
    except Exception as e:
        logging.error("Failed to fetch document bundle: %s", e)
        return None


def download_wado_rendered_jpeg(
        wado_base_url: str,
        study_uid: str,
        series_uid: str,
        instance_uid: str,
) -> Optional[bytes]:
    """Construct a DICOMweb WADO-RS Rendered URL request targeting Orthanc."""
    if not wado_base_url.endswith("/"):
        wado_base_url += "/"

    wado_rendered_url = (
        f"{wado_base_url}studies/{study_uid}/series/{series_uid}/instances/{instance_uid}/rendered"
    )
    logging.info("Downloading rendered JPEG via WADO-RS: %s", wado_rendered_url)

    try:
        headers = {"Accept": "image/jpeg"}
        response = requests.get(wado_rendered_url, headers=headers, timeout=20)
        response.raise_for_status()
        return response.content
    except Exception as e:
        logging.error("Failed to retrieve image via WADO endpoint: %s", e)
        return None


def verify_ollama_model(model: str = OLLAMA_MODEL) -> None:
    """Warn early if the requested model is not listed by the local Ollama server."""
    try:
        response = requests.get(OLLAMA_TAGS_URL, timeout=10)
        response.raise_for_status()
        models = response.json().get("models", [])
        available_names = {m.get("name") for m in models}
        base_names = {name.split(":", 1)[0] for name in available_names if name}

        if model not in available_names and model.split(":", 1)[0] not in base_names:
            logging.warning(
                "Ollama model %r was not found in /api/tags. Pull it first with: ollama pull %s",
                model,
                model,
            )
    except Exception as e:
        logging.warning("Could not verify Ollama model list at %s: %s", OLLAMA_TAGS_URL, e)


def build_classification_prompt(modality: str, fallback_desc: str) -> str:
    """Build the prompt that forces stable CSV-ready JSON output."""
    return f"""
You are classifying one rendered frame from a medical imaging study for CSV metadata.

Inputs:
- Diagnostic modality: {modality}
- Existing baseline study description / indication text: {fallback_desc}

Goal:
Return the most accurate possible metadata classification using the rendered image first, then the baseline text, then modality-specific defaults only if the image is blank, irrelevant, non-anatomic, over/under-exposed, or otherwise not interpretable.

Rules:
1. Do not invent patient identifiers, dates, accession numbers, hospitals, or exact scanner model names.
2. Do not provide a diagnosis, treatment recommendation, or clinical certainty about pathology.
3. STUDYDESCRIPTION must be a rich technical imaging description, not a diagnosis. Include modality, likely acquisition/projection/plane, visible anatomy or inferred imaging target, contrast/windowing or signal characteristics when applicable, quality/artifact comments, and metadata-like imaging context.
4. BODYPART must be your best single body-part/region label in uppercase. Prefer DICOM-style terms such as HEAD, BRAIN, CHEST, ABDOMEN, PELVIS, SPINE, CERVICAL SPINE, LUMBAR SPINE, KNEE, SHOULDER, HAND, FOOT, BREAST, HEART, VASCULAR, WHOLE BODY, or UNKNOWN only if nothing reasonable can be inferred.
5. If the image shows little or nothing, make a plausible rich metadata-style STUDYDESCRIPTION based on the modality and baseline text. In that case, explicitly include the phrase "inferred fallback due to limited visible content" inside STUDYDESCRIPTION.
6. Be conservative and accurate when the image contains usable anatomy. Only use fallback invention when the image genuinely lacks usable visual information.

Return valid JSON only, with exactly these keys:
{{
  "STUDYDESCRIPTION": "...",
  "BODYPART": "..."
}}
""".strip()


def extract_json_object(text: str) -> dict[str, Any]:
    """Parse a JSON object from a model response, tolerating code fences or extra text."""
    cleaned = text.strip()

    # Remove common Markdown code fences if present.
    cleaned = re.sub(r"^```(?:json)?\s*", "", cleaned, flags=re.IGNORECASE)
    cleaned = re.sub(r"\s*```$", "", cleaned)

    try:
        parsed = json.loads(cleaned)
        if isinstance(parsed, dict):
            return parsed
    except json.JSONDecodeError:
        pass

    # Try to recover the first {...} block.
    match = re.search(r"\{.*\}", cleaned, flags=re.DOTALL)
    if match:
        parsed = json.loads(match.group(0))
        if isinstance(parsed, dict):
            return parsed

    raise ValueError("Could not parse a JSON object from Ollama response.")


def modality_fallback_bodypart(modality: str, fallback_desc: str) -> str:
    """Deterministic body-part fallback used only if model output is invalid or no image was available."""
    text = f"{modality} {fallback_desc}".upper()

    keyword_map = [
        ("CERVICAL", "CERVICAL SPINE"),
        ("LUMBAR", "LUMBAR SPINE"),
        ("THORACIC", "THORACIC SPINE"),
        ("SPINE", "SPINE"),
        ("BRAIN", "BRAIN"),
        ("HEAD", "HEAD"),
        ("SKULL", "HEAD"),
        ("CHEST", "CHEST"),
        ("LUNG", "CHEST"),
        ("THORAX", "CHEST"),
        ("ABDOMEN", "ABDOMEN"),
        ("PELVIS", "PELVIS"),
        ("KNEE", "KNEE"),
        ("SHOULDER", "SHOULDER"),
        ("HAND", "HAND"),
        ("WRIST", "WRIST"),
        ("FOOT", "FOOT"),
        ("ANKLE", "ANKLE"),
        ("BREAST", "BREAST"),
        ("MAMMO", "BREAST"),
        ("CARDIAC", "HEART"),
        ("HEART", "HEART"),
        ("VASCULAR", "VASCULAR"),
        ("ANGIO", "VASCULAR"),
        ("WHOLE BODY", "WHOLE BODY"),
        ("WHOLEBODY", "WHOLE BODY"),
    ]
    for keyword, bodypart in keyword_map:
        if keyword in text:
            return bodypart

    modality_upper = modality.upper()
    if modality_upper in {"MG", "MAMMO"}:
        return "BREAST"
    if modality_upper in {"US", "SR"}:
        return "ABDOMEN"
    if modality_upper in {"NM", "PT", "PET"}:
        return "WHOLE BODY"
    if modality_upper in {"CR", "DX", "DR"}:
        return "CHEST"
    if modality_upper in {"CT", "MR", "MRI"}:
        return "HEAD"
    return "UNKNOWN"


def fallback_classification(modality: str, fallback_desc: str, reason: str) -> dict[str, str]:
    """Create a CSV-safe fallback classification when model/image output cannot be used."""
    bodypart = modality_fallback_bodypart(modality, fallback_desc)
    description = (
        f"{modality or 'Unknown modality'} study metadata summary; inferred fallback due to limited visible content. "
        f"Likely imaging target/body region: {bodypart}. "
        f"Baseline context: {fallback_desc or 'No baseline description available'}. "
        f"Technical description is modality-derived and should be reviewed against source DICOM metadata. "
        f"Fallback reason: {reason}."
    )
    return {"STUDYDESCRIPTION": description, "BODYPART": bodypart}


def classify_with_local_ollama(
        image_bytes: bytes,
        modality: str,
        fallback_desc: str,
) -> dict[str, str]:
    """Send a base64 encoded image frame to Ollama and return CSV-ready metadata fields."""
    logging.info("Prompting Ollama model %r at %s...", OLLAMA_MODEL, OLLAMA_CHAT_URL)
    base64_image = base64.b64encode(image_bytes).decode("utf-8")
    prompt = build_classification_prompt(modality=modality, fallback_desc=fallback_desc)

    payload = {
        "model": OLLAMA_MODEL,
        "messages": [
            {
                "role": "system",
                "content": (
                    "You are a medical-imaging metadata classifier. "
                    "You output strict JSON only and classify study-level technical descriptions and body region."
                ),
            },
            {
                "role": "user",
                "content": prompt,
                "images": [base64_image],
            },
        ],
        "stream": False,
        "think": False,
        "format": "json",
        "options": {
            "temperature": 0.1,
        },
    }

    try:
        response = requests.post(OLLAMA_CHAT_URL, json=payload, timeout=180)
        response.raise_for_status()
        result_json = response.json()

        message = result_json.get("message", {})
        content = message.get("content") or result_json.get("response")
        if not content:
            return fallback_classification(modality, fallback_desc, "empty Ollama response")

        parsed = extract_json_object(str(content))
        study_description = str(parsed.get("STUDYDESCRIPTION", "")).strip()
        bodypart = str(parsed.get("BODYPART", "")).strip().upper()

        if not study_description:
            study_description = fallback_classification(
                modality, fallback_desc, "missing STUDYDESCRIPTION in Ollama response"
            )["STUDYDESCRIPTION"]

        if not bodypart:
            bodypart = modality_fallback_bodypart(modality, fallback_desc)

        return {
            "STUDYDESCRIPTION": study_description,
            "BODYPART": bodypart,
        }
    except requests.HTTPError as e:
        body = e.response.text if e.response is not None else ""
        logging.error("Ollama HTTP error: %s | Response body: %s", e, body[:1000])
        return fallback_classification(modality, fallback_desc, f"Ollama HTTP error: {e}")
    except Exception as e:
        logging.error("Ollama integration/parsing error: %s", e)
        return fallback_classification(modality, fallback_desc, f"Ollama integration/parsing error: {e}")


def extract_modality_code(resource: dict[str, Any]) -> str:
    """Extract modality code from DocumentReference extensions when present."""
    modality_code = "Unknown"
    for ext in resource.get("extension", []):
        if "modality" in ext.get("url", ""):
            coding = ext.get("valueCodeableConcept", {}).get("coding", [])
            if coding:
                return coding[0].get("code", modality_code)
    return modality_code


def extract_study_uid(imaging_study: dict[str, Any]) -> Optional[str]:
    """Extract DICOM StudyInstanceUID from ImagingStudy.identifier."""
    for ident in imaging_study.get("identifier", []):
        system = ident.get("system", "")
        value = ident.get("value", "")
        if "urn:dicom:uid" in system and value.startswith("urn:oid:"):
            return value.replace("urn:oid:", "", 1)
    return None


def write_csv_row(csv_writer: csv.DictWriter, csv_file, study_uid: str, classification: dict[str, str]) -> None:
    """Write and flush one output row so progress is retained during long runs."""
    csv_writer.writerow(
        {
            "STUDYUID": study_uid,
            "STUDYDESCRIPTION": classification.get("STUDYDESCRIPTION", ""),
            "BODYPART": classification.get("BODYPART", "UNKNOWN"),
        }
    )
    csv_file.flush()


def process_dicom_studies() -> None:
    verify_ollama_model()

    searchset_bundle = query_fhir_endpoint(FHIR_DOCUMENT_REFERENCE_URL)
    if not searchset_bundle or "entry" not in searchset_bundle:
        logging.error("No entries found to parse.")
        return

    rows_written = 0
    processed_study_uids: set[str] = set()

    with open(OUTPUT_CSV, "w", newline="", encoding="utf-8") as csv_file:
        csv_writer = csv.DictWriter(csv_file, fieldnames=CSV_COLUMNS)
        csv_writer.writeheader()

        for entry in searchset_bundle["entry"]:
            resource = entry.get("resource", {})
            if resource.get("resourceType") != "DocumentReference":
                continue

            res_id = resource.get("id", "")

            # Filter out KOS manifests to ensure we only spend compute on raw imaging resources.
            if res_id.startswith("kos-"):
                logging.info("Skipping KOS Structural Manifest DocumentReference ID: %s", res_id)
                continue

            logging.info("Processing Image DocumentReference ID: %s", res_id)
            modality_code = extract_modality_code(resource)

            for content_item in resource.get("content", []):
                attachment = content_item.get("attachment", {})
                content_type = attachment.get("contentType", "")
                attachment_url = attachment.get("url", "")

                if "json" not in content_type or not attachment_url:
                    continue

                study_bundle = fetch_json_bundle(attachment_url)
                if not study_bundle:
                    continue

                entries = study_bundle.get("entry", [])

                wado_endpoint_address = next(
                    (
                        inner_entry.get("resource", {}).get("address")
                        for inner_entry in entries
                        if inner_entry.get("resource", {}).get("resourceType") == "Endpoint"
                    ),
                    None,
                )

                imaging_studies = [
                    inner_entry.get("resource", {})
                    for inner_entry in entries
                    if inner_entry.get("resource", {}).get("resourceType") == "ImagingStudy"
                ]

                for imaging_study in imaging_studies:
                    study_description = imaging_study.get("description", "No baseline description")
                    study_uid = extract_study_uid(imaging_study)
                    series_list = imaging_study.get("series", [])

                    if not study_uid:
                        logging.warning("Skipping ImagingStudy because Study UID is missing.")
                        continue

                    if study_uid in processed_study_uids:
                        logging.info("Skipping duplicate Study UID already written: %s", study_uid)
                        continue

                    if not series_list or not wado_endpoint_address:
                        logging.warning(
                            "No series or WADO endpoint for Study UID %s; writing deterministic fallback row.",
                            study_uid,
                        )
                        classification = fallback_classification(
                            modality_code,
                            study_description,
                            "missing series or WADO endpoint",
                        )
                        write_csv_row(csv_writer, csv_file, study_uid, classification)
                        processed_study_uids.add(study_uid)
                        rows_written += 1
                        continue

                    target_series = series_list[0]
                    series_uid = target_series.get("uid")
                    instances = target_series.get("instance", [])
                    instance_uid = instances[0].get("uid") if instances else None

                    if not series_uid or not instance_uid:
                        logging.warning(
                            "No Series UID or Instance UID for Study UID %s; writing deterministic fallback row.",
                            study_uid,
                        )
                        classification = fallback_classification(
                            modality_code,
                            study_description,
                            "missing Series UID or Instance UID",
                        )
                        write_csv_row(csv_writer, csv_file, study_uid, classification)
                        processed_study_uids.add(study_uid)
                        rows_written += 1
                        continue

                    image_data = download_wado_rendered_jpeg(
                        wado_base_url=wado_endpoint_address,
                        study_uid=study_uid,
                        series_uid=series_uid,
                        instance_uid=instance_uid,
                    )

                    if image_data:
                        classification = classify_with_local_ollama(
                            image_bytes=image_data,
                            modality=modality_code,
                            fallback_desc=study_description,
                        )
                    else:
                        classification = fallback_classification(
                            modality_code,
                            study_description,
                            "could not download rendered WADO image",
                        )

                    write_csv_row(csv_writer, csv_file, study_uid, classification)
                    processed_study_uids.add(study_uid)
                    rows_written += 1

                    logging.info(
                        "Wrote CSV row for Study UID %s with BODYPART=%s",
                        study_uid,
                        classification.get("BODYPART", "UNKNOWN"),
                    )

    logging.info("Done. Wrote %d row(s) to %s", rows_written, OUTPUT_CSV)


if __name__ == "__main__":
    process_dicom_studies()
