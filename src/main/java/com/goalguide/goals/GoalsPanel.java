package com.goalguide.goals;

import static com.goalguide.goals.Text.esc;
import static com.goalguide.goals.Text.head;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import com.goalguide.guide.Guide;
import com.goalguide.guide.StepItems;
import com.goalguide.quests.QuestPlan;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

/** The sidebar: add goals, the guide box, then the route as cards you can open. */
final class GoalsPanel extends PluginPanel
{
    private static final Color BG = ColorScheme.DARK_GRAY_COLOR;

    private final GoalsView view;
    private final JPanel list = Ui.column(BG);
    private final JPanel route = Ui.column(BG);
    private final JLabel hint = Ui.text("", Ui.WIDTH);
    private final JLabel guide = Ui.text("", Ui.WIDTH);
    private final JButton wiki = Ui.button("Wiki guide", null, this::openWiki);
    private volatile String wikiKey;
    /** Opened cards and their text, so a replan doesn't close them or flash. */
    private final Set<String> opened = new HashSet<>();
    private final Map<String, String> details = new HashMap<>();
    private String routeShown;

    GoalsPanel(GoalsView view)
    {
        super(true);
        this.view = view;
        setBackground(BG);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        setLayout(new BorderLayout());

        JPanel top = Ui.column(BG);
        JLabel title = new JLabel("Goal Guide");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(Color.WHITE);
        JTextField field = new JTextField();
        Runnable add = () ->
        {
            String t = field.getText().trim();
            if (t.isEmpty()) return;
            boolean ok = view.add(t);
            if (ok) field.setText("");
            hint.setText(ok ? "" : Ui.html(Text.color(Text.RED, "Hmm, don't know that one. Try Fishing 70, Priest in Peril, Ardougne medium or 1000 raw karambwan."), Ui.WIDTH));
        };
        field.addActionListener(e -> add.run());
        field.setAlignmentX(LEFT_ALIGNMENT);
        stack(top, title, field, Ui.button("Add goal", null, add), hint);
        top.add(Box.createVerticalStrut(6));
        top.add(list);
        top.add(Box.createVerticalStrut(8));

        JPanel box = Ui.box();
        JCheckBox onScreen = new JCheckBox("Show on the game screen", view.guideOnScreen());
        onScreen.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        onScreen.setFont(FontManager.getRunescapeSmallFont());
        onScreen.addActionListener(e -> view.setGuideOnScreen(onScreen.isSelected()));
        wiki.setVisible(false);
        stack(box, guide, wiki, onScreen);

        JPanel all = Ui.column(BG);
        all.add(top);
        all.add(box);
        all.add(Box.createVerticalStrut(8));
        all.add(route);
        add(all, BorderLayout.NORTH);
    }

    private static void stack(JPanel p, JComponent... cs)
    {
        for (JComponent c : cs)
        {
            c.setAlignmentX(LEFT_ALIGNMENT);
            p.add(c);
        }
    }

    private void openWiki()
    {
        String k = wikiKey;
        if (k != null) LinkBrowser.browse(StepDetail.questWikiUrl(k));
    }

    void showGoals(List<Goal> goals)
    {
        SwingUtilities.invokeLater(() ->
        {
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
                if (i > 0) buttons.add(Ui.small("↑", "Do this one first", () -> view.pin(idx)));
                buttons.add(Ui.small("✕", "Remove", () -> view.remove(idx)));
                row.add(l, BorderLayout.CENTER);
                row.add(buttons, BorderLayout.EAST);
                list.add(row);
                list.add(Box.createVerticalStrut(2));
            }
            list.revalidate();
            list.repaint();
        });
    }

    void showGuide(Guide g)
    {
        SwingUtilities.invokeLater(() ->
        {
            guide.setText(Ui.html(guideHtml(g), Ui.WIDTH));
            String key = g == null ? null : g.now != null && g.now.quest != null ? g.now.quest : g.next == null ? null : g.next.quest;
            wikiKey = key;
            wiki.setVisible(key != null);
            if (key != null) wiki.setText(QuestPlan.pretty(key) + " on the wiki");
        });
    }

    static String guideHtml(Guide g)
    {
        StringBuilder b = new StringBuilder("<b>Guide</b><br>");
        if (g == null) return b.append(Text.color(Text.GREY, "Add a goal and this shows what to do next.")).toString();
        if (g.now != null)
        {
            b.append(head("Do now")).append(Text.color(Text.GREEN, g.now.what)).append("<br>");
            if (!g.now.how.isEmpty()) b.append(Text.color(Text.GREY, g.now.how)).append("<br>");
        }
        if (!g.items.isEmpty())
        {
            b.append(head("Items"));
            for (StepItems.Item i : g.items) b.append(item(i)).append("<br>");
        }
        if (g.next != null) b.append(head("Then")).append(esc(g.next.what)).append("<br>");
        if (!g.filler.isEmpty()) b.append(head("Train between steps"));
        for (String f : g.filler) b.append(Text.color(Text.BLUE, f)).append("<br>");
        return b.toString();
    }

    /** Green on you, yellow in a storage, red missing, grey to bring. */
    private static String item(StepItems.Item i)
    {
        switch (i.has)
        {
            case CARRIED: return Text.color(Text.GREEN, "✓ " + i.say());
            case STORED: return Text.color(Text.YELLOW, "○ " + i.say());
            case MISSING: return Text.color(Text.RED, "✗ " + i.say());
            default: return Text.color(Text.GREY, "• " + i.say());
        }
    }

    /** The route as cards, or a note when there's none. */
    void showRoute(Planner.Route r, String note)
    {
        SwingUtilities.invokeLater(() ->
        {
            String sig = r == null ? note : routeHead(r) + r.steps + routeTail(r);
            if (sig.equals(routeShown)) return;
            routeShown = sig;
            if (details.size() > 60) details.clear();
            route.removeAll();
            if (r == null) route.add(Ui.text(note, Ui.WIDTH));
            else
            {
                route.add(Ui.text(routeHead(r), Ui.WIDTH));
                int n = 1;
                for (Planner.Step s : r.steps)
                {
                    route.add(Box.createVerticalStrut(3));
                    route.add(card(n++, s));
                }
                route.add(Ui.text(routeTail(r), Ui.WIDTH));
            }
            route.revalidate();
            route.repaint();
        });
    }

    /** One step: a title line you click to open its text. */
    private JPanel card(int n, Planner.Step s)
    {
        JPanel c = Ui.box();
        String key = s.toString();
        JLabel title = Ui.text("", Ui.CARD_WIDTH);
        title.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        LinkText body = new LinkText(Ui.CARD_WIDTH + 55);
        Runnable paint = () ->
        {
            boolean open = opened.contains(key);
            title.setText(Ui.html(cardTitle(n, s, open), Ui.CARD_WIDTH));
            body.setVisible(open);
            if (open)
            {
                body.body(details.getOrDefault(key, "<i>Working it out...</i>"));
                view.detail(s, h -> SwingUtilities.invokeLater(() -> { details.put(key, h); body.body(h); c.revalidate(); }));
            }
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
        stack(c, title, body);
        if (s.kind == Planner.Kind.QUEST) stack(c, Ui.link(s.quest != null ? "Wiki quick guide" : "Wiki page", StepDetail.wikiUrl(s)));
        paint.run();
        return c;
    }

    /** "+ 3. Get Ruby 1 min": + closed, - open; training yellow, quests blue, items white. */
    static String cardTitle(int n, Planner.Step s, boolean open)
    {
        String color = s.kind == Planner.Kind.TRAIN ? Text.YELLOW : s.kind == Planner.Kind.QUEST ? Text.BLUE : "#ffffff";
        StringBuilder b = new StringBuilder(Text.color(Text.GREY, open ? "–" : "+")).append(' ');
        b.append("<b>").append(n).append(". ").append(Text.color(color, StepDetail.title(s))).append("</b>");
        if (s.hours > 0) b.append(' ').append(Text.color(Text.GREY, Text.hours(s.hours)));
        else if (s.hours < 0 && s.kind != Planner.Kind.QUEST) b.append(' ').append(Text.color(Text.GREY, "(no rate)"));
        return b.toString();
    }

    /** Steps and hours, or that you're done. */
    static String routeHead(Planner.Route r)
    {
        if (r.steps.isEmpty()) return Text.color(Text.GREEN, "✓ All goals done!") + "<br>";
        StringBuilder b = new StringBuilder(head("Route"));
        b.append("<b>").append(r.steps.size()).append(r.steps.size() == 1 ? " step" : " steps").append(", about ").append(Text.hours(r.hours)).append("</b>");
        if (r.unknown > 0) b.append(' ').append(Text.color(Text.GREY, "+ " + r.unknown + (r.unknown == 1 ? " step" : " steps") + " with no rate"));
        return b.append("<br>").append(Text.color(Text.GREY, "Click a step to open it.")).append("<br>").toString();
    }

    /** Under the steps: training between steps, the GE total and goals done. */
    static String routeTail(Planner.Route r)
    {
        StringBuilder b = new StringBuilder();
        if (!r.filler.isEmpty())
        {
            b.append(head("Train between steps")).append(Text.color(Text.GREY, "(the rest of each skill, mix it in)")).append("<br>");
            for (Planner.Filler f : r.filler)
            {
                b.append(Text.color(Text.YELLOW, f.what()));
                if (f.hours > 0) b.append(' ').append(Text.color(Text.GREY, Text.hours(f.hours)));
                if (f.byWhen != null) b.append(' ').append(Text.color(Text.GREY, "(" + f.byWhen + ")"));
                b.append("<br>");
            }
        }
        if (r.ge > 0) b.append(head("Grand Exchange")).append(Text.color(Text.BLUE, "About " + Prices.gp(r.ge) + " in total.")).append("<br>");
        if (!r.done.isEmpty()) b.append(head("Done")).append(Text.color(Text.GREEN, String.join(", ", r.done)));
        return b.toString();
    }
}
