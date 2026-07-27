/*
 * SPDX-FileCopyrightText: 2026 The Refinery Authors <https://refinery.tools/>
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package tools.refinery.ibex.gradle

plugins {
    id("tools.refinery.ibex.gradle.java-library")
}

val libraryName = project.name.replace("refinery-ibex-solver", "ibex-java")
val libraryResourcesDir = layout.projectDirectory.dir("src/main/resources/$libraryName")

// Expose the extracted native libraries to :refinery-ibex-solver, which puts their directory on the dynamic linker
// search path to test loading them without extracting them from the jars on the classpath.
configurations.create("nativeLibraries") {
    isCanBeConsumed = true
    isCanBeResolved = false
    outgoing.artifact(libraryResourcesDir)
}
