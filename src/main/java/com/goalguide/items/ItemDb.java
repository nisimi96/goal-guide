package com.goalguide.items;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Where the item database lives and how it gets there. The file is the one the Not Enough Runes plugin downloads
 * (its author's wiki scrape, refreshed as the wiki changes). If NER is installed we read its copy; otherwise we
 * download the same file to .runelite/goal-guide/ and re-download only when its published checksum changes.
 * Loaded once per client, shared by every reader through {@link #get()}.
 */
@Slf4j
public final class ItemDb
{
    static final String URL = "https://raw.githubusercontent.com/Hannah-GBS/runelite-wiki-scraper/wiki-data/output/data.sqlite";
    static final File OURS = new File(new File(RuneLite.RUNELITE_DIR, com.goalguide.know.Know.HOME), "data.sqlite");
    static final File NER = new File(new File(RuneLite.RUNELITE_DIR, "not-enough-runes"), "data.sqlite");

    private static volatile ItemData data;
    private static volatile String status = "not loaded";

    private ItemDb() { }

    /** The loaded data, null until {@link #load} finishes. */
    public static ItemData get() { return data; }

    public static String status() { return status; }

    /** Fetch if needed and load, off the calling thread. done gets the data, or null on failure (see status()). */
    public static void load(OkHttpClient http, Consumer<ItemData> done)
    {
        if (data != null) { done.accept(data); return; }
        Thread t = new Thread(() ->
        {
            try
            {
                File f = fetch(http);
                status = "reading " + f.getName();
                Class.forName("org.sqlite.JDBC");
                try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + f.getAbsolutePath()))
                {
                    data = ItemData.load(c);
                }
                status = "loaded " + data.names.size() + " items from " + (f.equals(NER) ? "Not Enough Runes' copy" : f.getParent());
                log.info("Item roots: {}", status);
            }
            catch (Exception e)
            {
                status = "failed: " + e.getMessage();
                log.warn("Item roots: could not load the item database", e);
            }
            done.accept(data);
        }, "item-roots-load");
        t.setDaemon(true);
        t.start();
    }

    private static File fetch(OkHttpClient http) throws IOException
    {
        com.goalguide.know.Know.home(); // moves a copy left in the old folder, so no new download
        String remoteSum = null;
        try
        {
            status = "checking for a newer item database";
            remoteSum = text(http, URL + ".sha256").trim().split("\\s+")[0].toLowerCase();
        }
        catch (IOException e)
        {
            log.debug("Item roots: no checksum ({}), using a local copy if there is one", e.getMessage());
        }
        if (NER.isFile() && (remoteSum == null || remoteSum.equals(sha256(NER)))) return NER;
        if (OURS.isFile() && (remoteSum == null || remoteSum.equals(sha256(OURS)))) return OURS;
        if (remoteSum == null && NER.isFile()) return NER;
        status = "downloading the item database (about 8 MB)";
        OURS.getParentFile().mkdirs();
        File tmp = new File(OURS.getParentFile(), "data.sqlite.part");
        try (Response r = http.newCall(new Request.Builder().url(URL).build()).execute())
        {
            if (!r.isSuccessful() || r.body() == null) throw new IOException("HTTP " + r.code());
            try (InputStream in = r.body().byteStream())
            {
                Files.copy(in, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        if (remoteSum != null && !remoteSum.equals(sha256(tmp))) throw new IOException("checksum mismatch on download");
        Files.move(tmp.toPath(), OURS.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return OURS;
    }

    private static String text(OkHttpClient http, String url) throws IOException
    {
        try (Response r = http.newCall(new Request.Builder().url(url).build()).execute())
        {
            if (!r.isSuccessful() || r.body() == null) throw new IOException("HTTP " + r.code());
            return new String(r.body().bytes(), StandardCharsets.UTF_8);
        }
    }

    static String sha256(File f) throws IOException
    {
        try
        {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(Files.readAllBytes(f.toPath()));
            StringBuilder b = new StringBuilder();
            for (byte x : h) b.append(String.format("%02x", x));
            return b.toString();
        }
        catch (java.security.NoSuchAlgorithmException e)
        {
            throw new IOException(e);
        }
    }
}
