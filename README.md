# GrapheneReset

**Emergency device wipe application for GrapheneOS with customizable wipe timers**

## Fork Notice

GrapheneReset is a fork of [Oblivion](https://gitlab.com/swampc4-group/oblivionwipe), an emergency wipe application originally developed by the swampc4-group. This fork maintains the core emergency wipe functionality while adding enhanced customization features specifically tailored for GrapheneOS users.

We are grateful to the original Oblivion developers for creating this essential security tool.

## Why This Fork?

The original Oblivion provides five preset wipe timer options (7, 14, 30, 60, and 90 days). GrapheneReset extends this with **fully customizable wipe timers**, allowing users to set any duration from 1 to 365 days according to their specific security requirements.

This fork was created to provide GrapheneOS users with:
- More granular control over device wipe timing
- A reproducible build system using Nix flakes
- Better integration with GrapheneOS security features
- Simplified development environment setup

## Key Differences from Upstream Oblivion

| Feature | Oblivion (Upstream) | GrapheneReset (This Fork) |
|---------|---------------------|---------------------------|
| **Wipe Timer Options** | 5 fixed presets (7/14/30/60/90 days) | Customizable (1-365 days) + presets |
| **Build System** | Standard Gradle | Nix flakes + gradle2nix |
| **Target Platform** | Android (general) | GrapheneOS optimized |
| **Reproducibility** | Not guaranteed | Reproducible builds via Nix |
| **Development Setup** | Manual SDK/NDK setup | Declarative Nix environment |

## Features

### Core Emergency Wipe Features (Inherited from Oblivion)

- **Automatic device wipe** after specified period of inactivity
- **Multiple trigger methods**:
  - Quick Settings tile
  - Lock screen countdown
  - Notification listener
  - Panic button integration
  - USB connection monitoring
  - Shortcut activation
  - Broadcast receiver
- **Simple, intentional UI** - no guesswork in emergency situations
- **Device admin integration** - secure wipe capabilities
- **QR code sharing** - share device ID with trusted contacts

### New Features in GrapheneReset

- **Custom wipe timers**: Set any duration from 1 to 365 days
- **Improved user experience**: Clear display of custom timer settings
- **Enhanced preset options**: Quick access to common durations with custom fallback

## Installation

### For GrapheneOS Users

1. Download the latest APK from the [Releases](https://github.com/ForgottenBeast/GrapheneReset/releases) page
2. Install the APK on your GrapheneOS device
3. Grant Device Admin permissions when prompted
4. Enable Notification Listener access when prompted
5. Configure your desired wipe timer (preset or custom)
6. The app runs in the background and monitors device unlock activity

**Security Note**: GrapheneReset requires Device Admin permissions to perform factory reset operations. This is the same permission level required by the upstream Oblivion app.

## Building from Source

This project uses Nix flakes for reproducible builds, along with gradle2nix for dependency management.

### Prerequisites

- Nix with flakes enabled
- Git

### Quick Start

1. Clone the repository:
```bash
git clone https://github.com/ForgottenBeast/GrapheneReset.git
cd GrapheneReset
```

2. Enter the development environment:
```bash
nix develop
```

This automatically provides:
- Android SDK (API 35) with GrapheneOS-required build tools (36.0.0)
- Android NDK (29.0.14206865)
- JDK 21
- Gradle
- gradle2nix for dependency locking
- Android emulator and development tools

### Building the APK

#### Method 1: Interactive Development (Recommended)

```bash
# Enter FHS environment for Gradle compatibility
nix develop
android-env

# Inside the android-env shell:
gradle assembleDebug      # Build debug APK
gradle assembleRelease    # Build release APK
```

The built APKs are located in `app/build/outputs/apk/`.

#### Method 2: Reproducible Build with gradle2nix

```bash
# Generate dependency lock file
nix develop
android-env
gradle2nix -t assembleRelease
exit  # Leave android-env

# Build reproducibly using Nix
nix build
```

The reproducible APK will be in `result/`.

### Development Workflow

```bash
# Enter development environment
nix develop

# Generate Gradle dependency lockfile (when dependencies change)
android-env
gradle2nix -t assembleRelease
exit

# Create Android Virtual Device for testing
create-android-avd pixel_8

# Start emulator
emulator @pixel_8

# In another terminal: build and install
android-env
gradle installDebug
```

## Development

### Project Structure

```
GrapheneReset/
├── app/                    # Main application code
│   ├── src/main/java/     # Kotlin source files
│   └── build.gradle       # App-level Gradle config
├── flake.nix              # Nix flake for dev environment
├── gradle.lock            # Dependency lockfile (gradle2nix)
├── build.gradle           # Project-level Gradle config
└── README.md              # This file
```

### Custom Wipe Timer Implementation

The custom timer feature is implemented in `MainActivity.kt`:
- **UI Component**: NumberPicker dialog (1-365 days)
- **Storage**: Preferences system (encrypted)
- **Display**: Custom time TextView with visibility control
- **Integration**: Works with existing LockJobManager for countdown

### Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/your-feature`)
3. Make your changes with clear commit messages
4. Test on GrapheneOS (or AOSP-based ROM)
5. Submit a pull request

**Note**: This is a security-focused application. All contributions will be carefully reviewed for potential security implications.

### GrapheneOS Compatibility Guidelines

- Avoid Google Play Services APIs
- Use GrapheneOS-compatible build tools (specified in flake.nix)
- Test on actual GrapheneOS devices when possible
- Follow Android security best practices

## Credits

### Original Project

GrapheneReset is based on [Oblivion](https://gitlab.com/swampc4-group/oblivionwipe) by the swampc4-group.

**Original Oblivion Contributors:**
- swampc4-group - Original development and design
- All upstream contributors who built the foundation of this security tool

### This Fork

- **Fork Maintainer**: ForgottenBeast
- **Custom Timer Feature**: Added customizable 1-365 day wipe timer
- **Nix Build System**: Reproducible builds with Nix flakes and gradle2nix
- **GrapheneOS Optimization**: Build configuration for GrapheneOS compatibility

### Dependencies and Libraries

- [ZXing](https://github.com/zxing/zxing) - QR code generation
- [SlideToAct](https://github.com/cortinico/slidetoact) - Slide-to-confirm UI component
- [Guardian Project Panic Kit](https://github.com/guardianproject/PanicKit) - Panic button integration
- AndroidX libraries - Modern Android development

## License

```
GrapheneReset - Emergency device wipe for GrapheneOS
Copyright (C) 2025 ForgottenBeast

This is a modified version of Oblivion.
Original Oblivion Copyright (C) 2025 quaatos1

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program. If not, see <https://www.gnu.org/licenses/>.
```

See [LICENSE](LICENSE) for the full GPL-3.0 license text.

## Disclaimer

**This application performs irreversible device wipes.** Use with extreme caution and ensure you understand the configured wipe timer settings. The developers are not responsible for data loss resulting from use or misconfiguration of this application.

Always maintain secure backups of important data.

## Links

- **This Fork**: https://github.com/ForgottenBeast/GrapheneReset
- **Upstream Oblivion**: https://gitlab.com/swampc4-group/oblivionwipe
- **GrapheneOS**: https://grapheneos.org/
- **Issue Tracker**: https://github.com/ForgottenBeast/GrapheneReset/issues
