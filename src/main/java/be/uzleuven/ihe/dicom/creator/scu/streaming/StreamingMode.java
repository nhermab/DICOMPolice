package be.uzleuven.ihe.dicom.creator.scu.streaming;

public enum StreamingMode {
    /** Existing behavior: build a full DICOM manifest in memory and then write it. */
    DICOM,

    /** Low-memory streaming output: one NDJSON file per study. */
    NDJSON
}

