package suwayomi.tachidesk.util;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

/**
 * A variant of the URLClassLoader that first loads from the URLs and only after that from the
 * parent.
 * Ref: https://github.com/apache/flink/tree/master/flink-core/src/main/java/org/apache/flink/util/ChildFirstClassLoader.java
 */
public final class ChildFirstURLClassLoader extends URLClassLoader {

    /**
     * The classes that should always go through the parent ClassLoader.
     */
    private static final String[] alwaysParentFirstPatterns = {
            "java.", "javax.", "sun.", "com.sun.", "jdk.internal.","org.xml.", "org.w3c.",
            "okhttp3."
    };

    public ChildFirstURLClassLoader(URL[] urls) {
        super(urls);
    }

    @Override
    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            // First, check if the class has already been loaded
            Class<?> c = findLoadedClass(name);

            if (c == null) {
                // check whether the class should go parent-first
                for (String alwaysParentFirstPattern : alwaysParentFirstPatterns) {
                    if (name.startsWith(alwaysParentFirstPattern)) {
                        return super.loadClass(name, resolve);
                    }
                }
                try {
                    // check the URLs
                    c = findClass(name);
                    if (resolve) {
                        resolveClass(c);
                    }
                } catch (ClassNotFoundException e) {
                    // let URLClassLoader do it, which will eventually call the parent
                    c = super.loadClass(name, resolve);
                }
            } else if (resolve) {
                resolveClass(c);
            }

            return c;
        }
    }

    @Override
    public URL getResource(String name) {
        // first, try and find it via the URLClassloader
        URL urlClassLoaderResource = findResource(name);

        if (urlClassLoaderResource != null) {
            return urlClassLoaderResource;
        }

        // delegate to super
        return super.getResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        // first get resources from URLClassloader
        Enumeration<URL> urlClassLoaderResources = findResources(name);

        final List<URL> result = new ArrayList<>();

        while (urlClassLoaderResources.hasMoreElements()) {
            result.add(urlClassLoaderResources.nextElement());
        }

        // get parent urls
        Enumeration<URL> parentResources = getParent().getResources(name);

        while (parentResources.hasMoreElements()) {
            result.add(parentResources.nextElement());
        }

        return new Enumeration<>() {
            final Iterator<URL> iter = result.iterator();

            public boolean hasMoreElements() {
                return iter.hasNext();
            }

            public URL nextElement() {
                return iter.next();
            }
        };
    }

    static {
        ClassLoader.registerAsParallelCapable();
    }
}