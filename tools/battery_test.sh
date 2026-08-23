#!/usr/bin/env bash
# One-hour screen-off battery measurement of TabGreater on a real phone.
#
# Usage (Git Bash, repo root):
#   tools/battery_test.sh start            # resets battery stats, prints the app's UID, tells you to unplug
#   tools/battery_test.sh report [minutes] # prints device discharge + the app's share since `start`
#
# Measure over Wi-Fi ADB so the phone is NOT charging during the hour:
#   adb tcpip 5555 && adb connect <phone-ip>:5555   (then unplug the USB cable)
# Android only attributes battery to apps while the device is discharging, so an hour on USB
# would read as zero.
set -uo pipefail
ADB="${ADB:-C:/Android/Sdk/platform-tools/adb.exe}${ADB_SERIAL:+ -s $ADB_SERIAL}"
PKG="${PKG:-com.neatcode.tabgreater}"
STAMP=".scratch/battery_start.txt"
mkdir -p .scratch

case "${1:-}" in
  start)
    $ADB shell dumpsys batterystats --reset >/dev/null
    $ADB shell dumpsys batterystats --enable full-wake-history >/dev/null 2>&1 || true
    UID_LINE=$(set +o pipefail; $ADB shell dumpsys package "$PKG" | grep -m1 -E 'userId=|uid=' | tr -d '\r')
    LEVEL=$($ADB shell dumpsys battery | grep -m1 level | tr -d '\r')
    date +%s > "$STAMP"
    echo "stats reset at $(date '+%H:%M:%S'); $UID_LINE; battery $LEVEL"
    echo "Now: unplug USB (keep Wi-Fi ADB), lock the phone, leave it for 60 min."
    ;;
  report)
    START=$(cat "$STAMP")
    NOW=$(date +%s)
    echo "elapsed: $(( (NOW - START) / 60 )) min"
    echo "--- battery now"; $ADB shell dumpsys battery | grep -E 'level|status' | tr -d '\r'
    echo "--- device discharge since reset"
    $ADB shell dumpsys batterystats | grep -E 'Discharge: |Screen off discharge|Screen on discharge|Device battery capacity' | head -6 | tr -d '\r'
    echo "--- estimated power use (top 12)"
    $ADB shell dumpsys batterystats | sed -n '/Estimated power use/,/^$/p' | head -16 | tr -d '\r'
    echo "--- app detail"
    $ADB shell dumpsys batterystats "$PKG" | grep -E 'Wake lock|Foreground for|Total cpu time|Wifi data|Mobile data|Wifi Running|Total running|Cpu user|mAh' | head -20 | tr -d '\r'
    echo "--- service / alarms"
    $ADB shell dumpsys activity services "$PKG" | grep -E 'ServiceRecord|isForeground' | head -4 | tr -d '\r'
    $ADB shell dumpsys alarm | grep -A2 "$PKG" | grep -E 'ELAPSED|RTC|tag=' | head -6 | tr -d '\r'
    echo "--- live service log (last 40 lines)"
    $ADB logcat -d -s LiveTicker:I LiveAlarmRx:I | tail -40 | tr -d '\r'
    ;;
  *)
    echo "usage: $0 start | report" >&2; exit 1 ;;
esac
