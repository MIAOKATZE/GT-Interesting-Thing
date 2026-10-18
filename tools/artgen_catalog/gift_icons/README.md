# Approved gift icon catalog

Run `python -B tools/artgen_catalog/gift_icons/generate.py` with Pillow installed.
The current command exports 32 static 24×24 RGBA sprites from the native
`approved_r4.png` master. It preserves the exact user-approved R4 colors and
half-pixel black-gray outline coverage; it does not invoke image generation.

The approved artwork originated as an ImageGen concept and was refined through
the R1–R4 review under `plan/implementation/ui-review-20261018*`. The R4 outline
uses `#2c2e30` / `#434547`, with partial alpha for 0.5px coverage. Pixel values
in the native master are the source of truth; nearest-neighbor scaling is only
used for previews.

All sprites are now static, including the eight progression gifts. The exporter
removes stale eight-frame `.png.mcmeta` files and also updates the legacy
`items/starter_gift.png` fallback. Registry IDs remain unchanged. Register and
render catalog items through the Minecraft item atlas as before.

`manifest.json` records dimensions, registration paths and hashes. The exporter
writes a contact sheet and validation to `build/ui-v1.8.97/icons/`. Historical
32px drawing routines remain in `drawing.py` / `palette.py` and `legacy_main`,
but they are not used to generate the approved shipped assets.
