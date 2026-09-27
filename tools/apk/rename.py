"""Give a binary AndroidManifest.xml its own package name (apktool -r leaves the manifest binary).

Replaces the package attribute, the app's signature permission and the androidx-startup provider
authority. Two apps may not define the same permission or provider authority, so renaming only the
package would still be refused next to the original build. Class names in this manifest are fully
qualified, so they stay.
"""
import struct, sys
sys.path.insert(0, __import__("os").path.dirname(__file__))
from stringpool import rewrite

path, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
b = open(path, "rb").read()
assert struct.unpack_from("<H", b, 0)[0] == 0x0003
targets = {old, old + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION", old + ".androidx-startup"}
seen = set()


def fix(s):
    if s in targets:
        seen.add(s)
        return new + s[len(old):]
    return s


out = bytearray(rewrite(b, 8, fix))
assert seen == targets, seen
struct.pack_into("<I", out, 4, len(out))
open(path, "wb").write(out)
print("renamed:", ", ".join(sorted(seen)))
