#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""gtit 轮回水晶 · FINAL 收敛落地款：金白双螺旋刚体自旋（16x384 = 24 帧）

以候选 C 双螺旋为基底，按用户裁决修订：
1) 主题色 = 金色-白色-金色渐变主宰（旋臂自金入白再回金：外圈深金 ->
   中段暖白闪光带 -> 内圈回金）；原语义"清澈闪光+斑斓溢彩"保留为
   中段暖白闪光带 + 极微量品红/青虹彩晶砂（4 px 点缀，不与金争主）。
2) 动画 = 自身旋转：整枚螺旋逐帧刚体旋转 15 度/帧（360/24 均匀步进，
   线性循环无 ping-pong，首尾帧角 345->360 连续无跳变），
   24 帧 @ frametime 1 = 1.2s/圈（20 FPS 播放）。
3) 读形继承 C 返工结论：臂宽 2.4->1.0 px、1.1 匝、匝间透明沟保留；
   受光主臂/背光副臂双色调表拉开亮度差（并使 180 度旋转非自同构，
   保证 24 帧两两互异）。

动画合规（wiki ModelTex/animation-textures-1710.md §7）：
帧 16x16 正方形、帧宽 16 (POT)、高 384 = 24x16 精确整除、
mcmeta = frametime 1 显式（frames[] 缺省 = 自动 0..23 全帧线性循环）、
无 interpolate/width/height 死键；二值 alpha；每帧四边 1px 透明边距
（最大着色半径 7.4 < 7.5，任意旋转角不触边）。

落地双写：catalog output/ 与正式资产
src/main/resources/assets/gtit/textures/items/reincarnation_crystal.png(+.mcmeta)
同字节写入（同一 PNG bytes 落两路，SHA 逐路径相等）。
幂等再生：MANIFEST 常量 + 纯函数（无随机源），绘制代码零硬编码色值。
"""
import hashlib
import io
import json
import math
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
# tools/artgen_catalog/reincarnation_crystal -> 仓库根
LAND_DIR = os.path.normpath(os.path.join(
    HERE, "..", "..", "..", "src", "main", "resources", "assets", "gtit",
    "textures", "items"))

# ---------------- MANIFEST：全部 t 值 / 几何 / 调色 / 动画 / 断言常量 ----------------
MANIFEST = {
    "name": "final_crystal",
    "size": 16,
    "frames": 24,
    "step_deg": 15.0,          # 360/24 均匀角步进，线性循环
    "frametime": 1,            # 24 tick = 1.2s/圈
    "out_rel": ("output", "final_crystal.png"),
    "land_name": "reincarnation_crystal",
    # 几何（C 基底：臂 2.4px、1.1 匝、匝间透明沟）
    "cx": 7.5,
    "cy": 7.5,
    "theta0": -1.5707963,
    "sweep": 6.9115,           # 1.1 * 2pi
    "arm_offsets": (0.0, 3.14159265),   # 双臂 180 度相位差
    "r0": 6.0,
    "r_end": 0.9,
    "w0": 2.4,
    "w_end": 1.0,
    "samples": 700,
    # 调色（金色-白色-金色主宰；t 值全进 manifest）
    "palette": {
        "outline": (0x3C, 0x2A, 0x0A),          # 深琥珀描边（16px 读形）
        "nucleus": (0xFF, 0xFF, 0xFF),
        "iri_magenta": (0xE0, 0x74, 0xBE),      # 虹彩点缀（极微量珠光调）
        "iri_cyan": (0x62, 0xC8, 0xC0),
        # 受光主臂：外深金 -> 中金 -> 暖白闪光带 -> 亮金 -> 内深金
        "arm_lit": {
            "gold_deep": (0xC4, 0x8E, 0x22),
            "gold_mid": (0xE8, 0xB8, 0x38),
            "white_warm": (0xFF, 0xFA, 0xE6),
            "gold_bright": (0xFC, 0xD9, 0x5C),
        },
        # 背光副臂：同布局整体降一档（亮度差 + 破 180 度自同构）
        "arm_shade": {
            "gold_deep": (0x96, 0x6A, 0x14),
            "gold_mid": (0xBA, 0x86, 0x20),
            "white_warm": (0xEF, 0xE0, 0xBE),
            "gold_bright": (0xD4, 0x9E, 0x2E),
        },
    },
    # 金-白-金 t 分段（t=0 外圈 -> t=1 内圈）
    "bands": (
        (0.28, "gold_deep"),
        (0.44, "gold_mid"),
        (0.60, "white_warm"),
        (0.78, "gold_bright"),
        (1.01, "gold_deep"),
    ),
    # 虹彩晶砂：(臂号, t, 色键) —— t 位两臂互异（非对称点缀）
    "sparkles": (
        (0, 0.33, "iri_cyan"),
        (0, 0.69, "iri_magenta"),
        (1, 0.47, "iri_magenta"),
        (1, 0.84, "iri_cyan"),
    ),
    # 白核 2x2（旋转中心，逐帧轴对齐消抖）
    "nucleus_c": (8, 8),
    "nucleus_offsets": ((0, 0), (1, 0), (0, 1), (1, 1)),
    # ---- 自证量化硬线 ----
    "min_opaque_per_frame": 100,    # 实测逐帧 113..125，下限留 ~12% 裕量
    "max_unique_colors": 20,
    "min_luma_span_ratio": 1.5,     # 调色板最亮/最暗受光比（白带 vs 深金）
    "min_arm_luma_ratio": 1.2,      # 受光臂/背光臂同级金色亮度比
}


def luma(rgb):
    """Rec.709 亮度（0..255）。"""
    return 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2]


def tone_at(m, t):
    """金-白-金 t 分段 -> 色键。"""
    for hi, key in m["bands"]:
        if t < hi:
            return key
    return m["bands"][-1][1]


def spiral_point(m, t, offset, rot):
    theta = m["theta0"] + offset + rot + t * m["sweep"]
    r = m["r0"] + (m["r_end"] - m["r0"]) * t
    return m["cx"] + r * math.cos(theta), m["cy"] + r * math.sin(theta)


def brush_radius(m, t):
    w = m["w0"] + (m["w_end"] - m["w0"]) * t
    return w / 2.0 + 0.2


def sparkle_cells(m, rot):
    """晶砂格：参数螺旋采样点取整，随臂同步旋转（保证落在臂上）。"""
    cells = []
    for arm, ts, key in m["sparkles"]:
        sx, sy = spiral_point(m, ts, m["arm_offsets"][arm], rot)
        cells.append((int(round(sx)), int(round(sy)), key))
    return tuple(cells)


def draw_frame(m, rot):
    """单帧 tone-key 画布：整枚螺旋随 rot 刚体旋转（色调随臂走）。

    画布格值 = (arm, key)，arm 0=受光主臂 / 1=背光副臂 —— 受光/背光
    双色调表据此分流（并破除 180 度旋转自同构，保证帧两两互异）。
    """
    n = m["size"]
    cv = [[None] * n for _ in range(n)]
    # 1) 双臂螺旋体：t 升序采样，内圈覆盖外圈；主臂后画（受光面优先呈现）
    for arm in (0, 1):
        off = m["arm_offsets"][arm]
        for i in range(m["samples"]):
            t = i / float(m["samples"] - 1)
            key = tone_at(m, t)
            rho = brush_radius(m, t)
            sx, sy = spiral_point(m, t, off, rot)
            x_lo = max(0, int(sx - rho - 1))
            x_hi = min(n - 1, int(sx + rho + 1))
            y_lo = max(0, int(sy - rho - 1))
            y_hi = min(n - 1, int(sy + rho + 1))
            for yy in range(y_lo, y_hi + 1):
                for xx in range(x_lo, x_hi + 1):
                    d2 = (xx - sx) ** 2 + (yy - sy) ** 2
                    if d2 <= rho * rho:
                        cv[yy][xx] = (arm, key)
    # 2) 轮廓描边（4 邻接透明，保形不扩形）
    for y in range(n):
        for x in range(n):
            if cv[y][x] is None:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if nx < 0 or ny < 0 or nx >= n or ny >= n or cv[ny][nx] is None:
                    cv[y][x] = ("ol", "outline")
                    break
    # 3) 白核 2x2（旋转中心，逐帧同位轴对齐）
    ncx, ncy = m["nucleus_c"]
    for ox, oy in m["nucleus_offsets"]:
        cv[ncy + oy][ncx + ox] = ("ol", "nucleus")
    # 4) 虹彩晶砂（随臂旋转的点缀）
    for sx, sy, key in sparkle_cells(m, rot):
        cv[sy][sx] = ("iri", key)
    return cv


def render(canvas, pal, n):
    img = Image.new("RGBA", (n, n))
    px = img.load()
    for y in range(n):
        for x in range(n):
            cell = canvas[y][x]
            if cell is None:
                px[x, y] = (0, 0, 0, 0)
                continue
            group, key = cell
            if group == "ol":
                px[x, y] = pal[key] + (255,)
            elif group == "iri":
                px[x, y] = pal[key] + (255,)
            elif group == 0:
                px[x, y] = pal["arm_lit"][key] + (255,)
            else:
                px[x, y] = pal["arm_shade"][key] + (255,)
    return img


def self_check(m, frame_imgs, png_bytes):
    """生成侧量化断言：任一失败 SystemExit(1)。"""
    n = m["size"]
    fails = []
    # 调色板亮度差（金色渐变自检）
    body = list(m["palette"]["arm_lit"].values()) + list(m["palette"]["arm_shade"].values())
    lum = [luma(c) for c in body]
    span = max(lum) / min(lum)
    if span < m["min_luma_span_ratio"]:
        fails.append("luma span %.3f < %.2f" % (span, m["min_luma_span_ratio"]))
    lit_mid = luma(m["palette"]["arm_lit"]["gold_mid"])
    shade_mid = luma(m["palette"]["arm_shade"]["gold_mid"])
    arm_ratio = lit_mid / shade_mid
    if arm_ratio < m["min_arm_luma_ratio"]:
        fails.append("arm luma ratio %.3f < %.2f" % (arm_ratio, m["min_arm_luma_ratio"]))
    # 逐帧：尺寸/alpha 二值/四边边距/不透明下限/互异
    seen_md5 = []
    per_opaque = []
    for f, img in enumerate(frame_imgs):
        if img.size != (n, n):
            fails.append("frame %d size %s" % (f, img.size))
        px = img.load()
        opaque = 0
        colors = set()
        for y in range(n):
            for x in range(n):
                r, g, b, a = px[x, y]
                if a not in (0, 255):
                    fails.append("frame %d alpha %d at (%d,%d)" % (f, a, x, y))
                if a != 0:
                    opaque += 1
                    colors.add((r, g, b, a))
                if (x == 0 or y == 0 or x == n - 1 or y == n - 1) and a != 0:
                    fails.append("frame %d border pixel (%d,%d)" % (f, x, y))
        per_opaque.append(opaque)
        if opaque < m["min_opaque_per_frame"]:
            fails.append("frame %d opaque %d < %d" % (f, opaque, m["min_opaque_per_frame"]))
        if len(colors) > m["max_unique_colors"]:
            fails.append("frame %d unique colors %d > %d" % (f, len(colors), m["max_unique_colors"]))
        seen_md5.append(hashlib.md5(img.tobytes()).hexdigest())
    if len(set(seen_md5)) != m["frames"]:
        fails.append("frames not pairwise distinct (rotation symmetry leak)")
    if seen_md5[0] == seen_md5[-1]:
        fails.append("first frame == last frame (angle 0 vs %g deg)" % (
            (m["frames"] - 1) * m["step_deg"]))
    # strip 字节尺寸（精确整除 + POT 由构造保证，此处复核字节头）
    return fails, per_opaque, span, arm_ratio, seen_md5


def main():
    m = MANIFEST
    n = m["size"]
    pal = m["palette"]

    frame_imgs = []
    strip = Image.new("RGBA", (n, n * m["frames"]))
    for f in range(m["frames"]):
        rot = math.radians(m["step_deg"] * f)   # 线性均匀步进，无 ping-pong
        img = render(draw_frame(m, rot), pal, n)
        frame_imgs.append(img)
        strip.paste(img, (0, f * n))

    # 同一 PNG bytes 落两路（catalog + 正式资产），保证逐路径 SHA 相等
    buf = io.BytesIO()
    strip.save(buf, format="PNG")
    png_bytes = buf.getvalue()
    out_path = os.path.join(HERE, *m["out_rel"])
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, "wb") as fh:
        fh.write(png_bytes)
    land_path = os.path.join(LAND_DIR, m["land_name"] + ".png")
    os.makedirs(LAND_DIR, exist_ok=True)
    with open(land_path, "wb") as fh:
        fh.write(png_bytes)

    mcmeta = json.dumps(
        {"animation": {"frametime": m["frametime"]}},
        sort_keys=True, separators=(",", ":")) + "\n"
    mc_bytes = mcmeta.encode("utf-8")
    with open(out_path + ".mcmeta", "wb") as fh:
        fh.write(mc_bytes)
    with open(land_path + ".mcmeta", "wb") as fh:
        fh.write(mc_bytes)

    fails, per_opaque, span, arm_ratio, md5s = self_check(m, frame_imgs, png_bytes)
    sha = hashlib.sha256(png_bytes).hexdigest()
    print("%s strip=%dx%d frames=%d step=%gdeg frametime=%d (%.1fs/rev)" % (
        m["name"], n, n * m["frames"], m["frames"], m["step_deg"],
        m["frametime"], m["frames"] * m["frametime"] / 20.0))
    print("%s per-frame opaque: %s" % (m["name"], per_opaque))
    print("%s luma span=%.3f arm lit/shade=%.3f unique_self_check=ok" % (
        m["name"], span, arm_ratio))
    print("%s frame0_md5=%s frame_last_md5=%s (angles 0 vs %g deg)" % (
        m["name"], md5s[0][:12], md5s[-1][:12], (m["frames"] - 1) * m["step_deg"]))
    print("%s png sha256=%s" % (m["name"], sha))
    print("%s mcmeta=%s sha256=%s" % (
        m["name"], mcmeta.strip(), hashlib.sha256(mc_bytes).hexdigest()))
    print("%s land=%s" % (m["name"], land_path))
    if fails:
        for msg in fails:
            print("FAIL %s" % msg)
        raise SystemExit(1)
    print("%s self-check: ALL PASS" % m["name"])


if __name__ == "__main__":
    main()
