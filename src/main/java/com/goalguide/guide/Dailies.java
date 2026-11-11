package com.goalguide.guide;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.goalguide.items.ItemTree;

/**
 * "daily shop runs": the once-a-day stock an account can claim, from the wiki's Repeatable events page
 * (read 2026-11-11). Each shows only when the account qualifies, with the amount its diaries give, and a "done today"
 * tick that clears at the daily reset (00:00 UTC). Pure.
 */
public final class Dailies
{
    private Dailies() { }

    /** One daily: the amount at each tier, and what opens that tier (a diary or quest key; null = nothing). */
    public static final class Daily
    {
        public final String key, who, where, what;
        final int[] amounts;
        final String[] needs;

        Daily(String key, String who, String where, String what, int[] amounts, String[] needs)
        {
            this.key = key;
            this.who = who;
            this.where = where;
            this.what = what;
            this.amounts = amounts;
            this.needs = needs;
        }

        /** How many the account can claim a day; 0 when it can't yet. */
        public int amount(ItemTree.Account a)
        {
            int n = 0;
            for (int i = 0; i < amounts.length; i++) if (needs[i] == null || a.questsDone.contains(needs[i])) n = amounts[i];
            return n;
        }

        /** "Zaff: 15 battlestaves (Varrock)". */
        public String say(ItemTree.Account a)
        {
            return who + ": " + amount(a) + " " + what + " (" + where + ")";
        }

        /** What opens the next tier ("Varrock medium diary for 30"), null at the top. */
        public String next(ItemTree.Account a)
        {
            int have = amount(a);
            for (int i = 0; i < amounts.length; i++)
                if (amounts[i] > have && needs[i] != null && !a.questsDone.contains(needs[i]))
                    return pretty(needs[i]) + " for " + amounts[i];
            return null;
        }
    }

    private static String[] diary(String region, int from)
    {
        String[] tiers = {"EASY", "MEDIUM", "HARD", "ELITE"};
        String[] out = new String[tiers.length - from];
        for (int i = from; i < tiers.length; i++) out[i - from] = region + "_" + tiers[i] + "_DIARY";
        return out;
    }

    private static String[] withBase(String base, String[] rest)
    {
        String[] out = new String[rest.length + 1];
        out[0] = base;
        System.arraycopy(rest, 0, out, 1, rest.length);
        return out;
    }

    public static final List<Daily> ALL = List.of(
        new Daily("zaff", "Zaff", "Varrock", "battlestaves at 7,000 gp", new int[]{5, 15, 30, 60, 120}, withBase(null, diary("VARROCK", 0))),
        new Daily("cromperty", "Wizard Cromperty", "East Ardougne", "pure essence", new int[]{100, 150, 250}, diary("ARDOUGNE", 1)),
        new Daily("bert", "Bert", "Yanille", "buckets of sand", new int[]{84}, new String[]{"THE_HAND_IN_THE_SAND"}),
        new Daily("flax", "Flax keeper", "Seers' Village", "bow strings for flax", new int[]{30, 60, 120, 250}, diary("KANDARIN", 0)),
        new Daily("rantz", "Rantz", "Feldip Hills", "ogre arrows", new int[]{25, 50, 100, 150}, diary("WESTERN", 0)),
        new Daily("robin", "Robin", "Port Phasmatys", "bones to bonemeal and slime", new int[]{13, 26, 39}, diary("MORYTANIA", 1)),
        new Daily("lundail", "Lundail", "Mage Arena bank", "random runes", new int[]{40, 80, 120, 200}, diary("WILDERNESS", 0)),
        new Daily("thirus", "Thirus", "Lovakengj", "dynamite", new int[]{20, 40, 80}, diary("KOUREND", 1)));

    /** The dailies this account can claim now. */
    public static List<Daily> open(ItemTree.Account a)
    {
        List<Daily> out = new ArrayList<>();
        for (Daily d : ALL) if (d.amount(a) > 0) out.add(d);
        return out;
    }

    /** Today in the game's daily reset (UTC). */
    public static String today(long ms)
    {
        return java.time.LocalDate.ofEpochDay(Math.floorDiv(ms, 86_400_000L)).toString();
    }

    /** The dailies ticked today, from the saved text "2026-11-11;zaff;bert". Empty when the day has passed. */
    public static Set<String> doneToday(String saved, long ms)
    {
        Set<String> out = new LinkedHashSet<>();
        if (saved == null || saved.isEmpty()) return out;
        String[] p = saved.split(";");
        if (!p[0].equals(today(ms))) return out;
        for (int i = 1; i < p.length; i++) if (!p[i].isEmpty()) out.add(p[i]);
        return out;
    }

    /** The saved text after ticking (or unticking) one daily today. */
    public static String tick(String saved, long ms, String key, boolean done)
    {
        Set<String> d = doneToday(saved, ms);
        if (done) d.add(key);
        else d.remove(key);
        StringBuilder b = new StringBuilder(today(ms));
        for (String k : d) b.append(';').append(k);
        return b.toString();
    }

    /** "VARROCK_MEDIUM_DIARY" -> "Varrock medium diary", "THE_HAND_IN_THE_SAND" -> "The Hand in the Sand". */
    static String pretty(String key)
    {
        if (!key.endsWith("_DIARY")) return com.goalguide.quests.QuestPlan.pretty(key);
        String s = key.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
