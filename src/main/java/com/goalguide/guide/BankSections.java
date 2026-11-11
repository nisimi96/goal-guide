package com.goalguide.guide;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;

/**
 * While the bank button is on, the bank shows a Bank Tags layout: a blank row per section for its line and
 * title, then the section's items in order. An item a section shares with an earlier one (the steel bar of
 * each sword) is in the layout again, and Bank Tags draws every copy as the real, clickable bank item.
 * We add the lines, titles and a have/need count under each step item, red with a cross when short.
 * Only draws; withdrawing is Bank Tags' own. Client thread.
 */
final class BankSections
{
    private BankSections() { }

    // The bank's item grid
    static final int PER_ROW = 8, DX = 48, DY = 36, X0 = 51, GAP = 5, LINE = 2, TITLE = 15, ITEM_W = 36, ITEM_H = 32;
    static final int TITLE_COLOR = 0xe4d8a2, ENOUGH = 0x7fd17f, SHORT = 0xff6060;

    /** The layout to show, and what sits where in it. */
    static final class Plan
    {
        /** Item id per bank slot, -1 for empty. */
        final int[] ids;
        /** Slot of the first cell of each title row, to its section title. */
        final Map<Integer, String> titles;
        /** Slot of each step item, to its count. */
        final Map<Integer, StepItems.Item> counts;

        Plan(int[] ids, Map<Integer, String> titles, Map<Integer, StepItems.Item> counts)
        {
            this.ids = ids;
            this.titles = titles;
            this.counts = counts;
        }

        boolean has(int id)
        {
            for (int i : ids) if (i == id) return true;
            return false;
        }
    }

    /**
     * Lays the sections out: each starts on a new row under a blank title row.
     * @param idOf   a step item to the id to show for it (one you hold of its kind, else its own), 0 when unknown
     * @param idsOf  a run's item name to the ids to show (each kind you hold, else its own)
     */
    static Plan plan(List<BankFilter.Section> sections, ToIntFunction<String> idOf, Function<String, List<Integer>> idsOf)
    {
        List<Integer> ids = new ArrayList<>();
        Map<Integer, String> titles = new LinkedHashMap<>();
        Map<Integer, StepItems.Item> counts = new HashMap<>();
        for (BankFilter.Section s : sections)
        {
            while (ids.size() % PER_ROW != 0) ids.add(-1);
            titles.put(ids.size(), s.title);
            for (int i = 0; i < PER_ROW; i++) ids.add(-1);
            Set<Integer> here = new HashSet<>();
            if (!s.items.isEmpty())
                for (StepItems.Item it : s.items)
                {
                    int id = idOf.applyAsInt(it.name);
                    if (id <= 0 || !here.add(id)) continue;
                    counts.put(ids.size(), it);
                    ids.add(id);
                }
            else
                for (String k : s.keys)
                    for (int id : idsOf.apply(k))
                        if (here.add(id)) ids.add(id);
        }
        int[] out = new int[ids.size()];
        for (int i = 0; i < out.length; i++) out[i] = ids.get(i);
        return new Plan(out, titles, counts);
    }

    /** How many children the item box had before ours were added; -1 = none added. */
    private static int before = -1;

    /** The bank lays out again: take our lines, titles and counts away (they are the last children). */
    static void clear(Client client)
    {
        int n = before;
        before = -1;
        Widget box = client.getWidget(InterfaceID.Bankmain.ITEMS);
        Widget[] kids = box == null ? null : box.getChildren();
        if (n < 0 || kids == null || kids.length <= n) return;
        box.setChildren(Arrays.copyOf(kids, n));
        box.revalidate();
    }

    /** Bank Tags laid the plan out: add the lines, titles and counts. */
    static void lay(Client client, Plan plan)
    {
        Widget box = client.getWidget(InterfaceID.Bankmain.ITEMS);
        Widget[] kids = box == null ? null : box.getChildren();
        if (kids == null || plan.ids.length == 0 || before >= 0) return;
        // The bank's own tab lines and "Tab 2" titles make way for ours
        for (Widget w : kids)
            if (w != null && !w.isSelfHidden() && (w.getSpriteId() == SpriteID.TRADEBACKING_DARK
                || w.getType() == WidgetType.TEXT && w.getText() != null && w.getText().startsWith("Tab")))
                w.setHidden(true);
        before = kids.length;
        for (Map.Entry<Integer, String> t : plan.titles.entrySet())
        {
            int y = yOf(t.getKey());
            line(box, y);
            text(box, t.getValue(), TITLE_COLOR, X0, y + GAP, PER_ROW * DX, TITLE);
        }
        for (Map.Entry<Integer, StepItems.Item> c : plan.counts.entrySet())
        {
            int at = c.getKey();
            count(box, c.getValue(), X0 + (at % PER_ROW) * DX, yOf(at));
        }
    }

    /** Where Bank Tags puts a layout slot (LayoutManager: x = 51 + col * 48, y = row * 36). */
    private static int yOf(int slot)
    {
        return (slot / PER_ROW) * DY;
    }

    /** "74/225" along the bottom of an item, red with a cross when short. */
    private static void count(Widget box, StepItems.Item it, int x, int y)
    {
        boolean isShort = BankFilter.isShort(it);
        text(box, BankFilter.count(it), isShort ? SHORT : ENOUGH, x, y + ITEM_H - 10, ITEM_W, 10);
        if (!isShort) return;
        Widget c = box.createChild(-1, WidgetType.GRAPHIC);
        c.setSpriteId(SpriteID.Checkbox.CROSSED);
        place(c, x + ITEM_W - 13, y, 13, 13);
    }

    private static void line(Widget box, int y)
    {
        Widget w = box.createChild(-1, WidgetType.GRAPHIC);
        w.setSpriteId(SpriteID.TRADEBACKING_DARK);
        place(w, X0, y, PER_ROW * DX, LINE);
    }

    private static void text(Widget box, String s, int color, int x, int y, int w, int h)
    {
        Widget t = box.createChild(-1, WidgetType.TEXT);
        t.setText(s);
        t.setFontId(FontID.PLAIN_11);
        t.setTextColor(color);
        t.setTextShadowed(true);
        place(t, x, y, w, h);
    }

    private static void place(Widget w, int x, int y, int width, int height)
    {
        w.setOriginalX(x);
        w.setOriginalY(y);
        w.setOriginalWidth(width);
        w.setOriginalHeight(height);
        w.revalidate();
    }
}
