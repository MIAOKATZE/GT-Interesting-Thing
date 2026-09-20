"""R6 位图清单单一权威：猫猫无限存储单元物品图标三套候选（只出候选，不落地）。

用户口径（原话）：「把之前的无限元件的贴图拿过来做对照，边缘不要大改，调调颜色就行，
中间加入一些猫猫元素即可。」⇒ 形状 / 轮廓 / 明度层级逐字沿用 `infinity_cell.png`，
本清单只登记三件事：配色角色映射、中心猫猫像素表、动画帧表。

纪律：
- 本模块**不写任何字面 RGB**：颜色 = 「钉点 (来源图, RGB)」× 亮度系数 k(<=1，保色相)。
  钉点在加载时按「该 RGB 确实出现在该实图中」核对（`load_palette`）。
- 轮廓集（非透明且 4-邻接透明/越界）在生成器里冻结为基准图字节，任何方案/任何帧都不得改。
- 脚本不含魔数：帧数 / frametime / 中心框 / 呼吸档位 / 通道点亮表 / 门限全部在此登记。
"""
from __future__ import annotations

import hashlib
from pathlib import Path

from PIL import Image

# ---------------------------------------------------------------- 源与字节钉

ITEM_DIR = Path("src/main/resources/assets/gtit/textures/items")

REF_SHA = {
    "infinity_cell.png":
        "074e595027d57b46ab8ef5125b7726629d941c0ae9bc8b6497f1fac6a8955ec5",
    "infinity_fluid_cell.png":
        "3eb2a4c0d5a90ecffc5a7e2f253889c3f09302a310f387c2424e551f441d9bba",
    "miao_coin.png":
        "65c73db9baf2c76c8dbf7397dd26f523369bb6e343f13d1abe66b67f40696612",
    "neko_infinity_unit.png":
        "d9822f7a6018840938da0f2e8cf3346c8288e00b418219bafbf06945a1989cb6",
    "neko_infinity_unit.png.mcmeta":
        "f40687b33732ca9dc467e6ed874f6d5945675c8fd645ab98efbb27b929b76577",
}

CELL = "infinity_cell.png"
FLUID = "infinity_fluid_cell.png"
COIN = "miao_coin.png"

# ---------------------------------------------------------------- 基准形状


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def check_sources(root: Path) -> list[str]:
    """三张基准图 + 现役图与 mcmeta 字节必须与钉值一致（本任务全程只读）。"""
    bad = []
    for name, want in REF_SHA.items():
        p = root / name
        got = sha256(p) if p.exists() else "<missing>"
        if got != want:
            bad.append(f"{name}: {got[:12]} != {want[:12]}")
    return bad


def load_base(root: Path) -> list[list[tuple[int, int, int, int]]]:
    im = Image.open(root / CELL).convert("RGBA")
    assert im.size == (16, 16), im.size
    return [[im.getpixel((x, y)) for x in range(16)] for y in range(16)]


def contour_set(grid, ring8: bool = False) -> set:
    """轮廓集：非透明且紧邻透明/越界的像素（默认 4 邻域口径）。"""
    out = set()
    nb = ([(1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (1, -1), (-1, 1), (-1, -1)]
          if ring8 else [(1, 0), (-1, 0), (0, 1), (0, -1)])
    for y in range(16):
        for x in range(16):
            if grid[y][x][3] == 0:
                continue
            for dx, dy in nb:
                nx, ny = x + dx, y + dy
                if not (0 <= nx < 16 and 0 <= ny < 16) or grid[ny][nx][3] == 0:
                    out.add((x, y))
                    break
    return out


# ---------------------------------------------------------------- 颜色钉点
# name -> (来源图, 期望 RGB)；load_palette 断言该 RGB 确实出现在该实图里。

REF_POINTS = {
    # infinity_cell：结构层（角色语义与基准图一一对应）
    "cell_outline":  (CELL, (40, 40, 40)),        # A 外壳主灰（轮廓冻结）
    "cell_bevel":    (CELL, (64, 64, 64)),        # B 受光倒角
    "cell_band":     (CELL, (0, 162, 232)),       # C 通道青带
    "cell_cavity":   (CELL, (0, 0, 0)),           # D 暗腔
    "cell_spark":    (CELL, (255, 242, 0)),       # E 中心高光徽记（本轮由猫脸承接同层）
    "cell_shell":    (CELL, (36, 29, 36)),        # F 下体暗壳
    "cell_accent1":  (CELL, (197, 21, 201)),      # G 角标最亮
    "cell_accent2":  (CELL, (138, 31, 140)),      # H 角标中
    "cell_accent3":  (CELL, (95, 28, 97)),        # I 角标暗
    "cell_green":    (CELL, (25, 255, 0)),        # J 源质绿点
    # infinity_fluid_cell：同族流体读法
    "fluid_magenta": (FLUID, (200, 111, 200)),
    "fluid_pale":    (FLUID, (255, 231, 255)),
    # miao_coin：家族金暖系（猫脸币语汇）
    "coin_pale":     (COIN, (254, 254, 192)),
    "coin_bright":   (COIN, (254, 245, 124)),
    "coin_gold":     (COIN, (254, 224, 74)),
    "coin_amber":    (COIN, (253, 223, 73)),
    "coin_mid":      (COIN, (253, 208, 55)),
    "coin_warm":     (COIN, (243, 157, 22)),
    "coin_deep":     (COIN, (203, 88, 23)),
    "coin_rim":      (COIN, (178, 86, 19)),
    "coin_bronze":   (COIN, (166, 61, 3)),
    "coin_brown1":   (COIN, (132, 46, 7)),
    "coin_brown2":   (COIN, (111, 37, 2)),
    "coin_brown3":   (COIN, (88, 27, 0)),
}


def load_palette(root: Path) -> dict:
    """按钉点读实图并核对；返回 name -> RGB。任一钉点不在实图里就抛错。"""
    present: dict[str, set] = {}
    pal: dict = {}
    for name, (src, want) in REF_POINTS.items():
        if src not in present:
            im = Image.open(root / src).convert("RGB")
            present[src] = set(im.getdata())
        if want not in present[src]:
            raise AssertionError(f"钉点 {name} {want} 不存在于 {src}")
        pal[name] = want
    return pal


def shade(pal: dict, ref: str, k: float = 1.0):
    """唯一允许的派生手段：基色 x 亮度系数（k<=1，保持色相）。返回 (RGB, 溯源说明)。"""
    assert 0.0 < k <= 1.0, k
    src, want = REF_POINTS[ref]
    r, g, b = pal[ref]
    rgb = (round(r * k), round(g * k), round(b * k))
    tag = "%02x%02x%02x" % want
    if rgb == want:
        return rgb, "%s #%s" % (src, tag)
    return rgb, "%s #%s x%.2f" % (src, tag, k)


def luma(rgb) -> float:
    return 0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2]


def rgb_dist(a, b) -> float:
    return sum((x - y) ** 2 for x, y in zip(a, b)) ** 0.5


# ---------------------------------------------------------------- 猫猫像素表
# 除双耳各向上顶 1 px（EAR_BREAK_THROUGH，门限 <=2 px，用户口径已允许"耳朵顶破轮廓"）外，
# 全部落在基准图暗腔 D / 高光 E 像素内（中心 8x8 之内），不触碰任何轮廓像素。
# slot: hi=面部受光金（耳+颅顶+眼周）, mid=面部背光金（颊+下颌）, nose=小鼻, eye=眼（露暗腔）。

FACE_PIXELS = {
    (6, 3): "hi", (9, 3): "hi",                   # 耳尖（顶破内圈青带，共 2 px）
    (6, 4): "hi", (9, 4): "hi",                   # 耳基
    (6, 5): "hi", (7, 5): "hi",
    (8, 5): "hi", (9, 5): "hi",                   # 颅顶 4 px（(5,5)(10,5) 留黑做耳-颊空档）
    (5, 6): "hi", (6, 6): "eye", (7, 6): "hi",
    (8, 6): "hi", (9, 6): "eye", (10, 6): "hi",   # 双眼 + 宽颊
    (5, 7): "mid", (6, 7): "mid", (7, 7): "nose",
    (8, 7): "nose", (9, 7): "mid", (10, 7): "mid",
    (6, 8): "mid", (7, 8): "mid", (8, 8): "mid", (9, 8): "mid",   # 下颌
}
FACE_SLOTS = ("hi", "mid", "nose", "eye")
FACE_BASE_OK = {(0, 0, 0), (255, 242, 0)}         # 只允许覆盖基准图的暗腔/高光像素
EAR_BREAK_THROUGH = [(6, 3), (9, 3)]              # 猫耳顶破内圈的 2 px（基准此处为青带 C）
EAR_BREAK_BASE = (0, 162, 232)
GATE_EAR_BREAK_MAX = 2
# 留黑的暗腔像素：耳隙 / 颅侧 / 颏下，保住「暗腔洞口」读法
FACE_KEEP_DARK = [(7, 3), (8, 3), (7, 4), (8, 4), (5, 5), (10, 5), (7, 9), (8, 9)]

# 三通道角标组：逐字沿用基准图 G/H/I/J 斜条位置（底行补 2px 凑成 5px 横条，均为内部像素）
MARKER_GROUPS = {
    "item": [(2, 8), (3, 9), (4, 10), (5, 11)],
    "fluid": [(13, 8), (12, 9), (11, 10), (10, 11)],
    "essen": [(6, 12), (7, 12), (8, 12), (9, 12), (10, 12)],
}
MARKER_BASE_OK = {(197, 21, 201), (138, 31, 140), (95, 28, 97), (25, 255, 0), (40, 40, 40)}

CENTER_BOX = [(x, y) for y in range(4, 12) for x in range(4, 12)]   # 中心 8x8

# ---------------------------------------------------------------- 门限（收紧不放宽）

GATE_CONTOUR = 90.0        # 轮廓像素逐位（含色）重合度下限 %
GATE_BLINK_DL = 25.0       # 每枚角标 亮/暗 两态的最小明度差
GATE_HIER_PAIR_MIN = 24.0  # 「三色并存」方案三枚角标两两 RGB 距离下限
LAYER_CHAIN = ("cavity", "shell", "outline", "bevel", "band", "face_mid", "face_hi")

# 锁死为基准图字节的中性层（真正的"边缘不大改"）：轮廓灰 A、倒角灰 B、暗壳紫黑 F 一律不改色，
# 改色只发生在青带 C、暗腔 D、角标 G/H/I/J 位与中心徽记上。
FROZEN_NEUTRALS = {"outline": "cell_outline", "bevel": "cell_bevel", "shell": "cell_shell"}

# ---------------------------------------------------------------- 配色方案
# roles: 结构层角色 -> (钉点名, k)；markers: 通道 -> (钉点名, 亮k, 暗k)。

SCHEMES = [
    {
        "id": "r6_s1_family_gold",
        "label": "方案1 家族金暖调",
        "note": "灰阶外壳 / 倒角 / 暗壳 / 纯黑暗腔逐字保留；青带整体换成 miao_coin 琥珀，"
                "角标压到最低饱和的深棕，全图唯一亮部 = 中心暖金猫脸。",
        "roles": {"band": ("coin_bronze", 1.0), "cavity": ("cell_cavity", 1.0)},
        "face": {"hi": ("coin_gold", 1.0), "mid": ("coin_warm", 1.0),
                 "nose": ("coin_brown1", 1.0), "eye": ("cell_cavity", 1.0)},
        "markers": {"item": ("coin_brown1", 1.0, 0.45),
                    "fluid": ("coin_brown2", 1.0, 0.45),
                    "essen": ("coin_brown3", 1.0, 0.4)},
        "hier_pair_gate": False,
    },
    {
        "id": "r6_s2_cool_channel",
        "label": "方案2 通道冷调",
        "note": "主体完全走基准图的冷青读法（青带 / 灰阶 / 暗壳原值），三枚角标只用青带同色相"
                "分三档明度，家族淡金只落在中心猫脸这一个对比点上。",
        "roles": {"band": ("cell_band", 1.0), "cavity": ("cell_cavity", 1.0)},
        "face": {"hi": ("coin_bright", 1.0), "mid": ("coin_mid", 1.0),
                 "nose": ("coin_brown1", 1.0), "eye": ("cell_cavity", 1.0)},
        "markers": {"item": ("cell_band", 0.85, 0.35),
                    "fluid": ("cell_band", 0.7, 0.3),
                    "essen": ("cell_band", 0.55, 0.25)},
        "hier_pair_gate": False,
    },
    {
        "id": "r6_s3_three_channel",
        "label": "方案3 三色并存",
        "note": "主体走基准读法（青带 / 灰阶 / 暗壳原值 + 基准家族黄做猫脸），三枚 >=4px 短条"
                "各占一条通道色：物品青、流体品红（取自 infinity_fluid_cell）、源质绿。",
        "roles": {"band": ("cell_band", 1.0), "cavity": ("cell_cavity", 1.0)},
        "face": {"hi": ("cell_spark", 1.0), "mid": ("coin_mid", 1.0),
                 "nose": ("coin_brown1", 1.0), "eye": ("cell_cavity", 1.0)},
        "markers": {"item": ("cell_band", 0.9, 0.4),
                    "fluid": ("fluid_magenta", 1.0, 0.4),
                    "essen": ("cell_green", 0.95, 0.35)},
        "hier_pair_gate": True,
    },
]

# ---------------------------------------------------------------- 动画表

FRAMES = 8
FRAMETIME = 4                                   # tick/帧；整环 = 8*4 = 32 tick
LOOP_SECONDS = FRAMES * FRAMETIME / 20.0        # 1.6 s
BREATH_STEPS = [1.00, 0.96, 0.92, 0.88]
BREATH_PLAN = [0, 1, 2, 3, 3, 2, 1, 0]         # 往返档；末帧与首帧同档 -> 无缝
CHANNEL_PLAN = {"item": (0, 1), "fluid": (2, 3), "essen": (4, 5)}   # 顺次点亮，6/7 全暗
MCMETA = {"animation": {"frametime": FRAMETIME}}
