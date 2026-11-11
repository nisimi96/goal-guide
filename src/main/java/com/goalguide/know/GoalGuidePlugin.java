package com.goalguide.know;

import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

/**
 * Goal Guide: your goals as one route, with the next step in a box on the game screen.
 * It only reads the game (levels, quests, what you hold) and never clicks or types.
 * The account is saved as know-&lt;profile&gt;.json in the plugin's data folder.
 */
@lombok.extern.slf4j.Slf4j
@PluginDescriptor(
    name = "Goal Guide",
    description = "Turns your goals (quests, diaries, levels, items) into one route and shows the next step on screen",
    tags = {"guide", "planner", "quest", "goals", "diary", "ironman"},
    internalName = "goalguide",
    legacyDataDirectory = "goal-guide"
)
public class GoalGuidePlugin extends Plugin
{
    /** Full re-read of levels and quests (quest states run a client script each, so not every tick). */
    static final int ACCOUNT_EVERY = 100, POUCH_EVERY = 5;
    static final long SAVE_EVERY_MS = 30_000;
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private ConfigManager configManager;
    @Inject private ItemManager itemManager;
    @Inject private ScheduledExecutorService executor;
    @Inject private net.runelite.client.ui.ClientToolbar clientToolbar;
    @Inject private com.google.gson.Gson gson;
    @Inject private net.runelite.client.ui.overlay.OverlayManager overlayManager;
    private com.goalguide.goals.GoalsView goals;
    private com.goalguide.guide.GuideOverlay overlay;

    private AccountState state;
    private String profile;
    private int tick;
    private int fullReadAt = -1, potionsAt = -1, accountAt, pouchAt;
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
        // A main's Get steps show the Grand Exchange price (RuneLite's own prices)
        com.goalguide.goals.Prices.set(name ->
        {
            com.goalguide.items.ItemData d = com.goalguide.items.ItemDb.get();
            for (int id : d == null ? java.util.List.<Integer>of() : d.ids(name))
            {
                long p = itemManager.getItemPrice(id);
                if (p > 0) return (int) Math.min(p, Integer.MAX_VALUE);
            }
            return 0;
        });
        goals.start();
        overlay = new com.goalguide.guide.GuideOverlay(goals::guideOnScreen);
        overlayManager.add(overlay);
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
        overlayManager.remove(overlay);
        overlay = null;
        com.goalguide.goals.Prices.set(null);
        if (goals != null) { goals.stop(); goals = null; }
        clientThread.invoke(() ->
        {
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
        }
        if (full || tick - pouchAt >= POUCH_EVERY)
        {
            pouchAt = tick;
            change(KnowRead.runePouch(client, itemManager, state, now));
            change(KnowRead.follower(client, state, now));
        }
        if (potionsAt >= 0 && tick >= potionsAt)
        {
            potionsAt = -1;
            if (bankOpen()) change(KnowRead.potionStorage(client, itemManager, state, now));
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

    private boolean bankOpen()
    {
        Widget w = client.getWidget(net.runelite.api.gameval.InterfaceID.Bankmain.ITEMS);
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
        if (state == null || profile == null || !dirty) return;
        long t = System.currentTimeMillis();
        if (!now && t - savedAt < SAVE_EVERY_MS) return;
        dirty = false;
        savedAt = t;
        String key = profile, json = Know.toJson(state);
        executor.execute(() -> Know.write(key, json));
    }
}
