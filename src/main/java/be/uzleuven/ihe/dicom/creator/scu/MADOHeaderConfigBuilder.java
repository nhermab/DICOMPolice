package be.uzleuven.ihe.dicom.creator.scu;

import be.uzleuven.ihe.dicom.creator.utils.ManifestHeaderUtils.HeaderConfig;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

import static be.uzleuven.ihe.dicom.creator.utils.DicomCreatorUtils.*;

/**
 * Builder for MADO manifest header configuration.
 */
class MADOHeaderConfigBuilder {

    private final Attributes studyAttrs;
    private final DefaultMetadata defaults;
    private final String normalizedStudyInstanceUID;

    MADOHeaderConfigBuilder(Attributes studyAttrs, DefaultMetadata defaults) {
        this.studyAttrs = studyAttrs;
        this.defaults = defaults;
        this.normalizedStudyInstanceUID = normalizeUidNoLeadingZeros(
            studyAttrs.getString(Tag.StudyInstanceUID));
    }

    /**
     * Builds the header configuration for MADO manifest.
     */
    HeaderConfig buildHeaderConfig() {
        HeaderConfig config = new HeaderConfig();

        // SOP Common Module
        config.sopClassUID = UID.KeyObjectSelectionDocumentStorage;
        config.sopInstanceUID = createNormalizedUid();
        config.specificCharacterSet = "ISO_IR 192"; // UTF-8

        // Patient Module
        configurePatientModule(config);

        // Study Module
        configureStudyModule(config);

        // Series Module
        configureSeriesModule(config);

        // Equipment Module
        configureEquipmentModule(config);

        // SR Document Module
        configureSRDocumentModule(config);

        return config;
    }

    private void configurePatientModule(HeaderConfig config) {
        config.patientID = studyAttrs.getString(Tag.PatientID, "UNKNOWN");
        config.patientName = studyAttrs.getString(Tag.PatientName, "UNKNOWN^PATIENT");
        config.patientBirthDate = studyAttrs.getString(Tag.PatientBirthDate, "");
        config.patientSex = studyAttrs.getString(Tag.PatientSex, "O");
        config.issuerOfPatientID = studyAttrs.getString(Tag.IssuerOfPatientID,
            defaults.patientIdIssuerLocalNamespace);
        config.patientIdIssuerOid = defaults.patientIdIssuerOid;
        config.includeTypeOfPatientID = true; // MADO-specific
    }

    private void configureStudyModule(HeaderConfig config) {
        config.studyInstanceUID = normalizedStudyInstanceUID;
        config.studyDate = studyAttrs.getString(Tag.StudyDate, "");
        config.studyTime = studyAttrs.getString(Tag.StudyTime, "");
        config.studyID = studyAttrs.getString(Tag.StudyID, "1");
        config.studyDescription = studyAttrs.getString(Tag.StudyDescription, "");
        config.referringPhysicianName = studyAttrs.getString(Tag.ReferringPhysicianName, "");

        // MADO requires AccessionNumber to be present (Type R+)
        config.accessionNumber = ensureAccessionNumber(studyAttrs);
        config.accessionNumberIssuerOid = defaults.accessionNumberIssuerOid;
    }

    private void configureSeriesModule(HeaderConfig config) {
        config.seriesInstanceUID = createNormalizedUid();
        config.seriesNumber = 1;
        config.modality = "KO";
        config.seriesDate = studyAttrs.getString(Tag.StudyDate, "");
        config.seriesTime = studyAttrs.getString(Tag.StudyTime, "");
        config.seriesDescription = "MADO Manifest";
    }

    private void configureEquipmentModule(HeaderConfig config) {
        config.manufacturer = "DICOMPolice";
        config.manufacturerModelName = "MADO SCU Creator";
        config.softwareVersions = "1.0";
        config.institutionName = defaults.institutionName;
    }

    private void configureSRDocumentModule(HeaderConfig config) {
        config.instanceNumber = 1;
        config.contentDate = new SimpleDateFormat("yyyyMMdd").format(new Date());
        config.contentTime = new SimpleDateFormat("HHmmss.SSSSSS").format(new Date());
        config.timezoneOffset = timezoneOffsetFromUTC();
    }

    private String ensureAccessionNumber(Attributes attrs) {
        String accessionNumber = attrs.getString(Tag.AccessionNumber, "");
        if (accessionNumber == null || accessionNumber.trim().isEmpty()) {
            accessionNumber = "ACC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
        return accessionNumber;
    }

    String getNormalizedStudyInstanceUID() {
        return normalizedStudyInstanceUID;
    }

    String getAccessionNumber() {
        return ensureAccessionNumber(studyAttrs);
    }
}

