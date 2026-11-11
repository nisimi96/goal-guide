package com.goalguide.guide;

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
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;

/**
 * The "GG" button on the bank's top bar. A click shows only the Now step's items, through
 * RuneLite's Bank Tags filter; another click shows the whole bank. It only hides items from view.
 * Client thread.
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

    private Widget button, text;
    private Set<String> wanted = Set.of();

    public void start()
    {
        tags.registerTag(TAG, id -> BankFilter.shows(itemManager.getItemComposition(itemManager.canonicalize(id)).getName(), wanted));
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
        Set<String> now = now();
        if (!now.equals(wanted))
        {
            wanted = now;
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
        button.setAction(0, "Show step items");
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
        else if (wanted.isEmpty()) client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Goal Guide: this step uses no items.", null);
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

    private static Set<String> now()
    {
        Guide g = Guide.current();
        return g == null ? Set.of() : BankFilter.wanted(g.items);
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
