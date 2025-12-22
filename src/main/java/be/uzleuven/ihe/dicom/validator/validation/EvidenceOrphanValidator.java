package be.uzleuven.ihe.dicom.validator.validation;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import be.uzleuven.ihe.dicom.validator.model.ValidationResult;

import java.util.HashSet;
import java.util.Set;

/**
 * Validates evidence reference consistency in Key Object Selection documents.
 * Ensures that all instances referenced in ContentSequence are also present in
 * CurrentRequestedProcedureEvidenceSequence to prevent "orphan" references.
 */
public class EvidenceOrphanValidator {

    /**
     * Validate that all instances in ContentSequence are also in Evidence Sequence.
     * This prevents the "orphan" problem where an image is visible in the content tree
     * but not in the evidence list used by XDS consumers for retrieval.
     */
    public static void validateNoOrphanReferences(Attributes dataset, ValidationResult result, String path) {
        // Collect all SOP Instance UIDs from Evidence Sequence
        Set<String> evidenceInstanceUIDs = collectEvidenceInstanceUIDs(dataset);

        // Collect all SOP Instance UIDs from Content Sequence
        Set<String> contentInstanceUIDs = new HashSet<>();
        collectContentInstanceUIDs(dataset, contentInstanceUIDs);

        if (contentInstanceUIDs.isEmpty()) {
            result.addInfo("No instance references found in ContentSequence", path);
            return;
        }

        if (evidenceInstanceUIDs.isEmpty()) {
            result.addError("ContentSequence references instances, but " + "CurrentRequestedProcedureEvidenceSequence is empty or missing. " + "All referenced instances must be listed in Evidence.", path);
            return;
        }

        // Find orphans - instances in Content but not in Evidence
        Set<String> orphans = new HashSet<>(contentInstanceUIDs);
        orphans.removeAll(evidenceInstanceUIDs);

        if (orphans.isEmpty()) {
            result.addInfo("All " + contentInstanceUIDs.size() +
                          " instance(s) in ContentSequence are properly listed in Evidence", path);
        } else {
            result.addError("ORPHAN REFERENCES DETECTED: " + orphans.size() + " instance(s) are referenced in ContentSequence but NOT in " + "CurrentRequestedProcedureEvidenceSequence. XDS consumers use the Evidence " + "list to build retrieval batches and will fail to retrieve these orphan instances. " + "Orphan UIDs: " + orphans, path);
        }

        // Also check reverse - instances in Evidence but not in Content (less critical but worth noting)
        Set<String> unreferenced = new HashSet<>(evidenceInstanceUIDs);
        unreferenced.removeAll(contentInstanceUIDs);

        if (!unreferenced.isEmpty()) {
            result.addWarning("Evidence sequence contains " + unreferenced.size() + " instance(s) not referenced in ContentSequence. " + "While not an error, this may indicate unused evidence. " + "UIDs: " + unreferenced, path);
        }
    }

    /**
     * Collect all SOP Instance UIDs from CurrentRequestedProcedureEvidenceSequence.
     */
    private static Set<String> collectEvidenceInstanceUIDs(Attributes dataset) {
        Set<String> instanceUIDs = new HashSet<>();

        org.dcm4che3.data.Sequence evidenceSeq = dataset.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence);
        if (evidenceSeq == null) {
            return instanceUIDs;
        }

        for (Attributes studyItem : evidenceSeq) {
            org.dcm4che3.data.Sequence seriesSeq = studyItem.getSequence(Tag.ReferencedSeriesSequence);
            if (seriesSeq == null) {
                continue;
            }

            for (Attributes seriesItem : seriesSeq) {
                org.dcm4che3.data.Sequence sopSeq = seriesItem.getSequence(Tag.ReferencedSOPSequence);
                if (sopSeq == null) {
                    continue;
                }

                for (Attributes sopItem : sopSeq) {
                    String instanceUID = sopItem.getString(Tag.ReferencedSOPInstanceUID);
                    if (instanceUID != null && !instanceUID.isEmpty()) {
                        instanceUIDs.add(instanceUID);
                    }
                }
            }
        }

        return instanceUIDs;
    }

    /**
     * Recursively collect all SOP Instance UIDs from ContentSequence.
     */
    private static void collectContentInstanceUIDs(Attributes dataset, Set<String> instanceUIDs) {
        org.dcm4che3.data.Sequence contentSeq = dataset.getSequence(Tag.ContentSequence);
        if (contentSeq == null) {
            return;
        }

        for (Attributes contentItem : contentSeq) {
            // Check for Referenced SOP Sequence
            org.dcm4che3.data.Sequence refSOPSeq = contentItem.getSequence(Tag.ReferencedSOPSequence);
            if (refSOPSeq != null) {
                for (Attributes sopItem : refSOPSeq) {
                    String instanceUID = sopItem.getString(Tag.ReferencedSOPInstanceUID);
                    if (instanceUID != null && !instanceUID.isEmpty()) {
                        instanceUIDs.add(instanceUID);
                    }
                }
            }

            // Recurse into nested content
            collectContentInstanceUIDs(contentItem, instanceUIDs);
        }
    }

    /**
     * Provide detailed report of evidence structure.
     */
    public static void reportEvidenceStructure(Attributes dataset, ValidationResult result, String path) {
        org.dcm4che3.data.Sequence evidenceSeq = dataset.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence);
        if (evidenceSeq == null || evidenceSeq.isEmpty()) {
            result.addInfo("No evidence sequence present", path);
            return;
        }

        int totalStudies = evidenceSeq.size();
        int totalSeries = 0;
        int totalInstances = 0;

        for (Attributes studyItem : evidenceSeq) {
            org.dcm4che3.data.Sequence seriesSeq = studyItem.getSequence(Tag.ReferencedSeriesSequence);

            if (seriesSeq != null) {
                totalSeries += seriesSeq.size();

                for (Attributes seriesItem : seriesSeq) {
                    org.dcm4che3.data.Sequence sopSeq = seriesItem.getSequence(Tag.ReferencedSOPSequence);

                    if (sopSeq != null) {
                        totalInstances += sopSeq.size();
                    }
                }
            }
        }

        result.addInfo(String.format("Evidence structure: %d study(ies), %d series, %d instance(s)",
                                    totalStudies, totalSeries, totalInstances), path);
    }
}

