# IHE MHD Document Responder - MADO Implementation

This module implements an **IHE MHD (Mobile access to Health Documents) Document Responder** that provides a FHIR R4 facade over a DICOM PACS, generating **MADO (Manifest for Access to DICOM Objects)** manifests on-the-fly.

## Overview

The MHD Document Responder bridges the RESTful FHIR world with the DIMSE-based DICOM world, allowing FHIR clients to discover and retrieve imaging study information without direct DICOM connectivity.

### Supported IHE Transactions

| Transaction | Description | FHIR Resource |
|------------|-------------|---------------|
| **ITI-66** | Find Document Lists | `List` (SubmissionSet) |
| **ITI-67** | Find Document References | `DocumentReference` |
| **ITI-68** | Retrieve Document | `Binary` (MADO manifest) |

## Architecture

```
┌─────────────────────┐      ┌────────────────────────┐      ┌──────────────┐
│   FHIR Client       │─────▶│  MHD Document Responder │─────▶│  DICOM PACS  │
│  (XDS.b Consumer)   │◀─────│     (FHIR R4 Facade)    │◀─────│    (C-FIND)  │
└─────────────────────┘      └────────────────────────┘      └──────────────┘
                                       │
                                       ▼
                              ┌─────────────────┐
                              │  MADO Manifest  │
                              │    Generator    │
                              └─────────────────┘
```

## Endpoints

### FHIR Endpoints (HAPI FHIR Server)

Base URL: `http://localhost:8080/fhir`

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/metadata` | GET | CapabilityStatement |
| `/DocumentReference` | GET | Search for documents (ITI-67) |
| `/DocumentReference/{id}` | GET | Read a single document (ITI-67) |
| `/List` | GET | Search for submission sets (ITI-66) |
| `/List/{id}` | GET | Read a single submission set (ITI-66) |
| `/Binary/{id}` | GET | Retrieve MADO manifest (ITI-68) |

### Raw DICOM Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/mhd/Document/{id}` | GET | Retrieve MADO manifest as raw DICOM file |
| `/mhd/studies/{studyUid}/manifest` | GET | Retrieve MADO by Study Instance UID |
| `/mhd/health` | GET | Health check |

## Search Parameters

### DocumentReference (ITI-67)

| Parameter | Type | DICOM Mapping | Description |
|-----------|------|---------------|-------------|
| `patient` | reference | Patient ID (0010,0020) | Patient reference |
| `patient.identifier` | token | Patient ID (0010,0020) | Patient identifier |
| `status` | token | - | Document status (current) |
| `date` | date | Study Date (0008,0020) | Study date range |
| `study-instance-uid` | string | (0020,000D) | MADO: Study Instance UID |
| `accession` | token | (0008,0050) | MADO: Accession Number |
| `modality` | token | (0008,0061) | MADO: Modality filter |

### List/SubmissionSet (ITI-66)

| Parameter | Type | Description |
|-----------|------|-------------|
| `patient` | reference | Patient reference |
| `patient.identifier` | token | Patient identifier |
| `status` | token | List status |
| `code` | token | List type (submissionset) |
| `date` | date | Submission date range |

## Configuration

Configure the MHD Document Responder in `application.properties`:

```properties
# FHIR Server Base URL
mhd.fhir-base-url=http://localhost:8080/fhir

# Institution Information
mhd.institution-name=IHE Demo Hospital
mhd.manufacturer-model-name=DICOM Police MHD Responder
mhd.software-version=1.0.0

# Patient ID Issuer
mhd.patient-id-issuer-oid=1.2.840.113619.6.197
mhd.patient-id-issuer-local-namespace=HOSPITAL_A

# Accession Number Issuer
mhd.accession-number-issuer-oid=1.2.840.113619.6.197.1

# Repository Information
mhd.repository-unique-id=1.2.3.4.5.6.7.8.9.10

# DICOM Connection Settings (PACS)
mhd.calling-aet=DICOMPOLICE
mhd.called-aet=ORTHANC
mhd.remote-host=localhost
mhd.remote-port=4242
mhd.connect-timeout=5000
mhd.response-timeout=10000
```

## MADO Manifest Generation

The system generates MADO (Key Object Selection) manifests on-the-fly by:

1. Performing C-FIND queries against the PACS
2. Building a DICOM KOS with TID 1600 (Image Library) content
3. Including proper timezone offset (0008,0201)
4. Setting document title to "Manifest with Description" per CP-2595

### Key MADO Features

- **SOP Class UID**: `1.2.840.10008.5.1.4.1.1.88.59` (Key Object Selection Document Storage)
- **Document Title**: Manifest with Description (per DICOM CP-2595)
- **Timezone Offset**: Always included per MADO requirements
- **Evidence Sequence**: References all SOP Instances in the study

## Example Usage

### Search for Documents by Patient

```bash
curl "http://localhost:8080/fhir/DocumentReference?patient.identifier=12345"
```

### Search by Accession Number

```bash
curl "http://localhost:8080/fhir/DocumentReference?accession=ACC12345"
```

### Retrieve MADO Manifest

```bash
# Via FHIR Binary
curl "http://localhost:8080/fhir/Binary/{documentId}"

# Via raw DICOM endpoint
curl "http://localhost:8080/mhd/studies/1.2.3.4.5/manifest" -o manifest.dcm
```

### Get CapabilityStatement

```bash
curl "http://localhost:8080/fhir/metadata"
```

## IHE Profiles Conformance

This implementation conforms to:

- **IHE ITI MHD v4.2.3** - Mobile access to Health Documents
- **IHE RAD MADO** - Manifest for Access to DICOM Objects (supplement)
- **FHIR R4 (STU4)** - HL7 FHIR Release 4

## Security Considerations

> **Note**: Full security implementation (ATNA, IUA) is not included but placeholders are provided.

For production deployments, consider:

1. **IUA (Internet User Authorization)** - OAuth2/OIDC integration for authorization
2. **ATNA (Audit Trail and Node Authentication)** - Audit logging for all transactions
3. **TLS 1.2+** - Transport layer security
4. **SMART on FHIR** - App authorization

## Development

### Building

```bash
mvn clean package
```

### Running

```bash
mvn spring-boot:run
```

### Testing

Access the FHIR server at: http://localhost:8080/fhir

The ResponseHighlighterInterceptor provides a browser-friendly view of FHIR responses.

## Module Structure

```
src/main/java/be/uzleuven/ihe/service/MHD/
├── config/
│   ├── FhirServerConfig.java       # Spring servlet registration
│   └── MHDConfiguration.java       # Configuration properties
├── controller/
│   └── MHDRawDocumentController.java  # Raw DICOM endpoints
├── dicom/
│   └── DicomBackendService.java    # DICOM C-FIND service
├── fhir/
│   ├── DicomToFhirMapper.java      # DICOM to FHIR mapping
│   ├── MHDFhirRestfulServer.java   # HAPI FHIR server
│   └── provider/
│       ├── BinaryProvider.java          # ITI-68 provider
│       ├── CapabilityStatementProvider.java
│       ├── DocumentReferenceProvider.java  # ITI-67 provider
│       └── ListProvider.java             # ITI-66 provider
└── MHDService.java                 # High-level facade service
```

## Dependencies

- **Spring Boot 3.x** - Web framework
- **HAPI FHIR 6.x** - FHIR R4 Plain Server
- **dcm4che 5.x** - DICOM library (C-FIND, DICOM file I/O)

