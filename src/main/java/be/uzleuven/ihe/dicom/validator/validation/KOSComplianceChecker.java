package be.uzleuven.ihe.dicom.validator.validation;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import be.uzleuven.ihe.dicom.validator.model.ValidationResult;
import be.uzleuven.ihe.dicom.constants.ValidationMessages;

/**
 * KOS Compliance Checker - validates critical requirements for standard KOS objects.
 * This validator ensures that KOS objects meet the fundamental DICOM requirements,
 * particularly the Evidence Sequence which is often overlooked but critical.
 */
public final class KOSComplianceChecker {

    private KOSComplianceChecker() {
    }

    /**
     * Perform a KOS compliance check focusing on critical requirements.
     */
    public static void checkKOSCompliance(Attributes dataset, ValidationResult result, boolean verbose) {
        String modulePath = "KOSCompliance";

        if (verbose) {
            result.addInfo("=== KOS Compliance Check ===", modulePath);
            result.addInfo("Checking critical KOS requirements per DICOM PS3.3 Key Object Selection", modulePath);
        }

        // Check 1: Evidence Sequence (CRITICAL)
        checkEvidenceSequence(dataset, result, modulePath, verbose);

        // Check 2: Content Sequence
        checkContentSequence(dataset, result, modulePath, verbose);

        // Check 3: Document Title
        checkDocumentTitle(dataset, result, modulePath, verbose);

        if (verbose) {
            result.addInfo("=== End KOS Compliance Check ===", modulePath);
        }
    }

    /**
     * Check 1: Evidence Sequence - CRITICAL for KOS
     */
    private static void checkEvidenceSequence(Attributes dataset, ValidationResult result,
                                             String modulePath, boolean verbose) {
        if (verbose) {
            result.addInfo("Check 1: Verifying Evidence Sequence (CurrentRequestedProcedureEvidenceSequence)", modulePath);
        }

        Sequence evidenceSeq = dataset.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence);

        if (evidenceSeq == null) {
            result.addError("CRITICAL KOS COMPLIANCE FAILURE:\n" + "  CurrentRequestedProcedureEvidenceSequence (0040,A375) is MISSING.\n" + "  Impact: PACS/Archive will reject this KOS object.\n" + "  Requirement: DICOM PS3.3 C.17.6.2.1 - Type 1C (required when instances are referenced).\n" + "  Fix: Add Evidence Sequence listing all referenced Study/Series/Instance UIDs.", modulePath);
            return;
        }

        if (evidenceSeq.isEmpty()) {
            result.addError("CRITICAL KOS COMPLIANCE FAILURE:\n" + "  CurrentRequestedProcedureEvidenceSequence is present but EMPTY.\n" + "  Impact: KOS claims 'no evidence' - will be rejected or misinterpreted.\n" + "  Fix: Populate Evidence Sequence with all referenced instances.", modulePath);
            return;
        }

        // Validate basic structure
        int studyCount = evidenceSeq.size();
        int totalSeriesCount = 0;
        int totalInstanceCount = 0;

        for (Attributes studyItem : evidenceSeq) {
            String studyUID = studyItem.getString(Tag.StudyInstanceUID);
            Sequence seriesSeq = studyItem.getSequence(Tag.ReferencedSeriesSequence);

            if (studyUID == null || studyUID.trim().isEmpty()) {
                result.addError(ValidationMessages.STUDY_UID_MISSING_EVIDENCE, modulePath);
            }

            if (seriesSeq != null && !seriesSeq.isEmpty()) {
                totalSeriesCount += seriesSeq.size();

                for (Attributes seriesItem : seriesSeq) {
                    String seriesUID = seriesItem.getString(Tag.SeriesInstanceUID);
                    Sequence sopSeq = seriesItem.getSequence(Tag.ReferencedSOPSequence);

                    if (seriesUID == null || seriesUID.trim().isEmpty()) {
                        result.addError(String.format(ValidationMessages.SERIES_HIERARCHY_ERROR, "SeriesInstanceUID missing"), modulePath);
                    }

                    if (sopSeq != null && !sopSeq.isEmpty()) {
                        totalInstanceCount += sopSeq.size();
                    } else {
                        result.addError(String.format(ValidationMessages.SERIES_HIERARCHY_ERROR, "ReferencedSOPSequence missing or empty"), modulePath);
                    }
                }
            } else {
                result.addError(String.format(ValidationMessages.SERIES_HIERARCHY_ERROR, "ReferencedSeriesSequence missing or empty"), modulePath);
            }
        }

        if (verbose) {
            result.addInfo("Evidence Sequence: " + studyCount + " study(ies), " +
                         totalSeriesCount + " series, " + totalInstanceCount + " instance(s)", modulePath);
        }
    }

    /**
     * Check 2: Content Sequence
     */
    private static void checkContentSequence(Attributes dataset, ValidationResult result,
                                            String modulePath, boolean verbose) {
        if (verbose) {
            result.addInfo("Check 2: Verifying ContentSequence structure", modulePath);
        }

        Sequence contentSeq = dataset.getSequence(Tag.ContentSequence);

        if (contentSeq == null || contentSeq.isEmpty()) {
            result.addWarning("ContentSequence is missing or empty. " +
                            "KOS should contain at least one referenced object in content tree.", modulePath);
            return;
        }

        // Count reference items
        int imageCount = 0;
        int compositeCount = 0;
        int waveformCount = 0;

        for (Attributes item : contentSeq) {
            String valueType = item.getString(Tag.ValueType);
            if ("IMAGE".equals(valueType)) {
                imageCount++;
            } else if ("COMPOSITE".equals(valueType)) {
                compositeCount++;
            } else if ("WAVEFORM".equals(valueType)) {
                waveformCount++;
            }
        }

        int totalRefs = imageCount + compositeCount + waveformCount;

        if (totalRefs == 0) {
            result.addWarning("ContentSequence contains no IMAGE/COMPOSITE/WAVEFORM references. " +
                            "KOS appears to be empty.", modulePath);
        } else if (verbose) {
            result.addInfo("ContentSequence: " + imageCount + " IMAGE, " + compositeCount +
                         " COMPOSITE, " + waveformCount + " WAVEFORM reference(s)", modulePath);
        }
    }

    /**
     * Check 3: Document Title
     */
    private static void checkDocumentTitle(Attributes dataset, ValidationResult result,
                                          String modulePath, boolean verbose) {
        if (verbose) {
            result.addInfo("Check 3: Verifying Document Title (ConceptNameCodeSequence)", modulePath);
        }

        Sequence conceptSeq = dataset.getSequence(Tag.ConceptNameCodeSequence);

        if (conceptSeq == null || conceptSeq.isEmpty()) {
            result.addError("ConceptNameCodeSequence is missing - document title is required", modulePath);
            return;
        }

        Attributes concept = conceptSeq.get(0);
        String codeValue = concept.getString(Tag.CodeValue);
        String codingScheme = concept.getString(Tag.CodingSchemeDesignator);
        String codeMeaning = concept.getString(Tag.CodeMeaning);

        if (codeValue == null || codeValue.trim().isEmpty()) {
            result.addError("Document Title: CodeValue is missing", modulePath);
        }
        if (codingScheme == null || codingScheme.trim().isEmpty()) {
            result.addError("Document Title: CodingSchemeDesignator is missing", modulePath);
        }
        if (codeMeaning == null || codeMeaning.trim().isEmpty()) {
            result.addError("Document Title: CodeMeaning is missing", modulePath);
        }

        if (verbose && codeValue != null) {
            result.addInfo("Document Title: (" + codeValue + ", " + codingScheme + ", '" + codeMeaning + "')", modulePath);
        }
    }
}

