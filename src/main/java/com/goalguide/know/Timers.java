package com.goalguide.know;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.timetracking.SummaryState;
import net.runelite.client.plugins.timetracking.Tab;
import net.runelite.client.plugins.timetracking.TimeTrackingConfig;
import net.runelite.client.plugins.timetracking.TimeTrackingPlugin;
import net.runelite.client.plugins.timetracking.farming.FarmingTracker;

/**
 * Farming and bird house timers, from RuneLite's own Time Tracking plugin (it already watches every patch and bird
 * house, so we never re-implement its growth maths).
 *
 * Bird houses: read straight from Time Tracking's saved values, so they work even with that plugin off.
 * Patches: asked from Time Tracking's farming tracker while it runs; with it off the last saved reading stays.
 */
@Slf4j
final class Timers
{
    private Timers() { }

    /** Average time to catch 10 birds, the same number Time Tracking uses. */
    static final long BIRD_HOUSE_SECONDS = 50 * 60;
    static final int[] BIRD_HOUSE_VARPS = {
        VarPlayerID.BIRDHOUSE_TRANSMIT_A, VarPlayerID.BIRDHOUSE_TRANSMIT_B, VarPlayerID.BIRDHOUSE_TRANSMIT_C, VarPlayerID.BIRDHOUSE_TRANSMIT_D};

    /** All timers Time Tracking knows for the logged-in account. Client thread. */
    static Map<String, AccountState.Timer> read(PluginManager plugins, ConfigManager config, long nowMs)
    {
        Map<String, AccountState.Timer> out = new LinkedHashMap<>();
        List<String> saved = new ArrayList<>();
        for (int varp : BIRD_HOUSE_VARPS) saved.add(config.getRSProfileConfiguration(TimeTrackingConfig.CONFIG_GROUP, TimeTrackingConfig.BIRD_HOUSE + "." + varp));
        out.put(AccountState.BIRDS, birdHouses(saved, nowMs));
        FarmingTracker farming = farmingTracker(plugins);
        if (farming != null)
        {
            for (Tab tab : Tab.FARMING_TABS)
            {
                try
                {
                    out.put(name(tab), patch(farming.getSummary(tab), farming.getCompletionTime(tab), nowMs));
                }
                catch (RuntimeException e)
                {
                    log.debug("Know: no {} timer", tab, e);
                }
            }
        }
        return out;
    }

    /** The runs whose stops the run box shows (Time Tracking tabs). */
    static final List<Tab> STOP_TABS = List.of(Tab.HERB, Tab.TREE, Tab.FRUIT_TREE);

    /** Time Tracking's region names that differ from the stop names the guide uses. */
    static final Map<String, String> STOP_NAMES = Map.of("Kourend", "Hosidius", "Morytania", "Port Phasmatys", "Harmony", "Harmony Island");

    /**
     * Each herb, tree and fruit tree patch as Time Tracking predicts it, by run and stop. Empty while Time Tracking
     * is off. Its world and predictions are package-private, so they are read by reflection. Client thread.
     */
    static Map<String, Map<String, AccountState.Timer>> stops(PluginManager plugins, long nowMs)
    {
        Map<String, Map<String, AccountState.Timer>> out = new LinkedHashMap<>();
        FarmingTracker farming = farmingTracker(plugins);
        if (farming == null) return out;
        try
        {
            Field wf = FarmingTracker.class.getDeclaredField("farmingWorld");
            wf.setAccessible(true);
            Object world = wf.get(farming);
            java.lang.reflect.Method tabs = world.getClass().getDeclaredMethod("getTabs");
            tabs.setAccessible(true);
            Map<?, ?> byTab = (Map<?, ?>) tabs.invoke(world);
            for (Tab tab : STOP_TABS)
            {
                Object set = byTab.get(tab);
                if (!(set instanceof Iterable)) continue;
                Map<String, AccountState.Timer> run = new LinkedHashMap<>();
                for (Object patch : (Iterable<?>) set)
                {
                    Object region = call(patch, "getRegion");
                    String name = region == null ? null : (String) call(region, "getName");
                    Object pred = farming.getClass().getMethod("predictPatch", patch.getClass()).invoke(farming, patch);
                    if (name == null || pred == null) continue;
                    Object crop = call(pred, "getCropState");
                    long done = (Long) call(pred, "getDoneEstimate");
                    AccountState.Timer t = stop(crop == null ? null : crop.toString(), done, nowMs);
                    run.merge(STOP_NAMES.getOrDefault(name, name), t, Timers::worse);
                }
                out.put(name(tab), run);
            }
        }
        catch (ReflectiveOperationException | RuntimeException e)
        {
            log.debug("Know: Time Tracking's patches are not reachable", e);
        }
        return out;
    }

    private static Object call(Object o, String method) throws ReflectiveOperationException
    {
        java.lang.reflect.Method m = o.getClass().getMethod(method);
        m.setAccessible(true);
        return m.invoke(o);
    }

    /** One patch from Time Tracking's crop state (HARVESTABLE, GROWING, DISEASED, DEAD, EMPTY, FILLING). */
    static AccountState.Timer stop(String crop, long doneSec, long nowMs)
    {
        if (crop == null) return new AccountState.Timer(AccountState.Timer.UNKNOWN, -1, nowMs);
        switch (crop)
        {
            case "HARVESTABLE": return new AccountState.Timer(AccountState.Timer.READY, 0, nowMs);
            case "GROWING":
            case "FILLING": return new AccountState.Timer(AccountState.Timer.GROWING, doneSec, nowMs);
            case "DISEASED": return new AccountState.Timer(AccountState.Timer.DISEASED, -1, nowMs);
            case "DEAD": return new AccountState.Timer(AccountState.Timer.DEAD, -1, nowMs);
            case "EMPTY": return new AccountState.Timer(AccountState.Timer.EMPTY, -1, nowMs);
            default: return new AccountState.Timer(AccountState.Timer.UNKNOWN, -1, nowMs);
        }
    }

    /** Two patches at one stop (the Farming Guild has more than one): the one that needs the player most. */
    static AccountState.Timer worse(AccountState.Timer a, AccountState.Timer b)
    {
        List<String> order = List.of(AccountState.Timer.DEAD, AccountState.Timer.DISEASED, AccountState.Timer.READY, AccountState.Timer.EMPTY,
            AccountState.Timer.GROWING, AccountState.Timer.UNKNOWN);
        int x = order.indexOf(a.state), y = order.indexOf(b.state);
        if (x != y) return x >= 0 && (y < 0 || x < y) ? a : b;
        return a.readyAt >= b.readyAt ? a : b;
    }

    /** "herb patches" for Tab.HERB, "fruit tree patches" for Tab.FRUIT_TREE. */
    static String name(Tab tab)
    {
        return tab.getName().toLowerCase(Locale.ROOT);
    }

    static AccountState.Timer patch(SummaryState s, long completionSec, long nowMs)
    {
        switch (s)
        {
            case COMPLETED: return new AccountState.Timer(AccountState.Timer.READY, 0, nowMs);
            case IN_PROGRESS: return new AccountState.Timer(AccountState.Timer.GROWING, completionSec, nowMs);
            case EMPTY: return new AccountState.Timer(AccountState.Timer.EMPTY, -1, nowMs);
            default: return new AccountState.Timer(AccountState.Timer.UNKNOWN, -1, nowMs);
        }
    }

    /**
     * The four bird houses from Time Tracking's saved "varp:epochSeconds" values (null = never seen). Same rules as
     * Time Tracking: varp 0 = empty, a multiple of 3 = seeded (ready 50 min after it was seen seeded), else built.
     */
    static AccountState.Timer birdHouses(List<String> saved, long nowMs)
    {
        boolean any = false, allEmpty = true, seeded = false;
        long ready = 0;
        for (String v : saved)
        {
            if (v == null) continue;
            String[] p = v.split(":");
            if (p.length != 2) continue;
            int varp;
            long at;
            try
            {
                varp = Integer.parseInt(p[0].trim());
                at = Long.parseLong(p[1].trim());
            }
            catch (NumberFormatException e)
            {
                continue;
            }
            if (varp < 0) continue;
            any = true;
            if (varp == 0) continue;
            allEmpty = false;
            if (varp % 3 == 0)
            {
                seeded = true;
                ready = Math.max(ready, at + BIRD_HOUSE_SECONDS);
            }
        }
        if (!any) return new AccountState.Timer(AccountState.Timer.UNKNOWN, -1, nowMs);
        if (allEmpty) return new AccountState.Timer(AccountState.Timer.EMPTY, -1, nowMs);
        if (!seeded) return new AccountState.Timer(AccountState.Timer.BUILT, -1, nowMs);
        return ready <= nowMs / 1000 ? new AccountState.Timer(AccountState.Timer.READY, 0, nowMs)
            : new AccountState.Timer(AccountState.Timer.GROWING, ready, nowMs);
    }

    /** Time Tracking's farming tracker while that plugin runs, else null. It is private to the plugin, so it is read by reflection. */
    static FarmingTracker farmingTracker(PluginManager plugins)
    {
        for (Plugin p : plugins.getPlugins())
        {
            if (!(p instanceof TimeTrackingPlugin) || !plugins.isPluginActive(p)) continue;
            try
            {
                Field f = TimeTrackingPlugin.class.getDeclaredField("farmingTracker");
                f.setAccessible(true);
                return (FarmingTracker) f.get(p);
            }
            catch (ReflectiveOperationException | RuntimeException e)
            {
                log.debug("Know: Time Tracking's farming tracker is not reachable", e);
                return null;
            }
        }
        return null;
    }
}
