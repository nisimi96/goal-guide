# Notices

## Code

Goal Guide's code is under the BSD 2-Clause license (see `LICENSE`).

## Game data from the OSRS Wiki

These data files hold facts from the [Old School RuneScape Wiki](https://oldschool.runescape.wiki), whose content is licensed under [CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/). They are shared under the same license, not under BSD-2:

- `src/main/resources/com/goalguide/items/item_data.tsv.gz`: recipes, shops, spawns, drops and item ids, from the wiki's item pages (built by `tools/item_data.py` from the wiki item scrape at https://github.com/Hannah-GBS/runelite-wiki-scraper).
- `src/main/resources/com/goalguide/facts/*.tsv`: training methods, rates and item sources. Each row names its wiki page in the `source` column.
- `src/main/resources/com/goalguide/quests/quest_lengths.tsv`: quest lengths and quest points (wiki page Quests/List).

Attribution: "Old School RuneScape Wiki contributors", with links to the pages named in each file. Changes: the facts are reformatted as tab-separated tables for the planner.

Old School RuneScape is a trademark of Jagex Ltd. This plugin is not made or endorsed by Jagex.
