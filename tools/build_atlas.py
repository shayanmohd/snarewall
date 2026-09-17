"""Builds app/src/main/res/drawable-nodpi/sprite_atlas.png from the Kenney 1-Bit Pack (CC0).

Source: https://kenney.nl/assets/1-bit-pack, Tilesheet/monochrome-transparent.png (16px tiles, 1px gap).
Each entry is (name, column, row). The order is the index used by ui/board/Sprites.kt.
"""
import sys
from PIL import Image

SHEET = sys.argv[1]
OUT = sys.argv[2]
TILES = [
    ("raider", 25, 0), ("runner", 29, 4), ("brute", 30, 6), ("shieldbearer", 28, 0),
    ("swarmling", 31, 5), ("digger", 26, 4), ("jumper", 24, 3), ("flyer", 26, 8),
    ("oilskin", 24, 2), ("frostborn", 31, 2), ("sapper", 28, 2), ("warlord", 28, 3),
    ("ember", 28, 11), ("frost", 28, 12), ("snare", 2, 15), ("oil", 15, 10),
    ("pusher", 28, 21), ("dart", 35, 5), ("hammer", 37, 7), ("grinder", 45, 16),
    ("keep", 43, 20), ("rock_chalk", 20, 5), ("rock_salt", 19, 5), ("rock_fen", 0, 2),
]
src = Image.open(SHEET).convert("RGBA")
out = Image.new("RGBA", (16 * len(TILES), 16), (0, 0, 0, 0))
for i, (_, c, r) in enumerate(TILES):
    tile = src.crop((c * 17, r * 17, c * 17 + 16, r * 17 + 16))
    # Force pure white where opaque so the app can tint with any theme token.
    px = tile.load()
    for y in range(16):
        for x in range(16):
            a = px[x, y][3]
            px[x, y] = (255, 255, 255, 255) if a > 127 else (0, 0, 0, 0)
    out.paste(tile, (i * 16, 0))
out.save(OUT, optimize=True)
print("atlas", out.size, len(TILES))
