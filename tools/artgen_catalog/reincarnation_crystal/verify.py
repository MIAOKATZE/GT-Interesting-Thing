#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""gtit 轮回水晶候选集 · 自证脚本

验收项（任务包）：
1) 每个生成脚本双跑 SHA256 逐文件一致（幂等证据）；
2) 不透明像素断言非空 + 套间互异（候选套 base 帧两两不同）；
   动画款追加帧间互异（动画非退化为静帧；FINAL 24 帧逐帧旋转）；
3) 动画款 mcmeta 合规清单：帧宽 POT、高=帧数x宽精确整除、
   frametime/frames[] 序列、无 interpolate/width/height 死键、index 越界检查；
4) 每套一行设计语言摘要（供 viewer 比选 AUQ）；
5) FINAL 落地款：正式资产路径回读，png/mcmeta 与 catalog 产物逐字节一致，
   并复跑条带级断言（落地资产本体参与双跑 SHA 自证）。

附加量化硬线：二值 alpha（仅 0/255）、逐帧四边 1px 透明边距、
不透明像素数下限（整条 + 可选逐帧）、不透明唯一色数、
FINAL 首尾帧角度不同（旋转线性循环非退化）。

输出：stdout + output/verify_report.txt（内容确定性，无时间戳）。
退出码：全部通过 0，任一失败 1。
"""
import hashlib
import json
import os
import subprocess
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "output")
REPORT = os.path.join(OUT, "verify_report.txt")
LAND_DIR = os.path.normpath(os.path.join(
    HERE, "..", "..", "..", "src", "main", "resources", "assets", "gtit",
    "textures", "items"))
LAND_PNG = os.path.join(LAND_DIR, "reincarnation_crystal.png")

GENS = (
    "gen_style_a_shard.py",
    "gen_style_b_ring.py",
    "gen_style_c_spiral.py",
    "gen_style_d_orb.py",
    "gen_final_crystal.py",
)

EXPECT = {
    "style_a_shard.png": {"size": (16, 16), "frames": 1, "min_opaque": 70, "mcmeta": None},
    "style_b_ring.png": {"size": (16, 128), "frames": 8, "min_opaque": 50,
                         "mcmeta": {"frametime": 2}},
    "style_c_spiral.png": {"size": (16, 64), "frames": 4, "min_opaque": 40,
                           "mcmeta": {"frametime": 3, "frames": [0, 1, 2, 3, 2, 1]}},
    "style_d_orb.png": {"size": (16, 16), "frames": 1, "min_opaque": 80, "mcmeta": None},
    "final_crystal.png": {"size": (16, 384), "frames": 24, "min_opaque": 2600,
                          "per_frame_min_opaque": 100, "first_last_distinct": True,
                          "mcmeta": {"frametime": 1}},
}

PRODUCTS = ("style_a_shard.png", "style_b_ring.png",
            "style_b_ring.png.mcmeta", "style_c_spiral.png",
            "style_c_spiral.png.mcmeta", "style_d_orb.png",
            "final_crystal.png", "final_crystal.png.mcmeta")

DESIGN_SUMMARY = {
    "style_a_shard.png":
        "候选A 六棱晶柱（静态）：清澈蓝白多面晶簇，左受光右背光+横向断口分晶，虹彩顶点品红/青/金+星芒；底形=竖向棱柱群",
    "style_b_ring.png":
        "候选B 轮回法环（动画 8帧 frametime=2）：蓝白圆环受光弧固定，虹彩彗星流光（白头-青-品红-金尾）绕环飞驰，LCG星尘常驻；底形=环形",
    "style_c_spiral.png":
        "候选C 轮回双螺旋（动画 4帧 ping-pong frametime=3）：紫白双臂向心汇聚（1.1匝留匝间沟），呼吸明暗+向心脉冲+虹彩晶砂渐次点亮，白核收束；底形=涡旋",
    "style_d_orb.png":
        "候选D 晶球核（静态）：蓝紫玻璃球，左上高光十字+右下透射热点，白核+八角虹彩光环+气泡微晶；底形=正球",
    "final_crystal.png":
        "FINAL 金白自旋双螺旋（落地款，动画 24帧 frametime=1 = 1.2s/圈）：C 基底刚体自旋 15度/帧线性循环，金-白-金渐变主宰（外深金-中暖白闪光带-内回金），受光主臂/背光副臂拉亮度差，白核收束+极微量品红/青珠光晶砂；底形=涡旋",
}

_lines = []
_fail = 0


def log(line):
    _lines.append(line)
    print(line)


def check(cond, ok_msg, fail_msg):
    global _fail
    if cond:
        log("  PASS " + ok_msg)
    else:
        _fail += 1
        log("  FAIL " + fail_msg)


def sha_of(path):
    with open(path, "rb") as fh:
        return hashlib.sha256(fh.read()).hexdigest()


def snapshot():
    snap = {}
    for name in sorted(os.listdir(OUT)):
        if name.endswith(".png") or name.endswith(".mcmeta"):
            snap[name] = sha_of(os.path.join(OUT, name))
    return snap


def run_gen(gen):
    proc = subprocess.run([sys.executable, gen], cwd=HERE,
                          capture_output=True, text=True)
    if proc.returncode != 0:
        log("  FAIL generator %s exited %d\n%s\n%s" % (
            gen, proc.returncode, proc.stdout, proc.stderr))
        global _fail
        _fail += 1
        return False
    return True


def is_pot(v):
    return v > 0 and (v & (v - 1)) == 0


def main():
    os.makedirs(OUT, exist_ok=True)
    log("== gtit reincarnation_crystal candidate set verification ==")

    # ---- 1) 双跑幂等 ----
    log("[1] double-run SHA256 idempotency")
    for gen in GENS:
        if not run_gen(gen):
            log("[abort] generator failed: %s" % gen)
            _write_report()
            sys.exit(1)
    snap1 = snapshot()
    for gen in GENS:
        run_gen(gen)
    snap2 = snapshot()
    expect_set = set(PRODUCTS)
    check(set(snap1) == expect_set,
          "run1 products exactly %s" % sorted(snap1),
          "run1 product set mismatch: %s" % sorted(snap1))
    check(set(snap2) == expect_set,
          "run2 products exactly %s" % sorted(snap2),
          "run2 product set mismatch: %s" % sorted(snap2))
    for name in sorted(expect_set):
        h1, h2 = snap1.get(name), snap2.get(name)
        check(h1 is not None and h1 == h2,
              "%s sha256 stable %s" % (name, h1),
              "%s sha256 differs across runs: %s vs %s" % (name, h1, h2))

    # ---- 2)+3) 逐文件量化断言与动画合规 ----
    log("[2][3] per-file assertions & animation mcmeta compliance")
    base_md5 = {}
    for name, exp in EXPECT.items():
        path = os.path.join(OUT, name)
        img = Image.open(path).convert("RGBA")
        w, h = img.size
        log(" -- %s" % name)
        check((w, h) == exp["size"],
              "size %dx%d" % (w, h),
              "size %dx%d != expected %s" % (w, h, exp["size"]))
        check(is_pot(w), "frame width %d is POT" % w,
              "frame width %d not POT" % w)
        check(h % w == 0, "height %d %% width %d == 0 (frames=%d)" % (h, w, h // w),
              "height %d not divisible by width %d (remainder rows would be dropped)" % (h, w))
        check(h // w == exp["frames"],
              "frame count %d == expected %d" % (h // w, exp["frames"]),
              "frame count %d != expected %d" % (h // w, exp["frames"]))
        px = img.load()
        alphas = set()
        opaque = 0
        colors = set()
        border_clear = True
        for y in range(h):
            for x in range(w):
                r, g, b, a = px[x, y]
                alphas.add(a)
                if a != 0:
                    opaque += 1
                    colors.add((r, g, b, a))
                if (x == 0 or y == 0 or x == w - 1 or y == h - 1) and a != 0:
                    border_clear = False
        check(alphas <= {0, 255}, "alpha binary (only 0/255): %s" % sorted(alphas),
              "alpha has non-binary values: %s" % sorted(alphas))
        check(opaque >= exp["min_opaque"],
              "opaque pixels %d >= min %d" % (opaque, exp["min_opaque"]),
              "opaque pixels %d < min %d" % (opaque, exp["min_opaque"]))
        check(border_clear, "1px transparent margin on all 4 edges",
              "opaque pixel touches image border")
        log("  INFO unique opaque colors: %d" % len(colors))
        # mcmeta
        mc_path = path + ".mcmeta"
        if exp["mcmeta"] is None:
            check(not os.path.exists(mc_path),
                  "no mcmeta (square static icon)", "unexpected mcmeta present")
        else:
            check(os.path.exists(mc_path),
                  "mcmeta present: %s.mcmeta" % name, "mcmeta missing")
            with open(mc_path, "r", encoding="utf-8") as fh:
                data = json.load(fh)
            check(set(data.keys()) == {"animation"},
                  "mcmeta top-level keys == {animation}", "mcmeta top-level keys: %s" % sorted(data))
            anim = data.get("animation", {})
            bad_keys = set(anim.keys()) - {"frametime", "frames"}
            check(not bad_keys,
                  "no dead keys (interpolate/width/height absent)",
                  "dead/unknown keys present: %s" % sorted(bad_keys))
            ft = anim.get("frametime")
            check(isinstance(ft, int) and ft >= 1,
                  "frametime explicit = %s (>=1)" % ft, "frametime invalid: %r" % ft)
            frames = anim.get("frames")
            n = h // w
            if frames is None:
                log("  INFO frames[] absent -> auto 0..%d full loop" % (n - 1))
                seq = list(range(n))
            else:
                check(all(isinstance(i, int) and 0 <= i < n for i in frames),
                      "frames[] %s all in [0,%d) (no out-of-range index)" % (frames, n),
                      "frames[] out of range: %s" % frames)
                seq = frames
            check(anim == exp["mcmeta"] or (frames is None and exp["mcmeta"] == {"frametime": ft}),
                  "mcmeta content matches manifest expectation %s" % exp["mcmeta"],
                  "mcmeta %s != expected %s" % (anim, exp["mcmeta"]))
        # 帧切分与帧间互异
        n_frames = h // w
        frame_md5 = []
        frame_opaque = []
        for f in range(n_frames):
            fr = img.crop((0, f * w, w, (f + 1) * w))
            frame_md5.append(hashlib.md5(fr.tobytes()).hexdigest())
            fpx = fr.load()
            f_op = sum(1 for yy in range(w) for xx in range(w) if fpx[xx, yy][3] != 0)
            f_margin = all(
                fpx[xx, yy][3] == 0
                for yy in range(w) for xx in range(w)
                if xx in (0, w - 1) or yy in (0, w - 1))
            frame_opaque.append(f_op)
            if exp.get("per_frame_min_opaque") is not None:
                check(f_op >= exp["per_frame_min_opaque"],
                      "frame %d opaque %d >= per-frame min %d" % (f, f_op, exp["per_frame_min_opaque"]),
                      "frame %d opaque %d < per-frame min %d" % (f, f_op, exp["per_frame_min_opaque"]))
                check(f_margin,
                      "frame %d 1px transparent margin (all 4 edges)" % f,
                      "frame %d has opaque pixel on frame border" % f)
        if frame_opaque:
            log("  INFO per-frame opaque: %s" % frame_opaque)
        base_md5[name] = frame_md5[0]
        if n_frames > 1:
            pairwise = len(set(frame_md5)) == n_frames
            check(pairwise,
                  "all %d frames pairwise distinct (animation non-degenerate)" % n_frames,
                  "duplicate frames detected: %s" % frame_md5)
            log("  INFO frame sequence (strip order): %s" % list(range(n_frames)))
            if exp["mcmeta"] and "frames" in (exp["mcmeta"] or {}):
                log("  INFO playback timeline: %s (frametime=%s ticks)"
                    % (exp["mcmeta"]["frames"], exp["mcmeta"]["frametime"]))
            else:
                log("  INFO playback timeline: auto 0..%d full loop, frametime=%s ticks"
                    % (n_frames - 1, exp["mcmeta"]["frametime"] if exp["mcmeta"] else 1))
            if exp.get("first_last_distinct"):
                check(frame_md5[0] != frame_md5[-1],
                      "first/last frame distinct (rotation linear loop, angles 0 vs %g deg)"
                      % ((n_frames - 1) * 360.0 / n_frames),
                      "first frame == last frame (rotation loop degenerate)")
        log("  INFO compliance: %dx%d = %d frames of %dx%d, mcmeta=%s"
            % (w, h, n_frames, w, w,
               ("present: " + json.dumps(exp["mcmeta"])) if exp["mcmeta"] else "none (square static)"))

    # ---- 套间互异 ----
    log("[2b] cross-set distinctness (base frames pairwise)")
    names = list(base_md5.keys())
    for i in range(len(names)):
        for j in range(i + 1, len(names)):
            check(base_md5[names[i]] != base_md5[names[j]],
                  "%s != %s" % (names[i], names[j]),
                  "%s and %s base frames identical!" % (names[i], names[j]))

    # ---- 5) FINAL 落地资产回读校验（正式资产路径） ----
    log("[5] FINAL landed asset re-read (formal path)")
    log("  INFO land path: %s" % LAND_PNG)
    check(os.path.exists(LAND_PNG), "landed png present", "landed png missing: %s" % LAND_PNG)
    land_mc = LAND_PNG + ".mcmeta"
    check(os.path.exists(land_mc), "landed mcmeta present", "landed mcmeta missing")
    catalog_png = os.path.join(OUT, "final_crystal.png")
    if os.path.exists(LAND_PNG) and os.path.exists(catalog_png):
        check(sha_of(LAND_PNG) == sha_of(catalog_png),
              "landed png sha256 == catalog final_crystal.png (%s)" % sha_of(LAND_PNG),
              "landed png sha mismatch vs catalog")
        limg = Image.open(LAND_PNG).convert("RGBA")
        check(limg.size == (16, 384),
              "landed strip size 16x384 = 24 frames of 16x16",
              "landed strip size %s unexpected" % (limg.size,))
        lpx = limg.load()
        l_alpha = {lpx[x, y][3] for y in range(limg.size[1]) for x in range(16)}
        check(l_alpha <= {0, 255}, "landed alpha binary", "landed alpha non-binary: %s" % sorted(l_alpha))
    if os.path.exists(land_mc) and os.path.exists(catalog_png + ".mcmeta"):
        with open(land_mc, "rb") as fh:
            lb = fh.read()
        with open(catalog_png + ".mcmeta", "rb") as fh:
            cb = fh.read()
        check(lb == cb,
              "landed mcmeta bytes == catalog mcmeta (%s)" % lb.decode("utf-8").strip(),
              "landed mcmeta mismatch: %r vs %r" % (lb, cb))

    # ---- 4) 设计语言摘要 ----
    log("[4] per-set design summary (for viewer AUQ)")
    for name in sorted(DESIGN_SUMMARY):
        log("  %s: %s" % (name, DESIGN_SUMMARY[name]))

    log("== RESULT: %s ==" % ("ALL PASS" if _fail == 0 else "%d FAILURES" % _fail))
    _write_report()
    sys.exit(0 if _fail == 0 else 1)


def _write_report():
    with open(REPORT, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("\n".join(_lines) + "\n")


if __name__ == "__main__":
    main()
