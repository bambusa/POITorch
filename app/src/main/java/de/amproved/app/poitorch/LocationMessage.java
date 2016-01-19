package de.amproved.app.poitorch;

/**
 * Created by BSD on 19.01.2016.
 */
public class LocationMessage {
    public boolean heading;
    public PoiModel poiModel;

    public LocationMessage() {
        heading = false;
    }

    public LocationMessage(PoiModel poiModel) {
        heading = true;
        this.poiModel = poiModel;
    }
}
