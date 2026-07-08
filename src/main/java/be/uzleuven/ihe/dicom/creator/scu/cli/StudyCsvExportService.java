package be.uzleuven.ihe.dicom.creator.scu.cli;

import be.uzleuven.ihe.dicom.creator.scu.CFindQueryBuilder;
import be.uzleuven.ihe.dicom.creator.scu.CFindResult;
import be.uzleuven.ihe.dicom.creator.scu.CFindService;
import be.uzleuven.ihe.dicom.creator.scu.DefaultMetadata;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Exports study-level C-FIND results to CSV.
 * Queries one study day at a time and one modality at a time, then de-duplicates by StudyInstanceUID.
 */
@SuppressWarnings("unused")
public class StudyCsvExportService {

    private static final Logger LOG = LoggerFactory.getLogger(StudyCsvExportService.class);
    private static final DateTimeFormatter DICOM_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String[] CSV_HEADER = {
        "STUDYINSTANCEUID",
        "STUDYDATE",
        "ACCESSIONNUMBER",
        "PATIENTID",
        "NUMBEROFSTUDYRELATEDINSTANCES"
    };

    private final CFindService cFindService;

    public StudyCsvExportService(DefaultMetadata defaults) {
        this.cFindService = new CFindService(defaults);
    }

    public int exportStudies(LocalDate beginDate, LocalDate endDate, List<String> modalities, File outputCsv, boolean overwrite) throws IOException {
        validateInputs(beginDate, endDate, modalities, outputCsv);
        ensureOutputParentExists(outputCsv);
        ensureOutputWritable(outputCsv, overwrite);

        List<String> normalizedModalities = normalizeModalities(modalities);
        Set<String> seenStudyUids = new LinkedHashSet<>();
        int writtenRows = 0;

        System.out.println("Opening CSV immediately: " + outputCsv.getAbsolutePath());
        try (BufferedWriter writer = Files.newBufferedWriter(outputCsv.toPath(), StandardCharsets.UTF_8)) {
            writeHeader(writer);

            LocalDate cursor = beginDate;
            while (!cursor.isAfter(endDate)) {
                String dicomDate = DICOM_DATE.format(cursor);
                System.out.println("Day " + cursor + " (" + normalizedModalities.size() + " modality queries)");
                Map<String, CsvRow> rowsByStudyUid = new LinkedHashMap<>();
                int matchedRowsThisDay = 0;

                for (String modality : normalizedModalities) {
                    Attributes keys = CFindQueryBuilder.buildStudyDateModalityQuery(dicomDate, modality);
                    LOG.info(
                        "C-FIND query: level={}, studyDate={}, modality={}, StudyInstanceUID={}, PatientID={}, AccessionNumber={}, NumStudyRelatedInstances={}, NumStudyRelatedSeries={}",
                        keys.getString(Tag.QueryRetrieveLevel),
                        keys.getString(Tag.StudyDate),
                        keys.getString(Tag.ModalitiesInStudy),
                        keys.getString(Tag.StudyInstanceUID),
                        keys.getString(Tag.PatientID),
                        keys.getString(Tag.AccessionNumber),
                        keys.getString(Tag.NumberOfStudyRelatedInstances),
                        keys.getString(Tag.NumberOfStudyRelatedSeries)
                    );

                    CFindResult result = cFindService.performCFind(keys);
                    if (!result.isSuccess()) {
                        throw new IOException(result.getErrorMessage() != null ? result.getErrorMessage() : "C-FIND failed");
                    }

                    LOG.info("C-FIND result: level={}, studyDate={}, modality={}, matched={}, uniqueSoFar={}",
                        keys.getString(Tag.QueryRetrieveLevel),
                        keys.getString(Tag.StudyDate),
                        keys.getString(Tag.ModalitiesInStudy),
                        result.getMatches().size(),
                        rowsByStudyUid.size());

                    for (Attributes attrs : result.getMatches()) {
                        CsvRow row = CsvRow.from(attrs, dicomDate);
                        String studyInstanceUid = row.studyInstanceUid;
                        if (studyInstanceUid == null) {
                            continue;
                        }

                        rowsByStudyUid.merge(studyInstanceUid, row, CsvRow::merge);
                        matchedRowsThisDay++;
                    }

                    System.out.println("    matched=" + result.getMatches().size() + " merged=" + rowsByStudyUid.size() + " totalWritten=" + writtenRows);
                }

                int newRowsThisDay = 0;
                for (CsvRow row : rowsByStudyUid.values()) {
                    if (row.studyInstanceUid == null || !seenStudyUids.add(row.studyInstanceUid)) {
                        continue;
                    }

                    writer.write(row.toCsvLine());
                    writer.newLine();
                    writtenRows++;
                    newRowsThisDay++;
                }

                writer.flush();
                System.out.println("Finished day " + cursor + ", matched=" + matchedRowsThisDay + ", new=" + newRowsThisDay + ", cumulative unique studies=" + writtenRows);
                cursor = cursor.plusDays(1);
            }
        }

        System.out.println("CSV write finished: " + outputCsv.getAbsolutePath());
        return writtenRows;
    }

    private static void validateInputs(LocalDate beginDate, LocalDate endDate, List<String> modalities, File outputCsv) {
        if (beginDate == null || endDate == null) {
            throw new IllegalArgumentException("Both beginDate and endDate are required");
        }
        if (beginDate.isAfter(endDate)) {
            throw new IllegalArgumentException("beginDate must be <= endDate");
        }
        if (modalities == null || modalities.isEmpty()) {
            throw new IllegalArgumentException("At least one modality is required");
        }
        if (outputCsv == null) {
            throw new IllegalArgumentException("Output CSV file is required");
        }
        if (outputCsv.exists() && outputCsv.isDirectory()) {
            throw new IllegalArgumentException("Output path points to a directory: " + outputCsv.getAbsolutePath());
        }
    }

    private static void ensureOutputParentExists(File outputCsv) throws IOException {
        File parent = outputCsv.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Failed to create output directory: " + parent.getAbsolutePath());
        }
    }

    private static void ensureOutputWritable(File outputCsv, boolean overwrite) throws IOException {
        if (outputCsv.exists() && !overwrite) {
            throw new IOException("Output exists (use --overwrite): " + outputCsv.getAbsolutePath());
        }
    }

    private static List<String> normalizeModalities(List<String> modalities) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String modality : modalities) {
            if (modality == null) {
                continue;
            }
            for (String token : modality.split(",")) {
                String normalized = token.trim();
                if (!normalized.isEmpty()) {
                    out.add(normalized.toUpperCase(Locale.ROOT));
                }
            }
        }
        return new ArrayList<>(out);
    }

    private static void writeHeader(BufferedWriter writer) throws IOException {
        writer.write(String.join(",", CSV_HEADER));
        writer.newLine();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String csvEscape(String value) {
        String safe = value == null ? "" : value;
        boolean needsQuotes = safe.indexOf(',') >= 0 || safe.indexOf('"') >= 0 || safe.indexOf('\n') >= 0 || safe.indexOf('\r') >= 0;
        if (!needsQuotes) {
            return safe;
        }
        return '"' + safe.replace("\"", "\"\"") + '"';
    }

    private static final class CsvRow {
        private final String studyInstanceUid;
        private final String studyDate;
        private final String accessionNumber;
        private final String patientId;
        private final String numberOfStudyRelatedInstances;

        private CsvRow(String studyInstanceUid, String studyDate, String accessionNumber, String patientId, String numberOfStudyRelatedInstances) {
            this.studyInstanceUid = studyInstanceUid;
            this.studyDate = studyDate;
            this.accessionNumber = accessionNumber;
            this.patientId = patientId;
            this.numberOfStudyRelatedInstances = numberOfStudyRelatedInstances;
        }

        static CsvRow from(Attributes attrs, String fallbackStudyDate) {
            return new CsvRow(
                trimToNull(attrs.getString(Tag.StudyInstanceUID)),
                firstNonBlank(trimToNull(attrs.getString(Tag.StudyDate)), fallbackStudyDate),
                trimToNull(attrs.getString(Tag.AccessionNumber)),
                trimToNull(attrs.getString(Tag.PatientID)),
                trimToNull(attrs.getString(Tag.NumberOfStudyRelatedInstances))
            );
        }

        CsvRow merge(CsvRow other) {
            return new CsvRow(
                firstNonBlank(studyInstanceUid, other.studyInstanceUid),
                firstNonBlank(studyDate, other.studyDate),
                firstNonBlank(accessionNumber, other.accessionNumber),
                firstNonBlank(patientId, other.patientId),
                firstNonBlank(numberOfStudyRelatedInstances, other.numberOfStudyRelatedInstances)
            );
        }

        String toCsvLine() {
            return String.join(",",
                csvEscape(studyInstanceUid),
                csvEscape(studyDate),
                csvEscape(accessionNumber),
                csvEscape(patientId),
                csvEscape(numberOfStudyRelatedInstances)
            );
        }

        private static String firstNonBlank(String first, String second) {
            String trimmedFirst = trimToNull(first);
            return trimmedFirst != null ? trimmedFirst : second;
        }
    }
}

