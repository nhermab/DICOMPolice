package be.uzleuven.ihe.service.MHD;

import be.uzleuven.ihe.service.MHD.config.MHDConfiguration;
import be.uzleuven.ihe.service.MHD.dicom.DicomBackendService;
import be.uzleuven.ihe.service.MHD.fhir.DicomToFhirMapper;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.ListResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Main MHD Document Responder Service.
 * Provides high-level operations for the IHE MHD facade.
 *
 * This service bridges the FHIR/REST world with the DICOM backend,
 * implementing the MHD Document Responder actor with MADO support.
 *
 * Supported IHE Transactions:
 * - ITI-66: Find Document Lists (SubmissionSets)
 * - ITI-67: Find Document References
 * - ITI-68: Retrieve Document (MADO manifest)
 */
@Service
public class MHDService {

    private final DicomBackendService dicomBackendService;
    private final MHDConfiguration config;

    @Autowired
    public MHDService(DicomBackendService dicomBackendService, MHDConfiguration config) {
        this.dicomBackendService = dicomBackendService;
        this.config = config;
    }

    /**
     * ITI-67: Find Document References
     * Searches for studies matching the criteria and returns them as DocumentReferences.
     *
     * @param patientId Patient identifier
     * @param accessionNumber Accession number
     * @param studyInstanceUid Study Instance UID (MADO-specific)
     * @param modality Modality filter
     * @param dateFrom Study date range start
     * @param dateTo Study date range end
     * @return List of matching DocumentReferences
     */
    public List<DocumentReference> findDocumentReferences(String patientId, String accessionNumber,
                                                           String studyInstanceUid, String modality,
                                                           String dateFrom, String dateTo) throws IOException {
        List<Attributes> studies = dicomBackendService.searchStudies(
            patientId, accessionNumber, studyInstanceUid, modality, dateFrom, dateTo);

        List<DocumentReference> results = new ArrayList<>();
        for (Attributes study : studies) {
            results.add(DicomToFhirMapper.mapStudyToDocumentReference(study, config, null));
        }

        return results;
    }

    /**
     * ITI-67: Read a single DocumentReference by Study Instance UID.
     *
     * @param studyInstanceUid The Study Instance UID
     * @return DocumentReference with full metadata including manifest size/hash
     */
    public DocumentReference getDocumentReference(String studyInstanceUid) throws IOException {
        Attributes study = dicomBackendService.getStudyMetadata(studyInstanceUid);
        if (study == null) {
            return null;
        }

        // Generate manifest to get accurate size/hash
        byte[] manifestBytes = dicomBackendService.createMADOManifestAsBytes(
            studyInstanceUid, study.getString(Tag.PatientID));

        return DicomToFhirMapper.mapStudyToDocumentReference(study, config, manifestBytes);
    }

    /**
     * ITI-66: Find Document Lists (SubmissionSets)
     * Returns SubmissionSets containing DocumentReferences.
     *
     * @param patientId Patient identifier
     * @param dateFrom Date range start
     * @param dateTo Date range end
     * @return List of matching SubmissionSets (as FHIR Lists)
     */
    public List<ListResource> findSubmissionSets(String patientId, String dateFrom, String dateTo)
            throws IOException {
        List<Attributes> studies = dicomBackendService.searchStudies(
            patientId, null, null, null, dateFrom, dateTo);

        List<ListResource> results = new ArrayList<>();
        for (Attributes study : studies) {
            DocumentReference docRef = DicomToFhirMapper.mapStudyToDocumentReference(study, config, null);
            results.add(DicomToFhirMapper.createSubmissionSetList(study, config, docRef));
        }

        return results;
    }

    /**
     * ITI-66: Read a single SubmissionSet by Study Instance UID.
     *
     * @param studyInstanceUid The Study Instance UID
     * @return SubmissionSet as a FHIR List
     */
    public ListResource getSubmissionSet(String studyInstanceUid) throws IOException {
        Attributes study = dicomBackendService.getStudyMetadata(studyInstanceUid);
        if (study == null) {
            return null;
        }

        DocumentReference docRef = DicomToFhirMapper.mapStudyToDocumentReference(study, config, null);
        return DicomToFhirMapper.createSubmissionSetList(study, config, docRef);
    }

    /**
     * ITI-68: Retrieve Document (MADO manifest)
     * Generates and returns the MADO manifest as a Binary resource.
     *
     * @param studyInstanceUid The Study Instance UID
     * @return Binary resource containing the DICOM MADO manifest
     */
    public Binary retrieveDocument(String studyInstanceUid) throws IOException {
        Attributes study = dicomBackendService.getStudyMetadata(studyInstanceUid);
        if (study == null) {
            return null;
        }

        String patientId = study.getString(Tag.PatientID);
        byte[] manifestBytes = dicomBackendService.createMADOManifestAsBytes(studyInstanceUid, patientId);

        return DicomToFhirMapper.createBinaryResource(studyInstanceUid, manifestBytes);
    }

    /**
     * ITI-68: Retrieve raw MADO manifest bytes.
     * Use this when you need to return the DICOM file directly (not as a FHIR Binary).
     *
     * @param studyInstanceUid The Study Instance UID
     * @return Raw DICOM Part 10 file bytes
     */
    public byte[] retrieveDocumentRaw(String studyInstanceUid) throws IOException {
        Attributes study = dicomBackendService.getStudyMetadata(studyInstanceUid);
        if (study == null) {
            return null;
        }

        String patientId = study.getString(Tag.PatientID);
        return dicomBackendService.createMADOManifestAsBytes(studyInstanceUid, patientId);
    }

    /**
     * Creates a FHIR Bundle containing all resources related to a study.
     * Useful for comprehensive responses.
     *
     * @param studyInstanceUid The Study Instance UID
     * @return Bundle containing DocumentReference, SubmissionSet List, and Binary
     */
    public Bundle getFullStudyBundle(String studyInstanceUid) throws IOException {
        Attributes study = dicomBackendService.getStudyMetadata(studyInstanceUid);
        if (study == null) {
            return null;
        }

        String patientId = study.getString(Tag.PatientID);
        byte[] manifestBytes = dicomBackendService.createMADOManifestAsBytes(studyInstanceUid, patientId);

        DocumentReference docRef = DicomToFhirMapper.mapStudyToDocumentReference(study, config, manifestBytes);
        ListResource submissionSet = DicomToFhirMapper.createSubmissionSetList(study, config, docRef);
        Binary binary = DicomToFhirMapper.createBinaryResource(studyInstanceUid, manifestBytes);

        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.SEARCHSET);

        bundle.addEntry()
            .setFullUrl(config.getFhirBaseUrl() + "/DocumentReference/" + docRef.getId())
            .setResource(docRef);

        bundle.addEntry()
            .setFullUrl(config.getFhirBaseUrl() + "/List/" + submissionSet.getId())
            .setResource(submissionSet);

        bundle.addEntry()
            .setFullUrl(config.getFhirBaseUrl() + "/Binary/" + binary.getId())
            .setResource(binary);

        bundle.setTotal(3);

        return bundle;
    }

    public MHDConfiguration getConfig() {
        return config;
    }
}
