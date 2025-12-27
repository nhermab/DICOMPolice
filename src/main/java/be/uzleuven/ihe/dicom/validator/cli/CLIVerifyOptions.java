package be.uzleuven.ihe.dicom.validator.cli;

import java.util.ArrayList;
import java.util.List;

/**
 * Options container for CLIDICOMVerify command-line tool.
 */
public class CLIVerifyOptions {
    private boolean showHelp = false;
    private boolean verbose = false;
    private boolean newFormat = false;
    private String profile = null;
    private List<String> files = new ArrayList<>();

    public boolean isShowHelp() {
        return showHelp;
    }

    public void setShowHelp(boolean showHelp) {
        this.showHelp = showHelp;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public boolean isNewFormat() {
        return newFormat;
    }

    public void setNewFormat(boolean newFormat) {
        this.newFormat = newFormat;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public List<String> getFiles() {
        return files;
    }

    public void addFile(String file) {
        this.files.add(file);
    }

    public boolean hasFiles() {
        return !files.isEmpty();
    }
}

