# Maven Central Release Status

## Completed Steps ✅

1. **Release Tag**: `v0.0.1` created and pushed to GitHub
2. **Version Updated**: Changed from `0.0.1-SNAPSHOT` to `0.0.1`
3. **Build Configuration**: Updated to read credentials from `~/.gradle/gradle.properties`
   - Uses `mavenCentralUsername` and `mavenCentralPassword`
   - Uses `signingKey` and `signingPassword` for GPG signing
4. **Signing**: GPG signing configured and working
5. **Build**: All tests passing, artifacts built successfully

## Current Issue ⚠️

**401 Authentication Error** when publishing to Maven Central:
```
Could not PUT 'https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/...'
Received status code 401 from server: Content access is protected by token
```

## Possible Causes

1. **Credentials Issue**: The `mavenCentralUsername`/`mavenCentralPassword` in `~/.gradle/gradle.properties` might be incorrect or expired
2. **Token-Based Auth**: Sonatype might require token-based authentication instead of username/password
3. **GroupId Not Approved**: The groupId `com.vajrapulse` might not be approved in Sonatype OSSRH yet
4. **Account Permissions**: The account might not have permissions to publish to this groupId

## Next Steps to Resolve

### Option 1: Verify Credentials
1. Check that `mavenCentralUsername` and `mavenCentralPassword` in `~/.gradle/gradle.properties` are correct
2. Verify the account has access to publish `com.vajrapulse` groupId
3. Check if the password needs to be updated

### Option 2: Check Sonatype Account Status
1. Login to https://s01.oss.sonatype.org/
2. Verify the account is active
3. Check if groupId `com.vajrapulse` is approved (create ticket at https://issues.sonatype.org/ if needed)

### Option 3: Use Token Authentication
If Sonatype requires tokens:
1. Generate a token in Sonatype account settings
2. Update `~/.gradle/gradle.properties` to use token instead of password
3. Or configure authentication headers in build.gradle.kts

### Option 4: Manual Upload
As an alternative, you can manually upload artifacts:
1. Build artifacts: `./gradlew clean build`
2. Sign artifacts manually with GPG
3. Upload via Sonatype web interface

## Verification Commands

```bash
# Verify credentials are being read
./gradlew tasks --all | grep publish

# Test local publishing
./gradlew publishToMavenLocal

# Attempt Maven Central publish
./gradlew publish
```

## Artifacts Ready

All artifacts are built and ready:
- `vortex-0.0.1.jar`
- `vortex-0.0.1-sources.jar`
- `vortex-0.0.1-javadoc.jar`
- POM file

Location: `build/libs/`

## Current Configuration

- **Credentials Source**: `~/.gradle/gradle.properties`
- **Properties Used**: 
  - `mavenCentralUsername`
  - `mavenCentralPassword`
  - `signingKey`
  - `signingPassword`
- **Repository**: `https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/`

## Support

For Sonatype OSSRH issues, check:
- https://central.sonatype.com/publish/publish-guide
- https://issues.sonatype.org/

