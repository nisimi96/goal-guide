package com.goalguide.goals;

import java.awt.Color;
import java.util.function.Consumer;
import javax.swing.BoxLayout;
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
            add(Ui.text(Text.color(Text.GREY, "Switches are saved for this account. A run that is on shows up on the bank button's right-click menu."), Ui.WIDTH));
            for (String run : Runs.names())
            {
                add(Box.createVerticalStrut(6));
                add(card(run, a, sw));
            }
            revalidate();
            repaint();
        });
    }

    private JPanel card(String run, ItemTree.Account a, Runs.Switches sw)
    {
        JPanel c = Ui.box();
        boolean on = sw.on(run);
        c.add(check("<b>" + Text.esc(run) + "</b>", on, Color.WHITE, true, b -> view.setRun(run, b)));
        if (!on) return c;
        for (Runs.Stop s : Runs.stops(run))
        {
            String closed = s.closed(a);
            String label = Text.esc(s.name) + (closed == null ? "" : " " + Text.color(Text.GREY, closed));
            c.add(check(label, closed == null && sw.on(run, s.name), ColorScheme.LIGHT_GRAY_COLOR, closed == null, b -> view.setStop(run, s.name, b)));
        }
        return c;
    }

    private static JCheckBox check(String html, boolean on, Color color, boolean enabled, Consumer<Boolean> set)
    {
        JCheckBox b = new JCheckBox(Ui.html(html, Ui.CARD_WIDTH), on);
        b.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        b.setForeground(color);
        b.setFont(FontManager.getRunescapeSmallFont());
        b.setEnabled(enabled);
        b.setAlignmentX(LEFT_ALIGNMENT);
        b.addActionListener(e -> set.accept(b.isSelected()));
        return b;
    }
}
