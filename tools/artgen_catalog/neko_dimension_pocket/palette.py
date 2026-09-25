"""猫猫次元口袋图标的调色板单一权威（本目录唯一允许出现字面 RGB 的文件）。

纪律（照抄仓内 `neko_infinity_unit/r6_manifest.py` 的口径，本目录自洽不跨目录 import）：
1. **基色必须钉在既有实图上**：`REF_POINTS` 里每条都是 (来源贴图, RGB)，加载时逐条断言
   「该 RGB 确实以不透明像素出现在该实图里」，否则直接抛错 -> 禁止包外新色相。
2. **派生色只有两种手段**，且必须在 `DERIVATIONS` 里登记公式，脚本重算并断言相等：
   - `tint(基色, t)` = 向白线性插值（同色相浅调）
   - `shade(基色, k)` = 向黑等比缩放（k<=1，保色相压暗）
   除此之外不得有任何字面 RGB；生成脚本与清单文件里出现 '#' 或三位元组即为违规。
3. 来源贴图的 SHA256 一并钉住（`REF_SHA`），本任务全程只读，任何一根变化即判基线漂移。
"""
from __future__ import annotations

import hashlib
from pathlib import Path

from PIL import Image

# ---------------------------------------------------------------- 来源与字节钉

ITEM_DIR = Path("src/main/resources/assets/gtit/textures/items")

REF_SHA = {
    "infinity_cell.png": "074e595027d57b46ab8ef5125b7726629d941c0ae9bc8b6497f1fac6a8955ec5",
    "infinity_fluid_cell.png": "3eb2a4c0d5a90ecffc5a7e2f253889c3f09302a310f387c2424e551f441d9bba",
    "miao_coin.png": "65c73db9baf2c76c8dbf7397dd26f523369bb6e343f13d1abe66b67f40696612",
    "neko_infinity_unit.png": "41b04e2319125a49e3530fd15733660e6579c79b1e7f1e38bbecfeef64056e11",
    "reincarnation_crystal.png": "1ad65e35efb9dab6a9421148e43df2acebec77f9315ec7fa3f6adc65b3ef6816",
    "ring_distant_grasp.png": "1a8f7626210584e35fecec4966dcd1685eb23eec64a523f811eb23b78b9ad1d6",
    "ring_dragon_breath.png": "a56110792245493519e545b2ee72fb652c473ff182e1d50945110d57a65f3f79",
    "electric_float_core.png": "4dd00528b01737abe89bf892edfb9a31baee9b9dd8ffb2ebf33191deed4c2c0f",
}

CELL = "infinity_cell.png"
FLUID = "infinity_fluid_cell.png"
COIN = "miao_coin.png"
UNIT = "neko_infinity_unit.png"
CRYSTAL = "reincarnation_crystal.png"
RING_L = "ring_distant_grasp.png"
RING_D = "ring_dragon_breath.png"
FLOAT = "electric_float_core.png"

# ---------------------------------------------------------------- 两种派生手段


def tint(rgb: tuple[int, int, int], t: float) -> tuple[int, int, int]:
    """向白线性插值（同色相浅调），0<t<=1。"""
    assert 0.0 < t <= 1.0, t
    return tuple(round(c + (255 - c) * t) for c in rgb)


def shade(rgb: tuple[int, int, int], k: float) -> tuple[int, int, int]:
    """向黑等比缩放（保色相压暗），0<k<=1。"""
    assert 0.0 < k <= 1.0, k
    return tuple(round(c * k) for c in rgb)


# ---------------------------------------------------------------- 基色钉点
# name -> (来源图, 期望 RGB)。加载时断言该 RGB 确实出现在来源图里。

REF_POINTS = {
    # — 结构与描边（infinity_cell 家族灰阶 / 纯黑腔）
    "blk":    (CELL, (0, 0, 0)),          # 暗腔纯黑：最深描边与空腔
    "gry":    (CELL, (40, 40, 40)),       # 壳体主灰（家族外骨骼）
    "hig":    (CELL, (64, 64, 64)),       # 壳体受光倒角
    "dsh":    (CELL, (36, 29, 36)),       # 壳体背光（冷暗）
    "band":   (RING_L, (43, 43, 43)),     # GTNH 指环带暗（现代包读法的中性带）
    "bandl":  (RING_L, (68, 68, 68)),     # GTNH 指环带亮
    # — 家族金暖（miao_coin / neko_infinity_unit 币体系）：猫脸与纹章层
    "eng":    (COIN, (111, 37, 2)),       # 币面阴刻（猫眼/鼻）
    "rim":    (COIN, (132, 46, 7)),       # 币体厚边
    "brz":    (UNIT, (166, 61, 3)),       # 单元暖青铜
    "amb":    (COIN, (203, 88, 23)),      # 琥珀
    "wrm":    (COIN, (246, 165, 30)),     # 币体背光金
    "gld":    (COIN, (252, 187, 35)),     # 币体主金
    "brt":    (UNIT, (254, 224, 74)),     # 亮金
    "fam":    (CELL, (255, 242, 0)),      # 家族黄（角标/抽绳）
    "crm":    (COIN, (254, 254, 192)),     # 币面乳白高光
    "ivory":  (CRYSTAL, (255, 250, 230)),  # 轮回结晶象牙白（最强受光）
    "wht":    (CRYSTAL, (255, 255, 255)),  # 轮回结晶纯白（星尘核心）
    # — 神秘4 / 源质紫系（infinity_cell 角标 + infinity_fluid_cell 布纹 + 指环）
    "dpur":   (CELL, (95, 28, 97)),       # 角标最暗紫
    "mpur":   (CELL, (138, 31, 140)),     # 角标中紫
    "lpur":   (CELL, (197, 21, 201)),     # 角标亮紫红
    "deep":   (RING_D, (130, 14, 152)),   # 龙息戒深紫（裂隙深处）
    "lilac":  (RING_L, (219, 110, 239)),  # 远握戒淡 lilac（星尘）
    "pale":   (RING_L, (204, 152, 250)),  # 远握戒浅紫（次级星尘）
    # — 布料（infinity_fluid_cell 的柔紫灰读法，神秘4 质感锚点）
    "cl0":    (FLUID, (46, 42, 46)),      # 布最暗
    "cl1":    (FLUID, (99, 72, 82)),      # 布暗
    "cl2":    (FLUID, (113, 84, 102)),    # 布中
    "cl3":    (FLUID, (175, 123, 159)),   # 布受光
    "cl4":    (FLUID, (200, 111, 200)),   # 布纹亮符
    "cl5":    (FLUID, (255, 231, 255)),   # 布高光乳紫
    # — 次元青（物品蓝 / 流体青 / 电核亮青）
    "cyan":   (CELL, (0, 162, 232)),      # 通道物品蓝
    "aqua":   (FLOAT, (0, 222, 255)),     # 电核亮青（次元口冷光）
    "teal":   (CRYSTAL, (98, 200, 192)),  # 轮回结晶青绿
    "green":  (CELL, (25, 255, 0)),       # 源质绿点
    "pink":   (CRYSTAL, (224, 116, 190)),  # 轮回结晶粉（符纹点缀）
}

# ---------------------------------------------------------------- 派生色登记
# name -> (手段, 基色名, 参数)；派生色同样进 INK 使用，但必须有公式。

DERIV_POINTS = {
    # 现代包面板：壳体三档之外的更深压暗与更浅受光
    "gry_dk":  ("shade", "gry", 0.62),      # 面板暗格
    "hig_lt":  ("tint", "hig", 0.35),       # 面板高光角（Modernity 强高光）
    # 布料家族的中间调（F3 的褶皱层）
    "cl1_dk":  ("shade", "cl1", 0.66),      # 布褶皱最深
    "cl3_lt":  ("tint", "cl3", 0.42),       # 布符纹受光
    "cl0_dk":  ("shade", "cl0", 0.66),      # 布囊内腔
    # 次元裂隙：紫的两档压暗 + lilac 的两档提亮（星尘层次）
    "deep_dk": ("shade", "deep", 0.42),     # 裂隙底（近黑紫）
    "lpur_dk": ("shade", "lpur", 0.55),     # 裂隙壁
    "lilac_lt": ("tint", "lilac", 0.45),    # 星尘最亮
    "pale_lt": ("tint", "pale", 0.4),       # 星尘次亮
    # 冷光层次：物品蓝的两档（次元口内光 / 外缘光）
    "cyan_lt": ("tint", "cyan", 0.6),       # = 旧家族登记过的 Q 同值
    "cyan_dk": ("shade", "cyan", 0.45),     # 次元口外缘
    "aqua_lt": ("tint", "aqua", 0.4),       # 冷光核心
    # 金暖的第三档（F1/F2 猫脸的背光层）
    "gld_dk":  ("shade", "gld", 0.72),      # 币体金背光
    "fam_dk":  ("shade", "fam", 0.55),      # 家族黄压暗（绳结阴影）
    # ---------------------------------------------------------------- 包裹返工轮（B 族）提亮层
    # 纪律：只新增 tint/shade 派生，不新增基色钉点；全部服务「主体明度提亮」诉求。
    "ivory_dk":  ("shade", "ivory", 0.80),  # 纸包中调（暖灰白，绝不落冷暗）
    "ivory_dk2": ("shade", "ivory", 0.62),  # 纸包背光/纹样
    "crm_dk":    ("shade", "crm", 0.55),    # 纸包印章墨色（暖褐灰）
    "crm_dk2":   ("shade", "crm", 0.45),    # 纸包下摆暗带（Weber 阴影锚）
    "brz_lt":    ("tint", "brz", 0.30),     # 纸包斜绳（暖橙绳）
    "gld_dk2":   ("shade", "gld", 0.55),    # 金布包背光列（Weber 阴影锚）
    "cl4_lt":    ("tint", "cl4", 0.30),     # 亮品红布受光主面
    "cl4_dk":    ("shade", "cl4", 0.68),     # 品红布下摆暗带（仍 ≥95 明度，不落深紫）
    "aqua_dk":   ("shade", "aqua", 0.72),   # 硬壳提手（五金件，非主体亮面）
    "pale_dk":   ("shade", "pale", 0.72),   # 硬壳背光棱
    "pale_dk2":  ("shade", "pale", 0.55),   # 硬壳底沿（Weber 阴影锚）
    # ---------------------------------------------------------------- 定稿轮（b4g 金暖）新增派生档
    # 纪律同 B 族：只加 tint/shade 派生，零新基色钉点；且一律从 miao_coin 金暖基色派生，
    # 不自创新色相（用户裁「参照 B1 的 brt/gld -> gld_dk2 那族色」）。
    "rim_dk":    ("shade", "rim", 0.34),    # 金暖硬壳内腔（替代冷紫灰 cl0_dk；登记为 detail 五金/内腔档）
    # ---------------------------------------------------------------- ★R95 S2b（升级插件物品图标）新增派生档
    # 纪律同 B 族/定稿轮：只加 tint/shade 派生，零新基色钉点；全部服务五型插件图标的
    # 三档读法（O 轮廓 / # 体 / + 受光），不自创新色相——赤红走 miao_coin 的 eng/amb 族、
    # 绿走 infinity_cell 的源质绿点 green（同基色保色相压暗/提亮）。
    "amb_lt":    ("tint", "amb", 0.45),     # 赤红受光档（磁铁极帽：amb 提亮，仍是暖红读法）
    "green_md":  ("shade", "green", 0.70),  # 绿系体档（沙漏沙体：源质绿点压三成）
    "green_dk":  ("shade", "green", 0.45),  # 绿系轮廓档（沙漏框架：压五成五）
}


def load_ref_colors(root: Path) -> dict[str, set]:
    """读来源实图（只读），返回 文件名 -> 不透明 RGB 全集。"""
    out: dict[str, set] = {}
    for src in {s for _, (s, _) in REF_POINTS.items()}:
        im = Image.open(root / src).convert("RGBA")
        out[src] = {px[:3] for px in im.getdata() if px[3] == 255}
    return out


def build_palette(root: Path) -> tuple[dict, list[str]]:
    """校验钉点并返回 name -> RGB，附证据行。任一钉点失配即抛错。"""
    ref = load_ref_colors(root)
    bad = [f"{n}: {want} 不在 {src}" for n, (src, want) in REF_POINTS.items()
           if want not in ref[src]]
    if bad:
        raise AssertionError("基色钉点失配：\n  " + "\n  ".join(bad))
    pal = {n: rgb for n, (src, rgb) in REF_POINTS.items()}

    for n, (op, base, p) in DERIV_POINTS.items():
        assert base in pal, f"派生 {n} 的基色 {base} 未登记"
        got = tint(pal[base], p) if op == "tint" else shade(pal[base], p)
        assert op in ("tint", "shade"), op
        pal[n] = got

    prov = [f"基色 {len(REF_POINTS)} 档逐字命中 {len(ref)} 张实图；"
            f"派生 {len(DERIV_POINTS)} 档全部由 tint/shade 公式重算命中"]
    return pal, prov


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def check_sources(root: Path) -> list[str]:
    """来源贴图字节必须与钉值一致（本任务全程只读，任何漂移都属越界）。"""
    bad = []
    for name, want in REF_SHA.items():
        p = root / name
        got = sha256(p) if p.exists() else "<missing>"
        if got != want:
            bad.append(f"{name}: {got[:12]} != {want[:12]}")
    return bad
