package com.goalguide.guide;

import java.util.ArrayList;
import java.util.List;
import com.goalguide.decide.Decide;
import com.goalguide.goals.Planner;

/**
 * What the route says to do now, the step under it,
 * and recommendations to fill the time (a timed run that is ready, the filler skills). Made from the route
 * and the Decide pick on every replan; read by the on-screen box and the sidebar. Pure.
 */
public final class Guide
{
    /** One line of the guide: the step, how, and the quest key when Quest Helper can open it. */
    public static final class Line
    {
        public final String what, how, quest;
        /** The route step, null for a filler line. */
        public final Planner.Step step;

        Line(String what, String how, String quest)
        {
            this(what, how, quest, null);
        }

        Line(String what, String how, String quest, Planner.Step step)
        {
            this.what = what;
            this.how = how;
            this.quest = quest;
            this.step = step;
        }
    }

    public final Line now, next;
    public final List<String> recommend;
    /** A timed run that is grown or comes ready in this sitting: what ("Herb run (Falador, ...)") and why, or null. */
    public String runWhat, runWhy;
    /** Skills to train between steps: "Ranged 40 to 47 (The Path of Glouphrie needs 47)". */
    public final List<String> filler = new ArrayList<>();
    /** The Now step's item list (carried / in the bank / missing), filled by the panel. */
    public volatile List<StepItems.Item> items = List.of();
    /** Steps skipped with "Next activity" this session, so Now moved past them. */
    public final int skipped;

    Guide(Line now, Line next, List<String> recommend, int skipped)
    {
        this.now = now;
        this.next = next;
        this.recommend = recommend;
        this.skipped = skipped;
    }

    /** The activity timer one for the client. */
    public static final ActivityTimer TIMER = new ActivityTimer();
    private static RunOverlay runOverlay;
    private static GetOverlay getOverlay;

    private static volatile Guide current;
    private static GuideOverlay overlay;

    /** Puts the guide box on the game screen; {@code shown} is the sidebar's "Show on screen" box. */
    public static synchronized void show(net.runelite.client.ui.overlay.OverlayManager om, java.util.function.BooleanSupplier shown)
    {
        hide(om);
        overlay = new GuideOverlay(shown);
        om.add(overlay);
        runOverlay = new RunOverlay(shown);
        om.add(runOverlay);
        getOverlay = new GetOverlay(shown);
        om.add(getOverlay);
    }

    public static synchronized void hide(net.runelite.client.ui.overlay.OverlayManager om)
    {
        if (overlay != null) om.remove(overlay);
        if (runOverlay != null) om.remove(runOverlay);
        if (getOverlay != null) om.remove(getOverlay);
        overlay = null;
        runOverlay = null;
        getOverlay = null;
        GetBox.close();
    }

    /** The guide from the last plan, null when there are no goals or no account yet. */
    public static Guide current() { return current; }

    public static void set(Guide g) { current = g; }

    /** The guide for a route and the Decide pick. Null when the route has nothing left. */
    public static Guide of(Planner.Route r, Decide.Decision d)
    {
        return of(r, d, java.util.Set.of());
    }

    /**
     * The guide, with the steps moved past by "Next activity" ({@code skipped}, by their text) left out of Now and
     * Next. When every step was moved past, it starts from the top again.
     */
    public static Guide of(Planner.Route r, Decide.Decision d, java.util.Set<String> skipped)
    {
        if (r == null || r.steps.isEmpty() && r.filler.isEmpty()) return null;
        List<Planner.Step> left = new ArrayList<>();
        for (Planner.Step s : r.steps) if (!skipped.contains(ActivityTimer.key(line(s).what))) left.add(s);
        int moved = r.steps.size() - left.size();
        if (left.isEmpty()) { left = r.steps; moved = 0; }
        Line now = left.isEmpty() ? null : line(left.get(0));
        Line next = left.size() < 2 ? null : line(left.get(1));
        List<String> rec = new ArrayList<>();
        // A timed run going to waste or ready beats everything (Decide ranks them first)
        Decide.Pick p = d == null ? null : d.now();
        boolean run = p != null && (p.rank == Decide.Rank.WASTE || p.rank == Decide.Rank.READY);
        if (run) rec.add(p.say());
        List<String> fill = new ArrayList<>();
        for (Planner.Filler f : r.filler)
        {
            if (fill.size() >= 3) break;
            fill.add(f.what() + (f.byWhen == null ? "" : " (" + f.byWhen + ")"));
            if (rec.size() < 3) rec.add("Between steps: " + fill.get(fill.size() - 1));
        }
        if (now == null && !r.filler.isEmpty())
        {
            Planner.Filler f = r.filler.get(0);
            now = new Line("Train " + f.what(), f.how, null);
        }
        Guide g = new Guide(now, next, rec, moved);
        if (run)
        {
            g.runWhat = p.what;
            g.runWhy = p.why;
        }
        g.filler.addAll(fill);
        return g;
    }

    static Line line(Planner.Step s)
    {
        String verb = s.kind == Planner.Kind.TRAIN ? "Train " : s.kind == Planner.Kind.GET ? "Get " : s.kind == Planner.Kind.STOCK ? "Stock " : "";
        String how = s.how == null ? "" : s.how;
        if (s.serves.size() > 1) how += (how.isEmpty() ? "" : ", ") + "moves " + String.join(" and ", s.serves);
        return new Line(verb + s.what, how, s.kind == Planner.Kind.QUEST ? s.quest : null, s);
    }
}
