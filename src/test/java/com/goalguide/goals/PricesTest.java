package com.goalguide.goals;

import java.util.List;
import com.goalguide.items.ItemTree;
import com.goalguide.quests.QuestData;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** USER 2026-11-11: a main's Get steps show the Grand Exchange first; an iron's never do. Train steps list their tools. */
public class PricesTest
{
    static final QuestData DATA = QuestData.parse(List.of("SOME_QUEST\titem\tIron bar\t5\t"));

    @After
    public void off()
    {
        Prices.set(null);
    }

    static Planner.Route plan(String mode)
    {
        ItemTree.Account a = ItemTree.Account.fresh();
        a.mode = mode;
        return Planner.plan(DATA, null, a, 300, List.of(Goal.quest("SOME_QUEST")));
    }

    @Test
    public void aMainBuysFirst()
    {
        Prices.set(name -> name.equals("iron bar") ? 150 : 0);
        Planner.Step get = plan("main").steps.get(0);
        assertEquals(Planner.Kind.GET, get.kind);
        assertTrue(get.how, get.how.startsWith("Grand Exchange, about 150 gp each (750 gp); or make: "));
        assertEquals(750, get.ge);
        assertEquals(750, plan("main").ge);
        Planner.Step iron = plan("iron").steps.get(0);
        assertFalse(iron.how, iron.how.contains("Grand Exchange"));
        assertEquals(0, iron.ge);
        assertEquals("12.5k", Prices.gp(12_500));
        assertEquals("3m", Prices.gp(3_000_000));
    }

    @Test
    public void trainStepsListWhatTheyUse()
    {
        ItemTree.Account a = ItemTree.Account.fresh();
        Planner.Route r = Planner.plan(DATA, null, a, 300, List.of(Goal.level("FISHING", 15)));
        Planner.Step t = r.steps.get(0);
        assertEquals(Planner.Kind.TRAIN, t.kind);
        assertFalse(t.uses.toString(), t.uses.isEmpty());
    }
}
