package be.uzleuven.ihe.dicom.creator.evil;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;

/**
 * Forbidden DICOM tags for KOS and MADO manifests.
 * These tags are explicitly disallowed in KOS/MADO specifications because:
 * - KOS is a lightweight manifest document (not an image)
 * - Should not contain pixel data, acquisition parameters, or image-specific attributes
 * Reference: XDSIManifestProfileUtils.validateForbiddenElements()
 */
public final class ForbiddenTags {

    private ForbiddenTags() {
    }

    /**
     * Tag definition for forbidden attributes.
     */
    private static class TagDef {
        final int tag;
        final VR vr;
        final String description;
        final Severity severity;

        TagDef(int tag, VR vr, String description, Severity severity) {
            this.tag = tag;
            this.vr = vr;
            this.description = description;
            this.severity = severity;
        }
    }

    private enum Severity {
        CRITICAL,  // Fundamental prohibition (e.g., Pixel Data)
        WARNING    // Image-specific attribute that shouldn't be in KOS
    }

    /**
     * Array of forbidden tags with their characteristics.
     */
    private static final TagDef[] FORBIDDEN_TAGS = {
        // CRITICAL: Pixel Data - fundamental prohibition
        new TagDef(Tag.PixelData, VR.OW, "Pixel Data", Severity.CRITICAL),
        new TagDef(Tag.FloatPixelData, VR.OF, "Float Pixel Data", Severity.CRITICAL),
        new TagDef(Tag.DoubleFloatPixelData, VR.OD, "Double Float Pixel Data", Severity.CRITICAL),

        // CRITICAL: Other bulk data types
        new TagDef(Tag.WaveformData, VR.OW, "Waveform Data", Severity.CRITICAL),
        new TagDef(Tag.AudioSampleData, VR.OW, "Audio Sample Data", Severity.CRITICAL),
        new TagDef(Tag.SpectroscopyData, VR.OF, "Spectroscopy Data", Severity.CRITICAL),

        // WARNING: Image Pixel Module attributes
        new TagDef(Tag.Rows, VR.US, "Rows", Severity.WARNING),
        new TagDef(Tag.Columns, VR.US, "Columns", Severity.WARNING),
        new TagDef(Tag.BitsAllocated, VR.US, "Bits Allocated", Severity.WARNING),
        new TagDef(Tag.BitsStored, VR.US, "Bits Stored", Severity.WARNING),
        new TagDef(Tag.HighBit, VR.US, "High Bit", Severity.WARNING),
        new TagDef(Tag.PixelRepresentation, VR.US, "Pixel Representation", Severity.WARNING),
        new TagDef(Tag.SamplesPerPixel, VR.US, "Samples Per Pixel", Severity.WARNING),
        new TagDef(Tag.PhotometricInterpretation, VR.CS, "Photometric Interpretation", Severity.WARNING),
        new TagDef(Tag.PlanarConfiguration, VR.US, "Planar Configuration", Severity.WARNING),

        // WARNING: VOI LUT Module
        new TagDef(Tag.WindowCenter, VR.DS, "Window Center", Severity.WARNING),
        new TagDef(Tag.WindowWidth, VR.DS, "Window Width", Severity.WARNING),

        // WARNING: Modality LUT Module
        new TagDef(Tag.RescaleIntercept, VR.DS, "Rescale Intercept", Severity.WARNING),
        new TagDef(Tag.RescaleSlope, VR.DS, "Rescale Slope", Severity.WARNING),

        // WARNING: Image positioning attributes
        new TagDef(Tag.ImagePositionPatient, VR.DS, "Image Position (Patient)", Severity.WARNING),
        new TagDef(Tag.ImageOrientationPatient, VR.DS, "Image Orientation (Patient)", Severity.WARNING),
        new TagDef(Tag.SliceLocation, VR.DS, "Slice Location", Severity.WARNING),
        new TagDef(Tag.SliceThickness, VR.DS, "Slice Thickness", Severity.WARNING),
        new TagDef(Tag.PixelSpacing, VR.DS, "Pixel Spacing", Severity.WARNING),

        // WARNING: Acquisition parameters
        new TagDef(Tag.KVP, VR.DS, "KVP", Severity.WARNING),
        new TagDef(Tag.ExposureTime, VR.IS, "Exposure Time", Severity.WARNING),
        new TagDef(Tag.XRayTubeCurrent, VR.IS, "X-Ray Tube Current", Severity.WARNING),
        new TagDef(Tag.Exposure, VR.IS, "Exposure", Severity.WARNING),
        new TagDef(Tag.RepetitionTime, VR.DS, "Repetition Time", Severity.WARNING),
        new TagDef(Tag.EchoTime, VR.DS, "Echo Time", Severity.WARNING),
        new TagDef(Tag.MagneticFieldStrength, VR.DS, "Magnetic Field Strength", Severity.WARNING),
        new TagDef(Tag.FlipAngle, VR.DS, "Flip Angle", Severity.WARNING),

        // WARNING: Image type and technical attributes
        new TagDef(Tag.ImageType, VR.CS, "Image Type", Severity.WARNING),
        new TagDef(Tag.AcquisitionNumber, VR.IS, "Acquisition Number", Severity.WARNING),
        new TagDef(Tag.AcquisitionDate, VR.DA, "Acquisition Date", Severity.WARNING),
        new TagDef(Tag.AcquisitionTime, VR.TM, "Acquisition Time", Severity.WARNING),
        new TagDef(Tag.AcquisitionDateTime, VR.DT, "Acquisition DateTime", Severity.WARNING),
        new TagDef(Tag.ContentDate, VR.DA, "Content Date", Severity.WARNING),
        new TagDef(Tag.ContentTime, VR.TM, "Content Time", Severity.WARNING),
    };

    /**
     * Randomly add 0-5 forbidden tags to the dataset to make it "more evil".
     *
     * @param dataset The DICOM dataset to corrupt
     * @param probability Probability of adding forbidden tags (0.0 to 1.0)
     */
    public static void addRandomForbiddenTags(Attributes dataset, double probability) {
        if (!EvilDice.chance(probability)) {
            return;  // Skip this evil mutation
        }

        // Decide how many forbidden tags to add (1-5)
        int count = 1 + EvilDice.randomInt(5);

        for (int i = 0; i < count; i++) {
            // 10% chance to add overlay or curve data (special group patterns)
            if (EvilDice.chance(0.10)) {
                addSpecialGroupData(dataset);
            } else {
                TagDef tagDef = FORBIDDEN_TAGS[EvilDice.randomInt(FORBIDDEN_TAGS.length)];
                addForbiddenTag(dataset, tagDef);
            }
        }
    }

    /**
     * Add overlay data (60xx,3000) or curve data (50xx,3000).
     * These use special group numbering patterns.
     */
    private static void addSpecialGroupData(Attributes dataset) {
        if (EvilDice.chance(0.5)) {
            // Add Overlay Data (60xx,3000)
            int overlayGroup = 0x6000 + (EvilDice.randomInt(16) * 2);  // 6000, 6002, 6004, ... 601E
            int overlayTag = (overlayGroup << 16) | 0x3000;
            if (!dataset.contains(overlayTag)) {
                dataset.setBytes(overlayTag, VR.OW, generateRandomBulkData());
            }
        } else {
            // Add Curve Data (50xx,3000) - deprecated but still checked
            int curveGroup = 0x5000 + (EvilDice.randomInt(16) * 2);  // 5000, 5002, 5004, ... 501E
            int curveTag = (curveGroup << 16) | 0x3000;
            if (!dataset.contains(curveTag)) {
                dataset.setBytes(curveTag, VR.OW, generateRandomBulkData());
            }
        }
    }

    /**
     * Add a specific forbidden tag with realistic but invalid data.
     */
    private static void addForbiddenTag(Attributes dataset, TagDef tagDef) {
        // Don't overwrite existing tags (even evil should be somewhat consistent)
        if (dataset.contains(tagDef.tag)) {
            return;
        }

        switch (tagDef.vr) {
            case US:  // Unsigned Short
                dataset.setInt(tagDef.tag, tagDef.vr, generateRandomUS());
                break;
            case IS:  // Integer String
                dataset.setString(tagDef.tag, tagDef.vr, String.valueOf(generateRandomIS()));
                break;
            case DS:  // Decimal String
                dataset.setString(tagDef.tag, tagDef.vr, generateRandomDS());
                break;
            case CS:  // Code String
                dataset.setString(tagDef.tag, tagDef.vr, generateRandomCS(tagDef.tag));
                break;
            case DA:  // Date
                dataset.setString(tagDef.tag, tagDef.vr, generateRandomDate());
                break;
            case TM:  // Time
                dataset.setString(tagDef.tag, tagDef.vr, generateRandomTime());
                break;
            case DT:  // DateTime
                dataset.setString(tagDef.tag, tagDef.vr, generateRandomDateTime());
                break;
            case OW:  // Other Word
            case OF:  // Other Float
            case OD:  // Other Double
                // For bulk data, add empty or minimal data
                dataset.setBytes(tagDef.tag, tagDef.vr, generateRandomBulkData());
                break;
            default:
                // Fallback for any other VR
                dataset.setString(tagDef.tag, tagDef.vr, "EVIL_VALUE");
        }
    }

    // ========== Value Generators ==========

    private static int generateRandomUS() {
        // Common image dimensions and bit values
        int[] commonValues = {8, 12, 16, 512, 1024, 2048, 4096};
        return EvilDice.chance(0.7) ?
            commonValues[EvilDice.randomInt(commonValues.length)] :
            EvilDice.randomInt(65536);
    }

    private static int generateRandomIS() {
        return EvilDice.randomInt(10000);
    }

    private static String generateRandomDS() {
        // Generate realistic decimal strings for medical imaging
        double[] commonRanges = {
            0.5 + EvilDice.randomInt(1000) / 10.0,  // 0.5-100.5
            100.0 + EvilDice.randomInt(500),        // 100-600 (kVp, etc.)
            EvilDice.randomInt(360) + Math.random() // 0-360 (angles)
        };
        return String.format("%.2f", commonRanges[EvilDice.randomInt(commonRanges.length)]);
    }

    private static String generateRandomCS(int tag) {
        // Tag-specific code strings
        if (tag == Tag.ImageType) {
            String[] types = {"ORIGINAL", "DERIVED", "PRIMARY", "SECONDARY"};
            return EvilDice.oneOf(types);
        } else if (tag == Tag.PhotometricInterpretation) {
            String[] values = {"MONOCHROME1", "MONOCHROME2", "RGB", "YBR_FULL"};
            return EvilDice.oneOf(values);
        }
        return "EVIL_CS";
    }

    private static String generateRandomDate() {
        int year = 2000 + EvilDice.randomInt(25);
        int month = 1 + EvilDice.randomInt(12);
        int day = 1 + EvilDice.randomInt(28);
        return String.format("%04d%02d%02d", year, month, day);
    }

    private static String generateRandomTime() {
        int hour = EvilDice.randomInt(24);
        int minute = EvilDice.randomInt(60);
        int second = EvilDice.randomInt(60);
        return String.format("%02d%02d%02d", hour, minute, second);
    }

    private static String generateRandomDateTime() {
        return generateRandomDate() + generateRandomTime();
    }

    private static byte[] generateRandomBulkData() {
        // Return minimal bulk data (not full image - that would be too large)
        // Just enough to trigger the validator
        int size = 16 + EvilDice.randomInt(240);  // 16-256 bytes
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) EvilDice.randomInt(256);
        }
        return data;
    }
}

