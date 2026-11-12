package com.goalguide.quests;

import java.util.List;
import net.runelite.api.Quest;
import com.goalguide.items.ItemTree;
import org.junit.Test;

import static org.junit.Assert.*;

public class QuestPlanTest
{
    @Test public void tableLoadsAndNamesRealQuests()
    {
        QuestData d = QuestData.get();
        assertTrue("quests in the table: " + d.byQuest.size(), d.byQuest.size() > 150);
        int bad = 0;
        for (String q : d.byQuest.keySet())
        {
            if (QuestData.isDiary(q)) continue;
            try { Quest.valueOf(q); }
            catch (IllegalArgumentException e) { bad++; }
        }
        assertEquals("quest names the client doesn't know", 0, bad);
    }

    @Test public void chainComesDeepestFirst()
    {
        List<QuestPlan.Step> chain = QuestPlan.plan(QuestData.get(), null, ItemTree.Account.fresh(), 0, "SONG_OF_THE_ELVES");
        int mep2 = -1, sote = -1, plague = -1;
        for (int i = 0; i < chain.size(); i++)
        {
            if (chain.get(i).quest.equals("MOURNINGS_END_PART_II")) mep2 = i;
            if (chain.get(i).quest.equals("SONG_OF_THE_ELVES")) sote = i;
            if (chain.get(i).quest.equals("PLAGUE_CITY")) plague = i;
        }
        assertTrue(plague >= 0 && plague < mep2 && mep2 < sote);
        assertEquals(chain.size() - 1, sote);
        assertTrue(chain.get(sote).train.containsKey("Agility 70"));
    }

    @Test public void readyWhenEverythingIsThere()
    {
        ItemTree.Account a = ItemTree.Account.fresh();
        assertNotEquals("ready", QuestPlan.status(QuestData.get(), null, a, 0, "PRIEST_IN_PERIL"));
        a.owned.put("bucket", 1);
        a.owned.put("rune or pure essence", 50);
        // With no item database the label is the name; the plan only needs the counts
        assertEquals("ready", QuestPlan.status(QuestData.get(), null, a, 0, "PRIEST_IN_PERIL"));
        a.questsDone.add("PRIEST_IN_PERIL");
        assertEquals("done", QuestPlan.status(QuestData.get(), null, a, 0, "PRIEST_IN_PERIL"));
    }

    @Test public void everyDiaryTierWithADoneVarbitTheClientKnows() throws Exception
    {
        QuestData d = QuestData.get();
        assertEquals(48, d.diaries().size());
        assertEquals(net.runelite.api.gameval.VarbitID.ARDOUGNE_EASY_REWARD, d.doneVarbit("ARDOUGNE_EASY_DIARY"));
        for (String k : d.diaries())
        {
            assertTrue(k, d.doneVarbit(k) > 0);
            for (QuestData.Need n : d.needs(k))
            {
                if (n.kind.equals("quest")) Quest.valueOf(n.what);
                if (n.kind.equals("skill")) net.runelite.api.Skill.valueOf(n.what);
                assertNotEquals("diaries carry no items", "item", n.kind);
            }
        }
    }

    @Test public void diaryLevelsMatchTheWiki()
    {
        // OSRS Wiki, Morytania Diary and Kandarin Diary, read 2026-10-25
        QuestData d = QuestData.get();
        assertEquals(40, d.skillNeed("MORYTANIA_MEDIUM_DIARY", "AGILITY"));
        assertEquals(71, d.skillNeed("MORYTANIA_HARD_DIARY", "AGILITY"));
        assertEquals(20, d.skillNeed("KANDARIN_EASY_DIARY", "AGILITY"));
        assertEquals(60, d.skillNeed("KANDARIN_HARD_DIARY", "AGILITY"));
        assertEquals(35, d.skillNeed("MORYTANIA_MEDIUM_DIARY", "SMITHING"));
    }

    @Test public void aBoostCoversASmallGapInsteadOfTraining()
    {
        ItemTree.Account a = ItemTree.Account.fresh();
        a.iron = true;
        a.skills.put("AGILITY", 37);
        a.skills.put("HERBLORE", 34);
        QuestPlan.Step st = last(QuestPlan.plan(QuestData.get(), null, a, 0, "MORYTANIA_MEDIUM_DIARY"));
        assertFalse(st.train.containsKey("Agility 40"));
        assertEquals("Agility potion", st.boosts.get("Agility 40").item);

        a.skills.put("HERBLORE", 1); // An iron who can't brew it trains instead
        st = last(QuestPlan.plan(QuestData.get(), null, a, 0, "MORYTANIA_MEDIUM_DIARY"));
        assertTrue(st.train.containsKey("Agility 40"));
        a.owned.put("agility potion(3)", 1); // Unless one is in the bank
        st = last(QuestPlan.plan(QuestData.get(), null, a, 0, "MORYTANIA_MEDIUM_DIARY"));
        assertEquals("Agility potion", st.boosts.get("Agility 40").item);

        a.owned.clear();
        a.iron = false; // A main buys a summer pie for the 4-level gap the potion can't cover
        a.skills.put("AGILITY", 36);
        st = last(QuestPlan.plan(QuestData.get(), null, a, 0, "MORYTANIA_MEDIUM_DIARY"));
        assertEquals("Summer pie", st.boosts.get("Agility 40").item);
    }

    @Test public void aLevelThatIsNotBoostableIsAlwaysTrained()
    {
        ItemTree.Account a = ItemTree.Account.fresh();
        a.skills.put("COOKING", 39); // Morytania Medium Cooking 40 is not boostable
        QuestPlan.Step st = last(QuestPlan.plan(QuestData.get(), null, a, 0, "MORYTANIA_MEDIUM_DIARY"));
        assertTrue(st.train.containsKey("Cooking 40"));
    }

    @Test public void mergedGoalsShareTheirQuestsOnce()
    {
        List<QuestPlan.Step> chain = QuestPlan.plan(QuestData.get(), null, ItemTree.Account.fresh(), 0,
            List.of("CABIN_FEVER", "MORYTANIA_MEDIUM_DIARY", "GHOSTS_AHOY"));
        long pip = chain.stream().filter(s -> s.quest.equals("PRIEST_IN_PERIL")).count();
        assertEquals(1, pip);
        int cabin = -1, diary = -1;
        for (int i = 0; i < chain.size(); i++)
        {
            if (chain.get(i).quest.equals("CABIN_FEVER")) cabin = i;
            if (chain.get(i).quest.equals("MORYTANIA_MEDIUM_DIARY")) diary = i;
        }
        assertTrue(cabin >= 0 && cabin < diary);
        QuestPlan.Summary m = QuestPlan.sum(chain);
        assertEquals(1, m.diaries);
        assertTrue(m.train.get("Agility") >= 40);
    }

    @Test public void aQuestThatOnlyNeedsStartingIsFineOnceStarted()
    {
        ItemTree.Account a = ItemTree.Account.fresh();
        QuestPlan.Step st = last(QuestPlan.plan(QuestData.get(), null, a, 0, "MORYTANIA_MEDIUM_DIARY"));
        assertTrue(st.startOnly.contains("IN_AID_OF_THE_MYREQUE"));
        a.questsStarted.add("IN_AID_OF_THE_MYREQUE");
        st = last(QuestPlan.plan(QuestData.get(), null, a, 0, "MORYTANIA_MEDIUM_DIARY"));
        assertFalse(st.waitingOn.contains("IN_AID_OF_THE_MYREQUE"));
    }

    @Test public void diaryNamesArePretty()
    {
        assertEquals("Morytania Medium Diary", QuestPlan.pretty("MORYTANIA_MEDIUM_DIARY"));
    }

    private static QuestPlan.Step last(List<QuestPlan.Step> chain)
    {
        return chain.get(chain.size() - 1);
    }

    @Test public void questItemBringsItsQuestFirst()
    {
        QuestData d = QuestData.parse(List.of("GOAL\titem\tMagic ring\t1\t123", "Magic ring\tGIVER\tIN_PROGRESS"));
        ItemTree.Account a = ItemTree.Account.fresh();
        List<QuestPlan.Step> chain = QuestPlan.plan(d, null, a, 0, "GOAL");
        assertEquals("GIVER", chain.get(0).quest);
        QuestPlan.Step goal = chain.get(1);
        assertEquals(List.of("GIVER"), goal.startOnly);
        assertTrue(goal.missingItems.isEmpty());

        // Started the quest but lost the ring: now it is an item to get back
        a.questsStarted.add("GIVER");
        assertTrue(QuestPlan.plan(d, null, a, 0, "GOAL").get(0).missingItems.containsKey("Magic ring"));
    }

    @Test public void ringOfCharosComesFromItsQuests()
    {
        assertNotNull(QuestPlan.questWay("Ring of Charos(a)"));
        for (QuestData.Need g : QuestData.get().givenBy.values()) Quest.valueOf(g.what);
        for (QuestPlan.Step s : QuestPlan.plan(QuestData.get(), null, ItemTree.Account.fresh(), 0, "IN_SEARCH_OF_THE_MYREQUE"))
            for (String item : s.missingItems.keySet()) assertFalse(item, item.toLowerCase().contains("charos"));
    }
}
