package com.goalguide.facts;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import com.goalguide.items.ItemTree;

/**
 * Anvils, furnaces and banks on the map (places.tsv, from the OSRS Wiki), and which to use: one this account
 * can reach (members, quests, levels), with a bank close by, since you go back and forth. Pure.
 */
public final class Places
{
    private Places() { }

    /** Tiles from a place to a bank that still count as "a bank next door". */
    public static final int BANK_NEAR = 25;

    public static final class Place
    {
        public final String kind, location;
        public final boolean members;
        public final int x, y, plane;
        /** Each a quest key or "SKILL N"; empty when open to all. */
        final List<String> needs;
        /** Tiles to the nearest bank; 0 for a bank. */
        public int bankDist;

        Place(String kind, String location, boolean members, int x, int y, int plane, List<String> needs)
        {
            this.kind = kind;
            this.location = location;
            this.members = members;
            this.x = x;
            this.y = y;
            this.plane = plane;
            this.needs = needs;
        }

        /** True when this account can use it. */
        public boolean open(ItemTree.Account a)
        {
            if (members && !a.members) return false;
            for (String n : needs)
            {
                String[] p = n.split(" ");
                if (p.length == 2 && p[1].chars().allMatch(Character::isDigit))
                {
                    if (a.skills.getOrDefault(p[0], 1) < Integer.parseInt(p[1])) return false;
                }
                else if (!a.questsDone.contains(n)) return false;
            }
            return true;
        }

        /** Tiles to a point, ignoring floors (the map pin is near the object, not on it). */
        public int dist(int px, int py)
        {
            return Math.max(Math.abs(x - px), Math.abs(y - py));
        }
    }

    static final List<Place> ALL = new ArrayList<>();

    static
    {
        for (String line : MethodTables.read("places.tsv"))
        {
            String[] f = line.split("\t");
            if (f.length < 7) continue;
            List<String> needs = new ArrayList<>();
            if (!f[6].equals("-")) for (String n : f[6].split(",")) needs.add(n.trim());
            ALL.add(new Place(f[0], f[1], f[2].equals("yes"), Integer.parseInt(f[3]), Integer.parseInt(f[4]), Integer.parseInt(f[5]), needs));
        }
        for (Place p : ALL)
        {
            if (p.kind.equals("bank")) continue;
            p.bankDist = Integer.MAX_VALUE;
            for (Place b : ALL) if (b.kind.equals("bank")) p.bankDist = Math.min(p.bankDist, b.dist(p.x, p.y));
        }
    }

    /**
     * The places of a kind ("anvil", "furnace", "bank") worth going to: ones this account can use, and of
     * those, ones with a bank next door when there are any. Closest to (px, py) first.
     */
    public static List<Place> best(String kind, ItemTree.Account a, int px, int py)
    {
        String k = kind.toLowerCase(Locale.ROOT);
        List<Place> open = new ArrayList<>(), near = new ArrayList<>();
        for (Place p : ALL)
        {
            if (!p.kind.equals(k) || !p.open(a)) continue;
            open.add(p);
            if (p.bankDist <= BANK_NEAR) near.add(p);
        }
        List<Place> out = near.isEmpty() ? open : near;
        out.sort(Comparator.comparingInt(p -> p.dist(px, py)));
        return out;
    }
}
