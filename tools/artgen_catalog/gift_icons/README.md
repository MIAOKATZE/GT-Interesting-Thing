# Gift icon pixel catalog

Run `python -B tools/artgen_catalog/gift_icons/generate.py` from the repository root
with Pillow installed. The script draws native 32×32 pixels directly with integer
rectangles, lines, ellipses and polygons. It never calls image generation or applies
antialiasing. The shipped C2 cloth, wood and copper textures anchor the palette;
era accents, highlights and shadows are declared in `palette.py`.

All 32 themes are visibly wrapped gift boxes: a stepped shaded body, raised lid,
offset ribbon and pixel bow. Four box proportions are combined with cloth, woven,
woodgrain, striped and riveted-metal construction. Individually drawn emblems
(cat, gauge, circuit, screen, lightning, crystals, lantern, envelope, wheat, tools,
house, bricks, moon, rocket, wave, leaf, snowflake, flowers, citrus, pumpkin, cake,
ticket, medal, lock and coins) carry the theme instead of colour changes alone.
The drawing instructions live in `drawing.py`; every line is an integer pixel.

The 32 themes include eight default progression gifts. Those eight
use eight vertically stacked 32px frames plus Minecraft `.png.mcmeta`. Their alpha
masks never change: only internal glints, a pressure needle, circuit/screen scans,
arcs, crystals and orbital lights animate. Remaining icons are one-frame 32px PNGs.

`manifest.json` records dimensions, materials, animations, atlas names and SHA256.
The script verifies RGBA mode, transparent empty space, binary alpha, fixed animated
silhouettes, different frames and distinct first frames. PNGs are deterministic.
Contact sheets, GIFs and `validation.json` are previews under
`plan/implementation/ui-presets-v1.8.95/icons/`; preview scaling is nearest only.
`gift-icons-animation-frames.png` shows every frame side by side for visual QA.
Translation additions are generated separately to `build/task-gift/icon-lang.json`.

Register every `GiftIconCatalog.keys()` entry with an item `IIconRegister`, using
`GiftIconCatalog.atlasResourcePath(key)`. Render its registered `IIcon` against the
Minecraft item texture atlas; Minecraft then advances its `.mcmeta` frames in both
item and GUI renderers. A `UITexture` pointed directly at the tall PNG does not run
Minecraft animations and must not be used to display these catalog icons.
