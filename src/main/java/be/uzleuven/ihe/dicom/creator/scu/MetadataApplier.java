package be.uzleuven.ihe.dicom.creator.scu;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Utility class for applying default metadata to DICOM attributes.
 * Ensures IHE XDS-I.b compliance by filling in missing required attributes.
 */
public class MetadataApplier {

    /**
     * Apply default metadata to attributes that are missing from C-FIND response.
     *
     * @param attrs The attributes to enhance with defaults
     * @param defaults The default metadata configuration
     */
    public static void applyDefaults(Attributes attrs, DefaultMetadata defaults) {
        // Patient ID Issuer - essential for IHE XDS-I.b
        if (!attrs.contains(Tag.IssuerOfPatientID) ||
                attrs.getString(Tag.IssuerOfPatientID, "").trim().isEmpty()) {
            attrs.setString(Tag.IssuerOfPatientID, VR.LO, defaults.patientIdIssuerLocalNamespace);
        }

        // Institution Name
        if (!attrs.contains(Tag.InstitutionName) ||
                attrs.getString(Tag.InstitutionName, "").trim().isEmpty()) {
            attrs.setString(Tag.InstitutionName, VR.LO, defaults.institutionName);
        }

        // Study Date - if missing, use current date
        if (!attrs.contains(Tag.StudyDate) ||
                attrs.getString(Tag.StudyDate, "").trim().isEmpty()) {
            String currentDate = new SimpleDateFormat("yyyyMMdd").format(new Date());
            attrs.setString(Tag.StudyDate, VR.DA, currentDate);
        }

        // Study Time - if missing, use current time
        if (!attrs.contains(Tag.StudyTime) ||
                attrs.getString(Tag.StudyTime, "").trim().isEmpty()) {
            String currentTime = new SimpleDateFormat("HHmmss.SSSSSS").format(new Date());
            attrs.setString(Tag.StudyTime, VR.TM, currentTime);
        }
    }

    /**
     * Normalizes PatientSex (0010,0040) to valid DICOM enumerated values.
     * Valid values are: M, F, O, or empty.
     * Some upstream systems use non-standard values (e.g. 'W').
     *
     * @param patientSex The patient sex value to normalize
     * @return Normalized patient sex value
     */
    public static String normalizePatientSex(String patientSex) {
        if (patientSex == null) {
            return "";
        }
        String s = patientSex.trim().toUpperCase();
        if (s.isEmpty()) {
            return "";
        }
        if ("M".equals(s) || "F".equals(s) || "O".equals(s)) {
            return s;
        }
        // Common non-standard mappings
        if ("W".equals(s)) { // woman
            return "F";
        }
        if ("MAN".equals(s) || "MALE".equals(s)) {
            return "M";
        }
        if ("WOMAN".equals(s) || "FEMALE".equals(s)) {
            return "F";
        }
        // Fallback for unknown/unsupported values
        return "O";
    }
}

