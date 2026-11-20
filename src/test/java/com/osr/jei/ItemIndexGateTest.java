package com.osr.jei;

import net.runelite.api.GameState;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ItemIndexGateTest {

    @Test
    public void indexWaitsUntilTheLoginScreen() {
        assertFalse(ItemIndexGate.cacheReady(null));
        assertFalse(ItemIndexGate.cacheReady(GameState.UNKNOWN));
        assertFalse(ItemIndexGate.cacheReady(GameState.STARTING));
        assertTrue(ItemIndexGate.cacheReady(GameState.LOGIN_SCREEN));
        assertTrue(ItemIndexGate.cacheReady(GameState.LOGGED_IN));
        assertTrue(ItemIndexGate.cacheReady(GameState.HOPPING));
    }

    @Test
    public void populatedIndexIsNotRebuiltOnLaterStates() {
        assertTrue(ItemIndexGate.shouldStart(GameState.LOGIN_SCREEN, false, false));
        assertTrue(ItemIndexGate.shouldStart(GameState.LOGGED_IN, false, false));
        assertFalse(ItemIndexGate.shouldStart(GameState.STARTING, false, false));
        assertFalse(ItemIndexGate.shouldStart(GameState.LOGGED_IN, true, false));
        assertFalse(ItemIndexGate.shouldStart(GameState.HOPPING, true, false));
        assertFalse(ItemIndexGate.shouldStart(GameState.LOGIN_SCREEN, false, true));
    }
}
