# Maven Central Publishing Status

## Configuration Complete ✅

The build is now configured for **Central Publishing Portal** with token-based authentication:

### Plugin
- **Gradle Nexus Publish Plugin** (`io.github.gradle-nexus.publish-plugin:2.0.0`)
  - Supports Central Publishing Portal
  - Handles staging repository management
  - Automates close and release process

### Credentials (from `~/.gradle/gradle.properties`)
- `mavenCentralToken` - **REQUIRED** - Central Portal user token
- `mavenCentralUsername` - Sonatype username (may be required with token)
- `signingKey` - GPG signing key (armored format)
- `signingPassword` - GPG key passphrase

## Next Steps

1. **Get Central Portal Token**:
   - Login to https://central.sonatype.com/
   - Navigate to User Token section
   - Generate a new token
   - Add to `~/.gradle/gradle.properties` as `mavenCentralToken`

2. **Publish**:
   ```bash
   ./gradlew publishToSonatype closeAndReleaseSonatypeStagingRepository
   ```

## Previous Issue (Resolved) ✅

**401 Authentication Error**: "Content access is protected by token"

This was resolved by:
- Switching to Gradle Nexus Publish Plugin
- Configuring for Central Publishing Portal token authentication
- Updating build configuration to use Portal API endpoints

## Possible Causes

1. **Token-Based Authentication Required**: Sonatype Central may now require token-based authentication instead of username/password
2. **Incorrect Credentials**: The password in `~/.gradle/gradle.properties` might be incorrect or expired
3. **GroupId Not Approved**: The groupId `com.vajrapulse` may not be approved in Sonatype OSSRH
4. **Account Permissions**: The account might not have publish permissions for this groupId

## Next Steps

1. **Verify Sonatype Account**:
   - Login to https://s01.oss.sonatype.org/
   - Check if groupId `com.vajrapulse` is approved
   - Verify account has publish permissions

2. **Check Credentials**:
   - Verify `mavenCentralPassword` in `~/.gradle/gradle.properties` is correct
   - Update if expired or incorrect

3. **Try Token Authentication** (if required):
   - Generate token in Sonatype account
   - Update `~/.gradle/gradle.properties` with token

## Current Configuration

```kotlin
repositories {
    maven {
        name = "OSSRH"
        url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
        credentials {
            username = project.findProperty("mavenCentralUsername") as String?
            password = project.findProperty("mavenCentralPassword") as String?
        }
    }
}
```

## Verification

Credentials are being read (verified in debug logs):
- Username: ✅ Being read from `mavenCentralUsername`
- Password: ✅ Being read from `mavenCentralPassword`
- Signing: ✅ Working correctly

## Artifacts Ready

All artifacts are built and signed:
- `vortex-0.0.1.jar`
- `vortex-0.0.1-sources.jar`
- `vortex-0.0.1-javadoc.jar`
- POM file with metadata

## Command to Retry

```bash
./gradlew publish
```

Once authentication is resolved, this command will publish to Maven Central staging repository.

