"""C2 装饰贴图的绘制几何清单 —— 只登记「怎么画」，不登记「什么色」（色在 palette.py）。

本文件与 `palette.py` 合起来是全部常量：绘图脚本里不得出现字面 RGB、不得出现 t/k 值、
不得出现第二个尺寸或第二个 N 值（尺寸与 N 一律回 `contract.py` 取）。

几条需要留痕的口径（都可被 `gen_pocket_gui.py` 机检，不是嘴上说的）：

1. **同心环 = 9-slice 安全基元**。环带沿"被拉伸的那根轴"恒定，所以：
   - 可能按非原生尺寸绘制的件（panel / coinbar / btn / bindbtn / scrollbar）一律只用
     同心环 + 整行/整列 + 角块内的图元，`STRETCH_SAFE` 登记为 True 并逐张机检；
   - 永远按原生尺寸绘制的件（slot / slot_tall 走 18 栅格，cloth / rope 平铺，
     corner / rivet 1:1 贴）允许中心带织纹，但改测"环带主导度"：环的明度差必须
     远大于中心织纹的最大落差，这样即便被误拉伸也不会生出第二条假边界。
2. **铆钉只能待在角块里**。9-slice 的四角块是唯一"任意方向都不拉伸"的区域，
   所以 coinbar 的"两铆钉"画成 4px  studs 坐在左右角块（x 0..3 / 84..87），
   而不是画一个 6px 圆点压进可拉伸带 —— 后者一拉伸就糊成竖条。
3. **强调色独占**：铜高光（palette 的 `accent`，来自 btn 的第一层内阴影）只允许出现在
   `ACCENT_ALLOWED` 三张可点物件上；包角/铆钉/铜条的亮档一律走 `cup_hi` 这类派生档。
4. **corner / rivet 是 1:1 贴**：corner 画成 90 度旋转对称，一张供四角（契约只给了一个名字），
   并机检旋转对称 + 掩码沿对角镜像；不指望 9-slice 兜住。
"""
from __future__ import annotations

from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
OUT = HERE / "out"
LAND_DIR = ROOT / "src" / "main" / "resources" / "assets" / "gtit" / "textures" / "gui" / "pocket"

# 契约里 btn 一条要出两张（未按下 / 按下）。契约只给了一个 token，第二张按同一命名法派生，
# 基准名仍是契约 token —— 全套里唯一的派生命，回执与消费片都要点名。
# 后缀取 `_pressed`：与并行消费片 PocketGuiTextureContract 的补全行逐字一致。
SHEET_STATE_SUFFIX = {"btn_pressed": "_pressed"}
SHEET_CONTRACT_KEY = {"btn_pressed": "btn"}

# 每张图对应一个 draw_* 函数名（gen 里查表），顺序 = 预览板顺序
SHEETS = [
    "cloth", "panel", "corner", "rivet", "slot", "slot_tall",
    "coinbar", "btn", "btn_pressed", "scrollbar", "bindbtn", "rope",
]

# 会被按非原生尺寸绘制的件（严格 band-legality：中心平色 + 边带沿拉伸轴恒定）
STRETCH_SAFE = {"panel", "coinbar", "btn", "btn_pressed", "scrollbar", "bindbtn"}
# 声明了 9-slice 但中心必须留织纹的件：改测"环带主导度"（环差 >= K 倍中心内部最大差），
# 这样即使被误拉伸也不可能生出第二条假边界；槽位本身按 18 栅格原生尺寸用。
TEXTURED_CENTER = {"slot", "slot_tall"}
# 平铺件 / 1:1 件（改测接缝连续性与旋转对称）
NATIVE_ONLY = {"cloth", "rope", "corner", "rivet"}

# ---------------------------------------------------------------- 布纹（平铺底 / 槽心 / 列底共用）

WEAVE = {
    "period": 2,                 # 经纬周期（必须整除平铺件边长，否则接缝）
    "slub_rate": 22,             # 纱节：hash % 100 < 22
    "base": {"cloth": ("cloth", "cl_warp", "cl_weft", "cl_cross", "cl_slub"),
             "floor": ("floor", "fl_warp", "fl_weft", "fl_cross", "fl_slub"),
             "col_floor": ("col_floor", "cf_warp", "cf_weft", "cf_cross", "cf_slub")},
}
CLOTH_SEED = 0x5CA07
SLOT_SEED = 0x15A1E
COL_SEED = 0xC01F

# ---------------------------------------------------------------- 面板（9-slice N=10）

PANEL_KEY = "panel"              # 环带厚度取自 contract.frame_bands('panel')，这里只给色序
PANEL_BAND_COLORS = ["keyline", "wood", "wood_ring", "copper"]   # 由外向内，与环带一一对应
PANEL_GRAIN = ["wood", "wd_lo", "wd_hi"]                          # 木框按环带序号轮换的纹理档
PANEL_WOOD_KEYS = (1, 2)         # 环带序号：0=keyline 1=wood 2=wood_ring 3=copper
PANEL_MITER = "wd_lo"            # 角块内的 45 度拼缝
PANEL_COPPER_INNER = "cup_hi"    # 铜线最内一档的受光

# ---------------------------------------------------------------- 铜包角（14×14 1:1）

CORNER_CHAMFER = 3
CORNER_RAMP = [                  # (深度上限, 色名)：深度由 depth_map 给出，逐档向内
    (0, "keyline"),
    (1, "cup_hi"),
    (3, "copper"),
    (5, "cup_lo"),
]
CORNER_CORE = "keyline"          # 比最后一档还深的兜底档 = 螺孔底（铆钉坐进去）

# ---------------------------------------------------------------- 铆钉 / stud（6×6 与角块 4×4）

RIVET_DISC = 6
# 6px 的盘上"近黑描边"会吃掉三分之二像素（描边环在这么小的掩码上占绝大多数），
# 所以铆钉的边用深铜而不是 keyline；孔的暗交给 corner 的 CORNER_CORE 去表达。
STUD_EDGE = "cup_deep"
STUD_RAMP = [                    # (深度下限, 色名)，浅->深
    (2, "cup_hi"),
    (1, "cup_mid"),
    (0, "cup_lo"),
]
STUD_SHADOW = "copper"
COINBAR_STUD = 4                 # 只允许坐在 N=4 的角块里
SCROLLBAR_STUD = 1

# ---------------------------------------------------------------- 金属凹槽槽位（18×18，N=3）

SLOT_RINGS = ["lip", "copper", "wall"]      # 由外向内：外亮 -> 铜坡 -> 内暗（整圈，非单侧）
SLOT_CENTER = "floor"                       # 布面心（织纹见 WEAVE）
SLOT_TALL_RINGS = ["steel_lip", "steel", "st_lo"]     # 冷色版，内暗档比物品槽更深（金属圈）
SLOT_TALL_CENTER = "col_floor"
SLOT_TALL_ROUND = "steel"                   # 角上压暗，让圈读成圆环而不是方框
SLOT_TALL_ROUND_CUT = 2

# ---------------------------------------------------------------- 币值条 / 按钮 / 绑定 / 铜条

COINBAR_RINGS = ["coin_rim", "coin_lip", "cn_hi", "copper"]   # 由外向内 4 档（N=4）
COINBAR_CENTER = "coin_floor"
COINBAR_BOTTOM = "cn_lo"                     # 整列底边压暗（沿 y 恒定 => 拉伸安全）

BTN_RINGS = ["btn_rim", "btn_floor", "bt_topsh", "btn_floor"]
BTN_TOP_LINE = "accent"                      # 顶内唇：可点物强调色
BTN_CENTER = "btn_floor"
BTN_PRESSED_RINGS = ["btn_rim", "bt_press", "bt_topsh", "bt_press"]
BTN_PRESSED_TOP = "bt_topsh"
BTN_PRESSED_BOTTOM = "accent"                   # 按下：亮唇翻到底边
BTN_PRESSED_CENTER = "bt_press"

SCROLLBAR_RINGS = ["keyline", "copper", "cup_mid"]            # N=3 => 三圈
SCROLLBAR_CENTER = "cup_hi"

BINDBTN_RINGS = ["btn_rim", "copper", "cup_hi", "copper"]
BINDBTN_TOP_LINE = "accent"
BINDBTN_CENTER = "bind_floor"
BINDBTN_BOTTOM = "bd_lo"

# ---------------------------------------------------------------- 绳结（8×8 平铺）

ROPE_AXIS = 3                  # 中心线在 3/4 两行（两行等距 => 90 度旋转对称）
ROPE_DASH = (0, 7)             # 每 8 格空 2 格 => 虚线；取 (0,7) 是因为它关于 7-d 封闭，
                               # 平铺时 7 与 0 连成一个 2px 缺口，且旋转后仍对得上
ROPE_FRAY = (1, 6)             # 紧靠缺口两端的那格压暗（绳头毛边）；同样关于 7-d 封闭
ROPE_KNOT = "rp_hi"
ROPE_BODY = "rope"
ROPE_SIDE = "rp_lo"

# ---------------------------------------------------------------- 预览板

BOARD = ROOT / "plan" / "assest" / "猫猫次元口袋-C2装饰贴图生产板.png"
ZOOM = 4
CELL_PAD = 10
ROW_LABEL_W = 262
LABEL_PX = 14
SAMPLE_ZOOM = 3
# 面板局部样例：一个 3×3 槽位盘 + 四角包角 + 铆钉 + 币值条 + 通道按钮 + 绳结分隔 + 铜条
SAMPLE_SLOTS = 3
