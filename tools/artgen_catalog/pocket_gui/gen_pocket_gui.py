"""C2｜铜包角束口 —— GUI 装饰贴图家族生成器（12 张，幂等直写 PNG）。

用法：
  python -B gen_pocket_gui.py                 # 生成到 out/ + 全量自检 + SHA 表
  python -B gen_pocket_gui.py --report        # 只跑自检与量化报告（不写盘）
  python -B gen_pocket_gui.py --land --i-have-authorization   # 落地到 textures/gui/pocket/

单一权威链（本文件正文零字面 RGB、零 t/k、零第二个尺寸）：
  pocket-ui-mockups-2.html --contract.py--> 名称 / 像素尺寸 / 是否 9-slice / N / 色值 / 环带厚度
                                          --palette.py--> 色名 + 派生公式（t/k 只在此登记）
                                          --gui_manifest.py--> 画法几何常量
  本文件只做两件事：按清单落像素、机检。

幂等五要件在本脚本的落点：
  1 清单常量      —— 尺寸/N/色/厚度全部回契约与 manifest 取，正文不重复
  2 LCG 纯函数    —— canvas.lcg_hash2（位置定值，不按遍历顺序推进状态）
  3 PNG 直写      —— pngwrite.encode_png（无时间戳、无 tEXt 块）
  4 双跑一致      —— 见 --sha 表与 run_pocket_gui_check 的双跑比对（逐文件 SHA256 必等）
  5 零硬编码      —— self_check 里的 scan_discipline 扫本目录正文的色值/反斜杠字面量
"""
from __future__ import annotations

import hashlib
import re
import sys
from pathlib import Path

import canvas as CV
import gui_manifest as GM
import pngwrite
from contract import CONTRACT
from palette import ACCENT_ALLOWED, PAL, PROV, contrast, deriv_signature, hex_to_rgb

BOUND_MIN_CONTRAST = 2.0     # 机检门：槽位"内暗环"对槽心 / 对布底的对比度下限
RING_DOMINANCE = 6.0         # 机检门：环带明度差 >= 6 倍中心织纹的最大落差
SEAM_SLACK = 0               # 平铺接缝允许的额外突变度（0 = 接缝不得比图内最差相邻更糟）
SEAM_MIN_INNER = 6           # 防空转门：图内最大相邻突变度至少到这个量，否则判据恒真


# ================================================================ 画布小工具

def new_sheet(key: str):
    t = CONTRACT.t(key)
    return t, CV.blank(t.w, t.h), t.w, t.h


def pal(name: str):
    return PAL[name]


def weave_fill(buf, w, h, kind: str, seed: int, region=None) -> None:
    """平纹织：偶列暗 / 偶行亮 / 交织点最暗 + 位置定值纱节。region 决定只铺哪一块。"""
    base, warp, weft, cross, slub = GM.WEAVE["base"][kind]
    per = GM.WEAVE["period"]
    rate = GM.WEAVE["slub_rate"]
    tones = (pal(base), pal(warp), pal(weft), pal(cross), pal(slub))
    for y in range(h):
        for x in range(w):
            if region is not None and not region(x, y):
                continue
            ex, ey = (x % per == 0), (y % per == 0)
            if ex and ey:
                c = tones[3]
            elif ex:
                c = tones[1]
            elif ey:
                c = tones[2]
            else:
                c = tones[0]
            if CV.lcg_hash2(x, y, seed) % 100 < rate:
                c = tones[4]
            CV.put(buf, w, x, y, c)


def rings_from(buf, w, h, names) -> int:
    """按 manifest 的色名序列画同心环（d=0 起，向外一圈一档）；返回占用的边距。"""
    for i, nm in enumerate(names):
        CV.ring(buf, w, h, i, pal(nm))
    return len(names)


def stud_mask(size: int):
    """铆钉头掩码：大尺寸用近圆，小尺寸用等量切角（两者都沿两轴与两条对角镜像对称）。"""
    if size >= 5:
        return CV.disc_mask(size)
    if size >= 3:
        return CV.octagon_mask(size, 1)
    return [[1] * size for _ in range(size)]


def stud(buf, w, h, ox: int, oy: int, size: int) -> None:
    """左上受光的半球铆钉头。size=6 是 rivet 资产本体，更小的是 9-slice 角块里的 stud。"""
    mask = stud_mask(size)
    dep = CV.depth_map(mask)
    dmax = max(max(r) for r in dep)
    for y in range(size):
        for x in range(size):
            if not mask[y][x]:
                continue
            dd = dep[y][x]
            rel = (x + y) - (size - 1)         # <0 偏左上（受光），>0 偏右下（背光）
            if dd == 0:
                c = pal(GM.STUD_EDGE)
            elif dd >= dmax:
                c = pal("cup_hi") if rel <= 0 else pal(GM.STUD_SHADOW)
            elif rel < 0:
                c = pal("cup_hi")
            elif rel > 0:
                c = pal(GM.STUD_SHADOW)
            else:
                c = pal("cup_mid")
            CV.put(buf, w, ox + x, oy + y, c)


def corner_studs(buf, w, h, n: int, size: int, which=("tl", "tr")) -> None:
    """铆钉头**只**允许坐在 9-slice 角块里（四角块是唯一"任意方向都不拉伸"的区域），
    并在各自角块中居中 —— 贴到块角上会与第 0 圈描边同色而看不见。"""
    off = (n - size) // 2
    pos = {"tl": (off, off), "tr": (w - size - off, off),
           "bl": (off, h - size - off), "br": (w - size - off, h - size - off)}
    for k in which:
        ox, oy = pos[k]
        assert ox + size <= w and oy + size <= h, (k, size, w, h)
        in_x = (ox + size <= n) or (ox >= w - n)
        in_y = (oy + size <= n) or (oy >= h - n)
        assert in_x and in_y, f"stud {k} size={size} 越出角块（N={n}）"
        stud(buf, w, h, ox, oy, size)


# ================================================================ 12 张

def draw_cloth():
    t, buf, w, h = new_sheet("cloth")
    weave_fill(buf, w, h, "cloth", GM.CLOTH_SEED)
    return t, buf


def draw_panel():
    key = GM.PANEL_KEY
    t = CONTRACT.t(key)
    buf, w, h = CV.blank(t.w, t.h), t.w, t.h
    bands = CONTRACT.frame_bands(key)
    assert len(bands) == len(GM.PANEL_BAND_COLORS), (bands, GM.PANEL_BAND_COLORS)
    spans, d = [], 0
    for (kind, idx, th), nm in zip(bands, GM.PANEL_BAND_COLORS):
        spans.append((d, d + th, kind, idx, nm))
        d += th
    frame_t = d
    assert frame_t <= t.n, f"框总厚 {frame_t} 超过契约 N={t.n}"
    wood_i = {i for i, s in enumerate(spans) if s[2] == "border"}
    last_i = len(spans) - 1
    for y in range(h):
        for x in range(w):
            dd = CV.depth_at(w, h, x, y)
            if dd >= frame_t:
                # 可拉伸的中心带必须留平色：织纹画在这里，一拉伸就成横/竖条纹
                CV.put(buf, w, x, y, pal("cloth"))
                continue
            si = next(i for i, s in enumerate(spans) if s[0] <= dd < s[1])
            nm = spans[si][4]
            if si in wood_i:                      # 木框明暗按环带序号轮换 => 沿拉伸轴恒定
                nm = GM.PANEL_GRAIN[(dd - spans[si][0]) % len(GM.PANEL_GRAIN)]
            elif si == last_i and dd == spans[si][1] - 1:
                nm = GM.PANEL_COPPER_INNER        # 铜线最内一档受光
            CV.put(buf, w, x, y, pal(nm))
    n = t.n                                       # 角块内的 45 度拼缝（角块不参与拉伸）
    for y in range(h):
        for x in range(w):
            if CV.depth_at(w, h, x, y) >= frame_t:
                continue
            l, r, u, dn = x < n, x >= w - n, y < n, y >= h - n
            miter = ((l and u and x == y) or (r and u and (w - 1 - x) == y)
                     or (l and dn and x == (h - 1 - y))
                     or (r and dn and (w - 1 - x) == (h - 1 - y)))
            if miter:
                CV.put(buf, w, x, y, pal(GM.PANEL_MITER))
    return t, buf


def draw_corner():
    t = CONTRACT.t("corner")
    size = t.w
    assert t.w == t.h, t
    buf = CV.blank(size, size)
    mask = CV.octagon_mask(size, GM.CORNER_CHAMFER)
    dep = CV.depth_map(mask)
    for y in range(size):
        for x in range(size):
            if not mask[y][x]:
                continue
            dd = dep[y][x]
            nm = GM.CORNER_CORE
            for lim, cand in GM.CORNER_RAMP:
                if dd <= lim:
                    nm = cand
                    break
            CV.put(buf, size, x, y, pal(nm))
    return t, buf


def draw_rivet():
    t = CONTRACT.t("rivet")
    size = t.w
    assert t.w == t.h, t
    buf = CV.blank(size, size)
    stud(buf, size, size, 0, 0, size)
    return t, buf


def _slot_core(key: str, ring_names, center_kind: str, seed: int, round_tone=None):
    t = CONTRACT.t(key)
    buf, w, h = CV.blank(t.w, t.h), t.w, t.h
    n = t.n
    assert len(ring_names) == n, f"{key} 环数 {len(ring_names)} 与契约 N={n} 不符"
    assert rings_from(buf, w, h, ring_names) == n
    weave_fill(buf, w, h, center_kind, seed,
               region=lambda x, y: CV.depth_at(w, h, x, y) >= n)
    if round_tone is not None:                    # 切角：让"圈"读成圆环，只动边距带不动槽心
        cut = GM.SLOT_TALL_ROUND_CUT
        for cx, cy in [(0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)]:
            sx = 1 if cx == 0 else -1
            sy = 1 if cy == 0 else -1
            for k in range(cut):
                CV.put(buf, w, cx + sx * k, cy + sy * k, pal(round_tone))
            CV.put(buf, w, cx + sx * (n - 1), cy + sy * (n - 1), pal(round_tone))
    return t, buf


def draw_slot():
    return _slot_core("slot", GM.SLOT_RINGS, "floor", GM.SLOT_SEED)


def draw_slot_tall():
    return _slot_core("slot_tall", GM.SLOT_TALL_RINGS, "col_floor", GM.COL_SEED,
                      GM.SLOT_TALL_ROUND)


def _plate(key: str, ring_names, center: str, studs=None, top_line=None, bottom_line=None):
    """宽块通用体：同心环 + 平色中心（+ 角块铆钉 / 整行唇线）。全是 9-slice 安全基元。"""
    t = CONTRACT.t(key)
    buf, w, h = CV.blank(t.w, t.h), t.w, t.h
    n = t.n
    assert len(ring_names) == n, f"{key} 环数 {len(ring_names)} 与契约 N={n} 不符"
    rings_from(buf, w, h, ring_names)
    CV.rect(buf, w, n, n, w - 1 - n, h - 1 - n, pal(center))
    if top_line:
        CV.rect(buf, w, 0, 1, w - 1, 1, pal(top_line))
    if bottom_line:
        CV.rect(buf, w, 0, h - 2, w - 1, h - 2, pal(bottom_line))
    if studs:
        corner_studs(buf, w, h, n, studs[0], studs[1])
    return t, buf


def draw_coinbar():
    return _plate("coinbar", GM.COINBAR_RINGS, GM.COINBAR_CENTER,
                  studs=(GM.COINBAR_STUD, ("tl", "tr")), bottom_line=GM.COINBAR_BOTTOM)


def draw_btn():
    return _plate("btn", GM.BTN_RINGS, GM.BTN_CENTER, top_line=GM.BTN_TOP_LINE)


def draw_btn_pressed():
    return _plate("btn", GM.BTN_PRESSED_RINGS, GM.BTN_PRESSED_CENTER,
                  top_line=GM.BTN_PRESSED_TOP, bottom_line=GM.BTN_PRESSED_BOTTOM)


def draw_scrollbar():
    t, buf = _plate("scrollbar", GM.SCROLLBAR_RINGS, GM.SCROLLBAR_CENTER)
    corner_studs(buf, t.w, t.h, t.n, GM.SCROLLBAR_STUD, ("tl", "tr", "bl", "br"))
    return t, buf


def draw_bindbtn():
    return _plate("bindbtn", GM.BINDBTN_RINGS, GM.BINDBTN_CENTER,
                  top_line=GM.BTN_TOP_LINE, bottom_line=GM.BINDBTN_BOTTOM)


def draw_rope():
    """绳结虚线：同一张 tile 要能横竖两用，所以色调规则必须**沿绳方向**表达。

    写成"上暗下亮"就不可能 90 度旋转对称（旋转会把横臂的顶行搬到竖臂的左列，
    两次传递就逼出两色相等）；改成"结扣亮 + 每个缺口两端毛边暗"后，
    判据只看 along 坐标，旋转自洽，可整张 RGBA 逐位对称。
    """
    t = CONTRACT.t("rope")
    buf, w, h = CV.blank(t.w, t.h), t.w, t.h
    band = (GM.ROPE_AXIS, GM.ROPE_AXIS + 1)     # 中心两行/两列
    for y in range(h):
        for x in range(w):
            on_h, on_v = y in band, x in band
            if on_h and on_v:
                nm = GM.ROPE_KNOT               # 井字交点 = 结扣
            elif on_h:
                along = x
            elif on_v:
                along = y
            else:
                continue
            if not (on_h and on_v):
                if along in GM.ROPE_DASH:
                    continue                    # 缺口（平铺后每 8 格空 2 格）
                nm = GM.ROPE_SIDE if along in GM.ROPE_FRAY else GM.ROPE_BODY
            CV.put(buf, w, x, y, pal(nm))
    return t, buf


DRAW = {
    "cloth": draw_cloth, "panel": draw_panel, "corner": draw_corner, "rivet": draw_rivet,
    "slot": draw_slot, "slot_tall": draw_slot_tall, "coinbar": draw_coinbar,
    "btn": draw_btn, "btn_pressed": draw_btn_pressed, "scrollbar": draw_scrollbar,
    "bindbtn": draw_bindbtn, "rope": draw_rope,
}


def contract_key(sheet: str) -> str:
    return GM.SHEET_CONTRACT_KEY.get(sheet, sheet)


def res_name(sheet: str) -> str:
    return CONTRACT.t(contract_key(sheet)).res_name + GM.SHEET_STATE_SUFFIX.get(sheet, "")


def build_all() -> dict[str, tuple[int, int, bytearray]]:
    out = {}
    for sheet in GM.SHEETS:
        t, buf = DRAW[sheet]()
        ct = CONTRACT.t(contract_key(sheet))
        assert (t.w, t.h) == (ct.w, ct.h), sheet
        out[sheet] = (t.w, t.h, buf)
        assert len(bytes(buf)) == t.w * t.h * 4
    assert len(out) == len(GM.SHEETS) > 0
    return out


def png_of(sheet: str, built) -> bytes:
    w, h, buf = built[sheet]
    return pngwrite.encode_png(w, h, bytes(buf))


# ================================================================ 机检

def band_legality(w, h, buf, n) -> list[str]:
    """9-slice 内容合法性：中心块平色、上下带沿 x 恒定、左右带沿 y 恒定、角块自由。"""
    bad = []
    cx0, cy0, cx1, cy1 = n, n, w - 1 - n, h - 1 - n
    if cx0 > cx1 or cy0 > cy1:
        return [f"中心块为空（{w}x{h}, N={n}）"]
    ref = CV.rgb_at(buf, w, cx0, cy0)
    for y in range(cy0, cy1 + 1):
        for x in range(cx0, cx1 + 1):
            if CV.rgb_at(buf, w, x, y) != ref:
                bad.append(f"中心块不平整 @({x},{y})")
    for y in range(n):
        for row in (range(cx0, cx1 + 1),):
            top = {CV.rgb_at(buf, w, x, y) for x in row}
            bot = {CV.rgb_at(buf, w, x, h - 1 - y) for x in row}
            if len(top) > 1:
                bad.append(f"上带 y={y} 沿 x 不恒定（{len(top)} 色）")
            if len(bot) > 1:
                bad.append(f"下带 y={h - 1 - y} 沿 x 不恒定（{len(bot)} 色）")
    for x in range(n):
        col = [y for y in range(cy0, cy1 + 1)]
        left = {CV.rgb_at(buf, w, x, y) for y in col}
        right = {CV.rgb_at(buf, w, w - 1 - x, y) for y in col}
        if len(left) > 1:
            bad.append(f"左带 x={x} 沿 y 不恒定（{len(left)} 色）")
        if len(right) > 1:
            bad.append(f"右带 x={w - 1 - x} 沿 y 不恒定（{len(right)} 色）")
    return bad


def measure_center_clearance(w, h, buf, ring_rgbs) -> int:
    """实测「中心净空」：从正中最大方块往内收，第一个不含任何环带色的居中方块边长。

    非循环定义 —— 不看 manifest 也不看 N，只看像素，所以它既能证伪"边距偷偷画大了"，
    也能证伪"环带色漏进槽心"。
    """
    ring_set = {tuple(c) for c in ring_rgbs}
    assert any(CV.rgb_at(buf, w, x, y) in ring_set
               for y in range(h) for x in range(w)), "环带色一个都没出现（贴图没画成？）"
    for s in range(min(w, h), 0, -1):
        x0, y0 = (w - s) // 2, (h - s) // 2
        if (w - s) % 2 or (h - s) % 2:
            continue
        if all(CV.rgb_at(buf, w, x, y) not in ring_set
               for y in range(y0, y0 + s) for x in range(x0, x0 + s)):
            return s
    return 0


def ring_uniform(w, h, buf, n, names) -> str | None:
    """逐环检查：每圈在"边的中段"必须逐位是该环色（角块/切角不参与，故取中段）。"""
    lo, hi = n, max(n, w - 1 - n)
    vlo, vhi = n, max(n, h - 1 - n)
    for i, nm in enumerate(names):
        want = pal(nm)
        seg = ([CV.rgb_at(buf, w, x, i) for x in range(lo, hi + 1)]
               + [CV.rgb_at(buf, w, x, h - 1 - i) for x in range(lo, hi + 1)]
               + [CV.rgb_at(buf, w, i, y) for y in range(vlo, vhi + 1)]
               + [CV.rgb_at(buf, w, w - 1 - i, y) for y in range(vlo, vhi + 1)])
        off = [p for p in seg if p != want]
        if off:
            return f"第 {i} 圈（{nm}）中段有 {len(off)} 个异色位"
    return None


def max_internal_delta(w, h, buf, x0, y0, x1, y1) -> float:
    """区域内相邻像素的最大相对亮度差（量化"织纹会不会被误读成边界"）。"""
    worst = 0.0
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            a = CV.rgb_at(buf, w, x, y)
            if x + 1 <= x1:
                worst = max(worst, abs(contrast(a, CV.rgb_at(buf, w, x + 1, y)) - 1.0))
            if y + 1 <= y1:
                worst = max(worst, abs(contrast(a, CV.rgb_at(buf, w, x, y + 1)) - 1.0))
    return worst


def ring_boundary_contrast(w, h, buf, n) -> float:
    """环带（d=n-1）与中心（d=n）交界处**最小**的对比度余量。"""
    worst = 99.0
    found = False
    for y in range(h):
        for x in range(w):
            if CV.depth_at(w, h, x, y) != n - 1:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if not (0 <= nx < w and 0 <= ny < h):
                    continue
                if CV.depth_at(w, h, nx, ny) != n:
                    continue
                found = True
                worst = min(worst, abs(contrast(CV.rgb_at(buf, w, x, y),
                                                CV.rgb_at(buf, w, nx, ny)) - 1.0))
    assert found, "找不到环带与中心的交界（边距画错了）"
    return worst


def rot9_symmetric(w, h, buf) -> bool:
    """RGBA 逐位 90 度旋转对称（正方形）。"""
    assert w == h
    for y in range(h):
        for x in range(w):
            if CV.rgba_at(buf, w, x, y) != CV.rgba_at(buf, w, h - 1 - y, x):
                return False
    return True


def _adj_cost(buf, w, x0, y0, x1, y1) -> int:
    """相邻两格的"突变度"：透明<->不透明算满值，其余取最大通道差。"""
    a, b = CV.rgba_at(buf, w, x0, y0), CV.rgba_at(buf, w, x1, y1)
    if a[3] != b[3]:
        return 255
    if a[3] == 0:
        return 0
    return max(abs(a[i] - b[i]) for i in range(3))


def tile_seam_ok(w, h, buf):
    """平铺接缝判据：跨接缝的突变度不得比图内最差的一次相邻突变更难（严格 <=，无预算）。"""
    inner = 0
    for y in range(h):
        for x in range(w - 1):
            inner = max(inner, _adj_cost(buf, w, x, y, x + 1, y))
    for y in range(h - 1):
        for x in range(w):
            inner = max(inner, _adj_cost(buf, w, x, y, x, y + 1))
    seam = 0
    for y in range(h):
        seam = max(seam, _adj_cost(buf, w, w - 1, y, 0, y))
    for x in range(w):
        seam = max(seam, _adj_cost(buf, w, x, h - 1, x, 0))
    # 防空转：图内一点起伏都没有的话，"接缝 <= 内部"是恒真的假绿
    assert inner >= SEAM_MIN_INNER, (inner, seam)
    return seam <= inner + SEAM_SLACK, inner, seam


def scan_discipline():
    """幂等纪律扫描：本目录自写 .py 正文不得出现色值字面量 / 反斜杠字面量。"""
    bad = []
    hexre = re.compile("#" + "[0-9A-Fa-f]" * 6)
    own = [p for p in sorted(GM.HERE.glob("*.py")) if p.name != "pngwrite.py"]
    for p in own:
        for i, ln in enumerate(p.read_text(encoding="utf-8").splitlines(), 1):
            if hexre.search(ln):
                bad.append(f"{p.name} 第 {i} 行有色值字面量")
            if chr(92) in ln:
                bad.append(f"{p.name} 第 {i} 行有反斜杠字面量")
    return bad, len(own)


def c1_boundary_pair():
    """C1 的槽位边界（单侧阴影方案）。只为量化 C2 的优势，不参与任何绘制。"""
    return (hex_to_rgb(CONTRACT.c1_hexa("cell", "border")),
            hex_to_rgb(CONTRACT.c1_hexa("cell", "bg")),
            hex_to_rgb(CONTRACT.c1_hexa("panel", "bg")))


def self_check(built):
    log, num = [], {}

    bad, n_own = scan_discipline()
    assert not bad, "纪律扫描失守：" + " ; ".join(bad)
    log.append(f"纪律扫描：自写 {n_own} 个脚本正文零色值字面量、零反斜杠字面量"
               f"（pngwrite 为仓内既有零依赖编码器，逐字节复用故除外）")

    # —— 1 契约面
    for sheet in GM.SHEETS:
        ct = CONTRACT.t(contract_key(sheet))
        w, h, buf = built[sheet]
        assert (w, h) == (ct.w, ct.h) and len(buf) == w * h * 4, sheet
    for key in ("cloth", "corner", "rivet", "rope"):
        assert not CONTRACT.t(key).slice, key
    for key in ("panel", "slot", "slot_tall", "coinbar", "btn", "scrollbar", "bindbtn"):
        t = CONTRACT.t(key)
        assert t.slice and min(t.w, t.h) >= 2 * t.n + 1, key
    num["sheets"] = len(GM.SHEETS)
    log.append(f"逐张对账契约：{len(GM.SHEETS)} 张名称/尺寸全中；9-slice 硬门 min(边)>=2N+1 "
               f"全过；corner/rivet/rope/cloth 确为非 9-slice")

    # —— 2 槽位 N=3：环带占位、中心净空实测、环带主导度
    for key, names in (("slot", GM.SLOT_RINGS), ("slot_tall", GM.SLOT_TALL_RINGS)):
        t = CONTRACT.t(key)
        w, h, buf = built[key]
        assert t.n == 3, (key, t.n)
        msg = ring_uniform(w, h, buf, t.n, names)
        assert msg is None, f"{key} {msg}"
        side = measure_center_clearance(w, h, buf, [pal(nm) for nm in names])
        assert side == w - 2 * t.n, (key, side, w - 2 * t.n)
        num[key + "_center"] = side
        b = ring_boundary_contrast(w, h, buf, t.n)
        c = max_internal_delta(w, h, buf, t.n, t.n, w - 1 - t.n, h - 1 - t.n)
        assert c * RING_DOMINANCE <= b, (key, b, c)
        log.append(f"{key}: 契约 N=3 的三圈环带逐位占满 d=0..2，实测中心净空 {side}x{side}；"
                   f"环心交界对比余量 {b:.2f} >= {RING_DOMINANCE:.0f}x 槽心织纹最大差 {c:.3f}"
                   f" => 误拉伸也生不出第二条边界")

    # —— 2b 三处底材的织纹公式必须逐档相同（"继承同一块布"要能被机器反驳掉才行）
    fam = {k: tuple(deriv_signature(nm) for nm in names[1:])
           for k, names in GM.WEAVE["base"].items()}
    assert len({v for v in fam.values()}) == 1, fam
    num["weave_families"] = sorted(fam)
    log.append("布纹公式在 " + "/".join(sorted(fam)) + " 三处底材逐档相同 => "
               "「底材继承」不是一个口号，是同一个 tint/shade 参数")

    # —— 3 双向描边（C2 相对 C1 的可读性优势，量化）
    c2_wall, c2_floor, c2_lip = pal("wall"), pal("floor"), pal("lip")
    c1_wall, c1_floor, c1_cloth = c1_boundary_pair()
    a1, b1 = contrast(c2_wall, c2_floor), contrast(c1_wall, c1_floor)
    a2, b2 = contrast(c2_wall, pal("cloth")), contrast(c1_wall, c1_cloth)
    assert a1 > b1 and a2 > b2, (a1, b1, a2, b2)
    assert a1 >= BOUND_MIN_CONTRAST and a2 >= BOUND_MIN_CONTRAST, (a1, a2)
    num.update(slot_wall_vs_floor=a1, c1_boundary_vs_floor=b1,
               slot_wall_vs_cloth=a2, c1_boundary_vs_cloth=b2,
               slot_lip_vs_cloth=contrast(c2_lip, pal("cloth")),
               slot_lip_vs_wall=contrast(c2_lip, c2_wall))
    log.append(f"凹槽双向描边：内暗环对槽心 {a1:.2f}、对布底 {a2:.2f}；"
               f"C1 单侧方案 {b1:.2f} / {b2:.2f} => C2 优势 {a1 / b1:.2f}x / {a2 / b2:.2f}x")
    # 环带确实是"整圈"而不是单侧：四个边各测一次
    for key, names in (("slot", GM.SLOT_RINGS), ("slot_tall", GM.SLOT_TALL_RINGS)):
        w, h, buf = built[key]
        dark = pal(names[-1])
        sides = [any(CV.rgb_at(buf, w, x, len(names) - 1) == dark for x in range(w)),
                 any(CV.rgb_at(buf, w, x, h - len(names)) == dark for x in range(w)),
                 any(CV.rgb_at(buf, w, len(names) - 1, y) == dark for y in range(h)),
                 any(CV.rgb_at(buf, w, w - len(names), y) == dark for y in range(h))]
        assert all(sides), (key, sides)
    log.append("内暗环在上下左右四条边各自整圈存在（双向描边，不是单侧阴影）")

    # —— 4 9-slice 内容合法性
    for sheet in GM.STRETCH_SAFE:
        t = CONTRACT.t(contract_key(sheet))
        w, h, buf = built[sheet]
        issues = band_legality(w, h, buf, t.n)
        assert not issues, f"{sheet} 违反 9-slice 内容合法性：" + " ; ".join(issues[:3])
    log.append("会被拉伸的件（" + "/".join(sorted(GM.STRETCH_SAFE)) + "）："
               "中心平色 + 边带沿拉伸轴恒定，逐张通过")

    # —— 5 1:1 与平铺件
    w, h, buf = built["corner"]
    assert rot9_symmetric(w, h, buf), "corner 不是 90 度旋转对称"
    n_trans = sum(1 for y in range(h) for x in range(w) if CV.alpha_at(buf, w, x, y) == 0)
    assert n_trans > 0, "corner 全不透明"
    log.append(f"corner 逐位 90 度旋转对称 => 一张供四角（契约只给一个名字），Java 侧无需旋转；"
               f"切角透明 {n_trans} 位，1:1 贴时不遮木框")
    rw, rh, rbuf = built["rivet"]
    for y in range(rh):
        for x in range(rw):
            assert CV.alpha_at(rbuf, rw, x, y) == CV.alpha_at(rbuf, rw, y, x), "rivet 掩码不沿对角镜像"
    assert any(CV.alpha_at(rbuf, rw, x, y) == 0 for y in range(rh) for x in range(rw))
    log.append(f"rivet {rw}x{rh} 沿主对角镜像（多点位复用无方向偏差）且切角透明")
    for key in ("cloth", "rope"):
        w2, h2, b2_ = built[key]
        ok, inner, seam = tile_seam_ok(w2, h2, b2_)
        assert ok, (key, inner, seam)
        num[key + "_seam"] = "接缝 " + str(seam) + " <= 图内最差相邻 " + str(inner)
        if key == "rope":
            assert rot9_symmetric(w2, h2, b2_), "rope 不是 90 度旋转对称"
            assert any(CV.alpha_at(b2_, w2, x, y) == 0
                       for y in range(h2) for x in range(w2)), "rope 全不透明，铺不出虚线"
    log.append("cloth 32x32 / rope 8x8：跨接缝突变度 <= 图内最差相邻突变度（零预算，且内部起伏"
               "过防空转门）；rope 另过 90 度旋转对称且确含透明缺口")

    # —— 6 强调色独占（只给可点物）
    accent = pal("accent")
    offenders = []
    for sheet in GM.SHEETS:
        w3, h3, b3 = built[sheet]
        hit = any(CV.rgb_at(b3, w3, x, y) == accent for y in range(h3) for x in range(w3))
        if hit != (sheet in ACCENT_ALLOWED):
            offenders.append(sheet)
    assert not offenders, f"强调色越界/缺席：{offenders}"
    num["accent_sheets"] = sorted(ACCENT_ALLOWED)
    log.append(f"铜高光逐张核对：只出现在 {sorted(ACCENT_ALLOWED)} 三张可点物件上，"
               f"包角/铆钉/铜条走 cup_hi 派生档")

    # —— 7 面板框厚 <= N（厚度取自契约）
    bands = CONTRACT.frame_bands(GM.PANEL_KEY)
    total = sum(b[2] for b in bands)
    assert total <= CONTRACT.t("panel").n, (total, CONTRACT.t("panel").n)
    num["panel_frame_px"] = total
    log.append(f"panel 框体系由契约导出：{'+'.join(str(b[2]) for b in bands)} = {total}px "
               f"<= N={CONTRACT.t('panel').n}px（keyline/木框/内圈/铜线，槽外还留 "
               f"{CONTRACT.t('panel').n - total}px 布带）")

    # —— 8 槽心零环带色：空槽与有货槽的差别只可能来自物品本体
    for key, names in (("slot", GM.SLOT_RINGS), ("slot_tall", GM.SLOT_TALL_RINGS)):
        w4, h4, b4 = built[key]
        n4 = CONTRACT.t(key).n
        inner = {CV.rgb_at(b4, w4, x, y) for y in range(n4, h4 - n4) for x in range(n4, w4 - n4)}
        assert not (inner & {pal(nm) for nm in names}), key
        num[key + "_center_colors"] = len(inner)
    log.append("槽心不含任何环带色：空槽看得见靠的是描边，不会被贴图自己冒充成「有货」")
    return log, num


# ================================================================ 输出与落地

def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def emit(built, out_dir) -> list[tuple[str, int, str]]:
    out_dir.mkdir(parents=True, exist_ok=True)
    rows = []
    for sheet in GM.SHEETS:
        data = png_of(sheet, built)
        (out_dir / (res_name(sheet) + ".png")).write_bytes(data)
        rows.append((res_name(sheet), len(data), sha(data)))
    assert len(rows) == len(GM.SHEETS) > 0
    return rows


def snapshot(d) -> dict:
    if not d.exists():
        return {}
    return {p.name: sha(p.read_bytes()) for p in sorted(d.glob("*")) if p.is_file()}


def land(built, log: list[str]) -> list[tuple[str, int, str]]:
    """落地。守卫两问：(a) 改动集必须是本批；(b) 本批之外的既有文件逐字节不变。"""
    before = snapshot(GM.LAND_DIR)
    GM.LAND_DIR.mkdir(parents=True, exist_ok=True)
    mine, rows = set(), []
    for sheet in GM.SHEETS:
        name = res_name(sheet) + ".png"
        data = png_of(sheet, built)
        (GM.LAND_DIR / name).write_bytes(data)
        mine.add(name)
        rows.append((name, len(data), sha(data)))
    after = snapshot(GM.LAND_DIR)
    changed = {k for k in set(after) | set(before) if after.get(k) != before.get(k)}
    assert changed <= mine, f"落地越界：{sorted(changed - mine)}"
    for k, v in before.items():
        if k in mine:
            continue          # ★本批目标正是本操作要写的对象，纳入断言会让本批永远落不了地
        assert after.get(k) == v, f"既有资产 {k} 被改动"
    log.append(f"落地 {GM.LAND_DIR.name} 目录：本批 {len(mine)} 张；本批之外既有文件 "
               f"{len(set(before) - mine)} 个逐字节未变")
    assert len(rows) == len(GM.SHEETS) > 0
    return rows


def fmt_num(v):
    if isinstance(v, (int, float)):
        return str(v) if isinstance(v, int) else str(round(v, 3))
    if isinstance(v, (list, tuple, set, frozenset)):
        return ",".join(str(z) for z in sorted(v))
    return str(v)


def main() -> int:
    args = sys.argv[1:]
    built = build_all()
    log, num = self_check(built)
    out = Path(args[args.index("--out") + 1]).resolve() if "--out" in args else GM.OUT

    if "--report" in args:
        for k in sorted(num):
            print(f"NUM {k} = {fmt_num(num[k])}")
        for ln in log:
            print("CHK", ln)
        for ln in PROV:
            print("PROV", ln)
        print("OK 自检通过，未写盘")
        return 0

    rows = emit(built, out)
    print(f"OUT {out} 共 {len(rows)} 张")
    for name, size, s in rows:
        print(f"SHA {name} {size} {s}")
    for k in sorted(num):
        print(f"NUM {k} = {fmt_num(num[k])}")
    for ln in log:
        print("CHK", ln)
    for ln in PROV:
        print("PROV", ln)

    if "--land" in args:
        assert "--i-have-authorization" in args, "落地需 --land 与 --i-have-authorization 双旗标"
        for name, size, s in land(built, log):
            print(f"LAND {name} {size} {s[:12]}")
        print("LANDGUARD " + log[-1])
    return 0


if __name__ == "__main__":
    sys.exit(main())
