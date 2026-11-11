package com.goalguide.goals;

import java.awt.Color;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;

/** Small Swing building blocks shared by the sidebar. */
final class Ui
{
    private Ui() { }

    /** Text width inside the sidebar, and inside a card (it has a border). */
    static final int WIDTH = 150, CARD_WIDTH = 135;

    /** A vertical stack. */
    static JPanel column(Color bg)
    {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(bg);
        p.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return p;
    }

    /** A dark box with a little padding. */
    static JPanel box()
    {
        JPanel p = column(ColorScheme.DARKER_GRAY_COLOR);
        p.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        return p;
    }

    static String html(String body, int width)
    {
        return "<html><body style='width:" + width + "px'>" + body + "</body></html>";
    }

    /** Small grey text that wraps. */
    static JLabel text(String html, int width)
    {
        JLabel l = new JLabel(html(html, width));
        l.setFont(FontManager.getRunescapeSmallFont());
        l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        l.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return l;
    }

    static JButton button(String text, String tip, Runnable r)
    {
        JButton b = new JButton(text);
        if (tip != null) b.setToolTipText(tip);
        b.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        b.addActionListener(e -> r.run());
        return b;
    }

    /** A narrow button for a row ("x", "↑"). */
    static JButton small(String text, String tip, Runnable r)
    {
        JButton b = button(text, tip, r);
        b.setMargin(new Insets(0, 4, 0, 4));
        return b;
    }

    static JButton link(String text, String url)
    {
        return button(text, url, () -> LinkBrowser.browse(url));
    }
}
