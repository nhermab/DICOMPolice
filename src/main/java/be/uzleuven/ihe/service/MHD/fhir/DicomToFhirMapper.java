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
 * Implements IHE MHD Document Responder mappings for MADO manifests.
 */
public class DicomToFhirMapper {

    private static final String STUDY_UID_SYSTEM = "urn:dicom:uid";
    private static final String ACCESSION_NUMBER_SYSTEM = "urn:oid:1.2.840.10008.2.16.4"; // DICOM Accession Number

    /**
     * Create a stable FHIR ID from a Study Instance UID.
     * Uses Base64 URL-safe encoding.
     */
    public static String createFhirId(String studyInstanceUid) {
        if (studyInstanceUid == null) {
            return UUID.randomUUID().toString();
        }
        // Create a URL-safe base64 encoding of the UID
        String base64 = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(studyInstanceUid.getBytes(StandardCharsets.UTF_8));

        // FHIR IDs allow [A-Za-z0-9\-\.]
        // Base64 URL uses '-' and '_'
        // Replace '_' with '.' to be FHIR compliant
        return base64.replace('_', '.');
    }

    /**
     * Decode a FHIR ID back to Study Instance UID.
     */
    public static String decodeStudyUidFromFhirId(String fhirId) {
        if (fhirId == null) return null;

        // Tolerate a trailing .dcm extension on IDs
        if (fhirId.toLowerCase().endsWith(".dcm")) {
            fhirId = fhirId.substring(0, fhirId.length() - 4);
        }

        try {
            // Revert the replacement
            String base64 = fhirId.replace('.', '_');
            byte[] decoded = Base64.getUrlDecoder().decode(base64);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // Not a valid base64, might be the raw UID
            return fhirId;
        }
    }

    /**
     * Map DICOM study attributes to a FHIR DocumentReference.
     * Implements ITI-67 (Find Document References) response mapping.
     */
    public static DocumentReference mapStudyToDocumentReference(Attributes study, MHDConfiguration config,
                                                                  byte[] manifestBytes) {
        DocumentReference docRef = new DocumentReference();

        String studyUid = study.getString(Tag.StudyInstanceUID);
        String fhirId = createFhirId(studyUid);

        // Set resource ID
        docRef.setId(fhirId);

        // Meta profile - MHD Minimal DocumentReference (Facade mode)
        Meta meta = new Meta();
        meta.addProfile("https://profiles.ihe.net/ITI/MHD/StructureDefinition/IHE.MHD.Minimal.DocumentReference");
        docRef.setMeta(meta);

        // Master Identifier - Study Instance UID (required for MADO)
        Identifier masterIdentifier = new Identifier();
        masterIdentifier.setSystem(STUDY_UID_SYSTEM);
        masterIdentifier.setValue(studyUid);
        docRef.setMasterIdentifier(masterIdentifier);

        // Document status - current
        docRef.setStatus(Enumerations.DocumentReferenceStatus.CURRENT);

        // Document type - imaging procedure
        CodeableConcept type = new CodeableConcept();
        type.addCoding()
            .setSystem(config.getTypeCodeSystem())
            .setCode(config.getTypeCode())
            .setDisplay("Imaging study manifest");
        docRef.setType(type);

        // Category/Class
        CodeableConcept category = new CodeableConcept();
        category.addCoding()
            .setSystem(config.getClassCodeSystem())
            .setCode(config.getClassCode())
            .setDisplay("Imaging");
        docRef.addCategory(category);

        // Subject - Patient reference
        String patientId = study.getString(Tag.PatientID);
        String issuer = study.getString(Tag.IssuerOfPatientID, config.getPatientIdIssuerLocalNamespace());
        if (patientId != null) {
            Reference patientRef = new Reference();
            patientRef.setType("Patient");
            // Use logical identifier for patient
            Identifier patientIdentifier = new Identifier();
            patientIdentifier.setSystem("urn:oid:" + config.getPatientIdIssuerOid());
            patientIdentifier.setValue(patientId);
            patientRef.setIdentifier(patientIdentifier);
            patientRef.setDisplay(study.getString(Tag.PatientName, ""));
            docRef.setSubject(patientRef);
        }

        // Date - Study Date/Time
        Date studyDate = parseDicomDateTime(study.getString(Tag.StudyDate), study.getString(Tag.StudyTime));
        if (studyDate != null) {
            docRef.setDate(studyDate);
        }

        // Author (if available)
        String authorName = study.getString(Tag.ReferringPhysicianName);
        if (authorName == null || authorName.isEmpty()) {
            authorName = study.getString(Tag.PerformingPhysicianName);
        }

        if (authorName == null || authorName.isEmpty()) {
            // Placeholder if no author found
            authorName = "Unknown Author";
        }

        Reference authorRef = new Reference();
        authorRef.setType("Practitioner");
        authorRef.setDisplay(authorName.replace("^", " "));
        docRef.addAuthor(authorRef);

        // Description
        String studyDescription = study.getString(Tag.StudyDescription);
        if (studyDescription != null && !studyDescription.isEmpty()) {
            docRef.setDescription(studyDescription);
        }

        // Security label (basic confidentiality)
        docRef.addSecurityLabel()
            .addCoding()
            .setSystem("http://terminology.hl7.org/CodeSystem/v3-Confidentiality")
            .setCode("N")
            .setDisplay("Normal");

        // Content - attachment with retrieval URL
        DocumentReference.DocumentReferenceContentComponent content = new DocumentReference.DocumentReferenceContentComponent();

        Attachment attachment = new Attachment();
        attachment.setContentType("application/dicom");
        attachment.setLanguage("en");

        // URL to retrieve the MADO manifest (ITI-68)
        // Append .dcm to the Binary resource id so clients can infer filename from URL without extra headers
        String retrieveUrl = config.getFhirBaseUrl() + "/Binary/" + fhirId + ".dcm";
        attachment.setUrl(retrieveUrl);

        // Title
        attachment.setTitle("MADO Manifest - " + (studyDescription != null ? studyDescription : studyUid));

        // Creation time
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

        // Format code for MADO
        Coding formatCoding = new Coding();
        formatCoding.setSystem(config.getFormatCodeSystem());
        formatCoding.setCode(config.getFormatCode());
        formatCoding.setDisplay("Manifest for Access to DICOM Objects");
        content.setFormat(formatCoding);

        docRef.addContent(content);

        // Context - including accession number and facility
        DocumentReference.DocumentReferenceContextComponent context = new DocumentReference.DocumentReferenceContextComponent();

        String accessionNumber = study.getString(Tag.AccessionNumber);
        if (accessionNumber != null && !accessionNumber.isEmpty()) {
            Reference eventRef = new Reference();
            eventRef.setType("ServiceRequest");
            Identifier accIdentifier = new Identifier();
            accIdentifier.setSystem("urn:oid:" + config.getAccessionNumberIssuerOid());
            accIdentifier.setValue(accessionNumber);
            eventRef.setIdentifier(accIdentifier);
            context.addRelated(eventRef);
        }

        // Facility type
        CodeableConcept facilityType = new CodeableConcept();
        facilityType.addCoding()
            .setSystem("http://snomed.info/sct")
            .setCode("22232009")
            .setDisplay("Hospital");
        context.setFacilityType(facilityType);

        // Modality in context.event
        String[] modalities = study.getStrings(Tag.ModalitiesInStudy);
        if (modalities != null && modalities.length > 0) {
            for (String modality : modalities) {
                CodeableConcept event = new CodeableConcept();
                event.addCoding()
                        .setSystem("urn:oid:1.2.840.10008.2.11.1")
                        .setCode(modality)
                        .setDisplay(modality);
                context.addEvent(event);
            }
        } else {
            // Default to OT if missing
            CodeableConcept event = new CodeableConcept();
            event.addCoding()
                    .setSystem("urn:oid:1.2.840.10008.2.11.1")
                    .setCode("OT")
                    .setDisplay("Other");
            context.addEvent(event);
        }

        docRef.setContext(context);

        return docRef;
    }

    /**
     * Create a FHIR List resource representing the MHD SubmissionSet.
     */
    public static ListResource createSubmissionSetList(Attributes study, MHDConfiguration config,
                                                        DocumentReference docRef) {
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
        identifier.setValue("urn:uuid:" + UUID.randomUUID().toString());
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

        // Entry - reference to the DocumentReference
        ListResource.ListEntryComponent entry = new ListResource.ListEntryComponent();
        entry.setItem(new Reference("DocumentReference/" + docRef.getId()));
        list.addEntry(entry);

        return list;
    }

    /**
     * Create a Binary resource containing the MADO manifest.
     */
    public static Binary createBinaryResource(String studyInstanceUid, byte[] manifestBytes) {
        Binary binary = new Binary();

        // Append .dcm to the Binary id so clients that infer filename from URL will receive an id with extension
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
                // Remove fractional seconds if present (e.g. 101530.500 -> 101530)
                if (time.contains(".")) {
                    time = time.substring(0, time.indexOf("."));
                }

                dateTimeString += time;

                // Construct format string based on length
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

