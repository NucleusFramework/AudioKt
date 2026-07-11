#!/bin/bash
# Compiles the Rust JNI bridge (nucleus_rodio) into per-architecture macOS
# dylibs and copies them into the resource folders consumed by
# NativeLibraryLoader:
#   src/main/resources/nucleus/native/{darwin-aarch64,darwin-x64}/libnucleus_rodio.dylib
#
# Prerequisites:
#   - rustup with the targets aarch64-apple-darwin and x86_64-apple-darwin
#   - Xcode command-line tools (clang) — only needed for `strip`
# Usage: ./build.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
NATIVE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
RESOURCE_DIR="$NATIVE_DIR/../resources/nucleus/native"
OUT_DIR_ARM64="$RESOURCE_DIR/darwin-aarch64"
OUT_DIR_X64="$RESOURCE_DIR/darwin-x64"

mkdir -p "$OUT_DIR_ARM64" "$OUT_DIR_X64"

if ! command -v cargo >/dev/null 2>&1; then
    echo "ERROR: cargo not found. Install rustup from https://rustup.rs/" >&2
    exit 1
fi

rustup target add aarch64-apple-darwin >/dev/null
rustup target add x86_64-apple-darwin  >/dev/null

pushd "$NATIVE_DIR" >/dev/null

cargo build --release --target aarch64-apple-darwin
cp "target/aarch64-apple-darwin/release/libnucleus_rodio.dylib" "$OUT_DIR_ARM64/libnucleus_rodio.dylib"
strip -x "$OUT_DIR_ARM64/libnucleus_rodio.dylib"

cargo build --release --target x86_64-apple-darwin
cp "target/x86_64-apple-darwin/release/libnucleus_rodio.dylib" "$OUT_DIR_X64/libnucleus_rodio.dylib"
strip -x "$OUT_DIR_X64/libnucleus_rodio.dylib"

popd >/dev/null

# ── Clear NativeLibraryLoader cache so fresh dylibs are picked up ───────────
# macOS cache lives under ~/Library/Caches/nucleus/native.
for CACHE_DIR in "$HOME/Library/Caches/nucleus/native" "$HOME/.cache/nucleus/native"; do
    if [ -d "$CACHE_DIR" ]; then
        rm -rf "$CACHE_DIR"
        echo "Cleared NativeLibraryLoader cache: $CACHE_DIR"
    fi
done

echo "Built per-architecture dylibs:"
ls -lh "$OUT_DIR_ARM64"/libnucleus_rodio.dylib "$OUT_DIR_X64"/libnucleus_rodio.dylib