package android.os.shadows;
// package org.robolectric.res.android;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A unique id per object registry. Used to emulate android platform behavior of storing a long
 * which represents a pointer to an object.
 */
public class NativeObjRegistry<T> {

  private static final int INITIAL_ID = 1;

  private final String name;
  private final boolean debug;
  private final HashMap<Long, T> nativeObjToIdMap = new HashMap<Long, T>();
  private final Map<Long, DebugInfo> idToDebugInfoMap;

  private long nextId = INITIAL_ID;

  public NativeObjRegistry(Class<T> theClass) {
    this(theClass, false);
  }

  public NativeObjRegistry(Class<T> theClass, boolean debug) {
    this(theClass.getSimpleName(), debug);
  }

  public NativeObjRegistry(String name) {
    this(name, false);
  }

  public NativeObjRegistry(String name, boolean debug) {
    this.name = name;
    this.debug = debug;
    this.idToDebugInfoMap = debug ? new HashMap<>() : null;
  }

  private Long getNativeObjectId(T o) {
    for(Map.Entry<Long, T> entry : nativeObjToIdMap.entrySet()) {
      if (o == entry.getValue())
        return entry.getKey();
    }
    return null;
  }

  /**
   * Register and assign a new unique native id for given object (representing a C memory pointer).
   *
   * @throws IllegalStateException if the object was previously registered
   */
  public synchronized long register(T o) {
    if (o == null)
      throw new IllegalStateException("Object must not be null");
    Long nativeId = getNativeObjectId(o);
    if (nativeId != null) {
      if (debug) {
        DebugInfo debugInfo = idToDebugInfoMap.get(nativeId);
        if (debugInfo != null) {
          System.out.printf(
              "NativeObjRegistry %s: register %d -> %s already registered:%n", name, nativeId, o);
          debugInfo.registrationTrace.printStackTrace(System.out);
        }
      }
      throw new IllegalStateException("Object was previously registered with id " + nativeId);
    }

    nativeId = nextId;
    if (debug) {
      System.out.printf("NativeObjRegistry %s: register %d -> %s%n", name, nativeId, o);
      idToDebugInfoMap.put(nativeId, new DebugInfo(new Trace()));
    }
    nativeObjToIdMap.put(nativeId, o);
    nextId++;
    return nativeId;
  }

  /**
   * Unregister an object previously registered with {@link #register(Object)}.
   *
   * @param nativeId the unique id (representing a C memory pointer) of the object to unregister.
   * @throws IllegalStateException if the object was never registered, or was previously
   *     unregistered.
   */
  public synchronized T unregister(long nativeId) {
    T o = nativeObjToIdMap.remove(nativeId);
    if (debug) {
      System.out.printf("NativeObjRegistry %s: unregister %d -> %s%n", name, nativeId, o);
      new RuntimeException("unregister debug").printStackTrace(System.out);
    }
    if (o == null) {
      if (debug) {
        DebugInfo debugInfo = idToDebugInfoMap.get(nativeId);
        debugInfo.unregistrationTraces.add(new Trace());
        if (debugInfo.unregistrationTraces.size() > 1) {
          System.out.format("NativeObjRegistry %s: Too many unregistrations:%n", name);
          for (Trace unregistration : debugInfo.unregistrationTraces) {
            unregistration.printStackTrace(System.out);
          }
        }
      }
      throw new IllegalStateException(
          nativeId + " has already been removed (or was never registered)");
    }
    return o;
  }

  /** Retrieve the native object for given id. Throws if object with that id cannot be found */
  public synchronized T getNativeObject(long nativeId) {
    T object = nativeObjToIdMap.get(nativeId);
    if (object != null) {
      return object;
    } else {
      throw new NullPointerException(
          String.format(
              "Could not find object with nativeId: %d. Currently registered ids: %s",
              nativeId, nativeObjToIdMap.keySet()));
    }
  }

  /**
   * Updates the native object for the given id.
   *
   * @throws IllegalStateException if no object was registered with the given id before
   */
  public synchronized void update(long nativeId, T o) {
    T previous = nativeObjToIdMap.get(nativeId);
    if (previous == null) {
      throw new IllegalStateException("Native id " + nativeId + " was never registered");
    }
    if (debug) {
      System.out.printf("NativeObjRegistry %s: update %d -> %s%n", name, nativeId, o);
      idToDebugInfoMap.put(nativeId, new DebugInfo(new Trace()));
    }
    nativeObjToIdMap.put(nativeId, o);
  }

  /**
   * Similar to {@link #getNativeObject(long)} but returns null if object with given id cannot be
   * found.
   */
  public synchronized T peekNativeObject(long nativeId) {
    return nativeObjToIdMap.get(nativeId);
  }

  /** WARNING -- dangerous! Call {@link #unregister(long)} instead! */
  public synchronized void clear() {
    nextId = INITIAL_ID;
    nativeObjToIdMap.clear();
  }

  private static class DebugInfo {
    final Trace registrationTrace;
    final List<Trace> unregistrationTraces = new ArrayList<>();

    public DebugInfo(Trace trace) {
      registrationTrace = trace;
    }
  }

  private static class Trace extends Throwable {

    private Trace() {}
  }
}
