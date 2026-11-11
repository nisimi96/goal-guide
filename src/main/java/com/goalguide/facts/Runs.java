package com.goalguide.facts;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.goalguide.items.ItemTree;
import com.goalguide.quests.QuestPlan;
import net.runelite.api.Quest;

/**
 * Farming and bird house runs: their stops, what opens each stop, and what each run takes from the bank.
 * Data in runs.tsv. Pure.
 */
public final class Runs
{
    private Runs() { }

    public static final class Stop
    {
        public final String name;
        /** A quest or diary key, "FARMING 65", or null when open to all. */
        final String needs;

        Stop(String name, String needs)
        {
            this.name = name;
            this.needs = needs;
        }

        /** "needs My Arm's Big Adventure", "needs Farming 65", or null when this account can use it. */
        public String closed(ItemTree.Account a)
        {
            if (needs == null) return null;
            String[] p = needs.split(" ");
            if (p.length == 2) return a.skills.getOrDefault(p[0], 1) >= Integer.parseInt(p[1]) ? null : "needs " + QuestPlan.pretty(p[0]) + " " + p[1];
            return a.questsDone.contains(needs) ? null : "needs " + questName(needs);
        }

        /** The game's own quest name ("Making Friends with My Arm"); a diary key reads as words. */
        private static String questName(String key)
        {
            for (Quest q : Quest.values()) if (q.name().equals(key)) return q.getName();
            return QuestPlan.pretty(key);
        }
    }

    private static final Map<String, List<Stop>> STOPS = new LinkedHashMap<>();
    private static final Map<String, List<String>> ITEMS = new LinkedHashMap<>();

    static
    {
        for (String line : MethodTables.read("runs.tsv"))
        {
            String[] f = line.split("\t");
            String needs = f[3].equals("-") ? null : f[3];
            if (f[0].equals("stop")) STOPS.computeIfAbsent(f[1], k -> new ArrayList<>()).add(new Stop(f[2], needs));
            else ITEMS.computeIfAbsent(f[1], k -> new ArrayList<>()).add(f[2]);
        }
    }

    /** "Herb run", "Tree run", ... in order. */
    public static List<String> names() { return new ArrayList<>(STOPS.keySet()); }

    public static List<Stop> stops(String run) { return STOPS.getOrDefault(run, List.of()); }

    public static List<String> items(String run) { return ITEMS.getOrDefault(run, List.of()); }

    /**
     * The runs and stops switched off, per account. Saved as two short texts:
     * "Tree run;Bird houses" and "Herb run|Weiss;Herb run|Harmony Island".
     */
    public static final class Switches
    {
        public static final Switches ALL_ON = new Switches(Set.of(), Set.of());

        private final Set<String> runsOff, stopsOff;

        private Switches(Set<String> runsOff, Set<String> stopsOff)
        {
            this.runsOff = runsOff;
            this.stopsOff = stopsOff;
        }

        public static Switches decode(String runs, String stops)
        {
            return new Switches(split(runs), split(stops));
        }

        public String runsText() { return String.join(";", runsOff); }

        public String stopsText() { return String.join(";", stopsOff); }

        public boolean on(String run) { return !runsOff.contains(run); }

        public boolean on(String run, String stop) { return !stopsOff.contains(run + "|" + stop); }

        public Switches withRun(String run, boolean on) { return new Switches(flip(runsOff, run, on), stopsOff); }

        public Switches withStop(String run, String stop, boolean on) { return new Switches(runsOff, flip(stopsOff, run + "|" + stop, on)); }

        /** The runs switched on, in order. */
        public List<String> onRuns()
        {
            List<String> out = new ArrayList<>();
            for (String r : names()) if (on(r)) out.add(r);
            return out;
        }

        private static Set<String> flip(Set<String> off, String key, boolean on)
        {
            Set<String> s = new LinkedHashSet<>(off);
            if (on) s.remove(key);
            else s.add(key);
            return s;
        }

        private static Set<String> split(String s)
        {
            Set<String> out = new LinkedHashSet<>();
            if (s != null) for (String p : s.split(";")) if (!p.trim().isEmpty()) out.add(p.trim());
            return out;
        }
    }

    private static volatile Switches current = Switches.ALL_ON;

    /** The logged-in account's switches. */
    public static Switches switches() { return current; }

    public static void switches(Switches s) { current = s == null ? Switches.ALL_ON : s; }
}
