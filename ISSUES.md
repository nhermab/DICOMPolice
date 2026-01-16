# Known Issues and Limitations

This document records the current, high-priority project limitations and reproducible bugs.

---

## MADO Profile - Public Comment Status

⚠️ **Impact: High** | **Status: Public Comment (Draft)**

The MADO (Manifest-based Access to DICOM Objects) profile used by this project is currently in **Public Comment** status within the IHE Radiology Technical Framework development process.

Implications:
- Code values using the "ddd" prefix are provisional and may change when the profile is finalized.
- Template and TID extensions are draft and may be reworked based on public feedback.
- This codebase currently implements validation rules against the draft; do not rely on it in clinical production environments.

Important implementation note:
- The project currently uses placeholder IDs for some TID1600 extensions in code. Example constants in the codebase:

```java
public static final String CODE_KOS_TITLE = "ddd061";
public static final String CODE_KOS_OBJECT_DESCRIPTION = "ddd061";

public static final String CODE_SOP_INSTANCE_UID = "ddd060";
```

These values are temporary and align with the draft/public-comment state of the profile.

---

## Active Known Issues

- Gazelle API: the Gazelle API integration does not yet work as intended. Functionality depending on Gazelle (test management/validation workflows) is incomplete.

- Validator UI — "Upload file" bug: there is a reproducible bug when clicking the "Upload file" button in the validator UI; files are not always accepted/processed as expected. Please file a GitHub issue with steps to reproduce and a browser/OS report when encountering this.

- FHIR→DICOM→FHIR→DICOM conversion issue: the conversion pipeline sometimes inserts Instance Number values in locations where none are expected. This is caused by a faulty implementation of the suggested KOS handling in the conversion logic; it needs to be corrected to follow the intended KOS specification behavior.

---

## Reporting

Please report any other issues via GitHub Issues with clear reproduction steps, the command or UI action used, and any example files (de-identified).

**Last Updated:** 2026-01-17
