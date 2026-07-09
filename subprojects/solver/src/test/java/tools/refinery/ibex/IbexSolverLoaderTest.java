/*
 * SPDX-FileCopyrightText: 2026 The Refinery Authors <https://refinery.tools/>
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package tools.refinery.ibex;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class IbexSolverLoaderTest {
	@Test
	void testLoad() {
		assertThrows(IllegalStateException.class, IbexSolverLoader::loadNativeLibraries);
	}
}
