"""像素原语 + 确定性 LCG（本目录不依赖 numpy，全部直接写 RGBA 字节缓冲）。

约定：
- 缓冲 = `bytearray(w*h*4)`，行主序 RGBA，与 `pngwrite.encode_png` 的输入口径一致。
- 坐标 x 向右、y 向下（与 MC GUI 贴图像素一致）。
- 所有随机性只允许经 `lcg_next` / `lcg_hash2` 两个纯函数产生：**按位置定值**，
  不按遍历顺序推进状态。这样平铺件换尺寸重排也不会变样，双跑自然逐字节一致。
"""
from __future__ import annotations

LCG_MUL = 1103515245
LCG_ADD = 12345
LCG_MOD = 0x7FFFFFFF


def lcg_next(state: int) -> int:
    return (state * LCG_MUL + LCG_ADD) & LCG_MOD


def lcg_hash2(x: int, y: int, seed: int) -> int:
    """位置 -> 定值。纯函数，无全局状态。"""
    s = (x * 374761393 + y * 668265263 + seed) & LCG_MOD
    s = lcg_next(s)
    s = lcg_next(s)
    return s


# ---------------------------------------------------------------- 基本读写


def blank(w: int, h: int) -> bytearray:
    return bytearray(w * h * 4)


def put(buf: bytearray, w: int, x: int, y: int, rgb, a: int = 255) -> None:
    i = (y * w + x) * 4
    buf[i] = rgb[0]
    buf[i + 1] = rgb[1]
    buf[i + 2] = rgb[2]
    buf[i + 3] = a


def rgb_at(buf: bytearray, w: int, x: int, y: int) -> tuple[int, int, int]:
    i = (y * w + x) * 4
    return buf[i], buf[i + 1], buf[i + 2]


def alpha_at(buf: bytearray, w: int, x: int, y: int) -> int:
    return buf[(y * w + x) * 4 + 3]


def rgba_at(buf: bytearray, w: int, x: int, y: int) -> tuple[int, int, int, int]:
    i = (y * w + x) * 4
    return buf[i], buf[i + 1], buf[i + 2], buf[i + 3]


def opaque(buf: bytearray, w: int, x: int, y: int) -> bool:
    return alpha_at(buf, w, x, y) == 255


def rect(buf: bytearray, w: int, x0: int, y0: int, x1: int, y1: int, rgb, a: int = 255) -> None:
    """闭区间矩形。"""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(buf, w, x, y, rgb, a)


def depth_at(w: int, h: int, x: int, y: int) -> int:
    """到最近边缘的切比雪夫距离（d=0 即最外一圈）——同心环的基础量。"""
    return min(x, y, w - 1 - x, h - 1 - y)


def ring(buf: bytearray, w: int, h: int, d: int, rgb, skip=None) -> None:
    """整圈描边。同心环对 9-slice 天然安全：沿被拉伸的那根轴恒定。"""
    for y in range(h):
        for x in range(w):
            if depth_at(w, h, x, y) == d and (skip is None or not skip(x, y)):
                put(buf, w, x, y, rgb)


def blit_over(dst: bytearray, dw: int, dh: int, src: bytearray, sw: int, sh: int,
              ox: int, oy: int) -> None:
    """把 src 的**不透明**像素盖到 dst 的 (ox,oy)。越界裁剪（1:1 贴语义）。"""
    for y in range(sh):
        ty = oy + y
        if not (0 <= ty < dh):
            continue
        for x in range(sw):
            tx = ox + x
            if not (0 <= tx < dw):
                continue
            i = (y * sw + x) * 4
            if src[i + 3] == 0:
                continue
            j = (ty * dw + tx) * 4
            dst[j:j + 4] = src[i:i + 4]


# ---------------------------------------------------------------- 形状


def octagon_mask(size: int, chamfer: int) -> list[list[int]]:
    """四角等量切角的方片掩码（1=在内部）。切角等量 => 90 度旋转对称，一张供四角。"""
    last = size - 1
    m = []
    for y in range(size):
        row = []
        for x in range(size):
            keep = (x + y >= chamfer) and (last - x + y >= chamfer)
            keep = keep and (x + last - y >= chamfer) and (last - x + last - y >= chamfer)
            row.append(1 if keep else 0)
        m.append(row)
    return m


def disc_mask(size: int) -> list[list[int]]:
    """近圆掩码；用切比雪夫+曼哈顿的组合保证沿两轴与两条对角线都镜像对称。"""
    c = (size - 1) / 2
    m = []
    for y in range(size):
        row = []
        for x in range(size):
            dx, dy = abs(x - c), abs(y - c)
            r = (dx * dx + dy * dy) ** 0.5
            row.append(1 if r <= c + 0.25 else 0)
        m.append(row)
    return m


def depth_map(mask: list[list[int]]) -> list[list[int]]:
    """掩码内部像素到"掩码外/数组外"的 8 邻域 BFS 环数：0 = 边界一圈。"""
    n = len(mask)

    def inside(y: int, x: int) -> bool:
        return 0 <= y < n and 0 <= x < n and bool(mask[y][x])

    dep = [[-1] * n for _ in range(n)]
    frontier = []
    for y in range(n):
        for x in range(n):
            if not inside(y, x):
                continue
            touching_out = any(not inside(y + dy, x + dx)
                               for dy in (-1, 0, 1) for dx in (-1, 0, 1)
                               if dy or dx)
            if touching_out:
                dep[y][x] = 0
                frontier.append((y, x))
    while frontier:
        nxt = []
        for y, x in frontier:
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    ny, nx = y + dy, x + dx
                    if inside(ny, nx) and dep[ny][nx] < 0:
                        dep[ny][nx] = dep[y][x] + 1
                        nxt.append((ny, nx))
        frontier = nxt
    return [[0 if v < 0 else v for v in row] for row in dep]


def checker(w: int, h: int, cell: int, rgb_a, rgb_b) -> bytearray:
    buf = blank(w, h)
    for y in range(h):
        for x in range(w):
            c = rgb_a if ((x // cell) + (y // cell)) % 2 == 0 else rgb_b
            put(buf, w, x, y, c)
    return buf


# ---------------------------------------------------------------- PIL 互转


def to_image(w: int, h: int, buf: bytearray):
    from PIL import Image
    return Image.frombytes("RGBA", (w, h), bytes(buf))


def read_png(path):
    from PIL import Image
    im = Image.open(path).convert("RGBA")
    return im.width, im.height, bytearray(im.tobytes())


def scale_nearest(w: int, h: int, buf: bytearray, k: int):
    from PIL import Image
    return to_image(w, h, buf).resize((w * k, h * k), Image.NEAREST)


def nine_slice(w: int, h: int, buf: bytearray, n: int, tw: int, th: int) -> bytearray:
    """按契约 N 值做 9-slice 出图（预览板用；与 MUI2 `adaptable(N)` 同一口径）。"""
    out = blank(tw, th)
    sx = [0, n, w - n, w]
    sy = [0, n, h - n, h]
    dx = [0, n, tw - n, tw]
    dy = [0, n, th - n, th]
    for by in range(3):
        for bx in range(3):
            sw, sh = sx[bx + 1] - sx[bx], sy[by + 1] - sy[by]
            dw, dh = dx[bx + 1] - dx[bx], dy[by + 1] - dy[by]
            if sw <= 0 or sh <= 0 or dw <= 0 or dh <= 0:
                continue
            im = to_image(w, h, buf).crop((sx[bx], sy[by], sx[bx] + sw, sy[by] + sh))
            if (sw, sh) != (dw, dh):
                from PIL import Image
                im = im.resize((dw, dh), Image.NEAREST)
            blit_over(out, tw, th, bytearray(im.tobytes()), im.width, im.height,
                      dx[bx], dy[by])
    return out
