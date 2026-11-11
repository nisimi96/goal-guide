package com.goalguide.goals;

import java.util.function.Consumer;
import javax.swing.BoxLayout;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import com.goalguide.facts.Runs;
import com.goalguide.items.ItemTree;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/** The Runs tab: switch each run, and each stop of it, on or off for this account. */
final class RunsPanel extends JPanel
{
    static final int INDENT = 14;
    static final String DIM = "#6a6a6a";

    private final GoalsView view;
    private String shown;

    RunsPanel(GoalsView view)
    {
        this.view = view;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
    }

    /** Builds the switches again when the account or the switches change. */
    void show(ItemTree.Account a, Runs.Switches sw)
    {
        SwingUtilities.invokeLater(() ->
        {
            String sig = a.questsDone + "" + a.skills + sw.runsText() + "|" + sw.stopsText();
            if (sig.equals(shown)) return;
            shown = sig;
            removeAll();
            add(Ui.text(Text.color(Text.GREY, "Switches are saved for this account. The bank button shows the items of every run that is on, with the step's."), Ui.WIDTH));
            for (String run : Runs.names())
            {
                add(Box.createVerticalStrut(6));
                add(card(run, a, sw));
            }
            revalidate();
            repaint();
        });
    }

    /** A run: its title as a header, its stops indented under it. Locked stops are dim and say what opens them. */
    private JPanel card(String run, ItemTree.Account a, Runs.Switches sw)
    {
        JPanel c = Ui.box();
        boolean on = sw.on(run);
        JCheckBox head = check(Text.color(on ? Text.ORANGE : Text.GREY, run), on, true, b -> view.setRun(run, b));
        head.setFont(FontManager.getRunescapeBoldFont());
        c.add(head);
        if (!on) return c;
        for (Runs.Stop s : Runs.stops(run))
        {
            String closed = s.closed(a);
            String label = closed == null ? Text.esc(s.name) : Text.color(DIM, s.name) + "<br>" + Text.color(Text.RED, closed);
            JCheckBox stop = check(label, closed == null && sw.on(run, s.name), closed == null, b -> view.setStop(run, s.name, b));
            stop.setBorder(BorderFactory.createEmptyBorder(0, INDENT, 0, 0));
            c.add(stop);
        }
        return c;
    }

    private static JCheckBox check(String html, boolean on, boolean enabled, Consumer<Boolean> set)
    {
        JCheckBox b = new JCheckBox(Ui.html(html, Ui.CARD_WIDTH - INDENT), on);
        b.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        b.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        b.setFont(FontManager.getRunescapeSmallFont());
        b.setEnabled(enabled);
        b.setAlignmentX(LEFT_ALIGNMENT);
        b.addActionListener(e -> set.accept(b.isSelected()));
        return b;
    }
}
