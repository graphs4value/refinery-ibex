/*
 * SPDX-FileCopyrightText: 2026 The Refinery Authors <https://refinery.tools/>
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package tools.refinery.ibex.gradle;

import org.gradle.api.Project;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

public abstract class RefineryIbexExtension {
    private final String z3Version;

    @Inject
    public RefineryIbexExtension(Project project) {
        z3Version = project.getVersion().toString().split("-")[0];
    }

    public String getIbexVersion() {
        return z3Version;
    }

    public abstract Property<String> getNameSuffix();

    public abstract Property<IntervalLib> getIntervalLib();
}
