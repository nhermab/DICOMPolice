# DICOMPolice

**A DICOM Key Object Selection (KOS) and MADO Manifest validator + sample generator for IHE XDS-I.b and MADO trial implementations.**

DICOMPolice validates DICOM Key Object Selection (KOS) documents and Manifest-based Access to DICOM Objects (MADO) manifests (TID 1600 Image Library) according to the IHE Radiology Technical Framework (XDS-I.b) and the draft MADO profile.

Available through **command-line interface**, **web interface**, and **REST API** (Gazelle-compatible). Includes generators for compliant sample files and optional "EVIL" intentionally broken samples for interoperability testing.

---
## DEMO

[online web based demo](https://ihebelgium.ehealthhub.be/TheDICOMPolice/)

## Quick Start

```cmd
# Build the project
mvn clean package

# Start the web interface
mvn spring-boot:run

# Or validate from command line
java -cp target\DICOMPolice-0.1.0-SNAPSHOT.jar be.uzleuven.ihe.dicom.validator.CLIDICOMVerify your-file.dcm

# Generate a sample KOS file
java -cp target\DICOMPolice-0.1.0-SNAPSHOT.jar be.uzleuven.ihe.dicom.creator.IHEKOSSampleCreator
```

---

## Features

### Validation Capabilities
- ✅ **IHE XDS-I.b KOS Validation**: Validates Key Object Selection documents for XDS-I Imaging Manifest compliance
- ✅ **MADO Profile Support**: Validates MADO Manifest with Description requirements (incl. TID 1600 Image Library)
- ✅ **Multi-Layer Validation**:
  - DICOM Part 10 file format and meta information
  - IOD compliance checks inspired by PixelMed's `dciodvfy`
  - Structured Reporting (SR) content tree validation
  - SR Template validation (TID 1600, TID 2010)
  - Advanced encoding validation (character sets, padding, UIDs)
  - Timezone consistency across MADO content
  - Digital signature structure checks (presence validation)
  - Evidence orphan detection
  - Forbidden tag detection (pixel data, waveforms, etc.)

### Interfaces
- 🖥️ **Command-Line Interface**: Batch validation with verbose output options
- 🌐 **Web Interface**: Modern drag-and-drop UI for file validation with detailed reports
- 🔌 **REST API**: Gazelle Validation Service compatible endpoints (`/validation/v2`)
- 📊 **Detailed Reports**: Categorized validation results (ERROR, WARNING, INFO)

### Sample Generation
- ✅ **Valid Sample Creators**: Generate compliant IHE KOS and MADO manifests for testing
- 🎲 **EVIL Generators**: Create intentionally malformed files with controlled randomness
  - 20% chance of missing required elements
  - 5% chance of explicit corruptions
  - 30% chance of forbidden tags injection
  - Deterministic mode with seed for reproducibility

---

## Table of Contents

- [Quick Start](#quick-start)
- [Features](#features)
- [Installation](#installation)
- [Usage](#usage)
  - [Web Interface](#web-interface)
  - [Command-Line Validation](#command-line-validation)
  - [Sample Creation](#sample-creation)
  - [EVIL (intentionally broken) generators](#evil-intentionally-broken-generators)
- [REST API](#rest-api)
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

### Web Interface

DICOMPolice includes a modern web interface for interactive validation.

#### Starting the Web Server

```cmd
REM Run as Spring Boot application
mvn spring-boot:run
```

Or run the WAR file directly:

```cmd
java -jar target\DICOMPolice-0.1.0-SNAPSHOT.war
```

The web interface will be available at `http://localhost:8080`

#### Using the Web Interface

1. **Select a validation profile** from the dropdown (IHE XDS-I.b Manifest or MADO)
2. **Upload a DICOM file** by:
   - Dragging and dropping onto the upload area, or
   - Clicking to browse and select a file
3. **Click "Validate"** to process the file
4. **View results** organized by severity:
   - **Errors** (❌) - Critical validation failures
   - **Warnings** (⚠️) - Non-critical issues that should be addressed
   - **Info** (ℹ️) - Informational messages and recommendations
5. **Filter messages** by severity using the category buttons
6. **Download results** for reporting and documentation

### Command-Line Validation

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

DICOMPolice includes specialized generators that create **intentionally malformed** DICOM files for negative testing and validator development.

#### Key Features

- **Probabilistic Corruption**: 20% missing elements, 5% explicit corruptions, 30% forbidden tags
- **Forbidden Tag Injection**: Adds tags that violate KOS/MADO specifications:
  - Critical violations: Pixel Data, Waveform Data, Audio Data
  - Warning violations: Image Pixel Module, positioning, acquisition parameters
- **Deterministic Mode**: Reproducible generation using seed values (`-Devil.seed=123`)
- **Multiple Profiles**: Available for both KOS and MADO

#### Quick Start

```cmd
REM Generate 5 intentionally broken KOS files
java -cp target\DICOMPolice-0.1.0-SNAPSHOT.jar be.uzleuven.ihe.dicom.creator.evil.EVILKOSCreator 5

REM Generate 5 broken MADO files deterministically
java -cp target\DICOMPolice-0.1.0-SNAPSHOT.jar -Devil.seed=123 be.uzleuven.ihe.dicom.creator.evil.EVILMADOCreator 5
```

For complete documentation, see [EVIL_GENERATORS.md](EVIL_GENERATORS.md).

---

## REST API

DICOMPolice implements the **Gazelle Validation Service API** specification, enabling integration with IHE Gazelle Test Management and other validation frameworks.

### API Endpoints

The REST API is available at `/validation/v2` when the web server is running.

**Base URL**: `http://localhost:8080/validation/v2`

### Key Endpoints

- **GET** `/validation/v2/profiles` - List available validation profiles
- **POST** `/validation/v2/validate/{validationServiceName}` - Validate a DICOM file
- **GET** `/validation/v2/validation-status/{uuid}` - Check validation status
- **GET** `/validation/v2/validation-result-overview/{uuid}` - Get validation summary
- **GET** `/validation/v2/validation-result-details/{uuid}` - Get detailed results

### Supported Profiles

- `IHEXDSIManifest` - IHE XDS-I.b Key Object Selection
- `IHEMADO` - MADO Manifest with Descriptors

### Example Usage

See [API_README.md](API_README.md) for complete API documentation, request/response formats, and integration examples.

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

This produces:
- **JAR**: `target\DICOMPolice-0.1.0-SNAPSHOT.jar` - For command-line usage and sample generation
- **WAR**: `target\DICOMPolice-0.1.0-SNAPSHOT.war` - For web interface deployment

---

## Third-Party Dependencies

### Spring Boot (Apache License 2.0)

- **Framework**: Spring Boot
- **Version used by this project**: **2.7.18**
- Used for web interface and REST API

### dcm4che (GPLv2)

- **Library**: `dcm4che-core`, `dcm4che-net`
- **Version used by this project**: **5.31.0**

### PixelMed (BSD-style License)

Portions of the IOD validation logic are based on PixelMed's `dciodvfy.cc`.
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
