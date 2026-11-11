package com.goalguide.know;

import net.runelite.client.plugins.timetracking.SummaryState;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class RunTimersTest
{
    @Test
    public void readyTimesInWords()
    {
        assertEquals("ready", RunTimers.say(SummaryState.COMPLETED, 0, 100));
        assertEquals("ready", RunTimers.say(SummaryState.IN_PROGRESS, 90, 100));
        assertEquals("in 1h 12m", RunTimers.say(SummaryState.IN_PROGRESS, 100 + 72 * 60, 100));
        assertEquals("in 8m", RunTimers.say(SummaryState.IN_PROGRESS, 100 + 8 * 60, 100));
        assertEquals("nothing planted", RunTimers.say(SummaryState.EMPTY, 0, 100));
        assertEquals("not seen yet", RunTimers.say(SummaryState.UNKNOWN, 0, 100));
        assertEquals("Herb run", com.goalguide.facts.Runs.names().get(0));
    }
}
