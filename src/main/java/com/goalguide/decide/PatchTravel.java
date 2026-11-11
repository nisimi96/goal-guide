package com.goalguide.decide;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import com.goalguide.items.ItemTree;

/**
 * How far each herb, tree and fruit tree patch and the bird houses are for this account, from a route planner: the teleports it owns or has
 * unlocked, carried or in the bank (a run is packed at the bank). "why would you spend 10 min
 * walking when its not worth it": a patch only counts when it is quick to reach.
 *
 * Patch tiles: each sits inside the herb patch region RuneLite's Time Tracking (FarmingWorld) lists for it, checked
 * 2026-10-25. Gates from the OSRS Wiki (Herb patch): Troll Stronghold needs My Arm's Big Adventure, Weiss needs
 * Making Friends with My Arm, Harmony needs the elite Morytania diary, the Farming Guild needs 65 Farming.
 */
public final class PatchTravel
{
    private PatchTravel() { }

    @lombok.Value @lombok.experimental.Accessors(fluent = true)
    public static class Patch
    {
        String name;
        WorldPoint tile;
        String quest;
        int farming;
    }

    public static final List<Patch> HERBS = List.of(
        new Patch("Falador", new WorldPoint(3058, 3311, 0), null, 0),
        new Patch("Port Phasmatys", new WorldPoint(3605, 3529, 0), null, 0),
        new Patch("Catherby", new WorldPoint(2813, 3463, 0), null, 0),
        new Patch("Ardougne", new WorldPoint(2670, 3374, 0), null, 0),
        new Patch("Hosidius", new WorldPoint(1738, 3550, 0), null, 0),
        new Patch("Civitas illa Fortis", new WorldPoint(1582, 3093, 0), null, 0),
        new Patch("Troll Stronghold", new WorldPoint(2826, 3694, 0), "MY_ARMS_BIG_ADVENTURE", 0),
        new Patch("Weiss", new WorldPoint(2848, 3934, 0), "MAKING_FRIENDS_WITH_MY_ARM", 0),
        new Patch("Harmony Island", new WorldPoint(3789, 2837, 0), "MORYTANIA_ELITE_DIARY", 0),
        new Patch("Farming Guild", new WorldPoint(1238, 3726, 0), null, 65));

    /**
     * Tree patches (OSRS Wiki, Tree patch: only the Farming Guild's needs a level, 65). Tiles inside Time Tracking's
     * regions. Nemus Retreat is not in yet (no checked tile).
     */
    public static final List<Patch> TREES = List.of(
        new Patch("Lumbridge", new WorldPoint(3193, 3231, 0), null, 0),
        new Patch("Varrock", new WorldPoint(3229, 3459, 0), null, 0),
        new Patch("Falador", new WorldPoint(3004, 3373, 0), null, 0),
        new Patch("Taverley", new WorldPoint(2936, 3438, 0), null, 0),
        new Patch("Gnome Stronghold", new WorldPoint(2436, 3415, 0), null, 0),
        new Patch("Farming Guild", new WorldPoint(1232, 3736, 0), null, 65));

    /** Fruit tree patches (OSRS Wiki, Fruit tree patch: the Farming Guild's needs 85). Kastori is not in yet. */
    public static final List<Patch> FRUIT_TREES = List.of(
        new Patch("Catherby", new WorldPoint(2860, 3433, 0), null, 0),
        new Patch("Gnome Stronghold", new WorldPoint(2475, 3446, 0), null, 0),
        new Patch("Tree Gnome Village", new WorldPoint(2490, 3180, 0), null, 0),
        new Patch("Brimhaven", new WorldPoint(2764, 3212, 0), null, 0),
        new Patch("Lletya", new WorldPoint(2346, 3162, 0), null, 0),
        new Patch("Farming Guild", new WorldPoint(1243, 3759, 0), null, 85));

    /** The four bird houses are close together on Fossil Island (Bone Voyage): one stop near the House on the Hill. */
    public static final List<Patch> BIRD_HOUSES = List.of(
        new Patch("Fossil Island", new WorldPoint(3764, 3869, 0), "BONE_VOYAGE", 0));

    /** Timer name (AccountState) -> its patches. */
    public static final Map<String, List<Patch>> RUNS = new LinkedHashMap<>();
    static
    {
        RUNS.put(com.goalguide.know.AccountState.HERBS, HERBS);
        RUNS.put("tree patches", TREES);
        RUNS.put("fruit tree patches", FRUIT_TREES);
        RUNS.put(com.goalguide.know.AccountState.BIRDS, BIRD_HOUSES);
    }

    /** Quick stops a run needs to be worth it: "3 patches", the bird houses are one stop. */
    public static int minStops(String timer)
    {
        return RUNS.get(timer) == BIRD_HOUSES ? 1 : 3;
    }

    /** Not open to this account (quest, diary or level). */
    public static final int CLOSED = -2;
    /** No route found inside the planner's budget. */
    public static final int NO_ROUTE = -1;

    /** Seconds per game tick. */
    static final double TICK = 0.6;

    /** The patch is open to this account (its quest and Farming level). */
    public static boolean open(Patch p, ItemTree.Account a)
    {
        if (p.quest() != null && !a.questsDone.contains(p.quest())) return false;
        return a.skills.getOrDefault("FARMING", 1) >= p.farming();
    }

    /** "Falador 40 s" or "Weiss: closed". */
    public static String say(String name, int ticks)
    {
        if (ticks == CLOSED) return name + ": not open";
        if (ticks == NO_ROUTE) return name + ": no route";
        int s = (int) Math.round(ticks * TICK);
        return name + " " + (s < 90 ? s + " s" : Math.round(s / 60.0) + " min");
    }

    // ---- unlocks: "each patch have unlocks and levels ... it can also plan to train to unlock better routes"

    /**
     * A quest, diary or level that would put a patch within {@link #QUICK_TICKS}: {@code needs} are goal texts the
     * goal box takes ("Agility 70", "Tree Gnome Village", "MORYTANIA_ELITE_DIARY").
     */
    @lombok.Value @lombok.experimental.Accessors(fluent = true)
    public static class Idea
    {
        String run;
        String patch;
        int ticks;
        List<String> needs;
    }

    /** Two minutes, in ticks: */
    public static final int QUICK_TICKS = 200;

    /** What the goal box takes for a quest or diary key: "My Arm's Big Adventure", "Morytania elite". */
    public static String goalText(String key)
    {
        if (key.endsWith("_DIARY"))
        {
            String[] w = key.substring(0, key.length() - 6).toLowerCase(java.util.Locale.ROOT).split("_");
            String region = String.join(" ", java.util.Arrays.copyOf(w, w.length - 1));
            return Character.toUpperCase(region.charAt(0)) + region.substring(1) + " " + w[w.length - 1];
        }
        try { return net.runelite.api.Quest.valueOf(key).getName(); }
        catch (IllegalArgumentException e) { return key; }
    }

    /** The last ideas worked out, per RS profile (recomputed with the travel check, not saved). */
    public static final Map<String, List<Idea>> IDEAS = new java.util.concurrent.ConcurrentHashMap<>();
}
