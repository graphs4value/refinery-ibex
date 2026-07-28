/*
 * SPDX-FileCopyrightText: 2026 The Refinery Authors <https://refinery.tools/>
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package tools.refinery.ibex.gradle;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Interval arithmetic library IBEX is built with. IBEX vendors the source code of all of them, but each platform is
 * built with (and distributes the binaries of) only one.
 * <p>
 * The library versions are the ones vendored by the IBEX version we build, i.e., the names of the archives under
 * {@code interval_lib_wrapper}.
 */
public enum IntervalLib {
    /**
     * GAOL, which relies on the IBM Accurate Portable MathLib.
     * <p>
     * The notices of the vendored MathLib are inconsistent: a GPL-2.0-only {@code COPYING} left over from autotools
     * boilerplate, and headers naming a Lesser General Public License version 2.0, which is really the Library
     * General Public License, renamed to Lesser only in 2.1. We read them as {@code LGPL-2.0-or-later}, the SPDX
     * identifier of that license, following GAOL, which is under it itself and vendors MathLib with its own sources.
     * <p>
     * This is not the license of the same code in glibc, which is {@code LGPL-2.1-or-later}: IBM assigned its
     * copyright to the FSF around 2008-2009, which relicensed the files while merging them, but GAOL vendors the
     * snapshot from before the assignment.
     */
    GAOL("the GAOL interval arithmetic library and the MathLib library it relies on",
            new Library("GAOL", "gaol", "4.2.3alpha0"),
            List.of(new Library("MathLib", "mathlib", "2.1.1")),
            "interval_lib_wrapper/gaol/3rd",
            "LGPL-2.0-or-later", "GNU Library General Public License, version 2 or later",
            "https://www.gnu.org/licenses/old-licenses/lgpl-2.0.html", List.of(
                    // The AUTHORS file of the vendored GAOL sources names a single author, who is also the packager
                    // of the vendored MathLib distribution according to its {@code mathlib.spec.in}.
                    new Author("Frédéric Goualard", "https://github.com/goualard-f/GAOL"),
                    // The AUTHORS file of the vendored MathLib sources is empty, but its README and
                    // {@code mathlib.spec.in} attribute it to IBM. The upstream FTP site is long gone.
                    new Author("IBM Accurate Portable Mathematical Library Authors", null))),

    /**
     * filib++.
     * <p>
     * Its headers name the Library GPL 2.0, inherited from filib++ 2.0, while the 3.0 fork IBEX uses ships a Lesser
     * GPL 2.1 {@code COPYING}. We follow the {@code COPYING} file, which the {@code or later} clause permits anyway.
     */
    FILIB("the filib++ interval arithmetic library",
            new Library("filib++", "filib%2B%2B", "3.0.2.2"),
            List.of(),
            "interval_lib_wrapper/filib/3rd",
            "LGPL-2.1-or-later", "GNU Lesser General Public License, version 2.1 or later",
            "https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html", List.of(
                    // The AUTHORS file of the vendored filib++ sources names five people, but warns that the list is
                    // not exhaustive, so we credit them collectively.
                    new Author("filib++ Authors", "http://www2.math.uni-wuppertal.de/wrswt/software/filib.html")));

    private final String description;
    private final Library library;
    private final List<Library> dependencies;
    private final String vendoredPath;
    private final String licenseSpdxId;
    private final String licenseName;
    private final String licenseUrl;
    private final List<Author> authors;

    IntervalLib(String description, Library library, List<Library> dependencies, String vendoredPath,
                String licenseSpdxId, String licenseName, String licenseUrl, List<Author> authors) {
        this.description = description;
        this.library = library;
        this.dependencies = dependencies;
        this.vendoredPath = vendoredPath;
        this.licenseSpdxId = licenseSpdxId;
        this.licenseName = licenseName;
        this.licenseUrl = licenseUrl;
        this.authors = authors;
    }

    /**
     * {@return this option as a noun phrase, e.g., for the first sentence of a license notice}
     */
    public String getDescription() {
        return description;
    }

    /**
     * {@return the interval arithmetic library itself}
     */
    public Library getLibrary() {
        return library;
    }

    /**
     * {@return the other libraries the interval arithmetic library relies on}
     */
    public List<Library> getDependencies() {
        return dependencies;
    }

    /**
     * {@return every library built into the native libraries when this option is selected}
     */
    public List<Library> getLibraries() {
        var libraries = new ArrayList<Library>(dependencies.size() + 1);
        libraries.add(library);
        libraries.addAll(dependencies);
        return List.copyOf(libraries);
    }

    /**
     * {@return the libraries selected by this option, as they should be referred to in license notices}
     */
    public String getComponents() {
        return getLibraries().stream().map(Library::name).collect(Collectors.joining(" and "));
    }

    /**
     * {@return the directory of the IBEX sources the libraries are vendored in}
     */
    public String getVendoredPath() {
        return vendoredPath;
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
     * An upstream library vendored in the IBEX sources.
     *
     * @param name     the name of the library, as it is referred to by its authors
     * @param purlName the name of the library in a package URL, i.e., percent-encoded
     * @param version  the version of the library vendored by the IBEX version we build
     */
    public record Library(String name, String purlName, String version) {
        /**
         * {@return the package URL of the library}
         * <p>
         * The libraries are only distributed as part of the IBEX sources, so there is no repository to point to and
         * we have to fall back to the {@code generic} package type.
         */
        public String purl() {
            return "pkg:generic/%s@%s".formatted(purlName, version);
        }
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
