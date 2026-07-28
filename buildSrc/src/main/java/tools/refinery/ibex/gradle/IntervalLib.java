/*
 * SPDX-FileCopyrightText: 2026 The Refinery Authors <https://refinery.tools/>
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package tools.refinery.ibex.gradle;

import java.util.List;

/**
 * Interval arithmetic library IBEX is built with. IBEX vendors the source code of all of them, but each platform is
 * built with (and distributes the binaries of) only one.
 */
public enum IntervalLib {
    /**
     * GAOL, which relies on the IBM Accurate Portable MathLib.
     */
    GAOL("GAOL and MathLib", "LGPL-2.0-or-later", "GNU Lesser General Public License, version 2 or later",
            "https://www.gnu.org/licenses/old-licenses/lgpl-2.0.html", List.of(
                    // The AUTHORS file of the vendored GAOL sources names a single author, who is also the packager
                    // of the vendored MathLib distribution according to its {@code mathlib.spec.in}.
                    new Author("Frédéric Goualard", "https://github.com/goualard-f/GAOL"),
                    // The AUTHORS file of the vendored MathLib sources is empty, but its README and
                    // {@code mathlib.spec.in} attribute it to IBM. The upstream FTP site is long gone.
                    new Author("IBM Accurate Portable Mathematical Library Authors", null))),

    /**
     * filib++.
     */
    FILIB("filib++", "LGPL-2.1-or-later", "GNU Lesser General Public License, version 2.1 or later",
            "https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html", List.of(
                    // The AUTHORS file of the vendored filib++ sources names five people, but warns that the list is
                    // not exhaustive, so we credit them collectively.
                    new Author("filib++ Authors", "http://www2.math.uni-wuppertal.de/wrswt/software/filib.html")));

    private final String components;
    private final String licenseSpdxId;
    private final String licenseName;
    private final String licenseUrl;
    private final List<Author> authors;

    IntervalLib(String components, String licenseSpdxId, String licenseName, String licenseUrl,
                List<Author> authors) {
        this.components = components;
        this.licenseSpdxId = licenseSpdxId;
        this.licenseName = licenseName;
        this.licenseUrl = licenseUrl;
        this.authors = authors;
    }

    /**
     * {@return the libraries selected by this option, as they should be referred to in license notices}
     */
    public String getComponents() {
        return components;
    }

    public String getLicenseSpdxId() {
        return licenseSpdxId;
    }

    public String getLicenseName() {
        return licenseName;
    }

    public String getLicenseUrl() {
        return licenseUrl;
    }

    public List<Author> getAuthors() {
        return authors;
    }

    /**
     * Author of an upstream library, as credited in the POM of the artifacts distributing it.
     *
     * @param name the name of the author
     * @param url  the homepage of the author, or {@code null} if there is none to link to
     */
    public record Author(String name, String url) {
    }
}
