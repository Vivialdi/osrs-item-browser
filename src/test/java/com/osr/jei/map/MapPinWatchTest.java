package com.osr.jei.map;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MapPinWatchTest {

    @Test
    public void pinsAreRestoredWhenTheMapOpensAgain() {
        MapPinWatch watch = new MapPinWatch();
        assertFalse(watch.onTick(false, true));
        assertTrue(watch.onTick(true, true));
        assertFalse(watch.onTick(true, true));
        assertFalse(watch.onTick(false, true));
        assertTrue(watch.onTick(true, true));
    }

    @Test
    public void anEmptyMapDoesNotRestore() {
        MapPinWatch watch = new MapPinWatch();
        assertFalse(watch.onTick(true, false));
        assertFalse(watch.onTick(false, false));
        assertFalse(watch.onTick(true, false));
    }
}
