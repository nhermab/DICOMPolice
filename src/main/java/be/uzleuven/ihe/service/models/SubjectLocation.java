package be.uzleuven.ihe.service.models;

/**
 * Location of a subject of an assertion.
 */
public class SubjectLocation {
    private String inputId;
    private String type;  // "line-column", "XPath", "JSONPath", "FHIRPath", etc.
    private String value;

    public SubjectLocation() {
    }

    public String getInputId() {
        return inputId;
    }

    public void setInputId(String inputId) {
        this.inputId = inputId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}

