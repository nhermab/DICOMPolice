package be.uzleuven.ihe.service.MHD.fhir.provider;

import be.uzleuven.ihe.service.MHD.config.MHDConfiguration;
import be.uzleuven.ihe.service.MHD.dicom.DicomBackendService;
import be.uzleuven.ihe.service.MHD.fhir.DicomToFhirMapper;
import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.param.ReferenceAndListParam;
import ca.uhn.fhir.rest.param.ReferenceOrListParam;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.SimpleBundleProvider;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.IdType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * HAPI FHIR Resource Provider for DocumentReference.
 * Implements ITI-67 (Find Document References) for MHD Document Responder.
 */
@Component
public class DocumentReferenceProvider implements IResourceProvider {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentReferenceProvider.class);

    private final DicomBackendService dicomService;
    private final MHDConfiguration config;

    @Autowired
    public DocumentReferenceProvider(DicomBackendService dicomService, MHDConfiguration config) {
        this.dicomService = dicomService;
        this.config = config;
    }

    @Override
    public Class<? extends IBaseResource> getResourceType() {
        return DocumentReference.class;
    }

    /**
     * Read a single DocumentReference by ID.
     * ID is a base64-encoded Study Instance UID.
     */
    @Read
    public DocumentReference read(@IdParam IdType id) {
        LOG.info("Reading DocumentReference: {}", id.getIdPart());

        String studyInstanceUid = DicomToFhirMapper.decodeStudyUidFromFhirId(id.getIdPart());

        try {
            Attributes study = dicomService.getStudyMetadata(studyInstanceUid);
            if (study == null) {
                throw new ResourceNotFoundException("DocumentReference not found for ID: " + id.getIdPart());
            }

            // Generate manifest to get size/hash
            byte[] manifestBytes = dicomService.createMADOManifestAsBytes(studyInstanceUid,
                study.getString(Tag.PatientID));

            return DicomToFhirMapper.mapStudyToDocumentReference(study, config, manifestBytes);

        } catch (IOException e) {
            LOG.error("Error reading DocumentReference", e);
            throw new InternalErrorException("Error retrieving study metadata: " + e.getMessage());
        }
    }

    /**
     * Search for DocumentReferences.
     * Supports both GET (/fhir/DocumentReference?params) and
     * POST (/fhir/DocumentReference/_search with application/x-www-form-urlencoded body).
     * Implements ITI-67 (Find Document References) search parameters with MADO IG R4 extensions.
     *
     * @param patient Patient reference or identifier (supports chaining with patient.identifier)
     * @param status Document status
     * @param date Creation date range
     * @param studyInstanceUid MADO-specific: Study Instance UID (custom search parameter, token type)
     * @param accessionNumber MADO-specific: Accession Number (custom search parameter)
     * @param modality MADO-specific: Modality filter (custom search parameter)
     * @param bodysite MADO-specific: Anatomical region filter (custom search parameter)
     * @param format Format code filter (e.g. urn:ihe:rad:MADO:fhir-manifest:2026)
     * @param relatedIdentifier Standard search: related:identifier for Study Instance UID or Accession Number
     * @return Bundle of matching DocumentReferences
     */
    @Search
    public IBundleProvider search(
            @OptionalParam(name = DocumentReference.SP_PATIENT, chainWhitelist = {"", "identifier"}) ReferenceAndListParam patient,
            @OptionalParam(name = DocumentReference.SP_STATUS) TokenParam status,
            @OptionalParam(name = DocumentReference.SP_DATE) DateRangeParam date,
            @OptionalParam(name = "study-instance-uid") TokenParam studyInstanceUid,
            @OptionalParam(name = "accession-number") TokenParam accessionNumber,
            @OptionalParam(name = "modality") TokenParam modality,
            @OptionalParam(name = "bodysite") TokenParam bodysite,
            @OptionalParam(name = "format") TokenParam format,
            @OptionalParam(name = "related:identifier") TokenParam relatedIdentifier) {

        LOG.info("Searching DocumentReferences - patient: {}, studyUid: {}, accession: {}, format: {}",
            patient, studyInstanceUid, accessionNumber, format);

        try {
            // Extract search parameters
            String patientId = extractPatientId(patient);
            String studyUid = studyInstanceUid != null ? stripUrnOidPrefix(studyInstanceUid.getValue()) : null;
            String accession = accessionNumber != null ? accessionNumber.getValue() : null;
            String modalityValue = modality != null ? modality.getValue() : null;

            // Handle related:identifier as a fallback for study-instance-uid or accession-number
            if (relatedIdentifier != null && relatedIdentifier.getValue() != null) {
                String relatedValue = relatedIdentifier.getValue();
                // If the value looks like a DICOM UID (contains dots, starts with urn:oid: or is numeric dotted)
                if (studyUid == null && (relatedValue.startsWith("urn:oid:") || relatedValue.matches("[0-9]+\\.[0-9.]+"))) {
                    studyUid = relatedValue.replace("urn:oid:", "");
                } else if (accession == null) {
                    accession = relatedValue;
                }
            }

            // Extract date range
            String dateFrom = null;
            String dateTo = null;
            if (date != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
                if (date.getLowerBoundAsInstant() != null) {
                    dateFrom = sdf.format(date.getLowerBoundAsInstant());
                }
                if (date.getUpperBoundAsInstant() != null) {
                    dateTo = sdf.format(date.getUpperBoundAsInstant());
                }
            }

            // Perform DICOM search
            List<Attributes> studies = dicomService.searchStudies(
                patientId, accession, studyUid, modalityValue, dateFrom, dateTo);

            // Map to DocumentReferences - per MADO IG, return BOTH FHIR and KOS DocumentReferences
            List<IBaseResource> results = new ArrayList<>();
            for (Attributes study : studies) {
                try {
                    // For search, we don't generate the full manifest (expensive)
                    // Instead, we provide the DocumentReferences without size/hash

                    // FHIR Imaging Manifest DocumentReference (application/fhir+json)
                    DocumentReference fhirDocRef = DicomToFhirMapper.mapStudyToDocumentReference(study, config, null);
                    results.add(fhirDocRef);

                    // DICOM KOS DocumentReference (application/dicom) - paired with the FHIR one
                    DocumentReference kosDocRef = DicomToFhirMapper.mapStudyToKosDocumentReference(study, config, null, null);
                    results.add(kosDocRef);
                } catch (Exception e) {
                    LOG.warn("Error mapping study to DocumentReference: {}",
                        study.getString(Tag.StudyInstanceUID), e);
                }
            }

            LOG.info("Found {} DocumentReferences ({} studies, both FHIR+KOS formats)", results.size(), studies.size());
            return new SimpleBundleProvider(results);

        } catch (IOException e) {
            LOG.error("Error searching DocumentReferences", e);
            throw new InternalErrorException("Error searching for studies: " + e.getMessage());
        }
    }

    /**
     * Extract patient ID from ReferenceAndListParam which supports both plain references
     * and chained identifier searches (patient.identifier=system|value).
     * The identifier system is intentionally ignored; only the value part is used for DICOM QIDO-RS queries.
     * When multiple patient values are provided (AND/OR), the first non-null value is used.
     */
    private String extractPatientId(ReferenceAndListParam patient) {
        if (patient == null) {
            return null;
        }

        for (ReferenceOrListParam orList : patient.getValuesAsQueryTokens()) {
            for (ReferenceParam ref : orList.getValuesAsQueryTokens()) {
                // Chained search: patient.identifier=system|value
                if ("identifier".equals(ref.getChain())) {
                    String value = ref.getValue();
                    if (value != null && !value.isEmpty()) {
                        // Handle system|value format - extract only the value part
                        int pipeIndex = value.indexOf('|');
                        if (pipeIndex >= 0) {
                            value = value.substring(pipeIndex + 1);
                        }
                        if (!value.isEmpty()) {
                            return value;
                        }
                    }
                } else {
                    // Plain reference: patient=Patient/123 or patient=123
                    String reference = ref.getValue();
                    if (reference != null && !reference.isEmpty()) {
                        if (reference.startsWith("Patient/")) {
                            return reference.substring(8);
                        }
                        return reference;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Strip "urn:oid:" prefix from a UID value if present.
     * MADO token searches may include this prefix per convention.
     */
    private String stripUrnOidPrefix(String value) {
        if (value == null) return null;
        if (value.startsWith("urn:oid:")) {
            return value.substring(8);
        }
        return value;
    }
}

