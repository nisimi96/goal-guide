package com.goalguide.goals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.goalguide.guide.Dailies;
import com.goalguide.items.ItemTree;
import com.goalguide.quests.QuestData;
import com.goalguide.quests.QuestLength;
import com.goalguide.quests.QuestPlan;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** USER 2026-11-11: quest lengths, closest goal by hours, QP and combat as steps, daily shop runs. */
public class QuestStepsTest
{
    @Test
    public void questLengthsAndPointsFromTheWiki()
    {
        assertEquals(0.25, QuestLength.hours("COOKS_ASSISTANT"), 1e-9);
        assertEquals(4, QuestLength.hours("Recipe for Disaster"), 1e-9);
        assertEquals(1, QuestLength.hours("DRAGON_SLAYER_I"), 1e-9);
        assertEquals(0.5, QuestLength.hours("ROMEO__JULIET"), 1e-9);
        assertEquals(1, QuestLength.hours("BARBARIAN_TRAINING"), 1e-9);
        assertEquals(0, QuestLength.qp("BARBARIAN_TRAINING"));
        assertEquals(1, QuestLength.hours("RECIPE_FOR_DISASTER__PIRATE_PETE"), 1e-9);
        assertEquals(-1, QuestLength.hours("NOT_A_QUEST"), 1e-9);
        // every quest RuneLite knows has a length now
        for (net.runelite.api.Quest q : net.runelite.api.Quest.values()) assertTrue(q.name(), QuestLength.hours(q.name()) > 0);
        assertEquals(5, QuestLength.qp("GOBLIN_DIPLOMACY"));
        assertEquals(10, QuestLength.qp("RECIPE_FOR_DISASTER"));
        // every quest the requirements table names has a length, but miniquests and the newest Sailing quests
        int known = 0, all = 0;
        for (String q : QuestData.get().byQuest.keySet())
        {
            if (QuestData.isDiary(q)) continue;
            all++;
            if (QuestLength.hours(q) > 0) known++;
        }
        assertTrue(known + " of " + all, known >= all - 30);
    }

    @Test
    public void stepsLinkToTheWiki()
    {
        Planner.Step q = new Planner.Step(Planner.Kind.QUEST, "Family Crest", "", 1, true);
        q.quest = "FAMILY_CREST";
        assertEquals("https://oldschool.runescape.wiki/w/Family_Crest", StepDetail.wikiPage(q));
        Planner.Step g = new Planner.Step(Planner.Kind.GET, "2 x Ruby", "", -1, true);
        g.item = "ruby";
        assertEquals("https://oldschool.runescape.wiki/w/Special:Search?search=Ruby&go=Go", StepDetail.wikiPage(g));
        Planner.Step t = new Planner.Step(Planner.Kind.TRAIN, "Hunter 9 to 52", "", 1, false);
        t.skill = "HUNTER";
        assertTrue(StepDetail.wikiPage(t), StepDetail.wikiPage(t).contains("search=Hunter+training"));
        // the item list links each item
        String h = StepDetail.html(g, com.goalguide.items.ItemTree.Account.fresh(), java.util.Map.of(), i -> null, null, "you");
        assertTrue(h, h.contains("WIKI") && h.contains("href="));
    }

    @Test
    public void combatLevelBecomesMeleeTraining()
    {
        ItemTree.Account a = ItemTree.Account.fresh();
        Planner.Route r = new Planner.Route();
        Planner.combat(r, a, new HashMap<>(), 20);
        assertFalse(r.steps.isEmpty());
        ItemTree.Account after = ItemTree.Account.fresh();
        for (Planner.Step s : r.steps)
        {
            assertEquals(Planner.Kind.TRAIN, s.kind);
            after.skills.put(s.skill, s.to);
        }
        assertTrue(QuestPlan.combat(after) >= 20);
    }

    @Test
    public void questPointsComeFromTheQuickestQuestsTheAccountCanDo()
    {
        ItemTree.Account a = ItemTree.Account.fresh();
        List<String> qs = Planner.forPoints(QuestData.get(), null, a, 6, Set.of());
        int got = 0;
        for (String q : qs) got += QuestLength.qp(q);
        assertTrue(qs + "", got >= 6);
        // a fresh account: quick ones with no needs, like Romeo & Juliet (5 QP, 30 min) or Goblin Diplomacy
        assertTrue(qs + "", qs.size() <= 3);
    }

    @Test
    public void dailiesShowWhatTheAccountQualifiesFor()
    {
        ItemTree.Account a = ItemTree.Account.fresh();
        List<String> keys = new ArrayList<>();
        for (Dailies.Daily d : Dailies.open(a)) keys.add(d.key);
        assertEquals(List.of("zaff"), keys);
        a.questsDone.add("VARROCK_EASY_DIARY");
        a.questsDone.add("ARDOUGNE_MEDIUM_DIARY");
        Map<String, String> say = new HashMap<>();
        for (Dailies.Daily d : Dailies.open(a)) say.put(d.key, d.say(a));
        assertEquals("Zaff: 15 battlestaves at 7,000 gp (Varrock)", say.get("zaff"));
        assertTrue(say.get("cromperty").startsWith("Wizard Cromperty: 100 "));
        // ticked today, cleared the next day (00:00 UTC)
        long day = 1_700_000_000_000L;
        String saved = Dailies.tick("", day, "zaff", true);
        assertEquals(Set.of("zaff"), Dailies.doneToday(saved, day));
        assertTrue(Dailies.doneToday(saved, day + 86_400_000L).isEmpty());
    }
}
