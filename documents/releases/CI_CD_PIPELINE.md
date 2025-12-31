# CI/CD Pipeline Documentation

This document describes the comprehensive CI/CD pipeline implemented for the Vortex project.

## 🎯 Pipeline Overview

The CI/CD pipeline automatically handles:
- **Build & Test**: Runs on every PR and main branch push
- **Benchmarking**: JMH benchmarks with HTML reports
- **Maven Central Publishing**: Automatic artifact publishing
- **GitHub Releases**: Automated releases with JAR files and benchmark results
- **GitHub Pages**: Benchmark report hosting

## 🔄 Pipeline Flow

### On Pull Request
```
PR Created/Updated → Build → Test → Coverage Report
```

### On Main Branch Push (PR Merge)
```
Main Push → Build → Test → Benchmarks → Release → Maven Central → GitHub Release → Pages Deploy
```

## 📁 Workflow Files

### `.github/workflows/build.yml`
Main CI/CD workflow with three jobs:

1. **`build`**: Compiles, tests, and generates coverage reports
2. **`benchmarks`**: Runs JMH benchmarks and generates reports
3. **`release`**: Publishes to Maven Central and creates GitHub releases

### `.github/workflows/pages.yml`
Dedicated workflow for publishing benchmark reports to GitHub Pages.

## 🔧 Required Secrets

Configure these secrets in your GitHub repository settings:

| Secret | Description | Required |
|--------|-------------|----------|
| `GPG_PRIVATE_KEY` | GPG private key for signing artifacts | ✅ |
| `GPG_PASSPHRASE` | GPG key passphrase | ✅ |
| `GPG_KEY_ID` | GPG key ID (optional, auto-detected if not provided) | ❌ |
| `MAVEN_CENTRAL_TOKEN` | Maven Central publishing token | ✅ |
| `GITHUB_TOKEN` | Automatically provided by GitHub Actions | ✅ |

### Setting up GPG Keys

1. Generate a GPG key (if you don't have one):
   ```bash
   gpg --gen-key
   ```

2. Export your private key:
   ```bash
   gpg --armor --export-secret-keys YOUR_KEY_ID
   ```

3. Add the exported key to `GPG_PRIVATE_KEY` secret

### Setting up Maven Central Token

1. Create an account on [Maven Central](https://central.sonatype.com/)
2. Generate a user token in your account settings
3. Add the token to `MAVEN_CENTRAL_TOKEN` secret

## 🏗️ Build Configuration

### Gradle Build (`build.gradle.kts`)
- **Publishing**: Configured for Maven Central
- **Signing**: GPG signing for artifacts
- **JMH**: Benchmark execution and reporting
- **Testing**: JUnit with Jacoco coverage

### Version Management
- Version is defined in `build.gradle.kts`
- Automatically extracted and used for tagging and releases
- Git tags follow format: `v{version}` (e.g., `v0.0.12`)

## 📊 Benchmarking

### JMH Configuration
- Source: `src/jmh/java/`
- Output: JSON results in `build/reports/jmh/results.json`
- HTML reports: `build/reports/jmh/html/`

### Benchmark Publishing
- Results attached to GitHub releases as ZIP files
- HTML reports deployed to GitHub Pages
- Accessible at: `https://{owner}.github.io/{repo}/`

## 📦 Artifact Publishing

### Maven Central
- Automatic publishing on main branch pushes
- Includes main JAR, sources, and JavaDoc
- All artifacts are GPG signed
- Published to: `com.vajrapulse:vortex:{version}`

### GitHub Releases
- Created automatically for each version
- Includes:
  - Main JAR file
  - Sources JAR
  - JavaDoc JAR
  - Benchmark results ZIP
- Release notes from: `documents/releases/RELEASE_NOTES_{version}.md`

## 🚀 Deployment Process

### Triggering a Release

1. **Update Version**: Modify version in `build.gradle.kts`
2. **Add Release Notes**: Create `documents/releases/RELEASE_NOTES_{version}.md`
3. **Create PR**: Submit changes via pull request
4. **Merge to Main**: After approval, merge PR to main branch
5. **Automatic Release**: Pipeline handles the rest automatically

### Release Pipeline Steps

1. **Build & Test**: Ensure code quality
2. **Run Benchmarks**: Generate performance reports
3. **Create Git Tag**: Tag the release commit
4. **Publish to Maven Central**: Upload signed artifacts
5. **Create GitHub Release**: Include JAR files and benchmarks
6. **Deploy to Pages**: Update benchmark reports website

## 🔍 Monitoring & Validation

### Validation Script
Run the validation script to check pipeline configuration:

```bash
./scripts/validate-cicd.sh
```

### GitHub Actions Status
Monitor pipeline status in the "Actions" tab of your GitHub repository.

### Troubleshooting

**Common Issues:**

1. **GPG Signing Failures**
   - Verify GPG_PRIVATE_KEY format includes proper headers
   - Check GPG_PASSPHRASE is correct
   - Ensure key hasn't expired

2. **Maven Central Publishing Failures**
   - Verify MAVEN_CENTRAL_TOKEN is valid
   - Check artifact signing is working
   - Ensure all required metadata is present in POM

3. **Benchmark Failures**
   - Check JMH source compilation
   - Verify benchmark methods are properly annotated
   - Monitor resource usage during benchmark runs

## 📋 Repository Setup Checklist

- [ ] Enable GitHub Actions in repository settings
- [ ] Configure required secrets (GPG, Maven Central token)
- [ ] Enable GitHub Pages with source: "GitHub Actions"
- [ ] Verify branch protection rules for main branch
- [ ] Test pipeline with a sample PR

## 🔗 Useful Links

- [Maven Central](https://central.sonatype.com/)
- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [JMH Documentation](https://github.com/openjdk/jmh)
- [Gradle Publishing Plugin](https://docs.gradle.org/current/userguide/publishing_maven.html)