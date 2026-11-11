package com.goalguide.items;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/** Quest item text must find the item data's drops and shops ("no guide data" was a name miss). */
public class ItemNameMatchTest
{
    private static ItemData data(String... items)
    {
        ItemData d = new ItemData();
        for (String i : items) d.add(new ItemData.Shop(i, "A shop", "Somewhere", "Coins", 1, false));
        return d;
    }

    @Test
    public void questTextFindsTheItem()
    {
        ItemData d = data("Death rune", "Potato seed", "Supercompost", "Ultracompost", "Compost", "Pure essence", "Rune essence",
            "Limpwurt root", "Candle", "Lit torch", "Raw shark", "Raw manta ray", "Ranarr potion (unf)", "Knife");
        assertEquals(List.of("Death rune"), d.matches("death runes"));
        assertEquals(List.of("Potato seed"), d.matches("potato seeds"));
        assertEquals(List.of("Supercompost", "Ultracompost", "Compost"), d.matches("normal/super/ultra compost"));
        assertEquals(List.of("Rune essence", "Pure essence"), d.matches("rune/pure essence"));
        assertEquals(List.of("Limpwurt root"), d.matches("limpwurt"));
        assertEquals(List.of("Lit torch", "Candle"), d.matches("lit torch or candle"));
        assertEquals(List.of("Raw shark", "Raw manta ray"), d.matches("raw shark, manta ray or sea turtle"));
        assertEquals(List.of("Ranarr potion (unf)"), d.matches("ranarr potion (unf)"));
        assertEquals(List.of("Knife"), d.matches("knife to get the boots of lightness"));
        assertEquals(List.of(), d.matches("access to the air altar"));
    }

    @Test
    public void theAccountsItemsCountUnderEverySpelling()
    {
        ItemData d = data("Death rune", "Supercompost", "Ultracompost", "Compost", "Candle", "Lit torch");
        ItemTree.Account a = ItemTree.Account.fresh();
        a.owned.put("death rune", 20);
        a.owned.put("supercompost", 1);
        a.owned.put("ultracompost", 2);
        a.owned.put("candle", 1);
        assertEquals(20, com.goalguide.quests.QuestPlan.owned(a, d, "Death runes"));
        assertEquals(3, com.goalguide.quests.QuestPlan.owned(a, d, "Normal/Super/Ultra compost"));
        assertEquals(1, com.goalguide.quests.QuestPlan.owned(a, d, "Lit torch or candle"));
        assertEquals(0, com.goalguide.quests.QuestPlan.owned(a, d, "Shears"));
    }
}
