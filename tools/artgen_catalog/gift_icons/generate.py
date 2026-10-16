"""Deterministic, hand-drawn 32px Minecraft gift icon catalog (Pillow pixel primitives).

Run: python -B tools/artgen_catalog/gift_icons/generate.py
Every asset is drawn at native resolution. Nearest-neighbour scaling is preview only.
Default era icons have fixed-alpha eight-frame loops; the item atlas owns animation.
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
PREVIEWS = ROOT / "plan/implementation/gift-preset-v1.8.93/icons"
DEFAULT_KEYS = ("starter", "steam", "lv", "mv", "hv", "ev", "iv", "luv")
# key, English, Chinese, distinct silhouette/material, animation type
CATALOG = [
    ("starter", "First Steps", "初见猫猫礼盒", "cloth cat gift", "ribbon glint"),
    ("steam", "Steam Age", "蒸汽铜炉礼盒", "riveted boiler", "pressure/steam"),
    ("lv", "Low Voltage", "低压电路工具箱", "green circuit toolbox", "circuit scan"),
    ("mv", "Medium Voltage", "中压蓝晶礼匣", "blue monitor suitcase", "screen scan"),
    ("hv", "High Voltage", "高压电弧装甲箱", "steel hazard crate", "electric arc"),
    ("ev", "Extreme Voltage", "超高压紫晶礼盒", "violet core hexagon", "reactor pulse"),
    ("iv", "Insane Voltage", "极高压钛金礼匣", "titanium crystal chest", "crystal facets"),
    ("luv", "Ludicrous Voltage", "LuV 蓝光星匣", "white-blue orbital cube", "orbit chase"),
    ("festival", "Festival", "节庆猫灯", "red festival lantern", "static"),
    ("mail", "Special Delivery", "猫爪快递", "sealed mail envelope", "static"),
    ("team", "Team Supplies", "小队补给箱", "three-cat field chest", "static"),
    ("energy", "Energy", "能量蓄电礼筒", "battery carrier", "static"),
    ("ae", "Applied Energistics", "福鲁伊克斯数据匣", "fluix terminal cube", "static"),
    ("decor", "Decoration", "小屋装饰包", "house parcel", "static"),
    ("mining", "Mining", "矿工宝箱", "ore crate and pick", "static"),
    ("farming", "Farming", "丰收麦篮", "woven wheat hamper", "static"),
    ("food", "Provisions", "猫猫便当", "layered bento", "static"),
    ("tools", "Workshop", "工匠工具卷", "canvas tool roll", "static"),
    ("building", "Building", "建筑材料托盘", "brick pallet", "static"),
    ("magic", "Arcane", "星月秘法书", "strapped tome", "static"),
    ("space", "Space", "星际补给舱", "rocket capsule", "static"),
    ("ocean", "Ocean", "漂流海盐瓶", "corked wave bottle", "static"),
    ("forest", "Forest", "林间邮差包", "leaf satchel", "static"),
    ("winter", "Winter", "暖冬围巾礼盒", "scarf and snow parcel", "static"),
    ("spring", "Spring", "春日花篮", "flower basket", "static"),
    ("summer", "Summer", "夏日清凉包", "striped cooler", "static"),
    ("autumn", "Autumn", "秋日南瓜礼盒", "pumpkin gift", "static"),
    ("birthday", "Birthday", "生日猫猫蛋糕", "candle cake gift", "static"),
    ("lottery", "Lucky Draw", "幸运转盘礼盒", "ticket and lucky wheel", "static"),
    ("achievement", "Achievement", "荣誉猫杯", "cat trophy", "static"),
    ("admin", "Administrator", "管理密钥匣", "locked strongbox", "static"),
    ("trade", "Trading", "铜币交易袋", "coin pouch", "static"),
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
    p = Pixel()
    if key == "starter":
        p.ears(y=11)
        p.box((5, 11, 26, 28))
        p.cloth((7, 14, 24, 26))
        p.box((4, 10, 27, 14))
        p.rect((14, 14, 17, 27), "red")
        p.rect((14, 15, 14, 26), "red_hi")
        p.cat(10, 21, tiny=True)
        p.bow(y=9)
        p.rivets(6, 12, 24, 26)
    elif key == "steam":
        p.box((8, 4, 12, 11), "copper")
        p.box((20, 6, 24, 11), "copper")
        p.ellipse((5, 9, 26, 28), "ink", None)
        p.ellipse((6, 10, 25, 27), "copper", None)
        p.line([(8, 16), (8, 13), (11, 11), (20, 11)], "copper_hi")
        p.box((4, 13, 27, 16), "copper")
        p.rect((7, 25, 24, 27), "copper_lo")
        p.box((8, 27, 12, 29), "steel")
        p.box((20, 27, 24, 29), "steel")
        p.ellipse((11, 16, 21, 25), "cream")
        p.line([(16, 21), (18, 18)], "red")
        p.cat(16, 23, "copper", tiny=True)
        p.rivets(6, 14, 25, 24)
        p.rect((9, 5, 11, 7), "steel_lo")
    elif key == "lv":
        p.box((11, 5, 21, 10), "copper")
        p.rect((13, 7, 19, 9), "ink")
        p.ears(y=12, color="green")
        p.box((4, 11, 27, 28), "green")
        p.box((4, 11, 27, 15), "copper")
        p.rect((7, 18, 24, 25), "green_lo")
        p.line([(8, 23), (11, 23), (11, 19), (14, 19)], "green_hi")
        p.line([(18, 24), (23, 24), (23, 19)], "green")
        p.box((13, 17, 19, 24), "steel")
        p.cat(16, 20, "yellow_hi", tiny=True)
        p.rect((8, 13, 10, 14), "steel_hi")
        p.rect((22, 13, 24, 14), "steel_hi")
    elif key == "mv":
        p.box((10, 4, 21, 9), "steel")
        p.rect((12, 6, 19, 8), "ink")
        p.box((5, 9, 26, 28), "blue")
        p.box((7, 12, 24, 23), "steel")
        p.rect((9, 14, 22, 21), "blue_lo")
        p.ears(16, 15, "blue")
        p.cat(16, 17, "blue_hi")
        p.box((4, 23, 27, 27), "steel")
        p.rect((8, 25, 10, 25), "green_hi")
        p.rect((13, 25, 16, 25), "yellow_hi")
        p.rect((21, 25, 23, 25), "copper_hi")
    elif key == "hv":
        p.ears(y=9, color="steel")
        p.box((4, 9, 27, 29), "steel")
        p.box((7, 12, 24, 25), "copper")
        p.rect((9, 14, 22, 23), "steel_lo")
        for x in (6, 24):
            p.rect((x, 11, x + 1, 27), "yellow")
            for y in (13, 18, 23):
                p.line([(x, y), (x + 1, y + 1)], "ink")
        p.bolt(16, 19)
        p.box((11, 6, 21, 10), "steel")
        p.rivets(5, 10, 25, 27)
    elif key == "ev":
        p.ears(y=9, color="purple")
        p.poly([(9, 8), (23, 8), (28, 14), (28, 24), (23, 29), (8, 29), (3, 23), (3, 14)], "purple")
        p.line([(5, 14), (10, 10), (22, 10)], "purple_hi")
        p.poly([(9, 13), (22, 13), (25, 17), (25, 24), (21, 27), (10, 27), (6, 23), (6, 17)], "purple_lo")
        p.diamond(16, 20, "pink")
        p.line([(8, 16), (10, 18), (10, 23)], "copper")
        p.line([(24, 16), (22, 18), (22, 23)], "copper_hi")
        p.bow(16, 10, "purple")
    elif key == "iv":
        p.ears(y=11, color="steel")
        p.poly([(5, 12), (11, 8), (24, 8), (28, 13), (28, 27), (23, 30), (5, 27)], "steel")
        p.poly([(7, 13), (12, 10), (23, 10), (25, 13), (20, 16), (7, 16)], "steel_hi")
        p.rect((7, 17, 25, 25), "steel_lo")
        p.box((12, 12, 20, 29), "copper")
        p.diamond(16, 21, "teal")
        p.line([(8, 19), (8, 23), (10, 24)], "white")
        p.line([(24, 18), (24, 24), (22, 26)], "blue_hi")
        p.rivets(6, 15, 26, 27)
    elif key == "luv":
        p.poly([(16, 5), (26, 10), (26, 23), (16, 29), (6, 23), (6, 10)], "steel_hi")
        p.poly([(16, 7), (24, 11), (16, 16), (8, 11)], "white")
        p.poly([(8, 13), (15, 17), (15, 26), (8, 22)], "blue")
        p.poly([(17, 17), (24, 13), (24, 22), (17, 26)], "blue_lo")
        p.line([(16, 9), (16, 14)], "copper")
        p.cat(16, 13, "teal_hi", tiny=True)
        p.ellipse((2, 12, 29, 23), "ink")
        # Open orbit interior by redrawing the cube; no resampling/masking edges.
        p.line([(3, 17), (6, 20), (14, 23), (22, 22), (28, 18)], "copper_hi", 2)
        p.poly([(8, 13), (15, 17), (15, 21), (8, 19)], "blue")
        p.poly([(17, 17), (24, 13), (24, 20), (17, 22)], "blue_lo")
        p.rect((15, 18, 16, 21), "teal_hi")
        p.ears(16, 9, "steel")
    elif key == "festival":
        p.box((13, 2, 19, 6), "copper")
        p.line([(16, 5), (16, 9)], "copper_hi")
        p.ellipse((5, 8, 26, 25), "red")
        p.line([(8, 12), (8, 21)], "red_hi")
        p.line([(23, 12), (23, 21)], "red_lo")
        p.box((8, 7, 23, 10), "copper")
        p.box((8, 24, 23, 26), "copper")
        p.cat(16, 16, "yellow_hi")
        p.rect((15, 27, 17, 30), "red")
        p.rect((13, 29, 19, 30), "yellow")
    elif key == "mail":
        p.box((3, 10, 28, 27))
        p.poly([(4, 11), (16, 21), (27, 11)], "cream")
        p.line([(4, 26), (11, 19)], "copper")
        p.line([(27, 26), (21, 20)], "copper_lo")
        p.ellipse((12, 18, 21, 26), "red")
        p.cat(16, 21, "yellow_hi", tiny=True)
        p.box((22, 12, 26, 16), "green")
        p.poly([(6, 10), (6, 4), (12, 9)], "cloth")
        p.poly([(20, 9), (26, 4), (26, 10)], "cloth")
    elif key == "team":
        p.box((3, 12, 28, 28), "green")
        p.box((5, 8, 26, 14), "copper")
        p.box((5, 15, 8, 28), "copper")
        p.box((23, 15, 26, 28), "copper")
        for x, y in ((10, 19), (22, 19), (16, 24)):
            p.cat(x, y, "cream", tiny=True)
        p.box((12, 5, 20, 9), "steel")
        p.rect((14, 7, 18, 8), "ink")
        p.rivets(4, 13, 26, 26)
    elif key == "energy":
        p.box((10, 3, 21, 7), "steel")
        p.box((7, 6, 24, 28), "copper")
        p.box((9, 9, 22, 25), "green")
        p.bolt(16, 17)
        p.rect((10, 23, 21, 24), "green_hi")
        p.box((5, 13, 8, 22), "steel")
        p.box((23, 13, 26, 22), "steel")
        p.bow(16, 7, "green")
    elif key == "ae":
        p.poly([(16, 4), (28, 10), (28, 24), (16, 30), (4, 24), (4, 10)], "steel")
        p.poly([(16, 6), (25, 11), (16, 16), (7, 11)], "purple_hi")
        p.poly([(6, 13), (15, 18), (15, 27), (6, 23)], "purple_lo")
        p.poly([(17, 18), (26, 13), (26, 23), (17, 27)], "blue_lo")
        p.line([(19, 19), (24, 16), (24, 21), (20, 24)], "purple_hi")
        p.line([(8, 16), (12, 19), (12, 24)], "blue_hi")
        p.cat(16, 11, "white", tiny=True)
    elif key == "decor":
        p.box((7, 14, 25, 29))
        p.poly([(3, 16), (16, 4), (29, 16)], "red")
        p.line([(7, 14), (16, 6), (24, 14)], "red_hi")
        p.box((11, 20, 17, 29), "copper")
        p.box((20, 19, 23, 23), "blue")
        p.box((21, 5, 25, 11), "copper")
        p.cat(16, 13, "cream", tiny=True)
    elif key == "mining":
        p.box((4, 15, 27, 29), "copper")
        for x in (9, 17, 24):
            p.line([(x, 17), (x, 27)], "copper_lo")
        p.box((3, 17, 28, 19), "steel")
        p.box((3, 25, 28, 28), "steel")
        p.diamond(11, 14, "teal")
        p.line([(16, 23), (24, 6)], "ink", 3)
        p.line([(16, 22), (23, 7)], "copper_hi")
        p.poly([(18, 5), (23, 3), (29, 7), (29, 11), (24, 7), (20, 7)], "steel_hi")
    elif key == "farming":
        p.ellipse((6, 4, 25, 25), "copper")
        p.ellipse((8, 6, 23, 23), (0, 0, 0, 0), None)
        p.poly([(4, 17), (28, 17), (25, 29), (7, 29)], "copper")
        for y in (20, 24, 27):
            p.line([(7, y), (25, y)], "copper_hi")
        for x in (10, 15, 20, 24):
            p.line([(x, 19), (x - 1, 28)], "copper_lo")
        for x, y in ((10, 9), (16, 6), (22, 10)):
            p.line([(x, y), (x + 1, 18)], "green_lo")
            for k in range(3):
                p.line([(x - 2, y + k * 2), (x, y + k * 2 + 2), (x + 2, y + k * 2)], "yellow_hi")
        p.cat(16, 23, "cream", tiny=True)
    elif key == "food":
        p.box((3, 11, 28, 28), "red")
        p.box((5, 10, 26, 23), "copper")
        p.box((7, 12, 17, 21), "cloth")
        p.ellipse((8, 13, 16, 20), "white")
        p.cat(12, 16, "red", tiny=True)
        p.rect((19, 12, 24, 21), "green_lo")
        for x, y in ((20, 14), (23, 17), (20, 20)):
            p.ellipse((x - 1, y - 1, x + 1, y + 1), "red_hi", None)
        p.line([(6, 5), (26, 9)], "copper_hi", 2)
        p.line([(6, 7), (26, 11)], "wood", 1)
        p.rect((5, 25, 26, 26), "red_lo")
    elif key == "tools":
        p.box((4, 13, 28, 29))
        p.cloth((6, 15, 26, 27))
        for x in (8, 15, 23):
            p.box((x - 2, 18, x + 2, 28), "copper")
        p.line([(8, 21), (8, 8)], "steel_lo", 3)
        p.poly([(4, 5), (6, 4), (6, 8), (10, 8), (10, 4), (12, 5), (11, 11), (8, 13), (5, 10)], "steel_hi")
        p.line([(16, 22), (16, 7)], "copper_hi", 2)
        p.box((13, 5, 20, 9), "steel")
        p.line([(23, 21), (23, 6)], "steel_hi", 2)
        p.rect((22, 11, 24, 17), "red")
        p.rect((5, 22, 27, 24), "red_lo")
        p.cat(16, 24, "cream", tiny=True)
    elif key == "building":
        p.box((3, 26, 29, 29), "copper")
        for x, y in ((5, 18), (16, 18), (8, 10), (19, 10)):
            p.box((x, y, x + 9, y + 7), "red")
            p.line([(x + 2, y + 3), (x + 6, y + 3)], "red_hi")
        p.rect((13, 9, 16, 26), "cloth")
        p.bow(15, 8, "green")
        p.rect((5, 27, 7, 28), "copper_hi")
        p.rect((25, 27, 27, 28), "copper_hi")
    elif key == "magic":
        p.poly([(6, 8), (24, 5), (28, 9), (28, 26), (10, 30), (5, 26)], "purple_lo")
        p.poly([(8, 9), (24, 7), (25, 24), (9, 27)], "purple")
        p.line([(10, 10), (23, 9)], "purple_hi")
        p.line([(10, 28), (26, 25)], "cream")
        p.box((6, 17, 27, 21), "copper")
        p.diamond(17, 16, "blue")
        p.rect((6, 10, 7, 26), "copper_lo")
        p.cat(17, 11, "yellow_hi", tiny=True)
    elif key == "space":
        p.poly([(16, 3), (23, 10), (23, 24), (18, 28), (13, 28), (8, 24), (8, 10)], "steel_hi")
        p.poly([(16, 4), (21, 10), (10, 10)], "red")
        p.ellipse((11, 11, 21, 21), "copper")
        p.ellipse((13, 13, 19, 19), "blue_lo")
        p.cat(16, 15, "blue_hi", tiny=True)
        p.poly([(8, 19), (4, 25), (4, 29), (11, 25)], "red")
        p.poly([(23, 19), (28, 25), (28, 29), (21, 25)], "red_lo")
        p.rect((13, 25, 18, 28), "steel_lo")
        p.rect((14, 29, 17, 30), "orange_hi")
    elif key == "ocean":
        p.box((11, 3, 21, 7), "copper")
        p.poly([(11, 8), (21, 8), (21, 11), (26, 15), (26, 27), (23, 30), (8, 30), (5, 27), (5, 15), (11, 11)], "blue")
        p.line([(8, 16), (8, 26)], "blue_hi")
        p.poly([(8, 20), (12, 18), (17, 21), (22, 19), (24, 20), (24, 27), (22, 28), (9, 28)], "teal")
        p.line([(9, 21), (12, 20), (17, 23), (22, 21)], "teal_hi")
        p.rect((11, 12, 21, 15), "cream")
        p.cat(16, 14, "copper", tiny=True)
        p.bow(16, 9, "blue")
    elif key == "forest":
        p.box((10, 4, 22, 18), "copper")
        p.box((12, 6, 20, 17), "wood")
        p.poly([(7, 11), (25, 11), (28, 26), (24, 29), (7, 29), (4, 25)], "green_lo")
        p.poly([(5, 12), (26, 12), (23, 22), (8, 22)], "green")
        p.line([(8, 14), (23, 14)], "green_hi")
        p.box((14, 20, 18, 24), "copper")
        p.poly([(16, 9), (22, 5), (24, 9), (20, 15), (15, 17)], "green_hi")
        p.line([(16, 16), (22, 8)], "green_lo")
        p.cat(11, 25, "cloth", tiny=True)
    elif key == "winter":
        p.ears(y=11, color="blue")
        p.box((5, 12, 26, 29), "blue")
        p.box((4, 10, 27, 14), "steel")
        p.rect((8, 14, 11, 28), "white")
        p.rect((7, 15, 25, 18), "red")
        p.rect((19, 17, 23, 27), "red")
        p.rect((19, 22, 23, 23), "cream")
        p.line([(20, 27), (20, 29), (22, 29), (22, 27)], "red_hi")
        p.line([(14, 22), (14, 27)], "white")
        p.line([(11, 24), (17, 24)], "white")
        p.rect((12, 22, 16, 26), "blue_hi")
        p.rect((14, 23, 14, 25), "white")
        p.rect((13, 24, 15, 24), "white")
    elif key == "spring":
        p.ellipse((6, 4, 25, 25), "copper")
        p.ellipse((8, 6, 23, 23), (0, 0, 0, 0), None)
        p.poly([(5, 18), (27, 18), (24, 29), (8, 29)], "cloth")
        for y in (21, 24, 27):
            p.line([(8, y), (24, y)], "copper")
        for x, y, color in ((10, 12, "pink"), (20, 10, "purple"), (16, 17, "red")):
            p.line([(x, y), (x, 23)], "green_lo")
            for dx, dy in ((-2, 0), (2, 0), (0, -2), (0, 2)):
                p.ellipse((x + dx - 1, y + dy - 1, x + dx + 1, y + dy + 1), color, None)
            p.rect((x, y, x, y), "yellow_hi")
        p.bow(16, 25, "pink")
    elif key == "summer":
        p.box((4, 12, 27, 29), "teal")
        for x in (8, 16, 24):
            p.rect((x, 15, x + 1, 27), "cream")
        p.box((3, 11, 28, 15), "steel")
        p.box((10, 7, 21, 11), "steel")
        p.rect((12, 9, 19, 10), "ink")
        p.cat(16, 22, "yellow_hi")
        p.line([(23, 10), (25, 3), (29, 3)], "pink_hi", 2)
        p.ellipse((3, 3, 10, 10), "yellow_hi")
        p.line([(7, 4), (7, 9)], "orange")
        p.line([(4, 7), (9, 7)], "orange")
    elif key == "autumn":
        p.ears(y=12, color="orange")
        p.box((14, 3, 18, 9), "green")
        p.ellipse((3, 9, 28, 29), "orange")
        p.ellipse((8, 10, 23, 28), "orange_hi")
        p.ellipse((13, 10, 19, 28), "orange")
        p.line([(16, 11), (16, 27)], "orange_lo")
        p.rect((5, 18, 26, 21), "green_lo")
        p.bow(16, 17, "green")
        p.cat(16, 24, "yellow_hi", tiny=True)
    elif key == "birthday":
        p.box((4, 19, 27, 28), "pink")
        p.box((7, 11, 24, 20), "cloth")
        p.rect((5, 19, 26, 21), "cream")
        p.rect((8, 11, 23, 13), "white")
        for x in (9, 17, 23):
            p.rect((x, 21, x + 1, 23), "cream")
        p.cat(16, 16, "red", tiny=True)
        p.rect((14, 5, 17, 10), "blue")
        p.poly([(16, 1), (18, 4), (16, 5), (14, 4)], "yellow_hi")
        p.box((2, 28, 29, 30), "copper")
        p.rect((13, 24, 17, 27), "pink_lo")
    elif key == "lottery":
        p.ellipse((3, 4, 27, 28), "copper")
        p.ellipse((5, 6, 25, 26), "cream")
        p.poly([(15, 16), (7, 10), (15, 7)], "red", None)
        p.poly([(15, 16), (24, 12), (24, 19)], "green", None)
        p.poly([(15, 16), (14, 25), (7, 21)], "blue", None)
        p.line([(15, 8), (15, 24)], "copper")
        p.line([(7, 12), (23, 21)], "copper")
        p.line([(7, 21), (23, 12)], "copper")
        p.ellipse((11, 12, 19, 20), "yellow")
        p.cat(15, 15, "cream", tiny=True)
        p.poly([(25, 2), (29, 4), (25, 9), (22, 6)], "red")
        p.box((20, 22, 30, 28), "cloth")
        p.rect((23, 24, 27, 25), "red")
    elif key == "achievement":
        p.ellipse((3, 8, 14, 20), "copper")
        p.ellipse((5, 10, 12, 18), (0, 0, 0, 0), None)
        p.ellipse((18, 8, 29, 20), "copper")
        p.ellipse((20, 10, 27, 18), (0, 0, 0, 0), None)
        p.ears(y=9, color="yellow")
        p.poly([(8, 8), (24, 8), (23, 18), (18, 23), (18, 26), (14, 26), (14, 23), (9, 18)], "yellow")
        p.line([(10, 10), (10, 16), (14, 21)], "yellow_hi")
        p.cat(16, 14, "cream", tiny=True)
        p.box((9, 26, 23, 29), "copper")
        p.rect((12, 27, 20, 28), "yellow_hi")
    elif key == "admin":
        p.box((4, 8, 27, 29), "steel")
        p.box((7, 11, 24, 26), "steel")
        p.box((9, 13, 22, 24), "blue")
        p.ellipse((12, 12, 20, 21), "copper")
        p.ellipse((14, 14, 18, 19), "steel_lo")
        p.box((11, 18, 21, 25), "copper")
        p.rect((15, 20, 17, 23), "ink")
        p.rivets(5, 9, 25, 27)
        p.bow(16, 7, "red")
    elif key == "trade":
        p.poly([(10, 11), (8, 6), (14, 7), (17, 4), (20, 7), (25, 6), (22, 12),
                (27, 21), (26, 27), (22, 30), (9, 30), (5, 26), (5, 21)], "cloth")
        p.line([(9, 19), (8, 25), (11, 28)], "cream")
        p.line([(22, 18), (24, 25), (21, 28)], "copper")
        p.box((8, 11, 24, 14), "red")
        p.ellipse((10, 17, 22, 28), "copper")
        p.ellipse((12, 18, 20, 26), "yellow")
        p.cat(16, 21, "cream", tiny=True)
        p.bow(16, 12, "red")
    else:
        raise KeyError(key)
    return p.im


def animated(key, base, frame):
    p = Pixel()
    p.im = base.copy()
    p.d = ImageDraw.Draw(p.im)
    level = (0, 1, 2, 3, 3, 2, 1, 0)[frame]
    if key == "starter":
        p.rect((14, 16 + frame, 14, 18 + frame), (P["red"], P["red_hi"], P["pink_hi"], P["cream"])[level])
        p.rect((22, 12, 23, 12), (P["copper"], P["copper_hi"], P["cream"], P["white"])[level])
    elif key == "steam":
        p.rect((9, 5, 11, 7), "steel_lo")
        p.line([(9, 7 - frame % 3), (10, 6 - frame % 2), (11, 7 - frame % 3)], (P["steel"], P["steel_hi"], P["cream"], P["white"])[level])
        p.rect((14, 18, 18, 21), "cream")
        p.line([(16, 21), (14 + frame % 5, 18)], "red")
    elif key == "lv":
        p.line([(8, 23), (11, 23), (11, 19)], "green_hi")
        coords = [(8, 23), (9, 23), (10, 23), (11, 23), (11, 22), (11, 21), (11, 20), (11, 19)]
        x, y = coords[frame]
        p.rect((x, y, x, y), "white")
    elif key == "mv":
        y = 14 + frame
        # LCD scan is constrained to the screen; the cat remains legible in every frame.
        p.rect((10, y, 11, y), "blue_hi")
        p.rect((21, y, 22, y), "white")
        p.rect((8, 25, 10, 25), (P["green_lo"], P["green"], P["green_hi"], P["white"])[level])
    elif key == "hv":
        if frame in (1, 2, 5, 6):
            y = 15 + frame % 3
            p.line([(10, y), (12, y + 1), (11, y + 3), (13, y + 4)], "blue_hi" if frame % 2 else "white")
            p.line([(20, y + 1), (21, y), (20, y + 3)], "blue_hi")
    elif key == "ev":
        p.rect((16, 16, 16, 22), (P["pink_lo"], P["pink"], P["pink_hi"], P["white"])[level])
        for x in (8, 24):
            p.rect((x, 17 + frame % 6, x, 18 + frame % 6), "purple_hi")
    elif key == "iv":
        p.line([(16, 17), (13, 20), (16, 25)], (P["teal"], P["teal_hi"], P["cream"], P["white"])[level])
        p.rect((8, 20 + frame % 4, 8, 21 + frame % 4), "white")
    elif key == "luv":
        coords = [(4, 18), (7, 20), (11, 22), (15, 23), (20, 22), (24, 21), (27, 19), (28, 17)]
        x, y = coords[frame]
        p.rect((x, y, x, y), "white")
        p.rect((15, 18, 16, 21), (P["blue"], P["blue_hi"], P["teal_hi"], P["white"])[level])
    # Only alter RGB inside existing opaque pixels. Fixed silhouette is a hard invariant.
    p.im.putalpha(base.getchannel("A"))
    return p.im


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


def main():
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
    lang = {"en_US": {}, "zh_CN": {}}
    for key, english, chinese, _, _ in CATALOG:
        lang["en_US"]["gtit.gift.icon." + key] = english
        lang["zh_CN"]["gtit.gift.icon." + key] = chinese
    lang_path = ROOT / "build/task-gift/icon-lang.json"
    lang_path.parent.mkdir(parents=True, exist_ok=True)
    lang_path.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    summary = {"icons": len(manifest), "animated": len(DEFAULT_KEYS), "frames": sum(len(x) for x in all_images.values()),
               "rgba": True, "binary_alpha": True, "fixed_animation_masks": True, "deterministic_png": True,
               "all_first_frames_distinct": True, "mcmeta_valid": True}
    (PREVIEWS / "validation.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary))


if __name__ == "__main__":
    main()
