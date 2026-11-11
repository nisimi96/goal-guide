package com.goalguide.items;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import lombok.extern.slf4j.Slf4j;

/**
 * The item database: recipes, shops, spawns, drops and item ids from the OSRS Wiki, bundled with the plugin as
 * item_data.tsv.gz (built by tools/item_data.py; CC BY-NC-SA 3.0, see NOTICE.md). Loaded once per client, off the
 * client thread, and shared by every reader through {@link #get()}.
 */
@Slf4j
public final class ItemDb
{
    static final String FILE = "item_data.tsv.gz";

    private static volatile ItemData data;
    private static volatile String status = "not loaded";

    private ItemDb() { }

    /** The loaded data, null until {@link #load} finishes. */
    public static ItemData get() { return data; }

    public static String status() { return status; }

    /** Loads the bundled data in the background. done gets the data, or null on failure (see status()). */
    public static void load(Consumer<ItemData> done)
    {
        if (data != null) { done.accept(data); return; }
        CompletableFuture.runAsync(() ->
        {
            synchronized (ItemDb.class)
            {
                if (data == null) data = read();
            }
            done.accept(data);
        });
    }

    static ItemData read()
    {
        status = "reading the item database";
        try (InputStream raw = ItemDb.class.getResourceAsStream(FILE))
        {
            if (raw == null) throw new java.io.IOException(FILE + " is missing");
            try (BufferedReader in = new BufferedReader(new InputStreamReader(new GZIPInputStream(raw), StandardCharsets.UTF_8)))
            {
                ItemData d = ItemData.load(in);
                status = "loaded " + d.names.size() + " items";
                log.debug("Item database: {}", status);
                return d;
            }
        }
        catch (java.io.IOException | RuntimeException e)
        {
            status = "failed: " + e.getMessage();
            log.warn("Could not load the item database", e);
            return null;
        }
    }
}
