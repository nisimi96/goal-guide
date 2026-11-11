package com.goalguide.decide;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.goalguide.items.ItemTree;

/**
 * USER 2026-11-11 (Runs tab): each run (herb, tree, fruit tree, bird houses) and each stop of it is switched on or
 * off per account. A switched-off run is never offered; a switched-off stop is never counted or shown.
 *
 * Saved as two short texts: the runs that are off ("tree patches;bird houses") and the stops that are off
 * ("herb patches|Weiss;herb patches|Harmony Island"). Pure.
 */
public final class RunPrefs
{
    /** Nothing switched off. */
    public static final RunPrefs ALL_ON = new RunPrefs(Set.of(), Set.of());

    private final Set<String> runsOff, stopsOff;

    public RunPrefs(Set<String> runsOff, Set<String> stopsOff)
    {
        this.runsOff = new LinkedHashSet<>(runsOff);
        this.stopsOff = new LinkedHashSet<>(stopsOff);
    }

    public static RunPrefs decode(String runs, String stops)
    {
        return new RunPrefs(split(runs), split(stops));
    }

    private static Set<String> split(String s)
    {
        Set<String> out = new LinkedHashSet<>();
        if (s == null) return out;
        for (String p : s.split(";")) if (!p.trim().isEmpty()) out.add(p.trim());
        return out;
    }

    public String runsText() { return String.join(";", runsOff); }

    public String stopsText() { return String.join(";", stopsOff); }

    public boolean on(String run) { return !runsOff.contains(run); }

    public boolean on(String run, String stop) { return !stopsOff.contains(run + "|" + stop); }

    /** A copy with this run switched. */
    public RunPrefs withRun(String run, boolean on)
    {
        Set<String> r = new LinkedHashSet<>(runsOff);
        if (on) r.remove(run);
        else r.add(run);
        return new RunPrefs(r, stopsOff);
    }

    /** A copy with this stop switched. */
    public RunPrefs withStop(String run, String stop, boolean on)
    {
        Set<String> s = new LinkedHashSet<>(stopsOff);
        if (on) s.remove(run + "|" + stop);
        else s.add(run + "|" + stop);
        return new RunPrefs(runsOff, s);
    }

    /** The stops of a run that are open to the account and switched on, in run order. */
    public List<String> stops(String run, ItemTree.Account a)
    {
        List<String> out = new ArrayList<>();
        for (PatchTravel.Patch p : PatchTravel.RUNS.getOrDefault(run, List.of()))
            if (PatchTravel.open(p, a) && on(run, p.name())) out.add(p.name());
        return out;
    }

    /** Why a stop can't be used yet ("needs My Arm's Big Adventure", "needs Farming 65"), null when it is open. */
    public static String closed(PatchTravel.Patch p, ItemTree.Account a)
    {
        if (p.quest() != null && !a.questsDone.contains(p.quest())) return "needs " + PatchTravel.goalText(p.quest());
        if (a.skills.getOrDefault("FARMING", 1) < p.farming()) return "needs Farming " + p.farming();
        return null;
    }
}
