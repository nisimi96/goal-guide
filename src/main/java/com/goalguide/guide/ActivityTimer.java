package com.goalguide.guide;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * a timer for the activity you are on. Set a length per activity in the panel, Start counts down
 * (panel and on-screen box), a notification when time is up, then "Next activity" moves on and starts the next
 * one's timer; "Same again" restarts it, "Stop here" ends it. Times are passed in, so it is pure.
 */
public final class ActivityTimer
{
    public enum State { IDLE, RUNNING, PAUSED, UP }

    /** Length when none was set for an activity, in minutes. */
    public static final int DEFAULT_MIN = 30;

    private State state = State.IDLE;
    private String what;
    private int minutes;
    private long endsAt, leftAtPause;
    private boolean told;

    public synchronized void start(String what, int minutes, long nowMs)
    {
        this.what = what;
        this.minutes = Math.max(1, minutes);
        endsAt = nowMs + this.minutes * 60_000L;
        state = State.RUNNING;
        told = false;
    }

    public synchronized void pause(long nowMs)
    {
        if (state != State.RUNNING) return;
        leftAtPause = Math.max(0, endsAt - nowMs);
        state = State.PAUSED;
    }

    public synchronized void resume(long nowMs)
    {
        if (state != State.PAUSED) return;
        endsAt = nowMs + leftAtPause;
        state = State.RUNNING;
    }

    /** Adds minutes; on a timer that is up it runs again. */
    public synchronized void add(int min, long nowMs)
    {
        if (state == State.PAUSED) { leftAtPause += min * 60_000L; return; }
        if (state(nowMs) == State.UP) { endsAt = nowMs; state = State.RUNNING; told = false; }
        if (state == State.RUNNING) endsAt += min * 60_000L;
    }

    public synchronized void stop()
    {
        state = State.IDLE;
        what = null;
    }

    /** The state now: a running timer past its end is up. */
    public synchronized State state(long nowMs)
    {
        if (state == State.RUNNING && nowMs >= endsAt) state = State.UP;
        return state;
    }

    public synchronized long left(long nowMs)
    {
        State s = state(nowMs);
        return s == State.PAUSED ? leftAtPause : s == State.RUNNING ? endsAt - nowMs : 0;
    }

    /** True once, the first time it is seen up: for the notification. */
    public synchronized boolean takeUp(long nowMs)
    {
        if (state(nowMs) != State.UP || told) return false;
        told = true;
        return true;
    }

    public synchronized String what() { return what; }

    public synchronized int minutes() { return minutes; }

    /** "23:41", "1:02:03". */
    public static String clock(long ms)
    {
        long s = Math.max(0, (ms + 999) / 1000);
        long h = s / 3600, m = s / 60 % 60, sec = s % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, sec) : String.format("%d:%02d", m, sec);
    }

    /**
     * One length per activity, kept across level-ups: "Train Fishing 40 to 65 (Barbarian fishing)" and "Train
     * Fishing 52 to 65" share "train fishing".
     */
    public static String key(String what)
    {
        if (what == null) return "";
        String k = what.toLowerCase(Locale.ROOT).replaceAll("\\(.*?\\)", " ").replaceAll("[0-9,]+", " ").replaceAll("\\b(to|x)\\b", " ");
        return k.replaceAll("[=;]", " ").replaceAll("\\s+", " ").trim();
    }

    /** "train fishing=45;herb run=10". */
    public static Map<String, Integer> lengths(String text)
    {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (text == null) return out;
        for (String p : text.split(";"))
        {
            int eq = p.lastIndexOf('=');
            if (eq <= 0) continue;
            try { out.put(p.substring(0, eq).trim(), Math.max(1, Integer.parseInt(p.substring(eq + 1).trim()))); }
            catch (NumberFormatException ignored) { }
        }
        return out;
    }

    public static String lengthsText(Map<String, Integer> m)
    {
        StringBuilder b = new StringBuilder();
        m.forEach((k, v) -> b.append(b.length() == 0 ? "" : ";").append(k).append('=').append(v));
        return b.toString();
    }
}
