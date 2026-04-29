#!/usr/bin/env bash
set -euo pipefail

SOURCE_IMAGE="/Users/snooplsm/Desktop/blocked_crosswalk.jpg"
DEST_NAME="reported-blocked-crosswalk-$(date +%s).jpg"
DEST_PATH="/sdcard/DCIM/Camera/${DEST_NAME}"

if [[ ! -f "${SOURCE_IMAGE}" ]]; then
  echo "Missing test image: ${SOURCE_IMAGE}" >&2
  exit 1
fi

if ! command -v adb >/dev/null 2>&1; then
  echo "adb was not found on PATH." >&2
  exit 1
fi

if ! adb get-state >/dev/null 2>&1; then
  echo "No adb device is connected or authorized." >&2
  exit 1
fi

echo "Pushing ${SOURCE_IMAGE}"
adb push "${SOURCE_IMAGE}" "${DEST_PATH}"

echo "Triggering Android media indexer for ${DEST_PATH}"
adb shell am broadcast \
  -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
  -d "file://${DEST_PATH}" >/dev/null

echo
echo "Pushed to: ${DEST_PATH}"
echo "Now toggle the Profile media scanner off/on, or wait for scheduled work."
echo "Watch scanner logs with:"
echo "  adb logcat -s ReportedMediaScanner"
