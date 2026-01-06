package be.uzleuven.ihe.dicom.convertor.fhir;

import be.uzleuven.ihe.dicom.constants.CodeConstants;
import be.uzleuven.ihe.dicom.constants.DicomConstants;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;
import org.hl7.fhir.r4.model.*;

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
 * - ImagingSelection resources for key image flagging (backported to R4)
 * - Device resource for the creating system
 * - Practitioner resource for the author (if available)
 *
 * Compliant with MADO Implementation Guide specifications.
 * Note: Uses R4 with backported R5 concepts where necessary.
 */
public class MADOToFHIRConverter {

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
        ResourceUUIDs uuids = new ResourceUUIDs();

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
        List<Endpoint> endpoints = createEndpoints(attrs, metadata, uuids);

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
            String uuid = UUID.randomUUID().toString();
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

        // Identifier from KOS SOP Instance UID
        composition.setIdentifier(new Identifier()
            .setSystem("urn:dicom:uid")
            .setValue(IHE_UID_PREFIX + metadata.sopInstanceUID));

        // Status
        composition.setStatus(Composition.CompositionStatus.FINAL);

        // Type - MADO Imaging Manifest (LOINC code for Radiology Study)
        composition.setType(new CodeableConcept()
            .addCoding(new Coding()
                .setSystem("http://loinc.org")
                .setCode("11303-0")
                .setDisplay("Radiology Study")));

        // Subject - Patient
        composition.setSubject(new Reference("urn:uuid:" + uuids.patientUuid));

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

            // Set system from issuer - use OID format per MADO
            if (metadata.issuerOfPatientIdUniversalId != null) {
                identifier.setSystem(IHE_UID_PREFIX + metadata.issuerOfPatientIdUniversalId);
            } else if (metadata.issuerOfPatientId != null) {
                identifier.setSystem(metadata.issuerOfPatientId);
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

        // Manufacturer
        if (metadata.manufacturer != null) {
            device.setManufacturer(metadata.manufacturer);
        }

        // Device name
        if (metadata.manufacturerModelName != null) {
            Device.DeviceDeviceNameComponent deviceName = device.addDeviceName();
            deviceName.setName(metadata.manufacturerModelName);
            deviceName.setType(Device.DeviceNameType.MANUFACTURERNAME);
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
    private List<Endpoint> createEndpoints(Attributes attrs, MADOMetadata metadata, ResourceUUIDs uuids) {
        List<Endpoint> endpoints = new ArrayList<>();
        Set<String> processedUrls = new HashSet<>();
        Set<String> processedLocationUids = new HashSet<>();

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
                                String uuid = UUID.randomUUID().toString();
                                wadoEndpoint.setId(uuid);
                                uuids.endpointUuids.put(baseUrl, uuid);
                                endpoints.add(wadoEndpoint);
                                processedUrls.add(baseUrl);

                                // MADO requirement: Create IHE-IID endpoint for viewer launch
                                Endpoint iidEndpoint = createIheIidEndpoint(baseUrl, metadata.studyInstanceUID);
                                String iidUuid = UUID.randomUUID().toString();
                                iidEndpoint.setId(iidUuid);
                                String iidAddress = iidEndpoint.getAddress();
                                uuids.endpointUuids.put(iidAddress, iidUuid);
                                endpoints.add(iidEndpoint);
                            }
                        }

                        // Retrieve Location UID based endpoint
                        String retrieveLocationUID = seriesItem.getString(Tag.RetrieveLocationUID);
                        if (retrieveLocationUID != null && !processedLocationUids.contains(retrieveLocationUID)) {
                            Endpoint locationEndpoint = createLocationUidEndpoint(retrieveLocationUID);
                            String uuid = UUID.randomUUID().toString();
                            locationEndpoint.setId(uuid);
                            String locationAddress = IHE_UID_PREFIX + retrieveLocationUID;
                            uuids.endpointUuids.put(locationAddress, uuid);
                            endpoints.add(locationEndpoint);
                            processedLocationUids.add(retrieveLocationUID);
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
     * Creates a WADO-RS Endpoint resource (R4 compatible).
     */
    private Endpoint createWadoRsEndpoint(String address) {
        Endpoint endpoint = new Endpoint();

        // Add profile
        endpoint.getMeta().addProfile(PROFILE_WADO_ENDPOINT);

        endpoint.setStatus(Endpoint.EndpointStatus.ACTIVE);

        // Connection type: dicom-wado-rs
        Coding connectionType = new Coding()
            .setSystem(ENDPOINT_CONNECTION_TYPE_SYSTEM)
            .setCode("dicom-wado-rs")
            .setDisplay("DICOM WADO-RS");
        endpoint.setConnectionType(connectionType);

        // Payload type
        CodeableConcept payloadType = new CodeableConcept();
        payloadType.addCoding()
            .setSystem("http://terminology.hl7.org/CodeSystem/endpoint-payload-type")
            .setCode("any")
            .setDisplay("Any");
        payloadType.setText("DICOM");
        endpoint.addPayloadType(payloadType);

        endpoint.addPayloadMimeType("application/dicom");
        endpoint.setAddress(address);

        return endpoint;
    }

    /**
     * Creates IHE-IID Endpoint for viewer launch (MADO requirement).
     */
    private Endpoint createIheIidEndpoint(String baseUrl, String studyInstanceUID) {
        Endpoint endpoint = new Endpoint();

        // Add profile
        endpoint.getMeta().addProfile(PROFILE_IID_ENDPOINT);

        endpoint.setStatus(Endpoint.EndpointStatus.ACTIVE);

        // Connection type: ihe-iid (from MADO IG)
        Coding connectionType = new Coding()
            .setSystem(IHE_ENDPOINT_CONNECTION_TYPE_SYSTEM)
            .setCode("ihe-iid")
            .setDisplay("IHE IID endpoint");
        endpoint.setConnectionType(connectionType);

        // Payload type
        CodeableConcept payloadType = new CodeableConcept();
        payloadType.setText("IHE IID");
        endpoint.addPayloadType(payloadType);

        endpoint.addPayloadMimeType("text/html");

        // IID URL format - typically base URL + viewer path + study reference
        String iidUrl = baseUrl + "/viewer?studyUID=" + studyInstanceUID;
        endpoint.setAddress(iidUrl);

        return endpoint;
    }

    /**
     * Creates an endpoint based on Retrieve Location UID.
     */
    private Endpoint createLocationUidEndpoint(String locationUID) {
        Endpoint endpoint = new Endpoint();
        endpoint.setStatus(Endpoint.EndpointStatus.ACTIVE);

        // MADO requirement: identifier must be OID for lookup
        endpoint.addIdentifier()
            .setSystem("urn:dicom:uid")
            .setValue(IHE_UID_PREFIX + locationUID);

        // Connection type
        Coding connectionType = new Coding()
            .setSystem(ENDPOINT_CONNECTION_TYPE_SYSTEM)
            .setCode("dicom-wado-rs")
            .setDisplay("DICOM WADO-RS");
        endpoint.setConnectionType(connectionType);

        // Payload type
        CodeableConcept payloadType = new CodeableConcept();
        payloadType.addCoding()
            .setSystem("http://terminology.hl7.org/CodeSystem/endpoint-payload-type")
            .setCode("any")
            .setDisplay("Any");
        payloadType.setText("DICOM");
        endpoint.addPayloadType(payloadType);

        endpoint.addPayloadMimeType("application/dicom");
        endpoint.setAddress(IHE_UID_PREFIX + locationUID);

        return endpoint;
    }

    /**
     * Creates the ImagingStudy resource with series and instances.
     */
    private ImagingStudy createImagingStudy(Attributes attrs, MADOMetadata metadata, ResourceUUIDs uuids) {
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

        // MADO requirement: Map ALL entries from Referenced Request Sequence to basedOn
        if (!metadata.referencedRequests.isEmpty()) {
            for (ReferencedRequest req : metadata.referencedRequests) {
                if (req.accessionNumber != null) {
                    Reference basedOn = new Reference();
                    Identifier accessionId = new Identifier();

                    // Set system from issuer if available
                    if (req.issuerOfAccessionNumber != null) {
                        accessionId.setSystem(IHE_UID_PREFIX + req.issuerOfAccessionNumber);
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
        for (String uuid : uuids.endpointUuids.values()) {
            study.addEndpoint(new Reference("urn:uuid:" + uuid));
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
                                series.setModality(new Coding()
                                    .setSystem("http://dicom.nema.org/resources/ontology/DCM")
                                    .setCode(modality));
                            }

                            // MADO requirement: Map Target Region to bodySite
                            if (metadata.targetRegionCode != null) {
                                Coding bodySite = mapBodySite(
                                    metadata.targetRegionCode,
                                    metadata.targetRegionScheme,
                                    metadata.targetRegionMeaning);
                                if (bodySite != null) {
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
                                }

                                // Number of frames extension (MADO IG URL)
                                if (numberOfFrames > 0) {
                                    Extension framesExt = new Extension();
                                    framesExt.setUrl(EXT_NUMBER_OF_FRAMES);
                                    framesExt.setValue(new IntegerType(numberOfFrames));
                                    instance.addExtension(framesExt);
                                }

                                // Instance description extension (MADO IG URL)
                                if (rows > 0 && columns > 0) {
                                    Extension descExt = new Extension();
                                    descExt.setUrl(EXT_INSTANCE_DESCRIPTION);
                                    descExt.setValue(new StringType(rows + "x" + columns));
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

            // Add all series to study
            for (ImagingStudy.ImagingStudySeriesComponent series : seriesMap.values()) {
                study.addSeries(series);
            }
        }

        return study;
    }

    /**
     * Maps SRT body site codes to SNOMED CT.
     */
    private Coding mapBodySite(String code, String scheme, String meaning) {
        // First try the mapping table
        if (DicomConstants.SCHEME_SRT.equals(scheme) && CodeConstants.BODY_SITE_MAP.containsKey(code)) {
            String[] snomedInfo = CodeConstants.BODY_SITE_MAP.get(code);
            return new Coding()
                .setSystem(CodeConstants.SNOMED_SYSTEM)
                .setCode(snomedInfo[0])
                .setDisplay(snomedInfo[1]);
        }

        // Fallback: use original coding
        return new Coding()
            .setSystem(DicomConstants.SCHEME_SRT.equals(scheme) ? "http://snomed.info/srt" : scheme)
            .setCode(code)
            .setDisplay(meaning);
    }

    /**
     * Enriches series information from the SR Content Tree (TID 1600/1602).
     */
    private void enrichSeriesFromContentTree(Attributes attrs,
                                             Map<String, ImagingStudy.ImagingStudySeriesComponent> seriesMap) {
        Sequence contentSeq = attrs.getSequence(Tag.ContentSequence);
        if (contentSeq == null) return;

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
        Sequence contentSeq = imageLibrary.getSequence(Tag.ContentSequence);
        if (contentSeq == null) return;

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
        Sequence contentSeq = group.getSequence(Tag.ContentSequence);
        if (contentSeq == null) return;

        String seriesUID = null;
        String seriesDescription = null;
        Integer seriesNumber = null;
        Map<String, Integer> instanceNumbers = new HashMap<>(); // SOP Instance UID -> Instance Number

        for (Attributes item : contentSeq) {
            String valueType = item.getString(Tag.ValueType);
            Attributes conceptName = getConceptNameCode(item);
            if (conceptName == null) continue;

            String codeValue = conceptName.getString(Tag.CodeValue);

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
            } else if ("IMAGE".equals(valueType)) {
                // Process IMAGE items to extract instance numbers
                extractInstanceNumberFromImage(item, instanceNumbers);
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
                // Populate instance numbers from SR content tree
                if (!instanceNumbers.isEmpty() && series.hasInstance()) {
                    for (ImagingStudy.ImagingStudySeriesInstanceComponent instance : series.getInstance()) {
                        String instanceUID = instance.getUid();
                        if (instanceNumbers.containsKey(instanceUID)) {
                            instance.setNumber(instanceNumbers.get(instanceUID));
                        }
                    }
                }
            }
        }
    }

    /**
     * Extracts instance number from an IMAGE item in the SR content tree.
     */
    private void extractInstanceNumberFromImage(Attributes imageItem, Map<String, Integer> instanceNumbers) {
        // Get the SOP Instance UID from ReferencedSOPSequence
        Sequence refSopSeq = imageItem.getSequence(Tag.ReferencedSOPSequence);
        if (refSopSeq != null && !refSopSeq.isEmpty()) {
            Attributes sopItem = refSopSeq.get(0);
            String sopInstanceUID = sopItem.getString(Tag.ReferencedSOPInstanceUID);

            if (sopInstanceUID != null) {
                // Look for instance number in the image item's content sequence
                Sequence contentSeq = imageItem.getSequence(Tag.ContentSequence);
                if (contentSeq != null) {
                    for (Attributes item : contentSeq) {
                        String valueType = item.getString(Tag.ValueType);
                        Attributes conceptName = getConceptNameCode(item);
                        if (conceptName != null && DicomConstants.VALUE_TYPE_TEXT.equals(valueType)) {
                            String codeValue = conceptName.getString(Tag.CodeValue);
                            if (CODE_INSTANCE_NUMBER.equals(codeValue)) {
                                try {
                                    Integer instanceNumber = Integer.parseInt(item.getString(Tag.TextValue).trim());
                                    instanceNumbers.put(sopInstanceUID, instanceNumber);
                                } catch (NumberFormatException e) {
                                    // Ignore invalid instance number
                                }
                                break;
                            }
                        }
                    }
                }
            }
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

            return sdf.parse(dicomDateTime.substring(0, Math.min(dicomDateTime.length(), pattern.length())));
        } catch (ParseException e) {
            return null;
        }
    }

    // ============================================================================
    // HELPER CLASSES
    // ============================================================================

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
        String compositionUuid = UUID.randomUUID().toString();
        String patientUuid = UUID.randomUUID().toString();
        String studyUuid = UUID.randomUUID().toString();
        String deviceUuid = UUID.randomUUID().toString();
        String practitionerUuid = UUID.randomUUID().toString();
        Map<String, String> endpointUuids = new HashMap<>();
        Composition.SectionComponent keyImageSelectionSection = null; // For populating Key Image Selection references
    }
}

