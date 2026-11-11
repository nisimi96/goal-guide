@AGENTS.md

# Goal Guide project rules

- Every OSRS fact (items, teleports, rates, quest and diary requirements) comes from the OSRS Wiki. Name the wiki page and the date in the code or data file.
- `GuideIsBotFreeTest` must pass: code lives only in `com.goalguide`, and uses no other plugin besides RuneLite's own. One exception (yuval, 2026-11-11): Goal Guide may post a `PluginMessage` asking Shortest Path or Travel Guide for a route. No code or classes of theirs, and it must work without them.
