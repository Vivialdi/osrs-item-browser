package com.osr.jei.map;

/** Drawn size of a world map pin. Medium matches the original 16 pixel icon. */
public enum PinSize {
    SMALL("Small", 12),
    MEDIUM("Medium", 16),
    LARGE("Large", 24);

    private final String label;
    private final int pixels;

    PinSize(String label, int pixels) {
        this.label = label;
        this.pixels = pixels;
    }

    public int pixels() {
        return pixels;
    }

    @Override
    public String toString() {
        return label;
    }
}
