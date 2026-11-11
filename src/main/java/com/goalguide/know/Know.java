package com.goalguide.know;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import com.goalguide.items.ItemTree;

/**
 * The one place the guide asks "what does this account have right now?". The Goal Guide plugin fills it from the
 * client and saves one file per account (~/.runelite/goal-guide/know-&lt;profile&gt;.json), so a storage seen once is
 * known after a restart, and the planners still work logged out.
 *
 * Readers: {@link #state()} or {@link #account()} for a planner copy. Null means no account known yet (the guide
 * plugin is off, or this is a fresh install that never logged in).
 */
@Slf4j
public final class Know
{
    private Know() { }

    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile File dir;
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

    /** True while the Goal Guide plugin runs on a logged-in account, so {@link #state()} is kept up to date. */
    public static boolean live()
    {
        return live && current != null;
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

    private static volatile boolean touched;

    /**
     * Another plugin changed the state (travel times, house scans): tell the listeners and have the guide
     * save it. Client thread. Ignored when {@code s} is no longer the current account.
     */
    public static void touched(AccountState s)
    {
        if (s == null || s != current) return;
        touched = true;
        changed();
    }

    /** True once after {@link #touched}: the guide saves then. */
    static boolean takeTouched()
    {
        boolean t = touched;
        touched = false;
        return t;
    }

    // ---- the plugin side

    /** The guide's own folder under .runelite (renamed from "iron-loop" 2026-11-11; {@link #home()} moves old files). */
    public static final String HOME = "goal-guide", OLD_HOME = "iron-loop";
    private static volatile boolean moved;

    /** ~/.runelite/goal-guide, made on first use; files left in the old folder are moved in once. Any thread. */
    public static File home()
    {
        File home = new File(net.runelite.client.RuneLite.RUNELITE_DIR, HOME);
        if (!moved) synchronized (Know.class)
        {
            if (!moved)
            {
                moved = true;
                move(new File(net.runelite.client.RuneLite.RUNELITE_DIR, OLD_HOME), home);
            }
        }
        return home;
    }

    /** Moves every file of {@code from} not already in {@code to}, then removes {@code from} when it is empty. */
    static void move(File from, File to)
    {
        File[] files = from.listFiles();
        if (files == null) return;
        if (!to.isDirectory() && !to.mkdirs()) return;
        for (File f : files)
        {
            File t = new File(to, f.getName());
            if (t.exists()) continue;
            try
            {
                Files.move(f.toPath(), t.toPath());
            }
            catch (IOException e)
            {
                log.warn("Goal Guide: could not move {} to {}", f, t, e);
            }
        }
        String[] left = from.list();
        if (left != null && left.length == 0 && !from.delete()) log.debug("Goal Guide: old folder {} kept", from);
    }

    /** The folder the states are saved in, null before startUp. */
    public static File dir() { return dir; }

    static void dir(File d)
    {
        dir = d;
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
            return GSON.toJson(s);
        }
    }

    static AccountState fromJson(String json)
    {
        AccountState s = GSON.fromJson(json, AccountState.class);
        if (s == null) return null;
        // a field added later reads as null from an older file
        AccountState blank = new AccountState();
        if (s.levels == null) s.levels = blank.levels;
        if (s.xp == null) s.xp = blank.xp;
        if (s.questsDone == null) s.questsDone = blank.questsDone;
        if (s.questsStarted == null) s.questsStarted = blank.questsStarted;
        if (s.storages == null) s.storages = blank.storages;
        if (s.timers == null) s.timers = blank.timers;
        if (s.stops == null) s.stops = blank.stops;
        s.storages.values().removeIf(st -> st == null || st.items == null);
        s.timers.values().removeIf(t -> t == null || t.state == null);
        return s;
    }

    /** The saved state of one account, or a fresh one. Never null. */
    static AccountState load(String profile)
    {
        File d = dir;
        if (d != null && profile != null)
        {
            File f = new File(d, fileName(profile));
            if (f.isFile())
            {
                try
                {
                    AccountState s = fromJson(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
                    if (s != null)
                    {
                        s.profile = profile;
                        return s;
                    }
                }
                catch (IOException | RuntimeException e)
                {
                    log.warn("Know: could not read {}", f, e);
                }
            }
        }
        AccountState s = new AccountState();
        s.profile = profile;
        return s;
    }

    /** The most recently saved account, for a client that has not logged in yet. Null when none. */
    static AccountState lastSaved()
    {
        File d = dir;
        File[] files = d == null ? null : d.listFiles((x, n) -> n.startsWith("know-") && n.endsWith(".json"));
        if (files == null || files.length == 0) return null;
        File newest = files[0];
        for (File f : files) if (f.lastModified() > newest.lastModified()) newest = f;
        try
        {
            return fromJson(new String(Files.readAllBytes(newest.toPath()), StandardCharsets.UTF_8));
        }
        catch (IOException | RuntimeException e)
        {
            log.warn("Know: could not read {}", newest, e);
            return null;
        }
    }

    /** Writes the JSON (made on the client thread) to the account's file, through a temp file so a crash never leaves half a file. */
    static void write(String profile, String json)
    {
        File d = dir;
        if (d == null || profile == null) return;
        try
        {
            Files.createDirectories(d.toPath());
            File f = new File(d, fileName(profile)), tmp = new File(d, fileName(profile) + ".tmp");
            Files.write(tmp.toPath(), json.getBytes(StandardCharsets.UTF_8));
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException e)
        {
            log.warn("Know: could not save {}", profile, e);
        }
    }
}
