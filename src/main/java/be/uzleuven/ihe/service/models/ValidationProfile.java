package be.uzleuven.ihe.service.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Validation profile is a group of business rules (or assertions) that can be applied to one or more item types.
 */
public class ValidationProfile {
    private String profileID;
    private String profileName;
    private String domain;
    private List<String> coveredItems;
    private List<String> standards;
    private String version;

    public ValidationProfile() {
        this.coveredItems = new ArrayList<>();
        this.standards = new ArrayList<>();
    }

    public String getProfileID() {
        return profileID;
    }

    public void setProfileID(String profileID) {
        this.profileID = profileID;
    }

    public String getProfileName() {
        return profileName;
    }

    public void setProfileName(String profileName) {
        this.profileName = profileName;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public List<String> getCoveredItems() {
        return coveredItems;
    }

    public void setCoveredItems(List<String> coveredItems) {
        this.coveredItems = coveredItems;
    }

    public List<String> getStandards() {
        return standards;
    }

    public void setStandards(List<String> standards) {
        this.standards = standards;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
