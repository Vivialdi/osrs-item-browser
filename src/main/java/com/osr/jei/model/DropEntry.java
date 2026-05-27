package com.osr.jei.model;

import lombok.Data;

/**
 * Represents one row in a drop table.
 * e.g. "Abyssal demon drops Abyssal whip (1) at rate 1/512"
 */
@Data
public class DropEntry {
    private String source;    // Monster or NPC name
    private String level;     // Combat level, e.g. "50" or "2-13"
    private String quantity;  // e.g. "1" or "1-5"
    private String rate;      // e.g. "1/512"
    private String rarity;    // "Always", "Common", "Uncommon", "Rare", "Very rare"
}
