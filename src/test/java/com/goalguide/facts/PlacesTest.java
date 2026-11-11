package com.goalguide.facts;

import java.util.List;
import com.goalguide.items.ItemTree;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlacesTest
{
    @Test
    public void bestAnvilFromVarrockIsVarrockWithABank()
    {
        ItemTree.Account a = ItemTree.Account.fresh();
        a.members = false;
        // Standing in Varrock west bank
        List<Places.Place> anvils = Places.best("anvil", a, 3185, 3436);
        assertFalse(anvils.isEmpty());
        Places.Place p = anvils.get(0);
        assertTrue(p.location, p.location.contains("Varrock"));
        assertFalse(p.members);
        assertTrue(p.bankDist <= Places.BANK_NEAR);
        // A free account never gets a members place or a quest place it hasn't done
        for (Places.Place q : anvils) assertFalse(q.location, q.members);
        assertEquals("furnace", Places.best("furnace", a, 3093, 3494).get(0).kind);
    }
}
