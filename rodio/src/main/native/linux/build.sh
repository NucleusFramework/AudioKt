#!/bin/bash
# Compiles the Rust JNI bridge (nucleus_rodio) into a Linux shared library and
# copies it into the host-architecture resource folder consumed by
# NativeLibraryLoader:
#   src/main/resources/nucleus/native/{linux-x64,linux-aarch64}/libnucleus_rodio.so
#
# Only the host architecture is built here — cross-building aarch64 from
# x86_64 (or vice-versa) needs a proper ALSA sysroot for the target, which is
# not reasonable to assume in every dev environment. CI builds both slots.
#
# Prerequisites:
#   - rustup with the matching host target (x86_64- or aarch64-unknown-linux-gnu)
#   - ALSA development headers (libasound2-dev / alsa-lib-devel) — rodio's cpal
#   - pkg-config
# Usage: ./build.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
NATIVE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
RESOURCE_DIR="$NATIVE_DIR/../resources/nucleus/native"
OUT_DIR_X64="$RESOURCE_DIR/linux-x64"
OUT_DIR_ARM64="$RESOURCE_DIR/linux-aarch64"

mkdir -p "$OUT_DIR_X64" "$OUT_DIR_ARM64"

HOST_ARCH="$(uname -m)"

if ! command -v cargo >/dev/null 2>&1; then
    echo "ERROR: cargo not found. Install rustup from https://rustup.rs/" >&2
    exit 1
fi

pushd "$NATIVE_DIR" >/dev/null

case "$HOST_ARCH" in
    x86_64)
        rustup target add x86_64-unknown-linux-gnu >/dev/null
        cargo build --release --target x86_64-unknown-linux-gnu
        cp "target/x86_64-unknown-linux-gnu/release/libnucleus_rodio.so" \
           "$OUT_DIR_X64/libnucleus_rodio.so"
        strip --strip-unneeded "$OUT_DIR_X64/libnucleus_rodio.so" || true
        ;;
    aarch64|arm64)
        rustup target add aarch64-unknown-linux-gnu >/dev/null
        cargo build --release --target aarch64-unknown-linux-gnu
        cp "target/aarch64-unknown-linux-gnu/release/libnucleus_rodio.so" \
           "$OUT_DIR_ARM64/libnucleus_rodio.so"
        strip --strip-unneeded "$OUT_DIR_ARM64/libnucleus_rodio.so" || true
        ;;
    *)
        echo "ERROR: unsupported host arch '$HOST_ARCH'" >&2
        exit 1
        ;;
esac

popd >/dev/null

# ── Clear NativeLibraryLoader cache so fresh .so's are picked up ───────────
for CACHE_DIR in "$HOME/.cache/nucleus/native"; do
    if [ -d "$CACHE_DIR" ]; then
        rm -rf "$CACHE_DIR"
        echo "Cleared NativeLibraryLoader cache: $CACHE_DIR"
    fi
done

echo "Built Linux native library:"
case "$HOST_ARCH" in
    x86_64) ls -lh "$OUT_DIR_X64"/libnucleus_rodio.so ;;
    aarch64|arm64) ls -lh "$OUT_DIR_ARM64"/libnucleus_rodio.so ;;
esac