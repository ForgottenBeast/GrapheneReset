# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**GrapheneReset** is an emergency wipe application for Android/GrapheneOS. It provides a simple interface to trigger device wipes in emergency situations through multiple methods (quick settings tile, notification listener, lock timeout).

**Note**: This project is a fork of Oblivion, renamed and updated for GrapheneOS compatibility and security enhancements.

**Application ID**: `net.graphenereset.wipe`
**Language**: Kotlin
**Min SDK**: 24 (Android 7.0)
**Target SDK**: 33 (Android 13)
**Build Tools**: 36.0.0 (GrapheneOS requirement)

## Development Environment

This project uses **Nix flakes** for reproducible builds and development environment management.

### Setup

```bash
# Enter development environment
nix develop

# Or use direnv (recommended)
direnv allow
```

### Build Commands

The project uses **Gradle** for building. All commands should be run inside the Nix development environment (`nix develop` or via `android-env`).

```bash
# Enter FHS environment (required for Gradle compatibility)
android-env

# Build debug APK
gradle assembleDebug

# Build release APK
gradle assembleRelease

# Clean build artifacts
gradle clean

# Install debug APK to connected device
gradle installDebug

# Run lint checks
gradle lint
```

### Testing

```bash
# Run unit tests
gradle test

# Run instrumented tests (requires emulator or device)
gradle connectedAndroidTest
```

### Android Emulator

```bash
# Create AVD (Android Virtual Device)
create-android-avd [name]  # defaults to "pixel_8"

# Start emulator
emulator @pixel_8

# Or use Nix app
nix run
```

### Reproducible Builds with gradle2nix

```bash
# Generate gradle.lock file for reproducible builds
gradle2nix -t assembleRelease

# Build reproducibly with Nix
nix build
```

## Architecture

### Core Components

**WipeManager** (`app/src/main/java/net/graphenereset/wipe/WipeManager.kt`)
- **CRITICAL**: The ONLY place in the app allowed to call `wipeData()`
- Enforces allowlist of triggers: `TILE`, `LOCK`, `NOTIFICATION`
- All wipe requests must go through `WipeManager.requestWipe()`
- Validates device admin status and user preferences before executing

**Trigger System** (`app/src/main/java/net/graphenereset/wipe/Preferences.kt:140`)
- Multiple trigger mechanisms: PANIC_KIT, TILE, SHORTCUT, BROADCAST, NOTIFICATION, LOCK, USB, APPLICATION
- Implemented as bit flags for efficient storage and checking
- Only TILE, LOCK, and NOTIFICATION are currently enabled in production

**Preferences** (`app/src/main/java/net/graphenereset/wipe/Preferences.kt`)
- Dual storage: encrypted (normal) and device-protected (for direct boot)
- Uses `EncryptedSharedPreferences` with AES256-GCM encryption
- Preferences sync between encrypted and unencrypted storage for direct boot compatibility

**Utils** (`app/src/main/java/net/graphenereset/wipe/Utils.kt`)
- Component enable/disable management (tiles, services, receivers)
- Trigger activation logic
- Recast functionality for chaining wipe to other apps

### Package Structure

```
net.graphenereset.wipe/
├── admin/                    # Device admin management
│   ├── DeviceAdminManager.kt
│   └── DeviceAdminReceiver.kt
├── Helpers/                  # Utility classes
│   ├── DatabaseHelper.kt     # ID generation and storage
│   ├── KeyHelper.kt
│   ├── NotificationListener.kt
│   ├── Setup.kt
│   └── Validator.kt
├── trigger/                  # Trigger implementations
│   ├── application/          # App-based triggers (Signal, Telegram, etc.)
│   ├── broadcast/            # Broadcast receiver triggers
│   ├── lock/                 # Lock timeout trigger (LockJobService)
│   ├── notification/         # Notification listener trigger
│   ├── panic/                # PanicKit integration
│   ├── shared/               # Shared services (ForegroundService, RestartReceiver)
│   ├── shortcut/             # App shortcut trigger
│   ├── tile/                 # Quick settings tile trigger
│   └── usb/                  # USB connection trigger
├── MainActivity.kt           # Main UI and setup
├── Preferences.kt            # Settings and trigger definitions
├── WipeManager.kt            # Core wipe logic
└── Utils.kt                  # Component management
```

### Direct Boot Support

Many components use `android:directBootAware="true"` to function before the device is unlocked. This is critical for emergency wipe functionality.

### Permissions and Components

The app requires:
- **Device Admin** permission (mandatory for wipe functionality)
- **Notification Listener** permission (for notification trigger)
- Foreground service permission
- Boot completed receiver

Components are dynamically enabled/disabled based on user settings via `Utils.setEnabled()`.

## GrapheneOS Compatibility

- Uses build-tools 36.0.0 (GrapheneOS requirement)
- NDK 29.0.14206865 for native code
- Avoids Google Play Services APIs
- Designed for privacy-focused Android distributions

## Beads Integration

This project uses Beads for issue tracking (`.beads/` directory). Configuration is minimal with default priority 2 and task type.

## Important Notes

- **Security-sensitive code**: This app performs device wipes. Changes to `WipeManager` require extreme care.
- **No tests currently exist**: When adding features, consider adding unit tests.
- **Trigger allowlist**: Only TILE, LOCK, and NOTIFICATION triggers are enabled in `WipeManager`. Do not add triggers without security review.
- **Direct boot**: Many components must work before unlock. Test direct boot scenarios when modifying trigger code.
