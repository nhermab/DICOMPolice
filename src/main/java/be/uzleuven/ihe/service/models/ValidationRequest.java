package be.uzleuven.ihe.service.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Validation request is the message to send when requesting a validate operation.
 */
public class ValidationRequest {
    private String validationProfileId;
    private List<Input> inputs;

    public ValidationRequest() {
        this.inputs = new ArrayList<>();
    }

    public String getValidationProfileId() {
        return validationProfileId;
    }

    public void setValidationProfileId(String validationProfileId) {
        this.validationProfileId = validationProfileId;
    }

    public List<Input> getInputs() {
        return inputs;
    }

    public void setInputs(List<Input> inputs) {
        this.inputs = inputs;
    }
}

