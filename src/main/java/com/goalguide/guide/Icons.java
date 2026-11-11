package com.goalguide.guide;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import net.runelite.client.ui.FontManager;

/** Sidebar icons for the guide's panels: two letters on a dark tile ("IL"). */
public final class Icons
{
    private Icons() { }

    public static BufferedImage initials(String initials)
    {
        BufferedImage icon = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = icon.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(70, 70, 70));
        g.fillRoundRect(1, 1, 22, 22, 6, 6);
        g.setColor(new Color(238, 196, 92));
        g.setFont(FontManager.getRunescapeBoldFont());
        int w = g.getFontMetrics().stringWidth(initials);
        g.drawString(initials, (24 - w) / 2, 17);
        g.dispose();
        return icon;
    }
}
