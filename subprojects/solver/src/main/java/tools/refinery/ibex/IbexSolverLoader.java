/*
 * Copyright 2010-2022 Google LLC
 * Copyright 2023 The Refinery Authors <https://refinery.tools/>
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * This file is based on
 * https://github.com/google/or-tools/blob/f3d1d5f6a67356ec38a7fd2ab607624eea8ad3a6/ortools/java/com/google/ortools
 * /Loader.java
 * We adapted the loader logic to extract the JNI libraries of Z3 and the corresponding dependencies instead of the
 * Google OR-Tools JNI libraries.
 */
package tools.refinery.ibex;

import com.sun.jna.Platform;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;

public final class IbexSolverLoader {
    private static final String SOLVER_LIBRARY_NAME = "ibex";
    private static final String JNI_LIBRARY_NAME = SOLVER_LIBRARY_NAME + "-java";

    private static boolean loaded;

    private IbexSolverLoader() {
        throw new IllegalStateException("This is a static utility class and should not be instantiated directly");
    }

    public static synchronized void loadNativeLibraries() {
        if (loaded) {
            return;
        }
        try {
            System.loadLibrary(JNI_LIBRARY_NAME);
            loaded = true;
            return;
        } catch (UnsatisfiedLinkError e) {
            // Continue, we'll have to extract the libraries from the classpath.
        }
        try {
            extractAndLoad();
            loaded = true;
        } catch (IOException e) {
            throw new IllegalStateException("Could not extract and load " + JNI_LIBRARY_NAME, e);
        }
    }

    private static void extractAndLoad() throws IOException {
        var resourceName = JNI_LIBRARY_NAME + "-" + Platform.RESOURCE_PREFIX;
        var resource = IbexSolverLoader.class.getClassLoader().getResource(resourceName);
        if (resource == null) {
            throw new IllegalStateException("Resource %s was not found".formatted(resourceName));
        }
        URI resourceUri;
        try {
            resourceUri = resource.toURI();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Invalid resource URI: " + resource);
        }
        FileSystem fileSystem = null;
        boolean newFileSystem = false;
        Path extractedPath;
        try {
            try {
                fileSystem = FileSystems.newFileSystem(resourceUri, Map.of());
                newFileSystem = true;
            } catch (FileSystemAlreadyExistsException e) {
                fileSystem = FileSystems.getFileSystem(resourceUri);
            }
            var resourcePath = fileSystem.provider().getPath(resourceUri);
            if (fileSystem.equals(FileSystems.getDefault())) {
                extractedPath = resourcePath;
            } else {
                extractedPath = extract(resourcePath);
            }
        } finally {
            if (newFileSystem) {
                fileSystem.close();
            }
        }
        // We can't rely on RPATH, so we load libraries in reverse dependency order manually.
        // Some libraries are only used on specific platforms, so load them conditionally.
        loadFromPath(extractedPath, "ultim", true);
        loadFromPath(extractedPath, "gaol", true);
        loadFromPath(extractedPath, "prim", true);
        loadFromPath(extractedPath, SOLVER_LIBRARY_NAME);
        loadFromPath(extractedPath, JNI_LIBRARY_NAME);
    }

    private static Path extract(Path resourcePath) throws IOException {
        var tempDir = Files.createTempDirectory(SOLVER_LIBRARY_NAME).toAbsolutePath();
        tempDir.toFile().deleteOnExit();
        Files.walkFileTree(resourcePath, new SimpleFileVisitor<>() {
            @Override
            public @NotNull FileVisitResult preVisitDirectory(@NotNull Path dir, @NotNull BasicFileAttributes attrs)
                    throws IOException {
                var result = super.preVisitDirectory(dir, attrs);
                var targetPath = getTargetPath(dir, resourcePath, tempDir);
                if (!Files.exists(targetPath)) {
                    Files.createDirectory(targetPath);
                    targetPath.toFile().deleteOnExit();
                }
                return result;
            }

            @Override
            public @NotNull FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs)
                    throws IOException {
                var result = super.visitFile(file, attrs);
                var targetPath = getTargetPath(file, resourcePath, tempDir);
                Files.copy(file, targetPath);
                targetPath.toFile().deleteOnExit();
                return result;
            }
        });
        return tempDir;
    }

    private static Path getTargetPath(Path sourcePath, Path resourcePath, Path tempDir) {
        var targetPath = tempDir.resolve(resourcePath.relativize(sourcePath).toString()).normalize();
        if (!targetPath.startsWith(tempDir)) {
            throw new IllegalStateException("Target path '%s' for '%s' is outside '%s'"
                    .formatted(targetPath, sourcePath, tempDir));
        }
        return targetPath;
    }

    private static void loadFromPath(Path extractedPath, String libraryName) {
        loadFromPath(extractedPath, libraryName, false);
    }

    /**
     * Loads an extracted library.
     *
     * @param extractedPath The path where the libraries for this platform have been extracted.
     * @param libraryName   The name of the library to load.
     * @param intervalLib   Denotes that this dependency is an interval library used by IBEX. Interval libraries are
     *                      optional to load, because different platforms may use different interval libraries.
     *                      Moreover, they use the soname version {@code .0} on Linux and macOS.
     */
    private static void loadFromPath(Path extractedPath, String libraryName, boolean intervalLib) {
        if (intervalLib && Platform.isMac()) {
            libraryName += ".0";
        }
        var systemName = System.mapLibraryName(libraryName);
        if (intervalLib && Platform.isLinux()) {
            systemName += ".0";
        }
        var libraryPath = extractedPath.resolve(systemName).toAbsolutePath();
        if (intervalLib && !libraryPath.toFile().exists()) {
            return;
        }
        System.load(libraryPath.toString());
    }
}
