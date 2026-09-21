package be.uzleuven.ihe.dicom.creator.kin;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import be.uzleuven.ihe.dicom.validator.model.ValidationResult;
import be.uzleuven.ihe.dicom.validator.validation.tid1600.TID1600ImageLibraryValidator;

import java.nio.file.Path;

import static be.uzleuven.ihe.dicom.creator.utils.DicomCreatorUtils.code;
import static be.uzleuven.ihe.dicom.creator.utils.DicomCreatorUtils.writeDicomFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MadoKinUpgraderTest {

    private static final String STUDY_UID = "1.2.826.0.1.3680043.10.100.1";
    private static final String SERIES_UID = "1.2.826.0.1.3680043.10.100.2";
    private static final String IMAGE_UID = "1.2.826.0.1.3680043.10.100.3";
    private static final String RETRIEVE_LOCATION_UID = "1.2.826.0.1.3680043.10.100.4";

    @Test
    void createsSeparateKinAndReferencesItInAllThreeMadoLocations() {
        Attributes source = createMado();

        MadoKinUpgrader.UpgradeResult result = MadoKinUpgrader.upgrade(source,
                MadoKinUpgrader.Options.defaults());
        Attributes kin = result.kin();
        Attributes revised = result.revisedMado();
        String kinUID = kin.getString(Tag.SOPInstanceUID);

        assertEquals(IMAGE_UID, result.selectedImage().sopInstanceUID());
        assertEquals(UID.KeyObjectSelectionDocumentStorage, kin.getString(Tag.SOPClassUID));
        assertEquals("KO", kin.getString(Tag.Modality));
        assertEquals(STUDY_UID, kin.getString(Tag.StudyInstanceUID));
        assertEquals("113000", conceptCode(kin));
        assertEquals(IMAGE_UID, referencedUID(kin.getSequence(Tag.ContentSequence).get(1)));
        assertEquals(IMAGE_UID, kin.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence).get(0)
                .getSequence(Tag.ReferencedSeriesSequence).get(0)
                .getSequence(Tag.ReferencedSOPSequence).get(0)
                .getString(Tag.ReferencedSOPInstanceUID));

        assertNotEquals(source.getString(Tag.SOPInstanceUID), revised.getString(Tag.SOPInstanceUID));
        assertEquals("131560", conceptCode(revised));
        assertTrue(hasDirectReference(revised, "COMPOSITE", kinUID));
        assertTrue(hasEvidenceReference(revised, kinUID));
        assertTrue(directReferenceIndex(revised, "COMPOSITE", kinUID)
                < directConceptIndex(revised.getSequence(Tag.ContentSequence), "111028"));

        Attributes koGroup = findKoGroup(revised);
        assertNotNull(koGroup);
        Attributes kinEntry = findReference(koGroup.getSequence(Tag.ContentSequence), "COMPOSITE", kinUID);
        assertNotNull(kinEntry);
        assertEquals("113000", codeValue(childByConcept(kinEntry, "121144")));
        assertEquals(MadoKinUpgrader.DEFAULT_DESCRIPTION,
                childByConcept(kinEntry, "113012").getString(Tag.TextValue));
        assertEquals(2, numericValue(childByConcept(findImageLibrary(revised), "131565")));

        ValidationResult validation = new ValidationResult();
        TID1600ImageLibraryValidator.validateImageLibraryContainer(
                findImageLibrary(revised), validation, "MADO.ImageLibrary", false);
        assertTrue(validation.getErrors().stream().noneMatch(message ->
                message.getMessage().contains("KOS/KIN")
                        || message.getMessage().contains("missing required Document Title")));
        assertTrue(validation.getWarnings().stream().noneMatch(message ->
                message.getMessage().contains("COMPOSITE for a non-KOS")));

        // The source object remains untouched.
        assertFalse(hasEvidenceReference(source, kinUID));
        assertFalse(hasDirectReference(source, "COMPOSITE", kinUID));
    }

    @Test
    void cliWritesPart10KinAndRevisedMado(@TempDir Path tempDir) throws Exception {
        Path input = tempDir.resolve("source.dcm");
        Path kin = tempDir.resolve("kin.dcm");
        Path revised = tempDir.resolve("revised.dcm");
        writeDicomFile(input.toFile(), createMado());

        int status = MadoKinUpgradeCli.run(new String[]{
                "--input", input.toString(),
                "--kin-out", kin.toString(),
                "--mado-out", revised.toString()
        });

        assertEquals(0, status);
        assertTrue(java.nio.file.Files.size(kin) > 132);
        assertTrue(java.nio.file.Files.size(revised) > 132);
        assertEquals("DICM", new String(java.nio.file.Files.readAllBytes(kin), 128, 4,
                java.nio.charset.StandardCharsets.US_ASCII));
    }

    private static Attributes createMado() {
        Attributes mado = new Attributes();
        mado.setString(Tag.SOPClassUID, VR.UI, UID.KeyObjectSelectionDocumentStorage);
        mado.setString(Tag.SOPInstanceUID, VR.UI, "1.2.826.0.1.3680043.10.100.5");
        mado.setString(Tag.StudyInstanceUID, VR.UI, STUDY_UID);
        mado.setString(Tag.SeriesInstanceUID, VR.UI, "1.2.826.0.1.3680043.10.100.6");
        mado.setString(Tag.Modality, VR.CS, "KO");
        mado.setString(Tag.SeriesNumber, VR.IS, "1");
        mado.setString(Tag.InstanceNumber, VR.IS, "1");
        mado.setString(Tag.ValueType, VR.CS, "CONTAINER");
        mado.setString(Tag.ContinuityOfContent, VR.CS, "SEPARATE");
        mado.newSequence(Tag.ConceptNameCodeSequence, 1)
                .add(code("131560", "DCM", "Manifest with Description"));

        Attributes sop = new Attributes();
        sop.setString(Tag.ReferencedSOPClassUID, VR.UI, UID.CTImageStorage);
        sop.setString(Tag.ReferencedSOPInstanceUID, VR.UI, IMAGE_UID);
        Attributes series = new Attributes();
        series.setString(Tag.SeriesInstanceUID, VR.UI, SERIES_UID);
        series.setString(Tag.RetrieveLocationUID, VR.UI, RETRIEVE_LOCATION_UID);
        series.setString(Tag.RetrieveURL, VR.UR, "https://example.test/dicom-web");
        series.newSequence(Tag.ReferencedSOPSequence, 1).add(sop);
        Attributes study = new Attributes();
        study.setString(Tag.StudyInstanceUID, VR.UI, STUDY_UID);
        study.newSequence(Tag.ReferencedSeriesSequence, 1).add(series);
        mado.newSequence(Tag.CurrentRequestedProcedureEvidenceSequence, 1).add(study);

        Sequence root = mado.newSequence(Tag.ContentSequence, 2);
        root.add(reference("IMAGE", UID.CTImageStorage, IMAGE_UID));
        Attributes library = new Attributes();
        library.setString(Tag.RelationshipType, VR.CS, "CONTAINS");
        library.setString(Tag.ValueType, VR.CS, "CONTAINER");
        library.newSequence(Tag.ConceptNameCodeSequence, 1)
                .add(code("111028", "DCM", "Image Library"));
        Sequence libraryContent = library.newSequence(Tag.ContentSequence, 2);
        libraryContent.add(numeric("131565", 1, "{series}", "series"));
        Attributes group = new Attributes();
        group.setString(Tag.RelationshipType, VR.CS, "CONTAINS");
        group.setString(Tag.ValueType, VR.CS, "CONTAINER");
        group.newSequence(Tag.ConceptNameCodeSequence, 1)
                .add(code("126200", "DCM", "Image Library Group"));
        group.newSequence(Tag.ContentSequence, 1).add(reference("IMAGE", UID.CTImageStorage, IMAGE_UID));
        libraryContent.add(group);
        root.add(library);
        return mado;
    }

    private static Attributes reference(String valueType, String classUID, String instanceUID) {
        Attributes item = new Attributes();
        item.setString(Tag.RelationshipType, VR.CS, "CONTAINS");
        item.setString(Tag.ValueType, VR.CS, valueType);
        Attributes ref = new Attributes();
        ref.setString(Tag.ReferencedSOPClassUID, VR.UI, classUID);
        ref.setString(Tag.ReferencedSOPInstanceUID, VR.UI, instanceUID);
        item.newSequence(Tag.ReferencedSOPSequence, 1).add(ref);
        return item;
    }

    private static Attributes numeric(String concept, int value, String unit, String unitMeaning) {
        Attributes item = new Attributes();
        item.setString(Tag.RelationshipType, VR.CS, "HAS ACQ CONTEXT");
        item.setString(Tag.ValueType, VR.CS, "NUM");
        item.newSequence(Tag.ConceptNameCodeSequence, 1).add(code(concept, "DCM", "count"));
        Attributes measured = new Attributes();
        measured.setString(Tag.NumericValue, VR.DS, Integer.toString(value));
        measured.newSequence(Tag.MeasurementUnitsCodeSequence, 1).add(code(unit, "UCUM", unitMeaning));
        item.newSequence(Tag.MeasuredValueSequence, 1).add(measured);
        return item;
    }

    private static boolean hasDirectReference(Attributes root, String type, String uid) {
        return findReference(root.getSequence(Tag.ContentSequence), type, uid) != null;
    }

    private static int directReferenceIndex(Attributes root, String type, String uid) {
        Sequence content = root.getSequence(Tag.ContentSequence);
        for (int i = 0; i < content.size(); i++) {
            Attributes item = content.get(i);
            if (type.equals(item.getString(Tag.ValueType)) && uid.equals(referencedUID(item))) return i;
        }
        return -1;
    }

    private static int directConceptIndex(Sequence content, String concept) {
        for (int i = 0; i < content.size(); i++) {
            if (concept.equals(conceptCode(content.get(i)))) return i;
        }
        return -1;
    }

    private static boolean hasEvidenceReference(Attributes root, String uid) {
        Sequence studies = root.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence);
        if (studies == null) return false;
        for (Attributes study : studies) {
            for (Attributes series : study.getSequence(Tag.ReferencedSeriesSequence)) {
                for (Attributes sop : series.getSequence(Tag.ReferencedSOPSequence)) {
                    if (uid.equals(sop.getString(Tag.ReferencedSOPInstanceUID))) return true;
                }
            }
        }
        return false;
    }

    private static Attributes findKoGroup(Attributes mado) {
        Sequence content = findImageLibrary(mado).getSequence(Tag.ContentSequence);
        for (Attributes item : content) {
            if (!"126200".equals(conceptCode(item))) continue;
            Attributes modality = childByConcept(item, "121139");
            if (modality != null && "KO".equals(codeValue(modality))) return item;
        }
        return null;
    }

    private static Attributes findImageLibrary(Attributes mado) {
        for (Attributes item : mado.getSequence(Tag.ContentSequence)) {
            if ("111028".equals(conceptCode(item))) return item;
        }
        return null;
    }

    private static Attributes childByConcept(Attributes parent, String concept) {
        Sequence content = parent.getSequence(Tag.ContentSequence);
        if (content == null) return null;
        for (Attributes child : content) {
            if (concept.equals(conceptCode(child))) return child;
        }
        return null;
    }

    private static Attributes findReference(Sequence content, String type, String uid) {
        if (content == null) return null;
        for (Attributes item : content) {
            if (type.equals(item.getString(Tag.ValueType)) && uid.equals(referencedUID(item))) return item;
        }
        return null;
    }

    private static String referencedUID(Attributes item) {
        Sequence refs = item.getSequence(Tag.ReferencedSOPSequence);
        return refs == null || refs.isEmpty() ? null : refs.get(0).getString(Tag.ReferencedSOPInstanceUID);
    }

    private static String conceptCode(Attributes item) {
        Sequence concepts = item.getSequence(Tag.ConceptNameCodeSequence);
        return concepts == null || concepts.isEmpty() ? null : concepts.get(0).getString(Tag.CodeValue);
    }

    private static String codeValue(Attributes item) {
        Sequence values = item.getSequence(Tag.ConceptCodeSequence);
        return values == null || values.isEmpty() ? null : values.get(0).getString(Tag.CodeValue);
    }

    private static int numericValue(Attributes item) {
        return Integer.parseInt(item.getSequence(Tag.MeasuredValueSequence).get(0).getString(Tag.NumericValue));
    }
}
