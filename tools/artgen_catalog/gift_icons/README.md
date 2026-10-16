# Gift icon pixel catalog

Run `python -B tools/artgen_catalog/gift_icons/generate.py` from the repository root
with Pillow installed. The script draws native 32×32 pixels directly with integer
rectangles, lines, ellipses and polygons. It never calls image generation or applies
antialiasing. The shipped C2 cloth, wood and copper textures anchor the palette;
era accents, highlights and shadows are declared in `palette.py`.

The 32 distinct silhouettes include eight default progression gifts. Those eight
use eight vertically stacked 32px frames plus Minecraft `.png.mcmeta`. Their alpha
masks never change: only internal glints, pressure needles, circuit scans, arcs,
crystals and orbital lights animate. Remaining icons are one-frame 32px PNGs.

`manifest.json` records dimensions, materials, animations, atlas names and SHA256.
The script verifies RGBA mode, transparent empty space, binary alpha, fixed animated
silhouettes, different frames and distinct first frames. PNGs are deterministic.
Contact sheets, GIFs and `validation.json` are previews under
`plan/implementation/gift-preset-v1.8.93/icons/`; preview scaling is nearest only.
Translation additions are generated separately to `build/task-gift/icon-lang.json`.

Register every `GiftIconCatalog.keys()` entry with an item `IIconRegister`, using
`GiftIconCatalog.atlasResourcePath(key)`. Render its registered `IIcon` against the
Minecraft item texture atlas; Minecraft then advances its `.mcmeta` frames in both
item and GUI renderers. A `UITexture` pointed directly at the tall PNG does not run
Minecraft animations and must not be used to display these catalog icons.
