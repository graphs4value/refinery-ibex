/*
 * SPDX-FileCopyrightText: 2026 The Refinery Authors <https://refinery.tools/>
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package tools.refinery.ibex.gradle

plugins {
    id("tools.refinery.ibex.gradle.java-library")
}

val solverProjectName = "refinery-ibex-solver"
val libraryName = project.name.replace(solverProjectName, "ibex-java")
val libraryResourcesDir = layout.projectDirectory.dir("src/main/resources/$libraryName")

// The LGPL requires us to distribute the source code of IBEX along with the native libraries built from it. The
// complete source is embedded in the sources jar of :refinery-ibex-solver, which every consumer of this module also
// depends on, so here we only have to point at it.
val refinery = extensions.getByType<RefineryIbexExtension>()
val ibexCommit = providers.gradleProperty("tools.refinery.ibex.commit").get()
val noticeFile = layout.buildDirectory.file("generated/upstream/IBEX-SOURCE.md")
val noticeText = """
    # IBEX source code

    This artifact bundles native libraries built from [IBEX](https://github.com/ibex-team/ibex-lib)
    ${refinery.ibexVersion}, which is licensed under the GNU Lesser General Public License, version 3 or later
    (see `COPYING.LESSER`).

    The complete corresponding source code of IBEX is embedded in the sources jar of

        ${project.group}:$solverProjectName:${project.version} (classifier `sources`)

    It is a verbatim copy of https://github.com/ibex-team/ibex-lib/tree/$ibexCommit
""".trimIndent() + "\n"

val upstreamSourceNotice = tasks.register("upstreamSourceNotice") {
    description = "Generate the notice pointing at the IBEX sources"
    // Rebind as locals, so that the action below doesn't have to reference the build script.
    val outputFile = noticeFile
    val text = noticeText
    inputs.property("noticeText", text)
    outputs.file(outputFile)
    doLast {
        outputFile.get().asFile.writeText(text)
    }
}

tasks.named<Jar>("sourcesJar") {
    from(upstreamSourceNotice)
}

// Expose the extracted native libraries to :refinery-ibex-solver, which puts their directory on the dynamic linker
// search path to test loading them without extracting them from the jars on the classpath.
configurations.create("nativeLibraries") {
    isCanBeConsumed = true
    isCanBeResolved = false
    outgoing.artifact(libraryResourcesDir)
}
