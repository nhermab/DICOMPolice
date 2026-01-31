package be.uzleuven.ihe.dicom.creator.utils;

import org.dcm4che3.data.*;

import java.util.List;

import static be.uzleuven.ihe.dicom.creator.utils.DicomCreatorUtils.randomInt;
import static be.uzleuven.ihe.dicom.creator.utils.DicomCreatorUtils.createNormalizedUid;

/**
 * Utility methods for populating DICOM sequences (Evidence, Referenced Request, etc.).
 */
public class DicomSequenceUtils {

    /**
     * Populates ReferencedRequestSequence with standard attributes.
     */
    public static void populateReferencedRequestSequence(Attributes dataset, String studyInstanceUID,
                                                         String accessionNumber) {
        Sequence rrs = dataset.newSequence(Tag.ReferencedRequestSequence, 1);
        Attributes item = new Attributes();
        item.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUID);
        item.setString(Tag.AccessionNumber, VR.SH, accessionNumber);
        item.setString(Tag.PlacerOrderNumberImagingServiceRequest, VR.LO,
                      "PLC" + (100000 + randomInt(900000)));
        item.setString(Tag.FillerOrderNumberImagingServiceRequest, VR.LO,
                      "FIL" + (100000 + randomInt(900000)));
        rrs.add(item);
    }

    /**
     * Populates ReferencedRequestSequence with issuer information.
     */
    public static void populateReferencedRequestSequenceWithIssuer(Attributes dataset, String studyInstanceUID,
                                                                   String accessionNumber, String issuerOID) {
        Sequence rrs = dataset.newSequence(Tag.ReferencedRequestSequence, 1);
        Attributes item = new Attributes();
        item.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUID);
        item.setString(Tag.AccessionNumber, VR.SH, accessionNumber);

        // Add issuer
        addAccessionNumberIssuer(item, issuerOID);

        item.setString(Tag.PlacerOrderNumberImagingServiceRequest, VR.LO,
                      "PLACER-ORDER-" + (1000 + randomInt(9000)));
        rrs.add(item);
    }

    /**
     * Populates CurrentRequestedProcedureEvidenceSequence from a list of referenced SOPs.
     */
    public static void populateEvidence(Attributes dataset, String studyInstanceUID,
                                       List<Attributes> referencedSops) {
        populateEvidence(dataset, studyInstanceUID, referencedSops, null);
    }

    /**
     * Populates CurrentRequestedProcedureEvidenceSequence with a single series.
     */
    public static void populateEvidence(Attributes dataset, String studyInstanceUID,
                                       List<Attributes> referencedSops, String seriesInstanceUID) {
        Sequence evidence = dataset.newSequence(Tag.CurrentRequestedProcedureEvidenceSequence, 1);

        Attributes studyItem = new Attributes();
        studyItem.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUID);

        Sequence refSeries = studyItem.newSequence(Tag.ReferencedSeriesSequence, 1);
        Attributes seriesItem = new Attributes();
        String actualSeriesUID = seriesInstanceUID != null ? seriesInstanceUID : createNormalizedUid();
        seriesItem.setString(Tag.SeriesInstanceUID, VR.UI, actualSeriesUID);

        // Per IHE XDS-I.b DICOM Retrieve by WADO-RS Option:
        // Retrieve URL (0008,1190) provides direct WADO-RS endpoint for web-based retrieval
        String wadoRsUrl = String.format("https://pacs.example.org/dicom-web/studies/%s/series/%s",
                studyInstanceUID, actualSeriesUID);
        seriesItem.setString(Tag.RetrieveURL, VR.UR, wadoRsUrl.trim());

        Sequence refSops = seriesItem.newSequence(Tag.ReferencedSOPSequence,
                                                  Math.max(1, referencedSops.size()));
        for (Attributes sop : referencedSops) {
            Attributes ref = new Attributes();
            ref.setString(Tag.ReferencedSOPClassUID, VR.UI, sop.getString(Tag.ReferencedSOPClassUID));
            ref.setString(Tag.ReferencedSOPInstanceUID, VR.UI, sop.getString(Tag.ReferencedSOPInstanceUID));
            refSops.add(ref);
        }

        refSeries.add(seriesItem);
        evidence.add(studyItem);
    }

    /**
     * Adds Patient ID Qualifiers sequence with OID.
     */
    public static void addPatientIDQualifiers(Attributes dataset, String oid) {
        Sequence pidQualifiers = dataset.newSequence(Tag.IssuerOfPatientIDQualifiersSequence, 1);
        Attributes pidQual = new Attributes();
        pidQual.setString(Tag.UniversalEntityID, VR.UT, oid);
        pidQual.setString(Tag.UniversalEntityIDType, VR.CS, "ISO");
        pidQualifiers.add(pidQual);
    }

    /**
     * Adds Issuer of Accession Number sequence with OID.
     */
    public static void addAccessionNumberIssuer(Attributes dataset, String oid) {
        Sequence issuerAccSeq = dataset.newSequence(Tag.IssuerOfAccessionNumberSequence, 1);
        Attributes issuerAcc = new Attributes();
        issuerAcc.setString(Tag.UniversalEntityID, VR.UT, oid);
        issuerAcc.setString(Tag.UniversalEntityIDType, VR.CS, "ISO");
        issuerAccSeq.add(issuerAcc);
    }
}
