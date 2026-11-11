package com.goalguide.guide;

import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import com.goalguide.quests.QuestData;
import com.goalguide.quests.QuestPlan;

/**
 * Opens a quest in the official Quest Helper (Plugin Hub, BSD-2), as picking it from its own list does: its sidebar
 * and step arrows take over. Quest Helper lives in its own class loader, so it is reached by name through
 * reflection: QuestHelperQuest.getByName(name) -> plugin.getQuestManager().startUpQuest(helper, true). Nothing of
 * its guide is copied.
 */
public final class QuestHelperLink
{
    static final String PLUGIN = "com.questhelper.QuestHelperPlugin", QUESTS = "com.questhelper.questinfo.QuestHelperQuest",
        HELPER = "com.questhelper.questhelpers.QuestHelper";

    private QuestHelperLink() { }

    /** The name Quest Helper lists a quest key under: the game's quest name, or "Ardougne Easy Diary". */
    static String nameOf(String key)
    {
        if (QuestData.isDiary(key)) return QuestPlan.pretty(key);
        try { return net.runelite.api.Quest.valueOf(key).getName(); }
        catch (IllegalArgumentException e) { return QuestPlan.pretty(key); }
    }

    /** Starts the quest in Quest Helper. Null when it worked, else what the player should do. Any thread. */
    public static String open(PluginManager plugins, String questKey)
    {
        Plugin qh = null;
        for (Plugin p : plugins.getPlugins()) if (p.getClass().getName().equals(PLUGIN)) qh = p;
        if (qh == null) return "Quest Helper is not installed: add it from the Plugin Hub";
        if (!plugins.isPluginEnabled(qh)) return "Quest Helper is switched off: switch it on";
        String name = nameOf(questKey);
        try
        {
            ClassLoader cl = qh.getClass().getClassLoader();
            Object helper = Class.forName(QUESTS, true, cl).getMethod("getByName", String.class).invoke(null, name);
            if (helper == null) return "Quest Helper has no guide called " + name;
            Object manager = qh.getClass().getMethod("getQuestManager").invoke(qh);
            manager.getClass().getMethod("startUpQuest", Class.forName(HELPER, true, cl), boolean.class).invoke(manager, helper, true);
            return null;
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError e)
        {
            return "Quest Helper did not open " + name + " (" + e.getClass().getSimpleName() + "): open it from its own list";
        }
    }
}
