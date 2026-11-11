package com.goalguide.items;

import java.awt.BorderLayout;
import java.awt.Color;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import com.goalguide.know.AccountState;
import com.goalguide.know.Know;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.PluginPanel;

/**
 * Item Roots: type an item, see every way this account can get it, traced down to the roots (free, coins, a kill,
 * a reward, or something only the player can do). It clicks nothing. The same tree is what other readers ask through {@link ItemDb#get()} and {@link ItemTree}.
 */
@PluginDescriptor(
    name = "Item Roots",
    description = "Every way to get an item for this account, traced to its roots (data: OSRS Wiki)",
    tags = {"iron", "ironman", "recipe", "source", "planner", "roots"},
    enabledByDefault = false
)
public class ItemRootsPlugin extends Plugin
{
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private ClientToolbar clientToolbar;

    private NavigationButton navButton;
    private Panel panel;
    private javax.swing.Timer knowTimer;
    private final java.util.function.Consumer<AccountState> knowChanged = st -> showKnow();

    @Override
    protected void startUp()
    {
        panel = new Panel(this::trace);
        navButton = NavigationButton.builder().tooltip("Item Roots").icon(com.goalguide.guide.Icons.initials("IR")).priority(12).panel(panel).build();
        clientToolbar.addNavigation(navButton);
        panel.show("<i>" + ItemDb.status() + "</i>");
        ItemDb.load(d -> panel.show("<i>" + ItemDb.status() + "</i>"));
        Know.listen(knowChanged);
        // Timers count down while nothing changes, so the box refreshes on its own too
        knowTimer = new javax.swing.Timer(30_000, e -> showKnow());
        knowTimer.start();
        showKnow();
    }

    private void showKnow()
    {
        Panel p = panel;
        if (p != null) p.know(knowHtml(Know.state(), Know.live(), System.currentTimeMillis()));
    }

    /** The "what I know" box: who, how fresh, each storage and the timers. */
    public static String knowHtml(AccountState st, boolean live, long now)
    {
        if (st == null) return "<font color='#f0c070'>Turn on the Goal Guide plugin and log in once, so it knows your levels, storages and timers.</font>";
        StringBuilder b = new StringBuilder();
        b.append("<b>").append(esc(st.name == null ? "Your account" : st.name)).append("</b> <font color='#a0a0a0'>")
            .append(live ? "live" : "saved " + AccountState.ago(st.updatedAt, now)).append("</font><br>");
        for (String l : st.timerLines(now)) b.append(esc(l)).append("<br>");
        for (String l : st.storageLines(now)) b.append("<font color='#a0a0a0'>").append(esc(l)).append("</font><br>");
        return b.toString();
    }

    @Override
    protected void shutDown()
    {
        if (navButton != null) { clientToolbar.removeNavigation(navButton); navButton = null; }
        Know.unlisten(knowChanged);
        if (knowTimer != null) { knowTimer.stop(); knowTimer = null; }
        panel = null;
    }

    static final String MINE = "My account", FRESH = "New account (all level 1)", MAXED = "Maxed account (99s, all quests)";

    private void trace(String item, String planFor)
    {
        ItemData d = ItemDb.get();
        if (d == null) { panel.show("<i>" + ItemDb.status() + "</i>"); return; }
        String quest = questNamed(item);
        if (quest == null && !d.known(item)) { panel.show("No item or quest called <b>" + esc(item) + "</b>. Use the full name, e.g. Prayer potion(4) or Priest in Peril."); return; }
        clientThread.invoke(() ->
        {
            boolean in = client.getGameState() == GameState.LOGGED_IN;
            boolean mine = MINE.equals(planFor);
            // My account comes from Know: every storage as last seen, and it still works logged out
            AccountState st = mine ? Know.state() : null;
            ItemTree.Account a = FRESH.equals(planFor) ? ItemTree.Account.fresh() : MAXED.equals(planFor) ? ItemTree.Account.maxed()
                : st != null ? st.toAccount() : new ItemTree.Account();
            if (mine && st == null)
            {
                if (in)
                {
                    for (Skill s : Skill.values())
                    {
                        try { a.skills.put(s.name(), client.getRealSkillLevel(s)); }
                        catch (RuntimeException ignored) { }
                    }
                    com.goalguide.quests.AccountRead.questsAndDiaries(client, a);
                    a.members = client.getWorldType().contains(net.runelite.api.WorldType.MEMBERS);
                }
                else for (Skill s : Skill.values()) a.skills.put(s.name(), 99);
            }
            String who = !mine ? planFor
                : st != null ? (Know.live() ? MINE : MINE + " as saved " + AccountState.ago(st.updatedAt, System.currentTimeMillis()))
                : in ? MINE + " (Goal Guide off, so items are not counted)" : "not logged in, so as if every skill were 99 and no quests done";
            int qp = !mine ? (MAXED.equals(planFor) ? 999 : 0) : st != null ? st.questPoints : in ? client.getVarpValue(net.runelite.api.gameval.VarPlayerID.QP) : 999;
            new Thread(() ->
            {
                if (quest != null)
                {
                    panel.show(questHtml(d, a, qp, quest, who));
                    return;
                }
                ItemTree.Node n = ItemTree.trace(d, a, item);
                panel.show(html(d, a, n, who));
            }, "item-roots-trace").start();
        });
    }

    public static String html(ItemData d, ItemTree.Account a, ItemTree.Node n, String planFor)
    {
        StringBuilder b = new StringBuilder();
        b.append("<font size='+1' color='#ffffff'><b>").append(esc(n.item)).append("</b></font><br>");
        b.append("<font color='#a0a0a0'>Planned for: ").append(esc(planFor)).append("</font><br>");
        b.append(tableWays(a, n.item));
        if (n.best == null) return b.append("No way found for this item in the wiki data.").toString();
        List<ItemTree.Step> steps = ItemTree.steps(d, a, n);
        java.util.Set<String> missing = new java.util.LinkedHashSet<>();
        long coins = 0;
        for (ItemTree.Step st : steps)
        {
            st.needs.forEach((k, ok) -> { if (!ok) missing.add(k); });
            if ("Coins".equalsIgnoreCase(st.item)) coins += Math.round(st.qty);
        }
        b.append(missing.isEmpty() ? "<font color='#7fd17f'>&#10003; You can do every step now.</font>"
            : "<font color='#f0c070'>Train first: " + esc(String.join(", ", highest(missing))) + "</font>").append("<br><br>");

        b.append("<b>Steps for 1</b> (top to bottom)<br><br>");
        // A level the plan is missing becomes its own step, once, at the highest level the plan needs
        Map<String, Integer> train = new java.util.LinkedHashMap<>();
        for (ItemTree.Step st : steps)
            st.needs.forEach((k, ok) -> {
                int sp = k.lastIndexOf(' ');
                if (!ok && sp > 0 && !k.startsWith("quest ") && k.substring(sp + 1).matches("\\d+"))
                    train.merge(k.substring(0, sp), Integer.parseInt(k.substring(sp + 1)), Math::max);
            });
        java.util.Set<String> trained = new java.util.HashSet<>();
        int i = 1;
        for (ItemTree.Step st : steps)
        {
            if ("Coins".equalsIgnoreCase(st.item)) continue;
            for (Map.Entry<String, Boolean> nd : st.needs.entrySet())
            {
                int sp = nd.getKey().lastIndexOf(' ');
                String skill = sp > 0 ? nd.getKey().substring(0, sp) : "";
                if (nd.getValue() || !train.containsKey(skill) || !trained.add(skill)) continue;
                b.append("<b>").append(i++).append(". <font color='#f0c070'>Train ").append(esc(skill)).append(" to ").append(train.get(skill)).append("</font></b><br>")
                    .append("Comes before the next step").append("<br><br>");
            }
            b.append("<b>").append(i++).append(". ").append(wiki(st.item)).append("</b>");
            String q = amount(st.qty);
            if (!q.isEmpty()) b.append(" <font color='#a0a0a0'>").append(q).append("</font>");
            b.append("<br>").append(esc(st.way.say == null ? st.way.label : st.way.say)).append("<br>");
            if (!st.needs.isEmpty())
            {
                List<String> parts = new java.util.ArrayList<>();
                st.needs.forEach((k, ok) -> parts.add(ok ? "<font color='#7fd17f'>" + esc(k) + " &#10003;</font>" : "<font color='#f08a78'>" + esc(k) + " &#10007;</font>"));
                b.append(String.join(", ", parts)).append("<br>");
            }
            b.append("<br>");
        }
        if (coins > 0) b.append("<b>Coins:</b> ").append(String.format("%,d", coins)).append(" gp each<br><br>");

        int shown = 0;
        StringBuilder other = new StringBuilder();
        for (ItemTree.Way w : n.ways)
        {
            if (w == n.best || shown >= 3) continue;
            other.append("&bull; ").append(esc(w.say == null ? w.label : w.say));
            if (!w.missing.isEmpty()) other.append(" <font color='#f08a78'>(needs ").append(esc(String.join(", ", w.missing))).append(")</font>");
            other.append("<br>");
            shown++;
        }
        if (shown > 0) b.append("<font color='#a0a0a0'><b>Other ways to get ").append(esc(n.item)).append("</b><br>").append(other).append("</font>");
        return b.toString();
    }

    /**
     * The skill methods that make this item, from the method tables: per hour at the account's level (or at the
     * start level when it is not there yet), and what is missing. Empty when no table makes it.
     */
    public static String tableWays(ItemTree.Account a, String item)
    {
        String key = item.trim().toLowerCase(java.util.Locale.ROOT);
        List<com.goalguide.facts.Method> ways = com.goalguide.facts.MethodTables.waysToMake(key, a);
        if (ways.isEmpty()) return "";
        StringBuilder b = new StringBuilder("<br><b>Best per hour</b> (skill methods)<br>");
        int shown = 0;
        for (com.goalguide.facts.Method m : ways)
        {
            if (shown++ >= 3) break;
            int lvl = Math.max(m.level, a.skills.getOrDefault(m.skill, 1));
            int per = m.givesAt(key, lvl);
            List<String> miss = m.missing(a);
            b.append(miss.isEmpty() ? "<font color='#7fd17f'>&#10003; </font>" : "&bull; ").append(esc(m.name))
                .append(" <font color='#a0a0a0'>(").append(com.goalguide.facts.Method.pretty(m.skill)).append(' ').append(m.level).append(")</font>: ")
                .append(per < 0 ? "rate unknown" : String.format("%,d", per) + " an hour" + (lvl > m.level ? "" : " at " + m.level));
            if (!miss.isEmpty()) b.append(" <font color='#f08a78'>needs ").append(esc(String.join(", ", miss))).append("</font>");
            b.append("<br>");
        }
        return b.append("<br>").toString();
    }

    /** "Herblore 25", "Herblore 38" -> "Herblore 38": the highest level per skill, quests kept as they are. */
    static List<String> highest(java.util.Collection<String> needs)
    {
        Map<String, Integer> top = new java.util.LinkedHashMap<>();
        List<String> other = new java.util.ArrayList<>();
        for (String k : needs)
        {
            int sp = k.lastIndexOf(' ');
            if (sp > 0 && !k.startsWith("quest ") && k.substring(sp + 1).matches("\\d+")) top.merge(k.substring(0, sp), Integer.parseInt(k.substring(sp + 1)), Math::max);
            else other.add(k);
        }
        List<String> out = new java.util.ArrayList<>();
        top.forEach((k, v) -> out.add(k + " " + v));
        out.addAll(other);
        return out;
    }

    /** "x 5", "1 for every 6", "" for exactly one. */
    /**
     * "each step / item / quest should also get its wiki link": the name as a link to its wiki page
     * (the wiki search jumps straight to an exact page). Clickable where the panel shows links; plain text elsewhere.
     */
    public static String wiki(String name)
    {
        String url = "https://oldschool.runescape.wiki/w/Special:Search?search="
            + java.net.URLEncoder.encode(name, java.nio.charset.StandardCharsets.UTF_8) + "&go=Go";
        return "<a href='" + url.replace("'", "%27") + "' style='color:#ffffff; text-decoration:underline'>" + esc(name) + "</a>";
    }

    static String amount(double q)
    {
        if (Math.abs(q - 1) < 1e-9) return "";
        if (q < 1) return "(1 for every " + Math.round(1 / q) + ")";
        return "x " + (q == Math.rint(q) ? String.valueOf((long) q) : String.format("%.1f", q));
    }

    /** The net.runelite.api.Quest name for typed text ("priest in peril"), null when it is no quest. */
    public static String questNamed(String text)
    {
        String t = text.trim().toLowerCase(java.util.Locale.ROOT).replace("'", "");
        for (Quest q : Quest.values())
            if (q.getName().toLowerCase(java.util.Locale.ROOT).replace("'", "").equals(t) || q.name().equalsIgnoreCase(text.trim().replace(' ', '_'))) return q.name();
        // A diary tier: "morytania medium" or "morytania medium diary"
        String key = t.replace(' ', '_').toUpperCase(java.util.Locale.ROOT);
        if (!key.endsWith("_DIARY")) key += "_DIARY";
        if (com.goalguide.quests.QuestData.get().byQuest.containsKey(key)) return key;
        return null;
    }

    public static String questHtml(ItemData d, ItemTree.Account a, int qp, String goal, String planFor)
    {
        com.goalguide.quests.QuestData qd = com.goalguide.quests.QuestData.get();
        List<com.goalguide.quests.QuestPlan.Step> chain = com.goalguide.quests.QuestPlan.plan(qd, d, a, qp, goal);
        StringBuilder b = new StringBuilder();
        b.append("<font size='+1' color='#ffffff'><b>").append(esc(com.goalguide.quests.QuestPlan.pretty(goal))).append("</b></font><br>");
        b.append("<font color='#a0a0a0'>Planned for: ").append(esc(planFor)).append("</font><br>");
        if (chain.isEmpty()) return b.append("<font color='#7fd17f'>&#10003; Already done.</font>").toString();
        if (qd.needs(goal).isEmpty()) b.append("<font color='#a0a0a0'>No requirements listed for this quest.</font><br>");
        long ready = chain.stream().filter(com.goalguide.quests.QuestPlan.Step::ready).count();
        b.append(chain.size()).append(" quest").append(chain.size() > 1 ? "s" : "").append(" in the chain, ").append(ready).append(" ready now.<br><br>");
        int i = 1;
        for (com.goalguide.quests.QuestPlan.Step st : chain)
        {
            String qn = com.goalguide.quests.QuestPlan.pretty(st.quest);
            b.append("<b>").append(i++).append(". ").append(com.goalguide.quests.QuestData.isDiary(st.quest) ? esc(qn) : wiki(qn)).append("</b><br>");
            st.boosts.forEach((k, boost) -> b.append("<font color='#8fc8f0'>Boost ").append(esc(k)).append(" with ").append(esc(boost.item.toLowerCase(java.util.Locale.ROOT)))
                .append("</font> <font color='#a0a0a0'>(+").append(boost.amount(a.skills.getOrDefault(k.substring(0, k.lastIndexOf(' ')).toUpperCase(java.util.Locale.ROOT), 1))).append(")</font><br>"));
            if (st.ready())
            {
                b.append("<font color='#7fd17f'>&#10003; Ready: you can do this ").append(com.goalguide.quests.QuestData.isDiary(st.quest) ? "diary" : "quest").append(".</font><br>");
            }
            else
            {
                for (String q : st.waitingOn) b.append("<font color='#a0a0a0'>after ").append(esc(com.goalguide.quests.QuestPlan.pretty(q))).append("</font><br>");
                st.train.forEach((k, have) -> b.append("<font color='#f0c070'>Train ").append(esc(k)).append("</font> <font color='#a0a0a0'>(you: ").append(have).append(")</font><br>"));
                for (String o : st.other) b.append("<font color='#f0c070'>").append(esc(o)).append("</font><br>");
                st.missingItems.forEach((name, nh) -> {
                    b.append("<font color='#f08a78'>Get ").append(nh[0] > 1 ? nh[0] + " x " : "").append(esc(name)).append("</font>");
                    if (d.known(name))
                    {
                        ItemTree.Node n = ItemTree.trace(d, a, name);
                        if (n.best != null && n.best.say != null) b.append(" <font color='#a0a0a0'>: ").append(esc(n.best.say)).append("</font>");
                    }
                    b.append("<br>");
                });
            }
            for (String x : st.also) b.append("<font color='#a0a0a0'>also: ").append(esc(x)).append(" (not checked)</font><br>");
            if (!st.haveItems.isEmpty()) b.append("<font color='#7fd17f'>have: ").append(esc(String.join(", ", st.haveItems))).append("</font><br>");
            if (!st.usedItems.isEmpty()) b.append("<font color='#a0a0a0'>maybe used in the quest already: ").append(esc(String.join(", ", st.usedItems))).append("</font><br>");
            b.append("<br>");
        }
        return b.toString();
    }

    static String esc(String s)
    {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static final class Panel extends PluginPanel
    {
        private final JLabel out = new JLabel();
        private final JLabel knowBox = new JLabel();

        Panel(java.util.function.BiConsumer<String, String> onTrace)
        {
            super(true);
            setBackground(ColorScheme.DARK_GRAY_COLOR);
            setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            setLayout(new BorderLayout());
            JPanel head = new JPanel();
            head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
            head.setBackground(ColorScheme.DARK_GRAY_COLOR);
            JLabel title = new JLabel("Item Roots");
            title.setFont(FontManager.getRunescapeBoldFont());
            title.setForeground(Color.WHITE);
            JLabel hint = new JLabel("<html>Type an item. Shows how this account gets it, down to the roots.</html>");
            hint.setFont(FontManager.getRunescapeSmallFont());
            hint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            JTextField field = new JTextField();
            JButton go = new JButton("Trace");
            javax.swing.JComboBox<String> who = new javax.swing.JComboBox<>(new String[]{MINE, FRESH, MAXED});
            Runnable run = () -> { String t = field.getText().trim(); if (!t.isEmpty()) { show("<i>tracing " + esc(t) + "...</i>"); onTrace.accept(t, (String) who.getSelectedItem()); } };
            who.addActionListener(e -> run.run());
            field.addActionListener(e -> run.run());
            go.addActionListener(e -> run.run());
            head.add(title);
            head.add(Box.createVerticalStrut(4));
            head.add(hint);
            head.add(Box.createVerticalStrut(6));
            head.add(field);
            head.add(Box.createVerticalStrut(4));
            head.add(who);
            head.add(Box.createVerticalStrut(4));
            head.add(go);
            head.add(Box.createVerticalStrut(8));
            knowBox.setFont(FontManager.getRunescapeSmallFont());
            knowBox.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            knowBox.setAlignmentX(LEFT_ALIGNMENT);
            head.add(knowBox);
            head.add(Box.createVerticalStrut(8));
            add(head, BorderLayout.NORTH);
            out.setFont(FontManager.getRunescapeSmallFont());
            out.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            out.setVerticalAlignment(SwingConstants.TOP);
            add(out, BorderLayout.CENTER);
        }

        void know(String html)
        {
            SwingUtilities.invokeLater(() -> knowBox.setText("<html><body style='width:150px'>" + html + "</body></html>"));
        }

        void show(String html)
        {
            SwingUtilities.invokeLater(() -> out.setText("<html><body style='width:150px'>" + html + "</body></html>"));
        }
    }

    /** For tests and the scheduler: the plan for an item as plain lines. */
    static List<String> plain(ItemData d, ItemTree.Account a, String item)
    {
        return ItemTree.lines(ItemTree.trace(d, a, item), 2);
    }
}
