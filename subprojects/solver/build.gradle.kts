/*
 * SPDX-FileCopyrightText: 2026 The Refinery Authors <https://refinery.tools/>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
	id("tools.refinery.ibex.gradle.java-library")
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
