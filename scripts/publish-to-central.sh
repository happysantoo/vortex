#!/usr/bin/env bash
# Publish to Maven Central using Central Portal API
# Uses base64 token for authentication

set -euo pipefail

# Read version from build.gradle.kts if not provided as argument
if [[ -z "${1:-}" ]]; then
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
    VERSION=$(grep 'version =' "${PROJECT_ROOT}/build.gradle.kts" | sed 's/.*version = "\(.*\)".*/\1/')
    if [[ -z "${VERSION}" ]] || [[ "${VERSION}" == *"version"* ]]; then
        echo "ERROR: Could not determine version from build.gradle.kts"
        echo "Please provide version as argument: $0 <version>"
        exit 1
    fi
    echo "Auto-detected version from build.gradle.kts: ${VERSION}"
else
    VERSION="${1}"
fi
GROUP_PATH="com/vajrapulse"
ARTIFACT="vortex"
REPO_ROOT="${HOME}/.m2/repository"

echo "=========================================="
echo "Publishing vortex ${VERSION} to Maven Central"
echo "=========================================="
echo ""

# Check if artifacts exist in Maven Local
ARTIFACT_DIR="${REPO_ROOT}/${GROUP_PATH}/${ARTIFACT}/${VERSION}"
if [[ ! -d "${ARTIFACT_DIR}" ]]; then
    echo "ERROR: Artifacts not found in ${ARTIFACT_DIR}"
    echo "Please run: ./gradlew publishToMavenLocal"
    exit 1
fi

echo "Checking artifacts..."
for artifact in \
    "${ARTIFACT_DIR}/${ARTIFACT}-${VERSION}.pom" \
    "${ARTIFACT_DIR}/${ARTIFACT}-${VERSION}.module" \
    "${ARTIFACT_DIR}/${ARTIFACT}-${VERSION}.jar" \
    "${ARTIFACT_DIR}/${ARTIFACT}-${VERSION}-sources.jar" \
    "${ARTIFACT_DIR}/${ARTIFACT}-${VERSION}-javadoc.jar"; do
    if [[ ! -f "${artifact}" ]]; then
        echo "ERROR: Missing ${artifact}"
        exit 1
    fi
done

echo "✓ All artifacts found"
echo ""

# Generate checksums if missing
echo "Generating checksums..."
for artifact in \
    "${ARTIFACT_DIR}/${ARTIFACT}-${VERSION}.pom" \
    "${ARTIFACT_DIR}/${ARTIFACT}-${VERSION}.module" \
    "${ARTIFACT_DIR}/${ARTIFACT}-${VERSION}.jar" \
    "${ARTIFACT_DIR}/${ARTIFACT}-${VERSION}-sources.jar" \
    "${ARTIFACT_DIR}/${ARTIFACT}-${VERSION}-javadoc.jar"; do
    # Use md5sum/sha1sum for Linux, md5/shasum for macOS
    if command -v md5sum >/dev/null 2>&1; then
        # Linux
        [[ -f "${artifact}.md5" ]] || md5sum "${artifact}" | awk '{print $1}' > "${artifact}.md5"
        [[ -f "${artifact}.sha1" ]] || sha1sum "${artifact}" | awk '{print $1}' > "${artifact}.sha1"
    else
        # macOS
        [[ -f "${artifact}.md5" ]] || md5 -q "${artifact}" > "${artifact}.md5"
        [[ -f "${artifact}.sha1" ]] || shasum -a 1 "${artifact}" | awk '{print $1}' > "${artifact}.sha1"
    fi
done
echo "✓ Checksums generated"
echo ""

# Create bundle
OUT="/tmp/vortex-${VERSION}-central.zip"
echo "Creating bundle: ${OUT}"
rm -f "${OUT}"
cd "${REPO_ROOT}"
zip -r "${OUT}" "${GROUP_PATH}/${ARTIFACT}/${VERSION}" >/dev/null
echo "✓ Bundle created"
echo ""

# Get credentials from gradle.properties
GRADLE_PROPS="${HOME}/.gradle/gradle.properties"
if [[ ! -f "${GRADLE_PROPS}" ]]; then
    echo "ERROR: ${GRADLE_PROPS} not found"
    exit 1
fi

# Read base64 token
TOKEN=$(grep "^mavenCentralToken=" "${GRADLE_PROPS}" | cut -d'=' -f2)
if [[ -z "${TOKEN}" ]]; then
    echo "ERROR: mavenCentralToken not found in ${GRADLE_PROPS}"
    exit 1
fi

echo "Uploading to Central Portal..."
echo ""

# Upload using base64 token as Bearer token
RESPONSE=$(curl -s -w "\n%{http_code}" \
    -H "Authorization: Bearer ${TOKEN}" \
    -F "bundle=@${OUT}" \
    "https://central.sonatype.com/api/v1/publisher/upload?publishingType=AUTOMATIC")

HTTP_CODE=$(echo "${RESPONSE}" | tail -n1)
BODY=$(echo "${RESPONSE}" | sed '$d')

if [[ "${HTTP_CODE}" == "200" ]] || [[ "${HTTP_CODE}" == "201" ]]; then
    echo "✓ Upload successful!"
    echo ""
    echo "Response:"
    echo "${BODY}" | python3 -m json.tool 2>/dev/null || echo "${BODY}"
    echo ""
    echo "Deployment is being processed. Check status at:"
    echo "https://central.sonatype.com/"
    echo ""
    
    # Create GitHub release
    echo "=========================================="
    echo "Creating GitHub Release v${VERSION}"
    echo "=========================================="
    echo ""
    
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
    RELEASE_NOTES="${PROJECT_ROOT}/documents/releases/RELEASE_NOTES_${VERSION}.md"
    
    # Check if release notes exist
    if [[ ! -f "${RELEASE_NOTES}" ]]; then
        echo "⚠ Warning: Release notes not found at ${RELEASE_NOTES}"
        echo "Creating release without release notes..."
        gh release create "v${VERSION}" \
            --title "Release ${VERSION}" \
            --notes "Release ${VERSION} of Vortex Micro-Batching Library" || {
            echo "✗ Failed to create GitHub release"
            echo "Note: Maven Central upload was successful, but GitHub release creation failed"
            exit 1
        }
    else
        echo "Using release notes from: ${RELEASE_NOTES}"
        gh release create "v${VERSION}" \
            --title "Release ${VERSION}: $(head -n 1 "${RELEASE_NOTES}" | sed 's/# //' | head -c 80)" \
            --notes-file "${RELEASE_NOTES}" || {
            echo "✗ Failed to create GitHub release"
            echo "Note: Maven Central upload was successful, but GitHub release creation failed"
            exit 1
        }
    fi
    
    echo "✓ GitHub release created: https://github.com/happysantoo/vortex/releases/tag/v${VERSION}"
    echo ""
else
    echo "✗ Upload failed with HTTP ${HTTP_CODE}"
    echo "Response:"
    echo "${BODY}"
    exit 1
fi

