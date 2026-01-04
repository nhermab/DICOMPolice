package be.uzleuven.ihe.service.MHD.fhir.provider;

import be.uzleuven.ihe.service.MHD.config.MHDConfiguration;
import be.uzleuven.ihe.service.MHD.dicom.DicomBackendService;
import be.uzleuven.ihe.service.MHD.fhir.DicomToFhirMapper;
import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.param.DateRangeParam;
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
import org.hl7.fhir.r4.model.ListResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * HAPI FHIR Resource Provider for List (SubmissionSet).
 * Implements ITI-66 (Find Document Lists) for MHD Document Responder.
 * In MHD, a List represents a SubmissionSet that groups DocumentReferences.
 */
@Component
public class ListProvider implements IResourceProvider {

    private static final Logger LOG = LoggerFactory.getLogger(ListProvider.class);
    private static final String SS_PREFIX = "ss-";

    private final DicomBackendService dicomService;
    private final MHDConfiguration config;

    @Autowired
    public ListProvider(DicomBackendService dicomService, MHDConfiguration config) {
        this.dicomService = dicomService;
        this.config = config;
    }

    @Override
    public Class<? extends IBaseResource> getResourceType() {
        return ListResource.class;
    }

    /**
     * Read a single List (SubmissionSet) by ID.
     * ID format: "ss-{base64-encoded Study Instance UID}"
     */
    @Read
    public ListResource read(@IdParam IdType id) {
        LOG.info("Reading List (SubmissionSet): {}", id.getIdPart());

        String listId = id.getIdPart();
        if (!listId.startsWith(SS_PREFIX)) {
            throw new ResourceNotFoundException("Invalid SubmissionSet ID format: " + listId);
        }

        String encodedStudyUid = listId.substring(SS_PREFIX.length());
        String studyInstanceUid = DicomToFhirMapper.decodeStudyUidFromFhirId(encodedStudyUid);

        try {
            Attributes study = dicomService.getStudyMetadata(studyInstanceUid);
            if (study == null) {
                throw new ResourceNotFoundException("SubmissionSet not found for ID: " + listId);
            }

            // Create the associated DocumentReference
            DocumentReference docRef = DicomToFhirMapper.mapStudyToDocumentReference(study, config, null);

            // Create the SubmissionSet List
            return DicomToFhirMapper.createSubmissionSetList(study, config, docRef);

        } catch (IOException e) {
            LOG.error("Error reading SubmissionSet", e);
            throw new InternalErrorException("Error retrieving study metadata: " + e.getMessage());
        }
    }

    /**
     * Search for Lists (SubmissionSets).
     * Implements ITI-66 (Find Document Lists) search parameters.
     *
     * @param patient Patient reference or identifier
     * @param patientIdentifier Patient identifier token
     * @param status List status
     * @param code List type code (should be "submissionset")
     * @param date Submission date range
     * @param sourceId Source identifier
     * @return Bundle of matching Lists
     */
    @Search
    public IBundleProvider search(
            @OptionalParam(name = ListResource.SP_PATIENT) ReferenceParam patient,
            @OptionalParam(name = "patient.identifier") TokenParam patientIdentifier,
            @OptionalParam(name = ListResource.SP_STATUS) TokenParam status,
            @OptionalParam(name = ListResource.SP_CODE) TokenParam code,
            @OptionalParam(name = ListResource.SP_DATE) DateRangeParam date,
            @OptionalParam(name = "source.identifier") TokenParam sourceId) {

        LOG.info("Searching Lists (SubmissionSets) - patient: {}", patientIdentifier);

        // Verify this is a submissionset query if code is provided
        if (code != null && !"submissionset".equals(code.getValue())) {
            // Not a submissionset query, return empty
            return new SimpleBundleProvider(new ArrayList<>());
        }

        try {
            // Extract search parameters
            String patientId = extractPatientId(patient, patientIdentifier);

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
                patientId, null, null, null, dateFrom, dateTo);

            // Map to Lists (SubmissionSets)
            List<IBaseResource> results = new ArrayList<>();
            for (Attributes study : studies) {
                try {
                    DocumentReference docRef = DicomToFhirMapper.mapStudyToDocumentReference(study, config, null);
                    ListResource list = DicomToFhirMapper.createSubmissionSetList(study, config, docRef);
                    results.add(list);
                } catch (Exception e) {
                    LOG.warn("Error mapping study to SubmissionSet: {}",
                        study.getString(Tag.StudyInstanceUID), e);
                }
            }

            LOG.info("Found {} SubmissionSets", results.size());
            return new SimpleBundleProvider(results);

        } catch (IOException e) {
            LOG.error("Error searching SubmissionSets", e);
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

