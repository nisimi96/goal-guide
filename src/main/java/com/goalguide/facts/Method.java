package com.goalguide.facts;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.goalguide.items.ItemTree;

/**
 * One way to train a skill, in the one format every skill's table uses: what it needs (levels, quests, tools, items
 * used up per hour), what it gives (xp and items per hour by level), who runs it, and where the numbers came from.
 * The tables live in {@link MethodTables}; the planners pick from them.
 */
public final class Method
{
    public enum Kind { GATHER, COURSE, MINIGAME, QUEST, PASSIVE }

    /** Upper-case skill name it trains ("FISHING"). */
    public final String skill;
    public final String id, name;
    public final Kind kind;
    /** Level of {@link #skill} to start it. */
    public final int level;
    /** {level, xp per hour} rows, rising by level; the rate at a level is the last row at or below it. */
    public final int[][] xp;
    /** net.runelite.api.Quest names, all needed. */
    public final List<String> quests;
    /** Other skills needed, upper-case name -> level. */
    public final Map<String, Integer> skills;
    /** Items held, not used up (lower-case names). */
    public final List<String> tools;
    /** Items used up: lower-case name -> {level, per hour} rows. */
    public final Map<String, int[][]> uses;
    /** Items made: lower-case name -> {level, per hour} rows; -1 per hour = makes it, rate not known. */
    public final Map<String, int[][]> gives;
    public final boolean members;
    /** Real players train this way (only methods players use are planned). */
    public final boolean used;
    public final String where;
    public final String note, source;
    /** Account modes it is for: "main", "iron", "uim", "gim". Empty = every mode. */
    public final java.util.Set<String> modes;

    public static final List<String> MODES = List.of("main", "iron", "uim", "gim");

    public Method(String skill, String id, String name, Kind kind, int level, int[][] xp, List<String> quests, Map<String, Integer> skills,
        List<String> tools, Map<String, int[][]> uses, Map<String, int[][]> gives, boolean members, boolean used, String where,
        String note, String source)
    {
        this(skill, id, name, kind, level, xp, quests, skills, tools, uses, gives, members, used, where, note, source, java.util.Set.of());
    }

    public Method(String skill, String id, String name, Kind kind, int level, int[][] xp, List<String> quests, Map<String, Integer> skills,
        List<String> tools, Map<String, int[][]> uses, Map<String, int[][]> gives, boolean members, boolean used, String where,
        String note, String source, java.util.Set<String> modes)
    {
        this.modes = modes;
        this.skill = skill;
        this.id = id;
        this.name = name;
        this.kind = kind;
        this.level = level;
        this.xp = xp;
        this.quests = quests;
        this.skills = skills;
        this.tools = tools;
        this.uses = uses;
        this.gives = gives;
        this.members = members;
        this.used = used;
        this.where = where;
        this.note = note;
        this.source = source;
    }

    /** The value of the last row at or below the level, 0 below the first row. */
    public static int at(int[][] rows, int level)
    {
        int r = 0;
        for (int[] row : rows) if (row[0] <= level) r = row[1];
        return r;
    }

    /** Xp per hour at this level, 0 below the start level. */
    public int xpAt(int lvl)
    {
        return lvl < level ? 0 : at(xp, lvl);
    }

    /** How many of an item it makes per hour at this level: 0 = none yet, -1 = makes it but the rate is unknown. */
    public int givesAt(String item, int lvl)
    {
        int[][] rows = gives.get(item);
        if (rows == null || lvl < level) return 0;
        return at(rows, lvl);
    }

    /** True when this method is for the account's mode ("iron"): a main does not plan an iron-only way, and back. */
    public boolean forMode(String mode)
    {
        return modes.isEmpty() || modes.contains(mode);
    }

    /** What the account still lacks for it: "Fishing 65", "quest Tai Bwo Wannai Trio", "members". Empty = can do it now. */
    public List<String> missing(ItemTree.Account a)
    {
        List<String> out = new ArrayList<>();
        int have = a.skills.getOrDefault(skill, 1);
        if (have < level) out.add(pretty(skill) + " " + level);
        skills.forEach((s, l) -> { if (a.skills.getOrDefault(s, 1) < l) out.add(pretty(s) + " " + l); });
        for (String q : quests) if (!a.questsDone.contains(q)) out.add("quest " + MethodTables.questName(q));
        if (members && !a.members) out.add("members");
        if (!forMode(a.mode())) out.add("not for " + a.mode() + " accounts");
        return out;
    }

    /** "FISHING" -> "Fishing". */
    public static String pretty(String skill)
    {
        return skill.isEmpty() ? skill : skill.charAt(0) + skill.substring(1).toLowerCase(java.util.Locale.ROOT);
    }

    /** Every item name it touches: tools, items used up, items made. */
    public List<String> items()
    {
        List<String> out = new ArrayList<>(tools);
        out.addAll(uses.keySet());
        out.addAll(gives.keySet());
        return out;
    }

    static Map<String, int[][]> rows(Map<String, int[][]> m)
    {
        return m == null ? new LinkedHashMap<>() : m;
    }

    @Override public String toString() { return skill + "/" + id; }
}
