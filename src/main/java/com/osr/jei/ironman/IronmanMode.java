package com.osr.jei.ironman;

/**
 * How the sidebar treats Grand Exchange trade.
 *
 * <p>{@link #AUTO} is the default. It follows the logged-in account: normal
 * accounts keep the standard layout, and Ironman, Hardcore, Ultimate, Group,
 * Hardcore Group, and Unranked Group accounts put shops, drops, and crafting
 * ahead of Grand Exchange info. The login screen has no account yet, so AUTO
 * stays off until the game reports one. {@link #ON} and {@link #OFF} override
 * that, including at the login screen.
 */
public enum IronmanMode {
    OFF("Off"),
    AUTO("Auto"),
    ON("On");

    private final String label;

    IronmanMode(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
