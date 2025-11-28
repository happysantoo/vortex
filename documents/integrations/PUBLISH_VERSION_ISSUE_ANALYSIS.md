# Maven Central Publishing Version Issue - Analysis & Fix

## Issue Summary

The 0.0.2 package published to Maven Central was not the correct version. Investigation revealed that the `publish-to-central.sh` script had a hardcoded default version of `0.0.1`, which caused the wrong version to be published when the script was run without explicitly passing the version as an argument.

## Root Cause

**File**: `scripts/publish-to-central.sh`

**Problem**: Line 7 had:
```bash
VERSION="${1:-0.0.1}"
```

This meant:
- If the script was run as `./scripts/publish-to-central.sh`, it would default to `0.0.1`
- If the script was run as `./scripts/publish-to-central.sh 0.0.2`, it would use `0.0.2`

**What Happened**: When publishing 0.0.2, the script was likely run without the version argument, causing it to publish 0.0.1 artifacts instead of 0.0.2.

## Solution

Updated the script to automatically read the version from `build.gradle.kts` when no version argument is provided:

```bash
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
```

## Benefits

1. **Prevents Version Mismatches**: The script now automatically uses the version from `build.gradle.kts`, ensuring consistency
2. **Still Allows Override**: You can still pass a version explicitly if needed: `./scripts/publish-to-central.sh 0.0.3`
3. **Better Error Handling**: The script will fail with a clear error message if it can't determine the version
4. **User-Friendly**: No need to remember to pass the version argument

## Usage

### Automatic Version Detection (Recommended)
```bash
./gradlew publishToMavenLocal
./scripts/publish-to-central.sh
```

The script will automatically detect `0.0.2` from `build.gradle.kts`.

### Explicit Version Override (If Needed)
```bash
./gradlew publishToMavenLocal
./scripts/publish-to-central.sh 0.0.3
```

## Verification

To verify the script correctly detects the version:
```bash
# Test version detection
cd /path/to/vortex
VERSION=$(grep 'version =' build.gradle.kts | sed 's/.*version = "\(.*\)".*/\1/')
echo "Detected version: ${VERSION}"
```

## Next Steps

1. **Verify Current Maven Central Status**: Check what version is actually published on Maven Central
2. **Republish if Needed**: If 0.0.1 was published instead of 0.0.2, republish with the correct version:
   ```bash
   # Ensure build.gradle.kts has version = "0.0.2"
   ./gradlew clean build publishToMavenLocal
   ./scripts/publish-to-central.sh
   ```
3. **Update Documentation**: Ensure all release documentation reflects the correct version

## Prevention

To prevent this issue in the future:
- ✅ Script now auto-detects version from `build.gradle.kts`
- ✅ Always verify the version before publishing
- ✅ Check the script output which now shows: "Auto-detected version from build.gradle.kts: X.X.X"
- ✅ Consider adding a confirmation prompt before publishing

## Related Files

- `scripts/publish-to-central.sh` - Updated script with auto-version detection
- `build.gradle.kts` - Source of truth for version (line 21: `version = "0.0.2"`)
- `documents/integrations/PUBLISH_SUCCESS.md` - Previous publishing documentation (shows 0.0.1 was published)

