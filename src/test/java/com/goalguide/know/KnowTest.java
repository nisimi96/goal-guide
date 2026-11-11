package com.goalguide.know;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
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
        s.name = "Iron Yuval";
        s.putAccount(m("AGILITY", 31, "PRAYER", 43), m("AGILITY", 15000), Set.of("PRIEST_IN_PERIL", "ARDOUGNE_EASY_DIARY"), Set.of("NATURE_SPIRIT"), 25, true, true, 1000);
        s.putStorage(AccountState.BANK, m("mark of grace", 40, "ranarr seed", 3), 1000);
        s.putStorage(AccountState.INVENTORY, m("mark of grace", 2), 1000);
        s.putStorage(AccountState.SEED_VAULT, m("ranarr seed", 10), 1000);
        s.putPotions(m("prayer potion", 9), m("prayer potion", 2), 1000);
        s.putTimer(AccountState.HERBS, new AccountState.Timer(AccountState.Timer.GROWING, 5000, 1000));
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
        assertFalse(s.putTimer(AccountState.HERBS, new AccountState.Timer(AccountState.Timer.GROWING, 5000, 3000)));
    }

    @Test
    public void jsonRoundTripAndFileSave() throws Exception
    {
        File dir = Files.createTempDirectory("know").toFile();
        Know.dir(dir);
        AccountState s = sample();
        Know.write("abc", Know.toJson(s));
        AccountState back = Know.load("abc");
        assertEquals("Iron Yuval", back.name);
        assertEquals(25, back.questPoints);
        assertEquals(s.owned(), back.owned());
        assertEquals(9, (int) back.storages.get(AccountState.POTION_STORAGE).doses.get("prayer potion"));
        assertEquals(5000, back.timer(AccountState.HERBS).readyAt);
        assertEquals(s.questsDone, back.questsDone);
        assertNotNull(Know.lastSaved());
        // an account never saved is a fresh one, not null
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
        assertNotNull(s.timers);
        assertNull(Know.fromJson(""));
    }

    @Test
    public void fileNamesAreSafe()
    {
        assertEquals("know-a_b_c.json", Know.fileName("a/b:c"));
    }

    @Test
    public void timerText()
    {
        AccountState.Timer t = new AccountState.Timer(AccountState.Timer.GROWING, 10_000, 0);
        assertEquals("in 34 min", AccountState.when(t, 10_000 - 34 * 60));
        assertEquals("in 2 h 5 min", AccountState.when(t, 10_000 - 125 * 60));
        assertEquals("ready", AccountState.when(t, 10_001));
        assertEquals("no data", AccountState.when(null, 0));
        List<String> lines = sample().timerLines(5000_000L + 1000);
        assertEquals("Herbs: ready", lines.get(0));
        assertEquals("Bird houses: no data", lines.get(1));
    }

    @Test
    public void storageLinesAskForTheBankAndVaultOnce()
    {
        AccountState s = new AccountState();
        List<String> lines = s.storageLines(0);
        assertEquals(List.of("bank: not seen yet, open it once", "seed vault: not seen yet, open it once"), lines);
        // below 45 Farming the Farming Guild is shut, so the seed vault is not asked for
        s.putAccount(m("FARMING", 30), m(), Set.of(), Set.of(), 0, true, true, 0);
        assertEquals(List.of("bank: not seen yet, open it once"), s.storageLines(0));
        s.putStorage(AccountState.BANK, m("coins", 5), 0);
        assertEquals("bank: 1 item, 3 h ago", s.storageLines(3 * 3_600_000L).get(0));
    }

    @Test
    public void birdHousesFromTimeTrackingValues()
    {
        long now = 100_000_000L;
        long sec = now / 1000;
        assertEquals(AccountState.Timer.UNKNOWN, Timers.birdHouses(Arrays.asList(null, null, null, null), now).state);
        assertEquals(AccountState.Timer.EMPTY, Timers.birdHouses(Arrays.asList("0:1", "0:1", null, null), now).state);
        assertEquals(AccountState.Timer.BUILT, Timers.birdHouses(Arrays.asList("1:1", "0:1", null, null), now).state);
        // seeded (a multiple of 3) 10 minutes ago: ready in 40
        AccountState.Timer t = Timers.birdHouses(Arrays.asList("3:" + (sec - 600), "6:" + (sec - 1200), "bad", null), now);
        assertEquals(AccountState.Timer.GROWING, t.state);
        assertEquals(sec - 600 + Timers.BIRD_HOUSE_SECONDS, t.readyAt);
        assertEquals(AccountState.Timer.READY, Timers.birdHouses(Arrays.asList("3:" + (sec - 4000), null, null, null), now).state);
    }

    @Test
    public void itemRootsBoxSaysWhatIsKnown()
    {
        String none = com.goalguide.items.ItemRootsPlugin.knowHtml(null, false, 0);
        assertTrue(none.contains("Goal Guide"));
        String html = com.goalguide.items.ItemRootsPlugin.knowHtml(sample(), false, 61_000);
        assertTrue(html, html.contains("Iron Yuval"));
        assertTrue(html, html.contains("saved 1 min ago"));
        assertTrue(html, html.contains("seed vault: 1 item"));
    }

    @Test
    public void abilitiesSaveAndReportChanges()
    {
        AccountState s = new AccountState();
        assertFalse(s.hasHouse());
        Map<String, Integer> ch = new HashMap<>();
        ch.put("explorerRing", 3);
        assertTrue(s.putAbilities("Rimmington", Arrays.asList("pool"), "lunar", "on", ch));
        assertFalse(s.putAbilities("Rimmington", Arrays.asList("pool"), "lunar", "on", ch));
        assertTrue(s.hasHouse());
        AccountState back = Know.fromJson(Know.toJson(s));
        assertEquals("lunar", back.spellbook);
        assertEquals(Integer.valueOf(3), back.charges.get("explorerRing"));
        assertEquals(Arrays.asList("pool"), back.houseHas);
        assertTrue(s.putAbilities("none", Arrays.asList(), "lunar", "on", ch));
        assertFalse(s.hasHouse());
    }
}
