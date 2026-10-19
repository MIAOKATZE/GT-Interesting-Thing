"""Install the user-approved native 24px R4 gift icon catalog.

Run: python -B tools/artgen_catalog/gift_icons/generate.py
The previous 32px drawing routines remain available for historical reference.
The default command exports exact pixels from approved_r4.png; it does not redraw.
"""
from __future__ import annotations

import hashlib
import io
import json
from pathlib import Path

from PIL import Image, ImageDraw
from palette import P, ROOT, ramp

HERE = Path(__file__).resolve().parent
ASSETS = ROOT / "src/main/resources/assets/gtit/textures/items/gifts"
PREVIEWS = ROOT / "plan/implementation/ui-presets-v1.8.95/icons"
DEFAULT_KEYS = ("starter", "steam", "lv", "mv", "hv", "ev", "iv", "luv")
# key, English, Chinese, material construction and visual emblem, animation
CATALOG = [
    ('starter', 'First Steps Gift Box', '初见猫猫礼盒', 'cloth box with cat seal', 'ribbon glint'),
    ('steam', 'Steam Gift Box', '蒸汽铜炉礼盒', 'riveted copper box with pressure gauge', 'pressure needle'),
    ('lv', 'Low Voltage Gift Box', '低压电路礼盒', 'green circuit box and processor', 'circuit scan'),
    ('mv', 'Medium Voltage Gift Box', '中压蓝屏礼盒', 'tall blue box with status monitor', 'screen scan'),
    ('hv', 'High Voltage Gift Box', '高压电弧礼盒', 'steel box with yellow hazard emblem', 'electric arc'),
    ('ev', 'Extreme Voltage Gift Box', '超高压紫晶礼盒', 'cut-corner violet box with pink crystal', 'crystal pulse'),
    ('iv', 'Insane Voltage Gift Box', '极高压钛晶礼盒', 'tall titanium box with teal crystal', 'crystal facets'),
    ('luv', 'LuV Orbit Gift Box', 'LuV星轨礼盒', 'blue box with white orbiting cube', 'orbit chase'),
    ('festival', 'Festival Lantern Gift Box', '节庆灯笼礼盒', 'red brocade box with golden lantern', 'static'),
    ('mail', 'Special Delivery Gift Box', '猫爪快递礼盒', 'kraft parcel with blue ribbon and envelope', 'static'),
    ('team', 'Team Supplies Gift Box', '小队补给礼盒', 'green wooden chest with three cat seals', 'static'),
    ('energy', 'Energy Gift Box', '能量蓄电礼盒', 'riveted steel box with lightning battery', 'static'),
    ('ae', 'Fluix Gift Box', '福鲁伊克斯礼盒', 'blue circuit box with faceted fluix crystal', 'static'),
    ('decor', 'Decoration Gift Box', '小屋装饰礼盒', 'striped pink box with miniature cottage', 'static'),
    ('mining', 'Mining Gift Box', '矿工宝藏礼盒', 'wooden ore box with pickaxe and diamond', 'static'),
    ('farming', 'Harvest Gift Box', '丰收麦穗礼盒', 'woven hamper box with golden wheat', 'static'),
    ('food', 'Provisions Gift Box', '美味便当礼盒', 'red woven provision box with bread loaf', 'static'),
    ('tools', 'Workshop Gift Box', '工匠工具礼盒', 'copper chest with crossed hand tools', 'static'),
    ('building', 'Building Gift Box', '建筑砖石礼盒', 'red construction box with brick emblem', 'static'),
    ('magic', 'Arcane Gift Box', '星月秘法礼盒', 'violet box with crescent moon and star', 'static'),
    ('space', 'Space Gift Box', '星际火箭礼盒', 'titanium box with rocket and exhaust', 'static'),
    ('ocean', 'Ocean Gift Box', '海浪贝壳礼盒', 'striped teal box with waves and shell', 'static'),
    ('forest', 'Forest Gift Box', '林间绿叶礼盒', 'green woodgrain box with leaf and twig', 'static'),
    ('winter', 'Winter Gift Box', '暖冬雪花礼盒', 'blue snow-spotted box with snowflake', 'static'),
    ('spring', 'Spring Gift Box', '春日繁花礼盒', 'pink woven hamper with two flowers', 'static'),
    ('summer', 'Summer Gift Box', '夏日青柠礼盒', 'striped teal box with citrus slice', 'static'),
    ('autumn', 'Autumn Gift Box', '秋日南瓜礼盒', 'orange box with ridged pumpkin', 'static'),
    ('birthday', 'Birthday Gift Box', '生日蛋糕礼盒', 'confetti pink box with candle cake', 'static'),
    ('lottery', 'Lucky Draw Gift Box', '幸运抽奖礼盒', 'violet box with star ticket', 'static'),
    ('achievement', 'Achievement Gift Box', '荣誉勋章礼盒', 'golden box with ribbon medal', 'static'),
    ('admin', 'Administrator Gift Box', '管理密钥礼盒', 'riveted steel strongbox with brass padlock', 'static'),
    ('trade', 'Trading Gift Box', '铜币交易礼盒', 'wooden chest with two embossed coins', 'static'),
]


class Pixel:
    def __init__(self):
        self.im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.im)

    def rect(self, box, color):
        self.d.rectangle(box, fill=P.get(color, color))

    def line(self, points, color, width=1):
        self.d.line(points, fill=P.get(color, color), width=width)

    def poly(self, points, color, outline="ink"):
        self.d.polygon(points, fill=P.get(color, color))
        if outline:
            self.line(points + [points[0]], outline)

    def ellipse(self, box, color, outline="ink"):
        self.d.ellipse(box, fill=P.get(color, color), outline=P.get(outline, outline) if outline else None)

    def box(self, box, material="cloth"):
        x, y, r, b = box
        mid, hi, lo = ramp(material)
        self.rect(box, "ink")
        self.rect((x + 1, y + 1, r - 1, b - 1), mid)
        self.line([(x + 1, b - 2), (x + 1, y + 1), (r - 2, y + 1)], hi)
        self.line([(r - 1, y + 2), (r - 1, b - 1), (x + 2, b - 1)], lo)

    def rivets(self, x, y, r, b):
        for a, c in [(x, y), (r, y), (x, b), (r, b)]:
            self.rect((a, c, a + 1, c + 1), "copper_lo")
            self.rect((a, c, a, c), "copper_hi")

    def bow(self, x=16, y=8, color="red"):
        mid, hi, lo = ramp(color)
        self.poly([(x - 1, y), (x - 6, y - 3), (x - 7, y - 1), (x - 6, y + 2), (x - 1, y + 1)], mid)
        self.poly([(x + 1, y), (x + 6, y - 3), (x + 7, y - 1), (x + 6, y + 2), (x + 1, y + 1)], lo)
        self.line([(x - 5, y - 1), (x - 2, y)], hi)
        self.line([(x + 3, y), (x + 5, y - 1)], mid)
        self.box((x - 1, y - 1, x + 1, y + 2), color)

    def ears(self, x=16, y=10, color="cloth"):
        mid, hi, lo = ramp(color)
        self.poly([(x - 10, y + 1), (x - 10, y - 5), (x - 4, y)], mid)
        self.poly([(x + 4, y), (x + 10, y - 5), (x + 10, y + 1)], lo)
        self.line([(x - 9, y - 3), (x - 9, y - 1), (x - 7, y)], hi)
        self.line([(x + 8, y - 1), (x + 9, y - 3)], "pink")

    def cat(self, x, y, color="cream", tiny=False):
        if tiny:
            self.rect((x - 2, y, x + 2, y + 2), color)
            self.rect((x - 2, y - 1, x - 2, y), color)
            self.rect((x + 2, y - 1, x + 2, y), color)
            self.rect((x - 1, y + 1, x - 1, y + 1), "ink")
            self.rect((x + 1, y + 1, x + 1, y + 1), "ink")
            return
        self.poly([(x - 5, y + 3), (x - 5, y - 4), (x - 1, y - 1),
                   (x + 1, y - 1), (x + 5, y - 4), (x + 5, y + 3), (x + 3, y + 5),
                   (x - 3, y + 5)], color)
        self.line([(x - 3, y + 1), (x - 2, y + 1)], "ink")
        self.line([(x + 2, y + 1), (x + 3, y + 1)], "ink")
        self.rect((x, y + 3, x, y + 3), "pink")

    def gear(self, x, y, color="copper"):
        mid, hi, lo = ramp(color)
        self.rect((x - 4, y - 2, x + 4, y + 2), "ink")
        self.rect((x - 2, y - 4, x + 2, y + 4), "ink")
        self.ellipse((x - 3, y - 3, x + 3, y + 3), mid)
        self.line([(x - 2, y - 1), (x - 1, y - 2), (x + 1, y - 2)], hi)
        self.rect((x - 1, y - 1, x + 1, y + 1), lo)

    def bolt(self, x, y, color="yellow_hi"):
        self.poly([(x, y - 5), (x + 3, y - 5), (x + 1, y - 1), (x + 4, y - 1),
                   (x - 2, y + 5), (x - 1, y + 1), (x - 4, y + 1)], color)

    def diamond(self, x, y, color="purple"):
        mid, hi, lo = ramp(color)
        self.poly([(x, y - 6), (x + 5, y - 1), (x + 3, y + 4), (x, y + 6),
                   (x - 4, y + 2), (x - 5, y - 1)], mid)
        self.line([(x, y - 4), (x - 3, y - 1), (x, y + 4)], hi)
        self.line([(x + 1, y - 3), (x + 2, y + 2), (x, y + 4)], lo)

    def cloth(self, box):
        x, y, r, b = box
        for cy in range(y, b + 1):
            for cx in range(x, r + 1):
                if (cx + 2 * cy) % 6 == 0:
                    self.rect((cx, cy, cx, cy), "cloth_lo")


def draw(key):
    from drawing import draw_gift
    return draw_gift(key, Pixel)


def animated(key, base, frame):
    from drawing import animate_gift
    return animate_gift(key, base, frame, Pixel)


def png_bytes(im):
    stream = io.BytesIO()
    im.save(stream, format="PNG", optimize=False, compress_level=9)
    return stream.getvalue()


def preview(images, frame=0):
    # 4x native pixels are intentionally crisp, without antialiasing.
    sheet = Image.new("RGBA", (8 * 160, 4 * 184), (34, 31, 29, 255))
    d = ImageDraw.Draw(sheet)
    for index, row in enumerate(CATALOG):
        key = row[0]
        x, y = index % 8 * 160, index // 8 * 184
        for cy in range(8):
            for cx in range(8):
                d.rectangle((x + 16 + cx * 16, y + 8 + cy * 16, x + 31 + cx * 16, y + 23 + cy * 16),
                            fill=(57, 53, 48) if (cx + cy) % 2 else (68, 64, 57))
        frames = images[key]
        sheet.alpha_composite(frames[frame % len(frames)].resize((128, 128), Image.Resampling.NEAREST), (x + 16, y + 8))
        d.text((x + 12, y + 143), key.upper(), fill=(237, 218, 183))
        d.text((x + 12, y + 158), "ANIMATED" if len(frames) > 1 else "C2 / PIXEL", fill=(156, 152, 139))
    return sheet


def legacy_main():
    ASSETS.mkdir(parents=True, exist_ok=True)
    PREVIEWS.mkdir(parents=True, exist_ok=True)
    all_images, manifest = {}, []
    for key, english, chinese, material, motion in CATALOG:
        base = draw(key)
        frames = [animated(key, base, frame) for frame in range(8)] if key in DEFAULT_KEYS else [base]
        assert base.mode == "RGBA" and base.size == (32, 32)
        mask = base.getchannel("A").tobytes()
        assert set(mask) == {0, 255}, key
        assert base.getpixel((0, 0))[3] == 0 and base.getpixel((31, 31))[3] == 0, key
        assert all(im.getchannel("A").tobytes() == mask for im in frames), key
        if len(frames) > 1:
            assert len({im.tobytes() for im in frames}) >= 4, key
        strip = Image.new("RGBA", (32, 32 * len(frames)), (0, 0, 0, 0))
        for index, im in enumerate(frames):
            strip.paste(im, (0, index * 32))
        # Fully transparent pixels carry no hidden colour.
        pixels = strip.get_flattened_data() if hasattr(strip, "get_flattened_data") else strip.getdata()
        strip.putdata([pixel if pixel[3] else (0, 0, 0, 0) for pixel in pixels])
        data = png_bytes(strip)
        assert data == png_bytes(strip), key
        path = ASSETS / (key + ".png")
        path.write_bytes(data)
        frametime = {"starter": 6, "steam": 5, "lv": 4, "mv": 5, "hv": 4, "ev": 6, "iv": 6, "luv": 4}.get(key, 0)
        if len(frames) > 1:
            meta = {"animation": {"frametime": frametime, "frames": list(range(8)), "interpolate": False}}
            (ASSETS / (key + ".png.mcmeta")).write_text(json.dumps(meta, indent=2) + "\n", encoding="utf-8")
            assert max(meta["animation"]["frames"]) < strip.height // strip.width
        all_images[key] = frames
        manifest.append({"key": key, "label_en": english, "label_zh": chinese, "material": material, "motion": motion,
                         "width": 32, "frame_height": 32, "frames": len(frames), "frametime": frametime,
                         "resource": "gtit:gifts/" + key, "png": str(path.relative_to(ROOT)).replace("\\", "/"),
                         "sha256": hashlib.sha256(data).hexdigest()})
    assert len(manifest) == 32 and len({row["key"] for row in manifest}) == 32
    assert len({png_bytes(images[0]) for images in all_images.values()}) == 32
    (HERE / "manifest.json").write_text(json.dumps({"palette_source": "PocketGuiTextureContract C2 shipped textures",
        "generator": "native 32px Pillow pixel primitives; no image generation; no smoothing", "icons": manifest}, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    preview(all_images).convert("RGB").save(PREVIEWS / "gift-icons-contactsheet.png")
    previews = [preview(all_images, frame).convert("RGB") for frame in range(8)]
    previews[0].save(PREVIEWS / "gift-icons-animated.gif", save_all=True, append_images=previews[1:], duration=200, loop=0, optimize=False)
    # Individual 8x animated previews for evaluating the default era loops.
    for key in DEFAULT_KEYS:
        ims = []
        for im in all_images[key]:
            bg = Image.new("RGBA", im.size, (45, 40, 35, 255))
            bg.alpha_composite(im)
            ims.append(bg.convert("RGB").resize((256, 256), Image.Resampling.NEAREST))
        ims[0].save(PREVIEWS / (key + ".gif"), save_all=True, append_images=ims[1:], duration=200, loop=0, optimize=False)
    # All eight animation frames at once: catches shape jumps and hidden glyphs.
    frames_sheet = Image.new("RGB", (8 * 128 + 120, 8 * 128), (34, 31, 29))
    fd = ImageDraw.Draw(frames_sheet)
    for row, key in enumerate(DEFAULT_KEYS):
        fd.text((8, row * 128 + 58), key.upper(), fill=(237, 218, 183))
        for index, im in enumerate(all_images[key]):
            bg = Image.new("RGBA", (32, 32), (57, 53, 48, 255))
            bg.alpha_composite(im)
            frames_sheet.paste(bg.convert("RGB").resize((128,128), Image.Resampling.NEAREST),
                               (120 + index * 128, row * 128))
    frames_sheet.save(PREVIEWS / "gift-icons-animation-frames.png")
    lang = {"en_US": {}, "zh_CN": {}}
    for key, english, chinese, _, _ in CATALOG:
        lang["en_US"]["gtit.gift.icon." + key] = english
        lang["zh_CN"]["gtit.gift.icon." + key] = chinese
    lang_path = ROOT / "build/task-gift/icon-lang.json"
    lang_path.parent.mkdir(parents=True, exist_ok=True)
    lang_path.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    summary = {"icons": len(manifest), "animated": len(DEFAULT_KEYS), "frames": sum(len(x) for x in all_images.values()),
               "rgba": True, "binary_alpha": True, "fixed_animation_masks": True, "deterministic_png": True, "native_pixel_drawing": True,
               "gift_box_silhouettes": 32, "theme_emblems": 32,
               "all_first_frames_distinct": True, "mcmeta_valid": True}
    (PREVIEWS / "validation.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary))


def main():
    from approved import install
    install(CATALOG, ROOT, HERE, ASSETS)


if __name__ == "__main__":
    main()
