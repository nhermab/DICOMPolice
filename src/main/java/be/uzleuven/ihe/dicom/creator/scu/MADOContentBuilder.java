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
    private final boolean includeExtendedInstanceMetadata;

    MADOContentBuilder(DefaultMetadata defaults, String normalizedStudyInstanceUID,
                       String studyDate, String studyTime,
                       List<SeriesData> allSeries) {
        this(defaults, normalizedStudyInstanceUID, studyDate, studyTime, allSeries, false);
    }

    MADOContentBuilder(DefaultMetadata defaults, String normalizedStudyInstanceUID,
                       String studyDate, String studyTime,
                       List<SeriesData> allSeries, boolean includeExtendedInstanceMetadata) {
        this.normalizedStudyInstanceUID = normalizedStudyInstanceUID;
        this.studyDate = studyDate;
        this.studyTime = studyTime;
        this.allSeries = allSeries;
        this.includeExtendedInstanceMetadata = includeExtendedInstanceMetadata;

        // DEBUG: Log the flag value
        System.out.println("DEBUG MADOContentBuilder: includeExtendedInstanceMetadata = " + includeExtendedInstanceMetadata);
    }

    /**
     * Builds the root content sequence with TID 1600 Image Library as child of TID 2010 root.
     *
     * Per MADO specification, CP-2595, and TID 2010 requirements, the content structure is:
     *
     * Root (TID 2010): CONTAINER "Manifest with Description"
     *   ├── CONTAINS -> TEXT "Key Object Description"
     *   ├── CONTAINS -> IMAGE (root-level references - required by KOS SOP Class)
     *   └── CONTAINS -> CONTAINER "Image Library" (TID 1600)
     *
     * The 3-tier reference requirement:
     * 1. Evidence Sequence (0040,a375) - top level
     * 2. Root Content Sequence - direct IMAGE children (this is what standard viewers use)
     * 3. Image Library - nested metadata with IMAGE references
     */
    Sequence buildContentSequence() {
        Sequence contentSeq = new Attributes().newSequence(Tag.ContentSequence, 10 + countAllInstances());
        String studyModality = getStudyModality();

        // TID 2010 requires Key Object Description (113012, DCM) as first item
        Attributes keyObjDesc = createTextItem(DicomConstants.RELATIONSHIP_CONTAINS,
             CodeConstants.CODE_KOS_DESCRIPTION, CodeConstants.SCHEME_DCM,
            CodeConstants.MEANING_KOS_DESCRIPTION, "Manifest with Description");
        contentSeq.add(keyObjDesc);

        // Add root-level IMAGE references (required by KOS SOP Class for standard viewers)
        addRootLevelImageReferences(contentSeq);

        // Add Image Library container (TID 1600) as child of root
        // Study-level context is added INSIDE the Image Library with HAS ACQ CONTEXT
        contentSeq.add(buildImageLibraryContainer(studyModality));

        return contentSeq;
    }

    private String getStudyModality() {
        return !allSeries.isEmpty()
            ? allSeries.get(0).seriesAttrs.getString(Tag.Modality, "CT")
            : "CT";
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
            code(CodeConstants.SNOMED_LOWER_TRUNK, "SCT", "Lower trunk")));
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

    /**
     * Adds instance entries to the Image Library Group.
     *
     * Per TID 1601, instance-level metadata (Instance Number, Number of Frames, if available/relevant)
     * should be children of the IMAGE item within the Image Library Group
     *
     * Structure:
     *   Image Library Group (CONTAINER)
     *     ├── HAS ACQ CONTEXT -> series level attributes
     *     └── CONTAINS -> IMAGE (with ReferencedSOPSequence)
     *          ├── HAS ACQ CONTEXT -> Instance Number
     *          └── HAS ACQ CONTEXT -> Number Of Frames
     */
    private void addInstanceEntries(Sequence groupSeq, SeriesData sd) {
        if (sd.instances == null) return;

        // Sort instances by InstanceNumber before adding to ensure correct order
        java.util.List<Attributes> sortedInstances = new java.util.ArrayList<>(sd.instances);

        // currently disabled in order to test the writing and reading of Instance Numbers in MADO
        // MADO doesn't ask for sorting, so maybe leave it definitely out
        // the reason for sorting would be to make life easier for viewers that don't bother to read the Instance Number in MADO?
        // though the instances may already be ordered in the ReferencedProcedureEvidenceSequence or in TID 2010 root content
        /*
        sortedInstances.sort((a, b) -> {
            int instNumA = getInstanceNumber(a);
            int instNumB = getInstanceNumber(b);
            return Integer.compare(instNumA, instNumB);
        });
        */

        for (Attributes instAttrs : sortedInstances) {
            // Add the IMAGE item (CONTAINS)
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

    /**
     * Builds an IMAGE content item for the Image Library Group.
     *
     * Per TID 1601, the IMAGE item should contain a Reference SOP Sequence
     * Per MADO spec, it should also contain some nested instance level attributes
     * Instance Number and Number of Frames if available/relevant
     */
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

        Sequence contentSeq = entry.newSequence(Tag.ContentSequence, 2);

        // get extra instance info
        int instNum = getInstanceNumber(instAttrs);
        String instanceNumber = (instNum == Integer.MAX_VALUE) ? "1" : String.valueOf(instNum);
        contentSeq.add(createTextItem("HAS ACQ CONTEXT", CODE_INSTANCE_NUMBER,
                SCHEME_DCM, CodeConstants.MEANING_INSTANCE_NUMBER, instanceNumber));

        // Add Number of Frames if multiframe (HAS ACQ CONTEXT)
        addNumberOfFramesIfRequired(contentSeq, instAttrs);

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
     * Per MADO specification, CP-2595, and TID 2010 requirements, the content structure is:
     *
     * Root (TID 2010): CONTAINER "Manifest with Description"
     *   ├── CONTAINS -> TEXT "Key Object Description"
     *   ├── CONTAINS -> IMAGE (root-level references - required by KOS SOP Class)
     *   ├── CONTAINS -> IMAGE (one per referenced instance)
     *   └── CONTAINS -> CONTAINER "Image Library" (TID 1600)
     *         ├── HAS ACQ CONTEXT -> Modality, Study Instance UID, Target Region
     *         └── CONTAINS -> CONTAINER "Image Library Group" (TID 1601)
     *               ├── HAS ACQ CONTEXT -> Series metadata
     *               ├── HAS ACQ CONTEXT -> Instance Number
     *               └── CONTAINS -> IMAGE
     *
     * The 3-tier reference requirement:
     * 1. Evidence Sequence (0040,a375) - top level
     * 2. Root Content Sequence - direct IMAGE children (this is what standard viewers use)
     * 3. Image Library - nested metadata with IMAGE references
     *
     * Important: dcm4che does not allow an {@link Attributes} item to be contained by more than one
     * {@link Sequence}. Building on a temporary dataset and then copying with addAll(...) can therefore
     * fail with "Item already contained by Sequence".
     */
    void populateContentSequence(Attributes target) {
        Sequence contentSeq = target.newSequence(Tag.ContentSequence, 10 + countAllInstances());
        String studyModality = getStudyModality();

        // TID 2010 requires Key Object Description (113012, DCM) as first item
        Attributes keyObjDesc = createTextItem(DicomConstants.RELATIONSHIP_CONTAINS,
            CodeConstants.CODE_KOS_DESCRIPTION, CodeConstants.SCHEME_DCM,
            CodeConstants.MEANING_KOS_DESCRIPTION, "Manifest with Description");
        contentSeq.add(keyObjDesc);

        // Add root-level IMAGE references (required by KOS SOP Class for standard viewers)
        // These are direct children of root with CONTAINS -> IMAGE
        addRootLevelImageReferences(contentSeq);

        // Add Image Library container (TID 1600) as child of root
        // Study-level context is added INSIDE the Image Library with HAS ACQ CONTEXT
        contentSeq.add(buildImageLibraryContainer(studyModality));
    }

    /**
     * Adds root-level IMAGE references as direct children of the root content.
     * These are required by the KOS SOP Class so standard DICOM viewers can see the referenced images.
     */
    private void addRootLevelImageReferences(Sequence contentSeq) {
        for (SeriesData sd : allSeries) {
            if (sd.instances == null) continue;
            for (Attributes instAttrs : sd.instances) {
                contentSeq.add(buildRootImageReference(instAttrs));
            }
        }
    }

    /**
     * Builds a root-level IMAGE reference for TID 2010.
     * This is a simple IMAGE item without nested ContentSequence.
     */
    private Attributes buildRootImageReference(Attributes instAttrs) {
        Attributes imageItem = new Attributes();
        imageItem.setString(Tag.RelationshipType, VR.CS, DicomConstants.RELATIONSHIP_CONTAINS);
        imageItem.setString(Tag.ValueType, VR.CS, "IMAGE");

        Sequence refSop = imageItem.newSequence(Tag.ReferencedSOPSequence, 1);
        Attributes refItem = new Attributes();
        refItem.setString(Tag.ReferencedSOPClassUID, VR.UI, instAttrs.getString(Tag.SOPClassUID));
        refItem.setString(Tag.ReferencedSOPInstanceUID, VR.UI,
            normalizeUidNoLeadingZeros(instAttrs.getString(Tag.SOPInstanceUID)));
        refSop.add(refItem);

        return imageItem;
    }

    /**
     * Counts total instances across all series for initial sequence sizing.
     */
    private int countAllInstances() {
        int count = 0;
        for (SeriesData sd : allSeries) {
            if (sd.instances != null) {
                count += sd.instances.size();
            }
        }
        return count;
    }
}
