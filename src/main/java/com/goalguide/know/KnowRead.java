package com.goalguide.know;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ScriptID;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.game.ItemManager;
import com.goalguide.items.ItemData;
import com.goalguide.items.ItemTree;
import com.goalguide.quests.AccountRead;

/** Reads the client into an {@link AccountState}. Client thread only. */
final class KnowRead
{
    private KnowRead() { }

    /** Item container id -> storage name. The bank, seed vault, looting bag and kits are sent when the player opens or checks them. */
    static final Map<Integer, String> CONTAINERS = new LinkedHashMap<>();
    static
    {
        CONTAINERS.put(InventoryID.BANK, AccountState.BANK);
        CONTAINERS.put(InventoryID.INV, AccountState.INVENTORY);
        CONTAINERS.put(InventoryID.WORN, AccountState.WORN);
        CONTAINERS.put(InventoryID.SEED_VAULT, AccountState.SEED_VAULT);
        CONTAINERS.put(InventoryID.LOOTING_BAG, AccountState.LOOTING_BAG);
        CONTAINERS.put(InventoryID.SEED_BOX, AccountState.SEED_BOX);
        CONTAINERS.put(InventoryID.TACKLE_BOX, AccountState.TACKLE_BOX);
        CONTAINERS.put(InventoryID.FORESTRY_KIT, AccountState.FORESTRY_KIT);
        CONTAINERS.put(InventoryID.HUNTSMANS_KIT, AccountState.HUNTSMANS_KIT);
        CONTAINERS.put(InventoryID.DIZANAS_QUIVER_AMMO, AccountState.QUIVER);
        CONTAINERS.put(InventoryID.POH_COSTUMES, AccountState.POH_COSTUMES);
    }

    /** Levels, xp, quests done and started, diary tiers, quest points, ironman, members. */
    static boolean account(Client client, AccountState s, long now)
    {
        Map<String, Integer> levels = new LinkedHashMap<>(), xp = new LinkedHashMap<>();
        for (Skill sk : Skill.values())
        {
            try
            {
                levels.put(sk.name(), client.getRealSkillLevel(sk));
                xp.put(sk.name(), client.getSkillExperience(sk));
            }
            catch (RuntimeException ignored)
            {
                // Skill.OVERALL on some clients
            }
        }
        ItemTree.Account a = new ItemTree.Account();
        AccountRead.questsAndDiaries(client, a);
        MiniquestParts.read(client, a.questsDone, a.questsStarted);
        int qp = client.getVarpValue(VarPlayerID.QP);
        boolean members = client.getWorldType().contains(WorldType.MEMBERS);
        boolean modeChanged = s.putMode(a.mode);
        return s.putAccount(levels, xp, a.questsDone, a.questsStarted, qp, a.iron, members, now) | modeChanged;
    }

    /** Lower-case item name -> count, placeholders and empty slots skipped. */
    static Map<String, Integer> items(ItemManager items, Item[] slots)
    {
        return items(items::getItemComposition, items::canonicalize, slots);
    }

    /** Item name (key) -> count. a bank placeholder (the greyed-out "0" slot) is not the item. */
    static Map<String, Integer> items(java.util.function.IntFunction<net.runelite.api.ItemComposition> def, java.util.function.IntUnaryOperator canon, Item[] slots)
    {
        Map<String, Integer> m = new LinkedHashMap<>();
        if (slots == null) return m;
        for (Item it : slots)
        {
            if (it == null || it.getId() <= 0 || it.getQuantity() <= 0) continue;
            net.runelite.api.ItemComposition c = def.apply(it.getId());
            if (c != null && c.getPlaceholderTemplateId() != -1) continue;
            net.runelite.api.ItemComposition named = def.apply(canon.applyAsInt(it.getId()));
            m.merge(ItemData.key(named.getName()), it.getQuantity(), Integer::sum);
        }
        return m;
    }

    static String name(ItemManager items, int id)
    {
        return ItemData.key(items.getItemComposition(items.canonicalize(id)).getName());
    }

    static boolean container(ItemManager items, AccountState s, int containerId, ItemContainer c, long now)
    {
        String storage = CONTAINERS.get(containerId);
        if (storage == null || c == null) return false;
        return s.putStorage(storage, items(items, c.getItems()), now);
    }

    /** The rune pouch's rune-type enum and its slot varbits (the game's own, same as RuneLite's rune pouch overlay). */
    private static final int RUNEPOUCH_ENUM = 982;
    @SuppressWarnings("deprecation")
    private static final int[] POUCH_RUNE = {
        net.runelite.api.Varbits.RUNE_POUCH_RUNE1, net.runelite.api.Varbits.RUNE_POUCH_RUNE2, net.runelite.api.Varbits.RUNE_POUCH_RUNE3,
        net.runelite.api.Varbits.RUNE_POUCH_RUNE4, net.runelite.api.Varbits.RUNE_POUCH_RUNE5, net.runelite.api.Varbits.RUNE_POUCH_RUNE6};
    @SuppressWarnings("deprecation")
    private static final int[] POUCH_AMOUNT = {
        net.runelite.api.Varbits.RUNE_POUCH_AMOUNT1, net.runelite.api.Varbits.RUNE_POUCH_AMOUNT2, net.runelite.api.Varbits.RUNE_POUCH_AMOUNT3,
        net.runelite.api.Varbits.RUNE_POUCH_AMOUNT4, net.runelite.api.Varbits.RUNE_POUCH_AMOUNT5, net.runelite.api.Varbits.RUNE_POUCH_AMOUNT6};

    /** Rune item id -> count in the rune pouch. */
    static Map<Integer, Integer> pouchContents(Client client)
    {
        Map<Integer, Integer> out = new LinkedHashMap<>();
        EnumComposition runeEnum = client.getEnum(RUNEPOUCH_ENUM);
        if (runeEnum == null) return out;
        for (int i = 0; i < POUCH_RUNE.length; i++)
        {
            int type = client.getVarbitValue(POUCH_RUNE[i]);
            int qty = client.getVarbitValue(POUCH_AMOUNT[i]);
            if (type > 0 && qty > 0) out.merge(runeEnum.getIntValue(type), qty, Integer::sum);
        }
        return out;
    }

    /** The rune pouch: its varbits keep the runes even while the pouch is in the bank. */
    /**
     * The follower "is my cat following me"): the NPC walking after the player, as one item named
     * like it ("kitten", "hellcat"), any cat that follows you. Empty when none.
     */
    static boolean follower(Client client, AccountState s, long now)
    {
        net.runelite.api.NPC f = client.getFollower();
        Map<String, Integer> m = new LinkedHashMap<>();
        if (f != null && f.getName() != null) m.put(com.goalguide.items.ItemData.key(net.runelite.client.util.Text.removeTags(f.getName())), 1);
        if (m.isEmpty() && !s.seen(AccountState.FOLLOWER)) return false;
        return s.putStorage(AccountState.FOLLOWER, m, now);
    }

    static boolean runePouch(Client client, ItemManager items, AccountState s, long now)
    {
        Map<Integer, Integer> byId = pouchContents(client);
        Map<String, Integer> m = new LinkedHashMap<>();
        byId.forEach((id, n) -> m.merge(name(items, id), n, Integer::sum));
        if (m.isEmpty() && !s.seen(AccountState.RUNE_POUCH)) return false;
        return s.putStorage(AccountState.RUNE_POUCH, m, now);
    }

    /**
     * The bank's potion storage, the way RuneLite's Bank Tags reads it: a client script gives the doses held of each
     * potion. Counted as whole potions (doses over the potion's biggest dose), doses kept too. Only while the bank is open.
     */
    static boolean potionStorage(Client client, ItemManager items, AccountState s, long now)
    {
        Map<String, Integer> doses = new LinkedHashMap<>(), potions = new LinkedHashMap<>();
        for (int listId : new int[]{EnumID.POTIONSTORE_POTIONS, EnumID.POTIONSTORE_UNFINISHED_POTIONS})
        {
            EnumComposition list = client.getEnum(listId);
            if (list == null) continue;
            for (int potionEnumId : list.getIntVals())
            {
                EnumComposition potion = client.getEnum(potionEnumId);
                if (potion == null) continue;
                client.runScript(ScriptID.POTIONSTORE_DOSES, potionEnumId);
                int held = client.getIntStack()[0];
                if (held <= 0) continue;
                int maxDose = 0, anyId = -1;
                for (int d = 1; d <= 4; d++)
                {
                    int id = potion.getIntValue(d);
                    if (id > 0) { maxDose = d; anyId = id; }
                }
                if (anyId <= 0) continue;
                String key = name(items, anyId);
                doses.merge(key, held, Integer::sum);
                potions.merge(key, Math.max(1, held / maxDose), Integer::sum);
            }
        }
        int vials = client.getVarpValue(VarPlayerID.POTIONSTORE_VIALS);
        if (vials > 0) potions.merge("vial", vials, Integer::sum);
        return s.putPotions(doses, potions, now);
    }

    /** Sums of the live bag and worn items, for the first read after login (they are not re-sent until they change). */
    static boolean carried(Client client, ItemManager items, AccountState s, long now)
    {
        boolean changed = false;
        Map<Integer, String> live = new HashMap<>();
        live.put(InventoryID.INV, AccountState.INVENTORY);
        live.put(InventoryID.WORN, AccountState.WORN);
        for (Map.Entry<Integer, String> e : live.entrySet())
        {
            ItemContainer c = client.getItemContainer(e.getKey());
            if (c != null) changed |= container(items, s, e.getKey(), c, now);
        }
        return changed;
    }

    /** The bank screen's "used / size" counter, while the bank is open. */
    static boolean bankSpace(Client client, AccountState s)
    {
        int used = number(client.getWidget(net.runelite.api.gameval.InterfaceID.Bankmain.OCCUPIEDSLOTS));
        int size = number(client.getWidget(net.runelite.api.gameval.InterfaceID.Bankmain.CAPACITY));
        return used >= 0 && size > 0 && s.putBank(used, size);
    }

    /** The digits of a widget's text ("1,234" -> 1234), -1 when none. */
    static int number(net.runelite.api.widgets.Widget w)
    {
        if (w == null || w.getText() == null) return -1;
        String d = w.getText().replaceAll("<[^>]*>", "").replaceAll("[^0-9]", "");
        if (d.isEmpty() || d.length() > 6) return -1;
        return Integer.parseInt(d);
    }

    static final String[] SPELLBOOKS = {"standard", "ancient", "lunar", "arceuus"};
    static final String[] AUTOWEED = {"not owned", "off", "on"};
    /** RuneLite Item Charges keys (its config group "itemCharge", per account). */
    static final String[] CHARGE_KEYS = {"explorerRing", "dodgyNecklace", "bindingNecklace", "ringOfForging", "amuletOfChemistry",
        "amuletOfBounty", "braceletOfSlaughter", "expeditiousBracelet", "chronicle", "bloodEssence", "braceletOfClay"};

    /** House location varbit (0 = no house) and its values (OSRS Wiki, Player-owned house). */
    static final int VARBIT_HOUSE_CITY = 2187;
    static final Map<Integer, String> HOUSE_CITIES = Map.of(1, "Rimmington", 2, "Taverley", 3, "Pollnivneach", 4, "Rellekka", 5, "Brimhaven",
        6, "Yanille", 8, "Hosidius", 9, "Prifddinas", 13, "Aldarin");

    /**
     * What the account can do: house and its known rooms, spellbook, auto-weed, item charges. Client thread.
     * Spellbook varbit 4070 (0 standard .. 3 arceuus). House city varbit 2187 (0 = no house). Auto-weed varbit 5557
     * (Time Tracking: 2 = on). Bottomless bucket: varbits 7915 (compost type) and 7916 (uses).
     */
    static boolean abilities(Client client, net.runelite.client.config.ConfigManager config, AccountState s)
    {
        int city = client.getVarbitValue(VARBIT_HOUSE_CITY);
        String c = HOUSE_CITIES.get(city);
        String house = city == 0 ? "none" : c != null ? c : "city " + city;
        // What the house holds is set by another plugin when one knows it; kept as last set
        java.util.List<String> has;
        synchronized (s) { has = city == 0 ? java.util.List.of() : new java.util.ArrayList<>(s.houseHas); }
        int sb = client.getVarbitValue(net.runelite.api.gameval.VarbitID.SPELLBOOK);
        int aw = client.getVarbitValue(5557);
        Map<String, Integer> charges = new LinkedHashMap<>();
        for (String k : CHARGE_KEYS)
        {
            String v = config.getRSProfileConfiguration("itemCharge", k);
            if (v == null) continue;
            try { charges.put(k, Integer.parseInt(v.trim())); }
            catch (NumberFormatException ignored) { }
        }
        int bucketType = client.getVarbitValue(net.runelite.api.gameval.VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_TYPE);
        if (bucketType > 0) charges.put("bottomlessBucket", client.getVarbitValue(net.runelite.api.gameval.VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_QUANTITY));
        return s.putAbilities(house, has, sb >= 0 && sb < SPELLBOOKS.length ? SPELLBOOKS[sb] : "book " + sb,
            aw >= 0 && aw < AUTOWEED.length ? AUTOWEED[aw] : "unknown", charges);
    }
}
