package de.amproved.app.poitorch;

import android.location.Location;

/**
 * Created by BSD on 18.01.2016.
 */
public class PoiModel {

    private String name;
    private String description;
    private String source;
    private Location location;

    public PoiModel(String name, String description, String source, Location location) {
        this.name = name;
        this.description = description;
        this.source = source;
        this.location = location;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}