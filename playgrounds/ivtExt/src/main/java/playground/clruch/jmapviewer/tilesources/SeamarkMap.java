package playground.clruch.jmapviewer.tilesources;

/**
 * Seamark overlay
 */
public class SeamarkMap extends AbstractOsmTileSource {

    private static final String PATTERN = "http://tiles.openseamap.org/seamark";

    public SeamarkMap() {
        super("Seamark", PATTERN, "seamark");
    }

    @Override
    public String getBaseUrl() {
        return PATTERN;
    }

}