package com.goalguide.goals;

import static com.goalguide.goals.Text.cap;
import static com.goalguide.goals.Text.esc;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import com.goalguide.facts.Method;
import com.goalguide.facts.MethodTables;
import com.goalguide.guide.StepItems;
import com.goalguide.items.ItemData;
import com.goalguide.items.ItemTree;
import com.goalguide.quests.QuestPlan;

/**
 * what a route step shows when its card is opened, in titled sections like the guide box.
 * Every step: What to do and its items. An item: its how-to-get-it steps. Training: the recommended methods.
 * A quest: the guide links (the panel adds the buttons). Pure.
 */
public final class StepDetail
{
    private StepDetail() { }

    /** The step's sections as html (no html/body tags): a thin line and a small label above each */
    public static String html(Planner.Step s, ItemTree.Account a, Map<String, Map<String, Integer>> storages,
                              Function<String, String> source, ItemData d, String who)
    {
        StringBuilder b = new StringBuilder();
        if (s.serves.size() > 1) b.append(small("Moves " + s.serves.size() + " goals: " + String.join(", ", s.serves), "#8fc8f0"));
        else if (s.goal != null) b.append(small("For your goal: " + s.goal, DIM));

        // A training step's methods are its own section; others say how in short lines
        if (s.kind != Planner.Kind.TRAIN && s.how != null && !s.how.isEmpty())
        {
            b.append(section(s.kind == Planner.Kind.QUEST ? "Notes" : "How"));
            for (String part : s.kind == Planner.Kind.QUEST ? s.how.split(", ") : new String[]{s.how})
                b.append("<font color='#d0d0d0'>").append(s.kind == Planner.Kind.QUEST ? "&bull; " : "").append(esc(part)).append("</font><br>");
        }

        List<StepItems.Item> its = StepItems.of(s, storages, source);
        if (!its.isEmpty()) b.append(itemsHtml(its));

        if (s.kind == Planner.Kind.TRAIN && s.skill != null) b.append(recommendHtml(a, s.skill, s.from, s.to));
        String roots = (s.kind == Planner.Kind.GET || s.kind == Planner.Kind.STOCK) && s.item != null ? rootsHtml(s, a, d, who) : null;
        if (roots != null) b.append(section("How to get it")).append(roots);
        b.append(section("Wiki"));
        String page = wikiPage(s);
        if (page != null) b.append(link(wikiLabel(s), page, "#8fc8f0")).append("<br>");
        if (s.kind == Planner.Kind.QUEST) b.append(section("Quest guide"));
        return b.toString();
    }

    static final String DIM = "#9a9a9a";

    /** One row per item: a mark and the name, the state on the right, where to get a missing one under it. */
    static String itemsHtml(List<StepItems.Item> its)
    {
        int ready = 0;
        for (StepItems.Item i : its) if (i.has == StepItems.Has.CARRIED) ready++;
        StringBuilder b = new StringBuilder(section("Items", ready + "/" + its.size() + " carried"));
        b.append(TABLE);
        for (StepItems.Item i : its)
        {
            String c, mark, state;
            switch (i.has)
            {
                case CARRIED: c = "#7fd17f"; mark = "&#10003;"; state = "carried"; break;
                case STORED: c = "#f0c070"; mark = "&#9675;"; state = String.join(", ", i.where); break;
                case REMINDER: c = DIM; mark = "&bull;"; state = "bring"; break;
                case USED: c = DIM; mark = "&bull;"; state = "used?"; break;
                default: c = "#f08a78"; mark = "&#10007;"; state = "missing"; break;
            }
            String q = i.need > 1 && !i.perHour ? i.need + " x " : "";
            // Each item links to its wiki page (a reminder like "Combat equipment" is no item)
            String name = i.has == StepItems.Has.REMINDER || i.name.isEmpty() ? esc(cap(q + i.name)) : esc(q) + link(cap(i.name), itemUrl(i.name), c);
            b.append(row("<font color='" + c + "'>" + mark + " " + name + "</font>", "<font color='" + c + "'>" + esc(state) + "</font>"));
            if (i.from != null) b.append("<tr><td colspan='2'><font color='" + DIM + "'>&nbsp;&nbsp;&nbsp;" + esc(i.from) + "</font></td></tr>");
        }
        return b.append("</table>").toString();
    }

    /** "Get 2 x Ruby", "Train Ranged 16 to 40", "Family Crest". */
    public static String title(Planner.Step s)
    {
        return s.verb() + s.what;
    }

    /** Methods shown under Recommended: the best now, then this many that open on the way. */
    static final int MORE = 3;

    /**
     * The methods to use, one row each with its xp an hour on the right: the best the account can use now
     * (starred, with where), then the better ones that open on the way ("Lv 25"), the rest counted.
     */
    static String recommendHtml(ItemTree.Account a, String skill, int from, int to)
    {
        List<Method> ms = Planner.recommend(a, skill, from, to);
        List<String> unrated = new ArrayList<>();
        for (Method m : MethodTables.forSkill(skill))
            if (m.used && m.level <= from && m.xpAt(from) < 0 && m.kind != Method.Kind.PASSIVE && m.missing(a).isEmpty()) unrated.add(m.name);
        StringBuilder b = new StringBuilder(section("Recommended", "xp/h"));
        if (ms.isEmpty() && unrated.isEmpty()) return b.append("<font color='" + DIM + "'>No training data for this level yet.</font><br>").toString();
        b.append(TABLE);
        Method best = null;
        for (Method m : ms) if (m.level <= from) { best = m; break; }
        if (best != null)
        {
            b.append(row("<font color='#7fd17f'>&#9733; " + esc(best.name) + "</font>", "<font color='#7fd17f'>" + k(best.xpAt(from)) + "</font>"));
            if (best.where != null && !best.where.isEmpty()) b.append("<tr><td colspan='2'><font color='" + DIM + "'>&nbsp;&nbsp;&nbsp;" + esc(best.where) + "</font></td></tr>");
        }
        for (String u : unrated) b.append(row("<font color='#d0d0d0'>&bull; " + esc(u) + "</font>", "<font color='" + DIM + "'>?</font>"));
        int shown = 0, left = 0;
        for (Method m : ms)
        {
            if (m == best || m.level <= from) continue;
            if (shown++ >= MORE) { left++; continue; }
            b.append(row("<font color='" + DIM + "'>Lv " + m.level + "</font> <font color='#d0d0d0'>" + esc(m.name) + "</font>",
                "<font color='#d0d0d0'>" + k(m.xpAt(m.level)) + "</font>"));
        }
        b.append("</table>");
        if (left > 0) b.append("<font color='" + DIM + "'>+ " + left + " more on the way</font><br>");
        return b.toString();
    }

    /** 13000 -> "13k", 18500 -> "18.5k", 950 -> "950". */
    static String k(int xp)
    {
        if (xp < 1000) return String.valueOf(xp);
        return xp % 1000 == 0 ? xp / 1000 + "k" : String.format("%.1fk", xp / 1000.0);
    }

    static final String TABLE = "<table width='100%' cellpadding='0' cellspacing='0'>";

    static String row(String left, String right)
    {
        return "<tr><td valign='top'>" + left + "</td><td width='38' align='right' valign='top'>" + right + "</td></tr>";
    }

    /** A thin line, then a small grey label (and a note on the right). */
    static String section(String label)
    {
        return section(label, null);
    }

    static String section(String label, String right)
    {
        return LINE + TABLE + "<tr><td><font color='#ff981f'>" + esc(label.toUpperCase(java.util.Locale.ROOT)) + "</font></td>"
            + "<td align='right'><font color='" + DIM + "'>" + (right == null ? "" : esc(right)) + "</font></td></tr></table>";
    }

    /** A thin grey line across the card, with a little room above it. */
    static final String LINE = "<div style='margin-top:5px; border-top: 1px solid #5a5a5a; font-size:1px'>&nbsp;</div>";

    static String small(String text, String color)
    {
        return "<font color='" + color + "'>" + esc(text) + "</font><br>";
    }

    /** The how-to-get-it steps for the item, without its big title (the card says it). Null when the item data has no such item. */
    static String rootsHtml(Planner.Step s, ItemTree.Account a, ItemData d, String who)
    {
        if (d == null) return null;
        String name = d.known(s.item) ? s.item : null;
        if (name == null)
        {
            List<String> names = d.matches(ItemData.questText(s.item));
            if (!names.isEmpty()) name = names.get(0);
        }
        if (name == null) return null;
        return ItemRoute.html(d, a, ItemTree.trace(d, a, name), who) + "<br>";
    }

    /**
     * The wiki guide for a step: a quest's Quick guide page (checked on the wiki 2026-11-11), or a miniquest's own page
     * ("Barbarian Training: fishing" -> Barbarian Training).
     */
    public static String wikiUrl(Planner.Step s)
    {
        boolean quest = s.quest != null;
        String name = quest ? QuestPlan.pretty(s.quest) : s.what;
        if (!quest && name.contains(":")) name = name.substring(0, name.indexOf(':'));
        String page = URLEncoder.encode(name.trim().replace(' ', '_'), StandardCharsets.UTF_8).replace("%27", "'");
        return "https://oldschool.runescape.wiki/w/" + page + (quest ? "/Quick_guide" : "");
    }

    static final String WIKI = "https://oldschool.runescape.wiki/w/";

    /** A quest's wiki Quick guide by quest key (the game's quest name), or a diary's own page. */
    public static String questWikiUrl(String key)
    {
        String name;
        try { name = net.runelite.api.Quest.valueOf(key).getName(); }
        catch (IllegalArgumentException e) { name = QuestPlan.pretty(key); }
        boolean diary = com.goalguide.quests.QuestData.isDiary(key);
        String page = URLEncoder.encode(name.trim().replace(' ', '_'), StandardCharsets.UTF_8).replace("%27", "'");
        return WIKI + page + (diary ? "" : "/Quick_guide");
    }

    /** A link in the card's colours: the text keeps its colour, a dotted line under it says it can be clicked. */
    static String link(String text, String url, String color)
    {
        return "<a href='" + url.replace("'", "%27") + "' style='color:" + color + "; text-decoration:underline'>" + esc(text) + "</a>";
    }

    /** The wiki's page for an item: its search jumps straight to the page when the name is exact. */
    public static String itemUrl(String item)
    {
        return WIKI + "Special:Search?search=" + URLEncoder.encode(item, StandardCharsets.UTF_8) + "&go=Go";
    }

    /** The step's own wiki page: the skill's training guide, the item, the quest (or miniquest). */
    public static String wikiPage(Planner.Step s)
    {
        switch (s.kind)
        {
            case TRAIN:
                if (s.skill == null) return null;
                return WIKI + "Special:Search?search=" + URLEncoder.encode(QuestPlan.pretty(s.skill) + " training", StandardCharsets.UTF_8) + "&go=Go";
            case GET:
            case STOCK:
                return s.item == null ? null : itemUrl(cap(s.item));
            default:
            {
                String w = wikiUrl(s);
                return w.endsWith("/Quick_guide") ? w.substring(0, w.length() - "/Quick_guide".length()) : w;
            }
        }
    }

    static String wikiLabel(Planner.Step s)
    {
        switch (s.kind)
        {
            case TRAIN: return (s.skill == null ? "" : QuestPlan.pretty(s.skill)) + " training guide";
            case GET:
            case STOCK: return cap(s.item == null ? s.what : s.item);
            default: return s.quest != null ? QuestPlan.pretty(s.quest) : s.what;
        }
    }
}
