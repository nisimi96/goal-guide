package com.goalguide.items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.goalguide.items.ItemData.Drop;
import com.goalguide.items.ItemData.Recipe;
import com.goalguide.items.ItemData.Shop;
import com.goalguide.items.ItemData.Spawn;

/**
 * The item half of the goal tree: from an item down every way to get it, to its roots, for an ironman (no Grand
 * Exchange). Each item lists its ways (owned, ground spawn, gather, shop, recipe, drop, reward); the cheapest open
 * way is picked and its materials traced on, loops cut, the rest kept as plan B. A leaf is always a root:
 * owned, free (spawn or gather), coins (a shop), a kill, a reward, a special currency, or nothing known.
 * Our own rules sit on top of the wiki data: quest gates the data lacks, and which drops an iron can count on.
 */
public final class ItemTree
{
    public enum Kind { OWNED, SPAWN, GATHER, SHOP, RECIPE, DROP, REWARD, CURRENCY }

    /** What a leaf comes down to. */
    public enum Root { OWNED, FREE, COINS, KILL, REWARD, CURRENCY, NONE }

    public static final class Account
    {
        /** Upper-case skill name ("MAGIC") -> level. Missing = 1. */
        public Map<String, Integer> skills = new HashMap<>();
        /** Net.runelite.api.Quest names. */
        public Set<String> questsDone = new HashSet<>();
        /** Quests started but not finished (a diary that only needs a quest started counts these). */
        public Set<String> questsStarted = new HashSet<>();
        /** Ironman: a boost counts only when the account can make it, buy it in a shop or already owns it. */
        public boolean iron;
        /** "main", "iron", "uim" or "gim" (hardcore counts as its base mode); null = read from {@link #iron}. */
        public String mode;
        /** Lower-case item name -> how many the bank and bag hold. */
        public Map<String, Integer> owned = new HashMap<>();
        public boolean members = true;

        /** The account's mode: {@link #mode} when read from the game, else "iron" or "main". */
        public String mode()
        {
            return mode != null ? mode : iron ? "iron" : "main";
        }
        /** Drops rarer than this are not a way (an iron can't plan on a 1/5,000). Default 1 in 1,000: seeds and herbs
         * from a Master Farmer are rare per pickpocket but still how irons get them; the cost grows with the rarity. */
        public double minDropChance = 1 / 1000.0;
        /** Monsters above this combat level are not a way by default (bosses, the Mimic). */
        public int maxMonsterLevel = 150;

        /** A brand-new account: every skill 1 (Hitpoints 10), no quests, nothing in the bank. */
        public static Account fresh()
        {
            Account a = new Account();
            for (net.runelite.api.Skill s : net.runelite.api.Skill.values()) a.skills.put(s.name(), 1);
            a.skills.put("HITPOINTS", 10);
            return a;
        }

        /** The game's combat level formula. */
        int combat()
        {
            double base = 0.25 * (level("DEFENCE") + level("HITPOINTS") + Math.floor(level("PRAYER") / 2.0));
            double melee = 0.325 * (level("ATTACK") + level("STRENGTH"));
            double range = 0.325 * Math.floor(level("RANGED") * 1.5);
            double mage = 0.325 * Math.floor(level("MAGIC") * 1.5);
            return (int) Math.floor(base + Math.max(melee, Math.max(range, mage)));
        }

        int level(String skill)
        {
            if ("COMBAT".equalsIgnoreCase(skill)) return combat();
            return skills.getOrDefault(skill.toUpperCase(Locale.ROOT), 1);
        }
    }

    /** Facilities and methods the wiki data lists with no quest, that need one (checked on the OSRS Wiki). Our layer. */
    public static final Map<String, String> QUEST_GATES = new LinkedHashMap<>();
    static
    {
        QUEST_GATES.put("Cosmic Altar", "LOST_CITY");
        QUEST_GATES.put("Astral Altar", "LUNAR_DIPLOMACY");
        QUEST_GATES.put("Death Altar", "MOURNINGS_END_PART_II");
        QUEST_GATES.put("Blood Altar", "SINS_OF_THE_FATHER");
        QUEST_GATES.put("Wrath Altar", "DRAGON_SLAYER_II");
        QUEST_GATES.put("Plank Make", "DREAM_MENTOR");
        QUEST_GATES.put("Rune Essence", "RUNE_MYSTERIES");
        // Places: a spawn, shop or source there needs the quest that opens the place
        QUEST_GATES.put("Prifddinas", "SONG_OF_THE_ELVES");
        QUEST_GATES.put("Zanaris", "LOST_CITY");
        QUEST_GATES.put("Darkmeyer", "SINS_OF_THE_FATHER");
    }

    public static final class Way
    {
        public final Kind kind;
        public final String label;
        /** Upper-case skill -> level the way needs. */
        public final Map<String, Integer> skills = new LinkedHashMap<>();
        public final List<String> quests = new ArrayList<>();
        public final String tools;
        /** {item, qty per one output}. */
        public final List<Object[]> materials = new ArrayList<>();
        /** Filled for the picked recipe only. */
        public final List<Node> children = new ArrayList<>();
        public double cost;
        /** Why it can't be used now ("Magic 49", "quest Lost City"), empty when open. */
        public final List<String> missing = new ArrayList<>();
        /** The way in plain words for the panel's step list: "Pickpocket the Master Farmer". */
        public String say;
        /** Where a recipe is made ("Anvil", "Furnace"); null for other ways or one made anywhere. */
        public String facility;

        Way(Kind kind, String label, String tools)
        {
            this.kind = kind;
            this.label = label;
            this.tools = tools;
        }

        public boolean open() { return missing.isEmpty(); }

        public Root root()
        {
            switch (kind)
            {
                case OWNED: return Root.OWNED;
                case SPAWN: case GATHER: return Root.FREE;
                case SHOP: return Root.COINS;
                case DROP: return Root.KILL;
                case REWARD: return Root.REWARD;
                case CURRENCY: return Root.CURRENCY;
                default: return null;
            }
        }

        @Override public String toString()
        {
            return label + (missing.isEmpty() ? "" : " [needs " + String.join(", ", missing) + "]");
        }
    }

    public static final class Node
    {
        public final String item;
        public final List<Way> ways = new ArrayList<>();
        /** The picked way, null when nothing is known (a root of kind NONE). */
        public Way best;
        /** True when the item was cut because it is already being traced higher up (a loop). */
        public boolean loop;

        Node(String item) { this.item = item; }

        public double cost() { return best == null ? NO_WAY : best.cost; }
    }

    /** Costs are rough effort points: only their order matters. A missing level is cheap: an iron trains it and then makes the thing itself, which beats farming a rare drop. A missing quest costs far more,
     * because only the player does quests. */
    static final double NO_WAY = 1e6, MEMBERS = 1e5, QUEST = 5000, SKILL = 20, PER_LEVEL = 1;
    private static final int MAX_DEPTH = 10;

    private final ItemData data;
    private final Account account;
    private final Map<String, Node> memo = new HashMap<>();

    public ItemTree(ItemData data, Account account)
    {
        this.data = data;
        this.account = account;
    }

    public static Node trace(ItemData data, Account account, String item)
    {
        return new ItemTree(data, account).node(item, new HashSet<>(), 0);
    }

    Node node(String item, Set<String> stack, int depth)
    {
        String k = ItemData.key(item);
        Node done = memo.get(k);
        if (done != null) return done;
        Node n = new Node(data.display(item));
        if (stack.contains(k) || depth > MAX_DEPTH)
        {
            n.loop = true;
            return n;
        }
        stack.add(k);
        if (k.equals("coins"))
        {
            Way w = new Way(Kind.SHOP, "coins", null);
            w.say = "Coins";
            w.cost = 1 / 50.0;
            n.ways.add(w);
            n.best = w;
            stack.remove(k);
            memo.put(k, n);
            return n;
        }
        int have = account.owned.getOrDefault(k, 0);
        if (have > 0)
        {
            Way w = new Way(Kind.OWNED, "you have " + have, null);
            w.say = "Already in your bank (" + have + ")";
            w.cost = 0;
            n.ways.add(w);
        }
        for (Spawn s : top(data.spawns.get(k), 2, Comparator.comparing(s -> s.members && !account.members)))
        {
            String where = s.location.replaceAll("\\{\\{.*?}}", "").trim();
            Way w = new Way(Kind.SPAWN, "pick up at " + where, null);
            w.say = "Pick it up off the ground: " + where;
            gates(w, where);
            if (s.members && !account.members) w.missing.add("members");
            // A lone spawn is slow; many spawns close together (snape grass on Hobgoblin Peninsula) are a real way
            w.cost = Math.max(40, 400.0 / data.spawns.get(k).size());
            n.ways.add(w);
        }
        List<Drop> drops = data.drops.getOrDefault(k, List.of());
        List<Drop> gathers = new ArrayList<>(), kills = new ArrayList<>();
        for (Drop d : drops)
        {
            if (d.chance() < account.minDropChance) continue;
            if ("combat".equals(d.type) && monsterLevel(d) > account.maxMonsterLevel) continue;
            (d.gather() ? gathers : kills).add(d);
        }
        for (Drop d : top(gathers, 3, Comparator.comparingDouble(Drop::chance).reversed().thenComparingInt(Drop::firstLevel)))
        {
            String skill = cap(d.type);
            String seed = "farming".equals(d.type) ? seedFor(d.source) : null;
            Way w = new Way(seed != null ? Kind.RECIPE : Kind.GATHER, skill.toLowerCase(Locale.ROOT) + " " + d.sourceName(), null);
            // A crop needs its seed: about 6 crops per seed (our estimate; herbs and allotments vary)
            if (seed != null) w.materials.add(new Object[]{seed, 1 / 6.0});
            w.say = gatherSay(d);
            if (d.firstLevel() > 0) need(w, skill, d.firstLevel());
            for (Map.Entry<String, String> g : QUEST_GATES.entrySet()) if (contains(d.source, g.getKey())) gate(w, g.getValue());
            w.cost = 10 + 0.5 / Math.max(d.chance(), 0.001);
            n.ways.add(w);
        }
        for (Drop d : top(kills, 3, Comparator.comparingDouble(Drop::chance).reversed()))
        {
            boolean reward = !"combat".equals(d.type);
            Way w = new Way(reward ? Kind.REWARD : Kind.DROP, (reward ? "reward: " : "kill ") + d.sourceName() + " (" + d.rarity + ")", null);
            if (reward && !d.type.isEmpty() && !"reward".equals(d.type) && d.firstLevel() > 0) need(w, cap(d.type), d.firstLevel());
            w.say = (reward ? "Get it from " : "Kill ") + d.sourceName() + ", " + odds(d.chance());
            gates(w, d.source);
            if (!reward)
            {
                // Our rough rule: you want a combat level near 80% of the monster's to farm it
                int want = Math.max(3, (int) Math.round(monsterLevel(d) * 0.8));
                w.skills.put("COMBAT", want);
                if (account.combat() < want) w.missing.add("Combat " + want);
            }
            // A reward (a chest, a crate, a minigame shop) is rarely on tap, so it only wins when nothing repeatable does
            // And an iron makes what it can: a kill only wins when no skill route exists
            w.cost = (reward ? 400 : 300 + monsterLevel(d)) + 5 / d.chance();
            n.ways.add(w);
        }
        List<Shop> stocked = new ArrayList<>();
        for (Shop s : data.shops.getOrDefault(k, List.of())) if (!"0".equals(s.stock)) stocked.add(s);
        for (Shop s : top(stocked, 3, Comparator.comparingInt(s -> s.price < 0 ? Integer.MAX_VALUE : s.price)))
        {
            // A minigame's reward shop (Nightmare Zone, Last Man Standing) takes its own points, whatever the data says
            boolean coins = "Coins".equalsIgnoreCase(s.currency) && !s.shop.toLowerCase(Locale.ROOT).contains("reward");
            // A shop with its own currency and no price is an in-activity supply shop (Theatre of Blood, Fractured
            // Archive): its points only exist inside that activity, so it is no way to get the item
            if (!coins && s.price < 0) continue;
            String price = s.price < 0 ? "" : " for " + String.format("%,d", s.price) + " " + (coins ? "gp" : s.currency);
            Way w = new Way(coins ? Kind.SHOP : Kind.CURRENCY, "buy at " + trimDot(s.shop) + price, null);
            w.say = "Buy it at " + trimDot(s.shop) + price;
            gates(w, s.location + " " + s.shop);
            if (s.members && !account.members) w.missing.add("members");
            // Buying is quick but shops hold little stock; an unknown price counts as 500 gp
            w.cost = coins ? 12 + (s.price < 0 ? 500 : s.price) / 50.0 : 150;
            n.ways.add(w);
        }
        List<Recipe> rs = data.recipes.getOrDefault(k, List.of());
        for (Recipe r : rs.size() > 6 ? rs.subList(0, 6) : rs)
        {
            String how = r.subtext != null ? r.subtext : r.facility != null ? "make at " + r.facility : "make";
            Way w = new Way(Kind.RECIPE, how, r.tools);
            w.say = makeSay(r);
            w.facility = r.facility;
            for (Map.Entry<String, Integer> e : r.skills.entrySet()) need(w, e.getKey(), e.getValue());
            for (Map.Entry<String, String> g : QUEST_GATES.entrySet())
                if (contains(r.facility, g.getKey()) || contains(r.subtext, g.getKey())) gate(w, g.getValue());
            if (r.members && !account.members) w.missing.add("members");
            for (String[] m : r.materials) w.materials.add(new Object[]{m[0], Integer.parseInt(m[1]) / (double) Math.max(1, r.outputQty)});
            w.cost = 15;
            n.ways.add(w);
        }
        // Cost the recipes from their materials, then pick
        for (Way w : n.ways)
        {
            if (w.kind != Kind.RECIPE) continue;
            for (Object[] m : w.materials)
            {
                Node c = node((String) m[0], stack, depth + 1);
                w.children.add(c);
                w.cost += (c.loop ? NO_WAY : c.cost()) * (Double) m[1];
            }
        }
        for (Way w : n.ways) w.cost += penalty(w);
        for (Way w : n.ways) if (n.best == null || w.cost < n.best.cost) n.best = w;
        if (n.best != null && n.best.cost >= NO_WAY) n.best = null;
        // Keep children only under the picked recipe; the others stay as one-line plan Bs
        for (Way w : n.ways) if (w != n.best) w.children.clear();
        stack.remove(k);
        memo.put(k, n);
        return n;
    }

    /** "1 in 10", "every time". */
    static String odds(double p)
    {
        if (p >= 0.99) return "every time";
        return "about 1 in " + String.format("%,d", Math.round(1 / p));
    }

    static String gatherSay(Drop d)
    {
        String src = d.sourceName();
        String low = src.toLowerCase(Locale.ROOT);
        switch (d.type)
        {
            case "mining": return "Mine " + src;
            case "woodcutting": return "Chop " + src;
            case "fishing": return "Fish at " + src;
            case "hunter": return "Catch " + src;
            case "farming": return "Grow it in a farming patch (" + src.replaceAll("\\s*\\(.*\\)", "") + ")";
            case "thieving": return (low.contains("stall") || low.contains("chest") || low.contains("counter") ? "Steal from " : "Pickpocket ") + src
                + (d.chance() < 0.5 ? ", " + odds(d.chance()) : "");
            default: return cap(d.type) + ": " + src;
        }
    }

    static String makeSay(Recipe r)
    {
        String main = r.skills.isEmpty() ? "" : r.skills.keySet().iterator().next();
        boolean grimy = false;
        for (String[] m : r.materials) grimy |= m[0].startsWith("Grimy ");
        String verb = grimy ? "Clean the grimy herb"
            : "Herblore".equals(main) ? "Mix it"
            : "Smithing".equals(main) ? "Smelt it"
            : "Cooking".equals(main) ? "Cook it"
            : "Crafting".equals(main) ? "Craft it"
            : "Fletching".equals(main) ? "Fletch it"
            : "Magic".equals(main) ? "Cast the spell"
            : "Runecraft".equals(main) ? "Craft it at the altar"
            : "Make it";
        if (r.subtext != null && !grimy && !"Magic".equals(main)) verb = verb.equals("Make it") ? "Make it (" + r.subtext + ")" : verb + " (" + r.subtext + ")";
        else if (r.facility != null && !"Runecraft".equals(main)) verb += " at the " + r.facility.toLowerCase(Locale.ROOT);
        if (r.tools != null) verb += " (tools: " + r.tools + ")";
        return verb;
    }

    /** One step of the plan, in the order you would do them (things first, then what is made from them). */
    public static final class Step
    {
        public final String item;
        /** How many per one of the top item. */
        public final double qty;
        public final Way way;
        /** "Herblore 38" -> true when the account has it. Quests too ("quest Lost City"). */
        public final Map<String, Boolean> needs = new LinkedHashMap<>();

        Step(String item, double qty, Way way)
        {
            this.item = item;
            this.qty = qty;
            this.way = way;
        }
    }

    /** The picked plan as steps in doing order, each item once (its amounts added up). */
    public List<Step> steps(Node top)
    {
        Map<Node, Step> out = new LinkedHashMap<>();
        steps(top, 1, out, new HashSet<>());
        return new ArrayList<>(out.values());
    }

    public static List<Step> steps(ItemData data, Account account, Node top)
    {
        return new ItemTree(data, account).steps(top);
    }

    private void steps(Node n, double qty, Map<Node, Step> out, Set<Node> path)
    {
        if (n.best == null || n.loop || !path.add(n)) return;
        for (int i = 0; i < n.best.children.size(); i++) steps(n.best.children.get(i), qty * (Double) n.best.materials.get(i)[1], out, path);
        path.remove(n);
        Step prev = out.remove(n);
        Step st = new Step(n.item, qty + (prev == null ? 0 : prev.qty), n.best);
        for (Map.Entry<String, Integer> e : n.best.skills.entrySet())
            st.needs.put(cap(e.getKey()) + " " + e.getValue(), account.level(e.getKey()) >= e.getValue());
        for (String q : n.best.quests) st.needs.put("quest " + pretty(q), account.questsDone.contains(q));
        if (n.best.missing.contains("members")) st.needs.put("members", false);
        out.put(n, st);
    }

    /** The wiki leaves some bosses' level blank: count those as 100. */
    static int monsterLevel(Drop d)
    {
        return d.firstLevel() == 0 ? 100 : d.firstLevel();
    }

    /** "Ranarr weed (plant)" -> "Ranarr seed", "Mahogany tree (Farming)" -> "Mahogany sapling", null when none is known. */
    String seedFor(String source)
    {
        String base = source.replaceAll("\\s*\\(.*\\)", "").replaceAll("(?i)\\s+(plant|patch)$", "").trim();
        String first = base.split(" ")[0];
        String tree = base.replaceAll("(?i)\\s+tree$", "");
        for (String c : new String[]{base + " seed", first + " seed", tree + " sapling", tree + " seed", base + " seeds"})
            if (data.known(c)) return data.display(c);
        return null;
    }

    private void gates(Way w, String place)
    {
        for (Map.Entry<String, String> g : QUEST_GATES.entrySet())
            if (contains(place, g.getKey()) && !w.quests.contains(g.getValue())) gate(w, g.getValue());
    }

    private double penalty(Way w)
    {
        double p = 0;
        for (String m : w.missing) if (m.equals("members")) p += MEMBERS;
        for (String q : w.quests) if (!account.questsDone.contains(q)) p += QUEST;
        for (Map.Entry<String, Integer> e : w.skills.entrySet())
        {
            int gap = e.getValue() - account.level(e.getKey());
            if (gap > 0) p += SKILL + PER_LEVEL * gap;
        }
        return p;
    }

    private void need(Way w, String skill, int level)
    {
        String s = skill.toUpperCase(Locale.ROOT);
        w.skills.merge(s, level, Math::max);
        if (account.level(s) < level) w.missing.add(cap(skill) + " " + level);
    }

    private void gate(Way w, String quest)
    {
        w.quests.add(quest);
        if (!account.questsDone.contains(quest)) w.missing.add("quest " + pretty(quest));
    }

    /** Every leaf of the picked plan, with how many per one of the top item: the roots. */
    public static Map<String, Double> roots(Node n)
    {
        Map<String, Double> out = new LinkedHashMap<>();
        roots(n, 1, out);
        return out;
    }

    private static void roots(Node n, double qty, Map<String, Double> out)
    {
        if (n.loop) { out.merge("loop: " + n.item, qty, Double::sum); return; }
        if (n.best == null) { out.merge("no known way: " + n.item, qty, Double::sum); return; }
        if (n.best.kind != Kind.RECIPE) { out.merge(n.best.root() + ": " + n.item + " (" + n.best.label + ")", qty, Double::sum); return; }
        for (int i = 0; i < n.best.children.size(); i++) roots(n.best.children.get(i), qty * (Double) n.best.materials.get(i)[1], out);
    }

    /** What only the player can do on the picked plan: quests and skill levels it is missing. */
    public static Set<String> playerNeeds(Node n)
    {
        Set<String> out = new java.util.LinkedHashSet<>();
        playerNeeds(n, out, new HashSet<>());
        return out;
    }

    private static void playerNeeds(Node n, Set<String> out, Set<Node> seen)
    {
        if (n.best == null || !seen.add(n)) return;
        out.addAll(n.best.missing);
        for (Node c : n.best.children) playerNeeds(c, out, seen);
    }

    /** The picked plan as indented lines, plan Bs as "or" lines under each item. */
    public static List<String> lines(Node n, int planBs)
    {
        List<String> out = new ArrayList<>();
        lines(n, 1, 0, planBs, out, new HashSet<>());
        return out;
    }

    private static void lines(Node n, double qty, int depth, int planBs, List<String> out, Set<Node> path)
    {
        String pad = "  ".repeat(depth);
        String q = qty == 1 ? "" : (qty == Math.rint(qty) ? String.valueOf((long) qty) : String.format("%.2f", qty)) + " x ";
        if (n.loop) { out.add(pad + q + n.item + ": (loop, see above)"); return; }
        if (n.best == null) { out.add(pad + q + n.item + ": no known way"); return; }
        out.add(pad + q + n.item + ": " + n.best + (n.best.tools == null ? "" : ", tools: " + n.best.tools));
        if (!path.add(n)) return;
        int shown = 0;
        for (Way w : n.ways)
        {
            if (w == n.best || shown >= planBs) continue;
            out.add(pad + "    or " + w);
            shown++;
        }
        for (int i = 0; i < n.best.children.size(); i++)
            lines(n.best.children.get(i), qty * (Double) n.best.materials.get(i)[1], depth + 1, planBs, out, path);
        path.remove(n);
    }

    private static <T> List<T> top(List<T> list, int max, Comparator<T> by)
    {
        if (list == null) return List.of();
        List<T> l = new ArrayList<>(list);
        l.sort(by);
        return l.size() > max ? l.subList(0, max) : l;
    }

    private static boolean contains(String s, String part)
    {
        return s != null && s.toLowerCase(Locale.ROOT).contains(part.toLowerCase(Locale.ROOT));
    }

    private static String trimDot(String s)
    {
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }

    static String cap(String s)
    {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
    }

    static String pretty(String quest)
    {
        StringBuilder b = new StringBuilder();
        for (String w : quest.toLowerCase(Locale.ROOT).split("_+"))
        {
            if (w.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            b.append(w.matches("i+") ? w.toUpperCase(Locale.ROOT) : b.length() > 0 && (w.equals("of") || w.equals("the") || w.equals("in")) ? w : cap(w));
        }
        return b.toString();
    }
}
