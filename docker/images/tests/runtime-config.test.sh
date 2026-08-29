#!/usr/bin/env bash

set -euo pipefail

test_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$test_dir/../files/runtime-config.sh"

temporary_dir="$(mktemp -d)"
trap 'rm -rf "$temporary_dir"' EXIT
config_file="$temporary_dir/config.js"

cp "$test_dir/../files/config.js" "$config_file"

upsert_javascript_string_config "$config_file" "penpotPublicURI" "https://first.example/"
upsert_javascript_string_config "$config_file" "penpotPublicURI" "https://second.example/"
upsert_javascript_string_config "$config_file" "penpotSayHiSurface" "canvas"
upsert_javascript_string_config "$config_file" "penpotSayHiSurface" "canvas"
upsert_javascript_string_config "$config_file" "penpotSayHiStudioChromeURI" "https://studio.example/first"
upsert_javascript_string_config "$config_file" "penpotSayHiStudioChromeURI" "https://studio.example/second"
upsert_javascript_string_config "$config_file" "penpotSayHiMotionStudioURI" "https://motion.example/first"
upsert_javascript_string_config "$config_file" "penpotSayHiMotionStudioURI" "https://motion.example/second"
upsert_javascript_string_config "$config_file" "penpotSayHiWebRuntimeURI" "https://runtime.example/first"
upsert_javascript_string_config "$config_file" "penpotSayHiWebRuntimeURI" "https://runtime.example/second"

for variable in \
  penpotPublicURI \
  penpotSayHiSurface \
  penpotSayHiStudioChromeURI \
  penpotSayHiMotionStudioURI \
  penpotSayHiWebRuntimeURI; do
  test "$(grep -Ec "^(//)?var ${variable} = " "$config_file")" -eq 1
done

grep -Fq 'var penpotPublicURI = "https://second.example/";' "$config_file"
grep -Fq 'var penpotSayHiSurface = "canvas";' "$config_file"
grep -Fq 'var penpotSayHiStudioChromeURI = "https://studio.example/second";' "$config_file"
grep -Fq 'var penpotSayHiMotionStudioURI = "https://motion.example/second";' "$config_file"
grep -Fq 'var penpotSayHiWebRuntimeURI = "https://runtime.example/second";' "$config_file"

validate_http_url_config "TEST_URI" "https://valid.example/path"

if validate_http_url_config "TEST_URI" "file:///etc/passwd" 2>/dev/null; then
  echo "Runtime config accepted a non-HTTP URL" >&2
  exit 1
fi

if validate_http_url_config "TEST_URI" $'https://bad.example/\ninjected' 2>/dev/null; then
  echo "Runtime config accepted a line break" >&2
  exit 1
fi

if upsert_javascript_string_config "$config_file" "invalid-variable" "value" 2>/dev/null; then
  echo "Runtime config accepted an invalid JavaScript variable" >&2
  exit 1
fi

echo "SayHi runtime configuration injection is bounded and idempotent"
