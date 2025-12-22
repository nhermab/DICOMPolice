package be.uzleuven.ihe.service.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Report of the execution of an assertion.
 */
public class AssertionReport {
    private String assertionID;
    private String result;  // PASSED, FAILED, UNDEFINED
    private String severity;  // INFO, WARNING, ERROR
    private String priority;  // MANDATORY, RECOMMENDED, PERMITTED
    private String description;
    private String assertionType;
    private String formalExpression;
    private List<SubjectLocation> subjectLocations;
    private String subjectValue;
    private List<String> requirementIDs;
    private List<UnexpectedError> unexpectedErrors;

    public AssertionReport() {
        this.subjectLocations = new ArrayList<>();
        this.requirementIDs = new ArrayList<>();
        this.unexpectedErrors = new ArrayList<>();
    }

    public String getAssertionID() {
        return assertionID;
    }

    public void setAssertionID(String assertionID) {
        this.assertionID = assertionID;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAssertionType() {
        return assertionType;
    }

    public void setAssertionType(String assertionType) {
        this.assertionType = assertionType;
    }

    public String getFormalExpression() {
        return formalExpression;
    }

    public void setFormalExpression(String formalExpression) {
        this.formalExpression = formalExpression;
    }

    public List<SubjectLocation> getSubjectLocations() {
        return subjectLocations;
    }

    public void setSubjectLocations(List<SubjectLocation> subjectLocations) {
        this.subjectLocations = subjectLocations;
    }

    public String getSubjectValue() {
        return subjectValue;
    }

    public void setSubjectValue(String subjectValue) {
        this.subjectValue = subjectValue;
    }

    public List<String> getRequirementIDs() {
        return requirementIDs;
    }

    public void setRequirementIDs(List<String> requirementIDs) {
        this.requirementIDs = requirementIDs;
    }

    public List<UnexpectedError> getUnexpectedErrors() {
        return unexpectedErrors;
    }

    public void setUnexpectedErrors(List<UnexpectedError> unexpectedErrors) {
        this.unexpectedErrors = unexpectedErrors;
    }
}

