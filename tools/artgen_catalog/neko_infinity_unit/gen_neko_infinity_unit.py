"""neko_infinity_unit 物品图标幂等生成器（skill: gtnh-item-texture-pipeline）。

单一权威 = manifest.py（PALETTE + OCTAGON_SPANS + ART）；本脚本只做
「结构校验 + 确定性 PNG 直写 + 落位与非目标不变性核验」，不含任何随机源。

用法:
  python gen_neko_infinity_unit.py            # 双跑自检 + 写 ./output/neko_infinity_unit.png
  python gen_neko_infinity_unit.py --land     # 再字节拷贝到 src/.../assets/gtit/textures/items/
"""
from __future__ import annotations

import argparse
import hashlib
import pathlib
import struct
import sys
import zlib

import manifest as M

HERE = pathlib.Path(__file__).resolve().parent
OUT_DIR = HERE / "output"
SIZE = 16
ASSET = "neko_infinity_unit.png"
REPO_ITEM_DIR = HERE.parents[2] / "src" / "main" / "resources" / "assets" / "gtit" / "textures" / "items"
REF_TEX = ("infinity_cell.png", "infinity_fluid_cell.png", "miao_coin.png", "miao_coin.png.mcmeta")


# ---------------------------------------------------------------- 外壳三层
def _erosion(spans):
    inside = {y: set(range(lo, hi + 1)) for y, (lo, hi) in spans.items()}
    out = {}
    for y, xs in inside.items():
        keep = [x for x in sorted(xs)
                if {x - 1, x + 1} <= xs and x in inside.get(y - 1, set()) and x in inside.get(y + 1, set())]
        if keep:
            out[y] = (keep[0], keep[-1])
    return out


def _rings():
    e1, e2 = _erosion(M.OCTAGON_SPANS), _erosion(_erosion(M.OCTAGON_SPANS))
    def band(a, b):
        res = {}
        for y, (lo, hi) in a.items():
            xs = {x for x in range(lo, hi + 1) if y not in b or not (b[y][0] <= x <= b[y][1])}
            if xs:
                res[y] = xs
        return res
    shell = band(M.OCTAGON_SPANS, e1)
    lining = band(e1, e2)
    field = {y: set(range(lo, hi + 1)) for y, (lo, hi) in e2.items()}
    return shell, lining, field


RING1, RING2, FIELD = _rings()


# ------------------------------------------------------------------ 校验
def art_grid():
    rows = list(M.ART)
    assert len(rows) == SIZE, f"ART 行数 {len(rows)} != {SIZE}"
    for i, row in enumerate(rows):
        assert len(row) == SIZE, f"ART row {i} 宽 {len(row)} != {SIZE}"
        for ch in row:
            assert ch == "." or ch in M.PALETTE, f"ART row {i} 未登记色符 {ch!r}"
    return rows


def _pix(bands):
    return {(x, y) for y, xs in bands.items() for x in xs}


def check(rows, log):
    pos = {}
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            pos.setdefault(ch, set()).add((x, y))
    opaque = {p for ch, s in pos.items() if ch != "." for p in s}

    for y in range(SIZE):
        xs = {p[0] for p in opaque if p[1] == y}
        want = set(range(*[M.OCTAGON_SPANS[y][0], M.OCTAGON_SPANS[y][1] + 1])) if y in M.OCTAGON_SPANS else set()
        assert xs == want, f"row {y} 轮廓 {sorted(xs)} != infinity_cell 八边形骨架 {sorted(want)}"
    log.append(f"PASS 轮廓逐字 = infinity_cell 八边形骨架（不透明 {len(opaque)}px）")

    assert not [p for p in opaque if p[0] in (0, 15) or p[1] >= 15], "四边 1px 净空被占"
    log.append("PASS col0/col15/row15 全透明（无贴边像素，GUI 缩放不切形）")

    R1, R2, FL = _pix(RING1), _pix(RING2), _pix(FIELD)
    badge = {p for c in M.BADGE for p in pos.get(c, set())}
    SEPS = {(7, 1), (8, 1), (3, 10), (12, 10)}
    assert {p for c in M.CASING for p in pos.get(c, set())} == R1, "壳体环填充不闭合"
    assert {p for c in "BQP" for p in pos.get(c, set())} | SEPS == R2, "内圈环填充不闭合"
    assert pos["o"] == (FL - badge) | SEPS, "黑腔/黑分隔归属漂移（空腔必须填满黑）"
    log.append(f"PASS 三层无越界：壳体 {len(R1)} / 内圈 {len(R2)} / 空腔 {len(FL)}"
               f"（徽记 {len(badge)} + 黑腔 {len(FL) - len(badge)}）")

    arc = {k: pos.get(k, set()) for k in "BQP"}
    assert all(arc.values()), "三通道缺一"
    for a, b in (("B", "Q"), ("B", "P"), ("Q", "P")):
        assert not any((x + dx, y + dy) in arc[b] for (x, y) in arc[a]
                       for dx in (-1, 0, 1) for dy in (-1, 0, 1)), f"通道 {a}/{b} 相邻 -> 糊成一团"
    assert pos["o"] & R2 == SEPS, "分隔位漂移"
    assert {len(arc["B"]), len(arc["Q"]), len(arc["P"])} == {8}
    log.append("PASS 三通道等权分区：物品蓝 8 / 流体青 8 / 源质紫 8，4 粒黑分隔")

    assert all((x + dx, y + dy) not in R2 for (x, y) in badge
               for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))), "徽记贴住内圈 -> 糊底"
    assert all((x, y) in FL for (x, y) in badge)
    log.append(f"PASS 猫猫币徽记 {len(badge)}px 悬于黑空腔，与内圈隔 >=1px 黑")

    seen, stack = set(), [next(iter(opaque))]
    while stack:
        p = stack.pop()
        if p in seen:
            continue
        seen.add(p)
        stack += [(p[0] + dx, p[1] + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))
                  if (p[0] + dx, p[1] + dy) in opaque]
    assert seen == opaque, "不透明像素不连通"
    log.append("PASS 整体单连通（无孤立像素）")

    def rel(rgb):
        return (0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2]) / 255.0
    d_lit = max(rel(M.PALETTE[c]) for c in "yw") - min(rel(M.PALETTE[c]) for c in "dr")
    assert d_lit >= 0.5, d_lit
    log.append(f"PASS 币体受光/背光亮差 {d_lit:.2f}（>=0.5，16px 读形）")

    cols = {c for c in pos if c != "."}
    assert 8 <= len(cols) <= 22 and 100 <= len(opaque) <= 175
    assert M.DERIVED == {"Q": M._tint(M.BASE["B"], M.Q_TINT)}
    assert all(c in M.BASE for c in cols if c != "Q")

    def dist(a, b):
        return sum((x - y) ** 2 for x, y in zip(a, b)) ** 0.5
    dmin = min(dist(M.PALETTE[a], M.PALETTE[b]) for a, b in (("B", "Q"), ("B", "P"), ("Q", "P")))
    assert dmin >= 60, f"通道色过近 {dmin:.0f}，16px 下会读成一色"
    log.append(f"PASS 用色 {len(cols)} 档全为包内基色（唯一派生 Q=B 浅调 {M.Q_TINT:.0%}），"
               f"三通道最小色差 {dmin:.0f}，密度 {len(opaque)}px vs 样板 138/142")
    return opaque


# -------------------------------------------------------------- PNG 直写
def png_bytes(rows):
    body = bytearray()
    for row in rows:
        buf = bytearray()
        for ch in row:
            rgb = (0, 0, 0) if ch == "." else M.PALETTE[ch]
            buf += bytes((*rgb, 0 if ch == "." else 255))
        body += b"\x00" + bytes(buf)
    comp = zlib.compressobj(9, zlib.DEFLATED, 15)
    idat = comp.compress(bytes(body)) + comp.flush()

    def chunk(kind, data):
        return (struct.pack(">I", len(data)) + kind + data
                + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF))

    ihdr = struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", idat) + chunk(b"IEND", b"")


def render(log):
    rows = art_grid()
    check(rows, log)
    return png_bytes(rows)


def sha(b):
    return hashlib.sha256(b).hexdigest()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--land", action="store_true", help="写字进到 src 材质目录（含非目标不变性核验）")
    args = ap.parse_args()

    log1, log2 = [], []
    blob1, blob2 = render(log1), render(log2)
    print("[自检 run-1]")
    for line in log1:
        print("  " + line)
    assert log1 == log2
    assert blob1 == blob2, "非幂等：双跑字节不一致"
    print("[自检 run-2] 断言逐条同 run-1")
    print(f"  sha256 run1 = {sha(blob1)}")
    print(f"  sha256 run2 = {sha(blob2)}")
    print("  PASS 双跑逐字节一致（同输入幂等可再生）")

    OUT_DIR.mkdir(exist_ok=True)
    (OUT_DIR / ASSET).write_bytes(blob1)
    print(f"[产物] {OUT_DIR / ASSET}  {len(blob1)} B  sha256={sha(blob1)}")
    assert not (OUT_DIR / (ASSET + ".mcmeta")).exists(), "静态单帧不应有 mcmeta"
    print("  PASS 静态单帧 16x16（正方形，无 .png.mcmeta）")

    if args.land:
        refs = {n: sha((REPO_ITEM_DIR / n).read_bytes()) for n in REF_TEX}
        dest = REPO_ITEM_DIR / ASSET
        if dest.exists():
            cur = sha(dest.read_bytes())
            assert cur == sha(blob1), f"落位目标已存在且字节不同（{cur[:12]}），拒绝覆盖"
            print(f"[落位] 目标已为本产物，跳过写盘：{dest}")
        else:
            dest.write_bytes(blob1)
            print(f"[落位] 写盘 {dest}")
        after = {n: sha((REPO_ITEM_DIR / n).read_bytes()) for n in REF_TEX}
        assert refs == after, "参考贴图字节发生变化（非目标未保持不变）"
        assert sha(dest.read_bytes()) == sha(blob1)
        print("  PASS 落位 sha256 == output/ 产物")
        print("  PASS 非目标不变：infinity_cell / infinity_fluid_cell / miao_coin(+mcmeta) 逐字节未动")
        for n, h in after.items():
            print(f"       {n}: {h[:16]}…")
    return 0


if __name__ == "__main__":
    sys.exit(main())
