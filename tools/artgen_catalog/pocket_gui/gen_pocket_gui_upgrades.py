"""POCKET_C2_upg_* 升级格灰化占位图案的幂等生成器（5 张 18×18，S1 片）。

为什么是单独脚本（仿 `tools/artgen_catalog/neko_dimension_pocket/gen_pocket_gui_progress.py`
的先例，不立第二真相）：
  `gen_pocket_gui.py` 的清单 `gui_manifest.SHEETS` 与 `contract.py` 的 tex 数组都钉死在
  HTML 355-366 行（R75 单源），加不进升级格行 —— 动它就是动主管线，本片明令禁止。
  本脚本因此只**复用**同目录的既有权威，跨文件零复制：

  颜色  = 同目录 `palette.py` 的冷钢（steel）家族现成四档 —— st_lo / st_mid / st_hi /
          steel_lip 全是 palette.DERIV 已登记的派生档，本文件零新派生、零字面 RGB；
  尺寸  = 画布 = 契约 SPEC 的 GRID（18，槽位栅格单源）；语义区 = GRID-2 = 16，即 MC
          物品图标在 18px 槽内 +1 让位后的 16×16 —— 插件图标放入后逐像素遮盖图案；
  编码  = 同目录 `pngwrite.encode_png`（与家族 13 张同一实现，无时间戳）。

语义（底部右段行 3 的 5 个升级格，未升级时显示灰化插件图案占位）：
  upg_capacity 容量     = 桶形（流体桶轮廓，带提梁）
  upg_stack    堆叠     = 三层叠层（8/10/12 宽的阶台）
  upg_magnet   磁力     = 马蹄磁铁（开口向上，极帽提亮）
  upg_channel  通道     = 拱门/门户（通到底的开口 + 柱脚）
  upg_distill  蒸馏加速 = 沙漏（上空下满，腰部沙流）

用法:
  python -B gen_pocket_gui_upgrades.py                     # 自检 + 写 out/ + 打印双跑 SHA
  python -B gen_pocket_gui_upgrades.py --land --i-have-authorization   # 落地 5 张目标 PNG

判据：画布外圈全透明（16×16 语义区）/ 图形 4 连通单分量 / 实际用色 ⊆ 冷钢四档且三档俱全 /
对比度门（对槽心底）/ 低饱和门 / 双跑逐字节一致 / 落地前后 snapshot 只动本批 5 张。
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
OUT_DIR = HERE / "out"
TARGET_DIR = ROOT / "src" / "main" / "resources" / "assets" / "gtit" / "textures" / "gui" / "pocket"

sys.path.insert(0, str(HERE))          # palette / pngwrite / contract（全在本目录）

import canvas as CV                  # noqa: E402  本目录像素原语（与家族 13 张同一实现）
import palette as P                  # noqa: E402
import pngwrite                      # noqa: E402

# ---------------------------------------------------------------- 几何（尺寸回契约 SPEC，不另立数）
SIZE = P.CONTRACT.spec["GRID"]          # 18：槽位栅格 = 画布边长（契约单源）
SEMANTIC = SIZE - 2                     # 16：MC 物品图标在 18 槽内 +1 让位后的语义区
OFFSET = (SIZE - SEMANTIC) // 2         # 1：语义区左上角 = (1, 1)
assert SIZE >= 4 and SEMANTIC >= 2, (SIZE, SEMANTIC)

# ---------------------------------------------------------------- 色板（冷钢家族现成档，零新派生）
# 灰化占位的读法：图案整体坐冷灰（与暖色布底分形 => 一眼"未激活"），三档 = 轮廓/体/受光。
# 四档全部是 palette.DERIV 已登记的 steel 家族派生，本文件不出现任何 tint/shade 参数。
TONES = {
    "O": P.PAL["st_lo"],        # 轮廓（最深档）
    "#": P.PAL["st_mid"],       # 体
    "+": P.PAL["steel_lip"],    # 受光/极帽/玻璃（家族冷钢的最亮档，slot_tall 外圈同源）
}
BLANK_CH = "."

MIN_SPREAD_GATE = 48            # 低饱和门：max-min 通道差上界（实测四档 24..39）
CON_OUTLINE_GATE = 2.80         # 轮廓对槽心底（实测 3.42）：轮廓承重，门最高
CON_BODY_GATE = 1.60            # 体对槽心底（实测 1.86）
CON_HILITE_GATE = 1.60          # 受光对槽心底（实测 >1.8）
CON_SELF_GATE = 1.50            # 轮廓对体（实测 1.84）：图形自身要切得开

# ---------------------------------------------------------------- 像素（16×16 语义区，字符画 = 本脚本的几何登记处）
# 每张恰 16 行 x 16 列；列/行 0 与 15 对齐画布外圈 1px 让位。O=轮廓 #=体 +=受光 .=透明
UPGRADES: dict[str, tuple[str, tuple[str, ...]]] = {
    "POCKET_C2_upg_capacity": ("容量 = 流体桶（提梁 + 收口桶身）", (
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
    )),
    "POCKET_C2_upg_stack": ("堆叠 = 三层叠层（8/10/12 宽阶台）", (
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
    )),
    "POCKET_C2_upg_magnet": ("磁力 = 马蹄磁铁（开口向上，极帽提亮）", (
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
    )),
    "POCKET_C2_upg_channel": ("通道 = 拱门/门户（通底开口 + 柱脚）", (
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
    )),
    "POCKET_C2_upg_distill": ("蒸馏加速 = 沙漏（上空下满，腰部沙流）", (
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
    )),
}


# ---------------------------------------------------------------- 构图
def build_one(token: str) -> bytearray:
    """字符画 -> 18×18 RGBA 缓冲（语义区 (1,1) 起，外圈全透明）。"""
    _usage, art = UPGRADES[token]
    buf = CV.blank(SIZE, SIZE)
    for y, row in enumerate(art):
        for x, ch in enumerate(row):
            if ch != BLANK_CH:
                CV.put(buf, SIZE, OFFSET + x, OFFSET + y, TONES[ch])
    return buf


def flat_of(buf: bytearray) -> bytes:
    return bytes(buf)


def encode(token: str) -> bytes:
    return pngwrite.encode_png(SIZE, SIZE, flat_of(build_one(token)))


# ---------------------------------------------------------------- 判据
def _bbox_and_alpha(token: str):
    buf = build_one(token)
    xs, ys, opaque = [], [], 0
    for y in range(SIZE):
        for x in range(SIZE):
            if CV.alpha_at(buf, SIZE, x, y) != 0:
                xs.append(x)
                ys.append(y)
                opaque += 1
    assert xs and ys, token + " 全透明"
    return buf, (min(xs), min(ys), max(xs), max(ys)), opaque


def _connected(buf: bytearray, bbox) -> bool:
    """不透明像素的 4 连通单分量检查（图形是一个 glyph，不是散点）。"""
    x0, y0, x1, y1 = bbox
    seen = set()
    start = None
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if CV.alpha_at(buf, SIZE, x, y) != 0:
                start = (x, y)
                break
        if start:
            break
    stack = [start]
    seen.add(start)
    while stack:
        cx, cy = stack.pop()
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = cx + dx, cy + dy
            if nx < 0 or ny < 0 or nx >= SIZE or ny >= SIZE:
                continue
            if (nx, ny) in seen or CV.alpha_at(buf, SIZE, nx, ny) == 0:
                continue
            seen.add((nx, ny))
            stack.append((nx, ny))
    total = sum(1 for y in range(SIZE) for x in range(SIZE)
                if CV.alpha_at(buf, SIZE, x, y) != 0)
    return len(seen) == total


def self_check(built: dict[str, bytes]) -> list[str]:
    out = []
    floor = P.PAL["floor"]                     # 槽心底 = 图案的实际坐底（palette 现读）
    for tone_name in ("st_lo", "st_mid", "st_hi", "steel_lip"):
        c = P.PAL[tone_name]
        spread = max(c) - min(c)
        assert spread <= MIN_SPREAD_GATE, "%s 通道差 %d 超低饱和门" % (tone_name, spread)
    out.append("低饱和门：冷钢四档 max-min 通道差 <= %d（灰化读法的底子）" % MIN_SPREAD_GATE)
    assert P.contrast(TONES["O"], floor) >= CON_OUTLINE_GATE, "轮廓对槽心底不足"
    assert P.contrast(TONES["#"], floor) >= CON_BODY_GATE, "体对槽心底不足"
    assert P.contrast(TONES["+"], floor) >= CON_HILITE_GATE, "受光对槽心底不足"
    assert P.contrast(TONES["O"], TONES["#"]) >= CON_SELF_GATE, "轮廓对体不足"
    out.append("对比度门：轮廓/体/受光 对槽心底 = %.2f / %.2f / %.2f；轮廓对体 = %.2f"
               % (P.contrast(TONES["O"], floor), P.contrast(TONES["#"], floor),
                  P.contrast(TONES["+"], floor), P.contrast(TONES["O"], TONES["#"])))
    for token in UPGRADES:
        usage, art = UPGRADES[token]
        assert len(art) == SEMANTIC, token + " 行数不是语义区高"
        for row in art:
            assert len(row) == SEMANTIC, token + " 行宽不是语义区宽：" + row
            assert all(ch in TONES or ch == BLANK_CH for ch in row), token + " 行内未登记字符"
        png = built[token]
        assert len(png) > 8 and png[1:4] == b"PNG", token + " 不是 PNG"
        buf, bbox, opaque = _bbox_and_alpha(token)
        x0, y0, x1, y1 = bbox
        assert OFFSET <= x0 and x1 <= SIZE - 1 - OFFSET, token + " 图形越出语义区 x"
        assert OFFSET <= y0 and y1 <= SIZE - 1 - OFFSET, token + " 图形越出语义区 y"
        assert (x1 - x0 + 1) >= 10 and (y1 - y0 + 1) >= 12, token + " 图形包围盒太小"
        for x in range(SIZE):                   # 画布外圈（含让位 1px）必须全透明
            for y in (0, SIZE - 1):
                assert CV.alpha_at(buf, SIZE, x, y) == 0, token + " 上下外圈不透明"
        for y in range(SIZE):
            for x in (0, SIZE - 1):
                assert CV.alpha_at(buf, SIZE, x, y) == 0, token + " 左右外圈不透明"
        assert _connected(buf, bbox), token + " 不是 4 连通单分量"
        used = {CV.rgb_at(buf, SIZE, x, y) for y in range(SIZE) for x in range(SIZE)
                if CV.alpha_at(buf, SIZE, x, y) != 0}
        assert used <= set(TONES.values()), token + " 用色超出冷钢三档"
        assert used == set(TONES.values()), token + " 三档没有用全（图形没有深浅读法）"
        assert P.ACCENT_RGB not in used, token + " 强调色越界（占位图案不是可点物）"
        assert opaque >= 40, token + " 实体像素太少"
        again = pngwrite.encode_png(SIZE, SIZE, flat_of(build_one(token)))
        assert again == png, token + " 双跑不一致"
        out.append("%s：%s ｜ 包围盒 %dx%d @(%d,%d) ｜ 实体 %d px ｜ 双跑逐字节一致"
                   % (token, usage, x1 - x0 + 1, y1 - y0 + 1, x0, y0, opaque))
    return out


def sha(b: bytes) -> str:
    return hashlib.sha256(b).hexdigest()


def snapshot(d: pathlib.Path) -> dict:
    if not d.exists():
        return {}
    return {p.name: sha(p.read_bytes()) for p in sorted(d.glob("*.png")) if p.is_file()}


def main() -> int:
    built = {token: encode(token) for token in UPGRADES}
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    print("== 升级格灰化占位（%d 张，画布 %dx%d，语义区 %dx%d @(%d,%d)）=="
          % (len(UPGRADES), SIZE, SIZE, SEMANTIC, SEMANTIC, OFFSET, OFFSET))
    for line in self_check(built):
        print("  · " + line)
    for token in UPGRADES:
        (OUT_DIR / (token + ".png")).write_bytes(built[token])
        print("  SHA %-26s %d %s" % (token, len(built[token]), sha(built[token])))
    print("  候选产物目录 = " + str(OUT_DIR.relative_to(ROOT)).replace(chr(92), "/"))
    if "--land" in sys.argv:
        assert "--i-have-authorization" in sys.argv, "落地需 --i-have-authorization"
        before = snapshot(TARGET_DIR)
        mine = {token + ".png" for token in UPGRADES}
        for token in UPGRADES:
            (TARGET_DIR / (token + ".png")).write_bytes(built[token])
        after = snapshot(TARGET_DIR)
        changed = {k for k in set(after) | set(before) if before.get(k) != after.get(k)}
        assert changed <= mine, "落地越界：" + str(sorted(changed - mine))
        for token in UPGRADES:
            assert after[token + ".png"] == sha(built[token]), token + " 落地字节与本跑产物不符"
        print("  已落地 %d 张到 %s（该目录其余 %d 张逐字节未变）"
              % (len(mine), str(TARGET_DIR.relative_to(ROOT)).replace(chr(92), "/"),
                 len(after) - len(mine)))
        for name in sorted(after):
            print("    %-28s %s" % (name, after[name]))
    else:
        print("  （未落地；落地需 --land --i-have-authorization）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
