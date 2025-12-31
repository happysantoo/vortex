#!/usr/bin/env bash
# CI/CD Pipeline Validation Script
# Validates that all required components are in place for automated releases

set -euo pipefail

echo "=========================================="
echo "Vortex CI/CD Pipeline Validation"
echo "=========================================="
echo ""

# Function to check if file exists
check_file() {
    local file="$1"
    local description="$2"

    if [[ -f "$file" ]]; then
        echo "✓ $description: $file"
        return 0
    else
        echo "✗ $description: $file (MISSING)"
        return 1
    fi
}

# Function to check if directory exists
check_directory() {
    local dir="$1"
    local description="$2"

    if [[ -d "$dir" ]]; then
        echo "✓ $description: $dir"
        return 0
    else
        echo "✗ $description: $dir (MISSING)"
        return 1
    fi
}

# Function to check GitHub secret configuration
check_github_secrets() {
    echo "📋 Required GitHub Secrets:"
    echo "   - GPG_PRIVATE_KEY: GPG private key for signing artifacts"
    echo "   - GPG_PASSPHRASE: GPG key passphrase"
    echo "   - GPG_KEY_ID: GPG key ID (optional, can be auto-detected)"
    echo "   - MAVEN_CENTRAL_TOKEN: Maven Central publishing token"
    echo "   - GITHUB_TOKEN: Automatically provided by GitHub Actions"
    echo ""
    echo "📝 To configure secrets:"
    echo "   1. Go to your repository settings"
    echo "   2. Navigate to Secrets and Variables > Actions"
    echo "   3. Add the required secrets listed above"
    echo ""
}

echo "🔍 Checking CI/CD Pipeline Components..."
echo ""

# Track validation results
failures=0

# Check workflows
echo "📄 GitHub Actions Workflows:"
check_file ".github/workflows/build.yml" "Main build and release workflow" || ((failures++))
check_file ".github/workflows/pages.yml" "GitHub Pages deployment workflow" || ((failures++))
echo ""

# Check build configuration
echo "🔧 Build Configuration:"
check_file "build.gradle.kts" "Gradle build configuration" || ((failures++))
check_file "gradle.properties" "Gradle properties" || ((failures++))
echo ""

# Check publishing scripts
echo "📦 Publishing Scripts:"
check_file "scripts/publish-to-central.sh" "Maven Central publishing script" || ((failures++))
echo ""

# Check benchmark configuration
echo "📊 Benchmark Configuration:"
check_directory "src/jmh/java" "JMH benchmark source directory" || ((failures++))
echo ""

# Validate Gradle configuration
echo "🔍 Validating Gradle Configuration..."

if grep -q 'maven-publish' build.gradle.kts; then
    echo "✓ Maven publishing plugin configured"
else
    echo "✗ Maven publishing plugin not found in build.gradle.kts"
    ((failures++))
fi

if grep -q 'signing' build.gradle.kts; then
    echo "✓ Artifact signing plugin configured"
else
    echo "✗ Artifact signing plugin not found in build.gradle.kts"
    ((failures++))
fi

if grep -q 'me.champeau.jmh' build.gradle.kts; then
    echo "✓ JMH benchmarking plugin configured"
else
    echo "✗ JMH benchmarking plugin not found in build.gradle.kts"
    ((failures++))
fi

echo ""

# Validate workflow configuration
echo "🔍 Validating Workflow Configuration..."

if grep -q "needs: \[build, benchmarks\]" .github/workflows/build.yml; then
    echo "✓ Release job properly depends on build and benchmarks"
else
    echo "✗ Release job dependency chain not properly configured"
    ((failures++))
fi

if grep -q "gh release upload" .github/workflows/build.yml; then
    echo "✓ JAR artifacts upload to GitHub releases configured"
else
    echo "✗ JAR artifacts upload not configured"
    ((failures++))
fi

if grep -q "benchmark-results" .github/workflows/build.yml; then
    echo "✓ Benchmark results attachment to releases configured"
else
    echo "✗ Benchmark results attachment not configured"
    ((failures++))
fi

echo ""

# Check version extraction
echo "🔍 Checking Version Configuration..."
VERSION=$(grep 'version =' build.gradle.kts | sed 's/.*version = "\(.*\)".*/\1/' || echo "")
if [[ -n "$VERSION" ]]; then
    echo "✓ Current version: $VERSION"
else
    echo "✗ Could not extract version from build.gradle.kts"
    ((failures++))
fi

echo ""

# Check GitHub repository configuration
echo "📋 GitHub Repository Configuration Requirements:"
echo "   1. Enable GitHub Actions in repository settings"
echo "   2. Enable GitHub Pages with Source: GitHub Actions"
echo "   3. Configure required secrets (see list below)"
echo ""

check_github_secrets

# Final summary
echo "=========================================="
echo "Validation Summary"
echo "=========================================="

if [[ $failures -eq 0 ]]; then
    echo "🎉 CI/CD Pipeline Validation: PASSED"
    echo ""
    echo "✅ All required components are properly configured!"
    echo ""
    echo "📋 Next Steps:"
    echo "   1. Configure the required GitHub secrets"
    echo "   2. Enable GitHub Pages in repository settings"
    echo "   3. Merge your next PR to main to trigger the release pipeline"
    echo ""
    echo "🚀 Your CI/CD pipeline will now:"
    echo "   • Build and test code on every PR and main branch push"
    echo "   • Run benchmarks and generate reports"
    echo "   • Publish to Maven Central on main branch pushes"
    echo "   • Create GitHub releases with JAR artifacts and benchmarks"
    echo "   • Deploy benchmark reports to GitHub Pages"
    echo ""
else
    echo "❌ CI/CD Pipeline Validation: FAILED"
    echo ""
    echo "Found $failures issues that need to be resolved."
    echo ""
    exit 1
fi