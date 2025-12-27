package be.uzleuven.ihe.dicom.creator.utils;

import be.uzleuven.ihe.dicom.creator.model.SimulatedSeries;
import be.uzleuven.ihe.dicom.creator.model.SimulatedStudy;
import be.uzleuven.ihe.dicom.creator.model.MADOOptions;
import org.dcm4che3.data.UID;

import static be.uzleuven.ihe.dicom.creator.utils.DicomCreatorUtils.createNormalizedUid;

/**
 * Utility class for generating simulated DICOM studies for MADO testing.
 */
public class StudyGeneratorUtils {

    private StudyGeneratorUtils() {
        // Utility class
    }

    /**
     * Generate a simulated study based on provided options.
     *
     * @param options Configuration options for the study
     * @return Generated SimulatedStudy
     */
    public static SimulatedStudy generateSimulatedStudy(MADOOptions options) {
        if (options == null) {
            options = new MADOOptions();
        }

        SimulatedStudy study = new SimulatedStudy();
        study.setOptions(options);
        study.setStudyInstanceUID(createNormalizedUid());

        int seriesCount = Math.max(1, options.getSeriesCount());

        // Decide how many non-KIN series left.
        int nonKinSeriesCount = options.isIncludeKIN() ? Math.max(1, seriesCount - 1) : seriesCount;

        // Total instances to distribute among non-KIN series
        int totalInstances = Math.max(1, options.getTotalInstanceCount());

        int mfCount = (options.isIncludeMultiframe()) ?
            Math.min(Math.max(0, options.getMultiframeInstanceCount()), totalInstances) : 0;
        int sfCount = Math.max(0, totalInstances - mfCount);

        // Build non-KIN series
        int remainingSf = sfCount;
        int remainingMf = mfCount;

        for (int s = 0; s < nonKinSeriesCount; s++) {
            // Pick a modality label for this series.
            String modality = pickModality(options.getModalityPool(), s);
            String desc = "Series " + (s + 1) + " " + modality;

            SimulatedSeries series = new SimulatedSeries(createNormalizedUid(), modality, desc);

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
                series.addInstance(sopClassForSingleFrame(modality), createNormalizedUid(), false);
            }

            for (int i = 0; i < mfTake; i++) {
                series.addInstance(UID.EnhancedCTImageStorage, createNormalizedUid(), false);
            }

            remainingSf = Math.max(0, remainingSf - sfTake);
            remainingMf = Math.max(0, remainingMf - mfTake);
            study.addSeries(series);
        }

        // Add KIN series at the end if requested.
        if (options.isIncludeKIN()) {
            SimulatedSeries kin = new SimulatedSeries(createNormalizedUid(), "KO", "Key Images");
            kin.addInstance(UID.KeyObjectSelectionDocumentStorage, createNormalizedUid(), true);
            study.addSeries(kin);
            study.setKinSeries(kin);
        }

        // If distribution logic left instances unassigned (can happen with small counts), add them to first series.
        if (!study.getSeriesList().isEmpty() && (remainingSf > 0 || remainingMf > 0)) {
            SimulatedSeries first = study.getSeriesList().get(0);
            for (int i = 0; i < remainingSf; i++) {
                first.addInstance(sopClassForSingleFrame(first.getModality()), createNormalizedUid(), false);
            }
            for (int i = 0; i < remainingMf; i++) {
                first.addInstance(UID.EnhancedCTImageStorage, createNormalizedUid(), false);
            }
        }

        return study;
    }

    /**
     * Pick a modality from the pool based on index.
     */
    private static String pickModality(String[] pool, int idx) {
        if (pool == null || pool.length == 0) return "CT";
        return pool[idx % pool.length];
    }

    /**
     * Get the appropriate SOP Class UID for a single-frame image of the given modality.
     */
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

    /**
     * Helper method to determine if a SOP Class UID represents a multiframe image.
     */
    public static boolean isMultiframe(String sopClassUID) {
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
}
