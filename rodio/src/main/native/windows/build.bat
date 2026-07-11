@echo off
REM Builds the Rust JNI bridge (nucleus_rodio) into Windows DLLs and copies
REM them into the per-architecture resource folders consumed by
REM NativeLibraryLoader:
REM   src\main\resources\nucleus\native\{win32-x64,win32-aarch64}\nucleus_rodio.dll
REM
REM Prerequisites:
REM   - rustup with x86_64-pc-windows-msvc (and aarch64-pc-windows-msvc) targets
REM   - Visual Studio 2019/2022 Build Tools
REM Usage: build.bat

setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
set "NATIVE_DIR=%SCRIPT_DIR%.."
set "RESOURCE_DIR=%NATIVE_DIR%\..\resources\nucleus\native"
set "OUT_DIR_X64=%RESOURCE_DIR%\win32-x64"
set "OUT_DIR_ARM64=%RESOURCE_DIR%\win32-aarch64"

if not exist "%OUT_DIR_X64%" mkdir "%OUT_DIR_X64%"
if not exist "%OUT_DIR_ARM64%" mkdir "%OUT_DIR_ARM64%"

where cargo >nul 2>&1
if errorlevel 1 (
    echo ERROR: cargo not found in PATH. Install rustup from https://rustup.rs/ >&2
    exit /b 1
)

REM ===========================================================================
REM 1) Rust crate (nucleus_rodio.dll) — x64
REM ===========================================================================
echo.
echo === Building nucleus_rodio.dll (x64) ===
pushd "%NATIVE_DIR%"
rustup target add x86_64-pc-windows-msvc >nul 2>&1
cargo build --release --target x86_64-pc-windows-msvc
if errorlevel 1 (
    echo ERROR: cargo build x64 failed >&2
    popd
    exit /b 1
)
copy /Y "target\x86_64-pc-windows-msvc\release\nucleus_rodio.dll" "%OUT_DIR_X64%\nucleus_rodio.dll" >nul
popd

REM ===========================================================================
REM 2) Rust crate (nucleus_rodio.dll) — ARM64 (optional; needs the ARM64 toolchain)
REM ===========================================================================
echo.
echo === Building nucleus_rodio.dll (ARM64) ===
pushd "%NATIVE_DIR%"
rustup target add aarch64-pc-windows-msvc >nul 2>&1
cargo build --release --target aarch64-pc-windows-msvc
if errorlevel 1 (
    echo WARNING: cargo build ARM64 failed - skipping ARM64. >&2
    set "SKIP_ARM64=1"
) else (
    copy /Y "target\aarch64-pc-windows-msvc\release\nucleus_rodio.dll" "%OUT_DIR_ARM64%\nucleus_rodio.dll" >nul
)
popd

REM ===========================================================================
REM Clear NativeLibraryLoader cache so fresh DLLs are picked up
REM ===========================================================================
if exist "%USERPROFILE%\.cache\nucleus\native" (
    rmdir /s /q "%USERPROFILE%\.cache\nucleus\native"
    echo Cleared NativeLibraryLoader cache: %USERPROFILE%\.cache\nucleus\native
)
if exist "%LOCALAPPDATA%\nucleus\native" (
    rmdir /s /q "%LOCALAPPDATA%\nucleus\native"
    echo Cleared NativeLibraryLoader cache: %LOCALAPPDATA%\nucleus\native
)

echo.
echo Built DLLs:
if exist "%OUT_DIR_X64%\nucleus_rodio.dll" echo   %OUT_DIR_X64%\nucleus_rodio.dll
if exist "%OUT_DIR_ARM64%\nucleus_rodio.dll" echo   %OUT_DIR_ARM64%\nucleus_rodio.dll

exit /b 0

endlocal