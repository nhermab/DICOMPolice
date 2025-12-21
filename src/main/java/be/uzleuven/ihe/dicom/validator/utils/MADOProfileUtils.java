package be.uzleuven.ihe.dicom.validator.utils;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import be.uzleuven.ihe.dicom.validator.validation.iod.AbstractIODValidator;
import be.uzleuven.ihe.dicom.validator.model.ValidationResult;
import be.uzleuven.ihe.dicom.validator.validation.MADORetrievalValidator;
import be.uzleuven.ihe.dicom.validator.validation.MADOTemplateValidator;
import be.uzleuven.ihe.dicom.validator.validation.MADOTimezoneValidator;

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
            result.addError("MADO Format: Appendix B style attributes detected, but Approach 2 (TID 1600 Image Library) is required for this manifest.", modulePath);
        } else {
            result.addError("MADO Format: Unable to identify Approach 2 (TID 1600 Image Library). " +
                    "This KOS does not meet MADO KOS-Based Imaging Study Manifest requirements.", modulePath);
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

    /**
     * MADO approach enumeration.
     */
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
        // Parameters result, modulePath, verbose reserved for future enhanced detection logging

        // Check for TID 1600 Image Library in ContentSequence
        Sequence contentSeq = dataset.getSequence(Tag.ContentSequence);
        if (contentSeq != null && !contentSeq.isEmpty()) {
            if (hasImageLibrary(contentSeq)) {
                return MADOApproach.APPROACH_2_TID_1600;
            }
        }

        // Check for Appendix B extended attributes in Evidence
        Sequence evidenceSeq = dataset.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence);
        if (evidenceSeq != null && !evidenceSeq.isEmpty()) {
            if (hasAppendixBExtensions(evidenceSeq)) {
                return MADOApproach.APPENDIX_B;
            }
        }

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
        // This scans the content tree and checks for self-references and duplicates
        SRReferenceUtils.scanSRReferencesWithChecks(dataset, result, modulePath, ctx);

        // Check for IOCM rejection note titles (prohibited in sharing context)
        Sequence conceptSeq = dataset.getSequence(Tag.ConceptNameCodeSequence);
        if (conceptSeq != null && !conceptSeq.isEmpty()) {
            Attributes item = conceptSeq.get(0);
            String codeValue = item.getString(Tag.CodeValue);

            if (XDSIManifestProfileUtils.isForbiddenIOCMTitle(codeValue)) {
                result.addError("Document title code indicates an IOCM Rejection Note (CodeValue=" + codeValue +
                              "); this is not allowed for a MADO sharing manifest", modulePath);
            }
        }
    }

    private static void validateReferencedRequestSequence(Attributes dataset, ValidationResult result, String modulePath) {
        Sequence refRequestSeq = dataset.getSequence(Tag.ReferencedRequestSequence);
        if (refRequestSeq == null || refRequestSeq.isEmpty()) {
            result.addError("ReferencedRequestSequence (0040,A370) is missing/empty. MADO requires request information " +
                    "for each unique Accession Number/Placer Order combination.", modulePath);
            return;
        }

        // Validate each request item
        for (int i = 0; i < refRequestSeq.size(); i++) {
            Attributes item = refRequestSeq.get(i);
            String itemPath = modulePath + ".ReferencedRequestSequence[" + i + "]";

            String studyUID = item.getString(Tag.StudyInstanceUID);
            String accessionNumber = item.getString(Tag.AccessionNumber);
            String placerOrderNumber = item.getString(Tag.PlacerOrderNumberImagingServiceRequest);

            if (studyUID == null || studyUID.trim().isEmpty()) {
                result.addError("StudyInstanceUID missing/empty in ReferencedRequestSequence item " + i, itemPath);
            }

            if (accessionNumber == null || accessionNumber.trim().isEmpty()) {
                result.addError("AccessionNumber missing/empty in ReferencedRequestSequence item " + i, itemPath);
            } else {
                // If Accession Number present, Issuer SHALL be present
                if (!item.contains(Tag.IssuerOfAccessionNumberSequence)) {
                    result.addError("IssuerOfAccessionNumberSequence (0008,0051) missing in ReferencedRequestSequence item " + i,
                            itemPath);
                }
            }

            if (placerOrderNumber == null || placerOrderNumber.trim().isEmpty()) {
                result.addError("PlacerOrderNumber (0040,2016) missing/empty in ReferencedRequestSequence item " + i, itemPath);
            }
        }
    }

    private static void validateEvidenceContentConsistency(Attributes dataset, ValidationResult result,
                                                          String modulePath, AbstractIODValidator ctx) {
        // Parameter ctx reserved for future cross-validator functionality

        Sequence contentSeq = dataset.getSequence(Tag.ContentSequence);
        if (contentSeq == null || contentSeq.isEmpty()) {
            result.addError("ContentSequence is missing/empty. MADO manifest must contain TID 1600 Image Library.", modulePath);
            return;
        }

        // Collect referenced SOP Instance UIDs from Content
        java.util.Set<String> contentRefs = MADOContentUtils.collectReferencedInstancesFromContent(dataset);

        // Collect from Evidence
        java.util.Set<String> evidenceRefs = SRReferenceUtils.collectReferencedSOPInstanceUIDsFromEvidence(dataset);

        if (contentRefs.isEmpty() && evidenceRefs.isEmpty()) {
            result.addError("MADO manifest does not reference any instances (no content refs and no evidence refs)", modulePath);
            return;
        }

        // Cross-check consistency
        for (String uid : contentRefs) {
            if (!evidenceRefs.contains(uid)) {
                result.addError("SOP Instance UID present in Content but missing from Evidence: " + uid, modulePath);
            }
        }

        for (String uid : evidenceRefs) {
            if (!contentRefs.contains(uid)) {
                result.addWarning("SOP Instance UID present in Evidence but not in Content tree: " + uid, modulePath);
            }
        }
    }
}
