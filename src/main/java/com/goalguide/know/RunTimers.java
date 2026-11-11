package com.goalguide.know;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import com.goalguide.facts.Runs;
import net.runelite.client.plugins.timetracking.SummaryState;
import net.runelite.client.plugins.timetracking.Tab;
import net.runelite.client.plugins.timetracking.farming.FarmingTracker;
import net.runelite.client.plugins.timetracking.hunter.BirdHouseTracker;

/**
 * When each run is ready, from RuneLite's own Time Tracking (its public FarmingTracker and BirdHouseTracker,
 * the same summary its panel shows). Nothing of Time Tracking's is changed. Client thread.
 */
@Singleton
public class RunTimers
{
    @Inject private FarmingTracker farming;
    @Inject private BirdHouseTracker birds;

    /** Each run's state in words: "ready", "in 1h 12m", "nothing planted", "not seen yet". */
    public Map<String, String> read()
    {
        farming.loadCompletionTimes();
        birds.loadFromConfig();
        long now = Instant.now().getEpochSecond();
        Map<String, String> out = new LinkedHashMap<>();
        for (String run : Runs.names())
        {
            Tab tab = tab(run);
            if (tab == null) continue;
            boolean bird = tab == Tab.BIRD_HOUSE;
            SummaryState s = bird ? birds.getSummary() : farming.getSummary(tab);
            long done = bird ? birds.getCompletionTime() : farming.getCompletionTime(tab);
            out.put(run, say(s, done, now));
        }
        return out;
    }

    static String say(SummaryState s, long done, long now)
    {
        if (s == null) return "not seen yet";
        switch (s)
        {
            case COMPLETED: return "ready";
            case IN_PROGRESS:
            case OCCUPIED: return done <= now ? "ready" : "in " + span(done - now);
            case EMPTY: return "nothing planted";
            default: return "not seen yet";
        }
    }

    /** "1h 12m", "8m", "under a minute". */
    static String span(long seconds)
    {
        long m = (seconds + 59) / 60, h = m / 60;
        if (m <= 0) return "under a minute";
        return h > 0 ? h + "h " + (m % 60) + "m" : m + "m";
    }

    /** The Time Tracking tab a run's patches are on. */
    static Tab tab(String run)
    {
        switch (run)
        {
            case "Herb run": return Tab.HERB;
            case "Tree run": return Tab.TREE;
            case "Fruit tree run": return Tab.FRUIT_TREE;
            case "Bird houses": return Tab.BIRD_HOUSE;
            default: return null;
        }
    }
}
