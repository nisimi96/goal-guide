package com.goalguide.decide;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.goalguide.goals.Goal;
import com.goalguide.goals.Planner;
import com.goalguide.items.ItemTree;
import com.goalguide.know.AccountState;
import com.goalguide.quests.QuestData;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DecideTest
{
    static final long NOW = 1_800_000_000L;

    private static ItemTree.Account account(int fishing)
    {
        ItemTree.Account a = ItemTree.Account.fresh();
        for (String s : new String[]{"AGILITY", "COOKING", "STRENGTH", "ATTACK", "DEFENCE", "HERBLORE", "CRAFTING", "WOODCUTTING", "FIREMAKING"}) a.skills.put(s, 60);
        a.skills.put("FISHING", fishing);
        a.iron = true;
        return a;
    }

    /** Falador 40 s, Catherby 60 s, Ardougne 100 s, Port Phasmatys 5 min, Weiss closed. */
    static final Map<String, Integer> HERB_TRAVEL = new LinkedHashMap<>();
    /** Herbs as above, trees Lumbridge, Varrock, Falador quick; Fossil Island 50 s. */
    static final Map<String, Map<String, Integer>> TRAVEL = new LinkedHashMap<>();
    static
    {
        HERB_TRAVEL.put("Falador", 67);
        HERB_TRAVEL.put("Catherby", 100);
        HERB_TRAVEL.put("Ardougne", 167);
        HERB_TRAVEL.put("Port Phasmatys", 500);
        HERB_TRAVEL.put("Weiss", PatchTravel.CLOSED);
        TRAVEL.put(AccountState.HERBS, HERB_TRAVEL);
        TRAVEL.put("tree patches", Map.of("Lumbridge", 50, "Varrock", 60, "Falador", 70));
        TRAVEL.put(AccountState.BIRDS, Map.of("Fossil Island", 83));
    }

    private static Decide.Decision decide(ItemTree.Account a, Map<String, AccountState.Timer> timers, int free, List<Goal> goals, int sitting)
    {
        return decide(a, timers, TRAVEL, free, goals, sitting);
    }

    private static Decide.Decision decide(ItemTree.Account a, Map<String, AccountState.Timer> timers, Map<String, Map<String, Integer>> travel, int free, List<Goal> goals, int sitting)
    {
        Planner.Route r = Planner.plan(QuestData.get(), null, a, 50, goals);
        return Decide.decide(a, timers, travel, free, r, goals, NOW, sitting);
    }

    @Test
    public void grownHerbsComeFirst()
    {
        ItemTree.Account a = account(70);
        a.questsDone.add("TAI_BWO_WANNAI_TRIO");
        Map<String, AccountState.Timer> t = new LinkedHashMap<>();
        t.put(AccountState.HERBS, new AccountState.Timer(AccountState.Timer.GROWING, NOW - 2 * 3600, NOW * 1000 - 3 * 3600_000L));
        t.put(AccountState.BIRDS, new AccountState.Timer(AccountState.Timer.GROWING, NOW + 20 * 60, NOW * 1000));
        Decide.Decision d = decide(a, t, 50, List.of(Goal.stock("raw karambwan", 1000)), 60);
        Decide.Pick now = d.now();
        assertEquals(Decide.Rank.WASTE, now.rank);
        assertEquals("Herb run (Falador, Catherby, Ardougne)", now.what);
        assertTrue(now.say(), now.say().contains("herb patches are grown (2 h ago)"));
        // 67 + 100 + 167 ticks = 3.3 min of travel, plus a minute a patch
        assertEquals(7, now.minutes);
        // The bird houses are ready in 20 min: ranked second, not "now"
        assertEquals(Decide.Rank.READY, d.picks.get(1).rank);
        assertTrue(d.picks.get(1).why, d.picks.get(1).why.contains("ready in 20 min"));
        // The sitting: herbs, karambwan until the birds, birds, karambwan again
        assertTrue(d.sitting.toString(), d.sitting.get(0).pick.what.startsWith("Herb run"));
        assertTrue(d.sitting.toString(), d.sitting.get(1).pick.what.startsWith("Stock 1,000 raw karambwan"));
        assertEquals(d.sitting.toString(), 7, d.sitting.get(1).start);
        assertEquals(d.sitting.toString(), "Bird house run", d.sitting.get(2).pick.what);
        assertEquals(d.sitting.toString(), 20, d.sitting.get(2).start);
        assertTrue(d.sitting.toString(), d.sitting.get(3).pick.what.startsWith("Stock"));
        int end = d.sitting.get(d.sitting.size() - 1).start + d.sitting.get(d.sitting.size() - 1).minutes;
        assertEquals(d.sitting.toString(), 60, end);
    }

    @Test
    public void mostShortStockFirstThenFocus()
    {
        ItemTree.Account a = account(80);
        a.questsDone.add("TAI_BWO_WANNAI_TRIO");
        a.owned.put("raw karambwan", 900);
        a.owned.put("raw shark", 100);
        Decide.Decision d = decide(a, Map.of(), 50, List.of(Goal.level("FISHING", 85), Goal.stock("raw karambwan", 1000), Goal.stock("raw shark", 1000)), 120);
        assertEquals(Decide.Rank.STOCK, d.now().rank);
        assertTrue(d.now().what, d.now().what.contains("raw shark"));
        assertTrue(d.now().why, d.now().why.contains("100 of 1,000 raw shark (10%)"));
        assertEquals(Decide.Rank.STOCK, d.picks.get(1).rank);
        assertEquals(Decide.Rank.FOCUS, d.picks.get(2).rank);
        assertTrue(d.picks.get(2).what, d.picks.get(2).what.startsWith("Train Fishing 80 to 85"));
        // Each step once
        assertEquals(d.picks.toString(), 3, d.picks.size());
    }

    @Test
    public void stockBehindAQuestPointsAtTheQuest()
    {
        ItemTree.Account a = account(70);
        Decide.Decision d = decide(a, Map.of(), 50, List.of(Goal.stock("raw karambwan", 1000)), 60);
        // Tai Bwo Wannai Trio is the player's turn: the pick says so
        boolean quest = false;
        for (Decide.Pick p : d.picks) quest |= p.why.contains("needs quest Tai Bwo Wannai Trio first");
        assertTrue(d.picks.toString(), quest);
        // Player turns are not bot blocks in the sitting
        for (Decide.Block b : d.sitting) assertFalse(b.toString(), b.pick.you);
    }

    @Test
    public void guards()
    {
        ItemTree.Account a = account(70);
        assertTrue(decide(a, Map.of(), -1, List.of(), 60).guards.get(0).contains("open the bank once"));
        assertTrue(decide(a, Map.of(), 4, List.of(), 60).guards.get(0).contains("Only 4 bank slots free"));
        Decide.Decision full = decide(account(80), Map.of(), 0, List.of(Goal.stock("raw shark", 10)), 60);
        assertTrue(full.guards.toString(), full.guards.get(0).contains("bank is full"));
        assertTrue(full.guards.toString(), full.guards.toString().contains("No bank slot for raw shark"));
        assertTrue(decide(a, Map.of(), 50, List.of(), 60).guards.isEmpty());
        assertNull(decide(a, Map.of(), 50, List.of(), 60).now());
    }

    @Test
    public void questItemsAreKept()
    {
        ItemTree.Account a = account(70);
        Planner.Route r = new Planner.Route();
        r.keep.put("a key", "Some Quest");
        Decide.Decision d = Decide.decide(a, Map.of(), 50, r, List.of(), NOW, 60);
        assertEquals("Some Quest", d.keep.get("a key"));
        assertTrue(d.guards.toString(), d.guards.get(0).contains("a key (Some Quest)"));
        assertFalse(Decide.mayDrop("A key"));
        assertTrue(Decide.mayDrop("raw shark"));
    }

    @Test
    public void emptyPatchesAreWasteOnlyWhenPlantable()
    {
        Map<String, AccountState.Timer> t = new LinkedHashMap<>();
        t.put(AccountState.BIRDS, new AccountState.Timer(AccountState.Timer.BUILT, -1, NOW * 1000));
        t.put(AccountState.HERBS, new AccountState.Timer(AccountState.Timer.EMPTY, -1, NOW * 1000));
        t.put("tree patches", new AccountState.Timer(AccountState.Timer.EMPTY, -1, NOW * 1000));
        t.put("allotment patches", new AccountState.Timer(AccountState.Timer.EMPTY, -1, NOW * 1000));
        t.put("fruit tree patches", new AccountState.Timer(AccountState.Timer.UNKNOWN, -1, NOW * 1000));
        // No seeds, low Farming: nothing is waste (the screenshot of 2026-10-25 said "herb run" here)
        ItemTree.Account a = account(70);
        assertNull(decide(a, t, 50, List.of(), 30).now());
        // A guam seed at Farming 9: the herb run is waste; a ranarr seed at Farming 20 is not
        a.skills.put("FARMING", 20);
        a.owned.put("ranarr seed", 5);
        assertNull(decide(a, t, 50, List.of(), 30).now());
        a.owned.put("guam seed", 5);
        Decide.Decision d = decide(a, t, 50, List.of(), 30);
        assertTrue(d.now().what, d.now().what.startsWith("Herb run"));
        assertTrue(d.now().why, d.now().why.contains("are empty"));
        // Bird houses: Hunter 5 and a bait seed (the guam seed counts)
        a.skills.put("HUNTER", 5);
        d = decide(a, t, 50, List.of(), 30);
        assertEquals(d.picks.toString(), 2, d.picks.size());
        assertTrue(d.picks.get(0).why, d.picks.get(0).why.contains("have no seeds"));
        // Trees: an oak sapling at 15
        a.owned.put("oak sapling", 1);
        assertEquals(3, decide(a, t, 50, List.of(), 30).picks.size());
    }

    @Test
    public void grownPatchesAreAlwaysWaste()
    {
        Map<String, AccountState.Timer> t = new LinkedHashMap<>();
        t.put(AccountState.HERBS, new AccountState.Timer(AccountState.Timer.READY, 0, NOW * 1000));
        assertTrue(decide(account(70), t, 50, List.of(), 30).now().what.startsWith("Herb run"));
    }

    /** No herb run when the patches are a long walk away. */
    @Test
    public void herbRunOnlyWhenThePatchesAreQuick()
    {
        Map<String, AccountState.Timer> t = new LinkedHashMap<>();
        t.put(AccountState.HERBS, new AccountState.Timer(AccountState.Timer.READY, 0, NOW * 1000));
        Map<String, Integer> slow = new LinkedHashMap<>();
        slow.put("Falador", 60);
        slow.put("Catherby", 600);
        slow.put("Ardougne", 900);
        slow.put("Harmony Island", PatchTravel.CLOSED);
        Decide.Decision d = decide(account(70), t, Map.of(AccountState.HERBS, slow), 50, List.of(), 30);
        assertNull(d.picks.toString(), d.now());
        assertTrue(d.guards.toString(), d.guards.get(0).contains("only 1 patch is within 2 min (Falador). It needs 3"));
        // Not checked yet: no run, and it says why
        d = decide(account(70), t, Map.of(), 50, List.of(), 30);
        assertNull(d.now());
        assertTrue(d.guards.toString(), d.guards.get(0).contains("not checked yet"));
        // Bird houses: one stop, Fossil Island far away
        t.clear();
        t.put(AccountState.BIRDS, new AccountState.Timer(AccountState.Timer.READY, 0, NOW * 1000));
        d = decide(account(70), t, Map.of(AccountState.BIRDS, Map.of("Fossil Island", 400)), 50, List.of(), 30);
        assertNull(d.now());
        assertTrue(d.guards.toString(), d.guards.get(0).contains("Fossil Island is not within 2 min"));
        d = decide(account(70), t, TRAVEL, 50, List.of(), 30);
        assertEquals("Bird house run", d.now().what);
        // 50 s there, 5 min at the houses
        assertEquals(6, d.now().minutes);
    }

    /** The rogue outfit is a task the moment Thieving and Agility are 50. */
    @Test
    public void rogueOutfitOnceTheLevelsAreMet()
    {
        ItemTree.Account a = account(70);
        a.skills.put("THIEVING", 49);
        a.skills.put("AGILITY", 50);
        assertNull(decide(a, Map.of(), 50, List.of(), 60).now());
        a.skills.put("THIEVING", 50);
        Decide.Decision d = decide(a, Map.of(), 50, List.of(Goal.level("FISHING", 80)), 60);
        Decide.Pick p = d.now();
        assertEquals(Decide.Rank.UNLOCK, p.rank);
        assertEquals("Get the Rogue outfit (Rogues' Den)", p.what);
        assertTrue(p.why, p.why.contains("you have Thieving 50, Agility 50 and the full set doubles every pickpocket's loot"));
        assertEquals(48, p.minutes);
        // Part of it owned: says what is left; all of it: done
        a.owned.put("rogue mask", 1);
        a.owned.put("rogue top", 1);
        assertTrue(decide(a, Map.of(), 50, List.of(), 60).now().why.contains("still missing rogue trousers, rogue gloves, rogue boots"));
        for (String s : new String[]{"rogue trousers", "rogue gloves", "rogue boots"}) a.owned.put(s, 1);
        assertNull(decide(a, Map.of(), 50, List.of(), 60).now());
        // Free to play accounts can't
        a.owned.clear();
        a.members = false;
        assertNull(decide(a, Map.of(), 50, List.of(), 60).now());
    }
}
