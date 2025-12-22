package be.uzleuven.ihe.dicom.validator.utils;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import be.uzleuven.ihe.dicom.validator.validation.iod.AbstractIODValidator;
import be.uzleuven.ihe.dicom.validator.model.ValidationResult;
import be.uzleuven.ihe.dicom.validator.validation.MADORetrievalValidator;
import be.uzleuven.ihe.dicom.validator.validation.MADOTemplateValidator;
import be.uzleuven.ihe.dicom.validator.validation.MADOTimezoneValidator;
import be.uzleuven.ihe.dicom.constants.ValidationMessages;

/**
 * MADO (Manifest-based Access to DICOM Objects) profile validation utilities.
 * MADO extends XDS-I.b with TID 1600 Image Library for enhanced metadata.
 */
public final class MADOProfileUtils {

    private MADOProfileUtils() {
    }

    /**
     * Validate the MADO profile for the given dataset.
     *
     * @param dataset  The DICOM dataset to validate
     * @param result   The validation result object to accumulate findings
     * @param verbose  Whether to include verbose informational messages
     * @param ctx      The validation context, for shared state between validators
     */
    public static void validateMADOProfile(Attributes dataset, ValidationResult result, boolean verbose, AbstractIODValidator ctx) {
        // ctx is passed for potential cross-validator shared state; intentionally unused today.
        @SuppressWarnings("unused")
        AbstractIODValidator ignoredCtx = ctx;

        String modulePath = "MADOProfile";

        // Phase 1: Check for forbidden elements
        validateForbiddenElements(dataset, result, modulePath, ctx);

        // Phase 2: Validate timezone offset consistency
        MADOTimezoneValidator.validateTimezoneConsistency(dataset, result, modulePath);

        // Phase 3: Profile Identification - Cascading Decision Tree
        MADOApproach approach = detectMADOApproach(dataset, result, modulePath, verbose);

        if (approach == MADOApproach.APPROACH_2_TID_1600) {
            if (verbose) {
                result.addInfo("MADO Format: Approach 2 (TID 1600 Image Library)", modulePath);
            }
            MADOTemplateValidator.validateTID1600Structure(dataset, result, modulePath, verbose);
        } else if (approach == MADOApproach.APPENDIX_B) {
            // For this validator + checklist, Approach 2 is required.
            // Don't run Appendix B validator here (it would produce V-ALT-xx noise that doesn't apply).
            result.addError(ValidationMessages.MADO_APPENDIX_B_NOT_SUPPORTED, modulePath);
        } else {
            result.addError(ValidationMessages.MADO_APPROACH_2_NOT_DETECTED, modulePath);
        }

        // Phase 5: Validate retrieval information (still useful regardless of approach)
        MADORetrievalValidator.validateRetrievalInformation(dataset, result, modulePath, verbose);

        // Phase 6: Validate Referenced Request Sequence (MADO-specific semantics)
        validateReferencedRequestSequence(dataset, result, modulePath);

        // Phase 7: Cross-validate Evidence and Content
        validateEvidenceContentConsistency(dataset, result, modulePath, ctx);

        if (verbose) {
            result.addInfo("MADO profile validation complete", modulePath);
        }
    }

    /** MADO approach enumeration. */
    private enum MADOApproach {
        APPROACH_2_TID_1600,  // Uses TID 1600 Image Library in SR Content Tree
        APPENDIX_B,           // Uses extended Hierarchical Macro attributes
        UNKNOWN               // Legacy KOS or unrecognized format
    }

    /**
     * Detect which MADO approach is used (cascading decision tree).
     *
     * Per MADO Requirements Section 7.1:
     * 1. Scan SR Content Tree for (111028, DCM, "Image Library")
     * 2. If found: Execute Path A (Approach 2)
     * 3. If not found: Check ReferencedSeriesSequence for extended attributes
     * 4. If extended attributes found: Execute Path B (Appendix B)
     * 5. If neither found: Legacy KOS
     */
    private static MADOApproach detectMADOApproach(Attributes dataset, ValidationResult result,
                                                   String modulePath, boolean verbose) {
        // Check for TID 1600 Image Library in ContentSequence
        Sequence contentSeq = dataset.getSequence(Tag.ContentSequence);
        if (contentSeq == null || contentSeq.isEmpty()) {
            result.addError(ValidationMessages.MADO_CONTENT_SEQUENCE_MISSING_APPROACH, modulePath);
            return MADOApproach.UNKNOWN;
        }

        if (hasImageLibrary(contentSeq)) {
            if (verbose) {
                result.addInfo("Detected TID 1600 Image Library (111028, DCM) in content tree", modulePath);
            }
            return MADOApproach.APPROACH_2_TID_1600;
        }

        // Check for Appendix B extended attributes in Evidence
        Sequence evidenceSeq = dataset.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence);
        if (evidenceSeq != null && !evidenceSeq.isEmpty()) {
            if (hasAppendixBExtensions(evidenceSeq)) {
                if (verbose) {
                    result.addInfo("Detected Appendix B style attributes in Evidence sequence", modulePath);
                }
                return MADOApproach.APPENDIX_B;
            }
        }

        // Not found - provide helpful diagnostic
        result.addError(ValidationMessages.MADO_FORMAT_DETECTION_FAILED, modulePath);

        return MADOApproach.UNKNOWN;
    }

    /**
     * Check if ContentSequence contains Image Library (111028, DCM).
     */
    private static boolean hasImageLibrary(Sequence contentSeq) {
        for (Attributes item : contentSeq) {
            String valueType = item.getString(Tag.ValueType);
            if ("CONTAINER".equals(valueType)) {
                Sequence conceptSeq = item.getSequence(Tag.ConceptNameCodeSequence);
                if (conceptSeq != null && !conceptSeq.isEmpty()) {
                    Attributes concept = conceptSeq.get(0);
                    String codeValue = concept.getString(Tag.CodeValue);
                    String codingScheme = concept.getString(Tag.CodingSchemeDesignator);

                    if ("111028".equals(codeValue) && "DCM".equals(codingScheme)) {
                        return true;  // Found Image Library
                    }
                }
            }

            // Recursively check nested content
            Sequence nestedContent = item.getSequence(Tag.ContentSequence);
            if (nestedContent != null && hasImageLibrary(nestedContent)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if Evidence sequence has Appendix B extended attributes.
     * Look for Modality (0008,0060) or Retrieve Location UID (0040,E011) in ReferencedSeriesSequence.
     */
    private static boolean hasAppendixBExtensions(Sequence evidenceSeq) {
        for (Attributes studyItem : evidenceSeq) {
            Sequence seriesSeq = studyItem.getSequence(Tag.ReferencedSeriesSequence);
            if (seriesSeq != null && !seriesSeq.isEmpty()) {
                for (Attributes seriesItem : seriesSeq) {
                    // Check for extended attributes that are MADO-specific
                    if (seriesItem.contains(Tag.Modality) ||
                        seriesItem.contains(Tag.RetrieveLocationUID) ||
                        seriesItem.contains(Tag.RetrieveURL)) {
                        return true;  // Found Appendix B extensions
                    }
                }
            }
        }
        return false;
    }

    private static void validateForbiddenElements(Attributes dataset, ValidationResult result, String modulePath, AbstractIODValidator ctx) {
        // Use XDS-I.b profile utilities for common forbidden element checks
        XDSIManifestProfileUtils.validateForbiddenElements(dataset, result, modulePath);

        // Check for self-reference using SRReferenceUtils
        // This scans the content tree and checks for self-references and duplicates.
        // MADO manifests legitimately repeat IMAGE references (e.g., Image Library entry + visual reference),
        // so we allow duplicates here while still detecting self-references.
        SRReferenceUtils.scanSRReferencesWithChecks(dataset, result, modulePath, ctx, true);

        // Check for IOCM rejection note titles (prohibited in sharing context)
        Sequence conceptSeq = dataset.getSequence(Tag.ConceptNameCodeSequence);
        if (conceptSeq != null && !conceptSeq.isEmpty()) {
            Attributes item = conceptSeq.get(0);
            String codeValue = item.getString(Tag.CodeValue);

            if (XDSIManifestProfileUtils.isForbiddenIOCMTitle(codeValue)) {
                result.addError(String.format(ValidationMessages.FORBIDDEN_IOCM_TITLE, codeValue), modulePath);
            }
        }
    }

    private static void validateReferencedRequestSequence(Attributes dataset, ValidationResult result, String modulePath) {
        Sequence refRequestSeq = dataset.getSequence(Tag.ReferencedRequestSequence);
        if (refRequestSeq == null || refRequestSeq.isEmpty()) {
            result.addError(ValidationMessages.REFERENCED_REQUEST_MISSING_MADO, modulePath);
            return;
        }

        String manifestStudyUID = dataset.getString(Tag.StudyInstanceUID);

        // Validate each request item
        for (int i = 0; i < refRequestSeq.size(); i++) {
            Attributes item = refRequestSeq.get(i);
            String itemPath = modulePath + ".ReferencedRequestSequence[" + i + "]";

            String itemStudyUID = item.getString(Tag.StudyInstanceUID);
            String accessionNumber = item.getString(Tag.AccessionNumber);
            String placerOrderNumber = item.getString(Tag.PlacerOrderNumberImagingServiceRequest);

            if (itemStudyUID == null || itemStudyUID.trim().isEmpty()) {
                result.addError(String.format(ValidationMessages.REFERENCED_REQUEST_STUDY_UID_MISSING, i), itemPath);
            } else if (manifestStudyUID != null && !manifestStudyUID.trim().isEmpty()
                    && !manifestStudyUID.trim().equals(itemStudyUID.trim())) {
                result.addError(String.format(ValidationMessages.REFERENCED_REQUEST_STUDY_UID_MISMATCH,
                        i, itemStudyUID, manifestStudyUID), itemPath);
            }

            // MADO: if AccessionNumber is present, Issuer SHALL be present (RC+ per profile text)
            if (accessionNumber == null) {
                result.addError(String.format(ValidationMessages.REFERENCED_REQUEST_ACCESSION_MISSING, i), itemPath);
            } else {
                Sequence issuerSeq = item.getSequence(Tag.IssuerOfAccessionNumberSequence);
                if (issuerSeq == null || issuerSeq.isEmpty()) {
                    result.addError(String.format(ValidationMessages.REFERENCED_REQUEST_ISSUER_MISSING, i), itemPath);
                } else {
                    // Validate issuer item content (UniversalEntityID + UniversalEntityIDType=ISO)
                    Attributes issuer = issuerSeq.get(0);
                    String universalEntityId = issuer.getString(Tag.UniversalEntityID);
                    String universalEntityIdType = issuer.getString(Tag.UniversalEntityIDType);

                    if (universalEntityId == null || universalEntityId.trim().isEmpty()) {
                        result.addError(String.format(ValidationMessages.REFERENCED_REQUEST_UNIVERSAL_ENTITY_ID_MISSING, i), itemPath);
                    }
                    if (universalEntityIdType == null || universalEntityIdType.trim().isEmpty()) {
                        result.addError(String.format(ValidationMessages.REFERENCED_REQUEST_UNIVERSAL_ENTITY_ID_TYPE_MISSING, i), itemPath);
                    } else if (!"ISO".equalsIgnoreCase(universalEntityIdType.trim())) {
                        result.addError(String.format(ValidationMessages.REFERENCED_REQUEST_UNIVERSAL_ENTITY_ID_TYPE_WRONG,
                                universalEntityIdType), itemPath);
                    }
                }
            }

            if (placerOrderNumber == null || placerOrderNumber.trim().isEmpty()) {
                result.addError(String.format(ValidationMessages.REFERENCED_REQUEST_PLACER_ORDER_MISSING, i), itemPath);
            }
        }
    }

    private static void validateEvidenceContentConsistency(Attributes dataset, ValidationResult result,
                                                          String modulePath, AbstractIODValidator ctx) {
        @SuppressWarnings("unused")
        AbstractIODValidator ignoredCtx = ctx;

        // First check if Evidence sequence exists
        Sequence evidenceSeq = dataset.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence);
        if (evidenceSeq == null || evidenceSeq.isEmpty()) {
            result.addError(ValidationMessages.EVIDENCE_SEQUENCE_MISSING + " " +
                          ValidationMessages.ORPHAN_REFERENCES_DETECTED, modulePath);
            return;
        }

        Sequence contentSeq = dataset.getSequence(Tag.ContentSequence);
        if (contentSeq == null || contentSeq.isEmpty()) {
            result.addError(ValidationMessages.MADO_CONTENT_SEQUENCE_MISSING, modulePath);
            return;
        }

        // Collect referenced SOP Instance UIDs from Content
        java.util.Set<String> contentRefs = MADOContentUtils.collectReferencedInstancesFromContent(dataset);

        // Collect from Evidence
        java.util.Set<String> evidenceRefs = SRReferenceUtils.collectReferencedSOPInstanceUIDsFromEvidence(dataset);

        if (contentRefs.isEmpty() && evidenceRefs.isEmpty()) {
            result.addError(ValidationMessages.MADO_NO_INSTANCE_REFERENCES, modulePath);
            return;
        }

        // Cross-check consistency: Every instance in Content MUST be in Evidence
        for (String uid : contentRefs) {
            if (!evidenceRefs.contains(uid)) {
                result.addError(String.format(ValidationMessages.MADO_CONTENT_MISSING_FROM_EVIDENCE, uid), modulePath);
            }
        }

        // Warn if Evidence has instances not in Content (less critical but worth noting)
        for (String uid : evidenceRefs) {
            if (!contentRefs.contains(uid)) {
                result.addWarning("SOP Instance UID present in Evidence but not in Content tree: " + uid + ". " +
                                "This is allowed but unusual - typically Content and Evidence should match.", modulePath);
            }
        }
    }
}
