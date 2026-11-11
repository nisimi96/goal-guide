"""Builds src/main/resources/com/goalguide/items/item_data.tsv.gz from the OSRS Wiki item data.

Input: data.sqlite from https://github.com/Hannah-GBS/runelite-wiki-scraper (branch wiki-data, output/data.sqlite),
a scrape of the Old School RuneScape Wiki (https://oldschool.runescape.wiki), whose content is CC BY-NC-SA 3.0.

Usage: python3 tools/item_data.py path/to/data.sqlite
Each line is a record type, then tab-separated fields; \\N is a missing value.
  R recipe:   id, facilities, tools, members(0/1), output item, output quantity, output subtext
  M material: recipe id, item, quantity
  K skill:    recipe id, skill, level
  S shop:     item, shop, location, currency, buy price, members(0/1), stock
  P spawn:    item, location, members(0/1)
  D drop:     item, source, rarity, drop level, drop type, quantity low, quantity high
  I item:     name, id
"""
import datetime
import gzip
import sqlite3
import sys

OUT = "src/main/resources/com/goalguide/items/item_data.tsv.gz"

QUERIES = [
    ("R", "SELECT ID, FACILITIES, TOOLS, IS_MEMBERS, OUTPUT_ITEM_NAME, OUTPUT_QUANTITY, OUTPUT_SUBTEXT FROM PRODUCTION_RECIPES ORDER BY ID"),
    ("M", "SELECT RECIPE_ID, ITEM_NAME, QUANTITY FROM PRODUCTION_MATERIALS ORDER BY ID"),
    ("K", "SELECT RECIPE_ID, NAME, LEVEL FROM PRODUCTION_SKILLS ORDER BY ID"),
    ("S", "SELECT si.ITEM_NAME, s.NAME, s.LOCATION, si.CURRENCY, si.BUY_PRICE, s.IS_MEMBERS, si.STOCK FROM SHOP_ITEMS si JOIN SHOPS s ON s.ID = si.SHOP_ID ORDER BY si.ID"),
    ("P", "SELECT NAME, LOCATION, IS_MEMBERS FROM SPAWN_ITEMS ORDER BY ID"),
    ("D", "SELECT ITEM_NAME, SOURCE, RARITY, DROP_LEVEL, DROP_TYPE, QUANTITY_LOW, QUANTITY_HIGH FROM DROP_SOURCES ORDER BY ID"),
    ("I", "SELECT NAME, ID FROM ITEMS ORDER BY ID"),
]


def cell(v):
    if v is None:
        return "\\N"
    return str(v).replace("\\", "/").replace("\t", " ").replace("\r", " ").replace("\n", " ")


def main(path):
    db = sqlite3.connect(path)
    with gzip.open(OUT, "wt", encoding="utf-8", newline="\n", compresslevel=9) as out:
        out.write("# Item data from the Old School RuneScape Wiki (https://oldschool.runescape.wiki), CC BY-NC-SA 3.0.\n")
        out.write("# Built %s by tools/item_data.py. See NOTICE.md.\n" % datetime.date.today().isoformat())
        for kind, q in QUERIES:
            for row in db.execute(q):
                out.write(kind + "\t" + "\t".join(cell(v) for v in row) + "\n")


if __name__ == "__main__":
    main(sys.argv[1])
