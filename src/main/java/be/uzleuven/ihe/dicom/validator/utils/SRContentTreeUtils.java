package be.uzleuven.ihe.dicom.validator.utils;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import be.uzleuven.ihe.dicom.constants.DicomConstants;

import java.util.ArrayList;
import java.util.List;

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

    /**
     * Finds all direct child CONTAINER content items that match the given concept code.
     * Not recursive.
     *
     * @param root The starting point of the search (item containing a ContentSequence).
     * @param code The code to match in the ConceptNameCodeSequence (e.g., "111028").
     * @param scheme The coding scheme to match (e.g., "DCM").
     * @param onlyFirst If true, stop after the first match and return a list with at most one item.
     * @return The list of matching Attributes items, may be empty.
     */
    public static List<Attributes> findDirectChildContainersByConcept(Attributes root, String code, String scheme) {

        List<Attributes> results = new ArrayList<>();

        if (root!=null) {
            Sequence contentSeq = root.getSequence(Tag.ContentSequence);
            if (contentSeq != null) {
                for (Attributes item : contentSeq) {
                    if (isContainerWithConcept(item, code, scheme)) {
                        results.add(item);
                    }
                }
            }
        }

        return results;
    }

    /**
     * Find recursively the first nested content item that is a CONTAINER and matches the given concept code.
     * This method performs a depth-first search on the content tree.
     *
     * @param root The starting point of the search
     * @param code The code to match in the ConceptNameCodeSequence (e.g., "111028").
     * @param scheme The coding scheme to match (e.g., "DCM").
     * @return The matching Attributes item, or null if not found.
     */
    public static Attributes findContainerByConcept(Attributes root, String code, String scheme) {
        if (root == null) {
            return null;
        }

        // 1. Check if the current item itself is the one we're looking for.
        if (isContainerWithConcept(root, code, scheme)) {
            return root;
        }

        // 2. If not, recursively search its children.
        Sequence contentSeq = root.getSequence(Tag.ContentSequence);
        if (contentSeq != null) {
            for (Attributes item : contentSeq) {
                Attributes found = findContainerByConcept(item, code, scheme);
                if (found != null) {
                    return found; // Return the first match found.
                }
            }
        }
        // 3. If not found in this branch, return null.
        return null;
    }


    /**
     * Finds the concept value for a given concept name code,
     * when the value is stored in a simple Tag (e.g. TextValue, CodeValue).
     *
     * @param sequence The content sequence.
     * @param conceptNameCode The code value of the concept name to find.
     * @param conceptNameScheme The coding scheme of the concept name to find.
     * @param valueTag The DICOM tag where the value is stored (e.g. Tag.TextValue).
     * @return The value from the value Tag, or null if not found.
     */
    public static String findValueByConceptNameAndValueTag(Sequence sequence, String conceptNameCode, String conceptNameScheme, Integer valueTag) {

        if (sequence != null) {
            for (Attributes item : sequence) {
                if (isItemWithConceptName(item, conceptNameCode, conceptNameScheme)) {
                    return item.getString(valueTag);
                }
            }
        }
        return null;
    }

    public static List<Attributes> findItemsByValueType(Sequence sequence, String valueType) {

        List<Attributes> results = new ArrayList<>();

        if (sequence != null) {
            for (Attributes item : sequence) {
                if (isItemWithValueType(item, valueType)) {
                    results.add(item);
                }
            }
        }
        return results;
    }

    /**
     * Returns true if the item has a Concept Name Code matching (codeValue, codingSchemeDesignator).
     */
    public static boolean isItemWithConceptName(Attributes item, String codeValue, String codingSchemeDesignator) {
        if (item != null) {
            Attributes conceptName = firstItem(item.getSequence(Tag.ConceptNameCodeSequence));
            if (conceptName != null){
                return codeValue.equals(conceptName.getString(Tag.CodeValue))
                        && codingSchemeDesignator.equals(conceptName.getString(Tag.CodingSchemeDesignator));
            }
        }
        return false;
    }

    public static boolean isItemWithValueType(Attributes item, String valueType) {
        if (item != null) {
            if (valueType.equals(item.getString(Tag.ValueType))) {
                return true;
            }
        }
        return false;
    }


}
