package com.osr.jei;

import net.runelite.api.GameState;

/**
 * Item definitions are empty until the client reaches the login screen.
 * Once the index has been filled, later game states (including world hops)
 * must not scan the cache again.
 */
public final class ItemIndexGate {

    private ItemIndexGate() {
    }

    public static boolean cacheReady(GameState state) {
        return state != null && state.getState() >= GameState.LOGIN_SCREEN.getState();
    }

    public static boolean shouldStart(GameState state, boolean populated, boolean scanning) {
        return cacheReady(state) && !populated && !scanning;
    }
}
