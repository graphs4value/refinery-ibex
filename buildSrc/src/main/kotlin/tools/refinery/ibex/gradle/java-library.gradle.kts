/*
 * SPDX-FileCopyrightText: 2023-2026 The Refinery Authors <https://refinery.tools/>
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package tools.refinery.ibex.gradle

plugins {
    `java-library`
    `maven-publish`
    signing
}

java {
    withJavadocJar()
    withSourcesJar()

    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
}

val refinery = project.extensions.create<RefineryIbexExtension>("refinery")

// Our artifacts bundle code under several licenses at once, which the POM can only express by listing all of them.
val ALL_LICENSES_APPLY = "All licenses listed here apply; this is not a choice of license."

// Each project publishes into its own build directory, which the root project aggregates.
val mavenRepositoryDir = layout.buildDirectory.dir("repo")

// Licenses of everything in the jar this manifest belongs to: our own packaging, the IBEX code, and -- in the
// platform-specific projects only -- the interval arithmetic library the native libraries were built with. The main
// project distributes the interval arithmetic libraries as source code in its sources jar instead, which
// {@code Bundle-License} does not describe, but the POM below does.
val bundleLicense = refinery.intervalLib
    .map { "Apache-2.0 AND LGPL-3.0-or-later AND ${it.licenseSpdxId}" }
    .orElse("Apache-2.0 AND LGPL-3.0-or-later")

tasks {
    jar {
        // The architecture has to be spelled with an underscore, because {@code 64} on its own is not a Java
        // identifier.
        val moduleName = "${project.group}." + project.name
            .removePrefix("refinery-ibex-")
            .replace("x86-64", "x86_64")
            .replace('-', '.')
        manifest {
            attributes(
                "Automatic-Module-Name" to moduleName,
                // Documentation only, we don't set Bundle-ManifestVersion: 2, so these don't get interpreted by OSGi.
                "Bundle-SymbolicName" to "${project.group}.${project.name}",
                "Bundle-Version" to project.version,
                "Bundle-License" to bundleLicense,
            )
        }
    }

    named<Jar>("sourcesJar") {
        // No need to include binary resources in the sources jars. Shared libraries on Linux carry their version
        // after the extension, e.g., {@code libgaol.so.0}, while on macOS it comes before, e.g.,
        // {@code libgaol.0.dylib}, which is already matched by the {@code *.dylib} pattern.
        exclude("**/*.dll", "**/*.dylib", "**/*.so", "**/*.so.*")
    }

    javadoc {
        options {
            this as StandardJavadocDocletOptions
            addBooleanOption("Xdoclint:none", true)
            // {@code -Xmaxwarns 0} will print all warnings, so we must keep at least one.
            addStringOption("Xmaxwarns", "1")
            quiet()
        }
    }
}

// The interval arithmetic libraries the artifacts published by this project distribute: the single one the native
// libraries were built with, or — in the main project, which has none — all of them, because it embeds the IBEX
// sources they are vendored in.
val distributedIntervalLibs = refinery.intervalLib
    .map { listOf(it) }
    .orElse(IntervalLib.values().toList())

val cyclonedxBom = tasks.register<GenerateCycloneDxBomTask>("cyclonedxBom") {
    description = "Generate a CycloneDX SBOM recording the bundled upstream code"
    artifactGroup = project.group.toString()
    artifactName = project.name
    artifactVersion = project.version.toString()
    ibexVersion = refinery.ibexVersion
    ibexCommit = providers.gradleProperty("tools.refinery.ibex.commit")
    intervalLibs = distributedIntervalLibs
    bundledAsBinary = refinery.intervalLib.map { true }.orElse(false)
    outputFile = layout.buildDirectory.file("cyclonedx/bom.json")
}

// Attaching the SBOM to the publication doesn't make anything but publishing build it, but it is validated as it is
// generated, so we want an ordinary build to cover it, too.
tasks.named("assemble") {
    dependsOn(cyclonedxBom)
}

publishing {
    publications {
        register<MavenPublication>("mavenJava") {
            from(components["java"])
            artifact(cyclonedxBom) {
                classifier = "cyclonedx"
                extension = "json"
            }
            pom {
                val prefix = "Z3 Java Bindings"
                val nameString = refinery.nameSuffix.map { "$prefix ($it)" }.orElse(prefix)
                name = nameString.map { "Refinery $it" }
                description = nameString.map {
                    "$it for Refinery, an efficient graph solver for generating well-formed models"
                }
                url = "https://refinery.tools/"
                licenses {
                    license {
                        name = "The Apache License, Version 2.0"
                        url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
                        comments = "Applies to the Refinery-authored parts of this artifact, including its " +
                                "packaging. $ALL_LICENSES_APPLY"
                    }
                    license {
                        name = "GNU LESSER GENERAL PUBLIC LICENSE, Version 3"
                        url = "https://raw.githubusercontent.com/ibex-team/ibex-lib/refs/heads/master/COPYING.LESSER"
                        comments = refinery.intervalLib
                            .map { "Applies to the bundled IBEX code and native libraries. $ALL_LICENSES_APPLY" }
                            .orElse(
                                "Applies to the bundled IBEX Java code and to the IBEX sources embedded in the " +
                                        "sources jar. $ALL_LICENSES_APPLY"
                            )
                    }
                }
                developers {
                    developer {
                        name = "The Refinery Authors"
                        url = "https://refinery.tools/"
                    }
                    developer {
                        name = "IBEX Team"
                        url = "https://github.com/ibex-team"
                    }
                }
                scm {
                    connection = "scm:git:https://github.com/graphs4value/refinery-ibex.git"
                    developerConnection = "scm:git:ssh://github.com:graphs4value/refinery-ibex.git"
                    url = "https://github.com/graphs4value/refinery-ibex"
                }
                issueManagement {
                    url = "https://github.com/graphs4value/refinery-ibex/issues"
                }
            }
        }
    }

    repositories {
        maven {
            name = "file"
            setUrl(mavenRepositoryDir.map { uri(it) })
        }
    }
}

// Unlike {@code Bundle-License}, which only describes the jar its manifest belongs to, the POM covers every artifact
// published under the same coordinates. The main project therefore has to account for all interval arithmetic
// libraries, which it distributes as source code in its sources jar, while the platform-specific projects bundle the
// binaries of the single one they were built with.
//
// The signing configuration below realizes the publication before the projects applying this convention get to set
// {@code refinery.intervalLib}. Therefore, we can only add these licenses once the project has been evaluated.
afterEvaluate {
    val intervalLibs = distributedIntervalLibs.get()
    val bundledAsBinary = refinery.intervalLib.isPresent
    publishing.publications.named<MavenPublication>("mavenJava") {
        pom {
            licenses {
                for (lib in intervalLibs) {
                    license {
                        name = lib.licenseName
                        url = lib.licenseUrl
                        comments = if (bundledAsBinary) {
                            "Applies to ${lib.components}, which the bundled native libraries were built with. " +
                                    ALL_LICENSES_APPLY
                        } else {
                            "Applies to the sources of ${lib.components} vendored in the IBEX sources embedded " +
                                    "in the sources jar. $ALL_LICENSES_APPLY"
                        }
                    }
                }
            }
            developers {
                for (lib in intervalLibs) {
                    for (author in lib.authors) {
                        developer {
                            name = author.name
                            author.url?.let { url = it }
                        }
                    }
                }
            }
        }
    }
}

val cleanMavenRepository = tasks.register<Delete>("cleanMavenRepository") {
    delete(mavenRepositoryDir)
    description = "Clean Maven repository output files"
}

tasks.named("publishMavenJavaPublicationToFileRepository") {
    // Publishing only ever adds files, so drop stale ones (e.g., left over from an earlier version) first.
    dependsOn(cleanMavenRepository)
}

// Expose the published files, so that the root project can aggregate them into a single Maven repository without
// having to reach into this project.
configurations.create("mavenRepositoryElements") {
    isCanBeConsumed = true
    isCanBeResolved = false
    outgoing.artifact(mavenRepositoryDir) {
        builtBy(tasks.named("publishMavenJavaPublicationToFileRepository"))
    }
}

signing {
    setRequired {
        !version.toString().endsWith("SNAPSHOT") && project.hasProperty("forceSign")
    }
    val signingKeyId = System.getenv("PGP_KEY_ID")
    val signingKey = System.getenv("PGP_KEY")
    val signingPassword = System.getenv("PGP_PASSWORD")
    useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
    sign(publishing.publications["mavenJava"])
}
