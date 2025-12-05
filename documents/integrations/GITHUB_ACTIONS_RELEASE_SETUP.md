# GitHub Actions Release Automation Setup

**Date:** 2025-12-05  
**Version:** 0.0.6+

---

## Overview

The GitHub Actions workflow automatically handles the complete release process when code is merged to the `main` branch:

1. ✅ **Creates Git Tag** - Automatically creates and pushes `v<version>` tag
2. ✅ **Publishes to Maven Central** - Uploads artifacts via Central Portal API
3. ✅ **Creates GitHub Release** - Automatically creates release with release notes

---

## Workflow Configuration

### Trigger

The release workflow runs automatically when:
- Code is **pushed to `main` branch** (after PR merge)
- The `build` job completes successfully

**Note:** The workflow does NOT run on:
- Pull requests (only on merge to main)
- Pushes to other branches (except `release-*` branches for testing)

### Workflow File

`.github/workflows/build.yml` contains the release automation.

---

## Required GitHub Secrets

The workflow requires the following secrets to be configured in GitHub repository settings:

### Required Secrets

1. **`MAVEN_CENTRAL_TOKEN`** (REQUIRED)
   - Central Portal user token for Maven Central publishing
   - Get from: https://central.sonatype.com/usertoken
   - Format: Base64 encoded token (username:password)

### Optional Secrets (for GPG signing)

2. **`GPG_PRIVATE_KEY`** (OPTIONAL)
   - GPG private key in armored format
   - Used for signing artifacts
   - If not provided, artifacts will be published without signing

3. **`GPG_PASSPHRASE`** (OPTIONAL)
   - Passphrase for the GPG private key
   - Required if `GPG_PRIVATE_KEY` is provided

4. **`GPG_KEY_ID`** (OPTIONAL)
   - GPG key ID (last 8 characters of key fingerprint)
   - Required if `GPG_PRIVATE_KEY` is provided

### Automatic Secrets

- **`GITHUB_TOKEN`** - Automatically provided by GitHub Actions
  - Used for creating tags, releases, and authenticating GitHub CLI
  - No manual configuration needed

---

## How to Configure Secrets

### Step 1: Get Maven Central Token

1. Login to https://central.sonatype.com/
2. Navigate to **User Token** section
3. Click **Generate User Token**
4. Copy the token (you won't be able to see it again!)

### Step 2: Add Secrets to GitHub

1. Go to your repository on GitHub
2. Navigate to **Settings** → **Secrets and variables** → **Actions**
3. Click **New repository secret**
4. Add each secret:
   - Name: `MAVEN_CENTRAL_TOKEN`
   - Value: Your Central Portal token
5. Repeat for optional GPG secrets if you want signing

### Step 3: Verify Secrets

After adding secrets, the workflow will use them automatically on the next push to `main`.

---

## Release Process Flow

### 1. PR Merge to Main

When a PR is merged to `main`:
```
PR merged → Push to main → Workflow triggered
```

### 2. Build Job

The `build` job runs first:
- ✅ Compiles code
- ✅ Runs all tests
- ✅ Generates coverage reports
- ✅ Verifies coverage requirements

### 3. Release Job (Only on Push to Main)

After successful build, the `release` job runs:

#### Step 1: Extract Version
- Reads version from `build.gradle.kts`
- Example: `version = "0.0.6"` → extracts `0.0.6`

#### Step 2: Build and Test
- Runs `./gradlew clean build jacocoTestCoverageVerification`
- Ensures all tests pass and coverage is met

#### Step 3: Publish to Maven Local
- Runs `./gradlew publishToMavenLocal`
- Prepares artifacts for Maven Central upload

#### Step 4: Configure Credentials
- Sets up `~/.gradle/gradle.properties` with:
  - `mavenCentralToken` from `MAVEN_CENTRAL_TOKEN` secret
  - GPG signing keys (if provided)

#### Step 5: Create Git Tag
- Creates annotated tag: `v0.0.6`
- Pushes tag to remote repository
- Skips if tag already exists

#### Step 6: Publish to Maven Central
- Runs `./scripts/publish-to-central.sh <version>`
- Script:
  1. Verifies all artifacts exist
  2. Generates checksums (MD5, SHA1)
  3. Creates zip bundle
  4. Uploads to Central Portal API
  5. **Creates GitHub release** (after successful upload)

#### Step 7: Verify Release
- Verifies GitHub release was created
- Shows release details

---

## Release Notes

The workflow automatically uses release notes from:
- `documents/releases/RELEASE_NOTES_<version>.md`

**Example:** For version `0.0.6`, it looks for:
- `documents/releases/RELEASE_NOTES_0.0.6.md`

If the file doesn't exist, the release is created with a default title.

---

## Troubleshooting

### Workflow Doesn't Run

**Issue:** Release job doesn't run after PR merge

**Solutions:**
- Verify the push was to `main` branch (not another branch)
- Check that the `build` job completed successfully
- Verify workflow file is in `.github/workflows/build.yml`

### Maven Central Upload Fails

**Issue:** `ERROR: mavenCentralToken not found`

**Solutions:**
- Verify `MAVEN_CENTRAL_TOKEN` secret is set in GitHub repository settings
- Check that the secret name matches exactly (case-sensitive)
- Verify the token is valid and not expired

### Git Tag Push Fails

**Issue:** `Permission denied` when pushing tag

**Solutions:**
- Verify `GITHUB_TOKEN` is available (should be automatic)
- Check repository permissions for GitHub Actions
- Ensure workflow has write permissions

### GitHub Release Not Created

**Issue:** Maven Central upload succeeds but release not created

**Solutions:**
- Check if `gh` CLI is installed correctly
- Verify `GITHUB_TOKEN` is set
- Check workflow logs for error messages
- Release can be created manually: `gh release create v0.0.6 --title "Release 0.0.6" --notes-file documents/releases/RELEASE_NOTES_0.0.6.md`

### GPG Signing Fails

**Issue:** Artifacts not signed

**Solutions:**
- Verify `GPG_PRIVATE_KEY`, `GPG_PASSPHRASE`, and `GPG_KEY_ID` secrets are set
- Check that GPG key is in correct format (armored)
- Verify passphrase is correct
- Note: Signing is optional - artifacts can be published without signing

---

## Manual Release (Fallback)

If the automated workflow fails, you can release manually:

### 1. Create Tag
```bash
git tag -a v0.0.6 -m "Release 0.0.6"
git push origin v0.0.6
```

### 2. Publish to Maven Central
```bash
./gradlew clean build publishToMavenLocal
./scripts/publish-to-central.sh 0.0.6
```

### 3. Create GitHub Release
```bash
gh release create v0.0.6 \
  --title "Release 0.0.6" \
  --notes-file documents/releases/RELEASE_NOTES_0.0.6.md
```

---

## Testing the Workflow

To test the workflow without actually releasing:

1. **Create a test branch:**
   ```bash
   git checkout -b test-release-workflow
   ```

2. **Push to trigger build job:**
   ```bash
   git push origin test-release-workflow
   ```

3. **Note:** Release job only runs on `main`, so it won't actually release

4. **For full testing:** Create a test PR and merge to `main` (be careful!)

---

## Security Considerations

### Secrets Management
- ✅ Secrets are encrypted at rest
- ✅ Secrets are not exposed in logs
- ✅ Secrets are only available to workflows
- ✅ Use repository secrets (not organization secrets) for better isolation

### Token Permissions
- `GITHUB_TOKEN` has limited permissions (scoped to repository)
- `MAVEN_CENTRAL_TOKEN` should have minimal required permissions
- GPG keys should be dedicated signing keys (not personal keys)

---

## Summary

The automated release workflow:
- ✅ Runs automatically on PR merge to `main`
- ✅ Creates git tag
- ✅ Publishes to Maven Central
- ✅ Creates GitHub release
- ✅ Requires only `MAVEN_CENTRAL_TOKEN` secret (GPG optional)

**Setup Time:** ~5 minutes (just add secrets)  
**Release Time:** ~5-10 minutes (fully automated)

---

**Last Updated:** 2025-12-05

