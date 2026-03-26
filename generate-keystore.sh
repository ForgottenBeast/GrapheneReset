#!/usr/bin/env bash
# Generate a self-signed keystore for GrapheneReset APK signing

set -e

KEYSTORE_FILE="app/graphenereset.keystore"
KEYSTORE_ALIAS="graphenereset"
KEYSTORE_PASSWORD="graphenereset"

echo "Generating self-signed keystore for APK signing..."

# Check if keytool is available
if ! command -v keytool &> /dev/null; then
    echo "Error: keytool not found. Please run this script in the Nix development environment:"
    echo "  nix develop -c ./generate-keystore.sh"
    exit 1
fi

# Remove existing keystore if it exists
if [ -f "$KEYSTORE_FILE" ]; then
    echo "Removing existing keystore..."
    rm "$KEYSTORE_FILE"
fi

# Generate new keystore
keytool -genkeypair \
    -v \
    -keystore "$KEYSTORE_FILE" \
    -alias "$KEYSTORE_ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -storepass "$KEYSTORE_PASSWORD" \
    -keypass "$KEYSTORE_PASSWORD" \
    -dname "CN=GrapheneReset,OU=Development,O=GrapheneReset,L=Unknown,S=Unknown,C=US"

echo ""
echo "Keystore generated successfully at: $KEYSTORE_FILE"
echo "Alias: $KEYSTORE_ALIAS"
echo "Password: $KEYSTORE_PASSWORD"
echo ""
echo "You can now build signed APKs using:"
echo "  gradle assembleRelease"
