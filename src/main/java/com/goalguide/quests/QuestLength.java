package com.goalguide.quests;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * (quest lengths for the "I have 1 hour" plan): how long a quest takes, from the wiki's length column
 * (Quests/List, read 2026-11-11). Very Short 15 min, Short 30 min, Medium 1 hour, Long 2 hours, Very Long 4 hours.
 * Miniquests and the newest Sailing quests are not in the table: unknown (-1). Also the quest points each gives.
 */
public final class QuestLength
{
    private QuestLength() { }

    /** Hours used for ordering when a quest's length is not known. */
    public static final double UNKNOWN_GUESS = 1.0;

    private static Map<String, Double> table;
    private static Map<String, Integer> points;

    /** Hours for a quest key ("DRAGON_SLAYER_I") or name ("Dragon Slayer I"); -1 when not known. */
    public static double hours(String quest)
    {
        if (quest == null) return -1;
        Double h = table().get(norm(quest));
        return h == null ? -1 : h;
    }

    /** Quest points a quest gives (wiki Quests/List, read 2026-11-11); 0 when not known. */
    public static int qp(String quest)
    {
        if (quest == null) return 0;
        table();
        return points.getOrDefault(norm(quest), 0);
    }

    /** The wiki's word for a length in hours. */
    static double of(String length)
    {
        switch (length.trim().toLowerCase(Locale.ROOT))
        {
            case "very short": return 0.25;
            case "short": return 0.5;
            case "medium": return 1;
            case "long": return 2;
            case "very long": return 4;
            default: return -1;
        }
    }

    static String norm(String s)
    {
        return s.toLowerCase(Locale.ROOT).replace('_', ' ').replaceAll("[^a-z0-9]", "");
    }

    private static synchronized Map<String, Double> table()
    {
        if (table != null) return table;
        Map<String, Double> t = new HashMap<>();
        Map<String, Integer> q = new HashMap<>();
        try (InputStream in = QuestLength.class.getResourceAsStream("quest_lengths.tsv"))
        {
            if (in != null)
            {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                for (String l; (l = r.readLine()) != null; )
                {
                    if (l.startsWith("#") || l.isBlank()) continue;
                    String[] p = l.split("\t");
                    if (p.length < 2) continue;
                    double h = of(p[1]);
                    if (h > 0) t.put(norm(p[0]), h);
                    if (p.length > 2 && p[2].trim().matches("\\d+")) q.put(norm(p[0]), Integer.parseInt(p[2].trim()));
                }
            }
        }
        catch (IOException ignored)
        {
        }
        points = q;
        table = t;
        return t;
    }
}
