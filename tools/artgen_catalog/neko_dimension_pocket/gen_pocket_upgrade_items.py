"""口袋升级插件物品图标（5 张 16×16 彩色）的幂等生成器（R95 S2b）。

为什么是单独脚本（仿 `tools/artgen_catalog/pocket_gui/gen_pocket_gui_upgrades.py` 的先例）：
  本目录（neko_dimension_pocket = 物品图标族）的 `gen_pocket.py` 钉死在口袋四态帧带的
  `pocket_manifest` 清单上，加不进新物品行——动它就是动主管线。本脚本只**复用**同目录
  既有权威，跨文件零复制：

  颜色  = 同目录 `palette.py`（物品图标族唯一允许字面 RGB 的文件）：基色逐条钉在既有实图
          （`REF_POINTS` + `REF_SHA`），本脚本用到的三档全部由已登记的 tint/shade 派生档
          取得（R95 S2b 新登记 amb_lt / green_md / green_dk 三档），本文件零新派生、零字面 RGB；
  几何  = 语义形**逐字复用** `pocket_gui/gen_pocket_gui_upgrades.py` 的五张 16×16 字符画
          （桶/叠层/马蹄/拱门/沙漏——GUI 灰化占位与物品图标同形，玩家凭形状即可对上槽位；
          两处字符画由本脚本的自检"与 pocket_gui 版逐字符相等"钉住，不许单边漂移）；
  编码  = 同目录 `pngwrite.encode_png`（与口袋四态帧带同一实现，无时间戳）。

语义（五型插件，色系与效果对应）：
  capacity  容量   = 流体蓝系（cyan_dk/cyan/aqua_lt）桶形
  stack     堆叠   = 金棕系（eng/brz/brt）三层叠层
  magnet    磁力   = 赤红系（eng/amb/amb_lt）马蹄磁铁
  channel_persist 通道 = 紫系（deep/lpur/lilac）拱门
  distill_fast 蒸馏 = 绿系（green_dk/green_md/green）沙漏

用法:
  python -B gen_pocket_upgrade_items.py                     # 自检 + 写 out/ + 打印双跑 SHA
  python -B gen_pocket_upgrade_items.py --land --i-have-authorization   # 落地 5 张物品图标

判据：字符画与 pocket_gui 版逐字符相等 / 4 连通单分量 / 三档俱全且明度严格递增
（O<#<+，无字面 RGB，色名全部经 palette 公式命中）/ 双跑逐字节一致 / 落地 snapshot 只动本批 5 张。
"""
from __future__ import annotations

import hashlib
import pathlib
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

SIZE = 16                              # MC 物品图标原生尺寸（1.7.10 items 贴图口径）
BLANK_CH = "."

# ---------------------------------------------------------------- 色系登记（O 轮廓 / # 体 / + 受光；全部是 palette 档名）
# ★明度必须严格递增 O < # < +（自检里断言）：16px 小图的三档读法靠明度阶撑。
TONES: dict[str, tuple[str, str, str]] = {
    "capacity": ("cyan_dk", "cyan", "aqua_lt"),       # 流体蓝系
    "stack": ("eng", "brz", "brt"),                   # 金棕系
    "magnet": ("eng", "amb", "amb_lt"),               # 赤红系
    "channel_persist": ("deep", "lpur", "lilac"),     # 紫系
    "distill_fast": ("green_dk", "green_md", "green") # 绿系
}

CHAR_TO_ROLE = {"O": 0, "#": 1, "+": 2}

# ---------------------------------------------------------------- 像素（16×16 字符画：几何登记处 = 语义形单一来源的镜像）
# 与 pocket_gui/gen_pocket_gui_upgrades.py 的 UPGRADES 五张逐字符同源（自检钉住）；
# 本表按 type token 命名（与注册名 neko_pocket_upgrade_<token> 一致）。
ART: dict[str, tuple[str, ...]] = {
    "capacity": (
        "................",
        "......OOOO......",
        ".....OO..OO.....",
        "....OO....OO....",
        "....O......O....",
        "...OOOOOOOOOO...",
        "...O++++++++O...",
        "...O+#######O...",
        "...O+#######O...",
        "...O+#######O...",
        "...O+#######O...",
        "....O+#####O....",
        "....O+#####O....",
        "....O+#####O....",
        "....OOOOOOOO....",
        "................",
    ),
    "stack": (
        "................",
        "................",
        "....OOOOOOOO....",
        "....O+#####O....",
        "....O+#####O....",
        "....OOOOOOOO....",
        "...OOOOOOOOOO...",
        "...O+#######O...",
        "...O+#######O...",
        "...OOOOOOOOOO...",
        "..OOOOOOOOOOOO..",
        "..O+#########O..",
        "..O+#########O..",
        "..OOOOOOOOOOOO..",
        "................",
        "................",
    ),
    "magnet": (
        "................",
        "..OOO......OOO..",
        "..O+O......O+O..",
        "..O+O......O+O..",
        "..O#O......O#O..",
        "..O#O......O#O..",
        "..O#O......O#O..",
        "..O#O......O#O..",
        "..O#OO....OO#O..",
        "..O##OO..OO##O..",
        "..O##OO..OO##O..",
        "..O+#########O..",
        "...O+#######O...",
        "....OOOOOOOO....",
        "................",
        "................",
    ),
    "channel_persist": (
        "................",
        "....OOOOOOOO....",
        "...OOO####OOO...",
        "..OO##....##OO..",
        "..O#+......+#O..",
        "..O#+......+#O..",
        "..O#+......+#O..",
        "..O#+......+#O..",
        "..O#+......+#O..",
        "..O#+......+#O..",
        "..O#+......+#O..",
        "..O#+......+#O..",
        "..O#+......+#O..",
        "..OOO......OOO..",
        "................",
        "................",
    ),
    "distill_fast": (
        "................",
        "................",
        "..OOOOOOOOOOOO..",
        "..O++++++++++O..",
        "...O########O...",
        "....O+####+O....",
        ".....O+##+O.....",
        "......O##O......",
        "......O##O......",
        ".....O+##+O.....",
        "....O+#####O....",
        "...O########O...",
        "..O+#########O..",
        "..OOOOOOOOOOOO..",
        "................",
        "................",
    ),
}

# pocket_gui 版的 token（对齐自检用）：GUI 灰化图 token <本表 token> 的映射（其余恒等）
_GUI_TOKEN_MAP = {"channel": "channel_persist", "distill": "distill_fast"}


def _luma(rgb: tuple[int, int, int]) -> float:
    # 明度阶判据用的简化 luma（与 palette.rel_lum 同序即可；不引入第二份 WCAG 实现）
    return 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2]


def build_one(token: str) -> bytearray:
    """字符画 -> 16×16 RGBA 缓冲（色由 palette 档名现取，本文件无 RGB 字面量）。"""
    o, m, h = TONES[token]
    rgb = {BLANK_CH: None, "O": PAL[o], "#": PAL[m], "+": PAL[h]}
    buf = bytearray()
    for row in ART[token]:
        for ch in row:
            buf += b"\x00\x00\x00\x00" if ch == BLANK_CH else bytes((*rgb[ch], 255))
    return buf


def encode(token: str) -> bytes:
    return pngwrite.encode_png(SIZE, SIZE, bytes(build_one(token)))


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
    out.append("调色板：来源实图 %d 张 SHA 逐字节钉住；本批三档全部为 palette 已登记派生档" % len(P.REF_SHA))
    # --- 1 几何与 pocket_gui 版逐字符相等（语义形单一来源，不许单边漂移）
    gui_art = _gui_upgrades_source()
    assert set(gui_art) == set(ART), "token 集与 pocket_gui 版不一致：" + str(set(gui_art) ^ set(ART))
    for token in ART:
        assert ART[token] == gui_art[token], token + " 字符画与 pocket_gui 版漂移（两处必须逐字符同源）"
    out.append("语义形：五张字符画与 pocket_gui/gen_pocket_gui_upgrades.py 逐字符相等")
    for token in ART:
        art = ART[token]
        assert len(art) == SIZE, token + " 行数不是 16"
        for row in art:
            assert len(row) == SIZE, token + " 行宽不是 16：" + row
            assert all(ch in CHAR_TO_ROLE or ch == BLANK_CH for ch in row), token + " 行内未登记字符"
        # --- 2 色档：三档俱全 + 明度严格递增（O < # < +）
        o, m, h = TONES[token]
        cells_by_role = {0: 0, 1: 0, 2: 0}
        for row in art:
            for ch in row:
                if ch != BLANK_CH:
                    cells_by_role[CHAR_TO_ROLE[ch]] += 1
        assert all(v > 0 for v in cells_by_role.values()), token + " 三档没有用全（没有深浅读法）"
        assert _luma(PAL[o]) < _luma(PAL[m]) < _luma(PAL[h]), \
            token + " 明度阶被破坏：O %s < # %s < + %s" % (PAL[o], PAL[m], PAL[h])
        assert PAL[o] != PAL[m] and PAL[m] != PAL[h], token + " 存在重复 RGB"
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
        assert pngwrite.encode_png(SIZE, SIZE, bytes(build_one(token))) == png, token + " 双跑不一致"
        out.append("%s：%s / %s / %s ｜ 实体 %d px ｜ 双跑逐字节一致"
                   % (token, o, m, h, len(cells)))
    return out


def sha(b: bytes) -> str:
    return hashlib.sha256(b).hexdigest()


def snapshot(d: pathlib.Path) -> dict:
    if not d.exists():
        return {}
    return {p.name: sha(p.read_bytes()) for p in sorted(d.glob("*.png")) if p.is_file()}


def main() -> int:
    built = {token: encode(token) for token in ART}
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    print("== 口袋升级插件物品图标（%d 张，%dx%d，无 mcmeta）==" % (len(ART), SIZE, SIZE))
    for line in self_check(built):
        print("  · " + line)
    for token in ART:
        name = "neko_pocket_upgrade_%s.png" % token
        (OUT_DIR / name).write_bytes(built[token])
        print("  SHA %-40s %d %s" % (name, len(built[token]), sha(built[token])))
    print("  候选产物目录 = " + str(OUT_DIR.relative_to(ROOT)).replace(chr(92), "/"))
    if "--land" in sys.argv:
        assert "--i-have-authorization" in sys.argv, "落地需 --i-have-authorization"
        before = snapshot(ITEMS)
        mine = {"neko_pocket_upgrade_%s.png" % t for t in ART}
        for token in ART:
            (ITEMS / ("neko_pocket_upgrade_%s.png" % token)).write_bytes(built[token])
        after = snapshot(ITEMS)
        changed = {k for k in set(after) | set(before) if before.get(k) != after.get(k)}
        assert changed <= mine, "落地越界：" + str(sorted(changed - mine))
        for token in ART:
            name = "neko_pocket_upgrade_%s.png" % token
            assert after[name] == sha(built[token]), name + " 落地字节与本跑产物不符"
        print("  已落地 %d 张到 %s（该目录其余 %d 张逐字节未变；无 mcmeta 伴生文件）"
              % (len(mine), str(ITEMS.relative_to(ROOT)).replace(chr(92), "/"), len(after) - len(mine)))
    else:
        print("  （未落地；落地需 --land --i-have-authorization）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
