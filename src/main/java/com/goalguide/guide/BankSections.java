package com.goalguide.guide;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;

/**
 * While the bank button is on, the bank's items sit under a line and a small title per section. Step items
 * get a have/need count, with a red cross when short; a step item you have nowhere shows faded.
 * Only moves item pictures and adds text; nothing in the bank moves. Client thread.
 */
final class BankSections
{
    private BankSections() { }

    // The bank's item grid
    static final int PER_ROW = 8, DX = 48, DY = 36, X0 = 51, GAP = 5, LINE = 2, TITLE = 15, ITEM_W = 36, ITEM_H = 32;
    static final int TITLE_COLOR = 0xe4d8a2, ENOUGH = 0x7fd17f, SHORT = 0xff6060;

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

    /**
     * Groups the shown items under their sections.
     * @param nameOf bank item id to its name
     * @param idOf   item name to a game id, 0 when unknown (for faded missing items)
     */
    static void lay(Client client, List<BankFilter.Section> sections, IntFunction<String> nameOf, ToIntFunction<String> idOf, Runnable fixScroll)
    {
        Widget box = client.getWidget(InterfaceID.Bankmain.ITEMS);
        Widget[] kids = box == null ? null : box.getChildren();
        if (kids == null || sections.isEmpty() || before >= 0) return;
        Map<BankFilter.Section, List<Widget>> groups = new LinkedHashMap<>();
        for (BankFilter.Section s : sections) groups.put(s, new ArrayList<>());
        Map<Widget, String> names = new HashMap<>();
        for (Widget w : kids)
        {
            if (w == null || w.isSelfHidden()) continue;
            // The bank's own tab lines and "Tab 2" titles make way for ours
            if (w.getSpriteId() == SpriteID.TRADEBACKING_DARK || w.getType() == WidgetType.TEXT && w.getText() != null && w.getText().startsWith("Tab"))
            {
                w.setHidden(true);
                continue;
            }
            int id = w.getItemId();
            if (id <= -1 || id == ItemID.BLANKOBJECT) continue;
            String name = nameOf.apply(id);
            BankFilter.Section s = BankFilter.sectionOf(name, sections);
            if (s == null) continue;
            groups.get(s).add(w);
            names.put(w, name);
        }
        before = kids.length;
        int y = 0;
        for (Map.Entry<BankFilter.Section, List<Widget>> g : groups.entrySet())
        {
            BankFilter.Section s = g.getKey();
            line(box, y);
            text(box, s.title, TITLE_COLOR, X0, y + GAP, PER_ROW * DX, TITLE);
            y += GAP + TITLE;
            List<StepItems.Item> seen = new ArrayList<>();
            int i = 0;
            for (Widget w : g.getValue())
            {
                int x = X0 + (i % PER_ROW) * DX, at = y + (i / PER_ROW) * DY;
                w.setOriginalX(x);
                w.setOriginalY(at);
                w.revalidate();
                StepItems.Item it = BankFilter.itemOf(names.get(w), s.items);
                if (it != null && !seen.contains(it))
                {
                    seen.add(it);
                    count(box, it, x, at);
                }
                i++;
            }
            for (StepItems.Item it : s.items)
            {
                int id = seen.contains(it) || !BankFilter.isShort(it) ? 0 : idOf.applyAsInt(it.name);
                if (id <= 0) continue;
                int x = X0 + (i % PER_ROW) * DX, at = y + (i / PER_ROW) * DY;
                faded(box, id, it.name, x, at);
                count(box, it, x, at);
                i++;
            }
            y += Math.max(1, (i + PER_ROW - 1) / PER_ROW) * DY;
        }
        box.setScrollHeight(Math.max(y, box.getHeight()));
        fixScroll.run();
    }

    /** The bank's scrollbar after our layout. Run after the bank's own script ends. */
    static void scrollbar(Client client)
    {
        Widget box = client.getWidget(InterfaceID.Bankmain.ITEMS);
        if (box != null) client.runScript(ScriptID.UPDATE_SCROLLBAR, InterfaceID.Bankmain.SCROLLBAR, InterfaceID.Bankmain.ITEMS, box.getScrollY());
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

    /** A faded picture of an item the step needs and you have nowhere. */
    private static void faded(Widget box, int id, String name, int x, int y)
    {
        Widget w = box.createChild(-1, WidgetType.GRAPHIC);
        w.setItemId(id);
        w.setItemQuantity(0);
        w.setItemQuantityMode(0);
        w.setOpacity(150);
        w.setName("<col=ff9040>" + name + "</col>");
        place(w, x, y, ITEM_W, ITEM_H);
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
