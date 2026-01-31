package be.uzleuven.ihe.dicom.creator.utils;

import be.uzleuven.ihe.dicom.constants.CodeConstants;
import be.uzleuven.ihe.dicom.creator.model.SimulatedInstance;
import be.uzleuven.ihe.dicom.creator.model.SimulatedSeries;
import be.uzleuven.ihe.dicom.creator.model.SimulatedStudy;
import be.uzleuven.ihe.dicom.creator.model.MADOOptions;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.data.UID;

import java.util.ArrayList;
import java.util.List;

import static be.uzleuven.ihe.dicom.constants.CodeConstants.*;
import static be.uzleuven.ihe.dicom.creator.utils.DicomCreatorUtils.code;
import static be.uzleuven.ihe.dicom.creator.utils.SRContentItemUtils.*;

/**
 * Utility class for creating MADO-specific content items and descriptors.
 */
public class MADOContentUtils {

    private MADOContentUtils() {
        // Utility class
    }

    /**
     * Adds TID 16XX Descriptors to an Image Library Entry for a KOS/KIN instance.
     * This describes WHAT the KIN references without retrieving it.
     */
    public static void addKinDescriptors(Sequence entryContent, SimulatedStudy study) {
        // Container for KOS Descriptors (concept: Key Object Description)
        Attributes kosDesc = new Attributes();
        kosDesc.setString(Tag.RelationshipType, VR.CS, be.uzleuven.ihe.dicom.constants.DicomConstants.RELATIONSHIP_CONTAINS);
        kosDesc.setString(Tag.ValueType, VR.CS, "CONTAINER");
        kosDesc.newSequence(Tag.ConceptNameCodeSequence, 1)
                .add(code(CODE_KOS_DESCRIPTION, SCHEME_DCM, MEANING_KOS_DESCRIPTION));

        Sequence descSeq = kosDesc.newSequence(Tag.ContentSequence, 10);

        // KOS Title Code (required by this repo's validator for V-DESC-02)
        // Use an example from CID 7010-ish style; validator only checks presence.
        descSeq.add(createCodeItem(be.uzleuven.ihe.dicom.constants.DicomConstants.RELATIONSHIP_CONTAINS,
            CODE_KOS_TITLE, SCHEME_DCM, MEANING_KOS_TITLE,
            code(CODE_OF_INTEREST, SCHEME_DCM, MEANING_OF_INTEREST)));

        // KOS Description (optional)
        descSeq.add(createTextItem(be.uzleuven.ihe.dicom.constants.DicomConstants.RELATIONSHIP_CONTAINS,
                CODE_KOS_DESCRIPTION, CodeConstants.SCHEME_DCM, "KOS Object Description", "Key Objects for Surgery"));

        // Select up to N referenced instances (IMAGE items) from NON-KIN instances to describe.
        MADOOptions options = (MADOOptions) study.getOptions();
        int desired = options != null ? Math.max(0, options.getKeyImageCount()) : 3;
        if (desired == 0) desired = 3; // default fallback

        List<SimulatedInstance> selected = selectKeyImages(study, desired);

        // Add IMAGE items for selected instances (per MADO TID 16XX requirements)
        for (SimulatedInstance inst : selected) {
            Attributes imageItem = new Attributes();
            imageItem.setString(Tag.RelationshipType, VR.CS, be.uzleuven.ihe.dicom.constants.DicomConstants.RELATIONSHIP_CONTAINS);
            imageItem.setString(Tag.ValueType, VR.CS, "IMAGE"); // Must be IMAGE per MADO TID 16XX

            Sequence refSop = imageItem.newSequence(Tag.ReferencedSOPSequence, 1);
            Attributes refItem = new Attributes();
            refItem.setString(Tag.ReferencedSOPClassUID, VR.UI, inst.getSopClassUID());
            refItem.setString(Tag.ReferencedSOPInstanceUID, VR.UI, inst.getSopInstanceUID());
            refSop.add(refItem);

            descSeq.add(imageItem);
        }

        entryContent.add(kosDesc);
    }

    /**
     * Select key images from the study for KIN descriptors.
     * Prefers multiframe images first, then single-frame.
     */
    private static List<SimulatedInstance> selectKeyImages(SimulatedStudy study, int desired) {
        // Collect non-KIN instances and mark multiframe candidates
        List<SimulatedInstance> multiframeCandidates = new ArrayList<>();
        List<SimulatedInstance> singleframeCandidates = new ArrayList<>();

        for (SimulatedSeries s : study.getSeriesList()) {
            for (SimulatedInstance inst : s.getInstances()) {
                if (inst.isKIN()) continue;
                if (UID.EnhancedCTImageStorage.equals(inst.getSopClassUID())) {
                    multiframeCandidates.add(inst);
                } else {
                    singleframeCandidates.add(inst);
                }
            }
        }

        // Build final ordered list: prefer one multiframe if available, then single-frame items.
        List<SimulatedInstance> selected = new ArrayList<>();
        if (!multiframeCandidates.isEmpty()) {
            selected.add(multiframeCandidates.get(0));
        }
        for (SimulatedInstance si : singleframeCandidates) {
            if (selected.size() >= desired) break;
            selected.add(si);
        }
        // If still short, fill from remaining multiframe candidates
        for (SimulatedInstance mi : multiframeCandidates) {
            if (selected.size() >= desired) break;
            if (!selected.contains(mi)) selected.add(mi);
        }

        return selected;
    }

    /**
     * Create an Image Library Entry (IMAGE item) for a DICOM instance.
     *
     * Per TID 1601, the IMAGE item should NOT have a nested ContentSequence.
     * Instance-level metadata (Instance Number, Number of Frames) should be
     * added as siblings to this IMAGE item within the Image Library Group.
     */
    public static Attributes createImageLibraryEntry(SimulatedInstance inst) {
        Attributes entry = new Attributes();
        entry.setString(Tag.RelationshipType, VR.CS, be.uzleuven.ihe.dicom.constants.DicomConstants.RELATIONSHIP_CONTAINS);
        entry.setString(Tag.ValueType, VR.CS, "IMAGE");

        Sequence refSop = entry.newSequence(Tag.ReferencedSOPSequence, 1);
        Attributes refItem = new Attributes();
        refItem.setString(Tag.ReferencedSOPClassUID, VR.UI, inst.getSopClassUID());
        refItem.setString(Tag.ReferencedSOPInstanceUID, VR.UI, inst.getSopInstanceUID());
        refSop.add(refItem);

        // NOTE: Per TID 1601, no ContentSequence inside IMAGE items.
        // Instance-level metadata is added as siblings in createImageLibraryGroup().

        return entry;
    }

    /**
     * Create an Image Library Group for a DICOM series.
     *
     * Per TID 1601, instance-level metadata (Instance Number, Number of Frames)
     * should be SIBLINGS of the IMAGE item within the Image Library Group,
     * NOT children nested inside the IMAGE item.
     *
     * Structure:
     *   Image Library Group (CONTAINER)
     *     ├── HAS ACQ CONTEXT -> Series metadata (Modality, Series UID, etc.)
     *     ├── HAS ACQ CONTEXT -> Instance Number (TEXT)
     *     ├── HAS ACQ CONTEXT -> Number of Frames (NUM) [if multiframe]
     *     └── CONTAINS -> IMAGE (with ReferencedSOPSequence)
     */
    public static Attributes createImageLibraryGroup(SimulatedSeries series, SimulatedStudy study) {
        Attributes group = new Attributes();
        group.setString(Tag.RelationshipType, VR.CS, be.uzleuven.ihe.dicom.constants.DicomConstants.RELATIONSHIP_CONTAINS);
        group.setString(Tag.ValueType, VR.CS, "CONTAINER");
        group.newSequence(Tag.ConceptNameCodeSequence, 1)
                .add(code(CODE_IMAGE_LIBRARY_GROUP, SCHEME_DCM, MEANING_IMAGE_LIBRARY_GROUP));

        Sequence groupSeq = group.newSequence(Tag.ContentSequence, 20);

        // Series Level Metadata (TID 1602)
        groupSeq.add(createCodeItem("HAS ACQ CONTEXT", CODE_MODALITY, SCHEME_DCM, MEANING_MODALITY,
                code(series.getModality(), SCHEME_DCM, series.getModality())));
        groupSeq.add(createUIDRefItem("HAS ACQ CONTEXT", CODE_SERIES_INSTANCE_UID, SCHEME_DCM,
                MEANING_SERIES_INSTANCE_UID, series.getSeriesUID()));
        groupSeq.add(createTextItem("HAS ACQ CONTEXT", CODE_SERIES_DESCRIPTION, SCHEME_DCM,
                MEANING_SERIES_DESCRIPTION, series.getDescription()));
        groupSeq.add(createTextItem("HAS ACQ CONTEXT", CODE_SERIES_DATE, SCHEME_DCM,
                MEANING_SERIES_DATE, series.getSeriesDate()));
        groupSeq.add(createTextItem("HAS ACQ CONTEXT", CODE_SERIES_TIME, SCHEME_DCM,
                MEANING_SERIES_TIME, series.getSeriesTime()));
        // KOS TID 2010 forbids NUM, so represent the series number as TEXT.
        groupSeq.add(createTextItem("HAS ACQ CONTEXT", CODE_SERIES_NUMBER, SCHEME_DCM,
                MEANING_SERIES_NUMBER, Integer.toString(series.getSeriesNumber())));
        // Number of Series Related Instances (NUM) - Required by MADO TID 1602
        groupSeq.add(createNumericItem("HAS ACQ CONTEXT", CODE_NUM_SERIES_RELATED_INSTANCES, SCHEME_DCM,
                MEANING_NUM_SERIES_RELATED_INSTANCES, series.getInstances().size()));

        // Instance Level Entries (TID 1601) - metadata and IMAGE as siblings
        int instanceNumber = 1;
        for (SimulatedInstance inst : series.getInstances()) {
            // Add Instance Number as sibling (HAS ACQ CONTEXT)
            groupSeq.add(createTextItem("HAS ACQ CONTEXT", CODE_INSTANCE_NUMBER, SCHEME_DCM,
                    MEANING_INSTANCE_NUMBER, Integer.toString(instanceNumber)));

            // Add Number of Frames as sibling if multiframe (HAS ACQ CONTEXT)
            if (StudyGeneratorUtils.isMultiframe(inst.getSopClassUID())) {
                groupSeq.add(createNumericItem("HAS ACQ CONTEXT", CODE_NUMBER_OF_FRAMES, SCHEME_DCM,
                        MEANING_NUMBER_OF_FRAMES, 10));
            }

            // Add the IMAGE item (CONTAINS) - no nested ContentSequence
            Attributes entry = createImageLibraryEntry(inst);
            groupSeq.add(entry);

            // If this is the KIN instance, add KOS descriptors container as sibling
            if (inst.isKIN()) {
                addKinDescriptorsToGroup(groupSeq, study);
            }

            instanceNumber++;
        }

        return group;
    }

    /**
     * Adds KIN descriptors as siblings in the Image Library Group.
     */
    private static void addKinDescriptorsToGroup(Sequence groupSeq, SimulatedStudy study) {
        // Container for KOS Descriptors (concept: Key Object Description)
        Attributes kosDesc = new Attributes();
        kosDesc.setString(Tag.RelationshipType, VR.CS, be.uzleuven.ihe.dicom.constants.DicomConstants.RELATIONSHIP_CONTAINS);
        kosDesc.setString(Tag.ValueType, VR.CS, "CONTAINER");
        kosDesc.newSequence(Tag.ConceptNameCodeSequence, 1)
                .add(code(CODE_KOS_DESCRIPTION, SCHEME_DCM, MEANING_KOS_DESCRIPTION));

        Sequence descSeq = kosDesc.newSequence(Tag.ContentSequence, 10);

        // KOS Title Code
        descSeq.add(createCodeItem(be.uzleuven.ihe.dicom.constants.DicomConstants.RELATIONSHIP_CONTAINS,
            CODE_KOS_TITLE, SCHEME_DCM, MEANING_KOS_TITLE,
            code(CODE_OF_INTEREST, SCHEME_DCM, MEANING_OF_INTEREST)));

        // KOS Description
        descSeq.add(createTextItem(be.uzleuven.ihe.dicom.constants.DicomConstants.RELATIONSHIP_CONTAINS,
                CODE_KOS_DESCRIPTION, CodeConstants.SCHEME_DCM, "KOS Object Description", "Key Objects for Surgery"));

        // Select key images
        MADOOptions options = (MADOOptions) study.getOptions();
        int desired = options != null ? Math.max(0, options.getKeyImageCount()) : 3;
        if (desired == 0) desired = 3;

        List<SimulatedInstance> selected = selectKeyImages(study, desired);

        for (SimulatedInstance inst : selected) {
            Attributes imageItem = new Attributes();
            imageItem.setString(Tag.RelationshipType, VR.CS, be.uzleuven.ihe.dicom.constants.DicomConstants.RELATIONSHIP_CONTAINS);
            imageItem.setString(Tag.ValueType, VR.CS, "IMAGE");

            Sequence refSop = imageItem.newSequence(Tag.ReferencedSOPSequence, 1);
            Attributes refItem = new Attributes();
            refItem.setString(Tag.ReferencedSOPClassUID, VR.UI, inst.getSopClassUID());
            refItem.setString(Tag.ReferencedSOPInstanceUID, VR.UI, inst.getSopInstanceUID());
            refSop.add(refItem);

            descSeq.add(imageItem);
        }

        groupSeq.add(kosDesc);
    }
}
