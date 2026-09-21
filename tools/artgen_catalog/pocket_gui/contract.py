"""C2 装饰贴图契约的**唯一解析入口**——不抄表，直接读契约源文件。

契约源（R75 钉定，单源，不得在本文件重述任何尺寸/名称/N 值/色值）：
  `plan/assest/pocket-ui-mockups-2.html`
    - C2 的 `tex` 数组：账本指认在第 355-366 行；本文件按行号区间**取证并断言**（区间错=契约漂移，直接抛）
    - C2 的 `paint` 色值块：配色权威，由 `palette.py` 按 (key, 角色, 序号) 选择子取用

设计纪律：
1. **名称 / 像素尺寸 / 是否 9-slice / N 值 / 用途**全部由 `tex` 数组给出，本模块只解析不改写；
   生成脚本取尺寸时必须回到这里，禁止在绘图函数里出现第二个宽度。
2. **色值一个字都不在这里落地**——本模块只返回 CSS 声明解析后的结构化结果，由 `palette.py` 命名。
   （"单一真值"最先破功的地方往往是注释里重述了一遍常量，所以本文件注释也不写色值。）
3. 9-slice 硬门 `min(宽,高) >= 2N+1`（MUI2 `adaptable(N)` 口径）在此处一次性咬住：
   契约里任何一条不过即抛错，绘图阶段拿不到坏契约。
4. C1 的 `cell` 声明**只读不落地**，用于 `gen_pocket_gui.py` 量化「C2 凹槽双向描边 vs C1 单侧」
   的对比度优势（R75 说这是 C2 相对 C1 的唯一可读性优势，必须可证而不是一句口号）。
5. 本目录自写脚本一律**不出现反斜杠字面量**：正则全部用字符类拼（`[[]`、`[0-9]`、`[A-Za-z_]`），
   换行用 `chr(10)`。这样"禁反斜杠"可以机检而不用给谁开豁免。
"""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
SRC_HTML = ROOT / "plan" / "assest" / "pocket-ui-mockups-2.html"

# R75 指认的 tex 数组行号闭区间（1-based，含首尾）。tex 条目必须落在这里面。
TEX_LINE_LO, TEX_LINE_HI = 355, 366

MUL = chr(0xD7)      # 契约里的 '32x32' 用乘号而不是字母 x
NDASH = chr(0x2014)  # '—' 占位符：无 N 值

HEXC = "[0-9A-Fa-f]"
Q = "'"                                 # 契约数组用单引号包字段
CELL_Q = Q + "([^']*)" + Q              # 一个带捕获组的字段（引号本身也要匹配，否则错位）
_SEP = " *, *"
# 只靠"五个引号字段"的形状认行；方括号不进正则（Python 会对方括号里的集合发 Nested 警告，
# 而把 '[' 转义又要反斜杠，与本目录纪律冲突）。条目是否属于 C2 由后面的 startswith 断言负责。
_RE_TEX_ROW = re.compile(_SEP.join([CELL_Q] * 5))
_RE_PAINT_ITEM = re.compile("([A-Za-z_][A-Za-z0-9_]*) *: *'([^']*)'")
_RE_HEX = re.compile("#" + HEXC * 6)
_RE_N = re.compile("N=([0-9]+)")
_RE_SPEC = re.compile("([A-Za-z_][A-Za-z0-9_]*) *= *([0-9]+)")
_RE_NUM = re.compile("-?[0-9]+")


class ContractError(AssertionError):
    """契约漂移：源文件与 R75 指认的形状不再吻合，禁止继续生成。"""


# ---------------------------------------------------------------- tex 条目


class Tex:
    __slots__ = ("token", "w", "h", "slice", "n", "usage", "line")

    def __init__(self, token, w, h, sliced, n, usage, line):
        self.token = token        # 契约原样名，如 POCKET_C2_panel
        self.w, self.h = w, h
        self.slice = sliced       # True = 9-slice
        self.n = n                # 9-slice 边距；None = 非 9-slice
        self.usage = usage
        self.line = line          # 取证行号

    @property
    def key(self) -> str:
        """去掉 POCKET_C2_ 前缀后的契约键（panel / slot / ...）。"""
        pre = "POCKET_C2_"
        assert self.token.startswith(pre), self.token
        return self.token[len(pre):]

    @property
    def res_name(self) -> str:
        """落地文件名（不含扩展名）= 契约 token **逐字**，不转小写、不另造名。

        口径来源：同仓并行消费片 `PocketGuiTextureContract`（R75 要求"契约名与尺寸逐字对账"）。
        1.7.10 的 GUI 贴图走 `textures/gui/...` 完整路径直载，不受 block/item 图集的小写规则约束，
        所以这里保持 token 原样；若改名，改的是消费片那张表与本属性，不是像素。
        """
        return self.token

    def __repr__(self):
        return (f"Tex({self.key} {self.w}x{self.h} "
                f"slice={self.slice} n={self.n} L{self.line})")


def _parse_size(s: str) -> tuple[int, int]:
    parts = s.split(MUL)
    if len(parts) != 2:
        raise ContractError(f"尺寸字段不是 'W{MUL}H'：{s!r}")
    return int(parts[0]), int(parts[1])


def _parse_slice(s: str) -> bool:
    if s == "是":
        return True
    if s.startswith("否"):        # '否' / '否（平铺）'
        return False
    raise ContractError(f"无法识别的 9-slice 标记：{s!r}")


def _parse_n(s: str) -> int | None:
    s = s.strip()
    if s in (NDASH, "", "-"):
        return None
    m = _RE_N.fullmatch(s)
    if not m:
        raise ContractError(f"无法识别的 N 值：{s!r}")
    return int(m.group(1))


# ---------------------------------------------------------------- paint 声明


class Decl:
    """一条 CSS 短声明的结构化结果（值仍留在契约侧，这里只保留角色槽位）。"""

    __slots__ = ("bg", "border", "border_w", "border_style", "color", "shadows", "raw")

    def __init__(self, bg, border, border_w, border_style, color, shadows, raw):
        self.bg = bg
        self.border = border
        self.border_w = border_w
        self.border_style = border_style
        self.color = color
        self.shadows = shadows      # 列表：按声明顺序（越靠前越在顶层）
        self.raw = raw

    def spread(self, i: int) -> int:
        return self.shadows[i]["spread"]

    def top_spread(self, i: int) -> int:
        """该阴影层的**可见**厚度：被更靠前的同族阴影盖住的部分要扣掉。"""
        lim = max([0] + [s["spread"] for s in self.shadows[:i]])
        v = self.shadows[i]["spread"] - lim
        assert v >= 0, (i, self.shadows)
        return v


def _nums(s: str) -> list[int]:
    """CSS 长度列表 -> 整数；'0' 允许无单位（CSS 里零可以省 px，这是解析失败的第一现场）。"""
    out = []
    for tok in s.replace("inset", " ").split():
        if tok.endswith("px"):
            tok = tok[:-2]
        if _RE_NUM.fullmatch(tok):
            out.append(int(tok))
    return out


def _parse_decl(text: str) -> Decl:
    bg = border = border_style = color = raw = None
    border_w = 0
    shadows: list[dict] = []
    if text.strip().startswith("#") and ";" not in text:
        raw = _RE_HEX.findall(text)[0]
    for tok in text.split(";"):
        tok = tok.strip()
        if not tok:
            continue
        k, _, v = tok.partition(":")
        k, v = k.strip(), v.strip()
        if k == "background":
            bg = _RE_HEX.findall(v)[0]
        elif k == "border":
            nums = _nums(v)
            if nums:
                border_w = nums[0]
            hs = _RE_HEX.findall(v)
            if hs:
                border = hs[0]
            for sty in ("solid", "dashed", "dotted"):
                if sty in v:
                    border_style = sty
        elif k == "box-shadow":
            for layer in v.split(","):
                nums = _nums(layer)
                hs = _RE_HEX.findall(layer)
                if len(nums) == 3:
                    ox, oy, blur, spread = nums[0], nums[1], nums[2], 0
                elif len(nums) == 4:
                    ox, oy, blur, spread = nums
                else:
                    raise ContractError(f"无法解析的 box-shadow 层：{layer!r}")
                shadows.append({
                    "inset": "inset" in layer, "ox": ox, "oy": oy,
                    "blur": blur, "spread": spread, "hex": hs[0],
                })
        elif k == "color":
            color = _RE_HEX.findall(v)[0]
    return Decl(bg, border, border_w, border_style, color, shadows, raw)


# ---------------------------------------------------------------- 装载


class Contract:
    def __init__(self, tex: list[Tex], paint: dict[str, Decl], c1_paint: dict[str, Decl],
                 html_lines: list[str], spec: dict[str, int]):
        self.tex = tex
        self.by_key = {t.key: t for t in tex}
        self.paint = paint
        self.c1_paint = c1_paint
        self.html_lines = html_lines
        self.spec = spec

    def t(self, key: str) -> Tex:
        return self.by_key[key]

    def hexa(self, key: str, role: str, idx: int = 0) -> str:
        """(key, 角色, 序号) 选择子 -> 契约里的色值字符串（单源，不改写）。"""
        d = self.paint[key]
        if role == "shadow":
            v = d.shadows[idx]["hex"]
        else:
            v = {"bg": d.bg, "border": d.border, "color": d.color, "raw": d.raw}[role]
        if v is None:
            raise ContractError(f"契约 C2.paint.{key} 缺 {role}{idx} 槽位")
        return v

    def c1_hexa(self, key: str, role: str, idx: int = 0) -> str:
        d = self.c1_paint[key]
        plain = {"bg": d.bg, "border": d.border, "color": d.color, "raw": d.raw}
        v = d.shadows[idx]["hex"] if role == "shadow" else plain[role]
        if v is None:
            raise ContractError(f"契约 C1.paint.{key} 缺 {role}{idx} 槽位")
        return v

    def str_list(self, js_name: str) -> list[str]:
        """读契约里的 `var <js_name> = ['a', 'b', ...]`（预览板的假物品色与有货位同样单源）。"""
        for ln in self.html_lines:
            s = ln.strip()
            if s.startswith("var " + js_name):
                items = re.findall(Q + "[^" + Q + "]" + "*" + Q, s)
                if not items:
                    items = [z for z in s.split("[", 1)[1].split("]", 1)[0].split(",")
                             if z.strip().strip(Q)]
                return [z.strip().strip(Q) for z in items]
        raise ContractError(f"契约里找不到 var {js_name}")

    def int_list(self, js_name: str) -> list[int]:
        for ln in self.html_lines:
            s = ln.strip()
            if s.startswith("var " + js_name):
                body = s.split("[", 1)[1].split("]", 1)[0]
                nums = _RE_NUM.findall(body)
                if not nums:
                    raise ContractError(f"var {js_name} 里没数字")
                return [int(z) for z in nums]
        raise ContractError(f"契约里找不到 var {js_name}")

    def frame_bands(self, key: str) -> list[tuple[str, int, int]]:
        """把一条 paint 声明的边框体系拆成**由外向内**的环带：(层别, 阴影序号, 可见厚度 px)。

        层别 outer = 非 inset 阴影（画在盒子外），border = `border:Npx`，inset = 内阴影。
        厚度全部来自契约里的 px 字面量：内阴影按 CSS 叠放次序扣除被前一层盖住的部分，
        所以"框有多厚"这件事在整条管线上只有契约一个来源，脚本不重述。
        """
        d = self.paint[key]
        out: list[tuple[str, int, int]] = []
        lim = 0
        for idx, s in enumerate(d.shadows):
            if s["inset"]:
                continue
            vis = s["spread"] - lim
            if vis > 0:
                out.append(("outer", idx, vis))
            lim = max(lim, s["spread"])
        if d.border_w:
            out.append(("border", -1, d.border_w))
        lim = 0
        for idx, s in enumerate(d.shadows):
            if not s["inset"]:
                continue
            vis = s["spread"] - lim
            if vis > 0:
                out.append(("inset", idx, vis))
            lim = max(lim, s["spread"])
        return out


def _paint_region(lines: list[str], obj: str) -> dict[str, Decl]:
    """取 `<obj>: {` 对象里 paint 块的 key -> 声明。"""
    open_ln = [i for i, ln in enumerate(lines) if ln.strip() == f"{obj}: {{"]
    if len(open_ln) != 1:
        raise ContractError(f"{obj} 对象定位失败（命中 {len(open_ln)} 处）")
    lo = open_ln[0]
    try:
        p_lo = next(i for i in range(lo, len(lines)) if lines[i].strip().startswith("paint: {"))
        r_lo = next(i for i in range(p_lo, len(lines)) if lines[i].strip().startswith("role: {"))
    except StopIteration:
        raise ContractError(f"{obj} 内 paint/role 块定位失败")
    return {k: _parse_decl(v) for k, v in _RE_PAINT_ITEM.findall(chr(10).join(lines[p_lo:r_lo]))}


def _parse_spec(lines: list[str]) -> dict[str, int]:
    """几何 SPEC 常量（栅格/面板外边距/列间距）同样只在契约文件里存一次，这里只读不抄。"""
    hit = None
    for ln in lines:
        s = ln.strip()
        if s.startswith("var GRID") and "MARGIN" in s and "COLGAP" in s:
            hit = s
            break
    if hit is None:
        raise ContractError("找不到几何 SPEC 行（var GRID = ...）")
    got = {k: int(v) for k, v in _RE_SPEC.findall(hit)}
    for k in ("GRID", "MARGIN", "COLGAP"):
        if k not in got:
            raise ContractError(f"SPEC 行缺 {k}")
    return got


def t_by_key(tex: list[Tex], key: str) -> Tex | None:
    for t in tex:
        if t.key == key:
            return t
    return None


def load() -> Contract:
    text = SRC_HTML.read_text(encoding="utf-8")
    lines = text.splitlines()

    paint = _paint_region(lines, "C2")
    for need in ("panel", "col", "cell", "fluid", "fluidcol", "aspect", "distill", "text",
                 "legend", "coin", "bind", "btn", "prog", "hole", "labelColor"):
        if need not in paint:
            raise ContractError(f"C2.paint 缺 key {need}（契约结构变了）")
    c1_paint = _paint_region(lines, "C1")

    # --- C2 tex 数组：只在 R75 指认的行区间内取证 ---
    lo0 = TEX_LINE_LO - 1
    hi0 = min(TEX_LINE_HI, len(lines))
    window = lines[lo0:hi0]
    if not (window and window[0].strip().startswith("tex: [")):
        raise ContractError(f"R75 指认的第 {TEX_LINE_LO} 行不是 tex 数组起始，行号已漂移")
    tex: list[Tex] = []
    for off, ln in enumerate(window):
        m = _RE_TEX_ROW.search(ln)
        if not m:
            continue
        token, size, sl, nval, usage = m.groups()
        if not token.startswith("POCKET_C2_"):
            raise ContractError(f"行 {TEX_LINE_LO + off} 上的条目不属于 C2：{token}")
        w, h = _parse_size(size)
        tex.append(Tex(token, w, h, _parse_slice(sl), _parse_n(nval), usage, TEX_LINE_LO + off))
    if len(tex) != 11:
        raise ContractError(f"tex 条目数异常（{len(tex)}，期望 11），解析形状失配")

    # --- 9-slice 硬门：min(边) >= 2N+1 ---
    for t in tex:
        if t.slice:
            need = 2 * t.n + 1
            if min(t.w, t.h) < need:
                raise ContractError(
                    f"{t.token} 违反 9-slice 硬门：min({t.w},{t.h}) < 2*{t.n}+1 = {need}")
        elif t.n is not None:
            raise ContractError(f"{t.token} 非 9-slice 却带了 N 值")
    # --- 契约点名的「非 9-slice 1:1 贴」缺 N 值才叫自洽；角部/铆钉必须真的是 1:1 ---
    for k in ("corner", "rivet"):
        t = t_by_key(tex, k)
        assert t and not t.slice and t.n is None, k
    return Contract(tex, paint, c1_paint, lines, _parse_spec(lines))


CONTRACT = load()

if __name__ == "__main__":
    for t in CONTRACT.tex:
        print(t, "|", t.usage)
    print("paint keys:", sorted(CONTRACT.paint))
    print("spec:", CONTRACT.spec)
    print("panel frame bands:", CONTRACT.frame_bands("panel"))
