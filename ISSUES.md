# Known Issues and Limitations

This document tracks known issues, limitations, and caveats in the current version of DICOMPolice.

---

## Critical Limitations

### MADO Profile - Public Comment Status

⚠️ **Impact: High** | **Status: By Design**

The MADO (Manifest-based Access to DICOM Objects) profile is currently in **Public Comment** status within the IHE Radiology Technical Framework development process.

**Implications:**
- Code values using "ddd" prefix are **provisional** and will change
- Template structure may be modified based on public feedback
- Validation rules implemented here reflect draft specification
- **DO NOT use in production clinical environments**

**Mitigation:**
- Monitor IHE public comment process for updates
- Plan to update when final specification is published
- Participate in IHE Connectathon trial implementations
- Document which draft version your manifests conform to

**Timeline:**
- Expected finalization: TBD (monitor IHE.net)
- This validator will be updated promptly after publication

---

## Known Bugs

### None currently reported

This section will be populated as issues are discovered. Please report bugs via GitHub Issues.

---

## Validation Limitations

### 1. Digital Signature Verification Not Implemented

**Impact: Medium** | **Status: Not Implemented**

The validator checks for the **presence** of digital signatures but does **not verify** cryptographic validity.

**What's Checked:**
- DigitalSignaturesSequence presence
- MAC (Message Authentication Code) Parameters presence
- Basic structure of signature attributes

**What's NOT Checked:**
- Certificate validity
- Signature cryptographic verification
- Certificate chain validation
- Timestamp verification

**Workaround:**
- Use dedicated tools (OpenSSL, dcm4che DicomSigner) for signature verification
- Plan to implement in future version

**Reference:** See [TODO.md](TODO.md) - "Add support for validating signed documents"

---

### 2. Limited Private Attribute Validation

**Impact: Low** | **Status: By Design**

Private attributes (odd group numbers) are **not validated** in detail.

**What's Checked:**
- Proper private creator identification (if present)
- Basic VR validation

**What's NOT Checked:**
- Vendor-specific private attribute semantics
- Private data element structure
- Private dictionary compliance

**Workaround:**
- Obtain vendor DICOM Conformance Statements
- Use vendor-specific validation tools

**Rationale:** Private attributes are vendor-specific and require custom dictionaries

---

### 3. Incomplete IOD Coverage

**Impact: Medium** | **Status: Partial Implementation**

Currently only validates **Key Object Selection Document IOD**.

**Supported IODs:**
- ✅ Key Object Selection Document (1.2.840.10008.5.1.4.1.1.88.59)

**Unsupported IODs (may be added in future):**
- ❌ Basic Text SR
- ❌ Enhanced SR
- ❌ Comprehensive SR
- ❌ Other DICOM IODs (CT, MR, etc.)

**Workaround:**
- Use IOD-specific validators for other document types
- Consider contributing additional IOD validators

---

### 4. No Cross-Document Validation

**Impact: Medium** | **Status: Not Implemented**

The validator processes **one file at a time** and does not validate relationships between multiple documents.

**Missing Features:**
- Cross-manifest UID uniqueness checking
- Referenced SOP Instance validation (checking if referenced files exist)
- Study/Series consistency across multiple submissions
- XDS.b document set validation

**Workaround:**
- Manual cross-reference checking
- Use XDS.b Registry validation tools

**Future:** May be added in batch validation mode

---

## Performance Limitations

### 5. Large File Performance

**Impact: Low** | **Status: Known Limitation**

Performance degrades with very large DICOM files (>100 MB).

**Symptoms:**
- Slow validation times for large manifests
- High memory usage for files with many sequences

**Typical Performance:**
- Small files (<1 MB): < 1 second
- Medium files (1-10 MB): 1-3 seconds
- Large files (10-100 MB): 3-10 seconds
- Very large files (>100 MB): May exceed 30 seconds

**Workaround:**
- Increase JVM heap size: `java -Xmx2g -cp ...`
- Validate files individually rather than in large batches

**Future:** Consider streaming validation for large files

---

### 6. No Parallel Processing

**Impact: Low** | **Status: Not Implemented**

Validating multiple files is **sequential** - no parallel processing.

**Limitation:**
- Validating 100 files takes 100× longer than 1 file
- Does not utilize multi-core CPUs effectively

**Workaround:**
- Use shell scripts to parallelize: `xargs -P 4 -n 1 java ...`
- Process files in batches

**Future:** Planned for v0.3.0

---

## Interoperability Issues

### 7. Character Set Encoding Edge Cases

**Impact: Low** | **Status: Partial Implementation**

Some exotic character set combinations may not be fully validated.

**Known Issues:**
- Multi-byte character sets with code extension (ISO 2022)
- Mixed single-byte and multi-byte encodings in same file
- Surrogate pairs in UTF-8

**Workaround:**
- Test with your specific character set combinations
- Report issues with sample files

---

### 8. Transfer Syntax Support

**Impact: Low** | **Status: By Design**

The validator **reads** all standard transfer syntaxes but **recommends** only uncompressed formats for manifests.

**Fully Supported:**
- ✅ Implicit VR Little Endian
- ✅ Explicit VR Little Endian (recommended)
- ✅ Explicit VR Big Endian

**Parsed but Warned:**
- ⚠️ JPEG Lossy/Lossless
- ⚠️ JPEG 2000
- ⚠️ RLE

**Rationale:** MADO manifests are metadata documents and should not be compressed

---

## Documentation Gaps

### 9. Incomplete Error Code Documentation

**Impact: Medium** | **Status: In Progress**

Not all validation error messages have structured error codes.

**Current State:**
- Errors use descriptive text messages
- Some errors have module paths
- No standardized error code system (e.g., E001, W002)

**Future:** Planned structured error codes in v0.2.0

**Workaround:** Search error message text for troubleshooting

---

### 10. Limited Examples

**Impact: Low** | **Status: Ongoing**

Few real-world example files and use cases documented.

**What's Available:**
- Sample creators generate basic compliant documents
- README has basic usage examples

**What's Missing:**
- Real-world vendor file examples (can't share due to privacy)
- Common error scenarios and fixes
- Integration examples with PACS/XDS systems
- Video tutorials

**Contributing:** We welcome de-identified sample files from the community!

---

## Platform-Specific Issues

### 11. Windows Path Handling

**Impact: Low** | **Status: Known Issue**

Windows paths with spaces may require quotes in command-line usage.

**Symptoms:**
```
Error: File not found: C:\Program Files\DICOM\file.dcm
```

**Workaround:**
```bash
java -cp ... CLIDICOMVerify "C:\Program Files\DICOM\file.dcm"
```

**Future:** Improve path parsing in CLI

---

### 12. Console Output Encoding

**Impact: Low** | **Status: Platform-Dependent**

Unicode characters in validation messages may not display correctly on some terminals.

**Affected Platforms:**
- Windows Command Prompt (cmd.exe) - legacy code page issues
- Some Linux terminals without UTF-8 support

**Workaround:**
- Use PowerShell on Windows: `chcp 65001` (UTF-8)
- Configure terminal for UTF-8 support
- Use `--new-format` flag for ASCII-only output

---

## Specification Ambiguities

### 13. TID 1600 Interpretation Variations

**Impact: Medium** | **Status: Specification Issue**

The MADO draft specification allows multiple interpretations of TID 1600 structure.

**Ambiguities:**
- Optional vs. required nested structures
- Handling of missing descriptors
- Order of Image Library Groups

**Current Implementation:**
- Follows "Approach 2" strictly (Image Library container)
- May be more restrictive than some interpretations

**Mitigation:**
- Participate in IHE Connectathon to align interpretations
- Report specification ambiguities to IHE Radiology Committee

---

### 14. Timezone Offset Requirements

**Impact: Low** | **Status: Specification Evolution**

Timezone validation rules have evolved across DICOM supplements and IHE profiles.

**Current Implementation:**
- Follows DICOM PS3.3 format: ±HHMM
- Requires consistency across all date/time attributes
- Validates offset range: -1200 to +1400

**Edge Cases:**
- Historical time zones (e.g., pre-1960 changes)
- Daylight saving time transitions
- Leap seconds (not supported by DICOM)

**Workaround:** Ensure all timestamps use the same offset (don't mix DST and standard time)

---

## Future Considerations

### 15. Java Version Compatibility

**Impact: Low** | **Status: Stable**

Currently requires **Java 8+**. Future versions may require newer Java.

**Current Status:**
- ✅ Java 8 (primary target)
- ✅ Java 11 (tested)
- ✅ Java 17 (tested)
- ⚠️ Java 21+ (not extensively tested)

**Future:** May adopt Java 11+ features in v2.0.0

---

## Reporting Issues

### How to Report a Bug

1. **Search existing issues**: Check if already reported
2. **Collect information**:
   - Java version: `java -version`
   - DICOMPolice version
   - Command used
   - Error message (full output with `-v`)
3. **Prepare sample file** (de-identified!)
4. **Submit GitHub Issue** with:
   - Clear description
   - Steps to reproduce
   - Expected vs. actual behavior
   - Sample file (if possible)

### Issue Template

```markdown
**Description:**
Brief description of the issue

**Steps to Reproduce:**
1. Run command: `java -cp ... CLIDICOMVerify file.dcm`
2. Observe error: ...

**Expected Behavior:**
What should happen

**Actual Behavior:**
What actually happens

**Environment:**
- DICOMPolice version: 0.1.0-SNAPSHOT
- Java version: 11.0.x
- OS: Windows 10 / Linux / macOS
- DICOM file: [attach de-identified sample]

**Additional Context:**
Any other relevant information
```

---

## Workarounds Summary

| Issue | Temporary Workaround |
|-------|---------------------|
| Large file performance | Increase JVM heap: `-Xmx2g` |
| No parallel processing | Use shell parallelization |
| Digital signature verification | Use external tools (dcm4che DicomSigner) |
| Windows path spaces | Quote paths: `"C:\Program Files\..."` |
| Unicode display issues | Use PowerShell or `--new-format` flag |
| Character set edge cases | Report with sample files |

---

## Disclaimer

This software is provided for **testing and validation purposes only**. It has **not been approved for clinical use**.

- Not a medical device
- No warranty (see LICENSE)
- User assumes all responsibility for validation results
- Always perform independent verification for production systems

---

**Last Updated**: 2024-12-21  
**Version**: 0.1.0-SNAPSHOT

For the most current information, check:
- GitHub Issues: https://github.com/nhermab/DICOMPolice/issues
- IHE Public Comments: https://www.ihe.net/
- DICOM Standard: https://www.dicomstandard.org/

