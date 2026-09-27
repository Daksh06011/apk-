"""Rewrite an APK so every stored entry's data is aligned: native libs to 16 KiB (Android 15+ devices
with 16 KiB pages map them straight from the APK), everything else to 4 bytes (resources.arsc must be
stored and 4-aligned for targetSdk 30+). Replaces zipalign -P 16, which the signer's bundled copy lacks.
The APK must be signed afterwards (v2/v3 only; signing does not move entries)."""
import sys, zipfile

src, dst = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(src) as zin, open(dst, "wb") as raw:
    with zipfile.ZipFile(raw, "w") as zout:
        for info in zin.infolist():
            data = zin.read(info)
            out = zipfile.ZipInfo(info.filename, info.date_time)
            out.compress_type = info.compress_type
            out.external_attr = info.external_attr
            out.create_system = info.create_system
            if info.compress_type == zipfile.ZIP_STORED:
                align = 16384 if info.filename.endswith(".so") else 4
                name_len = len(out.filename.encode("utf-8"))
                data_at = raw.tell() + 30 + name_len
                pad = (-data_at) % align
                if pad and pad < 4:      # an extra field needs a 4-byte header
                    pad += align
                # 0xd935 is the id zipalign uses for alignment padding
                out.extra = (b"\x35\xd9" + (pad - 4).to_bytes(2, "little") + b"\0" * (pad - 4)) if pad else b""
            zout.writestr(out, data)
