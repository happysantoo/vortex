# Maven Central Release Instructions

## Status

✅ **Release Tag Created**: `v0.0.1` has been created and pushed to GitHub
✅ **Version Updated**: Changed from `0.0.1-SNAPSHOT` to `0.0.1` in `build.gradle.kts`
✅ **Build Successful**: All tests pass, artifacts built successfully
✅ **Local Publishing**: Successfully published to Maven Local for testing

⚠️ **Maven Central Publishing**: Requires credentials configuration

## Next Steps to Complete Maven Central Release

### 1. Configure Credentials

You need to set up credentials in `gradle.properties`. Copy from `gradle.properties.example` if needed:

```properties
# OSSRH (Maven Central) credentials
ossrhUsername=your-ossrh-username
ossrhPassword=your-ossrh-password

# GPG signing configuration
signing.keyId=your-gpg-key-id
signing.password=your-gpg-password
signing.secretKeyRingFile=/path/to/your/secring.gpg
```

### 2. Prerequisites

Before publishing, ensure you have:

1. **Sonatype OSSRH Account**
   - Account at https://issues.sonatype.org/
   - Ticket created and approved for groupId `com.vajrapulse`
   - See [RELEASE.md](RELEASE.md) for details

2. **GPG Key**
   - Generated GPG key
   - Public key uploaded to keyserver
   - Secret key exported to `secring.gpg`

### 3. Publish to Maven Central

Once credentials are configured:

```bash
# 1. Build and verify
./gradlew clean build
./gradlew test
./gradlew jacocoTestCoverageVerification

# 2. Publish to staging repository
./gradlew publish

# 3. Go to https://s01.oss.sonatype.org/
# 4. Login and navigate to "Staging Repositories"
# 5. Find your repository (com.vajrapulse:vortex:0.0.1)
# 6. Click "Close" and wait for validation
# 7. If successful, click "Release"
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

