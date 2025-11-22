# JReleaser Issue Analysis

## GitHub Issue #1992

**Issue**: [JReleaser does not work with the Gradle configuration cache](https://github.com/jreleaser/jreleaser/issues/1992)

**Problem**: `JReleaserDeployTask` calls `Task.project` at execution time, which breaks Gradle configuration cache support.

## Our Issue

**Problem**: JReleaser Gradle plugin not reading `description` and `copyright` from `jreleaser.yml` file.

**Symptoms**:
- YAML file correctly formatted with description and copyright
- JReleaser reports: "project.description must not be blank"
- JReleaser reports: "project.copyright must not be blank"
- Even with simple test values, fields not being read

## Are They Related?

**Partially, but different root causes:**

1. **Issue #1992**: Configuration cache compatibility problem
   - Tasks accessing `Task.project` at execution time
   - Breaks when configuration cache is enabled
   - Would cause task execution failures

2. **Our Issue**: YAML parsing/reading problem
   - Fields exist in YAML but not being read
   - Happens even without configuration cache
   - Validation fails before task execution

## Possible Connection

If configuration cache is enabled (even implicitly), it might cause:
- YAML file not being read properly during configuration phase
- Task dependencies not being resolved correctly
- Configuration not being loaded from YAML

However, we tested without configuration cache and the issue persisted, suggesting it's a separate bug.

## Solution

We've **bypassed JReleaser entirely** and use the **direct Central Portal API approach** (like vajrapulse project):

1. ✅ Publish to Maven Local: `./gradlew publishToMavenLocal`
2. ✅ Create bundle: `./scripts/publish-to-central.sh 0.0.1`
3. ✅ Upload via API with Bearer token authentication

This approach:
- ✅ Works reliably
- ✅ No JReleaser plugin needed
- ✅ No configuration cache issues
- ✅ No YAML parsing issues
- ✅ Successfully published: Deployment ID `eed6d204-1832-47eb-aac6-10aba1df878a`

## Conclusion

While issue #1992 might contribute to JReleaser problems, our specific issue (YAML fields not being read) appears to be a separate bug in the JReleaser Gradle plugin. Since we've successfully published using the direct API approach, we don't need to resolve the JReleaser issues.

