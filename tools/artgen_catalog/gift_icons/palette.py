"""Gift palette: C2 cloth/copper sampled from shipped textures, plus era accent ramps.

The source colours are asserted against opaque pixels in the real C2 textures. No
external images, generative image service, gradients, resampling or smoothing are used.
New era colours are restrained accent ramps; C2 wood/copper provides the common edge.
"""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
SOURCE = ROOT / "src/main/resources/assets/gtit/textures/gui/pocket"
ANCHORS = {
    "ink": ("POCKET_C2_panel.png", (43, 28, 16)),
    "wood": ("POCKET_C2_panel.png", (86, 59, 38)),
    "cloth": ("POCKET_C2_cloth.png", (201, 168, 120)),
    "cloth_lo": ("POCKET_C2_cloth.png", (186, 155, 111)),
    "cloth_hi": ("POCKET_C2_cloth.png", (203, 171, 125)),
    "copper": ("POCKET_C2_panel.png", (169, 121, 63)),
    "copper_hi": ("POCKET_C2_rivet.png", (199, 168, 130)),
    "copper_lo": ("POCKET_C2_rivet.png", (84, 60, 32)),
}
for key, (source, rgb) in ANCHORS.items():
    image = Image.open(SOURCE / source).convert("RGBA")
    pixels = image.get_flattened_data() if hasattr(image, "get_flattened_data") else image.getdata()
    assert (*rgb, 255) in set(pixels), key


def tint(rgb, amount):
    return tuple(round(c + (255 - c) * amount) for c in rgb)


def shade(rgb, amount):
    return tuple(round(c * amount) for c in rgb)


P = {key: rgb for key, (_, rgb) in ANCHORS.items()}
P.update({
    "cream": tint(P["cloth"], .58),
    "white": (234, 237, 220),
    "steel": (111, 136, 143),
    "steel_hi": (177, 196, 194),
    "steel_lo": (57, 77, 86),
    "green": (99, 146, 91),
    "green_hi": (174, 207, 126),
    "green_lo": (48, 82, 62),
    "blue": (67, 123, 158),
    "blue_hi": (127, 196, 211),
    "blue_lo": (37, 62, 100),
    "yellow": (215, 174, 66),
    "yellow_hi": (248, 221, 129),
    "yellow_lo": (121, 87, 39),
    "purple": (134, 103, 165),
    "purple_hi": (203, 170, 220),
    "purple_lo": (65, 48, 95),
    "pink": (196, 117, 142),
    "pink_hi": (235, 175, 181),
    "pink_lo": (113, 61, 86),
    "red": (183, 80, 67),
    "red_hi": (229, 134, 99),
    "red_lo": (102, 44, 43),
    "teal": (66, 156, 146),
    "teal_hi": (143, 223, 191),
    "teal_lo": (31, 82, 85),
    "orange": (200, 120, 50),
    "orange_hi": (239, 182, 96),
    "orange_lo": (120, 66, 36),
})


def ramp(name):
    if name == "wood":
        return P["wood"], tint(P["wood"], .25), shade(P["wood"], .65)
    if name == "cloth":
        return P["cloth"], P["cream"], P["cloth_lo"]
    if name == "copper":
        return P["copper"], P["copper_hi"], P["copper_lo"]
    return P[name], P[name + "_hi"], P[name + "_lo"]
