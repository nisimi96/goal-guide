package com.goalguide.guide;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * The guide on the game screen like Quest Helper's box), in sections with a title each
 * "divide it into sections"): Now (the step, how, timer, items), Next, Timed run, Between steps.
 * Off with the sidebar's "Show on screen" box.
 */
public final class GuideOverlay extends OverlayPanel
{
    static final Color NOW = new Color(0x7f, 0xd1, 0x7f), DIM = new Color(0xa0, 0xa0, 0xa0), REC = new Color(0x8f, 0xc8, 0xf0),
        WARN = new Color(0xf0, 0xc0, 0x70), BAD = new Color(0xf0, 0x8a, 0x78), HEAD = new Color(0xff, 0x98, 0x1f);
    private final BooleanSupplier shown;

    public GuideOverlay(BooleanSupplier shown)
    {
        this.shown = shown;
        setPosition(OverlayPosition.TOP_LEFT);
        // A path to the arrow's spot, drawn by the Shortest Path plugin (Plugin Hub) when it is on
        addMenuEntry(net.runelite.api.MenuAction.RUNELITE_OVERLAY, "Path to spot", "Shortest Path", e -> Waypoint.path());
    }

    @Override
    public Dimension render(Graphics2D g)
    {
        Guide gd = Guide.current();
        if (gd == null || !shown.getAsBoolean()) return null;
        panelComponent.setPreferredSize(new Dimension(230, 0));
        panelComponent.getChildren().add(TitleComponent.builder().text("Goal Guide").color(Color.WHITE).build());
        if (gd.now != null)
        {
            section("Do now", null);
            line(gd.now.what, NOW);
            timer();
            // The step's notes, one per line ("boost Crafting 40 with mushroom pie", ...)
            if (!gd.now.how.isEmpty())
                for (String part : gd.now.how.split(", (?=[a-z])")) line("• " + part, DIM);
            List<StepItems.Item> its = new ArrayList<>();
            int carried = 0;
            for (StepItems.Item i : gd.items)
            {
                if (i.has == StepItems.Has.USED) continue;   // Maybe used in the quest already: not worth the room
                its.add(i);
                if (i.has == StepItems.Has.CARRIED) carried++;
            }
            if (!its.isEmpty())
            {
                section("Items", carried + "/" + its.size() + " carried");
                for (StepItems.Item i : its) item(i);
            }
            getEntries(its);
            wikiEntry(gd.now.step);
            if (gd.now.quest != null) line("Open it in Quest Helper from the panel", DIM);
        }
        else { getEntries(List.of()); wikiEntry(null); }
        if (gd.next != null)
        {
            section("Then", null);
            line(gd.next.what, Color.WHITE);
        }
        if (gd.runWhat != null)
        {
            section("Timed run", null);
            line(gd.runWhat, REC);
            if (gd.runWhy != null) line(gd.runWhy, DIM);
        }
        if (!gd.filler.isEmpty())
        {
            section("Train between steps", null);
            for (String f : gd.filler) line(f, REC);
        }
        return super.render(g);
    }

    /** The missing items the right-click menu offers now. */
    private List<String> offered = List.of();

    /**
     * "click get ... a new small overlay box with the route for this item": a right-click "Get" line
     * for each missing item (and one in the bank, for someone who'd rather make it). Client thread.
     */
    private void getEntries(List<StepItems.Item> its)
    {
        List<String> want = new ArrayList<>();
        for (StepItems.Item i : its)
            if ((i.has == StepItems.Has.MISSING || i.has == StepItems.Has.STORED) && !i.name.isEmpty() && want.size() < 6) want.add(cap(i.name));
        if (want.equals(offered)) return;
        for (String o : offered) removeMenuEntry(net.runelite.api.MenuAction.RUNELITE_OVERLAY, "Get", o);
        for (String w : want) addMenuEntry(net.runelite.api.MenuAction.RUNELITE_OVERLAY, "Get", w, e -> GetBox.open(w));
        offered = want;
    }

    private String wikiShown;

    /** Wiki links: a right-click "Wiki" line for the step shown under Do now. Client thread. */
    private void wikiEntry(com.goalguide.goals.Planner.Step s)
    {
        String url = s == null ? null : com.goalguide.goals.StepDetail.wikiPage(s);
        if (java.util.Objects.equals(url, wikiShown)) return;
        removeMenuEntry(net.runelite.api.MenuAction.RUNELITE_OVERLAY, "Wiki", "this step");
        if (url != null) addMenuEntry(net.runelite.api.MenuAction.RUNELITE_OVERLAY, "Wiki", "this step", e -> net.runelite.client.util.LinkBrowser.browse(url));
        wikiShown = url;
    }

    private static String cap(String s)
    {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** A thin line, then the section's name small on the left (and a note on the right). */
    private void section(String label, String right)
    {
        panelComponent.getChildren().add(new Rule());
        panelComponent.getChildren().add(LineComponent.builder().left(label.toUpperCase(java.util.Locale.ROOT)).leftColor(HEAD)
            .leftFont(FontManager.getRunescapeSmallFont()).right(right == null ? "" : right).rightColor(DIM)
            .rightFont(FontManager.getRunescapeSmallFont()).build());
    }

    /** One item per row: its name on the left, where it is on the right. */
    private void item(StepItems.Item i)
    {
        String n = (i.need > 1 && !i.perHour ? i.need + " x " : "") + i.name;
        n = n.isEmpty() ? n : Character.toUpperCase(n.charAt(0)) + n.substring(1);
        Color c;
        String state;
        switch (i.has)
        {
            case CARRIED: c = NOW; state = "carried"; break;
            case STORED: c = WARN; state = String.join(", ", i.where); break;
            case REMINDER: c = DIM; state = "bring"; break;
            default: c = BAD; state = "missing"; break;
        }
        panelComponent.getChildren().add(LineComponent.builder().left(n).leftColor(c).right(state).rightColor(c).build());
    }

    private void line(String text, Color c)
    {
        panelComponent.getChildren().add(LineComponent.builder().left(text).leftColor(c).build());
    }

    /** A thin grey line across the box. */
    static final class Rule implements net.runelite.client.ui.overlay.components.LayoutableRenderableEntity
    {
        private final java.awt.Rectangle bounds = new java.awt.Rectangle();
        private java.awt.Point at = new java.awt.Point();
        private Dimension size = new Dimension(0, 0);

        @Override public Dimension render(Graphics2D g)
        {
            int w = Math.max(size.width, 1);
            g.setColor(new Color(0x8a, 0x8a, 0x8a, 160));
            g.fillRect(at.x, at.y + 4, w, 1);
            Dimension d = new Dimension(w, 8);
            bounds.setLocation(at);
            bounds.setSize(d);
            return d;
        }

        @Override public java.awt.Rectangle getBounds() { return bounds; }

        @Override public void setPreferredLocation(java.awt.Point p) { at = p; }

        @Override public void setPreferredSize(Dimension d) { size = d; }
    }

    /** "Timer 23:41 left" while it runs, "Time is up" when it ends (the panel has Next activity). */
    private void timer()
    {
        long now = System.currentTimeMillis();
        ActivityTimer.State s = Guide.TIMER.state(now);
        if (s == ActivityTimer.State.IDLE) return;
        String text = s == ActivityTimer.State.UP ? "Time is up: Next activity in the panel"
            : "Timer " + ActivityTimer.clock(Guide.TIMER.left(now)) + " left" + (s == ActivityTimer.State.PAUSED ? " (paused)" : "");
        line(text, s == ActivityTimer.State.UP ? WARN : Color.WHITE);
    }
}
