#!/bin/bash
# Helper script to set up Central Portal token and publish

set -e

echo "=========================================="
echo "Central Portal Token Setup & Publish"
echo "=========================================="
echo ""

GRADLE_PROPS="$HOME/.gradle/gradle.properties"

# Check if token already exists
if grep -q "mavenCentralToken=" "$GRADLE_PROPS" 2>/dev/null; then
    TOKEN=$(grep "mavenCentralToken=" "$GRADLE_PROPS" | cut -d'=' -f2)
    if [ -n "$TOKEN" ] && [ "$TOKEN" != "your-portal-token-here" ]; then
        echo "✓ Token already configured in $GRADLE_PROPS"
        echo ""
        read -p "Do you want to publish now? (y/n): " PUBLISH
        if [ "$PUBLISH" = "y" ]; then
            echo ""
            echo "Publishing to Maven Central..."
            cd "$(dirname "$0")/.."
            ./gradlew publishToSonatype closeAndReleaseSonatypeStagingRepository
            exit 0
        fi
    fi
fi

echo "Token generation must be done through the Central Portal web UI."
echo ""
echo "Steps:"
echo "1. Open: https://central.sonatype.com/"
echo "2. Sign in with your credentials"
echo "3. Go to: https://central.sonatype.com/usertoken"
echo "4. Click 'Generate User Token'"
echo "5. Enter a name (e.g., 'vortex-publishing')"
echo "6. Click 'Generate'"
echo "7. IMPORTANT: Copy BOTH the username and password shown"
echo ""
echo "Once you have the token, run this script again and enter the token when prompted."
echo ""

read -p "Do you have a token ready to enter? (y/n): " HAS_TOKEN
if [ "$HAS_TOKEN" != "y" ]; then
    echo ""
    echo "Please generate a token first, then run this script again."
    echo "You can also manually add it to $GRADLE_PROPS:"
    echo "  mavenCentralToken=<your-token-password>"
    exit 0
fi

echo ""
read -p "Enter the token PASSWORD (the long string): " TOKEN_PASSWORD
if [ -z "$TOKEN_PASSWORD" ]; then
    echo "Error: Token password cannot be empty"
    exit 1
fi

# Update gradle.properties
if [ ! -f "$GRADLE_PROPS" ]; then
    echo "Creating $GRADLE_PROPS..."
    touch "$GRADLE_PROPS"
fi

# Remove old token if exists
sed -i.bak '/^mavenCentralToken=/d' "$GRADLE_PROPS" 2>/dev/null || true

# Add new token
echo "" >> "$GRADLE_PROPS"
echo "# Central Portal Token (added by setup script)" >> "$GRADLE_PROPS"
echo "mavenCentralToken=$TOKEN_PASSWORD" >> "$GRADLE_PROPS"

echo ""
echo "✓ Token saved to $GRADLE_PROPS"
echo ""

read -p "Do you want to publish to Maven Central now? (y/n): " PUBLISH
if [ "$PUBLISH" = "y" ]; then
    echo ""
    echo "Publishing to Maven Central..."
    cd "$(dirname "$0")/.."
    ./gradlew publishToSonatype closeAndReleaseSonatypeStagingRepository
else
    echo ""
    echo "To publish later, run:"
    echo "  ./gradlew publishToSonatype closeAndReleaseSonatypeStagingRepository"
fi

