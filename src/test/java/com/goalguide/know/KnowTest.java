package com.goalguide.know;

import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.goalguide.items.ItemTree;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class KnowTest
{
    private static Map<String, Integer> m(Object... kv)
    {
        Map<String, Integer> out = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) out.put((String) kv[i], (Integer) kv[i + 1]);
        return out;
    }

    private static AccountState sample()
    {
        AccountState s = new AccountState();
        s.profile = "abc";
        s.name = "Iron Pat";
        s.putAccount(m("AGILITY", 31, "PRAYER", 43), m("AGILITY", 15000), Set.of("PRIEST_IN_PERIL", "ARDOUGNE_EASY_DIARY"), Set.of("NATURE_SPIRIT"), 25, true, true, 1000);
        s.putStorage(AccountState.BANK, m("mark of grace", 40, "ranarr seed", 3), 1000);
        s.putStorage(AccountState.INVENTORY, m("mark of grace", 2), 1000);
        s.putStorage(AccountState.SEED_VAULT, m("ranarr seed", 10), 1000);
        s.putPotions(m("prayer potion", 9), m("prayer potion", 2), 1000);
        return s;
    }

    @Test
    public void accountSumsEveryStorageAndKeepsFieldNames()
    {
        ItemTree.Account a = sample().toAccount();
        assertEquals(31, (int) a.skills.get("AGILITY"));
        assertTrue(a.questsDone.contains("PRIEST_IN_PERIL"));
        assertTrue(a.questsDone.contains("ARDOUGNE_EASY_DIARY"));
        assertTrue(a.questsStarted.contains("NATURE_SPIRIT"));
        assertTrue(a.iron);
        assertTrue(a.members);
        assertEquals(42, (int) a.owned.get("mark of grace"));
        assertEquals(13, (int) a.owned.get("ranarr seed"));
        assertEquals(2, (int) a.owned.get("prayer potion"));
    }

    @Test
    public void countFoldsDosesAndWhereNamesStorages()
    {
        AccountState s = sample();
        assertEquals(2, s.count("Prayer potion(4)"));
        assertEquals(13, s.count("Ranarr seed"));
        Map<String, Integer> w = s.where("ranarr seed");
        assertEquals(List.of(AccountState.BANK, AccountState.SEED_VAULT), List.copyOf(w.keySet()));
    }

    @Test
    public void changesAreReportedOnlyWhenSomethingChanged()
    {
        AccountState s = sample();
        assertFalse(s.putStorage(AccountState.BANK, m("mark of grace", 40, "ranarr seed", 3), 2000));
        assertTrue(s.putStorage(AccountState.BANK, m("mark of grace", 41, "ranarr seed", 3), 2000));
        assertFalse(s.putLevel("AGILITY", 31, 15100));
        assertTrue(s.putLevel("AGILITY", 32, 17000));
    }

    @Test
    public void jsonRoundTripAndFileSave() throws Exception
    {
        Know.init(net.runelite.client.util.Filepath.Unchecked.getRooted(Files.createTempDirectory("know")), new com.google.gson.Gson());
        AccountState s = sample();
        Know.write("abc", Know.toJson(s));
        AccountState back = Know.load("abc");
        assertEquals("Iron Pat", back.name);
        assertEquals(25, back.questPoints);
        assertEquals(s.owned(), back.owned());
        assertEquals(9, (int) back.storages.get(AccountState.POTION_STORAGE).doses.get("prayer potion"));
        assertEquals(s.questsDone, back.questsDone);
        assertNotNull(Know.lastSaved());
        // An account never saved is a fresh one, not null
        AccountState none = Know.load("other");
        assertEquals("other", none.profile);
        assertTrue(none.storages.isEmpty());
    }

    @Test
    public void oldOrBrokenFilesStillLoad()
    {
        AccountState s = Know.fromJson("{\"profile\":\"x\",\"storages\":{\"bank\":null}}");
        assertNotNull(s.levels);
        assertTrue(s.storages.isEmpty());
        assertNull(Know.fromJson(""));
    }

    @Test
    public void fileNamesAreSafe()
    {
        assertEquals("know-a_b_c.json", Know.fileName("a/b:c"));
    }
}
