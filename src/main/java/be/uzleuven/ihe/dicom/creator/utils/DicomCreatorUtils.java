package be.uzleuven.ihe.dicom.creator.utils;

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

    // --- UID Utilities ---

    /**
     * Normalizes a DICOM UID so that no component starts with leading zeros,
     * unless the component is exactly "0" (DICOM PS3.5 §6.2).
     * <p>
     * Example: "...061159.1.005.002" -> "...61159.1.5.2"
     * </p>
     * <p>
     * Note: only call this for UIDs you generate locally. Do not rewrite UIDs
     * originating from external systems.
     * </p>
     */
    public static String normalizeUidNoLeadingZeros(String uid) {
        if (uid == null) return null;
        String trimmed = uid.trim();
        if (trimmed.isEmpty()) return uid;

        String[] parts = trimmed.split("\\.");
        boolean changed = false;
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p == null) continue;
            String t = p.trim();
            if (t.isEmpty()) continue;

            // Only numeric UID components are expected. If not numeric, keep as-is.
            boolean numeric = true;
            for (int c = 0; c < t.length(); c++) {
                char ch = t.charAt(c);
                if (ch < '0' || ch > '9') {
                    numeric = false;
                    break;
                }
            }
            if (!numeric) continue;

            if (t.length() > 1 && t.charAt(0) == '0') {
                int firstNonZero = 0;
                while (firstNonZero < t.length() && t.charAt(firstNonZero) == '0') {
                    firstNonZero++;
                }
                String normalized = (firstNonZero == t.length()) ? "0" : t.substring(firstNonZero);
                if (!normalized.equals(t)) {
                    parts[i] = normalized;
                    changed = true;
                }
            }
        }

        if (!changed) return trimmed;
        return String.join(".", parts);
    }

    /**
     * Generates a UID using dcm4che and normalizes it so it never contains
     * components with leading zeros.
     */
    public static String createNormalizedUid() {
        return normalizeUidNoLeadingZeros(org.dcm4che3.util.UIDUtils.createUID());
    }

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
        String family = randomFrom(
                "Smith", "Johnson", "Brown", "Taylor", "Anderson", "Thomas", "Jackson", "White", "Harris", "Martin",
                "Peeters", "Janssens", "DeWitte", "Dubois", "Lefevre", "Laurent", "Moreau", "Dupont",
                "Muller", "Schmidt", "Schneider", "Wagner", "Fischer",
                "Garcia", "Rossi", "Bianchi", "Silva", "Santos",
                "Kim", "Lee", "Park", "Choi",
                "Sato", "Suzuki", "Takahashi", "Ito", "Yamamoto",
                "Nguyen", "Tran",
                "Shevchenko", "Bondarenko", "Ivanov", "Petrov",
                "Haddad", "Almasri", "Khan", "Rahman", "Hassan",
                "Ahmadi", "Mohammadi", "Hosseini",
                "Patel", "Singh", "OConnor", "Murphy", "Cohen", "Novak"
        );

        String given = randomFrom(
                "Emma", "Olivia", "Ava", "Sophia", "Isabella", "Mia", "Charlotte", "Amelia", "Harper", "Evelyn",
                "Liam", "Noah", "Oliver", "Elijah", "Lucas", "Mason", "Logan", "James", "Benjamin",
                "Camille", "Chloe", "Lea", "Juliette", "Louise", "Luc", "Louis", "Antoine", "Claire", "Marie",
                "Anna", "Lena", "Sophie", "Laura", "Lukas", "Max", "Jonas", "Felix",
                "Bram", "Tomas", "Lotte", "Nina",
                "Sofia", "Lucia", "Maria", "Diego", "Carlos", "Mateo",
                "Fatima", "Aisha", "Layla", "Zainab", "Nadia", "Sara",
                "Ahmed", "Mohammed", "Omar", "Hassan", "Youssef", "Ali",
                "Zahra", "Nazanin", "Fatemeh", "Mahnaz", "Leila",
                "AliReza", "Reza", "Mohammad", "Hossein", "Arman",
                "Sakura", "Yui", "Yuki", "Haruka", "Nao", "Aoi",
                "Haruki", "Takumi", "Yuto", "Kenta",
                "Jiwoo", "Minji", "Yuna", "Seojin", "Hyejin", "Soo",
                "Hyun", "Joon", "Minho", "Seung",
                "Olena", "Oksana", "Kateryna", "Anastasia", "Daria",
                "Dmytro", "Mykola", "Vlad",
                "Aanya", "Priya", "Neha", "Rahul", "Arjun",
                "Alex", "Sam", "Jordan", "Casey", "Taylor", "Morgan", "Riley", "Jamie", "Cameron", "Avery",
                "Isla", "Poppy", "Ibrahim", "Yosef", "Zara", "Maya"
        );
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
        // Defensive: ensure core identity tags are present and consistent before writing.
        // This helps catch logic faults where SOPClassUID gets overwritten late.
        String sopClassUid = dataset.getString(Tag.SOPClassUID);
        String sopInstanceUid = dataset.getString(Tag.SOPInstanceUID);
        if (sopClassUid == null || sopClassUid.trim().isEmpty()) {
            throw new IllegalStateException("Cannot write DICOM: SOPClassUID is missing");
        }
        if (sopInstanceUid == null || sopInstanceUid.trim().isEmpty()) {
            throw new IllegalStateException("Cannot write DICOM: SOPInstanceUID is missing");
        }

        // Final safety net: keep generated outputs PS3.5 §6.2-compliant.
        normalizeCoreUidsInPlace(dataset);

        try (DicomOutputStream dos = new DicomOutputStream(file)) {
            // File Meta is derived from SOPClassUID/SOPInstanceUID; create it at the last moment.
            dos.writeDataset(dataset.createFileMetaInformation(transferSyntax), dataset);
        }
    }

    private static void normalizeCoreUidsInPlace(Attributes dataset) {
        // Only normalize the manifest's own SOP Instance UID.
        // Study/Series UIDs can be externally sourced (e.g., from PACS) and must not be rewritten.
        normalizeUidTag(dataset, Tag.SOPInstanceUID);

        // These are commonly present in image objects; harmless on KO too if absent.
        // (Kept as-is to avoid rewriting external UIDs in query-derived manifests.)
        normalizeUidTag(dataset, Tag.FrameOfReferenceUID);
        normalizeUidTag(dataset, Tag.SynchronizationFrameOfReferenceUID);
    }

    private static void normalizeUidTag(Attributes dataset, int tag) {
        if (dataset == null) return;
        String v = dataset.getString(tag);
        if (v == null || v.trim().isEmpty()) return;
        String normalized = normalizeUidNoLeadingZeros(v);
        if (!v.equals(normalized)) {
            dataset.setString(tag, VR.UI, normalized);
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
