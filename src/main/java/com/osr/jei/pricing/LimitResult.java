package com.osr.jei.pricing;

/** One attempt to read the shared buy-limit list. */
public final class LimitResult {

    public final BuyLimitSnapshot snapshot;
    public final boolean failed;

    public LimitResult(BuyLimitSnapshot snapshot, boolean failed) {
        this.snapshot = snapshot == null ? BuyLimitSnapshot.empty() : snapshot;
        this.failed = failed;
    }
}
