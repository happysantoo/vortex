# Token Authentication Status

## What We've Tried

1. ✅ **Generated base64 token**: Created `base64(username:password)` token
2. ✅ **Configured in gradle.properties**: Token saved as `mavenCentralToken`
3. ❌ **Standard maven-publish**: Doesn't support Bearer tokens (only HTTP Basic)
4. ❌ **Gradle Nexus Publish Plugin**: Doesn't support Bearer tokens
5. ❌ **Various credential combinations**: Empty username, token as password, etc.

## Current Issue

The Central Portal requires **Bearer token authentication** in the `Authorization` header:
```
Authorization: Bearer <base64_token>
```

However, Gradle's standard `maven-publish` plugin only supports HTTP Basic authentication:
```
Authorization: Basic <base64_credentials>
```

## Possible Solutions

### Option 1: Use Central Portal API Directly
The Central Portal has a REST API that accepts Bearer tokens. We could:
- Use a custom Gradle task with HTTP client (OkHttp, Apache HttpClient)
- Manually upload artifacts via API calls
- This would require custom implementation

### Option 2: Generate Actual Portal Token
The base64(username:password) might work for some endpoints, but the Central Portal might require:
- A token generated through the web UI at https://central.sonatype.com/usertoken
- This token has its own username and password
- Then base64 encode that token's username:password

### Option 3: Wait for Plugin Support
Community plugins might add Bearer token support in the future.

## Next Steps

1. **Verify token format**: Check if base64(username:password) is correct, or if we need a portal-generated token
2. **Try Central Portal API**: Use direct HTTP calls with Bearer token
3. **Generate portal token**: Create token through web UI and use that

## Current Configuration

- Token generated: `base64(username:password)` ✅
- Token saved in: `~/.gradle/gradle.properties` ✅
- Build configured: ✅
- Authentication failing: ❌ (401 - Content access is protected by token)

