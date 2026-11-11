package com.goalguide.guide;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

/**
 * Marks where the trip's next stop is: the closest anvil, furnace or bank booth in the loaded scene gets the
 * game's own hint arrow (over it and on the minimap), and its model outlined like Object Markers does. Places are found from objects as they
 * spawn, by their in-game name, so nothing is scanned per frame. Only marks: it never clicks or walks.
 * Client thread, except render.
 */
@Singleton
public class PlaceMarker extends Overlay
{
    static final Color OUTLINE = new Color(0, 229, 255), FILL = new Color(0, 229, 255, 40);
    /** In-game object names that count as each trip place. */
    static final String[][] NAMES = {{"anvil", "anvil"}, {"furnace", "furnace"}, {"bank", "bank booth"}, {"bank", "bank chest"}};

    @Inject private Client client;
    @Inject private ModelOutlineRenderer outlines;

    private final Set<GameObject> places = new HashSet<>();
    private GameObject target;
    /** The hint arrow point we set, so we only ever clear our own. */
    private WorldPoint ours;

    public PlaceMarker()
    {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    public void spawned(GameObject o)
    {
        if (place(o) != null) places.add(o);
    }

    public void despawned(GameObject o)
    {
        places.remove(o);
        if (o == target) target = null;
    }

    /** A new scene is loading: its objects spawn again. */
    public void sceneLoading()
    {
        places.clear();
        target = null;
    }

    /** Each tick: point at the closest place of the trip's next stop, or clear the arrow. */
    public void tick(Trip.Stop stop)
    {
        Player me = client.getLocalPlayer();
        String want = stop == null ? null : stop.place.toLowerCase(Locale.ROOT);
        GameObject best = null;
        int bestDist = Integer.MAX_VALUE;
        if (me != null && want != null)
        {
            WorldPoint at = me.getWorldLocation();
            for (GameObject o : places)
            {
                if (!want.equals(place(o))) continue;
                int dist = o.getWorldLocation().distanceTo2D(at);
                if (dist < bestDist)
                {
                    best = o;
                    bestDist = dist;
                }
            }
        }
        target = best;
        WorldPoint p = best == null ? null : best.getWorldLocation();
        if (p != null && !p.equals(ours))
        {
            client.setHintArrow(p);
            ours = p;
        }
        else if (p == null) clear();
    }

    /** Takes our hint arrow away (one the game set stays). */
    public void clear()
    {
        if (ours != null && client.hasHintArrow() && ours.equals(client.getHintArrowPoint())) client.clearHintArrow();
        ours = null;
        target = null;
    }

    public void stop()
    {
        clear();
        places.clear();
    }

    /** The trip place an object is ("anvil", "furnace", "bank"), or null. */
    private String place(GameObject o)
    {
        ObjectComposition c = client.getObjectDefinition(o.getId());
        if (c != null && c.getImpostorIds() != null && c.getImpostor() != null) c = c.getImpostor();
        String name = c == null || c.getName() == null ? "" : c.getName().toLowerCase(Locale.ROOT);
        for (String[] n : NAMES) if (name.equals(n[1])) return n[0];
        return null;
    }

    @Override
    public Dimension render(Graphics2D g)
    {
        GameObject t = target;
        if (t == null) return null;
        // Like RuneLite's Object Markers with "Highlight hull" and "Highlight outline": a light fill and the model's outline
        Shape hull = t.getConvexHull();
        if (hull != null)
        {
            g.setColor(FILL);
            g.fill(hull);
        }
        outlines.drawOutline(t, 2, OUTLINE, 4);
        return null;
    }
}
