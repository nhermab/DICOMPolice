package be.uzleuven.ihe.dicom.creator.utils;

import be.uzleuven.ihe.dicom.constants.CodeConstants;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;

import java.util.List;

import static be.uzleuven.ihe.dicom.creator.utils.SRContentItemUtils.*;
import static be.uzleuven.ihe.dicom.constants.CodeConstants.*;
import be.uzleuven.ihe.dicom.creator.utils.DicomCreatorUtils;
import org.springframework.lang.NonNull;

/**
 * Utility class for building TID 1600 Image Library content structures for MADO manifests.
 * Extracts the complex Image Library hierarchy building logic from sample creators.
 */
public class ImageLibraryBuilder {

    private ImageLibraryBuilder() {
        // Utility class
    }

    /**
     * Data container for an image instance in the library.
     */
    public static class ImageInstance {
        public String sopClassUID;
        public String sopInstanceUID;
        public String instanceNumber;
        public Integer numberOfFrames; // null if not multiframe

        public ImageInstance(String sopClassUID, String sopInstanceUID, String instanceNumber) {
            this.sopClassUID = sopClassUID;
            this.sopInstanceUID = sopInstanceUID;
            this.instanceNumber = instanceNumber;
        }

        public ImageInstance withNumberOfFrames(int frames) {
            this.numberOfFrames = frames;
            return this;
        }
    }

    /**
     * Data container for a series in the library.
     */
    public static class ImageSeries {
        public String seriesInstanceUID;
        public String modality;
        public String seriesDescription;
        public String seriesDate;
        public String seriesTime;
        public int seriesNumber;
        public List<ImageInstance> instances;

        public ImageSeries(String seriesInstanceUID, String modality, int seriesNumber, List<ImageInstance> instances) {
            this.seriesInstanceUID = seriesInstanceUID;
            this.modality = modality;
            this.seriesNumber = seriesNumber;
            this.instances = instances;
        }

        public ImageSeries withDescription(String description) {
            this.seriesDescription = description;
            return this;
        }

        public ImageSeries withDateTime(String date, String time) {
            this.seriesDate = date;
            this.seriesTime = time;
            return this;
        }
    }

    /**
     * Builds a complete TID 1600 Image Library container with study-level acquisition context.
     *
     * @param studyInstanceUID The study UID
     * @param studyModality Primary modality for study-level context
     * @param seriesList List of series to include in the library
     * @return The Image Library container Attributes
     */
    public static Attributes buildImageLibraryContainer(String studyInstanceUID, String studyModality,
                                                         List<ImageSeries> seriesList) {
        Attributes libContainer = getAttributes(CodeConstants.CODE_IMAGE_LIBRARY, CodeConstants.MEANING_IMAGE_LIBRARY);

        Sequence libContent = libContainer.newSequence(Tag.ContentSequence, 50);

        // TID 1600 Study-level acquisition context requirements
        libContent.add(createCodeItem("HAS ACQ CONTEXT", CODE_MODALITY, SCHEME_DCM, MEANING_MODALITY,
            DicomCreatorUtils.code(studyModality, SCHEME_DCM, studyModality)));
        libContent.add(createUIDRefItem("HAS ACQ CONTEXT", CODE_STUDY_INSTANCE_UID, SCHEME_DCM,
            MEANING_STUDY_INSTANCE_UID, studyInstanceUID));
        libContent.add(createCodeItem("HAS ACQ CONTEXT", CODE_TARGET_REGION, SCHEME_DCM, MEANING_TARGET_REGION,
            DicomCreatorUtils.code(CODE_REGION_ABDOMEN, SCHEME_SRT, MEANING_REGION_ABDOMEN)));

        // Add Image Library Groups (one per series)
        for (ImageSeries series : seriesList) {
            libContent.add(buildImageLibraryGroup(series));
        }

        return libContainer;
    }

    @NonNull
    private static Attributes getAttributes(String codeImageLibrary, String meaningImageLibrary) {
        Attributes libContainer = new Attributes();
        libContainer.setString(Tag.RelationshipType, VR.CS, be.uzleuven.ihe.dicom.constants.DicomConstants.RELATIONSHIP_CONTAINS);
        libContainer.setString(Tag.ValueType, VR.CS, "CONTAINER");
        libContainer.newSequence(Tag.ConceptNameCodeSequence, 1)
            .add(DicomCreatorUtils.code(codeImageLibrary, SCHEME_DCM, meaningImageLibrary));
        return libContainer;
    }

    /**
     * Builds a single Image Library Group (TID 1602) for a series.
     */
    private static Attributes buildImageLibraryGroup(ImageSeries series) {
        Attributes group = getAttributes(CodeConstants.CODE_IMAGE_LIBRARY_GROUP, CodeConstants.MEANING_IMAGE_LIBRARY_GROUP);

        Sequence groupContent = group.newSequence(Tag.ContentSequence, 50);

        // Series-level acquisition context (TID 1602)
        String seriesDescription = (series.seriesDescription != null && !series.seriesDescription.trim().isEmpty())
            ? series.seriesDescription
            : "(no Series Description)";

        groupContent.add(createCodeItem("HAS ACQ CONTEXT", CODE_MODALITY, SCHEME_DCM, MEANING_MODALITY,
            DicomCreatorUtils.code(series.modality, SCHEME_DCM, series.modality)));
        groupContent.add(createUIDRefItem("HAS ACQ CONTEXT", CODE_SERIES_INSTANCE_UID, SCHEME_DCM,
            MEANING_SERIES_INSTANCE_UID, series.seriesInstanceUID));
        groupContent.add(createTextItem("HAS ACQ CONTEXT", CODE_SERIES_DESCRIPTION, SCHEME_DCM,
            MEANING_SERIES_DESCRIPTION, seriesDescription));

        if (series.seriesDate != null) {
            groupContent.add(createTextItem("HAS ACQ CONTEXT", CODE_SERIES_DATE, SCHEME_DCM,
                MEANING_SERIES_DATE, series.seriesDate));
        }

        if (series.seriesTime != null) {
            groupContent.add(createTextItem("HAS ACQ CONTEXT", CODE_SERIES_TIME, SCHEME_DCM,
                MEANING_SERIES_TIME, series.seriesTime));
        }

        groupContent.add(createTextItem("HAS ACQ CONTEXT", CODE_SERIES_NUMBER, SCHEME_DCM,
            MEANING_SERIES_NUMBER, String.valueOf(series.seriesNumber)));
        groupContent.add(createNumericItem("HAS ACQ CONTEXT", CODE_NUM_SERIES_RELATED_INSTANCES, SCHEME_DCM,
            MEANING_NUM_SERIES_RELATED_INSTANCES, series.instances != null ? series.instances.size() : 0));

        // Add Image Library Entries (TID 1601) - one per instance
        if (series.instances != null) {
            for (ImageInstance instance : series.instances) {
                groupContent.add(buildImageLibraryEntry(instance));
            }
        }

        return group;
    }

    /**
     * Builds a single Image Library Entry (TID 1601) for an instance.
     */
    private static Attributes buildImageLibraryEntry(ImageInstance instance) {
        Attributes entry = new Attributes();
        entry.setString(Tag.RelationshipType, VR.CS, be.uzleuven.ihe.dicom.constants.DicomConstants.RELATIONSHIP_CONTAINS);
        entry.setString(Tag.ValueType, VR.CS, "IMAGE");

        // Referenced SOP Sequence
        Sequence refSop = entry.newSequence(Tag.ReferencedSOPSequence, 1);
        Attributes refItem = new Attributes();
        refItem.setString(Tag.ReferencedSOPClassUID, VR.UI, instance.sopClassUID);
        refItem.setString(Tag.ReferencedSOPInstanceUID, VR.UI, instance.sopInstanceUID);
        refSop.add(refItem);

        // Entry content
        Sequence entryContent = entry.newSequence(Tag.ContentSequence, 5);

        if (instance.instanceNumber != null) {
            entryContent.add(createTextItem("HAS ACQ CONTEXT", CODE_INSTANCE_NUMBER, SCHEME_DCM,
                MEANING_INSTANCE_NUMBER, instance.instanceNumber));
        }

        // Number of Frames (conditionally required for multiframe)
        if (instance.numberOfFrames != null) {
            entryContent.add(createNumericItem("HAS ACQ CONTEXT", CODE_NUMBER_OF_FRAMES, SCHEME_DCM,
                MEANING_NUMBER_OF_FRAMES, instance.numberOfFrames));
        }

        return entry;
    }

    /**
     * Builds a simple root content sequence with study-level acquisition context.
     * Used for manifests that need these items at the root level.
     */
    public static void addRootAcquisitionContext(Sequence contentSeq, String studyInstanceUID, String modality) {
        contentSeq.add(createCodeItem(be.uzleuven.ihe.dicom.constants.DicomConstants.RELATIONSHIP_CONTAINS,
            CODE_MODALITY, SCHEME_DCM, MEANING_MODALITY,
            DicomCreatorUtils.code(modality, SCHEME_DCM, modality)));
        contentSeq.add(createUIDRefItem(be.uzleuven.ihe.dicom.constants.DicomConstants.RELATIONSHIP_CONTAINS,
            CODE_STUDY_INSTANCE_UID, SCHEME_DCM, MEANING_STUDY_INSTANCE_UID, studyInstanceUID));
        contentSeq.add(createCodeItem(be.uzleuven.ihe.dicom.constants.DicomConstants.RELATIONSHIP_CONTAINS,
            CODE_TARGET_REGION, SCHEME_DCM, MEANING_TARGET_REGION,
            DicomCreatorUtils.code(CODE_REGION_ABDOMEN, SCHEME_SRT, MEANING_REGION_ABDOMEN)));
    }
}

