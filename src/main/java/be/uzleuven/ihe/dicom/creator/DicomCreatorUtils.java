package be.uzleuven.ihe.dicom.creator;

import org.dcm4che3.data.*;
import org.dcm4che3.io.DicomOutputStream;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Common utility methods for DICOM creator classes.
 */
public class DicomCreatorUtils {

    private static final SecureRandom RND = new SecureRandom();

    // --- Date/Time Utilities ---

    public static String todayYYYYMMDD() {
        return new SimpleDateFormat("yyyyMMdd").format(new Date());
    }

    public static String nowHHMMSS() {
        return new SimpleDateFormat("HHmmss").format(new Date());
    }

    public static String now(String format) {
        return new SimpleDateFormat(format).format(new Date());
    }

    public static String randomDateYYYYMMDD(int startYearInclusive, int endYearInclusive) {
        int year = startYearInclusive + RND.nextInt(Math.max(1, endYearInclusive - startYearInclusive + 1));
        int month = 1 + RND.nextInt(12);
        int day = 1 + RND.nextInt(28);
        return String.format("%04d%02d%02d", year, month, day);
    }

    public static String timezoneOffsetFromUTC() {
        java.util.TimeZone tz = java.util.TimeZone.getDefault();
        int offsetMinutes = tz.getOffset(System.currentTimeMillis()) / 60000;
        char sign = offsetMinutes >= 0 ? '+' : '-';
        int abs = Math.abs(offsetMinutes);
        int hh = abs / 60;
        int mm = abs % 60;
        return String.format("%c%02d%02d", sign, hh, mm);
    }

    public static String randomSeriesTime(String baseStudyTime) {
        // Keep valid DICOM TM; jitter seconds a bit.
        try {
            int hh = Integer.parseInt(baseStudyTime.substring(0, 2));
            int mm = Integer.parseInt(baseStudyTime.substring(2, 4));
            int ss = Integer.parseInt(baseStudyTime.substring(4, 6));
            ss = (ss + RND.nextInt(50)) % 60;
            return String.format("%02d%02d%02d", hh, mm, ss);
        } catch (Exception e) {
            return now("HHmmss");
        }
    }

    // --- Random Data Generation ---

    public static String randomPersonName() {
        String family = randomFrom("Smith", "Johnson", "Brown", "Taylor", "Anderson", "Thomas", "Jackson", "White", "Harris", "Martin");
        String given = randomFrom("Alex", "Sam", "Jordan", "Casey", "Taylor", "Morgan", "Riley", "Jamie", "Cameron", "Avery");
        return family + "^" + given;
    }

    public static String randomPatientId() {
        return "P" + (100000 + RND.nextInt(900000)) + "-" + (10 + RND.nextInt(90));
    }

    public static String randomIssuer() {
        return randomFrom("HOSPITAL_A", "HOSPITAL_B", "REGION_X", "NATIONAL_ID", "IHE_TEST");
    }

    public static String randomAccession() {
        return "ACC" + (100000 + RND.nextInt(900000));
    }

    public static String randomFrom(String... values) {
        return values[RND.nextInt(values.length)];
    }

    public static int randomInt(int bound) {
        return RND.nextInt(bound);
    }

    // --- DICOM Code Utilities ---

    /**
     * Creates a DICOM code sequence item.
     */
    public static Attributes code(String value, String scheme, String meaning) {
        Attributes a = new Attributes();
        a.setString(Tag.CodeValue, VR.SH, value);
        a.setString(Tag.CodingSchemeDesignator, VR.SH, scheme);
        a.setString(Tag.CodeMeaning, VR.LO, meaning);
        return a;
    }

    /**
     * Creates a Content Template Sequence item.
     */
    public static Attributes createTemplateItem(String tid) {
        Attributes item = new Attributes();
        item.setString(Tag.MappingResource, VR.CS, "DCMR");
        item.setString(Tag.TemplateIdentifier, VR.CS, tid);
        return item;
    }

    // --- DICOM File I/O ---

    /**
     * Writes DICOM dataset to file in Part 10 format.
     */
    public static void writeDicomFile(File file, Attributes dataset) throws IOException {
        writeDicomFile(file, dataset, UID.ExplicitVRLittleEndian);
    }

    /**
     * Writes DICOM dataset to file in Part 10 format with specified transfer syntax.
     */
    public static void writeDicomFile(File file, Attributes dataset, String transferSyntax) throws IOException {
        try (DicomOutputStream dos = new DicomOutputStream(file)) {
            dos.writeDataset(dataset.createFileMetaInformation(transferSyntax), dataset);
        }
    }

    // --- Validation Helpers ---

    /**
     * Asserts that a sequence is present and not empty.
     */
    public static void assertNotEmptySequence(Attributes d, int tag, String name) {
        Sequence seq = d.getSequence(tag);
        if (seq == null || seq.isEmpty()) {
            throw new IllegalStateException(name + " is missing/empty in generated dataset");
        }
    }
}

