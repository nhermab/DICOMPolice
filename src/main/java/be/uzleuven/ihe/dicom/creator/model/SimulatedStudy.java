package be.uzleuven.ihe.dicom.creator.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a simulated DICOM study with series and instances.
 */
public class SimulatedStudy {
    private String studyInstanceUID;
    private List<be.uzleuven.ihe.dicom.creator.model.SimulatedSeries> seriesList = new ArrayList<>();
    private be.uzleuven.ihe.dicom.creator.model.SimulatedSeries kinSeries;
    private Object options;

    public String getStudyInstanceUID() {
        return studyInstanceUID;
    }

    public void setStudyInstanceUID(String studyInstanceUID) {
        this.studyInstanceUID = studyInstanceUID;
    }

    public List<be.uzleuven.ihe.dicom.creator.model.SimulatedSeries> getSeriesList() {
        return seriesList;
    }

    public void setSeriesList(List<be.uzleuven.ihe.dicom.creator.model.SimulatedSeries> seriesList) {
        this.seriesList = seriesList;
    }

    public be.uzleuven.ihe.dicom.creator.model.SimulatedSeries getKinSeries() {
        return kinSeries;
    }

    public void setKinSeries(be.uzleuven.ihe.dicom.creator.model.SimulatedSeries kinSeries) {
        this.kinSeries = kinSeries;
    }

    public Object getOptions() {
        return options;
    }

    public void setOptions(Object options) {
        this.options = options;
    }

    public void addSeries(be.uzleuven.ihe.dicom.creator.model.SimulatedSeries series) {
        seriesList.add(series);
    }
}
