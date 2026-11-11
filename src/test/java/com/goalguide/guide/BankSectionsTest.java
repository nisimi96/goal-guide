package com.goalguide.guide;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class BankSectionsTest
{
    private static StepItems.Item item(String name)
    {
        return StepItems.toGet(name, 1, Map.of(), null);
    }

    private static BankFilter.Section section(String title, String... names)
    {
        List<StepItems.Item> items = new java.util.ArrayList<>();
        for (String n : names) items.add(item(n));
        return new BankFilter.Section(title, BankFilter.keys(List.of(names)), items);
    }

    @Test
    public void everySectionGetsATitleRowAndItsOwnCopyOfASharedItem()
    {
        Map<String, Integer> ids = Map.of("Steel dagger", 1, "Hammer", 2, "Steel bar", 3, "Steel mace", 4);
        BankSections.Plan p = BankSections.plan(List.of(
            section("Get Steel dagger", "Steel dagger", "Hammer", "Steel bar"),
            section("Get Steel mace", "Steel mace", "Hammer", "Steel bar")),
            n -> ids.getOrDefault(n, 0), k -> List.of());
        int e = -1;
        assertArrayEquals(new int[]{
            e, e, e, e, e, e, e, e,
            1, 2, 3, e, e, e, e, e,
            e, e, e, e, e, e, e, e,
            4, 2, 3}, p.ids);
        assertEquals(List.of(0, 16), List.copyOf(p.titles.keySet()));
        assertEquals("Steel bar", p.counts.get(26).name);
    }
}
