package com.goalguide.guide;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import com.goalguide.goals.Planner;
import com.goalguide.goals.StepDetail;
import com.goalguide.goals.Text;
import net.runelite.api.MenuAction;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/** The guide on the game screen: Do now, Items, Then, Train between steps. Hidden with the sidebar checkbox. */
public final class GuideOverlay extends OverlayPanel
{
    private static final Color GREEN = Color.decode(Text.GREEN), GREY = Color.decode(Text.GREY), BLUE = Color.decode(Text.BLUE),
        YELLOW = Color.decode(Text.YELLOW), RED = Color.decode(Text.RED), ORANGE = Color.decode(Text.ORANGE);
    private final BooleanSupplier shown;
    private String wikiShown;

    public GuideOverlay(BooleanSupplier shown)
    {
        this.shown = shown;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D g)
    {
        Guide gd = Guide.current();
        if (gd == null || !shown.getAsBoolean()) return null;
        panelComponent.setPreferredSize(new Dimension(230, 0));
        panelComponent.getChildren().add(TitleComponent.builder().text("Goal Guide").color(Color.WHITE).build());
        wikiEntry(gd.now == null ? null : gd.now.step);
        if (gd.now != null)
        {
            section("Do now", null);
            line(gd.now.what, GREEN);
            if (!gd.now.how.isEmpty())
                for (String part : gd.now.how.split(", (?=[a-z])")) line("• " + part, GREY);
            List<StepItems.Item> its = new ArrayList<>();
            int carried = 0;
            for (StepItems.Item i : gd.items)
            {
                if (i.has == StepItems.Has.USED) continue;
                its.add(i);
                if (i.has == StepItems.Has.CARRIED) carried++;
            }
            if (!its.isEmpty())
            {
                section("Items", carried + "/" + its.size() + " on you");
                for (StepItems.Item i : its) item(i);
            }
            if (wikiShown != null) line("Shift + right-click for the wiki", GREY);
        }
        if (gd.next != null)
        {
            section("Then", null);
            line(gd.next.what, Color.WHITE);
        }
        if (!gd.filler.isEmpty())
        {
            section("Train between steps", null);
            for (String f : gd.filler) line(f, BLUE);
        }
        return super.render(g);
    }

    /** A shift + right-click "Wiki" entry for the step under Do now (RuneLite shows overlay menus on shift only). Client thread. */
    private void wikiEntry(Planner.Step s)
    {
        String url = s == null ? null : StepDetail.wikiPage(s);
        if (Objects.equals(url, wikiShown)) return;
        removeMenuEntry(MenuAction.RUNELITE_OVERLAY, "Wiki", "this step");
        if (url != null) addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Wiki", "this step", e -> net.runelite.client.util.LinkBrowser.browse(url));
        wikiShown = url;
    }

    /** A thin line, then the section's name small on the left (and a note on the right). */
    private void section(String label, String right)
    {
        panelComponent.getChildren().add(new Rule());
        panelComponent.getChildren().add(LineComponent.builder().left(label.toUpperCase(Locale.ROOT)).leftColor(ORANGE)
            .leftFont(FontManager.getRunescapeSmallFont()).right(right == null ? "" : right).rightColor(GREY)
            .rightFont(FontManager.getRunescapeSmallFont()).build());
    }

    /** One item per row: its name on the left, where it is on the right. */
    private void item(StepItems.Item i)
    {
        String n = Text.cap((i.need > 1 && !i.perHour ? i.need + " x " : "") + i.name);
        Color c;
        String state;
        switch (i.has)
        {
            case CARRIED: c = GREEN; state = "on you"; break;
            case STORED: c = YELLOW; state = String.join(", ", i.where); break;
            case REMINDER: c = GREY; state = "bring"; break;
            default: c = RED; state = "missing"; break;
        }
        panelComponent.getChildren().add(LineComponent.builder().left(n).leftColor(c).right(state).rightColor(c).build());
    }

    private void line(String text, Color c)
    {
        panelComponent.getChildren().add(LineComponent.builder().left(text).leftColor(c).build());
    }

    /** A thin grey line across the box. */
    private static final class Rule implements LayoutableRenderableEntity
    {
        private final Rectangle bounds = new Rectangle();
        private Point at = new Point();
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

        @Override public Rectangle getBounds() { return bounds; }

        @Override public void setPreferredLocation(Point p) { at = p; }

        @Override public void setPreferredSize(Dimension d) { size = d; }
    }
}
