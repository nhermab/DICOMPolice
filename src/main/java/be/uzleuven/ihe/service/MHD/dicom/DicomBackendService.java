package be.uzleuven.ihe.service.MHD.dicom;

import be.uzleuven.ihe.dicom.creator.scu.CFindResult;
import be.uzleuven.ihe.dicom.creator.scu.CFindService;
import be.uzleuven.ihe.dicom.creator.scu.DefaultMetadata;
import be.uzleuven.ihe.dicom.creator.scu.MADOSCUManifestCreator;
import be.uzleuven.ihe.dicom.creator.scu.MetadataApplier;
import be.uzleuven.ihe.dicom.creator.model.MADOOptions;
import be.uzleuven.ihe.service.MHD.config.MHDConfiguration;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * DICOM Backend Service for MHD Document Responder.
 * Handles C-FIND queries and MADO manifest generation.
 */
@Service
public class DicomBackendService {

    private final MHDConfiguration config;
    private final CFindService cFindService;
    private final DefaultMetadata defaultMetadata;
    private final boolean includeExtendedInstanceMetadata;

    @Autowired
    public DicomBackendService(MHDConfiguration config) {
        this.config = config;
        this.defaultMetadata = config.toDefaultMetadata();
        this.cFindService = new CFindService(defaultMetadata);
        this.includeExtendedInstanceMetadata = config.isIncludeExtendedInstanceMetadata();

        // DEBUG: Log configuration value
        System.out.println("===========================================");
        System.out.println("DEBUG DicomBackendService initialized:");
        System.out.println("  includeExtendedInstanceMetadata = " + includeExtendedInstanceMetadata);
        System.out.println("===========================================");
    }

    /**
     * Search for studies based on various criteria.
     *
     * @param patientId Patient ID (DICOM tag 0010,0020)
     * @param accessionNumber Accession Number (DICOM tag 0008,0050)
     * @param studyInstanceUid Study Instance UID (DICOM tag 0020,000D)
     * @param modality Modality filter (DICOM tag 0008,0060)
     * @param studyDateFrom Study date range start (YYYYMMDD)
     * @param studyDateTo Study date range end (YYYYMMDD)
     * @return List of study attributes matching the criteria
     * @throws IOException if C-FIND fails
     */
    public List<Attributes> searchStudies(String patientId, String accessionNumber,
                                          String studyInstanceUid, String modality,
                                          String studyDateFrom, String studyDateTo) throws IOException {
        Attributes keys = new Attributes();

        // Query level
        keys.setString(Tag.QueryRetrieveLevel, VR.CS, "STUDY");

        // Required return keys
        keys.setNull(Tag.StudyInstanceUID, VR.UI);
        keys.setNull(Tag.PatientID, VR.LO);
        keys.setNull(Tag.PatientName, VR.PN);
        keys.setNull(Tag.PatientBirthDate, VR.DA);
        keys.setNull(Tag.PatientSex, VR.CS);
        keys.setNull(Tag.IssuerOfPatientID, VR.LO);
        keys.setNull(Tag.AccessionNumber, VR.SH);
        keys.setNull(Tag.StudyDate, VR.DA);
        keys.setNull(Tag.StudyTime, VR.TM);
        keys.setNull(Tag.StudyDescription, VR.LO);
        keys.setNull(Tag.StudyID, VR.SH);
        keys.setNull(Tag.ReferringPhysicianName, VR.PN);
        keys.setNull(Tag.PerformingPhysicianName, VR.PN);
        keys.setNull(Tag.NumberOfStudyRelatedSeries, VR.IS);
        keys.setNull(Tag.NumberOfStudyRelatedInstances, VR.IS);
        keys.setNull(Tag.ModalitiesInStudy, VR.CS);

        // Apply search filters
        if (patientId != null && !patientId.trim().isEmpty()) {
            keys.setString(Tag.PatientID, VR.LO, patientId.trim());
        }

        if (accessionNumber != null && !accessionNumber.trim().isEmpty()) {
            keys.setString(Tag.AccessionNumber, VR.SH, accessionNumber.trim());
        }

        if (studyInstanceUid != null && !studyInstanceUid.trim().isEmpty()) {
            keys.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUid.trim());
        }

        if (modality != null && !modality.trim().isEmpty()) {
            keys.setString(Tag.ModalitiesInStudy, VR.CS, modality.trim());
        }

        // Handle date range
        if (studyDateFrom != null || studyDateTo != null) {
            String dateRange = buildDateRange(studyDateFrom, studyDateTo);
            if (dateRange != null) {
                keys.setString(Tag.StudyDate, VR.DA, dateRange);
            }
        }

        CFindResult result = cFindService.performCFind(keys);

        if (!result.isSuccess()) {
            throw new IOException("C-FIND failed: " + result.getErrorMessage());
        }

        // Apply defaults and normalize metadata
        List<Attributes> studies = result.getMatches();
        for (Attributes study : studies) {
            MetadataApplier.applyDefaults(study, defaultMetadata);
            normalizeStudyAttributes(study);
        }

        return studies;
    }

    /**
     * Create a MADO manifest for a specific study.
     *
     * @param studyInstanceUid The Study Instance UID
     * @param patientId Optional patient ID (for verification)
     * @return The generated MADO manifest as DICOM Attributes
     * @throws IOException if manifest creation fails
     */
    public Attributes createMADOManifest(String studyInstanceUid, String patientId) throws IOException {
        // DEBUG: Log when creating MADO
        System.out.println("DEBUG createMADOManifest: Creating MADO with includeExtendedInstanceMetadata = " + includeExtendedInstanceMetadata);

        // Use configured setting for extended instance metadata
        MADOOptions options = new MADOOptions()
            .withIncludeExtendedInstanceMetadata(includeExtendedInstanceMetadata);

        System.out.println("DEBUG createMADOManifest: MADOOptions.isIncludeExtendedInstanceMetadata() = " + options.isIncludeExtendedInstanceMetadata());

        MADOSCUManifestCreator creator = new MADOSCUManifestCreator(defaultMetadata, options);
        return creator.createManifest(studyInstanceUid, patientId);
    }

    /**
     * Create a MADO manifest and return it as bytes (DICOM Part 10 file).
     *
     * @param studyInstanceUid The Study Instance UID
     * @param patientId Optional patient ID
     * @return The manifest as a byte array (DICOM file format)
     * @throws IOException if manifest creation fails
     */
    public byte[] createMADOManifestAsBytes(String studyInstanceUid, String patientId) throws IOException {
        Attributes manifest = createMADOManifest(studyInstanceUid, patientId);
        return attributesToDicomBytes(manifest);
    }

    /**
     * Get study metadata for a specific study.
     *
     * @param studyInstanceUid The Study Instance UID
     * @return Study attributes or null if not found
     * @throws IOException if query fails
     */
    public Attributes getStudyMetadata(String studyInstanceUid) throws IOException {
        List<Attributes> studies = searchStudies(null, null, studyInstanceUid, null, null, null);
        return studies.isEmpty() ? null : studies.get(0);
    }

    /**
     * Convert DICOM Attributes to Part 10 file bytes.
     */
    public byte[] attributesToDicomBytes(Attributes attrs) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        String sopClassUID = attrs.getString(Tag.SOPClassUID);
        String sopInstanceUID = attrs.getString(Tag.SOPInstanceUID);

        try (DicomOutputStream dos = new DicomOutputStream(baos, org.dcm4che3.data.UID.ExplicitVRLittleEndian)) {
            Attributes fmi = attrs.createFileMetaInformation(org.dcm4che3.data.UID.ExplicitVRLittleEndian);
            dos.writeDataset(fmi, attrs);
        }

        return baos.toByteArray();
    }

    /**
     * Build a DICOM date range string.
     */
    private String buildDateRange(String from, String to) {
        if (from != null && !from.isEmpty() && to != null && !to.isEmpty()) {
            return from + "-" + to;
        } else if (from != null && !from.isEmpty()) {
            return from + "-";
        } else if (to != null && !to.isEmpty()) {
            return "-" + to;
        }
        return null;
    }

    /**
     * Normalize study attributes for FHIR mapping.
     */
    private void normalizeStudyAttributes(Attributes study) {
        // Normalize PatientSex
        String sex = study.getString(Tag.PatientSex);
        if (sex != null) {
            study.setString(Tag.PatientSex, VR.CS, MetadataApplier.normalizePatientSex(sex));
        }
    }

    public MHDConfiguration getConfig() {
        return config;
    }
}
