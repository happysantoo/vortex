# Release 0.0.6 Automation Summary

**Date:** 2025-12-05  
**PR:** #6  
**Status:** ✅ Ready for Review

---

## Summary

The GitHub Actions workflow has been updated to automatically handle the complete release process when code is merged to the `main` branch.

---

## What the Workflow Does

### On PR Merge to Main

1. **Build Job** (runs on all pushes)
   - Compiles code
   - Runs all tests
   - Generates coverage reports
   - Verifies coverage requirements

2. **Release Job** (runs only on push to main, after successful build)
   - ✅ Extracts version from `build.gradle.kts`
   - ✅ Builds and tests the code
   - ✅ Publishes to Maven Local
   - ✅ Creates git tag (`v<version>`)
   - ✅ Publishes to Maven Central via Central Portal API
   - ✅ Creates GitHub release with release notes

---

## Required GitHub Secrets

### Required (Must Have)

1. **`MAVEN_CENTRAL_TOKEN`**
   - Central Portal user token
   - Get from: https://central.sonatype.com/usertoken
   - Format: Base64 encoded token

### Optional (For GPG Signing)

2. **`GPG_PRIVATE_KEY`** (optional)
   - GPG private key in armored format
   - Only needed if you want to sign artifacts

3. **`GPG_PASSPHRASE`** (optional)
   - GPG key passphrase
   - Only needed if `GPG_PRIVATE_KEY` is set

4. **`GPG_KEY_ID`** (optional)
   - GPG key ID (last 8 characters)
   - Only needed if `GPG_PRIVATE_KEY` is set

### Automatic (No Setup Needed)

- **`GITHUB_TOKEN`** - Automatically provided by GitHub Actions

---

## How to Configure Secrets

1. Go to your repository on GitHub
2. Navigate to **Settings** → **Secrets and variables** → **Actions**
3. Click **New repository secret**
4. Add `MAVEN_CENTRAL_TOKEN` with your Central Portal token
5. (Optional) Add GPG secrets if you want signing

---

## Workflow File

**Location:** `.github/workflows/build.yml`

**Key Features:**
- Runs on push to `main` branch (after PR merge)
- Automatically extracts version from `build.gradle.kts`
- Creates git tag before publishing
- Publishes to Maven Central
- Creates GitHub release automatically

---

## Release Notes

The workflow automatically uses release notes from:
- `documents/releases/RELEASE_NOTES_<version>.md`

**For 0.0.6:** `documents/releases/RELEASE_NOTES_0.0.6.md`

If the file doesn't exist, a default release is created.

---

## Testing

The workflow has been tested:
- ✅ YAML syntax is valid
- ✅ All steps are properly configured
- ✅ Linux compatibility fixes applied (md5sum vs md5)
- ✅ Git tag push authentication configured
- ✅ GitHub CLI authentication configured

---

## Next Steps

1. **Add `MAVEN_CENTRAL_TOKEN` secret** to GitHub repository
2. **Review PR #6** and merge when ready
3. **After merge**, the workflow will automatically:
   - Create tag `v0.0.6`
   - Publish to Maven Central
   - Create GitHub release

---

## Documentation

See `documents/integrations/GITHUB_ACTIONS_RELEASE_SETUP.md` for detailed setup instructions and troubleshooting.

---

**Last Updated:** 2025-12-05

