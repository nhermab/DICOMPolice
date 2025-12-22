package be.uzleuven.ihe.dicom.validator.utils;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import be.uzleuven.ihe.dicom.validator.validation.iod.AbstractIODValidator;
import be.uzleuven.ihe.dicom.validator.model.ValidationResult;
import be.uzleuven.ihe.dicom.constants.DicomConstants;

import java.util.HashSet;
import java.util.Set;

/**
 * Utilities for scanning SR references and collecting Evidence UIDs
 */
public class SRReferenceUtils {
    public static class SRRefScan {
        public final Set<String> referencedSOPInstanceUIDs = new HashSet<>();
    }

    public static SRRefScan scanSRReferencesWithChecks(Attributes dataset, ValidationResult result, String modulePath, AbstractIODValidator ctx) {
        return scanSRReferencesWithChecks(dataset, result, modulePath, ctx, false);
    }

    /**
     * @param allowDuplicateReferencedSOPInstanceUIDs when true, do not emit an error if the same SOP Instance UID
     *                                               is referenced multiple times in SR ContentSequence.
     *                                               This is needed for profiles like IHE MADO where the same instance
     *                                               may legitimately appear both as an Image Library Entry and as a
     *                                               visual reference inside a Key Object Description subtree.
     */
    public static SRRefScan scanSRReferencesWithChecks(
            Attributes dataset,
            ValidationResult result,
            String modulePath,
            AbstractIODValidator ctx,
            boolean allowDuplicateReferencedSOPInstanceUIDs
    ) {
        SRRefScan scan = new SRRefScan();
        String selfSOPInstanceUID = dataset.getString(Tag.SOPInstanceUID);

        Sequence root = dataset.getSequence(Tag.ContentSequence);
        if (root == null) return scan;

        for (int i = 0; i < root.size(); i++) {
            Attributes item = root.get(i);
            String itemPath = ctx.buildPath(modulePath, "ContentSequence", i);

            // For XDS-I, top-level relationship should be CONTAINS.
            String rel = item.getString(Tag.RelationshipType);
            if (rel != null && !DicomConstants.RELATIONSHIP_CONTAINS.equals(rel)) {
                result.addError("Top-level ContentSequence item RelationshipType must be CONTAINS for XDS-I manifest; found: " + rel, itemPath);
            }

            scanSRReferencesRecursive(item, itemPath, selfSOPInstanceUID, scan, result, ctx, allowDuplicateReferencedSOPInstanceUIDs);
        }

        return scan;
    }

    private static void scanSRReferencesRecursive(Attributes item,
                                          String itemPath,
                                          String selfSOPInstanceUID,
                                          SRRefScan scan,
                                          ValidationResult result,
                                          AbstractIODValidator ctx,
                                          boolean allowDuplicateReferencedSOPInstanceUIDs) {

        String valueType = item.getString(Tag.ValueType);
        if (DicomConstants.VALUE_TYPE_IMAGE.equals(valueType) || DicomConstants.VALUE_TYPE_COMPOSITE.equals(valueType)) {
            Sequence refSeq = item.getSequence(Tag.ReferencedSOPSequence);
            if (refSeq == null || refSeq.isEmpty()) {
                result.addError(valueType + " content item must include a non-empty ReferencedSOPSequence", itemPath);
            } else {
                for (int r = 0; r < refSeq.size(); r++) {
                    Attributes ref = refSeq.get(r);
                    String refPath = ctx.buildPath(itemPath, "ReferencedSOPSequence", r);

                    String sopInst = ref.getString(Tag.ReferencedSOPInstanceUID);

                    if (ref.getString(Tag.ReferencedSOPClassUID) != null) {
                        ctx.checkUID(ref, Tag.ReferencedSOPClassUID, "ReferencedSOPClassUID", result, refPath);
                    }
                    if (sopInst != null) {
                        ctx.checkUID(ref, Tag.ReferencedSOPInstanceUID, "ReferencedSOPInstanceUID", result, refPath);
                    }

                    if (sopInst != null && !sopInst.trim().isEmpty()) {
                        String uid = sopInst.trim();

                        if (selfSOPInstanceUID != null && selfSOPInstanceUID.equals(uid)) {
                            result.addError("Manifest references itself (ReferencedSOPInstanceUID equals this KOS SOPInstanceUID)", refPath);
                        }

                        if (!scan.referencedSOPInstanceUIDs.add(uid)) {
                            if (!allowDuplicateReferencedSOPInstanceUIDs) {
                                result.addError("Duplicate referenced SOP Instance UID in SR ContentSequence: " + uid, refPath);
                            }
                        }
                    }
                }
            }
        }

        Sequence nested = item.getSequence(Tag.ContentSequence);
        if (nested != null) {
            for (int i = 0; i < nested.size(); i++) {
                Attributes child = nested.get(i);
                scanSRReferencesRecursive(child, ctx.buildPath(itemPath, "ContentSequence", i), selfSOPInstanceUID, scan, result, ctx, allowDuplicateReferencedSOPInstanceUIDs);
            }
        }
    }

    public static Set<String> collectReferencedSOPInstanceUIDsFromEvidence(Attributes dataset) {
        Set<String> uids = new HashSet<>();
        Sequence evidence = dataset.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence);
        if (evidence == null) return uids;

        for (Attributes studyItem : evidence) {
            Sequence seriesSeq = studyItem.getSequence(Tag.ReferencedSeriesSequence);
            if (seriesSeq == null) continue;
            for (Attributes seriesItem : seriesSeq) {
                Sequence sopSeq = seriesItem.getSequence(Tag.ReferencedSOPSequence);
                if (sopSeq == null) continue;
                for (Attributes sopItem : sopSeq) {
                    String uid = sopItem.getString(Tag.ReferencedSOPInstanceUID);
                    if (uid != null && !uid.trim().isEmpty()) {
                        uids.add(uid.trim());
                    }
                }
            }
        }
        return uids;
    }

    /**
     * Validate a ReferencedSOPSequence (structure + UID checks). This duplicates the checks in
     * KeyObjectSelectionValidator.validateReferencedSOPSequence and is used by content helpers.
     */
    public static void validateReferencedSOPSequence(Attributes parent, ValidationResult result, String parentPath, AbstractIODValidator ctx) {
        Sequence seq = parent.getSequence(Tag.ReferencedSOPSequence);
        if (seq == null) {
            return;
        }

        for (int i = 0; i < seq.size(); i++) {
            Attributes item = seq.get(i);
            String itemPath = ctx.buildPath(parentPath, "ReferencedSOPSequence", i);

            // ReferencedSOPClassUID - Type 1
            ctx.checkRequiredAttribute(item, Tag.ReferencedSOPClassUID, "ReferencedSOPClassUID", result, itemPath);
            ctx.checkUID(item, Tag.ReferencedSOPClassUID, "ReferencedSOPClassUID", result, itemPath);

            // ReferencedSOPInstanceUID - Type 1
            ctx.checkRequiredAttribute(item, Tag.ReferencedSOPInstanceUID, "ReferencedSOPInstanceUID", result, itemPath);
            ctx.checkUID(item, Tag.ReferencedSOPInstanceUID, "ReferencedSOPInstanceUID", result, itemPath);

            // Validate Retrieve Information (optional for XDS-I.b WADO-RS)
            validateRetrieveInformation(item, result, itemPath);
        }
    }

    /**
     * Validate Retrieve Information attributes (optional but recommended for XDS-I.b).
     * - Retrieve URL (0008,1190) for WADO-RS retrieval
     * - Retrieve AE Title (0008,0054) for DICOM network retrieval
     * - Retrieve Location UID (0040,E011) for advanced retrieval
     */
    private static void validateRetrieveInformation(Attributes item, ValidationResult result, String itemPath) {
        String retrieveURL = item.getString(Tag.RetrieveURL);
        String retrieveAETitle = item.getString(Tag.RetrieveAETitle);
        String retrieveLocationUID = item.getString(Tag.RetrieveLocationUID);

        // Validate Retrieve URL if present
        if (retrieveURL != null && !retrieveURL.isEmpty()) {
            // Check if it's a well-formed URL
            if (!retrieveURL.matches("^https?://.*")) {
                result.addError("Retrieve URL (0008,1190) must be a valid absolute URL starting with http:// or https://. " +
                              "Found: " + retrieveURL, itemPath);
            } else if (retrieveURL.contains(" ")) {
                result.addError("Retrieve URL (0008,1190) contains spaces which are not allowed in URLs", itemPath);
            } else {
                result.addInfo("Retrieve URL present for WADO-RS retrieval: " + retrieveURL, itemPath);
            }
        }

        // Validate Retrieve AE Title if present
        if (retrieveAETitle != null && !retrieveAETitle.isEmpty()) {
            // AE Title should be <= 16 characters and no leading/trailing spaces in DICOM
            if (retrieveAETitle.length() > 16) {
                result.addWarning("Retrieve AE Title (0008,0054) exceeds 16 characters (length: " +
                                retrieveAETitle.length() + ")", itemPath);
            }
            // Check for control characters
            if (!retrieveAETitle.matches("^[\\x20-\\x7E]+$")) {
                result.addWarning("Retrieve AE Title (0008,0054) contains invalid characters. " +
                                "Should contain only printable ASCII characters", itemPath);
            }
        }

        // Validate Retrieve Location UID if present
        if (retrieveLocationUID != null && !retrieveLocationUID.isEmpty()) {
            if (!retrieveLocationUID.matches("^[0-9.]+$") || retrieveLocationUID.length() > 64) {
                result.addError("Retrieve Location UID (0040,E011) is invalid. " +
                              "Must be a valid UID format (digits and dots, max 64 chars)", itemPath);
            }
        }

        // If multiple retrieval methods present, check consistency
        if (retrieveURL != null && retrieveAETitle != null) {
            result.addInfo("Multiple retrieval methods available: WADO-RS URL and DICOM AE Title", itemPath);
        }
    }

    /**
     * Validate CurrentRequestedProcedureEvidenceSequence structure (Study -> Series -> SOP) with UID checks.
     * This is a structural validator (doesn't enforce profile-level cardinality).
     */
    //noinspection unused
    public static void validateCurrentRequestedProcedureEvidenceSequence(
            Attributes dataset,
            ValidationResult result,
            String modulePath,
            boolean verbose,
            AbstractIODValidator ctx
    ) {
        if (verbose) {
            result.addInfo("Validating CurrentRequestedProcedureEvidenceSequence", modulePath);
        }

        Sequence seq = dataset.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence);
        if (seq == null) {
            return;
        }

        for (int i = 0; i < seq.size(); i++) {
            Attributes item = seq.get(i);
            String itemPath = ctx.buildPath(modulePath, "CurrentRequestedProcedureEvidenceSequence", i);

            // StudyInstanceUID - Type 1
            ctx.checkRequiredAttribute(item, Tag.StudyInstanceUID, "StudyInstanceUID", result, itemPath);
            ctx.checkUID(item, Tag.StudyInstanceUID, "StudyInstanceUID", result, itemPath);

            // ReferencedSeriesSequence - Type 1
            if (ctx.checkSequenceAttribute(item, Tag.ReferencedSeriesSequence, "ReferencedSeriesSequence", true, result, itemPath)) {
                validateReferencedSeriesSequence(item, result, itemPath, ctx);
            }
        }
    }

    /**
     * Validate ReferencedSeriesSequence structure (Series -> SOP) with UID checks.
     */
    public static void validateReferencedSeriesSequence(
            Attributes parent,
            ValidationResult result,
            String parentPath,
            AbstractIODValidator ctx
    ) {
        Sequence seq = parent.getSequence(Tag.ReferencedSeriesSequence);
        if (seq == null) {
            return;
        }

        for (int i = 0; i < seq.size(); i++) {
            Attributes item = seq.get(i);
            String itemPath = ctx.buildPath(parentPath, "ReferencedSeriesSequence", i);

            // SeriesInstanceUID - Type 1
            ctx.checkRequiredAttribute(item, Tag.SeriesInstanceUID, "SeriesInstanceUID", result, itemPath);
            ctx.checkUID(item, Tag.SeriesInstanceUID, "SeriesInstanceUID", result, itemPath);

            // ReferencedSOPSequence - Type 1
            if (ctx.checkSequenceAttribute(item, Tag.ReferencedSOPSequence, "ReferencedSOPSequence", true, result, itemPath)) {
                validateReferencedSOPSequence(item, result, itemPath, ctx);
            }
        }
    }
}
