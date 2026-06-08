#!/usr/bin/env bash
set -euo pipefail

APPLE_ID="${APPLE_ID:-6764116305}"
BUNDLE_VERSION="${BUNDLE_VERSION:-98}"
BUNDLE_SHORT_VERSION="${BUNDLE_SHORT_VERSION:-3.0.8}"
PLATFORM="${PLATFORM:-ios}"
DELIVERY_ID="${DELIVERY_ID:-eb6c3cea-d295-4563-b3b9-a51cd306465b}"

args=(--build-status --output-format json)

if [[ -n "${DELIVERY_ID}" ]]; then
  args+=(--delivery-id "${DELIVERY_ID}")
else
  args+=(
    --apple-id "${APPLE_ID}"
    --bundle-version "${BUNDLE_VERSION}"
    --bundle-short-version-string "${BUNDLE_SHORT_VERSION}"
    -t "${PLATFORM}"
  )
fi

if [[ -n "${ASC_API_KEY:-}" && -n "${ASC_API_ISSUER:-}" ]]; then
  args+=(--api-key "${ASC_API_KEY}" --api-issuer "${ASC_API_ISSUER}")
  if [[ -n "${ASC_P8_FILE:-}" ]]; then
    args+=(--p8-file-path "${ASC_P8_FILE}")
  fi
elif [[ -n "${ASC_USERNAME:-}" && -n "${ASC_APP_PASSWORD:-}" && -n "${ASC_PROVIDER_PUBLIC_ID:-84204a05-86c2-480e-a6e0-dbaec8f9b19a}" ]]; then
  args+=(
    --username "${ASC_USERNAME}"
    --app-password "${ASC_APP_PASSWORD}"
    --provider-public-id "${ASC_PROVIDER_PUBLIC_ID:-84204a05-86c2-480e-a6e0-dbaec8f9b19a}"
  )
else
  cat >&2 <<EOF
Missing App Store Connect auth.

Use API key auth:
  export ASC_API_KEY="YOUR_KEY_ID"
  export ASC_API_ISSUER="YOUR_ISSUER_ID"
  export ASC_P8_FILE="/path/to/AuthKey_YOUR_KEY_ID.p8"

Or app-specific password auth:
  export ASC_USERNAME="you@example.com"
  export ASC_APP_PASSWORD="xxxx-xxxx-xxxx-xxxx"
  export ASC_PROVIDER_PUBLIC_ID="84204a05-86c2-480e-a6e0-dbaec8f9b19a"

Then run:
  native/iosApp/scripts/check_appstore_build_status.sh
EOF
  exit 2
fi

xcrun altool "${args[@]}"
