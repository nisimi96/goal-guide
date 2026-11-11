package com.goalguide.guide;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.goalguide.items.ItemData;
import com.goalguide.items.ItemTree;
import com.goalguide.know.AccountState;

/**
 * Where to go next to make an item: the bank for what you lack on you, then each place a recipe is made at,
 * deepest first (no steel bar: bank for the ore, then a furnace, then the anvil). One stop at a time; the
 * next one shows once the last is done. Recipes, tools and places are the item data's (OSRS Wiki). Pure.
 */
public final class Trip
{
    private Trip() { }

    /** Deepest made-from level followed (steel dagger, steel bar, ore). */
    static final int DEPTH = 3;
    public static final String BANK = "Bank";

    /** One stop: the place ("Bank", "Anvil", "Furnace") and what to do there. */
    public static final class Stop
    {
        public final String place, say;

        Stop(String place, String say)
        {
            this.place = place;
            this.say = say;
        }

        @Override
        public boolean equals(Object o)
        {
            return o instanceof Stop && ((Stop) o).place.equals(place) && ((Stop) o).say.equals(say);
        }

        @Override
        public int hashCode()
        {
            return place.hashCode() * 31 + say.hashCode();
        }

        @Override
        public String toString()
        {
            return place + ": " + say;
        }
    }

    /** The next stop to make qty of the item, or null when there is none (you have it, or it isn't made at a place). */
    public static Stop next(ItemData d, ItemTree.Account a, String item, int qty, Map<String, Map<String, Integer>> storages)
    {
        Map<String, Integer> take = new LinkedHashMap<>();
        boolean[] blocked = {false};
        Stop s = make(d, a, item, qty, storages, take, blocked, 0);
        // Something you have nowhere and can't make here: buy or gather it first (the guide's step says how)
        if (blocked[0]) return null;
        if (!take.isEmpty())
        {
            List<String> what = new ArrayList<>();
            take.forEach((n, q) -> what.add(q + " " + n));
            return new Stop(BANK, "take " + String.join(", ", what));
        }
        return s;
    }

    /**
     * The stop that makes qty of the item, with what to take from the bank first added to take. Null when the
     * item is on you or only needs taking; blocked is set when it can't be got here.
     */
    private static Stop make(ItemData d, ItemTree.Account a, String item, int qty, Map<String, Map<String, Integer>> storages,
        Map<String, Integer> take, boolean[] blocked, int depth)
    {
        if (qty <= 0 || carried(item, storages) >= qty) return null;
        ItemTree.Way w = depth < DEPTH ? plain(ItemTree.trace(d, a, item)) : null;
        int stored = total(item, storages);
        // Not made, or you have enough in the bank already: take it
        if (w == null || stored >= qty)
        {
            if (stored >= qty) take.merge(item, qty - carried(item, storages), Integer::sum);
            else blocked[0] = true;
            return null;
        }
        int makes = qty - stored;
        Stop deeper = null;
        if (w.tools != null)
            for (String t : w.tools.split(",\\s*"))
                if (carried(t, storages) >= 1) continue;
                else if (total(t, storages) >= 1) take.merge(t, 1, Integer::sum);
                else blocked[0] = true;
        for (Object[] m : w.materials)
        {
            String name = (String) m[0];
            int need = (int) Math.ceil(makes * (Double) m[1]);
            Stop s = make(d, a, name, need, storages, take, blocked, depth + 1);
            if (deeper == null) deeper = s;
        }
        if (deeper != null || !take.isEmpty()) return deeper;
        // Everything is on you: go make it
        if (w.facility == null) return null;
        return new Stop(w.facility, verb(w.facility) + " " + makes + " " + item);
    }

    /**
     * The everyday recipe: one made at a place, at a plain anvil or furnace ("Normal furnace", not the Blast
     * Furnace, a catalysed bar or Superheat), else the cheapest. A level still to train doesn't count against
     * it: the guide trains it first. Null when it isn't made.
     */
    static ItemTree.Way plain(ItemTree.Node n)
    {
        ItemTree.Way pick = null;
        int best = -1;
        for (ItemTree.Way w : n.ways)
        {
            if (w.kind != ItemTree.Kind.RECIPE) continue;
            String l = w.label == null ? "" : w.label.toLowerCase(Locale.ROOT);
            int score = (w.facility != null ? 2 : 0) + (l.startsWith("make") || l.startsWith("normal") ? 1 : 0);
            if (score > best || score == best && w.cost < pick.cost)
            {
                pick = w;
                best = score;
            }
        }
        return pick;
    }

    private static String verb(String facility)
    {
        switch (facility.toLowerCase(Locale.ROOT))
        {
            case "anvil": return "smith";
            case "furnace": return "smelt";
            case "range": return "cook";
            default: return "make";
        }
    }

    /** How many are on you (inventory, worn, follower), of the item or its kind. */
    static int carried(String item, Map<String, Map<String, Integer>> storages)
    {
        return count(item, storages, true);
    }

    static int total(String item, Map<String, Map<String, Integer>> storages)
    {
        return count(item, storages, false);
    }

    private static int count(String item, Map<String, Map<String, Integer>> storages, boolean onYou)
    {
        String k = ItemData.key(item);
        int n = 0;
        for (Map.Entry<String, Map<String, Integer>> st : storages.entrySet())
        {
            boolean on = st.getKey().equals(AccountState.INVENTORY) || st.getKey().equals(AccountState.WORN)
                || st.getKey().equals(AccountState.FOLLOWER);
            if (onYou && !on) continue;
            for (Map.Entry<String, Integer> it : st.getValue().entrySet())
                if (it.getValue() != null && it.getValue() > 0 && StepItems.matches(it.getKey(), k)) n += it.getValue();
        }
        return n;
    }
}
