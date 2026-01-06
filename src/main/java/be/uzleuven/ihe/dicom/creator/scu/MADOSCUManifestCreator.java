package be.uzleuven.ihe.dicom.creator.scu;

import org.dcm4che3.data.*;

import java.io.File;
import java.io.IOException;

import static be.uzleuven.ihe.dicom.creator.utils.DicomCreatorUtils.*;
import static be.uzleuven.ihe.dicom.constants.CodeConstants.*;
import be.uzleuven.ihe.dicom.creator.utils.ManifestHeaderUtils;
import be.uzleuven.ihe.dicom.creator.utils.ManifestHeaderUtils.HeaderConfig;
import be.uzleuven.ihe.dicom.creator.utils.CommandLineParser;

/**
 * SCU client for creating IHE RAD MADO (Manifest for Advanced Document Organization)
 * manifests from C-FIND query results.
 * This class performs C-FIND queries against a DICOM archive (PACS) and
 * constructs valid MADO manifests compliant with IHE RAD TF-3: 6.X.1 specification,
 * including TID 1600 Image Library content tree.
 * Example usage:
 * <pre>
 * DefaultMetadata defaults = new DefaultMetadata()
 *     .withCallingAET("NICK")
 *     .withCalledAET("ORTHANC")
 *     .withRemoteHost("172.20.240.184")
 *     .withRemotePort(4242)
 *     .withPatientIdIssuerOid("1.2.840.113619.6.197")
 *     .withAccessionNumberIssuerOid("1.2.840.113619.6.197.1")
 *     .withRetrieveLocationUid("1.2.3.4.5.6.7.8.9.10");
 *
 * MADOSCUManifestCreator creator = new MADOSCUManifestCreator(defaults);
 * Attributes madoManifest = creator.createManifest(
 *     "1.3.46.670589.11.0.1.1996082307380006",
 *     "7");
 *
 * creator.saveToFile(madoManifest, new File("MADO_FROM_SCU.dcm"));
 * </pre>
 */
public class MADOSCUManifestCreator extends SCUManifestCreator {

    public MADOSCUManifestCreator() {
        super();
    }

    public MADOSCUManifestCreator(DefaultMetadata defaults) {
        super(defaults);
    }

    /**
     * Creates a MADO manifest by querying the PACS for study/series/instance information.
     *
     * @param studyInstanceUid The Study Instance UID to create a manifest for
     * @param patientId The Patient ID (can be null, will be queried from PACS)
     * @return Attributes object containing the complete MADO manifest
     * @throws IOException if C-FIND queries fail
     */
    @Override
    public Attributes createManifest(String studyInstanceUid, String patientId) throws IOException {
        // Step 1: Query study level to get patient and study metadata
        CFindResult studyResult = findStudy(studyInstanceUid, patientId);

        if (!studyResult.isSuccess() || studyResult.getMatches().isEmpty()) {
            throw new IOException("Study not found: " + studyInstanceUid +
                ". Error: " + studyResult.getErrorMessage());
        }

        Attributes studyAttrs = studyResult.getMatches().get(0);
        applyDefaults(studyAttrs);

        // Step 2: Query series level to get all series in the study
        CFindResult seriesResult = findSeries(studyInstanceUid, null);

        if (!seriesResult.isSuccess() || seriesResult.getMatches().isEmpty()) {
            throw new IOException("No series found for study: " + studyInstanceUid);
        }

        // Step 3: For each series, query instances
        java.util.List<SeriesData> allSeries = new java.util.ArrayList<>();

        for (Attributes seriesAttrs : seriesResult.getMatches()) {
            String seriesInstanceUid = seriesAttrs.getString(Tag.SeriesInstanceUID);

            CFindResult instanceResult = findInstances(studyInstanceUid, seriesInstanceUid);

            if (instanceResult.isSuccess() && !instanceResult.getMatches().isEmpty()) {
                SeriesData sd = new SeriesData();
                sd.seriesAttrs = seriesAttrs;
                sd.instances = instanceResult.getMatches();
                allSeries.add(sd);
            }
        }

        if (allSeries.isEmpty()) {
            throw new IOException("No instances found for study: " + studyInstanceUid);
        }

        // Step 4: Build the MADO manifest
        return buildMADOManifest(studyAttrs, allSeries);
    }


    /**
     * Builds the complete MADO manifest from queried data.
     * Implements IHE RAD MADO profile with TID 1600 Image Library.
     */
    private Attributes buildMADOManifest(Attributes studyAttrs, java.util.List<SeriesData> allSeries) {
        Attributes mado = new Attributes();

        // Build header configuration using builder
        MADOHeaderConfigBuilder headerBuilder = new MADOHeaderConfigBuilder(studyAttrs, defaults);
        HeaderConfig config = headerBuilder.buildHeaderConfig();

        String normalizedStudyInstanceUID = headerBuilder.getNormalizedStudyInstanceUID();
        String accessionNumber = headerBuilder.getAccessionNumber();
        String studyDate = studyAttrs.getString(Tag.StudyDate, "");
        String studyTime = studyAttrs.getString(Tag.StudyTime, "");

        // Populate all common modules using utility
        ManifestHeaderUtils.populateSOPCommonModule(mado, config);
        ManifestHeaderUtils.populatePatientModule(mado, config);
        ManifestHeaderUtils.populateStudyModule(mado, config);
        ManifestHeaderUtils.populateSeriesModule(mado, config);
        ManifestHeaderUtils.populateEquipmentModule(mado, config);
        ManifestHeaderUtils.populateSRDocumentModule(mado, config);

        // SR root content item attributes
        configureSRRootAttributes(mado);

        // Evidence Sequence
        // NOTE: dcm4che does not allow the same Attributes item to be contained by multiple Sequences.
        // The previous implementation built the evidence sequence on a temporary Attributes, then
        // addAll()'d the items into the real dataset, which can trigger:
        //   "Item already contained by Sequence".
        // Build directly on the target dataset instead.
        MADOEvidenceBuilder evidenceBuilder = new MADOEvidenceBuilder(defaults, normalizedStudyInstanceUID, allSeries);
        evidenceBuilder.populateEvidenceSequence(mado);

        // ReferencedRequestSequence
        buildReferencedRequestSequence(mado, accessionNumber, normalizedStudyInstanceUID);

        // SR Document Content Module with TID 1600 Image Library
        MADOContentBuilder contentBuilder = new MADOContentBuilder(defaults, normalizedStudyInstanceUID,
            studyDate, studyTime, allSeries);
        contentBuilder.populateContentSequence(mado);

        return mado;
    }

    private void configureSRRootAttributes(Attributes mado) {
        mado.setString(Tag.ValueType, VR.CS, "CONTAINER");
        mado.setString(Tag.ContinuityOfContent, VR.CS, be.uzleuven.ihe.dicom.constants.DicomConstants.CONTINUITY_SEPARATE);

        // Document Title: MADO requires Manifest or Manifest with Description
        Sequence conceptNameCodeSeq = mado.newSequence(Tag.ConceptNameCodeSequence, 1);
        conceptNameCodeSeq.add(code(CODE_MANIFEST_WITH_DESCRIPTION, SCHEME_DCM, MEANING_MANIFEST_WITH_DESCRIPTION));

        // Explicitly identify TID 2010 (XDS-I / KOS template)
        mado.newSequence(Tag.ContentTemplateSequence, 1)
            .add(createTemplateItem("2010"));

        // MADO Preliminary Flag (recommended)
        mado.setString(Tag.PreliminaryFlag, VR.CS, "FINAL");
    }

    private void buildReferencedRequestSequence(Attributes mado, String accessionNumber,
                                                 String normalizedStudyInstanceUID) {
        Sequence refRequestSeq = mado.newSequence(Tag.ReferencedRequestSequence, 1);
        Attributes reqItem = new Attributes();

        // Always include AccessionNumber + issuer (Type R+ in MADO)
        reqItem.setString(Tag.AccessionNumber, VR.SH, accessionNumber);

        Sequence issuerAccSeq = reqItem.newSequence(Tag.IssuerOfAccessionNumberSequence, 1);
        Attributes issuerAcc = new Attributes();
        issuerAcc.setString(Tag.UniversalEntityID, VR.UT, defaults.accessionNumberIssuerOid);
        issuerAcc.setString(Tag.UniversalEntityIDType, VR.CS, "ISO");
        issuerAccSeq.add(issuerAcc);

        reqItem.setString(Tag.StudyInstanceUID, VR.UI, normalizedStudyInstanceUID);
        reqItem.setString(Tag.RequestedProcedureID, VR.SH, "RP001");
        reqItem.setString(Tag.PlacerOrderNumberImagingServiceRequest, VR.LO, "PO001");
        reqItem.setString(Tag.FillerOrderNumberImagingServiceRequest, VR.LO, "FO001");

        refRequestSeq.add(reqItem);
    }

    /**
     * Saves the manifest to a DICOM file.
     */
    public void saveToFile(Attributes manifest, File outputFile) throws IOException {
        writeDicomFile(outputFile, manifest);
    }

    /**
     * Main method for command-line usage.
     * Example: java MADOSCUManifestCreator -aec ORTHANC -aet NICK -host 172.20.240.184 -port 4242
     *          -study 1.3.46.670589.11.0.1.1996082307380006 -pid 7 -out MADO_SCU.dcm
     */
    public static void main(String[] args) {
        try {
            CommandLineParser.ParsedArgs parsed = CommandLineParser.parseArgs(args, "MADO_FROM_SCU.dcm");

            if (parsed.isInvalid()) {
                System.err.println(parsed.getUsageMessage("MADOSCUManifestCreator"));
                System.exit(1);
            }

            CommandLineParser.printStartupInfo(parsed, "MADO");

            MADOSCUManifestCreator creator = new MADOSCUManifestCreator(parsed.defaults);
            Attributes mado = creator.createManifest(parsed.studyUID, parsed.patientID);

            creator.saveToFile(mado, new File(parsed.outputFile));

            CommandLineParser.printSuccessInfo(parsed.outputFile, mado.getString(Tag.SOPInstanceUID), "MADO");

        } catch (Exception e) {
            CommandLineParser.printErrorAndExit("Error creating MADO manifest: " + e.getMessage(), e);
        }
    }
}
