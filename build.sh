#!/usr/bin/env bash
# Rebuilds dist/PhoneTemp-1.0.1-debug.apk from original/PhoneTemp-1.0.0-debug.apk:
#   - all text uses the device system font (FontFamily.Default) instead of bundled Manrope / DM Mono
#   - the thermal ring fill is a temperature colour ramp (cool -> normal -> warm -> hot -> critical)
#   - Pulse Lab gains O-Haptics Studio (feature/), haptics recreated from OnePlus's O-Haptics video
set -euo pipefail
cd "$(dirname "$0")"
TOOLS=.tools; mkdir -p "$TOOLS" dist
[ -f $TOOLS/apktool.jar ] || curl -sSL -o $TOOLS/apktool.jar https://github.com/iBotPeaches/Apktool/releases/download/v2.10.0/apktool_2.10.0.jar
[ -f $TOOLS/signer.jar ]  || curl -sSL -o $TOOLS/signer.jar  https://github.com/patrickfav/uber-apk-signer/releases/download/v1.3.0/uber-apk-signer-1.3.0.jar
WORK=$(mktemp -d)
java -jar $TOOLS/apktool.jar d -r -f -o "$WORK/src" original/PhoneTemp-1.0.0-debug.apk
python3 patch/gen.py
python3 patch/apply.py "$WORK/src"
# Decoding with -r leaves sdkInfo empty, which makes smali emit dex 035. ART rejects interface
# default methods (used by Compose and GradientRing) in dex < 037 -> crash on launch. Match the
# original APK (minSdk 26 -> dex 038).
sed -i "s/^sdkInfo:.*/sdkInfo:\n  minSdkVersion: '26'\n  targetSdkVersion: '35'/" "$WORK/src/apktool.yml"
# A versionCode above any earlier build (the original is 1), so Android treats this as an update
# instead of refusing a downgrade. Date based, so each rebuild is newer than the last.
python3 tools/apk/version.py "$WORK/src/AndroidManifest.xml" "$(date -u +%y%m%d%H)"
# Its own package name, so it installs next to any PhoneTemp already on the phone. Android refuses
# to replace an installed app (or one kept after "uninstall, keep data") signed with a different key.
python3 tools/apk/rename.py "$WORK/src/AndroidManifest.xml" com.phonetemp.app com.phonetemp.tacta
java -jar $TOOLS/apktool.jar b "$WORK/src" -o "$WORK/unsigned.apk"
# O-Haptics Studio (feature/): Kotlin/Compose compiled against the app, dexed, added as classes10.dex.
gradle --no-daemon -q :feature:dex
python3 - "$WORK/unsigned.apk" feature/build/dex/classes10.dex <<'PY'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1], "a", zipfile.ZIP_DEFLATED) as z:
    assert "classes10.dex" not in z.namelist()
    z.write(sys.argv[2], "classes10.dex")
PY
python3 tools/apk/align.py "$WORK/unsigned.apk" "$WORK/aligned.apk"
java -jar $TOOLS/signer.jar -a "$WORK/aligned.apk" -o "$WORK/signed" --skipZipAlign
cp "$WORK"/signed/*-debugSigned.apk dist/PhoneTemp-1.0.1-debug.apk
rm -rf "$WORK"
echo "Built dist/PhoneTemp-1.0.1-debug.apk"
