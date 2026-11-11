package com.goalguide.goals;

import static com.goalguide.goals.Text.esc;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.goalguide.facts.Method;
import com.goalguide.facts.MethodTables;
import com.goalguide.items.ItemData;
import com.goalguide.items.ItemTree;

/** How to get an item, step by step, as card html. Pure. */
public final class ItemRoute
{
    private ItemRoute() { }

    static String html(ItemData d, ItemTree.Account a, ItemTree.Node n, String planFor)
    {
        StringBuilder b = new StringBuilder();
        b.append(Text.color(Text.GREY, "Planned for: " + planFor)).append("<br>");
        b.append(skillWays(a, n.item));
        if (n.best == null) return b.append("No way to get this one in the wiki data.").toString();

        List<ItemTree.Step> steps = ItemTree.steps(d, a, n);
        Set<String> missing = new LinkedHashSet<>();
        long coins = 0;
        for (ItemTree.Step st : steps)
        {
            st.needs.forEach((k, ok) -> { if (!ok) missing.add(k); });
            if ("Coins".equalsIgnoreCase(st.item)) coins += Math.round(st.qty);
        }
        b.append(missing.isEmpty() ? Text.color(Text.GREEN, "✓ You can do every step now.")
            : Text.color(Text.YELLOW, "Train first: " + String.join(", ", highest(missing)))).append("<br><br>");

        b.append("<b>Steps for 1</b> (top to bottom)<br><br>");
        // A missing level gets its own step, once, at the highest level the steps need
        Map<String, Integer> train = levels(steps);
        Set<String> trained = new HashSet<>();
        int i = 1;
        for (ItemTree.Step st : steps)
        {
            if ("Coins".equalsIgnoreCase(st.item)) continue;
            for (Map.Entry<String, Boolean> nd : st.needs.entrySet())
            {
                String skill = skillOf(nd.getKey());
                if (nd.getValue() || skill == null || !train.containsKey(skill) || !trained.add(skill)) continue;
                b.append("<b>").append(i++).append(". ").append(Text.color(Text.YELLOW, "Train " + skill + " to " + train.get(skill)))
                    .append("</b><br>Do this first<br><br>");
            }
            b.append("<b>").append(i++).append(". ").append(StepDetail.link(st.item, StepDetail.itemUrl(st.item), "#ffffff")).append("</b>");
            String q = amount(st.qty);
            if (!q.isEmpty()) b.append(' ').append(Text.color(Text.GREY, q));
            b.append("<br>").append(esc(st.way.say == null ? st.way.label : st.way.say)).append("<br>");
            if (!st.needs.isEmpty())
            {
                List<String> parts = new ArrayList<>();
                st.needs.forEach((k, ok) -> parts.add(ok ? Text.color(Text.GREEN, k + " ✓") : Text.color(Text.RED, k + " ✗")));
                b.append(String.join(", ", parts)).append("<br>");
            }
            b.append("<br>");
        }
        if (coins > 0) b.append("<b>Coins:</b> ").append(String.format("%,d", coins)).append(" gp each<br><br>");

        StringBuilder other = new StringBuilder();
        int shown = 0;
        for (ItemTree.Way w : n.ways)
        {
            if (w == n.best || shown >= 3) continue;
            other.append("&bull; ").append(esc(w.say == null ? w.label : w.say));
            if (!w.missing.isEmpty()) other.append(' ').append(Text.color(Text.RED, "(needs " + String.join(", ", w.missing) + ")"));
            other.append("<br>");
            shown++;
        }
        if (shown > 0) b.append("<font color='").append(Text.GREY).append("'><b>Other ways</b><br>").append(other).append("</font>");
        return b.toString();
    }

    /** Skill methods that make the item, best per hour first. Empty when none do. */
    public static String skillWays(ItemTree.Account a, String item)
    {
        String key = item.trim().toLowerCase(Locale.ROOT);
        List<Method> ways = MethodTables.waysToMake(key, a);
        if (ways.isEmpty()) return "";
        StringBuilder b = new StringBuilder("<br><b>Best per hour</b><br>");
        for (Method m : ways.subList(0, Math.min(3, ways.size())))
        {
            int lvl = Math.max(m.level, a.skills.getOrDefault(m.skill, 1));
            int per = m.givesAt(key, lvl);
            List<String> miss = m.missing(a);
            b.append(miss.isEmpty() ? Text.color(Text.GREEN, "✓ ") : "&bull; ").append(esc(m.name))
                .append(' ').append(Text.color(Text.GREY, "(" + Method.pretty(m.skill) + " " + m.level + ")")).append(": ")
                .append(per < 0 ? "rate unknown" : String.format("%,d", per) + " an hour" + (lvl > m.level ? "" : " at " + m.level));
            if (!miss.isEmpty()) b.append(' ').append(Text.color(Text.RED, "needs " + String.join(", ", miss)));
            b.append("<br>");
        }
        return b.append("<br>").toString();
    }

    /** "Herblore 38" -> "Herblore", null for a quest or anything without a level. */
    private static String skillOf(String need)
    {
        int sp = need.lastIndexOf(' ');
        return sp > 0 && !need.startsWith("quest ") && need.substring(sp + 1).matches("\\d+") ? need.substring(0, sp) : null;
    }

    /** The highest missing level per skill. */
    private static Map<String, Integer> levels(List<ItemTree.Step> steps)
    {
        Map<String, Integer> top = new LinkedHashMap<>();
        for (ItemTree.Step st : steps)
            st.needs.forEach((k, ok) -> {
                String skill = skillOf(k);
                if (!ok && skill != null) top.merge(skill, Integer.parseInt(k.substring(skill.length() + 1)), Math::max);
            });
        return top;
    }

    /** "Herblore 25", "Herblore 38" -> "Herblore 38"; quests stay as they are. */
    static List<String> highest(Collection<String> needs)
    {
        Map<String, Integer> top = new LinkedHashMap<>();
        List<String> other = new ArrayList<>();
        for (String k : needs)
        {
            String skill = skillOf(k);
            if (skill != null) top.merge(skill, Integer.parseInt(k.substring(skill.length() + 1)), Math::max);
            else other.add(k);
        }
        List<String> out = new ArrayList<>();
        top.forEach((k, v) -> out.add(k + " " + v));
        out.addAll(other);
        return out;
    }

    /** "x 5", "(1 for every 6)", or "" for exactly one. */
    static String amount(double q)
    {
        if (Math.abs(q - 1) < 1e-9) return "";
        if (q < 1) return "(1 for every " + Math.round(1 / q) + ")";
        return "x " + (q == Math.rint(q) ? String.valueOf((long) q) : String.format("%.1f", q));
    }
}
