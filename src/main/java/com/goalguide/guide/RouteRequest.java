package com.goalguide.guide;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;

/**
 * Asks a route plugin for the way to the trip's next stop, if one is installed: Shortest Path or Travel Guide
 * (both on the Plugin Hub, both take a "path" message with one or more target tiles and route to the
 * closest). This is a RuneLite event only: no code of theirs, and nothing happens when neither is installed.
 * yuval allowed this one exception to "no other plugins" on 2026-11-11. Client thread.
 */
@Singleton
public class RouteRequest
{
    /** The plugins' message namespaces (their config groups). */
    static final String[] PLUGINS = {"shortestpath", "travelguide"};

    @Inject private EventBus eventBus;

    private Set<WorldPoint> sent = Set.of();

    /** Route to the closest of these tiles; an empty set clears our route. Sends only on a change. */
    public void to(Set<WorldPoint> targets)
    {
        if (targets.equals(sent)) return;
        if (targets.isEmpty())
        {
            for (String p : PLUGINS) eventBus.post(new PluginMessage(p, "clear"));
        }
        else
        {
            Map<String, Object> data = new HashMap<>();
            data.put("target", new HashSet<>(targets));
            for (String p : PLUGINS) eventBus.post(new PluginMessage(p, "path", data));
        }
        sent = Set.copyOf(targets);
    }
}
