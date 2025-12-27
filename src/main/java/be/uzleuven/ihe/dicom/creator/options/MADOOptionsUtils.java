package be.uzleuven.ihe.dicom.creator.options;

import be.uzleuven.ihe.dicom.creator.model.MADOOptions;
import be.uzleuven.ihe.dicom.creator.samples.IHEKOSSampleCreator;
import org.dcm4che3.data.UID;

import static be.uzleuven.ihe.dicom.creator.utils.DicomCreatorUtils.randomInt;

/**
 * Utility class for generating random MADO options and converting between KOS and MADO options.
 */
public class MADOOptionsUtils {

    private MADOOptionsUtils() {
        // Utility class
    }

    /**
     * Generate random options with balanced parameters designed to be comparable to
     * {@link IHEKOSSampleCreator#generateRandomOptions()}.
     * Shared ranges (KOS & MADO):
     * - totalInstanceCount: 10-10000
     * - seriesCount: 1-30 (but <= totalInstanceCount)
     * - modalityPool size: 1-4
     * - multiframe: ~50% chance
     * MADO-only extras are kept small so manifests stay comparable in evidence size:
     * - includeKIN always true (kept for MADO profile behavior)
     * - multiframeInstanceCount is capped to avoid dominating the study
     * - keyImageCount is capped to <= 5% of non-KIN instances
     */
    public static MADOOptions generateRandomOptions() {
        MADOOptions options = new MADOOptions();

        // Keep comparable with KOS: 10-10000 instances
        options.withTotalInstanceCount(10 + randomInt(9991)); // 10..10000

        // Keep comparable with KOS: 1-30 series, but can't exceed instance count
        int maxSeries = Math.min(30, options.getTotalInstanceCount());
        options.withSeriesCount(1 + randomInt(maxSeries));

        // Keep comparable with KOS: 1-4 modalities
        int modalityCount = 1 + randomInt(4); // 1..4
        String[] allModalities = new String[]{"CT", "MR", "US", "OT"};
        String[] selectedModalities = new String[modalityCount];
        for (int i = 0; i < modalityCount; i++) {
            selectedModalities[i] = allModalities[i % allModalities.length];
        }
        options.withModalityPool(selectedModalities);

        // MADO keeps KIN enabled by default; it adds 1 referenced instance.
        options.withIncludeKIN(true);

        // Keep comparable with KOS multiframe toggle: 50% chance.
        options.withIncludeMultiframe(false); // randomInt(200) == 100

        // If multiframe is enabled, keep counts modest so it doesn't skew sizes.
        if (options.isIncludeMultiframe()) {
            int maxByPct = Math.max(1, options.getTotalInstanceCount() / 4);
            int maxMultiframe = Math.min(100, Math.min(options.getTotalInstanceCount(), maxByPct));
            options.withMultiframeInstanceCount(1 + randomInt(maxMultiframe));
        } else {
            options.withMultiframeInstanceCount(0);
        }

        // Key images are only descriptive UIDREFs inside the KIN descriptors.
        int nonKinInstances = Math.max(1, options.getTotalInstanceCount() - 1);
        int capByPct = Math.max(1, (int) Math.floor(nonKinInstances * 0.05));
        int maxKeyImages = Math.min(500, Math.min(nonKinInstances, capByPct));
        options.withKeyImageCount((maxKeyImages == 1) ? 1 : (1 + randomInt(maxKeyImages)));

        return options;
    }

    /**
     * Map KOS Options to MADO Options so --default-sizes uses KOS defaults translated into MADO semantics.
     */
    public static MADOOptions fromKOSOptions(IHEKOSSampleCreator.Options kos) {
        MADOOptions opt = new MADOOptions();
        if (kos == null) return opt;

        // KOS sopInstanceCount roughly maps to MADO totalInstanceCount (minus the KIN instance we add)
        opt.withTotalInstanceCount(Math.max(1, kos.sopInstanceCount - 1));

        // KOS evidenceSeriesCount maps to MADO seriesCount (we add one for KIN later if includeKIN true)
        opt.withSeriesCount(Math.max(1, kos.evidenceSeriesCount + (opt.isIncludeKIN() ? 1 : 0)));

        // Map modalities by converting SOP Class UIDs to modality codes
        if (kos.modalities != null && kos.modalities.length > 0) {
            String[] pool = new String[kos.modalities.length];
            for (int i = 0; i < kos.modalities.length; i++) {
                pool[i] = modalityFromSopClass(kos.modalities[i]);
            }
            opt.withModalityPool(pool);
        }

        // Map multiframe flag
        opt.withIncludeMultiframe(kos.multiframe);
        if (kos.multiframe) {
            int count = Math.max(1, Math.min(5, kos.sopInstanceCount / 10));
            opt.withMultiframeInstanceCount(count);
        } else {
            opt.withMultiframeInstanceCount(0);
        }

        // keyImageCount: keep small descriptive number
        opt.withKeyImageCount(3);

        return opt;
    }

    /**
     * Convert a SOP Class UID to a modality code.
     */
    private static String modalityFromSopClass(String sopClassUid) {
        if (sopClassUid == null) return "CT";
        switch (sopClassUid) {
            case UID.MRImageStorage:
                return "MR";
            case UID.UltrasoundImageStorage:
                return "US";
            case UID.DigitalXRayImageStorageForPresentation:
            case UID.DigitalXRayImageStorageForProcessing:
                return "CR";
            case UID.SecondaryCaptureImageStorage:
                return "OT";
            case UID.CTImageStorage:
            default:
                return "CT";
        }
    }
}

