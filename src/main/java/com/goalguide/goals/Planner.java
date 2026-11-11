package com.goalguide.goals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.runelite.api.Experience;
import com.goalguide.facts.Method;
import com.goalguide.facts.MethodTables;
import com.goalguide.items.ItemData;
import com.goalguide.items.ItemTree;
import com.goalguide.quests.QuestData;
import com.goalguide.quests.QuestLength;
import com.goalguide.quests.QuestPlan;

/**
 * Every goal as one route. Quests (and the quests a stock method needs) come from
 * {@link QuestPlan} as one chain, a step two goals share comes once, each level is trained once (a later, higher need
 * of the same skill continues from where the earlier one stopped), stock counts what every storage already holds,
 * and every step gets hours from the method tables' typical rates.
 *
 * Pure: give it the account, quest points and goals; it reads no client. Hours are -1 where no table covers a step.
 */
public final class Planner
{
    private Planner() { }

    public enum Kind { TRAIN, QUEST, GET, STOCK, DONE }

    public static final class Step
    {
        public final Kind kind;
        /** "Fishing 40 to 65", "Tai Bwo Wannai Trio", "1,000 raw karambwan". */
        public final String what;
        /** How: the method, quest notes, the way to get an item. */
        public final String how;
        /** Hours at typical rates, -1 = not known. */
        public final double hours;
        /** A quest step: the player plays it through (the wiki quick guide shows how). */
        public final boolean you;
        /** The goal this step is for (its label), null for a quest-chain step that may serve several. */
        public String goal;
        /** QUEST: the quest's key (net.runelite.api.Quest name), so the guide can link its wiki quick guide. Null otherwise. */
        public String quest;
        /** QUEST: the goals this quest moves forward (labels); more than one = shared, done first. */
        public List<String> serves = List.of();
        /** What the step uses, for the item list: tools and items, quest items. */
        public final List<Use> uses = new ArrayList<>();
        /** A GET step on a main: the Grand Exchange price of all of it, 0 = not priced. */
        public long ge;
        /** GET / STOCK: the item to get (for its how-to-get-it steps). Null otherwise. */
        public String item;
        /** A group: the items one quest still needs, each its own step. Empty otherwise. */
        public final List<Step> parts = new ArrayList<>();
        /** QUEST: already started (its items not held may be used up: shown, never fetched again). */
        public boolean started;
        /** TRAIN: the skill ("THIEVING") and the levels, for the recommended methods. Null / 0 otherwise. */
        public String skill;
        public int from, to;

        Step(Kind kind, String what, String how, double hours, boolean you)
        {
            this.kind = kind;
            this.what = what;
            this.how = how;
            this.hours = hours;
            this.you = you;
        }

        /** "Train ", "Get ", "Stock ", or nothing for a quest. */
        public String verb()
        {
            switch (kind)
            {
                case TRAIN: return "Train ";
                case GET: return "Get ";
                case STOCK: return "Stock ";
                default: return "";
            }
        }

        @Override public String toString() { return kind + " " + what + (how == null ? "" : " (" + how + ")") + (hours >= 0 ? String.format(" %.1fh", hours) : ""); }
    }

    /** One item a step uses: a tool (1), a quest item (how many), or an item used up (about how many an hour). */
    public static final class Use
    {
        public final String item;
        public final int amount;
        public final boolean perHour;
        /** No one item ("Combat equipment"): shown as a reminder, not checked. */
        public final boolean reminder;

        Use(String item, int amount, boolean perHour)
        {
            this(item, amount, perHour, false);
        }

        Use(String item, int amount, boolean perHour, boolean reminder)
        {
            this.item = item;
            this.amount = amount;
            this.perHour = perHour;
            this.reminder = reminder;
        }

        @Override public String toString() { return item + " x" + amount + (perHour ? "/h" : ""); }
    }

    /** The tools and items a method uses at this level. */
    static void uses(Step s, Method m, int lvl)
    {
        if (m == null) return;
        for (String t : m.tools) s.uses.add(new Use(t, 1, false));
        m.uses.forEach((item, rows) -> s.uses.add(new Use(item, Math.max(1, Method.at(rows, lvl)), true)));
    }

    /** A skill to train in mixed sessions: from where the route leaves it to the highest level any goal needs. */
    public static final class Filler
    {
        public final String skill;
        public final int from, to;
        public final double hours;
        public final String how;
        /** The first later quest that needs more of it ("The Path of Glouphrie needs 56"), or the level goal. */
        public final String byWhen;

        Filler(String skill, int from, int to, Training t, String byWhen)
        {
            this.skill = skill;
            this.from = from;
            this.to = to;
            this.hours = t.hours;
            this.how = t.how;
            this.byWhen = byWhen;
        }

        public String what() { return QuestPlan.pretty(skill) + " " + from + " to " + to; }
    }

    public static final class Route
    {
        public final List<Step> steps = new ArrayList<>();
        /** Sum of the steps with known hours. */
        public double hours;
        /** Steps whose hours are not known. */
        public int unknown;
        /** GET steps on a main: what buying them on the Grand Exchange costs, 0 = none priced. */
        public long ge;
        /** Goals already met. */
        public final List<String> done = new ArrayList<>();
        /** Items the route's quests need (held or still missing) -> the quest: never drop, sell or use them up. */
        public final Map<String, String> keep = new LinkedHashMap<>();
        /**
         * training past what the next quest needs is filler, done in mixed sessions between steps
         * (Thieving 53 to 75 when Desert Treasure I needs 53 and a later quest 72). One per skill, in route order.
         */
        public final List<Filler> filler = new ArrayList<>();

        void add(Step s)
        {
            steps.add(s);
            count(s);
        }

        private void count(Step s)
        {
            if (!s.parts.isEmpty()) { s.parts.forEach(this::count); return; }
            ge += s.ge;
            if (s.kind == Kind.DONE) return;
            if (s.hours >= 0) hours += s.hours;
            else unknown++;
        }
    }

    /** The route for these goals, in pinned order. items may be null (no item names for quest items). */
    public static Route plan(QuestData quests, ItemData items, ItemTree.Account a, int qp, List<Goal> goals)
    {
        Route r = new Route();
        Map<String, Integer> trainedTo = new LinkedHashMap<>();
        // What each stock goal will use, picked first: its method may add a quest and a level to the route
        Map<Goal, Method> stockWay = new LinkedHashMap<>();
        List<String> questGoals = new ArrayList<>();
        for (Goal g : goals)
        {
            if (g.type == Goal.Type.QUEST) questGoals.add(g.target);
            if (g.type == Goal.Type.STOCK && have(a, g.target) < g.amount)
            {
                List<Method> ways = MethodTables.waysToMake(g.target, a);
                Method m = best(ways, a, g.target);
                if (m != null)
                {
                    stockWay.put(g, m);
                    for (String q : m.quests) if (!a.questsDone.contains(q) && !questGoals.contains(q)) questGoals.add(q);
                }
            }
        }

        // Miniquest parts (Barbarian Training fishing...) are the player's turn, not in the quest data
        for (java.util.Iterator<String> it = questGoals.iterator(); it.hasNext(); )
        {
            String q = it.next();
            String part = com.goalguide.know.MiniquestParts.name(q);
            if (part == null) continue;
            it.remove();
            if (!a.questsDone.contains(q)) r.add(new Step(Kind.QUEST, part, a.questsStarted.contains(q) ? "started, finish it" : "", -1, true));
        }
        // 1. the quest chain one list for every goal. A quest is placed once its earlier quests are
        // Done; among those, the one most goals share goes first, then the one whose goal is closest, then goal order,
        // So a goal reached on the way to another is reached early. Each skill is trained only to what that quest needs;
        // The rest up to the highest any goal needs is filler for mixed sessions.
        List<QuestPlan.Step> chain = questGoals.isEmpty() ? List.of() : QuestPlan.plan(quests, items, a, qp, questGoals);
        Map<String, java.util.Set<String>> perGoal = new LinkedHashMap<>();
        for (String g : questGoals)
        {
            java.util.Set<String> c = new java.util.LinkedHashSet<>();
            for (QuestPlan.Step st : QuestPlan.plan(quests, items, a, qp, g)) c.add(st.quest);
            perGoal.put(g, c);
        }
        Map<String, Integer> top = new LinkedHashMap<>();
        for (QuestPlan.Step qs : chain) qs.train.forEach((k, v) -> top.merge(skillOf(k), levelOf(k), Math::max));
        for (Goal g : goals) if (g.type == Goal.Type.LEVEL) top.merge(g.target, g.amount, Math::max);
        List<QuestPlan.Step> ordered = order(chain, perGoal, a);
        // Where the quests first need each skill; a skill that goes higher later is filler from there
        Map<String, Integer> firstNeed = new LinkedHashMap<>();
        for (QuestPlan.Step qs : ordered) for (String k : qs.train.keySet()) firstNeed.putIfAbsent(skillOf(k), levelOf(k));
        java.util.Set<String> fill = new java.util.LinkedHashSet<>();
        firstNeed.forEach((sk, lvl) -> { if (top.getOrDefault(sk, 0) > lvl) fill.add(sk); });
        // Quest points by each step: the account's now, plus each quest the route finishes
        int[] qpBy = {qp};
        java.util.Set<String> inRoute = new java.util.HashSet<>();
        for (QuestPlan.Step qs : ordered) inRoute.add(qs.quest);
        for (QuestPlan.Step qs : ordered)
        {
            // "QP and combat as steps": a combat level the quest needs becomes melee training steps,
            // Quest points it is short of become the quickest quests the account can do now
            if (qs.combatNeed > 0) combat(r, a, trainedTo, qs.combatNeed);
            if (qs.qpNeed > qpBy[0])
                for (String q : forPoints(quests, items, a, qs.qpNeed - qpBy[0], inRoute))
                {
                    Step fq = new Step(Kind.QUEST, QuestPlan.pretty(q), "for quest points: " + QuestPlan.pretty(qs.quest) + " needs " + qs.qpNeed,
                        QuestLength.hours(q), true);
                    fq.quest = q;
                    fq.serves = List.of(QuestPlan.pretty(qs.quest));
                    r.add(fq);
                    inRoute.add(q);
                    qpBy[0] += QuestLength.qp(q);
                }
            qpBy[0] += QuestLength.qp(qs.quest);
            List<String> fromFiller = new ArrayList<>();
            qs.train.forEach((k, v) ->
            {
                String sk = skillOf(k);
                if (fill.contains(sk) && levelOf(k) > firstNeed.get(sk)) fromFiller.add(QuestPlan.pretty(sk) + " " + levelOf(k));
                else train(r, a, trainedTo, sk, levelOf(k));
            });
            List<Step> gets = new ArrayList<>();
            qs.missingItems.forEach((name, nh) ->
            {
                Step g = get(items, a, name, nh[0] - nh[1], qs.quest);
                g.item = name;
                gets.add(g);
            });
            if (gets.size() == 1) r.add(gets.get(0));
            else if (gets.size() > 1) r.add(group(qs.quest, gets));
            List<String> quest = new ArrayList<>(qs.haveItems);
            quest.addAll(qs.missingItems.keySet());
            for (String it : quest)
            {
                String k = clean(it);
                // Coins are spent everywhere and come back: never a kept item
                if (!k.equals("coins")) r.keep.putIfAbsent(k, QuestPlan.pretty(qs.quest));
            }
            List<String> notes = new ArrayList<>();
            qs.boosts.forEach((k, b) -> notes.add("boost " + k + " with " + b.item.toLowerCase(Locale.ROOT)));
            for (String o : qs.other) if (!o.startsWith("Combat ") && !o.contains(" quest points (you have ")) notes.add(o);
            if (!fromFiller.isEmpty()) notes.add("needs " + String.join(", ", fromFiller) + " from the filler by now");
            String how = String.join(", ", notes);
            // The wiki's quest length, so the sitting plan and the route's hours count quests too
            Step st = new Step(Kind.QUEST, QuestPlan.pretty(qs.quest), how, QuestLength.hours(qs.quest), true);
            st.quest = qs.quest;
            st.started = a.questsStarted.contains(qs.quest);
            for (QuestData.Need n : quests.needs(qs.quest))
                if ("item".equals(n.kind))
                {
                    boolean note = QuestPlan.NO_ITEM.equals(n.note);
                    String name = note ? n.what : QuestPlan.itemName(n, items);
                    Use u = new Use(name, Math.max(1, n.amount), false, note);
                    // The same item listed twice (two parts of the quest): the bigger amount
                    st.uses.removeIf(o -> o.item.equalsIgnoreCase(name) && o.amount <= u.amount);
                    boolean bigger = false;
                    for (Use o : st.uses) bigger |= o.item.equalsIgnoreCase(name);
                    if (!bigger) st.uses.add(u);
                }
            List<String> serves = new ArrayList<>();
            perGoal.forEach((g, c) -> { if (c.contains(qs.quest)) serves.add(QuestPlan.pretty(g)); });
            st.serves = serves;
            r.add(st);
        }
        // The filler: each skill from where the quests leave it to the highest any goal needs
        for (String sk : fill)
        {
            int at = Math.max(a.skills.getOrDefault(sk, 1), firstNeed.get(sk)), to = top.get(sk);
            Filler f = new Filler(sk, at, to, hours(a, sk, at, to), byWhen(ordered, goals, sk, at));
            r.filler.add(f);
            if (f.hours >= 0) r.hours += f.hours;
            else r.unknown++;
            trainedTo.merge(sk, to, Math::max);   // A level goal on this skill is reached by the filler
        }
        for (String q : questGoals) if (a.questsDone.contains(q) && goalNamed(goals, q)) r.done.add(QuestPlan.pretty(q));

        // 2. levels and stock, in pinned order
        for (Goal g : goals)
        {
            if (g.type == Goal.Type.LEVEL)
            {
                if (a.skills.getOrDefault(g.target, 1) >= g.amount) r.done.add(g.label());
                else tag(r, g, () -> train(r, a, trainedTo, g.target, g.amount));
            }
            else if (g.type == Goal.Type.STOCK)
            {
                int have = have(a, g.target), need = g.amount - have;
                if (need <= 0) { r.done.add(g.label() + " (have " + String.format("%,d", have) + ")"); continue; }
                Method m = stockWay.get(g);
                String what = String.format("%,d", need) + " " + g.target + (have > 0 ? " (have " + String.format("%,d", have) + " of " + String.format("%,d", g.amount) + ")" : "");
                if (m == null)
                {
                    tag(r, g, () ->
                    {
                        Step st = new Step(Kind.STOCK, what, itemWay(items, a, g.target), -1, false);
                        st.item = g.target;
                        r.add(st);
                    });
                    continue;
                }
                tag(r, g, () ->
                {
                    train(r, a, trainedTo, m.skill, m.level);
                    m.skills.forEach((s, l) -> train(r, a, trainedTo, s, l));
                    int lvl = Math.max(m.level, a.skills.getOrDefault(m.skill, 1));
                    int per = m.givesAt(g.target, lvl);
                    Step st = new Step(Kind.STOCK, what, m.name + (per > 0 ? ", " + String.format("%,d", per) + " an hour" : ""),
                        per > 0 ? need / (double) per : -1, false);
                    st.item = g.target;
                    uses(st, m, lvl);
                    r.add(st);
                });
            }
        }
        return r;
    }

    static String skillOf(String key) { return key.substring(0, key.lastIndexOf(' ')).toUpperCase(Locale.ROOT); }

    static int levelOf(String key) { return Integer.parseInt(key.substring(key.lastIndexOf(' ') + 1)); }

    /**
     * The quests in doing order: a quest comes once every earlier quest it needs finished is placed (started-only
     * ones may wait); among those ready, most goals served first, then fewest hours left in its nearest goal, then
     * the goals' order. Pure.
     */
    static List<QuestPlan.Step> order(List<QuestPlan.Step> chain, Map<String, java.util.Set<String>> perGoal, ItemTree.Account a)
    {
        List<QuestPlan.Step> left = new ArrayList<>(chain), out = new ArrayList<>();
        java.util.Set<String> done = new java.util.HashSet<>(a.questsDone);
        List<java.util.Set<String>> goals = new ArrayList<>(perGoal.values());
        Map<String, Integer> cost = new HashMap<>();
        for (QuestPlan.Step s : chain) cost.put(s.quest, minutes(s, a));
        while (!left.isEmpty())
        {
            QuestPlan.Step best = null;
            int[] bestKey = null;
            for (QuestPlan.Step s : left)
            {
                boolean ready = true;
                for (String w : s.waitingOn) if (!s.startOnly.contains(w) && !done.contains(w)) { ready = false; break; }
                if (!ready) continue;
                int served = 0, closest = Integer.MAX_VALUE, first = goals.size();
                for (int g = 0; g < goals.size(); g++)
                {
                    if (!goals.get(g).contains(s.quest)) continue;
                    served++;
                    // "closest goal by hours": minutes left in the goal's quests and their training
                    int n = 0;
                    for (String q : goals.get(g)) if (!done.contains(q)) n += cost.getOrDefault(q, (int) (QuestLength.UNKNOWN_GUESS * 60));
                    closest = Math.min(closest, n);
                    first = Math.min(first, g);
                }
                int[] key = {-served, closest, first};
                if (bestKey == null || java.util.Arrays.compare(key, bestKey) < 0) { best = s; bestKey = key; }
            }
            if (best == null) best = left.get(0);   // A loop in the data: keep the chain's own order
            left.remove(best);
            out.add(best);
            done.add(best.quest);
        }
        return out;
    }

    /**
     * Rough minutes a quest takes from here: its wiki length (a guess when not known) plus the training to the levels
     * it needs, each counted from the account's level now. Shared training is counted per quest: only for ordering.
     */
    static int minutes(QuestPlan.Step s, ItemTree.Account a)
    {
        double h = QuestLength.hours(s.quest);
        double total = h > 0 ? h : QuestLength.UNKNOWN_GUESS;
        for (String k : s.train.keySet())
        {
            String sk = skillOf(k);
            int at = a.skills.getOrDefault(sk, 1), to = levelOf(k);
            if (to <= at) continue;
            Training t = hours(a, sk, at, to);
            if (t.hours > 0) total += t.hours;
        }
        return (int) Math.round(total * 60);
    }

    /** "The Path of Glouphrie needs 56": the next quest after the first that needs more, else the level goal. */
    private static String byWhen(List<QuestPlan.Step> ordered, List<Goal> goals, String skill, int at)
    {
        for (QuestPlan.Step s : ordered)
            for (String k : s.train.keySet())
                if (skillOf(k).equals(skill) && levelOf(k) > at) return QuestPlan.pretty(s.quest) + " needs " + levelOf(k);
        for (Goal g : goals) if (g.type == Goal.Type.LEVEL && g.target.equals(skill)) return "your goal";
        return null;
    }

    /** Runs {@code add} and tags the steps it added with the goal. */
    private static void tag(Route r, Goal g, Runnable add)
    {
        int from = r.steps.size();
        add.run();
        for (int i = from; i < r.steps.size(); i++) r.steps.get(i).goal = g.label();
    }

    /** The way a stock goal uses: the best per hour the account can reach, preferring one it can do now. */
    static Method best(List<Method> ways, ItemTree.Account a, String item)
    {
        Method open = null, any = null;
        for (Method m : ways)
        {
            if (any == null) any = m;
            if (open == null && m.missing(a).isEmpty()) open = m;
        }
        return open != null ? open : any;
    }

    static int have(ItemTree.Account a, String item)
    {
        return a.owned.getOrDefault(ItemData.key(item), 0);
    }

    private static boolean goalNamed(List<Goal> goals, String quest)
    {
        for (Goal g : goals) if (g.type == Goal.Type.QUEST && g.target.equals(quest)) return true;
        return false;
    }

    /** A train step from where the route has the skill now to {@code to}, once. */
    private static void train(Route r, ItemTree.Account a, Map<String, Integer> trainedTo, String skill, int to)
    {
        int from = Math.max(a.skills.getOrDefault(skill, skill.equals("HITPOINTS") ? 10 : 1), trainedTo.getOrDefault(skill, 0));
        if (from >= to) return;
        trainedTo.put(skill, to);
        Training t = hours(a, skill, from, to);
        Step st = new Step(Kind.TRAIN, QuestPlan.pretty(skill) + " " + from + " to " + to, t.how, t.hours, false);
        st.skill = skill;
        st.from = from;
        st.to = to;
        uses(st, t.first, from);
        r.add(st);
    }

    /**
     * The quickest quests that make up {@code short} quest points: ones the account can start and finish now (no
     * earlier quest, level or item missing), fewest hours per point first. Not ones already on the route.
     */
    static List<String> forPoints(QuestData quests, ItemData items, ItemTree.Account a, int need, java.util.Set<String> skip)
    {
        List<String[]> can = new ArrayList<>();
        for (net.runelite.api.Quest q : net.runelite.api.Quest.values())
        {
            String k = q.name();
            int pts = QuestLength.qp(k);
            if (pts <= 0 || skip.contains(k) || a.questsDone.contains(k)) continue;
            List<QuestPlan.Step> p = QuestPlan.plan(quests, items, a, 10_000, k);
            if (p.size() != 1) continue;
            QuestPlan.Step s = p.get(0);
            if (!s.waitingOn.isEmpty() || !s.train.isEmpty() || !s.missingItems.isEmpty() || !s.other.isEmpty() || s.qpNeed > 0) continue;
            double h = QuestLength.hours(k);
            can.add(new String[]{k, String.valueOf((h > 0 ? h : QuestLength.UNKNOWN_GUESS) / pts)});
        }
        can.sort((x, y) -> Double.compare(Double.parseDouble(x[1]), Double.parseDouble(y[1])));
        List<String> out = new ArrayList<>();
        int got = 0;
        for (String[] c : can)
        {
            if (got >= need) break;
            out.add(c[0]);
            got += QuestLength.qp(c[0]);
        }
        return out;
    }

    /**
     * Training to reach a combat level: Attack, Strength and Defence raised one level at a time, the lowest first,
     * from where the route has them by now, until the level is reached. Each becomes an ordinary training step.
     */
    static void combat(Route r, ItemTree.Account a, Map<String, Integer> trainedTo, int need)
    {
        ItemTree.Account t = new ItemTree.Account();
        t.skills.putAll(a.skills);
        trainedTo.forEach((k, v) -> t.skills.merge(k, v, Math::max));
        String[] melee = {"ATTACK", "STRENGTH", "DEFENCE"};
        Map<String, Integer> to = new LinkedHashMap<>();
        for (int guard = 0; QuestPlan.combat(t) < need && guard < 300; guard++)
        {
            String low = melee[0];
            for (String m : melee) if (t.skills.getOrDefault(m, 1) < t.skills.getOrDefault(low, 1)) low = m;
            int next = t.skills.getOrDefault(low, 1) + 1;
            if (next > 99) break;
            t.skills.put(low, next);
            to.put(low, next);
        }
        to.forEach((sk, lvl) -> train(r, a, trainedTo, sk, lvl));
    }

    static final class Training
    {
        double hours;
        String how;
        /** The method at the starting level, null when none. */
        Method first;
    }

    /**
     * Hours from one level to another, level by level at the best rate the table has for each level (methods the
     * account can do, by its other levels and quests). -1 when a level has no method.
     */
    static Training hours(ItemTree.Account a, String skill, int from, int to)
    {
        Training t = new Training();
        List<Method> table = MethodTables.forSkill(skill);
        if (table.isEmpty())
        {
            t.hours = -1;
            t.how = "no " + QuestPlan.pretty(skill) + " training data yet";
            return t;
        }
        double h = 0;
        List<String> used = new ArrayList<>();
        for (int lvl = from; lvl < to; lvl++)
        {
            Method m = bestAt(table, a, skill, lvl);
            if (m == null)
            {
                t.hours = -1;
                t.how = "no method at " + QuestPlan.pretty(skill) + " " + lvl;
                // Timed runs (tree, herb runs) train it, but their hours are not counted yet
                List<String> runs = new ArrayList<>();
                for (Method p : table) if (p.used && p.kind == Method.Kind.PASSIVE && p.level <= lvl && p.missing(a).isEmpty()) runs.add(p.name);
                if (!runs.isEmpty()) t.how = String.join(", ", runs) + " (timed runs, hours not counted yet)";
                // Methods the account can do whose rate the wiki doesn't give (Slayer masters)
                List<String> unrated = new ArrayList<>();
                for (Method p : table)
                    if (p.used && p.level <= lvl && p.kind != Method.Kind.QUEST && p.kind != Method.Kind.PASSIVE && p.xpAt(lvl) < 0
                        && p.missing(a).isEmpty() && p.forMode(a.mode())) unrated.add(p.name);
                if (runs.isEmpty() && !unrated.isEmpty())
                    t.how = String.join(", ", unrated.size() > 3 ? unrated.subList(unrated.size() - 3, unrated.size()) : unrated) + " (no rate on the wiki, hours not counted)";
                // Every way is behind something else first (Sailing before Pandemonium): say what
                if (runs.isEmpty() && unrated.isEmpty())
                    for (Method p : table)
                        if (p.used && p.level <= lvl && p.kind != Method.Kind.QUEST && p.kind != Method.Kind.PASSIVE && !p.missing(a).isEmpty())
                        {
                            t.how = "no method at " + QuestPlan.pretty(skill) + " " + lvl + " until " + String.join(", ", p.missing(a));
                            break;
                        }
                return t;
            }
            if (t.first == null) t.first = m;
            if (!used.contains(m.name)) used.add(m.name);
            h += (Experience.getXpForLevel(lvl + 1) - Experience.getXpForLevel(lvl)) / (double) m.xpAt(lvl);
        }
        t.hours = h;
        t.how = String.join(", then ", used.size() > 3 ? used.subList(0, 3) : used) + (used.size() > 3 ? ", ..." : "");
        return t;
    }

    /** The highest xp/h method at this level that the account's other levels and quests allow, null when none. */
    static Method bestAt(List<Method> table, ItemTree.Account a, String skill, int lvl)
    {
        Method best = null;
        for (Method m : table)
        {
            if (!m.used || m.level > lvl || m.xpAt(lvl) <= 0) continue;
            if (m.kind == Method.Kind.QUEST || m.kind == Method.Kind.PASSIVE) continue;
            boolean ok = (!m.members || a.members) && m.forMode(a.mode());
            for (String q : m.quests) ok &= a.questsDone.contains(q);
            for (Map.Entry<String, Integer> e : m.skills.entrySet()) ok &= a.skills.getOrDefault(e.getKey(), 1) >= e.getValue();
            if (ok && (best == null || m.xpAt(lvl) > best.xpAt(lvl))) best = m;
        }
        return best;
    }

    /**
     * step cards: the methods to train a skill from one level to another. First the best the account
     * can use now (by xp an hour), then each better one that opens on the way and the level it opens at. Pure.
     */
    public static List<Method> recommend(ItemTree.Account a, String skill, int from, int to)
    {
        List<Method> table = MethodTables.forSkill(skill), now = new ArrayList<>();
        for (Method m : table)
        {
            if (!m.used || m.level > from || m.xpAt(from) <= 0 || m.kind == Method.Kind.QUEST || m.kind == Method.Kind.PASSIVE) continue;
            if (bestAt(List.of(m), a, skill, from) == m) now.add(m);
        }
        now.sort((x, y) -> Integer.compare(y.xpAt(from), x.xpAt(from)));
        List<Method> out = new ArrayList<>(now.subList(0, Math.min(3, now.size())));
        for (int lvl = from + 1; lvl < to; lvl++)
        {
            Method m = bestAt(table, a, skill, lvl);
            if (m != null && !out.contains(m)) out.add(m);
        }
        return out;
    }

    /** One step for every item a quest still needs: "Get items for X", the items listed in how. */
    static Step group(String quest, List<Step> parts)
    {
        double hours = 0;
        boolean known = false;
        List<String> names = new ArrayList<>();
        for (Step p : parts)
        {
            names.add(p.what);
            if (p.hours >= 0) { hours += p.hours; known = true; }
        }
        Step g = new Step(Kind.GET, "items for " + QuestPlan.pretty(quest), String.join(", ", names), known ? hours : -1, false);
        g.parts.addAll(parts);
        for (Step p : parts) g.uses.addAll(p.uses);
        return g;
    }

    /**
     * A GET step for a quest item: the table's best way per hour, else the item tree's way. A table method that
     * needs this same quest is no way to get it (the quest hands it over: the karambwan in Tai Bwo Wannai Trio).
     */
    private static Step get(ItemData items, ItemTree.Account a, String name, int n, String quest)
    {
        String key = clean(name);
        String what = (n > 1 ? n + " x " : "") + name;
        if (key.equals("coins")) return new Step(Kind.GET, what, "any money maker", -1, false);
        List<Method> ways = new ArrayList<>();
        for (Method m : MethodTables.waysToMake(key, a)) if (!m.quests.contains(quest)) ways.add(m);
        Method m = best(ways, a, key);
        if (!ways.isEmpty() || MethodTables.waysToMake(key, a).isEmpty())
        {
            // A main buys it first (Grand Exchange), making it comes second
            int each = "main".equals(a.mode()) ? Prices.each(key) : 0;
            if (each > 0)
            {
                long total = (long) each * n;
                String make = m != null ? m.name : itemWay(items, a, key);
                Step st = new Step(Kind.GET, what, "Grand Exchange, about " + Prices.gp(each) + " each" + (n > 1 ? " (" + Prices.gp(total) + ")" : "")
                    + "; or make: " + make, 0, false);
                st.ge = total;
                return st;
            }
        }
        if (m != null)
        {
            int lvl = Math.max(m.level, a.skills.getOrDefault(m.skill, 1));
            int per = m.givesAt(key, lvl);
            Step st = new Step(Kind.GET, what, m.name, per > 0 ? n / (double) per : -1, false);
            uses(st, m, lvl);
            return st;
        }
        if (!MethodTables.waysToMake(key, a).isEmpty()) return new Step(Kind.GET, what, "you get it during the quest", 0, false);
        return new Step(Kind.GET, what, itemWay(items, a, key), -1, false);
    }

    /**
     * A quest's item text as an item name: "Iron spear or better (You will lose the spear)" -> "iron spear",
     * "Any logs to make a fire" -> "logs", "Agility Potion (4)" -> "agility potion(4)".
     */
    static String clean(String name)
    {
        return ItemData.questText(name);
    }

    /** The item tree's way, not counting what is held (a GET step is for what is still missing). */
    private static String itemWay(ItemData items, ItemTree.Account a, String item)
    {
        String quest = QuestPlan.questWay(item);
        if (quest != null) return quest;
        List<String> names = items == null ? List.of() : items.matches(item);
        if (names.isEmpty()) return "no guide data for this item yet";
        // "a or b": the cheapest way over every item the text can mean
        ItemTree.Way w = null;
        String which = null;
        for (String name : names)
        {
            ItemTree.Node n = ItemTree.trace(items, a, name);
            for (ItemTree.Way x : n.ways)
                if (x.kind != ItemTree.Kind.OWNED && (w == null || x.cost < w.cost)) { w = x; which = name; }
        }
        if (w == null) return "no way found";
        String say = w.say == null ? w.label : w.say;
        return (names.size() > 1 ? which + ": " : "") + say + " (no rate yet)";
    }
}
