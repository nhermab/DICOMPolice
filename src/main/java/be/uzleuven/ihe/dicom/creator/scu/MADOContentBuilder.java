package be.uzleuven.ihe.dicom.creator.scu;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;

import java.util.List;

import static be.uzleuven.ihe.dicom.constants.CodeConstants.CODE_INSTANCE_NUMBER;
import static be.uzleuven.ihe.dicom.constants.DicomConstants.SCHEME_DCM;
import static be.uzleuven.ihe.dicom.creator.utils.DicomCreatorUtils.*;
import static be.uzleuven.ihe.dicom.creator.utils.SRContentItemUtils.*;
import be.uzleuven.ihe.dicom.constants.CodeConstants;
import be.uzleuven.ihe.dicom.constants.DicomConstants;

/**
 * Builder for MADO manifest content sequences.
 * Implements TID 1600 Image Library hierarchy.
 */
class MADOContentBuilder {

    private final String normalizedStudyInstanceUID;
    private final String studyDate;
    private final String studyTime;
    private final List<SeriesData> allSeries;

    MADOContentBuilder(DefaultMetadata defaults, String normalizedStudyInstanceUID,
                       String studyDate, String studyTime,
                       List<SeriesData> allSeries) {
        this.normalizedStudyInstanceUID = normalizedStudyInstanceUID;
        this.studyDate = studyDate;
        this.studyTime = studyTime;
        this.allSeries = allSeries;
    }

    /**
     * Builds the root content sequence with study-level context and Image Library container.
     */
    Sequence buildContentSequence() {
        Sequence contentSeq = new Attributes().newSequence(Tag.ContentSequence, 10);
        String studyModality = getStudyModality();

        // Add study-level acquisition context
        addStudyLevelContext(contentSeq, studyModality);

        // Add Image Library container with all series
        contentSeq.add(buildImageLibraryContainer(studyModality));

        return contentSeq;
    }

    private String getStudyModality() {
        return !allSeries.isEmpty()
            ? allSeries.get(0).seriesAttrs.getString(Tag.Modality, "CT")
            : "CT";
    }

    private void addStudyLevelContext(Sequence contentSeq, String studyModality) {
        // TID 1600 Study-level requirements: Modality, Study Instance UID, Target Region
        contentSeq.add(createCodeItem(DicomConstants.RELATIONSHIP_CONTAINS, CodeConstants.CODE_MODALITY,
            CodeConstants.SCHEME_DCM, CodeConstants.MEANING_MODALITY,
            code(studyModality, CodeConstants.SCHEME_DCM, studyModality)));

        contentSeq.add(createUIDRefItem(DicomConstants.RELATIONSHIP_CONTAINS, CodeConstants.CODE_STUDY_INSTANCE_UID,
            CodeConstants.SCHEME_DCM, CodeConstants.MEANING_STUDY_INSTANCE_UID, normalizedStudyInstanceUID));

        contentSeq.add(createCodeItem(DicomConstants.RELATIONSHIP_CONTAINS, CodeConstants.CODE_TARGET_REGION,
            CodeConstants.SCHEME_DCM, CodeConstants.MEANING_TARGET_REGION,
            code(CodeConstants.CODE_REGION_ABDOMEN, CodeConstants.SCHEME_SRT, CodeConstants.MEANING_REGION_ABDOMEN)));
    }

    private Attributes buildImageLibraryContainer(String studyModality) {
        Attributes libContainer = new Attributes();
        libContainer.setString(Tag.RelationshipType, VR.CS, DicomConstants.RELATIONSHIP_CONTAINS);
        libContainer.setString(Tag.ValueType, VR.CS, "CONTAINER");
        libContainer.newSequence(Tag.ConceptNameCodeSequence, 1)
            .add(code(CodeConstants.CODE_IMAGE_LIBRARY, CodeConstants.SCHEME_DCM, CodeConstants.MEANING_IMAGE_LIBRARY));

        Sequence libContent = libContainer.newSequence(Tag.ContentSequence, 50);

        // Study-level context within Image Library
        addImageLibraryContext(libContent, studyModality);

        // Add series groups
        addSeriesGroups(libContent);

        return libContainer;
    }

    private void addImageLibraryContext(Sequence libContent, String studyModality) {
        libContent.add(createCodeItem("HAS ACQ CONTEXT", CodeConstants.CODE_MODALITY, CodeConstants.SCHEME_DCM,
            CodeConstants.MEANING_MODALITY, code(studyModality, CodeConstants.SCHEME_DCM, studyModality)));

        libContent.add(createUIDRefItem("HAS ACQ CONTEXT", CodeConstants.CODE_STUDY_INSTANCE_UID,
            CodeConstants.SCHEME_DCM, CodeConstants.MEANING_STUDY_INSTANCE_UID, normalizedStudyInstanceUID));

        libContent.add(createCodeItem("HAS ACQ CONTEXT", CodeConstants.CODE_TARGET_REGION,
            CodeConstants.SCHEME_DCM, CodeConstants.MEANING_TARGET_REGION,
            code(CodeConstants.CODE_REGION_ABDOMEN, CodeConstants.SCHEME_SRT, CodeConstants.MEANING_REGION_ABDOMEN)));
    }

    private void addSeriesGroups(Sequence libContent) {
        int seriesNumberCounter = 1;
        for (SeriesData sd : allSeries) {
            libContent.add(buildSeriesGroup(sd, seriesNumberCounter++));
        }
    }

    private Attributes buildSeriesGroup(SeriesData sd, int seriesNumber) {
        Attributes group = new Attributes();
        group.setString(Tag.RelationshipType, VR.CS, DicomConstants.RELATIONSHIP_CONTAINS);
        group.setString(Tag.ValueType, VR.CS, "CONTAINER");
        group.newSequence(Tag.ConceptNameCodeSequence, 1)
            .add(code(CodeConstants.CODE_IMAGE_LIBRARY_GROUP, CodeConstants.SCHEME_DCM, CodeConstants.MEANING_IMAGE_LIBRARY_GROUP));

        Sequence groupSeq = group.newSequence(Tag.ContentSequence, 50);

        // Add series metadata
        addSeriesMetadata(groupSeq, sd, seriesNumber);

        // Add instance entries
        addInstanceEntries(groupSeq, sd);

        return group;
    }

    private void addSeriesMetadata(Sequence groupSeq, SeriesData sd,
                                    int seriesNumber) {
        String seriesUid = sd.seriesAttrs.getString(Tag.SeriesInstanceUID, "");
        String seriesModality = sd.seriesAttrs.getString(Tag.Modality, "OT");
        String seriesDescription = sd.seriesAttrs.getString(Tag.SeriesDescription, "");
        String seriesDate = sd.seriesAttrs.getString(Tag.SeriesDate, studyDate);
        String seriesTime = sd.seriesAttrs.getString(Tag.SeriesTime, studyTime);
        String normalizedSeriesUid = normalizeUidNoLeadingZeros(seriesUid);

        // Ensure SeriesDescription is not empty (Type 1 requirement for TEXT ValueType)
        if (seriesDescription == null || seriesDescription.trim().isEmpty()) {
            seriesDescription = "(no Series Description)";
        }

        // TID 1602 series-level metadata
        groupSeq.add(createCodeItem("HAS ACQ CONTEXT", CodeConstants.CODE_MODALITY, CodeConstants.SCHEME_DCM,
            CodeConstants.MEANING_MODALITY, code(seriesModality, CodeConstants.SCHEME_DCM, seriesModality)));

        groupSeq.add(createUIDRefItem("HAS ACQ CONTEXT", CodeConstants.CODE_SERIES_INSTANCE_UID,
            CodeConstants.SCHEME_DCM, CodeConstants.MEANING_SERIES_INSTANCE_UID, normalizedSeriesUid));

        groupSeq.add(createTextItem("HAS ACQ CONTEXT", CodeConstants.CODE_SERIES_DESCRIPTION,
            CodeConstants.SCHEME_DCM, CodeConstants.MEANING_SERIES_DESCRIPTION, seriesDescription));

        groupSeq.add(createTextItem("HAS ACQ CONTEXT", CodeConstants.CODE_SERIES_DATE,
            CodeConstants.SCHEME_DCM, CodeConstants.MEANING_SERIES_DATE, seriesDate));

        groupSeq.add(createTextItem("HAS ACQ CONTEXT", CodeConstants.CODE_SERIES_TIME,
            CodeConstants.SCHEME_DCM, CodeConstants.MEANING_SERIES_TIME, seriesTime));

        groupSeq.add(createTextItem("HAS ACQ CONTEXT", CodeConstants.CODE_SERIES_NUMBER,
            CodeConstants.SCHEME_DCM, CodeConstants.MEANING_SERIES_NUMBER, Integer.toString(seriesNumber)));

        groupSeq.add(createNumericItem("HAS ACQ CONTEXT", CodeConstants.CODE_NUM_SERIES_RELATED_INSTANCES,
            CodeConstants.SCHEME_DCM, CodeConstants.MEANING_NUM_SERIES_RELATED_INSTANCES,
            sd.instances != null ? sd.instances.size() : 0));
    }

    private void addInstanceEntries(Sequence groupSeq, SeriesData sd) {
        if (sd.instances == null) return;

        // Sort instances by InstanceNumber before adding to ensure correct order
        java.util.List<Attributes> sortedInstances = new java.util.ArrayList<>(sd.instances);
        sortedInstances.sort((a, b) -> {
            int instNumA = getInstanceNumber(a);
            int instNumB = getInstanceNumber(b);
            return Integer.compare(instNumA, instNumB);
        });

        for (Attributes instAttrs : sortedInstances) {
            groupSeq.add(buildInstanceEntry(instAttrs));
        }
    }

    /**
     * Extracts the InstanceNumber from instance attributes.
     * Falls back to Integer.MAX_VALUE if not present or invalid, so such instances
     * sort to the end.
     */
    private int getInstanceNumber(Attributes instAttrs) {
        String instNumStr = instAttrs.getString(Tag.InstanceNumber);
        if (instNumStr != null && !instNumStr.trim().isEmpty()) {
            try {
                return Integer.parseInt(instNumStr.trim());
            } catch (NumberFormatException ignore) {
                // Fall through to default
            }
        }
        return Integer.MAX_VALUE;
    }

    private Attributes buildInstanceEntry(Attributes instAttrs) {
        Attributes entry = new Attributes();
        entry.setString(Tag.RelationshipType, VR.CS, DicomConstants.RELATIONSHIP_CONTAINS);
        entry.setString(Tag.ValueType, VR.CS, "IMAGE");

        // Referenced SOP Sequence
        Sequence refSop = entry.newSequence(Tag.ReferencedSOPSequence, 1);
        Attributes refItem = new Attributes();
        refItem.setString(Tag.ReferencedSOPClassUID, VR.UI, instAttrs.getString(Tag.SOPClassUID));
        refItem.setString(Tag.ReferencedSOPInstanceUID, VR.UI,
            normalizeUidNoLeadingZeros(instAttrs.getString(Tag.SOPInstanceUID)));
        refSop.add(refItem);

        // Content sequence with comprehensive instance metadata
        Sequence entryContent = entry.newSequence(Tag.ContentSequence, 30);

        // Core identifiers
        String instanceNumber = instAttrs.getString(Tag.InstanceNumber, "1");
        entryContent.add(createTextItem("HAS ACQ CONTEXT", CODE_INSTANCE_NUMBER,
            SCHEME_DCM, CodeConstants.MEANING_INSTANCE_NUMBER, instanceNumber));

        // Add Number of Frames if multiframe
        addNumberOfFramesIfRequired(entryContent, instAttrs);

        // Image dimensions
        addIfPresent(entryContent, instAttrs, Tag.Rows, "Rows");
        addIfPresent(entryContent, instAttrs, Tag.Columns, "Columns");

        // Pixel Module attributes (critical for OHIF viewer)
        addIfPresent(entryContent, instAttrs, Tag.BitsAllocated, "Bits Allocated");
        addIfPresent(entryContent, instAttrs, Tag.BitsStored, "Bits Stored");
        addIfPresent(entryContent, instAttrs, Tag.HighBit, "High Bit");
        addIfPresent(entryContent, instAttrs, Tag.PixelRepresentation, "Pixel Representation");
        addIfPresent(entryContent, instAttrs, Tag.SamplesPerPixel, "Samples per Pixel");
        addTextIfPresent(entryContent, instAttrs, Tag.PhotometricInterpretation, "Photometric Interpretation");

        // Geometry and spatial information (critical for MPR/3D)
        addArrayIfPresent(entryContent, instAttrs, Tag.ImagePositionPatient, "Image Position (Patient)");
        addArrayIfPresent(entryContent, instAttrs, Tag.ImageOrientationPatient, "Image Orientation (Patient)");
        addArrayIfPresent(entryContent, instAttrs, Tag.PixelSpacing, "Pixel Spacing");
        addIfPresent(entryContent, instAttrs, Tag.SliceThickness, "Slice Thickness");
        addIfPresent(entryContent, instAttrs, Tag.SliceLocation, "Slice Location");
        addIfPresent(entryContent, instAttrs, Tag.SpacingBetweenSlices, "Spacing Between Slices");

        // Window/Level and rescale (for proper display)
        addArrayIfPresent(entryContent, instAttrs, Tag.WindowCenter, "Window Center");
        addArrayIfPresent(entryContent, instAttrs, Tag.WindowWidth, "Window Width");
        addIfPresent(entryContent, instAttrs, Tag.RescaleIntercept, "Rescale Intercept");
        addIfPresent(entryContent, instAttrs, Tag.RescaleSlope, "Rescale Slope");
        addTextIfPresent(entryContent, instAttrs, Tag.RescaleType, "Rescale Type");

        // Additional useful metadata
        addArrayIfPresent(entryContent, instAttrs, Tag.ImageType, "Image Type");
        addIfPresent(entryContent, instAttrs, Tag.AcquisitionNumber, "Acquisition Number");
        addTextIfPresent(entryContent, instAttrs, Tag.AcquisitionDate, "Acquisition Date");
        addTextIfPresent(entryContent, instAttrs, Tag.AcquisitionTime, "Acquisition Time");
        addTextIfPresent(entryContent, instAttrs, Tag.ContentDate, "Content Date");
        addTextIfPresent(entryContent, instAttrs, Tag.ContentTime, "Content Time");

        return entry;
    }

    /**
     * Adds a numeric attribute as TEXT item if present in source attributes.
     */
    private void addIfPresent(Sequence seq, Attributes attrs, int tag, String meaning) {
        String value = attrs.getString(tag);
        if (value != null && !value.trim().isEmpty()) {
            seq.add(createTextItem("HAS ACQ CONTEXT",
                String.format("(%04X,%04X)", tag >>> 16, tag & 0xFFFF),
                CodeConstants.SCHEME_DCM, meaning, value));
        }
    }

    /**
     * Adds a text/string attribute as TEXT item if present in source attributes.
     */
    private void addTextIfPresent(Sequence seq, Attributes attrs, int tag, String meaning) {
        String value = attrs.getString(tag);
        if (value != null && !value.trim().isEmpty()) {
            seq.add(createTextItem("HAS ACQ CONTEXT",
                String.format("(%04X,%04X)", tag >>> 16, tag & 0xFFFF),
                CodeConstants.SCHEME_DCM, meaning, value));
        }
    }

    /**
     * Adds an array attribute (DS or CS) as TEXT item if present in source attributes.
     * Formats array values as comma-separated string.
     */
    private void addArrayIfPresent(Sequence seq, Attributes attrs, int tag, String meaning) {
        // Try doubles first (for DS values like PixelSpacing, ImagePositionPatient)
        double[] doubleValues = attrs.getDoubles(tag);
        if (doubleValues != null && doubleValues.length > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < doubleValues.length; i++) {
                if (i > 0) sb.append("\\");
                sb.append(doubleValues[i]);
            }
            seq.add(createTextItem("HAS ACQ CONTEXT",
                String.format("(%04X,%04X)", tag >>> 16, tag & 0xFFFF),
                CodeConstants.SCHEME_DCM, meaning, sb.toString()));
            return;
        }

        // Try strings (for CS/CS multi-valued attributes like ImageType)
        String[] stringValues = attrs.getStrings(tag);
        if (stringValues != null && stringValues.length > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < stringValues.length; i++) {
                if (i > 0) sb.append("\\");
                sb.append(stringValues[i]);
            }
            seq.add(createTextItem("HAS ACQ CONTEXT",
                String.format("(%04X,%04X)", tag >>> 16, tag & 0xFFFF),
                CodeConstants.SCHEME_DCM, meaning, sb.toString()));
            return;
        }

        // Fallback to single string value
        String value = attrs.getString(tag);
        if (value != null && !value.trim().isEmpty()) {
            seq.add(createTextItem("HAS ACQ CONTEXT",
                String.format("(%04X,%04X)", tag >>> 16, tag & 0xFFFF),
                CodeConstants.SCHEME_DCM, meaning, value));
        }
    }

    private void addNumberOfFramesIfRequired(Sequence entryContent, Attributes instAttrs) {
        String sopClassUID = instAttrs.getString(Tag.SOPClassUID);
        if (be.uzleuven.ihe.dicom.validator.validation.tid1600.TID1600Rules.isMultiframeSOP(sopClassUID)) {
            String numFramesStr = instAttrs.getString(Tag.NumberOfFrames);
            if (numFramesStr != null) {
                numFramesStr = numFramesStr.trim();
            }
            if (numFramesStr != null && !numFramesStr.isEmpty()) {
                try {
                    int numFrames = Integer.parseInt(numFramesStr);
                    entryContent.add(createNumericItem("HAS ACQ CONTEXT", CodeConstants.CODE_NUMBER_OF_FRAMES,
                        CodeConstants.SCHEME_DCM, CodeConstants.MEANING_NUMBER_OF_FRAMES, numFrames));
                } catch (NumberFormatException ignore) {
                    // Keep the manifest valid; omission will be caught by validator if required.
                }
            }
        }
    }

    /**
     * Populates the ContentSequence directly onto the target dataset.
     *
     * Important: dcm4che does not allow an {@link Attributes} item to be contained by more than one
     * {@link Sequence}. Building on a temporary dataset and then copying with addAll(...) can therefore
     * fail with "Item already contained by Sequence".
     */
    void populateContentSequence(Attributes target) {
        Sequence contentSeq = target.newSequence(Tag.ContentSequence, 10);
        String studyModality = getStudyModality();

        addStudyLevelContext(contentSeq, studyModality);
        contentSeq.add(buildImageLibraryContainer(studyModality));
    }
}
