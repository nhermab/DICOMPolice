package be.uzleuven.ihe.dicom.convertor.dicom;

import be.uzleuven.ihe.dicom.convertor.fhir.MADOToFHIRConverter;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;
import org.hl7.fhir.r5.model.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static be.uzleuven.ihe.dicom.creator.utils.DicomCreatorUtils.*;

/**
 * Tests round-trip conversion: DICOM MADO -> FHIR MADO -> DICOM MADO.
 *
 * This class verifies that the conversion process preserves critical information
 * when converting back and forth between DICOM and FHIR formats.
 *
 * Usage:
 *   java FHIRToMADORoundTripTest <input.dcm> [output.dcm]
 */
public class FHIRToMADORoundTripTest {

    private final MADOToFHIRConverter toFhir = new MADOToFHIRConverter();
    private final FHIRToMADOConverter toDicom = new FHIRToMADOConverter();

    // Tracking comparison results
    private List<String> matches = new ArrayList<>();
    private List<String> mismatches = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();

    /**
     * Performs round-trip conversion and comparison.
     *
     * @param inputDicomFile Original DICOM MADO file
     * @return ComparisonResult with detailed findings
     */
    public ComparisonResult testRoundTrip(File inputDicomFile) throws IOException {
        // Step 1: Read original DICOM
        Attributes original;
        try (DicomInputStream dis = new DicomInputStream(inputDicomFile)) {
            original = dis.readDataset();
        }

        // Step 2: Convert to FHIR
        Bundle fhirBundle = toFhir.convert(original);

        // Step 3: Convert back to DICOM
        Attributes roundTripped = toDicom.convert(fhirBundle);

        // Step 4: Compare
        compareAttributes(original, roundTripped);

        return new ComparisonResult(matches, mismatches, warnings);
    }

    /**
     * Performs round-trip and saves the result.
     */
    public ComparisonResult testRoundTripAndSave(File inputDicomFile, File outputDicomFile)
            throws IOException {
        // Step 1: Read original DICOM
        Attributes original;
        try (DicomInputStream dis = new DicomInputStream(inputDicomFile)) {
            original = dis.readDataset();
        }

        // Step 2: Convert to FHIR
        Bundle fhirBundle = toFhir.convert(original);

        // Step 3: Convert back to DICOM
        Attributes roundTripped = toDicom.convert(fhirBundle);

        // Step 4: Save
        writeDicomFile(outputDicomFile, roundTripped);

        // Step 5: Compare
        compareAttributes(original, roundTripped);

        return new ComparisonResult(matches, mismatches, warnings);
    }

    // ============================================================================
    // COMPARISON LOGIC
    // ============================================================================

    private void compareAttributes(Attributes original, Attributes roundTripped) {
        // Patient Module
        compareTag("Patient ID", original, roundTripped, Tag.PatientID);
        compareTag("Patient Name", original, roundTripped, Tag.PatientName);
        compareTag("Patient Birth Date", original, roundTripped, Tag.PatientBirthDate);
        compareTag("Patient Sex", original, roundTripped, Tag.PatientSex);
        compareTag("Issuer of Patient ID", original, roundTripped, Tag.IssuerOfPatientID);
        compareTag("Type of Patient ID", original, roundTripped, Tag.TypeOfPatientID);
        compareIssuerOfPatientIDQualifiers(original, roundTripped);

        // Study Module
        compareTag("Study Instance UID", original, roundTripped, Tag.StudyInstanceUID);
        compareTag("Study Date", original, roundTripped, Tag.StudyDate);
        compareTag("Study Time", original, roundTripped, Tag.StudyTime);
        compareTag("Study Description", original, roundTripped, Tag.StudyDescription);
        compareTag("Study ID", original, roundTripped, Tag.StudyID);
        compareTag("Accession Number", original, roundTripped, Tag.AccessionNumber);
        compareTag("Referring Physician Name", original, roundTripped, Tag.ReferringPhysicianName);
        compareIssuerOfAccessionNumber(original, roundTripped);

        // Series Module (manifest series)
        compareTag("Series Date", original, roundTripped, Tag.SeriesDate);
        compareTag("Series Time", original, roundTripped, Tag.SeriesTime);
        noteNewValue("Series Instance UID", original, roundTripped, Tag.SeriesInstanceUID);

        // SR Document Module
        compareTag("Content Date", original, roundTripped, Tag.ContentDate);
        compareTag("Content Time", original, roundTripped, Tag.ContentTime);

        // Manifest identity - SOP Instance UID will differ (new manifest)
        noteNewValue("SOP Instance UID", original, roundTripped, Tag.SOPInstanceUID);
        compareTag("SOP Class UID", original, roundTripped, Tag.SOPClassUID);

        // Equipment Module
        compareTag("Manufacturer", original, roundTripped, Tag.Manufacturer);
        compareTag("Manufacturer Model Name", original, roundTripped, Tag.ManufacturerModelName);
        compareTag("Software Versions", original, roundTripped, Tag.SoftwareVersions);
        compareTag("Institution Name", original, roundTripped, Tag.InstitutionName);

        // Evidence Sequence
        compareEvidenceSequence(original, roundTripped);

        // Referenced Request Sequence
        compareReferencedRequestSequence(original, roundTripped);

        // Content Sequence (TID 1600)
        compareContentSequence(original, roundTripped);
    }

    private void compareTag(String name, Attributes original, Attributes roundTripped, int tag) {
        String origValue = original.getString(tag);
        String rtValue = roundTripped.getString(tag);

        if (Objects.equals(origValue, rtValue)) {
            matches.add(name + ": " + origValue);
        } else if (origValue == null && rtValue != null) {
            warnings.add(name + ": NEW VALUE = " + rtValue + " (original was null)");
        } else if (origValue != null && rtValue == null) {
            mismatches.add(name + ": LOST VALUE = " + origValue);
        } else {
            // Check for semantically equivalent values
            if (isEquivalent(origValue, rtValue)) {
                matches.add(name + ": " + origValue + " ≈ " + rtValue + " (equivalent)");
            } else {
                mismatches.add(name + ": " + origValue + " -> " + rtValue);
            }
        }
    }

    private void noteNewValue(String name, Attributes original, Attributes roundTripped, int tag) {
        String origValue = original.getString(tag);
        String rtValue = roundTripped.getString(tag);

        if (Objects.equals(origValue, rtValue)) {
            matches.add(name + ": " + origValue + " (unchanged)");
        } else {
            warnings.add(name + ": " + origValue + " -> " + rtValue + " (expected to change)");
        }
    }

    private boolean isEquivalent(String v1, String v2) {
        if (v1 == null || v2 == null) return false;

        // Normalize whitespace
        String n1 = v1.trim();
        String n2 = v2.trim();

        if (n1.equals(n2)) return true;

        // Check DICOM name equivalence (ignore trailing ^)
        if (n1.replaceAll("\\^+$", "").equals(n2.replaceAll("\\^+$", ""))) {
            return true;
        }

        return false;
    }

    private void compareIssuerOfPatientIDQualifiers(Attributes original, Attributes roundTripped) {
        Sequence origQualSeq = original.getSequence(Tag.IssuerOfPatientIDQualifiersSequence);
        Sequence rtQualSeq = roundTripped.getSequence(Tag.IssuerOfPatientIDQualifiersSequence);

        String origOid = null;
        String rtOid = null;

        if (origQualSeq != null && !origQualSeq.isEmpty()) {
            origOid = origQualSeq.get(0).getString(Tag.UniversalEntityID);
        }
        if (rtQualSeq != null && !rtQualSeq.isEmpty()) {
            rtOid = rtQualSeq.get(0).getString(Tag.UniversalEntityID);
        }

        if (Objects.equals(origOid, rtOid)) {
            matches.add("Issuer of Patient ID OID: " + origOid);
        } else if (origOid == null && rtOid != null) {
            warnings.add("Issuer of Patient ID OID: NEW = " + rtOid);
        } else if (origOid != null && rtOid == null) {
            mismatches.add("Issuer of Patient ID OID: LOST = " + origOid);
        } else {
            mismatches.add("Issuer of Patient ID OID: " + origOid + " -> " + rtOid);
        }
    }

    private void compareIssuerOfAccessionNumber(Attributes original, Attributes roundTripped) {
        Sequence origSeq = original.getSequence(Tag.IssuerOfAccessionNumberSequence);
        Sequence rtSeq = roundTripped.getSequence(Tag.IssuerOfAccessionNumberSequence);

        String origOid = null;
        String rtOid = null;

        if (origSeq != null && !origSeq.isEmpty()) {
            origOid = origSeq.get(0).getString(Tag.UniversalEntityID);
        }
        if (rtSeq != null && !rtSeq.isEmpty()) {
            rtOid = rtSeq.get(0).getString(Tag.UniversalEntityID);
        }

        if (Objects.equals(origOid, rtOid)) {
            matches.add("Issuer of Accession Number OID: " + origOid);
        } else if (origOid == null && rtOid != null) {
            warnings.add("Issuer of Accession Number OID: NEW = " + rtOid);
        } else if (origOid != null && rtOid == null) {
            mismatches.add("Issuer of Accession Number OID: LOST = " + origOid);
        } else {
            mismatches.add("Issuer of Accession Number OID: " + origOid + " -> " + rtOid);
        }
    }

    private void compareEvidenceSequence(Attributes original, Attributes roundTripped) {
        Sequence origSeq = original.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence);
        Sequence rtSeq = roundTripped.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence);

        if (origSeq == null && rtSeq == null) {
            warnings.add("Evidence Sequence: both null");
            return;
        }

        if (origSeq == null || rtSeq == null) {
            mismatches.add("Evidence Sequence: " +
                (origSeq == null ? "original null" : "round-trip null"));
            return;
        }

        // Compare Study Instance UIDs
        Set<String> origStudyUIDs = extractStudyUIDsFromEvidence(origSeq);
        Set<String> rtStudyUIDs = extractStudyUIDsFromEvidence(rtSeq);

        if (origStudyUIDs.equals(rtStudyUIDs)) {
            matches.add("Evidence Study UIDs: " + origStudyUIDs);
        } else {
            mismatches.add("Evidence Study UIDs: " + origStudyUIDs + " -> " + rtStudyUIDs);
        }

        // Compare Series UIDs
        Set<String> origSeriesUIDs = extractSeriesUIDsFromEvidence(origSeq);
        Set<String> rtSeriesUIDs = extractSeriesUIDsFromEvidence(rtSeq);

        if (origSeriesUIDs.equals(rtSeriesUIDs)) {
            matches.add("Evidence Series UIDs: " + origSeriesUIDs.size() + " series");
        } else {
            Set<String> missing = new HashSet<>(origSeriesUIDs);
            missing.removeAll(rtSeriesUIDs);
            Set<String> extra = new HashSet<>(rtSeriesUIDs);
            extra.removeAll(origSeriesUIDs);

            if (!missing.isEmpty()) {
                mismatches.add("Evidence Series UIDs: MISSING " + missing.size() + " series");
            }
            if (!extra.isEmpty()) {
                warnings.add("Evidence Series UIDs: EXTRA " + extra.size() + " series");
            }
        }

        // Compare Instance UIDs
        Set<String> origInstUIDs = extractInstanceUIDsFromEvidence(origSeq);
        Set<String> rtInstUIDs = extractInstanceUIDsFromEvidence(rtSeq);

        if (origInstUIDs.equals(rtInstUIDs)) {
            matches.add("Evidence Instance UIDs: " + origInstUIDs.size() + " instances");
        } else {
            Set<String> missing = new HashSet<>(origInstUIDs);
            missing.removeAll(rtInstUIDs);
            Set<String> extra = new HashSet<>(rtInstUIDs);
            extra.removeAll(origInstUIDs);

            if (!missing.isEmpty()) {
                mismatches.add("Evidence Instance UIDs: MISSING " + missing.size() + " instances");
            }
            if (!extra.isEmpty()) {
                warnings.add("Evidence Instance UIDs: EXTRA " + extra.size() + " instances");
            }
        }
    }

    private Set<String> extractStudyUIDsFromEvidence(Sequence evidenceSeq) {
        Set<String> uids = new HashSet<>();
        for (Attributes item : evidenceSeq) {
            String uid = item.getString(Tag.StudyInstanceUID);
            if (uid != null) uids.add(uid);
        }
        return uids;
    }

    private Set<String> extractSeriesUIDsFromEvidence(Sequence evidenceSeq) {
        Set<String> uids = new HashSet<>();
        for (Attributes studyItem : evidenceSeq) {
            Sequence refSeriesSeq = studyItem.getSequence(Tag.ReferencedSeriesSequence);
            if (refSeriesSeq != null) {
                for (Attributes seriesItem : refSeriesSeq) {
                    String uid = seriesItem.getString(Tag.SeriesInstanceUID);
                    if (uid != null) uids.add(uid);
                }
            }
        }
        return uids;
    }

    private Set<String> extractInstanceUIDsFromEvidence(Sequence evidenceSeq) {
        Set<String> uids = new HashSet<>();
        for (Attributes studyItem : evidenceSeq) {
            Sequence refSeriesSeq = studyItem.getSequence(Tag.ReferencedSeriesSequence);
            if (refSeriesSeq != null) {
                for (Attributes seriesItem : refSeriesSeq) {
                    Sequence refSopSeq = seriesItem.getSequence(Tag.ReferencedSOPSequence);
                    if (refSopSeq != null) {
                        for (Attributes sopItem : refSopSeq) {
                            String uid = sopItem.getString(Tag.ReferencedSOPInstanceUID);
                            if (uid != null) uids.add(uid);
                        }
                    }
                }
            }
        }
        return uids;
    }

    private void compareReferencedRequestSequence(Attributes original, Attributes roundTripped) {
        Sequence origSeq = original.getSequence(Tag.ReferencedRequestSequence);
        Sequence rtSeq = roundTripped.getSequence(Tag.ReferencedRequestSequence);

        if (origSeq == null && rtSeq == null) {
            warnings.add("Referenced Request Sequence: both null");
            return;
        }

        Set<String> origAccessions = new HashSet<>();
        Set<String> rtAccessions = new HashSet<>();

        if (origSeq != null) {
            for (Attributes item : origSeq) {
                String acc = item.getString(Tag.AccessionNumber);
                if (acc != null) origAccessions.add(acc);
            }
        }

        if (rtSeq != null) {
            for (Attributes item : rtSeq) {
                String acc = item.getString(Tag.AccessionNumber);
                if (acc != null) rtAccessions.add(acc);
            }
        }

        if (origAccessions.equals(rtAccessions)) {
            matches.add("Referenced Request Accession Numbers: " + origAccessions);
        } else {
            mismatches.add("Referenced Request Accession Numbers: " + origAccessions + " -> " + rtAccessions);
        }
    }

    private void compareContentSequence(Attributes original, Attributes roundTripped) {
        Sequence origSeq = original.getSequence(Tag.ContentSequence);
        Sequence rtSeq = roundTripped.getSequence(Tag.ContentSequence);

        if (origSeq == null && rtSeq == null) {
            warnings.add("Content Sequence: both null");
            return;
        }

        if (origSeq == null || rtSeq == null) {
            mismatches.add("Content Sequence: " +
                (origSeq == null ? "original null" : "round-trip null"));
            return;
        }

        // Extract modality from content
        String origModality = extractModalityFromContent(origSeq);
        String rtModality = extractModalityFromContent(rtSeq);

        if (Objects.equals(origModality, rtModality)) {
            matches.add("Content Modality: " + origModality);
        } else {
            mismatches.add("Content Modality: " + origModality + " -> " + rtModality);
        }

        // Extract Study UID from content
        String origStudyUID = extractStudyUIDFromContent(origSeq);
        String rtStudyUID = extractStudyUIDFromContent(rtSeq);

        if (Objects.equals(origStudyUID, rtStudyUID)) {
            matches.add("Content Study UID: present");
        } else {
            mismatches.add("Content Study UID: " + origStudyUID + " -> " + rtStudyUID);
        }

        // Check Image Library presence
        boolean origHasImageLibrary = hasImageLibrary(origSeq);
        boolean rtHasImageLibrary = hasImageLibrary(rtSeq);

        if (origHasImageLibrary && rtHasImageLibrary) {
            matches.add("Image Library: present in both");
            // Compare Series Date/Time in Image Library Groups
            compareSeriesDateTimeInContent(origSeq, rtSeq);
        } else {
            mismatches.add("Image Library: orig=" + origHasImageLibrary + ", rt=" + rtHasImageLibrary);
        }
    }

    /**
     * Compares Series Date/Time values in Image Library Groups.
     */
    private void compareSeriesDateTimeInContent(Sequence origContent, Sequence rtContent) {
        // Extract Series Date/Time from original
        Map<String, String> origSeriesDates = extractSeriesDateTimeFromContent(origContent, "ddd003");
        Map<String, String> origSeriesTimes = extractSeriesDateTimeFromContent(origContent, "ddd004");

        // Extract Series Date/Time from round-tripped
        Map<String, String> rtSeriesDates = extractSeriesDateTimeFromContent(rtContent, "ddd003");
        Map<String, String> rtSeriesTimes = extractSeriesDateTimeFromContent(rtContent, "ddd004");

        // Compare Series Dates
        if (origSeriesDates.isEmpty() && rtSeriesDates.isEmpty()) {
            warnings.add("Series Date (ddd003): none in either");
        } else if (origSeriesDates.equals(rtSeriesDates)) {
            matches.add("Series Date (ddd003): " + origSeriesDates.size() + " values preserved");
        } else {
            // Check which dates match
            int matchCount = 0;
            for (Map.Entry<String, String> entry : origSeriesDates.entrySet()) {
                if (Objects.equals(entry.getValue(), rtSeriesDates.get(entry.getKey()))) {
                    matchCount++;
                }
            }
            if (matchCount == origSeriesDates.size()) {
                matches.add("Series Date (ddd003): all " + matchCount + " values preserved");
            } else {
                mismatches.add("Series Date (ddd003): " + matchCount + "/" + origSeriesDates.size() +
                    " preserved. Orig=" + origSeriesDates + " RT=" + rtSeriesDates);
            }
        }

        // Compare Series Times
        if (origSeriesTimes.isEmpty() && rtSeriesTimes.isEmpty()) {
            warnings.add("Series Time (ddd004): none in either");
        } else if (origSeriesTimes.equals(rtSeriesTimes)) {
            matches.add("Series Time (ddd004): " + origSeriesTimes.size() + " values preserved");
        } else {
            int matchCount = 0;
            for (Map.Entry<String, String> entry : origSeriesTimes.entrySet()) {
                if (Objects.equals(entry.getValue(), rtSeriesTimes.get(entry.getKey()))) {
                    matchCount++;
                }
            }
            if (matchCount == origSeriesTimes.size()) {
                matches.add("Series Time (ddd004): all " + matchCount + " values preserved");
            } else {
                mismatches.add("Series Time (ddd004): " + matchCount + "/" + origSeriesTimes.size() +
                    " preserved. Orig=" + origSeriesTimes + " RT=" + rtSeriesTimes);
            }
        }
    }

    /**
     * Extracts Series Date or Time values from Content Sequence.
     * @param codeValue "ddd003" for Series Date, "ddd004" for Series Time
     * @return Map of Series UID to Date/Time value
     */
    private Map<String, String> extractSeriesDateTimeFromContent(Sequence contentSeq, String codeValue) {
        Map<String, String> result = new HashMap<>();

        // Find Image Library
        for (Attributes item : contentSeq) {
            if ("CONTAINER".equals(item.getString(Tag.ValueType))) {
                Sequence conceptNameSeq = item.getSequence(Tag.ConceptNameCodeSequence);
                if (conceptNameSeq != null && !conceptNameSeq.isEmpty()) {
                    String code = conceptNameSeq.get(0).getString(Tag.CodeValue);
                    if ("111028".equals(code)) { // Image Library
                        extractFromImageLibrary(item, codeValue, result);
                    }
                }
            }
        }

        return result;
    }

    private void extractFromImageLibrary(Attributes imageLibrary, String codeValue, Map<String, String> result) {
        Sequence libContent = imageLibrary.getSequence(Tag.ContentSequence);
        if (libContent == null) return;

        for (Attributes item : libContent) {
            if ("CONTAINER".equals(item.getString(Tag.ValueType))) {
                Sequence conceptNameSeq = item.getSequence(Tag.ConceptNameCodeSequence);
                if (conceptNameSeq != null && !conceptNameSeq.isEmpty()) {
                    String code = conceptNameSeq.get(0).getString(Tag.CodeValue);
                    if ("126200".equals(code)) { // Image Library Group
                        extractFromImageLibraryGroup(item, codeValue, result);
                    }
                }
            }
        }
    }

    private void extractFromImageLibraryGroup(Attributes group, String codeValue, Map<String, String> result) {
        Sequence groupContent = group.getSequence(Tag.ContentSequence);
        if (groupContent == null) return;

        String seriesUID = null;
        String dateTimeValue = null;

        for (Attributes item : groupContent) {
            Sequence conceptNameSeq = item.getSequence(Tag.ConceptNameCodeSequence);
            if (conceptNameSeq == null || conceptNameSeq.isEmpty()) continue;

            String code = conceptNameSeq.get(0).getString(Tag.CodeValue);

            if ("ddd006".equals(code) && "UIDREF".equals(item.getString(Tag.ValueType))) {
                // Series Instance UID
                seriesUID = item.getString(Tag.UID);
            } else if (codeValue.equals(code) && "TEXT".equals(item.getString(Tag.ValueType))) {
                // Series Date or Time
                dateTimeValue = item.getString(Tag.TextValue);
            }
        }

        if (seriesUID != null && dateTimeValue != null) {
            result.put(seriesUID, dateTimeValue);
        }
    }

    private String extractModalityFromContent(Sequence contentSeq) {
        for (Attributes item : contentSeq) {
            if ("CODE".equals(item.getString(Tag.ValueType))) {
                Sequence conceptNameSeq = item.getSequence(Tag.ConceptNameCodeSequence);
                if (conceptNameSeq != null && !conceptNameSeq.isEmpty()) {
                    String codeValue = conceptNameSeq.get(0).getString(Tag.CodeValue);
                    if ("121139".equals(codeValue)) { // Modality
                        Sequence conceptCodeSeq = item.getSequence(Tag.ConceptCodeSequence);
                        if (conceptCodeSeq != null && !conceptCodeSeq.isEmpty()) {
                            return conceptCodeSeq.get(0).getString(Tag.CodeValue);
                        }
                    }
                }
            }
        }
        return null;
    }

    private String extractStudyUIDFromContent(Sequence contentSeq) {
        for (Attributes item : contentSeq) {
            if ("UIDREF".equals(item.getString(Tag.ValueType))) {
                Sequence conceptNameSeq = item.getSequence(Tag.ConceptNameCodeSequence);
                if (conceptNameSeq != null && !conceptNameSeq.isEmpty()) {
                    String codeValue = conceptNameSeq.get(0).getString(Tag.CodeValue);
                    if ("ddd011".equals(codeValue)) { // Study Instance UID
                        return item.getString(Tag.UID);
                    }
                }
            }
        }
        return null;
    }

    private boolean hasImageLibrary(Sequence contentSeq) {
        for (Attributes item : contentSeq) {
            if ("CONTAINER".equals(item.getString(Tag.ValueType))) {
                Sequence conceptNameSeq = item.getSequence(Tag.ConceptNameCodeSequence);
                if (conceptNameSeq != null && !conceptNameSeq.isEmpty()) {
                    String codeValue = conceptNameSeq.get(0).getString(Tag.CodeValue);
                    if ("111028".equals(codeValue)) { // Image Library
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ============================================================================
    // RESULT CLASS
    // ============================================================================

    public static class ComparisonResult {
        public final List<String> matches;
        public final List<String> mismatches;
        public final List<String> warnings;

        public ComparisonResult(List<String> matches, List<String> mismatches, List<String> warnings) {
            this.matches = new ArrayList<>(matches);
            this.mismatches = new ArrayList<>(mismatches);
            this.warnings = new ArrayList<>(warnings);
        }

        public boolean isSuccess() {
            return mismatches.isEmpty();
        }

        public void print() {
            System.out.println("\n========== ROUND-TRIP COMPARISON RESULTS ==========\n");

            System.out.println("✓ MATCHES (" + matches.size() + "):");
            for (String match : matches) {
                System.out.println("  ✓ " + match);
            }

            System.out.println("\n⚠ WARNINGS (" + warnings.size() + "):");
            for (String warning : warnings) {
                System.out.println("  ⚠ " + warning);
            }

            System.out.println("\n✗ MISMATCHES (" + mismatches.size() + "):");
            for (String mismatch : mismatches) {
                System.out.println("  ✗ " + mismatch);
            }

            System.out.println("\n========== SUMMARY ==========");
            System.out.println("Matches: " + matches.size());
            System.out.println("Warnings: " + warnings.size());
            System.out.println("Mismatches: " + mismatches.size());
            System.out.println("Result: " + (isSuccess() ? "SUCCESS" : "FAILURE"));
            System.out.println("=====================================\n");
        }
    }

    // ============================================================================
    // MAIN
    // ============================================================================

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java FHIRToMADORoundTripTest <input.dcm> [output.dcm]");
            System.exit(1);
        }

        File inputFile = new File(args[0]);
        if (!inputFile.exists()) {
            System.err.println("Input file not found: " + args[0]);
            System.exit(1);
        }

        try {
            FHIRToMADORoundTripTest test = new FHIRToMADORoundTripTest();
            ComparisonResult result;

            if (args.length > 1) {
                File outputFile = new File(args[1]);
                System.out.println("Performing round-trip: " + inputFile + " -> FHIR -> " + outputFile);
                result = test.testRoundTripAndSave(inputFile, outputFile);
            } else {
                System.out.println("Performing round-trip: " + inputFile + " -> FHIR -> DICOM (in memory)");
                result = test.testRoundTrip(inputFile);
            }

            result.print();

            System.exit(result.isSuccess() ? 0 : 1);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

