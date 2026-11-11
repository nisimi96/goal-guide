package com.goalguide.know;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;

/**
 * Miniquests read by part, not as one done-or-not via the farm runs thread). Barbarian Training keeps
 * each part in its own varbit: 1 = tasked, 2 = did it, 3 = finished (Quest Helper). A part counts as started at 1 and
 * done at 2, so a method checks its own part ("BARBARIAN_FISHING"), not the whole miniquest.
 */
public final class MiniquestParts
{
    private MiniquestParts() { }

    @lombok.Value @lombok.experimental.Accessors(fluent = true)
    public static class Part
    {
        int varbit;
        String name;
    }

    /** Part key (as a quest key in questsDone / method tables) -> varbit and name. */
    public static final Map<String, Part> PARTS = new LinkedHashMap<>();
    static
    {
        PARTS.put("BARBARIAN_FISHING", new Part(VarbitID.BRUT_FISHING_R, "Barbarian Training: fishing"));
        PARTS.put("BARBARIAN_HARPOONING", new Part(VarbitID.BRUT_FISHING_S, "Barbarian Training: barehanded harpoon"));
        PARTS.put("BARBARIAN_FIREMAKING", new Part(VarbitID.BRUT_FIRE, "Barbarian Training: bow fires"));
        PARTS.put("BARBARIAN_PYRES", new Part(VarbitID.BRUT_CRAFT_SHIP, "Barbarian Training: pyre ships"));
        PARTS.put("BARBARIAN_PLANTING", new Part(VarbitID.BRUT_FARMING_PLANTING, "Barbarian Training: planting without a dibber"));
        PARTS.put("BARBARIAN_POT_SMASHING", new Part(VarbitID.BRUT_FARMING_SMASHING, "Barbarian Training: pot smashing"));
        PARTS.put("BARBARIAN_SPEARS", new Part(VarbitID.BRUT_SMITH_SPEAR, "Barbarian Training: spears"));
        PARTS.put("BARBARIAN_HASTAE", new Part(VarbitID.BRUT_SMITH_HASTA, "Barbarian Training: hastae"));
        PARTS.put("BARBARIAN_HERBLORE", new Part(VarbitID.BRUT_HERB_POTION, "Barbarian Training: barbarian mixes"));
    }

    /** "BARBARIAN_FISHING" -> "Barbarian Training: fishing"; null when it is no part. */
    public static String name(String key)
    {
        Part p = PARTS.get(key);
        return p == null ? null : p.name();
    }

    /** Adds the parts done (2+) and started (1+). Client thread. */
    static void read(Client client, Set<String> done, Set<String> started)
    {
        for (Map.Entry<String, Part> e : PARTS.entrySet())
        {
            int v = client.getVarbitValue(e.getValue().varbit());
            add(e.getKey(), v, done, started);
        }
    }

    static void add(String key, int value, Set<String> done, Set<String> started)
    {
        if (value >= 1) started.add(key);
        if (value >= 2) done.add(key);
    }
}
