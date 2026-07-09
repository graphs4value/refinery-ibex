/*
 * SPDX-FileCopyrightText: 2026 The Refinery Authors <https://refinery.tools/>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
	alias(libs.plugins.versions)
}

val mavenRepositoryDir = layout.buildDirectory.map { it.dir("repo") }

val cleanMavenRepository by tasks.registering(Delete::class) {
	delete(mavenRepositoryDir)
}

val mavenRepository by tasks.registering(Task::class) {
	dependsOn(cleanMavenRepository)
}

gradle.projectsEvaluated {
	mavenRepository.configure {
		for (subproject in rootProject.subprojects) {
			if (subproject.plugins.hasPlugin(MavenPublishPlugin::class)) {
				val publishTask = subproject.tasks.named("publishMavenJavaPublicationToFileRepository")
				publishTask.configure {
					mustRunAfter(cleanMavenRepository)
				}
				dependsOn(publishTask)
			}
		}
	}
}
