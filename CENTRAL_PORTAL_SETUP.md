# Central Publishing Portal Setup Guide

## Overview

The Vortex library is configured to publish to Maven Central via the **Central Publishing Portal**, which requires token-based authentication.

## Prerequisites

1. **Central Portal Account**: Create an account at https://central.sonatype.com/
2. **Namespace Registration**: Register and verify the namespace `com.vajrapulse`
3. **User Token**: Generate a user token for authentication
4. **GPG Key**: Set up GPG signing for artifacts

## Step-by-Step Setup

### 1. Register Namespace

1. Login to https://central.sonatype.com/
2. Navigate to "Namespaces" section
3. Register `com.vajrapulse` namespace
4. Complete DNS verification (if required)
5. Wait for approval

### 2. Generate User Token

1. In Central Portal, go to "User Token" section
2. Click "Generate Token"
3. Copy the generated token (you won't be able to see it again)
4. Store it securely

### 3. Configure Gradle Properties

Create or update `~/.gradle/gradle.properties`:

```properties
# Central Publishing Portal Token (REQUIRED)
mavenCentralToken=your-generated-token-here
mavenCentralUsername=your-username

# GPG Signing
signing.keyId=your-gpg-key-id
signingKey=-----BEGIN PGP PRIVATE KEY BLOCK-----
...
-----END PGP PRIVATE KEY BLOCK-----
signingPassword=your-gpg-passphrase
```

### 4. Publish

Once configured, publish using:

```bash
# Publish and automatically close/release
./gradlew publishToSonatype closeAndReleaseSonatypeStagingRepository
```

## Plugin Used

This project uses the **Gradle Nexus Publish Plugin** (`io.github.gradle-nexus.publish-plugin`) which:
- Supports Central Publishing Portal
- Handles staging repository management
- Automates close and release process
- Supports token-based authentication

## References

- [Central Portal Documentation](https://central.sonatype.org/publish/publish-portal-gradle/)
- [Gradle Nexus Publish Plugin](https://github.com/gradle-nexus/publish-plugin)

