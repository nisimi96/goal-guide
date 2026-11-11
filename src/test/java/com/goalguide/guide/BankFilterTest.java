package com.goalguide.guide;

import java.util.List;
import java.util.Map;
import java.util.Set;
import com.goalguide.facts.Runs;
import com.goalguide.know.AccountState;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The bank button shows the step's items and each run that is on, in sections, with have/need counts. */
public class BankFilterTest
{
    @Test
    public void sectionsAreTheStepThenRunsThatAreOn()
    {
        Guide g = Guide.of(GuideTest.route());
        g.items = StepItems.of(g.now.step, Map.of(AccountState.BANK, Map.of("coins", 100)), x -> null);
        Runs.Switches sw = Runs.Switches.ALL_ON.withRun("Tree run", false).withRun("Fruit tree run", false).withRun("Bird houses", false);
        List<BankFilter.Section> secs = BankFilter.sections(g, sw);
        assertEquals(2, secs.size());
        assertEquals("Now: " + g.now.what, secs.get(0).title);
        assertEquals("Herb run", secs.get(1).title);
        assertEquals(secs.get(0), BankFilter.sectionOf("Coins", secs));
        assertEquals(secs.get(1), BankFilter.sectionOf("Ranarr seed", secs));
        assertNull(BankFilter.sectionOf("Lobster", secs));

        StepItems.Item coins = BankFilter.itemOf("Coins", secs.get(0).items);
        assertEquals("coins", coins.name.toLowerCase());
        assertEquals("100/" + coins.need, BankFilter.count(coins));
    }

    @Test
    public void gettingAQuestsItemsIsASectionPerItem()
    {
        Guide g = Guide.of(GuideTest.route());
        StepItems.Item axe = StepItems.of(g.now.step, Map.of(), x -> null).get(0);
        StepItems.Item planks = StepItems.toGet("Plank", 6, Map.of(AccountState.BANK, Map.of("plank", 2)), x -> null);
        assertEquals("2/8", BankFilter.count(planks));
        assertTrue(BankFilter.isShort(planks));
        g.parts = List.of(new Guide.Part("Get 6 x Plank", "Plank", List.of(planks, axe)),
            new Guide.Part("Get Bucket", "Bucket", List.of(StepItems.toGet("Bucket", 1, Map.of(), x -> null))));
        List<BankFilter.Section> secs = BankFilter.sections(g, Runs.Switches.ALL_ON.withRun("Herb run", false)
            .withRun("Tree run", false).withRun("Fruit tree run", false).withRun("Bird houses", false));
        assertEquals(2, secs.size());
        assertEquals("Get 6 x Plank", secs.get(0).title);
        assertEquals(secs.get(0), BankFilter.sectionOf("Plank", secs));
        assertEquals(secs.get(1), BankFilter.sectionOf("Bucket", secs));
    }

    @Test
    public void kindsAndDosesMatch()
    {
        assertTrue(BankFilter.shows("Rune pickaxe", Set.of("pickaxe")));
        assertFalse(BankFilter.shows("Rune axe", Set.of("pickaxe")));
        assertTrue(BankFilter.shows("Amulet of glory(4)", BankFilter.keys(List.of("Amulet of glory"))));
        assertFalse(BankFilter.shows(null, Set.of("coins")));
    }
}
