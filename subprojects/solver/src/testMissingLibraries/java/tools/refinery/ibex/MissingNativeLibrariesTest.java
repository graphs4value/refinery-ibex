/*
 * SPDX-FileCopyrightText: 2026 The Refinery Authors <https://refinery.tools/>
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package tools.refinery.ibex;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Negative control for the tests that load the native libraries: neither the platform-specific jars are on the
 * classpath, nor are the native libraries on the dynamic linker search path, so loading has to fail.
 */
class MissingNativeLibrariesTest {
	@Test
	void testMissingNativeLibraries() {
		assertThrows(IllegalStateException.class, IbexSolverLoader::loadNativeLibraries);
	}
}
