package be.uzleuven.ihe.dicom.creator.model;

/**
 * Represents a simulated DICOM instance.
 */
public class SimulatedInstance {
    private String sopClassUID;
    private String sopInstanceUID;
    private boolean isKIN;

    public SimulatedInstance(String cls, String uid, boolean isKin) {
        this.sopClassUID = cls;
        this.sopInstanceUID = uid;
        this.isKIN = isKin;
    }

    public String getSopClassUID() {
        return sopClassUID;
    }

    public void setSopClassUID(String sopClassUID) {
        this.sopClassUID = sopClassUID;
    }

    public String getSopInstanceUID() {
        return sopInstanceUID;
    }

    public void setSopInstanceUID(String sopInstanceUID) {
        this.sopInstanceUID = sopInstanceUID;
    }

    public boolean isKIN() {
        return isKIN;
    }

    public void setKIN(boolean KIN) {
        isKIN = KIN;
    }
}

