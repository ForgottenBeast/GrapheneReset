# Building Signed APKs

This guide explains how to build installable, signed APKs for GrapheneReset.

## Prerequisites

The build process **automatically generates** a self-signed keystore (`app/graphenereset.keystore`) when you enter the development environment or run a build. No manual setup required!

## Quick Build Instructions

### Method 1: Using Nix (Recommended)

```bash
# Enter the Nix development environment
nix develop

# Enter the FHS environment for Gradle compatibility
android-env

# Build signed debug APK
gradle assembleDebug

# Build signed release APK
gradle assembleRelease

# Exit the FHS environment
exit
```

### Method 2: Direct Gradle (if you already have Android SDK configured)

```bash
# Ensure ANDROID_HOME and JAVA_HOME are set
export ANDROID_HOME=/path/to/android-sdk
export JAVA_HOME=/path/to/jdk

# Build debug APK
gradle assembleDebug

# Build release APK
gradle assembleRelease
```

## Output Locations

After building, the signed APKs will be at:

- **Debug**: `app/build/outputs/apk/debug/app-debug.apk`
- **Release**: `app/build/outputs/apk/release/app-release.apk`

## Installing the APK

### Via ADB (Android Debug Bridge)

```bash
# Install debug APK
adb install app/build/outputs/apk/debug/app-debug.apk

# Or use Gradle
gradle installDebug
```

### Via File Transfer

1. Copy the APK to your Android device
2. Open the file manager and tap the APK
3. Allow installation from unknown sources if prompted
4. Install the app

## Verifying the Signature

You can verify the APK is signed correctly:

```bash
# Using apksigner (from Android SDK)
apksigner verify --verbose app/build/outputs/apk/release/app-release.apk

# Using jarsigner
jarsigner -verify -verbose -certs app/build/outputs/apk/release/app-release.apk
```

## Troubleshooting

### "App not installed" error

If you get this error when trying to install:
1. The APK wasn't signed → Check that `app/graphenereset.keystore` exists
2. Conflicting signatures → Uninstall the old version first
3. Insufficient storage → Free up space on your device

### Build fails with "Keystore not found"

```bash
# Make sure you're in the project root directory
cd /path/to/GrapheneReset

# Verify the keystore exists
ls -l app/graphenereset.keystore

# If missing, regenerate it
./generate-keystore.sh
```

### Nix development environment issues

If `nix develop` fails due to disk space:
1. Clean Nix store: `nix-collect-garbage -d`
2. Or build directly if you have Android SDK installed system-wide

## Keystore Information

The included development keystore has these credentials:
- **File**: `app/graphenereset.keystore`
- **Alias**: `graphenereset`
- **Passwords**: `graphenereset` (both store and key)
- **Type**: PKCS12
- **Validity**: 10,000 days

See [SIGNING.md](SIGNING.md) for more details on the signing configuration.
