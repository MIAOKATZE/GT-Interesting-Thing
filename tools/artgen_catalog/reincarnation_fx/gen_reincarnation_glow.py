#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""gtit 周目系统 · 升天/发放演出 glow 光斑贴图（16x16 径向渐变）

任务包 S8 资产项：为 client.fx 的 additive glow billboard（GTSR 手法，
GL_SRC_ALPHA/GL_ONE + 全亮光值）生成 16x16 径向渐变光斑，落地
src/main/resources/assets/gtit/textures/misc/reincarnation_glow.png。

设计（纯函数，零随机源，双跑逐字节一致）：
- 几何：16x16，中心 (7.5, 7.5)，像素中心距 r；归一化 d = r / 8.0；
- alpha：硬截止 d <= 1.0，falloff = (1 - d^2)^1.6（中心饱满、边缘快收，
  additive 叠加无方形边）；
- 颜色：白核 -> 青蓝边（与 billboard tint (0.75, 0.95, 1.0) 同系），
  lerp(d, 白(255,255,255), 青(190,240,255))；
- 量化：int(x * 255 + 0.5)，确定性舍入；
- PNG 保存参数固定（无 optimize/时间戳项），双跑 SHA256 断言一致。

幂等落地：目标文件 SHA 等于本次产物则跳过写入（保留 mtime），
否则原子覆写；stdout 输出 sha256= 行供任务包自证采集。
"""
import hashlib
import io
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
LAND_DIR = os.path.normpath(os.path.join(
    HERE, "..", "..", "..", "src", "main", "resources", "assets", "gtit",
    "textures", "misc"))
LAND_PNG = os.path.join(LAND_DIR, "reincarnation_glow.png")

SIZE = 16
# 色彩端点（白核 / 青蓝边，与 Java 消费层 billboard tint 同色系）
CORE = (255, 255, 255)
EDGE = (190, 240, 255)
ALPHA_EXP = 1.6


def build_pixels():
    """确定性像素生成：返回 RGBA 四通道字节序列（纯 t 值函数）。"""
    rgba = bytearray(SIZE * SIZE * 4)
    half = SIZE / 2.0
    for y in range(SIZE):
        for x in range(SIZE):
            d = (((x + 0.5) - half) ** 2 + ((y + 0.5) - half) ** 2) ** 0.5 / half
            i = (y * SIZE + x) * 4
            if d >= 1.0:
                continue  # 透明（bytearray 零默认）
            fall = (1.0 - d * d) ** ALPHA_EXP
            rgba[i] = int(CORE[0] + (EDGE[0] - CORE[0]) * d + 0.5)
            rgba[i + 1] = int(CORE[1] + (EDGE[1] - CORE[1]) * d + 0.5)
            rgba[i + 2] = int(CORE[2] + (EDGE[2] - CORE[2]) * d + 0.5)
            rgba[i + 3] = int(fall * 255.0 + 0.5)
    return bytes(rgba)


def build_png_bytes():
    """内存构建 PNG bytes（固定保存参数，确定性输出）。"""
    img = Image.frombytes("RGBA", (SIZE, SIZE), build_pixels())
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


def main():
    data = build_png_bytes()
    digest = hashlib.sha256(data).hexdigest()

    # 自证 1：双跑内存构建逐字节一致（纯函数无随机源）
    again = build_png_bytes()
    assert again == data, "double-build mismatch: generator is not deterministic"
    # 自证 2：可往返解码且尺寸/模式正确
    rt = Image.open(io.BytesIO(data))
    assert rt.size == (SIZE, SIZE) and rt.mode == "RGBA", (
        "roundtrip mismatch: %s %s" % (rt.size, rt.mode))
    # 自证 3：alpha 剖面合理（中心近全不透明、边缘透明、非退化）
    # 注：16x16 像素中心不经过几何中心 (7.5,7.5)，中心像素 d≈0.088 -> alpha≈252
    px = rt.load()
    assert px[7, 7][3] >= 250, "center must be near-opaque, got %d" % (px[7, 7][3],)
    assert px[0, 0][3] == 0, "corner must be fully transparent"
    opaque = sum(1 for yy in range(SIZE) for xx in range(SIZE) if px[xx, yy][3] > 0)
    assert opaque > SIZE * SIZE / 2, "glow too thin: opaque=%d" % opaque

    existed = os.path.exists(LAND_PNG)
    if existed:
        with open(LAND_PNG, "rb") as fh:
            if fh.read() == data:
                print("sha256=%s" % digest)
                print("land=%s (unchanged, idempotent skip)" % LAND_PNG)
                print("opaque_px=%d/%d" % (opaque, SIZE * SIZE))
                return 0

    os.makedirs(LAND_DIR, exist_ok=True)
    tmp = LAND_PNG + ".tmp"
    with open(tmp, "wb") as fh:
        fh.write(data)
    os.replace(tmp, LAND_PNG)
    print("sha256=%s" % digest)
    print("land=%s (%s)" % (LAND_PNG, "overwritten" if existed else "created"))
    print("opaque_px=%d/%d" % (opaque, SIZE * SIZE))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
