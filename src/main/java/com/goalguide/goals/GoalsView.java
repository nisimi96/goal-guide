package com.goalguide.goals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import com.goalguide.facts.Method;
import com.goalguide.facts.MethodTables;
import com.goalguide.facts.Runs;
import com.goalguide.guide.Guide;
import com.goalguide.guide.Icons;
import com.goalguide.guide.StepItems;
import com.goalguide.items.ItemData;
import com.goalguide.items.ItemDb;
import com.goalguide.items.ItemTree;
import com.goalguide.know.AccountState;
import com.goalguide.know.Know;
import com.goalguide.quests.QuestData;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

/**
 * Keeps the goals (saved per account), plans the route off the client thread on every account change,
 * and hands the result to the sidebar and the screen box.
 */
@Slf4j
public final class GoalsView
{
    static final String GROUP = "goalguide", KEY = "goals", GUIDE_ON_SCREEN = "guideOnScreen", RUNS_OFF = "runsOff", STOPS_OFF = "stopsOff";

    private final ConfigManager config;
    private final ClientToolbar toolbar;
    private final ExecutorService executor;
    private final Consumer<AccountState> onKnow = s -> replan();
    private GoalsPanel panel;
    private NavigationButton nav;
    private volatile List<Goal> goals = new ArrayList<>();
    private volatile String profile;
    private volatile boolean queued;

    public GoalsView(ConfigManager config, ClientToolbar toolbar, ExecutorService executor)
    {
        this.config = config;
        this.toolbar = toolbar;
        this.executor = executor;
    }

    /** The box on the game screen: on unless the sidebar's checkbox is off. */
    public boolean guideOnScreen()
    {
        return !"false".equals(config.getConfiguration(GROUP, GUIDE_ON_SCREEN));
    }

    void setGuideOnScreen(boolean on)
    {
        config.setConfiguration(GROUP, GUIDE_ON_SCREEN, String.valueOf(on));
    }

    public void start()
    {
        panel = new GoalsPanel(this);
        nav = NavigationButton.builder().tooltip("Goal Guide").icon(Icons.initials("GG")).priority(11).panel(panel).build();
        toolbar.addNavigation(nav);
        Know.listen(onKnow);
        ItemDb.load(d -> replan());
        replan();
    }

    public void stop()
    {
        Know.unlisten(onKnow);
        if (nav != null) toolbar.removeNavigation(nav);
        nav = null;
        panel = null;
        Guide.set(null);
        Runs.switches(null);
    }

    /** Plans again off the client thread. At most one plan waits at a time. */
    void replan()
    {
        if (queued) return;
        queued = true;
        executor.execute(() ->
        {
            queued = false;
            try { plan(); }
            catch (RuntimeException | AssertionError e)
            {
                log.warn("Goal Guide: planning failed", e);
                GoalsPanel pn = panel;
                if (pn != null) pn.showRoute(null, Text.color(Text.RED, "Something went wrong working out the route. It's in the RuneLite log."));
            }
        });
    }

    private void plan()
    {
        AccountState st = Know.state();
        String p = st == null ? null : st.profile;
        if (p != null && !p.equals(profile))
        {
            profile = p;
            goals = Goal.decodeAll(config.getConfiguration(GROUP, p, KEY));
            Runs.switches(Runs.Switches.decode(config.getConfiguration(GROUP, p, RUNS_OFF), config.getConfiguration(GROUP, p, STOPS_OFF)));
        }
        GoalsPanel pn = panel;
        if (pn == null) return;
        pn.showGoals(goals);
        if (st == null)
        {
            Guide.set(null);
            pn.showGuide(null);
            pn.showRoute(null, Text.color(Text.YELLOW, "Log in once with the plugin on so it can see your account."));
            return;
        }
        ItemTree.Account a = st.toAccount();
        pn.showRuns(a, Runs.switches());
        if (goals.isEmpty())
        {
            Guide.set(null);
            pn.showGuide(null);
            pn.showRoute(null, Text.color(Text.GREY, "No goals yet. Try Fishing 70, Priest in Peril, Ardougne medium or 1000 raw karambwan."));
            return;
        }
        Planner.Route r = Planner.plan(QuestData.get(), ItemDb.get(), a, st.questPoints, goals);
        Guide gd = Guide.of(r);
        if (gd != null && gd.now != null) gd.items = StepItems.of(gd.now.step, st.items(), item -> source(a, item));
        Guide.set(gd);
        pn.showGuide(gd);
        pn.showRoute(r, null);
    }

    /** Where to get an item the step is missing: a main's GE price first, then the best way to make or get it. */
    static String source(ItemTree.Account a, String item)
    {
        String key = ItemData.questText(item);
        int each = "main".equals(a.mode()) ? Prices.each(key) : 0;
        String ge = each > 0 ? "Grand Exchange about " + Prices.gp(each) : null;
        List<Method> ways = MethodTables.waysToMake(key, a);
        String make = !ways.isEmpty() ? ways.get(0).name : null;
        if (make == null)
        {
            ItemData d = ItemDb.get();
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

    /** An opened step card's text, worked out off the client thread. */
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
            catch (RuntimeException | AssertionError e)
            {
                log.warn("Goal Guide: step detail failed", e);
                show.accept(Text.color(Text.RED, "Couldn't work this step out."));
            }
        });
    }

    // ---- edits from the sidebar (Swing thread)

    /** True when the text was a goal and it got added. */
    boolean add(String text)
    {
        Goal g = Goal.parse(text, QuestData::named);
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

    /** Moves a goal to the top: the first goal goes first. */
    void pin(int i)
    {
        List<Goal> next = new ArrayList<>(goals);
        if (i <= 0 || i >= next.size()) return;
        next.add(0, next.remove(i));
        save(next);
    }

    void setRun(String run, boolean on)
    {
        saveSwitches(Runs.switches().withRun(run, on));
    }

    void setStop(String run, String stop, boolean on)
    {
        saveSwitches(Runs.switches().withStop(run, stop, on));
    }

    private void saveSwitches(Runs.Switches next)
    {
        Runs.switches(next);
        String p = profile;
        if (p == null) return;
        config.setConfiguration(GROUP, p, RUNS_OFF, next.runsText());
        config.setConfiguration(GROUP, p, STOPS_OFF, next.stopsText());
        replan();
    }

    private void save(List<Goal> next)
    {
        goals = next;
        String p = profile;
        if (p != null) config.setConfiguration(GROUP, p, KEY, Goal.encodeAll(next));
        replan();
    }
}
