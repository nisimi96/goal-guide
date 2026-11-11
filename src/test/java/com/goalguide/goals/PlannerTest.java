package com.goalguide.goals;

import java.util.ArrayList;
import java.util.List;
import com.goalguide.items.ItemRootsPlugin;
import com.goalguide.items.ItemTree;
import com.goalguide.quests.QuestData;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PlannerTest
{
    private static Goal parse(String s)
    {
        return Goal.parse(s, ItemRootsPlugin::questNamed);
    }

    @Test
    public void typedGoals()
    {
        assertEquals(Goal.level("FISHING", 70), parse("fishing 70"));
        assertEquals(Goal.level("FISHING", 70), parse("70 Fishing"));
        assertEquals(Goal.quest("PRIEST_IN_PERIL"), parse("Priest in Peril"));
        assertEquals(Goal.quest("ARDOUGNE_MEDIUM_DIARY"), parse("ardougne medium"));
        assertEquals(Goal.stock("raw karambwan", 1000), parse("1,000 Raw karambwan"));
        assertEquals(Goal.stock("raw shark", 500), parse("raw shark x500"));
        assertNull(parse("hello"));
        assertNull(parse("fishing 120"));
        assertEquals("1,000 raw karambwan", parse("1000 raw karambwan").label());
        assertEquals("Fishing 70", parse("fishing 70").label());
    }

    @Test
    public void goalsSaveAndLoad()
    {
        List<Goal> g = List.of(Goal.level("FISHING", 70), Goal.quest("PRIEST_IN_PERIL"), Goal.stock("raw karambwan", 1000));
        assertEquals(g, Goal.decodeAll(Goal.encodeAll(g)));
        assertEquals(List.of(Goal.level("FISHING", 70)), Goal.decodeAll("LEVEL:FISHING:70|junk|BAD:x:1"));
        assertTrue(Goal.decodeAll(null).isEmpty());
    }

    private static ItemTree.Account account(int fishing)
    {
        ItemTree.Account a = ItemTree.Account.fresh();
        for (String s : new String[]{"AGILITY", "COOKING", "STRENGTH", "ATTACK", "DEFENCE", "HERBLORE", "CRAFTING", "WOODCUTTING", "FIREMAKING"}) a.skills.put(s, 60);
        a.skills.put("FISHING", fishing);
        a.iron = true;
        return a;
    }

    private static List<Planner.Step> of(Planner.Route r, Planner.Kind k)
    {
        List<Planner.Step> out = new ArrayList<>();
        for (Planner.Step s : r.steps) if (s.kind == k) out.add(s);
        return out;
    }

    @Test
    public void stockAddsItsQuestAndLevelThenHours()
    {
        ItemTree.Account a = account(40);
        a.owned.put("raw karambwan", 420);
        Planner.Route r = Planner.plan(QuestData.get(), null, a, 50, List.of(Goal.stock("raw karambwan", 1000)));
        // the karambwan method needs Tai Bwo Wannai Trio: it is in the route, as the player's turn
        boolean trio = false;
        for (Planner.Step s : of(r, Planner.Kind.QUEST)) trio |= s.what.equals("Tai Bwo Wannai Trio") && s.you;
        assertTrue(r.steps.toString(), trio);
        List<Planner.Step> train = of(r, Planner.Kind.TRAIN);
        assertEquals(r.steps.toString(), 1, train.stream().filter(s -> s.what.startsWith("Fishing")).count());
        Planner.Step fish = train.stream().filter(s -> s.what.startsWith("Fishing")).findFirst().get();
        assertEquals("Fishing 40 to 65", fish.what);
        assertTrue(fish.toString(), fish.hours > 3 && fish.hours < 80);
        Planner.Step stock = of(r, Planner.Kind.STOCK).get(0);
        // what the bank holds counts first: 580 more, at 580 an hour at 65
        assertTrue(stock.what, stock.what.startsWith("580 raw karambwan"));
        assertEquals(1.0, stock.hours, 0.01);
        assertTrue(r.hours > fish.hours);
    }

    @Test
    public void eachLevelIsTrainedOnce()
    {
        ItemTree.Account a = account(40);
        a.questsDone.add("TAI_BWO_WANNAI_TRIO");
        Planner.Route r = Planner.plan(QuestData.get(), null, a, 50, List.of(Goal.level("FISHING", 70), Goal.stock("raw karambwan", 100)));
        List<Planner.Step> train = of(r, Planner.Kind.TRAIN);
        assertEquals(r.steps.toString(), 1, train.size());
        assertEquals("Fishing 40 to 70", train.get(0).what);
        assertEquals(2, r.steps.size());
    }

    @Test
    public void metGoalsAreDoneAndUnknownRatesCount()
    {
        ItemTree.Account a = account(80);
        a.owned.put("raw shark", 2000);
        Planner.Route r = Planner.plan(QuestData.get(), null, a, 50, List.of(Goal.level("FISHING", 75), Goal.stock("raw shark", 1000), Goal.level("SAILING", 30)));
        assertTrue(r.done.toString(), r.done.contains("Fishing 75"));
        assertTrue(r.done.toString(), r.done.get(1).startsWith("1,000 raw shark"));
        // Sailing needs Pandemonium first, and its first levels have no wiki rate: one step, hours not known
        assertEquals(1, r.steps.size());
        assertEquals(1, r.unknown);
        assertTrue(r.steps.get(0).how, r.steps.get(0).how.contains("Pandemonium"));
        String html = GoalsView.routeHtml(r);
        assertTrue(html, html.contains("Train Sailing 1 to 30"));
        assertTrue(html, html.contains("<b>Done</b>") && html.contains("Fishing 75, 1,000 raw shark"));
    }

    @Test
    public void methodsWithNoWikiRateAreNamed()
    {
        ItemTree.Account a = account(80);
        a.skills.put("SLAYER", 40);
        Planner.Route r = Planner.plan(QuestData.get(), null, a, 50, List.of(Goal.level("SLAYER", 50)));
        // the wiki gives no xp per hour for Slayer masters: the step names them instead of "no method"
        assertTrue(r.steps.get(0).how, r.steps.get(0).how.contains("no rate on the wiki"));
        assertTrue(r.steps.get(0).how, r.steps.get(0).how.contains("Slayer master"));
    }

    @Test
    public void hourText()
    {
        assertEquals("30 min", GoalsView.hours(0.5));
        assertEquals("5.2 h", GoalsView.hours(5.2));
        assertEquals("3.0 days", GoalsView.hours(72));
    }

    @Test
    public void questItemNames()
    {
        assertEquals("iron spear", Planner.clean("Iron spear or better (You will lose the spear)"));
        assertEquals("logs", Planner.clean("Any logs to make a fire"));
        assertEquals("agility potion(4)", Planner.clean("Agility Potion (4)"));
        assertEquals("small fishing net", Planner.clean("Small Fishing Net"));
    }

    /** From yuval's screenshot of 2026-10-25: the low account's route to karambwan. */
    @Test
    public void trioRouteFromTheScreenshot()
    {
        ItemTree.Account a = ItemTree.Account.fresh();
        a.skills.put("AGILITY", 33);
        a.skills.put("FISHING", 29);
        a.skills.put("COOKING", 17);
        a.iron = true;
        Planner.Route r = Planner.plan(QuestData.get(), null, a, 20, List.of(Goal.stock("raw karambwan", 1000)));
        for (Planner.Step s : r.steps)
        {
            // the karambwan for the quest comes in the quest, not from the karambwan method that needs the quest
            if (s.kind == Planner.Kind.GET && s.what.startsWith("Raw karambwan")) assertEquals("you get it during the quest", s.how);
            if (s.kind == Planner.Kind.GET && s.what.startsWith("Coins")) assertEquals("any money maker", s.how);
        }
        assertTrue(r.keep.toString(), r.keep.containsKey("logs"));
        assertTrue(r.keep.toString(), r.keep.containsKey("iron spear"));
        assertTrue(r.keep.toString(), !r.keep.containsKey("coins"));
    }

    @Test
    public void thievingHoursFollowTheAccount()
    {
        ItemTree.Account a = account(40);
        a.skills.put("THIEVING", 42);
        Planner.Training plain = Planner.hours(a, "THIEVING", 42, 75);
        assertTrue(plain.how, plain.hours > 0);
        // The Feud opens blackjacking at 45: faster than without it
        a.questsDone.add("THE_FEUD");
        Planner.Training feud = Planner.hours(a, "THIEVING", 42, 75);
        assertTrue(feud.how, feud.hours <= plain.hours);
        // a free-to-play account has only men and women to steal from
        a.members = false;
        Planner.Training f2p = Planner.hours(a, "THIEVING", 42, 43);
        assertTrue(f2p.how, f2p.how.startsWith("Pickpocketing men"));
    }

    @Test
    public void farmingBelowTitheSaysTimedRuns()
    {
        ItemTree.Account a = account(40);
        a.skills.put("FARMING", 20);
        Planner.Training t = Planner.hours(a, "FARMING", 20, 30);
        assertEquals(-1, t.hours, 0.0);
        assertTrue(t.how, t.how.contains("Herb runs") && t.how.contains("timed runs"));
        a.skills.put("FARMING", 34);
        Planner.Training tithe = Planner.hours(a, "FARMING", 34, 60);
        assertTrue(tithe.how, tithe.hours > 0 && tithe.how.startsWith("Tithe Farm"));
    }

    @Test
    public void decideLogRollsOver() throws Exception
    {
        java.io.File dir = java.nio.file.Files.createTempDirectory("decide").toFile();
        java.io.File f = new java.io.File(dir, "decide-x.log");
        java.nio.file.Files.write(f.toPath(), new byte[(int) GoalsView.LOG_MAX_BYTES]);
        GoalsView.roll(f);
        assertTrue(!f.exists());
        assertTrue(new java.io.File(dir, "decide-x.log.old").exists());
        java.nio.file.Files.write(f.toPath(), new byte[10]);
        GoalsView.roll(f);
        assertTrue("a small log stays", f.exists());
    }
}
