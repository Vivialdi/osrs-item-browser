package com.osr.jei.ironman;

/**
 * Account-type values of {@code VarbitID.IRONMAN} (varbit 1777), the
 * non-deprecated replacement for {@code client.getAccountType()}.
 *
 * <p>0 normal, 1 ironman, 2 ultimate, 3 hardcore, 4 group, 5 hardcore group,
 * 6 unranked group. Values 1 through 6 cannot use the Grand Exchange.
 */
public final class IronmanAccounts {

    public static final int NORMAL = 0;
    public static final int IRONMAN = 1;
    public static final int ULTIMATE = 2;
    public static final int HARDCORE = 3;
    public static final int GROUP = 4;
    public static final int HARDCORE_GROUP = 5;
    public static final int UNRANKED_GROUP = 6;

    private IronmanAccounts() {
    }

    public static boolean isIronman(int accountType) {
        return accountType >= IRONMAN && accountType <= UNRANKED_GROUP;
    }

    /** Effective ironman layout. A null mode is treated as off. */
    public static boolean effective(IronmanMode mode, int accountType) {
        if (mode == IronmanMode.ON) {
            return true;
        }
        if (mode == IronmanMode.AUTO) {
            return isIronman(accountType);
        }
        return false;
    }

    /** The open detail view is rebuilt only when this flips. */
    public static boolean shouldRefreshDetail(boolean previous, boolean next) {
        return previous != next;
    }
}
