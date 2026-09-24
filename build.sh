#!/usr/bin/env bash
# Rebuilds dist/PhoneTemp-1.0.1-debug.apk from original/PhoneTemp-1.0.0-debug.apk:
#   - all text uses the device system font (FontFamily.Default) instead of bundled Manrope / DM Mono
#   - the thermal ring fill is a temperature colour ramp (cool -> normal -> warm -> hot -> critical)
set -euo pipefail
cd "$(dirname "$0")"
TOOLS=.tools; mkdir -p "$TOOLS" dist
[ -f $TOOLS/apktool.jar ] || curl -sSL -o $TOOLS/apktool.jar https://github.com/iBotPeaches/Apktool/releases/download/v2.10.0/apktool_2.10.0.jar
[ -f $TOOLS/signer.jar ]  || curl -sSL -o $TOOLS/signer.jar  https://github.com/patrickfav/uber-apk-signer/releases/download/v1.3.0/uber-apk-signer-1.3.0.jar
WORK=$(mktemp -d)
java -jar $TOOLS/apktool.jar d -r -f -o "$WORK/src" original/PhoneTemp-1.0.0-debug.apk
python3 patch/gen.py
python3 patch/apply.py "$WORK/src"
java -jar $TOOLS/apktool.jar b "$WORK/src" -o "$WORK/unsigned.apk"
java -jar $TOOLS/signer.jar -a "$WORK/unsigned.apk" -o "$WORK/signed"
cp "$WORK"/signed/*-debugSigned.apk dist/PhoneTemp-1.0.1-debug.apk
rm -rf "$WORK"
echo "Built dist/PhoneTemp-1.0.1-debug.apk"
