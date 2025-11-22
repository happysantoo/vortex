# Central Portal Token Generation Guide

## Quick Steps

Since token generation requires manual interaction through the web UI, follow these steps:

### 1. Generate Token (Manual Step - Required)

1. **Open your browser** and go to: https://central.sonatype.com/
2. **Sign in** with your credentials (the same username/password from `~/.gradle/gradle.properties`)
3. **Navigate to**: https://central.sonatype.com/usertoken
4. **Click** "Generate User Token"
5. **Enter** a display name (e.g., "vortex-publishing")
6. **Set expiration** (optional, or leave default)
7. **Click** "Generate"
8. **IMPORTANT**: Copy BOTH values shown:
   - Token Username
   - Token Password (this is what you'll use as `mavenCentralToken`)

### 2. Configure Token

Once you have the token, you have two options:

#### Option A: Use the Setup Script (Recommended)

```bash
./scripts/setup-token.sh
```

The script will:
- Prompt you to enter the token password
- Update `~/.gradle/gradle.properties` automatically
- Optionally publish to Maven Central

#### Option B: Manual Configuration

Edit `~/.gradle/gradle.properties` and add:

```properties
mavenCentralToken=<token-password-from-portal>
```

### 3. Publish

Once the token is configured:

```bash
./gradlew publishToSonatype closeAndReleaseSonatypeStagingRepository
```

## Why Manual Token Generation?

The Central Publishing Portal does not provide a programmatic API for token generation. This is a security measure to ensure:
- Tokens are generated with user consent
- Users can see and securely store tokens
- Token generation requires authentication through the web UI

## Troubleshooting

If you get a 401 error:
- Verify the token is correctly set in `~/.gradle/gradle.properties`
- Check that you copied the token password (not username)
- Ensure the token hasn't expired
- Verify your namespace `com.vajrapulse` is registered and approved

## Alternative: Browser Automation

If you want to automate token generation, you can use the Python script:

```bash
# Install dependencies
pip install selenium

# Install ChromeDriver (or use another browser)
# macOS: brew install chromedriver
# Linux: Download from https://chromedriver.chromium.org/

# Run the automation script
python3 scripts/generate_token.py
```

**Note**: Browser automation requires Selenium and a browser driver, and may need adjustments based on the Central Portal UI.

