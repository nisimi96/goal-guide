package com.goalguide.goals;

/** Small text helpers shared by the panel, the step cards and the screen box. */
public final class Text
{
    private Text() { }

    public static final String GREEN = "#7fd17f", GREY = "#a0a0a0", YELLOW = "#f0c070", BLUE = "#8fc8f0", RED = "#f08a78", ORANGE = "#ff981f";

    public static String esc(String s)
    {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Escaped text in a colour. */
    public static String color(String hex, String text)
    {
        return "<font color='" + hex + "'>" + esc(text) + "</font>";
    }

    /** A section title. */
    public static String head(String t)
    {
        return "<br><font color='" + ORANGE + "'><b>" + esc(t) + "</b></font><br>";
    }

    /** "40 min", "5.2 h", "3.1 days". */
    public static String hours(double h)
    {
        if (h < 1) return Math.max(1, Math.round(h * 60)) + " min";
        if (h < 48) return String.format("%.1f h", h);
        return String.format("%.1f days", h / 24);
    }

    public static String cap(String s)
    {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
