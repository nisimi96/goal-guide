package com.goalguide.guide;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.function.BooleanSupplier;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/** The run box on the game screen (USER 2026-11-11): the run's stops in order and how each one is. */
public final class RunOverlay extends OverlayPanel
{
    private final BooleanSupplier shown;

    public RunOverlay(BooleanSupplier shown)
    {
        this.shown = shown;
        setPosition(OverlayPosition.TOP_LEFT);
        // USER 2026-11-11: a path to the next stop, drawn by the Shortest Path plugin (Plugin Hub) when it is on
        addMenuEntry(net.runelite.api.MenuAction.RUNELITE_OVERLAY, "Path to next stop", "Shortest Path", e -> Waypoint.path());
    }

    @Override
    public Dimension render(Graphics2D g)
    {
        RunBox.Box b = RunBox.current();
        if (b == null || !shown.getAsBoolean()) return null;
        panelComponent.setPreferredSize(new Dimension(230, 0));
        panelComponent.getChildren().add(TitleComponent.builder().text(b.title + "  " + b.done() + "/" + b.rows.size()).color(Color.WHITE).build());
        for (RunBox.Row r : b.rows)
            panelComponent.getChildren().add(LineComponent.builder().left(r.stop).right(r.text).rightColor(color(r.mark)).build());
        return super.render(g);
    }

    static Color color(RunBox.Mark m)
    {
        switch (m)
        {
            case READY: return GuideOverlay.NOW;
            case DONE: return GuideOverlay.DIM;
            case DISEASED:
            case EMPTY: return GuideOverlay.WARN;
            case DEAD: return GuideOverlay.BAD;
            default: return Color.WHITE;
        }
    }
}
