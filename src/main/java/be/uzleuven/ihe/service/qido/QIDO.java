package be.uzleuven.ihe.service.qido;

/**
 * QIDO-RS (Query based on ID for DICOM Objects) DICOMweb service.
 *
 * This package implements the DICOM PS3.18 QIDO-RS interface:
 * - {@link QIDORestController}: REST controller with QIDO-RS endpoints
 * - {@link QIDOConfiguration}: Configuration properties for the service
 * - {@link QIDOUtils}: Utilities for parsing query parameters to DICOM Attributes
 * - {@link DICOMJSONConverter}: Converter for DICOM Attributes to JSON format
 *
 * Endpoints provided:
 * - GET /dicomweb/studies - Search for studies
 * - GET /dicomweb/studies/{studyUID}/series - Search for series in a study
 * - GET /dicomweb/studies/{studyUID}/series/{seriesUID}/instances - Search for instances
 * - GET /dicomweb/series - Search for all series
 * - GET /dicomweb/instances - Search for all instances
 * - GET /dicomweb/studies/{studyUID}/instances - Search for all instances in a study
 */
public class QIDO {
    // Package marker class - see package-level documentation above
}
