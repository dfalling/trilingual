#!/usr/bin/env bash
# Boots a headless emulator and waits until Android has finished starting.
#
# Uses -gpu swangle_indirect (ANGLE on SwiftShader's Vulkan backend). The default
# software renderer (gles_swiftshader) segfaults on this Fedora/Mesa 26 host as
# soon as rendering starts, and -gpu host can't create a renderer without a window.
#
# Usage: scripts/emulator.sh [--window] [avd-name]   (default: Pixel_7)
#        scripts/emulator.sh --stop
#
# --window opens a normal emulator window to use by hand, rendered with -gpu host.
set -euo pipefail

WINDOW_ARGS=(-no-window -gpu swangle_indirect)
if [[ "${1:-}" == "--window" ]]; then
  WINDOW_ARGS=(-gpu host)
  shift
fi

SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
ADB="$SDK/platform-tools/adb"
AVD="${1:-Pixel_7}"
LOG="${TMPDIR:-/tmp}/trilingual-emulator.log"

if [[ "$AVD" == "--stop" ]]; then
  "$ADB" emu kill 2>/dev/null || true
  exit 0
fi

if [[ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]]; then
  echo "Emulator already running."
  exit 0
fi

# Locks left behind by a crashed emulator make the next launch refuse to start.
rm -f "$HOME/.android/avd/$AVD.avd/"*.lock

nohup "$SDK/emulator/emulator" -avd "$AVD" "${WINDOW_ARGS[@]}" -no-audio -no-snapshot -no-boot-anim \
  >"$LOG" 2>&1 &
pid=$!

echo "Booting $AVD (log: $LOG)..."
until [[ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]]; do
  if ! kill -0 "$pid" 2>/dev/null; then
    echo "Emulator exited during boot:" >&2
    tail -5 "$LOG" >&2
    exit 1
  fi
  sleep 3
done
echo "Booted."
