package com.goalguide.items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.goalguide.guide.StepItems;
import com.goalguide.know.AccountState;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** A steel dagger is made from a steel bar with a hammer; with no bar, the bar's ore and coal show too. */
public class MadeFromTest
{
    @Test
    public void madeFromGoesDownToWhatYouLack()
    {
        ItemData d = ItemDb.read();
        ItemTree.Account a = ItemTree.Account.fresh();
        List<String> names = names(StepItems.madeFrom(d, a, "Steel dagger", 1, Map.of(), x -> null));
        assertTrue(names.toString(), names.contains("hammer"));
        assertTrue(names.toString(), names.contains("steel bar"));
        assertTrue(names.toString(), names.contains("iron ore"));
        assertTrue(names.toString(), names.contains("coal"));
        // With a bar in the bank, no ore
        names = names(StepItems.madeFrom(d, a, "Steel dagger", 1, Map.of(AccountState.BANK, Map.of("steel bar", 1)), x -> null));
        assertFalse(names.toString(), names.contains("iron ore"));
    }

    private static List<String> names(List<StepItems.Item> items)
    {
        List<String> out = new ArrayList<>();
        for (StepItems.Item i : items) out.add(i.name.toLowerCase());
        return out;
    }
}
