package com.goalguide.guide;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Objects;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.client.util.ImageUtil;

/**
 * "arrow / path to the spot": where to go next (a run's next stop), shown as the game's hint arrow
 * and a pin on the world map, and handed to the Shortest Path plugin from the guide box's menu when the player has it.
 * The target is set off the client thread; {@link #tick} applies it on the client thread.
 */
public final class Waypoint
{
    private Waypoint() { }

    private static volatile WorldPoint target;
    private static volatile String label;
    private static WorldPoint shown;
    private static WorldMapPoint pin;

    /** Go here next ("Herb run: Falador"); null clears it. */
    public static void set(WorldPoint p, String what)
    {
        target = p;
        label = what;
    }

    public static WorldPoint target() { return target; }

    public static String label() { return label; }

    /** Client thread, every tick: the arrow and the map pin follow the target. */
    public static void tick(Client client, WorldMapPointManager map, boolean on)
    {
        WorldPoint t = on ? target : null;
        if (Objects.equals(t, shown)) return;
        clear(client, map);
        if (t == null) return;
        // Another plugin's arrow stays: the pin still shows the way
        if (!client.hasHintArrow()) client.setHintArrow(t);
        pin = WorldMapPoint.builder().worldPoint(t).image(icon()).name(label == null ? "Goal Guide" : label)
            .tooltip(label == null ? "Goal Guide" : label).snapToEdge(true).jumpOnClick(true).build();
        map.add(pin);
        shown = t;
    }

    /** Takes the arrow and the pin away (only the arrow the guide set). */
    public static void clear(Client client, WorldMapPointManager map)
    {
        if (shown != null && client.hasHintArrow() && shown.equals(client.getHintArrowPoint())) client.clearHintArrow();
        if (pin != null) map.remove(pin);
        pin = null;
        shown = null;
    }

    private static volatile EventBus bus;

    public static void bus(EventBus b) { bus = b; }

    /** Asks the Shortest Path plugin (Plugin Hub) for a path to the target; nothing happens when it isn't installed. */
    public static void path()
    {
        WorldPoint t = target;
        EventBus b = bus;
        if (t != null && b != null) b.post(new PluginMessage("shortestpath", "path", Map.of("target", t)));
    }

    private static BufferedImage icon;

    private static BufferedImage icon()
    {
        if (icon != null) return icon;
        BufferedImage img = new BufferedImage(12, 12, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new java.awt.Color(0xff, 0x98, 0x1f));
        g.fillOval(1, 1, 10, 10);
        g.setColor(java.awt.Color.BLACK);
        g.drawOval(1, 1, 10, 10);
        g.dispose();
        icon = ImageUtil.resizeImage(img, 12, 12);
        return icon;
    }
}
