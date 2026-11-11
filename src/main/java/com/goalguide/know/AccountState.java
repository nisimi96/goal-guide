package com.goalguide.know;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import com.goalguide.items.ItemData;
import com.goalguide.items.ItemTree;

/**
 * Everything the guide knows about one account, saved per account: levels, quests, diaries, every storage as
 * last seen, and the farming and bird house timers. Know is the only part of the system that reads the client for
 * this; Item Roots, Quest Roots and the planners ask {@link Know} instead of keeping their own copies.
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

    /** Farming level to enter the Farming Guild, where the seed vault is (boosts work too). */
    public static final int SEED_VAULT_FARMING = 45;

    // Timer names
    public static final String HERBS = "herb patches", BIRDS = "bird houses";

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
    /** Timer name -> patch -> ticks to reach it with this account's teleports (PatchTravel), -2 not open, -1 no route. */
    public Map<String, Map<String, Integer>> travel = new LinkedHashMap<>();
    /** Epoch millis of the travel check, 0 = never. */
    public long travelAt;

    // ---- what the account can do "think straight about what the account can do and what it cannot")
    /** House city ("Rimmington"), "none" without a house, null = not read yet. */
    public String house;
    /** What the own house is known to have ("pool", "jewellery box:..."), set by another plugin when one knows it. */
    public List<String> houseHas = new ArrayList<>();
    /** "standard", "ancient", "lunar", "arceuus". */
    public String spellbook;
    /** Farming auto-weed: "not owned", "off", "on". */
    public String autoweed;
    /** Item charges: RuneLite Item Charges' counts ("explorerRing" -> 3) and the bottomless compost bucket. */
    public Map<String, Integer> charges = new LinkedHashMap<>();

    /** Bank slots used and the bank's size, from the bank screen's own counter; 0 = not seen yet. */
    public int bankUsed, bankSize;

    /** Timer name ("herb patches", "bird houses", "tree patches"...) -> state. */
    public Map<String, Timer> timers = new LinkedHashMap<>();

    /** Each stop of a run, as Time Tracking predicts it: timer name -> stop ("Falador") -> state. For the run box. */
    public Map<String, Map<String, Timer>> stops = new LinkedHashMap<>();

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

    public static final class Timer
    {
        public static final String READY = "ready", GROWING = "growing", EMPTY = "empty", UNKNOWN = "unknown", BUILT = "built, no seeds",
            DISEASED = "diseased", DEAD = "dead";
        public String state = UNKNOWN;
        /** Epoch seconds when it is ready, 0 when it already is, -1 unknown. */
        public long readyAt = -1;
        /** Epoch millis of the reading. */
        public long seenAt;
        /** Stops only: epoch millis when this state was first seen (a stop growing since the run began was done in it). */
        public long since;

        public Timer() { }

        public Timer(String state, long readyAt, long seenAt)
        {
            this.state = state;
            this.readyAt = readyAt;
            this.seenAt = seenAt;
        }

        /** The state at {@code nowSec}: a growing timer whose time has passed is ready. */
        public String stateAt(long nowSec)
        {
            return GROWING.equals(state) && readyAt > 0 && readyAt <= nowSec ? READY : state;
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

    public synchronized boolean putTravel(Map<String, Map<String, Integer>> ticks, long now)
    {
        travelAt = now;
        travel = new LinkedHashMap<>(ticks);
        return true;
    }

    public synchronized Map<String, Map<String, Integer>> travel()
    {
        return travel == null ? Map.of() : new LinkedHashMap<>(travel);
    }

    /** What the account can do, read every full account read. True when anything changed. */
    public synchronized boolean putAbilities(String house, List<String> houseHas, String spellbook, String autoweed, Map<String, Integer> charges)
    {
        boolean c = !java.util.Objects.equals(house, this.house) || !java.util.Objects.equals(houseHas, this.houseHas)
            || !java.util.Objects.equals(spellbook, this.spellbook) || !java.util.Objects.equals(autoweed, this.autoweed)
            || !java.util.Objects.equals(charges, this.charges);
        this.house = house;
        this.houseHas = new ArrayList<>(houseHas);
        this.spellbook = spellbook;
        this.autoweed = autoweed;
        this.charges = new LinkedHashMap<>(charges);
        return c;
    }

    /** What the own house holds (portals, nexus, jewellery box...), set by another plugin. True when it changed. */
    public synchronized boolean putHouseHas(List<String> has)
    {
        if (java.util.Objects.equals(has, houseHas)) return false;
        houseHas = new ArrayList<>(has);
        return true;
    }

    /** True when the account has a house. */
    public synchronized boolean hasHouse()
    {
        return house != null && !house.equals("none");
    }

    /** The bank counter ("752" of "820"). True when it changed. */
    public synchronized boolean putBank(int used, int size)
    {
        if (used < 0 || size <= 0 || used > size || (used == bankUsed && size == bankSize)) return false;
        bankUsed = used;
        bankSize = size;
        return true;
    }

    /** Free bank slots, -1 when the bank counter was never seen. */
    public synchronized int bankFree()
    {
        return bankSize <= 0 ? -1 : bankSize - bankUsed;
    }

    public synchronized boolean putTimer(String name, Timer t)
    {
        Timer old = timers.get(name);
        timers.put(name, t);
        boolean same = old != null && old.state.equals(t.state) && old.readyAt == t.readyAt;
        if (!same) updatedAt = t.seenAt;
        return !same;
    }

    /** One run's stops; true when a state changed. A stop keeps {@code since} while its state stays the same. */
    public synchronized boolean putStops(String run, Map<String, Timer> now)
    {
        Map<String, Timer> old = stops.getOrDefault(run, Map.of());
        boolean changed = !old.keySet().equals(now.keySet());
        for (Map.Entry<String, Timer> e : now.entrySet())
        {
            Timer o = old.get(e.getKey()), t = e.getValue();
            if (o != null && o.state.equals(t.state)) t.since = o.since > 0 ? o.since : t.seenAt;
            else { t.since = t.seenAt; changed = true; }
            if (o != null && o.readyAt != t.readyAt) changed = true;
        }
        stops.put(run, new LinkedHashMap<>(now));
        return changed;
    }

    /** A copy of one run's stops (empty when never read). */
    public synchronized Map<String, Timer> stops(String run)
    {
        Map<String, Timer> m = stops == null ? null : stops.get(run);
        return m == null ? Map.of() : new LinkedHashMap<>(m);
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

    public synchronized Timer timer(String name)
    {
        return timers.get(name);
    }

    public synchronized int level(String skill)
    {
        return levels.getOrDefault(skill.toUpperCase(java.util.Locale.ROOT), 1);
    }

    // ---- text for panels

    /** "5 min ago", "3 h ago", "2 days ago". */
    public static String ago(long thenMs, long nowMs)
    {
        long min = Math.max(0, (nowMs - thenMs) / 60_000);
        if (min < 1) return "just now";
        if (min < 60) return min + " min ago";
        long h = min / 60;
        if (h < 48) return h + " h ago";
        return (h / 24) + " days ago";
    }

    /** "ready", "in 34 min", "in 2 h 5 min", "empty", "unknown". */
    public static String when(Timer t, long nowSec)
    {
        if (t == null) return "no data";
        String st = t.stateAt(nowSec);
        if (Timer.READY.equals(st)) return "ready";
        if (!Timer.GROWING.equals(st)) return st;
        long min = Math.max(1, (t.readyAt - nowSec + 59) / 60);
        return min < 60 ? "in " + min + " min" : "in " + (min / 60) + " h" + (min % 60 == 0 ? "" : " " + (min % 60) + " min");
    }

    /** One line per storage: "bank: 412 items, 3 h ago", "seed vault: never seen". */
    public synchronized List<String> storageLines(long nowMs)
    {
        List<String> out = new ArrayList<>();
        for (String name : STORAGES)
        {
            Storage s = storages.get(name);
            if (s == null)
            {
                // The seed vault is in the Farming Guild: 45 Farming to get in (wiki), so below that it is not asked for
                if (BANK.equals(name) || SEED_VAULT.equals(name) && (levels.isEmpty() || level("FARMING") >= SEED_VAULT_FARMING))
                    out.add(name + ": not seen yet, open it once");
                continue;
            }
            int kinds = s.items.size();
            out.add(name + ": " + (kinds == 0 ? "empty" : kinds + (kinds == 1 ? " item" : " items")) + ", " + ago(s.seenAt, nowMs));
        }
        return out;
    }

    /** One line per timer: herbs and bird houses first, then the other patches that have data. */
    public synchronized List<String> timerLines(long nowMs)
    {
        long nowSec = nowMs / 1000;
        List<String> out = new ArrayList<>();
        out.add("Herbs: " + when(timers.get(HERBS), nowSec));
        out.add("Bird houses: " + when(timers.get(BIRDS), nowSec));
        timers.forEach((name, t) ->
        {
            if (HERBS.equals(name) || BIRDS.equals(name)) return;
            String st = t.stateAt(nowSec);
            if (Timer.UNKNOWN.equals(st) || Timer.EMPTY.equals(st)) return;
            out.add(Character.toUpperCase(name.charAt(0)) + name.substring(1) + ": " + when(t, nowSec));
        });
        return out;
    }
}
