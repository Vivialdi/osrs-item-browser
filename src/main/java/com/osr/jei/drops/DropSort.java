package com.osr.jei.drops;

/** How the drop list is ordered. The choice is stored in config. */
public enum DropSort {
    RATE("Rate"),
    COMBAT("Combat level"),
    NAME("Name");

    private final String label;

    DropSort(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
