#!/bin/bash
# Script to help generate Central Portal token
# Note: Token generation must be done manually through the web UI

echo "=========================================="
echo "Central Portal Token Generation Helper"
echo "=========================================="
echo ""
echo "Token generation requires manual interaction through the web browser."
echo "Follow these steps:"
echo ""
echo "1. Open your browser and go to: https://central.sonatype.com/"
echo "2. Click 'Sign In' and login with your credentials"
echo "3. Navigate to: https://central.sonatype.com/usertoken"
echo "4. Click 'Generate User Token'"
echo "5. Provide a display name (e.g., 'vortex-publishing')"
echo "6. Set expiration date (optional)"
echo "7. Click 'Generate'"
echo "8. IMPORTANT: Copy BOTH the username and password shown"
echo "   (You won't be able to see them again!)"
echo ""
echo "Once you have the token, update ~/.gradle/gradle.properties:"
echo ""
echo "  mavenCentralToken=<token-password-from-portal>"
echo "  mavenCentralUsername=<token-username-from-portal>"
echo ""
echo "Then run: ./gradlew publishToSonatype closeAndReleaseSonatypeStagingRepository"
echo ""

