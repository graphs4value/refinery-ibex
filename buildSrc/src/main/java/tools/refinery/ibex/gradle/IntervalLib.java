/*
 * SPDX-FileCopyrightText: 2026 The Refinery Authors <https://refinery.tools/>
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package tools.refinery.ibex.gradle;

/**
 * Interval arithmetic library IBEX is built with. IBEX vendors the source code of all of them, but each platform is
 * built with (and distributes the binaries of) only one.
 * <p>
 * Must be kept in sync with the {@code interval-lib} entries of the {@code ibex} job matrix in
 * {@code .github/workflows/build.yml}.
 */
public enum IntervalLib {
    /**
     * GAOL, which relies on MathLib.
     */
    GAOL,

    /**
     * filib++.
     */
    FILIB
}
