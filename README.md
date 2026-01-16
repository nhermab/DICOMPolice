# DICOMPolice

**IHE XDS-I.b KOS / MADO Validator + MHD Document Responder + DICOM/FHIR Converter**

DICOMPolice is a comprehensive toolkit centered on the powerful **Manifest-based Access to DICOM Objects (MADO)** and **KeY Object Selection (KOS)** standards defined in IHE-RAD. It validates, creates, and converts these advanced DICOM objects, demonstrating the robust capabilities of modern DICOM.

For environments requiring FHIR interoperability, it supplements the core DICOM functionality with bridge tools (like SCU-driven generation) that connect legacy DICOM archives to state-of-the-art MADO and FHIR workflows.

Available via **Web Interface** (drag-and-drop), **REST API** (Gazelle-compatible), and **Command-Line Interface (CLI)**.

---

## 🌐 Web Interface Tools

The easiest way to use DICOMPolice is through the served web applications.
Launch the server:
```bash
mvn spring-boot:run
# Open http://localhost:8080
```

### 1. Validator (MADO & KOS)
> **URL**: [https://ihebelgium.ehealthhub.be/TheDICOMPolice/](https://ihebelgium.ehealthhub.be/TheDICOMPolice/)

Drag-and-drop DICOM files to validate them against IHE profiles.
- **Profiles**: IHE XDS-I.b Key Object Selection (KOS), MADO Draft Profile (TID 1600).
- **Features**: Detailed error logs, SR tree visualization, rigorous DICOM constraints.
- **Core Code**:
  - [`DicomVisualizerController.java`](src/main/java/be/uzleuven/ihe/service/DicomVisualizerController.java) (Web controller)
  - [`GazelleValidatorAPIController.java`](src/main/java/be/uzleuven/ihe/service/GazelleValidatorAPIController.java) (Validation logic)

### 2. MHD Viewer & MADO SCU Client
> **URL**: [https://ihebelgium.ehealthhub.be/TheDICOMPolice/xtehdsMADO](https://ihebelgium.ehealthhub.be/TheDICOMPolice/xtehdsMADO)

A **FHIR MHD (Mobile access to Health Documents)** Document Responder facade that sits on top of a standard DICOM PACS.
- **Workflow**:
  1. User searches via the web UI (MHD ITI-67 transaction).
  2. Backend queries the PACS via **DICOM C-FIND** (SCU).
  3. Backend generates a MADO/KOS on-the-fly from C-FIND results.
  4. Returns FHIR DocumentReference resources or the binary MADO file.
- **Core Code**:
  - [`MHDService.java`](src/main/java/be/uzleuven/ihe/service/MHD/MHDService.java) (MHD to DICOM C-FIND orchestration)
  - [`MADOSCUManifestCreator.java`](src/main/java/be/uzleuven/ihe/dicom/creator/scu/MADOSCUManifestCreator.java) (Builds MADO from PACS responses)

### 3. DICOM ↔ FHIR Converter
> **URL**: [https://ihebelgium.ehealthhub.be/TheDICOMPolice/converter](https://ihebelgium.ehealthhub.be/TheDICOMPolice/converter)

Visualize and test the bidirectional conversion between DICOM MADO manifests and FHIR R5 Document Bundles.
- **MADO to FHIR**: Converts DICOM attributes and SR tree to FHIR Composition/DocumentReference.
- **FHIR to MADO**: Reconstructs a valid binary DICOM MADO file from a FHIR JSON bundle.
- **Core Code**:
  - [`ConverterController.java`](src/main/java/be/uzleuven/ihe/service/ConverterController.java)
  - [`FHIRToMADOConverter.java`](src/main/java/be/uzleuven/ihe/dicom/convertor/dicom/FHIRToMADOConverter.java)
  - [`MADOToFHIRConverter.java`](src/main/java/be/uzleuven/ihe/dicom/convertor/fhir/MADOToFHIRConverter.java)

### 4. QIDO-RS MADO Explorer
> **URL**: [http://localhost:8080/qido-explorer](http://localhost:8080/qido-explorer)

An interactive web application for exploring DICOM studies, series, and instances via **QIDO-RS** (DICOMweb Query).
- **Workflow**:
  1. Search for studies using various DICOM query parameters (Patient ID, Study UID, Modality, Date, etc.).
  2. Drill down into series and instances with hierarchical navigation.
  3. View all DICOM tags with human-readable names and values.
  4. Export metadata as CSV or JSON.
- **Features**:
  - Configurable QIDO-RS endpoint (local or remote).
  - Study/Series/Instance level queries.
  - Tag dictionary with common DICOM attribute names.
  - Responsive UI matching the MHD MADO Viewer style.
- **Core Code**:
  - [`QIDORestController.java`](src/main/java/be/uzleuven/ihe/service/qido/QIDORestController.java) (QIDO-RS backend)
  - [`QIDOExplorerController.java`](src/main/java/be/uzleuven/ihe/service/qido/QIDOExplorerController.java) (Web page controller)
  - Frontend: `qido-explorer.html`, `qido-explorer.css`, `js/qido-explorer.js`

### 5. DICOM Downloader
> **URL**: [http://localhost:8080/dicom-downloader](http://localhost:8080/dicom-downloader)

A compact web utility to load a MADO (FHIR) manifest (URL, file or paste), inspect the Study → Series → Instance hierarchy, and download selected DICOM instances from a WADO‑RS endpoint. Supports automatic conversion of DICOM MADO files to FHIR via the built-in converter.

- Input: URL, drag‑&‑drop (.json / .dcm), or paste.
- Key features: hierarchical tree view, select-by-study/series/instance, key-image highlighting, progress reporting, File System Access API (native folder save) with ZIP fallback.
- Download settings: export folder name, naming schemes (SOP UID / numbered / Series_Instance), option to include the source manifest.

**Core files**: `dicom-downloader.html`, `dicom-downloader.css`, `js/dicom-downloader.js` (frontend) and [`ConverterController.java`](src/main/java/be/uzleuven/ihe/service/ConverterController.java) for on-server conversion.

---

## 🖥️ Command Line Tools

Useful for batch processing, scripting, or headless environments.

### 1. Validation CLI
Validate generic DICOM files or specific profiles.

**Source**: [`CLIDICOMVerify.java`](src/main/java/be/uzleuven/ihe/dicom/validator/CLIDICOMVerify.java)

```bash
# Validate generic DICOM compliance
java -cp target/DICOMPolice-0.1.0-SNAPSHOT.jar be.uzleuven.ihe.dicom.validator.CLIDICOMVerify file.dcm

# Validate against MADO profile with verbose output
java -cp target/DICOMPolice-0.1.0-SNAPSHOT.jar be.uzleuven.ihe.dicom.validator.CLIDICOMVerify --profile IHEMADO -v file.dcm
```

### 2. MADO/KOS from SCU (Query PACS)
Query a PACS (C-FIND) and generate a local manifest file referencing the remote images. This tool can query for specific studies or crawl a date range to generate manifests in batches.

**Source**: [`SCUManifestCli.java`](src/main/java/be/uzleuven/ihe/dicom/creator/scu/cli/SCUManifestCli.java)

**Note on Compatibility**: The examples below often reference an **Orthanc** server (which we highly respect and use for testing <3). However, this tool relies only on standard DICOM C-FIND and WADO-RS, so it works with **any** compliant PACS/VNA (SCP).

```bash
# Example: Batch create MADO manifests for all studies in a date range (30-day windows)
java -cp target/DICOMPolice-0.1.0-SNAPSHOT.jar be.uzleuven.ihe.dicom.creator.scu.cli.SCUManifestCli ^
  --type mado ^
  -aec ORTHANC ^
  -aet DICOMPOLICE ^
  --host 172.20.240.184 ^
  --port 4242 ^
  --begin-date 1992-01-01 ^
  --end-date 2026-01-01 ^
  --window-days 30 ^
  --retrieve-location-uid 1.3.6.1.4.1.21297.150.1.2 ^
  --issuer 1.3.6.1.4.1.21297.100.1.1 ^
  --accissuer 1.3.6.1.4.1.21297.120.1.1 ^
  --wado "https://ihebelgium.ehealthhub.be/orthanc/dicom-web/wado-rs/studies" ^
  --out-dir .\MADO_FROM_SCU\
```

### 3. Converter CLI
Batch convert files between formats.

**Source**: [`MADOBatchConverter.java`](src/main/java/be/uzleuven/ihe/dicom/convertor/fhir/MADOBatchConverter.java)

```bash
# Batch convert directory of DICOM MADO files to FHIR JSON
java -cp target/DICOMPolice-0.1.0-SNAPSHOT.jar be.uzleuven.ihe.dicom.convertor.fhir.MADOBatchConverter \
  ./dicom-input-dir ./fhir-output-dir
```

**Source**: [`ConvertFHIRToMADOApp.java`](src/main/java/be/uzleuven/ihe/dicom/convertor/dicom/ConvertFHIRToMADOApp.java)

```bash
# Convert single FHIR Bundle to DICOM MADO
java -cp target/DICOMPolice-0.1.0-SNAPSHOT.jar be.uzleuven.ihe.dicom.convertor.dicom.ConvertFHIRToMADOApp \
  bundle.json output.dcm
```

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

## Third Party Notices

The following third party notices are reproduced here for convenience. For the complete list, see [`THIRD_PARTY_NOTICES`](THIRD_PARTY_NOTICES).

### Primary Project License

**License:** MIT License  
**Applies to:** All code in DICOMPolice (except where noted below in third-party notices)

### Third-Party Libraries

**Apache License 2.0:**
- Spring Boot (org.springframework.boot)
- HAPI FHIR (ca.uhn.hapi.fhir)
- Jackson JSON Processor (com.fasterxml.jackson)

**Mozilla Public License 1.1 (MPL 1.1):**
- dcm4che (org.dcm4che) - Source: https://github.com/dcm4che/dcm4che

**MIT License:**
- OHIF (Open Health Imaging Foundation)

**Eclipse Public License 2.0 (EPL 2.0):**
- Jakarta Servlet API (jakarta.servlet)

**PixelMed Publishing (BSD-Style License):**
- Portions derived from PixelMed dciodvfy.cc
- Applies to: CLIDICOMVerify.java, IODValidator.java, IODValidatorFactory.java

### Acknowledgements

The samples and demos provided in this project were developed and tested against an [Orthanc PACS server](https://www.orthanc-server.com/). We have great respect for the Orthanc project and its contributors.

**Note:** DICOMPolice does not use Orthanc code and does not depend on Orthanc specifically. The SCU / MADO generation tools are standard-compliant and will work against any DICOM SCP (Service Class Provider) or WADO-RS capable server.


## License

This project is licensed under the **MIT License**. See [LICENSE](LICENSE) for the full license text.

Third-party libraries used by this project retain their original licenses. See [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES) for complete details.
