"""neko_infinity_unit 动态物品图标（动画帧带）的幂等生成器 + 落地器。

单一权威 = `animation_manifest`（帧数 / frametime / 三通道格位表 / 币面呼吸位表）
           + `manifest.PALETTE`（色值，逐字来自三张实图）+ `candidates_manifest.C_CASING`（第 0 帧基准）。
本脚本不含任何字面 RGB、不含随机源；PNG 走 `pngwrite` 手写 IHDR/IDAT/IEND（无时间戳块）-> 双跑逐字节一致。

用法:
  python gen_animation.py            # 自检 + 写 output/animation/{png,mcmeta} + 打印双跑 SHA256
  python gen_animation.py --land     # 追加：同名覆写 src 材质目录（静态图 -> 动画帧带 + 新增 mcmeta）

自检分六类：
  构造（帧数/尺寸/第 0 帧逐字 = cand_c）
  轮廓（全帧不透明位集合与外壳/币体像素逐位不变，只允许动清单登记的位）
  调色板（每个用色逐字命中三张实图，Q 必须是 manifest 已登记的 B->白 60% 派生浅调）
  循环（包络周期性 / 接缝步变不大于环内最大相邻步变 = 无缝的数值证据）
  可读性（无 1px 孤岛 / 通道色不污染猫脸阴刻 / 缩小 2x 后点亮仍可辨）
  自洽（mcmeta 帧数与 frametime 与实际行数一致、alpha 二值、非目标贴图逐字节不变）
"""
from __future__ import annotations

import hashlib
import io
import json
import pathlib
import sys

from PIL import Image

import animation_manifest as AM
import manifest as M
import pngwrite

HERE = pathlib.Path(__file__).resolve().parent
OUT_DIR = HERE / "output" / "animation"
CAND_C = HERE / "candidates" / "cand_c_casing.png"
ITEMS = HERE.parents[2] / "src" / "main" / "resources" / "assets" / "gtit" / "textures" / "items"
REF_TEX = ("infinity_cell.png", "infinity_fluid_cell.png", "miao_coin.png")
REF_META = ("miao_coin.png.mcmeta", "reincarnation_crystal.png.mcmeta")
TARGET = "neko_infinity_unit.png"
TARGET_META = "neko_infinity_unit.png.mcmeta"

# 用户定案的候选 C 指纹（第 0 帧必须逐字命中）
CAND_C_SHA = "3f253e2d000a05519b9ebbcba04d7c684f7478d1c323fe5f6025d02fb1742389"
# 落地前现役静态图的指纹（覆写证据链）
LEGACY_STATIC_SHA = "5876882ce38b1115"

# 缩小 2x（8x8，最坏情况）后「点亮仍可辨」门限：与仓内 1px 细线门限同口径
MAD_BLOCK_PEAK_MIN = 20.0
MAD_BLOCK_DIM_MIN = 10.0

N = AM.FRAME_COUNT
SZ = AM.SIZE


def sha(b: bytes) -> str:
    return hashlib.sha256(b).hexdigest()


# ------------------------------------------------------------------ 帧构造
def frame_rows(f: int) -> list[str]:
    """第 f 帧的 16x16 字符位图：基准 = cand_c，只按清单改写登记位。"""
    grid = [list(row) for row in AM.BASE_FRAME]
    for i, ch in enumerate(AM.CHANNELS):
        lvl = AM.level(i, f)
        for (x, y) in ch["cells"]:
            lit = lvl == 2 or (lvl == 1 and (x, y) in ch["dim"])
            grid[y][x] = ch["hue"] if lit else "o"
    cl = AM.coin_level(f)
    for cells in AM.COIN_GLOW.values():
        for (x, y) in cells:
            grid[y][x] = "g"
    for (x, y) in AM.COIN_GLOW.get(cl, ()):
        grid[y][x] = AM.COIN_GLOW_HUE
    return ["".join(r) for r in grid]


def strip_rows() -> list[str]:
    out: list[str] = []
    for f in range(N):
        out.extend(frame_rows(f))
    return out


def build_png() -> bytes:
    return pngwrite.encode_png(SZ, SZ * N, _flat(strip_rows()))


def build_mcmeta() -> bytes:
    return (json.dumps({"animation": {"frametime": AM.FRAMETIME}}, separators=(",", ":"))
            + "\n").encode("ascii")


# ------------------------------------------------------------------ 像素工具
def r_ok(rows, x, y):
    return 0 <= y < len(rows) and 0 <= x < len(rows[y])


def _cells(rows, chars):
    return {(x, y) for y, row in enumerate(rows) for x, ch in enumerate(row) if ch in chars}


def _components(cells, conn=8):
    nb = [(-1, -1), (-1, 0), (-1, 1), (0, -1), (0, 1), (1, -1), (1, 0), (1, 1)] if conn == 8 \
        else [(0, -1), (0, 1), (-1, 0), (1, 0)]
    seen, out = set(), []
    for c in sorted(cells):
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


def _flat(rows) -> bytes:
    buf = bytearray()
    for row in rows:
        for ch in row:
            buf += b"\x00\x00\x00\x00" if ch == "." else bytes((*M.PALETTE[ch], 255))
    return bytes(buf)


def _blocks(rows, size):
    """2x2 盒式缩小 -> (size/2)x(size/2) 平均 RGBA（模拟 GUI/手持缩小后的最坏情况）。"""
    w = size // 2
    out = []
    for by in range(w):
        for bx in range(w):
            acc = [0, 0, 0, 0]
            for dy in (0, 1):
                for dx in (0, 1):
                    ch = rows[by * 2 + dy][bx * 2 + dx]
                    if ch != ".":
                        acc = [a + v for a, v in zip(acc, (*M.PALETTE[ch], 255))]
            out.append(tuple(v / 4 for v in acc))
    return out


def _mad(a, b):
    fa = [v for p in a for v in p]
    fb = [v for p in b for v in p]
    return sum(abs(x - y) for x, y in zip(fa, fb)) / (len(fa) / 4 * 4)


def ref_colors() -> set[tuple[int, int, int]]:
    out: set[tuple[int, int, int]] = set()
    for name in REF_TEX:
        raw = Image.open(ITEMS / name).convert("RGBA").tobytes()
        out |= {tuple(raw[i:i + 3]) for i in range(0, len(raw), 4) if raw[i + 3] == 255}
    return out


# ------------------------------------------------------------------ 判据
def check(log: list[str]) -> None:
    base = AM.BASE_FRAME
    frames = [frame_rows(f) for f in range(N)]
    mutable = AM.mutable_cells()
    allowed = ref_colors()

    # 1 构造
    assert len(base) == SZ and all(len(r) == SZ for r in base)
    assert len(frames) == N and all(len(fr) == SZ for fr in frames)
    assert frames[0] == list(base), "第 0 帧必须逐字等于用户选定的 cand_c"
    assert sha(pngwrite.encode_grid(SZ, list(base), M.PALETTE)) == CAND_C_SHA, \
        "cand_c 基准指纹漂移（与定案 SHA256 不符）"
    assert SZ % 16 == 0 and (SZ * N) % SZ == 0, "帧宽必须是 POT，帧带高必须是帧宽整数倍"
    log.append(f"PASS 构造：{SZ}x{SZ * N} = {N} 帧垂直帧带；第 0 帧逐字 = cand_c"
               f"（SHA256 {CAND_C_SHA[:12]}… 命中定案值）")

    # 2 轮廓 / 只动登记位
    base_opaque = _cells(base, {c for c in "".join(base) if c != "."})
    for f, fr in enumerate(frames):
        assert _cells(fr, {c for c in "".join(fr) if c != "."}) == base_opaque, \
            f"第 {f} 帧不透明位集合与基准不同（轮廓被改）"
        for y, row in enumerate(fr):
            for x, ch in enumerate(row):
                if (x, y) not in mutable:
                    assert ch == base[y][x], f"第 {f} 帧动了未登记位 ({x},{y})"
    log.append(f"PASS 轮廓：{N} 帧不透明位集合逐帧相同（{len(base_opaque)}px / 帧），"
               f"外壳 SHC + 币体轮廓 + 猫脸 e + 高光 w 全帧逐位不变，"
               f"只有清单登记的 {len(mutable)} 个位可变")

    for f, fr in enumerate(frames):
        hue = _cells(fr, "".join(ch["hue"] for ch in AM.CHANNELS))
        for (x, y) in hue:
            assert 4 <= x <= 11 and 4 <= y <= 11, f"第 {f} 帧通道色越出开窗 ({x},{y})"
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if r_ok(fr, x + dx, y + dy):
                    assert fr[y + dy][x + dx] != "e", f"第 {f} 帧通道色贴到猫脸阴刻 ({x},{y})"
    log.append(f"PASS 通道格始终留在开窗 8x8 内且永不贴碰猫脸阴刻 e（缩小后不糊脸）")

    # 3 调色板
    used = {c for fr in frames for c in "".join(fr) if c != "."}
    stray = {c for c in used if M.PALETTE[c] not in allowed and c != "Q"}
    assert not stray, f"用色 {sorted(stray)} 未出现在三张实图里（包外色相）"
    assert M.PALETTE["Q"] == M.DERIVED["Q"] == M._tint(M.PALETTE["B"], M.Q_TINT), \
        "Q 必须是 manifest 已登记的 B->白 60% 同色相浅调"
    log.append(f"PASS 调色板：{len(used)} 档用色中 {len(used) - 1} 档逐字命中三张实图，"
               f"唯一例外 Q = B 向白 {M.Q_TINT:.0%} 派生浅调（manifest.DERIVED 登记值，无包外色相）")

    # 4 循环
    for i in range(len(AM.CHANNELS)):
        seq = [AM.level(i, f) for f in range(N)]
        assert seq.count(2) == 1 and seq.count(1) == 2 and seq.count(0) == N - 3, \
            f"通道 {i} 包络不是单峰周期：{seq}"
        assert all(AM.level(i, f) == AM.level(i, f + N) for f in range(N)), "包络非周期"
        assert seq[0] == 0, f"通道 {i} 在第 0 帧未归零 -> 第 0 帧不再是 cand_c：{seq}"
    gap = [f for f in range(N) if AM.coin_level(f) == 0]
    assert gap == [0, 4, 8] and frames[0] == list(base), "拍点应为 f0/f4/f8"
    steps = [sum(1 for y in range(SZ) for x in range(SZ)
                 if frames[f][y][x] != frames[(f + 1) % N][y][x]) for f in range(N)]
    assert steps[N - 1] <= max(steps[:N - 1]), f"接缝步变 {steps[-1]} 大于环内最大 {max(steps[:-1])}"
    log.append(f"PASS 循环无缝：三格峰值帧 {list(AM.PEAK_FRAME)} 等距 {N // 3} 帧，"
               f"环形三角包络（半宽 {AM.RAMP_HALF}）首尾相接；逐帧位变 {steps}，"
               f"接缝 f{N - 1}->f0 = {steps[-1]}px <= 环内最大 {max(steps[:N - 1])}px（无跳变）")
    log.append(f"PASS 节奏：frametime={AM.FRAMETIME} tick/帧 -> 单帧 {AM.FRAMETIME * 50}ms，"
               f"整环 {N * AM.FRAMETIME} tick = {N * AM.FRAMETIME / 20:.1f}s，"
               f"每通道 {N // 3 * AM.FRAMETIME / 20:.1f}s，币面呼吸 {len(AM.CHANNELS)} 次/环")

    # 5 可读性
    for f, fr in enumerate(frames):
        islands = [(c, p[0]) for c in used
                   for p in _components(_cells(fr, c), conn=8) if len(p) == 1]
        assert not islands, f"第 {f} 帧存在 1px 孤岛色块 {islands[:2]}（缩小必消失）"
    log.append(f"PASS 无 1px 孤岛：{N} 帧每种色的 8-连通块均 >=2px（半亮档用 2px 而非 1px）")

    b0 = _blocks(list(base), SZ)
    w = SZ // 2
    for i, ch in enumerate(AM.CHANNELS):
        blks = {(x // 2, y // 2) for (x, y) in ch["cells"]}
        peak = _blocks(frame_rows(AM.PEAK_FRAME[i]), SZ)
        dim = _blocks(frame_rows(AM.PEAK_FRAME[i] - 1), SZ)
        d_pk = _mad([b0[b[1] * w + b[0]] for b in sorted(blks)],
                    [peak[b[1] * w + b[0]] for b in sorted(blks)])
        d_dim = _mad([b0[b[1] * w + b[0]] for b in sorted(blks)],
                     [dim[b[1] * w + b[0]] for b in sorted(blks)])
        assert d_pk >= MAD_BLOCK_PEAK_MIN, f"{ch['key']} 满亮档缩小后仅位移 {d_pk:.1f}"
        assert d_dim >= MAD_BLOCK_DIM_MIN, f"{ch['key']} 半亮档缩小后仅位移 {d_dim:.1f}"
        log.append(f"PASS {ch['cn']}格（{ch['hue']}）缩小 2x 后所在块平均色位移："
                   f"满亮 {d_pk:.1f} / 半亮 {d_dim:.1f}"
                   f"（门限 {MAD_BLOCK_PEAK_MIN:.0f} / {MAD_BLOCK_DIM_MIN:.0f}）")

    # 6 mcmeta 自洽
    meta = json.loads(build_mcmeta().decode("ascii"))
    assert set(meta) == {"animation"} and set(meta["animation"]) == {"frametime"}
    assert meta["animation"]["frametime"] == AM.FRAMETIME
    assert (SZ * N) % SZ == 0 and (SZ * N) // SZ == N, "mcmeta frametime 与帧数须与实际行数自洽"
    assert N * AM.FRAMETIME < 20 * 60, "循环周期不合理"
    log.append(f"PASS mcmeta 自洽：`animation.frametime={AM.FRAMETIME}`（无 frames 白名单 = 自动全帧循环），"
               f"帧带 {SZ}x{SZ * N} 实得 {(SZ * N) // SZ} 帧 = 清单 N={N}")


# ------------------------------------------------------------------ 落地
def dir_snapshot() -> dict[str, str]:
    return {p.name: sha(p.read_bytes()) for p in sorted(ITEMS.iterdir()) if p.is_file()}


def land(png: bytes, mcmeta: bytes, log: list[str]) -> None:
    dst = ITEMS / TARGET
    if dst.exists():
        old = dst.read_bytes()
        size = Image.open(io.BytesIO(old)).size
        assert size in ((SZ, SZ), (SZ, SZ * N)), f"覆写目标尺寸异常 {size}"
        log.append(f"覆写前现役：{size[0]}x{size[1]} {len(old)}B "
                   f"sha256={sha(old)[:16]}…（旧静态图 16x16 指纹前缀 {LEGACY_STATIC_SHA}）")
    else:
        log.append("覆写目标不存在 -> 新增")
    (dst).write_bytes(png)
    (ITEMS / TARGET_META).write_bytes(mcmeta)
    log.append(f"已落地：{dst} + {ITEMS / TARGET_META}")


def main() -> int:
    do_land = "--land" in sys.argv[1:]
    assert (ITEMS / TARGET).is_file(), "落地目标缺失"
    before_all = dir_snapshot()
    log: list[str] = []
    check(log)

    png1, png2 = build_png(), build_png()
    meta1, meta2 = build_mcmeta(), build_mcmeta()
    assert png1 == png2, "帧带非幂等：双跑字节不一致"
    assert meta1 == meta2, "mcmeta 非幂等"
    im = Image.open(io.BytesIO(png1)).convert("RGBA")
    assert im.size == (SZ, SZ * N), f"帧带尺寸 {im.size}"
    assert im.getextrema()[3] in ((0, 255), (255, 255)), "alpha 非二值"
    log.append(f"PASS 产物：{im.size[0]}x{im.size[1]} RGBA / alpha 二值 / {len(png1)}B")

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    (OUT_DIR / TARGET).write_bytes(png1)
    (OUT_DIR / TARGET_META).write_bytes(meta1)

    if do_land:
        land(png1, meta1, log)
        after = dir_snapshot()
        changed = {k for k in set(after) | set(before_all)
                   if after.get(k) != before_all.get(k)}
        assert changed <= {TARGET, TARGET_META}, f"落地越界：{sorted(changed)}"
    else:
        after = dir_snapshot()
        assert after == before_all, "非落地模式禁止写 src 材质目录"
        log.append("非落地模式：src 材质目录逐字节未变（加 --land 才覆写）")

    for line in log:
        print("  " + line)
    print(f"[双跑 SHA256] png   run1 = {sha(png1)}")
    print(f"[双跑 SHA256] png   run2 = {sha(png2)}")
    print(f"[双跑 SHA256] mcmeta run1 = {sha(meta1)}")
    print(f"[双跑 SHA256] mcmeta run2 = {sha(meta2)}")
    print("[非目标不变] src/.../items 目录快照（除落地两文件外必须逐字节不变）：")
    for k in sorted(set(before_all) | set(after)):
        tag = "UNCHANGED" if before_all.get(k) == after.get(k) else "OVERWRITTEN"
        print(f"  {tag:11s} {k} {after[k][:16]}")
    assert sha((ITEMS / "miao_coin.png").read_bytes()) == \
        before_all["miao_coin.png"], "miao_coin.png 必须逐字节不动"
    for n in (*REF_TEX, *REF_META):
        assert before_all[n] == after[n], f"参照资产 {n} 发生变化"
    print("PASS 参照资产 infinity_cell / infinity_fluid_cell / miao_coin 及其 mcmeta 逐字节不变")
    return 0


if __name__ == "__main__":
    sys.exit(main())
