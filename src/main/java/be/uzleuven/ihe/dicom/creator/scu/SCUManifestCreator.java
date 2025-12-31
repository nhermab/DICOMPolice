package be.uzleuven.ihe.dicom.creator.scu;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;

import java.io.File;
import java.io.IOException;

import static be.uzleuven.ihe.dicom.creator.utils.DicomCreatorUtils.writeDicomFile;
import be.uzleuven.ihe.dicom.creator.scu.streaming.ManifestStreamWriter;

/**
 * Base class for SCU (Service Class User) manifest creators.
 * Provides C-FIND functionality to query DICOM archives and retrieve metadata
 * that can be used to construct KOS or MADO manifests.
 */
public abstract class SCUManifestCreator {

    protected final DefaultMetadata defaults;
    private final CFindService cFindService;

    public SCUManifestCreator() {
        this(new DefaultMetadata());
    }

    public SCUManifestCreator(DefaultMetadata defaults) {
        this.defaults = defaults;
        this.cFindService = new CFindService(defaults);
    }

    /**
     * Performs a C-FIND query at STUDY level.
     *
     * @param studyInstanceUid The Study Instance UID to query
     * @param patientId Optional Patient ID (may be null)
     * @return CFindResult containing matched studies
     * @throws IOException if network communication fails
     */
    public CFindResult findStudy(String studyInstanceUid, String patientId) throws IOException {
        Attributes keys = CFindQueryBuilder.buildStudyQuery(studyInstanceUid, patientId);
        return cFindService.performCFind(keys);
    }

    /**
     * Performs a C-FIND query at SERIES level.
     *
     * @param studyInstanceUid The Study Instance UID
     * @param seriesInstanceUid Optional Series Instance UID (may be null to get all series)
     * @return CFindResult containing matched series
     * @throws IOException if network communication fails
     */
    public CFindResult findSeries(String studyInstanceUid, String seriesInstanceUid) throws IOException {
        Attributes keys = CFindQueryBuilder.buildSeriesQuery(studyInstanceUid, seriesInstanceUid);
        return cFindService.performCFind(keys);
    }

    /**
     * Performs a C-FIND query at IMAGE (INSTANCE) level.
     *
     * @param studyInstanceUid The Study Instance UID
     * @param seriesInstanceUid The Series Instance UID
     * @return CFindResult containing matched instances
     * @throws IOException if network communication fails
     */
    public CFindResult findInstances(String studyInstanceUid, String seriesInstanceUid) throws IOException {
        Attributes keys = CFindQueryBuilder.buildInstanceQuery(studyInstanceUid, seriesInstanceUid);
        return cFindService.performCFind(keys);
    }

    /**
     * Public/CLI-friendly entrypoint for performing an arbitrary C-FIND with custom keys.
     * This is intentionally a thin wrapper around the internal implementation so higher-level
     * query services can add search modes (accessionNumber, patientID, studyDate, ...)
     * without duplicating DIMSE boilerplate.
     */
    public CFindResult performCFindPublic(Attributes keys) throws IOException {
        return cFindService.performCFind(keys);
    }

    /**
     * Apply default metadata to attributes that are missing from C-FIND response.
     * Subclasses should call this to ensure compliance with IHE requirements.
     */
    protected void applyDefaults(Attributes attrs) {
        MetadataApplier.applyDefaults(attrs, defaults);
    }


    /**
     * Saves a generated manifest to a DICOM Part-10 file.
     * Subclasses previously duplicated this method; keeping it here avoids CLI downcasts.
     */
    public void saveToFile(Attributes manifest, File outputFile) throws IOException {
        writeDicomFile(outputFile, manifest);
    }

    /**
     * Creates a manifest (KOS or MADO) from C-FIND results.
     * To be implemented by subclasses.
     */
    public abstract Attributes createManifest(String studyInstanceUid, String patientId) throws IOException;

    /**
     * Stream study/series/instance metadata to a writer, avoiding large in-memory lists.
     * This is intended for huge PACS crawls where building an entire manifest dataset in memory
     * per study could exhaust heap.
     */
    public void streamStudy(String studyInstanceUid, String patientId, ManifestStreamWriter writer) throws IOException {
        // Step 1: Query study
        CFindResult studyResult = findStudy(studyInstanceUid, patientId);
        if (!studyResult.isSuccess() || studyResult.getMatches().isEmpty()) {
            throw new IOException("Study not found: " + studyInstanceUid +
                ". Error: " + studyResult.getErrorMessage());
        }

        Attributes studyAttrs = studyResult.getMatches().get(0);
        applyDefaults(studyAttrs);
        writer.openStudy(studyAttrs);

        // Step 2: Query series
        CFindResult seriesResult = findSeries(studyInstanceUid, null);
        if (!seriesResult.isSuccess() || seriesResult.getMatches().isEmpty()) {
            throw new IOException("No series found for study: " + studyInstanceUid);
        }

        // Step 3: For each series, query instances and stream them immediately
        for (Attributes seriesAttrs : seriesResult.getMatches()) {
            // Series-level responses from most PACS won't echo StudyInstanceUID, but our writer
            // benefits from it for indexing.
            seriesAttrs.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUid);

            writer.openSeries(seriesAttrs);

            String seriesInstanceUid = seriesAttrs.getString(Tag.SeriesInstanceUID);
            if (seriesInstanceUid == null || seriesInstanceUid.trim().isEmpty()) {
                writer.closeSeries();
                continue;
            }

            CFindResult instanceResult = findInstances(studyInstanceUid, seriesInstanceUid);
            if (instanceResult.isSuccess() && !instanceResult.getMatches().isEmpty()) {
                for (Attributes instAttrs : instanceResult.getMatches()) {
                    // Ensure identifiers are present in the instance record.
                    instAttrs.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUid);
                    instAttrs.setString(Tag.SeriesInstanceUID, VR.UI, seriesInstanceUid);
                    writer.writeInstance(instAttrs);
                }
            }

            writer.closeSeries();
        }

        writer.closeStudy();
    }
}
