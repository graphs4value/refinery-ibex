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

// Each project publishes into its own build directory, which the root project aggregates.
val mavenRepositoryDir = layout.buildDirectory.dir("repo")

tasks {
	jar {
		manifest {
			attributes(
				"Bundle-SymbolicName" to "${project.group}.${project.name}",
				"Bundle-Version" to project.version,
				"Bundle-License" to "Apache-2.0 AND LGPL-3.0-or-later",
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

publishing {
	publications {
		register<MavenPublication>("mavenJava") {
			from(components["java"])
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
								"packaging. Both licenses apply; this is not a choice of license."
					}
					license {
						name = "GNU LESSER GENERAL PUBLIC LICENSE, Version 3"
						url = "https://raw.githubusercontent.com/ibex-team/ibex-lib/refs/heads/master/COPYING.LESSER"
						comments = "Applies to the bundled IBEX code and native libraries. Both licenses apply; " +
								"this is not a choice of license."
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
