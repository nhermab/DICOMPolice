package be.uzleuven.ihe.dicom.creator.scu;

import org.dcm4che3.data.Attributes;
import java.util.List;

/**
 * Data holder for series and its instances.
 * Used by manifest creators to group instance data by series.
 */
class SeriesData {
    public Attributes seriesAttrs;
    public List<Attributes> instances;
}

