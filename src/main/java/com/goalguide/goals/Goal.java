package com.goalguide.goals;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.runelite.api.Skill;
import com.goalguide.quests.QuestPlan;

/**
 * One thing the player wants: a level, a quest, a diary tier, or a stock of an item ("keep 1,000 raw karambwan").
 * Goals are kept in pinned order; the first is the focus.
 */
public final class Goal
{
    public enum Type { LEVEL, QUEST, STOCK }

    public final Type type;
    /** LEVEL: upper-case skill. QUEST: Quest name or diary key ("ARDOUGNE_MEDIUM_DIARY"). STOCK: lower-case item name. */
    public final String target;
    /** LEVEL: the level. STOCK: how many to keep. QUEST: 0. */
    public final int amount;

    public Goal(Type type, String target, int amount)
    {
        this.type = type;
        this.target = target;
        this.amount = amount;
    }

    public static Goal level(String skill, int level) { return new Goal(Type.LEVEL, skill.toUpperCase(Locale.ROOT), level); }

    public static Goal quest(String key) { return new Goal(Type.QUEST, key, 0); }

    public static Goal stock(String item, int n) { return new Goal(Type.STOCK, item.trim().toLowerCase(Locale.ROOT), n); }

    /** "Fishing 70", "Priest in Peril", "1,000 raw karambwan". */
    public String label()
    {
        switch (type)
        {
            case LEVEL: return QuestPlan.pretty(target) + " " + amount;
            case QUEST: return QuestPlan.pretty(target);
            default: return String.format("%,d", amount) + " " + target;
        }
    }

    /** "LEVEL:FISHING:70", "QUEST:PRIEST_IN_PERIL:0", "STOCK:raw karambwan:1000". */
    String encode()
    {
        return type + ":" + target.replace("|", "").replace(":", "") + ":" + amount;
    }

    static Goal decode(String s)
    {
        String[] p = s.split(":");
        if (p.length != 3) return null;
        try
        {
            return new Goal(Type.valueOf(p[0]), p[1], Integer.parseInt(p[2]));
        }
        catch (IllegalArgumentException e)
        {
            return null;
        }
    }

    public static String encodeAll(List<Goal> goals)
    {
        StringBuilder b = new StringBuilder();
        for (Goal g : goals) { if (b.length() > 0) b.append('|'); b.append(g.encode()); }
        return b.toString();
    }

    /** Bad entries are dropped, never thrown. */
    public static List<Goal> decodeAll(String s)
    {
        List<Goal> out = new ArrayList<>();
        if (s == null || s.isBlank()) return out;
        for (String part : s.split("\\|"))
        {
            Goal g = decode(part.trim());
            if (g != null) out.add(g);
        }
        return out;
    }

    /**
     * What the player typed, as a goal: "fishing 70", "70 fishing", "priest in peril", "ardougne medium",
     * "1000 raw karambwan", "raw karambwan x1000". Null when it is none of those.
     * @param questKey turns text into a Quest name or diary key, null when it is no quest
     */
    public static Goal parse(String text, java.util.function.Function<String, String> questKey)
    {
        String t = text.trim().replace(",", "");
        if (t.isEmpty()) return null;
        String[] w = t.split("\\s+");
        // A level: skill + number, either order
        if (w.length == 2)
        {
            Skill s = skill(w[0]);
            if (s != null && w[1].matches("\\d+")) return levelOk(s, Integer.parseInt(w[1]));
            s = skill(w[1]);
            if (s != null && w[0].matches("\\d+")) return levelOk(s, Integer.parseInt(w[0]));
        }
        String q = questKey.apply(t);
        if (q != null) return quest(q);
        // A stock: "1000 raw karambwan" or "raw karambwan x1000"
        if (w.length >= 2 && w[0].matches("\\d+")) return stock(t.substring(w[0].length()).trim(), Integer.parseInt(w[0]));
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(.+?)\\s*x\\s*(\\d+)$").matcher(t);
        if (m.find()) return stock(m.group(1), Integer.parseInt(m.group(2)));
        return null;
    }

    private static Goal levelOk(Skill s, int lvl)
    {
        return lvl >= 2 && lvl <= 99 ? level(s.name(), lvl) : null;
    }

    static Skill skill(String w)
    {
        for (Skill s : Skill.values()) if (s.name().equalsIgnoreCase(w) || s.getName().equalsIgnoreCase(w)) return s;
        return null;
    }

    @Override public boolean equals(Object o)
    {
        return o instanceof Goal && ((Goal) o).type == type && ((Goal) o).target.equals(target) && ((Goal) o).amount == amount;
    }

    @Override public int hashCode() { return type.hashCode() * 31 + target.hashCode() * 7 + amount; }

    @Override public String toString() { return encode(); }
}
