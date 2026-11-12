package com.goalguide.items;

import java.util.Map;
import com.goalguide.guide.Trip;
import com.goalguide.know.AccountState;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Making a steel dagger with no bar: bank for the ore, a furnace, then the anvil. */
public class TripTest
{
    private final ItemData d = ItemDb.read();
    private final ItemTree.Account a = ItemTree.Account.fresh();

    private Trip.Stop next(Map<String, Map<String, Integer>> st)
    {
        return Trip.next(d, a, "Steel dagger", 1, st);
    }

    @Test
    public void oneStopAtATime()
    {
        Map<String, Integer> hammer = Map.of("hammer", 1);
        // Ore and coal in the bank: take them
        Trip.Stop s = next(Map.of(AccountState.BANK, Map.of("iron ore", 5, "coal", 10), AccountState.INVENTORY, hammer));
        assertEquals(Trip.BANK, s.place);
        assertEquals("take 1 iron ore, 2 coal", s.say.toLowerCase());
        // Ore on you: the furnace
        s = next(Map.of(AccountState.INVENTORY, Map.of("hammer", 1, "iron ore", 1, "coal", 2)));
        assertEquals("Furnace", s.place);
        assertEquals("smelt 1 steel bar", s.say.toLowerCase());
        // The bar on you: the anvil
        s = next(Map.of(AccountState.INVENTORY, Map.of("hammer", 1, "steel bar", 1)));
        assertEquals("Anvil", s.place);
        assertEquals("smith 1 steel dagger", s.say.toLowerCase());
        // The bar and hammer in the bank: take both
        s = next(Map.of(AccountState.BANK, Map.of("hammer", 1, "steel bar", 3)));
        assertEquals("take 1 hammer, 1 steel bar", s.say.toLowerCase());
        // Done
        assertNull(next(Map.of(AccountState.INVENTORY, Map.of("steel dagger", 1))));
        // No ore anywhere: nothing to mark
        assertNull(next(Map.of(AccountState.INVENTORY, hammer)));
    }
}
