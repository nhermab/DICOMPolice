package be.uzleuven.ihe.dicom.creator.model;

/**
 * Configuration options for MADO (Manifest with Description) creation.
 * Provides builder-style methods for fluent configuration.
 */
public class MADOOptions {
    /** Total series in the referenced study (including optional KIN series). */
    private int seriesCount = 5;

    /** Ensure a KIN (Key Object) instance exists and is referenced as-is. */
    private boolean includeKIN = true;

    /** Modalities pool for non-KIN series. Uses DICOM modality codes ("CT", "MR", etc.). */
    private String[] modalityPool = new String[]{"CT", "OT", "US"};

    /** Total number of referenced instances across non-KIN series. */
    private int totalInstanceCount = 8; // matches default: 5 + 1 + 1 + 1

    /**
     * If true, generate one multiframe-like instance (Enhanced CT) as part of the total.
     * (Keeps compatibility with current validator expectations.)
     */
    private boolean includeMultiframe = true;

    /** When includeMultiframe=true, number of multiframe instances to generate. */
    private int multiframeInstanceCount = 1;

    /**
     * How many referenced "key images" (UIDREF items) to put in KIN descriptors.
     * NOTE: This does NOT limit instances in the content tree. ALL instances in Evidence
     * are always included in the Image Library per DICOM/IHE spec. This parameter only
     * affects how many images the nested KIN (Key Image Note) describes via UIDREF items.
     */
    private int keyImageCount = 3;

    /**
     * If true, include extended instance-level metadata (Rows, Columns, Bits Allocated, etc.)
     * in the MADO content sequence and evidence sequence.
     * If false (default), only include standard MADO instance-level attributes:
     * Instance Number and Number of Frames (for multiframe images).
     */
    private boolean includeExtendedInstanceMetadata = false;

    public MADOOptions withSeriesCount(int v) {
        this.seriesCount = Math.max(1, v);
        return this;
    }

    public MADOOptions withTotalInstanceCount(int v) {
        this.totalInstanceCount = Math.max(1, v);
        return this;
    }

    public MADOOptions withModalityPool(String... modalities) {
        if (modalities != null && modalities.length > 0) this.modalityPool = modalities;
        return this;
    }

    public MADOOptions withIncludeKIN(boolean v) {
        this.includeKIN = v;
        return this;
    }

    public MADOOptions withIncludeMultiframe(boolean v) {
        this.includeMultiframe = v;
        return this;
    }

    public MADOOptions withMultiframeInstanceCount(int v) {
        this.multiframeInstanceCount = Math.max(0, v);
        return this;
    }

    public MADOOptions withKeyImageCount(int v) {
        this.keyImageCount = Math.max(0, v);
        return this;
    }

    public MADOOptions withIncludeExtendedInstanceMetadata(boolean v) {
        this.includeExtendedInstanceMetadata = v;
        return this;
    }

    // Getters
    public int getSeriesCount() {
        return seriesCount;
    }

    public boolean isIncludeKIN() {
        return includeKIN;
    }

    public String[] getModalityPool() {
        return modalityPool;
    }

    public int getTotalInstanceCount() {
        return totalInstanceCount;
    }

    public boolean isIncludeMultiframe() {
        return includeMultiframe;
    }

    public int getMultiframeInstanceCount() {
        return multiframeInstanceCount;
    }

    public int getKeyImageCount() {
        return keyImageCount;
    }

    public boolean isIncludeExtendedInstanceMetadata() {
        return includeExtendedInstanceMetadata;
    }
}

