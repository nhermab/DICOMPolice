package be.uzleuven.ihe.dicom.creator.scu.cli;

import java.io.File;

import be.uzleuven.ihe.dicom.creator.scu.streaming.StreamingMode;

public class OutputOptions {
    private final File outFile;
    private final File outDir;
    private final String outPattern;
    private final boolean overwrite;
    private final StreamingMode streamingMode;

    public OutputOptions(File outFile, File outDir, String outPattern, boolean overwrite) {
        this(outFile, outDir, outPattern, overwrite, StreamingMode.DICOM);
    }

    public OutputOptions(File outFile, File outDir, String outPattern, boolean overwrite, StreamingMode streamingMode) {
        this.outFile = outFile;
        this.outDir = outDir;
        this.outPattern = outPattern;
        this.overwrite = overwrite;
        this.streamingMode = streamingMode == null ? StreamingMode.DICOM : streamingMode;
    }

    public File getOutFile() {
        return outFile;
    }

    public File getOutDir() {
        return outDir;
    }

    public String getOutPattern() {
        return outPattern;
    }

    public boolean isOverwrite() {
        return overwrite;
    }

    public StreamingMode getStreamingMode() {
        return streamingMode;
    }
}
