package com.goalguide.facts;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.goalguide.items.ItemData;
import com.goalguide.items.ItemTree;

/**
 * A one-time unlock (an outfit, a tool, an access) the guide suggests as soon as the account meets its needs.
 * Read from unlocks.tsv next to {@link MethodTables}.
 */
public final class Unlock
{
    public final String id, name, why, where, source;
    public final Map<String, Integer> skills;
    public final List<String> quests, items;
    public final boolean members;
    /** Skill whose level sets the minutes, and {level, minutes} rows. */
    public final String timeSkill;
    public final int[][] minutes;

    Unlock(String id, String name, Map<String, Integer> skills, List<String> quests, List<String> items, boolean members,
        String timeSkill, int[][] minutes, String why, String where, String source)
    {
        this.id = id;
        this.name = name;
        this.skills = skills;
        this.quests = quests;
        this.items = items;
        this.members = members;
        this.timeSkill = timeSkill;
        this.minutes = minutes;
        this.why = why;
        this.where = where;
        this.source = source;
    }

    private static volatile List<Unlock> all;

    public static List<Unlock> all()
    {
        List<Unlock> l = all;
        if (l == null)
        {
            l = new ArrayList<>();
            for (String line : MethodTables.read("unlocks.tsv"))
            {
                String[] f = line.split("\t", -1);
                if (f.length < 10) throw new IllegalArgumentException("unlocks.tsv: " + f.length + " columns, want 10: " + line);
                String[] t = f[6].trim().split(" ", 2);
                l.add(new Unlock(f[0].trim(), f[1].trim(), MethodTables.skills(f[2]), MethodTables.list(f[3], ","),
                    lower(MethodTables.list(f[4], ";")), f[5].trim().equalsIgnoreCase("y"), t[0].toUpperCase(Locale.ROOT),
                    MethodTables.levelRows(t.length > 1 ? t[1] : "-"), f[7].trim(), f[8].trim(), f[9].trim()));
            }
            all = l;
        }
        return l;
    }

    private static List<String> lower(List<String> l)
    {
        List<String> out = new ArrayList<>();
        for (String s : l) out.add(s.toLowerCase(Locale.ROOT));
        return out;
    }

    /** Every level, quest and members need met. */
    public boolean open(ItemTree.Account a)
    {
        if (members && !a.members) return false;
        for (Map.Entry<String, Integer> e : skills.entrySet()) if (a.skills.getOrDefault(e.getKey(), 1) < e.getValue()) return false;
        for (String q : quests) if (!a.questsDone.contains(q)) return false;
        return true;
    }

    /** The items it gives that the account does not own yet (any storage). Empty = done. */
    public List<String> missing(ItemTree.Account a)
    {
        List<String> out = new ArrayList<>();
        for (String it : items) if (a.owned.getOrDefault(ItemData.key(it), 0) <= 0) out.add(it);
        return out;
    }

    /** Minutes at the account's level, -1 when not known. */
    public int minutesAt(ItemTree.Account a)
    {
        int lvl = a.skills.getOrDefault(timeSkill, 1), m = -1;
        for (int[] r : minutes) if (r[0] <= lvl) m = r[1];
        return m;
    }
}
