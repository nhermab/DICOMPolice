# DICOMPolice

**A conformance testing toolkit for IHE imaging manifests: MADO and XDS-I.b KOS**

Validator · Test-data generator · Actor simulators · DICOM ↔ FHIR converter · Web test bench

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
![Java 17](https://img.shields.io/badge/Java-17-orange.svg)
![Spring Boot 3.2](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen.svg)
![dcm4che 5.31](https://img.shields.io/badge/dcm4che-5.31-lightgrey.svg)
![HAPI FHIR 8.2](https://img.shields.io/badge/HAPI%20FHIR-8.2-red.svg)

**Author:** Nick Hermans ([nick.hermans@uzleuven.be](mailto:nick.hermans@uzleuven.be))  
**Affiliation:** University Hospitals Leuven (UZ Leuven), IHE Belgium  
**Public instance:** <https://ihebelgium.ehealthhub.be/TheDICOMPolice/>

---

## Abstract

Imaging manifests reference DICOM studies from document-sharing infrastructures. Two
kinds are in use: the DICOM Key Object Selection (KOS) document of IHE XDS-I.b, and the
newer IHE Radiology *Manifest-based Access to DICOM Objects* (MADO) profile. A MADO
manifest has to satisfy the DICOM IOD rules, the SR content template constraints
(TID 2010 / TID 1600), IHE profile-specific requirements and, when exchanged through FHIR,
a consistent mapping to FHIR resources. Few tools check all of these layers, so
conformance problems often show up only during Connectathon testing or in production.

**DICOMPolice** is an open-source test bench for these manifests. At its core is a
rule-based **validator** for KOS and MADO objects. Around the validator sit a **test-data
generator**, **simulators** of the relevant IHE actors (MHD Document Responder, DICOM
Query/Retrieve SCP, DICOMweb QIDO-RS/WADO-RS endpoints), a **bidirectional DICOM ↔ FHIR
converter**, and a set of **web applications**. The web applications show what MADO makes
possible for downstream clinical use cases. The toolkit is maintained by IHE Belgium. It is
meant for implementers, testers and profile authors who work on cross-enterprise imaging
exchange.

> **Intended use.** DICOMPolice is a testing and research tool. It is **not** a medical
> device and must not be used for clinical decision-making or to process real patient data
> outside a controlled test environment.

---

## Table of Contents

1. [Purpose and Scope](#1-purpose-and-scope)
2. [Standards Coverage](#2-standards-coverage)
3. [Architecture](#3-architecture)
4. [Components](#4-components)
   - 4.1 [Validator](#41-validator)
   - 4.2 [Test-Data Generator](#42-test-data-generator)
   - 4.3 [Actor Simulators](#43-actor-simulators)
   - 4.4 [DICOM ↔ FHIR Converter](#44-dicom--fhir-converter)
   - 4.5 [Web Test Bench](#45-web-test-bench)
5. [Getting Started](#5-getting-started)
6. [Command-Line Reference](#6-command-line-reference)
7. [REST API Reference](#7-rest-api-reference)
8. [Configuration](#8-configuration)
9. [Limitations](#9-limitations)
10. [Citation](#10-citation)
11. [License and Acknowledgements](#11-license-and-acknowledgements)

---

## 1. Purpose and Scope

DICOMPolice supports the following activities:

| Activity | What DICOMPolice provides |
|---|---|
| **Conformance testing** | Validation of KOS and MADO instances against DICOM, IHE XDS-I.b and IHE MADO rules, with a detailed per-rule report. |
| **Connectathon / Gazelle integration** | A validation service that implements the Gazelle *External Validation Service* API (v2), so it can be plugged into IHE test management tooling. |
| **Test-data production** | Synthetic but standards-conformant KOS/MADO objects, and real-world manifests generated from any PACS through C-FIND. |
| **Actor simulation** | MHD Document Responder, DICOM Q/R SCP and QIDO-RS/WADO-RS endpoints backed by MADO manifests. Peer systems can be tested without a full production stack. |
| **Profile development** | A reference implementation for the MADO DICOM ↔ FHIR mapping, including round-trip testing, to support the evolution of the IHE RAD MADO profile and the HL7 Europe Imaging Manifest IG. |
| **Demonstration** | Browser applications that show downstream use cases (timelines, prior selection, dose tracking, download, viewing) built on top of MADO. |

---

## 2. Standards Coverage

| Standard / Profile | Role in DICOMPolice |
|---|---|
| DICOM PS3.3 – Key Object Selection Document IOD | IOD and module validation |
| DICOM PS3.16 – TID 2010 (Key Object Selection), TID 1600 (Image Library) | SR content-tree template validation |
| DICOM PS3.10 – Media storage and file format | Part 10 file meta-information checks |
| DICOM PS3.4 / PS3.7 – C-ECHO, C-FIND, C-MOVE, C-STORE | SCU manifest generation; SCP simulator |
| DICOM PS3.18 – QIDO-RS, WADO-RS, WADO-URI | DICOMweb simulator and proxy endpoints |
| IHE RAD XDS-I.b (Imaging Manifest) | KOS manifest validation and generation |
| IHE RAD MADO – Manifest-based Access to DICOM Objects | MADO validation, generation, conversion |
| IHE ITI MHD – ITI-66, ITI-67, ITI-68 | MHD Document Responder simulator; MHD Document Consumer client |
| HL7 FHIR R4 / R5 | MHD resources; MADO FHIR Document Bundle (Composition, ImagingStudy, DocumentReference) |
| HL7 Europe Imaging Manifest IG (`EuMadoComposition`) | Narrative and composition generation rules |
| Gazelle External Validation Service API v2 | Validation web service interface |

The MADO specification used during development is included in this repository as
[`IHE_RAD_MADO.pdf`](IHE_RAD_MADO.pdf).

---

## 3. Architecture

```
                    ┌──────────────────────────────────────────────────────────┐
                    │                       DICOMPolice                        │
                    │                                                          │
  DICOM file ──────►│  VALIDATOR  (Part 10 · IOD · TID 2010/1600 · XDS-I.b ·   │──► Validation report
  (upload/CLI/API)  │              MADO · timezone · retrieval · signatures)   │    (Gazelle JSON / text)
                    │                                                          │
                    │  GENERATOR  (synthetic samples · SCU-driven · KIN)       │──► KOS / MADO .dcm
                    │                                                          │
  FHIR Bundle ◄────►│  CONVERTER  (MADO DICOM  ⇄  FHIR R5 Document Bundle)     │
                    │                                                          │
                    │  SIMULATORS                                              │
  MHD Consumer ────►│   · MHD Document Responder  (ITI-66/67/68)  ─┐           │
  DICOM SCU ───────►│   · MADO Q/R SCP (C-FIND / C-MOVE)           ├── C-FIND ─┼──► PACS / VNA
  DICOMweb client ─►│   · QIDO-RS / WADO-RS / WADO-URI endpoints  ─┘  WADO-RS  │    (any DICOM SCP)
                    │                                                          │
  Browser ─────────►│  WEB TEST BENCH  (validator UI + demo applications)      │
                    └──────────────────────────────────────────────────────────┘
```

The application is a single Spring Boot service. DICOM processing uses **dcm4che 5** and
FHIR processing uses **HAPI FHIR**. All components share one MADO/KOS object model, so a
manifest produced by the generator, the SCU creator or the FHIR converter goes through the
same validator.

---

## 4. Components

### 4.1 Validator

The validator is the core of the toolkit. It applies a layered set of checks to KOS and
MADO instances. Each finding reports its severity and the rule it comes from.

| Layer | Implementation (`dicom/validator/validation/`) |
|---|---|
| Part 10 file structure | `Part10FileValidator` |
| IOD and module rules (KOS, Key Object Document, MADO manifest) | `iod/KeyObjectSelectionValidator`, `iod/KeyObjectModuleValidator`, `iod/MADOManifestValidator` |
| XDS-I.b manifest profile rules | `iod/KeyObjectProfileValidator`, `KOSComplianceChecker` |
| MADO profile rules and Appendix B | `MADOComplianceChecker`, `MADOAppendixBValidator`, `MADOTemplateValidator` |
| TID 1600 Image Library (root, study, series, instance) | `tid1600/*` |
| Evidence ↔ content-tree consistency | `EvidenceOrphanValidator` |
| Retrieval information (Retrieve Location UID, Retrieve URL) | `MADORetrievalValidator` |
| Timezone consistency | `TimezoneValidator`, `MADOTimezoneValidator` |
| Encoding and structural sanity | `AdvancedEncodingValidator`, `AdvancedStructureValidator` |
| Digital signatures | `DigitalSignatureValidator` |

**Validation profiles**

| Profile ID (REST / Gazelle) | CLI `--profile` | Scope |
|---|---|---|
| `IHE.RAD.XDSI.KOS` | `IHEXDSIManifest` | IHE XDS-I.b Key Object Selection manifest |
| `IHE.RAD.MADO` | `IHEMADO` | IHE MADO manifest with descriptive metadata (TID 1600) |

The validator can be reached in three ways: the drag-and-drop web page (which also
renders the SR content tree), the command-line tool (§6.1), and the Gazelle-compatible REST
API (§7.1).

### 4.2 Test-Data Generator

| Generator | Description | Entry point |
|---|---|---|
| Synthetic samples | Random but conformant KOS and MADO instances in several sizes, with built-in self-validation. | `creator/samples/IHEKOSSampleCreator`, `IHEMADOSampleCreator`, `GenerateAndValidate*` |
| SCU-driven manifests | Queries any PACS/VNA with C-FIND (study → series → instance) and builds a KOS or MADO that references the remote images through WADO-RS. Supports single studies, patient/accession/date queries and batch crawling of date ranges. | `creator/scu/cli/SCUManifestCli` |
| Key Image Note upgrade | Reads a MADO, reproducibly selects a referenced image, creates a separate KIN/KOS instance, and writes a new MADO revision that references the KIN in its evidence, TID 2010 content and TID 1600 Image Library. | `creator/kin/MadoKinUpgradeCli` |
| Study inventory export | Exports study-level C-FIND results to CSV, day by day and modality by modality. | `creator/scu/cli/StudyCsvExportCli` |

### 4.3 Actor Simulators

| Simulator | Simulated actor / transactions | Backed by |
|---|---|---|
| **MHD Document Responder** (`/fhir`) | IHE MHD Document Responder: CapabilityStatement, ITI-66 (List), ITI-67 (DocumentReference), ITI-68 (Binary). Also serves the converted FHIR Document Bundle. | Live C-FIND against the configured PACS, with MADO/KOS built on the fly |
| **MADO Q/R SCP** (DIMSE, default port `11112`) | DICOM Query/Retrieve SCP: C-ECHO, C-FIND, C-MOVE. Lets legacy DICOM clients reach data that is exposed only through MHD + MADO. | MHD Document Consumer (ITI-67/68) + WADO-RS retrieval + C-STORE forwarding |
| **DICOMweb endpoints** (`/dicomweb`) | QIDO-RS (studies/series/instances), optional WADO-RS proxy, WADO-URI proxy | MADO metadata obtained through MHD |

The full MHD API, with request/response examples and client code in JavaScript, Python
and Java, is documented in
[`src/main/java/be/uzleuven/ihe/service/MHD/README.md`](src/main/java/be/uzleuven/ihe/service/MHD/README.md).

### 4.4 DICOM ↔ FHIR Converter

This is a reference implementation of the MADO DICOM ↔ FHIR mapping:

- **MADO → FHIR:** turns the DICOM attributes and SR content tree into a FHIR R5 Document
  Bundle (Composition, ImagingStudy, DocumentReference, Patient, …), following the IHE
  MADO and HL7 Europe Imaging Manifest structure definitions.
- **FHIR → MADO:** rebuilds a valid binary DICOM MADO instance from a FHIR Bundle.
- **Round-trip testing:** a DICOM → FHIR → DICOM comparison endpoint, with deterministic
  UUID generation so that repeated conversions give identical resource identifiers.

Implementation: `convertor/fhir/MADOToFHIRConverter`, `convertor/dicom/FHIRToMADOConverter`.

### 4.5 Web Test Bench

Local paths are shown below. On the public instance, each path is prefixed with
`/TheDICOMPolice`.

**Core testing tools**

| Path | Application | Purpose |
|---|---|---|
| `/` | **MADO / KOS Validator** | Drag-and-drop validation, detailed findings, SR tree visualisation |
| `/xtehdsMADO` | **MHD MADO Viewer** | MHD Document Consumer UI: search (ITI-67), inspect and retrieve (ITI-68) manifests; supports OAuth2-secured endpoints |
| `/converter` | **DICOM ↔ FHIR Bridge** | Interactive bidirectional conversion and round-trip comparison |
| `/qido-explorer` | **QIDO-RS Explorer** | Hierarchical study/series/instance browsing, tag inspection, CSV/JSON export |
| `/dicom-downloader` | **DICOM Downloader** | Loads a MADO (DICOM or FHIR), selects instances and downloads them over WADO-RS |

**Use-case demonstrators**

| Path | Application | Purpose |
|---|---|---|
| `/imaging-timeline` | **Cross-Community Imaging Timeline** | Merges study metadata from several communities into one timeline and flags missing priors |
| `/clinical-prefetcher` | **Smart Clinical Context Prefetcher** | Ranks prior studies by relevance to the reason for visit with a local LLM (Ollama) |
| `/device-finder` | **Device & Implant Imaging Finder** | Finds imaging that shows implanted devices and reconciles it with an EHR device list |
| `/radiation-dose` | **Radiation Dose Passport** | Summarises cumulative exposure from RDSR objects retrieved through DICOMweb |
| `/lab-observations` | **Lab Observations Explorer** | Searches FHIR `Observation` resources by LOINC code through the same MHD endpoint |

---

## 5. Getting Started

### 5.1 Prerequisites

- Java 17 or later
- Apache Maven 3.6 or later
- *(Optional)* A DICOM PACS/VNA with C-FIND and WADO-RS (for example Orthanc or dcm4chee),
  needed for the SCU generator and the MHD simulator
- *(Optional)* A local [Ollama](https://ollama.com/) instance, needed for the AI-assisted demonstrators

### 5.2 Build

```bash
git clone https://github.com/nhermab/DICOMPolice.git
cd DICOMPolice
mvn clean package
```

The build produces an executable JAR: `target/DICOMPolice-0.1.0-SNAPSHOT.jar`.

### 5.3 Run the web service

```bash
mvn spring-boot:run
# or
java -jar target/DICOMPolice-0.1.0-SNAPSHOT.jar
```

Open <http://localhost:8080/>. To use the production settings (context path
`/TheDICOMPolice`), start with `--spring.profiles.active=prod`.

---

## 6. Command-Line Reference

The JAR is a Spring Boot executable archive. To run a command-line tool other than the
web service, use Spring Boot's `PropertiesLauncher` and pass the main class through
`loader.main`. The examples below use this helper:

```bash
JAR=target/DICOMPolice-0.1.0-SNAPSHOT.jar
dp() { java -Dloader.main="$1" -cp "$JAR" org.springframework.boot.loader.launch.PropertiesLauncher "${@:2}"; }
```

### 6.1 Validate a manifest

```bash
dp be.uzleuven.ihe.dicom.validator.CLIDICOMVerify --profile IHEMADO -v mado.dcm
dp be.uzleuven.ihe.dicom.validator.CLIDICOMVerify --profile IHEXDSIManifest kos.dcm
```

The exit code is `0` when every validation passes, so the tool can serve as a gate in
CI pipelines.

### 6.2 Generate manifests from a PACS (C-FIND)

```bash
# One study
dp be.uzleuven.ihe.dicom.creator.scu.cli.SCUManifestCli \
  --type mado -aec ORTHANC -aet DICOMPOLICE --host pacs.example.org --port 4242 \
  --study-uid 1.2.3.4.5 \
  --wado https://pacs.example.org/dicom-web/studies \
  --out-dir ./manifests/

# Batch crawl of a date range (7-day query windows)
dp be.uzleuven.ihe.dicom.creator.scu.cli.SCUManifestCli \
  --type mado -aec ORTHANC -aet DICOMPOLICE --host pacs.example.org --port 4242 \
  --begin-date 2020-01-01 --end-date 2026-01-01 --window-days 7 \
  --retrieve-location-uid 1.3.6.1.4.1.21297.150.1.2 \
  --issuer 1.3.6.1.4.1.21297.100.1.1 --accissuer 1.3.6.1.4.1.21297.120.1.1 \
  --wado https://pacs.example.org/dicom-web/studies \
  --out-dir ./manifests/ --max-results 10000
```

Run the tool without arguments to print the full option list, including output patterns,
NDJSON streaming and issuer settings.

### 6.3 Convert between DICOM and FHIR

```bash
# Directory of MADO .dcm files → FHIR JSON
dp be.uzleuven.ihe.dicom.convertor.fhir.MADOBatchConverter ./dicom-in ./fhir-out

# Single FHIR Bundle → DICOM MADO
dp be.uzleuven.ihe.dicom.convertor.dicom.ConvertFHIRToMADOApp bundle.json mado.dcm
```

### 6.4 Add a Key Image Note to a MADO

```bash
dp be.uzleuven.ihe.dicom.creator.kin.MadoKinUpgradeCli \
  --input mado-original.dcm \
  --kin-out example-kin.dcm \
  --mado-out mado-with-kin.dcm \
  --seed 12345 \
  --description "Automatically selected test key image"
```

The KIN is written as a separate DICOM object; it is not embedded in the MADO. By default
the tool assumes that the KIN is stored by the same Imaging Document Source as the selected
image, and it copies that series' Retrieve Location UID and Retrieve URL. If this is not the
case, pass `--retrieve-location-uid` (and optionally `--retrieve-url`). Existing files are
overwritten only when `--overwrite` is given.

---

## 7. REST API Reference

### 7.1 Validation service (Gazelle External Validation Service v2)

| Method | Path | Description |
|---|---|---|
| `GET` | `/validation/v2/profiles` | Lists the supported validation profiles |
| `POST` | `/validation/v2/validate` | Validates a Base64-encoded DICOM object and returns a Gazelle `ValidationReport` |

```bash
curl -s http://localhost:8080/validation/v2/validate \
  -H "Content-Type: application/json" \
  -d '{
        "validationProfileId": "IHE.RAD.MADO",
        "inputs": [{ "id": "input1", "content": "'"$(base64 -w0 mado.dcm)"'" }]
      }'
```

If `inputs` is empty, the service generates a random sample of the requested type and
validates it. This makes a quick self-test of the service possible.

### 7.2 Other endpoints

| Base path | Purpose |
|---|---|
| `/fhir` | MHD Document Responder (FHIR R4): `metadata`, `DocumentReference`, `List`, `Binary`, `Bundle` |
| `/dicomweb` | QIDO-RS search, optional WADO-RS proxy, WADO-URI proxy |
| `/api/visualizer` | Parses and validates an uploaded file for the web validator (multipart) |
| `/api/converter` | DICOM ↔ FHIR conversion and round-trip endpoints |
| `/api/scp` | Starts, stops and reports the status of the MADO Q/R SCP |
| `/api/mhd-proxy` | Authenticating reverse proxy for secured upstream MHD endpoints |

---

## 8. Configuration

All settings live in [`application.properties`](src/main/resources/application.properties)
(development) and [`application-prod.properties`](src/main/resources/application-prod.properties)
(production). The main groups are:

| Prefix | Configures |
|---|---|
| `mhd.*` | Backend PACS connection (AETs, host, port), WADO-RS base URL, patient/accession issuers, Retrieve Location UID, MHD coding (format/type/class codes) |
| `mado.scp.*` | Q/R SCP AE title, port, auto-start, timeouts, upstream MHD/WADO-RS endpoints, parallelism |
| `dicom.ae-directory.*` | C-MOVE destination AE directory |
| `dicom.cache.*` | In-memory cache for retrieved instances |
| `qido.rs.*` | DICOMweb base URL and WADO-RS proxy mode |
| `ollama.*` | Local LLM endpoint and model for the AI demonstrators |
| `mhd.proxy.*` | Upstream allow-list and timeout for the authenticating proxy |

Any property can be overridden on the command line, for example
`--mhd.remote-host=pacs.example.org`.

---

## 9. Limitations

- **Evolving specifications.** IHE RAD MADO and the HL7 Europe Imaging Manifest IG are
  still under active development. The validator rules follow the versions current at the
  time of each commit (see the git history), and they may lag behind or run ahead of the
  published texts.
- **Validator coverage.** The validator targets KOS and MADO manifests. It is not a
  general-purpose DICOM IOD validator for image objects.
- **Test environments only.** The simulators do not implement authentication, audit
  logging (ATNA) or consent management. Do not expose them to production networks or
  production data.
- **PACS interoperability.** Development and demonstrations used an Orthanc server. The
  SCU and WADO-RS components rely only on standard DICOM and DICOMweb services and are
  expected to work with any conformant PACS/VNA.

---

## 10. Citation

If you use DICOMPolice in academic work, Connectathon reports or profile development,
please cite it as:

```bibtex
@software{hermans_dicompolice,
  author       = {Hermans, Nick},
  title        = {{DICOMPolice}: A Conformance Testing Toolkit for IHE MADO and XDS-I.b Imaging Manifests},
  organization = {University Hospitals Leuven (UZ Leuven) and IHE Belgium},
  url          = {https://github.com/nhermab/DICOMPolice},
  license      = {MIT},
  year         = {2026}
}
```

---

## 11. License and Acknowledgements

DICOMPolice is released under the [MIT License](LICENSE). Third-party components keep
their original licenses; see [`THIRD_PARTY_NOTICES`](THIRD_PARTY_NOTICES) for the full
list.

| Component | License |
|---|---|
| Spring Boot, HAPI FHIR, Jackson | Apache License 2.0 |
| dcm4che | Mozilla Public License 1.1 |
| OHIF Viewer | MIT |
| Jakarta Servlet API | Eclipse Public License 2.0 |
| PixelMed `dciodvfy` (portions of `CLIDICOMVerify`, `IODValidator`, `IODValidatorFactory`) | PixelMed BSD-style license |

**Acknowledgements.** We thank the [Orthanc](https://www.orthanc-server.com/) project,
whose server was used as the reference PACS during development and demonstrations.
DICOMPolice contains no Orthanc code and does not depend on it. We also thank the
[dcm4che](https://github.com/dcm4che/dcm4che), [HAPI FHIR](https://hapifhir.io/),
[OHIF](https://ohif.org/) and PixelMed communities. Finally, we thank the IHE Radiology
MADO authors and the IHE Belgium community for their feedback during Connectathon and
Projectathon testing.

---

<sub>© 2026 Nick Hermans, UZ Leuven / IHE Belgium · Contact: [nick.hermans@uzleuven.be](mailto:nick.hermans@uzleuven.be)</sub>
