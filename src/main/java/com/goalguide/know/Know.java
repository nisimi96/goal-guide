package com.goalguide.know;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import com.goalguide.items.ItemTree;
import net.runelite.client.util.Filepath;

/**
 * The one place the guide asks "what does this account have right now?". The Goal Guide plugin fills it from the
 * client and saves one file per account (know-&lt;profile&gt;.json in the plugin's data folder), so a storage seen once is
 * known after a restart, and the planners still work logged out.
 *
 * Readers: {@link #state()} or {@link #account()} for a planner copy. Null means no account known yet (the guide
 * plugin is off, or this is a fresh install that never logged in).
 */
@Slf4j
public final class Know
{
    private Know() { }

    /** RuneLite's Gson, set by the plugin at startup. */
    private static volatile Gson gson = new Gson();
    private static volatile Filepath dir;
    private static volatile AccountState current;
    private static volatile boolean live;
    private static final List<Consumer<AccountState>> LISTENERS = new CopyOnWriteArrayList<>();

    /** The account the guide knows about: the logged-in one, or the last one saved. Null when none. */
    public static AccountState state()
    {
        return current;
    }

    /** A planner account from {@link #state()}, null when none. */
    public static ItemTree.Account account()
    {
        AccountState s = current;
        return s == null ? null : s.toAccount();
    }

    /** Called with the state after each change (on the client thread). Keep it short. */
    public static void listen(Consumer<AccountState> l)
    {
        LISTENERS.add(l);
    }

    public static void unlisten(Consumer<AccountState> l)
    {
        LISTENERS.remove(l);
    }

    // ---- the plugin side

    /** The plugin's data folder (null before startUp) and RuneLite's Gson. */
    static void init(Filepath d, Gson g)
    {
        dir = d;
        if (g != null) gson = g.newBuilder().setPrettyPrinting().create();
    }

    static void set(AccountState s, boolean isLive)
    {
        current = s;
        live = isLive;
        changed();
    }

    static void changed()
    {
        AccountState s = current;
        for (Consumer<AccountState> l : LISTENERS)
        {
            try { l.accept(s); }
            catch (RuntimeException e) { log.warn("Know listener failed", e); }
        }
    }

    static String fileName(String profile)
    {
        return "know-" + (profile == null ? "unknown" : profile.replaceAll("[^A-Za-z0-9_-]", "_")) + ".json";
    }

    static String toJson(AccountState s)
    {
        synchronized (s)
        {
            return gson.toJson(s);
        }
    }

    static AccountState fromJson(String json)
    {
        AccountState s = gson.fromJson(json, AccountState.class);
        if (s == null) return null;
        // A field added later reads as null from an older file
        AccountState blank = new AccountState();
        if (s.levels == null) s.levels = blank.levels;
        if (s.xp == null) s.xp = blank.xp;
        if (s.questsDone == null) s.questsDone = blank.questsDone;
        if (s.questsStarted == null) s.questsStarted = blank.questsStarted;
        if (s.storages == null) s.storages = blank.storages;
        s.storages.values().removeIf(st -> st == null || st.items == null);
        return s;
    }

    /** The saved state of one account, or a fresh one. Never null. */
    static AccountState load(String profile)
    {
        Filepath d = dir;
        if (d != null && profile != null)
        {
            try
            {
                Filepath f = d.joinSegment(fileName(profile));
                if (f.isFile())
                {
                    AccountState s = fromJson(read(f));
                    if (s != null)
                    {
                        s.profile = profile;
                        return s;
                    }
                }
            }
            catch (IOException | RuntimeException e)
            {
                log.warn("Know: could not read the saved state of {}", profile, e);
            }
        }
        AccountState s = new AccountState();
        s.profile = profile;
        return s;
    }

    /** The most recently saved account, for a client that has not logged in yet. Null when none. */
    static AccountState lastSaved()
    {
        Filepath d = dir;
        if (d == null || !d.isDirectory()) return null;
        Filepath newest = null;
        long newestAt = Long.MIN_VALUE;
        try (java.util.stream.Stream<Filepath> files = d.walk(1))
        {
            for (Filepath f : (Iterable<Filepath>) files::iterator)
            {
                String n = f.getFileName();
                if (n == null || !n.startsWith("know-") || !n.endsWith(".json") || !f.isFile()) continue;
                long at = f.getLastModifiedTime().toMillis();
                if (at > newestAt) { newest = f; newestAt = at; }
            }
            return newest == null ? null : fromJson(read(newest));
        }
        catch (IOException | RuntimeException e)
        {
            log.warn("Know: could not read the last saved state", e);
            return null;
        }
    }

    private static String read(Filepath f) throws IOException
    {
        try (java.io.InputStream in = f.openInputStream())
        {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Writes the JSON (made on the client thread) to the account's file, through a temp file so a crash never leaves half a file. */
    static void write(String profile, String json)
    {
        Filepath d = dir;
        if (d == null || profile == null) return;
        try
        {
            d.createDirectories();
            Filepath f = d.joinSegment(fileName(profile)), tmp = d.joinSegment(fileName(profile) + ".tmp");
            tmp.write(json.getBytes(StandardCharsets.UTF_8));
            tmp.moveTo(f, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException e)
        {
            log.warn("Know: could not save {}", profile, e);
        }
    }
}
