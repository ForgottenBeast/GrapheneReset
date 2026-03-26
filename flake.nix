{
  description = "GrapheneReset - Hardened Android ROM development environment with Gradle and emulator";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
    android-nixpkgs = {
      url = "github:tadfisher/android-nixpkgs";
      inputs.nixpkgs.follows = "nixpkgs";
    };
    gradle2nix = {
      url = "github:tadfisher/gradle2nix/30cfe5889188524223364ee7919d94e83d6ee44a";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs =
    {
      self,
      nixpkgs,
      flake-utils,
      android-nixpkgs,
      gradle2nix,
    }:
    flake-utils.lib.eachDefaultSystem (
      system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config = {
            android_sdk.accept_license = true;
            allowUnfree = true;
          };
        };

        # Android SDK composition using android-nixpkgs
        # Configured for GrapheneOS development requirements
        androidSdk = android-nixpkgs.sdk.${system} (
          sdkPkgs: with sdkPkgs; [
            cmdline-tools-latest
            build-tools-34-0-0 # Required by gradle dependencies
            build-tools-36-0-0 # GrapheneOS requires build-tools 36.0.0
            platform-tools
            platforms-android-33 # Required by gradle dependencies
            platforms-android-35
            emulator
            # System images for emulator (x86_64 for faster development)
            system-images-android-35-default-x86-64
            # GrapheneOS requires NDK 29.0.14206865 for native code
            ndk-29-0-14206865
          ]
        );

        # FHS environment for Gradle compatibility
        # Required because Gradle expects a traditional filesystem layout
        androidEnv = pkgs.buildFHSEnv {
          name = "android-env";
          targetPkgs =
            _:
            (with pkgs; [
              # Android SDK and tools
              androidSdk
              # Java (required for Gradle and Android development)
              jdk21
              # Build tools
              gradle
              # Development tools
              git
              which
              file
              # Emulator dependencies
              libGL
              libpulseaudio
              libx11
              libxext
              libxi
              libxrender
              libxtst
              zlib
            ]);
          multiPkgs = _: (with pkgs; [ zlib ]);

          # Set up Android environment variables
          profile = ''
            export ANDROID_HOME="${androidSdk}/share/android-sdk"
            export ANDROID_SDK_ROOT="$ANDROID_HOME"
            export ANDROID_NDK_ROOT="$ANDROID_HOME/ndk-bundle"
            export GRADLE_OPTS="-Dorg.gradle.project.android.aapt2FromMavenOverride=$ANDROID_HOME/build-tools/34.0.0/aapt2"
            export JAVA_HOME="${pkgs.jdk21}"
            export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$JAVA_HOME/bin:$PATH"

            # KVM acceleration for emulator (if available)
            export ANDROID_EMULATOR_USE_SYSTEM_LIBS=1

            # Gradle configuration
            export GRADLE_USER_HOME="$PWD/.gradle"

            # Auto-generate signing keystore if it doesn't exist
            if [ ! -f app/graphenereset.keystore ]; then
              echo "🔐 Generating self-signed keystore for APK signing..."
              keytool -genkeypair \
                -v \
                -keystore app/graphenereset.keystore \
                -alias graphenereset \
                -keyalg RSA \
                -keysize 2048 \
                -validity 10000 \
                -storepass graphenereset \
                -keypass graphenereset \
                -dname "CN=GrapheneReset,OU=Development,O=GrapheneReset,L=Unknown,S=Unknown,C=US" 2>/dev/null
              echo "✓ Keystore generated successfully"
              echo ""
            fi

            echo "Android development environment loaded!"
            echo "ANDROID_HOME: $ANDROID_HOME"
            echo "Java version: $(java -version 2>&1 | head -1)"
            echo ""
            echo "Quick commands:"
            echo "  gradle assembleDebug    - Build signed debug APK"
            echo "  gradle assembleRelease  - Build signed release APK"
            echo "  emulator @<avd-name>    - Start emulator"
            echo "  adb devices             - List connected devices"
            echo ""
            echo "📝 APKs are automatically signed with app/graphenereset.keystore"
          '';
          runScript = "bash";
        };

        # Helper script to create AVD
        createAvdScript = pkgs.writeShellScriptBin "create-android-avd" ''
          set -e
          AVD_NAME="''${1:-pixel_8}"

          echo "Creating Android Virtual Device: $AVD_NAME"
          echo "System image: android-35 x86_64"

          ${androidSdk}/bin/avdmanager create avd \
            --name "$AVD_NAME" \
            --package "system-images;android-35;default;x86_64" \
            --device "pixel_8" \
            --force

          echo ""
          echo "AVD created successfully!"
          echo "Start it with: emulator @$AVD_NAME"
        '';

        # Helper script to build APK
        buildApkScript = pkgs.writeShellScriptBin "build-apk" ''
          set -e
          BUILD_TYPE="''${1:-debug}"

          echo "Building $BUILD_TYPE APK..."

          ${androidEnv}/bin/android-env -c "gradle assemble''${BUILD_TYPE^}"

          echo ""
          echo "APK built successfully!"
          find app/build/outputs/apk -name "*.apk" -type f
        '';

      in
      {
        # Development shell with Android SDK, Gradle, and emulator
        # Includes GrapheneOS-specific build tools
        devShells.default = pkgs.mkShell {
          buildInputs = with pkgs; [
            androidEnv
            createAvdScript
            buildApkScript
            # gradle2nix for generating dependency lock files
            gradle2nix.packages.${system}.gradle2nix
            # Java and Gradle for gradle2nix
            jdk21
            gradle
            # Additional useful tools
            android-tools # adb, fastboot
            android-studio # Android Studio IDE
            # GrapheneOS-specific tools
            nodejs_24 # Node.js 24 LTS for adevtool (vendor file extraction)
            yarn # Package manager for Node.js tools
            git-repo # AOSP version control tool
            rsync # File synchronization
            zip
            unzip
            openssh # For release signing with ssh-keygen
          ];

          # Set environment variables for gradle2nix
          JAVA_HOME = "${pkgs.jdk21}";
          ANDROID_HOME = "${androidSdk}/share/android-sdk";
          ANDROID_SDK_ROOT = "${androidSdk}/share/android-sdk";

          shellHook = ''
            # Auto-generate signing keystore if it doesn't exist
            if [ ! -f app/graphenereset.keystore ]; then
              echo "🔐 Generating self-signed keystore for APK signing..."
              ${pkgs.jdk21}/bin/keytool -genkeypair \
                -v \
                -keystore app/graphenereset.keystore \
                -alias graphenereset \
                -keyalg RSA \
                -keysize 2048 \
                -validity 10000 \
                -storepass graphenereset \
                -keypass graphenereset \
                -dname "CN=GrapheneReset,OU=Development,O=GrapheneReset,L=Unknown,S=Unknown,C=US" 2>/dev/null
              echo "✓ Keystore generated at app/graphenereset.keystore"
              echo ""
            fi

            echo "🤖 GrapheneOS/Android Development Environment"
            echo "=============================================="
            echo ""
            echo "📦 Dependency Management:"
            echo "  gradle2nix -t assembleRelease  - Generate gradle.lock"
            echo ""
            echo "🔧 Development Tools:"
            echo "  android-env                    - Enter FHS environment"
            echo "  create-android-avd [name]      - Create AVD"
            echo "  build-apk [debug|release]      - Build APK"
            echo "  android-studio                 - Launch Android Studio"
            echo ""
            echo "🔐 GrapheneOS Tools:"
            echo "  node --version                 - Node.js 24 LTS (adevtool)"
            echo "  yarn --version                 - Yarn package manager"
            echo "  repo --version                 - AOSP version control"
            echo "  ssh-keygen                     - Release signing tool"
            echo ""
            echo "🚀 Quick Start:"
            echo "  1. android-env                 - Enter build environment"
            echo "  2. gradle assembleDebug        - Build signed debug APK"
            echo "  3. gradle assembleRelease      - Build signed release APK"
            echo "  Or: nix build                  - Build reproducibly with Nix"
            echo ""
            echo "📝 Signing: APKs are automatically signed with app/graphenereset.keystore"
            echo "For GrapheneOS compatibility, avoid Google Play Services APIs"
          '';
        };

        # Package to build GrapheneReset APK using gradle2nix
        packages.default = gradle2nix.builders.${system}.buildGradlePackage {
          pname = "graphenereset";
          version = "2.0.0";

          src = ./.;

          # Lock file generated by: gradle2nix -t assembleRelease
          lockFile = ./gradle.lock;

          # Gradle tasks to run (gradleBuildFlags is the correct parameter for gradle2nix)
          gradleBuildFlags = [ "assembleRelease" ];

          # Use Gradle 8.8 to match the lock file
          gradle = pkgs.gradle_8;

          # Use JDK 21 for building
          buildJdk = pkgs.jdk21;

          # Set Android SDK environment variables
          ANDROID_HOME = "${androidSdk}/share/android-sdk";
          ANDROID_SDK_ROOT = "${androidSdk}/share/android-sdk";
          ANDROID_NDK_ROOT = "${androidSdk}/share/android-sdk/ndk-bundle";

          # Android-specific Gradle options
          GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${androidSdk}/share/android-sdk/build-tools/34.0.0/aapt2";

          # Include Android SDK in build inputs
          nativeBuildInputs = [ androidSdk pkgs.jdk21 ];

          # Generate self-signed keystore before building
          preBuild = ''
            echo "Generating self-signed keystore for APK signing..."
            if [ ! -f app/graphenereset.keystore ]; then
              ${pkgs.jdk21}/bin/keytool -genkeypair \
                -v \
                -keystore app/graphenereset.keystore \
                -alias graphenereset \
                -keyalg RSA \
                -keysize 2048 \
                -validity 10000 \
                -storepass graphenereset \
                -keypass graphenereset \
                -dname "CN=GrapheneReset,OU=Development,O=GrapheneReset,L=Unknown,S=Unknown,C=US"
              echo "Keystore generated successfully"
            else
              echo "Keystore already exists, skipping generation"
            fi
          '';

          installPhase = ''
            mkdir -p $out
            cp app/build/outputs/apk/release/*.apk $out/
          '';
        };

        # Emulator package for quick testing
        packages.emulator = pkgs.writeShellScriptBin "run-android-emulator" ''
          ${androidSdk}/bin/emulator "@''${1:-pixel_8}" \
            -no-snapshot-save \
            -gpu swiftshader_indirect \
            "$@"
        '';

        apps.default = {
          type = "app";
          program = "${self.packages.${system}.emulator}/bin/run-android-emulator";
        };
      }
    );
}
