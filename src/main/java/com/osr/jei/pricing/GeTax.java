package com.osr.jei.pricing;

import net.runelite.api.gameval.ItemID;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Grand Exchange convenience fee: 2% of the sell price, rounded down, capped
 * at 5,000,000 coins per item. Items under 50 coins tax to zero because of
 * that rounding. Exempt ids follow the OSRS Wiki "Exempt from tax" list:
 * bonds, every energy-potion dose, the named early-game supplies, games
 * necklace(8), and ring of dueling(8).
 */
public final class GeTax {

    public static final int PERCENT = 2;
    public static final long CAP = 5_000_000L;

    /**
     * Tradeable ids from the wiki list. Energy potions are every dose.
     * Games necklace and ring of dueling are the fully charged items the
     * wiki names. Watering can is the empty can.
     */
    private static final Set<Integer> EXEMPT = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        ItemID.OSRS_BOND,
        ItemID._1DOSE1ENERGY,
        ItemID._2DOSE1ENERGY,
        ItemID._3DOSE1ENERGY,
        ItemID._4DOSE1ENERGY,
        ItemID.BRONZE_ARROW,
        ItemID.BRONZE_DART,
        ItemID.IRON_ARROW,
        ItemID.IRON_DART,
        ItemID.MINDRUNE,
        ItemID.STEEL_ARROW,
        ItemID.STEEL_DART,
        ItemID.BASS,
        ItemID.BREAD,
        ItemID.CAKE,
        ItemID.COOKED_CHICKEN,
        ItemID.COOKED_MEAT,
        ItemID.HERRING,
        ItemID.LOBSTER,
        ItemID.MACKEREL,
        ItemID.MEAT_PIE,
        ItemID.PIKE,
        ItemID.SALMON,
        ItemID.SHRIMP,
        ItemID.TUNA,
        ItemID.POH_TABLET_ARDOUGNETELEPORT,
        ItemID.POH_TABLET_CAMELOTTELEPORT,
        ItemID.POH_TABLET_FORTISTELEPORT,
        ItemID.POH_TABLET_FALADORTELEPORT,
        ItemID.NECKLACE_OF_MINIGAMES_8,
        ItemID.POH_TABLET_KOURENDTELEPORT,
        ItemID.POH_TABLET_LUMBRIDGETELEPORT,
        ItemID.RING_OF_DUELING_8,
        ItemID.POH_TABLET_TELEPORTTOHOUSE,
        ItemID.POH_TABLET_VARROCKTELEPORT,
        ItemID.CHISEL,
        ItemID.TROWEL,
        ItemID.GLASSBLOWINGPIPE,
        ItemID.HAMMER,
        ItemID.NEEDLE,
        ItemID.PESTLE_AND_MORTAR,
        ItemID.RAKE,
        ItemID.POH_SAW,
        ItemID.SECATEURS,
        ItemID.DIBBER,
        ItemID.SHEARS,
        ItemID.SPADE,
        ItemID.WATERING_CAN_0
    )));

    private GeTax() {
    }

    public static boolean exempt(int itemId) {
        return EXEMPT.contains(itemId);
    }

    /** Tax taken from one item sold at {@code sellPrice}. Zero when exempt. */
    public static long tax(long sellPrice, boolean exempt) {
        if (exempt || sellPrice <= 0L) {
            return 0L;
        }
        long raw = sellPrice * PERCENT / 100L;
        if (raw <= 0L) {
            return 0L;
        }
        return Math.min(CAP, raw);
    }

    /**
     * Coins the seller keeps. Null when either instant price is unknown,
     * so a missing side is never turned into a margin.
     */
    public static Long margin(long instantBuy, long instantSell, boolean exempt) {
        if (instantBuy <= 0L || instantSell <= 0L) {
            return null;
        }
        return instantSell - tax(instantSell, exempt) - instantBuy;
    }

    /** Margin times the 4-hour buy limit. Null when either input is missing. */
    public static Long perLimit(Long margin, Integer buyLimit) {
        if (margin == null || buyLimit == null || buyLimit <= 0) {
            return null;
        }
        return margin * buyLimit.longValue();
    }
}
