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

- Gazelle API: the Gazelle API field naming mismatch (`validationProfileId` vs `validationProfileID`) has been fixed. The `ValidationRequest` now accepts both field names via `@JsonAlias`. Input content null-safety checks have been added. Remaining Gazelle integration work (test management/validation workflows) may still be incomplete.

- Validator UI — "Upload file" bug: fixed. The file input is now reliably reset using a form-wrap-and-reset technique, ensuring the `change` event fires even when re-selecting the same file.

- FHIR→DICOM→FHIR→DICOM conversion issue: fixed. The `FHIRToMADOConverter` no longer inserts a default Instance Number "1" when none is present in the FHIR instance. Instance Number is now only added when `instance.hasNumber()` returns true, following the KOS specification behavior (RC+: "Required when present in the referenced SOP Instance").

---

## Reporting

Please report any other issues via GitHub Issues with clear reproduction steps, the command or UI action used, and any example files (de-identified).

**Last Updated:** 2026-05-29
