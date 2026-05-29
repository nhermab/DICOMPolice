import csv
import requests

ORTHANC_URL = "http://localhost:8042"
OUTPUT_CSV = "orthanc_metadata.csv"

# The exact target tags to pull from the DICOM dataset
TAGS_TO_EXTRACT = [
    "StudyDate", "StudyTime", "AccessionNumber", "ModalitiesInStudy",
    "BodyPartExamined", "ReferringPhysicianName", "StudyDescription",
    "RetrieveURL", "PatientName", "PatientID", "PatientBirthDate",
    "PatientSex", "StudyInstanceUID", "StudyID", "InstitutionName"
]

def extract_orthanc_metadata():
    print("Connecting to Orthanc to collect studies...")
    try:
        response = requests.get(f"{ORTHANC_URL}/studies")
        response.raise_for_status()
        study_ids = response.json()
    except requests.RequestException as e:
        print(f"Error: Could not connect to Orthanc: {e}")
        return

    print(f"Found {len(study_ids)} studies. Extracting tags...")

    # Define headers for the CSV
    headers = ["OrthancStudyID"] + TAGS_TO_EXTRACT + ["NumberOfStudyRelatedSeries", "NumberOfStudyRelatedInstances"]

    with open(OUTPUT_CSV, mode="w", newline="", encoding="utf-8") as csv_file:
        writer = csv.DictWriter(csv_file, fieldnames=headers)
        writer.writeheader()

        for index, study_id in enumerate(study_ids, start=1):
            print(f"[{index}/{len(study_ids)}] Extracting study: {study_id}")
            
            row_data = {"OrthancStudyID": study_id}

            try:
                # 1. Fetch study-level structural data for Series count
                study_data = requests.get(f"{ORTHANC_URL}/studies/{study_id}").json()
                row_data["NumberOfStudyRelatedSeries"] = len(study_data.get("Series", []))

                # 2. Fetch instances from the correct sub-endpoint
                instances_data = requests.get(f"{ORTHANC_URL}/studies/{study_id}/instances").json()
                row_data["NumberOfStudyRelatedInstances"] = len(instances_data)

                if instances_data:
                    # Orthanc can return a list of IDs or a list of dictionaries depending on the version.
                    # This safely extracts the first instance ID regardless of version format.
                    first_instance = instances_data[0]
                    first_instance_id = first_instance["ID"] if isinstance(first_instance, dict) else first_instance
                    
                    # 3. Grab the tags from the instance
                    tags_data = requests.get(f"{ORTHANC_URL}/instances/{first_instance_id}/tags?simplify").json()
                    
                    # Map the requested tags directly from the DICOM simplified dictionary
                    for tag in TAGS_TO_EXTRACT:
                        row_data[tag] = tags_data.get(tag, "")
                else:
                    for tag in TAGS_TO_EXTRACT:
                        row_data[tag] = ""

                writer.writerow(row_data)

            except Exception as e:
                print(f"   ⚠️ Error processing study {study_id}: {e}")

    print(f"\nExtraction complete! Data saved to '{OUTPUT_CSV}'. Your tags will now be fully populated.")

if __name__ == "__main__":
    extract_orthanc_metadata()
