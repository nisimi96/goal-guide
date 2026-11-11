package com.goalguide.quests;

import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import com.goalguide.items.ItemTree;

/** Fills a planner account from the logged-in client: quests done and started, diary tiers done, ironman. Client thread. */
public final class AccountRead
{
    private AccountRead() { }

    public static void questsAndDiaries(Client client, ItemTree.Account a)
    {
        for (Quest q : Quest.values())
        {
            QuestState qs = q.getState(client);
            if (qs == QuestState.FINISHED) a.questsDone.add(q.name());
            else if (qs == QuestState.IN_PROGRESS) a.questsStarted.add(q.name());
        }
        // A diary tier is done when its reward varbit reads 1
        QuestData qd = QuestData.get();
        for (String diary : qd.diaries())
        {
            String vb = qd.doneVarbit(diary);
            try
            {
                if (vb != null && client.getVarbitValue(net.runelite.api.gameval.VarbitID.class.getField(vb).getInt(null)) == 1) a.questsDone.add(diary);
            }
            catch (ReflectiveOperationException ignored)
            {
            }
        }
        a.mode = modeName(client.getAccountType());
        a.iron = !a.mode.equals("main");
    }

    /** The planner's mode name: "main", "iron", "uim" or "gim". Hardcore counts as its base mode; null (logged out) is main. */
    public static String modeName(net.runelite.api.vars.AccountType type)
    {
        if (type == null) return "main";
        switch (type)
        {
            case ULTIMATE_IRONMAN: return "uim";
            case GROUP_IRONMAN:
            case HARDCORE_GROUP_IRONMAN: return "gim";
            case IRONMAN:
            case HARDCORE_IRONMAN: return "iron";
            default: return type.isIronman() || type.isGroupIronman() ? "iron" : "main";
        }
    }
}
