package be.uzleuven.ihe.dicom.creator.evil;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;

/**
 * Applies small corruptions to a DICOM dataset.
 * The goal isn't to crash dcm4che, but to violate profile/spec expectations in realistic ways.
 */
public final class EvilMutator {

    private EvilMutator() {
    }

    /**
     * Apply a single mild corruption (call-site decides probability).
     */
    public static void corruptOne(Attributes d) {
        int pick = EvilDice.randomInt(8);
        switch (pick) {
            case 0:
                // Wrong SOP Class
                d.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.2"); // CT Image Storage
                break;
            case 1:
                // Invalid timezone (wrong format)
                d.setString(Tag.TimezoneOffsetFromUTC, VR.SH, "UTC");
                break;
            case 2:
                // Modality wrong value for KO documents
                d.setString(Tag.Modality, VR.CS, "CT");
                break;
            case 3:
                // Break SR root ValueType
                d.setString(Tag.ValueType, VR.CS, "TEXT");
                break;
            case 4:
                // Put PatientName in wrong VR (still allowed by API but nonsensical content)
                d.setString(Tag.PatientName, VR.LO, "NOT^A^PN");
                break;
            case 5:
                // Add an incompatible/odd tag in public range (should provoke validators)
                d.setString(0x0043102A, VR.LO, "EVIL");
                break;
            case 6:
                // Remove a typically required tag by overwriting with empty value
                d.setString(Tag.StudyInstanceUID, VR.UI, "");
                break;
            default:
                // Wrong continuity can break templates
                d.setString(Tag.ContinuityOfContent, VR.CS, "CONTINUOUS");
                break;
        }
    }
}
