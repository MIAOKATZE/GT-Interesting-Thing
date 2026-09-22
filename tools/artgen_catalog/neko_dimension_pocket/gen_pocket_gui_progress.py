"""`POCKET_C2_progress` 的幂等生成器（C2 契约的第 13 个 token = 蒸馏横向进度条材质）。

目录归属说明：同风格的另外 12 张由 `tools/artgen_catalog/pocket_gui/` 生成，但那一套的
`contract.py` 把「R75 指认的 tex 数组行区间」钉死在 HTML 的 355-366 行，且 `palette.SEED`
与 `gui_manifest.SHEETS` 都是它自己的表 ⇒ 在不动 `pocket_gui/**` 与 `plan/assest/*.html`
的前提下加不进第 13 行。本文件因此住在**本目录**，但**不另立第二份真相**：

  颜色  = 现场 `import` 兄弟目录的 `contract.py`（该目录自述为「C2 契约的唯一解析入口」）
          与 `palette.py`（`tint/shade/contrast` 公式的唯一持有者）→ 零字面 RGB；
  几何  = 回契约 HTML 现读 `regionSpec` 里 `role:'prog' kind:'bar'` 那一行的 w/h
          （`contract.py` 不解析 regionSpec，故这一处只在本文件解析一次，并断言恰一条）；
  编码  = 本目录 `pngwrite.encode_png`（与 12 张同一实现，双文件实测逐字节相同）。

兄弟目录的文件**全程只读**（本脚本只 import，不写）。

用法:
  python -B gen_pocket_gui_progress.py                     # 自检 + 写 out/gui/ + 打印双跑 SHA
  python -B gen_pocket_gui_progress.py --land --i-have-authorization   # 落地唯一目标 PNG

判据：构造 / 满条与空条横向均匀（裁切无伪影）/ 明度阶梯 / 对比度门 / 强调色不出现 /
双跑逐字节一致 / 非目标不变（除那一个目标文件外零写入）。
"""
from __future__ import annotations

import hashlib
import importlib.util
import pathlib
import re
import sys

HERE = pathlib.Path(__file__).resolve().parent
# 控制台默认 GBK 时打印中文/箭头会 UnicodeEncodeError；只影响读数输出，不参与任何字节。
try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except AttributeError:  # pragma: no cover
    pass
ROOT = HERE.parents[2]
GUI_CATALOG = HERE / "out" / "gui"
TARGET_DIR = ROOT / "src" / "main" / "resources" / "assets" / "gtit" / "textures" / "gui" / "pocket"
CONTRACT_HTML = ROOT / "plan" / "assest" / "pocket-ui-mockups-2.html"

TOKEN = "POCKET_C2_progress"

# ---------------------------------------------------------------- 兄弟目录（只读 import）
# 两个 catalog 各有 palette.py，故必须按文件位置显式装载，不能让 sys.path 顺序决定用哪份。
sys.path.insert(0, str(HERE))                      # pngwrite（本目录那份，与兄弟目录逐字节相同）
sys.path.insert(1, str(ROOT / "tools" / "artgen_catalog" / "pocket_gui"))


def _load(name: str, path: pathlib.Path):
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    sys.modules[name] = mod
    spec.loader.exec_module(mod)
    return mod


import pngwrite                                                        # noqa: E402  本目录编码器
_C = _load("c2_contract", ROOT / "tools/artgen_catalog/pocket_gui/contract.py")
_P = _load("c2_palette", ROOT / "tools/artgen_catalog/pocket_gui/palette.py")

CONTRACT = _C.CONTRACT

# ---------------------------------------------------------------- 色板（选择子回契约，公式归 palette）
# 基色两支，都直接是契约值：满条底 = C2.paint.prog 的 background，描边 = 同一条的 border。
BODY = _P.hex_to_rgb(CONTRACT.hexa("prog", "bg", 0))
BORDER = _P.hex_to_rgb(CONTRACT.hexa("prog", "border", 0))
# 强调色（铜高光）按 C2 的设计文本只给可点物；进度条不可点 ⇒ 机检「一个像素都不许等于它」。
ACCENT = _P.ACCENT_RGB

# 派生档：手段只有 tint/shade（与兄弟目录同规），参数登记在这里而不是绘制代码里。
DERIV = {
    "deep":    ("shade", BORDER, 0.45),   # 空槽内壁顶影（凹槽最深的信号）
    "floor":   ("shade", BORDER, 0.62),   # 空槽底
    "hi":      ("tint", BODY, 0.28),      # 满条上缘受光
    "lo_mid":  ("shade", BODY, 0.84),     # 满条下缘第一档过渡
    "lo":      ("shade", BODY, 0.70),     # 满条下缘（厚度感）
}
PAL = {}
for _name, _spec in DERIV.items():
    _op, _base, _p = _spec
    PAL[_name] = getattr(_P, _op)(_base, _p)
DEEP, FLOOR, HI, LO_MID, LO = (PAL["deep"], PAL["floor"], PAL["hi"], PAL["lo_mid"], PAL["lo"])
# 允许出现在 PNG 里的全部色档（机检"实际用色 ⊆ 登记用色"，防止有人在绘制函数里顺手写死一个 RGB）
COLORS = {BODY, BORDER} | set(PAL.values())

# ---------------------------------------------------------------- 几何（回契约现读，不写字面量）
Q = "'"                              # 契约数组的单引号包法（与兄弟目录同一形状）
_RE_REGION = re.compile("kind:" + Q + "bar" + Q + ".*?role:" + Q + "prog" + Q)
_RE_WH = re.compile("[wh] *: *([0-9]+)")


def bar_size() -> tuple[int, int]:
    """契约 `regionSpec` 里那一条进度条的像素尺寸（必须恰一条，多一条就是契约漂移）。"""
    hits = [ln for ln in CONTRACT_HTML.read_text(encoding="utf-8").splitlines() if _RE_REGION.search(ln)]
    assert len(hits) == 1, "role:'prog' + kind:'bar' 的契约行必须恰有一条，实得 %d" % len(hits)
    got = _RE_WH.findall(hits[0])
    assert len(got) == 2, "进度条契约行取不到 w/h：" + hits[0].strip()
    w, h = int(got[0]), int(got[1])
    assert h == CONTRACT.spec["GRID"], "进度条高度应恰为一格栅格高（契约 GRID），实得 %d" % h
    return w, h


BAR_W, BAR_H = bar_size()
# MUI2 `ProgressWidget.texture(单张堆叠图, imageSize)` 的口径：**上半空条、下半满条**，
# 两段各自按 1:1 画进 widget 区域（widget 尺寸 = BAR_W × BAR_H），故整张图 108×36。
IMG_W, IMG_H = BAR_W, BAR_H * 2
FRAME = CONTRACT.paint["prog"].border_w          # 描边厚度 = 契约 border 的 px 值
assert FRAME > 0 and BAR_H > 2 * FRAME, "prog 描边厚度吃掉条心"


# ---------------------------------------------------------------- 像素
def empty_rows() -> list[list[tuple[int, int, int]]]:
    """上半 = 空槽：一圈描边 + 内壁顶影 + 槽底（横向完全均匀 ⇒ 满条裁切时它是干净的背景）。"""
    rows = []
    for y in range(BAR_H):
        if y == 0 or y == BAR_H - 1:
            rows.append([BORDER] * BAR_W)
            continue
        inner = FLOOR if y > FRAME else DEEP
        rows.append([BORDER] + [inner] * (BAR_W - 2 * FRAME) + [BORDER] * FRAME)
    return rows


def full_rows() -> list[list[tuple[int, int, int]]]:
    """下半 = 满条：同一圈描边（两半的描边同色 ⇒ 裁切边界不跳色）+ 上缘高光 + 下缘两档暗部。"""
    rows = []
    for y in range(BAR_H):
        if y == 0 or y == BAR_H - 1:
            rows.append([BORDER] * BAR_W)
            continue
        if y == FRAME:
            body = HI
        elif y >= BAR_H - FRAME - 2:
            body = LO if y == BAR_H - FRAME - 1 else LO_MID
        else:
            body = BODY
        rows.append([BORDER] + [body] * (BAR_W - 2 * FRAME) + [BORDER] * FRAME)
    return rows


def flat() -> bytes:
    buf = bytearray()
    for row in empty_rows() + full_rows():
        for r, g, b in row:
            buf += bytes((r, g, b, 255))
    return bytes(buf)


def build() -> bytes:
    png = pngwrite.encode_png(IMG_W, IMG_H, flat())
    assert len(png) > 8 and png[1:4] == b"PNG", "不是 PNG"
    return png


# ---------------------------------------------------------------- 判据
def _uniform(bands: list[list[list[tuple[int, int, int]]]], tag: str) -> None:
    """除左右描边外，每一行必须横向完全同色：`ProgressWidget` 按 u 裁切 ⇒ 任何 x 相关特征
    都只在接近 100% 时才出现，那会变成"跑到快满才亮一下"的伪影。"""
    for rows in bands:
        for y, row in enumerate(rows):
            inner = set(row[FRAME:BAR_W - FRAME])
            assert len(inner) == 1, "%s 第 %d 行横向不均匀，裁切会出现竖条伪影" % (tag, y)


def self_check(png: bytes) -> list[str]:
    out = []
    out.append("几何：条 %d×%d（契约 role:'prog' 现读）⇒ 图 %d×%d，描边 %dpx（契约 border）"
               % (BAR_W, BAR_H, IMG_W, IMG_H, FRAME))
    rows = empty_rows() + full_rows()
    _uniform([rows[:BAR_H]], "空条")
    _uniform([rows[BAR_H:]], "满条")
    out.append("满条/空条逐行横向均匀 ⇒ u 裁切无竖条伪影")
    assert BAR_W * BAR_H * 2 * 4 == len(flat()), "RGBA 字节流长度与图尺寸不符"
    # 明度阶梯：凹槽 顶影 < 槽底；满条 下缘 < 条心 < 上缘
    assert _P.rel_lum(DEEP) < _P.rel_lum(FLOOR), "空槽内壁顶影必须比槽底暗"
    assert _P.rel_lum(LO) < _P.rel_lum(BODY) < _P.rel_lum(HI), "满条明度阶梯必须单调"
    out.append("明度阶梯 DEEP<FLOOR 且 LO<BODY<HI（同一支色只按 tint/shade 分档）")
    c_track = _P.contrast(BODY, FLOOR)
    c_rim = _P.contrast(BORDER, BODY)
    assert c_track >= 1.60, "满条与空槽底对比度不足（%.2f），推进读不出来" % c_track
    assert c_rim >= 1.30, "描边切不出条体（%.2f）" % c_rim
    out.append("对比度：满条体 vs 空槽底 = %.2f（门 1.60）；描边 vs 满条体 = %.2f（门 1.30）"
               % (c_track, c_rim))
    seen = set(all_pixels())
    assert seen <= COLORS, "实际用色超出登记的 %d 档" % len(COLORS)
    assert ACCENT not in seen, "强调色（铜高光）越界：进度条不是可点物"
    out.append("用色共 %d 档（契约基色 BODY=%s / BORDER=%s + tint/shade 派生 %d 档）；"
               "强调色白名单：铜高光 0 命中"
               % (len(seen), CONTRACT.hexa("prog", "bg", 0), CONTRACT.hexa("prog", "border", 0), len(DERIV)))
    twice = pngwrite.encode_png(IMG_W, IMG_H, flat())
    assert twice == png, "双跑不一致"
    out.append("双跑逐字节一致（同一次进程内两次编码）")
    return out


def all_pixels():
    """整张图（两半）的像素序列 —— 只给「实际用色 ⊆ 登记用色」与强调色门用。"""
    return [px for rows in (empty_rows() + full_rows()) for px in rows]


def sha(b: bytes) -> str:
    return hashlib.sha256(b).hexdigest()


def main() -> int:
    png = build()
    GUI_CATALOG.mkdir(parents=True, exist_ok=True)
    (GUI_CATALOG / (TOKEN + ".png")).write_bytes(png)
    print("== %s ==" % TOKEN)
    for line in self_check(png):
        print("  · " + line)
    print("  本跑 SHA256 = " + sha(png))
    print("  候选产物   = " + str((GUI_CATALOG / (TOKEN + ".png")).relative_to(ROOT)).replace("\\", "/"))
    if "--land" in sys.argv:
        assert "--i-have-authorization" in sys.argv, "落地需 --i-have-authorization"
        target = TARGET_DIR / (TOKEN + ".png")
        before = {p.name: sha(p.read_bytes()) for p in sorted(TARGET_DIR.glob("*.png"))}
        target.write_bytes(png)
        after = {p.name: sha(p.read_bytes()) for p in sorted(TARGET_DIR.glob("*.png"))}
        changed = {k for k in set(after) | set(before) if before.get(k) != after.get(k)}
        assert changed <= {TOKEN + ".png"}, "落地动了别的文件：" + str(sorted(changed))
        assert after[TOKEN + ".png"] == sha(png), "落地字节与本跑产物不符"
        print("  已落地 = " + str(target.relative_to(ROOT)).replace("\\", "/")
              + "（该目录其余 %d 张逐字节未变）" % (len(after) - 1))
        for name in sorted(after):
            print("    %-28s %s" % (name, after[name]))
    else:
        print("  （未落地；落地需 --land --i-have-authorization）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
