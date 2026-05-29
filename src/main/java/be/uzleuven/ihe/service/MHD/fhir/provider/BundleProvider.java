package be.uzleuven.ihe.service.MHD.fhir.provider;

import be.uzleuven.ihe.dicom.convertor.fhir.MADOToFHIRConverter;
import be.uzleuven.ihe.service.MHD.config.MHDConfiguration;
import be.uzleuven.ihe.service.MHD.dicom.DicomBackendService;
import be.uzleuven.ihe.service.MHD.fhir.DicomToFhirMapper;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static be.uzleuven.ihe.singletons.HAPI.FHIR_R5_CONTEXT;

/**
 * HAPI FHIR Resource Provider for Bundle.
 * Serves the FHIR MADO manifest Bundle by converting the DICOM KOS manifest
 * to FHIR on the fly.
 *
 * The Bundle ID is the base64-encoded Study Instance UID (same as the
 * FHIR DocumentReference content URL points to).
 */
@Component
public class BundleProvider implements IResourceProvider {

    private static final Logger LOG = LoggerFactory.getLogger(BundleProvider.class);
    private static final FhirContext FHIR_R4_CONTEXT = FhirContext.forR4();

    private final DicomBackendService dicomService;
    private final MADOToFHIRConverter madoToFhirConverter = new MADOToFHIRConverter();

    @Autowired
    public BundleProvider(DicomBackendService dicomService, MHDConfiguration config) {
        this.dicomService = dicomService;
    }

    @Override
    public Class<? extends IBaseResource> getResourceType() {
        return Bundle.class;
    }

    /**
     * Read (retrieve) a FHIR MADO Bundle by ID.
     *
     * The DICOM KOS manifest is fetched from the backend, converted to a FHIR
     * Bundle using the MADOToFHIRConverter (R5), serialized to JSON, and then
     * parsed back as an R4 Bundle for the HAPI R4 server.
     */
    @Read
    public Bundle read(@IdParam IdType id) {
        LOG.info("Reading Bundle (FHIR MADO Manifest): {}", id.getIdPart());

        String studyInstanceUid = DicomToFhirMapper.decodeStudyUidFromFhirId(id.getIdPart());

        try {
            // Verify study exists and get metadata
            Attributes study = dicomService.getStudyMetadata(studyInstanceUid);
            if (study == null) {
                throw new ResourceNotFoundException("Bundle not found for ID: " + id.getIdPart());
            }

            String patientId = study.getString(Tag.PatientID);

            // Generate the DICOM MADO manifest (KOS)
            Attributes manifestAttrs = dicomService.createMADOManifest(studyInstanceUid, patientId);
            if (manifestAttrs == null) {
                throw new InternalErrorException("Failed to generate MADO manifest");
            }

            // Convert DICOM KOS → FHIR R5 Bundle
            org.hl7.fhir.r5.model.Bundle r5Bundle = madoToFhirConverter.convert(manifestAttrs);

            // Serialize R5 Bundle to JSON
            IParser r5Parser = FHIR_R5_CONTEXT.newJsonParser();
            r5Parser.setPrettyPrint(false);
            r5Parser.setOverrideResourceIdWithBundleEntryFullUrl(false);
            String bundleJson = r5Parser.encodeResourceToString(r5Bundle);

            // Parse as R4 Bundle (R5 → R4 is compatible for document Bundles)
            IParser r4Parser = FHIR_R4_CONTEXT.newJsonParser();
            Bundle r4Bundle = r4Parser.parseResource(Bundle.class, bundleJson);

            // Set the ID to match the request
            r4Bundle.setId(id.getIdPart());

            LOG.info("Generated FHIR MADO Bundle for study {}", studyInstanceUid);
            return r4Bundle;

        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (IOException e) {
            LOG.error("Error generating FHIR MADO Bundle", e);
            throw new InternalErrorException("Error generating FHIR MADO Bundle: " + e.getMessage());
        } catch (Exception e) {
            LOG.error("Error converting DICOM to FHIR Bundle", e);
            throw new InternalErrorException("Error converting DICOM to FHIR Bundle: " + e.getMessage());
        }
    }
}

