# DICOMPolice

**A DICOM Key Object Selection (KOS) and MADO Manifest validator + sample generator for IHE XDS-I.b and MADO trial implementations.**

DICOMPolice validates DICOM Key Object Selection (KOS) documents and Manifest-based Access to DICOM Objects (MADO) manifests (TID 1600 Image Library) according to the IHE Radiology Technical Framework (XDS-I.b) and the draft MADO profile.

It also includes generators for compliant sample files (and optional “EVIL” intentionally broken samples) to support interoperability testing.

---

## Features

- ✅ **IHE XDS-I.b KOS Validation**: Validates Key Object Selection documents for XDS-I Imaging Manifest compliance
- ✅ **MADO Profile Support**: Validates MADO Manifest with Description requirements (incl. TID 1600 Image Library)
- ✅ **IOD Validation**: IOD compliance checks inspired by PixelMed’s `dciodvfy`
- ✅ **Advanced Encoding Checks**: Character set support + padding checks
- ✅ **Timezone Consistency**: Validates timezone offset requirements across content
- ✅ **Digital Signature Structure Checks**: Detects/validates presence (not cryptographic verification)
- ✅ **Part 10 File Format**: Validates DICOM Part 10 structure and meta information
- ✅ **Sample Creators**: Create compliant IHE KOS and MADO manifests for testing
- ✅ **CLI Interface**: Validate one or more files with optional verbose output

---

## Table of Contents

- [Installation](#installation)
- [Usage](#usage)
  - [Validation](#validation)
  - [Sample Creation](#sample-creation)
  - [EVIL (intentionally broken) generators](#evil-intentionally-broken-generators)
- [Validation Profiles](#validation-profiles)
- [Architecture](#architecture)
- [MADO Profile Notice](#mado-profile-notice)
- [Building](#building)
- [Third-Party Dependencies](#third-party-dependencies)
- [License](#license)

---

## Installation

### Prerequisites

- Java 8+
- Maven 3.6+ (for building)

### Build from Source

```cmd
git clone https://github.com/nhermab/DICOMPolice.git
cd DICOMPolice
mvn clean package
```

The compiled JAR will be in `target\`.

---

## Usage

### Validation

Validate DICOM KOS/MADO files using the CLI:

```cmd
REM Basic validation
java -cp target\DICOMPolice-0.1.0-SNAPSHOT.jar be.uzleuven.ihe.dicom.validator.CLIDICOMVerify kos.dcm

REM Verbose output
java -cp target\DICOMPolice-0.1.0-SNAPSHOT.jar be.uzleuven.ihe.dicom.validator.CLIDICOMVerify -v kos.dcm

REM Validate with IHE XDS-I.b Manifest profile
java -cp target\DICOMPolice-0.1.0-SNAPSHOT.jar be.uzleuven.ihe.dicom.validator.CLIDICOMVerify --profile IHEXDSIManifest kos.dcm

REM Validate with MADO profile
java -cp target\DICOMPolice-0.1.0-SNAPSHOT.jar be.uzleuven.ihe.dicom.validator.CLIDICOMVerify --profile IHEMADO mado_manifest.dcm

REM Validate multiple files
java -cp target\DICOMPolice-0.1.0-SNAPSHOT.jar be.uzleuven.ihe.dicom.validator.CLIDICOMVerify -v file1.dcm file2.dcm file3.dcm

REM Use the “new format” message rendering
java -cp target\DICOMPolice-0.1.0-SNAPSHOT.jar be.uzleuven.ihe.dicom.validator.CLIDICOMVerify --new-format --profile IHEMADO -v mado_manifest.dcm
```

**Command-Line Options:**

- `-h, --help` - Display help message
- `-v, --verbose` - Verbose output
- `--new-format` - Print messages using the new `[SEVERITY] path: message` style
- `--profile <name>` - Validation profile:
  - `IHEXDSIManifest` - IHE XDS-I.b KOS Manifest
  - `IHEMADO` - MADO Manifest with Description

**Exit Codes:**

- `0` - Validation successful
- `1` - Validation failed or file error

### Sample Creation

Generated samples are written to the current working directory.

#### IHE XDS-I.b KOS samples

```cmd
REM Create a single KOS sample (default behavior: random sizes)
java -cp target\DICOMPolice-0.1.0-SNAPSHOT.jar be.uzleuven.ihe.dicom.creator.IHEKOSSampleCreator

REM Create deterministic default-size samples
java -cp target\DICOMPolice-0.1.0-SNAPSHOT.jar be.uzleuven.ihe.dicom.creator.IHEKOSSampleCreator --default-sizes

REM Create N samples
java -cp target\DICOMPolice-0.1.0-SNAPSHOT.jar be.uzleuven.ihe.dicom.creator.IHEKOSSampleCreator 10
```

Notes:
- The generator ensures **all Evidence instances are included in the SR content tree** (per IHE/DICOM KOS requirements). Older “key image count” style knobs are kept only for backward compatibility.

#### MADO samples

```cmd
REM Create a single MADO sample (default behavior: random sizes)
java -cp target\DICOMPolice-0.1.0-SNAPSHOT.jar be.uzleuven.ihe.dicom.creator.IHEMADOSampleCreator

REM Create deterministic default-size samples (aligned to KOS defaults)
java -cp target\DICOMPolice-0.1.0-SNAPSHOT.jar be.uzleuven.ihe.dicom.creator.IHEMADOSampleCreator --default-sizes

REM Create N samples
java -cp target\DICOMPolice-0.1.0-SNAPSHOT.jar be.uzleuven.ihe.dicom.creator.IHEMADOSampleCreator 10
```

#### Helper “generate + validate” runners

For quick local development runs, the repo includes small runners that generate a file and immediately validate it:

```cmd
java -cp target\DICOMPolice-0.1.0-SNAPSHOT.jar be.uzleuven.ihe.dicom.creator.GenerateAndValidateKOS
java -cp target\DICOMPolice-0.1.0-SNAPSHOT.jar be.uzleuven.ihe.dicom.creator.GenerateAndValidateMado
```

### EVIL (intentionally broken) generators

For negative testing/fuzzing, see `EVIL_GENERATORS.md`.

---

## Validation Profiles

### IHE XDS-I.b Manifest (`IHEXDSIManifest`)

Validates Key Object Selection documents according to IHE XDS-I.b Imaging Manifest requirements.

### MADO (Manifest-based Access to DICOM Objects) (`IHEMADO`)

Validates the draft MADO profile, including TID 1600 Image Library structure.

⚠️ Note: code values with the `ddd` prefix are **provisional** and may change when the final profile is published.

---

## Architecture

See [ARCHITECTURE.md](ARCHITECTURE.md).

---

## MADO Profile Notice

⚠️ **Public Comment / Trial Implementation**

The MADO profile is currently implemented against a **draft** specification (Public Comment / trial implementation phase). Expect changes when the final profile is published.

---

## Building

```cmd
mvn clean package
```

---

## Third-Party Dependencies

### dcm4che (GPLv2)

- **Library**: `dcm4che-core`, `dcm4che-net`
- **Version used by this project**: **5.31.0**

### PixelMed (BSD-style License)

Portions of the IOD validation logic are based on PixelMed’s `dciodvfy.cc`.
See [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES) for details.

---

## License

This project is licensed under **GPLv2** (to comply with dcm4che’s license terms).

See [LICENSE](LICENSE).

---

## Known Issues and Limitations

See [ISSUES.md](ISSUES.md).

---

## TODO

See [TODO.md](TODO.md).
