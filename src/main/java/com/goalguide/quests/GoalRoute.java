package com.goalguide.quests;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import com.goalguide.items.ItemData;
import com.goalguide.items.ItemTree;

/**
 * Panel text for a set of goals planned as one route (QuestPlan.plan with several goals), and the diary tiers a
 * skill opens. Html for a JLabel, kept here so every panel shows goals the same way.
 */
public final class GoalRoute
{
    private GoalRoute() { }

    /**
     * Diary tiers not done yet that this skill (upper case) still holds back: they ask more than the account's level.
     * Lowest level first, so the first one is what the next levels open.
     */
    public static List<String> diariesNeeding(QuestData d, ItemTree.Account a, String skill)
    {
        List<String> out = new ArrayList<>();
        int have = a.skills.getOrDefault(skill, 1);
        for (String k : d.diaries()) if (!a.questsDone.contains(k) && d.skillNeed(k, skill) > have) out.add(k);
        out.sort(Comparator.comparingInt((String k) -> d.skillNeed(k, skill)).thenComparing(k -> k));
        return out;
    }

    /** "Morytania Medium Diary (Agility 40): needs Cabin Fever ..." per tier, at most max lines. */
    public static List<String> diaryLines(QuestData d, ItemData items, ItemTree.Account a, int qp, String skill, int max)
    {
        List<String> out = new ArrayList<>();
        for (String k : diariesNeeding(d, a, skill))
        {
            if (out.size() >= max) break;
            String st = QuestPlan.status(d, items, a, qp, k);
            String color = st.startsWith("ready") ? "#7fd17f" : "#f0c070";
            out.add(QuestPlan.pretty(k) + " (" + QuestPlan.cap(skill) + " " + d.skillNeed(k, skill) + ") <font color='" + color + "'>" + st + "</font>");
        }
        return out;
    }

    /** The merged route's block: totals, what is the player's turn now, then the steps in order (at most maxSteps). */
    public static String html(QuestData d, ItemData items, ItemTree.Account a, int qp, List<String> goals, int maxSteps)
    {
        List<QuestPlan.Step> chain = QuestPlan.plan(d, items, a, qp, goals);
        if (chain.isEmpty()) return "";
        QuestPlan.Summary m = QuestPlan.sum(chain);
        StringBuilder b = new StringBuilder();
        b.append("<b>One route for ").append(goals.size()).append(goals.size() == 1 ? " goal" : " goals").append("</b>: ")
            .append(m.quests).append(m.quests == 1 ? " quest" : " quests");
        if (m.diaries > 0) b.append(", ").append(m.diaries).append(m.diaries == 1 ? " diary" : " diaries");
        b.append("<br>");
        if (!m.train.isEmpty())
        {
            List<String> t = new ArrayList<>();
            m.train.forEach((k, v) -> t.add(k + " " + v));
            b.append("train ").append(String.join(", ", t)).append("<br>");
        }
        if (!m.boosts.isEmpty()) b.append("boost ").append(QuestPlan.boostText(m)).append("<br>");
        if (m.items > 0) b.append(m.items).append(m.items == 1 ? " item" : " items").append(" to get<br>");
        if (m.next != null) b.append("<font color='#7fd17f'>next quest: ").append(QuestPlan.pretty(m.next.quest)).append("</font><br>");
        int i = 0;
        for (QuestPlan.Step s : chain)
        {
            if (i >= maxSteps) { b.append("<font color='#a0a0a0'>... ").append(chain.size() - i).append(" more</font><br>"); break; }
            i++;
            b.append(i).append(". ").append(QuestPlan.pretty(s.quest));
            if (s.ready()) b.append(" <font color='#7fd17f'>&#10003; ready</font>");
            else if (!s.waitingOn.isEmpty() && s.train.isEmpty() && s.missingItems.isEmpty() && s.other.isEmpty()) b.append(" <font color='#a0a0a0'>(after the ones above)</font>");
            b.append("<br>");
        }
        return b.toString();
    }
}
