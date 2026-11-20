package com.osr.jei.pricing;

/**
 * High-alchemy profit. A missing GE price or nature-rune price never becomes
 * a fabricated profit; only the high-alch value is reported in that case.
 */
public final class Alchemy {

    public static final class Result {
        public final int haValue;
        /** Null when profit cannot be computed honestly. */
        public final Long profit;

        public Result(int haValue, Long profit) {
            this.haValue = haValue;
            this.profit = profit;
        }
    }

    private Alchemy() {
    }

    /**
     * @param haPrice     high-alch value from the game cache; {@code <= 0} means not alchable
     * @param geBuyPrice  what the player would pay on the GE, or {@code <= 0} if unknown
     * @param naturePrice nature rune cost, or {@code <= 0} if unknown
     * @return null when the item has no high-alch value
     */
    public static Result compute(int haPrice, long geBuyPrice, long naturePrice) {
        if (haPrice <= 0) {
            return null;
        }
        if (geBuyPrice <= 0 || naturePrice <= 0) {
            return new Result(haPrice, null);
        }
        return new Result(haPrice, haPrice - geBuyPrice - naturePrice);
    }
}
