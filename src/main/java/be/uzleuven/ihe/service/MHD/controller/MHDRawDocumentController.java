package be.uzleuven.ihe.service.MHD.controller;

import be.uzleuven.ihe.service.MHD.MHDService;
import be.uzleuven.ihe.service.MHD.fhir.DicomToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * REST Controller for raw DICOM MADO manifest retrieval.
 * Provides an alternative ITI-68 endpoint that returns the DICOM file directly
 * with application/dicom content type, rather than as a FHIR Binary resource.
 */
@RestController
@RequestMapping("/mhd")
public class MHDRawDocumentController {

    private static final Logger LOG = LoggerFactory.getLogger(MHDRawDocumentController.class);
    private static final MediaType APPLICATION_DICOM = MediaType.parseMediaType("application/dicom");

    private final MHDService mhdService;

    @Autowired
    public MHDRawDocumentController(MHDService mhdService) {
        this.mhdService = mhdService;
    }

    /**
     * ITI-68 alternative: Retrieve MADO manifest as raw DICOM file.
     *
     * @param documentId The document ID (base64-encoded Study Instance UID)
     * @return The DICOM MADO manifest file
     */
    @GetMapping(value = "/Document/{documentId}", produces = "application/dicom")
    public ResponseEntity<byte[]> retrieveDocument(@PathVariable String documentId) {
        LOG.info("Retrieving raw MADO document: {}", documentId);

        try {
            String studyInstanceUid = DicomToFhirMapper.decodeStudyUidFromFhirId(documentId);

            byte[] manifestBytes = mhdService.retrieveDocumentRaw(studyInstanceUid);

            if (manifestBytes == null) {
                LOG.warn("Document not found: {}", documentId);
                return ResponseEntity.notFound().build();
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(APPLICATION_DICOM);
            headers.setContentLength(manifestBytes.length);
            headers.setContentDispositionFormData("attachment",
                "MADO_" + studyInstanceUid + ".dcm");

            LOG.info("Returning MADO manifest for study {}, size: {} bytes",
                studyInstanceUid, manifestBytes.length);

            return new ResponseEntity<>(manifestBytes, headers, HttpStatus.OK);

        } catch (IOException e) {
            LOG.error("Error retrieving document: {}", documentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Retrieve MADO manifest by Study Instance UID directly.
     *
     * @param studyInstanceUid The DICOM Study Instance UID
     * @return The DICOM MADO manifest file
     */
    @GetMapping(value = "/studies/{studyInstanceUid}/manifest", produces = "application/dicom")
    public ResponseEntity<byte[]> retrieveManifestByStudyUid(@PathVariable String studyInstanceUid) {
        LOG.info("Retrieving MADO manifest for study: {}", studyInstanceUid);

        try {
            byte[] manifestBytes = mhdService.retrieveDocumentRaw(studyInstanceUid);

            if (manifestBytes == null) {
                LOG.warn("Study not found: {}", studyInstanceUid);
                return ResponseEntity.notFound().build();
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(APPLICATION_DICOM);
            headers.setContentLength(manifestBytes.length);
            headers.setContentDispositionFormData("attachment",
                "MADO_" + studyInstanceUid + ".dcm");

            return new ResponseEntity<>(manifestBytes, headers, HttpStatus.OK);

        } catch (IOException e) {
            LOG.error("Error retrieving manifest for study: {}", studyInstanceUid, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Health check endpoint for the MHD service.
     */
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("MHD Document Responder is running");
    }
}

