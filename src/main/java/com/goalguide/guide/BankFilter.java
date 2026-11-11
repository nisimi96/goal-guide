package com.goalguide.guide;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.goalguide.items.ItemData;

/**
 * What the bank button shows: the Now step's items or a run's. A bank item counts when it is one of them,
 * or of its kind ("rune pickaxe" for "pickaxe"). Pure.
 */
public final class BankFilter
{
    private BankFilter() { }

    /** The step's item names, lower case. */
    public static Set<String> wanted(List<StepItems.Item> items)
    {
        List<String> names = new ArrayList<>();
        if (items != null) for (StepItems.Item i : items) names.add(i.name);
        return keys(names);
    }

    /** Item names (a run's list), lower case. */
    public static Set<String> keys(Collection<String> names)
    {
        Set<String> out = new LinkedHashSet<>();
        for (String n : names) out.add(ItemData.key(n));
        return out;
    }

    /** True when a bank item with this name is one the step uses. */
    public static boolean shows(String bankName, Set<String> wanted)
    {
        if (bankName == null) return false;
        String k = ItemData.key(bankName);
        for (String w : wanted) if (StepItems.matches(k, w)) return true;
        return false;
    }
}
