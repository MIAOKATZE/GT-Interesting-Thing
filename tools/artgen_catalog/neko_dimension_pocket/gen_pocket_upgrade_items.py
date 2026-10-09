"""口袋升级插件物品动画图标（5 张 16×128 彩色帧带，每张8帧）的幂等生成器（R95 S2b 立 / R96 S12a 换形 + 放开用色档数判据）。

为什么是单独脚本（仿 `tools/artgen_catalog/pocket_gui/gen_pocket_gui_upgrades.py` 的先例）：
  本目录（neko_dimension_pocket = 物品图标族）的 `gen_pocket.py` 钉死在口袋四态帧带的
  `pocket_manifest` 清单上，加不进新物品行——动它就是动主管线。本脚本只**复用**同目录
  既有权威，跨文件零复制：

  颜色  = 同目录 `palette.py`（物品图标族唯一允许字面 RGB 的文件）：基色逐条钉在既有实图
          （`REF_POINTS` + `REF_SHA`）。本批每张实际用色 6~8 档（旧图恰 3 档 = 单调根因），
          全部由**已登记**档名取得（五金 blk/gry/hig/hig_lt + 家族黄 fam + 每型一条四档色相链），
          R96 零新增色档：本文件零新派生、零字面 RGB；
  几何  = frame0 语义形**逐字复用** `pocket_gui/gen_pocket_gui_upgrades.py` 的五张 16×16 字符画
          （储罐/金箱成摞/马蹄磁铁/闭环管道/尖顶巫师帽——GUI 灰化占位与物品图标同形，玩家凭形状
          即可对上槽位；两处字符画由本脚本的自检"与 pocket_gui 版逐字符相等"钉住，不许单边漂移）；
  动画  = 8 帧局部流光；frame0 保留静态底图，所有帧透明区及轮廓不变。GUI 灰化槽位保持静态。
  编码  = 同目录 `pngwrite.encode_png`（与口袋四态帧带同一实现，无时间戳）。

语义（五型插件；每型一条「暗 `-` → 体 `#` → 亮 `+` → 高光 `*`」四档色相链，五金档五张共用）：
  capacity        容量   = 流体蓝系（cyan_dk/cyan/aqua/aqua_lt）带液位线与刻度的储罐
  stack           堆叠   = 金暖系  （gld_dk/gld/brt/crm）三件金箱成摞
  magnet          磁力   = 赤红系  （eng/brz/amb/ivory）马蹄磁铁 + 被吸住的铁块
  channel_persist 通道   = 源质绿  （green_dk/green_md/green/ivory）闭环管道 + 四向流向箭
  mage            魔法使 = 紫系    （lpur_dk/lpur/lilac/lilac_lt）+ fam 金饰带：尖顶巫师帽
  ★R96 S8：token[4] 由 `distill_fast` 改名 `mage`（`ItemPocketUpgrade.TOKENS[4]` 单源派生基名），
  而 GUI 侧 token `POCKET_C2_upg_distill` 保持既有静态贴图及名称（改名属独立契约轮），
  故 `_GUI_TOKEN_MAP` 里存在这一处 `distill -> mage` 的有意不对称映射。

用法:
  python -B gen_pocket_upgrade_items.py                     # 自检 + 写 out/ + 打印双跑 SHA
  python -B gen_pocket_upgrade_items.py --land --i-have-authorization   # 落地 5 张帧带与 5 份 mcmeta

判据（R96 S12a 只放开「档数」这一条，其余硬门一律保持）：
  字符画与 pocket_gui 版逐字符相等（★「两族同改」的机检凭据，两处粘同一张 charart）/
  单帧 16×16 / 帧带 16×128 / 8 帧与 mcmeta 自洽 / 行内字符集合法 / 实际用色档数 >= 6 且角色档与 RGB 一一对应（防塌色）/
  同族色链明度严格递增 blk < 暗 < 体 < 亮 < 高光 + 五金档 blk < gry < hig < hig_lt 且五张共用 /
  4 连通单分量 / 外圈全透明 / 包围盒下限 / 双跑逐字节一致 / 来源 8 张实图 SHA 钉住。
  ★旧口径「三档俱全 + O<#<+」已放开：新图用 9 个角色档，三档判据在数学上不可能过（会误判红）。
"""
from __future__ import annotations

import hashlib
import json
import pathlib
import struct
import sys

HERE = pathlib.Path(__file__).resolve().parent
try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except AttributeError:  # pragma: no cover
    pass
ROOT = HERE.parents[2]
ITEMS = ROOT / "src" / "main" / "resources" / "assets" / "gtit" / "textures" / "items"
OUT_DIR = HERE / "out"
GUI_UPG = ROOT / "tools" / "artgen_catalog" / "pocket_gui" / "gen_pocket_gui_upgrades.py"

sys.path.insert(0, str(HERE))          # palette / pngwrite（全在本目录）

import palette as P                    # noqa: E402  物品图标族的调色板单一权威
import pngwrite                        # noqa: E402

PAL, _PROV = P.build_palette(ITEMS)

FRAMES = 8
FRAMETIME = 4                       # 20 ticks/s：每帧 0.2 秒，一轮 1.6 秒
SIZE = 16                              # MC 物品图标原生尺寸（1.7.10 items 贴图口径）
BLANK_CH = "."

# ---------------------------------------------------------------- 角色表（字符 -> palette 档名，本文件零字面 RGB）
# 五金档 = 五张共用（"一眼同族"的骨架，自检钉住不许单张漂移）；色相档 = 每型一条四档链。
# 明度序（自检断言严格递增）：
#   五金 blk(O) < gry(X) < hig(x) < hig_lt(L)          —— 冷钢外骨骼，家族其余贴图同源
#   色相 blk < 暗(-) < 体(#) < 亮(+) < 高光(*)          —— 每型一条，替代旧口径的 O<#<+
#   fam(Y) = 家族黄（口袋抽绳/角标同档）：只做饰带与星的**点缀档**，不进色链。
METAL: dict[str, str] = {
    "O": "blk",        # 1px 纯黑全包络描边（家族外骨骼：infinity_cell / 口袋主物品 / 单元一致）
    "X": "gry",        # 壳体主灰（暗钢面）
    "x": "hig",        # 壳体受光倒角
    "L": "hig_lt",     # 亮钢高光角（面板强高光档：罐肩/刻度/箍带亮棱）
    "Y": "fam",        # 家族黄（本仓自己的东西：帽饰带 + 四芒星）
}
METAL_CHAIN = ("O", "X", "x", "L")  # 五金档的明度序（严格递增，且五张共用同一 RGB）
HUE: dict[str, dict[str, str]] = {
    #        "-" 暗            "#" 体           "+" 亮           "*" 高光
    "capacity":        {"-": "cyan_dk",  "#": "cyan",     "+": "aqua",  "*": "aqua_lt"},   # 流体蓝系
    "stack":           {"-": "gld_dk",   "#": "gld",      "+": "brt",   "*": "crm"},        # 金暖系
    "magnet":          {"-": "eng",      "#": "brz",      "+": "amb",   "*": "ivory"},      # 赤红系
    "channel_persist": {"-": "green_dk", "#": "green_md", "+": "green", "*": "ivory"},      # 源质绿
    "mage":            {"-": "lpur_dk",  "#": "lpur",     "+": "lilac", "*": "lilac_lt"},   # 神秘紫
}
HUE_CHAIN = ("-", "#", "+", "*")     # 同族色链的明度序（blk 之后按此序严格递增）
MIN_TONES = 6                        # 实际用色档数下限（旧图恰 3 档 = 单调根因；本批 6~8 档）

# ---------------------------------------------------------------- 像素（16×16 字符画：几何登记处 = 语义形单一来源的镜像）
# 与 pocket_gui/gen_pocket_gui_upgrades.py 的 UPGRADES 五张逐字符同源（自检钉住）；
# 本表按 type token 命名（与注册名 neko_pocket_upgrade_<token> 一致）。
# 五张原生像素字符画同步重绘；保持 Minecraft 16×16、左上受光与右下暗面。
# 两处字符画保持逐字符一致，由下面的镜像自检约束。
ART: dict[str, tuple[str, ...]] = {
    # 容量升级 = 带液位计的储罐：瓶颈 + 圆肩罐体 + 左侧液面受光 + 中列液位刻度 + 暗面底托
    "capacity": (
        "................",
        "......OOOO......",
        ".....OLLLLO.....",
        ".....OxXXO......",
        "...OOOLxxOOO....",
        "..OLLLLxxxXXO...",
        ".Ox*++++##--XO..",
        ".OL+#######-XO..",
        ".Ox+#######-XO..",
        ".OL+###L###-XO..",
        ".Ox+#######-XO..",
        ".OL+###L###-XO..",
        "..Ox+#####-XO...",
        "..OxxLLLLXXOO...",
        "...OOOOOOOOO....",
        "................",
    ),
    # 堆叠升级 = 三件金箱成摞（下宽上窄阶台）：每件亮顶面 + 左受光 + 右暗面 + 金属锁扣
    "stack": (
        "................",
        ".....OOOOOOO....",
        "....O***++#-O...",
        "...O***++#--O...",
        "...O++L###--O...",
        "...O##X##---O...",
        "..OOOOOOOOOOO...",
        ".O***++#-O###O..",
        ".O++L###-O##-O..",
        ".O##X##--O---O..",
        ".OOOOOOOOOOOOO..",
        "..O***+++##--O..",
        "..O++L#####--O..",
        "..O##X###----O..",
        "..OOOOOOOOOOOO..",
        "................",
    ),
    # 磁力升级 = 马蹄磁铁（开口向上、两行象牙白极帽）+ 一极内侧被吸住的铁块 + 弧形底部金属箍带
    "magnet": (
        "................",
        "..OOOO....OOOO..",
        "..OL*O....OL*O..",
        "..Ox*O....Ox*O..",
        "..O+#O....O#-O..",
        "..O+#O....O#-O..",
        "..O+#OLXO.O#-O..",
        "..O+#OXXO.O#-O..",
        "..O+#OOOO.O#-O..",
        "..O+##O..O##-O..",
        "..O+##OOOO#--O..",
        "...O+#####--O...",
        "...Ox+###--XO...",
        "....OxxXXXOO....",
        ".....OOOOOO.....",
        "................",
    ),
    # 通道持续化升级 = 闭环管道（黑描边 / 冷钢管壁 / 源质绿流体）+ 四角削圆 + 四支旋转对称流向箭
    "channel_persist": (
        "................",
        ".....OOOOOO.....",
        "...OOxL++#XOO...",
        "..OxL++**##XXO..",
        "..OL+##**O##XO..",
        ".Ox+#OOO.O+#-XO.",
        ".OL+#O....O#-XO.",
        ".Ox**O....O**XO.",
        ".OX**O....O**xO.",
        ".OX-#O....O#+LO.",
        ".OX-#+O.OOO#+xO.",
        "..OX##O**##+LO..",
        "..OXX##**++LxO..",
        "...OOX#++LxOO...",
        ".....OOOOOO.....",
        "................",
    ),
    # 魔法使升级 = 尖顶巫师帽：左弯帽尖 + 金饰带 + 亮钢铜扣 + 四芒星 + 厚帽檐
    "mage": (
        "................",
        ".....OOOOOO.....",
        ".....O*++#-O....",
        "......OO+#-O....",
        "......O*+#--O...",
        ".....O*+##--O...",
        ".....O+##Y#-O...",
        "....O*+#YYY--O..",
        "....O+###Y#--O..",
        "...O*+######-O..",
        "...OYYYLXYYY-O..",
        "..OO*++######OO.",
        ".O*+++######--O.",
        ".O+####-------O.",
        "..OOOOOOOOOOOO..",
        "................",
    ),
}

# pocket_gui 版的 token（对齐自检用）：GUI 灰化图 token <本表 token> 的映射（其余恒等）。
# ★有意不对称：GUI 侧 `distill` 本轮不改名（锚在 plan/assest/pocket-ui-mockups-2.html 与
#   pocket_gui/contract.py 的行号断言 + PocketGuiTextureContract.java 的 18 行契约表上，
#   改名属独立契约轮），物品侧 token 已随 `ItemPocketUpgrade.TOKENS[4]` 变成 `mage`。
_GUI_TOKEN_MAP = {"channel": "channel_persist", "distill": "mage"}


def _luma(rgb: tuple[int, int, int]) -> float:
    # 明度阶判据用的简化 luma（与 palette.rel_lum 同序即可；不引入第二份 WCAG 实现）
    return 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2]


def color_of(token: str, ch: str) -> tuple[int, int, int]:
    """字符 -> RGB：五金档全局共用一张表，色相档按本型四档链取；色名一律经 palette 命中。"""
    name = METAL.get(ch) or HUE[token][ch]
    assert name in PAL, "%s 的字符 %s 指向未登记档名 %s" % (token, ch, name)
    return PAL[name]


# 动效只触碰各类型登记的内部像素；frame0 保留与静态 GUI 同源的底图。
# 每帧仅 1~3 个纹素变化，不移动轮廓，不向透明区域增加粒子。
ANIMATION_MARKS: dict[str, tuple[tuple[tuple[int, int, str], ...], ...]] = {
    # 液面亮点从左到右移动，尾端以 aqua 收光。
    "capacity": (
        ((4, 6, "*"),), ((5, 6, "*"), (4, 7, "+")),
        ((6, 6, "*"), (5, 7, "+")), ((7, 6, "*"), (6, 7, "+")),
        ((8, 6, "*"), (7, 7, "+")), ((9, 6, "*"), (8, 7, "+")),
        ((9, 6, "+"),),
    ),
    # 箱扣与附近亮边依次泛光；金箱主体始终稳定。
    "stack": (
        ((6, 5, "x"),), ((6, 5, "L"), (7, 4, "*")),
        ((4, 9, "x"), (7, 4, "+")), ((4, 9, "L"), (5, 8, "*")),
        ((5, 13, "x"), (5, 8, "+")), ((5, 13, "L"), (6, 12, "*")),
        ((5, 13, "x"), (6, 12, "+")),
    ),
    # 内侧吸引光沿磁极上行；经过铁块时出现短促钢面反光。
    "magnet": (
        ((4, 10, "+"),), ((4, 9, "*"),), ((4, 8, "*"),),
        ((4, 7, "*"), (7, 7, "x")), ((4, 6, "*"), (7, 6, "L")),
        ((4, 5, "*"), (7, 6, "x")), ((4, 4, "+"),),
    ),
    # 源质亮点顺时针巡回，四个静态方向标记与管壁不变。
    "channel_persist": (
        ((9, 3, "*"),), ((11, 5, "*"),), ((11, 9, "*"),),
        ((9, 12, "*"),), ((6, 12, "*"),), ((4, 10, "*"),),
        ((4, 5, "*"),),
    ),
    # 星纹周围的小光芒逐次闪现，帽尖与金属扣交替微亮。
    "mage": (
        ((9, 5, "Y"),), ((8, 6, "Y"), (8, 2, "*")),
        ((7, 7, "Y"),), ((8, 8, "Y"), (8, 10, "x")),
        ((10, 8, "Y"),), ((11, 7, "Y"), (8, 10, "L")),
        ((10, 6, "Y"), (9, 2, "+")),
    ),
}


def build_one(token: str, frame: int = 0) -> bytearray:
    """16×16 单帧；底形与调色板固定，只按登记位叠加局部流光。"""
    assert 0 <= frame < FRAMES, frame
    rows = [list(row) for row in ART[token]]
    if frame:
        for x, y, ch in ANIMATION_MARKS[token][frame - 1]:
            assert rows[y][x] not in (BLANK_CH, "O"), (token, frame, x, y)
            rows[y][x] = ch
    buf = bytearray()
    for row in rows:
        for ch in row:
            buf += b"\x00\x00\x00\x00" if ch == BLANK_CH else bytes((*color_of(token, ch), 255))
    return buf


def encode(token: str) -> bytes:
    strip = b"".join(bytes(build_one(token, frame)) for frame in range(FRAMES))
    return pngwrite.encode_png(SIZE, SIZE * FRAMES, strip)


def build_mcmeta() -> bytes:
    # 与本项目现有口袋动画一致：1.7.10 atlas 按竖带自动推导帧顺序。
    return (json.dumps({"animation": {"frametime": FRAMETIME}},
                       separators=(",", ":")) + "\n").encode("ascii")


def _opaque(buf: bytes) -> set:
    out = set()
    for y in range(SIZE):
        for x in range(SIZE):
            i = (y * SIZE + x) * 4
            if buf[i + 3] != 0:
                out.add((x, y))
    return out


def _connected(cells: set) -> bool:
    """不透明像素 4 连通单分量（图形是一个 glyph，不是散点）。"""
    start = min(cells)
    seen, stack = {start}, [start]
    while stack:
        cx, cy = stack.pop()
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (cx + dx, cy + dy)
            if n in cells and n not in seen:
                seen.add(n)
                stack.append(n)
    return len(seen) == len(cells)


def _gui_upgrades_source() -> dict[str, tuple[str, ...]]:
    """从 pocket_gui 脚本正文解析 UPGRADES 字符画（只读，不 import——两目录互不 import 的纪律）。"""
    text = GUI_UPG.read_text(encoding="utf-8")
    out: dict[str, tuple[str, ...]] = {}
    lines = text.splitlines()
    i = 0
    while i < len(lines):
        if '"POCKET_C2_upg_' in lines[i] and '": (' in lines[i]:
            gui_token = lines[i].split('"')[1][len("POCKET_C2_upg_"):]
            token = _GUI_TOKEN_MAP.get(gui_token, gui_token)
            rows = []
            j = i + 1
            while j < len(lines):
                seg = lines[j].strip().strip(',')
                if seg.startswith('"') and seg.endswith('"'):
                    rows.append(seg[1:-1])
                elif seg.startswith("))"):
                    break
                j += 1
            out[token] = tuple(rows)
            i = j
        i += 1
    return out


def self_check(built: dict[str, bytes]) -> list[str]:
    out = []
    # --- 0 调色板：来源实图钉值 + 派生公式复算（与 gen_pocket 的第 0 判据同一条纪律）
    bad = P.check_sources(ITEMS)
    assert not bad, "来源贴图基线漂移（本任务全程只读）：\n  " + "\n  ".join(bad)
    out.append("调色板：来源实图 %d 张 SHA 逐字节钉住；本批用色档全部为 palette 已登记档（R96 零新增色档）"
               % len(P.REF_SHA))
    # --- 0b 五金档：五张共用同一组冷钢/纯黑档，且自身明度严格递增 blk < gry < hig < hig_lt
    metal_rgb = [PAL[METAL[ch]] for ch in METAL_CHAIN]
    assert all(_luma(metal_rgb[i]) < _luma(metal_rgb[i + 1]) for i in range(len(metal_rgb) - 1)), \
        "五金档明度序被破坏 blk < gry < hig < hig_lt：" + str(metal_rgb)
    out.append("五金档（五张共用，成套骨架）：%s ｜ 明度 %.1f < %.1f < %.1f < %.1f"
               % (" < ".join("%s(%s)" % (ch, METAL[ch]) for ch in METAL_CHAIN),
                  *[_luma(c) for c in metal_rgb]))
    # --- 1 几何与 pocket_gui 版逐字符相等（语义形单一来源，不许单边漂移）
    gui_art = _gui_upgrades_source()
    assert set(gui_art) == set(ART), "token 集与 pocket_gui 版不一致：" + str(set(gui_art) ^ set(ART))
    for token in ART:
        assert ART[token] == gui_art[token], token + " 字符画与 pocket_gui 版漂移（两处必须逐字符同源）"
    out.append("frame0 语义形：五张字符画与 pocket_gui/gen_pocket_gui_upgrades.py 逐字符相等")
    for token in ART:
        art = ART[token]
        assert len(art) == SIZE, token + " 行数不是 16"
        for row in art:
            assert len(row) == SIZE, token + " 行宽不是 16：" + row
            assert all(ch in METAL or ch in HUE[token] or ch == BLANK_CH for ch in row), \
                token + " 行内未登记字符：" + row
        # --- 2 色档：实际用色档数 >= 6（★R96 放开口径，旧「三档俱全」在 9 角色档下数学上不可能过）
        #     + 角色档与 RGB 一一对应（防多字符塌成同一色 = 读不出深浅）
        #     + 同族色链明度严格递增 blk < 暗 < 体 < 亮 < 高光
        chars = {ch for ch in "".join(art) if ch != BLANK_CH}
        used = {color_of(token, ch) for ch in chars}
        assert len(used) >= MIN_TONES, \
            token + " 实际用色只有 %d 档（下限 %d；旧图正是 3 档才读成色块剪影）" % (len(used), MIN_TONES)
        assert len(used) == len(chars), \
            token + " 有角色档塌成同一 RGB（角色 %d 个 / 实色 %d 个）" % (len(chars), len(used))
        chain = [PAL["blk"]] + [PAL[HUE[token][k]] for k in HUE_CHAIN]
        assert all(_luma(chain[i]) < _luma(chain[i + 1]) for i in range(len(chain) - 1)), \
            token + " 色链明度不递增（blk < 暗 < 体 < 亮 < 高光）：" + str(chain)
        assert len(set(chain)) == len(chain), token + " 色链存在重复 RGB"
        # --- 3 图形：连通单分量 + 包围盒下限 + 边缘留白
        buf = bytes(build_one(token))
        cells = _opaque(buf)
        xs = [x for x, _ in cells]
        ys = [y for _, y in cells]
        assert len(cells) >= 40, token + " 实体像素太少"
        assert (max(xs) - min(xs) + 1) >= 10 and (max(ys) - min(ys) + 1) >= 12, token + " 图形包围盒太小"
        for x in range(SIZE):
            for y in (0, SIZE - 1):
                assert (x, y) not in cells, token + " 上下边缘不透明"
        for y in range(SIZE):
            for x in (0, SIZE - 1):
                assert (x, y) not in cells, token + " 左右边缘不透明"
        assert _connected(cells), token + " 不是 4 连通单分量"
        # --- 4 PNG 形状 + 双跑逐字节一致
        png = built[token]
        assert len(png) > 8 and png[1:4] == b"PNG", token + " 不是 PNG"
        assert encode(token) == png, token + " 双跑不一致"
        assert struct.unpack(">II", png[16:24]) == (SIZE, SIZE * FRAMES), token + " 帧带尺寸错误"
        frames = [bytes(build_one(token, f)) for f in range(FRAMES)]
        assert len(set(frames)) == FRAMES, token + " 有重复帧"
        allowed = set(METAL) | set(HUE[token])
        registered = {(x, y) for marks in ANIMATION_MARKS[token] for x, y, _ in marks}
        assert len(ANIMATION_MARKS[token]) == FRAMES - 1, token + " 动效登记帧数错误"
        for f, frame in enumerate(frames):
            assert _opaque(frame) == cells, token + " 动画改变透明区或轮廓"
            changed = set()
            for y in range(SIZE):
                for x in range(SIZE):
                    i = (y * SIZE + x) * 4
                    rgba = frame[i:i + 4]
                    assert rgba[3] == buf[i + 3], token + " 动画 alpha 漂移"
                    if rgba[3]:
                        assert tuple(rgba[:3]) in {color_of(token, ch) for ch in allowed}, token + " 动画用色越界"
                    if rgba != buf[i:i + 4]:
                        changed.add((x, y))
            assert changed <= registered, token + " 动画触碰未登记位"
            assert len(changed) <= 3, token + " 动效范围过大"
        adjacent = [sum(a[i:i + 4] != b[i:i + 4] for i in range(0, len(a), 4))
                    for a, b in zip(frames, frames[1:] + frames[:1])]
        assert all(1 <= n <= 6 for n in adjacent), token + " 相邻帧或循环衔接异常"
        metadata = build_mcmeta()
        assert json.loads(metadata) == {"animation": {"frametime": FRAMETIME}}
        assert isinstance(FRAMETIME, int) and FRAMETIME > 0
        assert metadata == build_mcmeta(), "mcmeta 非幂等"
        out.append("%s：%d 帧 / 16×128 / 单帧局部变色≤3 px / 含闭环相邻帧差 %s / alpha与轮廓逐帧一致"
                   % (token, FRAMES, adjacent))
        out.append("%s：用色 %d 档 / 角色 %d 档 ｜ 色链 %s ｜ 实体 %d px ｜ 包围盒 %dx%d ｜ 双跑逐字节一致"
                   % (token, len(used), len(chars),
                      " < ".join(["blk"] + [HUE[token][k] for k in HUE_CHAIN]),
                      len(cells), max(xs) - min(xs) + 1, max(ys) - min(ys) + 1))
    return out


def sha(b: bytes) -> str:
    return hashlib.sha256(b).hexdigest()


def snapshot(d: pathlib.Path) -> dict:
    if not d.exists():
        return {}
    return {p.name: sha(p.read_bytes()) for p in sorted(d.glob("*.png*")) if p.is_file()}


def main() -> int:
    built = {token: encode(token) for token in ART}
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    print("== 口袋升级插件动画（%d 张，%d 帧，%dx%d + mcmeta）==" % (len(ART), FRAMES, SIZE, SIZE * FRAMES))
    for line in self_check(built):
        print("  · " + line)
    for token in ART:
        name = "neko_pocket_upgrade_%s.png" % token
        (OUT_DIR / name).write_bytes(built[token])
        (OUT_DIR / (name + ".mcmeta")).write_bytes(build_mcmeta())
        print("  SHA %-40s %d %s" % (name, len(built[token]), sha(built[token])))
    # ★改名残留哨兵（R96 S8 起 token[4] = mage）：本脚本只认自己这一批基名，
    #   out/ 里同前缀但不在本批的文件 = 上一代的旧图或旧名，一律不得落地。
    stale = sorted(p.name for p in OUT_DIR.glob("neko_pocket_upgrade_*.png")
                   if p.name not in {"neko_pocket_upgrade_%s.png" % t for t in ART})
    if stale:
        print("  ★out/ 内非本批同族文件（旧名/旧图残留，★不得落地，需人工确认后再删）= " + ", ".join(stale))
    print("  候选产物目录 = " + str(OUT_DIR.relative_to(ROOT)).replace(chr(92), "/"))
    if "--land" in sys.argv:
        assert "--i-have-authorization" in sys.argv, "落地需 --i-have-authorization"
        before = snapshot(ITEMS)
        mine = {"neko_pocket_upgrade_%s.png%s" % (t, suffix) for t in ART for suffix in ("", ".mcmeta")}
        for token in ART:
            (ITEMS / ("neko_pocket_upgrade_%s.png" % token)).write_bytes(built[token])
            (ITEMS / ("neko_pocket_upgrade_%s.png.mcmeta" % token)).write_bytes(build_mcmeta())
        after = snapshot(ITEMS)
        changed = {k for k in set(after) | set(before) if before.get(k) != after.get(k)}
        assert changed <= mine, "落地越界：" + str(sorted(changed - mine))
        for token in ART:
            name = "neko_pocket_upgrade_%s.png" % token
            assert after[name] == sha(built[token]), name + " 落地字节与本跑产物不符"
            assert after[name + ".mcmeta"] == sha(build_mcmeta()), name + " 落地 mcmeta 不符"
        print("  已落地 %d 个文件到 %s（含5张帧带与5份mcmeta；其余 %d 个PNG/mcmeta逐字节未变）"
              % (len(mine), str(ITEMS.relative_to(ROOT)).replace(chr(92), "/"), len(after) - len(mine)))
        ghost = sorted(k for k in after if k.startswith("neko_pocket_upgrade_") and k not in mine)
        if ghost:
            print("  ★目标目录仍有旧名同族文件（snapshot 判不出「未变」，需主代理单独删除）= " + ", ".join(ghost))

    else:
        print("  （未落地；落地需 --land --i-have-authorization）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
