"""R5 重绘三套候选的唯一美术权威（16x16 字符位图清单）。

色符一律复用 `manifest.BASE`（其值逐字来自 infinity_cell / infinity_fluid_cell /
miao_coin 三张实图，由 gen_candidates.py 运行时反查证明），本文件不新立任何色值。
'.' = 全透明。三套方向由 plan/plan-promote.md §R5 锁定，本文件只负责把方向落成像素。
"""
from __future__ import annotations

import manifest as M

SIZE = 16

# ---------------------------------------------------------------- 候选 A 减法版
# 一枚币（圆形币体 + 猫脸三痕）+ 单个通道角标（1px 高色条），三色分弧全部删除。
# 角标用家族黄 y#fff200（两张旧单元中心同值）——它是「通道标记位」本身，不绑定任一通道。
A_MINIMAL = [
    "................",
    ".....rrrrrr.....",
    "....rwwggggr....",
    "...rwggggggdr...",
    "..rwwggggggddr..",
    "..rwgeeggeegdr..",
    "..rggeeggeegdr..",
    "..rgggggggggdr..",
    "..rggggeeggddr..",
    "...rgggggggdr...",
    "....rddddddr....",
    ".....rrrrrr.....",
    "....yyyyyyyy....",
    "....oooooooo....",
    ".....oooooo.....",
    "................",
]

# ---------------------------------------------------------------- 候选 B 两级明度三格
# 币面按 120° 三等分（Y 形黑色分格筋），三格只用两级明度：左格 w（受光）、
# 右上格 g（主金）、下格 g（背光）；不引入蓝/青/紫任何第三色相。
B_TWOTONE = [
    "................",
    "......rrrr......",
    "....rrwoogrr....",
    "...rwwwoogggr...",
    "..rwwwwooggggr..",
    "..rwwwwooggggd..",
    ".rwwwwwooggggdd.",
    ".rwwwwwooggggdd.",
    ".rwwwooooooggdd.",
    ".rwooooggoooodd.",
    "..roooggggoodd..",
    "..rogggggggddd..",
    "...rggggggddd...",
    "....rddddddd....",
    "......dddd......",
    "................",
]

# ---------------------------------------------------------------- 候选 C 元件感优先
# 存储单元外壳（带顶端子 + 斜切角的方壳，三档灰）为主视觉，中央开窗，
# 猫币退化为开窗内的小徽记（币体占比 < 外壳占比，竖猫眼）。
C_CASING = [
    "................",
    "......HHHH......",
    "....HHHHHHHH....",
    "..HSSSSSSSSSSC..",
    ".HSSooooooooSSC.",
    ".HSSoorwwdooSSC.",
    ".HSSorewgedoSSC.",
    ".HSSoreggedoSSC.",
    ".HSSorggggdoSSC.",
    ".HSSorggggdoSSC.",
    ".HSSoorggdooSSC.",
    "..HSooooooooSC..",
    "...HSSSSSSSSC...",
    "...CCCCCCCCCC...",
    "....CCCCCCCC....",
    "................",
]

# ---------------------------------------------------------------- 角色分组（判据用）
CANDIDATES = {
    "cand_a_minimal": {
        "art": A_MINIMAL,
        "title": "A 减法版：一枚币 + 单个通道角标",
        "spec": {"coin": "grwd", "face": "e", "bar": "y", "barbed": "o"},
    },
    "cand_b_twotone": {
        "art": B_TWOTONE,
        "title": "B 两级明度三格：Y 形三胞币面",
        "spec": {"rim": "rd", "ribs": "o", "sectors": "wg"},
    },
    "cand_c_casing": {
        "art": C_CASING,
        "title": "C 元件感优先：外壳为主 + 猫币小徽记",
        "spec": {"casing": "SHC", "window": "o", "badge": "grwde"},
    },
}

# 通道色相（物品蓝 / 流体青 / 源质紫）——B 的「不引入第三种色相」据此反查
CHANNEL_HUES = ("B", "P", "Q")

PALETTE = M.PALETTE
