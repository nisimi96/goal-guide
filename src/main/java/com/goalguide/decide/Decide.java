package com.goalguide.decide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.goalguide.facts.Method;
import com.goalguide.facts.MethodTables;
import com.goalguide.goals.Goal;
import com.goalguide.goals.Planner;
import com.goalguide.items.ItemData;
import com.goalguide.items.ItemTree;
import com.goalguide.know.AccountState;

/**
 * What to do right now: it picks and says why.
 *
 * Rank: 1. going to waste (a grown patch or a full bird house does nothing until it is run, an empty one grows
 * nothing), 2. ready runs (a timer that comes ready during this sitting), 3. stock short (the most short first),
 * 4. the focus goal (the first goal), 5. the rest of the route in order.
 *
 * Sitting filler: fills the minutes the player has with steps in rank order, and timed runs cut in at the
 * minute they come ready.
 *
 * Guards: bank space (from the bank screen's counter) and the items the route's quests need, which must not be
 * dropped, sold or used up.
 *
 * Pure: give it the account, the route, the goals and the time; it reads no client.
 */
public final class Decide
{
    private Decide() { }

    public enum Rank { WASTE, READY, UNLOCK, STOCK, FOCUS, NEXT }

    /** Rough minutes of a run whose travel is not checked, and of the bird houses at Fossil Island. Rough. */
    static final int HERB_RUN_MIN = 10, BIRD_RUN_MIN = 5, OTHER_RUN_MIN = 10;
    /** A patch counts only when it is this quick to reach (seconds); PatchTravel.minStops says how many a run needs. */
    static final int QUICK_SECONDS = 120;
    /** Minutes at each patch: harvest, compost, plant (rough). */
    static final double PATCH_MIN = 1;
    /** Minutes per stop when travel is not known: a teleport, a short walk and the patch (rough). */
    static final double STOP_MIN = 2;

    /** Fewer free bank slots than this is a warning. */
    static final int BANK_LOW = 10;

    public static final class Pick
    {
        public final Rank rank;
        /** "Herb run", "Train Fishing 40 to 65", "Stock 580 raw karambwan". */
        public final String what;
        /** Because: "herb patches grown 2 h ago". */
        public final String why;
        /** Minutes it takes at typical rates, -1 = not known. */
        public final int minutes;
        /** A quest: the player plays it through. */
        public final boolean you;
        /** Timed runs: epoch seconds when it is ready, 0 = now. */
        public final long at;
        /** Timed runs: the timer it runs ("herb patches"), null for other picks. */
        public String timer;

        Pick(Rank rank, String what, String why, int minutes, boolean you, long at)
        {
            this.rank = rank;
            this.what = what;
            this.why = why;
            this.minutes = minutes;
            this.you = you;
            this.at = at;
        }

        /** "Herb run now, because herb patches are grown (2 h ago)." */
        public String say()
        {
            return what + " now, because " + why + ".";
        }

        @Override public String toString() { return rank + " " + what + " (" + why + ")" + (minutes >= 0 ? " " + minutes + "m" : ""); }
    }

    /** One block of a sitting: start minute from now, length, the pick. */
    public static final class Block
    {
        public final int start, minutes;
        public final Pick pick;

        Block(int start, int minutes, Pick pick)
        {
            this.start = start;
            this.minutes = minutes;
            this.pick = pick;
        }

        @Override public String toString() { return "+" + start + " " + minutes + "m " + pick.what; }
    }

    public static final class Decision
    {
        /** Best first. Empty when there is nothing to do. */
        public final List<Pick> picks = new ArrayList<>();
        /** The sitting, in order. */
        public final List<Block> sitting = new ArrayList<>();
        /** Warnings the player should see ("Only 4 bank slots free"). */
        public final List<String> guards = new ArrayList<>();
        /** Lower-case item -> the quest that needs it. Never drop, sell or use these. */
        public final Map<String, String> keep = new LinkedHashMap<>();

        /** The pick for right now: the best one that can start now (a run not ready yet can't). */
        public Pick now()
        {
            for (Pick p : picks) if (p.rank != Rank.READY) return p;
            return null;
        }
    }

    private static volatile Set<String> keepNow = Collections.emptySet();

    /** False for an item the planned quests need (from the last decision). For droppers and sellers. */
    public static boolean mayDrop(String item)
    {
        return item == null || !keepNow.contains(ItemData.key(item));
    }

    /**
     * @param timers   the account's timers (Know), name -> timer
     * @param bankFree free bank slots, -1 = not known
     * @param sitting  minutes the player has now
     */
    public static Decision decide(ItemTree.Account a, Map<String, AccountState.Timer> timers, int bankFree, Planner.Route route,
        List<Goal> goals, long nowSec, int sitting)
    {
        return decide(a, timers, Map.of(), bankFree, route, goals, nowSec, sitting);
    }

    /** The patches quick enough to be in a run (PatchTravel ticks), slowest last. Null: travel not checked yet. */
    public static List<String> quick(Map<String, Integer> ticks)
    {
        if (ticks == null || ticks.isEmpty()) return null;
        List<String> out = new ArrayList<>();
        ticks.entrySet().stream().filter(e -> e.getValue() >= 0 && e.getValue() * 0.6 <= QUICK_SECONDS)
            .sorted(Map.Entry.comparingByValue()).forEach(e -> out.add(e.getKey()));
        return out;
    }

    /**
     * @param travel timer -> patch -> ticks to reach it (PatchTravel), empty when not checked yet: a run of a kind
     *               PatchTravel knows is only offered over {@link PatchTravel#minStops} patches within {@link #QUICK_SECONDS}
     */
    public static Decision decide(ItemTree.Account a, Map<String, AccountState.Timer> timers, Map<String, Map<String, Integer>> travel, int bankFree,
        Planner.Route route, List<Goal> goals, long nowSec, int sitting)
    {
        return decide(a, timers, travel, bankFree, route, goals, nowSec, sitting, null);
    }

    /**
     * @param prefs the Runs tab switched-off runs are skipped, switched-off stops not counted. With
     *              prefs and no travel times, a run counts the stops that are open and switched on (rough minutes).
     *              Null: the old rule, a run waits for the travel times.
     */
    public static Decision decide(ItemTree.Account a, Map<String, AccountState.Timer> timers, Map<String, Map<String, Integer>> travel, int bankFree,
        Planner.Route route, List<Goal> goals, long nowSec, int sitting, RunPrefs prefs)
    {
        Decision d = new Decision();
        List<Pick> later = new ArrayList<>();

        // 1 and 2: timers
        for (Map.Entry<String, AccountState.Timer> e : timers.entrySet())
        {
            AccountState.Timer t = e.getValue();
            String name = e.getKey(), s = t.stateAt(nowSec);
            if (prefs != null && !prefs.on(name)) continue;
            int min = runMinutes(name);
            String run = run(name);
            if (PatchTravel.RUNS.containsKey(name) && !AccountState.Timer.UNKNOWN.equals(s))
            {
                Map<String, Integer> ticks = travel == null ? null : travel.get(name);
                List<String> quick = quick(ticks);
                if (quick != null && prefs != null) quick.removeIf(p -> !prefs.on(name, p));
                int need = PatchTravel.minStops(name);
                boolean stop = name.equals(AccountState.BIRDS);
                if (quick == null && prefs != null)
                {
                    // No travel times: the stops the player left switched on and can use
                    List<String> mine = prefs.stops(name, a);
                    if (mine.size() < need)
                    {
                        d.guards.add(run + " left out: " + (stop ? "Fossil Island is not open or is switched off"
                            : "only " + mine.size() + " stop" + (mine.size() == 1 ? " is" : "s are") + " open and switched on. It needs " + need) + ".");
                        continue;
                    }
                    min = stop ? BIRD_RUN_MIN : (int) Math.ceil(mine.size() * STOP_MIN);
                    if (!stop) run = run + " (" + String.join(", ", mine) + ")";
                }
                else if (quick == null) { d.guards.add(run + ": travel not checked yet (it runs a few minutes after login)."); continue; }
                else if (quick.size() < need)
                {
                    d.guards.add(run + " left out: " + (stop ? "Fossil Island is not within " + QUICK_SECONDS / 60 + " min"
                        : "only " + quick.size() + " patch" + (quick.size() == 1 ? " is" : "es are") + " within " + QUICK_SECONDS / 60 + " min"
                        + (quick.isEmpty() ? "" : " (" + String.join(", ", quick) + ")") + ". It needs " + need) + ".");
                    continue;
                }
                else
                {
                    double m = 0;
                    for (String p : quick) m += ticks.get(p) * 0.6 / 60 + (stop ? BIRD_RUN_MIN : PATCH_MIN);
                    min = (int) Math.ceil(m);
                    if (!stop) run = run + " (" + String.join(", ", quick) + ")";
                }
            }
            Pick p = null;
            if (AccountState.Timer.READY.equals(s))
            {
                long since = t.readyAt > 0 ? t.readyAt : t.seenAt / 1000;
                d.picks.add(p = new Pick(Rank.WASTE, run, name + (name.equals(AccountState.BIRDS) ? " are full" : " are grown")
                    + (since > 0 && nowSec - since >= 60 ? " (" + AccountState.ago(since * 1000, nowSec * 1000) + ")" : "") + " and do nothing until run", min, false, 0));
            }
            else if ((AccountState.Timer.EMPTY.equals(s) || AccountState.Timer.BUILT.equals(s)) && canPlant(name, a))
                d.picks.add(p = new Pick(Rank.WASTE, run, name + (AccountState.Timer.BUILT.equals(s) ? " have no seeds" : " are empty") + ", so nothing grows", min, false, 0));
            else if (AccountState.Timer.GROWING.equals(s) && t.readyAt > nowSec && t.readyAt - nowSec <= sitting * 60L)
                later.add(p = new Pick(Rank.READY, run, name + " are ready " + AccountState.when(t, nowSec), min, false, t.readyAt));
            if (p != null) p.timer = name;
        }
        later.sort((x, y) -> Long.compare(x.at, y.at));

        // 2b: one-time unlocks the account just met "a task go to when we meet the req")
        List<Pick> work = new ArrayList<>();
        for (com.goalguide.facts.Unlock u : com.goalguide.facts.Unlock.all())
        {
            List<String> miss = u.missing(a);
            if (!u.open(a) || miss.isEmpty()) continue;
            String need = String.join(", ", u.skills.entrySet().stream().map(e -> Method.pretty(e.getKey()) + " " + e.getValue()).toArray(String[]::new));
            work.add(new Pick(Rank.UNLOCK, "Get the " + u.name + " (" + u.where + ")", "you have " + need + " and " + u.why
                + (miss.size() < u.items.size() ? "; still missing " + String.join(", ", miss) : ""), u.minutesAt(a), false, 0));
        }
        // 3: stock short, the most short first
        List<Goal> short_ = new ArrayList<>();
        for (Goal g : goals) if (g.type == Goal.Type.STOCK && have(a, g.target) < g.amount) short_.add(g);
        short_.sort((x, y) -> Double.compare(have(a, x.target) / (double) x.amount, have(a, y.target) / (double) y.amount));
        for (Goal g : short_)
        {
            int have = have(a, g.target);
            String why = "you have " + String.format("%,d", have) + " of " + g.label() + " (" + Math.round(100.0 * have / g.amount) + "%)";
            Pick p = next(route, g, a, Rank.STOCK, why);
            if (p != null) work.add(p);
        }
        // 4: the focus goal
        if (!goals.isEmpty())
        {
            Goal f = goals.get(0);
            Pick p = next(route, f, a, Rank.FOCUS, f.label() + " is your focus goal");
            if (p != null) work.add(p);
        }
        // 5: the rest of the route
        for (Planner.Step s : route.steps) work.add(pick(s, Rank.NEXT, "it is next on the route"));
        // The same step once, at its best rank
        List<String> seen = new ArrayList<>();
        for (Pick p : d.picks) seen.add(p.what);
        for (Pick p : work) if (!seen.contains(p.what)) { seen.add(p.what); d.picks.add(p); }
        // Ready runs rank after waste; the sitting places them at their time
        List<Pick> ranked = new ArrayList<>(d.picks);
        d.picks.addAll(countRank(d.picks, Rank.WASTE), later);

        guards(d, a, bankFree, route);
        sitting(d, ranked, later, nowSec, sitting);
        keepNow = Collections.unmodifiableSet(d.keep.keySet());
        return d;
    }

    private static int countRank(List<Pick> l, Rank r)
    {
        int n = 0;
        for (Pick p : l) if (p.rank == r) n++;
        return n;
    }

    /** The first route step that moves this goal on. */
    static Pick next(Planner.Route route, Goal g, ItemTree.Account a, Rank rank, String why)
    {
        String label = g.label();
        if (g.type == Goal.Type.QUEST)
        {
            if (a.questsDone.contains(g.target)) return null;
            // The quest chain is ordered with its needs first: its first step is where this quest starts
            for (Planner.Step s : route.steps) if (s.goal == null) return pick(s, rank, why);
            return null;
        }
        if (g.type == Goal.Type.STOCK)
        {
            Method m = best(g.target, a);
            List<String> missing = m == null ? List.of() : m.missing(a);
            boolean quest = false;
            for (String x : missing) quest |= x.startsWith("quest ");
            if (quest)
                for (Planner.Step s : route.steps) if (s.goal == null) return pick(s, rank, why + "; " + m.name + " needs " + String.join(", ", missing) + " first");
            for (Planner.Step s : route.steps)
                if (label.equals(s.goal)) return pick(s, rank, s.kind == Planner.Kind.TRAIN && m != null ? why + "; " + m.name + " needs " + String.join(", ", missing) + " first" : why);
            return null;
        }
        for (Planner.Step s : route.steps) if (label.equals(s.goal)) return pick(s, rank, why);
        return null;
    }

    private static Method best(String item, ItemTree.Account a)
    {
        List<Method> ways = MethodTables.waysToMake(item, a);
        return ways.isEmpty() ? null : ways.get(0);
    }

    static Pick pick(Planner.Step s, Rank rank, String why)
    {
        String verb = s.kind == Planner.Kind.TRAIN ? "Train " : s.kind == Planner.Kind.GET ? "Get " : s.kind == Planner.Kind.STOCK ? "Stock " : "";
        String what = verb + s.what + (s.how != null && !s.you ? " (" + s.how + ")" : "");
        return new Pick(rank, what, why, s.hours < 0 ? -1 : (int) Math.ceil(s.hours * 60), s.you, 0);
    }

    static void guards(Decision d, ItemTree.Account a, int bankFree, Planner.Route route)
    {
        d.keep.putAll(route.keep);
        if (bankFree < 0) d.guards.add("Bank space not known yet: open the bank once.");
        else if (bankFree == 0) d.guards.add("The bank is full: anything new can't be banked. Clear a slot before making a new item.");
        else if (bankFree < BANK_LOW) d.guards.add("Only " + bankFree + " bank slots free.");
        // A new kind of item needs a free slot: flag the picks that make one the bank doesn't hold
        if (bankFree == 0)
            for (Pick p : d.picks)
                if (p.rank == Rank.STOCK && p.what.startsWith("Stock "))
                {
                    String item = p.what.substring(6).replaceFirst("^[0-9,]+ ", "").replaceFirst(" \\(.*$", "");
                    if (have(a, item) == 0) d.guards.add("No bank slot for " + item + ".");
                }
        if (!d.keep.isEmpty())
        {
            List<String> l = new ArrayList<>();
            d.keep.forEach((item, q) -> l.add(item + " (" + q + ")"));
            d.guards.add("Keep for quests, never drop or use: " + String.join(", ", l.size() > 8 ? l.subList(0, 8) : l) + (l.size() > 8 ? ", +" + (l.size() - 8) + " more" : "") + ".");
        }
    }

    /**
     * Fills the sitting: grown and empty timers first, then the work in rank order. A run that comes ready cuts in at
     * its time and the cut block carries on after it. Player turns are left out.
     */
    static void sitting(Decision d, List<Pick> ranked, List<Pick> later, long nowSec, int total)
    {
        List<Pick> queue = new ArrayList<>();
        for (Pick p : ranked) if (p.rank == Rank.WASTE) queue.add(p);
        for (Pick p : ranked) if (p.rank != Rank.WASTE && !p.you) queue.add(p);
        int t = 0, li = 0;
        while (t < total)
        {
            int due = li < later.size() ? (int) Math.max(0, (later.get(li).at - nowSec + 59) / 60) : Integer.MAX_VALUE;
            if (due <= t)
            {
                Pick r = later.get(li++);
                d.sitting.add(new Block(t, Math.min(r.minutes, total - t), r));
                t += r.minutes;
                continue;
            }
            if (queue.isEmpty())
            {
                if (due >= total) break;
                t = due;
                continue;
            }
            Pick p = queue.remove(0);
            int left = total - t;
            int len = p.minutes < 0 ? left : Math.min(p.minutes, left);
            // A run cuts in at its time (every minute late is lost), the cut block carries on after it
            if (p.rank != Rank.WASTE && due < t + len) len = due - t;
            d.sitting.add(new Block(t, len, p));
            t += len;
            boolean more = p.minutes < 0 || p.minutes > len;
            if (more && p.rank != Rank.WASTE && t < total)
                queue.add(0, new Pick(p.rank, p.what, p.why, p.minutes < 0 ? -1 : p.minutes - len, p.you, p.at));
        }
    }

    // Seeds by patch, with the Farming level each needs (OSRS Wiki, Farming/Level up table, 2026-10-25)
    static final Map<String, Integer> HERB_SEEDS = new LinkedHashMap<>(), TREE_SEEDS = new LinkedHashMap<>(), FRUIT_SEEDS = new LinkedHashMap<>();
    /** Bird house bait (OSRS Wiki, Bird house trapping, 2026-10-25): hops, herb, flower, allotment and bush seeds. */
    static final List<String> BIRD_SEEDS = List.of("barley seed", "hammerstone seed", "asgarnian seed", "jute seed", "yanillian seed",
        "krandorian seed", "wildblood seed", "guam seed", "marrentill seed", "tarromin seed", "harralander seed", "ranarr seed",
        "toadflax seed", "irit seed", "avantoe seed", "kwuarm seed", "snapdragon seed", "cadantine seed", "lantadyme seed",
        "dwarf weed seed", "torstol seed", "marigold seed", "rosemary seed", "nasturtium seed", "woad seed", "limpwurt seed",
        "white lily seed", "potato seed", "onion seed", "cabbage seed", "tomato seed", "sweetcorn seed", "strawberry seed",
        "watermelon seed", "snape grass seed", "redberry seed", "poison ivy seed", "cadavaberry seed", "dwellberry seed",
        "jangerberry seed", "whiteberry seed");
    /** Hunter level to place a regular bird house (OSRS Wiki). */
    static final int BIRD_HOUSE_HUNTER = 5;
    static
    {
        String[] herbs = {"guam", "marrentill", "tarromin", "harralander", "ranarr", "toadflax", "irit", "avantoe", "kwuarm", "snapdragon",
            "cadantine", "lantadyme", "dwarf weed", "torstol"};
        int[] hl = {9, 14, 19, 26, 32, 38, 44, 50, 56, 62, 67, 73, 79, 85};
        for (int i = 0; i < herbs.length; i++) HERB_SEEDS.put(herbs[i] + " seed", hl[i]);
        String[] trees = {"oak", "willow", "maple", "yew", "magic"};
        int[] tl = {15, 30, 45, 60, 75};
        for (int i = 0; i < trees.length; i++) TREE_SEEDS.put(trees[i] + " sapling", tl[i]);
        String[] fruit = {"apple", "banana", "orange", "curry", "pineapple", "papaya", "palm"};
        int[] fl = {27, 33, 39, 42, 51, 57, 68};
        for (int i = 0; i < fruit.length; i++) FRUIT_SEEDS.put(fruit[i] + " sapling", fl[i]);
    }

    /**
     * An empty patch only wastes time when the account could plant it now: it holds a seed for it at its Farming
     * level (any storage). Patch kinds we have no seed list for are never called waste.
     */
    static boolean canPlant(String timer, ItemTree.Account a)
    {
        if (timer.equals(AccountState.BIRDS))
        {
            if (a.skills.getOrDefault("HUNTER", 1) < BIRD_HOUSE_HUNTER) return false;
            for (String s : BIRD_SEEDS) if (have(a, s) > 0) return true;
            return false;
        }
        Map<String, Integer> seeds = timer.equals(AccountState.HERBS) ? HERB_SEEDS : timer.equals("tree patches") ? TREE_SEEDS
            : timer.equals("fruit tree patches") ? FRUIT_SEEDS : null;
        if (seeds == null) return false;
        int farming = a.skills.getOrDefault("FARMING", 1);
        for (Map.Entry<String, Integer> e : seeds.entrySet()) if (e.getValue() <= farming && have(a, e.getKey()) > 0) return true;
        return false;
    }

    /** "Herb run" for "herb patches". */
    public static String runName(String timer)
    {
        return run(timer);
    }

    static String run(String timer)
    {
        if (timer.equals(AccountState.HERBS)) return "Herb run";
        if (timer.equals(AccountState.BIRDS)) return "Bird house run";
        String s = timer.replace(" patches", "");
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1) + " run";
    }

    static int runMinutes(String timer)
    {
        return timer.equals(AccountState.HERBS) ? HERB_RUN_MIN : timer.equals(AccountState.BIRDS) ? BIRD_RUN_MIN : OTHER_RUN_MIN;
    }

    private static int have(ItemTree.Account a, String item)
    {
        return a.owned.getOrDefault(ItemData.key(item), 0);
    }
}
