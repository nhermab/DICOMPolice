package be.uzleuven.ihe.dicom.validator.utils;

import be.uzleuven.ihe.dicom.constants.DicomConstants;
import be.uzleuven.ihe.dicom.constants.ValidationMessages;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import be.uzleuven.ihe.dicom.validator.validation.iod.AbstractIODValidator;
import be.uzleuven.ihe.dicom.validator.model.ValidationResult;
import be.uzleuven.ihe.dicom.validator.validation.DigitalSignatureValidator;

import java.util.HashSet;
import java.util.Set;

/**
 * XDS-I.b Imaging Manifest profile validation helpers.
 * <p>
 * Kept separate from the core KOS/IOD validator so the validator can stay focused on PS3.3 modules,
 * and profile-specific conventions can evolve independently.
 */
public final class XDSIManifestProfileUtils {

    private XDSIManifestProfileUtils() {
    }

    private static final String EXPECTED_MAPPING_RESOURCE_DCMR = ValidationMessages.DCMR;
    private static final String EXPECTED_TEMPLATE_IDENTIFIER_TID_2010 = "2010";

    // IHE XDS-I.b Imaging Manifest title
    private static final String XDSI_MANIFEST_CODE_VALUE = "113030";
    private static final String XDSI_MANIFEST_CSD = DicomConstants.SCHEME_DCM;
    private static final String XDSI_MANIFEST_CODE_MEANING = DicomConstants.CODE_MANIFEST_MEANING;

    // IOCM rejection note titles we explicitly forbid when the caller expects a sharing manifest.
    // (Non-exhaustive but covers the commonly encountered high-risk titles.)
    private static final Set<String> FORBIDDEN_IOCM_TITLE_CODE_VALUES = new HashSet<>();

    static {
        FORBIDDEN_IOCM_TITLE_CODE_VALUES.add(DicomConstants.IOCM_REJECTED_QUALITY); // Rejected for Quality Reasons
        FORBIDDEN_IOCM_TITLE_CODE_VALUES.add(DicomConstants.IOCM_REJECTED_PATIENT_SAFETY); // Rejected for Patient Safety Reasons
        FORBIDDEN_IOCM_TITLE_CODE_VALUES.add(DicomConstants.IOCM_DATA_RETENTION_EXPIRED); // Data Retention Policy Expired
    }

    public static boolean isForbiddenIOCMTitle(String codeValue) {
        return FORBIDDEN_IOCM_TITLE_CODE_VALUES.contains(codeValue);
    }

    public static void validateIHEXDSIManifestProfile(Attributes dataset, ValidationResult result, boolean verbose, AbstractIODValidator ctx) {
        String modulePath = "IHEXDSIManifestProfile";

        // Check for forbidden elements that should not be in a KOS
        validateForbiddenElements(dataset, result, modulePath);

        // Enforce Document Title = (113030, DCM, "Manifest") and forbid IOCM rejection note titles.
        validateManifestDocumentTitle(dataset, result, modulePath);

        // Validate Digital Signature requirements if applicable
        DigitalSignatureValidator.validateSignatureRequirement(dataset, result, modulePath);
        DigitalSignatureValidator.validateMACParameters(dataset, result, modulePath);

        // Require CompletionFlag/VerificationFlag for finalized XDS-I manifests
        ctx.checkRequiredAttribute(dataset, Tag.CompletionFlag, "CompletionFlag", result, modulePath);
        if (dataset.contains(Tag.CompletionFlag)) {
            ctx.checkEnumeratedValue(dataset, Tag.CompletionFlag, "CompletionFlag", new String[]{DicomConstants.COMPLETION_FLAG_COMPLETE}, result, modulePath);
        }

        ctx.checkRequiredAttribute(dataset, Tag.VerificationFlag, "VerificationFlag", result, modulePath);
        if (dataset.contains(Tag.VerificationFlag)) {
            ctx.checkEnumeratedValue(dataset, Tag.VerificationFlag, "VerificationFlag", new String[]{DicomConstants.VERIFICATION_FLAG_VERIFIED, DicomConstants.VERIFICATION_FLAG_UNVERIFIED}, result, modulePath);
        }

        // ContentTemplateSequence expected to be DCMR/TID 2010 for KOS (common XDS-I convention)
        Sequence cts = dataset.getSequence(Tag.ContentTemplateSequence);
        if (cts != null && !cts.isEmpty()) {
            Attributes item = cts.get(0);
            String mapping = item.getString(Tag.MappingResource);
            String tid = item.getString(Tag.TemplateIdentifier);
            if (mapping != null && !EXPECTED_MAPPING_RESOURCE_DCMR.equals(mapping)) {
                result.addError(ValidationMessages.XDSI_TEMPLATE_MAPPING_RESOURCE_WRONG, modulePath);
            }
            if (tid != null && !EXPECTED_TEMPLATE_IDENTIFIER_TID_2010.equals(tid)) {
                result.addError(ValidationMessages.XDSI_TEMPLATE_IDENTIFIER_WRONG, modulePath);
            }
        }

        // For XDS-I, the SR ContentSequence is the manifest tree and must be present and non-empty.
        Sequence contentSeq = dataset.getSequence(Tag.ContentSequence);
        if (contentSeq == null || contentSeq.isEmpty()) {
            result.addError(ValidationMessages.XDSI_CONTENT_SEQUENCE_MISSING, modulePath);
        }

        // Collect + validate SR referenced instances, with duplicate and self-reference checks.
        SRReferenceUtils.SRRefScan srScan = SRReferenceUtils.scanSRReferencesWithChecks(dataset, result, modulePath, ctx);

        // Evidence sequence references
        Set<String> evidenceRefs = SRReferenceUtils.collectReferencedSOPInstanceUIDsFromEvidence(dataset);

        if (srScan.referencedSOPInstanceUIDs.isEmpty() && evidenceRefs.isEmpty()) {
            result.addError(ValidationMessages.XDSI_NO_INSTANCE_REFERENCES, modulePath);
            return;
        }

        // For XDS-I, require SR references to be present (the SR content tree is the manifest)
        if (srScan.referencedSOPInstanceUIDs.isEmpty()) {
            result.addError(ValidationMessages.XDSI_NO_SR_REFERENCES, modulePath);
        }

        // Evidence is required if instances are referenced in SR
        if (!srScan.referencedSOPInstanceUIDs.isEmpty() && evidenceRefs.isEmpty()) {
            result.addError(ValidationMessages.XDSI_EVIDENCE_MISSING, modulePath);
        }

        // Cross-consistency: require SR refs to be included in Evidence.
        if (!srScan.referencedSOPInstanceUIDs.isEmpty() && !evidenceRefs.isEmpty()) {
            for (String uid : srScan.referencedSOPInstanceUIDs) {
                if (!evidenceRefs.contains(uid)) {
                    result.addError(String.format(ValidationMessages.XDSI_ORPHAN_REFERENCE, uid), modulePath);
                }
            }

            // Evidence contains extra references not shown in SR tree - warn (some systems include more evidence than displayed)
            for (String uid : evidenceRefs) {
                if (!srScan.referencedSOPInstanceUIDs.contains(uid)) {
                    result.addWarning("Evidence references SOP Instance UID not present in SR content tree: " + uid, modulePath);
                }
            }
        }

        if (verbose) {
            result.addInfo("IHEXDSI manifest profile checks executed", modulePath);
        }
    }

    /**
     * Validate that forbidden elements are not present in the KOS manifest.
     * A KOS is a lightweight pointer document and should not contain image data or acquisition attributes.
     */
    public static void validateForbiddenElements(Attributes dataset, ValidationResult result, String modulePath) {
        // Pixel Data - fundamental prohibition for KOS
        if (dataset.contains(Tag.PixelData)) {
            result.addError(ValidationMessages.XDSI_PIXEL_DATA_FORBIDDEN, modulePath);
        }

        // Float Pixel Data and Double Float Pixel Data
        if (dataset.contains(Tag.FloatPixelData)) {
            result.addError(ValidationMessages.XDSI_FLOAT_PIXEL_DATA_FORBIDDEN, modulePath);
        }
        if (dataset.contains(Tag.DoubleFloatPixelData)) {
            result.addError(ValidationMessages.XDSI_DOUBLE_FLOAT_PIXEL_DATA_FORBIDDEN, modulePath);
        }

        // Overlay Data (60xx,3000)
        int[] tags = dataset.tags();
        for (int tag : tags) {
            int group = (tag >>> 16) & 0xFFFF;
            int element = tag & 0xFFFF;
            // Overlay Data is in group 60xx with element 3000
            if ((group & 0xFF00) == 0x6000 && element == 0x3000) {
                result.addError(String.format("Overlay Data (%04X,3000) is present. KOS must not contain overlay data.", group), modulePath);
            }
        }

        // Waveform Data
        if (dataset.contains(Tag.WaveformData)) {
            result.addError(ValidationMessages.XDSI_WAVEFORM_DATA_FORBIDDEN, modulePath);
        }

        // Audio Data
        if (dataset.contains(Tag.AudioSampleData)) {
            result.addError(ValidationMessages.XDSI_AUDIO_DATA_FORBIDDEN, modulePath);
        }

        // Spectroscopy Data
        if (dataset.contains(Tag.SpectroscopyData)) {
            result.addError(ValidationMessages.XDSI_SPECTROSCOPY_DATA_FORBIDDEN, modulePath);
        }

        // Image-specific modules that should not be in KOS

        // Image Pixel Module attributes
        if (dataset.contains(Tag.Rows)) {
            result.addWarning("Rows (0028,0010) is present. This is an Image Pixel Module attribute and should not be in a KOS.", modulePath);
        }
        if (dataset.contains(Tag.Columns)) {
            result.addWarning("Columns (0028,0011) is present. This is an Image Pixel Module attribute and should not be in a KOS.", modulePath);
        }
        if (dataset.contains(Tag.BitsAllocated)) {
            result.addWarning("Bits Allocated (0028,0100) is present. This is an Image Pixel Module attribute and should not be in a KOS.", modulePath);
        }
        if (dataset.contains(Tag.BitsStored)) {
            result.addWarning("Bits Stored (0028,0101) is present. This is an Image Pixel Module attribute and should not be in a KOS.", modulePath);
        }
        if (dataset.contains(Tag.HighBit)) {
            result.addWarning("High Bit (0028,0102) is present. This is an Image Pixel Module attribute and should not be in a KOS.", modulePath);
        }
        if (dataset.contains(Tag.PixelRepresentation)) {
            result.addWarning("Pixel Representation (0028,0103) is present. This is an Image Pixel Module attribute and should not be in a KOS.", modulePath);
        }

        // VOI LUT Module
        if (dataset.contains(Tag.WindowCenter)) {
            result.addWarning("Window Center (0028,1050) is present. This is a VOI LUT Module attribute and should not be in a KOS.", modulePath);
        }
        if (dataset.contains(Tag.WindowWidth)) {
            result.addWarning("Window Width (0028,1051) is present. This is a VOI LUT Module attribute and should not be in a KOS.", modulePath);
        }
        if (dataset.contains(Tag.VOILUTSequence)) {
            result.addWarning("VOI LUT Sequence (0028,3010) is present. This is a VOI LUT Module attribute and should not be in a KOS.", modulePath);
        }

        // Modality LUT Module
        if (dataset.contains(Tag.ModalityLUTSequence)) {
            result.addWarning("Modality LUT Sequence (0028,3000) is present. This is a Modality LUT Module attribute and should not be in a KOS.", modulePath);
        }
        if (dataset.contains(Tag.RescaleIntercept)) {
            result.addWarning("Rescale Intercept (0028,1052) is present. This is a Modality LUT Module attribute and should not be in a KOS.", modulePath);
        }
        if (dataset.contains(Tag.RescaleSlope)) {
            result.addWarning("Rescale Slope (0028,1053) is present. This is a Modality LUT Module attribute and should not be in a KOS.", modulePath);
        }

        // Image acquisition/positioning attributes
        if (dataset.contains(Tag.ImagePositionPatient)) {
            result.addWarning("Image Position (Patient) (0020,0032) is present. This is an image-specific attribute and should not be in a KOS.", modulePath);
        }
        if (dataset.contains(Tag.ImageOrientationPatient)) {
            result.addWarning("Image Orientation (Patient) (0020,0037) is present. This is an image-specific attribute and should not be in a KOS.", modulePath);
        }
        if (dataset.contains(Tag.SliceLocation)) {
            result.addWarning("Slice Location (0020,1041) is present. This is an image-specific attribute and should not be in a KOS.", modulePath);
        }
        if (dataset.contains(Tag.SliceThickness)) {
            result.addWarning("Slice Thickness (0018,0050) is present. This is an image-specific attribute and should not be in a KOS.", modulePath);
        }

        // Acquisition context attributes
        if (dataset.contains(Tag.KVP)) {
            result.addWarning("KVP (0018,0060) is present. This is an acquisition parameter and should not be in a KOS.", modulePath);
        }
        if (dataset.contains(Tag.ExposureTime)) {
            result.addWarning("Exposure Time (0018,1150) is present. This is an acquisition parameter and should not be in a KOS.", modulePath);
        }
        if (dataset.contains(Tag.XRayTubeCurrent)) {
            result.addWarning("X-Ray Tube Current (0018,1151) is present. This is an acquisition parameter and should not be in a KOS.", modulePath);
        }

        // Curve Data (50xx,3000) - deprecated but should still check
        for (int tag : tags) {
            int group = (tag >>> 16) & 0xFFFF;
            int element = tag & 0xFFFF;
            // Curve Data is in group 50xx with element 3000
            if ((group & 0xFF00) == 0x5000 && element == 0x3000) {
                result.addWarning(String.format("Curve Data (%04X,3000) is present. KOS should not contain curve data.", group), modulePath);
            }
        }

        // Check for Group 0002 elements in the main dataset (they should only be in File Meta)
        for (int tag : tags) {
            int group = (tag >>> 16) & 0xFFFF;
            if (group == 0x0002) {
                result.addError(String.format("File Meta Information element (%04X,%04X) found in main dataset. " +
                    "Group 0002 elements must only appear in the File Meta Information header.",
                    group, tag & 0xFFFF), modulePath);
            }
        }
    }

    static void validateManifestDocumentTitle(Attributes dataset, ValidationResult result, String modulePath) {
        Sequence seq = dataset.getSequence(Tag.ConceptNameCodeSequence);
        if (seq == null || seq.isEmpty()) {
            // SRDocumentContent module will already have flagged this, but we keep profile-level message too.
            result.addError(ValidationMessages.XDSI_CONCEPT_NAME_MISSING, modulePath);
            return;
        }

        Attributes item = seq.get(0);
        String codeValue = item.getString(Tag.CodeValue);
        String csd = item.getString(Tag.CodingSchemeDesignator);
        String meaning = item.getString(Tag.CodeMeaning);

        if (codeValue != null && FORBIDDEN_IOCM_TITLE_CODE_VALUES.contains(codeValue)) {
            result.addError(String.format(ValidationMessages.XDSI_IOCM_REJECTION_FORBIDDEN, codeValue), modulePath);
            return;
        }

        boolean ok = XDSI_MANIFEST_CODE_VALUE.equals(codeValue) &&
                XDSI_MANIFEST_CSD.equals(csd) &&
                XDSI_MANIFEST_CODE_MEANING.equalsIgnoreCase(meaning);
        if (!ok) {
            result.addError(
                    "ConceptNameCodeSequence must be (" + XDSI_MANIFEST_CODE_VALUE + ", " + XDSI_MANIFEST_CSD + ", \"" + XDSI_MANIFEST_CODE_MEANING + "\") for XDS-I manifest; found (" +
                            codeValue + ", " + csd + ", \"" + meaning + "\")",
                    modulePath);
        }
    }
}
