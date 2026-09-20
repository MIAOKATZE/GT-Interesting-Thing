"""确定性 PNG 直写（RGBA8，无时间戳/tEXt 块）——同输入逐字节一致。

物品图标候选与预览板共用本编码器；PIL 的 save() 会按环境追加块，不能保证双跑一致。
"""
from __future__ import annotations

import struct
import zlib


def chunk(kind: bytes, data: bytes) -> bytes:
    return (struct.pack(">I", len(data)) + kind + data
            + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF))


def encode_png(width: int, height: int, rgba: bytes) -> bytes:
    """rgba = 行主序 RGBA 字节流（长度 width*height*4）。"""
    assert len(rgba) == width * height * 4, "RGBA 字节流长度不符"
    stride = width * 4
    body = bytearray()
    for y in range(height):
        body += b"\x00" + rgba[y * stride:(y + 1) * stride]
    comp = zlib.compressobj(9, zlib.DEFLATED, 15)
    idat = comp.compress(bytes(body)) + comp.flush()
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", idat)
            + chunk(b"IEND", b""))


def encode_grid(size: int, rows: list[str], palette: dict[str, tuple[int, int, int]]) -> bytes:
    """16x16 字符位图清单 -> PNG 字节；'.' = 全透明。"""
    buf = bytearray()
    for row in rows:
        for ch in row:
            if ch == ".":
                buf += b"\x00\x00\x00\x00"
            else:
                r, g, b = palette[ch]
                buf += bytes((r, g, b, 255))
    return encode_png(size, size, bytes(buf))


def image_to_png(im) -> bytes:
    """PIL RGBA Image -> 确定性 PNG 字节（预览板用）。"""
    im = im.convert("RGBA")
    return encode_png(im.width, im.height, im.tobytes())
