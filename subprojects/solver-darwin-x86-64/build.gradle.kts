/*
 * SPDX-FileCopyrightText: 2026 The Refinery Authors <https://refinery.tools/>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import tools.refinery.ibex.gradle.IntervalLib

plugins {
	id("tools.refinery.ibex.gradle.native-library")
}

refinery.nameSuffix = "Darwin x86_64"
refinery.intervalLib = IntervalLib.FILIB
