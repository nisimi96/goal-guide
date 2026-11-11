package com.goalguide.guide;

import java.util.List;
import java.util.Map;
import java.util.Set;
import com.goalguide.know.AccountState;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The bank button shows the step's items, and tools of the kind the step names. */
public class BankFilterTest
{
    @Test
    public void showsTheStepsItemsOnly()
    {
        List<StepItems.Item> items = StepItems.of(GuideTest.route().steps.get(0), Map.of(AccountState.BANK, Map.of("coins", 100)), x -> null);
        Set<String> wanted = BankFilter.wanted(items);
        assertTrue(wanted.contains("coins"));
        assertTrue(BankFilter.shows("Coins", wanted));
        assertFalse(BankFilter.shows("Lobster", wanted));
        assertFalse(BankFilter.shows(null, wanted));

        Set<String> pick = Set.of("pickaxe");
        assertTrue(BankFilter.shows("Rune pickaxe", pick));
        assertFalse(BankFilter.shows("Rune axe", pick));
        assertTrue(BankFilter.shows("Super restore(4)", Set.of("super restore")));
        assertEquals(Set.of(), BankFilter.wanted(null));
    }
}
