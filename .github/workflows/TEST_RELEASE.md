# Testing the Release Workflow

## Option 1: Manual Trigger (Recommended)

1. Go to https://github.com/happysantoo/vortex/actions/workflows/build.yml
2. Click "Run workflow" button
3. Select branch: `main`
4. Click "Run workflow"
5. This will trigger the release job without needing to push code

## Option 2: Test Branch (Safe Testing)

Create a test branch to verify the workflow without affecting main:

```bash
# Create a test branch
git checkout -b test-release-workflow

# Make a small change (e.g., update a comment)
# Then temporarily modify .github/workflows/build.yml:
# Change: if: (github.event_name == 'push' && github.ref == 'refs/heads/main')
# To:     if: (github.event_name == 'push' && github.ref == 'refs/heads/test-release-workflow')

# Push and test
git push origin test-release-workflow

# After testing, delete the branch and revert the workflow change
```

## Option 3: Dry Run (Check Syntax Only)

The workflow will fail early if there are syntax errors. You can also check the workflow file syntax:

```bash
# Install act (local GitHub Actions runner) for local testing
# brew install act  # macOS
# Then run: act -l  # List workflows
```

## What to Check

When testing, verify:
1. ✅ GPG key validation works (no grep errors)
2. ✅ GPG key import succeeds
3. ✅ GPG key trust configuration works
4. ✅ Publish script returns to project root
5. ✅ GitHub release creation works (if tag doesn't exist)

## Important Notes

- The release job will try to publish to Maven Central if all checks pass
- Make sure you have the required secrets configured:
  - `MAVEN_CENTRAL_TOKEN`
  - `GPG_PRIVATE_KEY` (optional)
  - `GPG_PASSPHRASE` (optional)
  - `GPG_KEY_ID` (optional)
- If you don't want to actually publish, you can stop the workflow after the GPG setup step
