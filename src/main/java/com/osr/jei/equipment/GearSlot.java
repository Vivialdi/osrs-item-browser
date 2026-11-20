package com.osr.jei.equipment;

import net.runelite.api.EquipmentInventorySlot;

import java.util.Locale;

/** Wiki slot text and the worn-container index it occupies. */
public enum GearSlot {
    HEAD("Head", EquipmentInventorySlot.HEAD.getSlotIdx()),
    CAPE("Cape", EquipmentInventorySlot.CAPE.getSlotIdx()),
    AMULET("Amulet", EquipmentInventorySlot.AMULET.getSlotIdx()),
    WEAPON("Weapon", EquipmentInventorySlot.WEAPON.getSlotIdx()),
    BODY("Body", EquipmentInventorySlot.BODY.getSlotIdx()),
    SHIELD("Shield", EquipmentInventorySlot.SHIELD.getSlotIdx()),
    LEGS("Legs", EquipmentInventorySlot.LEGS.getSlotIdx()),
    GLOVES("Gloves", EquipmentInventorySlot.GLOVES.getSlotIdx()),
    BOOTS("Boots", EquipmentInventorySlot.BOOTS.getSlotIdx()),
    RING("Ring", EquipmentInventorySlot.RING.getSlotIdx()),
    AMMO("Ammo", EquipmentInventorySlot.AMMO.getSlotIdx());

    private final String label;
    private final int containerIndex;

    GearSlot(String label, int containerIndex) {
        this.label = label;
        this.containerIndex = containerIndex;
    }

    public int containerIndex() {
        return containerIndex;
    }

    @Override
    public String toString() {
        return label;
    }

    public static GearSlot fromWiki(String slot) {
        if (slot == null) {
            return null;
        }
        String key = slot.trim().toLowerCase(Locale.ROOT);
        if (key.equals("2h") || key.startsWith("2h ") || key.contains("two-hand") || key.contains("two hand")) {
            return WEAPON;
        }
        if (key.equals("head") || key.equals("helmet")) {
            return HEAD;
        }
        if (key.equals("cape")) {
            return CAPE;
        }
        if (key.equals("neck") || key.equals("amulet")) {
            return AMULET;
        }
        if (key.equals("weapon")) {
            return WEAPON;
        }
        if (key.equals("body") || key.equals("torso")) {
            return BODY;
        }
        if (key.equals("shield")) {
            return SHIELD;
        }
        if (key.equals("legs")) {
            return LEGS;
        }
        if (key.equals("hands") || key.equals("gloves")) {
            return GLOVES;
        }
        if (key.equals("feet") || key.equals("boots")) {
            return BOOTS;
        }
        if (key.equals("ring") || key.equals("finger")) {
            return RING;
        }
        if (key.equals("ammo") || key.equals("ammunition")) {
            return AMMO;
        }
        return null;
    }

    public static GearSlot fromContainerIndex(int index) {
        for (GearSlot slot : values()) {
            if (slot.containerIndex == index) {
                return slot;
            }
        }
        return null;
    }
}
