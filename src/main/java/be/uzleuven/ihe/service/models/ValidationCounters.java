package be.uzleuven.ihe.service.models;

/**
 * Aggregate statistics about the number of assertion results contained.
 */
public class ValidationCounters {
    private int numberOfAssertions;
    private int numberOfFailedWithInfos;
    private int numberOfFailedWithWarnings;
    private int numberOfFailedWithErrors;
    private int numberOfUnexpectedErrors;
    private int numberOfUndefined;

    public ValidationCounters() {
    }

    public int getNumberOfAssertions() {
        return numberOfAssertions;
    }

    public void setNumberOfAssertions(int numberOfAssertions) {
        this.numberOfAssertions = numberOfAssertions;
    }

    public int getNumberOfFailedWithInfos() {
        return numberOfFailedWithInfos;
    }

    public void setNumberOfFailedWithInfos(int numberOfFailedWithInfos) {
        this.numberOfFailedWithInfos = numberOfFailedWithInfos;
    }

    public int getNumberOfFailedWithWarnings() {
        return numberOfFailedWithWarnings;
    }

    public void setNumberOfFailedWithWarnings(int numberOfFailedWithWarnings) {
        this.numberOfFailedWithWarnings = numberOfFailedWithWarnings;
    }

    public int getNumberOfFailedWithErrors() {
        return numberOfFailedWithErrors;
    }

    public void setNumberOfFailedWithErrors(int numberOfFailedWithErrors) {
        this.numberOfFailedWithErrors = numberOfFailedWithErrors;
    }

    public int getNumberOfUnexpectedErrors() {
        return numberOfUnexpectedErrors;
    }

    public void setNumberOfUnexpectedErrors(int numberOfUnexpectedErrors) {
        this.numberOfUnexpectedErrors = numberOfUnexpectedErrors;
    }

    public int getNumberOfUndefined() {
        return numberOfUndefined;
    }

    public void setNumberOfUndefined(int numberOfUndefined) {
        this.numberOfUndefined = numberOfUndefined;
    }
}

