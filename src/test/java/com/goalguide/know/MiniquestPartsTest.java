package com.goalguide.know;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.goalguide.facts.Method;
import com.goalguide.facts.MethodTables;
import com.goalguide.items.ItemTree;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MiniquestPartsTest
{
    @Test
    public void partsByValue()
    {
        Set<String> done = new HashSet<>(), started = new HashSet<>();
        MiniquestParts.add("BARBARIAN_FISHING", 0, done, started);
        assertTrue(started.isEmpty());
        MiniquestParts.add("BARBARIAN_FISHING", 1, done, started);
        assertTrue(started.contains("BARBARIAN_FISHING"));
        assertFalse(done.contains("BARBARIAN_FISHING"));
        MiniquestParts.add("BARBARIAN_FISHING", 3, done, started);
        assertTrue(done.contains("BARBARIAN_FISHING"));
    }

    /** Barbarian fishing checks its own part of Barbarian Training, and the route hands it over as the player's turn. */
    @Test
    public void barbarianFishingNeedsItsPart()
    {
        Method m = MethodTables.byId("barbarianfishing");
        ItemTree.Account a = ItemTree.Account.fresh();
        a.skills.put("FISHING", 60);
        a.skills.put("AGILITY", 20);
        a.skills.put("STRENGTH", 20);
        assertEquals(List.of("quest Barbarian Training: fishing"), m.missing(a));
        a.questsDone.add("BARBARIAN_FISHING");
        assertTrue(m.missing(a).isEmpty());
    }

}
