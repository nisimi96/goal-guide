package com.goalguide.items;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Every way to get every item, in memory: recipes, shops, ground spawns and drops (which include skilling gathers,
 * e.g. "Coal rocks, Always, mining 30"). Read once from the Not Enough Runes wiki database
 * (github.com/Hannah-GBS/notenoughrunes, BSD 2-Clause; data scraped from the OSRS Wiki), then pure.
 * Items are keyed by lower-case name; versions ("Cosmic rune (Catalytic)") are folded into the name.
 */
public final class ItemData
{
    public static final class Recipe
    {
        public final String output, facility, tools, subtext;
        public final int outputQty;
        public final boolean members;
        /** {name, qty}. */
        public final List<String[]> materials = new ArrayList<>();
        /** skill name ("Magic") -> level. */
        public final Map<String, Integer> skills = new HashMap<>();

        public Recipe(String output, int outputQty, String facility, String tools, String subtext, boolean members)
        {
            this.output = output;
            this.outputQty = outputQty;
            this.facility = blankToNull(facility);
            this.tools = blankToNull(tools);
            this.subtext = blankToNull(subtext);
            this.members = members;
        }
    }

    public static final class Shop
    {
        public final String item, shop, location, currency;
        public final int price;
        public final boolean members;
        /** The wiki's base stock: "0" means it only sells what players sold it, so it is no source. "inf" counts as plenty. */
        public String stock = "";

        public Shop(String item, String shop, String location, String currency, int price, boolean members)
        {
            this.item = item;
            this.shop = shop;
            this.location = location;
            this.currency = currency;
            this.price = price;
            this.members = members;
        }
    }

    public static final class Spawn
    {
        public final String item, location;
        public final boolean members;

        public Spawn(String item, String location, boolean members)
        {
            this.item = item;
            this.location = location;
            this.members = members;
        }
    }

    public static final class Drop
    {
        /** source as the wiki writes it, "Bandit#Level 22"; type: combat, reward, mining, fishing, thieving, ... */
        public final String item, source, rarity, level, type;
        public final int qtyLow, qtyHigh;

        public Drop(String item, String source, String rarity, String level, String type, int qtyLow, int qtyHigh)
        {
            this.item = item;
            this.source = source;
            this.rarity = rarity;
            this.level = level == null ? "" : level;
            this.type = type == null ? "" : type;
            this.qtyLow = qtyLow;
            this.qtyHigh = qtyHigh;
        }

        /** "Bandit (level 22)". */
        public String sourceName()
        {
            int h = source.indexOf('#');
            return h < 0 ? source : source.substring(0, h) + " (" + source.substring(h + 1) + ")";
        }

        /** Chance per kill / action, 0 when unknown or once-only. */
        public double chance()
        {
            return ItemData.chance(rarity);
        }

        /**
         * A repeatable skilling gather: a rock, tree, fishing spot, patch, impling, or a pickpocket or stall. Drops of
         * skill type from containers (crates, coffins, chests, salvage) are rewards, not gathers.
         */
        public boolean gather()
        {
            switch (type)
            {
                case "mining": case "woodcutting": case "fishing": case "hunter": case "farming": case "thieving":
                    break;
                default:
                    return false;
            }
            String s = source.toLowerCase(Locale.ROOT);
            for (String c : CONTAINERS) if (s.contains(c)) return false;
            // implings fly about at random: a lucky catch, not something to plan stock on
            if (s.contains("impling")) return false;
            return true;
        }

        /** Lowest number of the level column ("57,74" -> 57), 0 when blank. */
        public int firstLevel()
        {
            int best = 0;
            for (String part : level.split(","))
            {
                int v = intOr(part, 0);
                if (v > 0 && (best == 0 || v < best)) best = v;
            }
            return best;
        }
    }

    /** Source names that are one-off containers, not repeatable gathers. */
    static final String[] CONTAINERS = {"crate", "chest", "coffin", "casket", "salvage", "sack", "box", "barrel", "cache", "reward", "soil", "digs"};

    public final Map<String, String> names = new HashMap<>();
    /** Game item id -> wiki item name. */
    public final Map<Integer, String> byId = new HashMap<>();
    public final Map<String, List<Recipe>> recipes = new HashMap<>();
    public final Map<String, List<Shop>> shops = new HashMap<>();
    public final Map<String, List<Spawn>> spawns = new HashMap<>();
    public final Map<String, List<Drop>> drops = new HashMap<>();

    /**
     * The item a want names, or one of its kind: "rune pickaxe" is a "pickaxe", "dragon axe" an "axe" (not
     * "pickaxe"). A "cat" is any cat or kitten (pet, lazy, wily, hell, overgrown; Quest Helper's CATS), a "hunting cat"
     * one that is not overgrown (HUNTING_CATS). A wooden cat is no cat.
     */
    public static boolean sameKind(String held, String want)
    {
        if (held.equals(want)) return true;
        if (held.endsWith(" " + want)) return !held.startsWith("wooden ");
        if (want.equals("cat") || want.equals("hunting cat"))
            return (held.endsWith("cat") || held.endsWith("kitten")) && !held.startsWith("wooden ")
                && !(want.equals("hunting cat") && held.contains("overgrown"));
        return false;
    }

    public static String key(String name)
    {
        // doses fold together: "Super restore(4)" and the recipe's "Super restore" are one thing
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s*\\(\\d\\)$", "");
    }

    public String display(String name)
    {
        return names.getOrDefault(key(name), name);
    }

    public void add(Recipe r) { names.putIfAbsent(key(r.output), r.output); recipes.computeIfAbsent(key(r.output), k -> new ArrayList<>()).add(r); }
    public void add(Shop s) { names.putIfAbsent(key(s.item), s.item); shops.computeIfAbsent(key(s.item), k -> new ArrayList<>()).add(s); }
    public void add(Spawn s) { names.putIfAbsent(key(s.item), s.item); spawns.computeIfAbsent(key(s.item), k -> new ArrayList<>()).add(s); }
    public void add(Drop d) { names.putIfAbsent(key(d.item), d.item); drops.computeIfAbsent(key(d.item), k -> new ArrayList<>()).add(d); }

    /**
     * Quest Helper's item text as an item name: "Iron spear or better (You will lose the spear)" -> "iron spear",
     * "Any logs to make a fire" -> "logs", "Agility Potion (4)" -> "agility potion(4)". "(unf)" is kept.
     */
    public static String questText(String name)
    {
        String k = name.toLowerCase(Locale.ROOT).trim();
        k = k.replaceAll("\\s*\\((\\d)\\)", "($1)");
        k = k.replaceAll("\\s*\\((?!unf\\))[^)]*[a-z][^)]*\\)", "");
        k = k.replaceAll(" or better.*$", "");
        if (k.startsWith("any ")) k = k.substring(4);
        k = k.replaceAll(" to make .*$", "");
        return k.trim();
    }

    /**
     * The known item names a quest's item text can mean, best guess first: the text itself, the singular ("death
     * runes" -> "death rune"), each choice of "a/b/c x", "a or b", "a, b or c" ("normal/super/ultra compost",
     * "raw shark, manta ray or sea turtle"), and a known name that starts with it ("limpwurt" -> "limpwurt root").
     * Empty when none is known.
     */
    public List<String> matches(String text)
    {
        String t = key(text).replaceAll(" to (get|make|use|light|fill) .*$", "").trim();
        java.util.LinkedHashSet<String> tries = new java.util.LinkedHashSet<>();
        tries.add(t);
        String[] or = t.split("\\s*(?:,|\\bor\\b)\\s*");
        if (or.length > 1)
        {
            String head = or[0].contains(" ") ? or[0].substring(0, or[0].indexOf(' ') + 1) : "";
            for (String o : or) if (!o.isEmpty()) { tries.add(o.trim()); tries.add(head + o.trim()); }
        }
        if (t.contains("/"))
        {
            String[] parts = t.split("/");
            String last = parts[parts.length - 1];
            String tail = last.contains(" ") ? last.substring(last.indexOf(' ')) : "";
            for (String part : parts) tries.add(part.contains(" ") ? part : part + tail);
            if (!tail.isEmpty()) tries.add(tail.trim());
        }
        List<String> out = new ArrayList<>();
        for (String c : new ArrayList<>(tries))
        {
            for (String v : new String[]{c, c.endsWith("s") ? c.substring(0, c.length() - 1) : null, c.endsWith("es") ? c.substring(0, c.length() - 2) : null,
                c.contains(" ") ? c.replace(" ", "") : null})
                if (v != null && names.containsKey(v) && !out.contains(names.get(v))) out.add(names.get(v));
        }
        if (out.isEmpty() && t.length() >= 4)
        {
            String best = null;
            for (String k : names.keySet())
                if (k.startsWith(t + " ") && (best == null || k.length() < best.length())) best = k;
            if (best != null) out.add(names.get(best));
        }
        return out;
    }

    public boolean known(String name)
    {
        String k = key(name);
        return names.containsKey(k);
    }

    /** "3/128" -> 0.0234, "1/145,728", "Always" 1, "Common" 1/10, "Uncommon" 1/50, "Rare" 1/500, else 0. */
    static double chance(String rarity)
    {
        if (rarity == null) return 0;
        String r = rarity.replace(",", "").trim();
        switch (r.toLowerCase(Locale.ROOT))
        {
            case "always": return 1;
            case "common": return 0.1;
            case "uncommon": return 0.02;
            case "rare": return 0.002;
            case "very rare": return 0.0002;
            default: break;
        }
        int slash = r.indexOf('/');
        if (slash < 0) return 0;
        try
        {
            double a = Double.parseDouble(r.substring(0, slash).trim()), b = Double.parseDouble(r.substring(slash + 1).trim());
            return b <= 0 ? 0 : Math.min(1, a / b);
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }

    static int intOr(String s, int dflt)
    {
        if (s == null) return dflt;
        String t = s.replace(",", "").trim();
        int i = 0;
        while (i < t.length() && Character.isDigit(t.charAt(i))) i++;
        if (i == 0) return dflt;
        try { return Integer.parseInt(t.substring(0, i)); }
        catch (NumberFormatException e) { return dflt; }
    }

    private static String blankToNull(String s)
    {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

    /** Reads the whole database (about 60k rows, well under a second). */
    public static ItemData load(Connection db) throws SQLException
    {
        ItemData d = new ItemData();
        try (Statement st = db.createStatement())
        {
            Map<Integer, Recipe> byId = new HashMap<>();
            try (ResultSet rs = st.executeQuery("SELECT ID, FACILITIES, TOOLS, IS_MEMBERS, OUTPUT_ITEM_NAME, OUTPUT_QUANTITY, OUTPUT_SUBTEXT FROM PRODUCTION_RECIPES"))
            {
                while (rs.next())
                {
                    String out = rs.getString(5);
                    if (out == null) continue;
                    Recipe r = new Recipe(out, intOr(rs.getString(6), 1), rs.getString(2), rs.getString(3), rs.getString(7), rs.getBoolean(4));
                    byId.put(rs.getInt(1), r);
                    d.add(r);
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT RECIPE_ID, ITEM_NAME, QUANTITY FROM PRODUCTION_MATERIALS"))
            {
                while (rs.next())
                {
                    Recipe r = byId.get(rs.getInt(1));
                    if (r != null && rs.getString(2) != null) r.materials.add(new String[]{rs.getString(2), String.valueOf(intOr(rs.getString(3), 1))});
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT RECIPE_ID, NAME, LEVEL FROM PRODUCTION_SKILLS"))
            {
                while (rs.next())
                {
                    Recipe r = byId.get(rs.getInt(1));
                    if (r != null) r.skills.put(rs.getString(2), intOr(rs.getString(3), 1));
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT si.ITEM_NAME, s.NAME, s.LOCATION, si.CURRENCY, si.BUY_PRICE, s.IS_MEMBERS, si.STOCK FROM SHOP_ITEMS si JOIN SHOPS s ON s.ID = si.SHOP_ID"))
            {
                while (rs.next())
                    if (rs.getString(1) != null)
                    {
                        Shop sh = new Shop(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), intOr(rs.getString(5), -1), rs.getBoolean(6));
                        sh.stock = rs.getString(7) == null ? "" : rs.getString(7).trim();
                        d.add(sh);
                    }
            }
            try (ResultSet rs = st.executeQuery("SELECT NAME, LOCATION, IS_MEMBERS FROM SPAWN_ITEMS"))
            {
                while (rs.next()) d.add(new Spawn(rs.getString(1), rs.getString(2), rs.getBoolean(3)));
            }
            try (ResultSet rs = st.executeQuery("SELECT ITEM_NAME, SOURCE, RARITY, DROP_LEVEL, DROP_TYPE, QUANTITY_LOW, QUANTITY_HIGH FROM DROP_SOURCES"))
            {
                while (rs.next())
                    if (rs.getString(1) != null)
                        d.add(new Drop(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getInt(6), rs.getInt(7)));
            }
            try (ResultSet rs = st.executeQuery("SELECT NAME, ID FROM ITEMS"))
            {
                while (rs.next())
                {
                    d.names.putIfAbsent(key(rs.getString(1)), rs.getString(1));
                    d.byId.putIfAbsent(rs.getInt(2), rs.getString(1));
                }
            }
        }
        return d;
    }
}
