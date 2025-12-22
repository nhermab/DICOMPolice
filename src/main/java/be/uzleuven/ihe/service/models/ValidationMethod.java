package be.uzleuven.ihe.service.models;

/**
 * Identification of methods and tools used to perform the validation.
 */
public class ValidationMethod {
    private String validationServiceName;
    private String validationServiceVersion;
    private String validationProfileID;
    private String validationProfileName;
    private String validationProfileVersion;

    public ValidationMethod() {
    }

    public String getValidationServiceName() {
        return validationServiceName;
    }

    public void setValidationServiceName(String validationServiceName) {
        this.validationServiceName = validationServiceName;
    }

    public String getValidationServiceVersion() {
        return validationServiceVersion;
    }

    public void setValidationServiceVersion(String validationServiceVersion) {
        this.validationServiceVersion = validationServiceVersion;
    }

    public String getValidationProfileID() {
        return validationProfileID;
    }

    public void setValidationProfileID(String validationProfileID) {
        this.validationProfileID = validationProfileID;
    }

    public String getValidationProfileName() {
        return validationProfileName;
    }

    public void setValidationProfileName(String validationProfileName) {
        this.validationProfileName = validationProfileName;
    }

    public String getValidationProfileVersion() {
        return validationProfileVersion;
    }

    public void setValidationProfileVersion(String validationProfileVersion) {
        this.validationProfileVersion = validationProfileVersion;
    }
}

