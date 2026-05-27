#!/usr/bin/env bash
# 电视已开启「网络 ADB / 无线调试」后，在本机终端执行本脚本完成安装。
# 用法: ./scripts/install-tv.sh [电视IP] [端口] [apk路径]
# 例:   ./scripts/install-tv.sh 192.168.50.139 5555

set -euo pipefail

TV_IP="${1:-192.168.50.139}"
TV_PORT="${2:-5555}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="${3:-$ROOT/android/app/build/outputs/apk/debug/lx-music-tv-v1.8.4-universal.apk}"

ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
if [[ ! -x "$ADB" ]]; then
  echo "未找到 adb，请安装 Android SDK platform-tools 或将 adb 加入 PATH"
  exit 1
fi
if [[ ! -f "$APK" ]]; then
  echo "未找到 APK: $APK"
  echo "可先打包: npm run bundle-android:offline && cd android && ./gradlew assembleDebug"
  exit 1
fi

echo "连接 $TV_IP:$TV_PORT ..."
"$ADB" connect "${TV_IP}:${TV_PORT}" || true
sleep 1
"$ADB" devices -l

SERIAL="${TV_IP}:${TV_PORT}"
if ! "$ADB" devices | grep -q "$SERIAL"; then
  echo ""
  echo "若仍未出现 device ${SERIAL}:"
  echo "  1) 电视「开发者选项」里确认已开启 USB/网络调试；"
  echo "  2) Android 11+ 请先「使用配对码配对设备」，再 adb pair IP:配对端口，最后再 adb connect IP:调试端口；"
  echo "  3) 端口不一定是 5555，请以电视上显示的为准。"
  exit 1
fi

echo "安装 $APK ..."
"$ADB" -s "$SERIAL" install -r "$APK"
echo "完成。"
