package be.uzleuven.ihe.dicom.creator.evil;

import be.uzleuven.ihe.dicom.constants.DicomConstants;
import org.dcm4che3.data.*;
import org.dcm4che3.util.UIDUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static be.uzleuven.ihe.dicom.creator.utils.DicomCreatorUtils.*;
import static be.uzleuven.ihe.dicom.creator.utils.DicomSequenceUtils.*;
import static be.uzleuven.ihe.dicom.creator.utils.SRContentItemUtils.*;
import static be.uzleuven.ihe.dicom.constants.CodeConstants.*;

/**
 * EVIL generator: creates KOS documents that randomly omit creation steps or inject wrong tags/values.
 * This is intentionally isolated from the normal generators.
 */
public class EVILKOSCreator {

    private static final double SKIP_STEP_P = 0.20;
    private static final double CORRUPT_P = 0.05;
    private static final double MADO_VIOLATION_P = 0.35;
    private static final double EVIDENCE_MISMATCH_P = 0.40; // 40% chance to create Evidence/ContentTree mismatch

    public static void main(String[] args) throws Exception {
        int count = 1;
        if (args != null && args.length > 0) {
            try {
                count = Math.max(1, Integer.parseInt(args[0]));
            } catch (NumberFormatException ignore) {
            }
        }

        File outDir = new File(System.getProperty("user.dir"));
        for (int i = 0; i < count; i++) {
            Attributes kos = createRandomEvilKOS();
            File out = new File(outDir, "EVIL_IHEKOS_" + i + "_" + EvilDice.token(6) + ".dcm");
            writeDicomFile(out, kos);
            System.out.println("Wrote: " + out.getAbsolutePath());
        }
    }

    public static Attributes createRandomEvilKOS() {
        Attributes d = new Attributes();

        // Always writeable Part 10: keep SOP Class/Instance set.
        d.setString(Tag.SOPClassUID, VR.UI, UID.KeyObjectSelectionDocumentStorage);
        d.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());

        // --- Patient IE ---
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.PatientName, VR.PN, randomPersonName());
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.PatientID, VR.LO, randomPatientId());
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.IssuerOfPatientID, VR.LO, randomIssuer());
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.PatientBirthDate, VR.DA, randomDateYYYYMMDD(1940, 2015));
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.PatientSex, VR.CS, randomFrom("M", "F", "O"));

        // MADO-specific Patient violations
        if (EvilDice.chance(MADO_VIOLATION_P)) {
            violateMADOPatient(d);
        }

        // --- Study IE ---
        String studyInstanceUid = UIDUtils.createUID();
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUid);
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.StudyID, VR.SH, String.format("%04d", 1 + randomInt(9999)));
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.StudyDate, VR.DA, todayYYYYMMDD());
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.StudyTime, VR.TM, nowHHMMSS());
        String accessionNumber = randomAccession();
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.AccessionNumber, VR.SH, accessionNumber);
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.ReferringPhysicianName, VR.PN, randomPersonName());

        // MADO-specific Study violations
        if (EvilDice.chance(MADO_VIOLATION_P)) {
            violateMADOStudy(d, accessionNumber);
        }

        // --- Series IE ---
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.Modality, VR.CS, "KO");
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.SeriesInstanceUID, VR.UI, UIDUtils.createUID());
        if (!EvilDice.chance(SKIP_STEP_P)) d.setInt(Tag.SeriesNumber, VR.IS, 1 + randomInt(99));
        if (!EvilDice.chance(SKIP_STEP_P)) {
            d.newSequence(Tag.ReferencedPerformedProcedureStepSequence, 1).add(new Attributes());
        }

        // --- Equipment ---
        if (!EvilDice.chance(SKIP_STEP_P)) {
            d.setString(Tag.Manufacturer, VR.LO, randomFrom("ACME Imaging", "Globex Medical", "Initech", "Umbrella Radiology"));
        }

        // MADO-specific Equipment violations
        if (EvilDice.chance(MADO_VIOLATION_P)) {
            violateMADOEquipment(d);
        }

        // --- Key Object Document Module / SR general ---
        if (!EvilDice.chance(SKIP_STEP_P)) d.setInt(Tag.InstanceNumber, VR.IS, 1 + randomInt(999));
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.ContentDate, VR.DA, todayYYYYMMDD());
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.ContentTime, VR.TM, nowHHMMSS());
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.CompletionFlag, VR.CS, DicomConstants.COMPLETION_FLAG_COMPLETE);
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.VerificationFlag, VR.CS, DicomConstants.VERIFICATION_FLAG_UNVERIFIED);
        if (!EvilDice.chance(SKIP_STEP_P)) d.setString(Tag.TimezoneOffsetFromUTC, VR.SH, timezoneOffsetFromUTC());

        // MADO-specific Timezone violations
        if (EvilDice.chance(MADO_VIOLATION_P)) {
            violateMADOTimezone(d);
        }

        if (!EvilDice.chance(SKIP_STEP_P)) {
            // ReferencedRequestSequence is Type 2; EVIL may omit it.
            populateReferencedRequestSequence(d, studyInstanceUid, accessionNumber);
        }

        // --- SR Document Content Module (Root container) ---
        if (!EvilDice.chance(SKIP_STEP_P)) {
            d.setString(Tag.ValueType, VR.CS, "CONTAINER");
            d.setString(Tag.ContinuityOfContent, VR.CS, DicomConstants.CONTINUITY_SEPARATE);
        }

        if (!EvilDice.chance(SKIP_STEP_P)) {
            Sequence conceptName = d.newSequence(Tag.ConceptNameCodeSequence, 1);
            conceptName.add(code(CODE_MANIFEST, SCHEME_DCM, MEANING_MANIFEST));
        }

        // MADO-specific Document Title violations
        if (EvilDice.chance(MADO_VIOLATION_P)) {
            violateMADODocumentTitle(d);
        }

        if (!EvilDice.chance(SKIP_STEP_P)) {
            Sequence cts = d.newSequence(Tag.ContentTemplateSequence, 1);
            cts.add(createTemplateItem("2010"));
        }

        // MADO-specific Template violations
        if (EvilDice.chance(MADO_VIOLATION_P)) {
            violateMADOTemplate(d);
        }

        // Build reference list
        List<Attributes> referencedSops = buildReferencedSops();

        // EVIL: Sometimes create mismatch between Evidence and ContentTree
        // This violates DICOM/IHE XDS-I spec: "ALL instances in Evidence MUST appear in Content Tree"
        List<Attributes> contentTreeSops = referencedSops;
        if (EvilDice.chance(EVIDENCE_MISMATCH_P)) {
            contentTreeSops = createMismatchedContentTreeList(referencedSops);
        }

        if (!EvilDice.chance(SKIP_STEP_P)) {
            populateEvidence(d, studyInstanceUid, referencedSops);
        }

        // MADO-specific Evidence violations
        if (EvilDice.chance(MADO_VIOLATION_P)) {
            violateMADOEvidence(d);
        }

        if (!EvilDice.chance(SKIP_STEP_P)) {
            populateContentTree(d, contentTreeSops);
        }

        // MADO-specific Content Tree violations
        if (EvilDice.chance(MADO_VIOLATION_P)) {
            violateMADOContentTree(d);
        }

        if (!EvilDice.chance(SKIP_STEP_P)) {
            d.setString(Tag.SpecificCharacterSet, VR.CS, "ISO_IR 100");
        }

        // 5% chance: inject a corruption
        if (EvilDice.chance(CORRUPT_P)) {
            EvilMutator.corruptOne(d);
        }

        // 30% chance: add forbidden tags (makes it more evil!)
        ForbiddenTags.addRandomForbiddenTags(d, 0.30);

        return d;
    }

    private static void populateContentTree(Attributes d, List<Attributes> referencedSops) {
        Sequence content = d.newSequence(Tag.ContentSequence, 50);
        for (Attributes sop : referencedSops) {
            Attributes img = createImageItem(DicomConstants.RELATIONSHIP_CONTAINS,
                    code(CODE_IMAGE, SCHEME_DCM, MEANING_IMAGE),
                    sop.getString(Tag.ReferencedSOPClassUID),
                    sop.getString(Tag.ReferencedSOPInstanceUID));
            content.add(img);
        }
    }

    private static List<Attributes> buildReferencedSops() {
        int sopCount = 6;
        List<Attributes> sops = new ArrayList<>(sopCount);
        for (int i = 0; i < sopCount; i++) {
            Attributes sop = new Attributes();
            sop.setString(Tag.ReferencedSOPClassUID, VR.UI, randomFrom(
                    UID.CTImageStorage,
                    UID.MRImageStorage,
                    UID.DigitalXRayImageStorageForPresentation,
                    UID.UltrasoundImageStorage,
                    UID.SecondaryCaptureImageStorage
            ));
            sop.setString(Tag.ReferencedSOPInstanceUID, VR.UI, UIDUtils.createUID());
            sops.add(sop);
        }
        return sops;
    }

    /**
     * Create a mismatched Content Tree list to violate the DICOM/IHE XDS-I spec.
     * The spec requires: "ALL instances in Evidence MUST appear in Content Tree"
     * This method creates various types of mismatches:
     * - Fewer instances in Content Tree (missing some from Evidence)
     * - More instances in Content Tree (has extras not in Evidence)
     * - Different instances (some overlap but not complete match)
     * - Completely empty Content Tree
     */
    private static List<Attributes> createMismatchedContentTreeList(List<Attributes> evidenceSops) {
        int violationType = EvilDice.randomInt(5);
        List<Attributes> mismatched = new ArrayList<>();

        switch (violationType) {
            case 0:
                // Fewer instances: Remove 1-3 instances from Evidence list
                int removeCount = 1 + EvilDice.randomInt(Math.min(3, evidenceSops.size()));
                for (int i = removeCount; i < evidenceSops.size(); i++) {
                    mismatched.add(evidenceSops.get(i));
                }
                break;

            case 1:
                // More instances: Add Evidence instances + some extras
                mismatched.addAll(evidenceSops);
                int extraCount = 1 + EvilDice.randomInt(3);
                for (int i = 0; i < extraCount; i++) {
                    Attributes extra = new Attributes();
                    extra.setString(Tag.ReferencedSOPClassUID, VR.UI, randomFrom(
                            UID.CTImageStorage,
                            UID.MRImageStorage,
                            UID.SecondaryCaptureImageStorage
                    ));
                    extra.setString(Tag.ReferencedSOPInstanceUID, VR.UI, UIDUtils.createUID());
                    mismatched.add(extra);
                }
                break;

            case 2:
                // Different instances: Replace some instances with new ones
                for (int i = 0; i < evidenceSops.size(); i++) {
                    if (i < evidenceSops.size() / 2) {
                        mismatched.add(evidenceSops.get(i)); // Keep half
                    } else {
                        // Replace with new instance
                        Attributes replacement = new Attributes();
                        replacement.setString(Tag.ReferencedSOPClassUID, VR.UI, randomFrom(
                                UID.CTImageStorage,
                                UID.MRImageStorage
                        ));
                        replacement.setString(Tag.ReferencedSOPInstanceUID, VR.UI, UIDUtils.createUID());
                        mismatched.add(replacement);
                    }
                }
                break;

            case 3:
                // Empty Content Tree (severe violation!)
                // Return empty list
                break;

            case 4:
                // Only 1 instance when Evidence has many
                if (!evidenceSops.isEmpty()) {
                    mismatched.add(evidenceSops.get(0));
                }
                break;
        }

        return mismatched;
    }

    // ========== MADO-specific violation methods ==========

    /**
     * Violate MADO Patient IE requirements:
     * - Missing IssuerOfPatientIDQualifiersSequence
     * - Missing UniversalEntityID/Type
     * - Wrong TypeOfPatientID
     * - Missing OtherPatientIDsSequence
     */
    private static void violateMADOPatient(Attributes d) {
        int violation = EvilDice.randomInt(6);
        switch (violation) {
            case 0:
                // Remove IssuerOfPatientIDQualifiersSequence completely
                d.remove(Tag.IssuerOfPatientIDQualifiersSequence);
                break;
            case 1:
                // Add IssuerOfPatientIDQualifiersSequence but empty
                d.newSequence(Tag.IssuerOfPatientIDQualifiersSequence, 0);
                break;
            case 2:
                // Add IssuerOfPatientIDQualifiersSequence but missing UniversalEntityID
                Sequence issuerSeq = d.newSequence(Tag.IssuerOfPatientIDQualifiersSequence, 1);
                Attributes issuer = new Attributes();
                issuer.setString(Tag.UniversalEntityIDType, VR.CS, "ISO");
                issuerSeq.add(issuer);
                break;
            case 3:
                // Add IssuerOfPatientIDQualifiersSequence but wrong UniversalEntityIDType
                Sequence issuerSeq2 = d.newSequence(Tag.IssuerOfPatientIDQualifiersSequence, 1);
                Attributes issuer2 = new Attributes();
                issuer2.setString(Tag.UniversalEntityID, VR.UT, "1.2.3.4.5");
                issuer2.setString(Tag.UniversalEntityIDType, VR.CS, "DNS"); // Wrong! Should be ISO
                issuerSeq2.add(issuer2);
                break;
            case 4:
                // Wrong TypeOfPatientID
                d.setString(Tag.TypeOfPatientID, VR.CS, randomFrom("MRN", "ANON", "TEST"));
                break;
            case 5:
                // Make PatientID empty
                d.setString(Tag.PatientID, VR.LO, "");
                break;
        }
    }

    /**
     * Violate MADO Study IE requirements:
     * - Missing StudyDate/Time
     * - Missing AccessionNumber
     * - Non-empty AccessionNumber with multiple requests
     * - Missing IssuerOfAccessionNumberSequence
     */
    private static void violateMADOStudy(Attributes d, String accessionNumber) {
        int violation = EvilDice.randomInt(6);
        switch (violation) {
            case 0:
                // Remove StudyDate
                d.remove(Tag.StudyDate);
                break;
            case 1:
                // Remove StudyTime
                d.remove(Tag.StudyTime);
                break;
            case 2:
                // Set empty StudyDate
                d.setString(Tag.StudyDate, VR.DA, "");
                break;
            case 3:
                // Remove AccessionNumber
                d.remove(Tag.AccessionNumber);
                break;
            case 4:
                // AccessionNumber present but missing issuer
                if (accessionNumber != null && !accessionNumber.trim().isEmpty()) {
                    d.remove(Tag.IssuerOfAccessionNumberSequence);
                }
                break;
            case 5:
                // Multiple accession numbers but AccessionNumber not empty
                d.setString(Tag.AccessionNumber, VR.SH, randomAccession());
                // Add ReferencedRequestSequence with multiple items
                Sequence refReq = d.newSequence(Tag.ReferencedRequestSequence, 2);
                for (int i = 0; i < 2; i++) {
                    Attributes req = new Attributes();
                    req.setString(Tag.AccessionNumber, VR.SH, randomAccession());
                    req.setString(Tag.StudyInstanceUID, VR.UI, UIDUtils.createUID());
                    refReq.add(req);
                }
                break;
        }
    }

    /**
     * Violate MADO Equipment IE requirements:
     * - Missing Manufacturer
     * - Empty Manufacturer
     * - Missing InstitutionName
     * - Empty InstitutionName
     */
    private static void violateMADOEquipment(Attributes d) {
        int violation = EvilDice.randomInt(4);
        switch (violation) {
            case 0:
                // Remove Manufacturer
                d.remove(Tag.Manufacturer);
                break;
            case 1:
                // Empty Manufacturer
                d.setString(Tag.Manufacturer, VR.LO, "");
                break;
            case 2:
                // Remove InstitutionName
                d.remove(Tag.InstitutionName);
                break;
            case 3:
                // Empty InstitutionName
                d.setString(Tag.InstitutionName, VR.LO, "");
                break;
        }
    }

    /**
     * Violate MADO Timezone requirements:
     * - Missing TimezoneOffsetFromUTC
     * - Invalid timezone format
     */
    private static void violateMADOTimezone(Attributes d) {
        int violation = EvilDice.randomInt(4);
        switch (violation) {
            case 0:
                // Remove TimezoneOffsetFromUTC
                d.remove(Tag.TimezoneOffsetFromUTC);
                break;
            case 1:
                // Invalid format - no sign
                d.setString(Tag.TimezoneOffsetFromUTC, VR.SH, "0100");
                break;
            case 2:
                // Invalid format - wrong length
                d.setString(Tag.TimezoneOffsetFromUTC, VR.SH, "+1");
                break;
            case 3:
                // Invalid format - out of range
                d.setString(Tag.TimezoneOffsetFromUTC, VR.SH, "+9999");
                break;
        }
    }

    /**
     * Violate MADO Document Title requirements:
     * - Wrong code value
     * - Wrong coding scheme
     * - Wrong code meaning
     * - Missing ConceptNameCodeSequence
     * - Empty ConceptNameCodeSequence
     */
    private static void violateMADODocumentTitle(Attributes d) {
        int violation = EvilDice.randomInt(7);
        switch (violation) {
            case 0:
                // Wrong code value (use XDS-I.b code instead of MADO)
                Sequence conceptName = d.newSequence(Tag.ConceptNameCodeSequence, 1);
                conceptName.add(code("113030", "DCM", "Manifest")); // XDS-I.b, not MADO
                break;
            case 1:
                // Wrong coding scheme
                Sequence conceptName2 = d.newSequence(Tag.ConceptNameCodeSequence, 1);
                conceptName2.add(code("ddd001", "SCT", "Manifest with Description"));
                break;
            case 2:
                // Wrong meaning
                Sequence conceptName3 = d.newSequence(Tag.ConceptNameCodeSequence, 1);
                conceptName3.add(code("ddd001", "DCM", "Wrong Meaning"));
                break;
            case 3:
                // Remove ConceptNameCodeSequence
                d.remove(Tag.ConceptNameCodeSequence);
                break;
            case 4:
                // Empty ConceptNameCodeSequence
                d.newSequence(Tag.ConceptNameCodeSequence, 0);
                break;
            case 5:
                // Multiple items in ConceptNameCodeSequence
                Sequence conceptName4 = d.newSequence(Tag.ConceptNameCodeSequence, 2);
                conceptName4.add(code("ddd001", "DCM", "Manifest with Description"));
                conceptName4.add(code("113030", "DCM", "Manifest"));
                break;
            case 6:
                // Missing code attributes in ConceptNameCodeSequence
                Sequence conceptName5 = d.newSequence(Tag.ConceptNameCodeSequence, 1);
                conceptName5.add(new Attributes()); // Empty item
                break;
        }
    }

    /**
     * Violate MADO Template requirements:
     * - Wrong template ID (should be 1600, not 2010)
     * - Missing TemplateIdentifier
     * - Missing MappingResource
     * - Wrong template structure
     */
    private static void violateMADOTemplate(Attributes d) {
        int violation = EvilDice.randomInt(5);
        switch (violation) {
            case 0:
                // Wrong template ID - keep 2010 (KOS) instead of 1600 (MADO)
                // This is already wrong for MADO, but let's be explicit
                break;
            case 1:
                // Missing ContentTemplateSequence
                d.remove(Tag.ContentTemplateSequence);
                break;
            case 2:
                // Empty ContentTemplateSequence
                d.newSequence(Tag.ContentTemplateSequence, 0);
                break;
            case 3:
                // Wrong mapping resource
                Sequence cts = d.newSequence(Tag.ContentTemplateSequence, 1);
                Attributes template = new Attributes();
                template.setString(Tag.TemplateIdentifier, VR.CS, "1600");
                template.setString(Tag.MappingResource, VR.CS, "WRONG"); // Should be DCMR
                cts.add(template);
                break;
            case 4:
                // Multiple template items
                Sequence cts2 = d.newSequence(Tag.ContentTemplateSequence, 2);
                cts2.add(createTemplateItem("2010"));
                cts2.add(createTemplateItem("1600"));
                break;
        }
    }

    /**
     * Violate MADO Evidence sequence requirements:
     * - Missing retrieval information
     * - Invalid retrieval URLs
     * - Missing series-level attributes (Modality, etc.)
     * - Inconsistent addressing modes
     */
    private static void violateMADOEvidence(Attributes d) {
        Sequence evidenceSeq = d.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence);
        if (evidenceSeq == null || evidenceSeq.isEmpty()) {
            return;
        }

        int violation = EvilDice.randomInt(10);

        for (Attributes studyItem : evidenceSeq) {
            Sequence seriesSeq = studyItem.getSequence(Tag.ReferencedSeriesSequence);
            if (seriesSeq == null || seriesSeq.isEmpty()) {
                continue;
            }

            for (Attributes seriesItem : seriesSeq) {
                switch (violation) {
                    case 0:
                        // Remove all retrieval information
                        seriesItem.remove(Tag.RetrieveURL);
                        seriesItem.remove(Tag.RetrieveLocationUID);
                        break;
                    case 1:
                        // Invalid Retrieve URL
                        seriesItem.setString(Tag.RetrieveURL, VR.UR, "not-a-valid-url");
                        break;
                    case 2:
                        // Retrieve URL without protocol
                        seriesItem.setString(Tag.RetrieveURL, VR.UR, "example.com/wado");
                        break;
                    case 3:
                        // Invalid Retrieve Location UID
                        seriesItem.setString(Tag.RetrieveLocationUID, VR.UI, "invalid.uid..123");
                        break;
                    case 4:
                        // Missing Modality at series level (Appendix B requirement)
                        seriesItem.remove(Tag.Modality);
                        break;
                    case 5:
                        // Empty Modality at series level
                        seriesItem.setString(Tag.Modality, VR.CS, "");
                        break;
                    case 6:
                        // Missing Series Instance UID
                        seriesItem.remove(Tag.SeriesInstanceUID);
                        break;
                    case 7:
                        // Mix both retrieval addressing modes (inconsistent)
                        seriesItem.setString(Tag.RetrieveURL, VR.UR, "https://example.com/wado");
                        seriesItem.setString(Tag.RetrieveLocationUID, VR.UI, UIDUtils.createUID());
                        break;
                    case 8:
                        // Invalid RetrieveURI
                        seriesItem.setString(Tag.RetrieveURI, VR.UR, "javascript:alert('xss')");
                        break;
                    case 9:
                        // Missing ReferencedSOPSequence
                        seriesItem.remove(Tag.ReferencedSOPSequence);
                        break;
                }
            }
        }
    }

    /**
     * Violate MADO Content Tree (TID 1600) requirements:
     * - Missing Image Library container
     * - Missing required study-level attributes (Modality, Study UID, Target Region)
     * - Missing required series-level attributes
     * - Invalid content item structure
     */
    private static void violateMADOContentTree(Attributes d) {
        Sequence contentSeq = d.getSequence(Tag.ContentSequence);
        if (contentSeq == null) {
            return;
        }

        int violation = EvilDice.randomInt(15);

        switch (violation) {
            case 0:
                // Remove entire ContentSequence
                d.remove(Tag.ContentSequence);
                break;
            case 1:
                // Empty ContentSequence
                d.newSequence(Tag.ContentSequence, 0);
                break;
            case 2:
                // Remove Image Library container (111028, DCM)
                contentSeq.clear();
                // Add some content but not Image Library
                Attributes item = createTextItem(DicomConstants.RELATIONSHIP_CONTAINS,
                        "113012", "DCM", "Key Object Description",
                        "Some description");
                contentSeq.add(item);
                break;
            case 3:
                // Add Image Library but missing required study-level attributes
                contentSeq.clear();
                Attributes imageLib = new Attributes();
                imageLib.setString(Tag.ValueType, VR.CS, "CONTAINER");
                imageLib.setString(Tag.RelationshipType, VR.CS, DicomConstants.RELATIONSHIP_CONTAINS);
                Sequence libConcept = imageLib.newSequence(Tag.ConceptNameCodeSequence, 1);
                libConcept.add(code("111028", "DCM", "Image Library"));
                imageLib.setString(Tag.ContinuityOfContent, VR.CS, DicomConstants.CONTINUITY_SEPARATE);
                // Missing Modality, Study UID, Target Region
                contentSeq.add(imageLib);
                break;
            case 4:
                // Missing Modality (121139, DCM) at study level
                // Remove any Modality items
                for (int i = contentSeq.size() - 1; i >= 0; i--) {
                    Attributes item2 = contentSeq.get(i);
                    Sequence concept = item2.getSequence(Tag.ConceptNameCodeSequence);
                    if (concept != null && !concept.isEmpty()) {
                        String codeValue = concept.get(0).getString(Tag.CodeValue);
                        if ("121139".equals(codeValue)) {
                            contentSeq.remove(i);
                        }
                    }
                }
                break;
            case 5:
                // Missing Study Instance UID (ddd011, DCM)
                for (int i = contentSeq.size() - 1; i >= 0; i--) {
                    Attributes item2 = contentSeq.get(i);
                    Sequence concept = item2.getSequence(Tag.ConceptNameCodeSequence);
                    if (concept != null && !concept.isEmpty()) {
                        String codeValue = concept.get(0).getString(Tag.CodeValue);
                        if ("ddd011".equals(codeValue)) {
                            contentSeq.remove(i);
                        }
                    }
                }
                break;
            case 6:
                // Missing Target Region (123014, DCM)
                for (int i = contentSeq.size() - 1; i >= 0; i--) {
                    Attributes item2 = contentSeq.get(i);
                    Sequence concept = item2.getSequence(Tag.ConceptNameCodeSequence);
                    if (concept != null && !concept.isEmpty()) {
                        String codeValue = concept.get(0).getString(Tag.CodeValue);
                        if ("123014".equals(codeValue)) {
                            contentSeq.remove(i);
                        }
                    }
                }
                break;
            case 7:
                // Add Image Library Group but missing required series attributes
                Attributes imageLib2 = findOrCreateImageLibrary(d);
                Sequence libContent = imageLib2.getSequence(Tag.ContentSequence);
                if (libContent == null) {
                    libContent = imageLib2.newSequence(Tag.ContentSequence, 1);
                }
                // Add a group container without required attributes
                Attributes group = new Attributes();
                group.setString(Tag.ValueType, VR.CS, "CONTAINER");
                group.setString(Tag.RelationshipType, VR.CS, DicomConstants.RELATIONSHIP_CONTAINS);
                group.setString(Tag.ContinuityOfContent, VR.CS, DicomConstants.CONTINUITY_SEPARATE);
                // Missing Modality, Series Date, Series Time, etc.
                libContent.add(group);
                break;
            case 8:
                // Wrong ValueType for Image Library
                for (Attributes item2 : contentSeq) {
                    Sequence concept = item2.getSequence(Tag.ConceptNameCodeSequence);
                    if (concept != null && !concept.isEmpty()) {
                        String codeValue = concept.get(0).getString(Tag.CodeValue);
                        if ("111028".equals(codeValue)) {
                            item2.setString(Tag.ValueType, VR.CS, "TEXT"); // Wrong!
                        }
                    }
                }
                break;
            case 9:
                // Missing ContinuityOfContent in containers
                for (Attributes item2 : contentSeq) {
                    if ("CONTAINER".equals(item2.getString(Tag.ValueType))) {
                        item2.remove(Tag.ContinuityOfContent);
                    }
                }
                break;
            case 10:
                // Wrong RelationshipType
                for (Attributes item2 : contentSeq) {
                    item2.setString(Tag.RelationshipType, VR.CS, "HAS PROPERTIES"); // Wrong for top-level
                }
                break;
            case 11:
                // IMAGE items without ReferencedSOPSequence
                for (Attributes item2 : contentSeq) {
                    if ("IMAGE".equals(item2.getString(Tag.ValueType))) {
                        item2.remove(Tag.ReferencedSOPSequence);
                    }
                }
                break;
            case 12:
                // Add conflicting content items
                Attributes conflictItem = createTextItem(DicomConstants.RELATIONSHIP_CONTAINS,
                        "121139", "DCM", "Modality", "CT");
                contentSeq.add(conflictItem);
                Attributes conflictItem2 = createTextItem(DicomConstants.RELATIONSHIP_CONTAINS,
                        "121139", "DCM", "Modality", "MR");
                contentSeq.add(conflictItem2);
                break;
            case 13:
                // Missing ConceptNameCodeSequence in content items
                for (Attributes item2 : contentSeq) {
                    item2.remove(Tag.ConceptNameCodeSequence);
                }
                break;
            case 14:
                // Add orphan IMAGE references (in ContentSequence but not in Evidence)
                Attributes orphanImg = new Attributes();
                orphanImg.setString(Tag.ValueType, VR.CS, "IMAGE");
                orphanImg.setString(Tag.RelationshipType, VR.CS, DicomConstants.RELATIONSHIP_CONTAINS);
                Sequence orphanConcept = orphanImg.newSequence(Tag.ConceptNameCodeSequence, 1);
                orphanConcept.add(code("113012", "DCM", "Image"));
                Sequence orphanSOP = orphanImg.newSequence(Tag.ReferencedSOPSequence, 1);
                Attributes orphanSOPItem = new Attributes();
                orphanSOPItem.setString(Tag.ReferencedSOPClassUID, VR.UI, UID.CTImageStorage);
                orphanSOPItem.setString(Tag.ReferencedSOPInstanceUID, VR.UI, UIDUtils.createUID());
                orphanSOP.add(orphanSOPItem);
                contentSeq.add(orphanImg);
                break;
        }
    }

    /**
     * Helper to find or create Image Library container in ContentSequence
     */
    private static Attributes findOrCreateImageLibrary(Attributes d) {
        Sequence contentSeq = d.getSequence(Tag.ContentSequence);
        if (contentSeq == null) {
            contentSeq = d.newSequence(Tag.ContentSequence, 1);
        }

        // Try to find existing Image Library
        for (Attributes item : contentSeq) {
            Sequence concept = item.getSequence(Tag.ConceptNameCodeSequence);
            if (concept != null && !concept.isEmpty()) {
                String codeValue = concept.get(0).getString(Tag.CodeValue);
                if ("111028".equals(codeValue)) {
                    return item;
                }
            }
        }

        // Create new Image Library container
        Attributes imageLib = new Attributes();
        imageLib.setString(Tag.ValueType, VR.CS, "CONTAINER");
        imageLib.setString(Tag.RelationshipType, VR.CS, DicomConstants.RELATIONSHIP_CONTAINS);
        Sequence libConcept = imageLib.newSequence(Tag.ConceptNameCodeSequence, 1);
        libConcept.add(code("111028", "DCM", "Image Library"));
        imageLib.setString(Tag.ContinuityOfContent, VR.CS, DicomConstants.CONTINUITY_SEPARATE);
        contentSeq.add(imageLib);
        return imageLib;
    }
}


