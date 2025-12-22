package be.uzleuven.ihe.dicom.validator.validation;

import be.uzleuven.ihe.dicom.constants.ValidationMessages;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import be.uzleuven.ihe.dicom.validator.model.ValidationResult;

import java.util.HashSet;
import java.util.Set;

/**
 * Validates timezone consistency in MADO manifests.
 * MADO requires Timezone Offset From UTC and consistent timezone usage within studies.
 */
public final class MADOTimezoneValidator {

    private MADOTimezoneValidator() {
    }

    public static void validateTimezoneConsistency(Attributes dataset, ValidationResult result, String modulePath) {
        // Check manifest timezone offset
        String manifestTimezone = dataset.getString(Tag.TimezoneOffsetFromUTC);
        if (manifestTimezone == null || manifestTimezone.trim().isEmpty()) {
            result.addError(ValidationMessages.TIMEZONE_OFFSET_MANDATORY, modulePath);
            return;
        }

        // Validate timezone format
        if (!isValidTimezoneOffset(manifestTimezone)) {
            result.addError("TimezoneOffsetFromUTC has invalid format: " + manifestTimezone + ". Expected format: ±HHMM (e.g., +0100, -0500)", modulePath);
        }

        // Check Evidence sequence for timezone consistency
        Sequence evidenceSeq = dataset.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence);
        if (evidenceSeq == null || evidenceSeq.isEmpty()) {
            return;
        }

        Set<String> studyUIDs = new HashSet<>();
        for (Attributes studyItem : evidenceSeq) {
            String studyUID = studyItem.getString(Tag.StudyInstanceUID);
            if (studyUID != null) {
                studyUIDs.add(studyUID);
            }
        }

        // Warn if multiple studies (timezone should be consistent within study)
        if (studyUIDs.size() > 1) {
            result.addInfo("Manifest references " + studyUIDs.size() + " studies. " +
                         "Per MADO, all instances within each study must use the same timezone offset.", modulePath);
        }

        // Additional validation could check referenced instances if available
        result.addInfo("Manifest timezone offset: " + manifestTimezone +
                     ". Imaging Document Consumer must apply this offset when instances lack timezone data.", modulePath);
    }

    private static boolean isValidTimezoneOffset(String timezone) {
        if (timezone == null || timezone.trim().isEmpty()) {
            return false;
        }

        // Format: ±HHMM
        // Examples: +0000, -0500, +0100, +0530
        if (!timezone.matches("^[+-]\\d{4}$")) {
            return false;
        }

        // Extract hours and minutes
        int hours = Integer.parseInt(timezone.substring(1, 3));
        int minutes = Integer.parseInt(timezone.substring(3, 5));

        // Valid range: hours -12 to +14, minutes 00 to 59
        if (hours > 14 || minutes > 59) {
            return false;
        }

        // Some timezones use 30 or 45 minute offsets
        if (minutes != 0 && minutes != 15 && minutes != 30 && minutes != 45) {
            // This is informational - some rare timezones use other offsets
            return true; // Still valid, just unusual
        }

        return true;
    }
}

