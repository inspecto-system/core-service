plugins {
    java
    jacoco
    checkstyle
    pmd
    id("org.springframework.boot") version "4.1.2"
    id("com.diffplug.spotless") version "8.11.0"
    id("com.github.spotbugs") version "6.5.13"
}

group = "dev.inspecto"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    // Argon2 password hashing in Spring Security needs Bouncy Castle.
    implementation("org.bouncycastle:bcprov-jdk18on:1.81")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // @SuppressFBWarnings for narrow, justified SpotBugs suppressions (CODE_QUALITY.md §1).
    compileOnly("com.github.spotbugs:spotbugs-annotations:4.10.5")
    testCompileOnly("com.github.spotbugs:spotbugs-annotations:4.10.5")

    spotbugsPlugins("com.h3xstream.findsecbugs:findsecbugs-plugin:1.14.0")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all,-processing,-serial", "-Werror", "-parameters"))
}

// ---------- Formatting (CODE_QUALITY.md §2) ----------
spotless {
    java {
        googleJavaFormat()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
}

// ---------- Static analysis ----------
checkstyle {
    toolVersion = "13.4.2"
    configFile = file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = false
    maxWarnings = 0
}

pmd {
    toolVersion = "7.25.0"
    ruleSetFiles = files("config/pmd/ruleset.xml")
    ruleSets = listOf()
    isConsoleOutput = true
    isIgnoreFailures = false
}

tasks.named<Pmd>("pmdTest") {
    ruleSetFiles = files("config/pmd/ruleset-test.xml")
}

spotbugs {
    ignoreFailures = false
    excludeFilter = file("config/spotbugs/exclude.xml")
    effort = com.github.spotbugs.snom.Effort.MAX
    reportLevel = com.github.spotbugs.snom.Confidence.LOW
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    reports.create("html") { required = true }
    reports.create("text") { required = true }
}

// ---------- Tests and coverage (CODE_QUALITY.md §4–5) ----------
tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

// Application bootstrap code is not business logic and is excluded from coverage.
val coverageExclusions = listOf("**/CoreServiceApplication.class")

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(
        sourceSets.main
            .get()
            .output.classesDirs
            .map { fileTree(it) { exclude(coverageExclusions) } },
    )
    reports {
        xml.required = true
        html.required = true
    }
}

val criticalPackages =
    listOf(
        "dev.inspecto.core.security*",
        // Resource access checks (T-102) live with the property aggregate.
        "dev.inspecto.core.property*",
        "dev.inspecto.core.comparison*",
        "dev.inspecto.core.*.domain*",
    )

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    classDirectories.setFrom(
        sourceSets.main
            .get()
            .output.classesDirs
            .map { fileTree(it) { exclude(coverageExclusions) } },
    )
    violationRules {
        rule {
            element = "BUNDLE"
            limit {
                counter = "LINE"
                minimum = "0.60".toBigDecimal()
            }
        }
        rule {
            element = "PACKAGE"
            includes = criticalPackages
            limit {
                counter = "LINE"
                minimum = "0.80".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
