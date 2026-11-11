package com.goalguide.guide;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import com.goalguide.items.ItemData;
import com.goalguide.items.ItemTree;

/**
 * "I rather get it myself": a small second box on the game screen with the route to one item, opened
 * from the guide box's right-click menu ("Get" on a missing item) or a step card's button. The route is the Item Roots
 * plan for this account, in doing order (raw things first). Shown until closed from its own right-click menu.
 */
public final class GetBox
{
    private GetBox() { }

    /** One line of the box. */
    public static final class Line
    {
        public final String text;
        public final Color color;

        Line(String text, Color color)
        {
            this.text = text;
            this.color = color;
        }

        @Override public String toString() { return text; }
    }

    /** The item shown, null when the box is closed. */
    static volatile String item;
    static volatile List<Line> lines = List.of();
    /** Works the route out off the client thread (set by the panel, which has the account and the item data). */
    private static volatile BiConsumer<String, Consumer<List<Line>>> builder;

    public static void builder(BiConsumer<String, Consumer<List<Line>>> b) { builder = b; }

    public static String item() { return item; }

    /** Opens the box for an item; the route fills in a moment later. */
    public static void open(String name)
    {
        item = name;
        lines = List.of(new Line("Working out the route...", GuideOverlay.DIM));
        BiConsumer<String, Consumer<List<Line>>> b = builder;
        if (b == null) return;
        b.accept(name, l -> { if (name.equals(item)) lines = l; });
    }

    public static void close()
    {
        item = null;
        lines = List.of();
    }

    /** At most this many lines; the rest is in the panel's Items tab. */
    static final int MAX = 14;

    /**
     * The route as short lines: training the plan needs first, then one line per step ("1. Flax: pick it in
     * Seers' Village"), with the levels or quests a step still needs under it. Pure.
     */
    public static List<Line> lines(ItemData d, ItemTree.Account a, String wanted)
    {
        List<Line> out = new ArrayList<>();
        if (d == null) return List.of(new Line("Item data not loaded yet.", GuideOverlay.DIM));
        String name = d.known(wanted) ? wanted : null;
        if (name == null)
        {
            List<String> names = d.matches(ItemData.questText(wanted));
            if (!names.isEmpty()) name = names.get(0);
        }
        if (name == null) return List.of(new Line("No route known for " + wanted + ".", GuideOverlay.DIM));
        ItemTree.Node n = ItemTree.trace(d, a, name);
        if (n.best == null) return List.of(new Line("No way this account can get it yet.", GuideOverlay.BAD));
        List<ItemTree.Step> steps = ItemTree.steps(d, a, n);
        // A missing level: one line, at the highest the plan needs
        Map<String, Integer> train = new LinkedHashMap<>();
        List<String> quests = new ArrayList<>();
        for (ItemTree.Step st : steps)
            st.needs.forEach((k, ok) ->
            {
                if (ok) return;
                int sp = k.lastIndexOf(' ');
                if (k.startsWith("quest ")) { if (!quests.contains(k.substring(6))) quests.add(k.substring(6)); }
                else if (sp > 0 && k.substring(sp + 1).matches("\\d+")) train.merge(k.substring(0, sp), Integer.parseInt(k.substring(sp + 1)), Math::max);
            });
        for (String q : quests) out.add(new Line("First: quest " + q, GuideOverlay.WARN));
        train.forEach((sk, lvl) -> out.add(new Line("First: train " + sk + " to " + lvl, GuideOverlay.WARN)));
        List<ItemTree.Step> real = new ArrayList<>();
        for (ItemTree.Step st : steps) if (!"Coins".equalsIgnoreCase(st.item)) real.add(st);
        for (int i = 0; i < real.size(); i++)
        {
            if (out.size() >= MAX) { out.add(new Line("+ more in the panel's Items tab", GuideOverlay.DIM)); break; }
            ItemTree.Step st = real.get(i);
            String how = st.way.say == null ? st.way.label : st.way.say;
            // The last step makes the item itself
            out.add(new Line((i + 1) + ". " + qty(st.qty) + st.item + ": " + how, i == real.size() - 1 ? GuideOverlay.NOW : Color.WHITE));
        }
        return out;
    }

    /** "3 x ", "" for one, "" for a part of one (the line says how). */
    static String qty(double q)
    {
        if (q <= 1 + 1e-9) return "";
        return (q == Math.rint(q) ? String.valueOf((long) q) : String.format("%.1f", q)) + " x ";
    }
}
