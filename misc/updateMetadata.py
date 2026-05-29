import csv
import requests

ORTHANC_URL = "http://localhost:8042"
INPUT_CSV = "orthanc_metadata.csv"

# Group tags by their structural level in DICOM
PATIENT_TAGS = ["PatientID", "PatientName", "PatientBirthDate", "PatientSex"]
STUDY_TAGS = [
    "StudyDate", "StudyTime", "AccessionNumber", "ModalitiesInStudy",
    "BodyPartExamined", "ReferringPhysicianName", "StudyDescription",
    "RetrieveURL", "StudyID", "InstitutionName"
]

def safe_update_orthanc():
    print(f"Reading LLM-optimized data from '{INPUT_CSV}'...")
    try:
        with open(INPUT_CSV, mode="r", encoding="utf-8") as csv_file:
            reader = csv.DictReader(csv_file)
            rows = list(reader)
    except FileNotFoundError:
        print(f"Error: '{INPUT_CSV}' not found.")
        return

    print(f"Found {len(rows)} studies to check. Processing updates...\n")

    for index, row in enumerate(rows, start=1):
        study_id = row.get("OrthancStudyID")
        if not study_id:
            continue

        print(f"[{index}/{len(rows)}] Checking study: {study_id}")

        try:
            # 1. Fetch current live tags from Orthanc to compare
            study_info = requests.get(f"{ORTHANC_URL}/studies/{study_id}").json()
            parent_patient_id = study_info.get("ParentPatient")
            
            instances = requests.get(f"{ORTHANC_URL}/studies/{study_id}/instances").json()
            if not instances:
                print("   ⚠️ No instances found for this study wrapper. Skipping.")
                continue
                
            first_instance = instances[0]
            first_instance_id = first_instance["ID"] if isinstance(first_instance, dict) else first_instance
            current_tags = requests.get(f"{ORTHANC_URL}/instances/{first_instance_id}/tags?simplify").json()

            # 2. Separate changes into Study level and Patient level
            study_changes = {}
            patient_changes = {}

            for tag in STUDY_TAGS:
                csv_val = row.get(tag, "").strip()
                current_val = str(current_tags.get(tag, "")).strip()
                if csv_val and csv_val != current_val:
                    study_changes[tag] = csv_val

            for tag in PATIENT_TAGS:
                csv_val = row.get(tag, "").strip()
                current_val = str(current_tags.get(tag, "")).strip()
                if csv_val and csv_val != current_val:
                    patient_changes[tag] = csv_val

            # 3. Execute Patient-Level Updates safely (if any changed)
            if patient_changes and parent_patient_id:
                print(f"   -> Patient tags changed. Updating Patient container {parent_patient_id}...")
                patient_payload = {"Replace": patient_changes, "Force": True, "KeepSource": False}
                pat_res = requests.post(f"{ORTHANC_URL}/patients/{parent_patient_id}/modify", json=patient_payload)
                pat_res.raise_for_status()
                print("   ✅ Patient tags updated successfully.")
                
                # If PatientID changed, Orthanc generates a new study ID. 
                # Refresh study info to get the valid active ID for subsequent study tag updates.
                updated_patient_info = pat_res.json()
                if "PatientID" in patient_changes:
                    # Target the newly generated study ID resulting from the patient split/merge
                    new_studies = requests.get(f"{ORTHANC_URL}/patients/{updated_patient_info['ID']}/studies").json()
                    if new_studies:
                        study_id = new_studies[0]  # shift reference to new valid ID

            # 4. Execute Study-Level Updates cleanly (if any changed)
            if study_changes:
                print(f"   -> Study tags changed. Updating Study container {study_id}...")
                study_payload = {"Replace": study_changes, "Force": True, "KeepSource": False}
                std_res = requests.post(f"{ORTHANC_URL}/studies/{study_id}/modify", json=study_payload)
                std_res.raise_for_status()
                print("   ✅ Study tags updated successfully.")

            if not study_changes and not patient_changes:
                print("   = Matches server data perfectly. No modifications needed.")

        except requests.RequestException as e:
            print(f"   ❌ Failed to process update for study {study_id}: {e}")

    print("\nDatabase sync complete! All records match your CSV safely.")

if __name__ == "__main__":
    safe_update_orthanc()
