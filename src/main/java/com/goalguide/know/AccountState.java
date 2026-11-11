package com.goalguide.know;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import com.goalguide.items.ItemData;
import com.goalguide.items.ItemTree;

/**
 * What the guide knows about one account: levels, quests, diaries and every storage as last seen.
 * Only Know reads the client for this; the planner asks {@link Know}.
 *
 * Plain data, saved as JSON. Every method that reads or writes is synchronized, because the client thread writes
 * while a planner thread reads.
 */
public final class AccountState
{
    // Storage names, in the order the panel lists them
    public static final String BANK = "bank", INVENTORY = "inventory", WORN = "worn", SEED_VAULT = "seed vault",
        POTION_STORAGE = "potion storage", RUNE_POUCH = "rune pouch", LOOTING_BAG = "looting bag", SEED_BOX = "seed box",
        TACKLE_BOX = "tackle box", FORESTRY_KIT = "forestry kit", HUNTSMANS_KIT = "huntsman's kit", QUIVER = "dizana's quiver",
        POH_COSTUMES = "POH costume room",
        /** The follower walking after you (a cat counts for cat quests), by its name. */
        FOLLOWER = "follower";
    public static final List<String> STORAGES = List.of(BANK, INVENTORY, WORN, SEED_VAULT, POTION_STORAGE, RUNE_POUCH,
        LOOTING_BAG, SEED_BOX, TACKLE_BOX, FORESTRY_KIT, HUNTSMANS_KIT, QUIVER, POH_COSTUMES, FOLLOWER);

    public int version = 1;
    /** RuneLite's RS profile key: one per account (and per world type). */
    public String profile;
    public String name;
    /** Epoch millis of the last change. */
    public long updatedAt;
    /** Epoch millis of the last full read of levels and quests, 0 = never. */
    public long readAt;

    /** Upper-case skill name ("MAGIC") -> real level. */
    public Map<String, Integer> levels = new LinkedHashMap<>();
    public Map<String, Integer> xp = new LinkedHashMap<>();
    /** Net.runelite.api.Quest names, plus diary tiers done ("ARDOUGNE_EASY_DIARY"), as ItemTree.Account keeps them. */
    public Set<String> questsDone = new TreeSet<>();
    public Set<String> questsStarted = new TreeSet<>();
    public int questPoints;
    public boolean iron;
    /** "main", "iron", "uim" or "gim"; null in files saved before 2026-11-11. */
    public String mode;
    public boolean members = true;

    /** Storage name -> what it held when last seen. A storage never seen is missing. */
    public Map<String, Storage> storages = new LinkedHashMap<>();
    public static final class Storage
    {
        /** Epoch millis when it was last seen. */
        public long seenAt;
        /** Lower-case item name (doses folded, see {@link ItemData#key}) -> count. */
        public Map<String, Integer> items = new LinkedHashMap<>();
        /** Potion storage only: lower-case potion name -> doses. */
        public Map<String, Integer> doses;

        public Storage() { }

        public Storage(long seenAt, Map<String, Integer> items)
        {
            this.seenAt = seenAt;
            this.items = new LinkedHashMap<>(items);
        }
    }

    // ---- writes (client thread)

    /** True when it changed. */
    public synchronized boolean putStorage(String name, Map<String, Integer> items, long now)
    {
        Storage old = storages.get(name);
        boolean same = old != null && old.items.equals(items);
        Storage s = new Storage(now, items);
        if (old != null && same) s.doses = old.doses;
        storages.put(name, s);
        if (!same) updatedAt = now;
        return !same;
    }

    public synchronized boolean putPotions(Map<String, Integer> doses, Map<String, Integer> potions, long now)
    {
        Storage old = storages.get(POTION_STORAGE);
        boolean same = old != null && doses.equals(old.doses);
        Storage s = new Storage(now, potions);
        s.doses = new LinkedHashMap<>(doses);
        storages.put(POTION_STORAGE, s);
        if (!same) updatedAt = now;
        return !same;
    }

    public synchronized boolean putAccount(Map<String, Integer> levels, Map<String, Integer> xp, Set<String> done, Set<String> started,
        int qp, boolean iron, boolean members, long now)
    {
        boolean same = this.levels.equals(levels) && this.questsDone.equals(new TreeSet<>(done)) && this.questsStarted.equals(new TreeSet<>(started))
            && questPoints == qp && this.iron == iron && this.members == members;
        this.levels = new LinkedHashMap<>(levels);
        this.xp = new LinkedHashMap<>(xp);
        this.questsDone = new TreeSet<>(done);
        this.questsStarted = new TreeSet<>(started);
        this.questPoints = qp;
        this.iron = iron;
        this.members = members;
        readAt = now;
        if (!same) updatedAt = now;
        return !same;
    }

    /** The account mode ("uim"). True when it changed. */
    public synchronized boolean putMode(String m)
    {
        if (m == null || m.equals(mode)) return false;
        mode = m;
        return true;
    }

    /** A level-up between full reads. True when it changed. */
    public synchronized boolean putLevel(String skill, int level, int xpNow)
    {
        Integer old = levels.put(skill, level);
        xp.put(skill, xpNow);
        return old == null || old != level;
    }

    // ---- reads (any thread)

    /** A planner account: levels, quests, diaries, iron, members, and everything owned in every storage. */
    public synchronized ItemTree.Account toAccount()
    {
        ItemTree.Account a = new ItemTree.Account();
        a.skills.putAll(levels);
        a.questsDone.addAll(questsDone);
        a.questsStarted.addAll(questsStarted);
        a.iron = iron;
        a.mode = mode;
        a.members = members;
        a.owned.putAll(owned());
        return a;
    }

    /** Lower-case item name -> total over every storage. */
    public synchronized Map<String, Integer> owned()
    {
        Map<String, Integer> m = new HashMap<>();
        for (Storage s : storages.values()) s.items.forEach((k, v) -> m.merge(k, v, Integer::sum));
        return m;
    }

    /** How many of an item (any name form, doses folded) the account holds over every storage. */
    public synchronized int count(String item)
    {
        String k = ItemData.key(item);
        int n = 0;
        for (Storage s : storages.values()) n += s.items.getOrDefault(k, 0);
        return n;
    }

    /** Storage name -> count of the item there, only where it is held. */
    public synchronized Map<String, Integer> where(String item)
    {
        String k = ItemData.key(item);
        Map<String, Integer> m = new LinkedHashMap<>();
        storages.forEach((name, s) -> { Integer n = s.items.get(k); if (n != null && n > 0) m.put(name, n); });
        return m;
    }

    /** A copy of every storage: name -> lower-case item -> count. */
    public synchronized Map<String, Map<String, Integer>> items()
    {
        Map<String, Map<String, Integer>> out = new LinkedHashMap<>();
        storages.forEach((name, st) -> out.put(name, new LinkedHashMap<>(st.items)));
        return out;
    }

    public synchronized boolean seen(String storage)
    {
        return storages.containsKey(storage);
    }

    public synchronized int level(String skill)
    {
        return levels.getOrDefault(skill.toUpperCase(java.util.Locale.ROOT), 1);
    }

}
