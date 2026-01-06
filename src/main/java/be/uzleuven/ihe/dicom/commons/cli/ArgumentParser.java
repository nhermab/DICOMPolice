package be.uzleuven.ihe.dicom.commons.cli;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Base utility class for parsing command-line arguments.
 * Provides common patterns for flag detection, value extraction, and error handling.
 */
public class ArgumentParser {

    /**
     * Checks if any of the specified argument names are present in the args array.
     * Case-insensitive matching.
     */
    protected static boolean hasArg(String[] args, String... names) {
        for (String a : args) {
            for (String n : names) {
                if (n.equalsIgnoreCase(a)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Requires a value to follow a flag, throwing an exception if missing or out of bounds.
     *
     * @param args The full argument array
     * @param index The index where the value should be
     * @param flag The flag name (for error messages)
     * @return The value at the specified index
     * @throws IllegalArgumentException if the index is out of bounds
     */
    protected static String requireValue(String[] args, int index, String flag) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + flag);
        }
        return args[index];
    }

    /**
     * Checks if a string is NOT a flag (doesn't start with '-').
     */
    protected static boolean isNotFlag(String arg) {
        return arg == null || !arg.startsWith("-");
    }

    /**
     * Checks if a string is a flag (starts with '-').
     */
    protected static boolean isFlag(String arg) {
        return arg != null && arg.startsWith("-");
    }

    /**
     * Parses an integer value from the argument array.
     *
     * @param args The full argument array
     * @param index The index where the value should be
     * @param flag The flag name (for error messages)
     * @return The parsed integer
     * @throws IllegalArgumentException if the value is missing or not a valid integer
     */
    protected static int requireIntValue(String[] args, int index, String flag) {
        String value = requireValue(args, index, flag);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer value for " + flag + ": " + value);
        }
    }

    /**
     * Parses an ISO date (YYYY-MM-DD) from the argument array.
     *
     * @param args The full argument array
     * @param index The index where the value should be
     * @param flag The flag name (for error messages)
     * @return The parsed LocalDate
     * @throws IllegalArgumentException if the value is missing or not a valid date
     */
    protected static LocalDate requireDateValue(String[] args, int index, String flag) {
        String value = requireValue(args, index, flag);
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date for " + flag + ": '" + value + "'. Expected ISO format YYYY-MM-DD");
        }
    }

    /**
     * Gets a string value or returns a default if the flag is not found.
     *
     * @param args The full argument array
     * @param defaultValue The default value to return
     * @param flagNames Flag names to search for
     * @return The value following the flag, or defaultValue if flag not found
     */
    protected static String getStringOrDefault(String[] args, String defaultValue, String... flagNames) {
        for (int i = 0; i < args.length; i++) {
            for (String flag : flagNames) {
                if (flag.equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                    return args[i + 1];
                }
            }
        }
        return defaultValue;
    }

    /**
     * Extracts all non-flag arguments (arguments that don't start with '-').
     *
     * @param args The full argument array
     * @return List of non-flag arguments
     */
    protected static List<String> extractNonFlagArgs(String[] args) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (isNotFlag(arg)) {
                result.add(arg);
            } else if (i + 1 < args.length && isNotFlag(args[i + 1])) {
                // Skip the value following a flag
                i++;
            }
        }
        return result;
    }

    /**
     * Makes a string safe for use in filenames by replacing non-alphanumeric characters.
     *
     * @param s The string to sanitize
     * @return Safe filename string
     */
    protected static String safeFileToken(String s) {
        if (s == null) return "";
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * Prints an error message and exits with the specified code.
     */
    protected static void exitWithError(String message, int exitCode) {
        System.err.println("Error: " + message);
        System.exit(exitCode);
    }

    /**
     * Prints an error message with exception details and exits.
     */
    protected static void exitWithError(String message, Throwable error, int exitCode) {
        System.err.println("Error: " + message);
        if (error != null) {
            error.printStackTrace(System.err);
        }
        System.exit(exitCode);
    }
}

