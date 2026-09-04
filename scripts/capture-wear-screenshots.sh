#!/usr/bin/env bash
# Capture Wear OS UI screenshots via uiautomator navigation.
set -euo pipefail

DEVICE="${DEVICE:-emulator-5554}"
OUT_DIR="$(cd "$(dirname "$0")/.." && pwd)/docs/screenshots"
PKG="com.cbstudio.wearwallet"
ACTIVITY="${PKG}/.presentation.MainActivity"
UI_TMP="/sdcard/ui.xml"

mkdir -p "$OUT_DIR"

snap() {
  local name="$1"
  adb -s "$DEVICE" shell screencap -p "/sdcard/ww-cap.png" >/dev/null
  adb -s "$DEVICE" pull /sdcard/ww-cap.png "$OUT_DIR/$name" >/dev/null
  echo "  -> $name"
}

launch() {
  adb -s "$DEVICE" shell input keyevent KEYCODE_WAKEUP
  adb -s "$DEVICE" shell am start -n "$ACTIVITY" >/dev/null 2>&1 || true
  sleep 2.5
}

tap_text() {
  local text="$1"
  adb -s "$DEVICE" shell uiautomator dump "$UI_TMP" >/dev/null
  adb -s "$DEVICE" pull "$UI_TMP" /tmp/ww-ui-local.xml >/dev/null
  local coords
  coords=$(python3 - "$text" <<'PY'
import re, sys, xml.etree.ElementTree as ET
text = sys.argv[1]
root = ET.parse('/tmp/ww-ui-local.xml').getroot()
for node in root.iter('node'):
    t = node.attrib.get('text', '')
    d = node.attrib.get('content-desc', '')
    if t == text or text in d:
        m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds', ''))
        if not m:
            continue
        x1, y1, x2, y2 = map(int, m.groups())
        print(f"{(x1 + x2) // 2} {(y1 + y2) // 2}")
        sys.exit(0)
sys.exit(1)
PY
) || return 1
  adb -s "$DEVICE" shell input tap ${coords%% *} ${coords##* }
  sleep 1.5
}

back() {
  adb -s "$DEVICE" shell input keyevent KEYCODE_BACK
  sleep 0.8
}

echo "==> Capturing WearWallet screenshots on $DEVICE"
adb -s "$DEVICE" get-state >/dev/null

adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null
launch
snap "01-welcome-onboarding.png"

tap_text "創建新錢包" && snap "02-create-wallet-entry.png"
back
launch
tap_text "導入錢包" && snap "03-import-wallet-entry.png"
back

# Skip wallet creation — use debug/demo path if wallet exists later.
# For home/send/receive we need a wallet; tap through create with cancel if possible.
# Navigate to create and back to show flow only.
tap_text "創建新錢包" || true
sleep 1
back
back

# If main wallet visible, capture send/receive via content-desc heuristics
launch
sleep 2
snap "04-wallet-home.png"

# Try common action labels
tap_text "發送" 2>/dev/null && snap "05-send-entry.png" && back || true
tap_text "接收" 2>/dev/null && snap "06-receive-qr.png" && back || true
tap_text "設定" 2>/dev/null && snap "07-settings.png" && back || true

echo "==> Done: $OUT_DIR"
