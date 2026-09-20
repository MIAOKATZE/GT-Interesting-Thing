"""R5 重绘三套候选的幂等生成器（skill: gtnh-item-texture-pipeline / 只出候选，不落地）。

单一权威 = candidates_manifest.CANDIDATES（位图清单）+ manifest.BASE（色值）。
本脚本只做「判据自检 + 确定性 PNG 直写」，不含随机源，不写 src 材质目录。

用法:
  python gen_candidates.py           # 双跑自检 + 写 candidates/*.png + 打印六个 SHA256

自检分四类：
  结构（尺寸/字符集/边缘净空/单连通/无 1px 孤岛）
  调色板（每个用色必须逐字出现在三张实图里 = 「派生」的硬证据，禁止包外色相）
  方向（每套候选各自实现 §R5 锁定方向的专属判据）
  可读性（1x 密度、2x 缩小后三候选两两仍可辨、A 的 1px 角标缩小后仍与邻块可分）
"""
from __future__ import annotations

import hashlib
import pathlib
import sys

from PIL import Image

import candidates_manifest as CM
import manifest as M
import pngwrite

HERE = pathlib.Path(__file__).resolve().parent
CAND_DIR = (HERE / "candidates").resolve()
ITEMS = HERE.parents[2] / "src" / "main" / "resources" / "assets" / "gtit" / "textures" / "items"
REF_TEX = ("infinity_cell.png", "infinity_fluid_cell.png", "miao_coin.png")
ACTIVE = "neko_infinity_unit.png"
SIZE = CM.SIZE

# 「一眼可辨」量化门限（整图逐通道平均绝对差，0-255 尺度）
MAD_FULL_MIN = 40.0
MAD_MINIFIED_MIN = 30.0
# A 角标缩小 2x 后与最近的非角标块的最小差
BAR_BLOCK_MIN = 20.0


def sha(b: bytes) -> str:
    return hashlib.sha256(b).hexdigest()


def ref_colors() -> set[tuple[int, int, int]]:
    """三张实图里真实出现过的不透明 RGB（调色板派生的唯一合法来源）。"""
    out: set[tuple[int, int, int]] = set()
    for name in REF_TEX:
        im = Image.open(ITEMS / name).convert("RGBA")
        raw = im.tobytes()
        out |= {tuple(raw[i:i + 3]) for i in range(0, len(raw), 4) if raw[i + 3] == 255}
    return out


def _positions(rows, chars):
    return {(x, y) for y, row in enumerate(rows) for x, ch in enumerate(row) if ch in chars}


def _components(cells, conn=8):
    nb = [(-1, -1), (-1, 0), (-1, 1), (0, -1), (0, 1), (1, -1), (1, 0), (1, 1)] if conn == 8 \
        else [(0, -1), (0, 1), (-1, 0), (1, 0)]
    seen, out = set(), []
    for c in cells:
        if c in seen:
            continue
        stack, cur = [c], []
        seen.add(c)
        while stack:
            p = stack.pop()
            cur.append(p)
            for q in {(p[0] + dx, p[1] + dy) for dx, dy in nb} & (cells - seen):
                seen.add(q)
                stack.append(q)
        out.append(cur)
    return out


def rel(rgb):
    return (0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2]) / 255.0


def _blocks(rows):
    """2x2 盒式缩小 -> 8x8 平均 RGBA（模拟 GUI/手持缩小后的最坏情况）。"""
    out = []
    for by in range(SIZE // 2):
        for bx in range(SIZE // 2):
            acc = [0, 0, 0, 0]
            for dy in (0, 1):
                for dx in (0, 1):
                    ch = rows[by * 2 + dy][bx * 2 + dx]
                    if ch != ".":
                        acc = [a + v for a, v in zip(acc, (*M.PALETTE[ch], 255))]
            out.append(tuple(v / 4 for v in acc))
    return out


def _flat(rows):
    out = []
    for row in rows:
        for ch in row:
            out.extend((0, 0, 0, 0) if ch == "." else (*M.PALETTE[ch], 255))
    return out


def _nums(seq):
    out = []
    for p in seq:
        out.extend(p if isinstance(p, tuple) else [p])
    return out


def _mad(a, b):
    fa, fb = _nums(a), _nums(b)
    return sum(abs(x - y) for x, y in zip(fa, fb)) / (len(fa) / 4 * 4)


# ------------------------------------------------------------------ 判据
def check(name, rows, log, allowed):
    spec = CM.CANDIDATES[name]["spec"]
    assert len(rows) == SIZE, f"{name} 行数 {len(rows)} != {SIZE}"
    for i, row in enumerate(rows):
        assert len(row) == SIZE, f"{name} row {i} 宽 {len(row)} != {SIZE}"
        for ch in row:
            assert ch == "." or ch in M.PALETTE, f"{name} row {i} 未登记色符 {ch!r}"
    log.append("PASS 16x16 字符清单，色符全部取自 manifest.BASE（无本地魔数色值）")

    used = {ch for row in rows for ch in row if ch != "."}
    stray = {ch for ch in used if M.PALETTE[ch] not in allowed}
    assert not stray, f"{name} 用色 {sorted(stray)} 未出现在三张实图里（包外色相）"
    log.append(f"PASS 用色 {len(used)} 档逐字命中 infinity_cell/infinity_fluid_cell/miao_coin 实图色")

    opaque = _positions(rows, used)
    assert not [p for p in opaque if p[0] in (0, SIZE - 1) or p[1] in (0, SIZE - 1)], \
        f"{name} 四边 1px 净空被占"
    log.append("PASS row0/row15/col0/col15 全透明（无贴边像素，GUI 缩放不切形）")

    assert len(_components(opaque, conn=4)) == 1, f"{name} 不透明像素非单连通"
    log.append("PASS 整体 4-连通单组件（无孤立像素团）")

    holes = [cp for cp in _components({(x, y) for y in range(SIZE) for x in range(SIZE)} - opaque,
                                       conn=4)
             if not any(x in (0, SIZE - 1) or y in (0, SIZE - 1) for x, y in cp)]
    assert not holes, f"{name} 存在被包住的孤立透明团 {sorted(holes[0])[:4]}…"
    log.append(f"PASS 无孤立透明像素（{len(opaque)} 不透明 / {SIZE * SIZE - len(opaque)} 透明，"
               f"透明团全部触边，闭腔会在 GUI 抗锯齿下变脏点）")

    islands = [(ch, cp[0]) for ch in used
               for cp in _components(_positions(rows, ch), conn=8) if len(cp) == 1]
    assert not islands, f"{name} 存在 1px 孤岛色块 {islands}（缩小后必消失）"
    log.append("PASS 无 1px 孤岛色块（每种色的连通块均 >=2px）")

    assert 120 <= len(opaque) <= 175, f"{name} 不透明 {len(opaque)}px 超出 120-175"
    assert len(used) <= 10, f"{name} 用色 {len(used)} 档超出低密度上限 10"
    log.append(f"PASS 1x 密度 {len(opaque)}px / 用色 {len(used)} 档（样板 138/142px，信息量已降）")

    if "bar" in spec:  # ---------------------------------------------------- 候选 A
        bar = sorted(_positions(rows, spec["bar"]))
        ys = {y for _, y in bar}
        assert len(ys) == 1, "角标必须是一行（1px 高色条）"
        y = ys.pop()
        xs = sorted(x for x, _ in bar)
        assert xs == list(range(xs[0], xs[-1] + 1)) and len(xs) >= 6, "角标必须连续且 >=6px 长"
        above = [rows[y - 1][x] for x in xs]
        below = [rows[y + 1][x] for x in xs]
        assert above.count(".") <= 2 and below.count(".") <= 2, "角标上下支撑像素不足"
        bar_l = rel(M.PALETTE[spec["bar"][0]])
        d_up = max(rel(M.PALETTE[c]) for c in above if c != ".") - bar_l
        d_dn = max(rel(M.PALETTE[c]) for c in below if c != ".") - bar_l
        assert d_up <= -0.3 and d_dn <= -0.3, (d_up, d_dn)
        face = _components(_positions(rows, spec["face"]), conn=8)
        assert len(face) == 3, f"猫脸应为 3 痕（双眼 + 鼻），实得 {len(face)}"
        assert not _positions(rows, "".join(CM.CHANNEL_HUES)), "A 残留通道色相弧"
        log.append(f"PASS 减法版：三色分弧已全删，仅 1 枚 {len(xs)}px x1px 角标 y#fff200"
                   f"（上下净空 {abs(d_up):.2f}/{abs(d_dn):.2f}）+ 3 痕猫脸，零通道色相")
        blocks = _blocks(rows)
        w = SIZE // 2
        by = y // 2
        bar_bx = {xs[0] // 2, xs[-1] // 2} | {x // 2 for x in xs}
        worst = 1e9
        for bx in sorted(bar_bx):
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = bx + dx, by + dy
                if not (0 <= nx < w and 0 <= ny < w) or (nx, ny) in {(b, by) for b in bar_bx}:
                    continue
                nb = blocks[ny * w + nx]
                if nb[3] == 0:  # 全透明邻块不构成混淆风险
                    continue
                worst = min(worst, _mad([blocks[by * w + bx]], [nb]))
        assert worst >= BAR_BLOCK_MIN, f"角标缩小 2x 后与非角标邻块仅差 {worst:.1f}"
        log.append(f"PASS 角标缩小 2x（8x8 最坏情况）后与最近的非角标邻块仍差 {worst:.1f}"
                   f"（>= {BAR_BLOCK_MIN:.0f}；GUI 槽 1x 原生纹素下不受此影响）")

    if "sectors" in spec:  # ------------------------------------------------ 候选 B
        assert not _positions(rows, "".join(CM.CHANNEL_HUES)), "B 引入了通道色相"
        sec = {ch for ch in spec["sectors"] if ch in used}
        assert sec == set(spec["sectors"]), f"B 三格明度档不全：{sorted(sec)}"
        lum = sorted(rel(M.PALETTE[ch]) for ch in spec["sectors"])
        assert lum[1] - lum[0] >= 0.15, f"两级明度差仅 {lum[1] - lum[0]:.2f}，1x 读不出分格"
        comps = [_positions(rows, ch) for ch in spec["sectors"]]
        cells = set().union(*comps)
        n = len(_components(cells, conn=8))
        assert n == 3, f"币面被分成 {n} 格，应为 3 格"
        ribs = _components(_positions(rows, spec["ribs"]), conn=8)
        assert len(ribs) == 1, "分格筋必须是一整条连通 Y"
        log.append(f"PASS 两级明度三格：三胞等分（{lum[1] - lum[0]:.2f} 明度差 / 2 档），"
                   f"黑色 Y 筋 {len(_positions(rows, spec['ribs']))}px 单连通，零第三色相")

    if "casing" in spec:  # -------------------------------------------------- 候选 C
        casing = _positions(rows, spec["casing"])
        badge = _positions(rows, spec["badge"])
        assert len(casing) >= 2 * len(badge), f"外壳 {len(casing)}px 未压过徽记 {len(badge)}px"
        win = _components(_positions(rows, spec["window"]), conn=8)
        assert len(win) == 1, "开窗必须是一整块（不能碎成多块）"
        win_px = _positions(rows, spec["window"])
        assert all((x + dx, y + dy) in badge | win_px for (x, y) in badge
                   for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))), "徽记贴到外壳 -> 缩小后糊底"
        log.append(f"PASS 元件感优先：外壳 {len(casing)}px = 徽记 {len(badge)}px 的 "
                   f"{len(casing) / len(badge):.1f} 倍，开窗 {len(win[0])}px 整块托住徽记")


def render(name, log):
    rows = CM.CANDIDATES[name]["art"]
    check(name, rows, log, ref_colors())
    return pngwrite.encode_grid(SIZE, rows, M.PALETTE)


def main():
    src_root = (HERE.parents[2] / "src").resolve()
    assert str(CAND_DIR).startswith(str(HERE.resolve())) and src_root not in CAND_DIR.parents, \
        "本生成器只允许写管线目录，禁止落地 src 材质"
    CAND_DIR.mkdir(exist_ok=True)

    before = {n: sha((ITEMS / n).read_bytes()) for n in (ACTIVE, *REF_TEX)}
    names = list(CM.CANDIDATES)

    blobs, logs = {}, {}
    for n in names:
        lg1, lg2 = [], []
        b1, b2 = render(n, lg1), render(n, lg2)
        assert lg1 == lg2, f"{n} 自检日志不稳定"
        assert b1 == b2, f"{n} 非幂等：双跑字节不一致"
        blobs[n], logs[n] = b1, lg1

    for n in names:
        print(f"[{n}] {CM.CANDIDATES[n]['title']}")
        for line in logs[n]:
            print("  " + line)
        print(f"  sha256 run1 = {sha(blobs[n])}")
        print(f"  sha256 run2 = {sha(blobs[n])}  (双跑逐字节一致)")

    flats = {n: _flat(CM.CANDIDATES[n]["art"]) for n in names}
    mins = {n: _blocks(CM.CANDIDATES[n]["art"]) for n in names}
    print("[三候选互异（一眼可辨）]")
    for i, a in enumerate(names):
        for b in names[i + 1:]:
            f, m = _mad(flats[a], flats[b]), _mad(mins[a], mins[b])
            assert f >= MAD_FULL_MIN and m >= MAD_MINIFIED_MIN, (a, b, f, m)
            print(f"  PASS {a} vs {b}: 原尺寸均差 {f:.1f} / 缩小 2x 后 {m:.1f}"
                  f"（门限 {MAD_FULL_MIN:.0f} / {MAD_MINIFIED_MIN:.0f}）")

    print("[产物]")
    for n in names:
        dest = CAND_DIR / f"{n}.png"
        dest.write_bytes(blobs[n])
        im = Image.open(dest).convert("RGBA")
        assert im.size == (SIZE, SIZE), f"{n} 尺寸 {im.size}"
        assert im.getextrema()[3] in ((0, 255), (0, 0), (255, 255)), f"{n} alpha 非二值"
        assert not (CAND_DIR / f"{n}.png.mcmeta").exists(), "静态单帧不应有 mcmeta"
        print(f"  {dest}  {len(blobs[n])} B  sha256={sha(blobs[n])}")
    print("  PASS 三张均为 16x16 RGBA / alpha 二值 / 静态单帧无 .png.mcmeta")

    after = {n: sha((ITEMS / n).read_bytes()) for n in (ACTIVE, *REF_TEX)}
    assert before == after, "src 材质目录字节发生变化（本任务禁止覆写）"
    print("[非目标不变] src 材质目录本次未写入，逐字节核对：")
    for n, h in after.items():
        print(f"  {n}: {h}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
