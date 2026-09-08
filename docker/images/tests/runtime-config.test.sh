#!/usr/bin/env bash

set -euo pipefail

test_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$test_dir/../files/runtime-config.sh"

temporary_dir="$(mktemp -d)"
trap 'rm -rf "$temporary_dir"' EXIT
config_file="$temporary_dir/config.js"

cp "$test_dir/../files/config.js" "$config_file"

upsert_javascript_string_config "$config_file" "penpotSayHiMotionStudioURI" "https://first.example/"
upsert_javascript_string_config "$config_file" "penpotSayHiMotionStudioURI" "https://second.example/path"
upsert_javascript_string_config "$config_file" "penpotSayHiMotionStudioMode" "native-v2"
upsert_javascript_string_config "$config_file" "penpotSayHiMotionStudioMode" "native-v1"
upsert_javascript_string_config "$config_file" "penpotPublicURI" "https://first-public.example/"
upsert_javascript_string_config "$config_file" "penpotPublicURI" "https://second-public.example/"
upsert_javascript_string_config "$config_file" "penpotSayHiWebRuntimeMode" "standalone-v1"
upsert_javascript_string_config "$config_file" "penpotSayHiWebRuntimeMode" "shadow"
upsert_javascript_string_config "$config_file" "penpotSayHiWebRuntimeURI" "https://runtime.example/first"
upsert_javascript_string_config "$config_file" "penpotSayHiWebRuntimeURI" "https://runtime.example/second"
upsert_javascript_string_config "$config_file" "penpotSayHiStudioChromeURI" "https://studio.example/chrome/first"
upsert_javascript_string_config "$config_file" "penpotSayHiStudioChromeURI" "https://studio.example/chrome/second"
upsert_javascript_string_config "$config_file" "penpotSayHiStudioChromeMode" "internal-v1"
upsert_javascript_string_config "$config_file" "penpotSayHiStudioChromeMode" "external-v1"
upsert_javascript_string_config "$config_file" "penpotSayHiSurface" "standard"
upsert_javascript_string_config "$config_file" "penpotSayHiSurface" "canvas"

test "$(grep -Ec '^(//)?var penpotSayHiMotionStudioURI = ' "$config_file")" -eq 1
test "$(grep -Ec '^(//)?var penpotSayHiMotionStudioMode = ' "$config_file")" -eq 1
test "$(grep -Ec '^(//)?var penpotPublicURI = ' "$config_file")" -eq 1
test "$(grep -Ec '^(//)?var penpotSayHiWebRuntimeMode = ' "$config_file")" -eq 1
test "$(grep -Ec '^(//)?var penpotSayHiWebRuntimeURI = ' "$config_file")" -eq 1
test "$(grep -Ec '^(//)?var penpotSayHiStudioChromeURI = ' "$config_file")" -eq 1
test "$(grep -Ec '^(//)?var penpotSayHiStudioChromeMode = ' "$config_file")" -eq 1
test "$(grep -Ec '^(//)?var penpotSayHiSurface = ' "$config_file")" -eq 1
grep -Fq 'var penpotSayHiMotionStudioURI = "https://second.example/path";' "$config_file"
grep -Fq 'var penpotSayHiMotionStudioMode = "native-v1";' "$config_file"
grep -Fq 'var penpotPublicURI = "https://second-public.example/";' "$config_file"
grep -Fq 'var penpotSayHiWebRuntimeMode = "shadow";' "$config_file"
grep -Fq 'var penpotSayHiWebRuntimeURI = "https://runtime.example/second";' "$config_file"
grep -Fq 'var penpotSayHiStudioChromeURI = "https://studio.example/chrome/second";' "$config_file"
grep -Fq 'var penpotSayHiStudioChromeMode = "external-v1";' "$config_file"
grep -Fq 'var penpotSayHiSurface = "canvas";' "$config_file"

if upsert_javascript_string_config "$config_file" "penpotSayHiMotionStudioURI" $'https://bad.example/\ninjected' 2>/dev/null; then
  echo "Runtime config accepted a line break" >&2
  exit 1
fi

echo "runtime config injection is idempotent"
