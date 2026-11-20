package com.osr.jei.equipment;

import com.osr.jei.model.EquipmentStats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Bonus deltas against what is worn. A two-handed weapon replaces both the
 * weapon and the shield. Equipping a shield or a one-handed weapon while a
 * two-handed weapon is worn removes that weapon and does not also subtract a shield.
 */
public final class EquipmentCompare {

    private EquipmentCompare() {
    }

    /** One worn item's parsed bonuses. {@code weight} is NaN when the page has no weight. */
    public static final class Piece {
        public final EquipmentStats stats;
        public final double weight;

        public Piece(EquipmentStats stats, double weight) {
            this.stats = stats;
            this.weight = weight;
        }

        public boolean twoHanded() {
            return stats != null && stats.twoHanded();
        }
    }

    public static final class Delta {
        public final String label;
        public final double amount;
        public final boolean lowerIsBetter;
        public final boolean known;

        Delta(String label, double amount, boolean lowerIsBetter, boolean known) {
            this.label = label;
            this.amount = amount;
            this.lowerIsBetter = lowerIsBetter;
            this.known = known;
        }

        /** 1 when the change is better, -1 when it is worse, 0 when it is zero or unknown. */
        public int sign() {
            if (!known || amount == 0) {
                return 0;
            }
            boolean better = lowerIsBetter ? amount < 0 : amount > 0;
            return better ? 1 : -1;
        }
    }

    public static final class Result {
        public final String note;
        public final List<Delta> deltas;

        Result(String note, List<Delta> deltas) {
            this.note = note;
            this.deltas = deltas;
        }
    }

    public static Result compare(EquipmentStats item, double itemWeight, Piece weapon, Piece shield, Piece sameSlot) {
        if (item == null) {
            return new Result("", Collections.<Delta>emptyList());
        }
        GearSlot slot = GearSlot.fromWiki(item.slot);
        List<Piece> baseline = new ArrayList<>();
        String note = "Nothing in that slot";
        if (slot == GearSlot.WEAPON && item.twoHanded()) {
            if (weapon != null && weapon.twoHanded()) {
                baseline.add(weapon);
                note = "Replaces your two-handed weapon";
            } else {
                if (weapon != null) {
                    baseline.add(weapon);
                }
                if (shield != null) {
                    baseline.add(shield);
                }
                if (weapon != null && shield != null) {
                    note = "Replaces your weapon and shield";
                } else if (weapon != null) {
                    note = "Replaces your weapon";
                } else if (shield != null) {
                    note = "Replaces your shield";
                }
            }
        } else if (slot == GearSlot.WEAPON) {
            if (weapon != null) {
                baseline.add(weapon);
                note = weapon.twoHanded() ? "Replaces your two-handed weapon" : "Compared with your weapon";
            }
        } else if (slot == GearSlot.SHIELD) {
            if (weapon != null && weapon.twoHanded()) {
                baseline.add(weapon);
                note = "Replaces your two-handed weapon";
            } else if (shield != null) {
                baseline.add(shield);
                note = "Compared with your shield";
            }
        } else if (sameSlot != null) {
            baseline.add(sameSlot);
            note = "Compared with what you have equipped";
        }
        boolean includeSpeed = item.speed > 0 || slot == GearSlot.WEAPON;
        return new Result(note, deltas(item, itemWeight, baseline, includeSpeed));
    }

    private static List<Delta> deltas(EquipmentStats item, double itemWeight, List<Piece> baseline, boolean includeSpeed) {
        int[] worn = new int[14];
        double wornWeight = 0;
        boolean weightKnown = !Double.isNaN(itemWeight);
        int wornSpeed = 0;
        boolean sawWeaponSpeed = false;
        for (Piece piece : baseline) {
            if (piece == null || piece.stats == null) {
                continue;
            }
            EquipmentStats stats = piece.stats;
            worn[0] += stats.attackStab;
            worn[1] += stats.attackSlash;
            worn[2] += stats.attackCrush;
            worn[3] += stats.attackMagic;
            worn[4] += stats.attackRange;
            worn[5] += stats.defStab;
            worn[6] += stats.defSlash;
            worn[7] += stats.defCrush;
            worn[8] += stats.defMagic;
            worn[9] += stats.defRange;
            worn[10] += stats.meleeStr;
            worn[11] += stats.rangedStr;
            worn[12] += stats.magicDmg;
            worn[13] += stats.prayer;
            if (Double.isNaN(piece.weight)) {
                weightKnown = false;
            } else if (weightKnown) {
                wornWeight += piece.weight;
            }
            if (stats.speed > 0) {
                wornSpeed = stats.speed;
                sawWeaponSpeed = true;
            }
        }
        int[] itemStats = {
            item.attackStab, item.attackSlash, item.attackCrush, item.attackMagic, item.attackRange,
            item.defStab, item.defSlash, item.defCrush, item.defMagic, item.defRange,
            item.meleeStr, item.rangedStr, item.magicDmg, item.prayer
        };
        String[] labels = {
            "Stab atk", "Slash atk", "Crush atk", "Magic atk", "Range atk",
            "Stab def", "Slash def", "Crush def", "Magic def", "Range def",
            "Strength", "Ranged str", "Magic dmg", "Prayer"
        };
        List<Delta> lines = new ArrayList<>();
        for (int i = 0; i < itemStats.length; i++) {
            lines.add(new Delta(labels[i], itemStats[i] - worn[i], false, true));
        }
        if (weightKnown) {
            lines.add(new Delta("Weight", itemWeight - wornWeight, true, true));
        }
        if (includeSpeed && (item.speed > 0 || sawWeaponSpeed)) {
            lines.add(new Delta("Speed", item.speed - wornSpeed, true, true));
        }
        return lines;
    }
}
