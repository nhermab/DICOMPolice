package be.uzleuven.ihe.service.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Sub report used to categorize assertions.
 */
public class ValidationSubReport {
    private String name;
    private List<String> standards;
    private String subReportResult;  // PASSED, FAILED, UNDEFINED
    private List<ValidationSubReport> subReports;
    private List<AssertionReport> assertionReports;
    private ValidationCounters subCounters;
    private List<UnexpectedError> unexpectedErrors;

    public ValidationSubReport() {
        this.standards = new ArrayList<>();
        this.subReports = new ArrayList<>();
        this.assertionReports = new ArrayList<>();
        this.subCounters = new ValidationCounters();
        this.unexpectedErrors = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getStandards() {
        return standards;
    }

    public void setStandards(List<String> standards) {
        this.standards = standards;
    }

    public String getSubReportResult() {
        return subReportResult;
    }

    public void setSubReportResult(String subReportResult) {
        this.subReportResult = subReportResult;
    }

    public List<ValidationSubReport> getSubReports() {
        return subReports;
    }

    public void setSubReports(List<ValidationSubReport> subReports) {
        this.subReports = subReports;
    }

    public List<AssertionReport> getAssertionReports() {
        return assertionReports;
    }

    public void setAssertionReports(List<AssertionReport> assertionReports) {
        this.assertionReports = assertionReports;
    }

    public ValidationCounters getSubCounters() {
        return subCounters;
    }

    public void setSubCounters(ValidationCounters subCounters) {
        this.subCounters = subCounters;
    }

    public List<UnexpectedError> getUnexpectedErrors() {
        return unexpectedErrors;
    }

    public void setUnexpectedErrors(List<UnexpectedError> unexpectedErrors) {
        this.unexpectedErrors = unexpectedErrors;
    }
}

