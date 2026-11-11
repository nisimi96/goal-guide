package com.goalguide.guide;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.goalguide.goals.Planner;
import com.goalguide.items.ItemTree;
import com.goalguide.know.AccountState;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** USER 2026-11-11 mockups built: Next activity, the activity timer, the item list and the run box. */
public class GuideToolsTest
{
    @Test
    public void nextActivityMovesNowAndStartsOverWhenAllAreMoved()
    {
        Planner.Route r = GuideTest.route();
        Guide g = Guide.of(r, null, Set.of(ActivityTimer.key("Shared")));
        assertEquals("Train Thieving 40 to 50", g.now.what);
        assertEquals("Goal B", g.next.what);
        assertEquals(1, g.skipped);
        // a level-up changes the text, not the key: still moved past
        assertEquals(ActivityTimer.key("Train Thieving 40 to 50"), ActivityTimer.key("Train Thieving 44 to 50 (Men)"));
        java.util.Set<String> all = new java.util.HashSet<>();
        for (Planner.Step s : r.steps) all.add(ActivityTimer.key(Guide.line(s).what));
        Guide again = Guide.of(r, null, all);
        assertEquals("Shared", again.now.what);
        assertEquals(0, again.skipped);
    }

    @Test
    public void theTimerCountsDownPausesAndIsUpOnce()
    {
        ActivityTimer t = new ActivityTimer();
        assertEquals(ActivityTimer.State.IDLE, t.state(0));
        t.start("Train Fishing 40 to 65", 30, 0);
        assertEquals(30 * 60_000L, t.left(0));
        t.pause(10 * 60_000L);
        assertEquals(20 * 60_000L, t.left(25 * 60_000L));
        t.resume(25 * 60_000L);
        t.add(5, 25 * 60_000L);
        assertEquals(ActivityTimer.State.RUNNING, t.state(49 * 60_000L));
        assertFalse(t.takeUp(49 * 60_000L));
        assertEquals(ActivityTimer.State.UP, t.state(50 * 60_000L));
        assertTrue(t.takeUp(50 * 60_000L));
        assertFalse("tells once", t.takeUp(51 * 60_000L));
        assertEquals("23:41", ActivityTimer.clock((23 * 60 + 41) * 1000L));
        assertEquals("1:02:03", ActivityTimer.clock((3600 + 2 * 60 + 3) * 1000L));
        Map<String, Integer> m = ActivityTimer.lengths("train fishing=45;herb run=10;bad");
        assertEquals(Integer.valueOf(45), m.get("train fishing"));
        assertEquals("train fishing=45;herb run=10", ActivityTimer.lengthsText(m));
    }

    @Test
    public void itemListSaysCarriedBankOrMissing()
    {
        Planner.Route r = GuideTest.route();
        Planner.Step quest = r.steps.get(0);
        assertEquals("Coins", quest.uses.get(0).item);
        Map<String, Map<String, Integer>> st = new LinkedHashMap<>();
        st.put(AccountState.BANK, Map.of("coins", 100, "rune pickaxe", 1));
        st.put(AccountState.INVENTORY, Map.of("dragon axe", 1));
        List<StepItems.Item> items = StepItems.of(quest, st, x -> "a shop");
        assertEquals(StepItems.Has.STORED, items.get(0).has);
        assertEquals("Coins: in the bank (100)", items.get(0).say());
        // a tool named by kind: any pickaxe counts, and "axe" is not a pickaxe
        assertTrue(StepItems.matches("rune pickaxe", "pickaxe"));
        assertFalse(StepItems.matches("rune pickaxe", "axe"));
        assertTrue(StepItems.matches("dragon axe", "axe"));
        assertEquals(Map.of("coins", true), StepItems.bankNames(items));
    }

    @Test
    public void runBoxShowsEachStopAndEndsWhenNothingIsLeft()
    {
        long start = 1_000_000_000_000L, now = start / 1000 + 600;
        Map<String, AccountState.Timer> stops = new LinkedHashMap<>();
        AccountState.Timer planted = new AccountState.Timer(AccountState.Timer.GROWING, now + 4800, start + 120_000);
        planted.since = start + 120_000;
        AccountState.Timer old = new AccountState.Timer(AccountState.Timer.GROWING, now + 600, start - 3_600_000);
        old.since = start - 3_600_000;
        stops.put("Falador", planted);
        stops.put("Catherby", new AccountState.Timer(AccountState.Timer.READY, 0, start));
        stops.put("Ardougne", new AccountState.Timer(AccountState.Timer.DISEASED, -1, start));
        stops.put("Hosidius", old);
        List<RunBox.Row> rows = RunBox.rows(AccountState.HERBS, stops, List.of("Falador", "Catherby", "Ardougne", "Hosidius", "Port Phasmatys"), start, now);
        assertEquals("[Falador: done, Catherby: ready, Ardougne: diseased, Hosidius: growing, ready in 10 min, Port Phasmatys: not seen yet]", rows.toString());
        assertFalse(RunBox.finished(rows));
        assertTrue(RunBox.finished(rows.subList(0, 1)));
        assertNull(RunBox.current());
    }

    /** USER 2026-11-11 "the bot didn't bring the full list": every item Quest Helper lists, or-groups and keys too. */
    @Test
    public void questItemsAreTheFullQuestHelperList()
    {
        com.goalguide.quests.QuestData q = com.goalguide.quests.QuestData.get();
        List<String> ds = new java.util.ArrayList<>();
        for (com.goalguide.quests.QuestData.Need n : q.needs("DRAGON_SLAYER_I")) if (n.kind.equals("item")) ds.add(n.what + " " + n.amount);
        for (String want : List.of("Unfired bowl 1", "Planks 3", "Steel nails 90", "Coins 2000", "Combat equipment 1")) assertTrue(ds.toString(), ds.contains(want));
        // an "or" group is one row, a key from the keyring is an item
        boolean boots = false, key = false;
        for (com.goalguide.quests.QuestData.Need n : q.needs("TROLL_STRONGHOLD")) boots |= n.what.equals("Climbing boots or 12 coins");
        for (com.goalguide.quests.QuestData.Need n : q.needs("SCORPION_CATCHER")) key |= n.what.toLowerCase().contains("dusty key");
        assertTrue(boots);
        assertTrue(key);
        // "Combat equipment" is a reminder, never a Get step
        ItemTree.Account a = ItemTree.Account.fresh();
        a.skills.put("ATTACK", 99);
        Planner.Route r = Planner.plan(q, null, a, 300, List.of(com.goalguide.goals.Goal.quest("DRAGON_SLAYER_I")));
        for (Planner.Step s : r.steps) assertFalse(s.toString(), s.what.contains("Combat equipment"));
        Planner.Step ds1 = null;
        for (Planner.Step s : r.steps) if ("DRAGON_SLAYER_I".equals(s.quest)) ds1 = s;
        boolean reminder = false;
        for (Planner.Use u : ds1.uses) reminder |= u.reminder && u.item.equals("Combat equipment");
        assertTrue(ds1.uses.toString(), reminder);
        List<StepItems.Item> items = StepItems.of(ds1, Map.of(), x -> null);
        boolean bring = false;
        for (StepItems.Item i : items) bring |= i.has == StepItems.Has.REMINDER && i.say().equals("Bring: Combat equipment");
        assertTrue(bring);
    }

    /** USER 2026-11-11: a cat following you counts for the cat quests, as Quest Helper counts it. */
    @Test
    public void aCatFollowingYouCounts()
    {
        com.goalguide.quests.QuestData q = com.goalguide.quests.QuestData.get();
        Map<String, Map<String, Integer>> st = new LinkedHashMap<>();
        st.put(AccountState.FOLLOWER, Map.of("kitten", 1));
        ItemTree.Account a = ItemTree.Account.fresh();
        a.owned.put("kitten", 1);
        Planner.Route r = Planner.plan(q, null, a, 300, List.of(com.goalguide.goals.Goal.quest("ICTHLARINS_LITTLE_HELPER")));
        for (Planner.Step s : r.steps) assertFalse("no Get step for the cat: " + s, s.kind == Planner.Kind.GET && s.what.toLowerCase().contains("cat"));
        Planner.Step ilh = null;
        for (Planner.Step s : r.steps) if ("ICTHLARINS_LITTLE_HELPER".equals(s.quest)) ilh = s;
        boolean carried = false;
        for (StepItems.Item i : StepItems.of(ilh, st, x -> null)) carried |= i.name.equals("Cat") && i.has == StepItems.Has.CARRIED;
        assertTrue(carried);
        assertTrue(com.goalguide.items.ItemData.sameKind("lazy hellcat", "cat"));
        assertFalse(com.goalguide.items.ItemData.sameKind("overgrown cat", "hunting cat"));
        assertFalse(com.goalguide.items.ItemData.sameKind("wooden cat", "cat"));
        assertTrue(com.goalguide.items.ItemData.sameKind("wooden cat", "wooden cat"));
    }

    @Test
    public void bankToggleShowsTheStepAndTheRunsThatAreOn()
    {
        BankFilter.nameOf(id -> id == 1 ? "Ruby" : id == 2 ? "Ranarr seed" : id == 3 ? "Oak sapling" : "Shark");
        BankFilter.set("Get 2 x Ruby", List.of("Ruby"), null);
        BankFilter.runs(RunItems.on(com.goalguide.decide.RunPrefs.ALL_ON.withRun("tree patches", false)));
        BankFilter.showing(BankFilter.BOTH);
        assertTrue(BankFilter.shows(1));
        assertTrue(BankFilter.shows(2));
        assertFalse("tree run is off", BankFilter.shows(3));
        assertFalse(BankFilter.shows(4));
        BankFilter.showing("Get 2 x Ruby");
        assertFalse(BankFilter.shows(2));
        BankFilter.showing("Herb run");
        assertTrue(BankFilter.shows(2));
        assertFalse(BankFilter.shows(1));
        // USER 2026-11-11 a line and a title per section: the step first, then each run that is on
        BankFilter.showing(BankFilter.BOTH);
        assertEquals(List.of("Get 2 x Ruby", "Herb run", "Fruit tree run", "Bird house run"), BankFilter.sections());
        assertEquals("Get 2 x Ruby", BankFilter.section(1));
        assertEquals("Herb run", BankFilter.section(2));
        assertNull(BankFilter.section(4));
        assertEquals("This step: Get 2 x Ruby", BankSections.title("Get 2 x Ruby", 1));
        assertTrue(BankSections.title("Herb run", 0).startsWith("Herb run <col=a0a0a0>(none"));
        BankFilter.showing(null);
        BankFilter.nameOf(null);
        BankFilter.set(null, List.of(), null);
        BankFilter.runs(Map.of());
    }

    /** USER 2026-11-11: a quest already started gets no Get step for an item it may have used; the next quest does. */
    @Test
    public void startedQuestDoesNotFetchUsedItemsAgain()
    {
        com.goalguide.quests.QuestData q = com.goalguide.quests.QuestData.get();
        ItemTree.Account a = ItemTree.Account.fresh();
        List<com.goalguide.goals.Goal> goal = List.of(com.goalguide.goals.Goal.quest("FAMILY_CREST"));
        Planner.Route before = Planner.plan(q, null, a, 300, goal);
        boolean got = false;
        for (Planner.Step s : before.steps) got |= s.kind == Planner.Kind.GET;
        assertTrue("not started: items are fetched", got);

        a.questsStarted.add("FAMILY_CREST");
        Planner.Route r = Planner.plan(q, null, a, 300, goal);
        Planner.Step fc = null;
        for (Planner.Step s : r.steps)
        {
            if ("FAMILY_CREST".equals(s.quest)) fc = s;
            assertFalse("no Get step for a started quest's items: " + s, s.kind == Planner.Kind.GET);
        }
        assertTrue(fc.started);
        boolean used = false;
        for (StepItems.Item i : StepItems.of(fc, Map.of(), x -> null))
        {
            assertFalse(i.has == StepItems.Has.MISSING);
            used |= i.has == StepItems.Has.USED && i.say().contains("maybe used");
        }
        assertTrue(used);
    }
}
