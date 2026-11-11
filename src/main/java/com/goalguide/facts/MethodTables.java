package com.goalguide.facts;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.goalguide.items.ItemTree;

/**
 * Every skill's method table, in one format ({@link Method}), plus where each needed item comes from ({@link Root}).
 *
 * A skill's table is a TSV file next to this class, methods_&lt;skill&gt;.tsv, so a new skill is data, not code. Agility's
 * TSV is checked against another copy by a test, so the two never drift.
 * The roots test (MethodTablesTest) walks every need of every table down to a root.
 */
public final class MethodTables
{
    private MethodTables() { }

    /** Skills that have a TSV table. */
    static final List<String> TSV_SKILLS = List.of("AGILITY", "FISHING", "THIEVING", "HERBLORE", "FARMING",
        "ATTACK", "STRENGTH", "DEFENCE", "HITPOINTS", "RANGED", "PRAYER", "MAGIC", "SLAYER",
        "COOKING", "WOODCUTTING", "FLETCHING", "FIREMAKING", "CRAFTING", "SMITHING", "MINING", "HUNTER", "CONSTRUCTION", "RUNECRAFT",
        // "a Sailing table": wiki Sailing training, read 2026-11-11
        "SAILING");

    public enum RootKind
    {
        /** Bought for coins in a shop anyone can use. */
        SHOP,
        /** Picked up or handed out for nothing. */
        FREE,
        /** Given by a quest, miniquest or NPC once its requirement is met. */
        REWARD,
        /** Made by another method in these tables (detail = that method's id). */
        METHOD,
        /** Coins: any money maker. */
        COINS,
        /** Only the player can do it (a quest step). */
        YOU,
        /** Comes from a skill whose table is not built yet (detail names it). A known gap, listed in the roots test. */
        PLANNED
    }

    public static final class Root
    {
        public final String item;
        public final RootKind kind;
        public final String detail;

        Root(String item, RootKind kind, String detail)
        {
            this.item = item;
            this.kind = kind;
            this.detail = detail;
        }

        @Override public String toString() { return item + " <- " + kind + ": " + detail; }
    }

    private static volatile Map<String, List<Method>> bySkill;
    private static volatile Map<String, Root> roots;

    /** Every method of every table, Agility first. */
    public static List<Method> all()
    {
        List<Method> out = new ArrayList<>();
        for (List<Method> l : tables().values()) out.addAll(l);
        return out;
    }

    /** One skill's methods ("FISHING"), empty when it has no table yet. */
    public static List<Method> forSkill(String skill)
    {
        return tables().getOrDefault(skill.toUpperCase(Locale.ROOT), List.of());
    }

    public static Map<String, List<Method>> tables()
    {
        Map<String, List<Method>> t = bySkill;
        if (t == null)
        {
            t = new LinkedHashMap<>();
            for (String s : TSV_SKILLS) t.put(s, parse(s, read("methods_" + s.toLowerCase(Locale.ROOT) + ".tsv")));
            bySkill = t;
        }
        return t;
    }

    public static Method byId(String id)
    {
        for (Method m : all()) if (m.id.equals(id)) return m;
        return null;
    }

    /** Lower-case item name -> where it comes from. */
    public static Map<String, Root> roots()
    {
        Map<String, Root> r = roots;
        if (r == null)
        {
            r = new LinkedHashMap<>();
            for (String line : read("roots.tsv"))
            {
                String[] f = line.split("\t", -1);
                String k = f[0].trim().toLowerCase(Locale.ROOT);
                r.put(k, new Root(k, RootKind.valueOf(f[1].trim().toUpperCase(Locale.ROOT)), f.length > 2 ? f[2].trim() : ""));
            }
            roots = r;
        }
        return r;
    }

    /** The methods that make an item, best per hour first at the account's levels; the ones it can't do yet last. */
    public static List<Method> waysToMake(String item, ItemTree.Account a)
    {
        String key = item.trim().toLowerCase(Locale.ROOT);
        List<Method> out = new ArrayList<>();
        for (Method m : all()) if (m.used && m.gives.containsKey(key)) out.add(m);
        out.sort((x, y) ->
        {
            boolean xo = x.missing(a).isEmpty(), yo = y.missing(a).isEmpty();
            if (xo != yo) return xo ? -1 : 1;
            int lx = Math.max(x.level, a.skills.getOrDefault(x.skill, 1)), ly = Math.max(y.level, a.skills.getOrDefault(y.skill, 1));
            return Integer.compare(y.givesAt(key, ly), x.givesAt(key, lx));
        });
        return out;
    }

    /** "TAI_BWO_WANNAI_TRIO" -> "Tai Bwo Wannai Trio". */
    static String questName(String q)
    {
        String part = com.goalguide.know.MiniquestParts.name(q);
        if (part != null) return part;
        try { return net.runelite.api.Quest.valueOf(q).getName(); }
        catch (IllegalArgumentException e) { return q; }
    }

    // ---- the TSV format

    /**
     * Columns: id, name, kind, level, xp, quests, skills, tools, uses, gives, members, used, where, note, source,
     * and an optional 16th, modes: "all" or "main,iron" (which account modes it is for, see {@link Method#MODES}).
     * xp: "20:13000 30:25000". quests: "A,B". skills: "AGILITY 15,STRENGTH 15". tools: "a;b".
     * uses / gives: "raw trout=20:260,30:417;raw salmon=30:200" ("?" = rate not known). members, used: y / n. "-" = none.
     */
    static List<Method> parse(String skill, List<String> lines)
    {
        List<Method> out = new ArrayList<>();
        for (String line : lines)
        {
            String[] f = line.split("\t", -1);
            if (f.length < 15 || f.length > 16) throw new IllegalArgumentException(skill + ": " + f.length + " columns, want 15 or 16: " + line);
            out.add(new Method(skill, f[0].trim(), f[1].trim(), Method.Kind.valueOf(f[2].trim().toUpperCase(Locale.ROOT)), Integer.parseInt(f[3].trim()),
                levelRows(f[4]), list(f[5], ","), skills(f[6]), list(f[7], ";"), items(f[8]), items(f[9]), yes(f[10]), yes(f[11]),
                none(f[12]), none(f[13]), none(f[14]), f.length > 15 ? modes(f[15]) : java.util.Set.of()));
        }
        return out;
    }

    static int[][] levelRows(String s)
    {
        s = s.trim();
        if (s.isEmpty() || s.equals("-")) return new int[0][];
        String[] parts = s.split("[ ,]+");
        int[][] rows = new int[parts.length][];
        for (int i = 0; i < parts.length; i++)
        {
            String[] lv = parts[i].split(":");
            rows[i] = new int[]{Integer.parseInt(lv[0].trim()), lv[1].trim().equals("?") ? -1 : Integer.parseInt(lv[1].trim())};
            if (i > 0 && rows[i][0] <= rows[i - 1][0]) throw new IllegalArgumentException("levels must rise: " + s);
        }
        return rows;
    }

    static List<String> list(String s, String sep)
    {
        s = s.trim();
        if (s.isEmpty() || s.equals("-")) return List.of();
        List<String> out = new ArrayList<>();
        for (String p : s.split(sep)) if (!p.trim().isEmpty()) out.add(p.trim());
        return out;
    }

    static Map<String, Integer> skills(String s)
    {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (String p : list(s, ","))
        {
            String[] kv = p.split(" ");
            m.put(kv[0].trim().toUpperCase(Locale.ROOT), Integer.parseInt(kv[1].trim()));
        }
        return m;
    }

    static Map<String, int[][]> items(String s)
    {
        Map<String, int[][]> m = new LinkedHashMap<>();
        for (String p : list(s, ";"))
        {
            int eq = p.indexOf('=');
            m.put(p.substring(0, eq).trim().toLowerCase(Locale.ROOT), levelRows(p.substring(eq + 1)));
        }
        return m;
    }

    /** "all" or "-" = every mode; else "main,iron". */
    static java.util.Set<String> modes(String s)
    {
        s = s.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty() || s.equals("-") || s.equals("all")) return java.util.Set.of();
        java.util.Set<String> out = new java.util.LinkedHashSet<>(list(s, ","));
        for (String m : out) if (!Method.MODES.contains(m)) throw new IllegalArgumentException("unknown mode " + m + ": " + s);
        return out;
    }

    private static boolean yes(String s) { return s.trim().equalsIgnoreCase("y"); }

    private static String none(String s) { s = s.trim(); return s.isEmpty() || s.equals("-") ? null : s; }

    /** Non-comment, non-blank lines of a resource next to this class. */
    static List<String> read(String name)
    {
        List<String> out = new ArrayList<>();
        try (InputStream in = MethodTables.class.getResourceAsStream(name))
        {
            if (in == null) throw new IllegalStateException("missing resource " + name);
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line; (line = r.readLine()) != null; )
                if (!line.isBlank() && !line.startsWith("#")) out.add(line);
        }
        catch (IOException e)
        {
            throw new IllegalStateException(name, e);
        }
        return out;
    }
}
