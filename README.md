# DICOMPolice

**A comprehensive DICOM Key Object Selection (KOS) and MADO Manifest validator and creator for IHE XDS-I.b compliance.**

DICOMPolice validates DICOM Key Object Selection (KOS) documents and Manifest-based Access to DICOM Objects (MADO) manifests according to IHE XDS-I.b and MADO profile specifications. It provides both validation and sample creation tools to ensure interoperability in medical imaging document exchange.

---

## Features

- ✅ **IHE XDS-I.b KOS Validation**: Validates Key Object Selection documents for XDS-I Imaging Manifest compliance
- ✅ **MADO Profile Support**: Full validation for Manifest-based Access to DICOM Objects (TID 1600 Image Library)
- ✅ **IOD Validation**: DICOM Information Object Definition compliance checking (inspired by PixelMed's dciodvfy)
- ✅ **Advanced Encoding Checks**: Character set, UI padding, text padding validation
- ✅ **Timezone Consistency**: Validates timezone offset requirements across content
- ✅ **Digital Signature Support**: Validates digital signature presence and structure
- ✅ **Part 10 File Format**: Validates DICOM file format structure and transfer syntax
- ✅ **Sample Creators**: Generate compliant IHE KOS and MADO manifests for testing
- ✅ **CLI Interface**: Command-line tool with verbose output options

---

## Table of Contents

- [Installation](#installation)
- [Usage](#usage)
  - [Validation](#validation)
  - [Sample Creation](#sample-creation)
- [Validation Profiles](#validation-profiles)
- [Architecture](#architecture)
- [MADO Profile Notice](#mado-profile-notice)
- [Building](#building)
- [Third-Party Dependencies](#third-party-dependencies)
- [License](#license)
- [Contributing](#contributing)

---

## Installation

### Prerequisites

- Java 8 or higher
- Maven 3.6+ (for building from source)

### Build from Source

```bash
git clone https://github.com/nhermab/DICOMPolice.git
cd DICOMPolice
mvn clean package
```

The compiled JAR will be in the `target/` directory.

---

## Usage

### Validation

Validate DICOM KOS files using the command-line interface:

```bash
# Basic validation
java -cp target/DICOMPolice-0.1.0-SNAPSHOT.jar be.uzleuven.ihe.dicom.validator.CLIDICOMVerify kos.dcm

# Verbose output with detailed validation messages
java -cp target/DICOMPolice-0.1.0-SNAPSHOT.jar be.uzleuven.ihe.dicom.validator.CLIDICOMVerify -v kos.dcm

# Validate with IHE XDS-I Manifest profile
java -cp target/DICOMPolice-0.1.0-SNAPSHOT.jar be.uzleuven.ihe.dicom.validator.CLIDICOMVerify --profile IHEXDSIManifest kos.dcm

# Validate with MADO profile
java -cp target/DICOMPolice-0.1.0-SNAPSHOT.jar be.uzleuven.ihe.dicom.validator.CLIDICOMVerify --profile IHEMADO mado_manifest.dcm

# Validate multiple files
java -cp target/DICOMPolice-0.1.0-SNAPSHOT.jar be.uzleuven.ihe.dicom.validator.CLIDICOMVerify -v file1.dcm file2.dcm file3.dcm
```

**Command-Line Options:**

- `-h, --help` - Display help message
- `-v, --verbose` - Enable verbose output with detailed validation messages
- `--new-format` - Use new format for error messages
- `--profile <name>` - Specify validation profile:
  - `IHEXDSIManifest` - IHE XDS-I.b KOS Manifest
  - `IHEMADO` - MADO Manifest with Description (TID 1600)

**Exit Codes:**

- `0` - Validation successful
- `1` - Validation failed or file error

### Sample Creation

Generate compliant sample documents for testing:

```bash
# Create IHE XDS-I.b KOS sample
java -cp target/DICOMPolice-0.1.0-SNAPSHOT.jar be.uzleuven.ihe.dicom.creator.IHEKOSSampleCreator

# Create MADO manifest sample
java -cp target/DICOMPolice-0.1.0-SNAPSHOT.jar be.uzleuven.ihe.dicom.creator.IHEMADOSampleCreator
```

Generated samples are written to the current working directory.

#### EVIL (intentionally broken) generators

If you want to generate intentionally non-compliant KOS/MADO files for negative testing/fuzzing, see `EVIL_GENERATORS.md`.

---

## Validation Profiles

### IHE XDS-I.b Manifest

The **IHEXDSIManifest** profile validates Key Object Selection documents according to IHE Radiology Technical Framework Supplement XDS-I.b (Cross-Enterprise Document Sharing for Imaging).

**Key Requirements:**
- Document title: `(113030, DCM, "Manifest")`
- Template Identifier: TID 2010 (DCMR)
- Current Requested Procedure Evidence Sequence required
- Timezone offset consistency
- Explicit VR Little Endian transfer syntax (recommended)

### MADO (Manifest-based Access to DICOM Objects)

The **IHEMADO** profile extends XDS-I with enhanced metadata using TID 1600 Image Library template structure.

**Key Requirements:**
- Document title: `(ddd001, DCM, "Manifest with Description")` or `(113030, DCM, "Manifest")`
- TID 1600 Image Library container: `(111028, DCM, "Image Library")`
- Image Library Group structure with Study, Series, and Instance descriptors
- Enhanced patient identification with IssuerOfPatientIDQualifiersSequence
- AccessionNumber with IssuerOfAccessionNumberSequence
- Retrieval location metadata (RetrieveLocationUID)
- Comprehensive timezone validation

**Validation Checks Include:**
- ✅ TID 1600 structure validation (root, study, series, image library groups)
- ✅ Code sequence validation (concept names, modality codes, target regions)
- ✅ Study/Series/Instance UID cross-validation
- ✅ Orphaned evidence detection
- ✅ Retrieval information completeness
- ✅ Timezone consistency across all date/time attributes

---

## Architecture

See [ARCHITECTURE.md](ARCHITECTURE.md) for detailed system design and component documentation.

**High-Level Architecture:**

```
┌─────────────────────────────────────────────┐
│         CLI Entry Point                     │
│    (CLIDICOMVerify / Creators)              │
└─────────────────┬───────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────┐
│        IOD Validator Factory                │
│   (Selects appropriate validator)           │
└─────────────────┬───────────────────────────┘
                  │
      ┌───────────┴──────────┐
      ▼                      ▼
┌─────────────┐      ┌──────────────────┐
│ KOS Base    │      │ MADO Extended    │
│ Validator   │      │ Validator        │
└──────┬──────┘      └────────┬─────────┘
       │                      │
       └──────────┬───────────┘
                  ▼
┌─────────────────────────────────────────────┐
│         Specialized Validators              │
│  • Part10FileValidator                      │
│  • AdvancedEncodingValidator                │
│  • TimezoneValidator                        │
│  • TID1600Validator                         │
│  • DigitalSignatureValidator                │
│  • EvidenceOrphanValidator                  │
└─────────────────────────────────────────────┘
```

---

## MADO Profile Notice

⚠️ **IMPORTANT: Public Comment Version**

The MADO (Manifest-based Access to DICOM Objects) profile is currently in **Public Comment** status as part of the IHE Radiology Technical Framework development process. The implementation in DICOMPolice is based on the draft specification and is intended for:

- **Trial implementations** for Connectathon testing
- **Profile development feedback** and refinement
- **Interoperability testing** in pre-production environments

**Do NOT use in production clinical environments until:**
1. The MADO profile completes the IHE public comment period
2. A final published specification is released
3. Trial implementation results are reviewed and incorporated
4. Your organization completes appropriate validation and risk assessment

**Current Implementation Status:**
- ✅ TID 1600 Image Library validation (Approach 2)
- ✅ Basic MADO profile requirements
- ⚠️ May change based on public comment feedback
- ⚠️ Code values using "ddd" prefix are provisional

**Feedback and Contributions:**
If you identify issues or have suggestions for the MADO implementation, please:
1. Check existing issues in this repository
2. Submit detailed bug reports or enhancement requests
3. Include sample DICOM files (de-identified) when possible
4. Reference specific requirements from the MADO specification

---

## Building

### Build with Maven

```bash
mvn clean compile
mvn package
```

### Run Tests

```bash
mvn test
```

### Create Runnable JAR

```bash
mvn clean package
java -cp target/DICOMPolice-0.1.0-SNAPSHOT.jar be.uzleuven.ihe.dicom.validator.CLIDICOMVerify
```

---

## Third-Party Dependencies

This project depends on the following open-source libraries:

### dcm4che (GPLv2)

- **Library**: dcm4che-core, dcm4che-net (version 5.31.0)
- **License**: GNU General Public License v2.0
- **Purpose**: DICOM parsing, encoding, and data model
- **Website**: https://www.dcm4che.org/
- **Repository**: https://github.com/dcm4che/dcm4che

### PixelMed (BSD-style License)

Portions of this project's IOD validation logic are based on **PixelMed's dciodvfy.cc** utility:

- **Copyright**: (c) 1993-2024, David A. Clunie DBA PixelMed Publishing
- **License**: BSD-style (see THIRD_PARTY_NOTICES for full text)
- **Affected Files**:
  - `CLIDICOMVerify.java`
  - `IODValidator.java`
  - `IODValidatorFactory.java`

See [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES) for complete license information and copyright notices.

---

## License

This project is licensed under the **GNU General Public License v2.0 (GPLv2)**.

```
DICOMPolice - DICOM Key Object Selection and MADO Manifest Validator
Copyright (C) 2026 UZ Leuven

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License along
with this program; if not, write to the Free Software Foundation, Inc.,
51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
```

**Why GPLv2?**
This project uses dcm4che libraries, which are licensed under GPLv2. To comply with dcm4che's license terms, DICOMPolice is also distributed under GPLv2.

See [LICENSE](LICENSE) for the complete license text.

---

## Contributing

Contributions are welcome! Please follow these guidelines:

1. **Fork** the repository
2. **Create a feature branch**: `git checkout -b feature/your-feature`
3. **Commit your changes**: `git commit -am 'Add new feature'`
4. **Push to the branch**: `git push origin feature/your-feature`
5. **Submit a Pull Request**

### Code Style

- Follow Java naming conventions
- Add Javadoc comments for public APIs
- Include appropriate error handling
- Write clear commit messages

### Testing

- Include test cases for new validators
- Ensure existing tests pass: `mvn test`
- Test with sample DICOM files (de-identified only)

### Reporting Issues

- Use GitHub Issues for bug reports
- Include DICOM file samples (de-identified)
- Specify Java version and environment details
- Reference DICOM/IHE specification sections when applicable

---

## Known Issues and Limitations

See [ISSUES.md](ISSUES.md) for current known issues and limitations.

---

## TODO

See [TODO.md](TODO.md) for planned features and enhancements.

---

## Support

For questions, issues, or contributions:

- **GitHub Issues**: https://github.com/nhermab/DICOMPolice/issues
- **IHE Connectathon**: Participate in testing events
- **Email**: [nhermab@uzleuven.be]

---

## Acknowledgments

- **IHE Radiology Technical Committee** - For XDS-I.b and MADO specifications
- **IHE MCWG** - For XC-WADO and MADO specifications
- **dcm4che Team** - For the excellent DICOM toolkit
- **David A. Clunie / PixelMed** - For dciodvfy inspiration and IOD validation concepts
- **DICOM Standards Committee** - For maintaining the DICOM standard
- **UZ Leuven** - For maintaining me

---

## Disclaimer

This software has **not been tested or approved for clinical use** or for incorporation in a medical device. It is provided for testing, validation, and interoperability purposes only.

It is the user's responsibility to comply with any applicable local, state, national, or international regulations regarding medical software and protected health information.

**NO WARRANTY**: This software is provided "as is" without warranty of any kind, either express or implied, including but not limited to the implied warranties of merchantability and fitness for a particular purpose.
