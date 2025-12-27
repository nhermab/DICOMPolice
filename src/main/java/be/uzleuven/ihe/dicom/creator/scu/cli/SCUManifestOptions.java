package be.uzleuven.ihe.dicom.creator.scu.cli;

import be.uzleuven.ihe.dicom.creator.scu.DefaultMetadata;
import be.uzleuven.ihe.dicom.creator.scu.streaming.StreamingMode;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Options for SCU Manifest CLI tool.
 * Encapsulates all command-line configuration for KOS/MADO manifest generation.
 */
public class SCUManifestOptions {
    private ManifestType type;
    private DefaultMetadata defaults = new DefaultMetadata();

    // Query criteria
    private String accession;
    private String studyUid;
    private List<String> patientIds = new ArrayList<>();
    private List<String> studyDates = new ArrayList<>();
    private LocalDate beginDate;
    private LocalDate endDate;
    private int windowDays = 7;

    // Output options
    private File outFile;
    private File outDir = new File(".");
    private String outPattern = "{type}_{studyuid}.dcm";
    private boolean overwrite = false;
    private int maxResults = 100;
    private StreamingMode streamingMode = StreamingMode.DICOM;

    public ManifestType getType() {
        return type;
    }

    public void setType(ManifestType type) {
        this.type = type;
    }

    public DefaultMetadata getDefaults() {
        return defaults;
    }

    public String getAccession() {
        return accession;
    }

    public void setAccession(String accession) {
        this.accession = accession;
    }

    public String getStudyUid() {
        return studyUid;
    }

    public void setStudyUid(String studyUid) {
        this.studyUid = studyUid;
    }

    public List<String> getPatientIds() {
        return patientIds;
    }

    public void addPatientId(String patientId) {
        this.patientIds.add(patientId);
    }

    public List<String> getStudyDates() {
        return studyDates;
    }

    public void addStudyDate(String studyDate) {
        this.studyDates.add(studyDate);
    }

    public LocalDate getBeginDate() {
        return beginDate;
    }

    public void setBeginDate(LocalDate beginDate) {
        this.beginDate = beginDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public int getWindowDays() {
        return windowDays;
    }

    public void setWindowDays(int windowDays) {
        this.windowDays = windowDays;
    }

    public File getOutFile() {
        return outFile;
    }

    public void setOutFile(File outFile) {
        this.outFile = outFile;
    }

    public File getOutDir() {
        return outDir;
    }

    public void setOutDir(File outDir) {
        this.outDir = outDir;
    }

    public String getOutPattern() {
        return outPattern;
    }

    public void setOutPattern(String outPattern) {
        this.outPattern = outPattern;
    }

    public boolean isOverwrite() {
        return overwrite;
    }

    public void setOverwrite(boolean overwrite) {
        this.overwrite = overwrite;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }

    public StreamingMode getStreamingMode() {
        return streamingMode;
    }

    public void setStreamingMode(StreamingMode streamingMode) {
        this.streamingMode = streamingMode;
    }
}

