package be.uzleuven.ihe.dicom.validator.utils;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import be.uzleuven.ihe.dicom.constants.DicomConstants;

/**
 * Small utilities for navigating SR Content Trees (ContentSequence / CONTAINER nodes).
 */
public final class SRContentTreeUtils {

    private SRContentTreeUtils() {
    }

    /**
     * Returns true if the item is a CONTAINER with Concept Name Code matching (codeValue, codingSchemeDesignator).
     */
    public static boolean isContainerWithConcept(Attributes item, String codeValue, String codingSchemeDesignator) {
        if (item == null) {
            return false;
        }
        if (!DicomConstants.VALUE_TYPE_CONTAINER.equals(item.getString(Tag.ValueType))) {
            return false;
        }
        Attributes concept = firstItem(item.getSequence(Tag.ConceptNameCodeSequence));
        if (concept == null) {
            return false;
        }
        return codeValue.equals(concept.getString(Tag.CodeValue))
                && codingSchemeDesignator.equals(concept.getString(Tag.CodingSchemeDesignator));
    }

    /**
     * Returns the first element of a sequence, or null.
     */
    public static Attributes firstItem(Sequence seq) {
        return (seq == null || seq.isEmpty()) ? null : seq.get(0);
    }
}
