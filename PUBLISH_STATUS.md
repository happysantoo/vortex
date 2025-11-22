# Maven Central Publishing Status

## Configuration Complete ✅

The build is configured to read credentials from `~/.gradle/gradle.properties`:
- `mavenCentralUsername` - Sonatype username
- `mavenCentralPassword` - Sonatype password  
- `signingKey` - GPG signing key
- `signingPassword` - GPG key passphrase

## Current Issue ⚠️

**401 Authentication Error**: "Content access is protected by token"

The credentials are being read correctly (verified in debug output), but Sonatype is returning a 401 error with the message "Content access is protected by token".

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

