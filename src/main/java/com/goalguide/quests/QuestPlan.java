package com.goalguide.quests;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.goalguide.items.ItemData;
import com.goalguide.items.ItemTree;

/**
 * The quest half of the goal tree. A quest is something only the player does, so the plan gets everything else
 * ready for it: earlier quests in the chain first (each with its own needs), the skill levels to train, the items to
 * gather or make. When a quest's needs are all met it is READY, and the player is told it is their turn.
 *
 * <p>A goal can be a diary tier too (QuestData keys ending _DIARY). Several goals plan as one route: a quest two goals
 * share comes once, and each skill is trained once to the highest level any of them asks. A boostable level that an
 * everyday boost covers (Boosts) is a boost, not training. Merged goals and boosts are ideas from Codex Navigator.
 */
public final class QuestPlan
{
    private QuestPlan() { }

    /** One quest of the chain, in doing order (deepest prerequisite first, the goal quest last). */
    public static final class Step
    {
        public final String quest;
        /** Earlier quests not finished yet. */
        public final List<String> waitingOn = new ArrayList<>();
        /** "Agility 70" -> current level. */
        public final Map<String, Integer> train = new LinkedHashMap<>();
        /** "Agility 40" -> the boost that covers it from the current level ("Agility potion"). Doesn't block ready. */
        public final Map<String, Boosts.Boost> boosts = new LinkedHashMap<>();
        /** Things the client can't check, shown as a reminder only ("Knight Waves Training Grounds"). */
        public final List<String> also = new ArrayList<>();
        /** Earlier quests that only need to be started, not finished (a subset of waitingOn). */
        public final List<String> startOnly = new ArrayList<>();
        /** Items still missing: name -> {need, have}. */
        public final Map<String, int[]> missingItems = new LinkedHashMap<>();
        /**
         * items of a quest already started that are not held: likely used up in the quest, so no
         * Get step brings them back (Quest Helper shows what is left). A later quest that needs them gets its own.
         */
        public final List<String> usedItems = new ArrayList<>();
        /** Items already in the bank / bag. */
        public final List<String> haveItems = new ArrayList<>();
        /** Combat level / quest points short: "Combat 40". */
        public final List<String> other = new ArrayList<>();
        /** "QP and combat as steps": the combat level / quest points it needs, 0 when met now. */
        public int combatNeed, qpNeed;

        Step(String quest) { this.quest = quest; }

        public boolean ready() { return waitingOn.isEmpty() && train.isEmpty() && missingItems.isEmpty() && other.isEmpty(); }
    }

    /** An item row with no one item id (Quest Helper's "Combat equipment", "Runes for Fire Bolt"). */
    public static final String NO_ITEM = "-1";

    /** Tools and kinds of item Quest Helper names as a group: the simplest one counts. */
    static final Map<String, String> COLLECTIONS = new LinkedHashMap<>();
    static
    {
        COLLECTIONS.put("HAMMER", "Hammer");
        COLLECTIONS.put("COINS", "Coins");
        COLLECTIONS.put("PICKAXES", "Bronze pickaxe");
        COLLECTIONS.put("AXES", "Bronze axe");
        COLLECTIONS.put("CHISEL", "Chisel");
        COLLECTIONS.put("LIGHT_SOURCES", "Candle lantern");
        COLLECTIONS.put("SAW", "Saw");
        COLLECTIONS.put("NAILS", "Steel nails");
        COLLECTIONS.put("GHOSTSPEAK", "Ghostspeak amulet");
        COLLECTIONS.put("PRAYER_POTIONS", "Prayer potion(4)");
        COLLECTIONS.put("METAL_ARROWS", "Bronze arrow");
        COLLECTIONS.put("BOWS", "Shortbow");
        COLLECTIONS.put("CROSSBOWS", "Crossbow");
        COLLECTIONS.put("CLIMBING_BOOTS", "Climbing boots");
        COLLECTIONS.put("WATERING_CANS", "Watering can(8)");
        COLLECTIONS.put("ANTIPOISONS", "Antipoison(4)");
        COLLECTIONS.put("RESTORE_POTIONS", "Restore potion(4)");
        // Any cat or kitten, in the bag or following you (ItemData.sameKind)
        COLLECTIONS.put("CATS", "Cat");
        COLLECTIONS.put("HUNTING_CATS", "Hunting cat");
    }

    /** The item to look for: a group's simplest member, else the name the game uses for the id, else the label. */
    /**
     * How many of a quest item the account holds in every storage. The text may be a choice or another spelling
     * ("Lit torch or candle", "Normal/Super/Ultra compost", "Death runes"): every item it can mean counts.
     */
    public static int owned(ItemTree.Account a, ItemData items, String name)
    {
        int have = a.owned.getOrDefault(ItemData.key(name), 0);
        if (have == 0 && (ItemData.key(name).equals("cat") || ItemData.key(name).equals("hunting cat")))
            for (Map.Entry<String, Integer> e : a.owned.entrySet()) if (ItemData.sameKind(e.getKey(), ItemData.key(name))) have += e.getValue();
        if (have > 0 || items == null) return have;
        int sum = 0;
        for (String m : items.matches(ItemData.questText(name))) sum += a.owned.getOrDefault(ItemData.key(m), 0);
        return sum;
    }

    public static String itemName(QuestData.Need n, ItemData items)
    {
        if (n.note.startsWith("ItemCollections."))
            return COLLECTIONS.getOrDefault(n.note.substring("ItemCollections.".length()), n.what);
        if (items != null)
        {
            try
            {
                int id = net.runelite.api.gameval.ItemID.class.getField(n.note).getInt(null);
                String name = items.byId.get(id);
                if (name != null) return name;
            }
            catch (ReflectiveOperationException | RuntimeException ignored)
            {
            }
        }
        return n.what;
    }

    /**
     * Every quest of the chain the goal needs, deepest first, done quests left out.
     * @param account skills (upper-case names), quests done, owned items (lower-case names)
     * @param questPoints the account's quest points (or a large number to skip the check)
     */
    public static List<Step> plan(QuestData data, ItemData items, ItemTree.Account account, int questPoints, String goal)
    {
        return plan(data, items, account, questPoints, List.of(goal));
    }

    /** Several goals as one route, in doing order: a step two goals share is in it once. */
    public static List<Step> plan(QuestData data, ItemData items, ItemTree.Account account, int questPoints, List<String> goals)
    {
        List<Step> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String g : goals) walk(data, items, account, questPoints, g, out, seen);
        return out;
    }

    /** A route added up: each skill once at the highest level asked, every boost, how many quests and items. */
    public static final class Summary
    {
        /** "Agility" -> level to train to. */
        public final Map<String, Integer> train = new LinkedHashMap<>();
        /** "Agility" -> {level, boost}; dropped when the same skill must be trained higher anyway. */
        public final Map<String, Object[]> boosts = new LinkedHashMap<>();
        public int quests, diaries, items, ready;
        /** The first step that is the player's turn now, or null. */
        public Step next;
    }

    public static Summary sum(List<Step> chain)
    {
        Summary m = new Summary();
        for (Step s : chain)
        {
            if (QuestData.isDiary(s.quest)) m.diaries++;
            else m.quests++;
            if (s.ready()) { m.ready++; if (m.next == null) m.next = s; }
            s.train.forEach((k, v) -> m.train.merge(k.substring(0, k.lastIndexOf(' ')), Integer.parseInt(k.substring(k.lastIndexOf(' ') + 1)), Math::max));
            s.boosts.forEach((k, b) ->
            {
                String sk = k.substring(0, k.lastIndexOf(' '));
                int lvl = Integer.parseInt(k.substring(k.lastIndexOf(' ') + 1));
                Object[] old = m.boosts.get(sk);
                if (old == null || (int) old[0] < lvl) m.boosts.put(sk, new Object[]{lvl, b});
            });
            m.items += s.missingItems.size();
        }
        m.boosts.keySet().removeIf(sk -> m.train.getOrDefault(sk, 0) >= (int) m.boosts.get(sk)[0]);
        return m;
    }

    private static void walk(QuestData data, ItemData items, ItemTree.Account a, int qp, String quest, List<Step> out, Set<String> seen)
    {
        if (!seen.add(quest) || a.questsDone.contains(quest)) return;
        Step st = new Step(quest);
        for (QuestData.Need n : data.needs(quest))
        {
            switch (n.kind)
            {
                case "quest":
                {
                    boolean startIsEnough = "IN_PROGRESS".equals(n.note);
                    if (a.questsDone.contains(n.what) || startIsEnough && a.questsStarted.contains(n.what)) break;
                    walk(data, items, a, qp, n.what, out, seen);
                    st.waitingOn.add(n.what);
                    if (startIsEnough) st.startOnly.add(n.what);
                    break;
                }
                case "skill":
                {
                    int have = a.skills.getOrDefault(n.what, 1);
                    if (have >= n.amount) break;
                    String key = cap(n.what) + " " + n.amount;
                    Boosts.Boost b = "boostable".equals(n.note) ? Boosts.toReach(n.what, have, n.amount, a) : null;
                    if (b != null && !st.train.containsKey(key)) st.boosts.put(key, b);
                    else { st.boosts.remove(key); st.train.merge(key, have, Math::min); }
                    break;
                }
                case "also":
                    st.also.add(n.what);
                    break;
                case "combat":
                    if (combat(a) < n.amount) { st.other.add("Combat " + n.amount); st.combatNeed = Math.max(st.combatNeed, n.amount); }
                    break;
                case "qp":
                    if (qp < n.amount) { st.other.add(n.amount + " quest points (you have " + qp + ")"); st.qpNeed = Math.max(st.qpNeed, n.amount); }
                    break;
                case "item":
                {
                    // No one item ("Combat equipment"): a reminder, never a Get step
                    if (NO_ITEM.equals(n.note)) { st.also.add(n.what); break; }
                    String name = itemName(n, items);
                    int need = Math.max(1, n.amount);
                    int have = owned(a, items, name);
                    if (have >= need) st.haveItems.add(name);
                    else if (a.questsStarted.contains(st.quest)) st.usedItems.add(name);
                    else st.missingItems.merge(name, new int[]{need, have}, (x, y) -> new int[]{Math.max(x[0], y[0]), x[1]});
                    break;
                }
                default:
                    break;
            }
        }
        out.add(st);
    }

    /** One line for a panel: "ready" or what it still waits on. */
    public static String status(QuestData data, ItemData items, ItemTree.Account a, int qp, String quest)
    {
        if (a.questsDone.contains(quest)) return "done";
        List<Step> chain = plan(data, items, a, qp, quest);
        if (chain.isEmpty()) return "done";
        Step last = chain.get(chain.size() - 1);
        Summary m = sum(chain);
        if (chain.size() == 1 && last.ready()) return m.boosts.isEmpty() ? "ready" : "ready with " + boostText(m);
        int earlier = chain.size() - 1;
        List<String> parts = new ArrayList<>();
        if (earlier > 0) parts.add(earlier + " earlier quest" + (earlier > 1 ? "s" : "") + " first");
        m.train.forEach((k, v) -> parts.add(k + " " + v));
        if (!m.boosts.isEmpty()) parts.add("boost " + boostText(m));
        if (m.items > 0) parts.add(m.items + " item" + (m.items > 1 ? "s" : "") + " to get");
        return parts.isEmpty() ? "ready" : "needs " + String.join(", ", parts);
    }

    /** "Agility 40 (Agility potion), Fishing 50 (Admiral pie)". */
    public static String boostText(Summary m)
    {
        List<String> out = new ArrayList<>();
        m.boosts.forEach((sk, v) -> out.add(sk + " " + v[0] + " (" + ((Boosts.Boost) v[1]).item.toLowerCase(Locale.ROOT) + ")"));
        return String.join(", ", out);
    }

    public static int combat(ItemTree.Account a)
    {
        java.util.function.ToIntFunction<String> l = s -> a.skills.getOrDefault(s, s.equals("HITPOINTS") ? 10 : 1);
        double base = 0.25 * (l.applyAsInt("DEFENCE") + l.applyAsInt("HITPOINTS") + Math.floor(l.applyAsInt("PRAYER") / 2.0));
        double melee = 0.325 * (l.applyAsInt("ATTACK") + l.applyAsInt("STRENGTH"));
        double range = 0.325 * Math.floor(l.applyAsInt("RANGED") * 1.5);
        double mage = 0.325 * Math.floor(l.applyAsInt("MAGIC") * 1.5);
        return (int) Math.floor(base + Math.max(melee, Math.max(range, mage)));
    }

    public static String pretty(String quest)
    {
        StringBuilder b = new StringBuilder();
        for (String w : quest.toLowerCase(Locale.ROOT).split("_+"))
        {
            if (w.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            boolean small = b.length() > 0 && (w.equals("of") || w.equals("the") || w.equals("in") || w.equals("to") || w.equals("and"));
            b.append(w.matches("i+") ? w.toUpperCase(Locale.ROOT) : small ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1));
        }
        return b.toString();
    }

    static String cap(String s)
    {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
    }
}
