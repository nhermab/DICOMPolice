package be.uzleuven.ihe.dicom.creator.kin;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.util.UIDUtils;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

import static be.uzleuven.ihe.dicom.constants.CodeConstants.CODE_IMAGE_LIBRARY;
import static be.uzleuven.ihe.dicom.constants.CodeConstants.CODE_IMAGE_LIBRARY_GROUP;
import static be.uzleuven.ihe.dicom.constants.CodeConstants.CODE_INSTANCE_NUMBER;
import static be.uzleuven.ihe.dicom.constants.CodeConstants.CODE_KOS_DESCRIPTION;
import static be.uzleuven.ihe.dicom.constants.CodeConstants.CODE_KOS_DOCUMENT_TITLE;
import static be.uzleuven.ihe.dicom.constants.CodeConstants.CODE_MANIFEST_WITH_DESCRIPTION;
import static be.uzleuven.ihe.dicom.constants.CodeConstants.CODE_MODALITY;
import static be.uzleuven.ihe.dicom.constants.CodeConstants.CODE_NUM_SERIES_RELATED_INSTANCES;
import static be.uzleuven.ihe.dicom.constants.CodeConstants.CODE_NUM_STUDY_RELATED_SERIES;
import static be.uzleuven.ihe.dicom.constants.CodeConstants.CODE_OF_INTEREST;
import static be.uzleuven.ihe.dicom.constants.CodeConstants.CODE_SERIES_INSTANCE_UID;
import static be.uzleuven.ihe.dicom.constants.CodeConstants.CODE_SERIES_NUMBER;
import static be.uzleuven.ihe.dicom.constants.CodeConstants.SCHEME_DCM;
import static be.uzleuven.ihe.dicom.creator.utils.DicomCreatorUtils.code;
import static be.uzleuven.ihe.dicom.creator.utils.SRContentItemUtils.createCodeItem;
import static be.uzleuven.ihe.dicom.creator.utils.SRContentItemUtils.createNumericItem;
import static be.uzleuven.ihe.dicom.creator.utils.SRContentItemUtils.createTextItem;
import static be.uzleuven.ihe.dicom.creator.utils.SRContentItemUtils.createUIDRefItem;

/**
 * Creates a synthetic Key Image Note (KIN) and a revised MADO that references it.
 *
 * <p>The KIN is a separate Key Object Selection Document Storage SOP Instance. It is
 * not embedded in the MADO. The revised MADO references it in the evidence hierarchy,
 * as a direct TID 2010 COMPOSITE item, and as a TID 1601 COMPOSITE Image Library entry
 * carrying the TID 1609 document title and optional description.</p>
 */
public final class MadoKinUpgrader {

    public static final String DEFAULT_DESCRIPTION = "Automatically selected synthetic key image";
    public static final String DEFAULT_KIN_SERIES_NUMBER = "9001";
    public static final String DEFAULT_KIN_INSTANCE_NUMBER = "1";

    private static final String VALUE_TYPE_CONTAINER = "CONTAINER";
    private static final String VALUE_TYPE_IMAGE = "IMAGE";
    private static final String VALUE_TYPE_COMPOSITE = "COMPOSITE";
    private static final String REL_CONTAINS = "CONTAINS";
    private static final String REL_HAS_ACQ_CONTEXT = "HAS ACQ CONTEXT";

    private MadoKinUpgrader() {
    }

    public record SopReference(String studyInstanceUID,
                               String seriesInstanceUID,
                               String sopClassUID,
                               String sopInstanceUID,
                               Attributes evidenceSeriesItem) {
    }

    public record UpgradeResult(Attributes kin, Attributes revisedMado, SopReference selectedImage) {
    }

    public record Options(long randomSeed,
                          String description,
                          String kinSeriesNumber,
                          String kinInstanceNumber,
                          String retrieveLocationUID,
                          String retrieveURL) {

        public static Options defaults() {
            return new Options(12345L, DEFAULT_DESCRIPTION, DEFAULT_KIN_SERIES_NUMBER,
                    DEFAULT_KIN_INSTANCE_NUMBER, null, null);
        }
    }

    /** Creates both output datasets without mutating {@code originalMado}. */
    public static UpgradeResult upgrade(Attributes originalMado, Options options) {
        Objects.requireNonNull(originalMado, "originalMado");
        options = options == null ? Options.defaults() : options;
        validateMado(originalMado);

        SopReference selected = selectRandomImage(originalMado, new Random(options.randomSeed()));
        String retrieveLocationUID = firstNonBlank(options.retrieveLocationUID(),
                selected.evidenceSeriesItem().getString(Tag.RetrieveLocationUID));
        String retrieveURL = firstNonBlank(options.retrieveURL(),
                selected.evidenceSeriesItem().getString(Tag.RetrieveURL));

        if (retrieveLocationUID == null) {
            throw new IllegalArgumentException("The selected image series has no Retrieve Location UID; "
                    + "supply one explicitly for the Imaging Document Source that will store the KIN");
        }

        Attributes kin = createKin(originalMado, selected, options.description(),
                options.kinSeriesNumber(), options.kinInstanceNumber());
        Attributes revised = createRevisedMado(originalMado, kin, retrieveLocationUID,
                retrieveURL, options.description());
        return new UpgradeResult(kin, revised, selected);
    }

    public static SopReference selectRandomImage(Attributes mado, Random random) {
        Objects.requireNonNull(random, "random");
        Map<String, SopReference> unique = new LinkedHashMap<>();
        collectImageReferences(mado, mado.getSequence(Tag.ContentSequence), unique);
        if (unique.isEmpty()) {
            throw new IllegalArgumentException("MADO contains no IMAGE reference that is also present in evidence");
        }
        List<SopReference> candidates = new ArrayList<>(unique.values());
        return candidates.get(random.nextInt(candidates.size()));
    }

    public static Attributes createKin(Attributes originalMado, SopReference image,
                                       String description, String seriesNumber, String instanceNumber) {
        Attributes kin = new Attributes(originalMado);

        kin.setString(Tag.SOPClassUID, VR.UI, UID.KeyObjectSelectionDocumentStorage);
        kin.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
        kin.setString(Tag.Modality, VR.CS, "KO");
        kin.setString(Tag.SeriesInstanceUID, VR.UI, UIDUtils.createUID());
        kin.setString(Tag.SeriesNumber, VR.IS, requireNonBlank(seriesNumber, "KIN series number"));
        kin.setString(Tag.InstanceNumber, VR.IS, requireNonBlank(instanceNumber, "KIN instance number"));
        kin.remove(Tag.SeriesDate);
        kin.remove(Tag.SeriesTime);
        kin.remove(Tag.SeriesDescription);
        kin.remove(Tag.SeriesDescriptionCodeSequence);
        kin.remove(Tag.ProtocolName);
        kin.remove(Tag.IdenticalDocumentsSequence);

        if (!kin.contains(Tag.ReferencedPerformedProcedureStepSequence)) {
            kin.newSequence(Tag.ReferencedPerformedProcedureStepSequence, 0);
        }
        stampCreationTime(kin);

        kin.setString(Tag.ValueType, VR.CS, VALUE_TYPE_CONTAINER);
        kin.setString(Tag.ContinuityOfContent, VR.CS, "SEPARATE");
        setSingleCode(kin, Tag.ConceptNameCodeSequence,
                code(CODE_OF_INTEREST, SCHEME_DCM, "Of Interest"));
        kin.remove(Tag.ContentTemplateSequence);
        Attributes template = new Attributes();
        template.setString(Tag.MappingResource, VR.CS, "DCMR");
        template.setString(Tag.TemplateIdentifier, VR.CS, "2010");
        kin.newSequence(Tag.ContentTemplateSequence, 1).add(template);

        setKinEvidence(kin, image);
        kin.remove(Tag.ContentSequence);
        Sequence content = kin.newSequence(Tag.ContentSequence, 2);
        if (!isBlank(description)) {
            content.add(createTextItem(REL_CONTAINS, CODE_KOS_DESCRIPTION, SCHEME_DCM,
                    "Key Object Description", description));
        }
        content.add(createSopReferenceItem(REL_CONTAINS, VALUE_TYPE_IMAGE,
                image.sopClassUID(), image.sopInstanceUID()));
        return kin;
    }

    public static Attributes createRevisedMado(Attributes originalMado, Attributes kin,
                                                String retrieveLocationUID, String retrieveURL,
                                                String description) {
        Attributes revised = new Attributes(originalMado);
        revised.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
        int oldInstanceNumber = revised.getInt(Tag.InstanceNumber, 0);
        revised.setString(Tag.InstanceNumber, VR.IS, Integer.toString(oldInstanceNumber + 1));
        stampCreationTime(revised);

        String studyUID = requireNonBlank(kin.getString(Tag.StudyInstanceUID), "KIN Study Instance UID");
        String seriesUID = requireNonBlank(kin.getString(Tag.SeriesInstanceUID), "KIN Series Instance UID");
        String sopUID = requireNonBlank(kin.getString(Tag.SOPInstanceUID), "KIN SOP Instance UID");
        String seriesNumber = requireNonBlank(kin.getString(Tag.SeriesNumber), "KIN Series Number");
        String instanceNumber = requireNonBlank(kin.getString(Tag.InstanceNumber), "KIN Instance Number");

        addKinToEvidence(revised, studyUID, seriesUID, sopUID,
                requireNonBlank(retrieveLocationUID, "Retrieve Location UID"), retrieveURL);

        Sequence rootContent = revised.getSequence(Tag.ContentSequence);
        if (rootContent == null) {
            throw new IllegalArgumentException("MADO has no root Content Sequence");
        }
        Attributes rootKinReference = createSopReferenceItem(REL_CONTAINS, VALUE_TYPE_COMPOSITE,
                UID.KeyObjectSelectionDocumentStorage, sopUID);
        int imageLibraryIndex = directChildIndexByConcept(rootContent, CODE_IMAGE_LIBRARY);
        if (imageLibraryIndex >= 0) {
            rootContent.add(imageLibraryIndex, rootKinReference);
        } else {
            rootContent.add(rootKinReference);
        }

        Attributes imageLibrary = findDirectChildByConcept(revised, CODE_IMAGE_LIBRARY);
        if (imageLibrary == null) {
            throw new IllegalArgumentException("MADO has no direct TID 1600 Image Library container");
        }
        Sequence libraryContent = imageLibrary.getSequence(Tag.ContentSequence);
        if (libraryContent == null) {
            libraryContent = imageLibrary.newSequence(Tag.ContentSequence, 8);
        }
        addKoModalityIfMissing(libraryContent);
        libraryContent.add(createKoImageLibraryGroup(seriesUID, sopUID, seriesNumber,
                instanceNumber, description));
        setNumericChild(imageLibrary, CODE_NUM_STUDY_RELATED_SERIES,
                "Number of Study Related Series", countEvidenceSeries(revised),
                "{series}", "series");
        return revised;
    }

    private static void validateMado(Attributes mado) {
        if (!UID.KeyObjectSelectionDocumentStorage.equals(mado.getString(Tag.SOPClassUID))) {
            throw new IllegalArgumentException("Input is not Key Object Selection Document Storage");
        }
        Attributes title = first(mado.getSequence(Tag.ConceptNameCodeSequence));
        if (title == null || !CODE_MANIFEST_WITH_DESCRIPTION.equals(title.getString(Tag.CodeValue))
                || !SCHEME_DCM.equals(title.getString(Tag.CodingSchemeDesignator))) {
            throw new IllegalArgumentException("Input is not a MADO Manifest with Description (131560, DCM)");
        }
        requireNonBlank(mado.getString(Tag.StudyInstanceUID), "MADO Study Instance UID");
    }

    private static void collectImageReferences(Attributes mado, Sequence content,
                                               Map<String, SopReference> found) {
        if (content == null) {
            return;
        }
        for (Attributes item : content) {
            if (VALUE_TYPE_IMAGE.equals(item.getString(Tag.ValueType))) {
                Attributes ref = first(item.getSequence(Tag.ReferencedSOPSequence));
                if (ref != null) {
                    String instanceUID = ref.getString(Tag.ReferencedSOPInstanceUID);
                    if (!isBlank(instanceUID) && !found.containsKey(instanceUID)) {
                        SopReference evidence = findInEvidence(mado, instanceUID);
                        if (evidence != null) {
                            found.put(instanceUID, evidence);
                        }
                    }
                }
            }
            collectImageReferences(mado, item.getSequence(Tag.ContentSequence), found);
        }
    }

    private static SopReference findInEvidence(Attributes mado, String instanceUID) {
        Sequence studies = mado.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence);
        if (studies == null) {
            return null;
        }
        for (Attributes study : studies) {
            Sequence seriesSequence = study.getSequence(Tag.ReferencedSeriesSequence);
            if (seriesSequence == null) continue;
            for (Attributes series : seriesSequence) {
                Sequence sopSequence = series.getSequence(Tag.ReferencedSOPSequence);
                if (sopSequence == null) continue;
                for (Attributes sop : sopSequence) {
                    if (instanceUID.equals(sop.getString(Tag.ReferencedSOPInstanceUID))) {
                        return new SopReference(study.getString(Tag.StudyInstanceUID),
                                series.getString(Tag.SeriesInstanceUID),
                                sop.getString(Tag.ReferencedSOPClassUID), instanceUID, series);
                    }
                }
            }
        }
        return null;
    }

    private static void setKinEvidence(Attributes kin, SopReference image) {
        kin.remove(Tag.CurrentRequestedProcedureEvidenceSequence);
        Attributes study = new Attributes();
        study.setString(Tag.StudyInstanceUID, VR.UI, image.studyInstanceUID());
        Attributes series = new Attributes();
        series.setString(Tag.SeriesInstanceUID, VR.UI, image.seriesInstanceUID());
        Attributes sop = new Attributes();
        sop.setString(Tag.ReferencedSOPClassUID, VR.UI, image.sopClassUID());
        sop.setString(Tag.ReferencedSOPInstanceUID, VR.UI, image.sopInstanceUID());
        series.newSequence(Tag.ReferencedSOPSequence, 1).add(sop);
        study.newSequence(Tag.ReferencedSeriesSequence, 1).add(series);
        kin.newSequence(Tag.CurrentRequestedProcedureEvidenceSequence, 1).add(study);
    }

    private static void addKinToEvidence(Attributes mado, String studyUID, String seriesUID,
                                         String sopUID, String retrieveLocationUID, String retrieveURL) {
        Sequence studies = mado.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence);
        if (studies == null) {
            throw new IllegalArgumentException("MADO has no Current Requested Procedure Evidence Sequence");
        }
        Attributes targetStudy = null;
        for (Attributes study : studies) {
            if (studyUID.equals(study.getString(Tag.StudyInstanceUID))) {
                targetStudy = study;
                break;
            }
        }
        if (targetStudy == null) {
            throw new IllegalArgumentException("KIN study is not present in MADO evidence: " + studyUID);
        }

        Sequence seriesSequence = targetStudy.getSequence(Tag.ReferencedSeriesSequence);
        if (seriesSequence == null) {
            seriesSequence = targetStudy.newSequence(Tag.ReferencedSeriesSequence, 1);
        }
        Attributes series = new Attributes();
        series.setString(Tag.SeriesInstanceUID, VR.UI, seriesUID);
        series.setString(Tag.RetrieveLocationUID, VR.UI, retrieveLocationUID);
        if (!isBlank(retrieveURL)) {
            series.setString(Tag.RetrieveURL, VR.UR, retrieveURL);
        }
        Attributes sop = new Attributes();
        sop.setString(Tag.ReferencedSOPClassUID, VR.UI, UID.KeyObjectSelectionDocumentStorage);
        sop.setString(Tag.ReferencedSOPInstanceUID, VR.UI, sopUID);
        series.newSequence(Tag.ReferencedSOPSequence, 1).add(sop);
        seriesSequence.add(series);
    }

    private static Attributes createKoImageLibraryGroup(String seriesUID, String sopUID,
                                                         String seriesNumber, String instanceNumber,
                                                         String description) {
        Attributes group = new Attributes();
        group.setString(Tag.RelationshipType, VR.CS, REL_CONTAINS);
        group.setString(Tag.ValueType, VR.CS, VALUE_TYPE_CONTAINER);
        setSingleCode(group, Tag.ConceptNameCodeSequence,
                code(CODE_IMAGE_LIBRARY_GROUP, SCHEME_DCM, "Image Library Group"));

        Sequence content = group.newSequence(Tag.ContentSequence, 5);
        content.add(createCodeItem(REL_HAS_ACQ_CONTEXT, CODE_MODALITY, SCHEME_DCM,
                "Modality", code("KO", SCHEME_DCM, "Key Object Selection")));
        content.add(createTextItem(REL_HAS_ACQ_CONTEXT, CODE_SERIES_NUMBER, SCHEME_DCM,
                "Series Number", seriesNumber));
        content.add(createUIDRefItem(REL_HAS_ACQ_CONTEXT, CODE_SERIES_INSTANCE_UID, SCHEME_DCM,
                "Series Instance UID", seriesUID));
        content.add(createNumericItem(REL_HAS_ACQ_CONTEXT, CODE_NUM_SERIES_RELATED_INSTANCES,
                SCHEME_DCM, "Number of Series Related Instances", 1,
                "{instances}", "UCUM", "instances"));

        Attributes entry = createSopReferenceItem(REL_CONTAINS, VALUE_TYPE_COMPOSITE,
                UID.KeyObjectSelectionDocumentStorage, sopUID);
        Sequence entryContent = entry.newSequence(Tag.ContentSequence, 3);
        entryContent.add(createTextItem(REL_HAS_ACQ_CONTEXT, CODE_INSTANCE_NUMBER, SCHEME_DCM,
                "Instance Number", instanceNumber));
        entryContent.add(createCodeItem(REL_HAS_ACQ_CONTEXT, CODE_KOS_DOCUMENT_TITLE, SCHEME_DCM,
                "Document Title", code(CODE_OF_INTEREST, SCHEME_DCM, "Of Interest")));
        if (!isBlank(description)) {
            entryContent.add(createTextItem(REL_HAS_ACQ_CONTEXT, CODE_KOS_DESCRIPTION, SCHEME_DCM,
                    "Key Object Description", description));
        }
        content.add(entry);
        return group;
    }

    private static Attributes createSopReferenceItem(String relationship, String valueType,
                                                     String sopClassUID, String sopInstanceUID) {
        Attributes item = new Attributes();
        item.setString(Tag.RelationshipType, VR.CS, relationship);
        item.setString(Tag.ValueType, VR.CS, valueType);
        Attributes ref = new Attributes();
        ref.setString(Tag.ReferencedSOPClassUID, VR.UI, sopClassUID);
        ref.setString(Tag.ReferencedSOPInstanceUID, VR.UI, sopInstanceUID);
        item.newSequence(Tag.ReferencedSOPSequence, 1).add(ref);
        return item;
    }

    private static void addKoModalityIfMissing(Sequence libraryContent) {
        for (Attributes item : libraryContent) {
            if (!CODE_MODALITY.equals(conceptCode(item))) continue;
            Attributes value = first(item.getSequence(Tag.ConceptCodeSequence));
            if (value != null && "KO".equals(value.getString(Tag.CodeValue))) return;
        }
        Attributes modality = createCodeItem(REL_HAS_ACQ_CONTEXT, CODE_MODALITY, SCHEME_DCM,
                "Modality", code("KO", SCHEME_DCM, "Key Object Selection"));
        int firstGroup = directChildIndexByConcept(libraryContent, CODE_IMAGE_LIBRARY_GROUP);
        if (firstGroup >= 0) {
            libraryContent.add(firstGroup, modality);
        } else {
            libraryContent.add(modality);
        }
    }

    private static Attributes findDirectChildByConcept(Attributes parent, String codeValue) {
        Sequence content = parent.getSequence(Tag.ContentSequence);
        if (content == null) return null;
        for (Attributes item : content) {
            if (codeValue.equals(conceptCode(item))) return item;
        }
        return null;
    }

    private static int directChildIndexByConcept(Sequence content, String codeValue) {
        for (int i = 0; i < content.size(); i++) {
            if (codeValue.equals(conceptCode(content.get(i)))) return i;
        }
        return -1;
    }

    private static int countEvidenceSeries(Attributes mado) {
        int count = 0;
        Sequence studies = mado.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence);
        if (studies == null) return 0;
        for (Attributes study : studies) {
            Sequence series = study.getSequence(Tag.ReferencedSeriesSequence);
            if (series != null) count += series.size();
        }
        return count;
    }

    private static void setNumericChild(Attributes parent, String conceptValue, String meaning,
                                        int number, String unitCode, String unitMeaning) {
        Sequence content = parent.getSequence(Tag.ContentSequence);
        if (content == null) content = parent.newSequence(Tag.ContentSequence, 8);
        Attributes replacement = createNumericItem(REL_HAS_ACQ_CONTEXT, conceptValue, SCHEME_DCM,
                meaning, number, unitCode, "UCUM", unitMeaning);
        for (int i = 0; i < content.size(); i++) {
            if (conceptValue.equals(conceptCode(content.get(i)))) {
                content.set(i, replacement);
                return;
            }
        }
        content.add(replacement);
    }

    private static String conceptCode(Attributes item) {
        Attributes concept = first(item.getSequence(Tag.ConceptNameCodeSequence));
        return concept == null ? null : concept.getString(Tag.CodeValue);
    }

    private static void setSingleCode(Attributes owner, int tag, Attributes code) {
        owner.remove(tag);
        owner.newSequence(tag, 1).add(code);
    }

    private static Attributes first(Sequence sequence) {
        return sequence == null || sequence.isEmpty() ? null : sequence.get(0);
    }

    private static void stampCreationTime(Attributes dataset) {
        OffsetDateTime now = OffsetDateTime.now();
        String date = now.format(DateTimeFormatter.BASIC_ISO_DATE);
        String time = now.format(DateTimeFormatter.ofPattern("HHmmss.SSS"));
        dataset.setString(Tag.ContentDate, VR.DA, date);
        dataset.setString(Tag.ContentTime, VR.TM, time);
        dataset.setString(Tag.InstanceCreationDate, VR.DA, date);
        dataset.setString(Tag.InstanceCreationTime, VR.TM, time);
        dataset.setString(Tag.TimezoneOffsetFromUTC, VR.SH,
                now.format(DateTimeFormatter.ofPattern("xx")));
    }

    private static String requireNonBlank(String value, String label) {
        if (isBlank(value)) throw new IllegalArgumentException(label + " is required");
        return value;
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return !isBlank(preferred) ? preferred : (!isBlank(fallback) ? fallback : null);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
