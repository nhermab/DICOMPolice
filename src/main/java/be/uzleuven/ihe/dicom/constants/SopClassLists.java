package be.uzleuven.ihe.dicom.constants;

import org.dcm4che3.data.UID;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralized list of known SOP Class UIDs -> human-readable names.
 * Extracted from AdvancedStructureValidator to be reusable across the project.
 */
public final class SopClassLists {

    private SopClassLists() {
    }

    public static final Map<String, String> KNOWN_SOP_CLASSES;

    static {
        Map<String, String> m = new HashMap<>();

        // Storage SOP Classes
        m.put(UID.CTImageStorage, "CT Image Storage");
        m.put(UID.MRImageStorage, "MR Image Storage");
        m.put(UID.UltrasoundImageStorage, "Ultrasound Image Storage");
        m.put(UID.SecondaryCaptureImageStorage, "Secondary Capture Image Storage");
        m.put(UID.XRayAngiographicImageStorage, "X-Ray Angiographic Image Storage");
        m.put(UID.XRayRadiofluoroscopicImageStorage, "X-Ray Radiofluoroscopic Image Storage");
        m.put(UID.DigitalXRayImageStorageForPresentation, "Digital X-Ray Image Storage - For Presentation");
        m.put(UID.DigitalXRayImageStorageForProcessing, "Digital X-Ray Image Storage - For Processing");
        m.put(UID.KeyObjectSelectionDocumentStorage, "Key Object Selection Document Storage");
        m.put(UID.GrayscaleSoftcopyPresentationStateStorage, "Grayscale Softcopy Presentation State Storage");
        m.put(UID.EncapsulatedPDFStorage, "Encapsulated PDF Storage");
        m.put(UID.BasicTextSRStorage, "Basic Text SR Storage");
        m.put(UID.EnhancedSRStorage, "Enhanced SR Storage");
        m.put(UID.ComprehensiveSRStorage, "Comprehensive SR Storage");
        m.put(UID.Comprehensive3DSRStorage, "Comprehensive 3D SR Storage");

        // Media / directory
        m.put("1.2.840.10008.1.3.10", "Media Storage Directory Storage");

        // CR / DX / MG / IO
        m.put(UID.ComputedRadiographyImageStorage, "Computed Radiography Image Storage");
        m.put(UID.DigitalMammographyXRayImageStorageForPresentation, "Digital Mammography X-Ray Image Storage - For Presentation");
        m.put(UID.DigitalMammographyXRayImageStorageForProcessing, "Digital Mammography X-Ray Image Storage - For Processing");
        m.put(UID.DigitalIntraOralXRayImageStorageForPresentation, "Digital Intra-Oral X-Ray Image Storage - For Presentation");
        m.put(UID.DigitalIntraOralXRayImageStorageForProcessing, "Digital Intra-Oral X-Ray Image Storage - For Processing");

        // CT family
        m.put(UID.EnhancedCTImageStorage, "Enhanced CT Image Storage");
        m.put(UID.LegacyConvertedEnhancedCTImageStorage, "Legacy Converted Enhanced CT Image Storage");

        // MR family
        m.put(UID.EnhancedMRImageStorage, "Enhanced MR Image Storage");
        m.put(UID.MRSpectroscopyStorage, "MR Spectroscopy Storage");
        m.put(UID.EnhancedMRColorImageStorage, "Enhanced MR Color Image Storage");
        m.put(UID.LegacyConvertedEnhancedMRImageStorage, "Legacy Converted Enhanced MR Image Storage");

        // US
        m.put(UID.UltrasoundMultiFrameImageStorage, "Ultrasound Multi-frame Image Storage");
        m.put(UID.EnhancedUSVolumeStorage, "Enhanced US Volume Storage");

        // Secondary capture variants
        m.put(UID.MultiFrameSingleBitSecondaryCaptureImageStorage, "Multi-frame Single Bit Secondary Capture Image Storage");
        m.put(UID.MultiFrameGrayscaleByteSecondaryCaptureImageStorage, "Multi-frame Grayscale Byte Secondary Capture Image Storage");
        m.put(UID.MultiFrameGrayscaleWordSecondaryCaptureImageStorage, "Multi-frame Grayscale Word Secondary Capture Image Storage");
        m.put(UID.MultiFrameTrueColorSecondaryCaptureImageStorage, "Multi-frame True Color Secondary Capture Image Storage");

        // ECG / waveform
        m.put(UID.TwelveLeadECGWaveformStorage, "12-lead ECG Waveform Storage");
        m.put(UID.GeneralECGWaveformStorage, "General ECG Waveform Storage");
        m.put(UID.AmbulatoryECGWaveformStorage, "Ambulatory ECG Waveform Storage");
        m.put(UID.HemodynamicWaveformStorage, "Hemodynamic Waveform Storage");
        m.put(UID.CardiacElectrophysiologyWaveformStorage, "Cardiac Electrophysiology Waveform Storage");
        m.put(UID.BasicVoiceAudioWaveformStorage, "Basic Voice Audio Waveform Storage");
        m.put(UID.GeneralAudioWaveformStorage, "General Audio Waveform Storage");
        m.put(UID.ArterialPulseWaveformStorage, "Arterial Pulse Waveform Storage");
        m.put(UID.RespiratoryWaveformStorage, "Respiratory Waveform Storage");

        // Presentation states
        m.put(UID.ColorSoftcopyPresentationStateStorage, "Color Softcopy Presentation State Storage");
        m.put(UID.PseudoColorSoftcopyPresentationStateStorage, "Pseudo-Color Softcopy Presentation State Storage");
        m.put(UID.BlendingSoftcopyPresentationStateStorage, "Blending Softcopy Presentation State Storage");
        m.put(UID.XAXRFGrayscaleSoftcopyPresentationStateStorage, "XA/XRF Grayscale Softcopy Presentation State Storage");

        // XA/XRF + 3D + tomo + OCT
        m.put(UID.EnhancedXAImageStorage, "Enhanced XA Image Storage");
        m.put(UID.EnhancedXRFImageStorage, "Enhanced XRF Image Storage");
        m.put(UID.XRay3DAngiographicImageStorage, "X-Ray 3D Angiographic Image Storage");
        m.put(UID.XRay3DCraniofacialImageStorage, "X-Ray 3D Craniofacial Image Storage");
        m.put(UID.BreastTomosynthesisImageStorage, "Breast Tomosynthesis Image Storage");
        m.put(UID.IntravascularOpticalCoherenceTomographyImageStorageForPresentation, "Intravascular OCT Image Storage - For Presentation");
        m.put(UID.IntravascularOpticalCoherenceTomographyImageStorageForProcessing, "Intravascular OCT Image Storage - For Processing");

        // Nuclear medicine / PET
        m.put(UID.NuclearMedicineImageStorage, "Nuclear Medicine Image Storage");
        m.put(UID.PositronEmissionTomographyImageStorage, "Positron Emission Tomography Image Storage");
        m.put(UID.EnhancedPETImageStorage, "Enhanced PET Image Storage");
        m.put(UID.LegacyConvertedEnhancedPETImageStorage, "Legacy Converted Enhanced PET Image Storage");

        // Registration / segmentation / RWVM / surface
        m.put(UID.RawDataStorage, "Raw Data Storage");
        m.put(UID.SpatialRegistrationStorage, "Spatial Registration Storage");
        m.put(UID.SpatialFiducialsStorage, "Spatial Fiducials Storage");
        m.put(UID.DeformableSpatialRegistrationStorage, "Deformable Spatial Registration Storage");
        m.put(UID.SegmentationStorage, "Segmentation Storage");
        m.put(UID.SurfaceSegmentationStorage, "Surface Segmentation Storage");
        m.put(UID.RealWorldValueMappingStorage, "Real World Value Mapping Storage");
        m.put(UID.SurfaceScanMeshStorage, "Surface Scan Mesh Storage");
        m.put(UID.SurfaceScanPointCloudStorage, "Surface Scan Point Cloud Storage");

        // VL / ophthalmic
        m.put(UID.VLEndoscopicImageStorage, "VL Endoscopic Image Storage");
        m.put(UID.VideoEndoscopicImageStorage, "Video Endoscopic Image Storage");
        m.put(UID.VLMicroscopicImageStorage, "VL Microscopic Image Storage");
        m.put(UID.VideoMicroscopicImageStorage, "Video Microscopic Image Storage");
        m.put(UID.VLSlideCoordinatesMicroscopicImageStorage, "VL Slide-Coordinates Microscopic Image Storage");
        m.put(UID.VLPhotographicImageStorage, "VL Photographic Image Storage");
        m.put(UID.VideoPhotographicImageStorage, "Video Photographic Image Storage");
        m.put(UID.OphthalmicPhotography8BitImageStorage, "Ophthalmic Photography 8 Bit Image Storage");
        m.put(UID.OphthalmicPhotography16BitImageStorage, "Ophthalmic Photography 16 Bit Image Storage");
        m.put(UID.StereometricRelationshipStorage, "Stereometric Relationship Storage");
        m.put(UID.OphthalmicTomographyImageStorage, "Ophthalmic Tomography Image Storage");
        m.put(UID.VLWholeSlideMicroscopyImageStorage, "VL Whole Slide Microscopy Image Storage");

        // Reports / measurements
        m.put(UID.BasicStructuredDisplayStorage, "Basic Structured Display Storage");
        m.put(UID.ProcedureLogStorage, "Procedure Log");
        m.put(UID.MammographyCADSRStorage, "Mammography CAD SR");
        m.put(UID.ChestCADSRStorage, "Chest CAD SR");
        m.put(UID.XRayRadiationDoseSRStorage, "X-Ray Radiation Dose SR");
        m.put(UID.ColonCADSRStorage, "Colon CAD SR");
        m.put(UID.ImplantationPlanSRStorage, "Implantation Plan SR Storage");
        m.put(UID.EncapsulatedCDAStorage, "Encapsulated CDA Storage");

        // RT
        m.put(UID.RTImageStorage, "RT Image Storage");
        m.put(UID.RTDoseStorage, "RT Dose Storage");
        m.put(UID.RTStructureSetStorage, "RT Structure Set Storage");
        m.put(UID.RTBeamsTreatmentRecordStorage, "RT Beams Treatment Record Storage");
        m.put(UID.RTPlanStorage, "RT Plan Storage");
        m.put(UID.RTBrachyTreatmentRecordStorage, "RT Brachy Treatment Record Storage");
        m.put(UID.RTTreatmentSummaryRecordStorage, "RT Treatment Summary Record Storage");
        m.put(UID.RTIonPlanStorage, "RT Ion Plan Storage");
        m.put(UID.RTIonBeamsTreatmentRecordStorage, "RT Ion Beams Treatment Record Storage");
        m.put("1.2.840.10008.5.1.4.34.7", "RT Beams Delivery Instruction Storage");

        // Hanging protocol / palette
        m.put(UID.HangingProtocolStorage, "Hanging Protocol Storage");
        m.put(UID.ColorPaletteStorage, "Color Palette Storage");

        // Implants/templates
        m.put(UID.GenericImplantTemplateStorage, "Generic Implant Template Storage");
        m.put(UID.ImplantAssemblyTemplateStorage, "Implant Assembly Template Storage");
        m.put(UID.ImplantTemplateGroupStorage, "Implant Template Group Storage");

        KNOWN_SOP_CLASSES = Collections.unmodifiableMap(m);
    }
}

