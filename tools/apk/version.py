"""Set android:versionCode in a binary AndroidManifest.xml (apktool -r leaves it binary and ignores
apktool.yml's versionInfo)."""
import struct, sys

VERSION_CODE = 0x0101021B
path, code = sys.argv[1], int(sys.argv[2])
b = bytearray(open(path, "rb").read())
pos, ids, done = 8, [], 0
while pos < len(b):
    ctype, hsize, csize = struct.unpack_from("<HHI", b, pos)
    if ctype == 0x0180:                                   # resource map: string index -> attr id
        ids = list(struct.unpack_from(f"<{(csize - 8) // 4}I", b, pos + 8))
    elif ctype == 0x0102:                                 # start tag
        start, size, count = struct.unpack_from("<HHH", b, pos + 24)
        for i in range(count):
            a = pos + 16 + start + i * size
            name = struct.unpack_from("<I", b, a + 4)[0]
            if name < len(ids) and ids[name] == VERSION_CODE:
                assert b[a + 15] == 0x10, "versionCode is not an int"
                struct.pack_into("<I", b, a + 16, code)
                done += 1
    pos += csize
assert done == 1, done
open(path, "wb").write(b)
