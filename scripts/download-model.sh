#!/usr/bin/env bash
set -euo pipefail

readonly MODEL_URL_DEFAULT="https://huggingface.co/Cactus-Compute/needle2/resolve/main/needle2.cact?download=true"
readonly MODEL_SHA256="b43aabfcaf1a6db6acf488076eab71d823c08697c7af4521fc1d174b60ede5ba"

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
destination="${1:-${repo_root}/app/src/main/assets/needle2.cact}"
model_url="${AGAVE_MODEL_URL:-${MODEL_URL_DEFAULT}}"

sha256() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print $1}'
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$1" | awk '{print $1}'
    else
        echo "error: sha256sum or shasum is required" >&2
        return 1
    fi
}

if [[ -f "${destination}" ]] && [[ "$(sha256 "${destination}")" == "${MODEL_SHA256}" ]]; then
    echo "Needle 2 model already present and verified: ${destination}"
    exit 0
fi

if ! command -v curl >/dev/null 2>&1; then
    echo "error: curl is required to download the model" >&2
    exit 1
fi

mkdir -p "$(dirname "${destination}")"
temporary="$(mktemp "${destination}.download.XXXXXX")"
trap 'rm -f "${temporary}"' EXIT

echo "Downloading Needle 2 base model..."
curl \
    --fail \
    --location \
    --retry 3 \
    --progress-bar \
    --output "${temporary}" \
    "${model_url}"

actual_sha256="$(sha256 "${temporary}")"
if [[ "${actual_sha256}" != "${MODEL_SHA256}" ]]; then
    echo "error: model checksum mismatch" >&2
    echo "expected: ${MODEL_SHA256}" >&2
    echo "actual:   ${actual_sha256}" >&2
    exit 1
fi

mv "${temporary}" "${destination}"
trap - EXIT
echo "Installed verified model: ${destination}"
