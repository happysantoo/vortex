# Maven Central Publishing - SUCCESS! ✅

## Latest Deployment Status (0.0.2)

**Deployment ID**: `dfd8dadc-020e-421f-a7fa-44be74fee97c`  
**Version**: `0.0.2`  
**Status**: Uploaded successfully, processing by Central Portal  
**Date**: November 28, 2025

## Previous Deployment (0.0.1)

**Deployment ID**: `eed6d204-1832-47eb-aac6-10aba1df878a`  
**Version**: `0.0.1`  
**Status**: Uploaded successfully, processing by Central Portal

## How It Works

The vajrapulse project uses a **direct API approach** with the Central Portal, not the JReleaser Gradle plugin. We've implemented the same approach:

### Process

1. **Publish to Maven Local**:
   ```bash
   ./gradlew publishToMavenLocal
   ```

2. **Create Bundle and Upload**:
   ```bash
   ./scripts/publish-to-central.sh
   ```
   
   **Note**: The script now auto-detects the version from `build.gradle.kts`, so you don't need to pass the version argument. You can still override it if needed: `./scripts/publish-to-central.sh 0.0.3`

The script:
- Verifies all artifacts exist (jar, pom, sources, javadoc)
- Generates checksums (MD5, SHA1)
- Creates a zip bundle
- Uploads using Bearer token authentication
- Returns deployment ID for tracking

### Authentication

Uses **base64 token** (`mavenCentralToken`) as Bearer token:
```
Authorization: Bearer <base64_token>
```

The token is `base64(username:password)` and is stored in `~/.gradle/gradle.properties` as `mavenCentralToken`.

### Check Status

Monitor deployment at: https://central.sonatype.com/

After processing (usually a few minutes), artifacts will be available at:
- https://repo1.maven.org/maven2/com/vajrapulse/vortex/0.0.2/
- https://repo1.maven.org/maven2/com/vajrapulse/vortex/0.0.1/ (previous version)

## Why This Approach

1. **JReleaser Gradle Plugin Issue**: The plugin has a bug where it doesn't read `description` and `copyright` from YAML files
2. **Direct API Works**: The Central Portal API accepts Bearer token authentication directly
3. **Matches vajrapulse**: Uses the same reliable approach as the vajrapulse project

## Next Steps

1. Wait for Central Portal to process the deployment
2. Verify artifacts appear on Maven Central
3. Update documentation with Maven/Gradle coordinates
4. Tag the release: `git tag v0.0.1 && git push origin v0.0.1`

## Future Improvements

- Automate the process in a release script
- Add deployment status checking
- Integrate with CI/CD pipeline

