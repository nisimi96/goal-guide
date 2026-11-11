package com.goalguide.guide;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.goalguide.decide.Decide;
import com.goalguide.decide.RunPrefs;
import com.goalguide.know.AccountState;

/**
 * "but if i want do herb run mid quest?": what each run takes from the bank, for the bank button.
 * Item names checked on the wiki 2026-11-11 (Herb patch, Tree patch, Fruit tree patch, Bird house trapping, Seeds).
 * "run teleports": each run also lists the teleport items that reach its patches (the patch pages'
 * Transportation sections, read 2026-11-11), so whichever the account owns shows up. Spells need only runes: left out. Pure.
 */
public final class RunItems
{
    private RunItems() { }

    static final List<String> COMPOST = List.of("Compost", "Supercompost", "Ultracompost", "Bottomless compost bucket");

    static final Map<String, List<String>> RUNS = new LinkedHashMap<>();

    // Charge doses ("(4)") are matched by the item's base name; tiers are listed one by one
    static final List<String> HERB_TELES = List.of(
        // Falador
        "Explorer's ring 2", "Explorer's ring 3", "Explorer's ring 4", "Ring of the elements", "Draynor manor teleport",
        "Amulet of glory", "Skills necklace", "Ring of wealth", "Falador teleport",
        // Port Phasmatys
        "Ectophial", "Fenkenstrain's castle teleport", "Kharyrll teleport",
        // Catherby
        "Camelot teleport",
        // Ardougne
        "Ardougne cloak 2", "Ardougne cloak 3", "Ardougne cloak 4", "Combat bracelet", "Quest point cape", "Quest point cape (t)",
        "Ardougne teleport",
        // Hosidius
        "Xeric's talisman", "Teleport to house", "Hosidius teleport", "Kharedst's memoirs", "Book of the dead",
        // Civitas illa Fortis
        "Basic quetzal whistle", "Enhanced quetzal whistle", "Perfected quetzal whistle", "Hunter cape", "Hunter cape(t)",
        "Civitas illa fortis teleport",
        // Troll Stronghold, Weiss, Harmony Island
        "Trollheim teleport", "Ring of shadows", "Harmony island teleport", "Mos le'harmless teleport",
        // Farming Guild
        "Farming cape", "Farming cape(t)");

    static final List<String> TREE_TELES = List.of(
        "Lumbridge teleport", "Varrock teleport", "Falador teleport", "Achievement diary cape", "Achievement diary cape (t)",
        "Ring of wealth", "Skills necklace", "Games necklace", "Combat bracelet", "Goblin village sphere", "Taverley teleport",
        "Camelot teleport", "Slayer ring", "Grand seed pod", "Royal seed pod", "Necklace of passage", "Farming cape", "Farming cape(t)");

    static final List<String> FRUIT_TELES = List.of(
        "Achievement diary cape", "Achievement diary cape (t)", "Grand seed pod", "Royal seed pod", "Slayer ring",
        "Necklace of passage", "Camelot teleport", "Teleport to house", "Brimhaven teleport", "Karamja gloves 3", "Karamja gloves 4",
        "Ardougne teleport", "Amulet of glory", "Teleport crystal", "Eternal teleport crystal", "Skills necklace",
        "Farming cape", "Farming cape(t)");

    static
    {
        List<String> herbs = new ArrayList<>(List.of("Seed dibber", "Spade", "Rake", "Magic secateurs"));
        for (String h : List.of("Guam", "Marrentill", "Tarromin", "Harralander", "Ranarr", "Toadflax", "Irit", "Avantoe", "Kwuarm",
            "Huasca", "Snapdragon", "Cadantine", "Lantadyme", "Dwarf weed", "Torstol")) herbs.add(h + " seed");
        herbs.addAll(COMPOST);
        herbs.addAll(HERB_TELES);
        RUNS.put(AccountState.HERBS, herbs);

        // Saplings, and what the gardener takes to look after each (the patch's Protection column)
        List<String> trees = new ArrayList<>(List.of("Spade", "Rake", "Coins",
            "Oak sapling", "Willow sapling", "Maple sapling", "Yew sapling", "Magic sapling",
            "Tomatoes(5)", "Apples(5)", "Oranges(5)", "Cactus spine", "Coconut"));
        trees.addAll(COMPOST);
        trees.addAll(TREE_TELES);
        RUNS.put("tree patches", trees);

        List<String> fruit = new ArrayList<>(List.of("Spade", "Rake", "Coins",
            "Apple sapling", "Banana sapling", "Orange sapling", "Curry sapling", "Pineapple sapling", "Papaya sapling",
            "Palm sapling", "Dragonfruit sapling",
            "Sweetcorn", "Apples(5)", "Strawberries(5)", "Bananas(5)", "Watermelon"));
        fruit.addAll(COMPOST);
        fruit.addAll(FRUIT_TELES);
        RUNS.put("fruit tree patches", fruit);

        // Ready-made houses, or logs + clockwork + hammer + chisel; 10 low-level hop seeds fill one
        RUNS.put(AccountState.BIRDS, List.of("Digsite pendant", "Bird house", "Oak bird house", "Willow bird house", "Teak bird house",
            "Maple bird house", "Mahogany bird house", "Yew bird house", "Magic bird house", "Redwood bird house",
            "Clockwork", "Hammer", "Chisel", "Logs", "Oak logs", "Willow logs", "Teak logs", "Maple logs", "Mahogany logs",
            "Yew logs", "Magic logs", "Redwood logs",
            "Barley seed", "Hammerstone seed", "Asgarnian seed", "Jute seed", "Yanillian seed", "Krandorian seed", "Wildblood seed"));
    }

    /** "Herb run" -> its items, for each run switched on. */
    public static Map<String, List<String>> on(RunPrefs prefs)
    {
        Map<String, List<String>> out = new LinkedHashMap<>();
        RUNS.forEach((run, items) -> { if (prefs == null || prefs.on(run)) out.put(Decide.runName(run), items); });
        return out;
    }
}
