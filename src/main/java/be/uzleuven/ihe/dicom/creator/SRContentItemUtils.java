package be.uzleuven.ihe.dicom.creator;

import org.dcm4che3.data.*;

import static be.uzleuven.ihe.dicom.creator.DicomCreatorUtils.code;

/**
 * Utility methods for creating SR content items.
 */
public class SRContentItemUtils {

    /**
     * Creates a TEXT content item.
     */
    public static Attributes createTextItem(String relationshipType, String codeValue, String scheme,
                                           String meaning, String textValue) {
        Attributes item = new Attributes();
        item.setString(Tag.RelationshipType, VR.CS, relationshipType);
        item.setString(Tag.ValueType, VR.CS, "TEXT");
        item.newSequence(Tag.ConceptNameCodeSequence, 1).add(code(codeValue, scheme, meaning));
        item.setString(Tag.TextValue, VR.UT, textValue);
        return item;
    }

    /**
     * Creates a UIDREF content item.
     */
    public static Attributes createUIDRefItem(String relationshipType, String codeValue, String scheme,
                                             String meaning, String uidValue) {
        Attributes item = new Attributes();
        item.setString(Tag.RelationshipType, VR.CS, relationshipType);
        item.setString(Tag.ValueType, VR.CS, "UIDREF");
        item.newSequence(Tag.ConceptNameCodeSequence, 1).add(code(codeValue, scheme, meaning));
        item.setString(Tag.UID, VR.UI, uidValue);
        return item;
    }

    /**
     * Creates a CODE content item.
     */
    public static Attributes createCodeItem(String relationshipType, String codeValue, String scheme,
                                           String meaning, Attributes conceptCode) {
        Attributes item = new Attributes();
        item.setString(Tag.RelationshipType, VR.CS, relationshipType);
        item.setString(Tag.ValueType, VR.CS, "CODE");
        item.newSequence(Tag.ConceptNameCodeSequence, 1).add(code(codeValue, scheme, meaning));
        item.newSequence(Tag.ConceptCodeSequence, 1).add(conceptCode);
        return item;
    }

    /**
     * Creates a NUM content item.
     */
    public static Attributes createNumericItem(String relationshipType, String codeValue, String scheme,
                                              String meaning, int number) {
        Attributes item = new Attributes();
        item.setString(Tag.RelationshipType, VR.CS, relationshipType);
        item.setString(Tag.ValueType, VR.CS, "NUM");
        item.newSequence(Tag.ConceptNameCodeSequence, 1).add(code(codeValue, scheme, meaning));
        Attributes mv = new Attributes();
        mv.setString(Tag.NumericValue, VR.DS, Integer.toString(number));
        item.newSequence(Tag.MeasuredValueSequence, 1).add(mv);
        return item;
    }

    /**
     * Creates an IMAGE content item with referenced SOP.
     */
    public static Attributes createImageItem(String relationshipType, Attributes conceptName,
                                            String sopClassUID, String sopInstanceUID) {
        Attributes item = new Attributes();
        item.setString(Tag.RelationshipType, VR.CS, relationshipType);
        item.setString(Tag.ValueType, VR.CS, "IMAGE");

        if (conceptName != null) {
            item.newSequence(Tag.ConceptNameCodeSequence, 1).add(conceptName);
        }

        Sequence refSeq = item.newSequence(Tag.ReferencedSOPSequence, 1);
        Attributes refItem = new Attributes();
        refItem.setString(Tag.ReferencedSOPClassUID, VR.UI, sopClassUID);
        refItem.setString(Tag.ReferencedSOPInstanceUID, VR.UI, sopInstanceUID);
        refSeq.add(refItem);

        return item;
    }

    /**
     * Creates an IMAGE content item with frame number.
     */
    public static Attributes createImageItemWithFrame(String relationshipType, Attributes conceptName,
                                                     String sopClassUID, String sopInstanceUID, int frameNumber) {
        Attributes item = createImageItem(relationshipType, conceptName, sopClassUID, sopInstanceUID);

        if (frameNumber > 0) {
            Sequence refSeq = item.getSequence(Tag.ReferencedSOPSequence);
            if (refSeq != null && !refSeq.isEmpty()) {
                refSeq.get(0).setInt(Tag.ReferencedFrameNumber, VR.IS, frameNumber);
            }
        }

        return item;
    }

    /**
     * Creates a CONTAINER content item.
     */
    public static Attributes createContainerItem(String relationshipType, Attributes conceptName) {
        Attributes item = new Attributes();
        item.setString(Tag.RelationshipType, VR.CS, relationshipType);
        item.setString(Tag.ValueType, VR.CS, "CONTAINER");
        if (conceptName != null) {
            item.newSequence(Tag.ConceptNameCodeSequence, 1).add(conceptName);
        }
        return item;
    }
}

