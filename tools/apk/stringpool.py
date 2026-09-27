"""Rewrite the string pool chunk of a binary resource file (AndroidManifest.xml, resources.arsc)."""
import struct


def rewrite(b: bytes, sp: int, fix) -> bytes:
    """Return [b] with the string pool at offset [sp] replaced, every string passed through [fix].
    The caller updates any enclosing chunk sizes (by the length difference)."""
    ctype, hsize, csize = struct.unpack_from("<HHI", b, sp)
    assert ctype == 0x0001
    count, styles, flags, str_start, style_start = struct.unpack_from("<IIIII", b, sp + 8)
    assert styles == 0 and style_start == 0, "styled strings not supported"
    utf8 = bool(flags & 0x100)
    offsets = struct.unpack_from(f"<{count}I", b, sp + hsize)
    data = sp + str_start

    def read(o):
        p = data + o
        if utf8:
            p += 2 if b[p] & 0x80 else 1                 # UTF-16 length, recomputed on write
            n = b[p]
            if n & 0x80: n = ((n & 0x7F) << 8) | b[p + 1]; p += 2
            else: p += 1
            return b[p:p + n].decode("utf-8")
        n = struct.unpack_from("<H", b, p)[0]; p += 2
        if n & 0x8000: n = ((n & 0x7FFF) << 16) | struct.unpack_from("<H", b, p)[0]; p += 2
        return b[p:p + 2 * n].decode("utf-16-le")

    def len8(n):
        assert n < 0x8000
        return bytes([n]) if n < 0x80 else bytes([0x80 | (n >> 8), n & 0xFF])

    def write(s):
        if utf8:
            u = s.encode("utf-8")
            return len8(len(s.encode("utf-16-le")) // 2) + len8(len(u)) + u + b"\0"
        u = s.encode("utf-16-le"); n = len(u) // 2
        assert n < 0x8000
        return struct.pack("<H", n) + u + b"\0\0"

    blob, offs = b"", []
    for o in offsets:
        offs.append(len(blob)); blob += write(fix(read(o)))
    blob += b"\0" * (-len(blob) % 4)
    header = bytearray(b[sp:sp + hsize])
    start = hsize + 4 * count
    struct.pack_into("<I", header, 4, start + len(blob))
    struct.pack_into("<I", header, 20, start)
    return b[:sp] + bytes(header) + struct.pack(f"<{count}I", *offs) + blob + b[sp + csize:]
