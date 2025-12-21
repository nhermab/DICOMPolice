package be.uzleuven.ihe.dicom.validator.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the result of a DICOM validation check.
 */
public class ValidationResult {

    public enum Severity {
        ERROR,
        WARNING,
        INFO
    }

    private boolean valid = true;
    private final List<ValidationMessage> messages = new ArrayList<>();

    public void addError(String message) {
        messages.add(new ValidationMessage(Severity.ERROR, message));
        valid = false;
    }

    public void addError(String message, String path) {
        messages.add(new ValidationMessage(Severity.ERROR, message, path));
        valid = false;
    }

    public void addWarning(String message) {
        messages.add(new ValidationMessage(Severity.WARNING, message));
    }

    public void addWarning(String message, String path) {
        messages.add(new ValidationMessage(Severity.WARNING, message, path));
    }

    public void addInfo(String message) {
        messages.add(new ValidationMessage(Severity.INFO, message));
    }

    public void addInfo(String message, String path) {
        messages.add(new ValidationMessage(Severity.INFO, message, path));
    }

    public boolean isValid() {
        return valid;
    }

    public List<ValidationMessage> getMessages() {
        return new ArrayList<>(messages);
    }

    public List<ValidationMessage> getErrors() {
        return messages.stream()
            .filter(m -> m.severity == Severity.ERROR)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    public List<ValidationMessage> getWarnings() {
        return messages.stream()
            .filter(m -> m.severity == Severity.WARNING)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    public void merge(ValidationResult other) {
        this.messages.addAll(other.messages);
        if (!other.valid) {
            this.valid = false;
        }
    }

    public static class ValidationMessage {
        private final Severity severity;
        private final String message;
        private final String path;

        public ValidationMessage(Severity severity, String message) {
            this(severity, message, null);
        }

        public ValidationMessage(Severity severity, String message, String path) {
            this.severity = severity;
            this.message = message;
            this.path = path;
        }

        public Severity getSeverity() {
            return severity;
        }

        public String getMessage() {
            return message;
        }

        public String getPath() {
            return path;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(severity).append("] ");
            if (path != null && !path.isEmpty()) {
                sb.append(path).append(": ");
            }
            sb.append(message);
            return sb.toString();
        }
    }
}

