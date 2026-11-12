package com.goalguide.quests;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every quest's requirements: earlier quests, skill levels, combat level, quest points and items, from
 * quest_reqs.tsv (taken from Quest Helper's source, github.com/Zoinkwiz/quest-helper, BSD 2-Clause).
 *
 * <p>The 48 achievement diary tiers come the same way into diary_reqs.tsv, keyed like MORYTANIA_MEDIUM_DIARY, so a
 * diary is a goal the planner walks like a quest. Each tier has a "varbit" row: its
 * varbit id (the note column) that reads 1 once the tier is done.
 *
 * <p>quest_items.tsv (from the OSRS Wiki) names the quest that hands out an item no shop, skill or monster gives.
 */
public final class QuestData
{
    public static final class Need
    {
        /** Quest, skill, combat, qp, item, varbit (diaries: the done flag). */
        public final String kind;
        /** Quest name (net.runelite.api.Quest), Skill name, or item display name. */
        public final String what;
        public final int amount;
        /** Quest: FINISHED or IN_PROGRESS; skill: "boostable"; item: the game item id or ItemCollections.X; varbit: the varbit id. */
        public final String note;

        Need(String kind, String what, int amount, String note)
        {
            this.kind = kind;
            this.what = what;
            this.amount = amount;
            this.note = note;
        }

        @Override public String toString() { return kind + " " + what + " " + amount + " " + note; }
    }

    public final Map<String, List<Need>> byQuest = new LinkedHashMap<>();

    /** Lower-case item name -> the quest that hands it out (FINISHED, or IN_PROGRESS when starting it is enough). */
    public final Map<String, Need> givenBy = new LinkedHashMap<>();

    public List<Need> needs(String quest)
    {
        return byQuest.getOrDefault(quest, List.of());
    }

    /** The quest that hands this item out, null when it is no quest item. */
    public Need giver(String item)
    {
        return givenBy.get(com.goalguide.items.ItemData.key(item));
    }

    /** The quest key for typed text ("priest in peril", "morytania medium"), null when it's no quest or diary. */
    public static String named(String text)
    {
        String t = text.trim().toLowerCase(java.util.Locale.ROOT).replace("'", "");
        for (net.runelite.api.Quest q : net.runelite.api.Quest.values())
            if (q.getName().toLowerCase(java.util.Locale.ROOT).replace("'", "").equals(t) || q.name().equalsIgnoreCase(t.replace(' ', '_'))) return q.name();
        String key = t.replace(' ', '_').toUpperCase(java.util.Locale.ROOT);
        if (!key.endsWith("_DIARY")) key += "_DIARY";
        return get().byQuest.containsKey(key) ? key : null;
    }

    public static boolean isDiary(String key)
    {
        return key.endsWith("_DIARY");
    }

    /** Every diary tier in the table, in file order (area, then easy..elite as sorted by name). */
    public List<String> diaries()
    {
        List<String> out = new ArrayList<>();
        for (String k : byQuest.keySet()) if (isDiary(k)) out.add(k);
        return out;
    }

    /** The varbit id that reads 1 when the diary tier is done, or -1. */
    public int doneVarbit(String diary)
    {
        for (Need n : needs(diary)) if (n.kind.equals("varbit") && n.note.matches("\\d+")) return Integer.parseInt(n.note);
        return -1;
    }

    /** The level a quest or diary asks of this skill (upper case), 0 when none. */
    public int skillNeed(String key, String skill)
    {
        int lvl = 0;
        for (Need n : needs(key)) if (n.kind.equals("skill") && n.what.equals(skill)) lvl = Math.max(lvl, n.amount);
        return lvl;
    }

    public static QuestData parse(List<String> lines)
    {
        QuestData d = new QuestData();
        for (String line : lines)
        {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] c = line.split("\t", -1);
            if (c.length == 3) d.givenBy.put(com.goalguide.items.ItemData.key(c[0]), new Need("quest", c[1], 0, c[2]));
            if (c.length < 5) continue;
            int amount = 0;
            try { if (!c[3].isEmpty()) amount = Integer.parseInt(c[3]); }
            catch (NumberFormatException ignored) { }
            d.byQuest.computeIfAbsent(c[0], k -> new ArrayList<>()).add(new Need(c[1], c[2], amount, c[4]));
        }
        return d;
    }

    private static QuestData loaded;

    /** The table shipped with the plugin. */
    public static synchronized QuestData get()
    {
        if (loaded != null) return loaded;
        List<String> lines = new ArrayList<>();
        for (String file : new String[]{"quest_reqs.tsv", "diary_reqs.tsv", "quest_items.tsv"})
        {
            try (InputStream in = QuestData.class.getResourceAsStream(file))
            {
                if (in != null)
                {
                    BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    for (String l; (l = r.readLine()) != null; ) lines.add(l);
                }
            }
            catch (IOException ignored)
            {
            }
        }
        loaded = parse(lines);
        return loaded;
    }
}
