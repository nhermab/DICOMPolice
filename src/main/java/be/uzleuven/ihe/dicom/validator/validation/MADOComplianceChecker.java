package be.uzleuven.ihe.dicom.validator.validation;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import be.uzleuven.ihe.dicom.validator.model.ValidationResult;
import be.uzleuven.ihe.dicom.constants.ValidationMessages;

/**
 * MADO Compliance Checker - validates the critical requirements based on IHE MADO Profile.
 *
 * This validator performs a high-level compliance check focusing on the most common
 * issues that cause MADO KOS objects to be rejected:
 *
 * 1. Missing Evidence Sequence (CurrentRequestedProcedureEvidenceSequence)
 * 2. Incorrect Content Structure (flat list vs TID 1600 hierarchy)
 * 3. Wrong Document Title (generic KOS title vs MADO "Manifest")
 * 4. Missing mandatory header attributes (Timezone, Institution, Manufacturer)
 */
public final class MADOComplianceChecker {

    private MADOComplianceChecker() {
    }

    /**
     * Perform a comprehensive MADO compliance check.
     * This is a high-level validation that checks the most critical requirements.
     */
    public static void checkMADOCompliance(Attributes dataset, ValidationResult result, boolean verbose) {
        String modulePath = "MADOCompliance";

        if (verbose) {
            result.addInfo("=== MADO Compliance Check ===", modulePath);
            result.addInfo("Checking critical MADO requirements based on IHE MADO Profile", modulePath);
        }

        // Check 1: Evidence Sequence (CRITICAL)
        checkEvidenceSequence(dataset, result, modulePath, verbose);

        // Check 2: TID 1600 Structure
        checkTID1600Structure(dataset, result, modulePath, verbose);

        // Check 3: Document Title
        checkDocumentTitle(dataset, result, modulePath, verbose);

        // Check 4: Mandatory Header Attributes
        checkMandatoryHeaderAttributes(dataset, result, modulePath, verbose);

        // Check 5: Value Types in Content
        checkContentValueTypes(dataset, result, modulePath, verbose);

        if (verbose) {
            result.addInfo("=== End MADO Compliance Check ===", modulePath);
        }
    }

    /**
     * Check 1: Evidence Sequence - CRITICAL
     * Every instance referenced in the content tree must also be listed in the Evidence Sequence.
     */
    private static void checkEvidenceSequence(Attributes dataset, ValidationResult result,
                                             String modulePath, boolean verbose) {
        if (verbose) {
            result.addInfo("Check 1: Verifying Evidence Sequence (CurrentRequestedProcedureEvidenceSequence)", modulePath);
        }

        Sequence evidenceSeq = dataset.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence);

        if (evidenceSeq == null) {
            result.addError("CRITICAL MADO COMPLIANCE FAILURE:\n" +
                          "  CurrentRequestedProcedureEvidenceSequence (0040,A375) is MISSING.\n" +
                          "  Impact: PACS/Archive will reject this KOS object.\n" +
                          "  Fix: Add Evidence Sequence listing all referenced Study/Series/Instance UIDs.\n" +
                          "  See: DICOM PS3.3 C.17.6.2.1 Key Object Document Module", modulePath);
            return;
        }

        if (evidenceSeq.isEmpty()) {
            result.addError("CRITICAL MADO COMPLIANCE FAILURE:\n" +
                          "  CurrentRequestedProcedureEvidenceSequence is present but EMPTY.\n" +
                          "  Impact: KOS claims 'no evidence' for this procedure.\n" +
                          "  Fix: Populate Evidence Sequence with referenced instances.", modulePath);
            return;
        }

        // Validate structure
        int studyCount = evidenceSeq.size();
        int totalSeriesCount = 0;
        int totalInstanceCount = 0;

        for (Attributes studyItem : evidenceSeq) {
            String studyUID = studyItem.getString(Tag.StudyInstanceUID);
            Sequence seriesSeq = studyItem.getSequence(Tag.ReferencedSeriesSequence);

            if (studyUID == null || studyUID.trim().isEmpty()) {
                result.addError(ValidationMessages.STUDY_UID_MISSING_EVIDENCE, modulePath);
            }

            if (seriesSeq != null) {
                totalSeriesCount += seriesSeq.size();

                for (Attributes seriesItem : seriesSeq) {
                    String seriesUID = seriesItem.getString(Tag.SeriesInstanceUID);
                    Sequence sopSeq = seriesItem.getSequence(Tag.ReferencedSOPSequence);

                    if (seriesUID == null || seriesUID.trim().isEmpty()) {
                        result.addError(String.format(ValidationMessages.SERIES_HIERARCHY_ERROR, "SeriesInstanceUID missing in series item"), modulePath);
                    }

                    if (sopSeq != null) {
                        totalInstanceCount += sopSeq.size();
                    } else {
                        result.addError(String.format(ValidationMessages.SERIES_HIERARCHY_ERROR, "ReferencedSOPSequence missing in series"), modulePath);
                    }
                }
            } else {
                result.addError(String.format(ValidationMessages.SERIES_HIERARCHY_ERROR, "ReferencedSeriesSequence missing in study"), modulePath);
            }
        }

        if (verbose) {
            result.addInfo("Evidence Sequence: Found " + studyCount + " study(ies), " +
                         totalSeriesCount + " series, " + totalInstanceCount + " instance(s)", modulePath);
        }
    }

    /**
     * Check 2: TID 1600 Structure
     * MADO requires hierarchical structure: Library -> Group -> Entry
     */
    private static void checkTID1600Structure(Attributes dataset, ValidationResult result,
                                             String modulePath, boolean verbose) {
        if (verbose) {
            result.addInfo("Check 2: Verifying TID 1600 Image Library structure", modulePath);
        }

        Sequence contentSeq = dataset.getSequence(Tag.ContentSequence);

        if (contentSeq == null || contentSeq.isEmpty()) {
            result.addError("MADO COMPLIANCE FAILURE:\n" +
                          "  ContentSequence is missing/empty.\n" +
                          "  Expected: TID 1600 Image Library with hierarchical structure.\n" +
                          "  Fix: Create proper content tree with Library -> Groups -> Entries.", modulePath);
            return;
        }

        // Look for Image Library container (111028, DCM)
        boolean hasImageLibrary = false;
        boolean hasOnlyFlatCompositeReferences = true;

        for (Attributes item : contentSeq) {
            String valueType = item.getString(Tag.ValueType);

            if ("CONTAINER".equals(valueType)) {
                hasOnlyFlatCompositeReferences = false;

                Sequence conceptSeq = item.getSequence(Tag.ConceptNameCodeSequence);
                if (conceptSeq != null && !conceptSeq.isEmpty()) {
                    Attributes concept = conceptSeq.get(0);
                    String codeValue = concept.getString(Tag.CodeValue);
                    String codingScheme = concept.getString(Tag.CodingSchemeDesignator);

                    if ("111028".equals(codeValue) && "DCM".equals(codingScheme)) {
                        hasImageLibrary = true;
                        if (verbose) {
                            result.addInfo("Found TID 1600 Image Library (111028, DCM, 'Image Library')", modulePath);
                        }
                    }
                }
            }
        }

        if (!hasImageLibrary) {
            if (hasOnlyFlatCompositeReferences) {
                result.addError("MADO COMPLIANCE FAILURE:\n" +
                              "  ContentSequence has flat list of COMPOSITE/IMAGE references.\n" +
                              "  Expected: TID 1600 hierarchical structure with Image Library container (111028, DCM).\n" +
                              "  Note: This is standard KOS structure, NOT valid MADO.\n" +
                              "  Fix: Restructure content as: Root -> Image Library -> Groups -> Entries.", modulePath);
            } else {
                result.addError("MADO COMPLIANCE FAILURE:\n" +
                              "  ContentSequence contains CONTAINER items but no Image Library (111028, DCM).\n" +
                              "  Expected: TID 1600 Image Library structure.\n" +
                              "  Fix: Add Image Library container and organize content hierarchically.", modulePath);
            }
        }
    }

    /**
     * Check 3: Document Title
     * MADO requires "Manifest" (113030, DCM) or "Manifest with Description" (ddd001, DCM).
     */
    private static void checkDocumentTitle(Attributes dataset, ValidationResult result,
                                          String modulePath, boolean verbose) {
        if (verbose) {
            result.addInfo("Check 3: Verifying Document Title", modulePath);
        }

        Sequence conceptNameSeq = dataset.getSequence(Tag.ConceptNameCodeSequence);

        if (conceptNameSeq == null || conceptNameSeq.isEmpty()) {
            result.addError(ValidationMessages.CONCEPT_NAME_MISSING, modulePath);
            return;
        }

        Attributes concept = conceptNameSeq.get(0);
        String codeValue = concept.getString(Tag.CodeValue);
        String codingScheme = concept.getString(Tag.CodingSchemeDesignator);
        String codeMeaning = concept.getString(Tag.CodeMeaning);

        // Check for valid MADO titles
        boolean isManifest = "113030".equals(codeValue) && "DCM".equals(codingScheme);
        boolean isManifestWithDesc = "ddd001".equals(codeValue) && "DCM".equals(codingScheme);

        if (!isManifest && !isManifestWithDesc) {
            // Check if it's a generic KOS title
            if ("113000".equals(codeValue) && "DCM".equals(codingScheme)) {
                result.addError("MADO COMPLIANCE FAILURE:\n" +
                              "  Document Title is (113000, DCM, 'Of Interest') - generic KOS title.\n" +
                              "  MADO requires:\n" +
                              "    (113030, DCM, 'Manifest') OR\n" +
                              "    (ddd001, DCM, 'Manifest with Description')\n" +
                              "  Fix: Change ConceptNameCodeSequence to use MADO manifest title.", modulePath);
            } else {
                result.addError("MADO COMPLIANCE FAILURE:\n" +
                              "  Document Title: (" + codeValue + ", " + codingScheme + ", '" + codeMeaning + "')\n" +
                              "  MADO requires:\n" +
                              "    (113030, DCM, 'Manifest') OR\n" +
                              "    (ddd001, DCM, 'Manifest with Description')\n" +
                              "  Fix: Change ConceptNameCodeSequence to use MADO manifest title.", modulePath);
            }
        } else if (verbose) {
            result.addInfo("Document Title is valid for MADO: (" + codeValue + ", " +
                         codingScheme + ", '" + codeMeaning + "')", modulePath);
        }
    }

    /**
     * Check 4: Mandatory Header Attributes
     * MADO requires specific header attributes that are Type 3 in standard DICOM.
     */
    private static void checkMandatoryHeaderAttributes(Attributes dataset, ValidationResult result,
                                                      String modulePath, boolean verbose) {
        if (verbose) {
            result.addInfo("Check 4: Verifying mandatory MADO header attributes", modulePath);
        }

        // Timezone Offset From UTC (0008,0201) - MANDATORY in MADO
        String timezone = dataset.getString(Tag.TimezoneOffsetFromUTC);
        if (timezone == null || timezone.trim().isEmpty()) {
            result.addError("MADO COMPLIANCE FAILURE:\n" +
                          "  TimezoneOffsetFromUTC (0008,0201) is MISSING or empty.\n" +
                          "  Impact: Time consistency issues across sharing infrastructure.\n" +
                          "  Fix: Add timezone offset (e.g., '+0100', '-0500', '+0000' for UTC).\n" +
                          "  Note: Required by MADO to ensure time consistency.", modulePath);
        } else if (verbose) {
            result.addInfo("Timezone Offset: " + timezone, modulePath);
        }

        // Institution Name (0008,0080) - MANDATORY in MADO
        String institutionName = dataset.getString(Tag.InstitutionName);
        if (institutionName == null || institutionName.trim().isEmpty()) {
            result.addError("MADO COMPLIANCE FAILURE:\n" +
                          "  InstitutionName (0008,0080) is MISSING or empty.\n" +
                          "  Impact: Cannot trace manifest creator.\n" +
                          "  Fix: Add institution name.\n" +
                          "  Note: Required by MADO for provenance tracking.", modulePath);
        } else if (verbose) {
            result.addInfo("Institution Name: " + institutionName, modulePath);
        }

        // Manufacturer (0008,0070) - MANDATORY in MADO
        String manufacturer = dataset.getString(Tag.Manufacturer);
        if (manufacturer == null || manufacturer.trim().isEmpty()) {
            result.addError("MADO COMPLIANCE FAILURE:\n" +
                          "  Manufacturer (0008,0070) is MISSING or empty.\n" +
                          "  Impact: Cannot identify manifest creation software.\n" +
                          "  Fix: Add manufacturer/software identifier.\n" +
                          "  Note: Required by MADO for troubleshooting.", modulePath);
        } else if (verbose) {
            result.addInfo("Manufacturer: " + manufacturer, modulePath);
        }

        // Issuer of Patient ID Qualifiers Sequence (0010,0024) - MANDATORY in MADO
        Sequence qualifiers = dataset.getSequence(Tag.IssuerOfPatientIDQualifiersSequence);
        if (qualifiers == null || qualifiers.isEmpty()) {
            result.addError("MADO COMPLIANCE FAILURE:\n" +
                          "  IssuerOfPatientIDQualifiersSequence (0010,0024) is MISSING or empty.\n" +
                          "  Impact: Patient ID not globally unique.\n" +
                          "  Fix: Add issuer qualifiers with Universal Entity ID (OID).\n" +
                          "  Note: Required by MADO for cross-domain patient identification.", modulePath);
        } else if (verbose) {
            Attributes first = qualifiers.get(0);
            String universalEntityId = first.getString(Tag.UniversalEntityID);
            result.addInfo("Patient ID Issuer: " + universalEntityId, modulePath);
        }
    }

    /**
     * Check 5: Content Value Types
     * MADO prefers IMAGE over COMPOSITE for image references.
     */
    private static void checkContentValueTypes(Attributes dataset, ValidationResult result,
                                               String modulePath, boolean verbose) {
        if (verbose) {
            result.addInfo("Check 5: Checking ContentSequence value types", modulePath);
        }

        Sequence contentSeq = dataset.getSequence(Tag.ContentSequence);
        if (contentSeq == null || contentSeq.isEmpty()) {
            return; // Already reported in Check 2
        }

        int compositeCount = countValueTypes(contentSeq, "COMPOSITE");
        int imageCount = countValueTypes(contentSeq, "IMAGE");

        if (compositeCount > 0 && imageCount == 0) {
            result.addWarning("MADO RECOMMENDATION:\n" +
                            "  ContentSequence uses ValueType 'COMPOSITE' for image references.\n" +
                            "  MADO profile recommends 'IMAGE' for better type specificity.\n" +
                            "  Found " + compositeCount + " COMPOSITE reference(s).\n" +
                            "  Note: This is acceptable but 'IMAGE' is preferred.", modulePath);
        } else if (verbose && imageCount > 0) {
            result.addInfo("Content uses IMAGE value type (" + imageCount + " references) - preferred for MADO", modulePath);
        }

        // Avoid "KOS appears empty" style false positives by ensuring we actually have references somewhere in the tree.
        // MADO places most references under the Image Library container, not necessarily at root.
        int totalReferences = imageCount + compositeCount + countValueTypes(contentSeq, "WAVEFORM");
        if (totalReferences == 0) {
            result.addWarning("MADO content tree contains no IMAGE/COMPOSITE/WAVEFORM reference items. " +
                    "This manifest may be empty.", modulePath);
        }
    }

    /**
     * Count value types recursively in content sequence.
     */
    private static int countValueTypes(Sequence contentSeq, String targetType) {
        int count = 0;

        for (Attributes item : contentSeq) {
            String valueType = item.getString(Tag.ValueType);
            if (targetType.equals(valueType)) {
                count++;
            }

            // Recurse into nested content
            Sequence nestedContent = item.getSequence(Tag.ContentSequence);
            if (nestedContent != null) {
                count += countValueTypes(nestedContent, targetType);
            }
        }

        return count;
    }
}

