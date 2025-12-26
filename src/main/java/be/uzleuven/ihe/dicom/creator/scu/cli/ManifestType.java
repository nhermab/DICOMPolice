package be.uzleuven.ihe.dicom.creator.scu.cli;

public enum ManifestType {
    KOS,
    MADO;

    public static ManifestType fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("type is required");
        }
        switch (value.trim().toLowerCase()) {
            case "kos":
                return KOS;
            case "mado":
                return MADO;
            default:
                throw new IllegalArgumentException("Unknown type: " + value + " (expected: kos|mado)");
        }
    }
}

