"""Give a binary AndroidManifest.xml its own package name (apktool -r leaves the manifest binary).

Replaces the package attribute, the app's signature permission and the androidx-startup provider
authority by rebuilding the string pool. Two apps may
not define the same permission or provider authority, so renaming only the package would still be
refused next to the original build. Class names in this manifest are fully qualified, so they stay.
"""
import struct, sys

path, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
b = open(path, "rb").read()
assert struct.unpack_from("<HHI", b, 0)[0] == 0x0003
sp = 8
ctype, hsize, csize = struct.unpack_from("<HHI", b, sp)
assert ctype == 0x0001
count, styles, flags, str_start, style_start = struct.unpack_from("<IIIII", b, sp + 8)
assert styles == 0 and style_start == 0
utf8 = bool(flags & 0x100)
offsets = struct.unpack_from(f"<{count}I", b, sp + hsize)
data = sp + str_start


def read(o):
    p = data + o
    if utf8:
        n = b[p]; p += 2 if n & 0x80 else 1          # UTF-16 length, skipped
        n = b[p]
        if n & 0x80: n = ((n & 0x7F) << 8) | b[p + 1]; p += 2
        else: p += 1
        return b[p:p + n].decode("utf-8")
    n = struct.unpack_from("<H", b, p)[0]; p += 2
    if n & 0x8000: n = ((n & 0x7FFF) << 16) | struct.unpack_from("<H", b, p)[0]; p += 2
    return b[p:p + 2 * n].decode("utf-16-le")


def enc_len8(n):
    return bytes([n]) if n < 0x80 else bytes([0x80 | (n >> 8), n & 0xFF])


def write(s):
    if utf8:
        u = s.encode("utf-8")
        return enc_len8(len(s.encode("utf-16-le")) // 2) + enc_len8(len(u)) + u + b"\0"
    u = s.encode("utf-16-le"); n = len(u) // 2
    assert n < 0x8000
    return struct.pack("<H", n) + u + b"\0\0"


strings = [read(o) for o in offsets]
targets = {old, old + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION", old + ".androidx-startup"}
changed = [s for s in strings if s in targets]
assert set(changed) == targets, changed
strings = [new + s[len(old):] if s in changed else s for s in strings]
blob, offs = b"", []
for s in strings:
    offs.append(len(blob)); blob += write(s)
blob += b"\0" * (-len(blob) % 4)
header = bytearray(b[sp:sp + hsize])
new_start = hsize + 4 * count
struct.pack_into("<I", header, 4, new_start + len(blob))
struct.pack_into("<I", header, 20, new_start)
pool = bytes(header) + struct.pack(f"<{count}I", *offs) + blob
out = bytearray(b[:sp] + pool + b[sp + csize:])
struct.pack_into("<I", out, 4, len(out))
open(path, "wb").write(out)
print("renamed:", ", ".join(changed))
