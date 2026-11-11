package com.goalguide.guide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import com.goalguide.facts.Runs;
import com.goalguide.items.ItemData;
import com.goalguide.items.ItemDb;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.bank.BankSearch;

/**
 * The "GG" button on the bank's top bar: a toggle. On shows only the Now step's items and the items of
 * every run switched on, each under its own title (see BankSections); off shows the whole bank.
 * Withdrawing stays the bank's own: a click on one of our pictures goes to that item's real bank slot.
 * Client thread.
 */
public class BankButton
{
    static final int SIZE = 25, Y = 5, X_FROM_RIGHT = 52;
    static final int ON = 0x7fd17f, OFF = 0xff981f;

    @Inject private Client client;
    @Inject private ItemManager itemManager;
    @Inject private BankSearch bankSearch;
    @Inject private ClientThread clientThread;

    private Widget button, text;
    private boolean on;
    private List<BankFilter.Section> sections = List.of();
    /** Item name to a game id, for items not in the bank; built once per item data. */
    private Map<String, Integer> ids = Map.of();
    private ItemData idsFor;

    public void stop()
    {
        if (on) setOn(false);
        remove();
    }

    /** Each tick: the button follows the bank, and the sections follow the step. */
    public void tick()
    {
        Widget items = client.getWidget(InterfaceID.Bankmain.ITEMS);
        if (items == null || items.isHidden())
        {
            on = false;
            remove();
            return;
        }
        if (button == null) add();
        // Counts follow every replan; the bank only lays out again when the sections change
        List<BankFilter.Section> now = BankFilter.sections(Guide.current(), Runs.switches());
        boolean changed = !now.equals(sections);
        sections = now;
        if (changed && on) bankSearch.reset(true);
        if (text != null) text.setTextColor(on ? ON : OFF);
    }

    private void add()
    {
        Widget bar = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
        if (bar == null || bar.isHidden() || bar.getWidth() <= 0) return;
        int x = bar.getWidth() - X_FROM_RIGHT;
        button = bar.createChild(-1, WidgetType.GRAPHIC);
        place(button, x);
        button.setSpriteId(SpriteID.Miscgraphics3.UNKNOWN_BUTTON_SQUARE_SMALL);
        button.setHasListener(true);
        button.setName("<col=ff9040>Goal Guide</col>");
        button.setAction(0, "Toggle");
        button.setOnOpListener((JavaScriptCallback) e -> click());
        button.revalidate();
        text = bar.createChild(-1, WidgetType.TEXT);
        place(text, x);
        text.setText("GG");
        text.setFontId(FontID.PLAIN_11);
        text.setTextShadowed(true);
        text.setXTextAlignment(1);
        text.setYTextAlignment(1);
        text.revalidate();
    }

    private void click()
    {
        if (!on && sections.isEmpty()) client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Goal Guide: no step items and no runs on.", null);
        else setOn(!on);
    }

    private void setOn(boolean v)
    {
        on = v;
        bankSearch.reset(true);
    }

    /** The bank is about to lay out: our titles and counts go. */
    public void beforeLayout()
    {
        BankSections.clear(client);
    }

    /** The bank laid out: with the button on, show our sections. */
    public void afterLayout()
    {
        if (!on) return;
        clientThread.invokeAtTickEnd(() -> BankSections.lay(client, client.getItemContainer(InventoryID.BANK), sections,
            this::idOf, this::idsOf, this::nameOf, withdrawActions(),
            () -> clientThread.invokeLater(() -> BankSections.scrollbar(client))));
    }

    /** While on, the bank lays out as one list (as when searching), so no tab splits our sections. */
    public void afterSearching()
    {
        if (on) client.getIntStack()[client.getIntStackSize() - 1] = 1;
    }

    /**
     * A click on one of our pictures: the slot it sits in is not where the item lives in the bank, so point
     * the bank's own withdraw at the item's real slot.
     */
    public void clicked(MenuOptionClicked e)
    {
        if (!on || e.getParam1() != InterfaceID.Bankmain.ITEMS) return;
        MenuEntry m = e.getMenuEntry();
        Widget w = m.getWidget();
        ItemContainer bank = client.getItemContainer(InventoryID.BANK);
        if (w == null || w.getItemId() <= -1 || bank == null) return;
        int slot = bank.find(w.getItemId());
        if (slot > -1 && m.getParam0() != slot) m.setParam0(slot);
    }

    /** The bank's withdraw options by action index, following its quantity buttons. */
    private String[] withdrawActions()
    {
        int type = client.getVarbitValue(VarbitID.BANK_QUANTITY_TYPE), x = client.getVarbitValue(VarbitID.BANK_REQUESTEDQUANTITY);
        String[] a = new String[10];
        String[] first = {"1", "5", "10", String.valueOf(Math.max(1, x)), "All"};
        a[0] = "Withdraw-" + first[type >= 0 && type < first.length ? type : 0];
        if (type != 0) a[1] = "Withdraw-1";
        a[2] = "Withdraw-5";
        a[3] = "Withdraw-10";
        if (x > 0) a[4] = "Withdraw-" + x;
        a[5] = "Withdraw-X";
        a[6] = "Withdraw-All";
        a[7] = "Withdraw-All-but-1";
        a[9] = "Examine";
        return a;
    }

    private String nameOf(int id)
    {
        return itemManager.getItemComposition(itemManager.canonicalize(id)).getName();
    }

    /** A step item's id to show: the one of its kind you have most of in the bank, else its own. */
    private int idOf(String name)
    {
        List<Integer> held = heldOf(ItemData.key(name));
        return held.isEmpty() ? ownId(name) : held.get(0);
    }

    /** A run item's ids to show: each kind of it in the bank, else its own. */
    private List<Integer> idsOf(String key)
    {
        List<Integer> held = heldOf(key);
        int own = ownId(key);
        return !held.isEmpty() ? held : own > 0 ? List.of(own) : List.of();
    }

    /** Bank items of this kind, most first. */
    private List<Integer> heldOf(String key)
    {
        ItemContainer bank = client.getItemContainer(InventoryID.BANK);
        Map<Integer, Integer> n = new LinkedHashMap<>();
        if (bank != null)
            for (Item it : bank.getItems())
            {
                if (it.getId() <= 0 || it.getQuantity() <= 0) continue;
                if (StepItems.matches(ItemData.key(nameOf(it.getId())), key)) n.merge(it.getId(), it.getQuantity(), Integer::sum);
            }
        List<Integer> out = new ArrayList<>(n.keySet());
        out.sort((p, q) -> n.get(q) - n.get(p));
        return out;
    }

    private int ownId(String name)
    {
        ItemData d = ItemDb.get();
        if (d == null) return 0;
        if (d != idsFor)
        {
            Map<String, Integer> m = new HashMap<>();
            d.byId.forEach((id, n) -> m.merge(ItemData.key(n), id, Math::min));
            ids = m;
            idsFor = d;
        }
        return ids.getOrDefault(ItemData.key(name), 0);
    }

    private static void place(Widget w, int x)
    {
        w.setOriginalX(x);
        w.setOriginalY(Y);
        w.setOriginalWidth(SIZE);
        w.setOriginalHeight(SIZE);
    }

    private void remove()
    {
        if (button != null) button.setHidden(true);
        if (text != null) text.setHidden(true);
        button = null;
        text = null;
    }
}
