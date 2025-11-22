# Releasing to Maven Central

This guide explains how to release Vortex to Maven Central (Sonatype OSSRH).

## Prerequisites

1. **Sonatype OSSRH Account**
   - Create account at https://issues.sonatype.org/
   - Create a ticket to claim your groupId (com.vajrapulse)
   - Wait for approval

2. **GPG Key**
   - Generate GPG key: `gpg --gen-key`
   - Export public key: `gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID`
   - Export secret key: `gpg --export-secret-keys -o secring.gpg`

3. **Gradle Properties**
   - Copy `gradle.properties.example` to `gradle.properties`
   - Fill in your credentials

## Release Process

### 1. Update Version

Update version in `build.gradle.kts`:
```kotlin
version = "0.0.1"
```

### 2. Build and Test

```bash
./gradlew clean build
./gradlew test
./gradlew jacocoTestCoverageVerification
```

### 3. Publish to Staging

```bash
./gradlew publishToMavenLocal  # Test locally first
./gradlew publish
```

This publishes to Sonatype OSSRH staging repository.

### 4. Release from Staging

1. Go to https://s01.oss.sonatype.org/
2. Login with your OSSRH credentials
3. Navigate to "Staging Repositories"
4. Find your repository (com.vajrapulse:vortex:0.0.1)
5. Click "Close" and wait for validation
6. If successful, click "Release"

### 5. Verify Release

After release (usually takes a few minutes to sync):
- Check Maven Central: https://repo1.maven.org/maven2/com/vajrapulse/vortex/
- Check search: https://search.maven.org/search?q=g:com.vajrapulse%20AND%20a:vortex

## Snapshot Releases

For snapshot releases, use version `0.0.1-SNAPSHOT` and publish to:
```kotlin
repositories {
    maven {
        url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    }
}
```

## Troubleshooting

### GPG Signing Issues
- Ensure `signing.keyId` matches your GPG key ID
- Verify secret key file path is correct
- Check GPG key is exported to keyserver

### Authentication Issues
- Verify OSSRH credentials in `gradle.properties`
- Check groupId is approved in Sonatype JIRA

### Validation Errors
- Check POM has all required fields
- Verify license information
- Ensure sources and javadoc JARs are included

## Post-Release

1. Create git tag: `git tag -a v0.0.1 -m "Release 0.0.1"`
2. Push tag: `git push origin v0.0.1`
3. Update version to next snapshot: `0.0.2-SNAPSHOT`
4. Update CHANGELOG.md (if maintained)

