package com.goalguide.guide;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.ItemContainer;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.ItemQuantityMode;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;

/**
 * While the bank button is on, the bank shows only our sections, each under a line and a title. Like Quest
 * Helper's bank tab, every picture is one of the bank's own item slots, filled in order with the item to
 * show; so an item two sections need (the steel bar for each sword) shows in both, and BankButton points
 * each click at the item's real bank slot. A step item not in the bank shows faded with nothing to click;
 * run and teleport items not in the bank go, once each, in a last "Not in bank" section.
 * Step items get a have/need count, red with a cross when short. The bank's next build resets the slots.
 * Client thread.
 */
final class BankSections
{
    private BankSections() { }

    // The bank's item grid
    static final int PER_ROW = 8, DX = 48, DY = 36, X0 = 51, GAP = 5, LINE = 2, TITLE = 15, ITEM_W = 36, ITEM_H = 32;
    static final int TITLE_COLOR = 0xe4d8a2, ENOUGH = 0x7fd17f, SHORT = 0xff6060;
    // Widget opacity: 0 is solid, 255 is gone
    static final int SOLID = 0, FADED = 120;

    /** How many children the item box had before ours were added; -1 = none added. */
    private static int before = -1;

    /** The bank lays out again: take our lines, titles and counts away, and give the slots their size back. */
    static void clear(Client client)
    {
        Widget box = client.getWidget(InterfaceID.Bankmain.ITEMS);
        Widget[] kids = box == null ? null : box.getChildren();
        int n = before;
        before = -1;
        if (kids == null) return;
        if (n >= 0 && kids.length > n)
        {
            kids = Arrays.copyOf(kids, n);
            box.setChildren(kids);
        }
        for (Widget w : kids)
            if (w != null && w.getItemId() > -1 && (w.getOriginalWidth() != ITEM_W || w.getOriginalHeight() != ITEM_H))
            {
                w.setOriginalWidth(ITEM_W);
                w.setOriginalHeight(ITEM_H);
            }
        box.revalidate();
    }

    /**
     * Shows the sections.
     * @param idOf  a step item to the id to show (one of its kind in the bank, else its own), 0 when unknown
     * @param idsOf a run's item to the ids to show (each kind in the bank, else its own)
     */
    static void lay(Client client, ItemContainer bank, List<BankFilter.Section> sections, ToIntFunction<String> idOf,
        Function<String, List<Integer>> idsOf, IntFunction<String> nameOf, String[] withdraw, Runnable fixScroll)
    {
        Widget box = client.getWidget(InterfaceID.Bankmain.ITEMS);
        Widget[] kids = box == null ? null : box.getChildren();
        if (kids == null || bank == null || sections.isEmpty() || before >= 0) return;
        // Hide the bank's items and its own tab lines and "Tab 2" titles
        for (Widget w : kids)
            if (w != null && (!w.isSelfHidden() && w.getItemId() > -1 || w.getSpriteId() == SpriteID.TRADEBACKING_DARK
                || w.getType() == WidgetType.TEXT && w.getText() != null && w.getText().startsWith("Tab")))
                w.setHidden(true);
        before = kids.length;
        int[] slot = {0};
        int y = 0;
        // Run and teleport items not in the bank all go in one last section, once each
        List<Integer> notInBank = new ArrayList<>();
        for (BankFilter.Section s : sections)
        {
            // What to show: a step's items with counts, or a run's items you have
            List<Integer> ids = new ArrayList<>();
            List<StepItems.Item> items = new ArrayList<>();
            if (!s.items.isEmpty())
                for (StepItems.Item it : s.items)
                {
                    int id = idOf.applyAsInt(it.name);
                    if (id <= 0 || ids.contains(id)) continue;
                    ids.add(id);
                    items.add(it);
                }
            else
                for (String k : s.keys)
                    for (int id : idsOf.apply(k))
                    {
                        if (bank.count(id) <= 0)
                        {
                            if (!notInBank.contains(id)) notInBank.add(id);
                        }
                        else if (!ids.contains(id))
                        {
                            ids.add(id);
                            items.add(null);
                        }
                    }
            y = section(box, kids, s.title, ids, items, bank, nameOf, withdraw, slot, y);
        }
        List<StepItems.Item> none = new ArrayList<>();
        for (int i = 0; i < notInBank.size(); i++) none.add(null);
        y = section(box, kids, BankFilter.NOT_IN_BANK, notInBank, none, bank, nameOf, withdraw, slot, y);
        box.setScrollHeight(Math.max(y, box.getHeight()));
        fixScroll.run();
    }

    /** One section: line, title and its items in the next free bank slots; returns the height below it. */
    private static int section(Widget box, Widget[] kids, String title, List<Integer> ids, List<StepItems.Item> items,
        ItemContainer bank, IntFunction<String> nameOf, String[] withdraw, int[] slot, int y)
    {
        if (ids.isEmpty()) return y;
        line(box, y);
        text(box, title, TITLE_COLOR, X0, y + GAP, PER_ROW * DX, TITLE);
        y += GAP + TITLE;
        // A run's or teleport section's items go by kind, each kind on rows of its own (tablets, then rings...);
        // a step's items keep their order (the item, its tools, its materials)
        boolean byKind = items.stream().allMatch(it -> it == null);
        List<Integer> order = new ArrayList<>();
        for (int n = 0; n < ids.size(); n++) order.add(n);
        if (byKind) order.sort(Comparator.comparingInt(n -> BankFilter.kind(nameOf.apply(ids.get(n)))));
        int cell = 0, lastKind = -1;
        for (int n : order)
        {
            if (slot[0] >= before) break;
            int kind = byKind ? BankFilter.kind(nameOf.apply(ids.get(n))) : 0;
            if (lastKind >= 0 && kind != lastKind && cell % PER_ROW != 0) cell += PER_ROW - cell % PER_ROW;
            lastKind = kind;
            int x = X0 + (cell % PER_ROW) * DX, at = y + (cell / PER_ROW) * DY;
            Widget w = kids[slot[0]++];
            if (w == null) continue;
            show(w, ids.get(n), bank.count(ids.get(n)), nameOf.apply(ids.get(n)), withdraw);
            place(w, x, at, ITEM_W, ITEM_H);
            if (items.get(n) != null) count(box, items.get(n), x, at);
            cell++;
        }
        return y + Math.max(1, (cell + PER_ROW - 1) / PER_ROW) * DY;
    }

    /** Fills a bank slot with an item: the bank's count and withdraw actions, or faded with none when you have none. */
    private static void show(Widget w, int id, int qty, String name, String[] withdraw)
    {
        w.setItemId(id);
        w.setItemQuantity(qty);
        w.setItemQuantityMode(ItemQuantityMode.ALWAYS);
        w.setName("<col=ff9040>" + name + "</col>");
        w.clearActions();
        if (qty > 0)
        {
            for (int a = 0; a < withdraw.length; a++) if (withdraw[a] != null) w.setAction(a, withdraw[a]);
            w.setOpacity(SOLID);
        }
        else w.setOpacity(FADED);
        // Our order is fixed: no dragging items around
        w.setOnDragListener((JavaScriptCallback) e -> { });
        w.setOnDragCompleteListener((JavaScriptCallback) e -> { });
        w.setHidden(false);
    }

    /** The bank's scrollbar after our layout. Run after the bank's own script ends. */
    static void scrollbar(Client client)
    {
        Widget box = client.getWidget(InterfaceID.Bankmain.ITEMS);
        if (box != null) client.runScript(ScriptID.UPDATE_SCROLLBAR, InterfaceID.Bankmain.SCROLLBAR, InterfaceID.Bankmain.ITEMS, box.getScrollY());
    }

    /** Under an item: how many to take, or "74/225" in red with a cross when short; nothing once it's on you. */
    private static void count(Widget box, StepItems.Item it, int x, int y)
    {
        String n = BankFilter.bankCount(it);
        if (n == null) return;
        boolean isShort = BankFilter.isShort(it);
        text(box, n, isShort ? SHORT : ENOUGH, x, y + ITEM_H - 10, ITEM_W, 10);
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
