import csv
import json
import requests

# --- Configuration ---
ORTHANC_URL = "http://localhost:8042"
OUTPUT_CSV = "orthanc_series_metadata.csv"

# Fields that give an LLM highly diagnostic context clues to fix descriptions
TAGS_TO_EXTRACT = [
    "PatientID",
    "StudyDescription",
    "Modality",
    "SeriesNumber",
    "SeriesDescription",   # 🎯 The primary target for your LLM to clean/synthesize
    "ProtocolName",        # Critical clue: Internal machine scan name
    "BodyPartExamined",    # Standard text anatomy clue (e.g., CHEST, BRAIN)
    "SequenceName",        # Crucial hint for MRI pulse sequences (e.g., *tse2d1)
    "ScanOptions"          # Hint for CT/MR trajectory options
]

def extract_series_metadata():
    print("Connecting to Orthanc to collect all series...")
    try:
        response = requests.get(f"{ORTHANC_URL}/series")
        response.raise_for_status()
        series_ids = response.json()
    except requests.RequestException as e:
        print(f"Error: Could not connect to Orthanc: {e}")
        return

    print(f"Found {len(series_ids)} series in PACS. Compiling context tags...")

    # Define the final CSV headers layout
    headers = [
        "OrthancSeriesID", 
        "OrthancStudyID"
    ] + TAGS_TO_EXTRACT + [
        "AnatomicalRegionMeaning", 
        "AnatomicalRegionCode",
        "MADO_SnomedCode_Prediction",   # Blank column ready for LLM to fill
        "MADO_Display_Prediction"       # Blank column ready for LLM to fill
    ]

    with open(OUTPUT_CSV, mode="w", newline="", encoding="utf-8") as csv_file:
        writer = csv.DictWriter(csv_file, fieldnames=headers)
        writer.writeheader()

        for index, series_id in enumerate(series_ids, start=1):
            print(f"[{index}/{len(series_ids)}] Extracting series: {series_id}")
            
            row_data = {
                "OrthancSeriesID": series_id,
                "MADO_SnomedCode_Prediction": "",
                "MADO_Display_Prediction": ""
            }

            try:
                # 1. Fetch series structural metadata to link the parent study
                series_data = requests.get(f"{ORTHANC_URL}/series/{series_id}").json()
                row_data["OrthancStudyID"] = series_data.get("ParentStudy", "")

                # 2. Query child instances to grab the target instance
                instances_res = requests.get(f"{ORTHANC_URL}/series/{series_id}/instances")
                instances_data = instances_res.json()

                if instances_data:
                    first_instance = instances_data[0]
                    first_instance_id = first_instance["ID"] if isinstance(first_instance, dict) else first_instance
                    
                    # 3. Pull simplified DICOM dictionary tags from the instance
                    tags_data = requests.get(f"{ORTHANC_URL}/instances/{first_instance_id}/tags?simplify").json()
                    
                    # Extract target fields
                    for tag in TAGS_TO_EXTRACT:
                        row_data[tag] = str(tags_data.get(tag, "")).strip()

                    # 4. Safely unpack AnatomicalRegionSequence if the scanner populated it
                    anon_seq = tags_data.get("AnatomicalRegionSequence", "")
                    if isinstance(anon_seq, list) and len(anon_seq) > 0:
                        row_data["AnatomicalRegionMeaning"] = anon_seq[0].get("CodeMeaning", "")
                        row_data["AnatomicalRegionCode"] = anon_seq[0].get("CodeValue", "")
                    else:
                        row_data["AnatomicalRegionMeaning"] = ""
                        row_data["AnatomicalRegionCode"] = ""
                else:
                    # Fill blanks if the series container is empty
                    for tag in TAGS_TO_EXTRACT:
                        row_data[tag] = ""
                    row_data["AnatomicalRegionMeaning"] = ""
                    row_data["AnatomicalRegionCode"] = ""

                writer.writerow(row_data)

            except Exception as e:
                print(f"   ⚠️ Error parsing series {series_id}: {e}")

    print(f"\nExtraction complete! Data saved to '{OUTPUT_CSV}'.")

if __name__ == "__main__":
    extract_series_metadata()
