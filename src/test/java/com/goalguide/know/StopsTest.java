package com.goalguide.know;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Run box: each patch from Time Tracking, and when its state began. */
public class StopsTest
{
    @Test
    public void cropStatesBecomeStopStates()
    {
        assertEquals(AccountState.Timer.READY, Timers.stop("HARVESTABLE", 0, 1).state);
        assertEquals(AccountState.Timer.GROWING, Timers.stop("GROWING", 99, 1).state);
        assertEquals(99, Timers.stop("GROWING", 99, 1).readyAt);
        assertEquals(AccountState.Timer.DISEASED, Timers.stop("DISEASED", 0, 1).state);
        assertEquals(AccountState.Timer.DEAD, Timers.stop("DEAD", 0, 1).state);
        assertEquals(AccountState.Timer.UNKNOWN, Timers.stop(null, 0, 1).state);
        // Two patches at one stop: the one that needs the player
        AccountState.Timer grow = Timers.stop("GROWING", 99, 1), dead = Timers.stop("DEAD", 0, 1);
        assertEquals(dead, Timers.worse(grow, dead));
        assertEquals("Hosidius", Timers.STOP_NAMES.get("Kourend"));
    }

    @Test
    public void aStopKeepsWhenItsStateBegan()
    {
        AccountState s = new AccountState();
        Map<String, AccountState.Timer> m = new LinkedHashMap<>();
        m.put("Falador", new AccountState.Timer(AccountState.Timer.READY, 0, 1000));
        assertTrue(s.putStops(AccountState.HERBS, m));
        Map<String, AccountState.Timer> m2 = new LinkedHashMap<>();
        m2.put("Falador", new AccountState.Timer(AccountState.Timer.READY, 0, 5000));
        assertFalse(s.putStops(AccountState.HERBS, m2));
        assertEquals(1000, s.stops(AccountState.HERBS).get("Falador").since);
        Map<String, AccountState.Timer> m3 = new LinkedHashMap<>();
        m3.put("Falador", new AccountState.Timer(AccountState.Timer.GROWING, 99, 9000));
        assertTrue(s.putStops(AccountState.HERBS, m3));
        assertEquals(9000, s.stops(AccountState.HERBS).get("Falador").since);
        // Saved and read back
        AccountState back = Know.fromJson(Know.toJson(s));
        assertEquals(9000, back.stops(AccountState.HERBS).get("Falador").since);
    }

    /** Bank placeholders are not counted as items held. */
    @org.junit.Test
    public void bankPlaceholdersAreNotItems()
    {
        // 1265 bronze pickaxe, 1 of them; 9999 its placeholder (template set)
        java.util.function.IntFunction<net.runelite.api.ItemComposition> def = id -> (net.runelite.api.ItemComposition) java.lang.reflect.Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{net.runelite.api.ItemComposition.class}, (p, m, a) ->
            {
                switch (m.getName())
                {
                    case "getName": return id == 1265 || id == 9999 ? "Bronze pickaxe" : "Salmon";
                    case "getPlaceholderTemplateId": return id == 9999 ? 14401 : -1;
                    default: return m.getReturnType() == int.class ? 0 : m.getReturnType() == boolean.class ? false : null;
                }
            });
        java.util.Map<String, Integer> m = KnowRead.items(def, id -> id == 9999 ? 1265 : id,
            new net.runelite.api.Item[]{new net.runelite.api.Item(9999, 1), new net.runelite.api.Item(331, 5)});
        org.junit.Assert.assertEquals(java.util.Map.of("salmon", 5), m);
        m = KnowRead.items(def, id -> id, new net.runelite.api.Item[]{new net.runelite.api.Item(1265, 1)});
        org.junit.Assert.assertEquals(java.util.Map.of("bronze pickaxe", 1), m);
    }
}
