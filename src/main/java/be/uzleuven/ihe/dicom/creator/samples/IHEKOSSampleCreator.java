package be.uzleuven.ihe.dicom.creator.samples;

import be.uzleuven.ihe.dicom.constants.DicomConstants;
import org.dcm4che3.data.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static be.uzleuven.ihe.dicom.creator.utils.DicomCreatorUtils.*;
import static be.uzleuven.ihe.dicom.creator.utils.DicomSequenceUtils.*;
import static be.uzleuven.ihe.dicom.creator.utils.SRContentItemUtils.*;
import static be.uzleuven.ihe.dicom.constants.CodeConstants.*;

public class IHEKOSSampleCreator {

    /**
     * Default WADO-RS base URL for series-level retrieval addressing.
     * Per IHE XDS-I.b DICOM Retrieve by WADO-RS Option, Retrieve URL (0008,1190)
     * should be placed at the series level within Referenced Series Sequence.
     */
    private static final String DEFAULT_WADO_RS_BASE_URL = "https://pacs.example.org/dicom-web/studies";

    /**
     * Default OID for Patient ID Issuer (IHE XDS-I.b requirement).
     * Must be a valid ISO OID.
     */
    private static final String DEFAULT_PATIENT_ID_ISSUER_OID = "1.2.3.4.5.6.7.8.9";

    /**
     * Default OID for Accession Number Issuer (IHE XDS-I.b requirement).
     * Must be a valid ISO OID.
     */
    private static final String DEFAULT_ACCESSION_ISSUER_OID = "1.2.3.4.5.6.7.8.10";

    /**
     * Default Repository UID for XDS-I.b Retrieve Location.
     * Per IHE RAD TF-3 Section 4.68.4.1.2.2, the Retrieve Location UID (0040,E011)
     * identifies the XDS-I.b Imaging Document Source where the images can be retrieved.
     */
    private static final String DEFAULT_RETRIEVE_LOCATION_UID = "1.2.3.4.5.6.7.8.9.10";

    /**
     * Generation knobs for the KOS size/shape.
     *
     * Contract:
     * - evidenceSeriesCount controls how many series appear in Evidence (CurrentRequestedProcedureEvidenceSequence)
     * - sopInstanceCount controls how many SOP references appear overall (distributed across series)
     * - modalities is the pool used to assign SOPClassUIDs (and impacts variety)
     * - multiframe toggles using Enhanced CT storage for some/all references
     *
     * IMPORTANT: Per DICOM/IHE XDS-I specification, ALL instances in the Evidence Sequence MUST
     * appear in the SR Content Tree. The keyImageCount parameter is deprecated and ignored.
     */
    public static class Options {
        public int evidenceSeriesCount = 1;
        public int sopInstanceCount = 6;
        public String[] modalities = new String[]{
                UID.CTImageStorage,
                UID.MRImageStorage,
                UID.DigitalXRayImageStorageForPresentation,
                UID.UltrasoundImageStorage,
                UID.SecondaryCaptureImageStorage
        };

        /**
         * If true, references are allowed to be multiframe objects (Enhanced SOP classes).
         * For now we map "multiframe" to Enhanced CT.
         */
        public boolean multiframe = false;

        /**
         * @deprecated This parameter is ignored. Per spec, ALL instances in Evidence must appear in Content Tree.
         * Kept for backward compatibility only.
         */
        @Deprecated
        public int keyImageCount = -1;

        public Options withEvidenceSeriesCount(int v) {
            this.evidenceSeriesCount = Math.max(1, v);
            return this;
        }

        public Options withSopInstanceCount(int v) {
            this.sopInstanceCount = Math.max(1, v);
            return this;
        }

        public Options withModalities(String... sopClassUids) {
            if (sopClassUids != null && sopClassUids.length > 0) {
                this.modalities = sopClassUids;
            }
            return this;
        }

        public Options withMultiframe(boolean v) {
            this.multiframe = v;
            return this;
        }

        public Options withKeyImageCount(int v) {
            // Deprecated: parameter ignored, kept for backward compatibility
            this.keyImageCount = v;
            return this;
        }

        /**
         * @deprecated Always returns sopInstanceCount since all instances must be in content tree per spec.
         */
        @Deprecated
        private int resolvedKeyImageCount() {
            // Per DICOM/IHE spec: ALL instances in Evidence MUST be in Content Tree
            return sopInstanceCount;
        }
    }


    public static void main(String[] args) throws Exception {
        // Generate a single KOS file
        int count = 1;
        boolean useRandomSizes = true; // preserve historical behavior unless overridden

        if (args != null && args.length > 0) {
            for (String a : args) {
                if (a == null) continue;
                String token = a.trim();
                if (token.isEmpty()) continue;

                if ("--help".equalsIgnoreCase(token) || "-h".equalsIgnoreCase(token)) {
                    System.out.println("Usage: IHEKOSSampleCreator [count] [--default-sizes] [--random-sizes] [--help]");
                    System.out.println("  count           : number of KOS files to generate (default 1)");
                    System.out.println("  --default-sizes : use deterministic default size/shape options instead of random sizes");
                    System.out.println("  --random-sizes  : explicitly use random sizes (default behavior)");
                    return;
                } else if ("--default-sizes".equalsIgnoreCase(token)) {
                    useRandomSizes = false;
                } else if ("--random-sizes".equalsIgnoreCase(token)) {
                    useRandomSizes = true;
                } else {
                    try {
                        count = Math.max(1, Integer.parseInt(token));
                    } catch (NumberFormatException ignore) {
                        // ignore unknown token
                    }
                }
            }
        }

        File outDir = new File(System.getProperty("user.dir"));
        for (int i = 0; i < count; i++) {
            // Choose random parameters for each file unless deterministic defaults requested
            Options options = useRandomSizes ? generateRandomOptions() : defaultOptions();

            Attributes kos = createRandomIHEKOS(options);
            // Keep ourselves honest: never write a KOS with empty required sequences.
            assertNotEmptySequence(kos, Tag.CurrentRequestedProcedureEvidenceSequence, "CurrentRequestedProcedureEvidenceSequence");
            assertNotEmptySequence(kos, Tag.ConceptNameCodeSequence, "ConceptNameCodeSequence");
            assertNotEmptySequence(kos, Tag.ContentTemplateSequence, "ContentTemplateSequence");

            File out = new File(outDir, "IHEKOS_" + i + ".dcm");
            writeDicomFile(out, kos);
            System.out.println("Wrote: " + out.getAbsolutePath() + " (sopInstances=" + options.sopInstanceCount +
                    ", evidenceSeries=" + options.evidenceSeriesCount + ", modalities=" + options.modalities.length + ")");
        }
    }

    /**
     * Generate random options with balanced parameters:
     * - sopInstanceCount: 10-10000
     * - evidenceSeriesCount: 1-30 (but <= sopInstanceCount)
     * - modalities: 1-4 different types
     *
     * Note: ALL instances are included in the content tree per DICOM/IHE spec.
     */
    public static Options generateRandomOptions() {
        Options options = new Options();

        // Random instance count (10-10000)
        options.sopInstanceCount = 10 + randomInt(9991); // 10 to 10000

        // Random series count (1-30), but can't exceed instance count
        int maxSeries = Math.min(30, options.sopInstanceCount);
        options.evidenceSeriesCount = 1 + randomInt(maxSeries);

        // Random modality count (1-4)
        int modalityCount = 1 + randomInt(4); // 1 to 4
        String[] allModalities = new String[]{
            UID.CTImageStorage,
            UID.MRImageStorage,
            UID.DigitalXRayImageStorageForPresentation,
            UID.UltrasoundImageStorage,
            UID.SecondaryCaptureImageStorage
        };
        String[] selectedModalities = new String[modalityCount];
        for (int i = 0; i < modalityCount; i++) {
            selectedModalities[i] = allModalities[i];
        }
        options.modalities = selectedModalities;

        // Random multiframe setting (50% chance)
        options.multiframe = randomInt(2) == 1;

        return options;
    }

    /** Default behavior is identical to the original generator. */
    public static Options defaultOptions() {
        return new Options();
    }

    /** New entrypoint for programmatic use (tests/benchmarks/etc.). */
    public static Attributes createRandomIHEKOS(Options options) {
        if (options == null) options = defaultOptions();

        Attributes d = new Attributes();

        // --- SOP Common ---
        d.setString(Tag.SOPClassUID, VR.UI, UID.KeyObjectSelectionDocumentStorage);
        String kosSopInstanceUid = createNormalizedUid();
        d.setString(Tag.SOPInstanceUID, VR.UI, kosSopInstanceUid);

        // --- Patient IE (Patient Module: Type 2 in DICOM, but we populate) ---
        d.setString(Tag.PatientName, VR.PN, randomPersonName());
        d.setString(Tag.PatientID, VR.LO, randomPatientId());
        d.setString(Tag.IssuerOfPatientID, VR.LO, randomIssuer());

        // XDS-I.b requires IssuerOfPatientIDQualifiersSequence with UniversalEntityID + type ISO
        // for federated patient identification across enterprises
        addPatientIDQualifiers(d, DEFAULT_PATIENT_ID_ISSUER_OID);

        d.setString(Tag.PatientBirthDate, VR.DA, randomDateYYYYMMDD(1940, 2015));
        d.setString(Tag.PatientSex, VR.CS, randomFrom("M", "F", "O"));

        // --- Study IE (General Study) ---
        String studyInstanceUid = createNormalizedUid();
        d.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUid);
        d.setString(Tag.StudyID, VR.SH, String.format("%04d", 1 + randomInt(9999)));
        d.setString(Tag.StudyDate, VR.DA, todayYYYYMMDD());
        d.setString(Tag.StudyTime, VR.TM, nowHHMMSS());
        String accessionNumber = randomAccession();
        d.setString(Tag.AccessionNumber, VR.SH, accessionNumber);

        // XDS-I.b requires Issuer of Accession Number Sequence for cross-enterprise procedure tracking
        addAccessionNumberIssuer(d, DEFAULT_ACCESSION_ISSUER_OID);

        d.setString(Tag.ReferringPhysicianName, VR.PN, randomPersonName());

        // --- Series IE (Key Object Document Series) ---
        d.setString(Tag.Modality, VR.CS, "KO");
        d.setString(Tag.SeriesInstanceUID, VR.UI, createNormalizedUid());
        d.setInt(Tag.SeriesNumber, VR.IS, 1 + randomInt(99));
        // Type 2 sequence: include empty item to satisfy "present" semantics.
        d.newSequence(Tag.ReferencedPerformedProcedureStepSequence, 1).add(new Attributes());

        // --- Equipment IE ---
        d.setString(Tag.Manufacturer, VR.LO, randomFrom("ACME Imaging", "Globex Medical", "Initech", "Umbrella Radiology"));

        // --- Key Object Document Module ---
        d.setInt(Tag.InstanceNumber, VR.IS, 1 + randomInt(999));
        d.setString(Tag.ContentDate, VR.DA, todayYYYYMMDD());
        d.setString(Tag.ContentTime, VR.TM, nowHHMMSS());

        // SR General / XDS-I profile requirements
        d.setString(Tag.CompletionFlag, VR.CS, DicomConstants.COMPLETION_FLAG_COMPLETE);
        d.setString(Tag.VerificationFlag, VR.CS, DicomConstants.VERIFICATION_FLAG_UNVERIFIED);
        d.setString(Tag.TimezoneOffsetFromUTC, VR.SH, timezoneOffsetFromUTC());

        // ReferencedRequestSequence is Type 2 (must be present) and required by this validator
        // XDS-I.b requires issuer qualification for Accession Number
        populateReferencedRequestSequenceWithIssuer(d, studyInstanceUid, accessionNumber, DEFAULT_ACCESSION_ISSUER_OID);

        // --- SR Document Content Module (Root container) ---
        d.setString(Tag.ValueType, VR.CS, "CONTAINER");
        d.setString(Tag.ContinuityOfContent, VR.CS, DicomConstants.CONTINUITY_SEPARATE);

        // Document Title
        Sequence conceptName = d.newSequence(Tag.ConceptNameCodeSequence, 1);
        conceptName.add(code(CODE_MANIFEST, SCHEME_DCM, MEANING_MANIFEST));

        // ContentTemplateSequence identifies TID 2010 / DCMR
        Sequence cts = d.newSequence(Tag.ContentTemplateSequence, 1);
        cts.add(createTemplateItem("2010"));

        // Build a single shared reference list so Evidence and ContentSequence stay consistent.
        List<Attributes> referencedSops = buildReferencedSops(options);

        // Evidence
        populateEvidenceMultiSeries(d, studyInstanceUid, referencedSops, options.evidenceSeriesCount);

        // Content tree: build only from the same SOPs that appear in Evidence.
        populateContentTree(d, referencedSops, options.resolvedKeyImageCount());

        // Small extras that are commonly present
        d.setString(Tag.SpecificCharacterSet, VR.CS, "ISO_IR 100");

        return d;
    }

    // Backwards-compat shim: keep signature used nowhere else but safe.
    private static Attributes createRandomIHEKOS() {
        return createRandomIHEKOS(defaultOptions());
    }

    /**
     * Populate the SR Content Tree with ALL instances from the Evidence Sequence.
     * Per DICOM PS3.3 and IHE XDS-I: The Content Tree is the "playlist" that selects images.
     * ALL instances listed in Evidence MUST appear in the Content Tree.
     *
     * @param d The dataset to populate
     * @param referencedSops List of ALL SOP instances from Evidence
     * @param imageItemCount Deprecated parameter, ignored (kept for backward compatibility)
     */
    private static void populateContentTree(Attributes d, List<Attributes> referencedSops, int imageItemCount) {
        // Per spec: ALL instances in Evidence must be in Content Tree
        // The imageItemCount parameter is ignored
        int actualCount = referencedSops.size();
        Sequence content = d.newSequence(Tag.ContentSequence, actualCount);

        // Add ALL referenced SOPs to content tree
        for (Attributes sop : referencedSops) {
            Attributes img = createImageItem(DicomConstants.RELATIONSHIP_CONTAINS,
                    code(CODE_IMAGE, SCHEME_DCM, MEANING_IMAGE),
                    sop.getString(Tag.ReferencedSOPClassUID),
                    sop.getString(Tag.ReferencedSOPInstanceUID));
            content.add(img);
        }
    }

    private static void populateEvidenceMultiSeries(Attributes dataset, String studyInstanceUID,
                                                   List<Attributes> referencedSops, int seriesCount) {
        int resolvedSeriesCount = Math.max(1, seriesCount);

        Sequence evidence = dataset.newSequence(Tag.CurrentRequestedProcedureEvidenceSequence, 1);
        Attributes studyItem = new Attributes();
        studyItem.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUID);

        Sequence refSeries = studyItem.newSequence(Tag.ReferencedSeriesSequence, resolvedSeriesCount);

        // Distribute SOPs across series.
        int total = Math.max(1, referencedSops.size());
        int idx = 0;
        for (int s = 0; s < resolvedSeriesCount; s++) {
            Attributes seriesItem = new Attributes();
            String seriesInstanceUID = createNormalizedUid();
            seriesItem.setString(Tag.SeriesInstanceUID, VR.UI, seriesInstanceUID);

            // CRITICAL XDS-I.b REQUIREMENT: Retrieve Location UID (0040,E011)
            // Per IHE RAD TF-3 Section 4.68.4.1.2.2: "The KOS object shall include the Retrieve Location UID
            // (0040,E011) attribute... within the Referenced Series Sequence (0008,1115) within the
            // Current Requested Procedure Evidence Sequence (0040,A375)."
            // This identifies the XDS-I.b Imaging Document Source where images can be retrieved.
            seriesItem.setString(Tag.RetrieveLocationUID, VR.UI, DEFAULT_RETRIEVE_LOCATION_UID);

            // Per IHE XDS-I.b DICOM Retrieve by WADO-RS Option:
            // Retrieve URL (0008,1190) provides direct WADO-RS endpoint for web-based retrieval
            String wadoRsUrl = String.format("%s/%s/series/%s",
                    DEFAULT_WADO_RS_BASE_URL, studyInstanceUID, seriesInstanceUID);
            seriesItem.setString(Tag.RetrieveURL, VR.UR, wadoRsUrl);

            int remainingSeries = resolvedSeriesCount - s;
            int remainingSops = total - idx;
            int take = (s == resolvedSeriesCount - 1) ? remainingSops : Math.max(1, remainingSops / remainingSeries);

            Sequence refSops = seriesItem.newSequence(Tag.ReferencedSOPSequence, take);
            for (int j = 0; j < take && idx < total; j++, idx++) {
                Attributes sop = referencedSops.get(idx);
                Attributes ref = new Attributes();
                ref.setString(Tag.ReferencedSOPClassUID, VR.UI, sop.getString(Tag.ReferencedSOPClassUID));
                ref.setString(Tag.ReferencedSOPInstanceUID, VR.UI, sop.getString(Tag.ReferencedSOPInstanceUID));
                refSops.add(ref);
            }

            refSeries.add(seriesItem);
        }

        evidence.add(studyItem);
    }

    private static List<Attributes> buildReferencedSops(Options options) {
        int sopCount = Math.max(1, options.sopInstanceCount);
        List<Attributes> sops = new ArrayList<>(sopCount);

        String[] pool = (options.modalities != null && options.modalities.length > 0)
                ? options.modalities
                : defaultOptions().modalities;

        for (int i = 0; i < sopCount; i++) {
            Attributes sop = new Attributes();

            String sopClass;
            if (options.multiframe) {
                // Simple heuristic: mix in some multiframe storage.
                // Enhanced CT is widely recognized and already in repo usage.
                sopClass = (i % 4 == 0) ? UID.EnhancedCTImageStorage : pool[i % pool.length];
            } else {
                sopClass = pool[i % pool.length];
            }

            sop.setString(Tag.ReferencedSOPClassUID, VR.UI, sopClass);
            sop.setString(Tag.ReferencedSOPInstanceUID, VR.UI, createNormalizedUid());
            sops.add(sop);
        }

        return sops;
    }
}
