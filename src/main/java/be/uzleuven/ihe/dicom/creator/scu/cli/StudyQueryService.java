package be.uzleuven.ihe.dicom.creator.scu.cli;

import be.uzleuven.ihe.dicom.creator.scu.CFindResult;
import be.uzleuven.ihe.dicom.creator.scu.SCUManifestCreator;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Centralizes all query patterns needed by the CLI.
 * This is intentionally conservative and uses only STUDY level queries to
 * resolve StudyInstanceUIDs.
 */
public class StudyQueryService {

    private static final DateTimeFormatter DICOM_DA = DateTimeFormatter.BASIC_ISO_DATE; // YYYYMMDD

    private final SCUManifestCreator creator;

    public StudyQueryService(SCUManifestCreator creator) {
        this.creator = creator;
    }

    public List<StudyDescriptor> resolveStudies(QueryCriteria criteria, int maxResults) throws IOException {
        criteria.validate();

        // Crawl mode: from beginDate to endDate inclusive, using StudyDate range queries
        if (criteria.isCrawlByDateRange()) {
            return findStudiesByStudyDateRange(criteria.getBeginDate(), criteria.getEndDate(), criteria.getWindowDays(), maxResults);
        }

        // If we already have a study UID, don't search.
        if (criteria.getStudyInstanceUID() != null && !criteria.getStudyInstanceUID().trim().isEmpty()) {
            List<StudyDescriptor> one = new ArrayList<>();
            one.add(new StudyDescriptor(criteria.getStudyInstanceUID().trim(), null, criteria.getAccessionNumber(), null));
            return one;
        }

        List<Attributes> matches;
        if (criteria.getAccessionNumber() != null && !criteria.getAccessionNumber().trim().isEmpty()) {
            matches = findStudiesByAccessionNumber(criteria.getAccessionNumber().trim());
        } else if (!criteria.getPatientIds().isEmpty() || !criteria.getStudyDates().isEmpty()) {
            matches = findStudiesByPatientAndOrDate(criteria.getPatientIds(), criteria.getStudyDates());
        } else {
            throw new IllegalArgumentException("Unsupported criteria combination");
        }

        return toStudyDescriptors(matches, maxResults);
    }

    /**
     * Resolve studies and emit each {@link StudyDescriptor} to the provided consumer as soon as it is discovered.
     * This avoids holding the full list of studies in memory AND allows incremental output generation during
     * long-running crawls.
     */
    public void resolveStudiesStreaming(QueryCriteria criteria, int maxResults, Consumer<StudyDescriptor> onStudy) throws IOException {
        criteria.validate();
        if (onStudy == null) {
            throw new IllegalArgumentException("onStudy consumer is required");
        }

        // Crawl mode: from beginDate to endDate inclusive, using StudyDate range queries
        if (criteria.isCrawlByDateRange()) {
            streamStudiesByStudyDateRange(criteria.getBeginDate(), criteria.getEndDate(), criteria.getWindowDays(), maxResults, onStudy);
            return;
        }

        // If we already have a study UID, don't search.
        if (criteria.getStudyInstanceUID() != null && !criteria.getStudyInstanceUID().trim().isEmpty()) {
            onStudy.accept(new StudyDescriptor(criteria.getStudyInstanceUID().trim(), null, criteria.getAccessionNumber(), null));
            return;
        }

        List<Attributes> matches;
        if (criteria.getAccessionNumber() != null && !criteria.getAccessionNumber().trim().isEmpty()) {
            matches = findStudiesByAccessionNumber(criteria.getAccessionNumber().trim());
        } else if (!criteria.getPatientIds().isEmpty() || !criteria.getStudyDates().isEmpty()) {
            matches = findStudiesByPatientAndOrDate(criteria.getPatientIds(), criteria.getStudyDates());
        } else {
            throw new IllegalArgumentException("Unsupported criteria combination");
        }

        // Stream unique, with maxResults
        Set<String> seen = new LinkedHashSet<>();
        int emitted = 0;
        for (Attributes a : matches) {
            String uid = a.getString(Tag.StudyInstanceUID);
            if (uid == null || uid.trim().isEmpty()) {
                continue;
            }
            if (!seen.add(uid)) {
                continue;
            }

            onStudy.accept(new StudyDescriptor(
                uid,
                a.getString(Tag.PatientID),
                a.getString(Tag.AccessionNumber),
                a.getString(Tag.StudyDate)
            ));

            emitted++;
            if (maxResults > 0 && emitted >= maxResults) {
                break;
            }
        }
    }

    private List<StudyDescriptor> findStudiesByStudyDateRange(LocalDate begin, LocalDate end, int windowDays, int maxResults) throws IOException {
        if (begin == null || end == null) {
            throw new IllegalArgumentException("begin/end required");
        }
        if (windowDays < 1 || windowDays > 7) {
            throw new IllegalArgumentException("windowDays must be 1..7");
        }

        List<StudyDescriptor> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        LocalDate cursor = begin;
        while (!cursor.isAfter(end)) {
            LocalDate windowEnd = cursor.plusDays(windowDays - 1L);
            if (windowEnd.isAfter(end)) {
                windowEnd = end;
            }

            String range = toDicomDa(cursor) + "-" + toDicomDa(windowEnd);

            Attributes keys = baseStudyLevelKeys();
            keys.setString(Tag.StudyDate, VR.DA, range);

            List<Attributes> matches = creatorCFind(keys);
            for (Attributes a : matches) {
                String uid = a.getString(Tag.StudyInstanceUID);
                if (uid == null || uid.trim().isEmpty()) {
                    continue;
                }
                if (!seen.add(uid)) {
                    continue;
                }

                out.add(new StudyDescriptor(
                    uid,
                    a.getString(Tag.PatientID),
                    a.getString(Tag.AccessionNumber),
                    a.getString(Tag.StudyDate)
                ));

                if (maxResults > 0 && out.size() >= maxResults) {
                    return out;
                }
            }

            cursor = windowEnd.plusDays(1);
        }

        return out;
    }

    private void streamStudiesByStudyDateRange(LocalDate begin, LocalDate end, int windowDays, int maxResults, Consumer<StudyDescriptor> onStudy) throws IOException {
        if (begin == null || end == null) {
            throw new IllegalArgumentException("begin/end required");
        }
        if (windowDays < 1 || windowDays > 31) {
            throw new IllegalArgumentException("windowDays must be 1..31");
        }

        Set<String> seen = new LinkedHashSet<>();
        int emitted = 0;

        LocalDate cursor = begin;
        while (!cursor.isAfter(end)) {
            LocalDate windowEnd = cursor.plusDays(windowDays - 1L);
            if (windowEnd.isAfter(end)) {
                windowEnd = end;
            }

            String range = toDicomDa(cursor) + "-" + toDicomDa(windowEnd);

            Attributes keys = baseStudyLevelKeys();
            keys.setString(Tag.StudyDate, VR.DA, range);

            List<Attributes> matches = creatorCFind(keys);
            for (Attributes a : matches) {
                String uid = a.getString(Tag.StudyInstanceUID);
                if (uid == null || uid.trim().isEmpty()) {
                    continue;
                }
                if (!seen.add(uid)) {
                    continue;
                }

                onStudy.accept(new StudyDescriptor(
                    uid,
                    a.getString(Tag.PatientID),
                    a.getString(Tag.AccessionNumber),
                    a.getString(Tag.StudyDate)
                ));

                emitted++;
                if (maxResults > 0 && emitted >= maxResults) {
                    return;
                }
            }

            cursor = windowEnd.plusDays(1);
        }
    }

    private String toDicomDa(LocalDate d) {
        return DICOM_DA.format(d);
    }

    private List<StudyDescriptor> toStudyDescriptors(List<Attributes> matches, int maxResults) {
        List<StudyDescriptor> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Attributes a : matches) {
            String uid = a.getString(Tag.StudyInstanceUID);
            if (uid == null || uid.trim().isEmpty()) {
                continue;
            }
            if (!seen.add(uid)) {
                continue;
            }

            out.add(new StudyDescriptor(
                uid,
                a.getString(Tag.PatientID),
                a.getString(Tag.AccessionNumber),
                a.getString(Tag.StudyDate)
            ));

            if (maxResults > 0 && out.size() >= maxResults) {
                break;
            }
        }

        return out;
    }

    private List<Attributes> findStudiesByAccessionNumber(String accessionNumber) throws IOException {
        Attributes keys = baseStudyLevelKeys();
        keys.setString(Tag.AccessionNumber, VR.SH, accessionNumber);
        return creatorCFind(keys);
    }

    private List<Attributes> findStudiesByPatientAndOrDate(List<String> patientIds, List<String> studyDates) throws IOException {
        // We can't (portably) OR in C-FIND. So we run one query per combination and merge results.
        // If only patient IDs provided: query per patient.
        // If only dates provided: query per date.
        // If both: query for each (patient, date) pair.

        List<Attributes> merged = new ArrayList<>();

        if (!patientIds.isEmpty() && !studyDates.isEmpty()) {
            for (String pid : patientIds) {
                for (String date : studyDates) {
                    Attributes keys = baseStudyLevelKeys();
                    keys.setString(Tag.PatientID, VR.LO, pid);
                    keys.setString(Tag.StudyDate, VR.DA, date);
                    merged.addAll(creatorCFind(keys));
                }
            }
            return merged;
        }

        if (!patientIds.isEmpty()) {
            for (String pid : patientIds) {
                Attributes keys = baseStudyLevelKeys();
                keys.setString(Tag.PatientID, VR.LO, pid);
                keys.setNull(Tag.StudyDate, VR.DA);
                merged.addAll(creatorCFind(keys));
            }
            return merged;
        }

        for (String date : studyDates) {
            Attributes keys = baseStudyLevelKeys();
            keys.setNull(Tag.PatientID, VR.LO);
            keys.setString(Tag.StudyDate, VR.DA, date);
            merged.addAll(creatorCFind(keys));
        }
        return merged;
    }

    private Attributes baseStudyLevelKeys() {
        Attributes keys = new Attributes();
        keys.setString(Tag.QueryRetrieveLevel, VR.CS, "STUDY");

        // Return keys for summary + downstream defaults
        keys.setNull(Tag.StudyInstanceUID, VR.UI);
        keys.setNull(Tag.PatientID, VR.LO);
        keys.setNull(Tag.StudyDate, VR.DA);
        keys.setNull(Tag.AccessionNumber, VR.SH);
        keys.setNull(Tag.PatientName, VR.PN);
        return keys;
    }

    private List<Attributes> creatorCFind(Attributes keys) throws IOException {
        CFindResult r = creator.performCFindPublic(keys);
        if (!r.isSuccess()) {
            throw new IOException(r.getErrorMessage() != null ? r.getErrorMessage() : "C-FIND failed");
        }
        return r.getMatches();
    }
}
