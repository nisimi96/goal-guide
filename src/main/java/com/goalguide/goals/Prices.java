package com.goalguide.goals;

import java.util.function.ToIntFunction;

/**
 * Grand Exchange prices for main accounts (USER 2026-11-11: a main buys first, makes second). The plugin sets the
 * lookup from RuneLite's own item prices; without it nothing is priced and the planner shows the ways to make.
 */
public final class Prices
{
    private Prices() { }

    private static volatile ToIntFunction<String> lookup;

    /** {@code f}: lower-case item name -> gp each, 0 or less when not known. Null switches prices off. */
    public static void set(ToIntFunction<String> f)
    {
        lookup = f;
    }

    /** Gp each, 0 when not known or not tradeable. */
    public static int each(String item)
    {
        ToIntFunction<String> f = lookup;
        if (f == null || item == null) return 0;
        try { return Math.max(0, f.applyAsInt(item)); }
        catch (RuntimeException e) { return 0; }
    }

    /** "950 gp", "12.5k", "3.2m". */
    public static String gp(long n)
    {
        if (n < 10_000) return String.format("%,d gp", n);
        if (n < 1_000_000) return String.format("%.1fk", n / 1000.0).replace(".0k", "k");
        return String.format("%.1fm", n / 1_000_000.0).replace(".0m", "m");
    }
}
