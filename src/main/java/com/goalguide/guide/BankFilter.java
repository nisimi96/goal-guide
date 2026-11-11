package com.goalguide.guide;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import com.goalguide.items.ItemData;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;

/**
 * The bank button: shows only what the Now step uses, through RuneLite's own
 * Bank Tags plugin (a tag named "goal guide" that holds the step's items). Needs Bank Tags on. It only filters
 * what the bank shows; it never withdraws or moves anything.
 */
public final class BankFilter
{
    private BankFilter() { }

    public static final String TAG = "goal guide";
    /** The toggle: the step's items and every run switched on in the panel, together. */
    public static final String BOTH = "the step and your runs";

    private static volatile Set<Integer> ids = Set.of();
    /** The step's item names (lower-case, doses folded): a bank item counts when its name is one, or is of its kind ("rune pickaxe" for "pickaxe"). */
    private static volatile Set<String> names = Set.of();
    /** Game id -> item name (the plugin sets it from RuneLite's item data). */
    private static volatile java.util.function.IntFunction<String> nameOf;
    /** The activity the filter is for ("Train Fishing 40 to 65"). */
    private static volatile String label;
    /** "herb run mid quest": each run that is on -> its item names (keys), offered beside the step. */
    private static volatile Map<String, Set<String>> runs = Map.of();
    /** The run the run box follows now: the button's first choice. */
    private static volatile String preferRun;
    /** The step's items the account has nowhere: game id -> {name, where to get it}, shown faded in the bank. */
    private static volatile Map<Integer, String[]> missing = Map.of();
    /** The choice the bank shows now (the step's label or a run's). */
    private static volatile String showing;
    private static volatile Map<String, List<Integer>> byName;
    private static volatile ItemData byNameFor;

    /** The items to show: lower-case names, as the item list found them held. */
    public static void set(String what, Collection<String> names, ItemData d)
    {
        label = what;
        Set<String> keys = new LinkedHashSet<>();
        for (String n : names) if (n != null && !n.isEmpty()) keys.add(ItemData.key(n));
        BankFilter.names = keys;
        Set<Integer> out = new LinkedHashSet<>();
        if (d != null)
        {
            Map<String, List<Integer>> m = index(d);
            for (String n : names) out.addAll(m.getOrDefault(ItemData.key(n), List.of()));
        }
        ids = out;
    }

    public static String label() { return label; }

    /** The step's missing items (name -> where to get it, may be null), with their game ids from the item data. */
    public static void missing(Map<String, String> byName, ItemData d)
    {
        Map<Integer, String[]> m = new java.util.LinkedHashMap<>();
        if (d != null)
            byName.forEach((n, from) ->
            {
                List<Integer> idList = index(d).getOrDefault(ItemData.key(n), List.of());
                if (!idList.isEmpty()) m.put(java.util.Collections.min(idList), new String[]{n, from});
            });
        missing = m;
    }

    /** Game id -> {name, where to get it} of the step's missing items. */
    public static Map<Integer, String[]> missing() { return missing; }

    /** True when the step has items to show. */
    public static boolean any() { return label != null && (!ids.isEmpty() || !names.isEmpty()); }

    /** The runs to offer (label -> item names), in order. */
    public static void runs(Map<String, List<String>> byLabel)
    {
        Map<String, Set<String>> m = new java.util.LinkedHashMap<>();
        byLabel.forEach((k, v) ->
        {
            Set<String> keys = new LinkedHashSet<>();
            for (String n : v) keys.add(ItemData.key(n));
            m.put(k, keys);
        });
        runs = m;
    }

    /** The run being done now (its label), null when none: the button shows it first. */
    public static void prefer(String runLabel) { preferRun = runLabel; }

    /** What the button can show: the step and the runs together, the run being done, the step, the other runs. */
    public static List<String> choices()
    {
        List<String> out = new java.util.ArrayList<>();
        if (any() || !runs.isEmpty()) out.add(BOTH);
        String r = preferRun;
        if (r != null && runs.containsKey(r)) out.add(r);
        if (label != null) out.add(label);
        for (String k : runs.keySet()) if (!out.contains(k)) out.add(k);
        return out;
    }

    /** How the bank tag reads an item's name. */
    public static void nameOf(java.util.function.IntFunction<String> f) { nameOf = f; }

    /** True for a bank item the shown choice uses. Client thread (the bank asks it while it lays out). */
    static boolean shows(int id)
    {
        return section(id) != null;
    }

    /**
     * "put a line between": the sections the bank shows, in order. The toggle shows the step's
     * section, then one per run that is on; one choice alone is one section.
     */
    public static List<String> sections()
    {
        String on = showing;
        List<String> out = new java.util.ArrayList<>();
        if (BOTH.equals(on))
        {
            if (any()) out.add(label);
            out.addAll(runs.keySet());
        }
        else if (on != null) out.add(on);
        return out;
    }

    /** The first section a bank item belongs to, null when none. Client thread. */
    public static String section(int id)
    {
        List<String> secs = sections();
        if (secs.isEmpty()) return null;
        String k = null;
        java.util.function.IntFunction<String> f = nameOf;
        if (f != null)
        {
            try { String n = f.apply(id); k = n == null ? null : ItemData.key(n); }
            catch (RuntimeException e) { k = null; }
        }
        for (String sec : secs)
        {
            boolean step = sec.equals(label) && !runs.containsKey(sec);
            if (step && ids.contains(id)) return sec;
            if (k == null) continue;
            Set<String> want = step ? names : runs.getOrDefault(sec, Set.of());
            for (String w : want) if (StepItems.matches(k, w)) return sec;
        }
        return null;
    }

    /** The game ids of an item name. */
    public static List<Integer> ids(ItemData d, String name)
    {
        return d == null ? List.of() : index(d).getOrDefault(ItemData.key(name), List.of());
    }

    /** Wiki name -> game ids (an item has several: noted, charged...), built once per item data. */
    static synchronized Map<String, List<Integer>> index(ItemData d)
    {
        if (byNameFor == d && byName != null) return byName;
        Map<String, List<Integer>> m = new HashMap<>();
        d.byId.forEach((id, name) -> m.computeIfAbsent(ItemData.key(name), k -> new java.util.ArrayList<>()).add(id));
        byName = m;
        byNameFor = d;
        return m;
    }

    /** Shows the first choice's items in the bank (the run being done, else the step). Client thread. */
    public static String open(PluginManager plugins)
    {
        List<String> c = choices();
        return c.isEmpty() ? "Add a goal first." : open(plugins, c.get(0));
    }

    /** Shows one choice's items in the bank. Null when it worked, else why not. Client thread. */
    public static String open(PluginManager plugins, String choice)
    {
        BankTagsPlugin p = bankTags(plugins);
        if (p == null) return "Turn on RuneLite's Bank Tags plugin for this button.";
        boolean run = choice != null && runs.containsKey(choice) && !choice.equals(label);
        boolean both = BOTH.equals(choice) && !runs.isEmpty();
        if (!run && !both && !any()) return "This step uses no bank items.";
        showing = choice;
        TagManager tags = tagManager(p);
        if (tags == null) return "Bank Tags is not ready yet.";
        tags.registerTag(TAG, BankFilter::shows);
        // Open again from scratch, so a switch from the step to a run lays the bank out anew
        if (TAG.equals(((BankTagsService) p).getActiveTag())) ((BankTagsService) p).closeBankTag();
        ((BankTagsService) p).openBankTag(TAG, BankTagsService.OPTION_NO_LAYOUT);
        return null;
    }

    /** Which choice {@link #shows} answers for (open sets it). */
    static void showing(String choice) { showing = choice; }

    /** The toggle: on shows the step and the runs that are on, off shows the whole bank. Client thread. */
    public static String toggle(PluginManager plugins)
    {
        if (isOn(plugins)) { close(plugins); return null; }
        return open(plugins, BOTH);
    }

    /** True while the bank shows the guide's items. Client thread. */
    public static boolean isOn(PluginManager plugins)
    {
        BankTagsPlugin p = bankTags(plugins);
        return p != null && TAG.equals(((BankTagsService) p).getActiveTag());
    }

    /** Takes the tag away again (the plugin stops). Client thread. */
    public static void close(PluginManager plugins)
    {
        BankTagsPlugin p = bankTags(plugins);
        TagManager tags = p == null ? null : tagManager(p);
        if (tags == null) return;
        if (TAG.equals(((BankTagsService) p).getActiveTag())) ((BankTagsService) p).closeBankTag();
        tags.unregisterTag(TAG);
    }

    static BankTagsPlugin bankTags(PluginManager plugins)
    {
        if (plugins == null) return null;
        for (Plugin p : plugins.getPlugins())
            if (p instanceof BankTagsPlugin && plugins.isPluginActive(p)) return (BankTagsPlugin) p;
        return null;
    }

    /** Bank Tags' own tag store, from its injector. */
    static TagManager tagManager(BankTagsPlugin p)
    {
        try
        {
            return p.getInjector() == null ? null : p.getInjector().getInstance(TagManager.class);
        }
        catch (RuntimeException e)
        {
            return null;
        }
    }
}
