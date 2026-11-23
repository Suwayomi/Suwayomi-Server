package android.view;

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

/** A pointer event, limited to what is needed to hand touches to a view. */
public final class MotionEvent {
    public static final int INVALID_POINTER_ID = -1;

    public static final int ACTION_MASK = 0xff;
    public static final int ACTION_DOWN = 0;
    public static final int ACTION_UP = 1;
    public static final int ACTION_MOVE = 2;
    public static final int ACTION_CANCEL = 3;
    public static final int ACTION_OUTSIDE = 4;
    public static final int ACTION_POINTER_DOWN = 5;
    public static final int ACTION_POINTER_UP = 6;
    public static final int ACTION_HOVER_MOVE = 7;
    public static final int ACTION_SCROLL = 8;
    public static final int ACTION_HOVER_ENTER = 9;
    public static final int ACTION_HOVER_EXIT = 10;
    public static final int ACTION_POINTER_INDEX_MASK = 0xff00;
    public static final int ACTION_POINTER_INDEX_SHIFT = 8;

    public static final int TOOL_TYPE_UNKNOWN = 0;
    public static final int TOOL_TYPE_FINGER = 1;
    public static final int TOOL_TYPE_STYLUS = 2;
    public static final int TOOL_TYPE_MOUSE = 3;
    public static final int TOOL_TYPE_ERASER = 4;

    public static final class PointerProperties {
        public int id;
        public int toolType;

        public PointerProperties() {
            clear();
        }

        public PointerProperties(PointerProperties other) {
            copyFrom(other);
        }

        public void clear() {
            id = INVALID_POINTER_ID;
            toolType = TOOL_TYPE_UNKNOWN;
        }

        public void copyFrom(PointerProperties other) {
            id = other.id;
            toolType = other.toolType;
        }
    }

    public static final class PointerCoords {
        public float x;
        public float y;
        public float pressure;
        public float size;
        public float touchMajor;
        public float touchMinor;
        public float toolMajor;
        public float toolMinor;
        public float orientation;

        public PointerCoords() {
        }

        public PointerCoords(PointerCoords other) {
            copyFrom(other);
        }

        public void clear() {
            x = 0;
            y = 0;
            pressure = 0;
            size = 0;
            touchMajor = 0;
            touchMinor = 0;
            toolMajor = 0;
            toolMinor = 0;
            orientation = 0;
        }

        public void copyFrom(PointerCoords other) {
            x = other.x;
            y = other.y;
            pressure = other.pressure;
            size = other.size;
            touchMajor = other.touchMajor;
            touchMinor = other.touchMinor;
            toolMajor = other.toolMajor;
            toolMinor = other.toolMinor;
            orientation = other.orientation;
        }
    }

    private final long downTime;
    private final long eventTime;
    private final int action;
    private final PointerProperties[] pointerProperties;
    private final PointerCoords[] pointerCoords;
    private final int metaState;
    private final int source;

    private MotionEvent(
            long downTime, long eventTime, int action, PointerProperties[] pointerProperties,
            PointerCoords[] pointerCoords, int metaState, int source) {
        this.downTime = downTime;
        this.eventTime = eventTime;
        this.action = action;
        this.pointerProperties = pointerProperties;
        this.pointerCoords = pointerCoords;
        this.metaState = metaState;
        this.source = source;
    }

    public static MotionEvent obtain(
            long downTime, long eventTime, int action, int pointerCount, PointerProperties[] pointerProperties,
            PointerCoords[] pointerCoords, int metaState, int buttonState, float xPrecision, float yPrecision,
            int deviceId, int edgeFlags, int source, int flags) {
        PointerProperties[] properties = new PointerProperties[pointerCount];
        PointerCoords[] coords = new PointerCoords[pointerCount];
        for (int i = 0; i < pointerCount; i++) {
            properties[i] = new PointerProperties(pointerProperties[i]);
            coords[i] = new PointerCoords(pointerCoords[i]);
        }
        return new MotionEvent(downTime, eventTime, action, properties, coords, metaState, source);
    }

    public static MotionEvent obtain(long downTime, long eventTime, int action, float x, float y, int metaState) {
        PointerProperties properties = new PointerProperties();
        properties.id = 0;
        PointerCoords coords = new PointerCoords();
        coords.x = x;
        coords.y = y;
        coords.pressure = 1;
        coords.size = 1;
        return new MotionEvent(
                downTime, eventTime, action, new PointerProperties[] {properties}, new PointerCoords[] {coords},
                metaState, 0);
    }

    public void recycle() {
    }

    public long getDownTime() {
        return downTime;
    }

    public long getEventTime() {
        return eventTime;
    }

    public int getAction() {
        return action;
    }

    public int getActionMasked() {
        return action & ACTION_MASK;
    }

    public int getActionIndex() {
        return (action & ACTION_POINTER_INDEX_MASK) >> ACTION_POINTER_INDEX_SHIFT;
    }

    public int getMetaState() {
        return metaState;
    }

    public int getSource() {
        return source;
    }

    public int getPointerCount() {
        return pointerCoords.length;
    }

    public int getPointerId(int pointerIndex) {
        return pointerProperties[pointerIndex].id;
    }

    public int getToolType(int pointerIndex) {
        return pointerProperties[pointerIndex].toolType;
    }

    public float getX() {
        return getX(0);
    }

    public float getY() {
        return getY(0);
    }

    public float getX(int pointerIndex) {
        return pointerCoords[pointerIndex].x;
    }

    public float getY(int pointerIndex) {
        return pointerCoords[pointerIndex].y;
    }

    public float getPressure() {
        return getPressure(0);
    }

    public float getPressure(int pointerIndex) {
        return pointerCoords[pointerIndex].pressure;
    }

    public float getTouchMajor(int pointerIndex) {
        return pointerCoords[pointerIndex].touchMajor;
    }

    public float getTouchMinor(int pointerIndex) {
        return pointerCoords[pointerIndex].touchMinor;
    }

    public float getOrientation(int pointerIndex) {
        return pointerCoords[pointerIndex].orientation;
    }
}
