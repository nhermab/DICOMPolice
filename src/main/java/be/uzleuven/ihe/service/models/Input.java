package be.uzleuven.ihe.service.models;

/**
 * Item to attach to a validation request.
 */
public class Input {
    private String id;
    private String content;  // base64 encoded
    private String itemId;
    private String location;

    public Input() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}

