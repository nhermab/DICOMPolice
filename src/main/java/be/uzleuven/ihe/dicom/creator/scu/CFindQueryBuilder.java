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

        // Return attributes for instances
        keys.setNull(Tag.SOPInstanceUID, VR.UI);
        keys.setNull(Tag.SOPClassUID, VR.UI);
        keys.setNull(Tag.InstanceNumber, VR.IS);
        keys.setNull(Tag.NumberOfFrames, VR.IS);
        keys.setNull(Tag.Rows, VR.US);
        keys.setNull(Tag.Columns, VR.US);

        return keys;
    }
}

