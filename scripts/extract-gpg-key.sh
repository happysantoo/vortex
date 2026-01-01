#!/bin/bash

# Script to extract properly formatted GPG key for GitHub secrets
# This reads the signingKey from gradle.properties and converts \n to actual newlines

set -e

GRADLE_PROPS="$HOME/.gradle/gradle.properties"

if [ ! -f "$GRADLE_PROPS" ]; then
    echo "ERROR: $GRADLE_PROPS not found"
    exit 1
fi

echo "Extracting GPG key from $GRADLE_PROPS..."

# Extract the signingKey value and convert \n to actual newlines
SIGNING_KEY=$(grep '^signingKey=' "$GRADLE_PROPS" | sed 's/^signingKey=//' | sed 's/\\n/\n/g')

if [ -z "$SIGNING_KEY" ]; then
    echo "ERROR: signingKey not found in $GRADLE_PROPS"
    exit 1
fi

echo "==============================================="
echo "GPG Key for GitHub Secret (copy everything between the lines):"
echo "==============================================="
echo "$SIGNING_KEY"
echo "==============================================="
echo ""
echo "To update the GitHub secret, run:"
echo "gh secret set GPG_PRIVATE_KEY --body \"\$(<path_to_file_with_above_content>)\""
echo ""
echo "Or copy the content above and set it manually in the GitHub web interface."