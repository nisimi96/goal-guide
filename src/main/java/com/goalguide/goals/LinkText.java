package com.goalguide.goals;

import java.awt.Dimension;
import javax.swing.JEditorPane;
import javax.swing.event.HyperlinkEvent;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;

/**
 * USER 2026-11-11 "each step / item / quest should also get its wiki link": card text whose links open in the
 * browser (a JLabel can't click links). Fixed width, wraps like the old labels, RuneScape small font.
 */
final class LinkText extends JEditorPane
{
    private final int width;

    LinkText(int width)
    {
        super("text/html", "");
        this.width = width;
        setEditable(false);
        setOpaque(false);
        setBorder(null);
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        setFont(FontManager.getRunescapeSmallFont());
        setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        setAlignmentX(LEFT_ALIGNMENT);
        addHyperlinkListener(e ->
        {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && e.getDescription() != null) LinkBrowser.browse(e.getDescription());
        });
    }

    /** Sets the body (no html/body tags). */
    void body(String html)
    {
        setText("<html><body>" + html + "</body></html>");
        setCaretPosition(0);
        revalidate();
    }

    @Override
    public Dimension getPreferredSize()
    {
        setSize(width, Short.MAX_VALUE);
        Dimension d = super.getPreferredSize();
        return new Dimension(width, d.height);
    }

    @Override
    public Dimension getMaximumSize()
    {
        return getPreferredSize();
    }
}
