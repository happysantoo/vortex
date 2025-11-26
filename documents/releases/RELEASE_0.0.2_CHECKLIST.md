# Release 0.0.2 Checklist

## Pre-Release Checklist

### Code Quality ✅
- [x] All tests pass
- [x] Code coverage meets requirements (>86% for MicroBatcher, >90% for others)
- [x] No linter errors
- [x] Build completes successfully
- [x] Examples compile and run
- [x] Documentation is up to date

### Version Updates
- [x] Update version in `build.gradle.kts` to `0.0.2`
- [ ] Update version in README.md (if hardcoded)
- [ ] Create release notes (RELEASE_0.0.2_NOTES.md)
- [ ] Update CHANGELOG.md (if exists)

### Documentation
- [x] README.md updated with new features
- [x] Examples reviewed and updated
- [x] Backpressure documentation added
- [x] Migration guide updated
- [ ] JavaDoc reviewed and updated

### Git Operations
- [x] All changes committed
- [x] Changes pushed to feature branch
- [ ] Merge feature branch to main (after review)
- [ ] Create release tag: `v0.0.2`
- [ ] Push tag to remote

### Maven Central Publishing

#### Prerequisites
- [ ] Central Publishing Portal account verified
- [ ] Namespace `com.vajrapulse` registered and verified
- [ ] User token generated and configured in `~/.gradle/gradle.properties`
- [ ] GPG key generated and public key uploaded to keyserver
- [ ] GPG private key exported (armored) and configured

#### Credentials Configuration
Verify `~/.gradle/gradle.properties` contains:
```properties
# Central Publishing Portal - Token Authentication
mavenCentralToken=your-portal-token-here
mavenCentralUsername=your-ossrh-username

# GPG signing configuration
signing.keyId=your-gpg-key-id
signingKey=your-gpg-private-key-armored
signingPassword=your-gpg-key-passphrase
```

#### Build and Publish
- [ ] Clean build: `./gradlew clean build`
- [ ] Run tests: `./gradlew test`
- [ ] Verify coverage: `./gradlew jacocoTestCoverageVerification`
- [ ] Build artifacts: `./gradlew build`
- [ ] Verify artifacts in `build/libs/`:
  - [ ] `vortex-0.0.2.jar`
  - [ ] `vortex-0.0.2-sources.jar`
  - [ ] `vortex-0.0.2-javadoc.jar`
- [ ] Verify POM file in `build/publications/maven/pom-default.xml`
- [ ] Publish to staging: `./gradlew publishToSonatype` (if using Gradle Nexus plugin)
- [ ] OR use script: `./scripts/publish-to-central.sh`
- [ ] Verify staging repository
- [ ] Close and release staging repository

#### Post-Publish Verification
- [ ] Wait for sync (usually 10-30 minutes)
- [ ] Verify on Maven Central: https://repo1.maven.org/maven2/com/vajrapulse/vortex/
- [ ] Verify search: https://search.maven.org/search?q=g:com.vajrapulse%20AND%20a:vortex
- [ ] Test dependency resolution in a sample project

### GitHub Release
- [ ] Create GitHub release from tag `v0.0.2`
- [ ] Add release notes from RELEASE_0.0.2_NOTES.md
- [ ] Attach release artifacts (optional, Maven Central is primary)
- [ ] Mark as latest release

### Post-Release
- [ ] Update version to `0.0.3-SNAPSHOT` for next development cycle
- [ ] Announce release (if applicable)
- [ ] Update any external documentation
- [ ] Monitor for issues

## Current Status

### Completed ✅
- Code changes committed and pushed
- Version updated to 0.0.2 in build.gradle.kts
- Release notes created
- Documentation updated
- Examples reviewed and updated

### Pending ⏳
- Merge to main branch
- Create release tag
- Maven Central publishing (requires credentials)
- GitHub release creation

## Notes

- Build is configured for Central Publishing Portal with token authentication
- See `documents/integrations/MAVEN_CENTRAL_RELEASE.md` for detailed publishing instructions
- See `documents/integrations/PUBLISH_STATUS.md` for current publishing configuration

