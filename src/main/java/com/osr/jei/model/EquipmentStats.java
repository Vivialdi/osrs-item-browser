package com.osr.jei.model;

/**
 * Combat equipment bonuses parsed from {@code {{Infobox Bonuses}}} on the OSRS Wiki.
 *
 * All integer fields mirror the signed values shown in-game
 * (e.g. {@code +10}, {@code -4}, {@code 0}).
 * {@code magicDmg} is the percentage bonus stored as a plain integer
 * (e.g. {@code 1} means +1 %).
 */
public class EquipmentStats {

    // ── Attack bonuses ─────────────────────────────────────────────────────────
    public final int attackStab;
    public final int attackSlash;
    public final int attackCrush;
    public final int attackMagic;
    public final int attackRange;

    // ── Defence bonuses ────────────────────────────────────────────────────────
    public final int defStab;
    public final int defSlash;
    public final int defCrush;
    public final int defMagic;
    public final int defRange;

    // ── Other bonuses ──────────────────────────────────────────────────────────
    public final int meleeStr;   // Melee strength bonus
    public final int rangedStr;  // Ranged strength bonus
    public final int magicDmg;   // Magic damage % (integer, e.g. 1 = +1 %)
    public final int prayer;     // Prayer bonus

    // ── Meta ───────────────────────────────────────────────────────────────────
    public final String slot;    // e.g. "Weapon", "2h sword", "Head", "Body" …
    public final int    speed;   // Attack speed in ticks (0 if not a weapon)

    public EquipmentStats(
            int attackStab, int attackSlash, int attackCrush, int attackMagic, int attackRange,
            int defStab,    int defSlash,    int defCrush,    int defMagic,    int defRange,
            int meleeStr, int rangedStr, int magicDmg, int prayer,
            String slot, int speed) {

        this.attackStab  = attackStab;
        this.attackSlash = attackSlash;
        this.attackCrush = attackCrush;
        this.attackMagic = attackMagic;
        this.attackRange = attackRange;
        this.defStab     = defStab;
        this.defSlash    = defSlash;
        this.defCrush    = defCrush;
        this.defMagic    = defMagic;
        this.defRange    = defRange;
        this.meleeStr    = meleeStr;
        this.rangedStr   = rangedStr;
        this.magicDmg    = magicDmg;
        this.prayer      = prayer;
        this.slot        = slot;
        this.speed       = speed;
    }
}
