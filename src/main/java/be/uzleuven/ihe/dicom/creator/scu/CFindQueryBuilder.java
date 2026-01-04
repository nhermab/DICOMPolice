package be.uzleuven.ihe.dicom.creator.scu;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;

/**
 * Builder for creating C-FIND query keys at different query levels.
 */
public class CFindQueryBuilder {

    /**
     * Creates C-FIND keys for STUDY level query.
     *
     * @param studyInstanceUid The Study Instance UID to query
     * @param patientId Optional Patient ID (may be null)
     * @return Attributes containing the query keys
     */
    public static Attributes buildStudyQuery(String studyInstanceUid, String patientId) {
        Attributes keys = new Attributes();
        keys.setString(Tag.QueryRetrieveLevel, VR.CS, "STUDY");
        keys.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUid);

        if (patientId != null && !patientId.trim().isEmpty()) {
            keys.setString(Tag.PatientID, VR.LO, patientId);
        } else {
            keys.setNull(Tag.PatientID, VR.LO);
        }

        // Return attributes we need for manifest creation
        keys.setNull(Tag.PatientName, VR.PN);
        keys.setNull(Tag.PatientBirthDate, VR.DA);
        keys.setNull(Tag.PatientSex, VR.CS);
        keys.setNull(Tag.StudyDate, VR.DA);
        keys.setNull(Tag.StudyTime, VR.TM);
        keys.setNull(Tag.StudyDescription, VR.LO);
        keys.setNull(Tag.AccessionNumber, VR.SH);
        keys.setNull(Tag.ReferringPhysicianName, VR.PN);
        keys.setNull(Tag.StudyID, VR.SH);

        return keys;
    }

    /**
     * Creates C-FIND keys for SERIES level query.
     *
     * @param studyInstanceUid The Study Instance UID
     * @param seriesInstanceUid Optional Series Instance UID (may be null to get all series)
     * @return Attributes containing the query keys
     */
    public static Attributes buildSeriesQuery(String studyInstanceUid, String seriesInstanceUid) {
        Attributes keys = new Attributes();
        keys.setString(Tag.QueryRetrieveLevel, VR.CS, "SERIES");
        keys.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUid);

        if (seriesInstanceUid != null && !seriesInstanceUid.trim().isEmpty()) {
            keys.setString(Tag.SeriesInstanceUID, VR.UI, seriesInstanceUid);
        } else {
            keys.setNull(Tag.SeriesInstanceUID, VR.UI);
        }

        // Return attributes for series
        keys.setNull(Tag.Modality, VR.CS);
        keys.setNull(Tag.SeriesNumber, VR.IS);
        keys.setNull(Tag.SeriesDescription, VR.LO);
        keys.setNull(Tag.SeriesDate, VR.DA);
        keys.setNull(Tag.SeriesTime, VR.TM);

        return keys;
    }

    /**
     * Creates C-FIND keys for IMAGE (INSTANCE) level query.
     * Requests comprehensive metadata for high-quality MADO manifests.
     *
     * @param studyInstanceUid The Study Instance UID
     * @param seriesInstanceUid The Series Instance UID
     * @return Attributes containing the query keys
     */
    public static Attributes buildInstanceQuery(String studyInstanceUid, String seriesInstanceUid) {
        Attributes keys = new Attributes();
        keys.setString(Tag.QueryRetrieveLevel, VR.CS, "IMAGE");
        keys.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUid);
        keys.setString(Tag.SeriesInstanceUID, VR.UI, seriesInstanceUid);

        // Required identifiers
        keys.setNull(Tag.SOPInstanceUID, VR.UI);
        keys.setNull(Tag.SOPClassUID, VR.UI);
        keys.setNull(Tag.InstanceNumber, VR.IS);

        // Image dimensions and multiframe
        keys.setNull(Tag.NumberOfFrames, VR.IS);
        keys.setNull(Tag.Rows, VR.US);
        keys.setNull(Tag.Columns, VR.US);

        // Pixel Module attributes (critical for OHIF viewer)
        keys.setNull(Tag.BitsAllocated, VR.US);
        keys.setNull(Tag.BitsStored, VR.US);
        keys.setNull(Tag.HighBit, VR.US);
        keys.setNull(Tag.PixelRepresentation, VR.US);
        keys.setNull(Tag.SamplesPerPixel, VR.US);
        keys.setNull(Tag.PhotometricInterpretation, VR.CS);

        // Geometry and spatial information (critical for ordering and MPR)
        keys.setNull(Tag.ImagePositionPatient, VR.DS);
        keys.setNull(Tag.ImageOrientationPatient, VR.DS);
        keys.setNull(Tag.PixelSpacing, VR.DS);
        keys.setNull(Tag.SliceThickness, VR.DS);
        keys.setNull(Tag.SliceLocation, VR.DS);
        keys.setNull(Tag.SpacingBetweenSlices, VR.DS);

        // Window/Level and rescale (for proper display)
        keys.setNull(Tag.WindowCenter, VR.DS);
        keys.setNull(Tag.WindowWidth, VR.DS);
        keys.setNull(Tag.RescaleIntercept, VR.DS);
        keys.setNull(Tag.RescaleSlope, VR.DS);
        keys.setNull(Tag.RescaleType, VR.LO);

        // Additional useful metadata
        keys.setNull(Tag.ImageType, VR.CS);
        keys.setNull(Tag.AcquisitionNumber, VR.IS);
        keys.setNull(Tag.AcquisitionDate, VR.DA);
        keys.setNull(Tag.AcquisitionTime, VR.TM);
        keys.setNull(Tag.ContentDate, VR.DA);
        keys.setNull(Tag.ContentTime, VR.TM);

        return keys;
    }
}

