#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IOS_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

SCHEME="${SCHEME:-ReportediOS}"
CONFIGURATION="${CONFIGURATION:-Release}"
VERSION="${VERSION:-3.1.6}"
BUILD_NUMBER="${BUILD_NUMBER:-62}"
TEAM_ID="${TEAM_ID:-5GB9SD339W}"
ARCHIVE_PATH="${ARCHIVE_PATH:-${IOS_DIR}/build/appstore/ReportediOS-${VERSION}-${BUILD_NUMBER}-${TEAM_ID}.xcarchive}"
EXPORT_PATH="${EXPORT_PATH:-${IOS_DIR}/build/appstore/export-${VERSION}-${BUILD_NUMBER}-${TEAM_ID}}"
EXPORT_OPTIONS="${EXPORT_OPTIONS:-${SCRIPT_DIR}/ExportOptions.appstore.plist}"
PROVISIONING_ARGS=()
if [[ "${ALLOW_PROVISIONING_UPDATES:-1}" != "0" ]]; then
  PROVISIONING_ARGS=(-allowProvisioningUpdates)
fi

mkdir -p "$(dirname "${ARCHIVE_PATH}")" "${EXPORT_PATH}"

xcodebuild \
  -project "${IOS_DIR}/ReportediOS.xcodeproj" \
  -scheme "${SCHEME}" \
  -configuration "${CONFIGURATION}" \
  -destination "generic/platform=iOS" \
  -archivePath "${ARCHIVE_PATH}" \
  "${PROVISIONING_ARGS[@]}" \
  clean archive

xcodebuild \
  -exportArchive \
  -archivePath "${ARCHIVE_PATH}" \
  -exportPath "${EXPORT_PATH}" \
  -exportOptionsPlist "${EXPORT_OPTIONS}" \
  "${PROVISIONING_ARGS[@]}"
