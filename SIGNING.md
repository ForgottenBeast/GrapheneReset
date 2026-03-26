# APK Signing Configuration

This project uses a self-signed keystore for APK signing to ensure installable builds.

## Automatic Keystore Generation

The keystore is **automatically generated** when you:
- Enter the development environment: `nix develop`
- Enter the FHS build environment: `android-env`
- Build the APK with Nix: `nix build`

You don't need to manually create or manage the keystore!

## Keystore Details

- **Location**: `app/graphenereset.keystore` (auto-generated)
- **Alias**: `graphenereset`
- **Store Password**: `graphenereset`
- **Key Password**: `graphenereset`
- **Type**: PKCS12
- **Validity**: 10,000 days

## Building Signed APKs

The keystore is automatically used for both debug and release builds:

```bash
# Enter the Android development environment
android-env

# Build debug APK (signed)
gradle assembleDebug

# Build release APK (signed)
gradle assembleRelease

# Install debug APK to device
gradle installDebug
```

The signed APKs will be located at:
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`

## Regenerating the Keystore

If you need to regenerate the keystore, you can use the provided script:

```bash
./generate-keystore.sh
```

Or manually with keytool:

```bash
nix develop
keytool -genkeypair -v \
  -keystore app/graphenereset.keystore \
  -alias graphenereset \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass graphenereset \
  -keypass graphenereset \
  -dname "CN=GrapheneReset,OU=Development,O=GrapheneReset,L=Unknown,S=Unknown,C=US"
```

## Security Note

This is a **development keystore** with known passwords, suitable for:
- Open source development
- Debug builds
- Local testing
- CI/CD builds

For production releases to end users, you should:
1. Generate a new keystore with a strong password
2. Store it securely (not in version control)
3. Update `app/build.gradle` signing configuration
4. Consider using environment variables for passwords
