package be.uzleuven.ihe.service.models;

/**
 * Generic key-value structure to convey any additional information.
 */
public class Metadata {
    private String name;
    private String value;

    public Metadata() {
    }

    public Metadata(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}

