package com.goalguide.facts;

import java.util.List;
import com.goalguide.items.ItemTree;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The runs, what opens their stops, and the per-account switches. */
public class RunsTest
{
    @Test
    public void runsLoadWithStopsAndItems()
    {
        assertEquals(List.of("Herb run", "Tree run", "Fruit tree run", "Bird houses"), Runs.names());
        for (String r : Runs.names())
        {
            assertFalse(r, Runs.stops(r).isEmpty());
            assertFalse(r, Runs.items(r).isEmpty());
        }
        assertTrue(Runs.items("Herb run").contains("Ranarr seed"));
    }

    @Test
    public void lockedStopsSayWhatOpensThem()
    {
        ItemTree.Account a = ItemTree.Account.fresh();
        Runs.Stop weiss = stop("Herb run", "Weiss"), guild = stop("Herb run", "Farming Guild");
        assertNull(stop("Herb run", "Falador").closed(a));
        assertEquals("needs Making Friends with My Arm", weiss.closed(a));
        assertEquals("needs Farming 65", guild.closed(a));
        a.questsDone.add("MAKING_FRIENDS_WITH_MY_ARM");
        a.skills.put("FARMING", 65);
        assertNull(weiss.closed(a));
        assertNull(guild.closed(a));
    }

    @Test
    public void switchesRoundTrip()
    {
        Runs.Switches s = Runs.Switches.ALL_ON.withRun("Tree run", false).withStop("Herb run", "Weiss", false);
        Runs.Switches back = Runs.Switches.decode(s.runsText(), s.stopsText());
        assertFalse(back.on("Tree run"));
        assertFalse(back.on("Herb run", "Weiss"));
        assertTrue(back.on("Herb run", "Falador"));
        assertEquals(List.of("Herb run", "Fruit tree run", "Bird houses"), back.onRuns());
        assertTrue(back.withRun("Tree run", true).on("Tree run"));
    }

    private static Runs.Stop stop(String run, String name)
    {
        for (Runs.Stop s : Runs.stops(run)) if (s.name.equals(name)) return s;
        throw new AssertionError(name);
    }
}
