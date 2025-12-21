package be.uzleuven.ihe.dicom.validator.validation.iod;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.VR;
import be.uzleuven.ihe.dicom.validator.model.ValidationResult;

/**
 * Base class for IOD validators providing common validation utilities.
 */
public abstract class AbstractIODValidator implements IODValidator {

    protected final String iodName;

    protected AbstractIODValidator(String iodName) {
        this.iodName = iodName;
    }

    @Override
    public String getIODName() {
        return iodName;
    }

    /**
     * Check if a required attribute is present and non-empty.
     *
     * Note: Sequence (SQ) attributes do not have a string value; using getString(tag)
     * returns null even if the sequence has items. For SQ, we treat "non-empty" as
     * "sequence exists and has at least one item".
     */
    public boolean checkRequiredAttribute(Attributes dataset, int tag, String attributeName,
                                          ValidationResult result, String path) {
        if (!dataset.contains(tag)) {
            result.addError("Missing required attribute: " + attributeName + " " + tagString(tag), path);
            return false;
        }

        VR vr = org.dcm4che3.data.ElementDictionary.vrOf(tag, dataset.getPrivateCreator(tag));
        if (vr == VR.SQ) {
            org.dcm4che3.data.Sequence seq = dataset.getSequence(tag);
            if (seq == null || seq.isEmpty()) {
                result.addError("Required attribute is empty: " + attributeName + " " + tagString(tag), path);
                return false;
            }
            return true;
        }

        // Use getString to determine emptiness for non-sequence value representations.
        String sval = dataset.getString(tag);
        if (sval == null || sval.isEmpty()) {
            result.addError("Required attribute is empty: " + attributeName + " " + tagString(tag), path);
            return false;
        }
        return true;
    }

    /**
     * Check if an attribute of type 2 is present (may be empty).
     */
    public boolean checkType2Attribute(Attributes dataset, int tag, String attributeName,
                                         ValidationResult result, String path) {
        if (!dataset.contains(tag)) {
            result.addError("Missing Type 2 attribute: " + attributeName + " " + tagString(tag), path);
            return false;
        }
        return true;
    }

    /**
     * Check if a conditional attribute is present when condition is met.
     */
    public boolean checkConditionalAttribute(Attributes dataset, int tag, String attributeName,
                                               boolean conditionMet, ValidationResult result, String path) {
        if (conditionMet && !dataset.contains(tag)) {
            result.addError("Missing conditional attribute: " + attributeName + " " + tagString(tag), path);
            return false;
        }
        return true;
    }

    /**
     * Validate string value matches expected format/pattern.
     */
    public boolean checkStringValue(Attributes dataset, int tag, String attributeName,
                                      String expectedValue, ValidationResult result, String path) {
        String value = dataset.getString(tag);
        if (value == null) {
            result.addError("Attribute " + attributeName + " " + tagString(tag) + " is null", path);
            return false;
        }

        if (!value.equals(expectedValue)) {
            result.addError("Attribute " + attributeName + " " + tagString(tag) +
                          " has incorrect value. Expected: " + expectedValue + ", Found: " + value, path);
            return false;
        }

        return true;
    }

    /**
     * Check if attribute value is from a defined enumeration.
     */
    public boolean checkEnumeratedValue(Attributes dataset, int tag, String attributeName,
                                          String[] validValues, ValidationResult result, String path) {
        String value = dataset.getString(tag);
        if (value == null) {
            return true; // Already checked by required/type2 checks
        }

        for (String validValue : validValues) {
            if (value.equals(validValue)) {
                return true;
            }
        }

        result.addError("Attribute " + attributeName + " " + tagString(tag) +
                       " has invalid enumerated value: " + value, path);
        return false;
    }

    /**
     * Check UIDs for validity.
     */
    public boolean checkUID(Attributes dataset, int tag, String attributeName,
                              ValidationResult result, String path) {
        String uid = dataset.getString(tag);
        if (uid == null) {
            return true; // Already checked by required checks
        }

        // UID validation: must contain only digits and dots, no trailing dot
        if (!uid.matches("^[0-9.]+$")) {
            result.addError("Invalid UID format in " + attributeName + " " + tagString(tag) +
                          ": " + uid + " (must contain only digits and dots)", path);
            return false;
        }

        if (uid.endsWith(".")) {
            result.addError("Invalid UID in " + attributeName + " " + tagString(tag) +
                          ": " + uid + " (cannot end with dot)", path);
            return false;
        }

        if (uid.startsWith(".")) {
            result.addError("Invalid UID in " + attributeName + " " + tagString(tag) +
                          ": " + uid + " (cannot start with dot)", path);
            return false;
        }

        if (uid.contains("..")) {
            result.addError("Invalid UID in " + attributeName + " " + tagString(tag) +
                          ": " + uid + " (cannot contain consecutive dots)", path);
            return false;
        }

        // Each component must be valid
        String[] components = uid.split("\\.");
        for (String component : components) {
            if (component.isEmpty()) {
                result.addError("Invalid UID in " + attributeName + " " + tagString(tag) +
                              ": " + uid + " (empty component)", path);
                return false;
            }
            if (component.length() > 1 && component.startsWith("0")) {
                result.addWarning("UID component in " + attributeName + " " + tagString(tag) +
                                " has leading zero: " + component, path);
            }
        }

        return true;
    }

    /**
     * Check sequence attribute.
     */
    public boolean checkSequenceAttribute(Attributes dataset, int tag, String attributeName,
                                            boolean required, ValidationResult result, String path) {
        if (!dataset.contains(tag)) {
            if (required) {
                result.addError("Missing required sequence: " + attributeName + " " + tagString(tag), path);
                return false;
            }
            return true;
        }

        if (dataset.getSequence(tag) == null) {
            result.addError("Attribute " + attributeName + " " + tagString(tag) +
                          " is not a sequence", path);
            return false;
        }

        return true;
    }

    /**
     * Format tag as string (gggg,eeee).
     * Use bit operations to avoid dependency on Tag helper methods.
     */
    public String tagString(int tag) {
        int group = (tag >>> 16) & 0xFFFF;
        int element = tag & 0xFFFF;
        return String.format("(%04X,%04X)", group, element);
    }

    /**
     * Build path string for nested attributes.
     */
    public String buildPath(String parentPath, String attributeName) {
        if (parentPath == null || parentPath.isEmpty()) {
            return attributeName;
        }
        return parentPath + " > " + attributeName;
    }

    /**
     * Build path string with item number.
     */
    public String buildPath(String parentPath, String sequenceName, int itemNumber) {
        String seqPath = buildPath(parentPath, sequenceName);
        return seqPath + "[" + (itemNumber + 1) + "]";
    }
}

