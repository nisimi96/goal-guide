package com.goalguide.guide;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import com.goalguide.facts.Runs;
import com.goalguide.items.ItemData;
import com.goalguide.items.ItemDb;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;

/**
 * The "GG" button on the bank's top bar: a toggle. On shows only the Now step's items and the items of
 * every run switched on, through RuneLite's Bank Tags filter, each under its own title; off shows the
 * whole bank. It only hides items from view. Client thread.
 */
public class BankButton
{
    static final String TAG = "goal guide";
    static final int SIZE = 25, Y = 5, X_FROM_RIGHT = 52;
    static final int ON = 0x7fd17f, OFF = 0xff981f;

    @Inject private Client client;
    @Inject private ItemManager itemManager;
    @Inject private TagManager tags;
    @Inject private BankTagsService bankTags;
    @Inject private ClientThread clientThread;

    private Widget button, text;
    private List<BankFilter.Section> sections = List.of();
    private Set<String> wanted = Set.of();
    /** Item name to a game id, for the faded missing items; built once per item data. */
    private Map<String, Integer> ids = Map.of();
    private ItemData idsFor;

    public void start()
    {
        tags.registerTag(TAG, id -> BankFilter.shows(nameOf(id), wanted));
    }

    public void stop()
    {
        if (isOn()) bankTags.closeBankTag();
        tags.unregisterTag(TAG);
        remove();
    }

    /** Each tick: the button follows the bank, and the filter follows the step. */
    public void tick()
    {
        Widget items = client.getWidget(InterfaceID.Bankmain.ITEMS);
        if (items == null || items.isHidden())
        {
            if (isOn()) bankTags.closeBankTag();
            remove();
            return;
        }
        if (button == null) add();
        // Counts follow every replan; the bank only lays out again when the sections change
        List<BankFilter.Section> now = BankFilter.sections(Guide.current(), Runs.switches());
        boolean changed = !now.equals(sections);
        sections = now;
        if (changed)
        {
            Set<String> all = new HashSet<>();
            for (BankFilter.Section s : now) all.addAll(s.keys);
            wanted = all;
            if (isOn()) open();
        }
        if (text != null) text.setTextColor(isOn() ? ON : OFF);
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
        if (isOn()) bankTags.closeBankTag();
        else if (wanted.isEmpty()) client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Goal Guide: no step items and no runs on.", null);
        else open();
    }

    private void open()
    {
        // Close first, so a new step lays the bank out again
        if (isOn()) bankTags.closeBankTag();
        bankTags.openBankTag(TAG, BankTagsService.OPTION_NO_LAYOUT);
    }

    private boolean isOn()
    {
        return TAG.equals(bankTags.getActiveTag());
    }

    /** The bank is about to lay out: our titles and counts go. */
    public void beforeLayout()
    {
        BankSections.clear(client);
    }

    /** The bank laid out: with the filter on, group its items under the sections. */
    public void afterLayout()
    {
        if (!isOn()) return;
        clientThread.invokeAtTickEnd(() -> BankSections.lay(client, sections, this::nameOf, this::idOf,
            () -> clientThread.invokeLater(() -> BankSections.scrollbar(client))));
    }

    private String nameOf(int id)
    {
        return itemManager.getItemComposition(itemManager.canonicalize(id)).getName();
    }

    private int idOf(String name)
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
