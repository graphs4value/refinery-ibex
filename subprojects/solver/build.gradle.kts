/*
 * SPDX-FileCopyrightText: 2026 The Refinery Authors <https://refinery.tools/>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
	id("tools.refinery.ibex.gradle.java-library")
}

// The LGPL requires us to distribute the source code of IBEX along with the native libraries built from it, so we
// embed the upstream source tree into this sources jar. The archive is created by the CI build, which copies it into
// {@code src/upstream}; see {@code .github/workflows/build.yml}.
val upstreamSourceArchive = layout.projectDirectory.file("src/upstream/ibex-src.tar.gz")
val upstreamSourceArchiveName = "ibex-lib-${refinery.ibexVersion}-src.tar.gz"
val ibexCommit: String = providers.gradleProperty("tools.refinery.ibex.commit").get()

val noticeFile = layout.buildDirectory.file("generated/upstream/IBEX-SOURCE.md")
val noticeText = """
	# IBEX source code

	The native libraries distributed in the `${project.group}:${project.name}-*` artifacts are built from
	[IBEX](https://github.com/ibex-team/ibex-lib) ${refinery.ibexVersion}, which is licensed under the GNU Lesser
	General Public License, version 3 or later (see `COPYING.LESSER`).

	IBEX vendors the source code of the interval arithmetic libraries it can be built with. Depending on the
	platform, the native libraries are built with either

	  * GAOL and the MathLib library it relies on, which are licensed under the GNU Lesser General Public License,
	    version 2 or later (vendored under `interval_lib_wrapper/gaol/3rd`), or
	  * filib++, which is licensed under the GNU Lesser General Public License, version 2.1 or later (vendored under
	    `interval_lib_wrapper/filib/3rd`).

	See the `IBEX-SOURCE.md` of each platform-specific artifact for the library it was built with.

	The complete corresponding source code of IBEX, including the vendored interval arithmetic libraries, is embedded
	as `$upstreamSourceArchiveName` in the sources jar of

	    ${project.group}:${project.name}:${project.version} (classifier `sources`)

	It is a copy of https://github.com/ibex-team/ibex-lib/tree/$ibexCommit with the single deviation that the
	`lp_lib_wrapper/soplex/3rd` directory was left out. That directory holds the vendored source code of SoPlex 4.0.2
	and the patches derived from it, which are distributed under the ZIB Academic License instead of an open source
	license, and is available free of charge from https://soplex.zib.de/ instead. IBEX is built with its default
	`LP_LIB=none` setting, so SoPlex is neither compiled into nor distributed with any of our artifacts.
""".trimIndent() + "\n"

val upstreamSourceNotice = tasks.register("upstreamSourceNotice") {
	description = "Generate the notice pointing at the embedded IBEX sources"
	// Rebind as locals, so that the action below doesn't have to reference the build script.
	val outputFile = noticeFile
	val text = noticeText
	inputs.property("noticeText", text)
	outputs.file(outputFile)
	doLast {
		outputFile.get().asFile.writeText(text)
	}
}

// Consumers of the binary jars have to be able to find the sources, too, so the notice refers to the sources jar by
// its coordinates instead of pointing at the archive next to it.
tasks.named<Jar>("jar") {
	from(upstreamSourceNotice)
}

tasks.named<Jar>("sourcesJar") {
	// Rebind as locals, so that the actions below don't have to reference the build script.
	val archiveName = upstreamSourceArchiveName
	val archiveFile = upstreamSourceArchive.asFile
	from(upstreamSourceNotice)
	from(upstreamSourceArchive) {
		rename { archiveName }
	}
	// A missing archive would silently produce a sources jar that doesn't satisfy the LGPL, so fail loudly instead.
	doFirst {
		if (!archiveFile.exists()) {
			throw GradleException(
				"Missing IBEX source archive at $archiveFile. It is built by the ibex job of the CI workflow " +
						"and must be copied here before packaging."
			)
		}
	}
}

// Subprojects packaging the native libraries, keyed by the JNA resource prefix of the platform they target.
val nativeLibraryProjects = rootProject.subprojects
	.filter { it.name.startsWith("${project.name}-") }
	.associate {
		val name = it.name
		name.substring(project.name.length + 1) to ":${name}"
	}

// JNA resource prefix of the platform running the build, i.e., the platform the tests will run on.
val hostResourcePrefix: String = com.sun.jna.Platform.RESOURCE_PREFIX
val hostNativeLibraryProject = nativeLibraryProjects[hostResourcePrefix]

// Directory of already extracted native libraries for the platform running the build.
val hostNativeLibraries = configurations.create("hostNativeLibraries") {
	isCanBeConsumed = false
	isCanBeResolved = true
}
val hostNativeLibrariesDir = files(hostNativeLibraries)

tasks.withType<Test>().configureEach {
	useJUnitPlatform()
	jvmArgs("--enable-native-access=ALL-UNNAMED")
}

// Everything the tests need, except the jars containing the native libraries, so that
// {@see tools.refinery.z3.Z3SolverLoader} can't extract them.
val classpathWithoutNativeLibraries = run {
	// Rebind as a local, so that the filter below captures only this set instead of the whole build script.
	val nativeLibraryProjectPaths = nativeLibraryProjects.values.toSet()
	files(
		sourceSets.main.map { it.output },
		configurations.testRuntimeClasspath.map { testRuntimeClasspath ->
			testRuntimeClasspath.incoming.artifactView {
				componentFilter { component ->
					component !is ProjectComponentIdentifier ||
							component.projectPath !in nativeLibraryProjectPaths
				}
			}.files
		},
	)
}

// Tests that must run without any of the platform-specific jars on the classpath.
val testMissingLibrariesSourceSet = sourceSets.create("testMissingLibraries") {
	compileClasspath = sourceSets.test.get().compileClasspath
	runtimeClasspath = output + classpathWithoutNativeLibraries
}

// Counterpart of {@code test}: instead of letting the loader extract the native libraries from the
// platform-specific jars, we point the dynamic linker at an already extracted set of libraries and check that the
// plain {@code System.loadLibrary} code path finds them.
val testExtractedLibraries = tasks.register<Test>("testExtractedLibraries") {
	group = LifecycleBasePlugin.VERIFICATION_GROUP
	description = "Run tests against native libraries on the dynamic linker search path"

	testClassesDirs = sourceSets.test.get().output.classesDirs
	classpath = classpathWithoutNativeLibraries + sourceSets.test.get().output

	// The library path is passed to the forked JVM as a plain string, so we have to declare the dependency on the
	// extracted libraries explicitly.
	inputs.files(hostNativeLibrariesDir)
		.withPropertyName("hostNativeLibraries")
		.withPathSensitivity(PathSensitivity.RELATIVE)

	val hasHostNativeLibraries = hostNativeLibraryProject != null
	onlyIf("Z3 native libraries are available for the platform running the build") {
		hasHostNativeLibraries
	}

	// Capture the file collection in a local, so that the action below doesn't have to reference the build script.
	val librariesDir = hostNativeLibrariesDir
	doFirst {
		val librariesPath = librariesDir.asPath
		// Lets {@code System.loadLibrary} find the JNI library.
		systemProperty("java.library.path", librariesPath)
		// Lets the dynamic linker find the Z3 solver library the JNI library links against.
		val libraryPathVariable = when {
			com.sun.jna.Platform.isWindows() -> "PATH"
			com.sun.jna.Platform.isMac() -> "DYLD_LIBRARY_PATH"
			else -> "LD_LIBRARY_PATH"
		}
		environment(
			libraryPathVariable, listOfNotNull(librariesPath, System.getenv(libraryPathVariable))
				.joinToString(File.pathSeparator)
		)
	}
}

// Negative control for {@code testExtractedLibraries}: neither the platform-specific jars nor a library path, so
// there is nothing left to load and the loader has to report an error. Without this, {@code testExtractedLibraries}
// could pass for the wrong reason, e.g., if the native libraries leaked back onto its classpath.
val testMissingLibraries = tasks.register<Test>("testMissingLibraries") {
	group = LifecycleBasePlugin.VERIFICATION_GROUP
	description = "Check that loading fails if no Z3 native libraries are available at all"

	testClassesDirs = testMissingLibrariesSourceSet.output.classesDirs
	classpath = testMissingLibrariesSourceSet.runtimeClasspath

	doFirst {
		// Make sure a library path set in the environment of the build can't feed the loader after all.
		environment.remove("LD_LIBRARY_PATH")
		environment.remove("DYLD_LIBRARY_PATH")
	}
}

tasks.check {
	dependsOn(testExtractedLibraries, testMissingLibraries)
}

dependencies {
	implementation(libs.jna)
	for (projectPath in nativeLibraryProjects.values) {
		implementation(project(projectPath))
	}
	if (hostNativeLibraryProject != null) {
		hostNativeLibraries(project(path = hostNativeLibraryProject, configuration = "nativeLibraries"))
	}
	compileOnly(libs.jetbrainsAnnotations)
	testImplementation(libs.junit.api)
	testRuntimeOnly(libs.junit.engine)
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
