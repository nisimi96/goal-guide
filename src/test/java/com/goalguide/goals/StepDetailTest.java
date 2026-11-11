package com.goalguide.goals;

import java.util.List;
import java.util.Map;
import com.goalguide.decide.RunPrefs;
import com.goalguide.guide.BankFilter;
import com.goalguide.guide.RunItems;
import com.goalguide.items.ItemTree;
import com.goalguide.quests.QuestData;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** USER 2026-11-11: each route step opens into titled sections; the bank button offers the runs too. */
public class StepDetailTest
{
    @Test
    public void eachKindOfStepOpensIntoItsSections()
    {
        ItemTree.Account a = ItemTree.Account.fresh();
        Planner.Route r = Planner.plan(QuestData.get(), null, a, 0, List.of(Goal.quest("FAMILY_CREST")));
        Planner.Step train = null, quest = null, get = null;
        for (Planner.Step s : r.steps)
        {
            if (s.kind == Planner.Kind.TRAIN && train == null && s.hours > 0) train = s;
            if ("FAMILY_CREST".equals(s.quest)) quest = s;
            if (s.kind == Planner.Kind.GET && get == null) get = s;
        }
        assertNotNull(train);
        assertNotNull(quest);
        assertNotNull(get);

        assertNotNull(train.skill);
        assertTrue(train.to > train.from);
        String t = StepDetail.html(train, a, Map.of(), x -> null, null, "you");
        assertTrue(t, t.contains("RECOMMENDED") && t.contains("xp/h"));
        assertFalse(Planner.recommend(a, train.skill, train.from, train.to).isEmpty());

        String q = StepDetail.html(quest, a, Map.of(), x -> null, null, "you");
        assertTrue(q, q.contains("QUEST GUIDE") && q.contains("ITEMS"));
        assertEquals("https://oldschool.runescape.wiki/w/Family_Crest/Quick_guide", StepDetail.wikiUrl(quest));

        assertNotNull(get.item);
        String g = StepDetail.html(get, a, Map.of(), x -> null, null, "you");
        assertTrue(g, g.contains("HOW"));

        // the panel's card title: + closed, the step's own words
        assertTrue(GoalsView.Panel.cardTitle(1, quest, false).contains("1. "));
    }

    @Test
    public void bankButtonOffersTheRunBeingDoneFirst()
    {
        BankFilter.set("Get 2 x Ruby", List.of("Ruby"), null);
        BankFilter.runs(RunItems.on(RunPrefs.ALL_ON));
        BankFilter.prefer(null);
        assertEquals(List.of(BankFilter.BOTH, "Get 2 x Ruby", "Herb run", "Tree run", "Fruit tree run", "Bird house run"), BankFilter.choices());
        BankFilter.prefer("Herb run");
        assertEquals(BankFilter.BOTH, BankFilter.choices().get(0));
        assertEquals("Herb run", BankFilter.choices().get(1));
        BankFilter.runs(RunItems.on(RunPrefs.ALL_ON.withRun("tree patches", false)));
        assertFalse(BankFilter.choices().contains("Tree run"));
        BankFilter.prefer(null);
        BankFilter.set(null, List.of(), null);
        BankFilter.runs(Map.of());
    }
}
