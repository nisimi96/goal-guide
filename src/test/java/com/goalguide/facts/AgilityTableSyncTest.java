package com.goalguide.facts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.runelite.client.plugins.autoagility.AgilityMethods;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The guide's methods_agility.tsv and the Agility bot's own table (AgilityMethods) hold the same game facts: levels,
 * rates, quests, skills, items, marks. The guide keeps its own wording (no bot words, USER 2026-11-11), so names and
 * notes may differ; this test fails when a fact drifts. The bot's built courses are in the bot side's runners.tsv.
 */
public class AgilityTableSyncTest
{
    /** The Agility AIO's rows in the planner's format: the conversion MethodTables used before the TSV. */
    static List<Method> fromTheAio()
    {
        List<Method> out = new ArrayList<>();
        for (AgilityMethods.Method m : AgilityMethods.ALL)
        {
            Map<String, int[][]> gives = new LinkedHashMap<>();
            if (m.marksPerHour > 0) gives.put("mark of grace", new int[][]{{m.level, m.marksPerHour}});
            if (m.coinsPerHour > 0) gives.put("coins", new int[][]{{m.level, m.coinsPerHour}});
            List<String> tools = new ArrayList<>();
            for (String it : m.items) tools.add(it.toLowerCase(Locale.ROOT));
            out.add(new Method("AGILITY", m.id, m.name, Method.Kind.valueOf(m.kind.name()), m.level, m.xp, m.quests, m.skills, tools,
                new LinkedHashMap<>(), gives, true, !AgilityMethods.NOT_USED.contains(m.id), null,
                (m.gives == null || m.gives.isEmpty() ? "" : "also: " + m.gives + ". ") + m.note, "AgilityMethods (OSRS Wiki, 2026-10-23)"));
        }
        return out;
    }

    private static String rows(int[][] r)
    {
        StringBuilder b = new StringBuilder();
        for (int[] x : r) b.append(x[0]).append(':').append(x[1]).append(' ');
        return b.toString().trim();
    }

    private static String items(Map<String, int[][]> m)
    {
        StringBuilder b = new StringBuilder();
        m.forEach((k, v) -> b.append(k).append('=').append(rows(v)).append(';'));
        return b.toString();
    }

    private static String key(Method m)
    {
        return String.join(" | ", m.id, m.kind.name(), String.valueOf(m.level), rows(m.xp), m.quests.toString(),
            new java.util.TreeMap<>(m.skills).toString(), m.tools.toString(), items(m.uses), items(m.gives), String.valueOf(m.members),
            String.valueOf(m.used));
    }

    @Test
    public void tsvMatchesTheAgilityAio()
    {
        List<Method> aio = fromTheAio();
        List<Method> tsv = MethodTables.forSkill("AGILITY");
        assertEquals("rows", aio.size(), tsv.size());
        for (int i = 0; i < aio.size(); i++) assertEquals("methods_agility.tsv row " + (i + 1), key(aio.get(i)), key(tsv.get(i)));
    }

    @Test
    public void rootsMatchTheAgilityAio()
    {
        Map<String, MethodTables.Root> roots = MethodTables.roots();
        for (Map.Entry<String, String> e : AgilityMethods.ITEM_ROOTS.entrySet())
        {
            MethodTables.Root r = roots.get(e.getKey().toLowerCase(Locale.ROOT));
            assertNotNull("roots.tsv has no row for " + e.getKey(), r);
            String v = e.getValue();
            MethodTables.RootKind k = v.startsWith("shop:") ? MethodTables.RootKind.SHOP : v.startsWith("reward:") ? MethodTables.RootKind.REWARD
                : v.startsWith("you:") ? MethodTables.RootKind.YOU : v.startsWith("coins:") ? MethodTables.RootKind.COINS : MethodTables.RootKind.PLANNED;
            assertEquals(e.getKey(), k, r.kind);
        }
        assertTrue(Arrays.asList("AGILITY").contains(MethodTables.forSkill("agility").get(0).skill));
    }

}
