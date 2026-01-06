package be.uzleuven.ihe.service.MHD.fhir.provider;

import be.uzleuven.ihe.service.MHD.config.MHDConfiguration;
import be.uzleuven.ihe.service.MHD.dicom.DicomBackendService;
import be.uzleuven.ihe.service.MHD.fhir.DicomToFhirMapper;
import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.IdType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * HAPI FHIR Resource Provider for Binary.
 * Implements ITI-68 (Retrieve Document) for MHD Document Responder.
 * Returns the MADO manifest as a DICOM Part 10 file.
 */
@Component
public class BinaryProvider implements IResourceProvider {

    private static final Logger LOG = LoggerFactory.getLogger(BinaryProvider.class);

    private final DicomBackendService dicomService;

    @Autowired
    public BinaryProvider(DicomBackendService dicomService, MHDConfiguration config) {
        this.dicomService = dicomService;
    }

    @Override
    public Class<? extends IBaseResource> getResourceType() {
        return Binary.class;
    }

    /**
     * Read (retrieve) a Binary resource containing the MADO manifest.
     * Implements ITI-68 (Retrieve Document).
     *
     * ID is the base64-encoded Study Instance UID (same as DocumentReference ID).
     * Returns the DICOM KOS file as application/dicom content.
     */
    @Read
    public Binary read(@IdParam IdType id) {
        LOG.info("Reading Binary (MADO Manifest): {}", id.getIdPart());

        String studyInstanceUid = DicomToFhirMapper.decodeStudyUidFromFhirId(id.getIdPart());

        try {
            // Get study metadata to verify it exists and get patient ID
            Attributes study = dicomService.getStudyMetadata(studyInstanceUid);
            if (study == null) {
                throw new ResourceNotFoundException("Binary not found for ID: " + id.getIdPart());
            }

            String patientId = study.getString(Tag.PatientID);

            // Generate the MADO manifest
            byte[] manifestBytes = dicomService.createMADOManifestAsBytes(studyInstanceUid, patientId);

            if (manifestBytes == null || manifestBytes.length == 0) {
                throw new InternalErrorException("Failed to generate MADO manifest");
            }

            LOG.info("Generated MADO manifest for study {}, size: {} bytes", studyInstanceUid, manifestBytes.length);

            // Create and return the Binary resource (its ID already includes .dcm)
            return DicomToFhirMapper.createBinaryResource(studyInstanceUid, manifestBytes);

        } catch (IOException e) {
            LOG.error("Error retrieving MADO manifest", e);
            throw new InternalErrorException("Error generating MADO manifest: " + e.getMessage());
        }
    }
}
