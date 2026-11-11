package com.goalguide.decide;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.goalguide.goals.Planner;
import com.goalguide.items.ItemTree;
import com.goalguide.know.AccountState;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Runs tab: runs and stops switched off per account; a run needs 3 stops open and on. */
public class RunPrefsTest
{
    static final long NOW = 1_800_000_000L;

    static Map<String, AccountState.Timer> grownHerbs()
    {
        Map<String, AccountState.Timer> t = new LinkedHashMap<>();
        t.put(AccountState.HERBS, new AccountState.Timer(AccountState.Timer.READY, 0, NOW * 1000));
        return t;
    }

    static Decide.Decision decide(ItemTree.Account a, RunPrefs p)
    {
        return Decide.decide(a, grownHerbs(), Map.of(), 50, new Planner.Route(), List.of(), NOW, 60, p);
    }

    @Test
    public void withoutTravelTheRunUsesTheStopsLeftOn()
    {
        ItemTree.Account a = ItemTree.Account.fresh();
        RunPrefs p = RunPrefs.decode("", "herb patches|Port Phasmatys;herb patches|Hosidius");
        Decide.Decision d = decide(a, p);
        assertEquals("Herb run (Falador, Catherby, Ardougne, Civitas illa Fortis)", d.now().what);
        assertEquals(AccountState.HERBS, d.now().timer);
        assertEquals(8, d.now().minutes);
        // The old rule without the tab: wait for travel times
        assertTrue(Decide.decide(a, grownHerbs(), Map.of(), 50, new Planner.Route(), List.of(), NOW, 60).guards.toString().contains("travel not checked yet"));
    }

    @Test
    public void aRunSwitchedOffOrShortOfStopsIsNotOffered()
    {
        ItemTree.Account a = ItemTree.Account.fresh();
        assertNull(decide(a, RunPrefs.ALL_ON.withRun(AccountState.HERBS, false)).now());
        RunPrefs few = RunPrefs.ALL_ON;
        for (String s : List.of("Falador", "Port Phasmatys", "Catherby", "Ardougne")) few = few.withStop(AccountState.HERBS, s, false);
        Decide.Decision d = decide(a, few);
        assertNull(d.now());
        assertTrue(d.guards.toString(), d.guards.toString().contains("only 2 stops are open and switched on. It needs 3"));
        assertEquals("herb patches", few.withRun(AccountState.HERBS, false).runsText());
        assertTrue(few.stopsText().contains("herb patches|Falador"));
    }

    @Test
    public void closedStopsSayWhy()
    {
        ItemTree.Account a = ItemTree.Account.fresh();
        assertEquals("needs Farming 65", RunPrefs.closed(PatchTravel.HERBS.get(9), a));
        assertEquals("needs My Arm's Big Adventure", RunPrefs.closed(PatchTravel.HERBS.get(6), a));
        assertNull(RunPrefs.closed(PatchTravel.HERBS.get(0), a));
    }
}
