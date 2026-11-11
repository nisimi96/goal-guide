package com.goalguide.guide;

import java.util.List;
import com.goalguide.goals.Goal;
import com.goalguide.goals.Planner;
import com.goalguide.items.ItemTree;
import com.goalguide.quests.QuestData;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * several goals plan as one list. A quest two goals share goes first, a goal that is close is reached
 * early, a skill is trained only to what the next quest needs and the rest is filler; the guide shows Now, Next and the
 * filler.
 */
public class GuideTest
{
    /** Goal A needs Shared and A1 and Thieving 70; Goal B needs Shared and Thieving 50. */
    static final QuestData DATA = QuestData.parse(List.of(
        "GOAL_A\tquest\tSHARED\t\tFINISHED",
        "GOAL_A\tquest\tA_ONE\t\tFINISHED",
        "GOAL_A\tskill\tTHIEVING\t70\t",
        "GOAL_B\tquest\tSHARED\t\tFINISHED",
        "GOAL_B\tskill\tTHIEVING\t50\t",
        "SHARED\titem\tCoins\t1\tItemCollections.COINS"));

    static Planner.Route route()
    {
        ItemTree.Account a = ItemTree.Account.fresh();
        a.skills.put("THIEVING", 40);
        a.owned.put("coins", 100);
        return Planner.plan(DATA, null, a, 300, List.of(Goal.quest("GOAL_A"), Goal.quest("GOAL_B")));
    }

    @Test
    public void sharedFirstCloseGoalEarlyRestIsFiller()
    {
        Planner.Route r = route();
        StringBuilder order = new StringBuilder();
        for (Planner.Step s : r.steps) order.append(s.kind).append(' ').append(s.what).append(" | ");
        assertEquals("QUEST Shared | TRAIN Thieving 40 to 50 | QUEST Goal B | QUEST A One | QUEST Goal A | ", order.toString());
        assertEquals(List.of("Goal A", "Goal B"), r.steps.get(0).serves);
        assertEquals("SHARED", r.steps.get(0).quest);
        assertTrue(r.steps.get(4).how, r.steps.get(4).how.contains("needs Thieving 70 from the filler by now"));
        assertEquals(1, r.filler.size());
        assertEquals("Thieving 50 to 70", r.filler.get(0).what());
        assertEquals("Goal A needs 70", r.filler.get(0).byWhen);
    }

    @Test
    public void theGuideShowsNowNextAndFiller()
    {
        Guide g = Guide.of(route(), null);
        assertEquals("Shared", g.now.what);
        assertEquals("SHARED", g.now.quest);
        assertTrue(g.now.how, g.now.how.contains("moves Goal A and Goal B"));
        assertEquals("Train Thieving 40 to 50", g.next.what);
        assertNull(g.next.quest);
        assertEquals(List.of("Between steps: Thieving 50 to 70 (Goal A needs 70)"), g.recommend);
        assertNull(Guide.of(new Planner.Route(), null));
    }

    @Test
    public void questWikiLinks()
    {
        assertEquals("https://oldschool.runescape.wiki/w/Desert_Treasure_I/Quick_guide", com.goalguide.goals.StepDetail.questWikiUrl("DESERT_TREASURE_I"));
        assertEquals("https://oldschool.runescape.wiki/w/Ardougne_Easy_Diary", com.goalguide.goals.StepDetail.questWikiUrl("ARDOUGNE_EASY_DIARY"));
    }
}
