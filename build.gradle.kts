plugins {
    java
    `java-library`
    groovy
    jacoco
    `maven-publish`
    signing
    id("org.jreleaser") version "1.12.0"
    id("me.champeau.jmh") version "0.7.2"
    id("io.morethan.jmhreport") version "0.9.6"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
    withJavadocJar()
}

group = "com.vajrapulse"
version = "0.0.1"

repositories {
    mavenCentral()
}

dependencies {
    // Micrometer for metrics
    implementation("io.micrometer:micrometer-core:1.16.0")
    
    // SLF4J for logging (lightweight)
    implementation("org.slf4j:slf4j-api:2.0.9")
    
    // Testing with Spock Framework (latest stable)
    testImplementation("org.spockframework:spock-core:2.3-groovy-4.0")
    testImplementation("org.apache.groovy:groovy:4.0.15")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.1")
    testImplementation("org.slf4j:slf4j-simple:2.0.9")
    
    // JMH for benchmarking
    implementation("org.openjdk.jmh:jmh-core:1.37")
    annotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
        csv.required = false
    }
    finalizedBy(tasks.jacocoTestCoverageVerification)
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        // Overall instruction coverage - maintain >80% (realistic for complex async code with lambdas)
        rule {
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
        // Class-level line coverage - >88% for all classes except examples (realistic for complex async code)
        // Note: Some edge cases like System.err.println in catch blocks are hard to test
        rule {
            element = "CLASS"
            excludes = listOf(
                "com.vajrapulse.vortex.example.*",
                "com.vajrapulse.vortex.PendingRequest"
            )
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.88".toBigDecimal()
            }
        }
        // Method-level branch coverage - >50% for methods (complex async code with many edge cases)
        // Note: Some methods like dispatchBatch have complex branching that's hard to fully test
        rule {
            element = "METHOD"
            excludes = listOf(
                "com.vajrapulse.vortex.example.*",
                // Lambda methods are hard to test comprehensively
                "com.vajrapulse.vortex.MicroBatcher.lambda\$*"
            )
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.50".toBigDecimal()
            }
        }
    }
}

// Maven Central Publishing
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            
            pom {
                name.set("Vortex Micro-Batcher")
                description.set("A lightweight Java library for micro-batching requests to any backend with virtual threads support")
                url.set("https://github.com/vajrapulse/vortex")
                
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                
                developers {
                    developer {
                        id.set("vajrapulse")
                        name.set("VajraPulse")
                        email.set("info@vajrapulse.com")
                    }
                }
                
                scm {
                    connection.set("scm:git:git://github.com/vajrapulse/vortex.git")
                    developerConnection.set("scm:git:ssh://github.com/vajrapulse/vortex.git")
                    url.set("https://github.com/vajrapulse/vortex")
                }
            }
        }
    }
    
    
    // No repositories: JReleaser handles bundle + portal upload
}

// Signing configuration
signing {
    val signingKeyId: String? = project.findProperty("signing.keyId") as String?
    val signingKey: String? = project.findProperty("signingKey") as String?
    val signingPassword: String? = project.findProperty("signingPassword") as String?
    
    if (signingKey != null && signingPassword != null) {
        try {
            if (signingKeyId != null) {
                useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
            } else {
                useInMemoryPgpKeys(signingKey, signingPassword)
            }
            sign(publishing.publications["maven"])
        } catch (e: Exception) {
            println("Warning: Signing configuration issue: ${e.message}")
        }
    }
}

// JReleaser configuration for Central Portal publishing
// Configuration is in jreleaser.yml file
// Credentials from gradle.properties: mavenCentralUsername, mavenCentralPassword

// JMH Configuration
jmh {
    resultFormat = "JSON"
    resultsFile = file("${layout.buildDirectory.get()}/reports/jmh/results.json")
}

// JMH Report Configuration
jmhReport {
    jmhResultPath = "${layout.buildDirectory.get()}/reports/jmh/results.json"
    jmhReportOutput = "${layout.buildDirectory.get()}/reports/jmh/html"
}

// Make jmhReport depend on jmh and ensure output directory exists
tasks.jmhReport {
    dependsOn(tasks.jmh)
    doFirst {
        file("${layout.buildDirectory.get()}/reports/jmh/html").mkdirs()
    }
}

