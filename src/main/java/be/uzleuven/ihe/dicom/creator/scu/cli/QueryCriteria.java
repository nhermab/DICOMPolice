package be.uzleuven.ihe.dicom.creator.scu.cli;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Criteria used to identify studies for which we want to generate manifests.
 * Rules of thumb:
 * - If studyInstanceUID is provided, it uniquely identifies a single study.
 * - accessionNumber is usually unique-ish but can still match multiple.
 * - patientId and studyDate are broader and can match many studies.
 */
public class QueryCriteria {
    private final String accessionNumber;
    private final String studyInstanceUID;
    private final List<String> patientIds;
    private final List<String> studyDates; // DICOM DA format: YYYYMMDD (single day)

    // optional crawl mode: iterate StudyDate from beginDate to endDate (inclusive) in windows
    private final LocalDate beginDate; // ISO date
    private final LocalDate endDate;   // ISO date
    private final int windowDays;      // 1..7

    private QueryCriteria(Builder b) {
        this.accessionNumber = b.accessionNumber;
        this.studyInstanceUID = b.studyInstanceUID;
        this.patientIds = Collections.unmodifiableList(new ArrayList<>(b.patientIds));
        this.studyDates = Collections.unmodifiableList(new ArrayList<>(b.studyDates));
        this.beginDate = b.beginDate;
        this.endDate = b.endDate;
        this.windowDays = b.windowDays;
    }

    public String getAccessionNumber() {
        return accessionNumber;
    }

    public String getStudyInstanceUID() {
        return studyInstanceUID;
    }

    public List<String> getPatientIds() {
        return patientIds;
    }

    public List<String> getStudyDates() {
        return studyDates;
    }

    public LocalDate getBeginDate() {
        return beginDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public int getWindowDays() {
        return windowDays;
    }

    public boolean isCrawlByDateRange() {
        return beginDate != null || endDate != null;
    }

    public boolean hasAnyCriteria() {
        return (studyInstanceUID != null && !studyInstanceUID.trim().isEmpty())
            || (accessionNumber != null && !accessionNumber.trim().isEmpty())
            || !patientIds.isEmpty()
            || !studyDates.isEmpty()
            || isCrawlByDateRange();
    }

    public void validate() {
        if (!hasAnyCriteria()) {
            throw new IllegalArgumentException("No search criteria provided. Provide one of: --study-uid, --accession, --patient-id, --study-date, --begin-date/--end-date");
        }

        if (isCrawlByDateRange()) {
            if (beginDate == null || endDate == null) {
                throw new IllegalArgumentException("Both --begin-date and --end-date are required when using date range crawling");
            }
            if (beginDate.isAfter(endDate)) {
                throw new IllegalArgumentException("--begin-date must be <= --end-date");
            }
            if (windowDays < 1 || windowDays > 7) {
                throw new IllegalArgumentException("--window-days must be between 1 and 7 (inclusive)");
            }

            // avoid ambiguous combinations that might surprise users
            if ((studyInstanceUID != null && !studyInstanceUID.trim().isEmpty())
                || (accessionNumber != null && !accessionNumber.trim().isEmpty())
                || !patientIds.isEmpty()
                || !studyDates.isEmpty()) {
                throw new IllegalArgumentException("Date range crawl (--begin-date/--end-date) cannot be combined with other criteria");
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String accessionNumber;
        private String studyInstanceUID;
        private final List<String> patientIds = new ArrayList<>();
        private final List<String> studyDates = new ArrayList<>();

        private LocalDate beginDate;
        private LocalDate endDate;
        private int windowDays = 7;

        public Builder accessionNumber(String accessionNumber) {
            this.accessionNumber = accessionNumber;
            return this;
        }

        public Builder studyInstanceUID(String studyInstanceUID) {
            this.studyInstanceUID = studyInstanceUID;
            return this;
        }

        public Builder addPatientId(String patientId) {
            if (patientId != null && !patientId.trim().isEmpty()) {
                this.patientIds.add(patientId);
            }
            return this;
        }

        public Builder addStudyDate(String studyDate) {
            if (studyDate != null && !studyDate.trim().isEmpty()) {
                this.studyDates.add(studyDate);
            }
            return this;
        }

        public Builder beginDate(LocalDate beginDate) {
            this.beginDate = beginDate;
            return this;
        }

        public Builder endDate(LocalDate endDate) {
            this.endDate = endDate;
            return this;
        }

        public Builder windowDays(int windowDays) {
            this.windowDays = windowDays;
            return this;
        }

        public QueryCriteria build() {
            return new QueryCriteria(this);
        }
    }
}

