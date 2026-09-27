"""Replace a string value in a binary resources.arsc (the app name shown under the launcher icon).
The value must occur exactly once in the global string pool, so nothing else changes with it."""
import struct, sys
sys.path.insert(0, __import__("os").path.dirname(__file__))
from stringpool import rewrite

path, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
b = open(path, "rb").read()
assert struct.unpack_from("<HH", b, 0) == (0x0002, 12)
hits = []


def fix(s):
    if s == old:
        hits.append(s)
        return new
    return s


out = bytearray(rewrite(b, 12, fix))
assert len(hits) == 1, hits
struct.pack_into("<I", out, 4, len(out))
open(path, "wb").write(out)
print(f"label: {old!r} -> {new!r}")
