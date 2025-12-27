package be.uzleuven.ihe.dicom.creator.scu.streaming;

import org.dcm4che3.data.Attributes;

import java.io.Closeable;
import java.io.IOException;

/**
 * Streaming sink for SCU study/series/instance traversal.
 * Implementations should write data incrementally and avoid keeping large lists in memory.
 */
public interface ManifestStreamWriter extends Closeable {

    void openStudy(Attributes studyAttrs) throws IOException;

    void openSeries(Attributes seriesAttrs) throws IOException;

    void writeInstance(Attributes instanceAttrs) throws IOException;

    void closeSeries() throws IOException;

    void closeStudy() throws IOException;

    @Override
    void close() throws IOException;
}

