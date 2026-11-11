package com.goalguide.know;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;

/**
 * Goal Guide a plugin anyone can use, no automation): the goals panel, the route, the on-screen
 * Now / Next box and the Quest Helper button, on top of Know, which keeps one saved state per account with levels, quests, diaries, quest points, every
 * storage as last seen (bank, bag, worn, seed vault, potion storage, rune pouch, looting bag, kits, quiver, POH
 * costume room) and the herb and bird house timers. Item Roots and other readers read it through {@link Know}.
 *
 * Passive: it reads and saves, it never clicks or types. Saved as know-&lt;profile&gt;.json in the plugin's data folder.
 */
@lombok.extern.slf4j.Slf4j
@PluginDescriptor(
    name = "Goal Guide",
    description = "Plans your goals (quests, diaries, levels, stock) as one route for your account mode, and shows the next step on screen. Knows your levels, quests, storages and farming timers. Clicks nothing.",
    tags = {"iron", "ironman", "guide", "planner", "quest", "goals", "bank", "seed vault", "storage", "timers"},
    enabledByDefault = true,
    internalName = "goalguide",
    legacyDataDirectory = "goal-guide"
)
public class GoalGuidePlugin extends Plugin
{
    /** Full re-read of levels and quests (quest states run a client script each, so not every tick). */
    static final int ACCOUNT_EVERY = 100, POUCH_EVERY = 5, TIMERS_EVERY = 50;
    static final long SAVE_EVERY_MS = 30_000;
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private ConfigManager configManager;
    @Inject private ItemManager itemManager;
    @Inject private PluginManager pluginManager;
    @Inject private ScheduledExecutorService executor;
    @Inject private net.runelite.client.ui.ClientToolbar clientToolbar;
    @Inject private com.google.gson.Gson gson;
    @Inject private net.runelite.client.ui.overlay.OverlayManager overlayManager;
    @Inject private net.runelite.client.Notifier notifier;
    @Inject private net.runelite.client.ui.overlay.worldmap.WorldMapPointManager worldMapPointManager;
    @Inject private net.runelite.client.eventbus.EventBus eventBus;
    /** The goals sidebar and its route. */
    private com.goalguide.goals.GoalsView goals;

    private AccountState state;
    private String profile;
    private int tick;
    private int fullReadAt = -1, potionsAt = -1, accountAt, pouchAt, timersAt;
    private volatile boolean dirty;
    private boolean notify;
    private long savedAt;

    @Override
    protected void startUp()
    {
        try
        {
            Know.init(getPluginDirectory(), gson);
        }
        catch (java.io.IOException e)
        {
            log.warn("Goal Guide: no data folder, saving is off", e);
            Know.init(null, gson);
        }
        com.goalguide.goals.GoalsView.migrateConfig(configManager);
        goals = new com.goalguide.goals.GoalsView(configManager, clientToolbar, executor);
        goals.plugins(pluginManager);
        goals.hooks(clientThread::invokeLater, notifier::notify);
        // A main's Get steps show the Grand Exchange price first (RuneLite's own prices)
        com.goalguide.goals.Prices.set(name ->
        {
            for (int id : com.goalguide.guide.BankFilter.ids(com.goalguide.items.ItemDb.get(), name))
            {
                long p = itemManager.getItemPrice(id);
                if (p > 0) return (int) Math.min(p, Integer.MAX_VALUE);
            }
            return 0;
        });
        com.goalguide.guide.BankFilter.nameOf(id -> itemManager.getItemComposition(itemManager.canonicalize(id)).getName());
        goals.start();
        // The guide box on the game screen (Now, Next, what else is good now)
        com.goalguide.guide.Guide.show(overlayManager, goals::guideOnScreen);
        com.goalguide.guide.Waypoint.bus(eventBus);
        clientThread.invokeLater(() ->
        {
            if (client.getGameState() == GameState.LOGGED_IN)
            {
                switchTo(configManager.getRSProfileKey());
                fullReadAt = tick + 1;
            }
            else if (Know.state() == null)
            {
                executor.execute(() -> { AccountState s = Know.lastSaved(); if (s != null && Know.state() == null) Know.set(s, false); });
            }
        });
    }

    @Override
    protected void shutDown()
    {
        com.goalguide.guide.Guide.hide(overlayManager);
        com.goalguide.goals.Prices.set(null);
        if (goals != null) { goals.stop(); goals = null; }
        clientThread.invoke(() ->
        {
            com.goalguide.guide.BankFilter.close(pluginManager);
            com.goalguide.guide.Waypoint.clear(client, worldMapPointManager);
            com.goalguide.guide.Waypoint.set(null, null);
            removeBankButton();
            save(true);
            if (state != null) Know.set(state, false);
            state = null;
            profile = null;
        });
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged e)
    {
        if (e.getGameState() == GameState.LOGGED_IN)
        {
            switchTo(configManager.getRSProfileKey());
            // Varbits and containers arrive over the first ticks
            fullReadAt = tick + 3;
        }
        else if (e.getGameState() == GameState.LOGIN_SCREEN && state != null)
        {
            save(true);
            Know.set(state, false);
        }
    }

    @Subscribe
    public void onRuneScapeProfileChanged(RuneScapeProfileChanged e)
    {
        if (client.getGameState() == GameState.LOGGED_IN) switchTo(configManager.getRSProfileKey());
    }

    private void switchTo(String key)
    {
        if (key == null || key.equals(profile)) return;
        save(true);
        profile = key;
        state = Know.load(key);
        Know.set(state, true);
        fullReadAt = tick + 3;
    }

    @Subscribe
    public void onGameTick(GameTick e)
    {
        tick++;
        if (state == null || client.getGameState() != GameState.LOGGED_IN) return;
        // An arrow and a map pin to where the guide sends you next (a run's next stop)
        com.goalguide.guide.Waypoint.tick(client, worldMapPointManager, goals != null && goals.guideOnScreen());
        long now = System.currentTimeMillis();
        boolean full = fullReadAt >= 0 && tick >= fullReadAt;
        if (full)
        {
            fullReadAt = -1;
            if (client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null) state.name = client.getLocalPlayer().getName();
            change(KnowRead.carried(client, itemManager, state, now));
            if (client.getItemContainer(InventoryID.BANK) != null)
                change(KnowRead.container(itemManager, state, InventoryID.BANK, client.getItemContainer(InventoryID.BANK), now));
        }
        if (full || tick - accountAt >= ACCOUNT_EVERY)
        {
            accountAt = tick;
            change(KnowRead.account(client, state, now));
            change(KnowRead.abilities(client, configManager, state));
        }
        if (full || tick - pouchAt >= POUCH_EVERY)
        {
            pouchAt = tick;
            change(KnowRead.runePouch(client, itemManager, state, now));
            change(KnowRead.follower(client, state, now));
            // Each patch for the run box: often, so a stop shows done soon after it is planted
            for (Map.Entry<String, Map<String, AccountState.Timer>> r : Timers.stops(pluginManager, now).entrySet())
                change(state.putStops(r.getKey(), r.getValue()));
        }
        if (full || tick - timersAt >= TIMERS_EVERY)
        {
            timersAt = tick;
            for (Map.Entry<String, AccountState.Timer> t : Timers.read(pluginManager, configManager, now).entrySet())
                change(state.putTimer(t.getKey(), t.getValue()));
        }
        bankButtonTick();
        if (potionsAt >= 0 && tick >= potionsAt)
        {
            potionsAt = -1;
            if (bankOpen())
            {
                change(KnowRead.potionStorage(client, itemManager, state, now));
                change(KnowRead.bankSpace(client, state));
            }
        }
        if (notify)
        {
            notify = false;
            Know.changed();
        }
        save(false);
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged e)
    {
        if (state == null) return;
        change(KnowRead.container(itemManager, state, e.getContainerId(), e.getItemContainer(), System.currentTimeMillis()));
        if (e.getContainerId() == InventoryID.BANK) potionsAt = tick + 1;
    }

    @Subscribe
    public void onStatChanged(StatChanged e)
    {
        if (state == null || e.getSkill() == null) return;
        change(state.putLevel(e.getSkill().name(), client.getRealSkillLevel(e.getSkill()), e.getXp()));
    }

    // ---- the bank button like Quest Helper's): shows only what the step or a run uses

    private Widget bankButton, bankText;
    /** The choices the button was made with: made again when they change while the bank is open. */
    private String bankShown;

    @Subscribe
    public void onWidgetLoaded(net.runelite.api.events.WidgetLoaded e)
    {
        if (e.getGroupId() == InterfaceID.BANKMAIN) { bankShown = null; clientThread.invokeLater(this::addBankButton); }
    }

    /** While the filter is on, the bank's items sit under a line and a title per section. */
    @Subscribe
    public void onScriptPreFired(net.runelite.api.events.ScriptPreFired e)
    {
        if (e.getScriptId() == net.runelite.api.ScriptID.BANKMAIN_FINISHBUILDING) com.goalguide.guide.BankSections.clear(client);
    }

    @Subscribe
    public void onScriptPostFired(net.runelite.api.events.ScriptPostFired e)
    {
        if (e.getScriptId() != net.runelite.api.ScriptID.BANKMAIN_FINISHBUILDING || !com.goalguide.guide.BankFilter.isOn(pluginManager)) return;
        clientThread.invokeAtTickEnd(() -> com.goalguide.guide.BankSections.lay(client, clientThread::invokeLater));
    }

    /** Each tick: the button follows the guide (a new step, a run switched on) and shows on or off, while the bank is open. */
    private void bankButtonTick()
    {
        if (!bankOpen()) { if (bankButton != null) removeBankButton(); bankShown = null; return; }
        String sig = String.join("|", com.goalguide.guide.BankFilter.choices());
        if (!sig.equals(bankShown) || bankButton == null) addBankButton();
        if (bankText != null) bankText.setTextColor(com.goalguide.guide.BankFilter.isOn(pluginManager) ? 0x7fd17f : 0xff981f);
    }

    /**
     * a toggle on the title bar, right of Quest Helper's button (x 408, 25 wide). On shows the step's
     * items and every run switched on in the panel; off shows the whole bank. Right-click shows one of them alone.
     */
    private void addBankButton()
    {
        removeBankButton();
        Widget parent = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
        List<String> choices = com.goalguide.guide.BankFilter.choices();
        bankShown = String.join("|", choices);
        if (parent == null || parent.isHidden() || choices.isEmpty()) return;
        int[] at = bankButtonSpot(parent);
        if (at == null) { bankShown = null; return; }   // The bank is not laid out yet: next tick
        Widget w = parent.createChild(-1, net.runelite.api.widgets.WidgetType.GRAPHIC);
        place(w, at);
        w.setSpriteId(net.runelite.api.gameval.SpriteID.Miscgraphics3.UNKNOWN_BUTTON_SQUARE_SMALL);
        w.setHasListener(true);
        w.setName("<col=ff9040>Goal Guide</col>");
        w.setAction(0, "Toggle items for");
        // One at a time: the first choice is the toggle's (step and runs together)
        List<String> alone = choices.subList(1, Math.min(choices.size(), 8));
        for (int i = 0; i < alone.size(); i++) w.setAction(i + 1, "Show only " + alone.get(i) + " for");
        int all = alone.size() + 1;
        w.setAction(all, "Show all items");
        w.setOnOpListener((net.runelite.api.widgets.JavaScriptCallback) ev ->
        {
            int op = ev.getOp() - 1;
            String why;
            if (op <= 0) why = com.goalguide.guide.BankFilter.toggle(pluginManager);
            else if (op >= all) { com.goalguide.guide.BankFilter.close(pluginManager); why = null; }
            else why = com.goalguide.guide.BankFilter.open(pluginManager, alone.get(op - 1));
            if (why != null) client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "", "Goal Guide: " + why, null);
        });
        w.revalidate();
        Widget t = parent.createChild(-1, net.runelite.api.widgets.WidgetType.TEXT);
        place(t, at);
        t.setText("GG");
        t.setTextColor(com.goalguide.guide.BankFilter.isOn(pluginManager) ? 0x7fd17f : 0xff981f);
        t.setFontId(net.runelite.api.FontID.PLAIN_11);
        t.setTextShadowed(true);
        t.setXTextAlignment(1);
        t.setYTextAlignment(1);
        t.revalidate();
        bankButton = w;
        bankText = t;
    }

    /** Quest Helper's bank button: x 408, y 5, 25 square (its QuestBankTabInterface). */
    static final int QH_X = 408, BUTTON = 25, BUTTON_Y = 5;

    /**
     * Right of Quest Helper's button when that is clear of the bank's own top-right buttons, else left of it.
     * {x, y, w, h} in the bank; null when the bank is not laid out yet.
     */
    private int[] bankButtonSpot(Widget parent)
    {
        if (parent.getWidth() <= 0) return null;
        net.runelite.api.Point p = parent.getCanvasLocation();
        if (p == null) return null;
        List<int[]> taken = new java.util.ArrayList<>();
        taken.add(new int[]{QH_X, QH_X + BUTTON});
        for (int id : new int[]{InterfaceID.Bankmain.MENU_BUTTON, InterfaceID.Bankmain.WORNITEMS_BUTTON})
        {
            Widget b = client.getWidget(id);
            net.runelite.api.Point q = b == null || b.isHidden() ? null : b.getCanvasLocation();
            if (q != null && b.getWidth() > 0 && q.getY() - p.getY() < BUTTON_Y + BUTTON) taken.add(new int[]{q.getX() - p.getX(), q.getX() - p.getX() + b.getWidth()});
        }
        // The close cross takes the last ~28 px of the title bar
        taken.add(new int[]{parent.getWidth() - 28, parent.getWidth()});
        for (int x : new int[]{QH_X + BUTTON + 3, QH_X - BUTTON - 3, QH_X - 2 * (BUTTON + 3)})
        {
            boolean free = x >= 0;
            for (int[] r : taken) free &= x + BUTTON <= r[0] || x >= r[1];
            if (free) return new int[]{x, BUTTON_Y, BUTTON, BUTTON};
        }
        return new int[]{QH_X - BUTTON - 3, BUTTON_Y, BUTTON, BUTTON};
    }

    private static void place(Widget w, int[] at)
    {
        w.setOriginalX(at[0]);
        w.setOriginalY(at[1]);
        w.setOriginalWidth(at[2]);
        w.setOriginalHeight(at[3]);
    }

    private void removeBankButton()
    {
        if (bankButton != null) bankButton.setHidden(true);
        if (bankText != null) bankText.setHidden(true);
        bankButton = null;
        bankText = null;
    }

    private boolean bankOpen()
    {
        Widget w = client.getWidget(InterfaceID.Bankmain.ITEMS);
        return w != null && !w.isHidden();
    }

    private void change(boolean changed)
    {
        if (!changed) return;
        dirty = true;
        notify = true;
    }

    /** Saves when something changed, at most every 30 s unless {@code now}. JSON is made here, the file written off the client thread. */
    private void save(boolean now)
    {
        if (Know.takeTouched()) dirty = true;
        if (state == null || profile == null || !dirty) return;
        long t = System.currentTimeMillis();
        if (!now && t - savedAt < SAVE_EVERY_MS) return;
        dirty = false;
        savedAt = t;
        String key = profile, json = Know.toJson(state);
        executor.execute(() -> Know.write(key, json));
    }
}
