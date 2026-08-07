#!/usr/bin/env bash
# Build a staged Developer ID candidate. It never writes /Applications and
# never replaces the fixed current release archive.
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
release_root="$repo_root/../BIN/release/candidates"
app_path="$repo_root/flutter/build/macos/Build/Products/Release/KEMI远程办公.app"
version="$(awk -F': ' '/^version:/{print $2; exit}' "$repo_root/flutter/pubspec.yaml")"
candidate_name="KEMI-远程办公-macOS-${version}-developer-id"
candidate_app="$release_root/${candidate_name}.app"
candidate_zip="$release_root/${candidate_name}.zip"

flutter_bin="$repo_root/.toolchains/flutter/bin/flutter"
if [[ ! -x "$flutter_bin" ]]; then
  echo "Project Flutter SDK not found: $flutter_bin" >&2
  echo "This project never falls back to PATH, KEMI_FLUTTER_BIN, or a global Flutter SDK." >&2
  exit 1
fi
if ! "$flutter_bin" --version | grep -q 'Flutter 3.24.5'; then
  echo "Project Flutter SDK must be 3.24.5: $flutter_bin" >&2
  exit 1
fi

pod_bin="${KEMI_POD_BIN:-}"
if [[ -z "$pod_bin" ]]; then
  pod_bin="$(command -v pod || true)"
fi
if [[ -z "$pod_bin" && -x /private/tmp/kemi-cocoapods/bin/pod ]]; then
  pod_bin="/private/tmp/kemi-cocoapods/bin/pod"
  export GEM_HOME=/private/tmp/kemi-cocoapods
  export GEM_PATH=/private/tmp/kemi-cocoapods
  export PATH="/private/tmp/kemi-cocoapods/bin:$PATH"
fi
if [[ -z "$pod_bin" || ! -x "$pod_bin" ]]; then
  echo "CocoaPods not found. Set KEMI_POD_BIN or install a compatible pod executable." >&2
  exit 1
fi

if [[ -e "$candidate_app" || -e "$candidate_zip" ]]; then
  echo "Candidate already exists; refusing to overwrite: $candidate_name" >&2
  exit 1
fi

cd "$repo_root"
# The macOS bundle contains two independent Rust executables: the GUI binary
# and `service`. Build both explicitly so an old cached service can never be
# paired with a newly compiled GUI app.
cargo build --locked --features flutter --release --lib --bins
(
  cd flutter
  # The project-owned Flutter SDK prevents cross-project module-cache mixing.
  # Keep .dart_tool and Xcode outputs for normal releases: unconditional clean
  # used to destroy a valid dependency graph and force slow Git-cache repair.
  # Use a clean build only after an SDK/Xcode/plugin migration.
  if [[ "${KEMI_MACOS_FORCE_CLEAN:-0}" == "1" ]]; then
    "$flutter_bin" clean
  elif [[ "${KEMI_MACOS_FORCE_CLEAN:-0}" != "0" ]]; then
    echo "KEMI_MACOS_FORCE_CLEAN must be 0 or 1." >&2
    exit 2
  fi

  # A normal version bump does not require dependency resolution. Resolve only
  # when the generated package graph is absent (fresh checkout or forced clean).
  # Offline mode fails immediately if the established project cache is missing.
  if [[ ! -f .dart_tool/package_config.json ]]; then
    "$flutter_bin" pub get --offline
  fi
  FLUTTER_XCODE_ARCHS="$(uname -m)" \
  FLUTTER_XCODE_ONLY_ACTIVE_ARCH=YES \
  "$flutter_bin" build macos --release --no-pub
)

test -x "$repo_root/target/release/service"
test -f "$repo_root/target/release/liblibrustdesk.dylib"
test -d "$app_path"
cp "$repo_root/target/release/service" "$app_path/Contents/MacOS/service"
"$repo_root/res/sign-kemi-developer-id-macos.sh" "$app_path"

mkdir -p "$release_root"
ditto "$app_path" "$candidate_app"
ditto -c -k --keepParent "$candidate_app" "$candidate_zip"
shasum -a 256 "$candidate_zip" > "$release_root/${candidate_name}.SHA256"

echo "Staged app: $candidate_app"
echo "Staged zip: $candidate_zip"
echo "No installed application was modified. This Developer ID candidate is not notarized."
if [[ "${KEMI_MACOS_TIMESTAMP:-required}" == "off" ]]; then
  echo "The candidate was signed without an Apple timestamp and is for local testing only."
fi
