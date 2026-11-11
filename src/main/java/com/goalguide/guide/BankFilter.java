package com.goalguide.guide;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.goalguide.items.ItemData;

/**
 * What the bank button shows: the Now step's items. A bank item counts when it is one of them,
 * or of its kind ("rune pickaxe" for "pickaxe"). Pure.
 */
public final class BankFilter
{
    private BankFilter() { }

    /** The step's item names, lower case. */
    public static Set<String> wanted(List<StepItems.Item> items)
    {
        Set<String> out = new LinkedHashSet<>();
        if (items != null) for (StepItems.Item i : items) out.add(ItemData.key(i.name));
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
