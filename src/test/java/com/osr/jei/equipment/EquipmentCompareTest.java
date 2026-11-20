package com.osr.jei.equipment;

import com.osr.jei.model.EquipmentStats;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EquipmentCompareTest {

    @Test
    public void twoHandedReplacesWeaponAndShield() {
        EquipmentStats godsword = stats("2h", 132, 80, 6);
        EquipmentStats whip = stats("weapon", 82, 82, 4);
        EquipmentStats defender = stats("shield", 25, 6, 0);
        EquipmentCompare.Piece weapon = new EquipmentCompare.Piece(whip, 0.453);
        EquipmentCompare.Piece shield = new EquipmentCompare.Piece(defender, 0.453);

        EquipmentCompare.Result result = EquipmentCompare.compare(godsword, 10, weapon, shield, null);
        assertEquals("Replaces your weapon and shield", result.note);
        assertEquals(132 - 82 - 25, delta(result, "Slash atk"), 0);
        assertEquals(80 - 82 - 6, delta(result, "Strength"), 0);
        assertEquals(6 - 4, delta(result, "Speed"), 0);
        assertEquals(-1, deltaNamed(result, "Speed").sign());
        assertEquals(10 - 0.453 - 0.453, delta(result, "Weight"), 0.0001);
        assertEquals(-1, deltaNamed(result, "Weight").sign());
    }

    @Test
    public void wornTwoHandedIsNotAddedToAShield() {
        EquipmentStats incomingShield = stats("shield", 10, 0, 0);
        EquipmentStats godsword = stats("2h", 132, 80, 6);
        EquipmentStats decoy = stats("shield", 500, 500, 0);
        EquipmentCompare.Piece weapon = new EquipmentCompare.Piece(godsword, 10);
        EquipmentCompare.Piece shield = new EquipmentCompare.Piece(decoy, 5);

        EquipmentCompare.Result asShield = EquipmentCompare.compare(incomingShield, 2.5, weapon, shield, shield);
        assertEquals("Replaces your two-handed weapon", asShield.note);
        assertEquals(10 - 132, delta(asShield, "Slash atk"), 0);
        assertTrue(asShield.deltas.stream().noneMatch(delta -> "Speed".equals(delta.label)));

        EquipmentStats another2h = stats("2h", 140, 90, 6);
        EquipmentCompare.Result as2h = EquipmentCompare.compare(another2h, 9, weapon, shield, null);
        assertEquals("Replaces your two-handed weapon", as2h.note);
        assertEquals(140 - 132, delta(as2h, "Slash atk"), 0);
        assertEquals(1, deltaNamed(as2h, "Slash atk").sign());
    }

    @Test
    public void oneHandedWeaponLeavesTheShieldAlone() {
        EquipmentStats scimitar = stats("weapon", 67, 66, 4);
        EquipmentStats whip = stats("weapon", 82, 82, 4);
        EquipmentStats defender = stats("shield", 25, 6, 0);
        EquipmentCompare.Result result = EquipmentCompare.compare(
            scimitar,
            1.8,
            new EquipmentCompare.Piece(whip, 0.453),
            new EquipmentCompare.Piece(defender, 0.453),
            null
        );
        assertEquals("Compared with your weapon", result.note);
        assertEquals(67 - 82, delta(result, "Slash atk"), 0);
        assertEquals(0, delta(result, "Speed"), 0);
    }

    @Test
    public void emptySlotComparesAgainstZerosAndLighterIsBetter() {
        EquipmentStats helm = stats("head", 0, 0, 0);
        EquipmentCompare.Result result = EquipmentCompare.compare(helm, 1.0, null, null, null);
        assertEquals("Nothing in that slot", result.note);
        assertEquals(0, delta(result, "Slash atk"), 0);
        EquipmentCompare.Delta weight = deltaNamed(result, "Weight");
        assertEquals(1.0, weight.amount, 0);
        assertEquals(-1, weight.sign());

        EquipmentStats worn = stats("head", 0, 0, 0);
        EquipmentCompare.Result lighter = EquipmentCompare.compare(
            helm, 0.5, null, null, new EquipmentCompare.Piece(worn, 2.0));
        assertEquals(1, deltaNamed(lighter, "Weight").sign());
    }

    @Test
    public void gearSlotIndexesMatchTheWornContainer() {
        assertEquals(net.runelite.api.EquipmentInventorySlot.HEAD.getSlotIdx(), GearSlot.HEAD.containerIndex());
        assertEquals(net.runelite.api.EquipmentInventorySlot.WEAPON.getSlotIdx(), GearSlot.WEAPON.containerIndex());
        assertEquals(net.runelite.api.EquipmentInventorySlot.SHIELD.getSlotIdx(), GearSlot.SHIELD.containerIndex());
        assertEquals(GearSlot.WEAPON, GearSlot.fromWiki("2h"));
        assertTrue(stats("2h", 1, 1, 6).twoHanded());
        assertTrue(!stats("weapon", 1, 1, 4).twoHanded());
    }

    private static double delta(EquipmentCompare.Result result, String label) {
        return deltaNamed(result, label).amount;
    }

    private static EquipmentCompare.Delta deltaNamed(EquipmentCompare.Result result, String label) {
        for (EquipmentCompare.Delta delta : result.deltas) {
            if (label.equals(delta.label)) {
                return delta;
            }
        }
        throw new AssertionError(label);
    }

    private static EquipmentStats stats(String slot, int slash, int strength, int speed) {
        return new EquipmentStats(
            0, slash, 0, 0, 0,
            0, 0, 0, 0, 0,
            strength, 0, 0, 0,
            slot, speed
        );
    }
}
