package be.uzleuven.ihe.dicom.creator.scu;

import org.dcm4che3.data.Attributes;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the result of a C-FIND query.
 */
public class CFindResult {
    private final List<Attributes> matches = new ArrayList<>();
    private boolean success = false;
    private String errorMessage = null;

    public List<Attributes> getMatches() {
        return matches;
    }

    public void addMatch(Attributes attrs) {
        matches.add(attrs);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}

