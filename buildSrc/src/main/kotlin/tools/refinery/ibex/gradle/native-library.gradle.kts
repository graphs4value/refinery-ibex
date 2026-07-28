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
val noticeText = refinery.intervalLib.map { intervalLib ->
    // The paragraphs are joined instead of interpolated, because {@code trimIndent} would get confused by the
    // already unindented lines of the interval library notice.
    val paragraphs = listOf(
        """
            # IBEX source code

            This artifact bundles native libraries built from [IBEX](https://github.com/ibex-team/ibex-lib)
            ${refinery.ibexVersion}, which is licensed under the GNU Lesser General Public License, version 3 or
            later (see `COPYING.LESSER`).
        """.trimIndent(),
        // IBEX vendors the source code of every interval arithmetic library it supports, but the native libraries
        // of this platform are built with (and link against) only the one selected here.
        """
            The native libraries are built with ${intervalLib.description}, whose source code is vendored in the
            IBEX sources under `${intervalLib.vendoredPath}` and licensed under the ${intervalLib.licenseName}.
        """.trimIndent(),
        """
            The complete corresponding source code of IBEX, including the vendored interval arithmetic libraries, is
            embedded in the sources jar of

                ${project.group}:$solverProjectName:${project.version} (classifier `sources`)

            It is a copy of https://github.com/ibex-team/ibex-lib/tree/$ibexCommit with the vendored SoPlex sources
            left out, which are not built into these native libraries. See the `IBEX-SOURCE.md` of that artifact for
            details.
        """.trimIndent(),
    )
    paragraphs.joinToString("\n\n") + "\n"
}

val upstreamSourceNotice = tasks.register("upstreamSourceNotice") {
    description = "Generate the notice pointing at the IBEX sources"
    // Rebind as locals, so that the action below doesn't have to reference the build script.
    val outputFile = noticeFile
    val text = noticeText
    inputs.property("noticeText", text)
    outputs.file(outputFile)
    doLast {
        outputFile.get().asFile.writeText(text.get())
    }
}

// Consumers of the binary jar have to be able to find the sources, too.
tasks.named<Jar>("jar") {
    from(upstreamSourceNotice)
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
