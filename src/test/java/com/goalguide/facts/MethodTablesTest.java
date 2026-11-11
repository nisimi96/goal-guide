package com.goalguide.facts;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import com.goalguide.items.ItemTree;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The roots test: every need of every method table ends at a root (USER 2026-10-23 trace-to-the-roots rule). */
public class MethodTablesTest
{
    /** Needs that come from a skill whose table is not built yet. Adding one here is a choice, not an accident. */
    static final Set<String> KNOWN_GAPS = Set.of("food", "crossbow + mith grapple", "drift net",
        // Herblore secondaries from drops and patches the tables don't have yet (2026-11-11)
        "unicorn horn dust", "limpwurt root", "red spiders' eggs", "goat horn dust", "white berries", "potato cactus", "crushed nest");

    @Test
    public void everyTableParsesAndIdsAreUnique()
    {
        assertTrue(MethodTables.forSkill("AGILITY").size() > 20);
        assertEquals(14, MethodTables.forSkill("fishing").size());
        assertEquals(15, MethodTables.forSkill("thieving").size());
        assertEquals(19, MethodTables.forSkill("herblore").size());
        assertEquals(5, MethodTables.forSkill("farming").size());
        // every skill but Sailing has a table (2026-11-11)
        for (Skill s : Skill.values())
            if (s != Skill.SAILING) assertFalse(s.name(), MethodTables.forSkill(s.name()).isEmpty());
        Set<String> ids = new HashSet<>();
        for (Method m : MethodTables.all()) assertTrue("duplicate id " + m, ids.add(m.id));
    }

    @Test
    public void skillsAndQuestsAreRealNames()
    {
        Set<String> skills = new HashSet<>();
        for (Skill s : Skill.values()) skills.add(s.name());
        for (Method m : MethodTables.all())
        {
            assertTrue(m.toString(), skills.contains(m.skill));
            for (String s : m.skills.keySet()) assertTrue(m + " skill " + s, skills.contains(s));
            for (String q : m.quests) if (com.goalguide.know.MiniquestParts.name(q) == null) Quest.valueOf(q);
            assertTrue(m + " level", m.level >= 1 && m.level <= 99);
            for (int i = 1; i < m.xp.length; i++) assertTrue(m + " xp rows rise", m.xp[i][0] > m.xp[i - 1][0]);
        }
    }

    @Test
    public void everyNeedEndsAtARoot()
    {
        Map<String, MethodTables.Root> roots = MethodTables.roots();
        Set<String> gaps = new TreeSet<>();
        List<String> dangling = new ArrayList<>();
        for (Method m : MethodTables.all())
        {
            List<String> needs = new ArrayList<>(m.tools);
            needs.addAll(m.uses.keySet());
            for (String item : needs)
            {
                MethodTables.Root r = roots.get(item);
                if (r == null) { dangling.add(m + " needs " + item); continue; }
                if (r.kind == MethodTables.RootKind.PLANNED) gaps.add(item);
                if (r.kind == MethodTables.RootKind.METHOD)
                {
                    Method maker = MethodTables.byId(r.detail);
                    if (maker == null || !maker.gives.containsKey(item)) dangling.add(item + " root names " + r.detail + ", which does not make it");
                }
            }
        }
        assertTrue("needs with no root: " + dangling, dangling.isEmpty());
        assertEquals("needs whose root is a skill table not built yet", KNOWN_GAPS, gaps);
    }

    @Test
    public void ratesByLevel()
    {
        Method k = MethodTables.byId("karambwan");
        assertEquals(0, k.xpAt(64));
        assertEquals(29000, k.xpAt(65));
        assertEquals(31000, k.xpAt(79));
        assertEquals(840, k.givesAt("raw karambwan", 99));
        assertEquals(-1, MethodTables.byId("karambwanji").givesAt("raw karambwanji", 50));
    }

    @Test
    public void waysToMakePutsWhatYouCanDoFirst()
    {
        ItemTree.Account a = ItemTree.Account.fresh();
        a.skills.put("FISHING", 70);
        List<Method> ways = MethodTables.waysToMake("Raw karambwan", a);
        assertEquals("karambwan", ways.get(0).id);
        assertEquals(List.of("quest Tai Bwo Wannai Trio"), ways.get(0).missing(a));
        a.questsDone.add("TAI_BWO_WANNAI_TRIO");
        assertTrue(ways.get(0).missing(a).isEmpty());
        // lobsters: the one way, ready at 70
        assertFalse(MethodTables.waysToMake("raw lobster", a).isEmpty());
    }

    @Test
    public void agilityIsReadFromItsOwnTable()
    {
        Method canifis = MethodTables.byId("canifis");
        assertEquals("AGILITY", canifis.skill);
        assertEquals(17, canifis.givesAt("mark of grace", 40));
        assertFalse(MethodTables.byId("penguin").used);
    }

    @Test
    public void itemRootsShowsTheBestWayPerHour()
    {
        ItemTree.Account a = ItemTree.Account.fresh();
        a.skills.put("FISHING", 70);
        String html = com.goalguide.items.ItemRootsPlugin.tableWays(a, "Raw shark");
        assertTrue(html, html.contains("Sharks"));
        assertTrue(html, html.contains("90 an hour at 76"));
        assertTrue(html, html.contains("needs Fishing 76"));
        assertEquals("", com.goalguide.items.ItemRootsPlugin.tableWays(a, "Abyssal whip"));
        // dragon bones come from the Prayer table now
        assertTrue(com.goalguide.items.ItemRootsPlugin.tableWays(a, "Dragon bones").contains("blue dragons"));
    }

    @Test
    public void modesComeFromTheTable()
    {
        assertTrue(MethodTables.modes("all").isEmpty());
        assertEquals(Set.of("main", "iron"), MethodTables.modes("main, iron"));
        Method plunder = MethodTables.byId("plunder");
        assertTrue(plunder.forMode("iron"));
        // the UIM guide says Pyramid Plunder is not for UIM
        assertFalse(plunder.forMode("uim"));
        ItemTree.Account a = ItemTree.Account.fresh();
        a.skills.put("THIEVING", 80);
        a.questsDone.add("ICTHLARINS_LITTLE_HELPER");
        a.mode = "uim";
        assertTrue(plunder.missing(a).contains("not for uim accounts"));
        a.mode = null;
        a.iron = true;
        assertEquals("iron", a.mode());
        assertTrue(plunder.missing(a).isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void anUnknownModeIsAnError()
    {
        MethodTables.modes("hardcore");
    }

    @Test
    public void herbsTraceBackToMasterFarmers()
    {
        // ranarr for prayer potions <- herb runs <- herb seeds <- Master Farmers: the iron loop in the data
        assertEquals("herbrun", MethodTables.roots().get("ranarr weed").detail);
        assertEquals("masterfarmer", MethodTables.roots().get("herb seeds").detail);
        assertEquals(0, MethodTables.byId("herbrun").givesAt("ranarr weed", 31));
        assertEquals(-1, MethodTables.byId("herbrun").givesAt("ranarr weed", 32));
    }
}
