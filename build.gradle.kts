/*
 * SPDX-FileCopyrightText: 2026 The Refinery Authors <https://refinery.tools/>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
	alias(libs.plugins.versions)
}

val mavenRepositoryContents = configurations.create("mavenRepositoryContents") {
	isCanBeConsumed = false
	isCanBeResolved = true
}

dependencies {
	for (subprojectPath in subprojects.map { it.path }) {
		mavenRepositoryContents(project(path = subprojectPath, configuration = "mavenRepositoryElements"))
	}
}

tasks.register<Sync>("mavenRepository") {
	from(mavenRepositoryContents)
	into(layout.buildDirectory.dir("repo"))
	description = "Build Maven repository output"
}
