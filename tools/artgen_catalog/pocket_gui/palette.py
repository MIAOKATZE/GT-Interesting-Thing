"""C2 装饰贴图的调色板单一权威（资产色全部由契约选择子导出，本目录唯一允许字面 RGB 的文件）。

纪律：
1. **资产色零落地**：`SEED` 只登记 (契约 key, 角色, 序号) 选择子，色值由 `contract.py` 现读
   `pocket-ui-mockups-2.html` 的 C2.paint 得到。契约改一个字符，这里跟着变，不存在第二处真相。
2. **派生色只有三种手段**，且必须逐条登记在 `DERIV`（t/k 值在这里，绘图脚本不得自带）：
   - `tint(基色, t)` = 向白线性插值（同色相浅调）
   - `shade(基色, k)` = 向黑等比缩放（保色相压暗）
   - `mix(A, B, t)` = 两支**契约色**线性混合（只在"同一五金家族的两档之间"用）
   除此之外不得有任何资产色；资产色一律由 SEED 选择子回契约取。
3. **强调色独占**：C2 的铜高光（`btn` 的第一层 box-shadow）按设计文本只给可点物。
   这里提供 `ACCENT_KEYS` 白名单，由 `gen_pocket_gui.py` 机检「该 RGB 只出现在白名单贴图里」，
   不靠自觉。派生的铜亮档用 `tint(copper, .)` 而不是直接取强调色，避免白名单被稀释。
4. 本文件末尾的 `CHROME` 是**展示板 UI 灰阶**，不参与任何 PNG 资产，故允许字面值。
"""
from __future__ import annotations

from contract import CONTRACT


def hex_to_rgb(h: str) -> tuple[int, int, int]:
    assert h.startswith("#") and len(h) == 7, h
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


def tint(rgb: tuple[int, int, int], t: float) -> tuple[int, int, int]:
    """向白线性插值（同色相浅调），0<t<=1。"""
    assert 0.0 < t <= 1.0, t
    return tuple(round(c + (255 - c) * t) for c in rgb)


def shade(rgb: tuple[int, int, int], k: float) -> tuple[int, int, int]:
    """向黑等比缩放（保色相压暗），0<k<=1。"""
    assert 0.0 < k <= 1.0, k
    return tuple(round(c * k) for c in rgb)


def mix(a: tuple[int, int, int], b: tuple[int, int, int], t: float) -> tuple[int, int, int]:
    """两支契约色线性混合（0<t<1）；只在「同一家族的两档之间」使用，公式仍逐条登记。"""
    assert 0.0 < t < 1.0, t
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


# ---------------------------------------------------------------- 契约选择子
# name -> (paint key, 角色, box-shadow 层序号)

SEED = {
    # — 面板骨架（厚度同样来自契约，见 contract.Decl，不在这里重述）
    "cloth":      ("panel", "bg", 0),        # 布底主色
    "wood":       ("panel", "border", 0),    # 深木框
    "wood_ring":  ("panel", "shadow", 0),    # 木框内侧第一圈
    "copper":     ("panel", "shadow", 1),    # 内圈铜线（本家族的五金基色）
    "keyline":    ("panel", "shadow", 2),    # 最外暗线（与游戏背景分隔）
    # — 槽位（金属凹槽）
    "floor":      ("cell", "bg", 0),         # 布面心
    "wall":       ("cell", "border", 0),     # 内暗
    "lip":        ("cell", "shadow", 1),     # 外亮
    "bronze":     ("cell", "shadow", 0),     # 中段过渡
    # — 列底与角色区
    "col_floor":  ("fluidcol", "bg", 0),
    "col_wall":   ("fluidcol", "border", 0),
    "aspect_floor": ("aspect", "bg", 0),
    "text_floor": ("text", "bg", 0),
    "text_ink":   ("text", "color", 0),
    "hole_floor": ("hole", "bg", 0),
    "label_ink":  ("labelColor", "raw", 0),
    # — 币值牌
    "coin_floor": ("coin", "bg", 0),
    "coin_rim":   ("coin", "border", 0),
    "coin_lip":   ("coin", "shadow", 0),
    "coin_ink":   ("coin", "color", 0),
    # — 可点物（强调色只在这族里出现）
    "btn_floor":  ("btn", "bg", 0),
    "btn_rim":    ("btn", "border", 0),
    "accent":     ("btn", "shadow", 0),      # 铜高光：只给可点物
    "btn_ink":    ("btn", "color", 0),
    "bind_floor": ("bind", "bg", 0),
    # — 进度条 / 绳结
    "prog_floor": ("prog", "bg", 0),
    "rope":       ("hole", "border", 0),
    # — 流体五金（冷色，用来把「流体槽」和「物品槽」分形）
    "steel":      ("fluid", "border", 0),
    "steel_lip":  ("fluid", "shadow", 0),
    "fluid_floor": ("fluid", "bg", 0),
}

# ---------------------------------------------------------------- 派生档
# name -> (手段, 参数, 基色...)；手段 in {tint, shade, mix}

DERIV: dict[str, tuple] = {
    # 布纹（平铺底 + 槽位布面心共用同一组振幅，保证"底材继承"这句话是同一个数）
    "cl_warp":   ("shade", "cloth", 0.965),   # 经线暗
    "cl_weft":   ("tint", "cloth", 0.035),    # 纬线亮
    "cl_cross":  ("shade", "cloth", 0.945),   # 交织点最暗
    "cl_slub":   ("shade", "cloth", 0.925),   # 偶发纱节
    "fl_warp":   ("shade", "floor", 0.965),   # 槽心布纹三档（与上面同 k/t）
    "fl_weft":   ("tint", "floor", 0.035),
    "fl_cross":  ("shade", "floor", 0.945),
    "fl_slub":   ("shade", "floor", 0.925),
    "cf_warp":   ("shade", "col_floor", 0.965),
    "cf_weft":   ("tint", "col_floor", 0.035),
    "cf_cross":  ("shade", "col_floor", 0.945),
    "cf_slub":   ("shade", "col_floor", 0.925),
    # 铜五金坡面（铆钉 / 包角 / 铜条共用一套，绝不借用可点物强调色）
    "cup_hi":    ("tint", "copper", 0.35),
    "cup_mid":   ("tint", "copper", 0.12),
    "cup_lo":    ("shade", "copper", 0.72),
    "cup_deep":  ("shade", "copper", 0.50),
    # 木框
    "wd_hi":     ("tint", "wood", 0.10),
    "wd_lo":     ("shade", "wood", 0.80),
    "wg_lo":     ("shade", "wood_ring", 0.85),
    "wg_hi":     ("tint", "wood_ring", 0.12),
    # 冷钢五金
    "st_hi":     ("tint", "steel", 0.30),
    "st_mid":    ("tint", "steel", 0.08),
    "st_lo":     ("shade", "steel", 0.72),
    # 按下态（同一块面板的明度阶梯，参数登记在此而非脚本正文）
    "bt_press":  ("shade", "btn_floor", 0.80),
    "bt_topsh":  ("shade", "btn_floor", 0.60),
    "bt_hi":     ("tint", "btn_floor", 0.16),
    # 币牌与铜条厚度层
    "cn_lo":     ("shade", "coin_rim", 0.78),
    "cn_hi":     ("tint", "coin_rim", 0.18),
    "bd_lo":     ("shade", "bind_floor", 0.86),
    "rp_hi":     ("tint", "rope", 0.18),
    "rp_lo":     ("shade", "rope", 0.72),
}

# 强调色允许出现的贴图（契约里可点物 = 通道按钮两态 + 绑定按钮）
ACCENT_SEED = "accent"
ACCENT_ALLOWED = frozenset({"btn", "btn_pressed", "bindbtn"})


def build() -> tuple[dict[str, tuple[int, int, int]], list[str]]:
    pal = {n: hex_to_rgb(CONTRACT.hexa(*sel)) for n, sel in SEED.items()}
    for n, spec in DERIV.items():
        op = spec[0]
        if op == "tint":
            _, base, p = spec
            assert base in pal, f"派生 {n} 的基色 {base} 未登记"
            pal[n] = tint(pal[base], p)
        elif op == "shade":
            _, base, p = spec
            assert base in pal, f"派生 {n} 的基色 {base} 未登记"
            pal[n] = shade(pal[base], p)
        elif op == "mix":
            _, a, b, p = spec
            assert a in pal and b in pal, f"派生 {n} 的基色未登记"
            pal[n] = mix(pal[a], pal[b], p)
        else:
            raise AssertionError(f"未知派生手段 {op}（只允许 tint/shade/mix）")
    prov = [
        f"契约基色 {len(SEED)} 档由 C2.paint 选择子现读（零字面量）",
        f"派生 {len(DERIV)} 档全部由 tint/shade/mix 公式重算",
    ]
    return pal, prov


PAL, PROV = build()
SEED_RGB = {n: PAL[n] for n in SEED}
ACCENT_RGB = PAL[ACCENT_SEED]

# 预览板专用的"假物品"色与有货位：同样只从契约文件读，预览板不另立色值
HUES = tuple(hex_to_rgb(h) for h in CONTRACT.str_list("ASPECT_HUE"))
STOCK = tuple(CONTRACT.int_list("ASPECT_STOCK"))


def deriv_signature(name: str) -> tuple:
    """派生档的公式签名 (手段, 参数)；基色档返回 ('seed', None)。"""
    if name not in DERIV:
        return ("seed", None)
    spec = DERIV[name]
    return (spec[0], spec[-1])


# ---------------------------------------------------------------- 明度与对比度

def rel_lum(c: tuple[int, int, int]) -> float:
    """WCAG 2.x 相对亮度。"""
    def lin(v: int) -> float:
        s = v / 255
        return s / 12.92 if s <= 0.03928 else ((s + 0.055) / 1.055) ** 2.4
    r, g, b = (lin(v) for v in c)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def contrast(a: tuple[int, int, int], b: tuple[int, int, int]) -> float:
    la, lb = rel_lum(a), rel_lum(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)


def contrast_names(a: str, b: str) -> float:
    return contrast(PAL[a], PAL[b])


# ---------------------------------------------------------------- 展示板 UI（非资产色）

CHROME = {
    "bg": (26, 24, 22),
    "panel": (38, 35, 32),
    "line": (74, 68, 62),
    "txt": (232, 228, 222),
    "dim": (158, 150, 142),
    "warn": (214, 132, 96),
    "chk_a": (52, 50, 48),
    "chk_b": (70, 67, 64),
}

if __name__ == "__main__":
    for n in sorted(PAL):
        tag = "seed" if n in SEED else "deriv"
        print(f"{tag:5s} {n:12s} {PAL[n]}")
    print(chr(10).join(PROV))
    print("槽壁/槽心 对比度 =", round(contrast_names("wall", "floor"), 3))
    print("槽壁/布底 对比度 =", round(contrast_names("wall", "cloth"), 3))
