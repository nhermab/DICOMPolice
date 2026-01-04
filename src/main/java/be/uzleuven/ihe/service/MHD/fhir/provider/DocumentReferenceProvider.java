package be.uzleuven.ihe.service.MHD.fhir.provider;

import be.uzleuven.ihe.service.MHD.config.MHDConfiguration;
import be.uzleuven.ihe.service.MHD.dicom.DicomBackendService;
import be.uzleuven.ihe.service.MHD.fhir.DicomToFhirMapper;
import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.param.StringParam;
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
     * Implements ITI-67 (Find Document References) search parameters.
     *
     * @param patient Patient reference or identifier
     * @param patientIdentifier Patient identifier token
     * @param status Document status
     * @param date Creation date range
     * @param studyInstanceUid MADO-specific: Study Instance UID
     * @param accessionNumber MADO-specific: Accession Number
     * @param modality MADO-specific: Modality filter
     * @return Bundle of matching DocumentReferences
     */
    @Search
    public IBundleProvider search(
            @OptionalParam(name = DocumentReference.SP_PATIENT) ReferenceParam patient,
            @OptionalParam(name = "patient.identifier") TokenParam patientIdentifier,
            @OptionalParam(name = DocumentReference.SP_STATUS) TokenParam status,
            @OptionalParam(name = DocumentReference.SP_DATE) DateRangeParam date,
            @OptionalParam(name = "study-instance-uid") StringParam studyInstanceUid,
            @OptionalParam(name = "accession") TokenParam accessionNumber,
            @OptionalParam(name = "modality") TokenParam modality) {

        LOG.info("Searching DocumentReferences - patient: {}, studyUid: {}, accession: {}",
            patientIdentifier, studyInstanceUid, accessionNumber);

        try {
            // Extract search parameters
            String patientId = extractPatientId(patient, patientIdentifier);
            String studyUid = studyInstanceUid != null ? studyInstanceUid.getValue() : null;
            String accession = accessionNumber != null ? accessionNumber.getValue() : null;
            String modalityValue = modality != null ? modality.getValue() : null;

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

            // Map to DocumentReferences
            List<IBaseResource> results = new ArrayList<>();
            for (Attributes study : studies) {
                try {
                    // For search, we don't generate the full manifest (expensive)
                    // Instead, we provide the DocumentReference without size/hash
                    DocumentReference docRef = DicomToFhirMapper.mapStudyToDocumentReference(study, config, null);
                    results.add(docRef);
                } catch (Exception e) {
                    LOG.warn("Error mapping study to DocumentReference: {}",
                        study.getString(Tag.StudyInstanceUID), e);
                }
            }

            LOG.info("Found {} DocumentReferences", results.size());
            return new SimpleBundleProvider(results);

        } catch (IOException e) {
            LOG.error("Error searching DocumentReferences", e);
            throw new InternalErrorException("Error searching for studies: " + e.getMessage());
        }
    }

    /**
     * Extract patient ID from various FHIR reference/identifier formats.
     */
    private String extractPatientId(ReferenceParam patient, TokenParam patientIdentifier) {
        if (patientIdentifier != null) {
            return patientIdentifier.getValue();
        }

        if (patient != null) {
            String reference = patient.getValue();
            // Could be "Patient/123" or just "123"
            if (reference != null) {
                if (reference.startsWith("Patient/")) {
                    return reference.substring(8);
                }
                return reference;
            }
        }

        return null;
    }
}

