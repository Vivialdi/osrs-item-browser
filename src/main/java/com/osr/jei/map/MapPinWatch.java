package com.osr.jei.map;

/**
 * Notices the tick the world map renderer becomes loaded.
 * Pins are put back on that tick. A later tick while the map stays open does not move them.
 */
public final class MapPinWatch {

    private boolean mapWasOpen;

    public boolean onTick(boolean mapOpen, boolean hasPins) {
        boolean restore = mapOpen && !mapWasOpen && hasPins;
        mapWasOpen = mapOpen;
        return restore;
    }
}
