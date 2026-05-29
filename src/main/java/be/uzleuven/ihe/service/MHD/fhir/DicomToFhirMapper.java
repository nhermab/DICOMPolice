package be.uzleuven.ihe.service.MHD.fhir;

import be.uzleuven.ihe.service.MHD.config.MHDConfiguration;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.hl7.fhir.r4.model.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * Utility class to map DICOM study metadata to FHIR resources.
 * Implements IHE MHD Document Responder mappings for MADO IG R4 manifests.
 *
 * Produces DocumentReferences conforming to:
 * - MadoFhirDocumentReference (for FHIR Imaging Study Manifests)
 * - MadoDicomKosDocumentReference (for DICOM KOS Manifests)
 *
 * Per MADO IG, the MHD response SHALL include both FHIR and DICOM KOS
 * DocumentReferences linked via relatesTo with code "transforms".
 */
public class DicomToFhirMapper {

    private static final String STUDY_UID_SYSTEM = "urn:dicom:uid";

    // MADO IG R4 Extension URLs
    private static final String EXT_DOCREF_MODALITY = "http://hl7.org/fhir/5.0/StructureDefinition/extension-DocumentReference.modality";
    private static final String EXT_DOCREF_BODY_SITE = "http://hl7.org/fhir/5.0/StructureDefinition/extension-DocumentReference.bodySite";
    private static final String EXT_DOCREF_CONTENT_PROFILE = "http://hl7.org/fhir/5.0/StructureDefinition/extension-DocumentReference.content.profile";

    // MADO IG Profile URLs
    private static final String PROFILE_FHIR_DOCREF = "https://profiles.ihe.net/RAD/MADO/StructureDefinition/MadoFhirDocumentReference";
    private static final String PROFILE_KOS_DOCREF = "https://profiles.ihe.net/RAD/MADO/StructureDefinition/MadoDicomKosDocumentReference";
    private static final String PROFILE_MADO_BUNDLE = "https://profiles.ihe.net/RAD/MADO/StructureDefinition/MadoFhirBundle";
    private static final String PROFILE_MADO_CREATOR = "https://profiles.ihe.net/RAD/MADO/StructureDefinition/MadoCreator";
    private static final String PROFILE_MADO_CREATOR_ORG = "https://profiles.ihe.net/RAD/MADO/StructureDefinition/MadoCreatorOrganization";

    // MADO Device Type Code System
    private static final String MADO_DEVICE_TYPE_SYSTEM = "https://profiles.ihe.net/RAD/MADO/CodeSystem/MadoDeviceType";

    /**
     * Create a stable FHIR ID from a Study Instance UID.
     * Uses Base64 URL-safe encoding.
     */
    public static String createFhirId(String studyInstanceUid) {
        if (studyInstanceUid == null) {
            return UUID.randomUUID().toString();
        }
        String base64 = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(studyInstanceUid.getBytes(StandardCharsets.UTF_8));
        return base64.replace('_', '.');
    }

    /**
     * Decode a FHIR ID back to Study Instance UID.
     */
    public static String decodeStudyUidFromFhirId(String fhirId) {
        if (fhirId == null) return null;
        if (fhirId.toLowerCase().endsWith(".dcm")) {
            fhirId = fhirId.substring(0, fhirId.length() - 4);
        }
        try {
            String base64 = fhirId.replace('.', '_');
            byte[] decoded = Base64.getUrlDecoder().decode(base64);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return fhirId;
        }
    }

    /**
     * Map DICOM study attributes to a FHIR DocumentReference for a FHIR MADO manifest.
     * Implements MADO IG R4 MadoFhirDocumentReference profile.
     *
     * Per MADO IG, the FHIR DocumentReference SHALL have a relatesTo:kosReference
     * linking to the corresponding DICOM KOS DocumentReference with code "transforms".
     */
    public static DocumentReference mapStudyToDocumentReference(Attributes study, MHDConfiguration config,
                                                                  byte[] manifestBytes) {
        DocumentReference docRef = new DocumentReference();

        String studyUid = study.getString(Tag.StudyInstanceUID);
        String fhirId = createFhirId(studyUid);

        // Set resource ID
        docRef.setId(fhirId);

        // Meta profile - MADO FHIR DocumentReference
        Meta meta = new Meta();
        meta.addProfile(PROFILE_FHIR_DOCREF);
        docRef.setMeta(meta);

        // Modality extension (R5 cross-version extension) - MADO IG mandates exactly 1 extension with CodeableConcept
        String[] modalities = study.getStrings(Tag.ModalitiesInStudy);
        if (modalities != null && modalities.length > 0) {
            Extension modalityExt = new Extension(EXT_DOCREF_MODALITY);
            CodeableConcept modalityConcept = new CodeableConcept();
            for (String modality : modalities) {
                modalityConcept.addCoding()
                    .setSystem("http://dicom.nema.org/resources/ontology/DCM")
                    .setCode(modality);
            }
            modalityExt.setValue(modalityConcept);
            docRef.addExtension(modalityExt);
        }

        // BodySite extension (R5 cross-version extension) - MADO IG 0..1, MustSupport
        String bodyPartExamined = study.getString(Tag.BodyPartExamined);
        if (bodyPartExamined != null && !bodyPartExamined.isEmpty()) {
            Extension bodySiteExt = new Extension(EXT_DOCREF_BODY_SITE);
            Extension conceptExt = new Extension("concept");
            CodeableConcept bodySiteConcept = new CodeableConcept();
            bodySiteConcept.setText(bodyPartExamined);
            conceptExt.setValue(bodySiteConcept);
            bodySiteExt.addExtension(conceptExt);
            docRef.addExtension(bodySiteExt);
        }

        // Master Identifier - the Bundle identifier (MADO IG R4 requirement: UniqueIdIdentifier)
        String bundleIdentifierValue = "urn:oid:" + studyUid + ".1";
        Identifier masterIdentifier = new Identifier();
        masterIdentifier.setSystem("urn:ietf:rfc:3986");
        masterIdentifier.setValue(bundleIdentifierValue);
        docRef.setMasterIdentifier(masterIdentifier);

        // Identifier - must include masterIdentifier per mado-docref-1 constraint
        docRef.addIdentifier(masterIdentifier.copy());

        // Document status - current
        docRef.setStatus(Enumerations.DocumentReferenceStatus.CURRENT);

        // Document type - LOINC 18748-4 "Diagnostic imaging Study"
        CodeableConcept type = new CodeableConcept();
        type.addCoding()
            .setSystem("http://loinc.org")
            .setCode("18748-4")
            .setDisplay("Diagnostic imaging Study");
        docRef.setType(type);

        // Category - Medical-Imaging
        CodeableConcept category = new CodeableConcept();
        category.addCoding()
            .setSystem("http://hl7.eu/fhir/eu-health-data-api/CodeSystem/eehrxf-document-priority-category-cs")
            .setCode("Medical-Imaging")
            .setDisplay("Medical-Imaging");
        docRef.addCategory(category);

        // Subject - Patient reference
        String patientId = study.getString(Tag.PatientID);
        if (patientId != null) {
            Reference patientRef = new Reference();
            patientRef.setType("Patient");
            Identifier patientIdentifier = new Identifier();
            patientIdentifier.setSystem("urn:oid:" + config.getPatientIdIssuerOid());
            patientIdentifier.setValue(patientId);
            patientRef.setIdentifier(patientIdentifier);
            String patientName = study.getString(Tag.PatientName, "");
            patientRef.setDisplay(patientName.replace("^", " ").trim());
            docRef.setSubject(patientRef);
        }

        // new Extension for homeCommunityId in MHD Responder document references
        if (config.getHomeCommunityId() != null && !config.getHomeCommunityId().isEmpty()) {
            Extension homeCommunityUid = new Extension();
            homeCommunityUid.setValue(new Identifier()
                    .setSystem("urn:ietf:rfc:3986")
                    .setValue(config.getHomeCommunityId()))
                    .setUrl("https://profiles.ihe.net/ITI/MHD/StructureDefinition/ihe-homeCommunityId");
            docRef.addExtension(homeCommunityUid);
        }

        // Date - Study Date/Time
        Date studyDate = parseDicomDateTime(study.getString(Tag.StudyDate), study.getString(Tag.StudyTime));
        if (studyDate != null) {
            docRef.setDate(studyDate);
        }

        // Author - source-device (MADO Creator device)
        Reference deviceRef = createMadoCreatorDeviceReference(config);
        docRef.addAuthor(deviceRef);

        // Author - source-organization (MADO Creator Organization)
        Reference orgRef = createMadoCreatorOrganizationReference(config);
        docRef.addAuthor(orgRef);

        // Description - prefer StudyDescription, fallback to generic
        String studyDescription = study.getString(Tag.StudyDescription);
        if (studyDescription != null && !studyDescription.trim().isEmpty()) {
            docRef.setDescription(studyDescription);
        } else {
            docRef.setDescription("Imaging Manifest for Imaging Study urn:oid:" + studyUid);
        }

        // relatesTo:kosReference - link to corresponding KOS DocumentReference (code = "transforms")
        String kosDocRefId = "kos-" + fhirId;
        DocumentReference.DocumentReferenceRelatesToComponent kosRelatesTo =
            new DocumentReference.DocumentReferenceRelatesToComponent();
        kosRelatesTo.setCode(DocumentReference.DocumentRelationshipType.TRANSFORMS);
        kosRelatesTo.setTarget(new Reference("DocumentReference/" + kosDocRefId));
        docRef.addRelatesTo(kosRelatesTo);

        // Content - attachment with retrieval URL
        DocumentReference.DocumentReferenceContentComponent content = new DocumentReference.DocumentReferenceContentComponent();

        // Content profile extension (R5 cross-version) - valueCanonical with MadoFhirBundle profile
        Extension contentProfileExt = new Extension(EXT_DOCREF_CONTENT_PROFILE);
        Extension valueExt = new Extension("value[x]");
        valueExt.setValue(new CanonicalType(PROFILE_MADO_BUNDLE + "|0.1.0"));
        contentProfileExt.addExtension(valueExt);
        content.addExtension(contentProfileExt);

        Attachment attachment = new Attachment();
        attachment.setContentType("application/fhir+json");
        attachment.setLanguage("en");

        // URL to retrieve the MADO FHIR manifest Bundle
        String retrieveUrl = config.getFhirBaseUrl() + "/Bundle/" + fhirId;
        attachment.setUrl(retrieveUrl);

        // Title - prefer StudyDescription, fallback to generic
        String fhirStudyDesc = study.getString(Tag.StudyDescription);
        if (fhirStudyDesc != null && !fhirStudyDesc.trim().isEmpty()) {
            attachment.setTitle(fhirStudyDesc);
        } else {
            attachment.setTitle("FHIR Imaging Manifest for Imaging Study");
        }

        // Creation time (required by MADO IG min:1)
        attachment.setCreation(new Date());

        // If manifest bytes are provided, calculate size and hash
        if (manifestBytes != null && manifestBytes.length > 0) {
            attachment.setSize(manifestBytes.length);
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(manifestBytes);
                attachment.setHash(hash);
            } catch (NoSuchAlgorithmException e) {
                // SHA-256 should always be available
            }
        }

        content.setAttachment(attachment);

        // Format code for MADO FHIR manifest (patternCoding from IG)
        Coding formatCoding = new Coding();
        formatCoding.setSystem("http://ihe.net/fhir/ihe.formatcode.fhir/CodeSystem/formatcode");
        formatCoding.setVersion("1.5.0");
        formatCoding.setCode("urn:ihe:rad:MADO:fhir-manifest:2026");
        content.setFormat(formatCoding);

        docRef.addContent(content);

        // Context - including Study Instance UID and Accession Number in related
        DocumentReference.DocumentReferenceContextComponent context = new DocumentReference.DocumentReferenceContextComponent();

        // Period - study started date (required by MADO IG: context.period min:1, period.start min:1)
        if (studyDate != null) {
            Period period = new Period();
            period.setStart(studyDate);
            context.setPeriod(period);
        }

        // FacilityType (required by MADO IG min:1)
        CodeableConcept facilityType = new CodeableConcept();
        facilityType.addCoding()
            .setSystem("http://snomed.info/sct")
            .setCode("722171005")
            .setDisplay("Diagnostic imaging department");
        context.setFacilityType(facilityType);

        // PracticeSetting (required by MADO IG min:1)
        CodeableConcept practiceSetting = new CodeableConcept();
        practiceSetting.addCoding()
            .setSystem("http://snomed.info/sct")
            .setCode("394914008")
            .setDisplay("Radiology");
        context.setPracticeSetting(practiceSetting);

        // Related - Study Instance UID (typed identifier per MadoReferencedStudyInstanceUidIdentifier)
        Reference studyUidRef = new Reference();
        Identifier studyUidIdentifier = new Identifier();
        CodeableConcept studyUidType = new CodeableConcept();
        studyUidType.addCoding()
            .setSystem("http://dicom.nema.org/resources/ontology/DCM")
            .setCode("110180")
            .setDisplay("Study Instance UID");
        studyUidIdentifier.setType(studyUidType);
        studyUidIdentifier.setSystem("urn:dicom:uid");
        studyUidIdentifier.setValue("urn:oid:" + studyUid);
        studyUidRef.setIdentifier(studyUidIdentifier);
        context.addRelated(studyUidRef);

        // Related - Accession Number (typed identifier per MadoReferencedAccessionNumberIdentifier)
        String accessionNumber = study.getString(Tag.AccessionNumber);
        if (accessionNumber != null && !accessionNumber.isEmpty()) {
            Reference accRef = new Reference();
            Identifier accIdentifier = new Identifier();
            CodeableConcept accType = new CodeableConcept();
            accType.addCoding()
                .setSystem("http://dicom.nema.org/resources/ontology/DCM")
                .setCode("121022")
                .setDisplay("Accession Number");
            accType.addCoding()
                .setSystem("http://terminology.hl7.org/CodeSystem/v2-0203")
                .setCode("ACSN")
                .setDisplay("Accession Id");
            accIdentifier.setType(accType);
            accIdentifier.setSystem("urn:oid:" + config.getAccessionNumberIssuerOid());
            accIdentifier.setValue(accessionNumber);
            accRef.setIdentifier(accIdentifier);
            context.addRelated(accRef);
        }

        docRef.setContext(context);

        return docRef;
    }

    /**
     * Map DICOM study attributes to a FHIR DocumentReference for a DICOM KOS manifest.
     * Implements MADO IG R4 MadoDicomKosDocumentReference profile.
     */
    public static DocumentReference mapStudyToKosDocumentReference(Attributes study, MHDConfiguration config,
                                                                     byte[] kosManifestBytes, String sopInstanceUid) {
        DocumentReference docRef = new DocumentReference();

        String studyUid = study.getString(Tag.StudyInstanceUID);
        String fhirId = "kos-" + createFhirId(studyUid);

        // Set resource ID
        docRef.setId(fhirId);

        // Meta profile - MADO KOS DocumentReference
        Meta meta = new Meta();
        meta.addProfile(PROFILE_KOS_DOCREF);
        docRef.setMeta(meta);

        // Modality extension (R5 cross-version extension) - MADO IG mandates exactly 1 extension with CodeableConcept
        String[] modalities = study.getStrings(Tag.ModalitiesInStudy);
        if (modalities != null && modalities.length > 0) {
            Extension modalityExt = new Extension(EXT_DOCREF_MODALITY);
            CodeableConcept modalityConcept = new CodeableConcept();
            for (String modality : modalities) {
                modalityConcept.addCoding()
                    .setSystem("http://dicom.nema.org/resources/ontology/DCM")
                    .setCode(modality);
            }
            modalityExt.setValue(modalityConcept);
            docRef.addExtension(modalityExt);
        }

        // BodySite extension (R5 cross-version extension) - MADO IG 0..1, MustSupport
        String bodyPartExamined = study.getString(Tag.BodyPartExamined);
        if (bodyPartExamined != null && !bodyPartExamined.isEmpty()) {
            Extension bodySiteExt = new Extension(EXT_DOCREF_BODY_SITE);
            Extension conceptExt = new Extension("concept");
            CodeableConcept bodySiteConcept = new CodeableConcept();
            bodySiteConcept.setText(bodyPartExamined);
            conceptExt.setValue(bodySiteConcept);
            bodySiteExt.addExtension(conceptExt);
            docRef.addExtension(bodySiteExt);
        }

        // Master Identifier - SOP Instance UID of the KOS (MADO IG R4 requirement: UniqueIdIdentifier)
        String kosSopUid = sopInstanceUid != null ? sopInstanceUid : studyUid;
        Identifier masterIdentifier = new Identifier();
        masterIdentifier.setSystem("urn:ietf:rfc:3986");
        masterIdentifier.setValue("urn:oid:" + kosSopUid);
        docRef.setMasterIdentifier(masterIdentifier);

        // Identifier - must include masterIdentifier per mado-docref-1 constraint
        docRef.addIdentifier(masterIdentifier.copy());

        // Document status - current
        docRef.setStatus(Enumerations.DocumentReferenceStatus.CURRENT);

        // Document type - LOINC 18748-4 "Diagnostic imaging Study"
        CodeableConcept type = new CodeableConcept();
        type.addCoding()
            .setSystem("http://loinc.org")
            .setCode("18748-4")
            .setDisplay("Diagnostic imaging Study");
        docRef.setType(type);

        // Category - Medical-Imaging
        CodeableConcept category = new CodeableConcept();
        category.addCoding()
            .setSystem("http://hl7.eu/fhir/eu-health-data-api/CodeSystem/eehrxf-document-priority-category-cs")
            .setCode("Medical-Imaging")
            .setDisplay("Medical-Imaging");
        docRef.addCategory(category);

        // Subject - Patient reference
        String patientId = study.getString(Tag.PatientID);
        if (patientId != null) {
            Reference patientRef = new Reference();
            patientRef.setType("Patient");
            Identifier patientIdentifier = new Identifier();
            patientIdentifier.setSystem("urn:oid:" + config.getPatientIdIssuerOid());
            patientIdentifier.setValue(patientId);
            patientRef.setIdentifier(patientIdentifier);
            String patientName = study.getString(Tag.PatientName, "");
            patientRef.setDisplay(patientName.replace("^", " ").trim());
            docRef.setSubject(patientRef);
        }

        // Date - Study Date/Time
        Date studyDate = parseDicomDateTime(study.getString(Tag.StudyDate), study.getString(Tag.StudyTime));
        if (studyDate != null) {
            docRef.setDate(studyDate);
        }

        // Author - source-device (MADO Creator device)
        Reference deviceRef = createMadoCreatorDeviceReference(config);
        docRef.addAuthor(deviceRef);

        // Author - source-organization (MADO Creator Organization)
        Reference orgRef = createMadoCreatorOrganizationReference(config);
        docRef.addAuthor(orgRef);

        // Description - prefer StudyDescription, fallback to generic
        String kosStudyDescription = study.getString(Tag.StudyDescription);
        if (kosStudyDescription != null && !kosStudyDescription.trim().isEmpty()) {
            docRef.setDescription(kosStudyDescription);
        } else {
            docRef.setDescription("DICOM KOS Imaging Manifest for Imaging Study urn:oid:" + studyUid);
        }

        // Content - KOS Binary attachment (no content.profile extension for KOS - only format)
        DocumentReference.DocumentReferenceContentComponent content = new DocumentReference.DocumentReferenceContentComponent();

        Attachment attachment = new Attachment();
        attachment.setContentType("application/dicom");
        attachment.setLanguage("en");

        // URL to retrieve the KOS binary
        String retrieveUrl = config.getFhirBaseUrl() + "/Binary/" + createFhirId(studyUid) + ".dcm";
        attachment.setUrl(retrieveUrl);
        // Title - prefer StudyDescription, fallback to generic
        String kosStudyDesc = study.getString(Tag.StudyDescription);
        if (kosStudyDesc != null && !kosStudyDesc.trim().isEmpty()) {
            attachment.setTitle(kosStudyDesc);
        } else {
            attachment.setTitle("KOS Imaging Manifest for Imaging Study");
        }
        attachment.setCreation(new Date());

        if (kosManifestBytes != null && kosManifestBytes.length > 0) {
            attachment.setSize(kosManifestBytes.length);
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(kosManifestBytes);
                attachment.setHash(hash);
            } catch (NoSuchAlgorithmException e) {
                // SHA-256 should always be available
            }
        }

        content.setAttachment(attachment);

        // Format code for KOS (patternCoding from IG: DCMUID system with KOS SOP Class UID)
        Coding formatCoding = new Coding();
        formatCoding.setSystem("http://dicom.nema.org/resources/ontology/DCMUID");
        formatCoding.setCode("1.2.840.10008.5.1.4.1.1.88.59");
        formatCoding.setDisplay("Key Object Selection Document");
        content.setFormat(formatCoding);

        docRef.addContent(content);

        // Context
        DocumentReference.DocumentReferenceContextComponent context = new DocumentReference.DocumentReferenceContextComponent();

        // Period (required by MADO IG: context.period min:1, period.start min:1)
        if (studyDate != null) {
            Period period = new Period();
            period.setStart(studyDate);
            context.setPeriod(period);
        }

        // FacilityType (required by MADO IG min:1)
        CodeableConcept facilityType = new CodeableConcept();
        facilityType.addCoding()
            .setSystem("http://snomed.info/sct")
            .setCode("722171005")
            .setDisplay("Diagnostic imaging department");
        context.setFacilityType(facilityType);

        // PracticeSetting (required by MADO IG min:1)
        CodeableConcept practiceSetting = new CodeableConcept();
        practiceSetting.addCoding()
            .setSystem("http://snomed.info/sct")
            .setCode("394914008")
            .setDisplay("Radiology");
        context.setPracticeSetting(practiceSetting);

        // Related - Study Instance UID (per MadoReferencedStudyInstanceUidIdentifier)
        Reference studyUidRef = new Reference();
        Identifier studyUidIdentifier = new Identifier();
        CodeableConcept studyUidType = new CodeableConcept();
        studyUidType.addCoding()
            .setSystem("http://dicom.nema.org/resources/ontology/DCM")
            .setCode("110180")
            .setDisplay("Study Instance UID");
        studyUidIdentifier.setType(studyUidType);
        studyUidIdentifier.setSystem("urn:dicom:uid");
        studyUidIdentifier.setValue("urn:oid:" + studyUid);
        studyUidRef.setIdentifier(studyUidIdentifier);
        context.addRelated(studyUidRef);

        // Related - Accession Number (per MadoReferencedAccessionNumberIdentifier)
        String accessionNumber = study.getString(Tag.AccessionNumber);
        if (accessionNumber != null && !accessionNumber.isEmpty()) {
            Reference accRef = new Reference();
            Identifier accIdentifier = new Identifier();
            CodeableConcept accType = new CodeableConcept();
            accType.addCoding()
                .setSystem("http://dicom.nema.org/resources/ontology/DCM")
                .setCode("121022")
                .setDisplay("Accession Number");
            accType.addCoding()
                .setSystem("http://terminology.hl7.org/CodeSystem/v2-0203")
                .setCode("ACSN")
                .setDisplay("Accession Id");
            accIdentifier.setType(accType);
            accIdentifier.setSystem("urn:oid:" + config.getAccessionNumberIssuerOid());
            accIdentifier.setValue(accessionNumber);
            accRef.setIdentifier(accIdentifier);
            context.addRelated(accRef);
        }

        docRef.setContext(context);

        return docRef;
    }

    /**
     * Create a MADO Creator Device reference per MadoCreator profile.
     * Used as author:source-device in DocumentReferences.
     */
    private static Reference createMadoCreatorDeviceReference(MHDConfiguration config) {
        Reference deviceRef = new Reference();
        deviceRef.setType("Device");
        deviceRef.setDisplay(config.getManufacturerModelName() + " v" + config.getSoftwareVersion());

        // Use logical identifier with MADO device type
        Identifier deviceIdentifier = new Identifier();
        CodeableConcept deviceType = new CodeableConcept();
        deviceType.addCoding()
            .setSystem(MADO_DEVICE_TYPE_SYSTEM)
            .setCode("mado-creator")
            .setDisplay("MADO Creator");
        deviceIdentifier.setType(deviceType);
        deviceIdentifier.setValue(config.getManufacturerModelName());
        deviceRef.setIdentifier(deviceIdentifier);

        return deviceRef;
    }

    /**
     * Create a MADO Creator Organization reference per MadoCreatorOrganization profile.
     * Used as author:source-organization in DocumentReferences.
     */
    private static Reference createMadoCreatorOrganizationReference(MHDConfiguration config) {
        Reference orgRef = new Reference();
        orgRef.setType("Organization");
        orgRef.setDisplay(config.getInstitutionName());
        return orgRef;
    }

    /**
     * Create a FHIR List resource representing the MHD SubmissionSet.
     * Per MADO IG, includes entries for BOTH FHIR and KOS DocumentReferences.
     */
    public static ListResource createSubmissionSetList(Attributes study, MHDConfiguration config,
                                                        DocumentReference fhirDocRef,
                                                        DocumentReference kosDocRef) {
        ListResource list = new ListResource();

        String studyUid = study.getString(Tag.StudyInstanceUID);
        String fhirId = "ss-" + createFhirId(studyUid);

        list.setId(fhirId);

        // Meta profile
        Meta meta = new Meta();
        meta.addProfile("https://profiles.ihe.net/ITI/MHD/StructureDefinition/IHE.MHD.Minimal.SubmissionSet");
        list.setMeta(meta);

        // Identifier - unique submission set ID
        Identifier identifier = new Identifier();
        identifier.setSystem("urn:ietf:rfc:3986");
        identifier.setValue("urn:uuid:" + UUID.randomUUID());
        identifier.setUse(Identifier.IdentifierUse.OFFICIAL);
        list.addIdentifier(identifier);

        // Source ID
        Identifier sourceId = new Identifier();
        sourceId.setSystem("urn:ietf:rfc:3986");
        sourceId.setValue("urn:oid:" + config.getRepositoryUniqueId());
        sourceId.setUse(Identifier.IdentifierUse.USUAL);
        list.addIdentifier(sourceId);

        // Status
        list.setStatus(ListResource.ListStatus.CURRENT);
        list.setMode(ListResource.ListMode.WORKING);

        // Code - submission set
        CodeableConcept code = new CodeableConcept();
        code.addCoding()
            .setSystem("https://profiles.ihe.net/ITI/MHD/CodeSystem/MHDlistTypes")
            .setCode("submissionset")
            .setDisplay("SubmissionSet as a FHIR List");
        list.setCode(code);

        // Subject - same as DocumentReference
        String patientId = study.getString(Tag.PatientID);
        if (patientId != null) {
            Reference patientRef = new Reference();
            patientRef.setType("Patient");
            Identifier patientIdentifier = new Identifier();
            patientIdentifier.setSystem("urn:oid:" + config.getPatientIdIssuerOid());
            patientIdentifier.setValue(patientId);
            patientRef.setIdentifier(patientIdentifier);
            list.setSubject(patientRef);
        }

        // Date
        list.setDate(new Date());

        // Title
        String studyDescription = study.getString(Tag.StudyDescription, "MADO Manifest");
        list.setTitle("SubmissionSet: " + studyDescription);

        // Source - organization that created the submission
        list.setSource(new Reference()
            .setType("Organization")
            .setDisplay(config.getInstitutionName()));

        // Entry - reference to the FHIR DocumentReference
        ListResource.ListEntryComponent fhirEntry = new ListResource.ListEntryComponent();
        fhirEntry.setItem(new Reference("DocumentReference/" + fhirDocRef.getId()));
        list.addEntry(fhirEntry);

        // Entry - reference to the KOS DocumentReference (both formats in the submission set)
        if (kosDocRef != null) {
            ListResource.ListEntryComponent kosEntry = new ListResource.ListEntryComponent();
            kosEntry.setItem(new Reference("DocumentReference/" + kosDocRef.getId()));
            list.addEntry(kosEntry);
        }

        return list;
    }

    /**
     * Backward-compatible overload: Create a submission set with only one DocumentReference.
     * @deprecated Use {@link #createSubmissionSetList(Attributes, MHDConfiguration, DocumentReference, DocumentReference)} instead.
     */
    public static ListResource createSubmissionSetList(Attributes study, MHDConfiguration config,
                                                        DocumentReference docRef) {
        return createSubmissionSetList(study, config, docRef, null);
    }

    /**
     * Create a Binary resource containing the MADO manifest.
     */
    public static Binary createBinaryResource(String studyInstanceUid, byte[] manifestBytes) {
        Binary binary = new Binary();
        binary.setId(createFhirId(studyInstanceUid) + ".dcm");
        binary.setContentType("application/dicom");
        binary.setData(manifestBytes);
        return binary;
    }

    /**
     * Parse DICOM date and time strings into a Java Date.
     */
    private static Date parseDicomDateTime(String date, String time) {
        if (date == null || date.isEmpty()) {
            return null;
        }

        try {
            String dateTimeString = date;
            String formatString = "yyyyMMdd";

            if (time != null && !time.isEmpty()) {
                if (time.contains(".")) {
                    time = time.substring(0, time.indexOf("."));
                }

                dateTimeString += time;

                if (time.length() == 2) {
                    formatString += "HH";
                } else if (time.length() == 4) {
                    formatString += "HHmm";
                } else if (time.length() >= 6) {
                    formatString += "HHmmss";
                    if (time.length() > 6) {
                         dateTimeString = date + time.substring(0, 6);
                    }
                }
            }

            return new SimpleDateFormat(formatString).parse(dateTimeString);
        } catch (ParseException e) {
            return null;
        }
    }

    /**
     * Map DICOM PatientSex to FHIR AdministrativeGender.
     */
    public static Enumerations.AdministrativeGender mapPatientSex(String dicomSex) {
        if (dicomSex == null) {
            return Enumerations.AdministrativeGender.UNKNOWN;
        }
        switch (dicomSex.toUpperCase().trim()) {
            case "M":
                return Enumerations.AdministrativeGender.MALE;
            case "F":
                return Enumerations.AdministrativeGender.FEMALE;
            case "O":
                return Enumerations.AdministrativeGender.OTHER;
            default:
                return Enumerations.AdministrativeGender.UNKNOWN;
        }
    }
}
