package com.goalguide.guide;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;

/**
 * "put a line between and give small text to tell which one is the section for": while the guide's
 * bank filter is on, the bank's items are grouped under a line and a small title per section (the step, then each
 * run), like Quest Helper's bank tab. Only moves the item pictures; nothing is withdrawn or moved in the bank.
 * Client thread.
 */
public final class BankSections
{
    private BankSections() { }

    // The bank's item grid, as Quest Helper lays its tab out
    static final int PER_ROW = 8, DX = 48, DY = 36, X0 = 51, GAP = 5, LINE = 2, TEXT = 15;
    static final int TITLE_COLOR = 0xe4d8a2;

    /** How many children the item box had before we added our lines and titles; -1 = none added. */
    private static int before = -1;

    /** The bank is about to lay out again: take our lines and titles away (they are the last children). */
    public static void clear(Client client)
    {
        int n = before;
        before = -1;
        if (n < 0) return;
        Widget box = client.getWidget(InterfaceID.Bankmain.ITEMS);
        Widget[] kids = box == null ? null : box.getChildren();
        if (kids == null || kids.length <= n) return;
        box.setChildren(Arrays.copyOf(kids, n));
        box.revalidate();
    }

    /** The bank finished laying out with the guide's filter on: group the items under titled lines. */
    public static void lay(Client client, Consumer<Runnable> later)
    {
        Widget box = client.getWidget(InterfaceID.Bankmain.ITEMS);
        Widget[] kids = box == null ? null : box.getChildren();
        List<String> secs = BankFilter.sections();
        if (kids == null || secs.isEmpty() || before >= 0) return;
        Map<String, List<Widget>> groups = new LinkedHashMap<>();
        for (String s : secs) groups.put(s, new ArrayList<>());
        for (Widget w : kids)
        {
            if (w == null || w.isSelfHidden()) continue;
            // The bank's own tab lines and "Tab 2" titles: hidden, our titles take their place
            if (w.getSpriteId() == SpriteID.TRADEBACKING_DARK || (w.getType() == WidgetType.TEXT && w.getText() != null && w.getText().startsWith("Tab")))
            {
                w.setHidden(true);
                continue;
            }
            int id = w.getItemId();
            if (id <= -1 || id == ItemID.BLANKOBJECT) continue;
            String s = BankFilter.section(id);
            groups.computeIfAbsent(s == null ? "Other" : s, k -> new ArrayList<>()).add(w);
        }
        before = kids.length;
        String step = BankFilter.label();
        Map<Integer, String[]> gone = BankFilter.missing();
        int y = 0;
        for (Map.Entry<String, List<Widget>> g : groups.entrySet())
        {
            List<Widget> items = g.getValue();
            boolean isStep = g.getKey().equals(step);
            int fakes = isStep ? gone.size() : 0;
            String title = title(g.getKey(), items.size() + fakes);
            line(box, y);
            text(box, title, y + GAP);
            y += GAP + TEXT;
            for (int i = 0; i < items.size(); i++)
            {
                Widget w = items.get(i);
                w.setOriginalX(X0 + (i % PER_ROW) * DX);
                w.setOriginalY(y + (i / PER_ROW) * DY);
                w.revalidate();
            }
            // What the step needs and the account has nowhere, faded with a red cross
            int i = items.size();
            if (isStep)
                for (Map.Entry<Integer, String[]> m : gone.entrySet())
                {
                    missingItem(client, box, m.getKey(), m.getValue()[0], m.getValue()[1], X0 + (i % PER_ROW) * DX, y + (i / PER_ROW) * DY);
                    i++;
                }
            y += ((i + PER_ROW - 1) / PER_ROW) * DY;
        }
        box.setScrollHeight(Math.max(y, box.getHeight()));
        int scroll = box.getScrollY();
        // The script engine is busy now: the scrollbar is fixed after
        later.accept(() -> client.runScript(ScriptID.UPDATE_SCROLLBAR, InterfaceID.Bankmain.SCROLLBAR, InterfaceID.Bankmain.ITEMS, scroll));
    }

    /** "Herb run", "This step: Get 2 x Ruby", with "(none in your bank)" when the bank holds nothing for it. */
    static String title(String section, int items)
    {
        boolean run = section.endsWith(" run");
        String t = run || section.equals("Other") ? section : "This step: " + section;
        return items == 0 ? t + " <col=a0a0a0>(none in your bank)</col>" : t;
    }

    /** A faded picture of a missing item with a red cross; its menu says where to get it. Moves nothing. */
    private static void missingItem(Client client, Widget box, int id, String name, String from, int x, int y)
    {
        Widget w = box.createChild(-1, WidgetType.GRAPHIC);
        w.setItemId(id);
        w.setItemQuantity(1);
        w.setItemQuantityMode(0);
        w.setOpacity(150);
        w.setOriginalX(x);
        w.setOriginalY(y);
        w.setOriginalWidth(36);
        w.setOriginalHeight(32);
        w.setName("<col=ff9040>" + name + "</col>");
        w.setHasListener(true);
        w.setAction(0, "Missing:");
        w.setOnOpListener((net.runelite.api.widgets.JavaScriptCallback) ev ->
            client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "", "Goal Guide: you have no " + name
                + (from == null ? "." : ". Get it: " + from + "."), null));
        w.revalidate();
        Widget c = box.createChild(-1, WidgetType.GRAPHIC);
        c.setSpriteId(SpriteID.Checkbox.CROSSED);
        c.setOriginalX(x + 22);
        c.setOriginalY(y);
        c.setOriginalWidth(13);
        c.setOriginalHeight(13);
        c.revalidate();
    }

    private static void line(Widget box, int y)
    {
        Widget w = box.createChild(-1, WidgetType.GRAPHIC);
        w.setSpriteId(SpriteID.TRADEBACKING_DARK);
        w.setOriginalX(X0);
        w.setOriginalY(y);
        w.setOriginalWidth(PER_ROW * DX);
        w.setOriginalHeight(LINE);
        w.revalidate();
    }

    private static void text(Widget box, String s, int y)
    {
        Widget w = box.createChild(-1, WidgetType.TEXT);
        w.setText(s);
        w.setFontId(FontID.PLAIN_11);
        w.setTextColor(TITLE_COLOR);
        w.setTextShadowed(true);
        w.setOriginalX(X0);
        w.setOriginalY(y);
        w.setOriginalWidth(PER_ROW * DX);
        w.setOriginalHeight(TEXT);
        w.revalidate();
    }
}
