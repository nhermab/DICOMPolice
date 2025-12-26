package be.uzleuven.ihe.dicom.creator.scu.cli;

/**
 * Minimal study metadata returned by a STUDY-level C-FIND.
 */
public class StudyDescriptor {
    private final String studyInstanceUID;
    private final String patientId;
    private final String accessionNumber;
    private final String studyDate;

    public StudyDescriptor(String studyInstanceUID, String patientId, String accessionNumber, String studyDate) {
        this.studyInstanceUID = studyInstanceUID;
        this.patientId = patientId;
        this.accessionNumber = accessionNumber;
        this.studyDate = studyDate;
    }

    public String getStudyInstanceUID() {
        return studyInstanceUID;
    }

    public String getPatientId() {
        return patientId;
    }

    public String getAccessionNumber() {
        return accessionNumber;
    }

    public String getStudyDate() {
        return studyDate;
    }
}

