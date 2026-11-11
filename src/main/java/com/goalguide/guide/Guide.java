package com.goalguide.guide;

import java.util.ArrayList;
import java.util.List;
import com.goalguide.goals.Planner;

/**
 * What to do now: the route's first step, the one after it, and skills to train in between. Made from the route on
 * every replan and read by the screen box and the sidebar.
 */
public final class Guide
{
    /** One line of the guide: the step, how, and the quest key for its wiki quick guide. */
    public static final class Line
    {
        public final String what, how, quest;
        /** The route step, null for a training line. */
        public final Planner.Step step;

        Line(String what, String how, String quest, Planner.Step step)
        {
            this.what = what;
            this.how = how;
            this.quest = quest;
            this.step = step;
        }
    }

    public final Line now, next;
    /** Skills to train between steps: "Ranged 40 to 47 (The Path of Glouphrie needs 47)". */
    public final List<String> filler = new ArrayList<>();
    /** The Now step's items (carried / in the bank / missing), filled in after the plan. */
    public volatile List<StepItems.Item> items = List.of();
    /** When the Now step gets a quest's items: one part per item to get, with what that part uses. */
    public volatile List<Part> parts = List.of();

    /** "Get 6 x Plank": the item to get and the items getting it uses. */
    public static final class Part
    {
        public final String title, item;
        public final List<StepItems.Item> items;

        public Part(String title, String item, List<StepItems.Item> items)
        {
            this.title = title;
            this.item = item;
            this.items = items;
        }
    }

    Guide(Line now, Line next)
    {
        this.now = now;
        this.next = next;
    }

    private static volatile Guide current;

    /** The guide from the last plan, null when there are no goals or no account yet. */
    public static Guide current() { return current; }

    public static void set(Guide g) { current = g; }

    /** The guide for a route. Null when the route has nothing left. */
    public static Guide of(Planner.Route r)
    {
        if (r == null || r.steps.isEmpty() && r.filler.isEmpty()) return null;
        Line now = r.steps.isEmpty() ? null : line(r.steps.get(0));
        Line next = r.steps.size() < 2 ? null : line(r.steps.get(1));
        if (now == null)
        {
            Planner.Filler f = r.filler.get(0);
            now = new Line("Train " + f.what(), f.how, null, null);
        }
        Guide g = new Guide(now, next);
        for (Planner.Filler f : r.filler)
        {
            if (g.filler.size() >= 3) break;
            g.filler.add(f.what() + (f.byWhen == null ? "" : " (" + f.byWhen + ")"));
        }
        return g;
    }

    static Line line(Planner.Step s)
    {
        String how = s.how == null ? "" : s.how;
        if (s.serves.size() > 1) how += (how.isEmpty() ? "" : ", ") + "moves " + String.join(" and ", s.serves);
        return new Line(s.verb() + s.what, how, s.kind == Planner.Kind.QUEST ? s.quest : null, s);
    }
}
