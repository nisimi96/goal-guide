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
 * The fixed skill XP each quest gives, from quest_xp.tsv (OSRS Wiki, Quest experience rewards). Lamps and
 * "pick a skill" rewards are left out: the player picks those.
 */
public final class QuestXp
{
    private QuestXp() { }

    private static Map<String, Map<String, Integer>> table;

    /** Upper-case skill -> XP the quest gives; empty when none. */
    public static Map<String, Integer> of(String quest)
    {
        return table().getOrDefault(quest, Map.of());
    }

    /** Quests that give XP in this skill (upper case), most XP first. */
    public static List<String> giving(String skill)
    {
        List<String> out = new ArrayList<>();
        table().forEach((q, xp) -> { if (xp.containsKey(skill)) out.add(q); });
        out.sort((x, y) -> Integer.compare(of(y).get(skill), of(x).get(skill)));
        return out;
    }

    static Map<String, Map<String, Integer>> parse(List<String> lines)
    {
        Map<String, Map<String, Integer>> t = new LinkedHashMap<>();
        for (String l : lines)
        {
            if (l.startsWith("#") || l.isBlank()) continue;
            String[] p = l.split("\t");
            if (p.length == 3 && p[2].matches("\\d+")) t.computeIfAbsent(p[0], k -> new LinkedHashMap<>()).merge(p[1], Integer.parseInt(p[2]), Integer::sum);
        }
        return t;
    }

    private static synchronized Map<String, Map<String, Integer>> table()
    {
        if (table != null) return table;
        List<String> lines = new ArrayList<>();
        try (InputStream in = QuestXp.class.getResourceAsStream("quest_xp.tsv"))
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
        table = parse(lines);
        return table;
    }
}
