/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dalvik.system;

import org.jetbrains.annotations.Nullable;
import xyz.nulldev.androidcompat.pm.PackageController;
import xyz.nulldev.androidcompat.util.KoinGlobalHelper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Enumeration;

/**
 * Base class for common functionality between various dex-based
 * {@link ClassLoader} implementations.
 */
public class BaseDexClassLoader extends ClassLoader {
    private PackageController controller = KoinGlobalHelper.instance(PackageController.class);

    private final URLClassLoader realClassloader;

    /** originally specified path (just used for {@code toString()}) */
    private final String originalPath;

    /**
     * Constructs an instance.
     *
     * @param dexPath the list of jar/apk files containing classes and
     * resources, delimited by {@code File.pathSeparator}, which
     * defaults to {@code ":"} on Android
     * @param optimizedDirectory directory where optimized dex files
     * should be written; may be {@code null}
     * @param libraryPath the list of directories containing native
     * libraries, delimited by {@code File.pathSeparator}; may be
     * {@code null}
     * @param parent the parent class loader
     */
    public BaseDexClassLoader(String dexPath, File optimizedDirectory,
            String libraryPath, ClassLoader parent) {
        super(parent);
        this.originalPath = dexPath;

        URL[] urls = Arrays.stream(dexPath.split(File.pathSeparator)).map(s -> {
            try {
                File file = new File(s);

                if(s.endsWith(".jar"))
                    return file.toURI().toURL();

                File jar = controller.findJarFromApk(file);

                if(jar == null || !jar.exists())
                    throw new IllegalStateException("Could not find APK jar!");

                return jar.toURI().toURL();
            } catch (MalformedURLException e) {
                throw new IllegalStateException("APK JAR is invalid!");
            }
        }).toArray(URL[]::new);

        realClassloader = new URLClassLoader(urls, parent);
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        return realClassloader.loadClass(name);
    }

    @Nullable
    @Override
    public URL getResource(String name) {
        return realClassloader.getResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        return realClassloader.getResources(name);
    }

    public static boolean registerAsParallelCapable() {
        return ClassLoader.registerAsParallelCapable();
    }

    public static URL getSystemResource(String name) {
        return ClassLoader.getSystemResource(name);
    }

    public static Enumeration<URL> getSystemResources(String name) throws IOException {
        return ClassLoader.getSystemResources(name);
    }

    public static InputStream getSystemResourceAsStream(String name) {
        return ClassLoader.getSystemResourceAsStream(name);
    }

    public static ClassLoader getSystemClassLoader() {
        return ClassLoader.getSystemClassLoader();
    }

    @Override
    public void setDefaultAssertionStatus(boolean enabled) {
        realClassloader.setDefaultAssertionStatus(enabled);
    }

    @Override
    public void setPackageAssertionStatus(String packageName, boolean enabled) {
        realClassloader.setPackageAssertionStatus(packageName, enabled);
    }

    @Override
    public void setClassAssertionStatus(String className, boolean enabled) {
        realClassloader.setClassAssertionStatus(className, enabled);
    }

    @Override
    public void clearAssertionStatus() {
        realClassloader.clearAssertionStatus();
    }



    @Override
    public String toString() {
        return getClass().getName() + "[" + originalPath + "]";
    }
}