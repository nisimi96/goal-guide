package com.goalguide.facts;

/**
 * Rates measured for this player, when another plugin supplies them: per hour of a method (by its row name) for xp
 * or an item. The planner uses a measured rate instead of the table's typical one. None supplied: the tables' rates.
 */
public final class MeasuredRates
{
    private MeasuredRates() { }

    public interface Source
    {
        /** Per hour of {@code item} ("xp", "raw lobster") for this skill's method, or -1 when not measured. */
        int perHour(String skill, String method, String item);
    }

    private static volatile Source source = (skill, method, item) -> -1;

    public static void set(Source s)
    {
        source = s == null ? (skill, method, item) -> -1 : s;
    }

    public static int perHour(String skill, String method, String item)
    {
        return source.perHour(skill, method, item);
    }
}
