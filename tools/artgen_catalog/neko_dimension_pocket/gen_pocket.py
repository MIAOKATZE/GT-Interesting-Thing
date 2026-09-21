"""猫猫次元口袋图标候选的幂等生成器（**只写本目录 out/，绝不写 src**）。

单一权威 = `pocket_manifest`（位图清单 / 跨度表 / 闪光位表 / 状态模型 / frametime）
           + `palette`（色值：实图钉点 + tint/shade 公式）。
本脚本不含字面 RGB、不含随机源；PNG 走 `pngwrite` 手写 IHDR/IDAT/IEND -> 双跑逐字节一致。

用法:
  python -B gen_pocket.py                        # 全判据自检 + 写 out/ + 打印双跑 SHA256
  python -B gen_pocket.py --dump F3              # 只看某家族两版底形的字符图（美术自检）
  python -B gen_pocket.py --land --i-have-authorization   # 实装选中家族（manifest.SELECTED_FAMILY）
  python -B gen_pocket.py --land --i-have-authorization --family=F4   # 改选家族后重新实装

判据分八类：构造 / 轮廓继承 / 只动登记位 / 循环节奏 / 缩小可辨 / 家族互异 / mcmeta 自洽 / 非目标不变。
"""
from __future__ import annotations

import hashlib
import io
import json
import pathlib
import sys

from PIL import Image

import palette as P
import pocket_manifest as PM
import pngwrite

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parents[2]
ITEMS = ROOT / "src" / "main" / "resources" / "assets" / "gtit" / "textures" / "items"
OUT = HERE / "out"

N = PM.FRAMES
D_ON_MIN = 180.0           # 满亮档相对底色的 RGB 总位移（1x 原生纹素下的可辨硬门）
D_DIM_MIN = 90.0           # 半亮档相对底色的 RGB 总位移（等于底色则视为单档闪烁，跳过）
DUST_LUMA_MIN = 140.0      # 体外星（off 为透明）只要求「亮色」，透明度本身就是信号
MIP1_WHOLE_MIN = {"idle": 1.0, "open": 1.0, "work": 1.6, "work_open": 1.6}
MAD_FAMILY_UNION = 110.0   # 家族互异：只按「画到像素并集」求位移（整图口径被透明底稀释 ~6.6x，见 DESIGN.md）
SIL_FAMILY_MIN = 0.12      # 家族互异：剪影 XOR 至少占并集面积 12%
MAD_FAMILY_STRONG = 150.0  # 家族互异：剪影接近时（F1/F2 同轮廓不同画法）改走加重的明度结构门限
MAD_FAMILY_HALF = 24.0     # 家族互异：缩小 2x 后并集位移（mip 兜底）


def sha(b: bytes) -> str:
    return hashlib.sha256(b).hexdigest()


# ------------------------------------------------------------------ 产物
def strip_rows(fam: dict, state: str) -> list[str]:
    out: list[str] = []
    for f in range(N):
        out.extend(PM.frame_rows(fam, state, f))
    return out


def build_png(fam: dict, state: str) -> bytes:
    sz = fam["size"]
    return pngwrite.encode_png(sz, sz * N, _flat(strip_rows(fam, state)))


def build_mcmeta(state: str) -> bytes:
    return (json.dumps({"animation": {"frametime": PM.FRAMETIME[state]}},
                       separators=(",", ":")) + "\n").encode("ascii")


# ------------------------------------------------------------------ 像素工具
def _flat(rows: list[str]) -> bytes:
    buf = bytearray()
    for row in rows:
        for ch in row:
            buf += b"\x00\x00\x00\x00" if ch == "." else bytes((*PAL[PM.INK[ch]], 255))
    return bytes(buf)


def opaque(rows: list[str]) -> set:
    return {(x, y) for y, row in enumerate(rows) for x, c in enumerate(row) if c != "."}


def ring_cells(rows: list[str]) -> set:
    """最外一圈：不透明且 4-邻域触透明/越界。"""
    sz = len(rows)
    out = set()
    for y in range(sz):
        for x in range(sz):
            if rows[y][x] == ".":
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if not (0 <= nx < sz and 0 <= ny < sz) or rows[ny][nx] == ".":
                    out.add((x, y))
                    break
    return out


def components(cells: set, conn: int = 8) -> list[list]:
    nb = ([(1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (1, -1), (-1, 1), (-1, -1)] if conn == 8
          else [(1, 0), (-1, 0), (0, 1), (0, -1)])
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


def _blocks(rows: list[str], size: int) -> list[tuple]:
    """2x2 盒式平均 -> (size/2)^2 个 RGBA（模拟缩小后的最坏情况）。"""
    w = size // 2
    out = []
    for by in range(w):
        for bx in range(w):
            acc = [0, 0, 0, 0]
            for dy in (0, 1):
                for dx in (0, 1):
                    ch = rows[by * 2 + dy][bx * 2 + dx]
                    if ch != ".":
                        acc = [a + v for a, v in zip(acc, (*PAL[PM.INK[ch]], 255))]
            out.append(tuple(v / 4 for v in acc))
    return out


def mad(a: list[tuple], b: list[tuple]) -> float:
    fa = [v for p in a for v in p]
    fb = [v for p in b for v in p]
    assert len(fa) == len(fb)
    return sum(abs(x - y) for x, y in zip(fa, fb)) / len(fa)


def _px(rows: list[str]) -> list[tuple]:
    return [PAL[PM.INK[c]] + (255,) if c != "." else (0, 0, 0, 0) for r in rows for c in r]


def luma(rgb) -> float:
    return 0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2]


def ref_colors() -> set:
    out: set = set()
    for name in P.REF_SHA:
        im = Image.open(ITEMS / name).convert("RGBA")
        out |= {px[:3] for px in im.getdata() if px[3] == 255}
    return out


def load_ref_octagon() -> list[str]:
    """把 infinity_cell.png 读成字符网格（只读，供 F1 轮廓继承判据）。"""
    im = Image.open(ITEMS / "infinity_cell.png").convert("RGBA")
    want = {c: PAL[PM.INK[c]] for c in ("S", "H", "C", "B", "y", "#", "L", "P", "p", "G", "T", "K")}
    inv = {v: k for k, v in want.items()}
    return ["".join("." if im.getpixel((x, y))[3] == 0 else inv.get(im.getpixel((x, y))[:3], "#")
                    for x in range(16)) for y in range(16)]


# ------------------------------------------------------------------ 判据
def check(log: list[str]) -> None:
    # --- 0 调色板
    ref = ref_colors()
    for name, (op, base, prm) in P.DERIV_POINTS.items():
        got = P.tint(PAL[base], prm) if op == "tint" else P.shade(PAL[base], prm)
        assert got == PAL[name], f"派生色 {name} 公式不符"
    derived = set(P.DERIV_POINTS)
    for ch, name in PM.INK.items():
        assert name in PAL, f"色符 {ch!r} 未钉到调色板条目 {name}"
        assert len(ch) == 1, f"色符必须单字符：{ch!r}"
    base_used = {n for n in PM.INK.values() if n not in derived}
    miss = {n: PAL[n] for n in base_used if PAL[n] not in ref}
    assert not miss, f"基色未逐字命中实图：{miss}"
    rgbs = [PAL[n] for n in PM.INK.values()]
    assert len(set(rgbs)) == len(rgbs), "色符存在重复 RGB（别名会掩盖调色板膨胀）"
    unused = {c for c in PM.INK
              if not any(c in "".join(g) for f in PM.FAMILIES
                         for g in f["grid"].values())
              and not any(c in (d + o) for f in PM.FAMILIES
                          for t in f["spark"].values() for (_, _, _, d, o) in t)}
    log.append(f"PASS 调色板：色符 {len(PM.INK)} 档（未被任何清单引用的 {len(unused)} 档："
               f"{sorted(unused)}），基色 {len(base_used)} 档逐字命中 {len(P.REF_SHA)} 张实图，"
               f"派生 {len(derived)} 档全部由 tint/shade 公式重算命中，RGB 互不相同")

    octagon = load_ref_octagon()
    oct_ops, oct_ring = opaque(octagon), ring_cells(octagon)

    for fam in PM.FAMILIES:
        fid, sz = fam["id"], fam["size"]
        peak: dict[str, int] = {}
        # --- 1 构造（底形）
        for key, g in fam["grid"].items():
            assert len(g) == sz, f"{fid}/{key} 行数 {len(g)} != {sz}"
            for y, r in enumerate(g):
                assert len(r) == sz, f"{fid}/{key} 第 {y} 行宽 {len(r)} != {sz}：{r!r}"
                for c in r:
                    assert c == "." or c in PM.INK, f"{fid}/{key} 未登记色符 {c!r}"
            assert all(r[0] == "." and r[-1] == "." for r in g), f"{fid}/{key} 首末列必须全透明"
            assert set(g[sz - 1]) == {"."}, f"{fid}/{key} 最底行必须全透明"
            ops = opaque(g)
            comp = components(ops, conn=4)
            assert len(comp) == 1, f"{fid}/{key} 本体不是 4-连通单组件（{len(comp)} 团）"
            for c in {ch for ch in "".join(g) if ch != "."}:
                isl = [q for q in components({(x, y) for y, r in enumerate(g)
                                              for x, ch in enumerate(r) if ch == c})
                       if len(q) == 1]
                assert not isl, f"{fid}/{key} 色符 {c!r} 有 1px 孤岛 {isl[:3]}（缩小必消失）"
            cols = {PM.INK[ch] for ch in "".join(g) if ch != "."}
            assert 6 <= len(cols) <= 22, f"{fid}/{key} 用色 {len(cols)} 档越界"
            assert N * sz == len(strip_rows(fam, "idle")), "帧带高度必须 = 帧数 x 帧宽"
        # --- 2 轮廓继承（F1 硬要求；其余家族只要求同族可辨）
        if fid == "f1_family_inherit":
            ops = opaque(fam["grid"]["normal"])
            assert ops == oct_ops, \
                f"F1 轮廓未逐字继承 infinity_cell（差 {len(ops ^ oct_ops)} px）"
            ring = ring_cells(fam["grid"]["normal"])
            assert ring == oct_ring
            same = sum(1 for (x, y) in ring if fam["grid"]["normal"][y][x] == "S")
            assert same == len(ring), f"F1 外圈仅 {same}/{len(ring)} 位为家族主灰"
            log.append(f"PASS F1 轮廓继承：alpha 团与 infinity_cell 逐位相同（{len(ops)} px = "
                       f"138 同值），最外一圈 {len(ring)} px 逐字 = 主灰 #282828；"
                       f"内层受光仍走 hig、背光仍走 dsh（明度层级链不动）")
            for st in PM.STATES:                      # 动画永不改外壳
                cells = {(x, y) for (x, y, _, _, _) in fam["spark"][st]}
                assert not (cells & ring), \
                    f"F1 {st} 有闪光位落在继承外圈上：{sorted(cells & ring)[:4]}"
            log.append(f"PASS F1 继承外圈不可侵犯：4 态共 "
                       f"{sum(len(f['spark'][s]) for s in PM.STATES for f in [fam])} 个闪光位"
                       f"与外圈 {len(ring)} px 交集为空")
        # --- 3 打开态与本体是同一只袋子
        a, b = opaque(fam["grid"]["normal"]), opaque(fam["grid"]["open"])
        iou = len(a & b) / len(a | b)
        assert 0.55 <= iou <= 0.99, f"{fid} 开口态与闭口态位重合 {iou:.2f} 不在 [0.55,0.99]"
        # --- 4/5/6/7 帧级判据
        for st in PM.STATES:
            base = PM.grid_of(fam, st)
            base_ops = opaque(base)
            table = fam["spark"][st]
            sparks = {(x, y): (dim, on) for (x, y, _, dim, on) in table}
            for (x, y), (dim, on) in sparks.items():
                assert dim != on, f"{fid}/{st} ({x},{y}) 半亮与满亮同字符"
                assert dim in PM.INK or dim == ".", f"{fid}/{st} 半亮档色符非法 {dim!r}"
                assert on in PM.INK, f"{fid}/{st} 满亮档色符非法 {on!r}"
            frames = [PM.frame_rows(fam, st, f) for f in range(N)]
            for f, fr in enumerate(frames):
                fo = opaque(fr)
                assert base_ops <= fo, f"{fid}/{st} 第 {f} 帧本体被削掉位"
                extra = fo - base_ops
                assert extra <= set(sparks), \
                    f"{fid}/{st} 第 {f} 帧新增位未登记：{sorted(extra - set(sparks))[:3]}"
                for y, r in enumerate(fr):
                    for x, c in enumerate(r):
                        if (x, y) not in sparks:
                            assert c == base[y][x], f"{fid}/{st} 第 {f} 帧动了未登记位 ({x},{y})"
                        else:
                            assert c in (base[y][x], *sparks[(x, y)]), \
                                f"{fid}/{st} 第 {f} 帧 ({x},{y}) 出现表外色符 {c!r}"
                for (x, y) in extra:
                    assert not (x in (0, sz - 1) or y in (0, sz - 1)), \
                        f"{fid}/{st} 第 {f} 帧体外星贴画布边 ({x},{y})"
            steps = [sum(1 for y in range(sz) for x in range(sz)
                         if frames[f][y][x] != frames[(f + 1) % N][y][x]) for f in range(N)]
            assert steps[N - 1] <= max(steps[:N - 1]), \
                f"{fid}/{st} 接缝步变 {steps[-1]} > 环内最大 {max(steps[:-1])}"
            lit = [sum(1 for (x, y) in sparks if frames[f][y][x] != base[y][x]) for f in range(N)]
            assert min(lit) >= 2, f"{fid}/{st} 有帧几乎不亮：{lit}"
            assert max(lit) >= 4, f"{fid}/{st} 峰值太弱：{lit}"
            # 工作态必须比非工作态更闪（需求：「闪光更多」）
            peak[st] = max(lit)
            worst_on, worst_dim = 999.0, 999.0
            for (x, y), (dim, on) in sparks.items():
                b = base[y][x]
                assert on != b, f"{fid}/{st} 位 ({x},{y}) 满亮档与底色同值 -> 永不动"
                if b == ".":
                    d_on, d_dm = luma(PAL[PM.INK[on]]), 999.0
                    if dim != ".":
                        d_dm = luma(PAL[PM.INK[dim]])
                    assert d_on >= DUST_LUMA_MIN, \
                                        f"{fid}/{st} ({x},{y}) 体外星太暗 {d_on:.0f} < {DUST_LUMA_MIN}"
                    assert d_dm >= DUST_LUMA_MIN, \
                                        f"{fid}/{st} ({x},{y}) 半亮星太暗 {d_dm:.0f} < {DUST_LUMA_MIN}"
                else:
                    d_on = sum(abs(i - j) for i, j in zip(PAL[PM.INK[b]], PAL[PM.INK[on]]))
                    d_dm = sum(abs(i - j) for i, j in zip(PAL[PM.INK[b]], PAL[PM.INK[dim]]))
                    assert d_on >= D_ON_MIN, \
                                        f"{fid}/{st} ({x},{y}) {b}->{on} 满亮位移 {d_on} < {D_ON_MIN:.0f}"
                    if dim != b:
                        assert d_dm >= D_DIM_MIN, \
                                            f"{fid}/{st} ({x},{y}) {b}->{dim} 半亮位移 {d_dm} < {D_DIM_MIN:.0f}"
                worst_on, worst_dim = min(worst_on, d_on), min(worst_dim, d_dm)
            b0 = _blocks(base, sz)
            pk_f = max(range(N), key=lambda f: lit[f])
            mip1 = mad(b0, _blocks(frames[pk_f], sz))
            assert mip1 >= MIP1_WHOLE_MIN[st], f"{fid}/{st} mip1 整图位移 {mip1:.2f} 太小"
            meta = json.loads(build_mcmeta(st).decode("ascii"))
            assert list(meta) == ["animation"] and list(meta["animation"]) == ["frametime"], \
                f"{fid}/{st} mcmeta 键不合规（1.7.10 只认 animation.frametime，" \
                    f"interpolate/width/height 是死键）"
            ft = PM.FRAMETIME[st]
            assert (sz * N) % sz == 0 and (sz * N) // sz == N
            assert N * ft < 20 * 30, "循环不得超过 30 秒"
            log.append(f"PASS {fid} {st:9s} {sz}x{sz * N} = {N} 帧竖排；本体 {len(base_ops)} px "
                       f"全帧逐位不变，可变位 {len(sparks)}；逐帧位变 {steps}"
                       f"（接缝 {steps[-1]} <= 环内最大 {max(steps[:-1])}）；"
                       f"同刻亮位数 {min(lit)}~{max(lit)}；单像素最小位移 满 {worst_on:.0f}"
                       f" / 半 {worst_dim:.0f}（门限 {D_ON_MIN:.0f}/{D_DIM_MIN:.0f}）；"
                       f"mip1 整图位移 {mip1:.2f}（门限 {MIP1_WHOLE_MIN[st]}）；"
                       f"frametime={ft} -> 整环 {N * ft / 20:.1f}s")
        # --- 需求：工作中「闪光更多」
        assert peak["work"] > peak["idle"] and peak["work_open"] > peak["open"], \
            f"{fid} 工作态未比非工作态更闪：{peak}"
        assert peak["work_open"] > peak["work"], f"{fid} 开口工作态应最闪：{peak}"
        log.append(f"PASS {fid} 强度阶梯：同刻亮位数 闭口 {peak['idle']} -> 闭口工作 {peak['work']}，"
                   f"开口 {peak['open']} -> 开口工作 {peak['work_open']}（工作态严格更多，"
                   f"且整环更快：{N * PM.FRAMETIME['idle']} vs {N * PM.FRAMETIME['work_open']} tick）")

    # --- 8 家族互异
    ids = [f["id"] for f in PM.FAMILIES]
    for i in range(len(ids)):
        for j in range(i + 1, len(ids)):
            fa = next(f for f in PM.FAMILIES if f["id"] == ids[i])
            fb = next(f for f in PM.FAMILIES if f["id"] == ids[j])
            ra, rb = _to32(fa["grid"]["normal"]), _to32(fb["grid"]["normal"])
            union = mad_union(ra, rb)          # 原尺寸：只算画到像素
            small = mad(_blocks(ra, 32), _blocks(rb, 32))   # 缩小 2x：整图口径（此时已基本无空底）
            whole = mad(_px(ra), _px(rb))
            sa, sb = opaque(ra), opaque(rb)
            sil = len(sa ^ sb) / max(1, len(sa | sb))
            assert union >= MAD_FAMILY_UNION, \
                f"家族 {ids[i]}/{ids[j]} 画到像素位移不足：并集 {union:.1f} < {MAD_FAMILY_UNION:.0f}"
            assert sil >= SIL_FAMILY_MIN or union >= MAD_FAMILY_STRONG, \
                f"家族 {ids[i]}/{ids[j]} 既未换剪影（{sil:.2f} < {SIL_FAMILY_MIN:.2f}）" \
                f"也未换明度结构（{union:.1f} < {MAD_FAMILY_STRONG:.0f}）—— 等于同一版"
            assert small >= MAD_FAMILY_HALF, \
                f"家族 {ids[i]}/{ids[j]} 缩小后位移 {small:.1f} < {MAD_FAMILY_HALF:.0f}"
            log.append(f"PASS 家族互异 {fa['tag']} vs {fb['tag']}：并集位移 {union:.1f}"
                       f"（缩小后 {small:.1f}）/ 剪影差异 {sil:.0%}；整图口径 {whole:.1f} 仅参考"
                       f"——画到像素只占画布 {len(sa | sb) / 1024:.0%}，"
                       f"整图口径被透明底稀释 {union / max(1e-9, whole):.1f}x，故不作门限")


def mad_union(a: list[str], b: list[str]) -> float:
    """只统计「至少一方画到像素」的位点的平均 RGBA 位移。

    整图 MAD（mad(_px(...))）把透明背景也算进分母：16px 图标在 32x32 比对画布上只占
    ~15% 面积，于是同一批家族的整图值被稀释约 6-7 倍（实测 F1/F2 并集 161.1 -> 整图 24.5），
    该口径下 35 的门限对「轮廓刻意继承同族」的候选数学上不可达。故互异门限走并集口径，
    整图口径仍计算并打印，只是不作断言。
    """
    pa, pb = _px(a), _px(b)
    idx = [i for i in range(len(pa)) if pa[i][3] or pb[i][3]]
    assert idx, "两张底形全透明"
    return sum(sum(abs(u - v) for u, v in zip(pa[i], pb[i])) for i in idx) / len(idx)


def _to32(rows: list[str]) -> list[str]:
    """16px 底形按最近邻 2x 放到 32，与 32px 家族同口径比较。"""
    n = len(rows)
    k = 32 // n
    out = []
    for r in rows:
        line = "".join(c * k for c in r)
        out.extend([line] * k)
    return out


# ------------------------------------------------------------------ 落地 / 产物
def snapshot(d: pathlib.Path) -> dict:
    return {p.name: sha(p.read_bytes()) for p in sorted(d.iterdir()) if p.is_file()}


def emit() -> list[tuple[str, str, str]]:
    OUT.mkdir(parents=True, exist_ok=True)
    rows = []
    for fam in PM.FAMILIES:
        for st in PM.STATES:
            name = PM.candidate_name(fam["id"], st)
            png1, png2 = build_png(fam, st), build_png(fam, st)
            m1, m2 = build_mcmeta(st), build_mcmeta(st)
            assert png1 == png2, f"{name} 帧带非幂等"
            assert m1 == m2, f"{name} mcmeta 非幂等"
            im = Image.open(io.BytesIO(png1)).convert("RGBA")
            sz = fam["size"]
            assert im.size == (sz, sz * N), f"{name} 尺寸 {im.size}"
            assert im.getextrema()[3] in ((0, 255), (255, 255)), f"{name} alpha 非二值"
            (OUT / f"{name}.png").write_bytes(png1)
            (OUT / f"{name}.png.mcmeta").write_bytes(m1)
            rows.append((f"{name}.png", sha(png1), f"{im.size[0]}x{im.size[1]} {len(png1)}B"))
    return rows


def land(log: list[str], only_family: str | None = None) -> None:
    """实装（本任务未授权；主代理选定家族后带双旗标运行）。"""
    before = snapshot(ITEMS)
    fams = [f for f in PM.FAMILIES if only_family is None or f["id"] == only_family]
    mine = set()
    for fam in fams:
        for st in PM.STATES:
            name = PM.land_name(st)
            mine |= {f"{name}.png", f"{name}.png.mcmeta"}
            (ITEMS / f"{name}.png").write_bytes(build_png(fam, st))
            (ITEMS / f"{name}.png.mcmeta").write_bytes(build_mcmeta(st))
    after = snapshot(ITEMS)
    changed = {k for k in set(after) | set(before) if after.get(k) != before.get(k)}
    assert changed <= mine, f"落地越界：{sorted(changed - mine)}"
    for k, v in before.items():
        assert after.get(k) == v, f"既有资产 {k} 被改动"
    log.append(f"已落地 {len(mine)} 个文件；既有 {len(before)} 文件逐字节未变")


def dump(tag: str) -> int:
    fam = next(f for f in PM.FAMILIES if f["id"] == tag or f["tag"] == tag.upper())
    for key in ("normal", "open"):
        print(f"### {fam['id']} 底形 {key}")
        for y, r in enumerate(fam["grid"][key]):
            print(f"{y:2d} {r}")
    for st in PM.STATES:
        base = PM.grid_of(fam, st)
        cnt = []
        for f in range(N):
            fr = PM.frame_rows(fam, st, f)
            cnt.append(sum(1 for y in range(fam["size"]) for x in range(fam["size"])
                           if fr[y][x] != base[y][x]))
        print(f"{fam['id']} {st:9s} 逐帧相对底形变化位 = {cnt}")
    print("spark 表：")
    for st in PM.STATES:
        print("  ", st, fam["spark"][st])
    return 0


def main() -> int:
    args = sys.argv[1:]
    if "--dump" in args:
        return dump(args[args.index("--dump") + 1])

    bad = P.check_sources(ITEMS)
    assert not bad, "来源贴图基线漂移（本任务全程只读）：\n  " + "\n  ".join(bad)
    before_all = snapshot(ITEMS)
    log: list[str] = []
    check(log)
    rows = emit()

    if "--land" in args:
        assert "--i-have-authorization" in args, \
            "落地需 --land 与 --i-have-authorization 双旗标（本任务未授权落盘）"
        # 落地只写选中家族（R50 = F2）：四家族 land_name() 相同，全落会互相覆盖成最后一套。
        sel = next((a.split("=", 1)[1] for a in args if a.startswith("--family=")),
                   PM.SELECTED_FAMILY)
        land(log, only_family=PM.family(sel)["id"])
    else:
        after = snapshot(ITEMS)
        assert after == before_all, "非落地模式禁止写 src 材质目录"
        log.append("非落地模式：src/.../items 目录逐字节未变（候选只写 out/）")
    after = snapshot(ITEMS)

    for line in log:
        print("  " + line)
    print(f"[双跑 SHA256] {len(rows)} 张帧带 + 同数 mcmeta（每张均已就地重算并断言字节相等）：")
    for name, s, info in rows:
        print(f"  {name:44s} {s}  {info}")
    diff = sum(1 for k in set(before_all) | set(after) if before_all.get(k) != after.get(k))
    print(f"[非目标不变] src/.../items {len(before_all)} 文件，本次变化数 = {diff}")
    print("PASS 只读资产（infinity_cell / infinity_fluid_cell / miao_coin / neko_infinity_unit / "
          "reincarnation_crystal / 两枚指环 / electric_float_core）逐字节未变")
    return 0


PAL, _PROV = P.build_palette(ITEMS)

if __name__ == "__main__":
    sys.exit(main())
