# Vortex Micro-Batching Library - GitHub Copilot Instructions

## Project Overview

Vortex is a lightweight Java 21 micro-batching library for grouping requests and dispatching them to any backend. The library emphasizes high quality, comprehensive testing, and production-ready code.

**Key Technologies:**
- Java 21 with virtual threads
- Spock Framework (Groovy) for testing
- Micrometer for metrics
- Gradle for build management

---

## TESTING REQUIREMENTS (MANDATORY)

### Test Coverage Standards

**Minimum Coverage Thresholds:**
- **Line Coverage**: 90% for all classes (excluding examples and PendingRequest)
- **Instruction Coverage**: 80% overall
- **Branch Coverage**: 50% for methods
- **Public API Coverage**: 100% - Every public method MUST have tests

**Coverage Verification:**
- Run `./gradlew jacocoTestCoverageVerification` before every commit
- Coverage violations block commits
- Review coverage reports at `build/reports/jacoco/test/html/index.html`

### Testing Framework: Spock (Groovy)

**All tests MUST be written in Spock Framework using Groovy.**

**Test File Structure:**
- File naming: `*Spec.groovy` (e.g., `MicroBatcherSpec.groovy`)
- One spec file per class being tested
- Package structure mirrors source: `src/test/groovy/` mirrors `src/main/java/`

**Test Structure (BDD Style):**
```groovy
def "should [expected behavior] when [condition]"() {
    given: "setup description"
        def batcher = new MicroBatcher<>(backend, config)
    
    when: "action description"
        def result = batcher.submit("item")
    
    then: "verification description"
        result == ItemResult.Success("item")
    
    cleanup:
        batcher?.close()
}
```

**Test Naming Convention:**
- Format: `"should [expected behavior] when [condition]"`
- Examples:
  - `"should return success when item is submitted"`
  - `"should reject item when queue is full"`
  - `"should throw exception when batcher is closed"`

### What Must Be Tested

1. **All Public APIs**: Every public method, constructor, and class
2. **Edge Cases**: Null values, empty collections, invalid parameters, boundary conditions
3. **Error Conditions**: Exceptions, failures, timeouts, interruptions
4. **Concurrency**: Thread safety, race conditions, concurrent access patterns
5. **Integration Scenarios**: Real-world usage patterns, end-to-end flows
6. **Metrics**: Verify all metrics are recorded correctly with correct values
7. **Resource Cleanup**: Ensure proper cleanup in all scenarios (close, errors, interruptions)

### Testing Best Practices

**Synchronization & Timing:**
- Use `CountDownLatch` for coordinating async operations
- Avoid `Thread.sleep` - only use when absolutely necessary
- Always specify timeouts for async operations
- Test concurrent scenarios explicitly

**Example: Proper Async Test**
```groovy
def "should process batch with callback when items are submitted"() {
    given: "a batcher with callback"
        def latch = new CountDownLatch(1)
        def results = []
        def batcher = new MicroBatcher<>(backend, config)
    
    when: "submitting items with callback"
        batcher.submit("item1", { item, result ->
            results.add(result)
            latch.countDown()
        })
    
    then: "callback is invoked"
        latch.await(5, TimeUnit.SECONDS)
        results.size() == 1
        results[0] == ItemResult.Success("item1")
    
    cleanup:
        batcher?.close()
}
```

**Metrics Testing:**
```groovy
def "should record metrics when item is submitted"() {
    given: "a batcher with meter registry"
        def registry = new SimpleMeterRegistry()
        def batcher = new MicroBatcher<>(backend, config, registry)
    
    when: "submitting an item"
        batcher.submit("item")
        Thread.sleep(200) // Allow batch to process
    
    then: "metrics are recorded"
        registry.counter("vortex.requests.submitted").count() == 1
        registry.counter("vortex.requests.succeeded").count() == 1
    
    cleanup:
        batcher?.close()
}
```

**Resource Management:**
- Always use `cleanup:` blocks for resource cleanup
- Test cleanup on close, errors, and interruptions
- Verify no memory leaks in long-running scenarios

---

## CODE REVIEW EXPECTATIONS

### Pre-Commit Checklist

Before committing ANY code, verify:

1. **Build Status**
   - [ ] `./gradlew build` passes without errors
   - [ ] `./gradlew test` - All tests pass
   - [ ] No compilation warnings or errors

2. **Test Coverage**
   - [ ] `./gradlew jacocoTestReport` - Coverage report generated
   - [ ] `./gradlew jacocoTestCoverageVerification` - Coverage verification passes
   - [ ] Line coverage >90% for new/modified classes
   - [ ] Instruction coverage >80% overall
   - [ ] Branch coverage >50% for new/modified methods

3. **Code Quality**
   - [ ] All public APIs have JavaDoc comments
   - [ ] Code follows project style guidelines
   - [ ] No unused imports or variables
   - [ ] No TODO comments (unless with issue reference)
   - [ ] No hardcoded values (use constants)

4. **Testing**
   - [ ] Tests written in Spock (Groovy)
   - [ ] All new features have tests
   - [ ] Edge cases are tested
   - [ ] Error conditions are tested
   - [ ] Tests are not flaky (run multiple times)
   - [ ] Cleanup blocks are present in all tests

5. **Documentation**
   - [ ] README updated if API changes
   - [ ] JavaDoc added for public APIs
   - [ ] Examples updated if applicable
   - [ ] CHANGELOG.md updated for user-facing changes

### Code Review Focus Areas

**When reviewing code, check:**

1. **Test Quality**
   - Tests cover new functionality
   - Tests cover edge cases
   - Tests cover error conditions
   - Tests are not flaky
   - Test coverage meets requirements

2. **Code Quality**
   - Code is readable and maintainable
   - Code follows project conventions
   - No code duplication
   - Proper error handling
   - Meaningful variable and method names

3. **Functionality**
   - Code implements intended functionality correctly
   - Edge cases handled appropriately
   - Error conditions handled gracefully
   - No obvious bugs or logic errors

4. **Documentation**
   - Public APIs are documented
   - Complex logic is explained
   - Examples are updated if needed
   - README is updated if API changes

### Common Issues to Avoid

**Testing Issues:**
- ❌ Missing tests for new code
- ❌ Flaky tests (timing issues, race conditions)
- ❌ Insufficient coverage (below requirements)
- ❌ Missing cleanup in tests
- ❌ Hard-coded sleeps without proper synchronization

**Code Quality Issues:**
- ❌ Overly complex implementations
- ❌ Code duplication
- ❌ Unclear variable or method names
- ❌ Missing documentation for public APIs
- ❌ Dead code that should be removed

---

## CODE STYLE & CONVENTIONS

### Java Code Style
- **Simplicity first**: Keep code minimal and easy to understand
- **Java 21 features**: Use modern Java features (virtual threads, records, pattern matching)
- **No unnecessary dependencies**: Keep the library lightweight
- **Clear naming**: Use descriptive variable and method names
- **Documentation**: Public APIs must have JavaDoc comments

### Naming Conventions
- **Classes**: PascalCase (e.g., `MicroBatcher`)
- **Methods**: camelCase (e.g., `submitItem`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `MAX_QUEUE_SIZE`)
- **Variables**: camelCase (e.g., `batchSize`)
- **Packages**: lowercase (e.g., `com.vajrapulse.vortex`)

### Code Organization
- One class per file
- Logical package organization
- Organize imports (static imports last)
- Keep lines under 120 characters where possible

---

## ARCHITECTURE PRINCIPLES

### Core Principles
- **Generic design**: Support any backend type via `Backend<T>` interface
- **Non-blocking**: All operations should be asynchronous
- **Thread-safe**: Safe for concurrent use from multiple threads
- **Resource management**: Proper cleanup with AutoCloseable
- **Observability**: Comprehensive metrics via Micrometer

### Design Patterns
- **Builder pattern**: Use for configuration objects
- **Factory methods**: Use for common configurations
- **Strategy pattern**: Use for pluggable behaviors (tracing hooks)
- **Observer pattern**: Use for callbacks and metrics

---

## METRICS REQUIREMENTS

### Metric Standards
- **All metrics must be tested**: Verify metrics are recorded correctly
- **Metric names follow convention**: Use "vortex.*" prefix
- **Gauges for state**: Use gauges for queue depth and similar state metrics
- **Counters for events**: Use counters for discrete events
- **Timers for latencies**: Use timers for duration measurements

### Metric Naming
- **Format**: `vortex.<category>.<metric>`
- **Examples**:
  - `vortex.requests.submitted`
  - `vortex.requests.succeeded`
  - `vortex.requests.failed`
  - `vortex.queue.depth`
  - `vortex.batch.size`

---

## ERROR HANDLING REQUIREMENTS

### Error Handling Standards
- **Validate inputs**: Check for null and invalid parameters
- **Meaningful exceptions**: Provide clear error messages
- **Handle edge cases**: Empty batches, closed batcher, queue full scenarios
- **Graceful degradation**: Handle backend failures appropriately

### Exception Types
- **IllegalArgumentException**: For invalid parameters
- **IllegalStateException**: For invalid state (e.g., batcher closed)
- **ItemRejectedException**: For item rejection scenarios
- **RuntimeException**: For unexpected errors

---

## WHEN WRITING CODE

### Adding New Features
1. **Write tests first** (TDD approach)
2. **Test all scenarios**: Happy path, error path, edge cases
3. **Update documentation**: Update README, JavaDoc, examples
4. **Verify coverage**: Ensure coverage requirements are met
5. **Update CHANGELOG**: Document user-facing changes

### Fixing Bugs
1. **Reproduce with test**: Write a test that reproduces the bug
2. **Fix the bug**: Implement the fix
3. **Verify fix**: Ensure the test passes
4. **Check coverage**: Ensure coverage remains adequate
5. **Update CHANGELOG**: Document the bug fix

### Writing Tests
1. **Always use Spock Framework**: Write tests in Groovy using Spock
2. **Follow BDD structure**: Use given-when-then blocks
3. **Test all code paths**: Ensure all branches are tested
4. **Test edge cases**: Include null checks, empty collections, boundary conditions
5. **Test error conditions**: Test exceptions, failures, timeouts
6. **Use proper synchronization**: Use CountDownLatch for async operations
7. **Clean up resources**: Always use cleanup blocks
8. **Name tests descriptively**: Use "should [behavior] when [condition]" format

---

## WHEN REVIEWING CODE

### Test Quality Review
- Verify adequate test coverage for new code
- Ensure tests are clear and maintainable
- Check that tests are not flaky
- Verify tests don't depend on each other
- Ensure tests run quickly (< 1 second per test)

### Code Quality Review
- Code should be simple and easy to understand
- Variables and methods should have descriptive names
- Public APIs should be well-documented
- Proper error handling and meaningful error messages
- Verify thread safety for concurrent access

### Common Patterns to Use
- **CountDownLatch**: For coordinating async operations
- **Thread.sleep**: Only when absolutely necessary, with proper timeouts
- **cleanup blocks**: Always use for resource cleanup
- **@Unroll**: For parameterized tests
- **given-when-then**: For BDD-style test structure

### Common Pitfalls to Avoid
- ❌ **Flaky tests**: Don't use Thread.sleep without proper synchronization
- ❌ **Missing cleanup**: Always clean up resources
- ❌ **Insufficient coverage**: Ensure all code paths are tested
- ❌ **Test dependencies**: Don't make tests depend on each other
- ❌ **Hard-coded values**: Use constants or configuration
- ❌ **Missing edge cases**: Test null, empty, boundary conditions

---

## BUILD & CI REQUIREMENTS

### Build Requirements
- **Build must pass**: All tests must pass before committing
- **Coverage verification**: JaCoCo coverage verification must pass (>90%)
- **No compilation warnings**: Code should compile cleanly
- **Gradle 9.2.0**: Use specified Gradle version

### CI/CD Pipeline
- Automated testing: All tests run on every push
- Coverage verification: Coverage verified on every push
- Build verification: Build must pass before merge
- JMH benchmarks: Benchmarks run on merge to main

---

## FILE ORGANIZATION

### Markdown Documentation Files
**All markdown documentation files MUST be created in the `documents/` folder with proper classification.**

**Allowed Exceptions:**
- `README.md` in root directory (project overview)
- `README.md` in example directories (e.g., `examples/README.md`)
- `README.md` in script directories (e.g., `scripts/README-*.md`)
- `.github/*.md` files (GitHub-specific documentation)

**Document Classification:**
- `documents/releases/` - Release-specific documents
- `documents/roadmap/` - Strategic planning and roadmaps
- `documents/architecture/` - Design and architecture documents
- `documents/integrations/` - Integration guides and publishing
- `documents/guides/` - User guides and quick references
- `documents/analysis/` - Analysis and improvement documents
- `documents/resources/` - Non-markdown files
- `documents/archive/` - Completed and historical documents

---

## EXCLUDED FROM COVERAGE

The following are excluded from coverage requirements (see `build.gradle.kts` for full list):
- Example classes (`com.vajrapulse.vortex.example.*`)
- Internal helper classes (`PendingRequest`)
- Simple data classes (SuccessEvent, FailureEvent) - but still test their behavior
- Configuration classes (BatcherConfig, BatcherConfig.Builder)
- Functional interfaces (Backend)
- Enums (HealthStatus, BatchSizePreset)
- Complex methods with many branches (close(), submitInternal(), etc.)

**Note**: Even if excluded from coverage requirements, behavior should still be tested through integration tests.

---

## KEY EXPECTATIONS SUMMARY

### Testing Expectations
1. **All code must have tests**: No exceptions for new code
2. **Coverage must meet requirements**: >90% line, >80% instruction, >50% branch
3. **Tests must be in Spock**: All tests written in Groovy using Spock
4. **Tests must be comprehensive**: Cover happy path, error path, edge cases
5. **Tests must be maintainable**: Clear, well-organized, not flaky

### Code Review Expectations
1. **Review before commit**: Self-review before committing
2. **Check coverage**: Verify coverage requirements are met
3. **Check test quality**: Ensure tests are clear and maintainable
4. **Check code quality**: Ensure code follows standards
5. **Check documentation**: Ensure documentation is updated

### Project Expectations
1. **Quality over speed**: Don't sacrifice quality for speed
2. **Test-driven development**: Write tests first when possible
3. **Comprehensive testing**: Test all scenarios, not just happy path
4. **Maintainable code**: Write code that's easy to understand and maintain
5. **Documentation**: Keep documentation current and comprehensive

---

## REFERENCES

- **Build Configuration**: See `build.gradle.kts` for coverage rules and exclusions
- **Test Examples**: See `src/test/groovy/com/vajrapulse/vortex/` for test patterns
- **Project README**: See `README.md` for project overview and usage
- **CHANGELOG**: See `CHANGELOG.md` for version history

---

**Remember**: Quality is not negotiable. If tests don't pass or coverage doesn't meet requirements, the code is not ready to commit.

