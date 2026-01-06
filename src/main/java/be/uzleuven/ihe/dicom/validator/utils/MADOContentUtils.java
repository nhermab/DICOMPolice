package be.uzleuven.ihe.dicom.validator.utils;

import be.uzleuven.ihe.dicom.constants.CodeConstants;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import be.uzleuven.ihe.dicom.validator.model.ValidationResult;
import be.uzleuven.ihe.dicom.constants.DicomConstants;
import be.uzleuven.ihe.dicom.constants.ValidationMessages;

import java.util.HashSet;
import java.util.Set;

/**
 * Content utilities for MADO manifests.
 * Handles extraction and validation of references from TID 1600 Image Library structure.
 */
public final class MADOContentUtils {

    private MADOContentUtils() {
    }

    /**
     * Collect all referenced SOP Instance UIDs from MADO Content Sequence.
     * This includes traversing the TID 1600 Image Library structure.
     */
    public static Set<String> collectReferencedInstancesFromContent(Attributes dataset) {
        Set<String> referencedUIDs = new HashSet<>();

        Sequence contentSeq = dataset.getSequence(Tag.ContentSequence);
        if (contentSeq == null || contentSeq.isEmpty()) {
            return referencedUIDs;
        }

        // Recursively traverse content tree
        for (Attributes item : contentSeq) {
            collectReferencesRecursive(item, referencedUIDs);
        }

        return referencedUIDs;
    }

    private static void collectReferencesRecursive(Attributes item, Set<String> referencedUIDs) {
        String valueType = item.getString(Tag.ValueType);

        // IMAGE and COMPOSITE value types contain references
        if (DicomConstants.VALUE_TYPE_IMAGE.equals(valueType) || DicomConstants.VALUE_TYPE_COMPOSITE.equals(valueType)) {
            Sequence refSOPSeq = item.getSequence(Tag.ReferencedSOPSequence);
            if (refSOPSeq != null) {
                for (Attributes refSOP : refSOPSeq) {
                    String sopInstanceUID = refSOP.getString(Tag.ReferencedSOPInstanceUID);
                    if (sopInstanceUID != null && !sopInstanceUID.trim().isEmpty()) {
                        referencedUIDs.add(sopInstanceUID);
                    }
                }
            }
        }

        // Check for UIDREF value type (used for SOP Instance UID content items in TID 1600)
        if (DicomConstants.VALUE_TYPE_UIDREF.equals(valueType)) {
            String uid = item.getString(Tag.UID);
            if (uid != null && !uid.trim().isEmpty()) {
                // Check if this is a SOP Instance UID reference (ddd007 code)
                Sequence conceptSeq = item.getSequence(Tag.ConceptNameCodeSequence);
                if (conceptSeq != null && !conceptSeq.isEmpty()) {
                    Attributes concept = conceptSeq.get(0);
                    String codeValue = concept.getString(Tag.CodeValue);
                    if (CodeConstants.CODE_SOP_INSTANCE_UID.equals(codeValue)) { // SOP Instance UID code  EV (ddd007, DCM, “SOP Instance UID”)
                        //TODO: this is wrong????
                        referencedUIDs.add(uid);
                    }
                }
            }
        }

        // Recurse into nested content
        Sequence contentSeq = item.getSequence(Tag.ContentSequence);
        if (contentSeq != null && !contentSeq.isEmpty()) {
            for (Attributes nestedItem : contentSeq) {
                collectReferencesRecursive(nestedItem, referencedUIDs);
            }
        }
    }

    /**
     * Validate that there are no duplicate UID references in the manifest.
     */
    public static void validateNoDuplicateReferences(Attributes dataset, ValidationResult result, String modulePath) {
        Set<String> seenUIDs = new HashSet<>();
        Set<String> duplicateUIDs = new HashSet<>();

        Sequence contentSeq = dataset.getSequence(Tag.ContentSequence);
        if (contentSeq == null || contentSeq.isEmpty()) {
            return;
        }

        for (Attributes item : contentSeq) {
            checkForDuplicatesRecursive(item, seenUIDs, duplicateUIDs);
        }

        if (!duplicateUIDs.isEmpty()) {
            for (String uid : duplicateUIDs) {
                result.addWarning("Duplicate SOP Instance UID reference found in content: " + uid, modulePath);
            }
        }
    }

    private static void checkForDuplicatesRecursive(Attributes item, Set<String> seenUIDs, Set<String> duplicateUIDs) {
        String valueType = item.getString(Tag.ValueType);

        if (DicomConstants.VALUE_TYPE_IMAGE.equals(valueType) || DicomConstants.VALUE_TYPE_COMPOSITE.equals(valueType)) {
            Sequence refSOPSeq = item.getSequence(Tag.ReferencedSOPSequence);
            if (refSOPSeq != null) {
                for (Attributes refSOP : refSOPSeq) {
                    String sopInstanceUID = refSOP.getString(Tag.ReferencedSOPInstanceUID);
                    if (sopInstanceUID != null && !sopInstanceUID.trim().isEmpty()) {
                        if (seenUIDs.contains(sopInstanceUID)) {
                            duplicateUIDs.add(sopInstanceUID);
                        } else {
                            seenUIDs.add(sopInstanceUID);
                        }
                    }
                }
            }
        }

        // Recurse into nested content
        Sequence contentSeq = item.getSequence(Tag.ContentSequence);
        if (contentSeq != null && !contentSeq.isEmpty()) {
            for (Attributes nestedItem : contentSeq) {
                checkForDuplicatesRecursive(nestedItem, seenUIDs, duplicateUIDs);
            }
        }
    }

    /**
     * Validate UID hierarchy consistency (Study > Series > Instance).
     */
    public static void validateUIDHierarchy(Attributes dataset, ValidationResult result, String modulePath) {
        // Get study UID from dataset
        String studyUID = dataset.getString(Tag.StudyInstanceUID);
        if (studyUID == null) {
            return;
        }

        // Check Evidence sequence for hierarchy consistency
        Sequence evidenceSeq = dataset.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence);
        if (evidenceSeq == null || evidenceSeq.isEmpty()) {
            return;
        }

        for (int studyIdx = 0; studyIdx < evidenceSeq.size(); studyIdx++) {
            Attributes studyItem = evidenceSeq.get(studyIdx);
            String evidenceStudyUID = studyItem.getString(Tag.StudyInstanceUID);

            if (evidenceStudyUID == null) {
                result.addError(String.format(ValidationMessages.STUDY_UID_MISSING_EVIDENCE, studyIdx), modulePath);
                continue;
            }

            Sequence seriesSeq = studyItem.getSequence(Tag.ReferencedSeriesSequence);
            if (seriesSeq == null || seriesSeq.isEmpty()) {
                result.addWarning("No series references in Evidence study item " + studyIdx, modulePath);
                continue;
            }

            Set<String> seriesUIDs = new HashSet<>();
            for (int seriesIdx = 0; seriesIdx < seriesSeq.size(); seriesIdx++) {
                Attributes seriesItem = seriesSeq.get(seriesIdx);
                String seriesUID = seriesItem.getString(Tag.SeriesInstanceUID);

                if (seriesUID == null) {
                    result.addError(String.format(ValidationMessages.SERIES_UID_MISSING_EVIDENCE, studyIdx, seriesIdx), modulePath);
                    continue;
                }

                // Check for duplicate series UIDs within study
                if (seriesUIDs.contains(seriesUID)) {
                    result.addWarning("Duplicate Series Instance UID in study " + evidenceStudyUID +
                                    ": " + seriesUID, modulePath);
                } else {
                    seriesUIDs.add(seriesUID);
                }

                // Validate instance UIDs
                Sequence sopSeq = seriesItem.getSequence(Tag.ReferencedSOPSequence);
                if (sopSeq == null || sopSeq.isEmpty()) {
                    result.addWarning("No instance references in Evidence study " + studyIdx +
                                    ", series " + seriesIdx, modulePath);
                    continue;
                }

                Set<String> instanceUIDs = new HashSet<>();
                for (Attributes sopItem : sopSeq) {
                    String instanceUID = sopItem.getString(Tag.ReferencedSOPInstanceUID);
                    if (instanceUID == null) {
                        result.addError(ValidationMessages.SOP_INSTANCE_UID_MISSING_EVIDENCE, modulePath);
                        continue;
                    }

                    // Check for duplicate instance UIDs within series
                    if (instanceUIDs.contains(instanceUID)) {
                        result.addWarning("Duplicate SOP Instance UID in series " + seriesUID +
                                        ": " + instanceUID, modulePath);
                    } else {
                        instanceUIDs.add(instanceUID);
                    }
                }
            }
        }
    }
}

