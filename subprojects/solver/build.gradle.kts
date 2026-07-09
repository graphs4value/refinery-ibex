/*
 * SPDX-FileCopyrightText: 2026 The Refinery Authors <https://refinery.tools/>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
	id("tools.refinery.ibex.gradle.java-library")
}

tasks.test {
	useJUnitPlatform()
	jvmArgs("--enable-native-access=ALL-UNNAMED")
}

dependencies {
	implementation(libs.jna)
	implementation(project(":refinery-ibex-solver-darwin-aarch64"))
	implementation(project(":refinery-ibex-solver-linux-aarch64"))
	implementation(project(":refinery-ibex-solver-linux-x86-64"))
	implementation(project(":refinery-ibex-solver-win32-x86-64"))
	testImplementation(libs.junit.api)
	testRuntimeOnly(libs.junit.engine)
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
