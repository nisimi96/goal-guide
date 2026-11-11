package com.goalguide.guide;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import com.goalguide.goals.Planner;
import com.goalguide.items.ItemData;
import com.goalguide.know.AccountState;

/**
 * The item list of a step, each item the step uses, green when carried
 * (inventory or worn), yellow when it is in the bank or another storage, red when missing, with where to get it.
 * A tool named by kind ("pickaxe", "axe") counts any item of that kind. Pure.
 */
public final class StepItems
{
    private StepItems() { }

    public enum Has { CARRIED, STORED, MISSING, REMINDER, USED }

    public static final class Item
    {
        /** As the step names it ("pickaxe", "Raw shrimps"). */
        public final String name;
        public final int need;
        /** An item used up: {@link #need} is about this many an hour. */
        public final boolean perHour;
        public final Has has;
        /** Carried, and in every storage. */
        public final int carried, total;
        /** The storages that hold it ("bank", "seed vault"), the items that matched ("rune pickaxe"). */
        public final List<String> where, matched;
        /** Missing: where to get it, null when not known. */
        public final String from;

        Item(String name, int need, boolean perHour, Has has, int carried, int total, List<String> where, List<String> matched, String from)
        {
            this.name = name;
            this.need = need;
            this.perHour = perHour;
            this.has = has;
            this.carried = carried;
            this.total = total;
            this.where = where;
            this.matched = matched;
            this.from = from;
        }

        /** "Raw shrimps: 12 in the bank", "pickaxe: carried (rune pickaxe)", "Ghostspeak amulet: missing, Restless Ghost". */
        public String say()
        {
            String n = (need > 1 && !perHour ? need + " x " : "") + name;
            String m = matched.size() == 1 && !matched.get(0).equals(ItemData.key(name)) ? " (" + matched.get(0) + ")" : "";
            switch (has)
            {
                case CARRIED: return n + m + ": carried" + (perHour ? ", about " + need + " an hour" : "");
                case STORED: return n + m + ": " + (carried > 0 ? carried + " carried, " : "") + "in the " + String.join(", ", where)
                    + (perHour ? " (" + total + ", about " + need + " an hour)" : total > 1 ? " (" + total + ")" : "");
                case REMINDER: return "Bring: " + name;
                case USED: return n + ": maybe used in the quest already";
                default: return n + ": missing" + (total > 0 ? " (have " + total + ")" : "") + (from == null ? "" : ", " + from);
            }
        }
    }

    /** Names that are no item (a method's "food" means whatever the player eats). */
    static final Set<String> NOT_ITEMS = Set.of("food", "tools", "-");

    /**
     * The item list for a step.
     * @param storages storage -> lower-case item -> count (AccountState.items())
     * @param from     missing item -> where to get it (may return null)
     */
    public static List<Item> of(Planner.Step s, Map<String, Map<String, Integer>> storages, Function<String, String> from)
    {
        List<Item> out = new ArrayList<>();
        if (s == null) return out;
        Set<String> seen = new LinkedHashSet<>();
        for (Planner.Use u : s.uses)
        {
            String k = ItemData.key(u.item);
            if (NOT_ITEMS.contains(k) || !seen.add(k)) continue;
            if (u.reminder)
            {
                out.add(new Item(u.item, u.amount, false, Has.REMINDER, 0, 0, List.of(), List.of(), null));
                continue;
            }
            int carried = 0, total = 0;
            Set<String> where = new LinkedHashSet<>(), matched = new LinkedHashSet<>();
            for (Map.Entry<String, Map<String, Integer>> st : storages.entrySet())
                for (Map.Entry<String, Integer> it : st.getValue().entrySet())
                {
                    if (it.getValue() == null || it.getValue() <= 0 || !matches(it.getKey(), k)) continue;
                    total += it.getValue();
                    matched.add(it.getKey());
                    boolean on = st.getKey().equals(AccountState.INVENTORY) || st.getKey().equals(AccountState.WORN)
                        || st.getKey().equals(AccountState.FOLLOWER);
                    if (on) carried += it.getValue();
                    else where.add(st.getKey());
                }
            // An item used up is "carried" with some on you; a tool or quest item needs the full count
            int need = u.perHour ? 1 : u.amount;
            Has has = carried >= need ? Has.CARRIED : total >= need ? Has.STORED : Has.MISSING;
            // A quest already started may have used it up; not a missing item to fetch again
            if (has == Has.MISSING && s.started && !u.perHour) has = Has.USED;
            out.add(new Item(u.item, u.amount, u.perHour, has, carried, total, new ArrayList<>(where), new ArrayList<>(matched),
                has == Has.MISSING && from != null ? from.apply(u.item) : null));
        }
        return out;
    }

    /** The item itself, or one of its kind: "rune pickaxe" for "pickaxe", "dragon axe" for "axe" (not "pickaxe"). */
    static boolean matches(String held, String want)
    {
        return ItemData.sameKind(held, want);
    }
}
