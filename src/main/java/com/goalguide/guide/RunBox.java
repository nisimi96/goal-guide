package com.goalguide.guide;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.goalguide.know.AccountState;

/**
 * The run box (USER 2026-11-11): while a run is on, its stops in run order, each ready, growing (time left),
 * diseased, dead, empty or done (planted during this run). It goes away when every stop is done or growing. Pure.
 */
public final class RunBox
{
    private RunBox() { }

    /** Bird houses take about 50 minutes to fill (the same number Time Tracking uses). */
    static final long BIRD_SECONDS = 50 * 60;

    public enum Mark { READY, DISEASED, DEAD, EMPTY, GROWING, DONE, UNSEEN }

    public static final class Row
    {
        public final String stop, text;
        public final Mark mark;

        Row(String stop, Mark mark, String text)
        {
            this.stop = stop;
            this.mark = mark;
            this.text = text;
        }

        @Override public String toString() { return stop + ": " + text; }
    }

    /** The open box: its title and rows. */
    public static final class Box
    {
        public final String title;
        public final List<Row> rows;

        public Box(String title, List<Row> rows)
        {
            this.title = title;
            this.rows = rows;
        }

        public int done()
        {
            int n = 0;
            for (Row r : rows) if (r.mark == Mark.DONE) n++;
            return n;
        }
    }

    /**
     * @param stops    stop -> Time Tracking's reading (for bird houses: the one bird house timer under any name)
     * @param order    the stops that are open and switched on, in run order
     * @param startMs  when the run began
     */
    public static List<Row> rows(String run, Map<String, AccountState.Timer> stops, List<String> order, long startMs, long nowSec)
    {
        List<Row> out = new ArrayList<>();
        boolean birds = AccountState.BIRDS.equals(run);
        for (String stop : order)
        {
            AccountState.Timer t = birds && stops.size() == 1 ? stops.values().iterator().next() : stops.get(stop);
            if (t == null) { out.add(new Row(stop, Mark.UNSEEN, "not seen yet")); continue; }
            String s = t.stateAt(nowSec);
            // planted during the run: growing, and that began after the run did
            boolean fresh = birds ? t.readyAt > 0 && (t.readyAt - BIRD_SECONDS) * 1000 >= startMs - 60_000 : t.since >= startMs;
            if (AccountState.Timer.GROWING.equals(s) && fresh) out.add(new Row(stop, Mark.DONE, "done"));
            else if (AccountState.Timer.READY.equals(s)) out.add(new Row(stop, Mark.READY, birds ? "full" : "ready"));
            else if (AccountState.Timer.DISEASED.equals(s)) out.add(new Row(stop, Mark.DISEASED, "diseased"));
            else if (AccountState.Timer.DEAD.equals(s)) out.add(new Row(stop, Mark.DEAD, "dead, clear it"));
            else if (AccountState.Timer.EMPTY.equals(s) || AccountState.Timer.BUILT.equals(s)) out.add(new Row(stop, Mark.EMPTY, birds ? "no seeds" : "empty"));
            else if (AccountState.Timer.GROWING.equals(s)) out.add(new Row(stop, Mark.GROWING, "growing, ready " + AccountState.when(t, nowSec)));
            else out.add(new Row(stop, Mark.UNSEEN, "not seen yet"));
        }
        return out;
    }

    /** Nothing left to do at any stop: every one is done or still growing. */
    public static boolean finished(List<Row> rows)
    {
        if (rows.isEmpty()) return true;
        for (Row r : rows) if (r.mark != Mark.DONE && r.mark != Mark.GROWING) return false;
        return true;
    }

    private static volatile Box current;

    public static Box current() { return current; }

    public static void set(Box b) { current = b; }
}
