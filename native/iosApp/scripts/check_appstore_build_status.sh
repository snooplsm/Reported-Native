#!/usr/bin/env bash
set -euo pipefail

APP_STORE_APP_ID="${APP_STORE_APP_ID:-916072964}"
BUNDLE_VERSION="${BUNDLE_VERSION:-60}"
BUNDLE_SHORT_VERSION="${BUNDLE_SHORT_VERSION:-3.1.5}"
PLATFORM="${PLATFORM:-ios}"
DELIVERY_ID="${DELIVERY_ID:-}"
ASC_USERNAME="${ASC_USERNAME:-${APPLE_ID:-}}"
ASC_APP_PASSWORD="${ASC_APP_PASSWORD:-${APPLE_APP_SPECIFIC_PASSWORD:-}}"
ASC_PROVIDER_PUBLIC_ID="${ASC_PROVIDER_PUBLIC_ID:-b7db4176-68e0-4dfb-b85d-9f9438afeeae}"

args=(--build-status --output-format json)

if [[ -n "${DELIVERY_ID}" ]]; then
  args+=(--delivery-id "${DELIVERY_ID}")
else
  args+=(
    --apple-id "${APP_STORE_APP_ID}"
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
elif [[ -n "${ASC_USERNAME}" && -n "${ASC_APP_PASSWORD}" && -n "${ASC_PROVIDER_PUBLIC_ID}" ]]; then
  args+=(
    --username "${ASC_USERNAME}"
    --app-password "${ASC_APP_PASSWORD}"
    --provider-public-id "${ASC_PROVIDER_PUBLIC_ID}"
  )
else
  cat >&2 <<EOF
Missing App Store Connect auth.

Use API key auth:
  export ASC_API_KEY="YOUR_KEY_ID"
  export ASC_API_ISSUER="YOUR_ISSUER_ID"
  export ASC_P8_FILE="/path/to/AuthKey_YOUR_KEY_ID.p8"

Or app-specific password auth:
  export ASC_USERNAME="you@example.com" # defaults to APPLE_ID if set
  export ASC_APP_PASSWORD="xxxx-xxxx-xxxx-xxxx" # defaults to APPLE_APP_SPECIFIC_PASSWORD if set
  export ASC_PROVIDER_PUBLIC_ID="b7db4176-68e0-4dfb-b85d-9f9438afeeae"

Then run:
  native/iosApp/scripts/check_appstore_build_status.sh
EOF
  exit 2
fi

xcrun altool "${args[@]}"
