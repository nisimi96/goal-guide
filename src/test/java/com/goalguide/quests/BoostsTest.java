package com.goalguide.quests;

import com.goalguide.items.ItemTree;
import org.junit.Test;

import static org.junit.Assert.*;

public class BoostsTest
{
    /** Every row as read on the OSRS Wiki 2026-10-25: change a number here only after checking the wiki again. */
    @Test public void tableMatchesTheWiki()
    {
        String[][] rows = {
            {"AGILITY", "Agility potion", "3", "0", "HERBLORE", "34"},
            {"AGILITY", "Summer pie", "5", "0", "COOKING", "95"},
            {"FISHING", "Fishing potion", "3", "0", "HERBLORE", "50"},
            {"FISHING", "Admiral pie", "5", "0", "COOKING", "70"},
            {"HUNTER", "Hunter potion", "3", "0", "HERBLORE", "53"},
            {"SLAYER", "Wild pie", "5", "0", "COOKING", "85"},
            {"RANGED", "Wild pie", "4", "0", "COOKING", "85"},
            {"FARMING", "Garden pie", "3", "0", "COOKING", "34"},
            {"CRAFTING", "Mushroom pie", "4", "0", "COOKING", "60"},
            {"FLETCHING", "Dragonfruit pie", "4", "0", "COOKING", "73"},
            {"HERBLORE", "Botanical pie", "4", "0", "COOKING", "52"},
            {"ATTACK", "Attack potion", "3", "10", "HERBLORE", "3"},
            {"ATTACK", "Super attack", "5", "15", "HERBLORE", "45"},
        };
        for (String[] r : rows)
        {
            Boosts.Boost found = null;
            for (Boosts.Boost b : Boosts.BY_SKILL.get(r[0])) if (b.item.equals(r[1])) found = b;
            assertNotNull(r[1], found);
            assertEquals(r[1], Integer.parseInt(r[2]), found.flat);
            assertEquals(r[1], Integer.parseInt(r[3]), found.percent);
            assertEquals(r[1], r[4], found.makeSkill);
            assertEquals(r[1], Integer.parseInt(r[5]), found.makeLevel);
        }
        Boosts.Boost stout = Boosts.BY_SKILL.get("MINING").get(0);
        assertNull("dwarven stout is sold in shops", stout.makeSkill);
    }

    @Test public void percentBoostsRoundDown()
    {
        Boosts.Boost superAttack = Boosts.BY_SKILL.get("ATTACK").get(1);
        assertEquals(5 + 9, superAttack.amount(60)); // 15% of 60 = 9
        assertEquals(5 + 10, superAttack.amount(69)); // 10.35 -> 10
    }

    @Test public void smallestBoostThatIsEnough()
    {
        ItemTree.Account a = ItemTree.Account.fresh();
        assertEquals("Agility potion", Boosts.toReach("AGILITY", 47, 50, a).item);
        assertEquals("Summer pie", Boosts.toReach("AGILITY", 45, 50, a).item);
        assertNull(Boosts.toReach("AGILITY", 44, 50, a));
        assertNull("met already", Boosts.toReach("AGILITY", 50, 50, a));
        assertNull("no everyday boost", Boosts.toReach("PRAYER", 42, 43, a));
    }
}
