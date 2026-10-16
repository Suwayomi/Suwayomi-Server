/*
 * Copyright 2016 Andy Bao
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xyz.nulldev.androidcompat.res;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Custom implementation of R.java.
 */
public class RCompat {
    //Resources stored in memory
    private static final Map<Integer, Resource> resources = new ConcurrentHashMap<>();

    //The last used resource ID, used to generate new resource IDs.
    //IDs start at 0x7f000000 like real app resources so that resource id 0
    //keeps its "no resource" sentinel meaning.
    private static final AtomicInteger lastRes = new AtomicInteger(0x7f000000);

    /**
     * Add a new String resource.
     * @param s The value of the String resource.
     * @return The added String resource.
     */
    public static int sres(String s) {
        return res(new StringResource(s));
    }

    public static int dres(String s) {
        return res(new DrawableResource(s));
    }

    /**
     * Add a resource.
     * @param res The resource to add.
     * @return The ID of the added resource.
     */
    public static int res(Resource res) {
        int nextRes = lastRes.getAndIncrement();
        resources.put(nextRes, res);
        return nextRes;
    }

    /**
     * Get a string resource
     * @param id The id of the resource
     * @return The string resource
     * @throws NotFoundException if no resource exists for the given id
     */
    public static String getString(int id) {
        Resource resource = resources.get(id);
        if (resource == null) {
            throw new NotFoundException("No resource found for id: " + id);
        }
        return (String) cast(resource, StringResource.class).getValue();
    }

    /**
     * Thrown when a resource lookup misses, mirroring
     * android.content.res.Resources.NotFoundException.
     */
    public static class NotFoundException extends RuntimeException {
        public NotFoundException() {
        }

        public NotFoundException(String message) {
            super(message);
        }
    }

    /**
     * Convenience method for casting resources.
     * @param resource The resource to cast
     * @param output The class of the output resource type
     * @param <T> The type of the output resource
     * @return The casted resource
     */
    private static <T extends Resource> T cast(Resource resource, Class<T> output) {
        if(resource.getType().equals(output)) {
            return (T) resource;
        } else {
            throw new IllegalArgumentException("This resource is not of type: " + output.getSimpleName() + "!");
        }
    }
}
