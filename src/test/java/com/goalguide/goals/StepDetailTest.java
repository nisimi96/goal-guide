package com.goalguide.goals;

import java.util.List;
import java.util.Map;
import com.goalguide.items.ItemTree;
import com.goalguide.quests.QuestData;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Each route step opens into titled sections. */
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

        // The panel's card title: + closed, the step's own words
        assertTrue(GoalsPanel.cardTitle(1, quest, false).contains("1. "));
    }
}
