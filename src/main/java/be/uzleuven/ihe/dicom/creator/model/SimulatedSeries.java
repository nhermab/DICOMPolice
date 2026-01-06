package be.uzleuven.ihe.dicom.creator.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a simulated DICOM series with instances.
 */
public class SimulatedSeries {
    private final String seriesUID;
    private String modality;
    private String description;
    private int seriesNumber;
    private String seriesDate;
    private String seriesTime;
    private List<SimulatedInstance> instances = new ArrayList<>();

    public SimulatedSeries(String uid, String mod, String desc) {
        this.seriesUID = uid;
        this.modality = mod;
        this.description = desc;
    }

    public void addInstance(String sopClassUID, String sopInstanceUID, boolean isKin) {
        instances.add(new SimulatedInstance(sopClassUID, sopInstanceUID, isKin));
    }

    public String getSeriesUID() {
        return seriesUID;
    }


    public String getModality() {
        return modality;
    }

    public void setModality(String modality) {
        this.modality = modality;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getSeriesNumber() {
        return seriesNumber;
    }

    public void setSeriesNumber(int seriesNumber) {
        this.seriesNumber = seriesNumber;
    }

    public String getSeriesDate() {
        return seriesDate;
    }

    public void setSeriesDate(String seriesDate) {
        this.seriesDate = seriesDate;
    }

    public String getSeriesTime() {
        return seriesTime;
    }

    public void setSeriesTime(String seriesTime) {
        this.seriesTime = seriesTime;
    }

    public List<SimulatedInstance> getInstances() {
        return instances;
    }

    public void setInstances(List<SimulatedInstance> instances) {
        this.instances = instances;
    }
}
