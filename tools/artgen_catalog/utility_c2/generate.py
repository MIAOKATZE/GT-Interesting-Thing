"""Deterministic utility-only C2 states from existing Pocket artwork.

Run: python tools/artgen_catalog/utility_c2/generate.py --preview
Pocket source files remain read-only. The preview is a resource composition,
not a Minecraft screenshot or evidence of in-game rendering/input behavior.
"""
from pathlib import Path
import argparse
from PIL import Image, ImageDraw, ImageEnhance, ImageOps

ROOT = Path(__file__).resolve().parents[3]
GUI = ROOT / "src/main/resources/assets/gtit/textures/gui"
OUT = GUI / "utility"
NEAREST = Image.Resampling.NEAREST


def generate():
    OUT.mkdir(parents=True, exist_ok=True)
    specs = [("button_hover", "btn", 1.16, False),
             ("button_selected_hover", "btn_pressed", 1.14, False),
             ("button_disabled", "btn", 0.78, True)]
    for name, source, factor, gray in specs:
        original = Image.open(GUI / "pocket" / ("POCKET_C2_" + source + ".png")).convert("RGBA")
        rgb = original.convert("RGB")
        if gray:
            rgb = ImageOps.colorize(ImageOps.grayscale(rgb), (55, 49, 40), (124, 110, 86))
        else:
            rgb = ImageEnhance.Brightness(rgb).enhance(factor)
        rgba = rgb.convert("RGBA")
        rgba.putalpha(original.getchannel("A"))
        rgba.save(OUT / (name + ".png"))


def nine(texture, size, margin):
    out = Image.new("RGBA", size)
    x = [0, margin, texture.width - margin, texture.width]
    y = [0, margin, texture.height - margin, texture.height]
    dx = [0, margin, size[0] - margin, size[0]]
    dy = [0, margin, size[1] - margin, size[1]]
    for row in range(3):
        for col in range(3):
            tile = texture.crop((x[col], y[row], x[col + 1], y[row + 1]))
            tile = tile.resize((dx[col + 1] - dx[col], dy[row + 1] - dy[row]), NEAREST)
            out.alpha_composite(tile, (dx[col], dy[row]))
    return out


def preview():
    def source(name):
        return Image.open(GUI / "pocket" / ("POCKET_C2_" + name + ".png")).convert("RGBA")

    panel = Image.new("RGBA", (620, 350), (34, 28, 22, 255))
    d = ImageDraw.Draw(panel)
    d.text((12, 10), "C2 RESOURCE PREVIEW - NOT AN IN-GAME SCREENSHOT", fill=(255, 231, 185))
    body = nine(source("panel"), (598, 310), 10)
    panel.alpha_composite(body, (10, 30))
    corner = source("corner")
    for x, y in [(10, 30), (594, 30), (10, 326), (594, 326)]:
        panel.alpha_composite(corner, (x, y))
    d.text((28, 46), "Gift library / Default trade groups / Administration", fill=(51, 39, 27))
    cloth = source("cloth")
    for x in range(28, 592, 32):
        for y in range(68, 220, 32):
            tile = cloth.crop((0, 0, min(32, 592-x), min(32, 220-y)))
            panel.alpha_composite(tile, (x, y))
    d.rectangle((27, 67, 592, 220), outline=(140, 100, 59))
    d.text((38, 80), "Woven inset - dark brown body text", fill=(51, 39, 27))
    d.text((38, 100), "Copper frame / warm cloth / independent control states", fill=(104, 83, 60))
    panel.alpha_composite(nine(source("coinbar"), (330, 22), 4), (38, 124))
    d.text((46, 130), "Gift item: steam_gift", fill=(51, 39, 27))
    for i, key in enumerate(["starter", "steam", "lv", "mv", "hv", "iv"]):
        art = Image.open(ROOT / ("src/main/resources/assets/gtit/textures/items/gifts/" + key + ".png")).convert("RGBA")
        panel.alpha_composite(nine(source("btn_pressed" if i == 1 else "btn"), (34, 30), 4), (38+i*42, 166))
        panel.alpha_composite(art.crop((0, 0, 32, 32)).resize((24, 24), NEAREST), (43+i*42, 168))
    states = [("normal", source("btn")), ("hover", Image.open(OUT/"button_hover.png")),
              ("pressed", source("btn_pressed")), ("selected+hover", Image.open(OUT/"button_selected_hover.png")),
              ("disabled", Image.open(OUT/"button_disabled.png"))]
    for i, (label, texture) in enumerate(states):
        x = 28 + i*112
        panel.alpha_composite(nine(texture.convert("RGBA"), (104, 30), 4), (x, 240))
        d.text((x+5, 249), label, fill=(255, 231, 185))
    d.text((28, 292), "Actual Pocket C2 sources; only hover/disabled derivatives are utility-local.", fill=(51, 39, 27))
    output = ROOT / "build/task-ui/c2-style-preview-v1896.png"
    output.parent.mkdir(parents=True, exist_ok=True)
    panel.convert("RGB").resize((1240, 700), NEAREST).save(output)
    print(output)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--preview", action="store_true")
    args = parser.parse_args()
    generate()
    if args.preview:
        preview()
