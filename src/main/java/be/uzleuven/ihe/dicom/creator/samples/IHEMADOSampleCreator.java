package be.uzleuven.ihe.dicom.creator.samples;

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

public class IHEMADOSampleCreator {
    // Note: Code constants are now imported from CodeConstants class


    // Dummy values that satisfy this project's profile checks
    private static final String DEFAULT_INSTITUTION_NAME = "IHE Demo Hospital";
    private static final String DEFAULT_PATIENT_ID_ISSUER = "HUPA";
    private static final String DEFAULT_PATIENT_ID_ISSUER_OID = "1.2.3.4.5.6.7.8.9";
    private static final String DEFAULT_ACCESSION_ISSUER_OID = "2.16.840.1.113883.3.72.5.9.1";
    private static final String DEFAULT_RETRIEVE_LOCATION_UID = "1.2.3.4.5.6.7.8.9.10";

    /**
     * Parameterization for MADO size/shape.
     *
     * Defaults are chosen to keep the current file identical in structure:
     * - 5 series total
     * - CT single-frame: 5 instances
     * - CT multiframe: 1 instance (Enhanced CT)
     * - OT: 1
     * - US: 1
     * - KO (KIN): 1
     * - keyImageCount = 3 (the existing addKinDescriptors behavior)
     */
    public static class Options {
        /** Total series in the referenced study (including optional KIN series). */
        public int seriesCount = 5;

        /** Ensure a KIN (Key Object) instance exists and is referenced as-is. */
        public boolean includeKIN = true;

        /** Modalities pool for non-KIN series. Uses DICOM modality codes ("CT", "MR", etc.). */
        public String[] modalityPool = new String[]{"CT", "OT", "US"};

        /** Total number of referenced instances across non-KIN series. */
        public int totalInstanceCount = 8; // matches default: 5 + 1 + 1 + 1

        /**
         * If true, generate one multiframe-like instance (Enhanced CT) as part of the total.
         * (Keeps compatibility with current validator expectations.)
         */
        public boolean includeMultiframe = true;

        /** When includeMultiframe=true, number of multiframe instances to generate. */
        public int multiframeInstanceCount = 1;

        /**
         * How many referenced "key images" (UIDREF items) to put in KIN descriptors.
         * NOTE: This does NOT limit instances in the content tree. ALL instances in Evidence
         * are always included in the Image Library per DICOM/IHE spec. This parameter only
         * affects how many images the nested KIN (Key Image Note) describes via UIDREF items.
         */
        public int keyImageCount = 3;

        public Options withSeriesCount(int v) {
            this.seriesCount = Math.max(1, v);
            return this;
        }

        public Options withTotalInstanceCount(int v) {
            this.totalInstanceCount = Math.max(1, v);
            return this;
        }

        public Options withModalityPool(String... modalities) {
            if (modalities != null && modalities.length > 0) this.modalityPool = modalities;
            return this;
        }

        public Options withIncludeKIN(boolean v) {
            this.includeKIN = v;
            return this;
        }

        public Options withIncludeMultiframe(boolean v) {
            this.includeMultiframe = v;
            return this;
        }

        public Options withMultiframeInstanceCount(int v) {
            this.multiframeInstanceCount = Math.max(0, v);
            return this;
        }

        public Options withKeyImageCount(int v) {
            this.keyImageCount = Math.max(0, v);
            return this;
        }
    }

    public static Options defaultOptions() {
        return new Options();
    }

    public static void main(String[] args) throws Exception {
        // Generate a single random MADO file with random parameters
        int count = 1;
        boolean useRandomSizes = true; // preserve historical behavior unless overridden
        boolean useKOSDefaultSizes = false; // if true, derive defaults from IHEKOSSampleCreator
        if (args != null && args.length > 0) {
            for (String a : args) {
                if ("--help".equalsIgnoreCase(a) || "-h".equalsIgnoreCase(a)) {
                    System.out.println("Usage: IHEMADOSampleCreator [count] [--default-sizes] [--random-sizes] [--help]");
                    System.out.println("  count           : number of MADO files to generate (default 1)");
                    System.out.println("  --default-sizes : use deterministic default size/shape options instead of random sizes");
                    System.out.println("  --random-sizes  : explicitly use random sizes (default behavior)");
                    return;
                } else if ("--default-sizes".equalsIgnoreCase(a)) {
                    // Use deterministic defaults. Prefer KOS default sizing if available to keep KOS/MADO
                    // sample sizes aligned. This flag now indicates using the KOS default size mapping.
                    useRandomSizes = false;
                    useKOSDefaultSizes = true;
                } else if ("--random-sizes".equalsIgnoreCase(a)) {
                    useRandomSizes = true;
                    useKOSDefaultSizes = false;
                } else {
                    try {
                        count = Math.max(1, Integer.parseInt(a));
                    } catch (NumberFormatException ignore) {
                        // ignore unknown token
                    }
                }
            }
        }

        File outDir = new File(System.getProperty("user.dir"));

        for (int i = 0; i < count; i++) {
            // Choose options for each file (random or deterministic default)
            Options options;
            if (useKOSDefaultSizes) {
                // Map KOS defaults to MADO Options so generated MADO files are comparable to KOS defaults
                IHEKOSSampleCreator.Options kos = IHEKOSSampleCreator.defaultOptions();
                options = optionsFromKOSOptions(kos);
            } else {
                options = useRandomSizes ? generateRandomOptions() : defaultOptions();
            }

            // 1. Generate the Simulated Study Structure
            SimulatedStudy study = generateSimulatedStudy(options);

            // 2. Create the MADO Manifest Attributes
            Attributes madoKos = createMADOAttributes(study);

            // 3. Write to file
            String filename = "IHE_MADO_" + i + ".dcm";
            File outFile = new File(outDir, filename);
            writeDicomFile(outFile, madoKos);

            System.out.println("Created MADO Manifest: " + outFile.getAbsolutePath() +
                " (series=" + options.seriesCount + ", totalInstances=" + options.totalInstanceCount +
                ", modalities=" + options.modalityPool.length + ", multiframe=" + options.multiframeInstanceCount +
                ", keyImages=" + options.keyImageCount + ")");
        }
    }

    /**
     * Generate random options with balanced parameters designed to be comparable to
     * {@link IHEKOSSampleCreator#generateRandomOptions()}.
     *
     * Shared ranges (KOS & MADO):
     * - totalInstanceCount: 10-10000
     * - seriesCount: 1-30 (but <= totalInstanceCount)
     * - modalityPool size: 1-4
     * - multiframe: ~50% chance
     *
     * MADO-only extras are kept small so manifests stay comparable in evidence size:
     * - includeKIN always true (kept for MADO profile behavior)
     * - multiframeInstanceCount is capped to avoid dominating the study
     * - keyImageCount is capped to <= 5% of non-KIN instances
     */
    public static Options generateRandomOptions() {
        Options options = new Options();

        // Keep comparable with KOS: 10-10000 instances
        options.totalInstanceCount = 10 + randomInt(9991); // 10..10000

        // Keep comparable with KOS: 1-30 series, but can't exceed instance count
        int maxSeries = Math.min(30, options.totalInstanceCount);
        options.seriesCount = 1 + randomInt(maxSeries);

        // Keep comparable with KOS: 1-4 modalities
        int modalityCount = 1 + randomInt(4); // 1..4
        String[] allModalities = new String[]{"CT", "MR", "US", "OT"};
        String[] selectedModalities = new String[modalityCount];
        for (int i = 0; i < modalityCount; i++) {
            // Deterministic (non-random) selection is fine here: we only need variety and stable distribution.
            selectedModalities[i] = allModalities[i % allModalities.length];
        }
        options.modalityPool = selectedModalities;

        // MADO keeps KIN enabled by default; it adds 1 referenced instance.
        options.includeKIN = true;

        // Keep comparable with KOS multiframe toggle: 50% chance.
        options.includeMultiframe = false;//randomInt(200) == 100;

        // If multiframe is enabled, keep counts modest so it doesn't skew sizes.
        // Cap multiframe to at most 25% of total instances, and hard-cap to 100 to avoid extreme cases.
        if (options.includeMultiframe) {
            int maxByPct = Math.max(1, options.totalInstanceCount / 4);
            int maxMultiframe = Math.min(100, Math.min(options.totalInstanceCount, maxByPct));
            options.multiframeInstanceCount = 1 + randomInt(maxMultiframe);
        } else {
            options.multiframeInstanceCount = 0;
        }

        // Key images are only descriptive UIDREFs inside the KIN descriptors.
        // Cap to <= 5% of non-KIN instances to keep MADO overhead small.
        int nonKinInstances = Math.max(1, options.totalInstanceCount - 1);
        int capByPct = Math.max(1, (int) Math.floor(nonKinInstances * 0.05));
        int maxKeyImages = Math.min(500, Math.min(nonKinInstances, capByPct));
        options.keyImageCount = (maxKeyImages <= 1) ? 1 : (1 + randomInt(maxKeyImages));

        return options;
    }

    /**
     * Map KOS Options to MADO Options so --default-sizes uses KOS defaults translated into MADO semantics.
     */
    private static Options optionsFromKOSOptions(IHEKOSSampleCreator.Options kos) {
        Options opt = new Options();
        if (kos == null) return opt;

        // KOS sopInstanceCount roughly maps to MADO totalInstanceCount (minus the KIN instance we add)
        opt.totalInstanceCount = Math.max(1, kos.sopInstanceCount - 1);

        // KOS evidenceSeriesCount maps to MADO seriesCount (we add one for KIN later if includeKIN true)
        opt.seriesCount = Math.max(1, kos.evidenceSeriesCount + (opt.includeKIN ? 1 : 0));

        // Map modalities by converting SOP Class UIDs to modality codes; fall back to CT/OT/US
        if (kos.modalities != null && kos.modalities.length > 0) {
            String[] pool = new String[kos.modalities.length];
            for (int i = 0; i < kos.modalities.length; i++) {
                pool[i] = modalityFromSopClass(kos.modalities[i]);
            }
            opt.modalityPool = pool;
        }

        // Map multiframe flag: if KOS multiframe true, include a small number of multiframe MADO instances.
        opt.includeMultiframe = kos.multiframe;
        opt.multiframeInstanceCount = kos.multiframe ? Math.max(1, Math.min(5, kos.sopInstanceCount / 10)) : 0;

        // keyImageCount: keep small descriptive number
        opt.keyImageCount = 3;

        return opt;
    }

    private static String modalityFromSopClass(String sopClassUid) {
        if (sopClassUid == null) return "CT";
        switch (sopClassUid) {
            case UID.MRImageStorage:
                return "MR";
            case UID.UltrasoundImageStorage:
                return "US";
            case UID.DigitalXRayImageStorageForPresentation:
            case UID.DigitalXRayImageStorageForProcessing:
                return "CR"; // map to CR (treated as SecondaryCapture fallback)
            case UID.SecondaryCaptureImageStorage:
                return "OT";
            case UID.CTImageStorage:
            default:
                return "CT";
        }
    }

    public static Attributes createMADOFromOptions(Options options) {
        SimulatedStudy study = generateSimulatedStudy(options);
        return createMADOAttributes(study);
    }

    public static void writeMADO(Options options, File outFile) throws java.io.IOException {
        if (options == null) options = defaultOptions();
        Attributes attrs = createMADOFromOptions(options);
        writeDicomFile(outFile, attrs);
    }

    private static Attributes createMADOAttributes(SimulatedStudy study) {
        Attributes d = new Attributes();
        String manifestSOPUid = UIDUtils.createUID();

        String studyDate = now("yyyyMMdd");
        String studyTime = now("HHmmss");

        // --- Standard Headers ---
        d.setString(Tag.SOPClassUID, VR.UI, UID.KeyObjectSelectionDocumentStorage);
        d.setString(Tag.SOPInstanceUID, VR.UI, manifestSOPUid);
        d.setString(Tag.StudyInstanceUID, VR.UI, study.studyInstanceUID);
        d.setString(Tag.SeriesInstanceUID, VR.UI, UIDUtils.createUID());
        d.setString(Tag.Modality, VR.CS, "KO");
        d.setInt(Tag.SeriesNumber, VR.IS, 999); // Manifest Series
        d.setInt(Tag.InstanceNumber, VR.IS, 1);

        // Series module Type 2 attribute (must be present even if empty)
        d.newSequence(Tag.ReferencedPerformedProcedureStepSequence, 0);

        // --- Study IE (Type 2 attributes must be present even if empty) ---
        d.setString(Tag.StudyDate, VR.DA, studyDate);
        d.setString(Tag.StudyTime, VR.TM, studyTime);
        d.setString(Tag.ReferringPhysicianName, VR.PN, "^");
        d.setString(Tag.StudyID, VR.SH, "STUDY-001");

        // --- Patient IE ---
        d.setString(Tag.PatientName, VR.PN, "MADO^TEST^PATIENT");
        d.setString(Tag.PatientID, VR.LO, "MADO-12345");
        d.setString(Tag.IssuerOfPatientID, VR.LO, DEFAULT_PATIENT_ID_ISSUER);
        d.setString(Tag.PatientBirthDate, VR.DA, "19870828");
        d.setString(Tag.PatientSex, VR.CS, "O");

        // MADO requires IssuerOfPatientIDQualifiersSequence with UniversalEntityID + type ISO
        addPatientIDQualifiers(d, DEFAULT_PATIENT_ID_ISSUER_OID);

        // --- Accession + Issuer ---
        d.setString(Tag.AccessionNumber, VR.SH, "ACC-MADO-001");
        addAccessionNumberIssuer(d, DEFAULT_ACCESSION_ISSUER_OID);

        // --- Equipment IE ---
        d.setString(Tag.Manufacturer, VR.LO, "IHE_MADO_CREATOR");
        d.setString(Tag.InstitutionName, VR.LO, DEFAULT_INSTITUTION_NAME);

        // MADO Required: Timezone
        d.setString(Tag.TimezoneOffsetFromUTC, VR.SH, "+0000");

        // --- Key Object Document Module (Content Date/Time required) ---
        d.setString(Tag.ContentDate, VR.DA, studyDate);
        d.setString(Tag.ContentTime, VR.TM, studyTime);

        // --- ReferencedRequestSequence (Type 2, MADO semantics require non-empty) ---
        populateReferencedRequestSequenceWithIssuer(d, study.studyInstanceUID,
                                                   d.getString(Tag.AccessionNumber),
                                                   DEFAULT_ACCESSION_ISSUER_OID);

        // --- Evidence Sequence (Current Requested Procedure Evidence) ---
        // Must list ALL instances in the study
        // For MADO retrieval validation we also add per-series retrieval addressing.
        populateEvidence(d, study);

        // --- SR Content (TID 2010 + TID 1600) ---
        d.setString(Tag.ValueType, VR.CS, "CONTAINER");
        d.setString(Tag.ContinuityOfContent, VR.CS, DicomConstants.CONTINUITY_SEPARATE);

        // Root Concept: Manifest with Description (Triggers MADO logic)
        d.newSequence(Tag.ConceptNameCodeSequence, 1)
                .add(code(CODE_MANIFEST_WITH_DESCRIPTION, SCHEME_DCM, MEANING_MANIFEST_WITH_DESCRIPTION));

        d.newSequence(Tag.ContentTemplateSequence, 1)
                .add(createTemplateItem("2010"));

        // Completion / Verification flags (warnings otherwise)
        d.setString(Tag.CompletionFlag, VR.CS, be.uzleuven.ihe.dicom.constants.DicomConstants.COMPLETION_FLAG_COMPLETE);
        d.setString(Tag.VerificationFlag, VR.CS, be.uzleuven.ihe.dicom.constants.DicomConstants.VERIFICATION_FLAG_UNVERIFIED);

        // Content Sequence
        Sequence contentSeq = d.newSequence(Tag.ContentSequence, 10);

        // TID 1600 Study-level Acquisition Context requirements (repo validator expects these at ROOT ContentSequence)
        // NOTE: For XDS-I manifests, this project's validator requires top-level RelationshipType=CONTAINS.
        contentSeq.add(createCodeItem(be.uzleuven.ihe.dicom.constants.DicomConstants.RELATIONSHIP_CONTAINS, CODE_MODALITY, SCHEME_DCM, MEANING_MODALITY,
                code(CODE_MODALITY_CT, SCHEME_DCM, MEANING_MODALITY_CT)));
        contentSeq.add(createUIDRefItem(be.uzleuven.ihe.dicom.constants.DicomConstants.RELATIONSHIP_CONTAINS, CODE_STUDY_INSTANCE_UID, SCHEME_DCM, MEANING_STUDY_INSTANCE_UID,
                study.studyInstanceUID));
        contentSeq.add(createCodeItem(be.uzleuven.ihe.dicom.constants.DicomConstants.RELATIONSHIP_CONTAINS, CODE_TARGET_REGION, SCHEME_DCM, MEANING_TARGET_REGION,
                code(CODE_REGION_ABDOMEN, SCHEME_SRT, MEANING_REGION_ABDOMEN)));

        // Note: do NOT add a separate top-level IMAGE reference to the KIN.
        // The KIN is referenced within the Image Library entries already, and this project
        // checks for duplicate ReferencedSOPInstanceUID values across the entire SR tree.

        // B. MADO Image Library Extension (TID 1600)
        Attributes libContainer = new Attributes();
        libContainer.setString(Tag.RelationshipType, VR.CS, be.uzleuven.ihe.dicom.constants.DicomConstants.RELATIONSHIP_CONTAINS);
        libContainer.setString(Tag.ValueType, VR.CS, "CONTAINER");
        libContainer.newSequence(Tag.ConceptNameCodeSequence, 1)
                .add(code(CODE_IMAGE_LIBRARY, SCHEME_DCM, MEANING_IMAGE_LIBRARY));

        Sequence libContent = libContainer.newSequence(Tag.ContentSequence, 20);

        // TID 1600 Study-level Acquisition Context requirements (validator expects these directly under Image Library)
        // 1) Modality (CODE) 2) Study Instance UID (UIDREF) 3) Target Region (CODE)
        // We model "study modality" as CT here.
        libContent.add(createCodeItem("HAS ACQ CONTEXT", CODE_MODALITY, SCHEME_DCM, MEANING_MODALITY,
                code(CODE_MODALITY_CT, SCHEME_DCM, MEANING_MODALITY_CT)));
        libContent.add(createUIDRefItem("HAS ACQ CONTEXT", CODE_STUDY_INSTANCE_UID, SCHEME_DCM, MEANING_STUDY_INSTANCE_UID,
                study.studyInstanceUID));
        // Target Region: validator only enforces presence; use SNM/SCT/FMA to avoid info-level note.
        libContent.add(createCodeItem("HAS ACQ CONTEXT", CODE_TARGET_REGION, SCHEME_DCM, MEANING_TARGET_REGION,
                code(CODE_REGION_ABDOMEN, SCHEME_SRT, MEANING_REGION_ABDOMEN)));

        // Populate Library Groups (One per Series)
        int seriesNumber = 1;
        for (SimulatedSeries series : study.seriesList) {
            series.seriesNumber = seriesNumber++;
            series.seriesDate = studyDate;
            series.seriesTime = randomSeriesTime(studyTime);

            Attributes group = new Attributes();
            group.setString(Tag.RelationshipType, VR.CS, be.uzleuven.ihe.dicom.constants.DicomConstants.RELATIONSHIP_CONTAINS);
            group.setString(Tag.ValueType, VR.CS, "CONTAINER");
            group.newSequence(Tag.ConceptNameCodeSequence, 1)
                    .add(code(CODE_IMAGE_LIBRARY_GROUP, SCHEME_DCM, MEANING_IMAGE_LIBRARY_GROUP));

            Sequence groupSeq = group.newSequence(Tag.ContentSequence, 20);

            // Series Level Metadata (TID 1602)
            groupSeq.add(createCodeItem("HAS ACQ CONTEXT", CODE_MODALITY, SCHEME_DCM, MEANING_MODALITY,
                    code(series.modality, SCHEME_DCM, series.modality)));
            groupSeq.add(createUIDRefItem("HAS ACQ CONTEXT", CODE_SERIES_INSTANCE_UID, SCHEME_DCM, MEANING_SERIES_INSTANCE_UID, series.seriesUID));
            groupSeq.add(createTextItem("HAS ACQ CONTEXT", CODE_SERIES_DESCRIPTION, SCHEME_DCM, MEANING_SERIES_DESCRIPTION, series.description));
            groupSeq.add(createTextItem("HAS ACQ CONTEXT", CODE_SERIES_DATE, SCHEME_DCM, MEANING_SERIES_DATE, series.seriesDate));
            groupSeq.add(createTextItem("HAS ACQ CONTEXT", CODE_SERIES_TIME, SCHEME_DCM, MEANING_SERIES_TIME, series.seriesTime));
            // KOS TID 2010 forbids NUM, so represent the series number as TEXT.
            groupSeq.add(createTextItem("HAS ACQ CONTEXT", CODE_SERIES_NUMBER, SCHEME_DCM, MEANING_SERIES_NUMBER, Integer.toString(series.seriesNumber)));
            // Number of Series Related Instances (NUM) - Required by MADO TID 1602
            groupSeq.add(createNumericItem("HAS ACQ CONTEXT", CODE_NUM_SERIES_RELATED_INSTANCES, SCHEME_DCM,
                    MEANING_NUM_SERIES_RELATED_INSTANCES, series.instances.size()));

            // Instance Level Entries (TID 1601)
            int instanceNumber = 1;
            for (SimulatedInstance inst : series.instances) {
                Attributes entry = new Attributes();
                entry.setString(Tag.RelationshipType, VR.CS, be.uzleuven.ihe.dicom.constants.DicomConstants.RELATIONSHIP_CONTAINS);
                entry.setString(Tag.ValueType, VR.CS, "IMAGE");

                Sequence refSop = entry.newSequence(Tag.ReferencedSOPSequence, 1);
                Attributes refItem = new Attributes();
                refItem.setString(Tag.ReferencedSOPClassUID, VR.UI, inst.sopClassUID);
                refItem.setString(Tag.ReferencedSOPInstanceUID, VR.UI, inst.sopInstanceUID);
                refSop.add(refItem);

                // Instance-level metadata (TID 1601)
                Sequence entryContent = entry.newSequence(Tag.ContentSequence, 5);

                // Instance Number (TEXT) - Required by MADO TID 1601 (RC+)
                entryContent.add(createTextItem("HAS ACQ CONTEXT", CODE_INSTANCE_NUMBER, SCHEME_DCM,
                        MEANING_INSTANCE_NUMBER, Integer.toString(instanceNumber)));

                // Number of Frames (NUM) - Required if multi-frame (RC+)
                if (isMultiframe(inst.sopClassUID)) {
                    entryContent.add(createNumericItem("HAS ACQ CONTEXT", CODE_NUMBER_OF_FRAMES, SCHEME_DCM,
                            MEANING_NUMBER_OF_FRAMES, 10));
                }

                // If this is the KIN instance, add KOS descriptors container.
                if (inst.isKIN) {
                    addKinDescriptors(entryContent, study);
                }

                groupSeq.add(entry);
                instanceNumber++;
            }
            libContent.add(group);
        }

        // IMPORTANT: attach the Image Library container to the root ContentSequence.
        contentSeq.add(libContainer);
        return d;
    }


    /**
     * Adds TID 16XX Descriptors to an Image Library Entry for a KOS/KIN instance.
     * This describes WHAT the KIN references without retrieving it.
     */
    private static void addKinDescriptors(Sequence entryContent, SimulatedStudy study) {
        // Container for KOS Descriptors (concept: Key Object Description)
        Attributes kosDesc = new Attributes();
        kosDesc.setString(Tag.RelationshipType, VR.CS, be.uzleuven.ihe.dicom.constants.DicomConstants.RELATIONSHIP_CONTAINS);
        kosDesc.setString(Tag.ValueType, VR.CS, "CONTAINER");
        kosDesc.newSequence(Tag.ConceptNameCodeSequence, 1)
                .add(code(CODE_KOS_DESCRIPTION, SCHEME_DCM, MEANING_KOS_DESCRIPTION));

        Sequence descSeq = kosDesc.newSequence(Tag.ContentSequence, 10);

        // KOS Title Code (required by this repo's validator for V-DESC-02)
        // Use an example from CID 7010-ish style; validator only checks presence.
        descSeq.add(createCodeItem(be.uzleuven.ihe.dicom.constants.DicomConstants.RELATIONSHIP_CONTAINS, CODE_KOS_TITLE, SCHEME_DCM, MEANING_KOS_TITLE,
                code(CODE_OF_INTEREST, SCHEME_DCM, MEANING_OF_INTEREST)));

        // KOS Description (optional)
        descSeq.add(createTextItem(be.uzleuven.ihe.dicom.constants.DicomConstants.RELATIONSHIP_CONTAINS, "ddd009", "DCM", "KOS Object Description", "Key Objects for Surgery"));

        // Flagged images: MADO TID 16XX requires IMAGE value type for visual references.
        // Row 4/5 of TID 16XX "Image Library Entry Descriptors for Key Object Selection"
        // specifies IMAGE or COMPOSITE value types (Status MC).

        // Select up to N referenced instances (IMAGE items) from NON-KIN instances to describe.
        int desired = study.options != null ? Math.max(0, study.options.keyImageCount) : 3;
        if (desired == 0) desired = 3; // default fallback

        // Collect non-KIN instances and mark multiframe candidates
        List<SimulatedInstance> multiframeCandidates = new ArrayList<>();
        List<SimulatedInstance> singleframeCandidates = new ArrayList<>();

        for (SimulatedSeries s : study.seriesList) {
            for (SimulatedInstance inst : s.instances) {
                if (inst.isKIN) continue;
                if (UID.EnhancedCTImageStorage.equals(inst.sopClassUID)) {
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

        // Add IMAGE items for selected instances (per MADO TID 16XX requirements)
        for (SimulatedInstance inst : selected) {
            Attributes imageItem = new Attributes();
            imageItem.setString(Tag.RelationshipType, VR.CS, be.uzleuven.ihe.dicom.constants.DicomConstants.RELATIONSHIP_CONTAINS);
            imageItem.setString(Tag.ValueType, VR.CS, "IMAGE"); // Must be IMAGE per MADO TID 16XX

            Sequence refSop = imageItem.newSequence(Tag.ReferencedSOPSequence, 1);
            Attributes refItem = new Attributes();
            refItem.setString(Tag.ReferencedSOPClassUID, VR.UI, inst.sopClassUID);
            refItem.setString(Tag.ReferencedSOPInstanceUID, VR.UI, inst.sopInstanceUID);
            refSop.add(refItem);

            descSeq.add(imageItem);
        }

        entryContent.add(kosDesc);
    }

    /**
     * Helper method to determine if a SOP Class UID represents a multiframe image.
     */
    private static boolean isMultiframe(String sopClassUID) {
        // Common multiframe SOP Classes
        return UID.EnhancedCTImageStorage.equals(sopClassUID) ||
               UID.EnhancedMRImageStorage.equals(sopClassUID) ||
               UID.EnhancedMRColorImageStorage.equals(sopClassUID) ||
               UID.EnhancedPETImageStorage.equals(sopClassUID) ||
               UID.EnhancedUSVolumeStorage.equals(sopClassUID) ||
               UID.EnhancedXAImageStorage.equals(sopClassUID) ||
               UID.EnhancedXRFImageStorage.equals(sopClassUID) ||
               UID.XRay3DAngiographicImageStorage.equals(sopClassUID) ||
               UID.XRay3DCraniofacialImageStorage.equals(sopClassUID) ||
               UID.BreastTomosynthesisImageStorage.equals(sopClassUID);
    }

    // --- Evidence Sequence Helper ---
    private static void populateEvidence(Attributes d, SimulatedStudy study) {
        Sequence evidence = d.newSequence(Tag.CurrentRequestedProcedureEvidenceSequence, 1);
        Attributes studyItem = new Attributes();
        studyItem.setString(Tag.StudyInstanceUID, VR.UI, study.studyInstanceUID);

        int seriesSize = Math.max(1, study.seriesList.size());
        Sequence seriesSeq = studyItem.newSequence(Tag.ReferencedSeriesSequence, seriesSize);

        for (SimulatedSeries s : study.seriesList) {
            Attributes seriesItem = new Attributes();
            seriesItem.setString(Tag.SeriesInstanceUID, VR.UI, s.seriesUID);

            // Retrieval addressing required by MADOProfile (Appendix B style is accepted by this validator)
            // Provide at least one method. We use RetrieveLocationUID.
            seriesItem.setString(Tag.RetrieveLocationUID, VR.UI, DEFAULT_RETRIEVE_LOCATION_UID);

            Sequence sopSeq = seriesItem.newSequence(Tag.ReferencedSOPSequence, Math.max(1, s.instances.size()));
            for (SimulatedInstance i : s.instances) {
                Attributes sopItem = new Attributes();
                sopItem.setString(Tag.ReferencedSOPClassUID, VR.UI, i.sopClassUID);
                sopItem.setString(Tag.ReferencedSOPInstanceUID, VR.UI, i.sopInstanceUID);
                sopSeq.add(sopItem);
            }
            seriesSeq.add(seriesItem);
        }
        evidence.add(studyItem);
    }

    // --- Simulation Data Generation ---
    public static SimulatedStudy generateSimulatedStudy(Options options) {
        if (options == null) options = defaultOptions();

        SimulatedStudy study = new SimulatedStudy();
        study.options = options;
        study.studyInstanceUID = UIDUtils.createUID();

        int seriesCount = Math.max(1, options.seriesCount);

        // Decide how many non-KIN series left.
        int nonKinSeriesCount = options.includeKIN ? Math.max(1, seriesCount - 1) : seriesCount;

        // Total instances to distribute among non-KIN series
        int totalInstances = Math.max(1, options.totalInstanceCount);

        int mfCount = (options.includeMultiframe) ? Math.min(Math.max(0, options.multiframeInstanceCount), totalInstances) : 0;
        int sfCount = Math.max(0, totalInstances - mfCount);

        // Build non-KIN series
        int remainingSf = sfCount;
        int remainingMf = mfCount;

        for (int s = 0; s < nonKinSeriesCount; s++) {
            // Pick a modality label for this series.
            String modality = pickModality(options.modalityPool, s);
            String desc = "Series " + (s + 1) + " " + modality;

            SimulatedSeries series = new SimulatedSeries(UIDUtils.createUID(), modality, desc);

            int seriesLeft = nonKinSeriesCount - s;

            // Allocate at least one instance per series.
            int minForThisSeries = 1;
            int sfTake = 0;
            int mfTake = 0;

            if (seriesLeft == 1) {
                sfTake = remainingSf;
                mfTake = remainingMf;
            } else {
                // Spread single-frame across series
                int sfAvg = (remainingSf > 0) ? (remainingSf / seriesLeft) : 0;
                sfTake = Math.min(remainingSf, sfAvg);

                // Put multiframe into the first series by default to mimic original structure.
                if (remainingMf > 0 && s == 1) {
                    mfTake = 1;
                }
            }

            // Ensure at least one instance total; borrow from remaining.
            int totalTake = sfTake + mfTake;
            if (totalTake < minForThisSeries) {
                if (remainingSf > 0) {
                    sfTake = minForThisSeries;
                } else if (remainingMf > 0) {
                    mfTake = minForThisSeries;
                }
            }

            // Generate instances.
            for (int i = 0; i < sfTake; i++) {
                series.addInstance(sopClassForSingleFrame(modality), UIDUtils.createUID(), false);
            }

            for (int i = 0; i < mfTake; i++) {
                // Enhanced CT used as multiframe proxy.
                series.addInstance(UID.EnhancedCTImageStorage, UIDUtils.createUID(), false);
            }

            remainingSf = Math.max(0, remainingSf - sfTake);
            remainingMf = Math.max(0, remainingMf - mfTake);
            study.addSeries(series);
        }

        // Add KIN series at the end if requested.
        if (options.includeKIN) {
            SimulatedSeries kin = new SimulatedSeries(UIDUtils.createUID(), "KO", "Key Images");
            kin.addInstance(UID.KeyObjectSelectionDocumentStorage, UIDUtils.createUID(), true);
            study.addSeries(kin);
            study.kinSeries = kin;
        }

        // If distribution logic left instances unassigned (can happen with small counts), add them to first series.
        if (!study.seriesList.isEmpty() && (remainingSf > 0 || remainingMf > 0)) {
            SimulatedSeries first = study.seriesList.get(0);
            for (int i = 0; i < remainingSf; i++) {
                first.addInstance(sopClassForSingleFrame(first.modality), UIDUtils.createUID(), false);
            }
            for (int i = 0; i < remainingMf; i++) {
                first.addInstance(UID.EnhancedCTImageStorage, UIDUtils.createUID(), false);
            }
        }

        return study;
    }

    // Backwards compatible default study generator.
    private static SimulatedStudy generateSimulatedStudy() {
        return generateSimulatedStudy(defaultOptions());
    }

    private static String pickModality(String[] pool, int idx) {
        if (pool == null || pool.length == 0) return "CT";
        return pool[idx % pool.length];
    }

    private static String sopClassForSingleFrame(String modality) {
        if (modality == null) return UID.SecondaryCaptureImageStorage;
        switch (modality) {
            case "MR":
                return UID.MRImageStorage;
            case "US":
                return UID.UltrasoundImageStorage;
            case "OT":
                return UID.SecondaryCaptureImageStorage;
            case "CT":
            default:
                return UID.CTImageStorage;
        }
    }

    // --- Helper Classes ---
    static class SimulatedStudy {
        String studyInstanceUID;
        List<SimulatedSeries> seriesList = new ArrayList<>();
        SimulatedSeries kinSeries;
        Options options;

        void addSeries(SimulatedSeries s) {
            seriesList.add(s);
        }
    }

    static class SimulatedSeries {
        String seriesUID;
        String modality;
        String description;
        int seriesNumber;
        String seriesDate;
        String seriesTime;
        List<SimulatedInstance> instances = new ArrayList<>();

        SimulatedSeries(String uid, String mod, String desc) {
            this.seriesUID = uid;
            this.modality = mod;
            this.description = desc;
        }

        void addInstance(String cls, String uid, boolean isKin) {
            instances.add(new SimulatedInstance(cls, uid, isKin));
        }
    }

    static class SimulatedInstance {
        String sopClassUID;
        String sopInstanceUID;
        boolean isKIN;

        SimulatedInstance(String cls, String uid, boolean isKin) {
            this.sopClassUID = cls;
            this.sopInstanceUID = uid;
            this.isKIN = isKin;
        }
    }
}
