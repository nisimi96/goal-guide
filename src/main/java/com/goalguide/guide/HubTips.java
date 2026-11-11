package com.goalguide.guide;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "Plugin Hub suggestions per activity": RuneLite plugins that help with a step, shown on its card.
 * Mostly RuneLite's own (built in, maybe switched off), plus a few well-known Plugin Hub ones (marked "Plugin Hub").
 * Matched by skill and by words in the method's name. Pure.
 */
public final class HubTips
{
    private HubTips() { }

    static final Map<String, List<String>> BY_SKILL = new LinkedHashMap<>();
    static final Map<String, List<String>> BY_WORD = new LinkedHashMap<>();

    static
    {
        BY_SKILL.put("AGILITY", List.of("Agility (marks of grace, shortcuts)"));
        BY_SKILL.put("FISHING", List.of("Fishing (spots and timers)"));
        BY_SKILL.put("WOODCUTTING", List.of("Woodcutting (tree respawns, Forestry)"));
        BY_SKILL.put("MINING", List.of("Mining (rock respawns)"));
        BY_SKILL.put("HUNTER", List.of("Hunter (trap timers)"));
        BY_SKILL.put("COOKING", List.of("Cooking (burn count)"));
        BY_SKILL.put("SLAYER", List.of("Slayer (task and kills left)"));
        BY_SKILL.put("FARMING", List.of("Time Tracking (patch timers)", "Easy Farming (Plugin Hub)"));
        BY_SKILL.put("RUNECRAFT", List.of("Runecraft (pouches, rifts)"));
        BY_SKILL.put("PRAYER", List.of("Prayer (flick help, prayer left)"));

        BY_WORD.put("motherlode", List.of("Motherlode Mine (sack, veins)"));
        BY_WORD.put("wintertodt", List.of("Wintertodt (brazier, cold)"));
        BY_WORD.put("tithe", List.of("Tithe Farm (plant timers)"));
        BY_WORD.put("pest control", List.of("Pest Control (portals)"));
        BY_WORD.put("crashed star", List.of("World Hopper (find a world fast)"));
        BY_WORD.put("mixology", List.of("Mastering Mixology (Plugin Hub)"));
        BY_WORD.put("pickpocket", List.of("Item Charges (dodgy necklace)"));
        BY_WORD.put("cannon", List.of("Cannon (ammo left, spots)"));
        BY_WORD.put("bird house", List.of("Time Tracking (bird house timers)"));
        BY_WORD.put("glassblowing", List.of("Menu Entry Swapper"));
        BY_WORD.put("crab", List.of("Idle Notifier (when the crab dies)"));
    }

    /** The plugins worth having for a training step (skill + method name), at most 3. */
    public static List<String> forTraining(String skill, String method)
    {
        List<String> out = new ArrayList<>();
        String m = method == null ? "" : method.toLowerCase(Locale.ROOT);
        BY_WORD.forEach((w, ps) -> { if (m.contains(w)) for (String p : ps) if (!out.contains(p)) out.add(p); });
        if (skill != null) for (String p : BY_SKILL.getOrDefault(skill, List.of())) if (!out.contains(p)) out.add(p);
        return out.size() > 3 ? out.subList(0, 3) : out;
    }

    /** For a quest step. */
    public static List<String> forQuest()
    {
        return List.of("Quest Helper (Plugin Hub)", "Shortest Path (Plugin Hub)");
    }
}
