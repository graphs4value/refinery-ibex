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

open class MavenArtifactExtension {
	var nameSuffix: String? = null
}

val artifactExtension = project.extensions.create<MavenArtifactExtension>("mavenArtifact")

tasks {
	jar {
		manifest {
			attributes(
				"Bundle-SymbolicName" to "${project.group}.${project.name}",
				"Bundle-Version" to project.version
			)
		}
	}

	named<Jar>("sourcesJar") {
		// No need to include binary resources in the sources jars.
		exclude("**/*.dll", "**/*.dylib", "**/*.so")
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
				val nameString = provider {
					val prefix = "IBEX Java Bindings"
					val nameSuffix = artifactExtension.nameSuffix
					if (nameSuffix == null) prefix else "$prefix ($nameSuffix)"
				}
				name = nameString.map { "Refinery $it" }
				description = nameString.map {
					"$it for Refinery, an efficient graph solver for generating well-formed models"
				}
				url = "https://refinery.tools/"
				licenses {
					license {
						name = "GNU LESSER GENERAL PUBLIC LICENSE, Version 3"
						url = "https://raw.githubusercontent.com/ibex-team/ibex-lib/refs/heads/master/COPYING.LESSER"
					}
					license {
						name = "The Apache License, Version 2.0"
						url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
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
					connection = "scm:git:https://github.com/graphs4value/refinery-z3.git"
					developerConnection = "scm:git:ssh://github.com:graphs4value/refinery-z3.git"
					url = "https://github.com/graphs4value/refinery-z3"
				}
				issueManagement {
					url = "https://github.com/graphs4value/refinery-z3/issues"
				}
			}
		}
	}

	repositories {
		maven {
			name = "file"
			setUrl(rootProject.layout.buildDirectory.map { uri(it.dir("repo")) })
		}
	}
}

signing {
	setRequired {
		!version.toString().endsWith("SNAPSHOT") && gradle.taskGraph.hasTask("publish")
	}
	val signingKeyId = System.getenv("PGP_KEY_ID")
	val signingKey = System.getenv("PGP_KEY")
	val signingPassword = System.getenv("PGP_PASSWORD")
	useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
	sign(publishing.publications["mavenJava"])
}
