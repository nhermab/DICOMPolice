package be.uzleuven.ihe.dicom.creator.utils;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;

import java.util.List;

import static be.uzleuven.ihe.dicom.creator.utils.DicomCreatorUtils.normalizeUidNoLeadingZeros;

/**
 * Utility class for building Evidence Sequences in KOS/MADO manifests.
 * Extracts common patterns for CurrentRequestedProcedureEvidenceSequence population.
 */
public class EvidenceSequenceUtils {

    private EvidenceSequenceUtils() {
        // Utility class
    }

    /**
     * Container for referenced SOP instance data.
     */
    public static class ReferencedSOP {
        public String sopClassUID;
        public String sopInstanceUID;
        public String seriesInstanceUID;
        public String modality;

        // Optional MADO-specific attributes
        public String numberOfFrames;
        public String rows;
        public String columns;

        public ReferencedSOP(String sopClassUID, String sopInstanceUID) {
            this.sopClassUID = sopClassUID;
            this.sopInstanceUID = sopInstanceUID;
        }

        public ReferencedSOP withSeriesInstanceUID(String uid) {
            this.seriesInstanceUID = uid;
            return this;
        }

        public ReferencedSOP withModality(String modality) {
            this.modality = modality;
            return this;
        }

        public ReferencedSOP withNumberOfFrames(String frames) {
            this.numberOfFrames = frames;
            return this;
        }

        public ReferencedSOP withDimensions(String rows, String columns) {
            this.rows = rows;
            this.columns = columns;
            return this;
        }
    }

    /**
     * Container for series-level data in evidence sequence.
     */
    public static class SeriesEvidence {
        public String seriesInstanceUID;
        public String modality;
        public String retrieveLocationUID;
        public String retrieveURL;
        public List<ReferencedSOP> instances;

        public SeriesEvidence(String seriesInstanceUID, List<ReferencedSOP> instances) {
            this.seriesInstanceUID = seriesInstanceUID;
            this.instances = instances;
        }

        public SeriesEvidence withModality(String modality) {
            this.modality = modality;
            return this;
        }

        public SeriesEvidence withRetrievalInfo(String retrieveLocationUID, String retrieveURL) {
            this.retrieveLocationUID = retrieveLocationUID;
            this.retrieveURL = retrieveURL;
            return this;
        }
    }

    /**
     * Builds a CurrentRequestedProcedureEvidenceSequence with multiple series.
     *
     * @param attrs The dataset to populate
     * @param studyInstanceUID The study UID
     * @param seriesList List of series with their instances
     */
    public static void populateEvidenceSequence(Attributes attrs, String studyInstanceUID,
                                                 List<SeriesEvidence> seriesList) {
        Sequence evidenceSeq = attrs.newSequence(Tag.CurrentRequestedProcedureEvidenceSequence, 1);
        Attributes studyItem = new Attributes();
        studyItem.setString(Tag.StudyInstanceUID, VR.UI, normalizeUidNoLeadingZeros(studyInstanceUID));

        Sequence refSeriesSeq = studyItem.newSequence(Tag.ReferencedSeriesSequence, seriesList.size());

        for (SeriesEvidence series : seriesList) {
            Attributes seriesItem = new Attributes();
            String normalizedSeriesUID = normalizeUidNoLeadingZeros(series.seriesInstanceUID);
            seriesItem.setString(Tag.SeriesInstanceUID, VR.UI, normalizedSeriesUID);

            // Add modality if present (MADO requirement)
            if (series.modality != null) {
                seriesItem.setString(Tag.Modality, VR.CS, series.modality);
            }

            // Add retrieval information if present
            if (series.retrieveLocationUID != null) {
                seriesItem.setString(Tag.RetrieveLocationUID, VR.UI, series.retrieveLocationUID);
            }

            if (series.retrieveURL != null) {
                seriesItem.setString(Tag.RetrieveURL, VR.UR, series.retrieveURL);
            }

            // Add referenced SOPs
            Sequence refSOPSeq = seriesItem.newSequence(Tag.ReferencedSOPSequence, series.instances.size());
            for (ReferencedSOP sop : series.instances) {
                Attributes sopItem = new Attributes();
                sopItem.setString(Tag.ReferencedSOPClassUID, VR.UI, sop.sopClassUID);
                sopItem.setString(Tag.ReferencedSOPInstanceUID, VR.UI,
                    normalizeUidNoLeadingZeros(sop.sopInstanceUID));

                // Add optional MADO attributes
                if (sop.numberOfFrames != null && !sop.numberOfFrames.isEmpty()) {
                    sopItem.setString(Tag.NumberOfFrames, VR.IS, sop.numberOfFrames);
                }

                if (sop.rows != null && !sop.rows.isEmpty()) {
                    sopItem.setString(Tag.Rows, VR.US, sop.rows);
                }

                if (sop.columns != null && !sop.columns.isEmpty()) {
                    sopItem.setString(Tag.Columns, VR.US, sop.columns);
                }

                refSOPSeq.add(sopItem);
            }

            refSeriesSeq.add(seriesItem);
        }

        evidenceSeq.add(studyItem);
    }

    /**
     * Builds a simple evidence sequence with all instances in a single series (for backward compatibility).
     */
    public static void populateEvidenceSequenceSingleSeries(Attributes attrs, String studyInstanceUID,
                                                             String seriesInstanceUID,
                                                             List<ReferencedSOP> instances,
                                                             String retrieveLocationUID) {
        SeriesEvidence series = new SeriesEvidence(seriesInstanceUID, instances)
            .withRetrievalInfo(retrieveLocationUID, null);

        populateEvidenceSequence(attrs, studyInstanceUID, java.util.Collections.singletonList(series));
    }
}

