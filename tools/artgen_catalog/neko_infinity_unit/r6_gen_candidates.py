"""R6 生成器：三套「猫猫无限存储单元」动画候选（16x128，8 帧）+ 自校验断言。

管线：读基准 `infinity_cell.png` -> 冻结轮廓集 -> 按角色改色（只动内部像素）->
中心暗腔盖猫脸像素表 -> 三枚角标按帧点亮 -> 确定性 PNG 直写。
权威全在 `r6_manifest.py`；本文件不含字面 RGB 与形状魔数。

用法:  python r6_gen_candidates.py            # 生成 + 断言 + 报告 + 双跑 SHA 自检
       python r6_gen_candidates.py --check    # 只跑断言，不落盘
"""
from __future__ import annotations

import copy
import hashlib
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
sys.path.insert(0, str(HERE))

import r6_manifest as M                                    # noqa: E402
from pngwrite import encode_png                            # noqa: E402

OUT_DIR = HERE / "candidates"


# ---------------------------------------------------------------- 组装

def resolve(scheme, pal):
    """把清单里的 (钉点, k) 解析成 RGB + 溯源说明。"""
    roles, notes = {}, {}
    for slot, (ref, k) in scheme["roles"].items():
        rgb, note = M.shade(pal, ref, k)
        roles["L_" + slot] = rgb
        notes["L_" + slot] = note
    for slot, (ref, k) in scheme["face"].items():
        rgb, note = M.shade(pal, ref, k)
        roles["F_" + slot] = rgb
        notes["F_" + slot] = note
    for grp, (ref, kl, kd) in scheme["markers"].items():
        for state, k in (("lit", kl), ("dim", kd)):
            rgb, note = M.shade(pal, ref, k)
            roles["M_%s_%s" % (grp, state)] = rgb
            notes["M_%s_%s" % (grp, state)] = note
    for slot, ref in M.FROZEN_NEUTRALS.items():          # 中性边缘层：锁死为基准字节
        roles["L_" + slot] = pal[ref]
        notes["L_" + slot] = "%s = 基准值（边缘层锁死）" % ref
    for lvl, k in enumerate(M.BREATH_STEPS):
        for slot in ("hi", "mid"):
            rgb, note = M.shade(pal, scheme["face"][slot][0], k)
            roles["B%d_%s" % (lvl, slot)] = rgb
            notes["B%d_%s" % (lvl, slot)] = note
    return roles, notes


# 基准 RGB -> 可改色的结构层角色（灰阶中性层与轮廓像素都不在这张表里）
BASE_ROLE_OF = {"L_band": (0, 162, 232), "L_cavity": (0, 0, 0)}
FACE_SLOT_BASE = {"hi": (255, 242, 0), "mid": (255, 242, 0)}


def build_frame(scheme, roles, base, contour, fi):
    grid = copy.deepcopy(base)
    face_mid = {(x, y) for (x, y), s in M.FACE_PIXELS.items()}
    marker_px = {p for grp in M.MARKER_GROUPS.values() for p in grp}
    # 1) 结构层改色（跳过轮廓与后面要覆盖的猫脸/角标位）
    for y in range(16):
        for x in range(16):
            p = (x, y)
            if grid[y][x][3] == 0:                             # 透明留白逐位保留
                continue
            if p in contour or p in face_mid or p in marker_px:
                continue
            rgb = grid[y][x][:3]
            for role, want in BASE_ROLE_OF.items():
                if rgb == want:
                    grid[y][x] = roles[role] + (255,)
                    break
    # 2) 中心猫脸（呼吸档位只缩明亮两档金，位置固定）
    lvl = M.BREATH_PLAN[fi]
    for (x, y), slot in M.FACE_PIXELS.items():
        if slot in ("hi", "mid"):
            grid[y][x] = roles["B%d_%s" % (lvl, slot)] + (255,)
        else:
            grid[y][x] = roles["F_" + slot] + (255,)
    # 3) 三通道角标顺次点亮
    for grp, (f0, f1) in M.CHANNEL_PLAN.items():
        state = "lit" if f0 <= fi <= f1 else "dim"
        for (x, y) in M.MARKER_GROUPS[grp]:
            grid[y][x] = roles["M_%s_%s" % (grp, state)] + (255,)
    return grid


def to_bytes(frames):
    buf = bytearray()
    for g in frames:
        for y in range(16):
            for x in range(16):
                buf += bytes(g[y][x])
    return encode_png(16, 16 * len(frames), bytes(buf))


# ---------------------------------------------------------------- 度量

def lum_arr(grid):
    return [grid[y][x][3] / 255.0 * M.luma(grid[y][x][:3])
            for y in range(16) for x in range(16)]


def mad_minify(grid, n):
    a = lum_arr(grid)
    small = []
    for sy in range(16 // n):
        for sx in range(16 // n):
            blk = [a[(sy * n + j) * 16 + sx * n + i] for j in range(n) for i in range(n)]
            small.append(sum(blk) / len(blk))
    return sum(abs(a[y * 16 + x] - small[(y // n) * (16 // n) + x // n])
               for y in range(16) for x in range(16)) / 256.0


def mad_detail(grid):
    a = lum_arr(grid)
    diffs = [abs(a[y * 16 + x] - a[yy * 16 + xx])
             for y in range(16) for x in range(16)
             for yy, xx in ((y + 1, x), (y, x + 1))
             if yy < 16 and xx < 16 and grid[y][x][3] and grid[yy][xx][3]]
    return sum(diffs) / max(1, len(diffs))


def diff_px(a, b):
    return sum(1 for y in range(16) for x in range(16) if a[y][x] != b[y][x])


# ---------------------------------------------------------------- 断言

def assert_shape(base, contour):
    """清单坐标合法性：猫脸只覆盖暗腔/高光（耳尖允许顶破内圈 <=2 px）、角标只覆盖基准角标位、
    两者都不得是轮廓像素（4 邻域与 8 邻域口径都要守住）。"""
    ear = set(M.EAR_BREAK_THROUGH)
    assert len(ear) <= M.GATE_EAR_BREAK_MAX, "耳尖顶破预算超 2 px"
    xs = [x for x, _ in M.FACE_PIXELS]
    ys = [y for _, y in M.FACE_PIXELS]
    w, h = max(xs) - min(xs) + 1, max(ys) - min(ys) + 1
    assert 6 <= w <= 8 and 6 <= h <= 8, ("猫脸包围盒超出 6x6~8x8 预算", w, h)
    assert (min(xs) + max(xs)) / 2.0 == 7.5, ("猫脸水平不居中", min(xs), max(xs))
    assert min(ys) >= 3 and max(ys) <= 12, ("猫脸纵向越出中心区", min(ys), max(ys))
    for (x, y), _ in M.FACE_PIXELS.items():
        p = (x, y)
        assert p not in contour, ("猫脸压到轮廓", x, y)
        if p in ear:
            assert base[y][x][:3] == M.EAR_BREAK_BASE, ("耳尖位不是青带", x, y, base[y][x])
            continue
        assert base[y][x][:3] in M.FACE_BASE_OK, ("猫脸越界", x, y, base[y][x])
    for p in M.FACE_KEEP_DARK:
        assert base[p[1]][p[0]][:3] == (0, 0, 0), p
        assert p not in M.FACE_PIXELS, ("留黑位与猫脸重叠", p)
    for grp, pts in M.MARKER_GROUPS.items():
        assert len(pts) >= 2, grp
        for (x, y) in pts:
            assert base[y][x][:3] in M.MARKER_BASE_OK, ("角标越界", grp, x, y, base[y][x])
            assert (x, y) not in contour, ("角标压到轮廓", grp, x, y)
    e_px = {(x, y) for y in range(16) for x in range(16) if base[y][x][:3] == (255, 242, 0)}
    assert e_px <= set(M.FACE_PIXELS), ("基准高光像素未被猫脸完整承接", e_px - set(M.FACE_PIXELS))
    acc = {(x, y) for y in range(16) for x in range(16)
           if base[y][x][:3] in {(197, 21, 201), (138, 31, 140), (95, 28, 97), (25, 255, 0)}}
    assert acc <= {p for pts in M.MARKER_GROUPS.values() for p in pts}, ("角标位漏登记", acc)
    return (min(x for x, _ in M.FACE_PIXELS), min(y for _, y in M.FACE_PIXELS), w, h)


def assert_hierarchy(roles, base, pal):
    L = M.luma
    chain = {"cavity": roles["L_cavity"], "shell": roles["L_shell"],
             "outline": pal["cell_outline"], "bevel": pal["cell_bevel"],
             "band": roles["L_band"], "face_mid": roles["F_mid"], "face_hi": roles["F_hi"]}
    seq = [(r, chain[r]) for r in M.LAYER_CHAIN]
    for (r1, c1), (r2, c2) in zip(seq, seq[1:]):
        assert L(c1) < L(c2), ("明度层级越序", r1, L(c1), r2, L(c2))
    return {r: round(L(c), 1) for r, c in seq}


def assert_scheme_gates(scheme, roles, base):
    out = []
    L = M.luma
    for grp in M.MARKER_GROUPS:
        lit, dim = roles["M_%s_lit" % grp], roles["M_%s_dim" % grp]
        d = L(lit) - L(dim)
        assert d >= M.GATE_BLINK_DL, ("点亮对比不足", grp, round(d, 1))
        assert L(roles["L_shell"]) < L(lit) < L(roles["F_hi"]), ("角标越出层间", grp)
        out.append((grp, round(L(lit), 1), round(L(dim), 1)))
    if scheme["hier_pair_gate"]:
        cols = [roles["M_%s_lit" % g] for g in M.MARKER_GROUPS]
        for i in range(len(cols)):
            for j in range(i + 1, len(cols)):
                dd = M.rgb_dist(cols[i], cols[j])
                assert dd >= M.GATE_HIER_PAIR_MIN, ("三色不可分", i, j, round(dd, 1))
    # 眼睛/小鼻至少三边被面部金包住，1x 下才不会与暗腔连成一片
    for (x, y), s in M.FACE_PIXELS.items():
        if s not in ("eye", "nose"):
            continue
        gold = sum(1 for p in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1))
                   if M.FACE_PIXELS.get(p) in ("hi", "mid"))
        assert gold >= 3, ("暗部贴边易糊", x, y, gold)
    return out


# ---------------------------------------------------------------- 主流程

def generate(check_only: bool):
    root = ROOT / M.ITEM_DIR
    bad_before = M.check_sources(root)
    assert not bad_before, ("基准/现役图字节被改动", bad_before)

    base = M.load_base(root)
    pal = M.load_palette(root)
    contour4 = M.contour_set(base)
    contour8 = M.contour_set(base, ring8=True)
    c8 = set(M.CENTER_BOX)
    face_bbox = assert_shape(base, contour4)
    assert contour4 <= contour8

    from PIL import Image
    v2 = Image.open(root / "neko_infinity_unit.png").convert("RGBA")
    assert v2.size[0] == 16
    shipped = [[v2.getpixel((x, y)) for x in range(16)] for y in range(16)]

    ref_metrics = {
        "infinity_cell": {"mad_min2": round(mad_minify(base, 2), 2),
                          "mad_min4": round(mad_minify(base, 4), 2),
                          "mad_detail": round(mad_detail(base), 2)},
        "shipped_v2_frame0": {"mad_min2": round(mad_minify(shipped, 2), 2),
                              "mad_min4": round(mad_minify(shipped, 4), 2),
                              "mad_detail": round(mad_detail(shipped), 2)},
    }
    cap = 1.15

    report = {"gate_contour_overlap_pct": M.GATE_CONTOUR,
              "gates": {"contour_overlap_min_pct": M.GATE_CONTOUR,
                        "silhouette_deviation_max_px": 2,
                        "ear_break_through_max_px": M.GATE_EAR_BREAK_MAX,
                        "marker_blink_min_dl": M.GATE_BLINK_DL,
                        "hier_pair_min_rgb_dist_s3": M.GATE_HIER_PAIR_MIN,
                        "mad_max_ratio_vs_base": cap,
                        "layer_chain_luma_strictly_increasing": list(M.LAYER_CHAIN)},
              "unverified": [
                  "游戏内实机渲染（1.7.10 物品图集 + mcmeta 动画）未采集：需主代理在挑定并落地后实机核验",
                  "NEI/GUI 槽内与 infinity_cell / infinity_fluid_cell 同框观感仅在预览板确认，未在游戏内确认",
                  "frametime=4 tick 的动画速度是否合意，需用户在实机或 GIF 上判定（板面为静态逐帧排布）",
                  "候选未落地：src 下现役 neko_infinity_unit.png 与其 mcmeta 字节保持原 SHA 未动",
              ],
              "assertions_passed": [
                  "基准/现役图前后 SHA256 不变（三张基准 + 现役 png + mcmeta）",
                  "轮廓集（4 邻域最外一圈 32px）逐位含色重合 100%，且每一帧都不改轮廓",
                  "剪影 alpha 逐位一致（偏离 0 px，门限 <=2）",
                  "猫脸只覆盖基准暗腔/高光像素，唯一例外 = 耳尖 2px 顶破内圈青带（已登记）",
                  "明度层级链 cavity<shell<outline<bevel<band<face_mid<face_hi 严格递增",
                  "角标亮/暗态明度差 >=25；三色并存方案三枚通道色两两 RGB 距离 >=24",
                  "循环接缝位变 <= 环内最大相邻位变",
                  "MAD(缩小 2x/4x 回原 + 相邻明度差) 不劣于基准图 1.15 倍以上",
                  "alpha 二值（0/255）；不透明像素数 = 基准 138",
                  "全部颜色 = 实图钉点 x 亮度系数(k<=1)，无包外色相、脚本无字面 RGB",
              ],
              "frametime_ticks": M.FRAMETIME, "frames": M.FRAMES,
              "loop_seconds": M.LOOP_SECONDS, "mcmeta": M.MCMETA,
              "reference_metrics": ref_metrics, "schemes": []}

    for scheme in M.SCHEMES:
        roles, notes = resolve(scheme, pal)
        hier = assert_hierarchy(roles, base, pal)
        blinks = assert_scheme_gates(scheme, roles, base)
        frames = [build_frame(scheme, roles, base, contour4, fi)
                  for fi in range(M.FRAMES)]
        f0 = frames[0]

        # 轮廓逐位（含色）重合
        hit = sum(1 for p in contour4 if f0[p[1]][p[0]] == base[p[1]][p[0]])
        ov4 = 100.0 * hit / len(contour4)
        hit8 = sum(1 for p in contour8 if f0[p[1]][p[0]] == base[p[1]][p[0]])
        ov8 = 100.0 * hit8 / len(contour8)
        assert ov4 >= M.GATE_CONTOUR and ov8 >= M.GATE_CONTOUR, (ov4, ov8)
        for fi, g in enumerate(frames):
            assert all(g[p[1]][p[0]] == base[p[1]][p[0]] for p in contour4), ("帧内轮廓被改", fi)
        # 剪影逐位一致：不透明集合与基准完全相同 -> 偏离 0 px（门限允许 <=2）
        dev = [(x, y) for y in range(16) for x in range(16)
               if (f0[y][x][3] > 0) != (base[y][x][3] > 0)]
        assert len(dev) <= 2, ("剪影偏离过大", dev)
        mis8 = sorted((p, base[p[1]][p[0]][:3], f0[p[1]][p[0]][:3])
                      for p in contour8 if f0[p[1]][p[0]] != base[p[1]][p[0]])

        changed = {(x, y) for y in range(16) for x in range(16) if f0[y][x] != base[y][x]}
        out_c = sum(1 for p in changed if p not in c8)
        in_c = len(changed) - out_c
        max_out = max(sum(1 for y in range(16) for x in range(16)
                          if g[y][x] != base[y][x] and (x, y) not in c8) for g in frames)

        steps = [diff_px(frames[i], frames[(i + 1) % M.FRAMES]) for i in range(M.FRAMES)]
        seam, worst = steps[-1], max(steps[:-1])
        assert seam <= worst, ("循环接缝位变过大", seam, worst)

        m = {"mad_min2": mad_minify(f0, 2), "mad_min4": mad_minify(f0, 4),
             "mad_detail": mad_detail(f0)}
        for key in m:
            m[key] = round(m[key], 2)
            assert m[key] <= ref_metrics["infinity_cell"][key] * cap, (
                "1x 可读性劣于基准图", key, m[key], ref_metrics["infinity_cell"][key])

        n_colors = len({f0[y][x][:3] for y in range(16) for x in range(16)})
        alpha_bin = {f0[y][x][3] for y in range(16) for x in range(16)}
        assert alpha_bin <= {0, 255}, alpha_bin
        png = to_bytes(frames)

        entry = {
            "id": scheme["id"], "label": scheme["label"], "note": scheme["note"],
            "size": [16, 16 * M.FRAMES],
            "sha256_strip": hashlib.sha256(png).hexdigest(),
            "sha256_frame0_static": hashlib.sha256(to_bytes([f0])).hexdigest(),
            "contour_overlap_pct_4nb": round(ov4, 1),
            "contour_overlap_pct_8nb": round(ov8, 1),
            "contour_mismatch_8nb": [[list(p), list(a), list(b)] for p, a, b in mis8],
            "silhouette_deviation_px": len(dev),
            "ear_break_through_px": len(M.EAR_BREAK_THROUGH),
            "ear_break_through_coords": [list(p) for p in sorted(M.EAR_BREAK_THROUGH)],
            "face_bbox_x_y_w_h": list(face_bbox),
            "face_px": len(M.FACE_PIXELS),
            "contour_px": len(contour4),
            "changed_px_inside_center8": in_c,
            "changed_px_outside_center8_frame0": out_c,
            "changed_px_outside_center8_max_over_frames": max_out,
            "opaque_px": sum(1 for y in range(16) for x in range(16) if f0[y][x][3]),
            "distinct_colors_frame0": n_colors,
            "mad": m, "mad_ref": ref_metrics["infinity_cell"],
            "layer_luma": hier, "marker_luma": blinks,
            "frame_diff_steps": steps, "seam_vs_max_adjacent": [seam, worst],
            "palette_provenance": {k: v for k, v in sorted(notes.items())},
        }
        report["schemes"].append(entry)
        if not check_only:
            OUT_DIR.mkdir(parents=True, exist_ok=True)
            (OUT_DIR / (scheme["id"] + ".png")).write_bytes(png)
            (OUT_DIR / (scheme["id"] + ".png.mcmeta")).write_text(
                json.dumps(M.MCMETA, separators=(",", ":")), encoding="utf-8")
            (OUT_DIR / (scheme["id"] + "_frame0.png")).write_bytes(to_bytes([f0]))
            (OUT_DIR / (scheme["id"] + "_report.json")).write_text(
                json.dumps(entry, ensure_ascii=False, indent=1, sort_keys=True), encoding="utf-8")

    if not check_only:
        (OUT_DIR / "r6_report.json").write_text(
            json.dumps(report, ensure_ascii=False, indent=1, sort_keys=True), encoding="utf-8")

    bad_after = M.check_sources(root)
    assert not bad_after, ("生成过程改动了基准/现役图字节", bad_after)
    return report


if __name__ == "__main__":
    rep = generate("--check" in sys.argv)
    for s in rep["schemes"]:
        print("%-24s contour4=%.1f%% contour8=%.1f%% out_center=%d in_center=%d mad=%s seam=%s"
              % (s["id"], s["contour_overlap_pct_4nb"], s["contour_overlap_pct_8nb"],
                 s["changed_px_outside_center8_frame0"], s["changed_px_inside_center8"],
                 s["mad"], s["seam_vs_max_adjacent"]))
    print("REF", rep["reference_metrics"]["infinity_cell"], "V2",
          rep["reference_metrics"]["shipped_v2_frame0"])
    print("OK", "frames=%d frametime=%d loop=%.1fs" % (rep["frames"], rep["frametime_ticks"],
                                                       rep["loop_seconds"]))
