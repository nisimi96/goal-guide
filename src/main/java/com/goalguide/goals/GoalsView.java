package com.goalguide.goals;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import com.goalguide.decide.Decide;
import com.goalguide.items.ItemDb;
import com.goalguide.items.ItemRootsPlugin;
import com.goalguide.items.ItemTree;
import com.goalguide.know.AccountState;
import com.goalguide.know.Know;
import com.goalguide.quests.QuestData;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.PluginPanel;

/**
 * The Goal Guide sidebar: the account's goals (typed in, pinned order, saved per account) and the one
 * route that plans them all with hours left. Planning runs off the client thread, again on every Know change.
 */
@Slf4j
public final class GoalsView
{
    static final String GROUP = "goalguide", OLD_GROUP = "ironloop", KEY = "goals", SITTING = "sitting", GUIDE_ON_SCREEN = "guideOnScreen",
        RUNS_OFF = "runsOff", STOPS_OFF = "stopsOff", TIMER_LENGTHS = "timerLengths", DAILIES = "dailiesDone";
    /** A run box closes this long after the run began, even with stops left. */
    static final long RUN_BOX_MAX_MS = 60 * 60_000L;
    /** Sitting lengths offered, in minutes. */
    static final int[] SITTINGS = {30, 60, 120, 180};

    private final ConfigManager config;
    private final ClientToolbar toolbar;
    private final ExecutorService executor;
    private final Consumer<AccountState> onKnow = s -> replan();
    private Panel panel;
    private NavigationButton nav;
    private volatile List<Goal> goals = new ArrayList<>();
    private volatile String profile;
    private volatile boolean queued;
    private volatile int sitting = 60;
    /** The last "now" pick logged, so the log has one line per change. */
    private String lastSaid;
    /** For the bank filter button (Bank Tags). */
    private volatile net.runelite.client.plugins.PluginManager plugins;
    /** Runs tab: runs and stops switched off, per account. */
    private volatile com.goalguide.decide.RunPrefs prefs = com.goalguide.decide.RunPrefs.ALL_ON;
    /** Daily shop runs ticked today ("2026-11-11;zaff;bert"), per account. */
    private volatile String dailies = "";
    /** Activity timer lengths per activity (ActivityTimer.key), per account. */
    private volatile Map<String, Integer> lengths = new LinkedHashMap<>();
    /** Steps moved past with "Next activity" this session (ActivityTimer.key). */
    private final java.util.Set<String> skipped = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile boolean startNextAfterPlan;
    /** The run the run box is following, and when it began. */
    private volatile String activeRun;
    private volatile long activeRunStart;
    /** Runs a job on the client thread (the bank button), and the notification at the end of a timer. */
    private volatile Consumer<Runnable> onClient = Runnable::run;
    private volatile Consumer<String> notifier = m -> { };

    public GoalsView(ConfigManager config, ClientToolbar toolbar, ExecutorService executor)
    {
        this.config = config;
        this.toolbar = toolbar;
        this.executor = executor;
        // "Get it myself" box: its route is worked out here, off the client thread
        com.goalguide.guide.GetBox.builder((item, done) -> executor.execute(() ->
        {
            AccountState st = Know.state();
            ItemTree.Account a = st == null ? new ItemTree.Account() : st.toAccount();
            try { done.accept(com.goalguide.guide.GetBox.lines(ItemDb.get(), a, item)); }
            catch (RuntimeException e) { log.warn("get box", e); }
        }));
    }

    public void plugins(net.runelite.client.plugins.PluginManager p)
    {
        plugins = p;
    }

    /** {@code client}: runs a job on the client thread; {@code notify}: RuneLite's notification. */
    public void hooks(Consumer<Runnable> client, Consumer<String> notify)
    {
        onClient = client;
        notifier = notify;
    }

    /** The guide box on the game screen: on unless the sidebar's box was unticked. */
    public boolean guideOnScreen()
    {
        return !"false".equals(config.getConfiguration(GROUP, GUIDE_ON_SCREEN));
    }

    void setGuideOnScreen(boolean on)
    {
        config.setConfiguration(GROUP, GUIDE_ON_SCREEN, String.valueOf(on));
    }

    /**
     * Copies settings saved under the plugin's first config group name into {@link #GROUP}, once: account settings
     * per RuneScape profile and the global ones. A key already set under the new name is left alone.
     */
    public static void migrateConfig(ConfigManager config)
    {
        for (String full : config.getConfigurationKeys(OLD_GROUP + "."))
        {
            String key = full.substring(OLD_GROUP.length() + 1);
            if (key.startsWith("rsprofile.")) continue;
            if (config.getConfiguration(GROUP, key) == null) config.setConfiguration(GROUP, key, config.getConfiguration(OLD_GROUP, key));
        }
        for (net.runelite.client.config.RuneScapeProfile p : config.getRSProfiles())
        {
            String profile = p.getKey();
            for (String key : config.getRSProfileConfigurationKeys(OLD_GROUP, profile, ""))
                if (config.getConfiguration(GROUP, profile, key) == null)
                    config.setConfiguration(GROUP, profile, key, config.getConfiguration(OLD_GROUP, profile, key));
        }
    }

    public void start()
    {
        panel = new Panel(this);
        nav = NavigationButton.builder().tooltip("Goal Guide").icon(com.goalguide.guide.Icons.initials("GG")).priority(11).panel(panel).build();
        toolbar.addNavigation(nav);
        Know.listen(onKnow);
        ItemDb.load(d -> replan());
        replan();
    }

    public void stop()
    {
        Know.unlisten(onKnow);
        if (nav != null) toolbar.removeNavigation(nav);
        if (panel != null) panel.close();
        nav = null;
        panel = null;
    }

    /** Re-reads the goals when the account changed, then plans. At most one plan waits at a time. */
    void replan()
    {
        if (queued) return;
        queued = true;
        executor.execute(() ->
        {
            queued = false;
            AccountState st = Know.state();
            String p = st == null ? null : st.profile;
            if (p != null && !p.equals(profile))
            {
                profile = p;
                goals = Goal.decodeAll(config.getConfiguration(GROUP, p, KEY));
                sitting = sittingOf(config.getConfiguration(GROUP, p, SITTING));
                prefs = com.goalguide.decide.RunPrefs.decode(config.getConfiguration(GROUP, p, RUNS_OFF), config.getConfiguration(GROUP, p, STOPS_OFF));
                lengths = com.goalguide.guide.ActivityTimer.lengths(config.getConfiguration(GROUP, p, TIMER_LENGTHS));
                String dl = config.getConfiguration(GROUP, p, DAILIES);
                dailies = dl == null ? "" : dl;
                lastSaid = null;
                activeRun = null;
                skipped.clear();
            }
            Panel pn = panel;
            if (pn == null) return;
            pn.showGoals(goals, sitting);
            if (st == null)
            {
                com.goalguide.guide.Guide.set(null);
                com.goalguide.guide.RunBox.set(null);
                pn.showGuide(null);
                pn.showRoute("<font color='#f0c070'>Log in once with Goal Guide on, so it knows your account.</font>", null, null);
                return;
            }
            long now = System.currentTimeMillis() / 1000;
            ItemTree.Account a = st.toAccount();
            Planner.Route r = goals.isEmpty() ? null : Planner.plan(QuestData.get(), ItemDb.get(), a, st.questPoints, goals);
            Decide.Decision d = Decide.decide(a, timers(st), st.travel(), st.bankFree(), r == null ? new Planner.Route() : r, goals, now, sitting, prefs);
            log(p, d);
            com.goalguide.guide.Guide gd = r == null ? null : com.goalguide.guide.Guide.of(r, d, skipped);
            // Every step was moved past: start again from the top
            if (gd != null && gd.skipped == 0) skipped.clear();
            if (gd != null && gd.now != null)
            {
                gd.items = com.goalguide.guide.StepItems.of(gd.now.step, st.items(), item -> source(a, item));
                com.goalguide.guide.BankFilter.set(gd.now.what, com.goalguide.guide.StepItems.bankNames(gd.items).keySet(), ItemDb.get());
                Map<String, String> gone = new LinkedHashMap<>();
                for (com.goalguide.guide.StepItems.Item i : gd.items)
                    if (i.has == com.goalguide.guide.StepItems.Has.MISSING) gone.put(i.name, i.from);
                com.goalguide.guide.BankFilter.missing(gone, ItemDb.get());
            }
            else
            {
                com.goalguide.guide.BankFilter.set(null, List.of(), null);
                com.goalguide.guide.BankFilter.missing(Map.of(), null);
            }
            com.goalguide.guide.BankFilter.runs(com.goalguide.guide.RunItems.on(prefs));
            com.goalguide.guide.Guide.set(gd);
            if (startNextAfterPlan)
            {
                startNextAfterPlan = false;
                if (gd != null && gd.now != null) com.goalguide.guide.Guide.TIMER.start(gd.now.what, lengthFor(gd.now.what), System.currentTimeMillis());
            }
            runBox(st, a, d.now());
            // Arrow to the spot: with no run going, the training step's best method's spot
            if (com.goalguide.guide.RunBox.current() == null) trainingSpot(gd, a);
            pn.showGuide(gd);
            pn.showRuns(a, prefs, st, com.goalguide.guide.Dailies.doneToday(dailies, System.currentTimeMillis()));
            pn.showIdeas(ideaRows(p == null ? List.of() : com.goalguide.decide.PatchTravel.IDEAS.getOrDefault(p, List.of()), goals));
            pn.showRoute(decideHtml(d, sitting), r, r == null ? html(st, goals) : null);
        });
    }

    /** Follows the run that is Now until each stop is done or still growing run box). */
    private void runBox(AccountState st, ItemTree.Account a, Decide.Pick now)
    {
        long ms = System.currentTimeMillis();
        if (activeRun == null && now != null && now.timer != null && now.rank == Decide.Rank.WASTE)
        {
            activeRun = now.timer;
            activeRunStart = ms;
        }
        String run = activeRun;
        if (run != null && !prefs.on(run)) activeRun = run = null;
        com.goalguide.guide.BankFilter.prefer(run == null ? null : Decide.runName(run));
        if (run == null) { com.goalguide.guide.RunBox.set(null); com.goalguide.guide.Waypoint.set(null, null); return; }
        boolean birds = AccountState.BIRDS.equals(run);
        Map<String, AccountState.Timer> stops = birds ? (st.timer(AccountState.BIRDS) == null ? Map.of() : Map.of("Fossil Island", st.timer(AccountState.BIRDS))) : st.stops(run);
        List<com.goalguide.guide.RunBox.Row> rows = com.goalguide.guide.RunBox.rows(run, stops, prefs.stops(run, a), activeRunStart, ms / 1000);
        // Time Tracking off: no stops to follow
        if (stops.isEmpty() || com.goalguide.guide.RunBox.finished(rows) || ms - activeRunStart > RUN_BOX_MAX_MS)
        {
            activeRun = null;
            com.goalguide.guide.RunBox.set(null);
            com.goalguide.guide.Waypoint.set(null, null);
            return;
        }
        com.goalguide.guide.RunBox.set(new com.goalguide.guide.RunBox.Box(Decide.runName(run), rows));
        // Arrow to the spot: the first stop that still needs you
        net.runelite.api.coords.WorldPoint go = null;
        String goName = null;
        for (com.goalguide.guide.RunBox.Row row : rows)
        {
            if (row.mark == com.goalguide.guide.RunBox.Mark.DONE || row.mark == com.goalguide.guide.RunBox.Mark.GROWING) continue;
            for (com.goalguide.decide.PatchTravel.Patch p : com.goalguide.decide.PatchTravel.RUNS.getOrDefault(run, List.of()))
                if (p.name().equals(row.stop)) { go = p.tile(); goName = Decide.runName(run) + ": " + p.name(); }
            if (go != null) break;
        }
        com.goalguide.guide.Waypoint.set(go, goName);
    }

    /** Points the arrow and map pin at where the Now step's best method is done, when the wiki gave its spot. */
    private static void trainingSpot(com.goalguide.guide.Guide gd, ItemTree.Account a)
    {
        Planner.Step s = gd == null || gd.now == null ? null : gd.now.step;
        int[] at = null;
        String name = null;
        if (s != null && s.kind == Planner.Kind.TRAIN && s.skill != null)
            for (com.goalguide.facts.Method m : Planner.recommend(a, s.skill, s.from, s.to))
                if (m.level <= s.from)
                {
                    at = com.goalguide.facts.MethodTables.spot(s.skill, m.id);
                    name = m.name;
                    break;
                }
        com.goalguide.guide.Waypoint.set(at == null ? null : new net.runelite.api.coords.WorldPoint(at[0], at[1], at[2]), name);
    }

    /** Where to get an item the step is missing: a main's GE price first, then the best way to make or get it. */
    static String source(ItemTree.Account a, String item)
    {
        String key = com.goalguide.items.ItemData.questText(item);
        int each = "main".equals(a.mode()) ? Prices.each(key) : 0;
        String ge = each > 0 ? "Grand Exchange about " + Prices.gp(each) : null;
        List<com.goalguide.facts.Method> ways = com.goalguide.facts.MethodTables.waysToMake(key, a);
        String make = !ways.isEmpty() ? ways.get(0).name : null;
        if (make == null)
        {
            com.goalguide.items.ItemData d = ItemDb.get();
            List<String> names = d == null ? List.of() : d.matches(key);
            if (!names.isEmpty())
            {
                ItemTree.Node n = ItemTree.trace(d, a, names.get(0));
                if (n.best != null) make = n.best.say == null ? n.best.label : n.best.say;
            }
        }
        if (ge != null) return make == null ? ge : ge + ", or " + make;
        return make;
    }

    // ---- activity timer 
    int lengthFor(String what)
    {
        return lengths.getOrDefault(com.goalguide.guide.ActivityTimer.key(what), com.goalguide.guide.ActivityTimer.DEFAULT_MIN);
    }

    void setLength(String what, int minutes)
    {
        Map<String, Integer> m = new LinkedHashMap<>(lengths);
        m.put(com.goalguide.guide.ActivityTimer.key(what), Math.max(5, Math.min(480, minutes)));
        lengths = m;
        String p = profile;
        if (p != null) config.setConfiguration(GROUP, p, TIMER_LENGTHS, com.goalguide.guide.ActivityTimer.lengthsText(m));
    }

    /** Moves Now past the current step and starts the next one's timer. */
    void nextActivity()
    {
        com.goalguide.guide.Guide g = com.goalguide.guide.Guide.current();
        String what = com.goalguide.guide.Guide.TIMER.what();
        if (what == null && g != null && g.now != null) what = g.now.what;
        if (what != null) skipped.add(com.goalguide.guide.ActivityTimer.key(what));
        com.goalguide.guide.Guide.TIMER.stop();
        startNextAfterPlan = true;
        replan();
    }

    /** The timer ran out: one RuneLite notification. */
    void timeIsUp()
    {
        com.goalguide.guide.Guide g = com.goalguide.guide.Guide.current();
        String what = com.goalguide.guide.Guide.TIMER.what();
        String next = g == null ? null : g.next != null && g.now != null && g.now.what.equals(what) ? g.next.what : g.now == null ? null : g.now.what;
        notifier.accept("Goal Guide: time is up for " + what + "." + (next == null ? "" : " Next: " + next + "."));
    }

    // ---- runs tab 
    void setRun(String run, boolean on)
    {
        savePrefs(prefs.withRun(run, on));
    }

    void setStop(String run, String stop, boolean on)
    {
        savePrefs(prefs.withStop(run, stop, on));
    }

    /** Ticks a daily shop run as done today (it clears at the daily reset). */
    void setDaily(String key, boolean done)
    {
        dailies = com.goalguide.guide.Dailies.tick(dailies, System.currentTimeMillis(), key, done);
        String p = profile;
        if (p != null) config.setConfiguration(GROUP, p, DAILIES, dailies);
        replan();
    }

    private void savePrefs(com.goalguide.decide.RunPrefs next)
    {
        prefs = next;
        String p = profile;
        if (p != null)
        {
            config.setConfiguration(GROUP, p, RUNS_OFF, next.runsText());
            config.setConfiguration(GROUP, p, STOPS_OFF, next.stopsText());
        }
        replan();
    }

    // ---- bank button

    /** Shows the Now step's items in the bank (Bank Tags). {@code said} gets why not, or null. */
    void showInBank(Consumer<String> said)
    {
        net.runelite.client.plugins.PluginManager p = plugins;
        onClient.accept(() -> said.accept(com.goalguide.guide.BankFilter.toggle(p)));
    }

    // ---- items tab: Item Roots inside the guide

    /** The Item Roots trace for this account (an item or a quest), as html. Off the client thread. */
    void trace(String text, Consumer<String> show)
    {
        executor.execute(() ->
        {
            com.goalguide.items.ItemData d = ItemDb.get();
            if (d == null) { show.accept("<i>" + esc(ItemDb.status()) + "</i>"); return; }
            AccountState st = Know.state();
            ItemTree.Account a = st == null ? new ItemTree.Account() : st.toAccount();
            String who = st == null ? "an account with nothing (log in once so it knows yours)" : st.name == null ? "your account" : st.name;
            String quest = ItemRootsPlugin.questNamed(text);
            if (quest != null) { show.accept(ItemRootsPlugin.questHtml(d, a, st == null ? 0 : st.questPoints, quest, who)); return; }
            if (!d.known(text)) { show.accept("No item or quest called <b>" + esc(text) + "</b>. Use the full name, e.g. Prayer potion(4) or Priest in Peril."); return; }
            show.accept(ItemRootsPlugin.html(d, a, ItemTree.trace(d, a, text), who));
        });
    }

    static String knowBox()
    {
        return ItemRootsPlugin.knowHtml(Know.state(), Know.live(), System.currentTimeMillis());
    }

    private static Map<String, AccountState.Timer> timers(AccountState st)
    {
        synchronized (st) { return new LinkedHashMap<>(st.timers); }
    }

    static int sittingOf(String s)
    {
        try { int m = Integer.parseInt(s == null ? "" : s.trim()); for (int x : SITTINGS) if (x == m) return m; }
        catch (NumberFormatException ignored) { }
        return 60;
    }

    /** One debug line per change of the "now" pick. */
    private void log(String profile, Decide.Decision d)
    {
        Decide.Pick p = d.now();
        String said = p == null ? "nothing to do" : p.say();
        if (said.equals(lastSaid) || profile == null) return;
        lastSaid = said;
        log.debug("Now: {}{}", said, d.guards.isEmpty() ? "" : " | guards: " + String.join(" ", d.guards));
    }

    /** "What now": the pick, why, the sitting and the guards. */
    static String decideHtml(Decide.Decision d, int sitting)
    {
        StringBuilder b = new StringBuilder("<b>What now</b><br>");
        Decide.Pick p = d.now();
        if (p == null) b.append("<font color='#a0a0a0'>Nothing to do: add a goal, or wait for a timer.</font><br>");
        else b.append("<font color='#ffffff'>").append(esc(p.say())).append("</font><br>");
        if (!d.sitting.isEmpty())
        {
            b.append("<br><b>Your ").append(hours(sitting / 60.0)).append(":</b><br>");
            for (Decide.Block k : d.sitting)
                b.append("<font color='#a0a0a0'>+").append(k.start).append(" min</font> ").append(esc(k.pick.what))
                    .append(" <font color='#a0a0a0'>").append(k.minutes).append(" min</font>")
                    .append("<br>");
        }
        for (String g : d.guards) b.append("<font color='#f0c070'>").append(esc(g)).append("</font><br>");
        return b.toString();
    }

    /** One row per unlock: what it opens and the goals it needs. Biggest first, at most 6. */
    static final class IdeaRow
    {
        final String text;
        final List<String> needs;

        IdeaRow(String text, List<String> needs)
        {
            this.text = text;
            this.needs = needs;
        }
    }

    static List<IdeaRow> ideaRows(List<com.goalguide.decide.PatchTravel.Idea> ideas, List<Goal> goals)
    {
        Map<String, List<com.goalguide.decide.PatchTravel.Idea>> byNeed = new LinkedHashMap<>();
        for (com.goalguide.decide.PatchTravel.Idea i : ideas)
            byNeed.computeIfAbsent(i.run() + "|" + String.join(", ", i.needs()), k -> new ArrayList<>()).add(i);
        List<List<com.goalguide.decide.PatchTravel.Idea>> groups = new ArrayList<>(byNeed.values());
        groups.sort((x, y) -> y.size() != x.size() ? y.size() - x.size() : x.get(0).needs().size() - y.get(0).needs().size());
        List<IdeaRow> out = new ArrayList<>();
        for (List<com.goalguide.decide.PatchTravel.Idea> g : groups)
        {
            if (out.size() >= 6) break;
            List<String> patches = new ArrayList<>();
            for (com.goalguide.decide.PatchTravel.Idea i : g)
                patches.add(com.goalguide.decide.PatchTravel.say(i.patch(), i.ticks()));
            List<String> needs = new ArrayList<>();
            for (String n : g.get(0).needs())
            {
                Goal goal = Goal.parse(n, ItemRootsPlugin::questNamed);
                if (goal == null || !goals.contains(goal)) needs.add(n);
            }
            if (needs.isEmpty()) continue;
            String run = com.goalguide.decide.Decide.runName(g.get(0).run());
            out.add(new IdeaRow(run + ": " + String.join(", ", patches) + ". Needs " + String.join(", ", needs) + ".", needs));
        }
        return out;
    }

    /** Adds every need of an unlock as a goal. */
    void addAll(List<String> needs)
    {
        for (String n : needs) add(n);
    }

    void setSitting(int minutes)
    {
        sitting = minutes;
        String p = profile;
        if (p != null) config.setConfiguration(GROUP, p, SITTING, String.valueOf(minutes));
        replan();
    }

    static String html(AccountState st, List<Goal> goals)
    {
        if (goals.isEmpty()) return "<font color='#a0a0a0'>No goals yet. Type one above: <i>Fishing 70</i>, <i>Priest in Peril</i>, <i>Ardougne medium</i> or <i>1000 raw karambwan</i>.</font>";
        ItemTree.Account a = st.toAccount();
        Planner.Route r = Planner.plan(QuestData.get(), ItemDb.get(), a, st.questPoints, goals);
        return routeHtml(r);
    }

    static String routeHtml(Planner.Route r)
    {
        StringBuilder b = new StringBuilder(routeHead(r));
        if (!r.steps.isEmpty())
        {
            int i = 1;
            for (Planner.Step s : r.steps)
            {
                String color = s.kind == Planner.Kind.TRAIN ? "#f0c070" : "#ffffff";
                b.append("<b>").append(i++).append(". <font color='").append(color).append("'>").append(verb(s)).append(esc(s.what)).append("</font></b>");
                if (s.hours > 0) b.append(" <font color='#a0a0a0'>").append(hours(s.hours)).append("</font>");
                b.append("<br>");
                if (s.how != null) b.append("<font color='#a0a0a0'>").append(esc(s.how)).append("</font>");
                b.append("<br>");
            }
        }
        return b.append(routeTail(r)).toString();
    }

    /** The route's title: how many steps and hours, or that every goal is met. */
    static String routeHead(Planner.Route r)
    {
        if (r.steps.isEmpty()) return "<font color='#7fd17f'>&#10003; Every goal is met.</font><br>";
        StringBuilder b = new StringBuilder(Panel.head("Route"));
        b.append("<b>").append(r.steps.size()).append(r.steps.size() == 1 ? " step" : " steps").append(", about ").append(hours(r.hours)).append("</b>");
        if (r.unknown > 0) b.append(" <font color='#a0a0a0'>+ ").append(r.unknown).append(r.unknown == 1 ? " step" : " steps").append(" with no rate</font>");
        b.append("<br><font color='#a0a0a0'>Click a step to open it.</font><br>");
        return b.toString();
    }

    /** Under the steps: the filler, the Grand Exchange total and the goals met. */
    static String routeTail(Planner.Route r)
    {
        StringBuilder b = new StringBuilder();
        if (!r.filler.isEmpty())
        {
            b.append(Panel.head("Train between steps")).append("<font color='#a0a0a0'>(the rest of each skill, in mixed sessions)</font><br>");
            for (Planner.Filler f : r.filler)
            {
                b.append("<font color='#f0c070'>").append(esc(f.what())).append("</font>");
                if (f.hours > 0) b.append(" <font color='#a0a0a0'>").append(hours(f.hours)).append("</font>");
                if (f.byWhen != null) b.append(" <font color='#a0a0a0'>(").append(esc(f.byWhen)).append(")</font>");
                b.append("<br>");
            }
        }
        if (r.ge > 0) b.append(Panel.head("Grand Exchange")).append("<font color='#8fc8f0'>About ").append(Prices.gp(r.ge)).append(" in all.</font><br>");
        if (!r.done.isEmpty()) b.append(Panel.head("Done")).append("<font color='#7fd17f'>").append(esc(String.join(", ", r.done))).append("</font>");
        return b.toString();
    }

    /** Step cards: an opened step's sections, worked out off the client thread. */
    void detail(Planner.Step s, Consumer<String> show)
    {
        executor.execute(() ->
        {
            AccountState st = Know.state();
            ItemTree.Account a = st == null ? new ItemTree.Account() : st.toAccount();
            String who = st == null || st.name == null ? "your account" : st.name;
            try
            {
                show.accept(StepDetail.html(s, a, st == null ? Map.of() : st.items(), item -> source(a, item), ItemDb.get(), who));
            }
            catch (RuntimeException e)
            {
                log.warn("step detail", e);
                show.accept("<font color='#f08a78'>Could not work this step out.</font>");
            }
        });
    }

    private static String verb(Planner.Step s)
    {
        switch (s.kind)
        {
            case TRAIN: return "Train ";
            case GET: return "Get ";
            case STOCK: return "Stock ";
            default: return "";
        }
    }

    /** "40 min", "5.2 h", "3.1 days". */
    static String hours(double h)
    {
        if (h < 1) return Math.max(1, Math.round(h * 60)) + " min";
        if (h < 48) return String.format("%.1f h", h);
        return String.format("%.1f days", h / 24);
    }

    // ---- edits from the panel (Swing thread)

    /** True when the text was a goal and it was added. */
    boolean add(String text)
    {
        Goal g = Goal.parse(text, ItemRootsPlugin::questNamed);
        if (g == null || profile == null || goals.contains(g)) return false;
        List<Goal> next = new ArrayList<>(goals);
        next.add(g);
        save(next);
        return true;
    }

    void remove(int i)
    {
        List<Goal> next = new ArrayList<>(goals);
        if (i < 0 || i >= next.size()) return;
        next.remove(i);
        save(next);
    }

    /** Moves a goal to the top: the first goal is the focus. */
    void pin(int i)
    {
        List<Goal> next = new ArrayList<>(goals);
        if (i <= 0 || i >= next.size()) return;
        next.add(0, next.remove(i));
        save(next);
    }

    private void save(List<Goal> next)
    {
        goals = next;
        String p = profile;
        if (p != null) config.setConfiguration(GROUP, p, KEY, Goal.encodeAll(next));
        replan();
    }

    static String esc(String s)
    {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static final class Panel extends PluginPanel
    {
        private final GoalsView view;
        private final JPanel list = new JPanel();
        /** The route: a title, one card per step click to open), then the filler. */
        private final JPanel route = new JPanel();
        private String routeShown;
        private final java.util.Set<String> opened = new java.util.HashSet<>();
        private final Map<String, String> details = new java.util.HashMap<>();
        private final JLabel hint = new JLabel();
        private final JPanel ideas = new JPanel();
        private final javax.swing.JComboBox<String> sit = new javax.swing.JComboBox<>(new String[]{"I have 30 min", "I have 1 hour", "I have 2 hours", "I have 3 hours"});
        private boolean quiet;
        /** The guide: Now, Next, recommendations, and the wiki quick guide button. */
        private final JLabel guide = new JLabel();
        private final JButton questHelper = new JButton("Wiki quick guide");
        private final JLabel questHint = new JLabel();
        private final javax.swing.JCheckBox onScreen = new javax.swing.JCheckBox("Show the guide on the game screen");
        private volatile String questKey;
        /** The Now step's items (carried / bank / missing) and the bank button. */
        private final JLabel items = new JLabel();
        private final JButton bank = new JButton("Show in bank (on / off)");
        private final JLabel bankHint = new JLabel();
        /** The activity timer. */
        private final JPanel timerBox = new JPanel();
        private final JLabel clock = new JLabel();
        private ActivityState shownTimer;
        private String shownNow;
        private final javax.swing.Timer tick;
        private int ticks;
        /** Tabs: Goals, Runs, Items. */
        private final java.awt.CardLayout cards = new java.awt.CardLayout();
        private final JPanel body = new JPanel(cards);
        private final JPanel runs = new JPanel();
        private String runsShown;
        private final LinkText traced = new LinkText(200);
        private final JLabel know = new JLabel();

        /** What the timer box shows: the timer's state and, when idle, the step it would start. */
        private static final class ActivityState
        {
            final com.goalguide.guide.ActivityTimer.State state;
            final String what;

            ActivityState(com.goalguide.guide.ActivityTimer.State state, String what)
            {
                this.state = state;
                this.what = what;
            }

            boolean same(ActivityState o)
            {
                return o != null && o.state == state && java.util.Objects.equals(o.what, what);
            }
        }

        Panel(GoalsView view)
        {
            super(true);
            this.view = view;
            setBackground(ColorScheme.DARK_GRAY_COLOR);
            setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            setLayout(new BorderLayout());
            JLabel title = new JLabel("Goal Guide");
            title.setFont(FontManager.getRunescapeBoldFont());
            title.setForeground(Color.WHITE);
            JPanel top = new JPanel();
            top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
            top.setBackground(ColorScheme.DARK_GRAY_COLOR);
            JPanel tabs = new JPanel(new java.awt.GridLayout(1, 3, 2, 0));
            tabs.setBackground(ColorScheme.DARK_GRAY_COLOR);
            javax.swing.ButtonGroup group = new javax.swing.ButtonGroup();
            for (String t : new String[]{"Goals", "Runs", "Items"})
            {
                javax.swing.JToggleButton b = new javax.swing.JToggleButton(t, t.equals("Goals"));
                b.setFont(FontManager.getRunescapeSmallFont());
                b.addActionListener(e -> { cards.show(body, t); if (t.equals("Items")) know.setText(html(knowBox())); });
                group.add(b);
                tabs.add(b);
            }
            tabs.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
            title.setAlignmentX(LEFT_ALIGNMENT);
            tabs.setAlignmentX(LEFT_ALIGNMENT);
            top.add(title);
            top.add(Box.createVerticalStrut(6));
            top.add(tabs);
            top.add(Box.createVerticalStrut(8));
            add(top, BorderLayout.NORTH);
            body.setBackground(ColorScheme.DARK_GRAY_COLOR);
            body.add(goalsTab(), "Goals");
            body.add(runsTab(), "Runs");
            body.add(itemsTab(), "Items");
            add(body, BorderLayout.CENTER);
            tick = new javax.swing.Timer(1000, e -> everySecond());
            tick.start();
        }

        void close()
        {
            tick.stop();
        }

        private JPanel goalsTab()
        {
            JPanel head = column(ColorScheme.DARK_GRAY_COLOR);
            JTextField field = new JTextField();
            JButton add = new JButton("Add goal");
            Runnable run = () ->
            {
                String t = field.getText().trim();
                if (t.isEmpty()) return;
                if (view.add(t)) { field.setText(""); hint.setText(""); }
                else hint.setText("<html><font color='#f08a78'>Not a goal I know. Try Fishing 70, Priest in Peril, Ardougne medium, 1000 raw karambwan.</font></html>");
            };
            field.addActionListener(e -> run.run());
            add.addActionListener(e -> run.run());
            hint.setFont(FontManager.getRunescapeSmallFont());
            list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
            list.setBackground(ColorScheme.DARK_GRAY_COLOR);
            sit.addActionListener(e -> { if (!quiet && sit.getSelectedIndex() >= 0) view.setSitting(SITTINGS[sit.getSelectedIndex()]); });
            sit.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
            for (javax.swing.JComponent c : new javax.swing.JComponent[]{field, add, hint, list, sit}) c.setAlignmentX(LEFT_ALIGNMENT);
            head.add(field);
            head.add(Box.createVerticalStrut(4));
            head.add(add);
            head.add(hint);
            head.add(Box.createVerticalStrut(6));
            head.add(list);
            head.add(Box.createVerticalStrut(6));
            head.add(sit);
            head.add(Box.createVerticalStrut(8));
            route.setLayout(new BoxLayout(route, BoxLayout.Y_AXIS));
            route.setBackground(ColorScheme.DARK_GRAY_COLOR);
            ideas.setLayout(new BoxLayout(ideas, BoxLayout.Y_AXIS));
            ideas.setBackground(ColorScheme.DARK_GRAY_COLOR);
            JPanel guideBox = column(ColorScheme.DARKER_GRAY_COLOR);
            guideBox.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
            guide.setFont(FontManager.getRunescapeSmallFont());
            guide.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            questHint.setFont(FontManager.getRunescapeSmallFont());
            questHelper.setVisible(false);
            questHelper.addActionListener(e ->
            {
                String k = questKey;
                if (k == null) return;
                net.runelite.client.util.LinkBrowser.browse(StepDetail.questWikiUrl(k));
            });
            items.setFont(FontManager.getRunescapeSmallFont());
            items.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            bankHint.setFont(FontManager.getRunescapeSmallFont());
            bank.setVisible(false);
            bank.setToolTipText("Turns on or off: the bank shows only what this step and your switched-on runs use. Same as the GG button in the bank (right-click it for one alone). Needs RuneLite's Bank Tags plugin on.");
            bank.addActionListener(e -> view.showInBank(why -> SwingUtilities.invokeLater(() ->
                bankHint.setText(why == null ? "" : "<html><font color='#f08a78'>" + esc(why) + "</font></html>"))));
            timerBox.setLayout(new BoxLayout(timerBox, BoxLayout.Y_AXIS));
            timerBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            clock.setFont(FontManager.getRunescapeSmallFont());
            clock.setForeground(Color.WHITE);
            onScreen.setSelected(view.guideOnScreen());
            onScreen.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            onScreen.setFont(FontManager.getRunescapeSmallFont());
            onScreen.addActionListener(e -> view.setGuideOnScreen(onScreen.isSelected()));
            for (javax.swing.JComponent c : new javax.swing.JComponent[]{guide, timerBox, items, bank, bankHint, questHelper, questHint, onScreen})
            {
                c.setAlignmentX(LEFT_ALIGNMENT);
                guideBox.add(c);
            }
            JPanel tab = new JPanel(new BorderLayout());
            tab.setBackground(ColorScheme.DARK_GRAY_COLOR);
            JPanel upper = new JPanel(new BorderLayout());
            upper.setBackground(ColorScheme.DARK_GRAY_COLOR);
            upper.add(head, BorderLayout.NORTH);
            upper.add(guideBox, BorderLayout.CENTER);
            upper.add(route, BorderLayout.SOUTH);
            tab.add(upper, BorderLayout.NORTH);
            tab.add(ideas, BorderLayout.CENTER);
            return tab;
        }

        private JPanel runsTab()
        {
            JPanel tab = new JPanel(new BorderLayout());
            tab.setBackground(ColorScheme.DARK_GRAY_COLOR);
            runs.setLayout(new BoxLayout(runs, BoxLayout.Y_AXIS));
            runs.setBackground(ColorScheme.DARK_GRAY_COLOR);
            JLabel l = new JLabel(html("<font color='#a0a0a0'>Log in once so the guide knows your account.</font>"));
            l.setFont(FontManager.getRunescapeSmallFont());
            runs.add(l);
            tab.add(runs, BorderLayout.NORTH);
            return tab;
        }

        private JPanel itemsTab()
        {
            JPanel col = column(ColorScheme.DARK_GRAY_COLOR);
            JLabel about = new JLabel(html("<font color='#a0a0a0'>Type an item or a quest: every way your account can get it, traced to the roots.</font>"));
            about.setFont(FontManager.getRunescapeSmallFont());
            JTextField field = new JTextField();
            JButton go = new JButton("Trace");
            JPanel goal = new JPanel(new BorderLayout(4, 0));
            goal.setBackground(ColorScheme.DARK_GRAY_COLOR);
            JTextField amount = new JTextField("100", 5);
            JButton make = new JButton("Make it a goal");
            make.setToolTipText("Adds a stock goal: keep this many of the item");
            goal.add(amount, BorderLayout.WEST);
            goal.add(make, BorderLayout.CENTER);
            goal.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
            JLabel made = new JLabel();
            made.setFont(FontManager.getRunescapeSmallFont());
            traced.setFont(FontManager.getRunescapeSmallFont());
            traced.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            know.setFont(FontManager.getRunescapeSmallFont());
            know.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            Runnable trace = () ->
            {
                String t = field.getText().trim();
                if (t.isEmpty()) return;
                traced.body("<i>Tracing...</i>");
                view.trace(t, h -> SwingUtilities.invokeLater(() -> traced.body(h)));
            };
            field.addActionListener(e -> trace.run());
            go.addActionListener(e -> trace.run());
            make.addActionListener(e ->
            {
                String t = field.getText().trim();
                int n;
                try { n = Integer.parseInt(amount.getText().trim().replace(",", "")); }
                catch (NumberFormatException x) { n = 0; }
                boolean ok = !t.isEmpty() && n > 0 && ItemRootsPlugin.questNamed(t) == null && view.add(n + " " + t);
                made.setText(html(ok ? "<font color='#7fd17f'>Added: keep " + n + " " + esc(t) + ".</font>"
                    : "<font color='#f08a78'>Type an item name and an amount first.</font>"));
            });
            for (javax.swing.JComponent c : new javax.swing.JComponent[]{about, field, go, goal, made, traced, know}) c.setAlignmentX(LEFT_ALIGNMENT);
            col.add(about);
            col.add(Box.createVerticalStrut(4));
            col.add(field);
            col.add(Box.createVerticalStrut(4));
            col.add(go);
            col.add(Box.createVerticalStrut(6));
            col.add(goal);
            col.add(made);
            col.add(Box.createVerticalStrut(6));
            col.add(traced);
            col.add(Box.createVerticalStrut(10));
            col.add(know);
            JPanel tab = new JPanel(new BorderLayout());
            tab.setBackground(ColorScheme.DARK_GRAY_COLOR);
            tab.add(col, BorderLayout.NORTH);
            return tab;
        }

        private static JPanel column(Color bg)
        {
            JPanel p = new JPanel();
            p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
            p.setBackground(bg);
            return p;
        }

        private static String html(String body)
        {
            return "<html><body style='width:150px'>" + body + "</body></html>";
        }

        // ---- the activity timer

        private void everySecond()
        {
            long now = System.currentTimeMillis();
            com.goalguide.guide.ActivityTimer t = com.goalguide.guide.Guide.TIMER;
            if (t.takeUp(now)) view.timeIsUp();
            com.goalguide.guide.Guide g = com.goalguide.guide.Guide.current();
            com.goalguide.guide.ActivityTimer.State st = t.state(now);
            String what = st == com.goalguide.guide.ActivityTimer.State.IDLE ? (g == null || g.now == null ? null : g.now.what) : t.what();
            ActivityState shown = new ActivityState(st, what);
            if (!shown.same(shownTimer)) { shownTimer = shown; timerUi(shown); }
            if (st == com.goalguide.guide.ActivityTimer.State.RUNNING || st == com.goalguide.guide.ActivityTimer.State.PAUSED)
                clock.setText(html("<b>" + com.goalguide.guide.ActivityTimer.clock(t.left(now)) + "</b> left"
                    + (st == com.goalguide.guide.ActivityTimer.State.PAUSED ? " (paused)" : "") + " <font color='#a0a0a0'>" + esc(what) + "</font>"));
            // Timers count down and the run box moves while nothing else changes
            if (++ticks % 30 == 0) view.replan();
        }

        /** The timer's buttons for its state mock: Start / Pause +5 Stop / Next activity, Same again, Stop here). */
        private void timerUi(ActivityState s)
        {
            com.goalguide.guide.ActivityTimer t = com.goalguide.guide.Guide.TIMER;
            timerBox.removeAll();
            JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 2, 2));
            row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            row.setAlignmentX(LEFT_ALIGNMENT);
            switch (s.state)
            {
                case IDLE:
                {
                    if (s.what == null) break;
                    JLabel len = new JLabel(view.lengthFor(s.what) + " min");
                    len.setFont(FontManager.getRunescapeSmallFont());
                    row.add(small("-", "5 minutes less", () -> { view.setLength(s.what, view.lengthFor(s.what) - 5); len.setText(view.lengthFor(s.what) + " min"); }));
                    row.add(len);
                    row.add(small("+", "5 minutes more", () -> { view.setLength(s.what, view.lengthFor(s.what) + 5); len.setText(view.lengthFor(s.what) + " min"); }));
                    row.add(small("Start timer", "Counts down here and on the game screen, and tells you when time is up",
                        () -> t.start(s.what, view.lengthFor(s.what), System.currentTimeMillis())));
                    break;
                }
                case RUNNING:
                case PAUSED:
                {
                    timerBox.add(clock);
                    boolean paused = s.state == com.goalguide.guide.ActivityTimer.State.PAUSED;
                    row.add(small(paused ? "Resume" : "Pause", "", () -> { if (paused) t.resume(System.currentTimeMillis()); else t.pause(System.currentTimeMillis()); }));
                    row.add(small("+5", "5 minutes more", () -> t.add(5, System.currentTimeMillis())));
                    row.add(small("Stop", "", t::stop));
                    break;
                }
                case UP:
                {
                    JLabel up = new JLabel(html("<font color='#f0c070'><b>Time is up</b></font> <font color='#a0a0a0'>" + esc(s.what) + "</font>"));
                    up.setFont(FontManager.getRunescapeSmallFont());
                    up.setAlignmentX(LEFT_ALIGNMENT);
                    timerBox.add(up);
                    row.add(small("Next activity", "Moves Now to the next step and starts its timer", view::nextActivity));
                    row.add(small("Same again", "Starts this timer again", () -> t.start(t.what(), t.minutes(), System.currentTimeMillis())));
                    row.add(small("Stop here", "", t::stop));
                    break;
                }
                default:
                    break;
            }
            timerBox.add(row);
            timerBox.revalidate();
            timerBox.repaint();
        }

        void showGoals(List<Goal> goals, int sitting)
        {
            SwingUtilities.invokeLater(() ->
            {
                for (int i = 0; i < SITTINGS.length; i++) if (SITTINGS[i] == sitting && sit.getSelectedIndex() != i) { quiet = true; sit.setSelectedIndex(i); quiet = false; }
                list.removeAll();
                for (int i = 0; i < goals.size(); i++)
                {
                    final int idx = i;
                    JPanel row = new JPanel(new BorderLayout());
                    row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                    row.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 2));
                    row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
                    row.setAlignmentX(LEFT_ALIGNMENT);
                    JLabel l = new JLabel((i == 0 ? "★ " : "") + goals.get(i).label());
                    l.setFont(FontManager.getRunescapeSmallFont());
                    l.setForeground(i == 0 ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
                    JPanel buttons = new JPanel();
                    buttons.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                    if (i > 0) buttons.add(small("↑", "Make this the focus", () -> view.pin(idx)));
                    buttons.add(small("✕", "Remove", () -> view.remove(idx)));
                    row.add(l, BorderLayout.CENTER);
                    row.add(buttons, BorderLayout.EAST);
                    list.add(row);
                    list.add(Box.createVerticalStrut(2));
                }
                list.revalidate();
                list.repaint();
            });
        }

        private static JButton small(String text, String tip, Runnable r)
        {
            JButton b = new JButton(text);
            if (!tip.isEmpty()) b.setToolTipText(tip);
            b.setMargin(new java.awt.Insets(0, 4, 0, 4));
            b.addActionListener(e -> r.run());
            return b;
        }

        void showIdeas(List<IdeaRow> rows)
        {
            SwingUtilities.invokeLater(() ->
            {
                ideas.removeAll();
                if (!rows.isEmpty())
                {
                    JLabel h = new JLabel("<html><br><b>Faster runs</b> <font color='#a0a0a0'>(train these and the patch is under 2 min)</font></html>");
                    h.setFont(FontManager.getRunescapeSmallFont());
                    h.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
                    h.setAlignmentX(LEFT_ALIGNMENT);
                    ideas.add(h);
                }
                for (IdeaRow r : rows)
                {
                    JPanel row = new JPanel(new BorderLayout());
                    row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                    row.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 2));
                    row.setAlignmentX(LEFT_ALIGNMENT);
                    JLabel l = new JLabel("<html><body style='width:120px'>" + esc(r.text) + "</body></html>");
                    l.setFont(FontManager.getRunescapeSmallFont());
                    l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
                    row.add(l, BorderLayout.CENTER);
                    JPanel b = new JPanel();
                    b.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                    b.add(small("+", "Add " + String.join(", ", r.needs) + " as goals", () -> view.addAll(r.needs)));
                    row.add(b, BorderLayout.EAST);
                    ideas.add(row);
                    ideas.add(Box.createVerticalStrut(2));
                }
                ideas.revalidate();
                ideas.repaint();
            });
        }

        void showGuide(com.goalguide.guide.Guide g)
        {
            SwingUtilities.invokeLater(() ->
            {
                StringBuilder b = new StringBuilder("<b>Guide</b><br>");
                String key = null;
                if (g == null) b.append("<font color='#a0a0a0'>Add a goal and the guide shows the step to do now.</font>");
                else
                {
                    if (g.now != null)
                    {
                        b.append(head("Do now")).append("<font color='#7fd17f'>").append(esc(g.now.what)).append("</font><br>");
                        if (!g.now.how.isEmpty()) b.append("<font color='#a0a0a0'>").append(esc(g.now.how)).append("</font><br>");
                        key = g.now.quest;
                    }
                    if (g.skipped > 0) b.append("<font color='#a0a0a0'>(moved past ").append(g.skipped).append(g.skipped == 1 ? " step" : " steps").append(" this session)</font><br>");
                    if (g.next != null)
                    {
                        b.append(head("Then")).append(esc(g.next.what)).append("<br>");
                        if (key == null) key = g.next.quest;
                    }
                    if (g.runWhat != null)
                        b.append(head("Timed run")).append("<font color='#8fc8f0'>").append(esc(g.runWhat)).append("</font><br>")
                            .append("<font color='#a0a0a0'>").append(esc(g.runWhy == null ? "" : g.runWhy)).append("</font><br>");
                    if (!g.filler.isEmpty()) b.append(head("Train between steps"));
                    for (String r : g.filler) b.append("<font color='#8fc8f0'>").append(esc(r)).append("</font><br>");
                }
                guide.setText("<html><body style='width:150px'>" + b + "</body></html>");
                List<com.goalguide.guide.StepItems.Item> its = g == null ? List.of() : g.items;
                items.setText(its.isEmpty() ? "" : html(itemsHtml(its)));
                bank.setVisible(!com.goalguide.guide.BankFilter.choices().isEmpty());
                if (!bank.isVisible()) bankHint.setText("");
                questKey = key;
                questHelper.setVisible(key != null);
                if (key != null) questHelper.setText(com.goalguide.quests.QuestPlan.pretty(key) + ": wiki quick guide");
                questHint.setText("");
                String now = g == null || g.now == null ? null : g.now.what;
                if (!java.util.Objects.equals(now, shownNow)) { shownNow = now; shownTimer = null; }
            });
        }

        /** A section title in the guide box. */
        static String head(String t)
        {
            return "<br><font color='#ff981f'><b>" + esc(t) + "</b></font><br>";
        }

        /** The item list: green carried, yellow in a storage, red missing with where to get it, grey to bring. */
        static String itemsHtml(List<com.goalguide.guide.StepItems.Item> its)
        {
            StringBuilder b = new StringBuilder(head("Items for this step"));
            for (com.goalguide.guide.StepItems.Item i : its)
            {
                com.goalguide.guide.StepItems.Has h = i.has;
                String c = h == com.goalguide.guide.StepItems.Has.CARRIED ? "#7fd17f"
                    : h == com.goalguide.guide.StepItems.Has.STORED ? "#f0c070"
                    : h == com.goalguide.guide.StepItems.Has.REMINDER || h == com.goalguide.guide.StepItems.Has.USED ? "#a0a0a0" : "#f08a78";
                String mark = h == com.goalguide.guide.StepItems.Has.CARRIED ? "&#10003; "
                    : h == com.goalguide.guide.StepItems.Has.STORED ? "&#9675; "
                    : h == com.goalguide.guide.StepItems.Has.REMINDER || h == com.goalguide.guide.StepItems.Has.USED ? "&bull; " : "&#10007; ";
                b.append("<font color='").append(c).append("'>").append(mark).append(esc(i.say())).append("</font><br>");
            }
            return b.toString();
        }

        /** The Runs tab: each run and its stops, switched on or off, with how each stop is now. */
        void showRuns(ItemTree.Account a, com.goalguide.decide.RunPrefs prefs, AccountState st, java.util.Set<String> dailyDone)
        {
            long nowSec = System.currentTimeMillis() / 1000;
            List<com.goalguide.guide.Dailies.Daily> daily = com.goalguide.guide.Dailies.open(a);
            StringBuilder key = new StringBuilder(prefs.runsText() + "#" + prefs.stopsText() + "#" + dailyDone);
            for (com.goalguide.guide.Dailies.Daily dy : daily) key.append(dy.say(a));
            List<Object[]> rows = new ArrayList<>();
            for (Map.Entry<String, List<com.goalguide.decide.PatchTravel.Patch>> run : com.goalguide.decide.PatchTravel.RUNS.entrySet())
            {
                String name = run.getKey();
                boolean birds = AccountState.BIRDS.equals(name);
                Map<String, AccountState.Timer> stops = birds ? new LinkedHashMap<>() : st.stops(name);
                if (birds && st.timer(AccountState.BIRDS) != null) stops.put("Fossil Island", st.timer(AccountState.BIRDS));
                for (com.goalguide.decide.PatchTravel.Patch p : run.getValue())
                {
                    String closed = com.goalguide.decide.RunPrefs.closed(p, a);
                    AccountState.Timer t = stops.get(p.name());
                    String state = closed != null ? closed : t == null ? "not seen yet" : AccountState.when(t, nowSec);
                    rows.add(new Object[]{name, p.name(), closed == null, state});
                    key.append('|').append(name).append(p.name()).append(state);
                }
            }
            String k = key.toString();
            SwingUtilities.invokeLater(() ->
            {
                if (k.equals(runsShown)) return;
                runsShown = k;
                runs.removeAll();
                JLabel about = new JLabel(html("<font color='#a0a0a0'>Switch off the runs and stops you don't do. A run is offered when at least "
                    + "3 of its stops are open and on (bird houses: Fossil Island). States come from RuneLite's Time Tracking plugin.</font>"));
                about.setFont(FontManager.getRunescapeSmallFont());
                about.setAlignmentX(LEFT_ALIGNMENT);
                runs.add(about);
                String last = null;
                for (Object[] r : rows)
                {
                    String run = (String) r[0], stop = (String) r[1], state = (String) r[3];
                    boolean open = (Boolean) r[2];
                    if (!run.equals(last))
                    {
                        last = run;
                        javax.swing.JCheckBox c = new javax.swing.JCheckBox(Decide.runName(run), prefs.on(run));
                        c.setFont(FontManager.getRunescapeBoldFont());
                        c.setBackground(ColorScheme.DARK_GRAY_COLOR);
                        c.setAlignmentX(LEFT_ALIGNMENT);
                        c.addActionListener(e -> view.setRun(run, c.isSelected()));
                        runs.add(Box.createVerticalStrut(6));
                        runs.add(c);
                    }
                    // A run switched off hides its stops
                    if (!prefs.on(run)) continue;
                    JPanel row = new JPanel(new BorderLayout());
                    row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                    row.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 4));
                    row.setAlignmentX(LEFT_ALIGNMENT);
                    row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
                    javax.swing.JCheckBox c = new javax.swing.JCheckBox(stop, open && prefs.on(run, stop));
                    c.setEnabled(open && prefs.on(run));
                    c.setFont(FontManager.getRunescapeSmallFont());
                    c.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                    c.addActionListener(e -> view.setStop(run, stop, c.isSelected()));
                    JLabel s = new JLabel(state);
                    s.setFont(FontManager.getRunescapeSmallFont());
                    s.setForeground(!open ? ColorScheme.MEDIUM_GRAY_COLOR : state.equals("ready") ? new Color(0x7f, 0xd1, 0x7f)
                        : state.equals(AccountState.Timer.DISEASED) || state.equals(AccountState.Timer.DEAD) ? new Color(0xf0, 0x8a, 0x78) : ColorScheme.LIGHT_GRAY_COLOR);
                    row.add(c, BorderLayout.CENTER);
                    row.add(s, BorderLayout.EAST);
                    runs.add(row);
                }
                // The once-a-day shop stock this account can claim, ticked when done
                if (!daily.isEmpty())
                {
                    JLabel h = new JLabel(html("<font color='#ff981f'>DAILY SHOP RUNS</font> <font color='#a0a0a0'>reset 00:00 UTC</font>"));
                    h.setFont(FontManager.getRunescapeSmallFont());
                    h.setAlignmentX(LEFT_ALIGNMENT);
                    h.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0x5a, 0x5a, 0x5a)),
                        BorderFactory.createEmptyBorder(4, 0, 2, 0)));
                    runs.add(Box.createVerticalStrut(8));
                    runs.add(h);
                    for (com.goalguide.guide.Dailies.Daily dy : daily)
                    {
                        boolean done = dailyDone.contains(dy.key);
                        javax.swing.JCheckBox c = new javax.swing.JCheckBox(html((done ? "<font color='#7fd17f'>" : "<font color='#ffffff'>")
                            + esc(dy.say(a)) + "</font>"), done);
                        c.setFont(FontManager.getRunescapeSmallFont());
                        c.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                        c.setAlignmentX(LEFT_ALIGNMENT);
                        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height + 6));
                        String more = dy.next(a);
                        c.setToolTipText(more == null ? "Done today? Tick it; it clears at the daily reset." : "More with " + more);
                        c.addActionListener(e -> view.setDaily(dy.key, c.isSelected()));
                        runs.add(c);
                    }
                }
                runs.revalidate();
                runs.repaint();
            });
        }

        void showRoute(String top, Planner.Route r, String noRoute)
        {
            SwingUtilities.invokeLater(() ->
            {
                String sig = top + "|" + (r == null ? noRoute : routeHtml(r));
                if (sig.equals(routeShown)) return;
                routeShown = sig;
                if (details.size() > 60) details.clear();
                route.removeAll();
                route.add(text(top));
                if (r == null) { if (noRoute != null) route.add(text("<br>" + noRoute)); }
                else
                {
                    route.add(text(routeHead(r)));
                    int i = 1;
                    for (Planner.Step s : r.steps)
                    {
                        route.add(Box.createVerticalStrut(3));
                        route.add(card(i++, s));
                    }
                    route.add(text(routeTail(r)));
                }
                route.revalidate();
                route.repaint();
            });
        }

        private static JLabel text(String h)
        {
            JLabel l = new JLabel(html(h));
            l.setFont(FontManager.getRunescapeSmallFont());
            l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            l.setAlignmentX(LEFT_ALIGNMENT);
            return l;
        }

        /** One step: its title line, and when opened its sections (and the quest buttons). */
        private JPanel card(int n, Planner.Step s)
        {
            JPanel c = column(ColorScheme.DARKER_GRAY_COLOR);
            c.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
            c.setAlignmentX(LEFT_ALIGNMENT);
            String key = s.kind + " " + s.what;
            JLabel title = new JLabel();
            title.setFont(FontManager.getRunescapeSmallFont());
            title.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            title.setAlignmentX(LEFT_ALIGNMENT);
            title.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            JPanel inside = column(ColorScheme.DARKER_GRAY_COLOR);
            inside.setAlignmentX(LEFT_ALIGNMENT);
            Runnable paint = () ->
            {
                boolean open = opened.contains(key);
                title.setText(cardHtml(cardTitle(n, s, open)));
                inside.removeAll();
                if (open) fill(inside, s);
                inside.setVisible(open);
                c.revalidate();
                c.repaint();
            };
            title.addMouseListener(new java.awt.event.MouseAdapter()
            {
                @Override public void mouseClicked(java.awt.event.MouseEvent e)
                {
                    if (!opened.remove(key)) opened.add(key);
                    paint.run();
                }
            });
            c.add(title);
            c.add(inside);
            paint.run();
            return c;
        }

        /** "+ 3. Get Ruby  1 min": + closed, - open; training yellow, quests blue, items white. */
        static String cardTitle(int n, Planner.Step s, boolean open)
        {
            String color = s.kind == Planner.Kind.TRAIN ? "#f0c070" : s.kind == Planner.Kind.QUEST ? "#8fc8f0" : "#ffffff";
            StringBuilder b = new StringBuilder("<font color='#a0a0a0'>").append(open ? "&ndash;" : "+").append("</font> ");
            b.append("<b>").append(n).append(". <font color='").append(color).append("'>").append(esc(StepDetail.title(s))).append("</font></b>");
            if (s.hours > 0) b.append(" <font color='#a0a0a0'>").append(hours(s.hours)).append("</font>");
            else if (s.hours < 0 && s.kind != Planner.Kind.QUEST) b.append(" <font color='#a0a0a0'>(no rate)</font>");
            return b.toString();
        }

        private void fill(JPanel inside, Planner.Step s)
        {
            String key = s.toString();
            // The step, its items and its quest link to the wiki (clickable)
            LinkText l = new LinkText(CARD_WIDTH);
            l.body(details.getOrDefault(key, "<i>Working it out...</i>"));
            inside.add(l);
            view.detail(s, h -> SwingUtilities.invokeLater(() -> { details.put(key, h); l.body(h); inside.revalidate(); }));
            // "I rather get it myself": the item's route in a small box on the game screen
            if ((s.kind == Planner.Kind.GET || s.kind == Planner.Kind.STOCK) && s.item != null)
            {
                JButton get = new JButton("Get it myself (on screen)");
                get.setToolTipText("Shows how to get or make it yourself in a small box on the game screen; right-click the box to close");
                get.setAlignmentX(LEFT_ALIGNMENT);
                get.addActionListener(e -> com.goalguide.guide.GetBox.open(StepDetail.cap(s.item)));
                inside.add(Box.createVerticalStrut(3));
                inside.add(get);
            }
            if (s.kind != Planner.Kind.QUEST) return;
            JLabel why = new JLabel();
            why.setFont(FontManager.getRunescapeSmallFont());
            JButton wiki = new JButton(s.quest != null ? "Wiki quick guide" : "Wiki page");
            wiki.setAlignmentX(LEFT_ALIGNMENT);
            wiki.addActionListener(e -> net.runelite.client.util.LinkBrowser.browse(StepDetail.wikiUrl(s)));
            inside.add(Box.createVerticalStrut(3));
            inside.add(wiki);
            why.setAlignmentX(LEFT_ALIGNMENT);
            inside.add(why);
        }

        /** The card's text width in pixels. */
        static final int CARD_WIDTH = 190;

        /** Card text is a little narrower than the panel's (the card has a border). */
        private static String cardHtml(String body)
        {
            return "<html><body style='width:135px'>" + body + "</body></html>";
        }
    }
}
