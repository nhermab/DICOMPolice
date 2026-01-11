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

## Rest API

See [API_README.md](API_README.md).

## Architecture

See [ARCHITECTURE.md](ARCHITECTURE.md).

## Third Party Notices

The following third party notices are reproduced here for convenience. For the original, see `THIRD_PARTY_NOTICES`.

```text
DICOMPolice / MADOValidator - Third Party Notices

This project is distributed under the GNU General Public License, version 2 (GPLv2). See the file "LICENSE".

Portions of this project are based on, or derived from, third-party software listed below.
Where third-party notices and license terms apply, they are reproduced here, and/or referenced.
Redistributors must comply with the terms for this project (GPLv2) and any applicable third-party terms.

-------------------------------------------------------------------------------
1) PixelMed (Portions based on dciodvfy.cc)
-------------------------------------------------------------------------------
The following source files contain portions based on PixelMed's dciodvfy.cc:

- src/main/java/be/uzleuven/ihe/dicom/validator/CLIDICOMVerify.java
- src/main/java/be/uzleuven/ihe/dicom/validator/validation/iod/IODValidator.java
- src/main/java/be/uzleuven/ihe/dicom/validator/validation/iod/IODValidatorFactory.java

PixelMed license (as included in dciodvfy.cc):

Copyright (c) 1993-2024, David A. Clunie DBA PixelMed Publishing. All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are
permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this list of
   conditions and the following disclaimers.

2. Redistributions in binary form must reproduce the above copyright notice, this list of
   conditions and the following disclaimers in the documentation and/or other materials
   provided with the distribution.

3. Neither the name of PixelMed Publishing nor the names of its contributors may
   be used to endorse or promote products derived from this software.

This software is provided by the copyright holders and contributors "as is" and any
express or implied warranties, including, but not limited to, the implied warranties
of merchantability and fitness for a particular purpose are disclaimed. In no event
shall the copyright owner or contributors be liable for any direct, indirect, incidental,
special, exemplary, or consequential damages (including, but not limited to, procurement
of substitute goods or services; loss of use, data or profits; or business interruption)
however caused and on any theory of liability, whether in contract, strict liability, or
tort (including negligence or otherwise) arising in any way out of the use of this software,
even if advised of the possibility of such damage.

This software has neither been tested nor approved for clinical use or for incorporation in
a medical device. It is the redistributor's or user's responsibility to comply with any
applicable local, state, national or international regulations.

-------------------------------------------------------------------------------
2) dcm4che (library dependency)
-------------------------------------------------------------------------------
This project depends on dcm4che libraries (e.g., org.dcm4che:dcm4che-core and org.dcm4che:dcm4che-net),
which are licensed under GPLv2. Please refer to the dcm4che project for the complete license text and notices:

- https://www.dcm4che.org/
- https://github.com/dcm4che/dcm4che

-------------------------------------------------------------------------------
3) HAPI FHIR (library dependency)
-------------------------------------------------------------------------------
This project relies on the HAPI FHIR library (http://hapifhir.io/), which is licensed under the
Apache License, Version 2.0.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

-------------------------------------------------------------------------------
4) Acknowledgements - Orthanc Server
-------------------------------------------------------------------------------
The samples and demos provided in this project (specifically the SCU client tests and WADO-RS retrieval examples)
were developed and tested against an Orthanc PACS server (https://www.orthanc-server.com/).

We have great respect for the Orthanc project and its contributors.

Note: DICOMPolice does not use Orthanc code and does not depend on Orthanc specifically.
The SCU / MADO generation tools are standard-compliant and will work against any DICOM SCP
(Service Class Provider) or WADO-RS capable server.
```

## License

This project is licensed under **GPLv2** (to comply with dcm4che’s license terms). See [LICENSE](LICENSE).
