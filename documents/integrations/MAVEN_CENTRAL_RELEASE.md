# Maven Central Release Instructions

## Status

✅ **Release Tag Created**: `v0.0.1` has been created and pushed to GitHub
✅ **Version Updated**: Changed from `0.0.1-SNAPSHOT` to `0.0.1` in `build.gradle.kts`
✅ **Build Successful**: All tests pass, artifacts built successfully
✅ **Local Publishing**: Successfully published to Maven Local for testing

⚠️ **Maven Central Publishing**: Requires credentials configuration

## Next Steps to Complete Maven Central Release

### 1. Configure Credentials

You need to set up credentials in `~/.gradle/gradle.properties`. Copy from `gradle.properties.example` if needed:

**IMPORTANT**: Central Publishing Portal requires token-based authentication.

1. **Get your Portal Token**:
   - Login to https://central.sonatype.com/
   - Navigate to User Token section
   - Generate a new token
   - Copy the token

2. **Configure in `~/.gradle/gradle.properties`**:

```properties
# Central Publishing Portal - Token Authentication (REQUIRED)
mavenCentralToken=your-portal-token-here
mavenCentralUsername=your-ossrh-username

# GPG signing configuration
signing.keyId=your-gpg-key-id
signingKey=your-gpg-private-key-armored
signingPassword=your-gpg-key-passphrase
```

### 2. Prerequisites

Before publishing, ensure you have:

1. **Central Publishing Portal Account**
   - Account at https://central.sonatype.com/
   - Namespace `com.vajrapulse` registered and verified
   - User token generated (see step 1 above)
   - See [Central Portal Documentation](https://central.sonatype.org/publish/publish-portal-gradle/) for details

2. **GPG Key**
   - Generated GPG key
   - Public key uploaded to keyserver (e.g., keyserver.ubuntu.com)
   - Private key exported (armored format) for signing

### 3. Publish to Maven Central

Once credentials are configured:

```bash
# 1. Build and verify
./gradlew clean build
./gradlew test
./gradlew jacocoTestCoverageVerification

# 2. Publish to staging repository and release automatically
./gradlew publishToSonatype closeAndReleaseSonatypeStagingRepository

# OR publish and release manually:
# 2a. Publish to staging
# ./gradlew publishToSonatype
# 2b. Go to https://s01.oss.sonatype.org/
# 2c. Login and navigate to "Staging Repositories"
# 2d. Find your repository (com.vajrapulse:vortex:0.0.1)
# 2e. Click "Close" and wait for validation
# 2f. If successful, click "Release"
```

### 4. Verify Release

After release (usually takes a few minutes to sync):
- Check Maven Central: https://repo1.maven.org/maven2/com/vajrapulse/vortex/
- Check search: https://search.maven.org/search?q=g:com.vajrapulse%20AND%20a:vortex

## Current Status

- ✅ Git tag `v0.0.1` created and pushed
- ✅ Version updated to `0.0.1` (non-SNAPSHOT)
- ✅ All tests passing (81 tests)
- ✅ Code coverage: 88%
- ✅ Artifacts built successfully
- ✅ Published to Maven Local (for testing)
- ⏳ Maven Central publishing pending credentials

## Artifacts Ready for Release

The following artifacts are built and ready:
- `vortex-0.0.1.jar` - Main library
- `vortex-0.0.1-sources.jar` - Source code
- `vortex-0.0.1-javadoc.jar` - JavaDoc
- POM file with all metadata

All artifacts will be signed with GPG before publishing.

## Troubleshooting

If publishing fails:
1. Verify credentials in `gradle.properties`
2. Check GPG key is exported and accessible
3. Ensure Sonatype groupId is approved
4. Verify network connectivity to OSSRH

For detailed release process, see [RELEASE.md](RELEASE.md).

