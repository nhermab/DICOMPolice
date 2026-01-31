package be.uzleuven.ihe.dicom.convertor.fhir;

import be.uzleuven.ihe.dicom.constants.CodeConstants;
import be.uzleuven.ihe.dicom.constants.DicomConstants;
import be.uzleuven.ihe.dicom.convertor.utils.DeterministicUuidGenerator;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;
import org.hl7.fhir.r5.model.*;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static be.uzleuven.ihe.dicom.constants.CodeConstants.*;

/**
 * Converts DICOM MADO (Manifest-based Access to DICOM Objects) KOS manifests to FHIR Bundles.
 *
 * This converter follows the IHE MADO profile specification (ImImagingStudyManifest),
 * transforming DICOM Key Object Selection Documents into FHIR Document Bundles containing:
 * - Composition resource (document header, first entry)
 * - Patient resource
 * - ImagingStudy resource with series and instances
 * - Endpoint resources for WADO-RS and IHE-IID access
 * - ImagingSelection resources for key image flagging
 * - Device resource for the creating system
 * - Practitioner resource for the author (if available)
 *
 * Compliant with MADO Implementation Guide specifications.
 * Note: Uses FHIR R5 API to match the imaging manifest profiles.
 */
public class MADOToFHIRConverter {

    // ============================================================================
    // CONFIGURATION
    // ============================================================================

    /**
     * When true (default), UUIDs are generated deterministically based on DICOM identifiers.
     * This ensures that converting the same DICOM file multiple times produces identical UUIDs,
     * making it easier to compare outputs.
     * <p>
     * When false, random UUIDs are generated for each conversion (pre-existing behavior).
     */
    private boolean useDeterministicUuids = true;

    // ============================================================================
    // CONSTANTS
    // ============================================================================

    // KOS SOP Class UID for validation

    // MADO IG mandated UID prefix (invariant im-imagingstudy-01)
    // The MADO IG requires "ihe:urn:oid:" for identifiers per im-imagingstudy-01
    public static final String IHE_UID_PREFIX = "ihe:urn:oid:";

    // Body site mapping from SRT codes to SNOMED CT
    // Maps SRT anatomical region codes to {SNOMED CT code, display name}


    // ============================================================================
    // PUBLIC API
    // ============================================================================

    /**
     * Returns whether deterministic UUID generation is enabled.
     *
     * @return true if UUIDs are generated deterministically based on DICOM identifiers
     */
    public boolean isUseDeterministicUuids() {
        return useDeterministicUuids;
    }

    /**
     * Sets whether to use deterministic UUID generation.
     * <p>
     * When true (default), UUIDs are generated deterministically based on DICOM identifiers,
     * ensuring identical outputs for the same input DICOM file. This makes it easier to
     * compare conversion outputs across multiple conversions.
     * <p>
     * When false, random UUIDs are generated for each conversion.
     *
     * @param useDeterministicUuids true to enable deterministic UUIDs, false for random UUIDs
     * @return this converter instance for method chaining
     */
    public MADOToFHIRConverter setUseDeterministicUuids(boolean useDeterministicUuids) {
        this.useDeterministicUuids = useDeterministicUuids;
        return this;
    }

    /**
     * Converts a DICOM file to a FHIR Document Bundle.
     *
     * @param dicomFile Path to the DICOM MADO KOS file
     * @return FHIR Document Bundle compliant with MADO IG
     * @throws IOException If file cannot be read
     * @throws IllegalArgumentException If the file is not a valid MADO KOS
     */
    public Bundle convert(String dicomFile) throws IOException {
        return convert(new File(dicomFile));
    }

    /**
     * Converts a DICOM file to a FHIR Document Bundle.
     *
     * @param dicomFile The DICOM MADO KOS file
     * @return FHIR Document Bundle compliant with MADO IG
     * @throws IOException If file cannot be read
     * @throws IllegalArgumentException If the file is not a valid MADO KOS
     */
    public Bundle convert(File dicomFile) throws IOException {
        Attributes attrs;
        try (DicomInputStream dis = new DicomInputStream(dicomFile)) {
            attrs = dis.readDataset();
        }
        return convert(attrs);
    }

    /**
     * Converts DICOM Attributes to a FHIR Document Bundle.
     *
     * @param attrs The DICOM Attributes from a MADO KOS
     * @return FHIR Document Bundle compliant with MADO IG
     * @throws IllegalArgumentException If the attributes are not from a valid MADO KOS
     */
    public Bundle convert(Attributes attrs) {
        // Validate this is a KOS
        String sopClassUID = attrs.getString(Tag.SOPClassUID);
        if (!KOS_SOP_CLASS_UID.equals(sopClassUID)) {
            throw new IllegalArgumentException("Not a Key Object Selection Document. SOP Class UID: " + sopClassUID);
        }

        // Extract header metadata
        MADOMetadata metadata = extractMetadata(attrs);

        // Generate UUIDs for cross-referencing
        ResourceUUIDs uuids = new ResourceUUIDs(metadata, useDeterministicUuids);

        // Create the FHIR Document Bundle
        Bundle bundle = createDocumentBundle(metadata);

        // Create resources
        Patient patient = createPatient(metadata);
        patient.setId(uuids.patientUuid);

        Device device = createDevice(metadata);
        device.setId(uuids.deviceUuid);

        Practitioner practitioner = createPractitioner(metadata);
        practitioner.setId(uuids.practitionerUuid);

        // Create Endpoints
        List<Endpoint> endpoints = createEndpoints(attrs, metadata, uuids, useDeterministicUuids);

        // Create ImagingStudy
        ImagingStudy imagingStudy = createImagingStudy(attrs, metadata, uuids);
        imagingStudy.setId(uuids.studyUuid);

        // Create ImagingSelection resources (backported to R4 as Basic)
        List<Basic> imagingSelections = createImagingSelections(attrs, metadata, uuids);

        // Create Composition (first entry in document bundle)
        Composition composition = createComposition(metadata, uuids, imagingSelections.size());
        composition.setId(uuids.compositionUuid);

        // Add entries in MADO-compliant order
        // 1. Composition (MUST be first for document bundle)
        bundle.addEntry()
            .setFullUrl("urn:uuid:" + uuids.compositionUuid)
            .setResource(composition);

        // 2. Patient
        bundle.addEntry()
            .setFullUrl("urn:uuid:" + uuids.patientUuid)
            .setResource(patient);

        // 3. Device (author device)
        bundle.addEntry()
            .setFullUrl("urn:uuid:" + uuids.deviceUuid)
            .setResource(device);

        // 4. Practitioner (if meaningful data exists)
        if (metadata.referringPhysicianName != null && !metadata.referringPhysicianName.isEmpty()
            && !metadata.referringPhysicianName.startsWith("-")) {
            bundle.addEntry()
                .setFullUrl("urn:uuid:" + uuids.practitionerUuid)
                .setResource(practitioner);
        }

        // 5. Endpoints
        for (Endpoint endpoint : endpoints) {
            String uuid = uuids.endpointUuids.get(endpoint.getAddress());
            bundle.addEntry()
                .setFullUrl("urn:uuid:" + uuid)
                .setResource(endpoint);
        }

        // 6. ImagingStudy
        bundle.addEntry()
            .setFullUrl("urn:uuid:" + uuids.studyUuid)
            .setResource(imagingStudy);

        // 7. ImagingSelection resources (as Basic in R4)
        for (Basic selection : imagingSelections) {
            // Generate deterministic UUID based on the selection's extensions
            String uuid;
            if (useDeterministicUuids) {
                // Extract key data from the selection for deterministic UUID generation
                String sopInstanceUID = "";
                for (Extension ext : selection.getExtension()) {
                    if (EXT_SELECTED_INSTANCE.equals(ext.getUrl())) {
                        for (Extension innerExt : ext.getExtension()) {
                            if ("uid".equals(innerExt.getUrl()) && innerExt.getValue() instanceof StringType) {
                                sopInstanceUID = ((StringType) innerExt.getValue()).getValue();
                                break;
                            }
                        }
                        if (!sopInstanceUID.isEmpty()) break;
                    }
                }
                // Use the study UID and first SOP Instance UID to create a deterministic UUID
                uuid = DeterministicUuidGenerator.generateImagingSelectionUuid(
                    metadata.studyInstanceUID,
                    "", // Series UID not easily available here, but study + SOP should be unique enough
                    sopInstanceUID
                );
            } else {
                uuid = UUID.randomUUID().toString();
            }

            selection.setId(uuid);
            bundle.addEntry()
                .setFullUrl("urn:uuid:" + uuid)
                .setResource(selection);

            // Add reference to Composition Key Image Selections section
            if (uuids.keyImageSelectionSection != null) {
                uuids.keyImageSelectionSection.addEntry(new Reference("urn:uuid:" + uuid));
            }
        }

        return bundle;
    }

    // ============================================================================
    // BUNDLE AND COMPOSITION
    // ============================================================================

    /**
     * Creates the Document Bundle with mandatory identifiers and timestamp.
     */
    private Bundle createDocumentBundle(MADOMetadata metadata) {
        Bundle bundle = new Bundle();

        // MADO requirement: Bundle type must be 'document'
        bundle.setType(Bundle.BundleType.DOCUMENT);

        // Add profile
        bundle.getMeta().addProfile(PROFILE_IMAGING_STUDY_MANIFEST);

        // MADO requirement: Business identifier with system and value
        bundle.setIdentifier(new Identifier()
            .setSystem("urn:dicom:uid")
            .setValue(IHE_UID_PREFIX + metadata.sopInstanceUID));

        // MADO requirement: Timestamp
        Date bundleTime = parseDicomDateTime(
            metadata.studyDate + (metadata.studyTime != null ? metadata.studyTime : ""),
            metadata.timezoneOffset);
        bundle.setTimestamp(bundleTime != null ? bundleTime : new Date());

        return bundle;
    }

    /**
     * Creates the Composition resource (document header).
     * MADO requirement: First entry in the document bundle.
     */
    private Composition createComposition(MADOMetadata metadata, ResourceUUIDs uuids, int selectionCount) {
        Composition composition = new Composition();

        // Identifier from KOS SOP Instance UID (R5: array)
        composition.addIdentifier(new Identifier()
            .setSystem("urn:dicom:uid")
            .setValue(IHE_UID_PREFIX + metadata.sopInstanceUID));

        // Status
        composition.setStatus(Enumerations.CompositionStatus.FINAL);

        // Type - MADO Imaging Manifest (LOINC code for Diagnostic Imaging Study)
        composition.setType(new CodeableConcept()
            .addCoding(new Coding()
                .setSystem("http://loinc.org")
                .setCode("18748-4")
                .setDisplay("Diagnostic imaging study")));

        // Subject - Patient (R5: array)
        composition.addSubject(new Reference("urn:uuid:" + uuids.patientUuid));

        // Date - with timezone
        Date compositionDate = parseDicomDateTime(
            metadata.studyDate + (metadata.studyTime != null ? metadata.studyTime : ""),
            metadata.timezoneOffset);
        if (compositionDate != null) {
            composition.setDate(compositionDate);
        } else {
            composition.setDate(new Date());
        }

        // Author - Device (always)
        composition.addAuthor(new Reference("urn:uuid:" + uuids.deviceUuid));

        // Author - Practitioner (if available and meaningful)
        if (metadata.referringPhysicianName != null && !metadata.referringPhysicianName.isEmpty()
            && !metadata.referringPhysicianName.startsWith("-")) {
            composition.addAuthor(new Reference("urn:uuid:" + uuids.practitionerUuid));
        }

        // Title - with null check for studyDescription
        String title = "MADO Imaging Manifest";
        if (metadata.studyDescription != null && !metadata.studyDescription.isEmpty()) {
            title += " - " + metadata.studyDescription;
        } else if (metadata.accessionNumber != null && !metadata.accessionNumber.isEmpty()) {
            title += " - " + metadata.accessionNumber;
        } else if (metadata.studyID != null && !metadata.studyID.isEmpty()) {
            title += " - Study " + metadata.studyID;
        }
        composition.setTitle(title);

        // Add narrative
        composition.setText(createNarrative(createCompositionNarrative(metadata)));

        // Section for ImagingStudy
        Composition.SectionComponent studySection = composition.addSection();
        studySection.setTitle("Imaging Study");
        studySection.addEntry(new Reference("urn:uuid:" + uuids.studyUuid));

        // Section for ImagingSelections (if any)
        if (selectionCount > 0) {
            Composition.SectionComponent selectionsSection = composition.addSection();
            selectionsSection.setTitle("Key Image Selections");
            // Entries will be added after ImagingSelection resources are created
            // Store reference to section for later population
            uuids.keyImageSelectionSection = selectionsSection;
        }

        return composition;
    }

    // ============================================================================
    // METADATA EXTRACTION
    // ============================================================================

    /**
     * Extracts metadata from DICOM header attributes.
     */
    private MADOMetadata extractMetadata(Attributes attrs) {
        MADOMetadata metadata = new MADOMetadata();

        // Patient Identity
        metadata.patientName = attrs.getString(Tag.PatientName);
        metadata.patientId = attrs.getString(Tag.PatientID);
        metadata.issuerOfPatientId = attrs.getString(Tag.IssuerOfPatientID);
        metadata.typeOfPatientId = attrs.getString(Tag.TypeOfPatientID);
        metadata.patientBirthDate = attrs.getString(Tag.PatientBirthDate);
        metadata.patientSex = attrs.getString(Tag.PatientSex);

        // Study Identity
        metadata.studyInstanceUID = attrs.getString(Tag.StudyInstanceUID);
        metadata.accessionNumber = attrs.getString(Tag.AccessionNumber);
        metadata.studyDate = attrs.getString(Tag.StudyDate);
        metadata.studyTime = attrs.getString(Tag.StudyTime);
        metadata.studyDescription = attrs.getString(Tag.StudyDescription);
        metadata.studyID = attrs.getString(Tag.StudyID);

        // Manifest Identity
        metadata.sopInstanceUID = attrs.getString(Tag.SOPInstanceUID);
        metadata.seriesInstanceUID = attrs.getString(Tag.SeriesInstanceUID);
        metadata.contentDate = attrs.getString(Tag.ContentDate);
        metadata.contentTime = attrs.getString(Tag.ContentTime);
        metadata.seriesDate = attrs.getString(Tag.SeriesDate);
        metadata.seriesTime = attrs.getString(Tag.SeriesTime);

        // Timezone - Critical for MADO compliance
        metadata.timezoneOffset = attrs.getString(Tag.TimezoneOffsetFromUTC);

        // Device/Equipment info
        metadata.manufacturer = attrs.getString(Tag.Manufacturer);
        metadata.manufacturerModelName = attrs.getString(Tag.ManufacturerModelName);
        metadata.softwareVersions = attrs.getString(Tag.SoftwareVersions);
        metadata.institutionName = attrs.getString(Tag.InstitutionName);

        // Referring physician
        metadata.referringPhysicianName = attrs.getString(Tag.ReferringPhysicianName);

        // Issuer of Accession Number
        Sequence issuerSeq = attrs.getSequence(Tag.IssuerOfAccessionNumberSequence);
        if (issuerSeq != null && !issuerSeq.isEmpty()) {
            Attributes issuerAttrs = issuerSeq.get(0);
            metadata.issuerOfAccessionNumber = issuerAttrs.getString(Tag.UniversalEntityID);
            metadata.issuerOfAccessionNumberType = issuerAttrs.getString(Tag.UniversalEntityIDType);
        }

        // Issuer of Patient ID Qualifiers
        Sequence patientIdQualSeq = attrs.getSequence(Tag.IssuerOfPatientIDQualifiersSequence);
        if (patientIdQualSeq != null && !patientIdQualSeq.isEmpty()) {
            Attributes qualAttrs = patientIdQualSeq.get(0);
            metadata.issuerOfPatientIdUniversalId = qualAttrs.getString(Tag.UniversalEntityID);
            metadata.issuerOfPatientIdUniversalIdType = qualAttrs.getString(Tag.UniversalEntityIDType);
        }

        // Extract Referenced Request Sequence for multiple accession numbers
        metadata.referencedRequests = new ArrayList<>();
        Sequence refReqSeq = attrs.getSequence(Tag.ReferencedRequestSequence);
        if (refReqSeq != null) {
            for (Attributes refReq : refReqSeq) {
                ReferencedRequest req = new ReferencedRequest();
                req.accessionNumber = refReq.getString(Tag.AccessionNumber);
                req.studyInstanceUID = refReq.getString(Tag.StudyInstanceUID);
                req.requestedProcedureId = refReq.getString(Tag.RequestedProcedureID);
                req.placerOrderNumber = refReq.getString(Tag.PlacerOrderNumberImagingServiceRequest);
                req.fillerOrderNumber = refReq.getString(Tag.FillerOrderNumberImagingServiceRequest);

                Sequence reqIssuerSeq = refReq.getSequence(Tag.IssuerOfAccessionNumberSequence);
                if (reqIssuerSeq != null && !reqIssuerSeq.isEmpty()) {
                    req.issuerOfAccessionNumber = reqIssuerSeq.get(0).getString(Tag.UniversalEntityID);
                }

                metadata.referencedRequests.add(req);
            }
        }

        // Extract Target Region from Content Sequence
        extractTargetRegion(attrs, metadata);

        return metadata;
    }

    /**
     * Extracts Target Region (body site) from the SR Content Tree.
     */
    private void extractTargetRegion(Attributes attrs, MADOMetadata metadata) {
        Sequence contentSeq = attrs.getSequence(Tag.ContentSequence);
        if (contentSeq == null) return;

        for (Attributes item : contentSeq) {
            Attributes conceptName = getConceptNameCode(item);
            if (conceptName != null && CODE_TARGET_REGION.equals(conceptName.getString(Tag.CodeValue))) {
                Sequence conceptCodeSeq = item.getSequence(Tag.ConceptCodeSequence);
                if (conceptCodeSeq != null && !conceptCodeSeq.isEmpty()) {
                    Attributes targetCode = conceptCodeSeq.get(0);
                    metadata.targetRegionCode = targetCode.getString(Tag.CodeValue);
                    metadata.targetRegionScheme = targetCode.getString(Tag.CodingSchemeDesignator);
                    metadata.targetRegionMeaning = targetCode.getString(Tag.CodeMeaning);
                }
                break;
            }
        }
    }

    // ============================================================================
    // RESOURCE CREATION
    // ============================================================================

    /**
     * Creates a FHIR Patient resource from metadata.
     */
    private Patient createPatient(MADOMetadata metadata) {
        Patient patient = new Patient();

        // Add profile
        patient.getMeta().addProfile(PROFILE_IMAGING_PATIENT);

        // Add identifier with issuer
        if (metadata.patientId != null) {
            Identifier identifier = patient.addIdentifier();
            identifier.setValue(metadata.patientId);
            identifier.setUse(Identifier.IdentifierUse.OFFICIAL);

            // Set system from issuer - use absolute URI format (urn:oid: prefix for OIDs)
            if (metadata.issuerOfPatientIdUniversalId != null) {
                identifier.setSystem("urn:oid:" + metadata.issuerOfPatientIdUniversalId);
            } else if (metadata.issuerOfPatientId != null) {
                // If it's a local namespace, prefix it to make it absolute
                identifier.setSystem("urn:oid:" + metadata.issuerOfPatientId);
            }

            // Preserve local namespace separately if different from OID (for round-trip)
            if (metadata.issuerOfPatientId != null && !metadata.issuerOfPatientId.isEmpty()) {
                Extension localNsExt = new Extension();
                localNsExt.setUrl(EXT_LOCAL_NAMESPACE);
                localNsExt.setValue(new StringType(metadata.issuerOfPatientId));
                identifier.addExtension(localNsExt);
            }

            // Preserve TypeOfPatientID (for round-trip)
            if (metadata.typeOfPatientId != null && !metadata.typeOfPatientId.isEmpty()) {
                Extension typeExt = new Extension();
                typeExt.setUrl(EXT_TYPE_OF_PATIENT_ID);
                typeExt.setValue(new StringType(metadata.typeOfPatientId));
                identifier.addExtension(typeExt);
            }
        }

        // Add patient name
        if (metadata.patientName != null && !metadata.patientName.isEmpty()) {
            HumanName name = patient.addName();
            String[] nameParts = metadata.patientName.split("\\^");

            // Check if the name is meaningful (not just dashes)
            boolean hasValidName = false;
            for (String part : nameParts) {
                if (!part.isEmpty() && !part.equals("-") && !part.trim().isEmpty()) {
                    hasValidName = true;
                    break;
                }
            }

            if (hasValidName) {
                if (nameParts.length > 0 && !nameParts[0].isEmpty() && !nameParts[0].equals("-")) {
                    name.setFamily(nameParts[0]);
                }
                if (nameParts.length > 1 && !nameParts[1].isEmpty() && !nameParts[1].equals("-")) {
                    name.addGiven(nameParts[1]);
                }
                if (nameParts.length > 2 && !nameParts[2].isEmpty() && !nameParts[2].equals("-")) {
                    name.addGiven(nameParts[2]); // Middle name
                }
                if (nameParts.length > 3 && !nameParts[3].isEmpty() && !nameParts[3].equals("-")) {
                    name.addPrefix(nameParts[3]); // Prefix
                }
                if (nameParts.length > 4 && !nameParts[4].isEmpty() && !nameParts[4].equals("-")) {
                    name.addSuffix(nameParts[4]); // Suffix
                }
            } else {
                // Fallback for anonymous patients
                name.setFamily("ANONYMOUS");
                name.setText("Anonymous Patient");
            }
        } else {
            // Fallback when no patient name at all
            HumanName name = patient.addName();
            name.setFamily("UNKNOWN");
            name.setText("Unknown Patient");
        }

        // Birth date
        if (metadata.patientBirthDate != null && metadata.patientBirthDate.length() >= 8) {
            try {
                SimpleDateFormat dicomFormat = new SimpleDateFormat("yyyyMMdd");
                Date birthDate = dicomFormat.parse(metadata.patientBirthDate);
                patient.setBirthDate(birthDate);
            } catch (ParseException e) {
                // Ignore invalid date format
            }
        }

        // Gender
        if (metadata.patientSex != null) {
            switch (metadata.patientSex.toUpperCase()) {
                case "M":
                    patient.setGender(Enumerations.AdministrativeGender.MALE);
                    break;
                case "F":
                    patient.setGender(Enumerations.AdministrativeGender.FEMALE);
                    break;
                case "O":
                    patient.setGender(Enumerations.AdministrativeGender.OTHER);
                    break;
                default:
                    patient.setGender(Enumerations.AdministrativeGender.UNKNOWN);
            }
        }

        // Add narrative
        patient.setText(createNarrative(createPatientNarrative(metadata)));

        return patient;
    }

    /**
     * Creates Device resource for the manifest author system (ImImagingDevice).
     */
    private Device createDevice(MADOMetadata metadata) {
        Device device = new Device();

        // Add profile
        device.getMeta().addProfile(PROFILE_IMAGING_DEVICE);

        // Status
        device.setStatus(Device.FHIRDeviceStatus.ACTIVE);

        // Category (R5: changed from 'type' to 'category')
        // Required by ImImagingDevice profile slice Device.category:imaging
        // Pattern: SNOMED CT 314789007 "Diagnostic imaging equipment"
        CodeableConcept deviceCategory = new CodeableConcept();
        deviceCategory.addCoding()
            .setSystem("http://snomed.info/sct")
            .setCode("314789007")
            .setDisplay("Diagnostic imaging equipment");
        device.addCategory(deviceCategory);

        // Manufacturer (always set for round-trip consistency, even if empty)
        if (metadata.manufacturer != null && !metadata.manufacturer.isEmpty()) {
            device.setManufacturer(metadata.manufacturer);
        } else {
            // Set empty string to distinguish from null and ensure round-trip consistency
            device.setManufacturer("");
        }

        // Store original manufacturer value in extension for deterministic UUID generation
        // This ensures the UUID remains consistent even if manufacturer is null/empty
        String originalManufacturer = metadata.manufacturer != null ? metadata.manufacturer : "";
        device.addExtension(new Extension(EXT_ORIGINAL_MANUFACTURER, new StringType(originalManufacturer)));

        // Device name (R5: use name instead of deviceName)
        if (metadata.manufacturerModelName != null) {
            Device.DeviceNameComponent name = device.addName();
            name.setValue(metadata.manufacturerModelName);
            name.setType(Enumerations.DeviceNameType.USERFRIENDLYNAME);
        }

        // Software version (MADO requirement: must be populated)
        Device.DeviceVersionComponent version = device.addVersion();
        if (metadata.softwareVersions != null && !metadata.softwareVersions.isEmpty()) {
            version.setValue(metadata.softwareVersions);
        } else {
            // Default version if DICOM attribute is missing
            version.setValue("UZLeuven-MADO-Converter-v1.0");
        }

        // Owner (Institution)
        if (metadata.institutionName != null) {
            device.setOwner(new Reference().setDisplay(metadata.institutionName));
        }

        // Add narrative
        device.setText(createNarrative(createDeviceNarrative(metadata)));

        return device;
    }

    /**
     * Creates Practitioner resource for the referring physician.
     */
    private Practitioner createPractitioner(MADOMetadata metadata) {
        Practitioner practitioner = new Practitioner();

        if (metadata.referringPhysicianName != null && !metadata.referringPhysicianName.isEmpty()) {
            HumanName name = practitioner.addName();
            String[] nameParts = metadata.referringPhysicianName.split("\\^");
            if (nameParts.length > 0 && !nameParts[0].isEmpty()) {
                name.setFamily(nameParts[0]);
            }
            if (nameParts.length > 1 && !nameParts[1].isEmpty()) {
                name.addGiven(nameParts[1]);
            }
        }

        return practitioner;
    }

    /**
     * Creates Endpoint resources for WADO-RS and IHE-IID access.
     */
    private List<Endpoint> createEndpoints(Attributes attrs, MADOMetadata metadata, ResourceUUIDs uuids, boolean useDeterministicUuids) {
        List<Endpoint> endpoints = new ArrayList<>();
        Set<String> processedUrls = new HashSet<>();

        // Get endpoints from Current Requested Procedure Evidence Sequence
        Sequence evidenceSeq = attrs.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence);
        if (evidenceSeq != null) {
            for (Attributes evidenceItem : evidenceSeq) {
                Sequence refSeriesSeq = evidenceItem.getSequence(Tag.ReferencedSeriesSequence);
                if (refSeriesSeq != null) {
                    for (Attributes seriesItem : refSeriesSeq) {
                        // WADO-RS Endpoint from Retrieve URL
                        String retrieveUrl = seriesItem.getString(Tag.RetrieveURL);
                        if (retrieveUrl != null && !processedUrls.contains(retrieveUrl)) {
                            String baseUrl = extractBaseWadoUrl(retrieveUrl);
                            if (!processedUrls.contains(baseUrl)) {
                                Endpoint wadoEndpoint = createWadoRsEndpoint(baseUrl);
                                String uuid = useDeterministicUuids
                                    ? DeterministicUuidGenerator.generateEndpointUuid(baseUrl)
                                    : UUID.randomUUID().toString();
                                wadoEndpoint.setId(uuid);
                                uuids.endpointUuids.put(baseUrl, uuid);
                                endpoints.add(wadoEndpoint);
                                processedUrls.add(baseUrl);
                            }
                        }
                    }
                }
            }
        }

        return endpoints;
    }

    /**
     * Extracts the base WADO-RS URL from a full retrieve URL.
     */
    private String extractBaseWadoUrl(String fullUrl) {
        int studiesIndex = fullUrl.indexOf("/studies/");
        if (studiesIndex > 0) {
            return fullUrl.substring(0, studiesIndex);
        }
        return fullUrl;
    }

    /**
     * Creates a WADO-RS Endpoint resource (R5 compatible).
     * Per ImWadoRsEndpoint profile: requires 17 mimeTypes in payload:wadors slice.
     */
    private Endpoint createWadoRsEndpoint(String address) {
        Endpoint endpoint = new Endpoint();

        // Add profile
        endpoint.getMeta().addProfile(PROFILE_WADO_RS_ENDPOINT);

        endpoint.setStatus(Endpoint.EndpointStatus.ACTIVE);

        // Connection type: dicom-wado-rs (R5: array of CodeableConcept)
        // Must match pattern for connectionType:wado slice
        CodeableConcept connectionType = new CodeableConcept();
        connectionType.addCoding(new Coding()
            .setSystem(ENDPOINT_CONNECTION_TYPE_SYSTEM)
            .setCode("dicom-wado-rs")
            .setDisplay("DICOM WADO-RS"));
        endpoint.addConnectionType(connectionType);

        // Payload (R5: payload is now an array with type and mimeType inside)
        // Pattern for slice payload:wadors - type must match connectionType pattern
        Endpoint.EndpointPayloadComponent payload = new Endpoint.EndpointPayloadComponent();

        // Payload type - use dicom-wado-rs coding to match slice
        CodeableConcept payloadType = new CodeableConcept();
        payloadType.addCoding(new Coding()
            .setSystem(ENDPOINT_CONNECTION_TYPE_SYSTEM)
            .setCode("dicom-wado-rs")
            .setDisplay("DICOM WADO-RS"));
        payload.addType(payloadType);

        // ImWadoRsEndpoint profile requires 17 mimeTypes in payload:wadors slice
        // All these are required (Control 1..1*) per the profile specification
        payload.addMimeType("application/dicom");           // dicom
        payload.addMimeType("application/octet-stream");    // dicom-octet
        payload.addMimeType("application/dicom+xml");       // dicom-xml
        payload.addMimeType("application/json");            // dicom-json
        payload.addMimeType("image/jpg");                   // image-jpg (note: not image/jpeg)
        payload.addMimeType("image/gif");                   // image-gif
        payload.addMimeType("image/jp2");                   // image-jp2
        payload.addMimeType("image/jph");                   // image-jph
        payload.addMimeType("image/jxl");                   // image-jxl
        payload.addMimeType("video/mpeg");                  // video-mpeg
        payload.addMimeType("video/mp4");                   // video-mp4
        payload.addMimeType("video/H265");                  // video-H265
        payload.addMimeType("text/html");                   // text-html
        payload.addMimeType("text/plain");                  // text-plain
        payload.addMimeType("text/xml");                    // text-xml
        payload.addMimeType("text/rtf");                    // text-rtf
        payload.addMimeType("application/pdf");             // application-pdf

        endpoint.addPayload(payload);
        endpoint.setAddress(address);

        // Add narrative
        endpoint.setText(createNarrative(createEndpointNarrative(address, "DICOM WADO-RS")));

        return endpoint;
    }


    /**
     * Creates IHE-IID Endpoint for viewer launch (MADO requirement, R5 compatible).
     * Note: This endpoint is included in the Bundle as an entry, but we do NOT
     * automatically reference it from ImagingStudy.endpoint because the IG's
     * endpoint slicing expects specific endpoint profiles and some validators
     * cannot reliably evaluate those slices when the IG canonical URL is unreachable.
     */
    private Endpoint createIheIidEndpoint(String baseUrl, String studyInstanceUID) {
        Endpoint endpoint = new Endpoint();

        // Add profile
        endpoint.getMeta().addProfile(PROFILE_IID_ENDPOINT);

        endpoint.setStatus(Endpoint.EndpointStatus.ACTIVE);

        // Connection type: ihe-iid (from MADO IG) (R5: array of CodeableConcept)
        CodeableConcept connectionType = new CodeableConcept();
        connectionType.addCoding(new Coding()
            .setSystem(IHE_ENDPOINT_CONNECTION_TYPE_SYSTEM)
            .setCode("ihe-iid")
            .setDisplay("IHE IID endpoint"));
        endpoint.addConnectionType(connectionType);

        // Payload
        Endpoint.EndpointPayloadComponent payload = new Endpoint.EndpointPayloadComponent();
        CodeableConcept payloadType = new CodeableConcept();
        payloadType.setText("IHE IID");
        payload.addType(payloadType);
        payload.addMimeType("text/html");
        endpoint.addPayload(payload);

        // IID URL format
        String iidUrl = baseUrl + "/viewer?studyUID=" + studyInstanceUID;
        endpoint.setAddress(iidUrl);

        return endpoint;
    }

    /**
     * Creates an endpoint based on Retrieve Location UID (R5 compatible).
     * Uses ImWadoRsEndpoint profile with all 17 required mimeTypes.
     */
    private Endpoint createLocationUidEndpoint(String locationUID) {
        Endpoint endpoint = new Endpoint();

        // Add profile - use WADO-RS profile for location UID endpoints
        endpoint.getMeta().addProfile(PROFILE_WADO_RS_ENDPOINT);

        endpoint.setStatus(Endpoint.EndpointStatus.ACTIVE);

        // MADO requirement: identifier must be OID for lookup
        endpoint.addIdentifier()
            .setSystem("urn:dicom:uid")
            .setValue(IHE_UID_PREFIX + locationUID);

        // Connection type (R5: array of CodeableConcept)
        // Must match pattern for connectionType:wado slice
        CodeableConcept connectionType = new CodeableConcept();
        connectionType.addCoding(new Coding()
            .setSystem(ENDPOINT_CONNECTION_TYPE_SYSTEM)
            .setCode("dicom-wado-rs")
            .setDisplay("DICOM WADO-RS"));
        endpoint.addConnectionType(connectionType);

        // Payload (R5: payload is now an array with type and mimeType inside)
        // Use WADO-RS pattern for location UID endpoints
        Endpoint.EndpointPayloadComponent payload = new Endpoint.EndpointPayloadComponent();

        // Payload type - use dicom-wado-rs coding
        CodeableConcept payloadType = new CodeableConcept();
        payloadType.addCoding(new Coding()
            .setSystem(ENDPOINT_CONNECTION_TYPE_SYSTEM)
            .setCode("dicom-wado-rs")
            .setDisplay("DICOM WADO-RS"));
        payload.addType(payloadType);

        // ImWadoRsEndpoint profile requires 17 mimeTypes in payload:wadors slice
        payload.addMimeType("application/dicom");
        payload.addMimeType("application/octet-stream");
        payload.addMimeType("application/dicom+xml");
        payload.addMimeType("application/json");
        payload.addMimeType("image/jpg");
        payload.addMimeType("image/gif");
        payload.addMimeType("image/jp2");
        payload.addMimeType("image/jph");
        payload.addMimeType("image/jxl");
        payload.addMimeType("video/mpeg");
        payload.addMimeType("video/mp4");
        payload.addMimeType("video/H265");
        payload.addMimeType("text/html");
        payload.addMimeType("text/plain");
        payload.addMimeType("text/xml");
        payload.addMimeType("text/rtf");
        payload.addMimeType("application/pdf");

        endpoint.addPayload(payload);
        endpoint.setAddress(IHE_UID_PREFIX + locationUID);

        return endpoint;
    }

    /**
     * Creates the ImagingStudy resource with series and instances.
     */
    private ImagingStudy createImagingStudy(Attributes attrs, MADOMetadata metadata, ResourceUUIDs uuids) {
        System.out.println("DEBUG MADOToFHIRConverter.createImagingStudy: Starting conversion");

        ImagingStudy study = new ImagingStudy();

        // Add profile
        study.getMeta().addProfile(PROFILE_IMAGING_STUDY);

        study.setStatus(ImagingStudy.ImagingStudyStatus.AVAILABLE);

        // MADO requirement (im-imagingstudy-01): Study Instance UID with IHE prefix
        study.addIdentifier()
            .setSystem("urn:dicom:uid")
            .setValue(IHE_UID_PREFIX + metadata.studyInstanceUID);

        // Patient reference
        study.setSubject(new Reference("urn:uuid:" + uuids.patientUuid));

        // Started datetime - with timezone applied
        Date started = parseDicomDateTime(
            metadata.studyDate + (metadata.studyTime != null ? metadata.studyTime : ""),
            metadata.timezoneOffset);
        if (started != null) {
            study.setStarted(started);
        }

        // Extensions for round-trip preservation
        // StudyID
        if (metadata.studyID != null && !metadata.studyID.isEmpty()) {
            study.addExtension(new Extension(EXT_STUDY_ID, new StringType(metadata.studyID)));
        }
        // ContentDate
        if (metadata.contentDate != null && !metadata.contentDate.isEmpty()) {
            study.addExtension(new Extension(EXT_CONTENT_DATE, new StringType(metadata.contentDate)));
        }
        // ContentTime
        if (metadata.contentTime != null && !metadata.contentTime.isEmpty()) {
            study.addExtension(new Extension(EXT_CONTENT_TIME, new StringType(metadata.contentTime)));
        }
        // SeriesDate
        if (metadata.seriesDate != null && !metadata.seriesDate.isEmpty()) {
            study.addExtension(new Extension(EXT_SERIES_DATE, new StringType(metadata.seriesDate)));
        }
        // SeriesTime
        if (metadata.seriesTime != null && !metadata.seriesTime.isEmpty()) {
            study.addExtension(new Extension(EXT_SERIES_TIME, new StringType(metadata.seriesTime)));
        }
        // SOP Instance UID (manifest UID)
        if (metadata.sopInstanceUID != null && !metadata.sopInstanceUID.isEmpty()) {
            study.addExtension(new Extension(EXT_SOP_INSTANCE_UID, new StringType(metadata.sopInstanceUID)));
        }
        // Series Instance UID (manifest series UID)
        if (metadata.seriesInstanceUID != null && !metadata.seriesInstanceUID.isEmpty()) {
            study.addExtension(new Extension(EXT_SERIES_INSTANCE_UID, new StringType(metadata.seriesInstanceUID)));
        }
        // Referring Physician (preserved even if just dashes)
        if (metadata.referringPhysicianName != null) {
            study.addExtension(new Extension(EXT_REFERRING_PHYSICIAN, new StringType(metadata.referringPhysicianName)));
        }

        // MADO requirement: Map ALL entries from Referenced Request Sequence to basedOn
        if (!metadata.referencedRequests.isEmpty()) {
            for (ReferencedRequest req : metadata.referencedRequests) {
                if (req.accessionNumber != null) {
                    Reference basedOn = new Reference();
                    Identifier accessionId = new Identifier();

                    // Set system from issuer if available - use absolute URI (urn:oid:)
                    if (req.issuerOfAccessionNumber != null) {
                        accessionId.setSystem("urn:oid:" + req.issuerOfAccessionNumber);
                    } else {
                        accessionId.setSystem("urn:dicom:uid");
                    }
                    accessionId.setValue(req.accessionNumber);

                    // Add type for Accession Number
                    CodeableConcept type = new CodeableConcept();
                    type.addCoding()
                        .setSystem("http://terminology.hl7.org/CodeSystem/v2-0203")
                        .setCode("ACSN")
                        .setDisplay("Accession ID");
                    accessionId.setType(type);

                    basedOn.setIdentifier(accessionId);
                    study.addBasedOn(basedOn);
                }
            }
        } else if (metadata.accessionNumber != null) {
            // Fallback to header accession number
            Reference basedOn = new Reference();
            Identifier accessionId = new Identifier();
            accessionId.setSystem("urn:dicom:uid");
            accessionId.setValue(metadata.accessionNumber);

            CodeableConcept type = new CodeableConcept();
            type.addCoding()
                .setSystem("http://terminology.hl7.org/CodeSystem/v2-0203")
                .setCode("ACSN");
            accessionId.setType(type);

            basedOn.setIdentifier(accessionId);
            study.addBasedOn(basedOn);
        }

        // Referrer - Practitioner (if available and meaningful)
        if (metadata.referringPhysicianName != null && !metadata.referringPhysicianName.isEmpty()
            && !metadata.referringPhysicianName.startsWith("-")) {
            study.setReferrer(new Reference("urn:uuid:" + uuids.practitionerUuid));
        }

        // Description
        if (metadata.studyDescription != null) {
            study.setDescription(metadata.studyDescription);
        }

        // Add endpoint references
        // Only add base http(s) WADO-RS endpoints to avoid ambiguous slice matching
        // (e.g., viewer/iid endpoints and urn:oid endpoints can confuse validators).
        for (Map.Entry<String, String> e : uuids.endpointUuids.entrySet()) {
            String key = e.getKey();
            String uuid = e.getValue();
            if (key == null || uuid == null) continue;

            boolean isHttpBase = (key.startsWith("http://") || key.startsWith("https://"));
            boolean looksLikeViewer = key.contains("/viewer");

            if (isHttpBase && !looksLikeViewer) {
                // Add type information to help validators match the correct slice
                // The endpoint resource itself has meta.profile set to ImWadoRsEndpoint
                Reference endpointRef = new Reference("urn:uuid:" + uuid);
                endpointRef.setType("Endpoint");
                study.addEndpoint(endpointRef);
            }
        }

        // Process series from Current Requested Procedure Evidence Sequence
        Sequence evidenceSeq = attrs.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence);
        if (evidenceSeq != null) {
            Map<String, ImagingStudy.ImagingStudySeriesComponent> seriesMap = new LinkedHashMap<>();

            for (Attributes evidenceItem : evidenceSeq) {
                Sequence refSeriesSeq = evidenceItem.getSequence(Tag.ReferencedSeriesSequence);
                if (refSeriesSeq != null) {
                    for (Attributes seriesItem : refSeriesSeq) {
                        String seriesUID = seriesItem.getString(Tag.SeriesInstanceUID);
                        String modality = seriesItem.getString(Tag.Modality);

                        ImagingStudy.ImagingStudySeriesComponent series = seriesMap.get(seriesUID);
                        if (series == null) {
                            series = new ImagingStudy.ImagingStudySeriesComponent();
                            series.setUid(seriesUID);

                            if (modality != null) {
                                // R5: modality is now CodeableConcept instead of Coding
                                CodeableConcept modalityConcept = new CodeableConcept();
                                modalityConcept.addCoding(new Coding()
                                    .setSystem("http://dicom.nema.org/resources/ontology/DCM")
                                    .setCode(modality));
                                series.setModality(modalityConcept);
                            }

                            // MADO requirement: Map Target Region to bodySite
                            // R5: bodySite is now CodeableReference instead of Coding
                            if (metadata.targetRegionCode != null) {
                                Coding bodySiteCoding = mapBodySite(
                                    metadata.targetRegionCode,
                                    metadata.targetRegionScheme,
                                    metadata.targetRegionMeaning);
                                if (bodySiteCoding != null) {
                                    CodeableReference bodySite = new CodeableReference();
                                    CodeableConcept bodySiteConcept = new CodeableConcept();
                                    bodySiteConcept.addCoding(bodySiteCoding);
                                    bodySite.setConcept(bodySiteConcept);
                                    series.setBodySite(bodySite);
                                }
                            }

                            seriesMap.put(seriesUID, series);
                        }

                        // Process instances
                        Sequence refSopSeq = seriesItem.getSequence(Tag.ReferencedSOPSequence);
                        if (refSopSeq != null) {
                            for (Attributes sopItem : refSopSeq) {
                                String sopClassUID = sopItem.getString(Tag.ReferencedSOPClassUID);
                                String sopInstanceUID = sopItem.getString(Tag.ReferencedSOPInstanceUID);
                                int numberOfFrames = sopItem.getInt(Tag.NumberOfFrames, 0);
                                int rows = sopItem.getInt(Tag.Rows, 0);
                                int columns = sopItem.getInt(Tag.Columns, 0);
                                int instanceNumber = sopItem.getInt(Tag.InstanceNumber, 0);

                                System.out.println("DEBUG MADOToFHIRConverter: Reading from Evidence - SOP=" + sopInstanceUID +
                                                   ", InstanceNumber from Evidence=" + instanceNumber);

                                ImagingStudy.ImagingStudySeriesInstanceComponent instance =
                                    new ImagingStudy.ImagingStudySeriesInstanceComponent();
                                instance.setUid(sopInstanceUID);

                                if (sopClassUID != null) {
                                    instance.setSopClass(new Coding()
                                        .setSystem("urn:ietf:rfc:3986")
                                        .setCode(IHE_UID_PREFIX + sopClassUID));
                                }

                                // Instance number (DICOM Instance Number tag)
                                if (instanceNumber > 0) {
                                    instance.setNumber(instanceNumber);
                                    System.out.println("DEBUG MADOToFHIRConverter: Set InstanceNumber=" + instanceNumber +
                                                       " from Evidence on FHIR instance " + sopInstanceUID);
                                } else {
                                    System.out.println("DEBUG MADOToFHIRConverter: NO InstanceNumber in Evidence for " + sopInstanceUID +
                                                       ", will check ContentSequence later");
                                }

                                // Instance description extension (MADO IG URL)
                                // Include dimensions and optionally number of frames in description
                                StringBuilder descBuilder = new StringBuilder();
                                if (rows > 0 && columns > 0) {
                                    descBuilder.append(rows).append("x").append(columns);
                                    if (numberOfFrames > 0) {
                                        descBuilder.append(" (").append(numberOfFrames).append(" frames)");
                                    }
                                } else if (numberOfFrames > 0) {
                                    descBuilder.append(numberOfFrames).append(" frames");
                                }

                                if (descBuilder.length() > 0) {
                                    Extension descExt = new Extension();
                                    descExt.setUrl(EXT_INSTANCE_DESCRIPTION);
                                    descExt.setValue(new StringType(descBuilder.toString()));
                                    instance.addExtension(descExt);
                                }

                                series.addInstance(instance);
                            }
                        }
                    }
                }
            }

            // Enrich series with SR content tree metadata
            enrichSeriesFromContentTree(attrs, seriesMap);

            // Count instances for narrative
            int totalInstances = 0;
            for (ImagingStudy.ImagingStudySeriesComponent series : seriesMap.values()) {
                totalInstances += series.getInstance().size();
            }

            // Add narrative
            study.setText(createNarrative(createImagingStudyNarrative(metadata, seriesMap.size(), totalInstances)));

            // Add all series to study
            for (ImagingStudy.ImagingStudySeriesComponent series : seriesMap.values()) {
                study.addSeries(series);
            }
        }

        return study;
    }

    /**
     * Maps body site codes to SNOMED CT.
     * Only accepts MADO-compliant SNOMED CT codes with SCT scheme.
     */
    private Coding mapBodySite(String code, String scheme, String meaning) {
        // MADO only accepts SNOMED CT codes with SCT scheme
        if ("SCT".equals(scheme)) {
            // Validate it's a MADO-allowed code
            String display = CodeConstants.BODY_SITE_DISPLAY_MAP.get(code);
            if (display != null) {
                return new Coding()
                    .setSystem(CodeConstants.SNOMED_SYSTEM)
                    .setCode(code)
                    .setDisplay(display);
            }
            // Use provided display if code is not in our map (forward compatibility)
            return new Coding()
                .setSystem(CodeConstants.SNOMED_SYSTEM)
                .setCode(code)
                .setDisplay(meaning != null ? meaning : code);
        }

        // Reject legacy SRT codes - MADO does not support them
        System.err.println("WARNING: Non-compliant body site code scheme '" + scheme +
                          "' (code: " + code + "). MADO only accepts SNOMED CT with SCT scheme.");
        return null;
    }

    /**
     * Enriches series information from the SR Content Tree (TID 1600/1602).
     */
    private void enrichSeriesFromContentTree(Attributes attrs,
                                             Map<String, ImagingStudy.ImagingStudySeriesComponent> seriesMap) {
        System.out.println("DEBUG MADOToFHIRConverter.enrichSeriesFromContentTree: Starting to enrich from ContentSequence");
        Sequence contentSeq = attrs.getSequence(Tag.ContentSequence);
        if (contentSeq == null) {
            System.out.println("DEBUG MADOToFHIRConverter.enrichSeriesFromContentTree: NO ContentSequence found!");
            return;
        }
        System.out.println("DEBUG MADOToFHIRConverter.enrichSeriesFromContentTree: ContentSequence has " + contentSeq.size() + " items");

        for (Attributes item : contentSeq) {
            String valueType = item.getString(Tag.ValueType);

            if ("CONTAINER".equals(valueType)) {
                Attributes conceptName = getConceptNameCode(item);
                if (conceptName != null && CodeConstants.CODE_IMAGE_LIBRARY.equals(conceptName.getString(Tag.CodeValue))) {
                    processImageLibrary(item, seriesMap);
                }
            }
        }
    }

    /**
     * Processes the Image Library container in the SR content tree.
     */
    private void processImageLibrary(Attributes imageLibrary,
                                      Map<String, ImagingStudy.ImagingStudySeriesComponent> seriesMap) {
        System.out.println("DEBUG MADOToFHIRConverter.processImageLibrary: Processing Image Library");
        Sequence contentSeq = imageLibrary.getSequence(Tag.ContentSequence);
        if (contentSeq == null) {
            System.out.println("DEBUG MADOToFHIRConverter.processImageLibrary: NO ContentSequence in Image Library!");
            return;
        }
        System.out.println("DEBUG MADOToFHIRConverter.processImageLibrary: Image Library ContentSequence has " + contentSeq.size() + " items");

        for (Attributes item : contentSeq) {
            String valueType = item.getString(Tag.ValueType);

            if ("CONTAINER".equals(valueType)) {
                Attributes conceptName = getConceptNameCode(item);
                if (conceptName != null && CODE_IMAGE_LIBRARY_GROUP.equals(conceptName.getString(Tag.CodeValue))) {
                    processImageLibraryGroup(item, seriesMap);
                }
            }
        }
    }

    /**
     * Processes an Image Library Group in the SR content tree.
     */
    private void processImageLibraryGroup(Attributes group,
                                           Map<String, ImagingStudy.ImagingStudySeriesComponent> seriesMap) {
        System.out.println("DEBUG MADOToFHIRConverter.processImageLibraryGroup: Processing Image Library Group");
        Sequence contentSeq = group.getSequence(Tag.ContentSequence);
        if (contentSeq == null) {
            System.out.println("DEBUG MADOToFHIRConverter.processImageLibraryGroup: NO ContentSequence in group!");
            return;
        }
        System.out.println("DEBUG MADOToFHIRConverter.processImageLibraryGroup: Group ContentSequence has " + contentSeq.size() + " items");

        String seriesUID = null;
        String seriesDescription = null;
        Integer seriesNumber = null;
        String seriesDate = null;
        String seriesTime = null;
        Map<String, Integer> instanceNumbers = new HashMap<>(); // SOP Instance UID -> Instance Number
        Map<String, Integer> instanceFrameCounts = new HashMap<>(); // SOP Instance UID -> Number of Frames

        for (Attributes item : contentSeq) {
            String valueType = item.getString(Tag.ValueType);
            Attributes conceptName = getConceptNameCode(item);
            if (conceptName == null) continue;

            String codeValue = conceptName.getString(Tag.CodeValue);

            System.out.println("DEBUG MADOToFHIRConverter.processImageLibraryGroup: Item valueType=" + valueType + ", codeValue=" + codeValue);

            if ("UIDREF".equals(valueType) && CODE_SERIES_INSTANCE_UID.equals(codeValue)) {
                seriesUID = item.getString(Tag.UID);
            } else if ("TEXT".equals(valueType) && CODE_SERIES_DESCRIPTION.equals(codeValue)) {
                seriesDescription = item.getString(Tag.TextValue);
            } else if ("TEXT".equals(valueType) && CODE_SERIES_NUMBER.equals(codeValue)) {
                try {
                    seriesNumber = Integer.parseInt(item.getString(Tag.TextValue).trim());
                } catch (NumberFormatException e) {
                    // Ignore
                }
            } else if ("TEXT".equals(valueType) && CODE_SERIES_DATE.equals(codeValue)) {
                seriesDate = item.getString(Tag.TextValue);
            } else if ("TEXT".equals(valueType) && CODE_SERIES_TIME.equals(codeValue)) {
                seriesTime = item.getString(Tag.TextValue);
            } else if ("IMAGE".equals(valueType)) {
                // Process IMAGE items to extract instance numbers and frame counts
                System.out.println("DEBUG MADOToFHIRConverter.processImageLibraryGroup: Found IMAGE item, calling extractInstanceMetadataFromImage");
                extractInstanceMetadataFromImage(item, instanceNumbers, instanceFrameCounts);
            }
        }

        if (seriesUID != null) {
            ImagingStudy.ImagingStudySeriesComponent series = seriesMap.get(seriesUID);
            if (series != null) {
                if (seriesDescription != null) {
                    series.setDescription(seriesDescription);
                }
                if (seriesNumber != null) {
                    series.setNumber(seriesNumber);
                }
                // Store Series Date/Time as extensions for round-trip
                if (seriesDate != null && !seriesDate.isEmpty()) {
                    series.addExtension(new Extension(EXT_IMAGING_SERIES_DATE, new StringType(seriesDate)));
                }
                if (seriesTime != null && !seriesTime.isEmpty()) {
                    series.addExtension(new Extension(EXT_IMAGING_SERIES_TIME, new StringType(seriesTime)));
                }

                // Populate instance numbers and frame counts from SR content tree
                if (series.hasInstance()) {
                    if (!instanceNumbers.isEmpty() || !instanceFrameCounts.isEmpty()) {
                        // We have instance-level metadata from IMAGE entries
                        for (ImagingStudy.ImagingStudySeriesInstanceComponent instance : series.getInstance()) {
                            String instanceUID = instance.getUid();

                            // Set instance number from content tree if available
                            if (instanceNumbers.containsKey(instanceUID)) {
                                instance.setNumber(instanceNumbers.get(instanceUID));
                                System.out.println("DEBUG MADOToFHIRConverter: Set InstanceNumber=" + instanceNumbers.get(instanceUID) +
                                                   " on FHIR instance " + instanceUID);
                            } else {
                                System.out.println("DEBUG MADOToFHIRConverter: NO InstanceNumber in map for instance " + instanceUID);
                            }

                            // Update instance description with frame count from content tree if available
                            if (instanceFrameCounts.containsKey(instanceUID)) {
                                int frameCount = instanceFrameCounts.get(instanceUID);
                                updateInstanceDescriptionWithFrameCount(instance, frameCount);
                            }
                        }
                    } else {
                        // No IMAGE entries found in ContentSequence - assign sequential instance numbers
                        System.out.println("DEBUG MADOToFHIRConverter: No IMAGE entries in ContentSequence, assigning sequential instance numbers");
                        int sequentialNumber = 1;
                        for (ImagingStudy.ImagingStudySeriesInstanceComponent instance : series.getInstance()) {
                            instance.setNumber(sequentialNumber);
                            System.out.println("DEBUG MADOToFHIRConverter: Assigned sequential InstanceNumber=" + sequentialNumber +
                                               " to instance " + instance.getUid());
                            sequentialNumber++;
                        }
                    }
                }
            }
        }
    }

    /**
     * Extracts instance metadata (instance number and number of frames) from an IMAGE item in the SR content tree.
     */
    private void extractInstanceMetadataFromImage(Attributes imageItem,
                                                   Map<String, Integer> instanceNumbers,
                                                   Map<String, Integer> instanceFrameCounts) {
        System.out.println("DEBUG MADOToFHIRConverter.extractInstanceMetadataFromImage: Extracting metadata from IMAGE item");
        // Get the SOP Instance UID from ReferencedSOPSequence
        Sequence refSopSeq = imageItem.getSequence(Tag.ReferencedSOPSequence);
        if (refSopSeq != null && !refSopSeq.isEmpty()) {
            Attributes sopItem = refSopSeq.get(0);
            String sopInstanceUID = sopItem.getString(Tag.ReferencedSOPInstanceUID);

            System.out.println("DEBUG MADOToFHIRConverter.extractInstanceMetadataFromImage: SOP Instance UID = " + sopInstanceUID);

            if (sopInstanceUID != null) {
                // Look for instance metadata in the image item's content sequence
                Sequence contentSeq = imageItem.getSequence(Tag.ContentSequence);
                if (contentSeq != null) {
                    System.out.println("DEBUG MADOToFHIRConverter.extractInstanceMetadataFromImage: IMAGE ContentSequence has " + contentSeq.size() + " items");
                    for (Attributes item : contentSeq) {
                        String valueType = item.getString(Tag.ValueType);
                        Attributes conceptName = getConceptNameCode(item);
                        if (conceptName != null) {
                            String codeValue = conceptName.getString(Tag.CodeValue);

                            // Extract Instance Number (TEXT value type)
                            if (DicomConstants.VALUE_TYPE_TEXT.equals(valueType) && CODE_INSTANCE_NUMBER.equals(codeValue)) {
                                try {
                                    Integer instanceNumber = Integer.parseInt(item.getString(Tag.TextValue).trim());
                                    instanceNumbers.put(sopInstanceUID, instanceNumber);
                                    System.out.println("DEBUG MADOToFHIRConverter: Extracted InstanceNumber=" + instanceNumber +
                                                       " from ContentSequence for SOP " + sopInstanceUID);
                                } catch (NumberFormatException e) {
                                    System.out.println("DEBUG MADOToFHIRConverter: Failed to parse InstanceNumber from ContentSequence for SOP " + sopInstanceUID);
                                }
                            }

                            // Extract Number of Frames (NUM value type, code 121140)
                            if (DicomConstants.VALUE_TYPE_NUM.equals(valueType) && "121140".equals(codeValue)) {
                                try {
                                    // Number of Frames is stored in MeasuredValueSequence
                                    Sequence measuredValueSeq = item.getSequence(Tag.MeasuredValueSequence);
                                    if (measuredValueSeq != null && !measuredValueSeq.isEmpty()) {
                                        Attributes measuredValue = measuredValueSeq.get(0);
                                        String numericValue = measuredValue.getString(Tag.NumericValue);
                                        if (numericValue != null) {
                                            int frameCount = (int) Double.parseDouble(numericValue.trim());
                                            instanceFrameCounts.put(sopInstanceUID, frameCount);
                                        }
                                    }
                                } catch (NumberFormatException e) {
                                    // Ignore invalid frame count
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Updates the instance description extension with frame count information.
     * If the description already exists, merges the frame count. Otherwise, creates new description.
     */
    private void updateInstanceDescriptionWithFrameCount(ImagingStudy.ImagingStudySeriesInstanceComponent instance,
                                                          int frameCount) {
        // Find existing description extension
        Extension descExt = null;
        for (Extension ext : instance.getExtension()) {
            if (EXT_INSTANCE_DESCRIPTION.equals(ext.getUrl())) {
                descExt = ext;
                break;
            }
        }

        if (descExt != null) {
            // Update existing description
            String existingDesc = ((StringType) descExt.getValue()).getValue();
            if (existingDesc != null && !existingDesc.contains("frames")) {
                // Add frame count to existing dimensions
                descExt.setValue(new StringType(existingDesc + " (" + frameCount + " frames)"));
            } else if (existingDesc == null) {
                // Replace null description
                descExt.setValue(new StringType(frameCount + " frames"));
            }
            // If already has frames, don't update (prefer Evidence Sequence data)
        } else {
            // Create new description extension with frame count
            Extension newDescExt = new Extension();
            newDescExt.setUrl(EXT_INSTANCE_DESCRIPTION);
            newDescExt.setValue(new StringType(frameCount + " frames"));
            instance.addExtension(newDescExt);
        }
    }

    /**
     * Gets the ConceptNameCodeSequence item from an SR content item.
     */
    private Attributes getConceptNameCode(Attributes item) {
        Sequence seq = item.getSequence(Tag.ConceptNameCodeSequence);
        if (seq != null && !seq.isEmpty()) {
            return seq.get(0);
        }
        return null;
    }

    /**
     * Creates ImagingSelection resources (backported to R4 as Basic).
     * MADO relies on ImagingSelection for key image flagging.
     * Since ImagingSelection is R5-only, we use Basic resource with the ImImagingSelection profile.
     * This is the correct semantic backport (not DocumentReference which represents documents).
     */
    private List<Basic> createImagingSelections(Attributes attrs, MADOMetadata metadata,
                                                 ResourceUUIDs uuids) {
        List<Basic> selections = new ArrayList<>();

        Sequence contentSeq = attrs.getSequence(Tag.ContentSequence);
        if (contentSeq == null) return selections;

        Map<String, List<ImageReference>> keyImagesByCode = new LinkedHashMap<>();
        processContentForKeyImages(contentSeq, keyImagesByCode);

        for (Map.Entry<String, List<ImageReference>> entry : keyImagesByCode.entrySet()) {
            String codeKey = entry.getKey();
            List<ImageReference> images = entry.getValue();

            if (!images.isEmpty()) {
                Basic basic = new Basic();

                // CRITICAL: Add ImImagingSelection profile to indicate this is an R5 backport
                basic.getMeta().addProfile(PROFILE_IMAGING_SELECTION);

                // Subject reference to patient
                basic.setSubject(new Reference("urn:uuid:" + uuids.patientUuid));

                // Code - ImagingSelection resource type indicator
                CodeableConcept basicCode = new CodeableConcept();
                basicCode.addCoding()
                    .setSystem("http://terminology.hl7.org/CodeSystem/basic-resource-type")
                    .setCode("imaging-selection")
                    .setDisplay("Imaging Selection");
                basicCode.setText("MADO Key Image Selection (R5 ImagingSelection backport)");
                basic.setCode(basicCode);

                // Created date with timezone (maps to ImagingSelection.issued)
                Date selectionDate = parseDicomDateTime(
                    metadata.studyDate + (metadata.studyTime != null ? metadata.studyTime : ""),
                    metadata.timezoneOffset);
                if (selectionDate != null) {
                    basic.setCreated(selectionDate);
                }

                // Extension for selection code (ImagingSelection.code - the KOS Title Code)
                String[] codeParts = codeKey.split("\\|");
                Extension selectionCodeExt = new Extension();
                selectionCodeExt.setUrl(EXT_SELECTION_CODE);
                CodeableConcept selectionCode = new CodeableConcept();
                if (codeParts.length >= 2) {
                    selectionCode.addCoding()
                        .setSystem(codeParts[0].equals(DicomConstants.SCHEME_DCM) ? "http://dicom.nema.org/resources/ontology/DCM" : codeParts[0])
                        .setCode(codeParts[1])
                        .setDisplay(codeParts.length > 2 ? codeParts[2] : codeParts[1]);
                } else {
                    selectionCode.setText(codeKey);
                }
                selectionCodeExt.setValue(selectionCode);
                basic.addExtension(selectionCodeExt);

                // Extension for derivedFrom (ImagingSelection.derivedFrom - reference to ImagingStudy)
                Extension derivedFromExt = new Extension();
                derivedFromExt.setUrl(EXT_DERIVED_FROM);
                derivedFromExt.setValue(new Reference("urn:uuid:" + uuids.studyUuid));
                basic.addExtension(derivedFromExt);

                // Extensions for selected instances (ImagingSelection.instance)
                for (ImageReference img : images) {
                    Extension instanceExt = new Extension();
                    instanceExt.setUrl(EXT_SELECTED_INSTANCE);

                    // SOP Instance UID
                    Extension uidExt = new Extension();
                    uidExt.setUrl("uid");
                    uidExt.setValue(new StringType(img.sopInstanceUID));
                    instanceExt.addExtension(uidExt);

                    // SOP Class UID if available
                    if (img.sopClassUID != null) {
                        Extension sopClassExt = new Extension();
                        sopClassExt.setUrl("sopClass");
                        sopClassExt.setValue(new Coding()
                            .setSystem("urn:ietf:rfc:3986")
                            .setCode(IHE_UID_PREFIX + img.sopClassUID));
                        instanceExt.addExtension(sopClassExt);
                    }

                    basic.addExtension(instanceExt);
                }

                selections.add(basic);
            }
        }

        return selections;
    }

    /**
     * Processes content sequence looking for key image references.
     */
    private void processContentForKeyImages(Sequence contentSeq, Map<String, List<ImageReference>> keyImagesByCode) {
        for (Attributes item : contentSeq) {
            String valueType = item.getString(Tag.ValueType);

            if ("IMAGE".equals(valueType)) {
                Attributes conceptName = getConceptNameCode(item);
                if (conceptName != null) {
                    String codeValue = conceptName.getString(Tag.CodeValue);
                    String codingScheme = conceptName.getString(Tag.CodingSchemeDesignator);
                    String meaning = conceptName.getString(Tag.CodeMeaning);

                    if (isKeyImageCode(codeValue, codingScheme)) {
                        String codeKey = codingScheme + "|" + codeValue + "|" + meaning;

                        Sequence refSopSeq = item.getSequence(Tag.ReferencedSOPSequence);
                        if (refSopSeq != null) {
                            for (Attributes sopItem : refSopSeq) {
                                ImageReference ref = new ImageReference();
                                ref.sopClassUID = sopItem.getString(Tag.ReferencedSOPClassUID);
                                ref.sopInstanceUID = sopItem.getString(Tag.ReferencedSOPInstanceUID);

                                keyImagesByCode.computeIfAbsent(codeKey, k -> new ArrayList<>()).add(ref);
                            }
                        }
                    }
                }
            }

            Sequence nestedContent = item.getSequence(Tag.ContentSequence);
            if (nestedContent != null) {
                processContentForKeyImages(nestedContent, keyImagesByCode);
            }
        }
    }

    /**
     * Checks if a code indicates a key image reference.
     */
    private boolean isKeyImageCode(String codeValue, String codingScheme) {
        Set<String> keyImageCodes = Set.of(
            CodeConstants.CODE_OF_INTEREST,
            be.uzleuven.ihe.dicom.constants.DicomConstants.IOCM_REJECTED_QUALITY,
            "113002", "113003", "113004", "113005",
            "113006", "113007", "113008", "113009", be.uzleuven.ihe.dicom.constants.DicomConstants.CODE_QUALITY_ISSUE, be.uzleuven.ihe.dicom.constants.DicomConstants.CODE_BEST_IN_SET,
            "113018", "113020", CodeConstants.CODE_KOS_MANIFEST, "113035", "113036", be.uzleuven.ihe.dicom.constants.DicomConstants.IOCM_REJECTED_PATIENT_SAFETY, "113038"
        );

        return be.uzleuven.ihe.dicom.constants.DicomConstants.SCHEME_DCM.equals(codingScheme) && keyImageCodes.contains(codeValue);
    }

    /**
     * Parses DICOM datetime string to Java Date with timezone.
     * MADO requirement: Apply timezone offset to all datetime values.
     */
    private Date parseDicomDateTime(String dicomDateTime, String timezoneOffset) {
        if (dicomDateTime == null) return null;

        try {
            String pattern;
            if (dicomDateTime.length() == 8) {
                pattern = "yyyyMMdd";
            } else if (dicomDateTime.length() >= 14) {
                pattern = "yyyyMMddHHmmss";
            } else if (dicomDateTime.length() >= 12) {
                pattern = "yyyyMMddHHmm";
            } else {
                return null;
            }

            SimpleDateFormat sdf = new SimpleDateFormat(pattern);

            // MADO compliance: Apply timezone offset
            if (timezoneOffset != null && !timezoneOffset.isEmpty()) {
                sdf.setTimeZone(TimeZone.getTimeZone("GMT" + timezoneOffset));
            }

            // Truncate to pattern length (pattern is always <= dicomDateTime length based on the if-else above)
            return sdf.parse(dicomDateTime.substring(0, pattern.length()));
        } catch (ParseException e) {
            return null;
        }
    }

    // ============================================================================
    // HELPER CLASSES
    // ============================================================================

    /**
     * Generates narrative text for resources (satisfies dom-6 constraint).
     */
    private Narrative createNarrative(String divContent) {
        Narrative narrative = new Narrative();
        narrative.setStatus(Narrative.NarrativeStatus.GENERATED);
        narrative.setDivAsString("<div xmlns=\"http://www.w3.org/1999/xhtml\">" + divContent + "</div>");
        return narrative;
    }

    /**
     * Creates narrative for Composition resource.
     */
    private String createCompositionNarrative(MADOMetadata metadata) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h2>MADO Imaging Manifest</h2>");
        sb.append("<p><b>Study:</b> ").append(escapeHtml(metadata.studyDescription != null ? metadata.studyDescription : "Imaging Study")).append("</p>");
        sb.append("<p><b>Patient:</b> ").append(escapeHtml(metadata.patientName != null ? metadata.patientName : "Unknown")).append("</p>");
        sb.append("<p><b>Study Date:</b> ").append(escapeHtml(metadata.studyDate != null ? metadata.studyDate : "Unknown")).append("</p>");
        if (metadata.accessionNumber != null) {
            sb.append("<p><b>Accession Number:</b> ").append(escapeHtml(metadata.accessionNumber)).append("</p>");
        }
        return sb.toString();
    }

    /**
     * Creates narrative for Patient resource.
     */
    private String createPatientNarrative(MADOMetadata metadata) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h3>Patient Information</h3>");
        sb.append("<p><b>Name:</b> ").append(escapeHtml(metadata.patientName != null ? metadata.patientName : "Unknown")).append("</p>");
        sb.append("<p><b>ID:</b> ").append(escapeHtml(metadata.patientId != null ? metadata.patientId : "Unknown")).append("</p>");
        if (metadata.patientBirthDate != null) {
            sb.append("<p><b>Birth Date:</b> ").append(escapeHtml(metadata.patientBirthDate)).append("</p>");
        }
        if (metadata.patientSex != null) {
            sb.append("<p><b>Gender:</b> ").append(escapeHtml(metadata.patientSex)).append("</p>");
        }
        return sb.toString();
    }

    /**
     * Creates narrative for Device resource.
     */
    private String createDeviceNarrative(MADOMetadata metadata) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h3>Imaging Device</h3>");
        sb.append("<p><b>Manufacturer:</b> ").append(escapeHtml(metadata.manufacturer != null ? metadata.manufacturer : "Unknown")).append("</p>");
        if (metadata.manufacturerModelName != null) {
            sb.append("<p><b>Model:</b> ").append(escapeHtml(metadata.manufacturerModelName)).append("</p>");
        }
        if (metadata.softwareVersions != null) {
            sb.append("<p><b>Software Version:</b> ").append(escapeHtml(metadata.softwareVersions)).append("</p>");
        }
        if (metadata.institutionName != null) {
            sb.append("<p><b>Institution:</b> ").append(escapeHtml(metadata.institutionName)).append("</p>");
        }
        return sb.toString();
    }

    /**
     * Creates narrative for Endpoint resource.
     */
    private String createEndpointNarrative(String address, String type) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h3>").append(escapeHtml(type)).append(" Endpoint</h3>");
        sb.append("<p><b>Address:</b> ").append(escapeHtml(address)).append("</p>");
        sb.append("<p><b>Status:</b> Active</p>");
        return sb.toString();
    }

    /**
     * Creates narrative for ImagingStudy resource.
     */
    private String createImagingStudyNarrative(MADOMetadata metadata, int seriesCount, int instanceCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h3>Imaging Study</h3>");
        sb.append("<p><b>Description:</b> ").append(escapeHtml(metadata.studyDescription != null ? metadata.studyDescription : "Imaging Study")).append("</p>");
        sb.append("<p><b>Study Instance UID:</b> ").append(escapeHtml(metadata.studyInstanceUID)).append("</p>");
        sb.append("<p><b>Study Date:</b> ").append(escapeHtml(metadata.studyDate != null ? metadata.studyDate : "Unknown")).append("</p>");
        if (metadata.accessionNumber != null) {
            sb.append("<p><b>Accession Number:</b> ").append(escapeHtml(metadata.accessionNumber)).append("</p>");
        }
        sb.append("<p><b>Series Count:</b> ").append(seriesCount).append("</p>");
        sb.append("<p><b>Instance Count:</b> ").append(instanceCount).append("</p>");
        return sb.toString();
    }

    /**
     * Escapes HTML special characters.
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    /**
     * Container for extracted MADO metadata.
     */
    private static class MADOMetadata {
        // Patient
        String patientName;
        String patientId;
        String issuerOfPatientId;
        String issuerOfPatientIdUniversalId;
        String issuerOfPatientIdUniversalIdType;
        String typeOfPatientId;
        String patientBirthDate;
        String patientSex;

        // Study
        String studyInstanceUID;
        String accessionNumber;
        String issuerOfAccessionNumber;
        String issuerOfAccessionNumberType;
        String studyDate;
        String studyTime;
        String studyDescription;
        String studyID;

        // Manifest
        String sopInstanceUID;
        String seriesInstanceUID;
        String timezoneOffset;
        String contentDate;
        String contentTime;
        String seriesDate;
        String seriesTime;

        // Device/Equipment
        String manufacturer;
        String manufacturerModelName;
        String softwareVersions;
        String institutionName;

        // Personnel
        String referringPhysicianName;

        // Target Region (body site)
        String targetRegionCode;
        String targetRegionScheme;
        String targetRegionMeaning;

        // Referenced Requests (multiple accession numbers)
        List<ReferencedRequest> referencedRequests;
    }

    /**
     * Holds data from Referenced Request Sequence entries.
     */
    private static class ReferencedRequest {
        String accessionNumber;
        String issuerOfAccessionNumber;
        String studyInstanceUID;
        String requestedProcedureId;
        String placerOrderNumber;
        String fillerOrderNumber;
    }

    /**
     * Helper class for image references.
     */
    private static class ImageReference {
        String sopClassUID;
        String sopInstanceUID;
    }

    /**
     * Container for generated UUIDs.
     */
    private static class ResourceUUIDs {
        String compositionUuid;
        String patientUuid;
        String studyUuid;
        String deviceUuid;
        String practitionerUuid;
        Map<String, String> endpointUuids = new HashMap<>();
        Composition.SectionComponent keyImageSelectionSection = null; // For populating Key Image Selection references

        /**
         * Constructs ResourceUUIDs with deterministic or random UUIDs based on configuration.
         *
         * @param metadata The MADO metadata extracted from DICOM
         * @param useDeterministic If true, generate deterministic UUIDs; if false, use random UUIDs
         */
        ResourceUUIDs(MADOMetadata metadata, boolean useDeterministic) {
            if (useDeterministic) {
                // Generate deterministic UUIDs based on DICOM identifiers
                this.compositionUuid = DeterministicUuidGenerator.generateCompositionUuid(metadata.sopInstanceUID);
                this.patientUuid = DeterministicUuidGenerator.generatePatientUuid(metadata.patientId, metadata.issuerOfPatientId);
                this.studyUuid = DeterministicUuidGenerator.generateStudyUuid(metadata.studyInstanceUID);
                this.deviceUuid = DeterministicUuidGenerator.generateDeviceUuid(metadata.manufacturer, metadata.sopInstanceUID);
                this.practitionerUuid = DeterministicUuidGenerator.generatePractitionerUuid(
                    metadata.referringPhysicianName != null ? metadata.referringPhysicianName : "unknown"
                );
            } else {
                // Generate random UUIDs (original behavior)
                this.compositionUuid = UUID.randomUUID().toString();
                this.patientUuid = UUID.randomUUID().toString();
                this.studyUuid = UUID.randomUUID().toString();
                this.deviceUuid = UUID.randomUUID().toString();
                this.practitionerUuid = UUID.randomUUID().toString();
            }
        }
    }
}

