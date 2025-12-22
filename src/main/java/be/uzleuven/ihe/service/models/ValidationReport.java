package be.uzleuven.ihe.service.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Validation Report containing the result and all information related to processing a validation request.
 */
public class ValidationReport {
    private String modelVersion = "2.0";
    private String uuid;
    private String dateTime;
    private String disclaimer;
    private String overallResult;  // PASSED, FAILED, UNDEFINED
    private ValidationMethod validationMethod;
    private List<Input> inputs;
    private List<ValidationSubReport> reports;
    private ValidationCounters counters;
    private List<Metadata> additionalMetadata;

    public ValidationReport() {
        this.inputs = new ArrayList<>();
        this.reports = new ArrayList<>();
        this.additionalMetadata = new ArrayList<>();
        this.counters = new ValidationCounters();
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getDateTime() {
        return dateTime;
    }

    public void setDateTime(String dateTime) {
        this.dateTime = dateTime;
    }

    public String getDisclaimer() {
        return disclaimer;
    }

    public void setDisclaimer(String disclaimer) {
        this.disclaimer = disclaimer;
    }

    public String getOverallResult() {
        return overallResult;
    }

    public void setOverallResult(String overallResult) {
        this.overallResult = overallResult;
    }

    public ValidationMethod getValidationMethod() {
        return validationMethod;
    }

    public void setValidationMethod(ValidationMethod validationMethod) {
        this.validationMethod = validationMethod;
    }

    public List<Input> getInputs() {
        return inputs;
    }

    public void setInputs(List<Input> inputs) {
        this.inputs = inputs;
    }

    public List<ValidationSubReport> getReports() {
        return reports;
    }

    public void setReports(List<ValidationSubReport> reports) {
        this.reports = reports;
    }

    public ValidationCounters getCounters() {
        return counters;
    }

    public void setCounters(ValidationCounters counters) {
        this.counters = counters;
    }

    public List<Metadata> getAdditionalMetadata() {
        return additionalMetadata;
    }

    public void setAdditionalMetadata(List<Metadata> additionalMetadata) {
        this.additionalMetadata = additionalMetadata;
    }
}

