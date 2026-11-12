package com.goalguide.guide;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.goalguide.facts.Runs;
import com.goalguide.items.ItemData;

/**
 * What the bank button shows, in sections: the Now step's items (one section per item when it gets a
 * quest's items), then each run that is on.
 * A bank item counts when it is one of a section's items, or of its kind ("rune pickaxe" for "pickaxe"). Pure.
 */
public final class BankFilter
{
    private BankFilter() { }

    /** One part of the filtered bank: a title, its item names (lower case), and the step's items with counts. */
    public static final class Section
    {
        public final String title;
        public final Set<String> keys;
        /** The step's items (have and need); empty for a run. */
        public final List<StepItems.Item> items;

        Section(String title, Set<String> keys, List<StepItems.Item> items)
        {
            this.title = title;
            this.keys = keys;
            this.items = items;
        }

        @Override
        public boolean equals(Object o)
        {
            return o instanceof Section && ((Section) o).title.equals(title) && ((Section) o).keys.equals(keys);
        }

        @Override
        public int hashCode()
        {
            return title.hashCode() * 31 + keys.hashCode();
        }
    }

    public static final String TELEPORTS = "Teleports", NOT_IN_BANK = "Not in bank";

    /** The step (when it uses items) or its parts, then each run that is on, then the runs' teleports. */
    public static List<Section> sections(Guide g, Runs.Switches sw)
    {
        List<Section> out = new ArrayList<>();
        if (g != null && !g.parts.isEmpty())
        {
            // Getting a quest's items: a section per item to get, holding that item and what getting it uses
            for (Guide.Part p : g.parts)
            {
                List<String> names = new ArrayList<>();
                for (StepItems.Item i : p.items) names.add(i.name);
                out.add(new Section(p.title, keys(names), p.items));
            }
        }
        else if (g != null && g.now != null && !g.items.isEmpty())
        {
            List<String> names = new ArrayList<>();
            for (StepItems.Item i : g.items) names.add(i.name);
            out.add(new Section("Now: " + g.now.what, keys(names), g.items));
        }
        // Each run's own items, then one section of the ways to get around for all of them
        List<String> travel = new ArrayList<>();
        for (String run : sw.onRuns())
        {
            out.add(new Section(run, keys(Runs.items(run)), List.of()));
            travel.addAll(Runs.travel(run));
        }
        if (!travel.isEmpty()) out.add(new Section(TELEPORTS, keys(travel), List.of()));
        return out;
    }

    /** Kinds of item, in the order the bank shows them, each kind starting a new row. */
    static final String[][] KINDS = {
        {"spade", "rake", "dibber", "secateurs", "hammer", "chisel", "coins"},
        {"sapling"}, {"seed"}, {"(5)", "sweetcorn", "watermelon", "coconut", "cactus spine"}, {"compost"},
        {"logs"}, {"bird house", "clockwork"},
        {"teleport"}, {"ring"}, {"necklace", "amulet", "pendant", "bracelet"}, {"cape", "cloak", "gloves"},
        {"whistle", "seed pod", "talisman", "crystal", "sphere", "ectophial", "book", "memoirs"},
    };

    /** The row kind of an item, by whole words of its name; anything else last. */
    public static int kind(String name)
    {
        String k = " " + (name == null ? "" : ItemData.key(name)) + " ";
        // The last kind's words first: a "grand seed pod" is not a seed, a "teleport crystal" not a tablet
        for (int n = 0; n < KINDS.length; n++)
        {
            int i = (n + KINDS.length - 1) % KINDS.length;
            for (String w : KINDS[i])
                if (k.contains(" " + w + " ") || w.startsWith("(") && k.contains(w)) return i;
        }
        return KINDS.length;
    }

    /** Item names, lower case. */
    public static Set<String> keys(Collection<String> names)
    {
        Set<String> out = new LinkedHashSet<>();
        for (String n : names) out.add(ItemData.key(n));
        return out;
    }

    /** True when a bank item with this name is one of these. */
    public static boolean shows(String bankName, Set<String> wanted)
    {
        if (bankName == null) return false;
        String k = ItemData.key(bankName);
        for (String w : wanted) if (StepItems.matches(k, w)) return true;
        return false;
    }

    /** The first section a bank item belongs to, null when none. */
    public static Section sectionOf(String bankName, List<Section> sections)
    {
        for (Section s : sections) if (shows(bankName, s.keys)) return s;
        return null;
    }

    /** The step item a bank item is, null when none. */
    public static StepItems.Item itemOf(String bankName, List<StepItems.Item> items)
    {
        if (bankName == null) return null;
        String k = ItemData.key(bankName);
        for (StepItems.Item i : items) if (StepItems.matches(k, ItemData.key(i.name))) return i;
        return null;
    }

    /** "74/225" under a step item; a used-up item shows what you have. */
    public static String count(StepItems.Item i)
    {
        return i.perHour ? String.valueOf(i.total) : i.total + "/" + i.need;
    }

    /**
     * The bank's small text under a step item, or null for none: nothing once enough is on you; the number to
     * take when the bank has enough ("76"); have/need when short ("22/76"). Big numbers short, like the
     * bank's own ("486K", "12M").
     */
    public static String bankCount(StepItems.Item i)
    {
        if (i.perHour || i.carried >= i.need) return null;
        if (!isShort(i)) return shortNumber(i.need);
        return shortNumber(i.total) + "/" + shortNumber(i.need);
    }

    static String shortNumber(long n)
    {
        if (n >= 10_000_000) return n / 1_000_000 + "M";
        if (n >= 100_000) return n / 1_000 + "K";
        return String.valueOf(n);
    }

    /** Short: fewer than the step needs. */
    public static boolean isShort(StepItems.Item i)
    {
        return i.has == StepItems.Has.MISSING;
    }
}
