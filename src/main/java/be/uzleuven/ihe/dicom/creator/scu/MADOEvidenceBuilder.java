package be.uzleuven.ihe.dicom.creator.scu;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;

import java.util.List;

import static be.uzleuven.ihe.dicom.creator.utils.DicomCreatorUtils.*;

/**
 * Builder for MADO evidence sequence.
 * Handles CurrentRequestedProcedureEvidenceSequence construction.
 */
class MADOEvidenceBuilder {

    private final DefaultMetadata defaults;
    private final String normalizedStudyInstanceUID;
    private final List<SeriesData> allSeries;

    MADOEvidenceBuilder(DefaultMetadata defaults, String normalizedStudyInstanceUID,
                        List<SeriesData> allSeries) {
        this.defaults = defaults;
        this.normalizedStudyInstanceUID = normalizedStudyInstanceUID;
        this.allSeries = allSeries;
    }

    /**
     * Builds the CurrentRequestedProcedureEvidenceSequence.
     */
    Sequence buildEvidenceSequence() {
        Sequence evidenceSeq = new Attributes().newSequence(Tag.CurrentRequestedProcedureEvidenceSequence, 1);
        Attributes studyItem = new Attributes();
        studyItem.setString(Tag.StudyInstanceUID, VR.UI, normalizedStudyInstanceUID);

        Sequence refSeriesSeq = studyItem.newSequence(Tag.ReferencedSeriesSequence, allSeries.size());

        for (SeriesData sd : allSeries) {
            refSeriesSeq.add(buildSeriesItem(sd));
        }

        evidenceSeq.add(studyItem);
        return evidenceSeq;
    }

    private Attributes buildSeriesItem(SeriesData sd) {
        Attributes seriesItem = new Attributes();
        String serUID = sd.seriesAttrs.getString(Tag.SeriesInstanceUID);
        String normalizedSerUID = normalizeUidNoLeadingZeros(serUID);
        seriesItem.setString(Tag.SeriesInstanceUID, VR.UI, normalizedSerUID);

        // MADO Appendix B: Modality at series level (Type 1)
        String modality = sd.seriesAttrs.getString(Tag.Modality, "OT");
        seriesItem.setString(Tag.Modality, VR.CS, modality);

        // Add retrieval information (MADO requirement)
        seriesItem.setString(Tag.RetrieveLocationUID, VR.UI, defaults.retrieveLocationUid);

        // Build WADO-RS URL
        String wadoUrl = defaults.wadoRsBaseUrl + "/" + normalizedStudyInstanceUID +
            "/series/" + normalizedSerUID;
        seriesItem.setString(Tag.RetrieveURL, VR.UR, wadoUrl);

        // Add instances
        Sequence refSOPSeq = seriesItem.newSequence(Tag.ReferencedSOPSequence, sd.instances.size());
        for (Attributes instAttrs : sd.instances) {
            refSOPSeq.add(buildSOPItem(instAttrs));
        }

        return seriesItem;
    }

    private Attributes buildSOPItem(Attributes instAttrs) {
        Attributes sopItem = new Attributes();
        sopItem.setString(Tag.ReferencedSOPClassUID, VR.UI,
            instAttrs.getString(Tag.SOPClassUID));
        sopItem.setString(Tag.ReferencedSOPInstanceUID, VR.UI,
            normalizeUidNoLeadingZeros(instAttrs.getString(Tag.SOPInstanceUID)));

        // MADO Appendix B: Add NumberOfFrames if multiframe
        addOptionalAttribute(sopItem, instAttrs, Tag.NumberOfFrames, VR.IS);

        // MADO Appendix B: Rows/Columns recommended for bandwidth estimation
        addOptionalAttribute(sopItem, instAttrs, Tag.Rows, VR.US);
        addOptionalAttribute(sopItem, instAttrs, Tag.Columns, VR.US);

        return sopItem;
    }

    private void addOptionalAttribute(Attributes target, Attributes source, int tag, VR vr) {
        String value = source.getString(tag);
        if (value != null && !value.isEmpty()) {
            target.setString(tag, vr, value);
        }
    }
}

