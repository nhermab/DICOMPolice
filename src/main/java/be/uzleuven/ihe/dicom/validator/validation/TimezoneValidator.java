package be.uzleuven.ihe.dicom.validator.validation;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import be.uzleuven.ihe.dicom.validator.model.ValidationResult;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Timezone validation for DICOM objects used in XDS-I exchanges.
 * Validates Timezone Offset From UTC and ensures proper synchronization
 * between DICOM local time and XDS UTC metadata.
 */
public class TimezoneValidator {

    // Pattern for Timezone Offset From UTC: ±HHMM
    private static final Pattern TIMEZONE_PATTERN = Pattern.compile("^([+-])(\\d{2})(\\d{2})$");

    /**
     * Validate Timezone Offset From UTC attribute.
     * This is highly recommended for XDS-I.b to ensure proper time synchronization.
     */
    public static void validateTimezoneOffset(Attributes dataset, ValidationResult result, String path) {
        String timezoneOffset = dataset.getString(Tag.TimezoneOffsetFromUTC);

        if (timezoneOffset == null || timezoneOffset.isEmpty()) {
            result.addWarning("TimezoneOffsetFromUTC (0008,0201) is not present. " +
                            "This is highly recommended for XDS-I.b to ensure proper time synchronization " +
                            "between DICOM content and XDS metadata.", path);
            return;
        }

        // Validate format: ±HHMM
        Matcher matcher = TIMEZONE_PATTERN.matcher(timezoneOffset);
        if (!matcher.matches()) {
            result.addError("TimezoneOffsetFromUTC (0008,0201) has invalid format: " + timezoneOffset + ". Expected format: ±HHMM (e.g., +0100, -0500)", path);
            return;
        }

        int hours = Integer.parseInt(matcher.group(2));
        int minutes = Integer.parseInt(matcher.group(3));

        // Validate ranges
        if (hours > 14 || (hours == 14 && minutes > 0)) {
            result.addError("TimezoneOffsetFromUTC (0008,0201) has invalid offset: " + timezoneOffset +". Hours must be -14 to +14.", path);
        }

        if (minutes >= 60) {
            result.addError("TimezoneOffsetFromUTC (0008,0201) has invalid minutes: " + timezoneOffset + ". Minutes must be 00-59.", path);
        }

        result.addInfo("TimezoneOffsetFromUTC present: " + timezoneOffset, path);
    }

    /**
     * Validate consistency between ContentDate/ContentTime and timezone offset.
     * This helps ensure the "midnight bug" doesn't occur where dates appear inconsistent
     * due to timezone differences.
     */
    public static void validateContentTimeConsistency(Attributes dataset, ValidationResult result, String path) {
        String contentDate = dataset.getString(Tag.ContentDate);
        String contentTime = dataset.getString(Tag.ContentTime);
        String timezoneOffset = dataset.getString(Tag.TimezoneOffsetFromUTC);

        if (contentDate == null || contentTime == null) {
            return; // Already checked by other validators
        }

        if (timezoneOffset != null && !timezoneOffset.isEmpty()) {
            Matcher matcher = TIMEZONE_PATTERN.matcher(timezoneOffset);
            if (matcher.matches()) {
                String sign = matcher.group(1);
                int hours = Integer.parseInt(matcher.group(2));
                int minutes = Integer.parseInt(matcher.group(3));

                int offsetMinutes = hours * 60 + minutes;
                if ("-".equals(sign)) {
                    offsetMinutes = -offsetMinutes;
                }

                try {
                    // Parse content date/time
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");

                    Date date = dateFormat.parse(contentDate);

                    // Parse time (handle fractional seconds)
                    String timeStr = contentTime.split("\\.")[0]; // Remove fractional part
                    if (timeStr.length() < 6) {
                        timeStr = String.format("%-6s", timeStr).replace(' ', '0');
                    }

                    Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                    cal.setTime(date);

                    int hour = Integer.parseInt(timeStr.substring(0, 2));
                    int minute = Integer.parseInt(timeStr.substring(2, 4));
                    int second = Integer.parseInt(timeStr.substring(4, 6));

                    cal.set(Calendar.HOUR_OF_DAY, hour);
                    cal.set(Calendar.MINUTE, minute);
                    cal.set(Calendar.SECOND, second);

                    // Convert to UTC
                    cal.add(Calendar.MINUTE, -offsetMinutes);

                    result.addInfo(String.format("ContentDate/Time: %s %s %s converts to UTC: %tF %<tT",
                                                contentDate, contentTime, timezoneOffset, cal), path);

                } catch (ParseException e) {
                    result.addWarning("Could not parse ContentDate/ContentTime for timezone consistency check: "
                                    + e.getMessage(), path);
                }
            }
        }
    }

    /**
     * Check for common timezone-related issues:
     * - Missing timezone when dates/times span multiple days
     * - Inconsistent date values that might indicate timezone problems
     */
    public static void checkTimezoneConsistency(Attributes dataset, ValidationResult result, String path) {
        String studyDate = dataset.getString(Tag.StudyDate);
        String contentDate = dataset.getString(Tag.ContentDate);
        String timezoneOffset = dataset.getString(Tag.TimezoneOffsetFromUTC);

        // If StudyDate and ContentDate differ significantly and no timezone is specified, warn
        if (studyDate != null && contentDate != null &&
            !studyDate.equals(contentDate) &&
            (timezoneOffset == null || timezoneOffset.isEmpty())) {

            result.addWarning("StudyDate (" + studyDate + ") and ContentDate (" + contentDate + ") " +
                            "differ, but TimezoneOffsetFromUTC is not present. This may cause " +
                            "\"midnight bug\" issues in XDS-I where dates appear inconsistent.", path);
        }
    }
}

