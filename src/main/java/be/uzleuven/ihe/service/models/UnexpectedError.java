package be.uzleuven.ihe.service.models;

/**
 * Unexpected Error that occurred during the process.
 */
public class UnexpectedError {
    private String name;
    private String message;
    private UnexpectedError cause;

    public UnexpectedError() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public UnexpectedError getCause() {
        return cause;
    }

    public void setCause(UnexpectedError cause) {
        this.cause = cause;
    }
}

