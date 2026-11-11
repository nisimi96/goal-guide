package com.goalguide.guide;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.function.BooleanSupplier;
import net.runelite.api.MenuAction;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/** The small "Get it myself" box (see GetBox). Right-click it to close. */
public final class GetOverlay extends OverlayPanel
{
    private final BooleanSupplier shown;

    public GetOverlay(BooleanSupplier shown)
    {
        this.shown = shown;
        setPosition(OverlayPosition.TOP_LEFT);
        addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Close", "Get box", e -> GetBox.close());
    }

    @Override
    public Dimension render(Graphics2D g)
    {
        String item = GetBox.item;
        if (item == null || !shown.getAsBoolean()) return null;
        panelComponent.setPreferredSize(new Dimension(230, 0));
        panelComponent.getChildren().add(TitleComponent.builder().text("Get: " + item).color(GuideOverlay.HEAD).build());
        for (GetBox.Line l : GetBox.lines)
            panelComponent.getChildren().add(LineComponent.builder().left(l.text).leftColor(l.color).build());
        panelComponent.getChildren().add(LineComponent.builder().left("Right-click to close").leftColor(GuideOverlay.DIM).build());
        return super.render(g);
    }
}
