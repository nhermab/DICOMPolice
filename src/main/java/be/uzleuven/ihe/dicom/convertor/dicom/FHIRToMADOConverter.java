package be.uzleuven.ihe.dicom.convertor.dicom;

import be.uzleuven.ihe.dicom.constants.CodeConstants;
import be.uzleuven.ihe.dicom.constants.DicomConstants;
import org.dcm4che3.data.*;
import org.hl7.fhir.r5.model.*;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import static be.uzleuven.ihe.dicom.constants.CodeConstants.*;
import static be.uzleuven.ihe.dicom.creator.utils.DicomCreatorUtils.*;
import static be.uzleuven.ihe.dicom.creator.utils.SRContentItemUtils.*;
import static be.uzleuven.ihe.singletons.HAPI.FHIR_R5_CONTEXT;

/**
 * Converts FHIR MADO Bundles back to DICOM MADO KOS manifests.
 *
 * This converter performs the reverse transformation of MADOToFHIRConverter,
 * allowing round-trip conversion: DICOM MADO -> FHIR MADO -> DICOM MADO
 *
 * The goal is to preserve as much information as possible during round-trip conversion,
 * ensuring that the resulting DICOM file is semantically equivalent to the original.
 *
 * Compliant with IHE RAD MADO profile specification.
 */
public class FHIRToMADOConverter {

    // Shared FHIR context (initialization is expensive, so create once and reuse)


    // IHE UID prefix used in FHIR identifiers
    public static final String IHE_UID_PREFIX = "ihe:urn:oid:";

    // Default values when information is not available
    private static final String DEFAULT_INSTITUTION = "IHE Demo Hospital";
    private static final String DEFAULT_MANUFACTURER = "DICOMPolice";
    private static final String DEFAULT_MODEL = "FHIR-to-MADO Converter";
    private static final String DEFAULT_SOFTWARE_VERSION = "1.0";
    private static final String DEFAULT_PATIENT_ID_ISSUER_OID = "1.3.6.1.4.1.21297.100.1.1";
    private static final String DEFAULT_ACCESSION_ISSUER_OID = "1.3.6.1.4.1.21297.120.1.1";
    private static final String DEFAULT_RETRIEVE_LOCATION_UID = "1.3.6.1.4.1.21297.150.1.2";

    // SNOMED to SRT reverse mapping
    private static final Map<String, String[]> SNOMED_TO_SRT_MAP = createSnomedToSrtMap();

    // ============================================================================
    // PUBLIC API
    // ============================================================================

    /**
     * Converts a FHIR JSON string to DICOM MADO Attributes.
     *
     * @param fhirJson The FHIR Bundle as a JSON string
     * @return DICOM Attributes for a KOS document
     * @throws IllegalArgumentException If the JSON is not a valid MADO manifest
     */
    public Attributes convertFromJson(String fhirJson) {
        Bundle bundle = FHIR_R5_CONTEXT.newJsonParser().parseResource(Bundle.class, fhirJson);
        return convert(bundle);
    }

    /**
     * Converts a FHIR JSON file to DICOM MADO Attributes.
     *
     * @param fhirJsonFile The FHIR Bundle JSON file
     * @return DICOM Attributes for a KOS document
     * @throws IOException If the file cannot be read
     * @throws IllegalArgumentException If the file is not a valid MADO manifest
     */
    public Attributes convertFromJsonFile(File fhirJsonFile) throws IOException {
        String json = new String(java.nio.file.Files.readAllBytes(fhirJsonFile.toPath()),
            java.nio.charset.StandardCharsets.UTF_8);
        return convertFromJson(json);
    }

    /**
     * Converts a FHIR JSON file to DICOM MADO Attributes.
     *
     * @param fhirJsonPath Path to the FHIR Bundle JSON file
     * @return DICOM Attributes for a KOS document
     * @throws IOException If the file cannot be read
     * @throws IllegalArgumentException If the file is not a valid MADO manifest
     */
    public Attributes convertFromJsonFile(String fhirJsonPath) throws IOException {
        return convertFromJsonFile(new File(fhirJsonPath));
    }

    /**
     * Converts a FHIR Bundle to DICOM MADO Attributes.
     *
     * @param bundle The FHIR Document Bundle (MADO Imaging Manifest)
     * @return DICOM Attributes for a KOS document
     * @throws IllegalArgumentException If the bundle is not a valid MADO manifest
     */
    public Attributes convert(Bundle bundle) {
        validateBundle(bundle);

        // Extract resources from bundle
        BundleResources resources = extractResources(bundle);

        // Create DICOM dataset
        Attributes mado = new Attributes();

        // Populate modules
        populateSOPCommonModule(mado, bundle, resources);
        populatePatientModule(mado, resources.patient);
        populateStudyModule(mado, resources);
        populateSeriesModule(mado, bundle, resources);
        populateEquipmentModule(mado, resources.device);
        populateSRDocumentModule(mado, resources);

        // Configure SR root attributes
        configureSRRootAttributes(mado);

        // Build Evidence Sequence from ImagingStudy
        buildEvidenceSequence(mado, resources);

        // Build Referenced Request Sequence
        buildReferencedRequestSequence(mado, resources);

        // Build SR Content Sequence with TID 1600 Image Library
        buildContentSequence(mado, resources);

        return mado;
    }

    /**
     * Converts and saves a FHIR Bundle to a DICOM file.
     *
     * @param bundle The FHIR Document Bundle
     * @param outputFile The output DICOM file
     * @throws IOException If the file cannot be written
     */
    public void convertAndSave(Bundle bundle, File outputFile) throws IOException {
        Attributes mado = convert(bundle);
        writeDicomFile(outputFile, mado);
    }

    /**
     * Converts and saves a FHIR Bundle to a DICOM file.
     *
     * @param bundle The FHIR Document Bundle
     * @param outputPath The output file path
     * @throws IOException If the file cannot be written
     */
    public void convertAndSave(Bundle bundle, String outputPath) throws IOException {
        convertAndSave(bundle, new File(outputPath));
    }

    // ============================================================================
    // VALIDATION
    // ============================================================================

    private void validateBundle(Bundle bundle) {
        if (bundle == null) {
            throw new IllegalArgumentException("Bundle cannot be null");
        }

        if (bundle.getType() != Bundle.BundleType.DOCUMENT) {
            throw new IllegalArgumentException("Bundle must be of type 'document', found: " + bundle.getType());
        }

        // First entry must be Composition
        if (bundle.getEntry().isEmpty()) {
            throw new IllegalArgumentException("Bundle must have at least one entry (Composition)");
        }

        Resource firstResource = bundle.getEntryFirstRep().getResource();
        if (!(firstResource instanceof Composition)) {
            throw new IllegalArgumentException("First entry must be a Composition, found: " +
                (firstResource != null ? firstResource.getResourceType() : "null"));
        }
    }

    // ============================================================================
    // RESOURCE EXTRACTION
    // ============================================================================

    private BundleResources extractResources(Bundle bundle) {
        BundleResources resources = new BundleResources();

        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            Resource resource = entry.getResource();

            if (resource instanceof Composition) {
                resources.composition = (Composition) resource;
            } else if (resource instanceof Patient) {
                resources.patient = (Patient) resource;
            } else if (resource instanceof ImagingStudy) {
                resources.imagingStudy = (ImagingStudy) resource;
            } else if (resource instanceof Device) {
                resources.device = (Device) resource;
            } else if (resource instanceof Practitioner) {
                resources.practitioner = (Practitioner) resource;
            } else if (resource instanceof Endpoint) {
                resources.endpoints.add((Endpoint) resource);
            } else if (resource instanceof Basic) {
                // ImagingSelection backport
                resources.imagingSelections.add((Basic) resource);
            }
        }

        if (resources.composition == null) {
            throw new IllegalArgumentException("Bundle must contain a Composition resource");
        }
        if (resources.patient == null) {
            throw new IllegalArgumentException("Bundle must contain a Patient resource");
        }
        if (resources.imagingStudy == null) {
            throw new IllegalArgumentException("Bundle must contain an ImagingStudy resource");
        }

        return resources;
    }

    // ============================================================================
    // EXTENSION HELPERS
    // ============================================================================

    /**
     * Extracts a string value from an extension on a resource.
     */
    private String getExtensionStringValue(DomainResource resource, String extensionUrl) {
        if (resource == null) return null;
        for (Extension ext : resource.getExtension()) {
            if (extensionUrl.equals(ext.getUrl())) {
                if (ext.getValue() instanceof StringType) {
                    return ((StringType) ext.getValue()).getValue();
                }
            }
        }
        return null;
    }

    /**
     * Extracts a string value from an extension on an identifier.
     */
    private String getExtensionStringValue(Identifier identifier, String extensionUrl) {
        if (identifier == null) return null;
        for (Extension ext : identifier.getExtension()) {
            if (extensionUrl.equals(ext.getUrl())) {
                if (ext.getValue() instanceof StringType) {
                    return ((StringType) ext.getValue()).getValue();
                }
            }
        }
        return null;
    }

    /**
     * Extracts a string value from an extension on an ImagingStudy series component.
     */
    private String getSeriesExtensionStringValue(ImagingStudy.ImagingStudySeriesComponent series, String extensionUrl) {
        if (series == null) return null;
        for (Extension ext : series.getExtension()) {
            if (extensionUrl.equals(ext.getUrl())) {
                if (ext.getValue() instanceof StringType) {
                    return ((StringType) ext.getValue()).getValue();
                }
            }
        }
        return null;
    }

    // ============================================================================
    // MODULE POPULATION
    // ============================================================================

    private void populateSOPCommonModule(Attributes mado, Bundle bundle, BundleResources resources) {
        mado.setString(Tag.SOPClassUID, VR.UI, KOS_SOP_CLASS_UID);

        // Extract SOP Instance UID from Bundle or Composition identifier
        String sopInstanceUID = extractUidFromIdentifier(bundle.getIdentifier());
        if (sopInstanceUID == null && resources.composition != null) {
            for (Identifier id : resources.composition.getIdentifier()) {
                sopInstanceUID = extractUidFromIdentifier(id);
                if (sopInstanceUID != null) break;
            }
        }

        // Generate if not found
        if (sopInstanceUID == null) {
            sopInstanceUID = createNormalizedUid();
        }

        mado.setString(Tag.SOPInstanceUID, VR.UI, sopInstanceUID);
        mado.setString(Tag.SpecificCharacterSet, VR.CS, "ISO_IR 192"); // UTF-8
    }

    private void populatePatientModule(Attributes mado, Patient patient) {
        // Patient ID
        if (patient.hasIdentifier()) {
            Identifier identifier = patient.getIdentifierFirstRep();
            mado.setString(Tag.PatientID, VR.LO, identifier.getValue());

            // Check for local namespace extension (preserved for round-trip)
            String localNamespace = getExtensionStringValue(identifier, EXT_LOCAL_NAMESPACE);
            String typeOfPatientId = getExtensionStringValue(identifier, EXT_TYPE_OF_PATIENT_ID);

            // Issuer of Patient ID - prefer local namespace if available
            if (localNamespace != null && !localNamespace.isEmpty()) {
                mado.setString(Tag.IssuerOfPatientID, VR.LO, localNamespace);
            }

            // OID from system for IssuerOfPatientIDQualifiersSequence
            String system = identifier.getSystem();
            if (system != null) {
                String issuerOid = extractOidFromSystem(system);
                if (issuerOid != null) {
                    // If no local namespace was set, use OID as IssuerOfPatientID too
                    if (localNamespace == null || localNamespace.isEmpty()) {
                        mado.setString(Tag.IssuerOfPatientID, VR.LO, issuerOid);
                    }
                    addPatientIDQualifiers(mado, issuerOid);
                }
            }

            // TypeOfPatientID
            if (typeOfPatientId != null && !typeOfPatientId.isEmpty()) {
                mado.setString(Tag.TypeOfPatientID, VR.CS, typeOfPatientId);
            }
        } else {
            mado.setString(Tag.PatientID, VR.LO, "UNKNOWN");
        }

        // Patient Name
        if (patient.hasName()) {
            HumanName name = patient.getNameFirstRep();
            String dicomName = buildDicomName(name);
            mado.setString(Tag.PatientName, VR.PN, dicomName);
        } else {
            mado.setString(Tag.PatientName, VR.PN, "UNKNOWN^PATIENT");
        }

        // Birth Date
        if (patient.hasBirthDate()) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
            mado.setString(Tag.PatientBirthDate, VR.DA, sdf.format(patient.getBirthDate()));
        }

        // Sex
        if (patient.hasGender()) {
            String sex = mapGenderToSex(patient.getGender());
            mado.setString(Tag.PatientSex, VR.CS, sex);
        }
    }

    private void populateStudyModule(Attributes mado, BundleResources resources) {
        ImagingStudy study = resources.imagingStudy;

        // Study Instance UID
        String studyUID = null;
        for (Identifier id : study.getIdentifier()) {
            studyUID = extractUidFromIdentifier(id);
            if (studyUID != null) break;
        }
        if (studyUID == null) {
            studyUID = createNormalizedUid();
        }
        mado.setString(Tag.StudyInstanceUID, VR.UI, studyUID);

        // Study Date/Time from started - preserve original format with .0 suffix if available
        if (study.hasStarted()) {
            Date started = study.getStarted();
            SimpleDateFormat dateFmt = new SimpleDateFormat("yyyyMMdd");
            SimpleDateFormat timeFmt = new SimpleDateFormat("HHmmss.S");
            mado.setString(Tag.StudyDate, VR.DA, dateFmt.format(started));
            mado.setString(Tag.StudyTime, VR.TM, timeFmt.format(started));
        }

        // Study Description
        if (study.hasDescription()) {
            mado.setString(Tag.StudyDescription, VR.LO, study.getDescription());
        }

        // Study ID - first try extension (for round-trip), then fallback
        String studyId = getExtensionStringValue(study, EXT_STUDY_ID);
        if (studyId == null || studyId.isEmpty()) {
            studyId = "1";
            if (resources.composition != null && resources.composition.hasTitle()) {
                String title = resources.composition.getTitle();
                if (title != null && title.contains(" - Study ")) {
                    studyId = title.substring(title.lastIndexOf(" - Study ") + 9).trim();
                }
            }
        }
        mado.setString(Tag.StudyID, VR.SH, studyId);

        // Accession Number from basedOn
        if (study.hasBasedOn()) {
            Reference basedOn = study.getBasedOnFirstRep();
            if (basedOn.hasIdentifier()) {
                Identifier accessionId = basedOn.getIdentifier();
                String accessionValue = accessionId.getValue();

                // Only set if value is not null or empty
                if (accessionValue != null && !accessionValue.isEmpty()) {
                    mado.setString(Tag.AccessionNumber, VR.SH, accessionValue);

                    // Issuer of Accession Number
                    String system = accessionId.getSystem();
                    String issuerOid = extractOidFromSystem(system);
                    if (issuerOid != null) {
                        addAccessionNumberIssuer(mado, issuerOid);
                    }
                }
            }
        }

        // Referring Physician - first try extension (for round-trip), then practitioner
        String refPhysName = getExtensionStringValue(study, EXT_REFERRING_PHYSICIAN);
        if (refPhysName != null && !refPhysName.isEmpty()) {
            mado.setString(Tag.ReferringPhysicianName, VR.PN, refPhysName);
        } else if (study.hasReferrer() && resources.practitioner != null) {
            refPhysName = buildDicomNameFromPractitioner(resources.practitioner);
            mado.setString(Tag.ReferringPhysicianName, VR.PN, refPhysName);
        }
    }

    private void populateSeriesModule(Attributes mado, Bundle bundle, BundleResources resources) {
        ImagingStudy study = resources.imagingStudy;

        // Try to use original SeriesInstanceUID from extension
        String seriesInstanceUID = getExtensionStringValue(study, EXT_SERIES_INSTANCE_UID);
        if (seriesInstanceUID == null || seriesInstanceUID.isEmpty()) {
            seriesInstanceUID = createNormalizedUid();
        }
        mado.setString(Tag.SeriesInstanceUID, VR.UI, seriesInstanceUID);
        mado.setString(Tag.Modality, VR.CS, "KO");
        mado.setString(Tag.SeriesNumber, VR.IS, "1");

        // Series Date/Time - first try extensions, then bundle timestamp
        String seriesDate = getExtensionStringValue(study, EXT_SERIES_DATE);
        String seriesTime = getExtensionStringValue(study, EXT_SERIES_TIME);

        if (seriesDate != null && !seriesDate.isEmpty()) {
            mado.setString(Tag.SeriesDate, VR.DA, seriesDate);
        } else if (bundle.hasTimestamp()) {
            SimpleDateFormat dateFmt = new SimpleDateFormat("yyyyMMdd");
            mado.setString(Tag.SeriesDate, VR.DA, dateFmt.format(bundle.getTimestamp()));
        }

        if (seriesTime != null && !seriesTime.isEmpty()) {
            mado.setString(Tag.SeriesTime, VR.TM, seriesTime);
        } else if (bundle.hasTimestamp()) {
            SimpleDateFormat timeFmt = new SimpleDateFormat("HHmmss.S");
            mado.setString(Tag.SeriesTime, VR.TM, timeFmt.format(bundle.getTimestamp()));
        }

        mado.setString(Tag.SeriesDescription, VR.LO, "MADO Manifest");

        // ReferencedPerformedProcedureStepSequence - Type 2 (can be empty)
        mado.newSequence(Tag.ReferencedPerformedProcedureStepSequence, 0);
    }


    private void populateEquipmentModule(Attributes mado, Device device) {
        if (device != null) {
            // Try to get original manufacturer from extension first (for round-trip)
            String originalManufacturer = getExtensionStringValue(device, EXT_ORIGINAL_MANUFACTURER);

            if (originalManufacturer != null) {
                // Use the original value from extension (could be empty)
                if (!originalManufacturer.isEmpty()) {
                    mado.setString(Tag.Manufacturer, VR.LO, originalManufacturer);
                }
                // If empty, don't set the tag to preserve null/empty state
            } else if (device.hasManufacturer() && !device.getManufacturer().isEmpty()) {
                mado.setString(Tag.Manufacturer, VR.LO, device.getManufacturer());
            } else {
                // Only use default if we have no information at all
                mado.setString(Tag.Manufacturer, VR.LO, DEFAULT_MANUFACTURER);
            }

            if (device.hasName()) {
                mado.setString(Tag.ManufacturerModelName, VR.LO,
                    device.getNameFirstRep().getValue());
            } else {
                mado.setString(Tag.ManufacturerModelName, VR.LO, DEFAULT_MODEL);
            }

            if (device.hasVersion()) {
                mado.setString(Tag.SoftwareVersions, VR.LO,
                    device.getVersionFirstRep().getValue());
            } else {
                mado.setString(Tag.SoftwareVersions, VR.LO, DEFAULT_SOFTWARE_VERSION);
            }

            if (device.hasOwner() && device.getOwner().hasDisplay()) {
                mado.setString(Tag.InstitutionName, VR.LO, device.getOwner().getDisplay());
            } else {
                mado.setString(Tag.InstitutionName, VR.LO, DEFAULT_INSTITUTION);
            }
        } else {
            mado.setString(Tag.Manufacturer, VR.LO, DEFAULT_MANUFACTURER);
            mado.setString(Tag.ManufacturerModelName, VR.LO, DEFAULT_MODEL);
            mado.setString(Tag.SoftwareVersions, VR.LO, DEFAULT_SOFTWARE_VERSION);
            mado.setString(Tag.InstitutionName, VR.LO, DEFAULT_INSTITUTION);
        }
    }

    private void populateSRDocumentModule(Attributes mado, BundleResources resources) {
        mado.setString(Tag.InstanceNumber, VR.IS, "1");

        ImagingStudy study = resources.imagingStudy;

        // Content Date/Time - first try extensions (for round-trip), then Composition date
        String contentDateStr = getExtensionStringValue(study, EXT_CONTENT_DATE);
        String contentTimeStr = getExtensionStringValue(study, EXT_CONTENT_TIME);

        if (contentDateStr != null && !contentDateStr.isEmpty()) {
            mado.setString(Tag.ContentDate, VR.DA, contentDateStr);
        } else {
            Date contentDate = null;
            if (resources.composition.hasDate()) {
                contentDate = resources.composition.getDate();
            }
            if (contentDate == null) {
                contentDate = new Date();
            }
            SimpleDateFormat dateFmt = new SimpleDateFormat("yyyyMMdd");
            mado.setString(Tag.ContentDate, VR.DA, dateFmt.format(contentDate));
        }

        if (contentTimeStr != null && !contentTimeStr.isEmpty()) {
            mado.setString(Tag.ContentTime, VR.TM, contentTimeStr);
        } else {
            Date contentDate = null;
            if (resources.composition.hasDate()) {
                contentDate = resources.composition.getDate();
            }
            if (contentDate == null) {
                contentDate = new Date();
            }
            SimpleDateFormat timeFmt = new SimpleDateFormat("HHmmss.SSSSSS");
            mado.setString(Tag.ContentTime, VR.TM, timeFmt.format(contentDate));
        }

        // Timezone offset
        String timezoneOffset = timezoneOffsetFromUTC();
        mado.setString(Tag.TimezoneOffsetFromUTC, VR.SH, timezoneOffset);

        //this section below is wrong for KOS, it applies only to SR General
        // Completion and Verification flags
        /*mado.setString(Tag.CompletionFlag, VR.CS, DicomConstants.COMPLETION_FLAG_COMPLETE);
        mado.setString(Tag.VerificationFlag, VR.CS, DicomConstants.VERIFICATION_FLAG_UNVERIFIED);
        */
    }

    private void configureSRRootAttributes(Attributes mado) {
        mado.setString(Tag.ValueType, VR.CS, "CONTAINER");
        mado.setString(Tag.ContinuityOfContent, VR.CS, DicomConstants.CONTINUITY_SEPARATE);

        // Document Title: MADO requires Manifest or Manifest with Description
        Sequence conceptNameCodeSeq = mado.newSequence(Tag.ConceptNameCodeSequence, 1);
        conceptNameCodeSeq.add(code(CODE_MANIFEST_WITH_DESCRIPTION, SCHEME_DCM, MEANING_MANIFEST_WITH_DESCRIPTION));

        // Explicitly identify TID 2010 (XDS-I / KOS template)
        mado.newSequence(Tag.ContentTemplateSequence, 1)
            .add(createTemplateItem("2010"));

        // MADO Preliminary Flag (recommended)
        mado.setString(Tag.PreliminaryFlag, VR.CS, "FINAL");
    }

    // ============================================================================
    // EVIDENCE SEQUENCE
    // ============================================================================

    private void buildEvidenceSequence(Attributes mado, BundleResources resources) {
        ImagingStudy study = resources.imagingStudy;

        String studyUID = null;
        for (Identifier id : study.getIdentifier()) {
            studyUID = extractUidFromIdentifier(id);
            if (studyUID != null) break;
        }
        if (studyUID == null) {
            studyUID = mado.getString(Tag.StudyInstanceUID);
        }

        Sequence evidenceSeq = mado.newSequence(Tag.CurrentRequestedProcedureEvidenceSequence, 1);
        Attributes studyItem = new Attributes();
        studyItem.setString(Tag.StudyInstanceUID, VR.UI, studyUID);

        // Get WADO-RS base URL from endpoints
        String wadoBaseUrl = extractWadoBaseUrl(resources.endpoints);

        Sequence refSeriesSeq = studyItem.newSequence(Tag.ReferencedSeriesSequence,
            study.hasSeries() ? study.getSeries().size() : 0);

        for (ImagingStudy.ImagingStudySeriesComponent series : study.getSeries()) {
            Attributes seriesItem = buildSeriesEvidenceItem(series, studyUID, wadoBaseUrl);
            refSeriesSeq.add(seriesItem);
        }

        evidenceSeq.add(studyItem);
    }

    private Attributes buildSeriesEvidenceItem(ImagingStudy.ImagingStudySeriesComponent series,
                                               String studyUID, String wadoBaseUrl) {
        Attributes seriesItem = new Attributes();
        seriesItem.setString(Tag.SeriesInstanceUID, VR.UI, series.getUid());

        // Modality
        if (series.hasModality()) {
            Coding modalityCoding = series.getModality().getCodingFirstRep();
            seriesItem.setString(Tag.Modality, VR.CS, modalityCoding.getCode());
        } else {
            seriesItem.setString(Tag.Modality, VR.CS, "OT");
        }

        // Retrieve Location UID
        seriesItem.setString(Tag.RetrieveLocationUID, VR.UI, DEFAULT_RETRIEVE_LOCATION_UID);

        // Retrieve URL
        if (wadoBaseUrl != null) {
            String wadoUrl = wadoBaseUrl + "/studies/" + studyUID + "/series/" + series.getUid();
            seriesItem.setString(Tag.RetrieveURL, VR.UR, wadoUrl.trim());
        }

        // Instance references
        Sequence refSopSeq = seriesItem.newSequence(Tag.ReferencedSOPSequence,
            series.hasInstance() ? series.getInstance().size() : 0);

        for (ImagingStudy.ImagingStudySeriesInstanceComponent instance : series.getInstance()) {
            Attributes sopItem = new Attributes();

            // SOP Class UID - extract from the Coding
            if (instance.hasSopClass()) {
                String sopClassCode = instance.getSopClass().getCode();
                String sopClassUID = extractUidFromIhePrefix(sopClassCode);
                sopItem.setString(Tag.ReferencedSOPClassUID, VR.UI, sopClassUID);
            }

            // SOP Instance UID
            sopItem.setString(Tag.ReferencedSOPInstanceUID, VR.UI, instance.getUid());

            // Instance Number
            if (instance.hasNumber()) {
                sopItem.setString(Tag.InstanceNumber, VR.IS, String.valueOf(instance.getNumber()));
                System.out.println("DEBUG FHIRToMADOConverter.buildSeriesEvidenceItem: Adding InstanceNumber=" + instance.getNumber() + " to evidence for SOP " + instance.getUid());
            } else {
                System.out.println("DEBUG FHIRToMADOConverter.buildSeriesEvidenceItem: NO InstanceNumber for SOP " + instance.getUid());
            }

            // Extract dimensions from extension if available
            for (Extension ext : instance.getExtension()) {
                if (EXT_INSTANCE_DESCRIPTION.equals(ext.getUrl())) {
                    String desc = ((StringType) ext.getValue()).getValue();
                    parseDimensionsFromDescription(sopItem, desc);
                }
            }

            refSopSeq.add(sopItem);
        }

        return seriesItem;
    }

    private void parseDimensionsFromDescription(Attributes sopItem, String description) {
        if (description == null) return;

        // Parse "512x512 (10 frames)" or "512x512" or "10 frames"
        try {
            if (description.contains("x")) {
                String dimensionPart = description.split("\\s+")[0];
                String[] dims = dimensionPart.split("x");
                if (dims.length == 2) {
                    sopItem.setString(Tag.Rows, VR.US, dims[0].trim());
                    sopItem.setString(Tag.Columns, VR.US, dims[1].trim());
                }
            }

            if (description.contains("frames")) {
                // Extract number before "frames"
                String framesPart = description;
                if (description.contains("(")) {
                    framesPart = description.substring(description.indexOf("(") + 1);
                }
                String numFrames = framesPart.replaceAll("[^0-9]", "");
                if (!numFrames.isEmpty()) {
                    sopItem.setString(Tag.NumberOfFrames, VR.IS, numFrames);
                }
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
    }

    // ============================================================================
    // REFERENCED REQUEST SEQUENCE
    // ============================================================================

    private void buildReferencedRequestSequence(Attributes mado, BundleResources resources) {
        ImagingStudy study = resources.imagingStudy;

        Sequence refRequestSeq = mado.newSequence(Tag.ReferencedRequestSequence, 1);

        String studyUID = null;
        for (Identifier id : study.getIdentifier()) {
            studyUID = extractUidFromIdentifier(id);
            if (studyUID != null) break;
        }
        if (studyUID == null) {
            studyUID = mado.getString(Tag.StudyInstanceUID);
        }

        // Process each basedOn as a separate request
        for (Reference basedOn : study.getBasedOn()) {
            if (basedOn.hasIdentifier()) {
                Identifier accessionId = basedOn.getIdentifier();
                String accessionValue = accessionId.getValue();

                // Only process if accession number is not null or empty
                if (accessionValue != null && !accessionValue.isEmpty()) {
                    Attributes reqItem = new Attributes();

                    reqItem.setString(Tag.AccessionNumber, VR.SH, accessionValue);

                    // Issuer of Accession Number
                    String system = accessionId.getSystem();
                    String issuerOid = extractOidFromSystem(system);
                    if (issuerOid != null) {
                        Sequence issuerAccSeq = reqItem.newSequence(Tag.IssuerOfAccessionNumberSequence, 1);
                        Attributes issuerAcc = new Attributes();
                        issuerAcc.setString(Tag.UniversalEntityID, VR.UT, issuerOid);
                        issuerAcc.setString(Tag.UniversalEntityIDType, VR.CS, "ISO");
                        issuerAccSeq.add(issuerAcc);
                    }

                    reqItem.setString(Tag.StudyInstanceUID, VR.UI, studyUID);
                    reqItem.setString(Tag.RequestedProcedureID, VR.SH, "RP001");
                    reqItem.setString(Tag.PlacerOrderNumberImagingServiceRequest, VR.LO, "PO001");
                    reqItem.setString(Tag.FillerOrderNumberImagingServiceRequest, VR.LO, "FO001");

                    refRequestSeq.add(reqItem);
                }
            }
        }

        // If no basedOn, create minimal request from accession number
        if (refRequestSeq.isEmpty()) {
            String accessionNumber = mado.getString(Tag.AccessionNumber);
            if (accessionNumber != null && !accessionNumber.isEmpty()) {
                Attributes reqItem = new Attributes();
                reqItem.setString(Tag.AccessionNumber, VR.SH, accessionNumber);
                reqItem.setString(Tag.StudyInstanceUID, VR.UI, studyUID);
                reqItem.setString(Tag.RequestedProcedureID, VR.SH, "RP001");
                refRequestSeq.add(reqItem);
            }
        }
    }

    // ============================================================================
    // CONTENT SEQUENCE (TID 1600 IMAGE LIBRARY)
    // ============================================================================

    private void buildContentSequence(Attributes mado, BundleResources resources) {
        ImagingStudy study = resources.imagingStudy;

        Sequence contentSeq = mado.newSequence(Tag.ContentSequence, 10);

        // TID 2010 requires Key Object Description (113012, DCM) as first item
        Attributes keyObjDesc = createTextItem(DicomConstants.RELATIONSHIP_CONTAINS,
            CodeConstants.CODE_KOS_DESCRIPTION, SCHEME_DCM, CodeConstants.MEANING_KOS_DESCRIPTION, "Manifest with Description");
        contentSeq.add(keyObjDesc);

        // Get study modality from first series
        String studyModality = "CT";
        if (study.hasSeries() && study.getSeriesFirstRep().hasModality()) {
            studyModality = study.getSeriesFirstRep().getModality().getCodingFirstRep().getCode();
        }

        String studyUID = null;
        for (Identifier id : study.getIdentifier()) {
            studyUID = extractUidFromIdentifier(id);
            if (studyUID != null) break;
        }
        if (studyUID == null) {
            studyUID = mado.getString(Tag.StudyInstanceUID);
        }

        // Extract target region from first series bodySite
        String targetRegionCode = CODE_REGION_ABDOMEN;
        String targetRegionScheme = SCHEME_SRT;
        String targetRegionMeaning = MEANING_REGION_ABDOMEN;

        if (study.hasSeries()) {
            ImagingStudy.ImagingStudySeriesComponent firstSeries = study.getSeriesFirstRep();
            if (firstSeries.hasBodySite() && firstSeries.getBodySite().hasConcept()) {
                CodeableConcept bodySite = firstSeries.getBodySite().getConcept();
                if (bodySite.hasCoding()) {
                    Coding bodySiteCoding = bodySite.getCodingFirstRep();
                    // Map SNOMED back to SRT
                    String[] srtInfo = SNOMED_TO_SRT_MAP.get(bodySiteCoding.getCode());
                    if (srtInfo != null) {
                        targetRegionCode = srtInfo[0];
                        targetRegionScheme = SCHEME_SRT;
                        targetRegionMeaning = srtInfo[1];
                    } else {
                        // Use the SNOMED code directly if no mapping
                        targetRegionCode = bodySiteCoding.getCode();
                        targetRegionScheme = "SCT"; // SNOMED scheme designator
                        targetRegionMeaning = bodySiteCoding.hasDisplay() ?
                            bodySiteCoding.getDisplay() : bodySiteCoding.getCode();
                    }
                }
            }
        }

        // Study-level acquisition context
        addStudyLevelContext(contentSeq, studyModality, studyUID, targetRegionCode,
            targetRegionScheme, targetRegionMeaning);

        // Image Library container
        contentSeq.add(buildImageLibraryContainer(study, studyModality, studyUID,
            targetRegionCode, targetRegionScheme, targetRegionMeaning));
    }

    private void addStudyLevelContext(Sequence contentSeq, String studyModality, String studyUID,
                                      String targetRegionCode, String targetRegionScheme,
                                      String targetRegionMeaning) {
        // Modality
        contentSeq.add(createCodeItem(DicomConstants.RELATIONSHIP_CONTAINS, CODE_MODALITY,
            SCHEME_DCM, MEANING_MODALITY,
            code(studyModality, SCHEME_DCM, studyModality)));

        // Study Instance UID
        contentSeq.add(createUIDRefItem(DicomConstants.RELATIONSHIP_CONTAINS, CODE_STUDY_INSTANCE_UID,
            SCHEME_DCM, MEANING_STUDY_INSTANCE_UID, studyUID));

        // Target Region
        contentSeq.add(createCodeItem(DicomConstants.RELATIONSHIP_CONTAINS, CODE_TARGET_REGION,
            SCHEME_DCM, MEANING_TARGET_REGION,
            code(targetRegionCode, targetRegionScheme, targetRegionMeaning)));
    }

    private Attributes buildImageLibraryContainer(ImagingStudy study, String studyModality,
                                                   String studyUID, String targetRegionCode,
                                                   String targetRegionScheme, String targetRegionMeaning) {
        Attributes libContainer = new Attributes();
        libContainer.setString(Tag.RelationshipType, VR.CS, DicomConstants.RELATIONSHIP_CONTAINS);
        libContainer.setString(Tag.ValueType, VR.CS, "CONTAINER");
        libContainer.newSequence(Tag.ConceptNameCodeSequence, 1)
            .add(code(CODE_IMAGE_LIBRARY, SCHEME_DCM, MEANING_IMAGE_LIBRARY));

        Sequence libContent = libContainer.newSequence(Tag.ContentSequence, 50);

        // Image Library context
        libContent.add(createCodeItem("HAS ACQ CONTEXT", CODE_MODALITY, SCHEME_DCM,
            MEANING_MODALITY, code(studyModality, SCHEME_DCM, studyModality)));

        libContent.add(createUIDRefItem("HAS ACQ CONTEXT", CODE_STUDY_INSTANCE_UID,
            SCHEME_DCM, MEANING_STUDY_INSTANCE_UID, studyUID));

        libContent.add(createCodeItem("HAS ACQ CONTEXT", CODE_TARGET_REGION,
            SCHEME_DCM, MEANING_TARGET_REGION,
            code(targetRegionCode, targetRegionScheme, targetRegionMeaning)));

        // Add series groups
        int seriesNumber = 1;
        for (ImagingStudy.ImagingStudySeriesComponent series : study.getSeries()) {
            libContent.add(buildSeriesGroup(series, seriesNumber++));
        }

        return libContainer;
    }

    private Attributes buildSeriesGroup(ImagingStudy.ImagingStudySeriesComponent series, int seriesNumber) {
        Attributes group = new Attributes();
        group.setString(Tag.RelationshipType, VR.CS, DicomConstants.RELATIONSHIP_CONTAINS);
        group.setString(Tag.ValueType, VR.CS, "CONTAINER");
        group.newSequence(Tag.ConceptNameCodeSequence, 1)
            .add(code(CODE_IMAGE_LIBRARY_GROUP, SCHEME_DCM, MEANING_IMAGE_LIBRARY_GROUP));

        Sequence groupSeq = group.newSequence(Tag.ContentSequence, 50);

        // Series metadata
        String seriesModality = "OT";
        if (series.hasModality()) {
            seriesModality = series.getModality().getCodingFirstRep().getCode();
        }

        String seriesUid = series.getUid();
        String seriesDescription = series.hasDescription() ? series.getDescription() : "(no Series Description)";
        int seriesNum = series.hasNumber() ? series.getNumber() : seriesNumber;

        // Extract Series Date/Time from extensions (for round-trip)
        String seriesDate = getSeriesExtensionStringValue(series, EXT_IMAGING_SERIES_DATE);
        String seriesTime = getSeriesExtensionStringValue(series, EXT_IMAGING_SERIES_TIME);

        // Modality
        groupSeq.add(createCodeItem("HAS ACQ CONTEXT", CODE_MODALITY, SCHEME_DCM,
            MEANING_MODALITY, code(seriesModality, SCHEME_DCM, seriesModality)));

        // Series Instance UID
        groupSeq.add(createUIDRefItem("HAS ACQ CONTEXT", CODE_SERIES_INSTANCE_UID,
            SCHEME_DCM, MEANING_SERIES_INSTANCE_UID, seriesUid));

        // Series Description
        groupSeq.add(createTextItem("HAS ACQ CONTEXT", CODE_SERIES_DESCRIPTION,
            SCHEME_DCM, MEANING_SERIES_DESCRIPTION, seriesDescription));

        // Series Date (ddd003) - only if available from extension
        if (seriesDate != null && !seriesDate.isEmpty()) {
            groupSeq.add(createTextItem("HAS ACQ CONTEXT", CODE_SERIES_DATE,
                SCHEME_DCM, MEANING_SERIES_DATE, seriesDate));
        }

        // Series Time (ddd004) - only if available from extension
        if (seriesTime != null && !seriesTime.isEmpty()) {
            groupSeq.add(createTextItem("HAS ACQ CONTEXT", CODE_SERIES_TIME,
                SCHEME_DCM, MEANING_SERIES_TIME, seriesTime));
        }

        // Series Number
        groupSeq.add(createTextItem("HAS ACQ CONTEXT", CODE_SERIES_NUMBER,
            SCHEME_DCM, MEANING_SERIES_NUMBER, Integer.toString(seriesNum)));

        // Number of instances
        groupSeq.add(createNumericItem("HAS ACQ CONTEXT", CODE_NUM_SERIES_RELATED_INSTANCES,
            SCHEME_DCM, MEANING_NUM_SERIES_RELATED_INSTANCES,
            series.hasInstance() ? series.getInstance().size() : 0));

        // Instance entries
        for (ImagingStudy.ImagingStudySeriesInstanceComponent instance : series.getInstance()) {
            groupSeq.add(buildInstanceEntry(instance));
        }

        return group;
    }

    private Attributes buildInstanceEntry(ImagingStudy.ImagingStudySeriesInstanceComponent instance) {
        Attributes entry = new Attributes();
        entry.setString(Tag.RelationshipType, VR.CS, DicomConstants.RELATIONSHIP_CONTAINS);
        entry.setString(Tag.ValueType, VR.CS, "IMAGE");

        // Referenced SOP Sequence
        Sequence refSop = entry.newSequence(Tag.ReferencedSOPSequence, 1);
        Attributes refItem = new Attributes();

        // SOP Class UID
        String sopClassUID = null;
        if (instance.hasSopClass()) {
            String sopClassCode = instance.getSopClass().getCode();
            sopClassUID = extractUidFromIhePrefix(sopClassCode);
            refItem.setString(Tag.ReferencedSOPClassUID, VR.UI, sopClassUID);
        }

        // SOP Instance UID
        refItem.setString(Tag.ReferencedSOPInstanceUID, VR.UI, instance.getUid());
        refSop.add(refItem);

        // Content sequence for instance metadata
        Sequence entryContent = entry.newSequence(Tag.ContentSequence, 10);

        // Instance Number
        String instanceNumber = instance.hasNumber() ? String.valueOf(instance.getNumber()) : "1";
        System.out.println("DEBUG FHIRToMADOConverter.buildInstanceEntry: instance.hasNumber()=" + instance.hasNumber() +
                           ", instance.getNumber()=" + (instance.hasNumber() ? instance.getNumber() : "N/A") +
                           ", using instanceNumber='" + instanceNumber + "'");
        entryContent.add(createTextItem("HAS ACQ CONTEXT", CODE_INSTANCE_NUMBER,
            SCHEME_DCM, MEANING_INSTANCE_NUMBER, instanceNumber));

        // Number of Frames - extract from instance description extension and add if multiframe SOP Class
        Integer numberOfFrames = extractNumberOfFramesFromExtension(instance);
        if (numberOfFrames != null && sopClassUID != null &&
            be.uzleuven.ihe.dicom.validator.validation.tid1600.TID1600Rules.isMultiframeSOP(sopClassUID)) {
            entryContent.add(createNumericItem("HAS ACQ CONTEXT", CODE_NUMBER_OF_FRAMES,
                SCHEME_DCM, MEANING_NUMBER_OF_FRAMES, numberOfFrames));
        }

        return entry;
    }

    /**
     * Extracts number of frames from the instance description extension.
     * Parses patterns like "512x512 (1 frames)" or "10 frames"
     */
    private Integer extractNumberOfFramesFromExtension(ImagingStudy.ImagingStudySeriesInstanceComponent instance) {
        for (Extension ext : instance.getExtension()) {
            if (EXT_INSTANCE_DESCRIPTION.equals(ext.getUrl())) {
                String desc = ((StringType) ext.getValue()).getValue();
                if (desc != null && desc.contains("frames")) {
                    try {
                        // Extract number before "frames"
                        String framesPart = desc;
                        if (desc.contains("(")) {
                            framesPart = desc.substring(desc.indexOf("(") + 1);
                        }
                        String numFrames = framesPart.replaceAll("[^0-9]", "");
                        if (!numFrames.isEmpty()) {
                            return Integer.parseInt(numFrames);
                        }
                    } catch (Exception e) {
                        // Ignore parsing errors
                    }
                }
            }
        }
        return null;
    }

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    private String extractUidFromIdentifier(Identifier identifier) {
        if (identifier == null) return null;

        String value = identifier.getValue();
        if (value == null) return null;

        // Remove IHE prefix if present
        if (value.startsWith(IHE_UID_PREFIX)) {
            return value.substring(IHE_UID_PREFIX.length());
        }

        // Check if it looks like a UID (starts with digit and contains dots)
        if (value.matches("^[0-9][0-9.]*$")) {
            return value;
        }

        return null;
    }

    private String extractUidFromIhePrefix(String value) {
        if (value == null) return null;

        if (value.startsWith(IHE_UID_PREFIX)) {
            return value.substring(IHE_UID_PREFIX.length());
        }

        return value;
    }

    private String extractOidFromSystem(String system) {
        if (system == null) return null;

        if (system.startsWith("urn:oid:")) {
            return system.substring(8);
        }

        return null;
    }

    private String extractWadoBaseUrl(List<Endpoint> endpoints) {
        for (Endpoint endpoint : endpoints) {
            if (endpoint.hasConnectionType()) {
                for (CodeableConcept ct : endpoint.getConnectionType()) {
                    for (Coding coding : ct.getCoding()) {
                        if ("dicom-wado-rs".equals(coding.getCode())) {
                            String address = endpoint.getAddress();
                            // Return base URL (before /studies)
                            if (address != null && !address.startsWith("urn:")) {
                                int studiesIndex = address.indexOf("/studies");
                                if (studiesIndex > 0) {
                                    return address.substring(0, studiesIndex);
                                }
                                return address;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private String buildDicomName(HumanName name) {
        StringBuilder sb = new StringBuilder();

        // Family name
        if (name.hasFamily()) {
            sb.append(name.getFamily());
        }

        sb.append("^");

        // Given names
        if (name.hasGiven()) {
            List<String> given = name.getGiven().stream()
                .map(StringType::getValue)
                .collect(Collectors.toList());
            if (!given.isEmpty()) {
                sb.append(given.get(0));
            }
            if (given.size() > 1) {
                sb.append("^").append(given.get(1)); // Middle name
            }
        }

        // Prefix
        if (name.hasPrefix()) {
            while (sb.toString().split("\\^").length < 4) {
                sb.append("^");
            }
            sb.append(name.getPrefixAsSingleString());
        }

        // Suffix
        if (name.hasSuffix()) {
            while (sb.toString().split("\\^").length < 5) {
                sb.append("^");
            }
            sb.append(name.getSuffixAsSingleString());
        }

        return sb.toString();
    }

    private String buildDicomNameFromPractitioner(Practitioner practitioner) {
        if (practitioner.hasName()) {
            return buildDicomName(practitioner.getNameFirstRep());
        }
        return "";
    }

    private String mapGenderToSex(Enumerations.AdministrativeGender gender) {
        switch (gender) {
            case MALE:
                return "M";
            case FEMALE:
                return "F";
            case OTHER:
                return "O";
            default:
                return "U";
        }
    }

    private void addPatientIDQualifiers(Attributes mado, String oid) {
        Sequence qualSeq = mado.newSequence(Tag.IssuerOfPatientIDQualifiersSequence, 1);
        Attributes qual = new Attributes();
        qual.setString(Tag.UniversalEntityID, VR.UT, oid);
        qual.setString(Tag.UniversalEntityIDType, VR.CS, "ISO");
        qualSeq.add(qual);
    }

    private void addAccessionNumberIssuer(Attributes mado, String oid) {
        Sequence issuerSeq = mado.newSequence(Tag.IssuerOfAccessionNumberSequence, 1);
        Attributes issuer = new Attributes();
        issuer.setString(Tag.UniversalEntityID, VR.UT, oid);
        issuer.setString(Tag.UniversalEntityIDType, VR.CS, "ISO");
        issuerSeq.add(issuer);
    }

    private static Map<String, String[]> createSnomedToSrtMap() {
        // Reverse of BODY_SITE_MAP: SNOMED code -> {SRT code, meaning}
        Map<String, String[]> map = new HashMap<>();
        map.put(SNOMED_ABDOMEN, new String[]{CODE_REGION_ABDOMEN, "Abdomen"});
        map.put(SNOMED_HEAD, new String[]{SRT_REGION_HEAD, "Head"});
        map.put(SNOMED_HEAD_AND_NECK, new String[]{SRT_REGION_HEAD_AND_NECK, "Head and neck"});
        map.put(SNOMED_THORAX, new String[]{SRT_REGION_THORAX, "Thorax"});
        map.put(SNOMED_ENTIRE_BODY, new String[]{SRT_REGION_ENTIRE_BODY, "Entire body"});
        map.put(SNOMED_LOWER_LIMB, new String[]{SRT_REGION_LOWER_LIMB, "Lower limb"});
        map.put(SNOMED_LOWER_LEG, new String[]{SRT_REGION_LOWER_LEG, "Lower leg"});
        map.put(SNOMED_UPPER_LIMB, new String[]{SRT_REGION_UPPER_LIMB, "Upper limb"});
        map.put(SNOMED_BREAST_REGION, new String[]{SRT_REGION_BREAST, "Breast region"});
        map.put(SNOMED_PELVIS, new String[]{SRT_REGION_PELVIS, "Pelvis"});
        return map;
    }


    // ============================================================================
    // HELPER CLASSES
    // ============================================================================

    private static class BundleResources {
        Composition composition;
        Patient patient;
        ImagingStudy imagingStudy;
        Device device;
        Practitioner practitioner;
        List<Endpoint> endpoints = new ArrayList<>();
        List<Basic> imagingSelections = new ArrayList<>();
    }
}

