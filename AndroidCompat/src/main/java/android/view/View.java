/*
 * Copyright (C) 2006 The Android Open Source Project
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

package android.view;

import android.animation.AnimatorInflater;
import android.animation.StateListAnimator;
import android.annotation.AttrRes;
import android.annotation.CallSuper;
import android.annotation.ColorInt;
import android.annotation.DrawableRes;
import android.annotation.FloatRange;
import android.annotation.IdRes;
import android.annotation.IntDef;
import android.annotation.IntRange;
import android.annotation.LayoutRes;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.Size;
import android.annotation.StyleRes;
import android.annotation.SuppressLint;
import android.annotation.SystemApi;
import android.annotation.TestApi;
import android.annotation.UiThread;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentSender;
import android.content.res.ColorStateList;
import android.content.res.CompatibilityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Interpolator;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RecordingCanvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.input.InputManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.Trace;
import android.text.InputType;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.FloatProperty;
import android.util.LayoutDirection;
import android.util.Log;
import android.util.LongSparseArray;
import android.util.Pair;
import android.util.Property;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.util.StateSet;
import android.util.TimeUtils;
import android.util.TypedValue;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.WindowInsets.Type;
import android.view.WindowInsetsAnimation.Bounds;
import android.view.WindowManager.LayoutParams;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityEventSource;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;
import android.view.accessibility.AccessibilityNodeProvider;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.Transformation;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillManager;
import android.view.autofill.AutofillValue;
import android.view.contentcapture.ContentCaptureContext;
import android.view.contentcapture.ContentCaptureManager;
import android.view.contentcapture.ContentCaptureSession;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Checkable;

import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;

@UiThread
public class View implements Drawable.Callback, KeyEvent.Callback,
        AccessibilityEventSource {
    /** @hide */
    public static boolean DEBUG_DRAW = false;

    protected static final String VIEW_LOG_TAG = "View";

    public static boolean sDebugViewAttributes = false;

    public static String sDebugViewAttributesApplicationPackage;

    public static final int NO_ID = -1;

    public static final int LAST_APP_AUTOFILL_ID = Integer.MAX_VALUE / 2;

    /** @hide */
    // public HapticScrollFeedbackProvider mScrollFeedbackProvider = null;

    static boolean sTextureViewIgnoresDrawableSetters = false;

    protected static boolean sPreserveMarginParamsInLayoutParamConversion;

    static boolean sCascadedDragDrop;

    static boolean sHasFocusableExcludeAutoFocusable;

    static boolean sBrokenInsetsDispatch;

    protected static boolean sBrokenWindowBackground;

    static boolean sForceLayoutWhenInsetsChanged;

    /** @hide */
    @IntDef({NOT_FOCUSABLE, FOCUSABLE, FOCUSABLE_AUTO})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Focusable {}

    public static final int NOT_FOCUSABLE = 0x00000000;

    public static final int FOCUSABLE = 0x00000001;

    public static final int FOCUSABLE_AUTO = 0x00000010;

    /** @hide */
    @IntDef({VISIBLE, INVISIBLE, GONE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Visibility {}

    public static final int VISIBLE = 0x00000000;

    public static final int INVISIBLE = 0x00000004;

    public static final int GONE = 0x00000008;

    static final int VISIBILITY_MASK = 0x0000000C;

    public static final String AUTOFILL_HINT_EMAIL_ADDRESS = "emailAddress";

    public static final String AUTOFILL_HINT_NAME = "name";

    public static final String AUTOFILL_HINT_USERNAME = "username";

    public static final String AUTOFILL_HINT_PASSWORD = "password";

    public static final String AUTOFILL_HINT_PHONE = "phone";

    public static final String AUTOFILL_HINT_POSTAL_ADDRESS = "postalAddress";

    public static final String AUTOFILL_HINT_POSTAL_CODE = "postalCode";

    public static final String AUTOFILL_HINT_CREDIT_CARD_NUMBER = "creditCardNumber";

    public static final String AUTOFILL_HINT_CREDIT_CARD_SECURITY_CODE = "creditCardSecurityCode";

    public static final String AUTOFILL_HINT_CREDIT_CARD_EXPIRATION_DATE =
            "creditCardExpirationDate";

    public static final String AUTOFILL_HINT_CREDIT_CARD_EXPIRATION_MONTH =
            "creditCardExpirationMonth";

    public static final String AUTOFILL_HINT_CREDIT_CARD_EXPIRATION_YEAR =
            "creditCardExpirationYear";

    public static final String AUTOFILL_HINT_CREDIT_CARD_EXPIRATION_DAY = "creditCardExpirationDay";

    // TODO(229765029): unhide this for UI toolkit
    public static final String AUTOFILL_HINT_PASSWORD_AUTO = "passwordAuto";

    public static final String AUTOFILL_HINT_CREDENTIAL_MANAGER = "credential";

    /** @hide */
    @IntDef(prefix = { "AUTOFILL_TYPE_" }, value = {
            AUTOFILL_TYPE_NONE,
            AUTOFILL_TYPE_TEXT,
            AUTOFILL_TYPE_TOGGLE,
            AUTOFILL_TYPE_LIST,
            AUTOFILL_TYPE_DATE,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface AutofillType {}

    public static final int AUTOFILL_TYPE_NONE = 0;

    public static final int AUTOFILL_TYPE_TEXT = 1;

    public static final int AUTOFILL_TYPE_TOGGLE = 2;

    public static final int AUTOFILL_TYPE_LIST = 3;

    public static final int AUTOFILL_TYPE_DATE = 4;


    /** @hide */
    @IntDef(prefix = { "IMPORTANT_FOR_AUTOFILL_" }, value = {
            IMPORTANT_FOR_AUTOFILL_AUTO,
            IMPORTANT_FOR_AUTOFILL_YES,
            IMPORTANT_FOR_AUTOFILL_NO,
            IMPORTANT_FOR_AUTOFILL_YES_EXCLUDE_DESCENDANTS,
            IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface AutofillImportance {}

    public static final int IMPORTANT_FOR_AUTOFILL_AUTO = 0x0;

    public static final int IMPORTANT_FOR_AUTOFILL_YES = 0x1;

    public static final int IMPORTANT_FOR_AUTOFILL_NO = 0x2;

    public static final int IMPORTANT_FOR_AUTOFILL_YES_EXCLUDE_DESCENDANTS = 0x4;

    public static final int IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS = 0x8;

    /** @hide */
    @IntDef(flag = true, prefix = { "AUTOFILL_FLAG_" }, value = {
            AUTOFILL_FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface AutofillFlags {}

    public static final int AUTOFILL_FLAG_INCLUDE_NOT_IMPORTANT_VIEWS = 0x1;

    /** @hide */
    @IntDef(prefix = { "IMPORTANT_FOR_CONTENT_CAPTURE_" }, value = {
            IMPORTANT_FOR_CONTENT_CAPTURE_AUTO,
            IMPORTANT_FOR_CONTENT_CAPTURE_YES,
            IMPORTANT_FOR_CONTENT_CAPTURE_NO,
            IMPORTANT_FOR_CONTENT_CAPTURE_YES_EXCLUDE_DESCENDANTS,
            IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ContentCaptureImportance {}

    public static final int IMPORTANT_FOR_CONTENT_CAPTURE_AUTO = 0x0;

    public static final int IMPORTANT_FOR_CONTENT_CAPTURE_YES = 0x1;

    public static final int IMPORTANT_FOR_CONTENT_CAPTURE_NO = 0x2;

    public static final int IMPORTANT_FOR_CONTENT_CAPTURE_YES_EXCLUDE_DESCENDANTS = 0x4;

    public static final int IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS = 0x8;

    /** {@hide} */
    @IntDef(flag = true, prefix = {"SCROLL_CAPTURE_HINT_"},
            value = {
                    SCROLL_CAPTURE_HINT_AUTO,
                    SCROLL_CAPTURE_HINT_EXCLUDE,
                    SCROLL_CAPTURE_HINT_INCLUDE,
                    SCROLL_CAPTURE_HINT_EXCLUDE_DESCENDANTS
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ScrollCaptureHint {}

    public static final int SCROLL_CAPTURE_HINT_AUTO = 0;

    public static final int SCROLL_CAPTURE_HINT_EXCLUDE = 0x1;

    public static final int SCROLL_CAPTURE_HINT_INCLUDE = 0x2;

    public static final int SCROLL_CAPTURE_HINT_EXCLUDE_DESCENDANTS = 0x4;

    static final int ENABLED = 0x00000000;

    static final int DISABLED = 0x00000020;

    static final int ENABLED_MASK = 0x00000020;

    static final int WILL_NOT_DRAW = 0x00000080;

    static final int DRAW_MASK = 0x00000080;

    static final int SCROLLBARS_NONE = 0x00000000;

    static final int SCROLLBARS_HORIZONTAL = 0x00000100;

    static final int SCROLLBARS_VERTICAL = 0x00000200;

    static final int SCROLLBARS_MASK = 0x00000300;

    static final int FILTER_TOUCHES_WHEN_OBSCURED = 0x00000400;

    static final int OPTIONAL_FITS_SYSTEM_WINDOWS = 0x00000800;

    static final int FADING_EDGE_NONE = 0x00000000;

    static final int FADING_EDGE_HORIZONTAL = 0x00001000;

    static final int FADING_EDGE_VERTICAL = 0x00002000;

    static final int FADING_EDGE_MASK = 0x00003000;

    static final int CLICKABLE = 0x00004000;

    static final int DRAWING_CACHE_ENABLED = 0x00008000;

    static final int SAVE_DISABLED = 0x000010000;

    static final int SAVE_DISABLED_MASK = 0x000010000;

    static final int WILL_NOT_CACHE_DRAWING = 0x000020000;

    static final int FOCUSABLE_IN_TOUCH_MODE = 0x00040000;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = { "DRAWING_CACHE_QUALITY_" }, value = {
            DRAWING_CACHE_QUALITY_LOW,
            DRAWING_CACHE_QUALITY_HIGH,
            DRAWING_CACHE_QUALITY_AUTO
    })
    public @interface DrawingCacheQuality {}

    @Deprecated
    public static final int DRAWING_CACHE_QUALITY_LOW = 0x00080000;

    @Deprecated
    public static final int DRAWING_CACHE_QUALITY_HIGH = 0x00100000;

    @Deprecated
    public static final int DRAWING_CACHE_QUALITY_AUTO = 0x00000000;

    static final int DRAWING_CACHE_QUALITY_MASK = 0x00180000;

    static final int LONG_CLICKABLE = 0x00200000;

    static final int DUPLICATE_PARENT_STATE = 0x00400000;

    static final int CONTEXT_CLICKABLE = 0x00800000;

    /** @hide */
    @IntDef(prefix = { "SCROLLBARS_" }, value = {
            SCROLLBARS_INSIDE_OVERLAY,
            SCROLLBARS_INSIDE_INSET,
            SCROLLBARS_OUTSIDE_OVERLAY,
            SCROLLBARS_OUTSIDE_INSET
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ScrollBarStyle {}

    public static final int SCROLLBARS_INSIDE_OVERLAY = 0;

    public static final int SCROLLBARS_INSIDE_INSET = 0x01000000;

    public static final int SCROLLBARS_OUTSIDE_OVERLAY = 0x02000000;

    public static final int SCROLLBARS_OUTSIDE_INSET = 0x03000000;

    static final int SCROLLBARS_INSET_MASK = 0x01000000;

    static final int SCROLLBARS_OUTSIDE_MASK = 0x02000000;

    static final int SCROLLBARS_STYLE_MASK = 0x03000000;

    public static final int KEEP_SCREEN_ON = 0x04000000;

    public static final int SOUND_EFFECTS_ENABLED = 0x08000000;

    public static final int HAPTIC_FEEDBACK_ENABLED = 0x10000000;

    static final int PARENT_SAVE_DISABLED = 0x20000000;

    static final int PARENT_SAVE_DISABLED_MASK = 0x20000000;

    static final int TOOLTIP = 0x40000000;

    /** @hide */
    @IntDef(prefix = { "CONTENT_SENSITIVITY_" }, value = {
            CONTENT_SENSITIVITY_AUTO,
            CONTENT_SENSITIVITY_SENSITIVE,
            CONTENT_SENSITIVITY_NOT_SENSITIVE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ContentSensitivity {}

    public static final int CONTENT_SENSITIVITY_AUTO = 0x0;

    public static final int CONTENT_SENSITIVITY_SENSITIVE = 0x1;

    public static final int CONTENT_SENSITIVITY_NOT_SENSITIVE = 0x2;

    /** @hide */
    @IntDef(flag = true, prefix = { "FOCUSABLES_" }, value = {
            FOCUSABLES_ALL,
            FOCUSABLES_TOUCH_MODE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface FocusableMode {}

    public static final int FOCUSABLES_ALL = 0x00000000;

    public static final int FOCUSABLES_TOUCH_MODE = 0x00000001;

    /** @hide */
    @IntDef(prefix = { "FOCUS_" }, value = {
            FOCUS_BACKWARD,
            FOCUS_FORWARD,
            FOCUS_LEFT,
            FOCUS_UP,
            FOCUS_RIGHT,
            FOCUS_DOWN
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface FocusDirection {}

    /** @hide */
    @IntDef(prefix = { "FOCUS_" }, value = {
            FOCUS_LEFT,
            FOCUS_UP,
            FOCUS_RIGHT,
            FOCUS_DOWN
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface FocusRealDirection {} // Like @FocusDirection, but without forward/backward

    public static final int FOCUS_BACKWARD = 0x00000001;

    public static final int FOCUS_FORWARD = 0x00000002;

    public static final int FOCUS_LEFT = 0x00000011;

    public static final int FOCUS_UP = 0x00000021;

    public static final int FOCUS_RIGHT = 0x00000042;

    public static final int FOCUS_DOWN = 0x00000082;

    public static final int MEASURED_SIZE_MASK = 0x00ffffff;

    public static final int MEASURED_STATE_MASK = 0xff000000;

    public static final int MEASURED_HEIGHT_STATE_SHIFT = 16;

    public static final int MEASURED_STATE_TOO_SMALL = 0x01000000;

    // Singles
    protected static final int[] EMPTY_STATE_SET = new int[0];
    protected static final int[] ENABLED_STATE_SET = new int[0];
    protected static final int[] FOCUSED_STATE_SET = new int[0];
    protected static final int[] SELECTED_STATE_SET = new int[0];
    protected static final int[] PRESSED_STATE_SET = new int[0];
    protected static final int[] WINDOW_FOCUSED_STATE_SET = new int[0];
    // Doubles
    protected static final int[] ENABLED_FOCUSED_STATE_SET = new int[0];
    protected static final int[] ENABLED_SELECTED_STATE_SET = new int[0];
    protected static final int[] ENABLED_WINDOW_FOCUSED_STATE_SET = new int[0];
    protected static final int[] FOCUSED_SELECTED_STATE_SET = new int[0];
    protected static final int[] FOCUSED_WINDOW_FOCUSED_STATE_SET = new int[0];
    protected static final int[] SELECTED_WINDOW_FOCUSED_STATE_SET = new int[0];
    // Triples
    protected static final int[] ENABLED_FOCUSED_SELECTED_STATE_SET = new int[0];
    protected static final int[] ENABLED_FOCUSED_WINDOW_FOCUSED_STATE_SET = new int[0];
    protected static final int[] ENABLED_SELECTED_WINDOW_FOCUSED_STATE_SET = new int[0];
    protected static final int[] FOCUSED_SELECTED_WINDOW_FOCUSED_STATE_SET = new int[0];
    protected static final int[] ENABLED_FOCUSED_SELECTED_WINDOW_FOCUSED_STATE_SET = new int[0];
    protected static final int[] PRESSED_WINDOW_FOCUSED_STATE_SET = new int[0];
    protected static final int[] PRESSED_SELECTED_STATE_SET = new int[0];
    protected static final int[] PRESSED_SELECTED_WINDOW_FOCUSED_STATE_SET = new int[0];
    protected static final int[] PRESSED_FOCUSED_STATE_SET = new int[0];
    protected static final int[] PRESSED_FOCUSED_WINDOW_FOCUSED_STATE_SET = new int[0];
    protected static final int[] PRESSED_FOCUSED_SELECTED_STATE_SET = new int[0];
    protected static final int[] PRESSED_FOCUSED_SELECTED_WINDOW_FOCUSED_STATE_SET = new int[0];
    protected static final int[] PRESSED_ENABLED_STATE_SET = new int[0];
    protected static final int[] PRESSED_ENABLED_WINDOW_FOCUSED_STATE_SET = new int[0];
    protected static final int[] PRESSED_ENABLED_SELECTED_STATE_SET = new int[0];
    protected static final int[] PRESSED_ENABLED_SELECTED_WINDOW_FOCUSED_STATE_SET = new int[0];
    protected static final int[] PRESSED_ENABLED_FOCUSED_STATE_SET = new int[0];
    protected static final int[] PRESSED_ENABLED_FOCUSED_WINDOW_FOCUSED_STATE_SET = new int[0];
    protected static final int[] PRESSED_ENABLED_FOCUSED_SELECTED_STATE_SET = new int[0];
    protected static final int[] PRESSED_ENABLED_FOCUSED_SELECTED_WINDOW_FOCUSED_STATE_SET = new int[0];

    public static final int FRAME_RATE_CATEGORY_REASON_UNKNOWN = 0x0000_0000;

    public static final int FRAME_RATE_CATEGORY_REASON_SMALL = 0x0100_0000;

    public static final int FRAME_RATE_CATEGORY_REASON_INTERMITTENT = 0x0200_0000;

    public static final int FRAME_RATE_CATEGORY_REASON_LARGE = 0x03000000;

    public static final int FRAME_RATE_CATEGORY_REASON_REQUESTED = 0x0400_0000;

    public static final int FRAME_RATE_CATEGORY_REASON_INVALID = 0x0500_0000;

    public static final int FRAME_RATE_CATEGORY_REASON_VELOCITY = 0x0600_0000;

    public static final int FRAME_RATE_CATEGORY_REASON_BOOST = 0x0800_0000;

    public static final int FRAME_RATE_CATEGORY_REASON_TOUCH = 0x0900_0000;

    public static final int FRAME_RATE_CATEGORY_REASON_CONFLICTED = 0x0A00_0000;

    protected static boolean sToolkitSetFrameRateReadOnlyFlagValue;

    // Used to set frame rate compatibility.
    int mFrameRateCompatibility;

    static final int DEBUG_CORNERS_COLOR = Color.rgb(63, 127, 255);

    static final int DEBUG_CORNERS_SIZE_DIP = 8;

    static final ThreadLocal<Rect> sThreadLocal = ThreadLocal.withInitial(Rect::new);

    protected Animation mCurrentAnimation = null;

    @ViewDebug.ExportedProperty(category = "measurement")
    int mMeasuredWidth;

    @ViewDebug.ExportedProperty(category = "measurement")
    int mMeasuredHeight;

    boolean mRecreateDisplayList = false;

    @IdRes
    @ViewDebug.ExportedProperty(resolveId = true)
    int mID = NO_ID;
    protected Object mTag = null;

    /*
     * Masks for mPrivateFlags, as generated by dumpFlags():
     *
     * |-------|-------|-------|-------|
     *                                 1 PFLAG_WANTS_FOCUS
     *                                1  PFLAG_FOCUSED
     *                               1   PFLAG_SELECTED
     *                              1    PFLAG_IS_ROOT_NAMESPACE
     *                             1     PFLAG_HAS_BOUNDS
     *                            1      PFLAG_DRAWN
     *                           1       PFLAG_DRAW_ANIMATION
     *                          1        PFLAG_SKIP_DRAW
     *                        1          PFLAG_REQUEST_TRANSPARENT_REGIONS
     *                       1           PFLAG_DRAWABLE_STATE_DIRTY
     *                      1            PFLAG_MEASURED_DIMENSION_SET
     *                     1             PFLAG_FORCE_LAYOUT
     *                    1              PFLAG_LAYOUT_REQUIRED
     *                   1               PFLAG_PRESSED
     *                  1                PFLAG_DRAWING_CACHE_VALID
     *                 1                 PFLAG_ANIMATION_STARTED
     *                1                  PFLAG_SAVE_STATE_CALLED
     *               1                   PFLAG_ALPHA_SET
     *              1                    PFLAG_SCROLL_CONTAINER
     *             1                     PFLAG_SCROLL_CONTAINER_ADDED
     *            1                      PFLAG_DIRTY
     *            1                      PFLAG_DIRTY_MASK
     *          1                        PFLAG_OPAQUE_BACKGROUND
     *         1                         PFLAG_OPAQUE_SCROLLBARS
     *         11                        PFLAG_OPAQUE_MASK
     *        1                          PFLAG_PREPRESSED
     *       1                           PFLAG_CANCEL_NEXT_UP_EVENT
     *      1                            PFLAG_AWAKEN_SCROLL_BARS_ON_ATTACH
     *     1                             PFLAG_HOVERED
     *    1                              PFLAG_NOTIFY_AUTOFILL_MANAGER_ON_CLICK
     *   1                               PFLAG_ACTIVATED
     *  1                                PFLAG_INVALIDATED
     * |-------|-------|-------|-------|
     */
    /** {@hide} */
    static final int PFLAG_WANTS_FOCUS                 = 0x00000001;
    /** {@hide} */
    static final int PFLAG_FOCUSED                     = 0x00000002;
    /** {@hide} */
    static final int PFLAG_SELECTED                    = 0x00000004;
    /** {@hide} */
    static final int PFLAG_IS_ROOT_NAMESPACE           = 0x00000008;
    /** {@hide} */
    static final int PFLAG_HAS_BOUNDS                  = 0x00000010;
    /** {@hide} */
    static final int PFLAG_DRAWN                       = 0x00000020;
    static final int PFLAG_DRAW_ANIMATION              = 0x00000040;
    /** {@hide} */
    static final int PFLAG_SKIP_DRAW                   = 0x00000080;
    /** {@hide} */
    static final int PFLAG_REQUEST_TRANSPARENT_REGIONS = 0x00000200;
    /** {@hide} */
    static final int PFLAG_DRAWABLE_STATE_DIRTY        = 0x00000400;
    /** {@hide} */
    static final int PFLAG_MEASURED_DIMENSION_SET      = 0x00000800;
    /** {@hide} */
    static final int PFLAG_FORCE_LAYOUT                = 0x00001000;
    /** {@hide} */
    static final int PFLAG_LAYOUT_REQUIRED             = 0x00002000;

    /** {@hide} */
    static final int PFLAG_DRAWING_CACHE_VALID         = 0x00008000;
    static final int PFLAG_ANIMATION_STARTED           = 0x00010000;

    static final int PFLAG_ALPHA_SET                   = 0x00040000;

    static final int PFLAG_SCROLL_CONTAINER            = 0x00080000;

    static final int PFLAG_SCROLL_CONTAINER_ADDED      = 0x00100000;

    static final int PFLAG_DIRTY                       = 0x00200000;

    static final int PFLAG_DIRTY_MASK                  = 0x00200000;

    static final int PFLAG_OPAQUE_BACKGROUND           = 0x00800000;

    static final int PFLAG_OPAQUE_SCROLLBARS           = 0x01000000;

    static final int PFLAG_OPAQUE_MASK                 = 0x01800000;

    static final int PFLAG_CANCEL_NEXT_UP_EVENT        = 0x04000000;

    /** {@hide} */
    static final int PFLAG_ACTIVATED                   = 0x40000000;

    static final int PFLAG_INVALIDATED                 = 0x80000000;

    /* End of masks for mPrivateFlags */

    /*
     * Masks for mPrivateFlags2, as generated by dumpFlags():
     *
     * |-------|-------|-------|-------|
     *                                 1 PFLAG2_DRAG_CAN_ACCEPT
     *                                1  PFLAG2_DRAG_HOVERED
     *                              11   PFLAG2_LAYOUT_DIRECTION_MASK
     *                             1     PFLAG2_LAYOUT_DIRECTION_RESOLVED_RTL
     *                            1      PFLAG2_LAYOUT_DIRECTION_RESOLVED
     *                            11     PFLAG2_LAYOUT_DIRECTION_RESOLVED_MASK
     *                           1       PFLAG2_TEXT_DIRECTION_FLAGS[1]
     *                          1        PFLAG2_TEXT_DIRECTION_FLAGS[2]
     *                          11       PFLAG2_TEXT_DIRECTION_FLAGS[3]
     *                         1         PFLAG2_TEXT_DIRECTION_FLAGS[4]
     *                         1 1       PFLAG2_TEXT_DIRECTION_FLAGS[5]
     *                         11        PFLAG2_TEXT_DIRECTION_FLAGS[6]
     *                         111       PFLAG2_TEXT_DIRECTION_FLAGS[7]
     *                         111       PFLAG2_TEXT_DIRECTION_MASK
     *                        1          PFLAG2_TEXT_DIRECTION_RESOLVED
     *                       1           PFLAG2_TEXT_DIRECTION_RESOLVED_DEFAULT
     *                     111           PFLAG2_TEXT_DIRECTION_RESOLVED_MASK
     *                    1              PFLAG2_TEXT_ALIGNMENT_FLAGS[1]
     *                   1               PFLAG2_TEXT_ALIGNMENT_FLAGS[2]
     *                   11              PFLAG2_TEXT_ALIGNMENT_FLAGS[3]
     *                  1                PFLAG2_TEXT_ALIGNMENT_FLAGS[4]
     *                  1 1              PFLAG2_TEXT_ALIGNMENT_FLAGS[5]
     *                  11               PFLAG2_TEXT_ALIGNMENT_FLAGS[6]
     *                  111              PFLAG2_TEXT_ALIGNMENT_MASK
     *                 1                 PFLAG2_TEXT_ALIGNMENT_RESOLVED
     *                1                  PFLAG2_TEXT_ALIGNMENT_RESOLVED_DEFAULT
     *              111                  PFLAG2_TEXT_ALIGNMENT_RESOLVED_MASK
     *           111                     PFLAG2_IMPORTANT_FOR_ACCESSIBILITY_MASK
     *         11                        PFLAG2_ACCESSIBILITY_LIVE_REGION_MASK
     *       1                           PFLAG2_ACCESSIBILITY_FOCUSED
     *      1                            PFLAG2_SUBTREE_ACCESSIBILITY_STATE_CHANGED
     *     1                             PFLAG2_VIEW_QUICK_REJECTED
     *    1                              PFLAG2_PADDING_RESOLVED
     *   1                               PFLAG2_DRAWABLE_RESOLVED
     *  1                                PFLAG2_HAS_TRANSIENT_STATE
     * |-------|-------|-------|-------|
     */

    static final int PFLAG2_DRAG_CAN_ACCEPT            = 0x00000001;

    static final int PFLAG2_DRAG_HOVERED               = 0x00000002;

    /** @hide */
    @IntDef(prefix = { "LAYOUT_DIRECTION_" }, value = {
            LAYOUT_DIRECTION_LTR,
            LAYOUT_DIRECTION_RTL,
            LAYOUT_DIRECTION_INHERIT,
            LAYOUT_DIRECTION_LOCALE
    })
    @Retention(RetentionPolicy.SOURCE)
    // Not called LayoutDirection to avoid conflict with android.util.LayoutDirection
    public @interface LayoutDir {}

    /** @hide */
    @IntDef(prefix = { "LAYOUT_DIRECTION_" }, value = {
            LAYOUT_DIRECTION_LTR,
            LAYOUT_DIRECTION_RTL
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ResolvedLayoutDir {}

    public static final int LAYOUT_DIRECTION_UNDEFINED = 0;

    public static final int LAYOUT_DIRECTION_LTR = LayoutDirection.LTR;

    public static final int LAYOUT_DIRECTION_RTL = LayoutDirection.RTL;

    public static final int LAYOUT_DIRECTION_INHERIT = LayoutDirection.INHERIT;

    public static final int LAYOUT_DIRECTION_LOCALE = LayoutDirection.LOCALE;

    static final int PFLAG2_LAYOUT_DIRECTION_MASK_SHIFT = 2;

    static final int PFLAG2_LAYOUT_DIRECTION_MASK = 0x00000003 << PFLAG2_LAYOUT_DIRECTION_MASK_SHIFT;

    static final int PFLAG2_LAYOUT_DIRECTION_RESOLVED_RTL = 4 << PFLAG2_LAYOUT_DIRECTION_MASK_SHIFT;

    static final int PFLAG2_LAYOUT_DIRECTION_RESOLVED = 8 << PFLAG2_LAYOUT_DIRECTION_MASK_SHIFT;

    static final int PFLAG2_LAYOUT_DIRECTION_RESOLVED_MASK = 0x0000000C
            << PFLAG2_LAYOUT_DIRECTION_MASK_SHIFT;

    static final int LAYOUT_DIRECTION_RESOLVED_DEFAULT = LAYOUT_DIRECTION_LTR;

    public static final int TEXT_DIRECTION_INHERIT = 0;

    public static final int TEXT_DIRECTION_FIRST_STRONG = 1;

    public static final int TEXT_DIRECTION_ANY_RTL = 2;

    public static final int TEXT_DIRECTION_LTR = 3;

    public static final int TEXT_DIRECTION_RTL = 4;

    public static final int TEXT_DIRECTION_LOCALE = 5;

    public static final int TEXT_DIRECTION_FIRST_STRONG_LTR = 6;

    public static final int TEXT_DIRECTION_FIRST_STRONG_RTL = 7;

    static final int TEXT_DIRECTION_RESOLVED_DEFAULT = TEXT_DIRECTION_FIRST_STRONG;

    static final int PFLAG2_TEXT_DIRECTION_MASK_SHIFT = 6;

    static final int PFLAG2_TEXT_DIRECTION_MASK = 0x00000007
            << PFLAG2_TEXT_DIRECTION_MASK_SHIFT;

    static final int PFLAG2_TEXT_DIRECTION_RESOLVED = 0x00000008
            << PFLAG2_TEXT_DIRECTION_MASK_SHIFT;

    static final int PFLAG2_TEXT_DIRECTION_RESOLVED_MASK_SHIFT = 10;

    static final int PFLAG2_TEXT_DIRECTION_RESOLVED_MASK = 0x00000007
            << PFLAG2_TEXT_DIRECTION_RESOLVED_MASK_SHIFT;

    static final int PFLAG2_TEXT_DIRECTION_RESOLVED_DEFAULT =
            TEXT_DIRECTION_RESOLVED_DEFAULT << PFLAG2_TEXT_DIRECTION_RESOLVED_MASK_SHIFT;

    /** @hide */
    @IntDef(prefix = { "TEXT_ALIGNMENT_" }, value = {
            TEXT_ALIGNMENT_INHERIT,
            TEXT_ALIGNMENT_GRAVITY,
            TEXT_ALIGNMENT_CENTER,
            TEXT_ALIGNMENT_TEXT_START,
            TEXT_ALIGNMENT_TEXT_END,
            TEXT_ALIGNMENT_VIEW_START,
            TEXT_ALIGNMENT_VIEW_END
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface TextAlignment {}

    public static final int TEXT_ALIGNMENT_INHERIT = 0;

    public static final int TEXT_ALIGNMENT_GRAVITY = 1;

    public static final int TEXT_ALIGNMENT_TEXT_START = 2;

    public static final int TEXT_ALIGNMENT_TEXT_END = 3;

    public static final int TEXT_ALIGNMENT_CENTER = 4;

    public static final int TEXT_ALIGNMENT_VIEW_START = 5;

    public static final int TEXT_ALIGNMENT_VIEW_END = 6;

    static final int TEXT_ALIGNMENT_RESOLVED_DEFAULT = TEXT_ALIGNMENT_GRAVITY;

    static final int PFLAG2_TEXT_ALIGNMENT_MASK_SHIFT = 13;

    static final int PFLAG2_TEXT_ALIGNMENT_MASK = 0x00000007 << PFLAG2_TEXT_ALIGNMENT_MASK_SHIFT;

    static final int PFLAG2_TEXT_ALIGNMENT_RESOLVED = 0x00000008 << PFLAG2_TEXT_ALIGNMENT_MASK_SHIFT;

    static final int PFLAG2_TEXT_ALIGNMENT_RESOLVED_MASK_SHIFT = 17;

    static final int PFLAG2_TEXT_ALIGNMENT_RESOLVED_MASK = 0x00000007
            << PFLAG2_TEXT_ALIGNMENT_RESOLVED_MASK_SHIFT;

    // Accessiblity constants for mPrivateFlags2

    static final int PFLAG2_IMPORTANT_FOR_ACCESSIBILITY_SHIFT = 20;

    public static final int IMPORTANT_FOR_ACCESSIBILITY_AUTO = 0x00000000;

    public static final int IMPORTANT_FOR_ACCESSIBILITY_YES = 0x00000001;

    public static final int IMPORTANT_FOR_ACCESSIBILITY_NO = 0x00000002;

    public static final int IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS = 0x00000004;

    static final int IMPORTANT_FOR_ACCESSIBILITY_DEFAULT = IMPORTANT_FOR_ACCESSIBILITY_AUTO;

    public static final int ACCESSIBILITY_DATA_SENSITIVE_AUTO = 0x00000000;

    public static final int ACCESSIBILITY_DATA_SENSITIVE_YES = 0x00000001;

    public static final int ACCESSIBILITY_DATA_SENSITIVE_NO = 0x00000002;

    /** @hide */
    @IntDef(prefix = { "ACCESSIBILITY_DATA_SENSITIVE_" }, value = {
            ACCESSIBILITY_DATA_SENSITIVE_AUTO,
            ACCESSIBILITY_DATA_SENSITIVE_YES,
            ACCESSIBILITY_DATA_SENSITIVE_NO,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface AccessibilityDataSensitive {}

    static final int PFLAG2_IMPORTANT_FOR_ACCESSIBILITY_MASK = (IMPORTANT_FOR_ACCESSIBILITY_AUTO
        | IMPORTANT_FOR_ACCESSIBILITY_YES | IMPORTANT_FOR_ACCESSIBILITY_NO
        | IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
        << PFLAG2_IMPORTANT_FOR_ACCESSIBILITY_SHIFT;

    static final int PFLAG2_ACCESSIBILITY_LIVE_REGION_SHIFT = 23;

    public static final int ACCESSIBILITY_LIVE_REGION_NONE = 0x00000000;

    public static final int ACCESSIBILITY_LIVE_REGION_POLITE = 0x00000001;

    public static final int ACCESSIBILITY_LIVE_REGION_ASSERTIVE = 0x00000002;

    static final int ACCESSIBILITY_LIVE_REGION_DEFAULT = ACCESSIBILITY_LIVE_REGION_NONE;

    static final int PFLAG2_ACCESSIBILITY_LIVE_REGION_MASK = (ACCESSIBILITY_LIVE_REGION_NONE
            | ACCESSIBILITY_LIVE_REGION_POLITE | ACCESSIBILITY_LIVE_REGION_ASSERTIVE)
            << PFLAG2_ACCESSIBILITY_LIVE_REGION_SHIFT;

    static final int PFLAG2_ACCESSIBILITY_FOCUSED = 0x04000000;

    static final int PFLAG2_SUBTREE_ACCESSIBILITY_STATE_CHANGED = 0x08000000;

    static final int PFLAG2_VIEW_QUICK_REJECTED = 0x10000000;

    static final int PFLAG2_PADDING_RESOLVED = 0x20000000;

    static final int PFLAG2_DRAWABLE_RESOLVED = 0x40000000;

    static final int PFLAG2_HAS_TRANSIENT_STATE = 0x80000000;

    static final int ALL_RTL_PROPERTIES_RESOLVED = PFLAG2_LAYOUT_DIRECTION_RESOLVED |
            PFLAG2_TEXT_DIRECTION_RESOLVED |
            PFLAG2_TEXT_ALIGNMENT_RESOLVED |
            PFLAG2_PADDING_RESOLVED |
            PFLAG2_DRAWABLE_RESOLVED;

    // There are a couple of flags left in mPrivateFlags2

    /* End of masks for mPrivateFlags2 */

    /*
     * Masks for mPrivateFlags3, as generated by dumpFlags():
     *
     * |-------|-------|-------|-------|
     *                                 1 PFLAG3_VIEW_IS_ANIMATING_TRANSFORM
     *                                1  PFLAG3_VIEW_IS_ANIMATING_ALPHA
     *                               1   PFLAG3_IS_LAID_OUT
     *                              1    PFLAG3_MEASURE_NEEDED_BEFORE_LAYOUT
     *                             1     PFLAG3_CALLED_SUPER
     *                            1      PFLAG3_APPLYING_INSETS
     *                           1       PFLAG3_FITTING_SYSTEM_WINDOWS
     *                          1        PFLAG3_NESTED_SCROLLING_ENABLED
     *                         1         PFLAG3_SCROLL_INDICATOR_TOP
     *                        1          PFLAG3_SCROLL_INDICATOR_BOTTOM
     *                       1           PFLAG3_SCROLL_INDICATOR_LEFT
     *                      1            PFLAG3_SCROLL_INDICATOR_RIGHT
     *                     1             PFLAG3_SCROLL_INDICATOR_START
     *                    1              PFLAG3_SCROLL_INDICATOR_END
     *                   1               PFLAG3_ASSIST_BLOCKED
     *                  1                PFLAG3_CLUSTER
     *                 1                 PFLAG3_IS_AUTOFILLED
     *                1                  PFLAG3_FINGER_DOWN
     *               1                   PFLAG3_FOCUSED_BY_DEFAULT
     *           1111                    PFLAG3_IMPORTANT_FOR_AUTOFILL
     *          1                        PFLAG3_OVERLAPPING_RENDERING_FORCED_VALUE
     *         1                         PFLAG3_HAS_OVERLAPPING_RENDERING_FORCED
     *        1                          PFLAG3_TEMPORARY_DETACH
     *       1                           PFLAG3_NO_REVEAL_ON_FOCUS
     *      1                            PFLAG3_NOTIFY_AUTOFILL_ENTER_ON_LAYOUT
     *     1                             PFLAG3_SCREEN_READER_FOCUSABLE
     *    1                              PFLAG3_AGGREGATED_VISIBLE
     *   1                               PFLAG3_AUTOFILLID_EXPLICITLY_SET
     *  1                                PFLAG3_ACCESSIBILITY_HEADING
     * |-------|-------|-------|-------|
     */

    static final int PFLAG3_VIEW_IS_ANIMATING_TRANSFORM = 0x1;

    static final int PFLAG3_VIEW_IS_ANIMATING_ALPHA = 0x2;

    static final int PFLAG3_IS_LAID_OUT = 0x4;

    static final int PFLAG3_MEASURE_NEEDED_BEFORE_LAYOUT = 0x8;

    static final int PFLAG3_CALLED_SUPER = 0x10;

    static final int PFLAG3_APPLYING_INSETS = 0x20;

    static final int PFLAG3_FITTING_SYSTEM_WINDOWS = 0x40;

    static final int PFLAG3_NESTED_SCROLLING_ENABLED = 0x80;

    static final int PFLAG3_SCROLL_INDICATOR_TOP = 0x0100;

    static final int PFLAG3_SCROLL_INDICATOR_BOTTOM = 0x0200;

    static final int PFLAG3_SCROLL_INDICATOR_LEFT = 0x0400;

    static final int PFLAG3_SCROLL_INDICATOR_RIGHT = 0x0800;

    static final int PFLAG3_SCROLL_INDICATOR_START = 0x1000;

    static final int PFLAG3_SCROLL_INDICATOR_END = 0x2000;

    static final int DRAG_MASK = PFLAG2_DRAG_CAN_ACCEPT | PFLAG2_DRAG_HOVERED;

    static final int SCROLL_INDICATORS_NONE = 0x0000;

    static final int SCROLL_INDICATORS_PFLAG3_MASK = PFLAG3_SCROLL_INDICATOR_TOP
            | PFLAG3_SCROLL_INDICATOR_BOTTOM | PFLAG3_SCROLL_INDICATOR_LEFT
            | PFLAG3_SCROLL_INDICATOR_RIGHT | PFLAG3_SCROLL_INDICATOR_START
            | PFLAG3_SCROLL_INDICATOR_END;

    static final int SCROLL_INDICATORS_TO_PFLAGS3_LSHIFT = 8;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true, prefix = { "SCROLL_INDICATOR_" }, value = {
            SCROLL_INDICATOR_TOP,
            SCROLL_INDICATOR_BOTTOM,
            SCROLL_INDICATOR_LEFT,
            SCROLL_INDICATOR_RIGHT,
            SCROLL_INDICATOR_START,
            SCROLL_INDICATOR_END,
    })
    public @interface ScrollIndicators {}

    public static final int SCROLL_INDICATOR_TOP =
            PFLAG3_SCROLL_INDICATOR_TOP >> SCROLL_INDICATORS_TO_PFLAGS3_LSHIFT;

    public static final int SCROLL_INDICATOR_BOTTOM =
            PFLAG3_SCROLL_INDICATOR_BOTTOM >> SCROLL_INDICATORS_TO_PFLAGS3_LSHIFT;

    public static final int SCROLL_INDICATOR_LEFT =
            PFLAG3_SCROLL_INDICATOR_LEFT >> SCROLL_INDICATORS_TO_PFLAGS3_LSHIFT;

    public static final int SCROLL_INDICATOR_RIGHT =
            PFLAG3_SCROLL_INDICATOR_RIGHT >> SCROLL_INDICATORS_TO_PFLAGS3_LSHIFT;

    public static final int SCROLL_INDICATOR_START =
            PFLAG3_SCROLL_INDICATOR_START >> SCROLL_INDICATORS_TO_PFLAGS3_LSHIFT;

    public static final int SCROLL_INDICATOR_END =
            PFLAG3_SCROLL_INDICATOR_END >> SCROLL_INDICATORS_TO_PFLAGS3_LSHIFT;

    static final int PFLAG3_ASSIST_BLOCKED = 0x4000;

    static final int PFLAG3_IMPORTANT_FOR_AUTOFILL_SHIFT = 19;

    static final int PFLAG3_IMPORTANT_FOR_AUTOFILL_MASK = (IMPORTANT_FOR_AUTOFILL_AUTO
            | IMPORTANT_FOR_AUTOFILL_YES | IMPORTANT_FOR_AUTOFILL_NO
            | IMPORTANT_FOR_AUTOFILL_YES_EXCLUDE_DESCENDANTS
            | IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS)
            << PFLAG3_IMPORTANT_FOR_AUTOFILL_SHIFT;

    static final int PFLAG3_TEMPORARY_DETACH = 0x2000000;

    static final int PFLAG3_NOTIFY_AUTOFILL_ENTER_ON_LAYOUT = 0x8000000;

    /* End of masks for mPrivateFlags3 */

    /*
     * Masks for mPrivateFlags4, as generated by dumpFlags():
     *
     * |-------|-------|-------|-------|
     *                             1111 PFLAG4_IMPORTANT_FOR_CONTENT_CAPTURE_MASK
     *                            1     PFLAG4_NOTIFIED_CONTENT_CAPTURE_APPEARED
     *                           1      PFLAG4_NOTIFIED_CONTENT_CAPTURE_DISAPPEARED
     *                          1       PFLAG4_CONTENT_CAPTURE_IMPORTANCE_IS_CACHED
     *                         1        PFLAG4_CONTENT_CAPTURE_IMPORTANCE_CACHED_VALUE
     *                         11       PFLAG4_CONTENT_CAPTURE_IMPORTANCE_MASK
     *                        1         PFLAG4_FRAMEWORK_OPTIONAL_FITS_SYSTEM_WINDOWS
     *                       1          PFLAG4_AUTOFILL_HIDE_HIGHLIGHT
     *                     11           PFLAG4_SCROLL_CAPTURE_HINT_MASK
     *                    1             PFLAG4_ALLOW_CLICK_WHEN_DISABLED
     *                   1              PFLAG4_DETACHED
     *                  1               PFLAG4_HAS_TRANSLATION_TRANSIENT_STATE
     *                 1                PFLAG4_DRAG_A11Y_STARTED
     *                1                 PFLAG4_AUTO_HANDWRITING_INITIATION_ENABLED
     *               1                  PFLAG4_IMPORTANT_FOR_CREDENTIAL_MANAGER
     *              1                   PFLAG4_TRAVERSAL_TRACING_ENABLED
     *             1                    PFLAG4_RELAYOUT_TRACING_ENABLED
     *            1                     PFLAG4_ROTARY_HAPTICS_DETERMINED
     *           1                      PFLAG4_ROTARY_HAPTICS_ENABLED
     *          1                       PFLAG4_ROTARY_HAPTICS_SCROLL_SINCE_LAST_ROTARY_INPUT
     *         1                        PFLAG4_ROTARY_HAPTICS_WAITING_FOR_SCROLL_EVENT
     *       11                         PFLAG4_CONTENT_SENSITIVITY_MASK
     *      1                           PFLAG4_IS_COUNTED_AS_SENSITIVE
     *     1                            PFLAG4_HAS_DRAWN
     *    1                             PFLAG4_HAS_MOVED
     *   1                              PFLAG4_HAS_VIEW_PROPERTY_INVALIDATION
     *  1                               PFLAG4_FORCED_OVERRIDE_FRAME_RATE
     * 1                                PFLAG4_SELF_REQUESTED_FRAME_RATE
     * |-------|-------|-------|-------|
     */

    static final int PFLAG4_FRAMEWORK_OPTIONAL_FITS_SYSTEM_WINDOWS = 0x000000100;

    static final int PFLAG4_SCROLL_CAPTURE_HINT_SHIFT = 10;

    static final int PFLAG4_SCROLL_CAPTURE_HINT_MASK = (SCROLL_CAPTURE_HINT_INCLUDE
            | SCROLL_CAPTURE_HINT_EXCLUDE | SCROLL_CAPTURE_HINT_EXCLUDE_DESCENDANTS)
            << PFLAG4_SCROLL_CAPTURE_HINT_SHIFT;

    /* End of masks for mPrivateFlags4 */

    /** @hide */
    protected static final int VIEW_STRUCTURE_FOR_ASSIST = 0;
    /** @hide */
    protected  static final int VIEW_STRUCTURE_FOR_AUTOFILL = 1;
    /** @hide */
    protected  static final int VIEW_STRUCTURE_FOR_CONTENT_CAPTURE = 2;

    /** @hide */
    @IntDef(flag = true, prefix = { "VIEW_STRUCTURE_FOR" }, value = {
            VIEW_STRUCTURE_FOR_ASSIST,
            VIEW_STRUCTURE_FOR_AUTOFILL,
            VIEW_STRUCTURE_FOR_CONTENT_CAPTURE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ViewStructureType {}

    public static final int OVER_SCROLL_ALWAYS = 0;

    public static final int OVER_SCROLL_IF_CONTENT_SCROLLS = 1;

    public static final int OVER_SCROLL_NEVER = 2;

    @Deprecated
    public static final int SYSTEM_UI_FLAG_VISIBLE = 0;

    @Deprecated
    public static final int SYSTEM_UI_FLAG_LOW_PROFILE = 0x00000001;

    @Deprecated
    public static final int SYSTEM_UI_FLAG_HIDE_NAVIGATION = 0x00000002;

    @Deprecated
    public static final int SYSTEM_UI_FLAG_FULLSCREEN = 0x00000004;

    @Deprecated
    public static final int SYSTEM_UI_FLAG_LAYOUT_STABLE = 0x00000100;

    public static final int SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION = 0x00000200;

    @Deprecated
    public static final int SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN = 0x00000400;

    @Deprecated
    public static final int SYSTEM_UI_FLAG_IMMERSIVE = 0x00000800;

    @Deprecated
    public static final int SYSTEM_UI_FLAG_IMMERSIVE_STICKY = 0x00001000;

    @Deprecated
    public static final int SYSTEM_UI_FLAG_LIGHT_STATUS_BAR = 0x00002000;

    @Deprecated
    public static final int SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR = 0x00000010;

    @Deprecated
    public static final int STATUS_BAR_HIDDEN = SYSTEM_UI_FLAG_LOW_PROFILE;

    @Deprecated
    public static final int STATUS_BAR_VISIBLE = SYSTEM_UI_FLAG_VISIBLE;

    public static final int STATUS_BAR_DISABLE_EXPAND = 0x00010000;

    public static final int STATUS_BAR_DISABLE_NOTIFICATION_ICONS = 0x00020000;

    public static final int STATUS_BAR_DISABLE_NOTIFICATION_ALERTS = 0x00040000;

    public static final int STATUS_BAR_DISABLE_NOTIFICATION_TICKER = 0x00080000;

    public static final int STATUS_BAR_DISABLE_SYSTEM_INFO = 0x00100000;

    public static final int STATUS_BAR_DISABLE_HOME = 0x00200000;

    public static final int STATUS_BAR_DISABLE_BACK = 0x00400000;

    public static final int STATUS_BAR_DISABLE_CLOCK = 0x00800000;

    public static final int STATUS_BAR_DISABLE_RECENT = 0x01000000;

    public static final int STATUS_BAR_DISABLE_SEARCH = 0x02000000;

    public static final int STATUS_BAR_DISABLE_ONGOING_CALL_CHIP = 0x04000000;

    public static final int PUBLIC_STATUS_BAR_VISIBILITY_MASK = 0x00003FF7;

    public static final int SYSTEM_UI_CLEARABLE_FLAGS =
            SYSTEM_UI_FLAG_LOW_PROFILE | SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | SYSTEM_UI_FLAG_FULLSCREEN;

    @Deprecated
    public static final int SYSTEM_UI_LAYOUT_FLAGS =
            SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;

    /** @hide */
    @IntDef(flag = true, prefix = { "FIND_VIEWS_" }, value = {
            FIND_VIEWS_WITH_TEXT,
            FIND_VIEWS_WITH_CONTENT_DESCRIPTION
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface FindViewFlags {}

    public static final int FIND_VIEWS_WITH_TEXT = 0x00000001;

    public static final int FIND_VIEWS_WITH_CONTENT_DESCRIPTION = 0x00000002;

    public static final int FIND_VIEWS_WITH_ACCESSIBILITY_NODE_PROVIDERS = 0x00000004;

    public static final int ACCESSIBILITY_CURSOR_POSITION_UNDEFINED = -1;

    public static final int SCREEN_STATE_OFF = 0x0;

    public static final int SCREEN_STATE_ON = 0x1;

    public static final int SCROLL_AXIS_NONE = 0;

    public static final int SCROLL_AXIS_HORIZONTAL = 1 << 0;

    public static final int SCROLL_AXIS_VERTICAL = 1 << 1;

    protected ViewParent mParent;

    // AttachInfo mAttachInfo;

    @ViewDebug.ExportedProperty(flagMapping = {
        @ViewDebug.FlagToString(mask = PFLAG_FORCE_LAYOUT, equals = PFLAG_FORCE_LAYOUT,
                name = "FORCE_LAYOUT"),
        @ViewDebug.FlagToString(mask = PFLAG_LAYOUT_REQUIRED, equals = PFLAG_LAYOUT_REQUIRED,
                name = "LAYOUT_REQUIRED"),
        @ViewDebug.FlagToString(mask = PFLAG_DRAWING_CACHE_VALID, equals = PFLAG_DRAWING_CACHE_VALID,
            name = "DRAWING_CACHE_INVALID", outputIf = false),
        @ViewDebug.FlagToString(mask = PFLAG_DRAWN, equals = PFLAG_DRAWN, name = "DRAWN", outputIf = true),
        @ViewDebug.FlagToString(mask = PFLAG_DRAWN, equals = PFLAG_DRAWN, name = "NOT_DRAWN", outputIf = false),
        @ViewDebug.FlagToString(mask = PFLAG_DIRTY_MASK, equals = PFLAG_DIRTY, name = "DIRTY")
    }, formatToHexString = true)

    /* @hide */
    public int mPrivateFlags;
    int mPrivateFlags2;
    int mPrivateFlags3;

    @ViewDebug.ExportedProperty(flagMapping = {
            @ViewDebug.FlagToString(mask = SYSTEM_UI_FLAG_LOW_PROFILE,
                    equals = SYSTEM_UI_FLAG_LOW_PROFILE,
                    name = "LOW_PROFILE"),
            @ViewDebug.FlagToString(mask = SYSTEM_UI_FLAG_HIDE_NAVIGATION,
                    equals = SYSTEM_UI_FLAG_HIDE_NAVIGATION,
                    name = "HIDE_NAVIGATION"),
            @ViewDebug.FlagToString(mask = SYSTEM_UI_FLAG_FULLSCREEN,
                    equals = SYSTEM_UI_FLAG_FULLSCREEN,
                    name = "FULLSCREEN"),
            @ViewDebug.FlagToString(mask = SYSTEM_UI_FLAG_LAYOUT_STABLE,
                    equals = SYSTEM_UI_FLAG_LAYOUT_STABLE,
                    name = "LAYOUT_STABLE"),
            @ViewDebug.FlagToString(mask = SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION,
                    equals = SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION,
                    name = "LAYOUT_HIDE_NAVIGATION"),
            @ViewDebug.FlagToString(mask = SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN,
                    equals = SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN,
                    name = "LAYOUT_FULLSCREEN"),
            @ViewDebug.FlagToString(mask = SYSTEM_UI_FLAG_IMMERSIVE,
                    equals = SYSTEM_UI_FLAG_IMMERSIVE,
                    name = "IMMERSIVE"),
            @ViewDebug.FlagToString(mask = SYSTEM_UI_FLAG_IMMERSIVE_STICKY,
                    equals = SYSTEM_UI_FLAG_IMMERSIVE_STICKY,
                    name = "IMMERSIVE_STICKY"),
            @ViewDebug.FlagToString(mask = SYSTEM_UI_FLAG_LIGHT_STATUS_BAR,
                    equals = SYSTEM_UI_FLAG_LIGHT_STATUS_BAR,
                    name = "LIGHT_STATUS_BAR"),
            @ViewDebug.FlagToString(mask = SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR,
                    equals = SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR,
                    name = "LIGHT_NAVIGATION_BAR"),
            @ViewDebug.FlagToString(mask = STATUS_BAR_DISABLE_EXPAND,
                    equals = STATUS_BAR_DISABLE_EXPAND,
                    name = "STATUS_BAR_DISABLE_EXPAND"),
            @ViewDebug.FlagToString(mask = STATUS_BAR_DISABLE_NOTIFICATION_ICONS,
                    equals = STATUS_BAR_DISABLE_NOTIFICATION_ICONS,
                    name = "STATUS_BAR_DISABLE_NOTIFICATION_ICONS"),
            @ViewDebug.FlagToString(mask = STATUS_BAR_DISABLE_NOTIFICATION_ALERTS,
                    equals = STATUS_BAR_DISABLE_NOTIFICATION_ALERTS,
                    name = "STATUS_BAR_DISABLE_NOTIFICATION_ALERTS"),
            @ViewDebug.FlagToString(mask = STATUS_BAR_DISABLE_NOTIFICATION_TICKER,
                    equals = STATUS_BAR_DISABLE_NOTIFICATION_TICKER,
                    name = "STATUS_BAR_DISABLE_NOTIFICATION_TICKER"),
            @ViewDebug.FlagToString(mask = STATUS_BAR_DISABLE_SYSTEM_INFO,
                    equals = STATUS_BAR_DISABLE_SYSTEM_INFO,
                    name = "STATUS_BAR_DISABLE_SYSTEM_INFO"),
            @ViewDebug.FlagToString(mask = STATUS_BAR_DISABLE_HOME,
                    equals = STATUS_BAR_DISABLE_HOME,
                    name = "STATUS_BAR_DISABLE_HOME"),
            @ViewDebug.FlagToString(mask = STATUS_BAR_DISABLE_BACK,
                    equals = STATUS_BAR_DISABLE_BACK,
                    name = "STATUS_BAR_DISABLE_BACK"),
            @ViewDebug.FlagToString(mask = STATUS_BAR_DISABLE_CLOCK,
                    equals = STATUS_BAR_DISABLE_CLOCK,
                    name = "STATUS_BAR_DISABLE_CLOCK"),
            @ViewDebug.FlagToString(mask = STATUS_BAR_DISABLE_RECENT,
                    equals = STATUS_BAR_DISABLE_RECENT,
                    name = "STATUS_BAR_DISABLE_RECENT"),
            @ViewDebug.FlagToString(mask = STATUS_BAR_DISABLE_SEARCH,
                    equals = STATUS_BAR_DISABLE_SEARCH,
                    name = "STATUS_BAR_DISABLE_SEARCH"),
            @ViewDebug.FlagToString(mask = STATUS_BAR_DISABLE_ONGOING_CALL_CHIP,
                    equals = STATUS_BAR_DISABLE_ONGOING_CALL_CHIP,
                    name = "STATUS_BAR_DISABLE_ONGOING_CALL_CHIP")
    }, formatToHexString = true)
    @SystemUiVisibility
    int mSystemUiVisibility;

    @IntDef(flag = true, prefix = "", value = {
            SYSTEM_UI_FLAG_LOW_PROFILE,
            SYSTEM_UI_FLAG_HIDE_NAVIGATION,
            SYSTEM_UI_FLAG_FULLSCREEN,
            SYSTEM_UI_FLAG_LAYOUT_STABLE,
            SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION,
            SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN,
            SYSTEM_UI_FLAG_IMMERSIVE,
            SYSTEM_UI_FLAG_IMMERSIVE_STICKY,
            SYSTEM_UI_FLAG_LIGHT_STATUS_BAR,
            SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR,
            STATUS_BAR_DISABLE_EXPAND,
            STATUS_BAR_DISABLE_NOTIFICATION_ICONS,
            STATUS_BAR_DISABLE_NOTIFICATION_ALERTS,
            STATUS_BAR_DISABLE_NOTIFICATION_TICKER,
            STATUS_BAR_DISABLE_SYSTEM_INFO,
            STATUS_BAR_DISABLE_HOME,
            STATUS_BAR_DISABLE_BACK,
            STATUS_BAR_DISABLE_CLOCK,
            STATUS_BAR_DISABLE_RECENT,
            STATUS_BAR_DISABLE_SEARCH,
            STATUS_BAR_DISABLE_ONGOING_CALL_CHIP,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SystemUiVisibility {}

    int mTransientStateCount = 0;

    int mWindowAttachCount;

    protected ViewGroup.LayoutParams mLayoutParams;

    @ViewDebug.ExportedProperty(formatToHexString = true)
    int mViewFlags;

    static class TransformationInfo {
        float mTransitionAlpha = 1f;
    }

    /** @hide */
    public TransformationInfo mTransformationInfo;

    @ViewDebug.ExportedProperty(category = "drawing")
    Rect mClipBounds = null;

    @ViewDebug.ExportedProperty(category = "layout")
    protected int mLeft;
    @ViewDebug.ExportedProperty(category = "layout")
    protected int mRight;
    @ViewDebug.ExportedProperty(category = "layout")
    protected int mTop;
    @ViewDebug.ExportedProperty(category = "layout")
    protected int mBottom;

    @ViewDebug.ExportedProperty(category = "scrolling")
    protected int mScrollX;
    @ViewDebug.ExportedProperty(category = "scrolling")
    protected int mScrollY;

    @ViewDebug.ExportedProperty(category = "padding")
    protected int mPaddingLeft = 0;
    @ViewDebug.ExportedProperty(category = "padding")
    protected int mPaddingRight = 0;
    @ViewDebug.ExportedProperty(category = "padding")
    protected int mPaddingTop;
    @ViewDebug.ExportedProperty(category = "padding")
    protected int mPaddingBottom;

    @ViewDebug.ExportedProperty(category = "padding")
    protected int mUserPaddingRight;

    @ViewDebug.ExportedProperty(category = "padding")
    protected int mUserPaddingBottom;

    @ViewDebug.ExportedProperty(category = "padding")
    protected int mUserPaddingLeft;

    @ViewDebug.ExportedProperty(category = "padding")
    int mUserPaddingStart;

    @ViewDebug.ExportedProperty(category = "padding")
    int mUserPaddingEnd;

    int mUserPaddingLeftInitial;

    int mUserPaddingRightInitial;

    int mOldWidthMeasureSpec = Integer.MIN_VALUE;
    int mOldHeightMeasureSpec = Integer.MIN_VALUE;

    RenderNode mBackgroundRenderNode;

    static class TintInfo {
        ColorStateList mTintList;
        BlendMode mBlendMode;
        boolean mHasTintMode;
        boolean mHasTintList;
    }

    // ListenerInfo mListenerInfo;

    // TooltipInfo mTooltipInfo;

    @ViewDebug.ExportedProperty(deepExport = true)
    protected Context mContext;

    ViewOutlineProvider mOutlineProvider = ViewOutlineProvider.BACKGROUND;

    int mNextFocusForwardId = View.NO_ID;

    int mNextClusterForwardId = View.NO_ID;

    boolean mDefaultFocusHighlightEnabled = true;

    public static final int DRAG_FLAG_GLOBAL = 1 << 8;  // 256

    public static final int DRAG_FLAG_GLOBAL_URI_READ = Intent.FLAG_GRANT_READ_URI_PERMISSION;

    public static final int DRAG_FLAG_GLOBAL_URI_WRITE = Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

    public static final int DRAG_FLAG_GLOBAL_PERSISTABLE_URI_PERMISSION =
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION;

    public static final int DRAG_FLAG_GLOBAL_PREFIX_URI_PERMISSION =
            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;

    public static final int DRAG_FLAG_OPAQUE = 1 << 9;

    public static final int DRAG_FLAG_ACCESSIBILITY_ACTION = 1 << 10;

    public static final int DRAG_FLAG_REQUEST_SURFACE_FOR_RETURN_ANIMATION = 1 << 11;

    public static final int DRAG_FLAG_GLOBAL_SAME_APPLICATION = 1 << 12;

    public static final int DRAG_FLAG_START_INTENT_SENDER_ON_UNHANDLED_DRAG = 1 << 13;

    public static final int DRAG_FLAG_HIDE_CALLING_TASK_ON_DRAG_START = 1 << 14;

    public static final int SCROLLBAR_POSITION_DEFAULT = 0;

    public static final int SCROLLBAR_POSITION_LEFT = 1;

    public static final int SCROLLBAR_POSITION_RIGHT = 2;

    public static final int LAYER_TYPE_NONE = 0;

    public static final int LAYER_TYPE_SOFTWARE = 1;

    public static final int LAYER_TYPE_HARDWARE = 2;

    /** @hide */
    @IntDef(prefix = { "LAYER_TYPE_" }, value = {
            LAYER_TYPE_NONE,
            LAYER_TYPE_SOFTWARE,
            LAYER_TYPE_HARDWARE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface LayerType {}

    int mLayerType = LAYER_TYPE_NONE;
    Paint mLayerPaint;

    public boolean mCachingFailed;

    final RenderNode mRenderNode;

    AccessibilityDelegate mAccessibilityDelegate;

    ViewOverlay mOverlay;

    // protected final InputEventConsistencyVerifier mInputEventConsistencyVerifier =
    //         InputEventConsistencyVerifier.isInstrumentationEnabled() ?
    //                 new InputEventConsistencyVerifier(this, 0) : null;

    // GhostView mGhostView;

    @ViewDebug.ExportedProperty(category = "attributes", hasAdjacentMapping = true)
    public String[] mAttributes;

    String mStartActivityRequestWho;

    int mUnbufferedInputSource = InputDevice.SOURCE_CLASS_NONE;

    static final float MAX_FRAME_RATE = 120;

    public static final float REQUESTED_FRAME_RATE_CATEGORY_DEFAULT = Float.NaN;
    public static final float REQUESTED_FRAME_RATE_CATEGORY_NO_PREFERENCE = -1;
    public static final float REQUESTED_FRAME_RATE_CATEGORY_LOW = -2;
    public static final float REQUESTED_FRAME_RATE_CATEGORY_NORMAL = -3;
    public static final float REQUESTED_FRAME_RATE_CATEGORY_HIGH = -4;

    public View(Context context) {
        mContext = context;
        mRenderNode = null;
    }

    public View(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public View(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public View(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        this(context);
    }

    @NonNull
    public int[] getAttributeResolutionStack(@AttrRes int attribute) {
        throw new RuntimeException("Stub!");
    }

    @NonNull
    @SuppressWarnings("AndroidFrameworkEfficientCollections")
    public Map<Integer, Integer> getAttributeSourceResourceMap() {
        throw new RuntimeException("Stub!");
    }

    @StyleRes
    public int getExplicitStyle() {
        throw new RuntimeException("Stub!");
    }

    View() {
        mRenderNode = null;
    }

    public final boolean isShowingLayoutBounds() {
        throw new RuntimeException("Stub!");
    }

    @TestApi
    public final void setShowingLayoutBounds(boolean debugLayout) {
        throw new RuntimeException("Stub!");
    }

    public final void saveAttributeDataForStyleable(@NonNull Context context,
            @NonNull int[] styleable, @Nullable AttributeSet attrs, @NonNull TypedArray t,
            int defStyleAttr, int defStyleRes) {
        throw new RuntimeException("Stub!");
    }

    protected void initializeFadingEdge(TypedArray a) {
        throw new RuntimeException("Stub!");
    }

    protected void initializeFadingEdgeInternal(TypedArray a) {
        throw new RuntimeException("Stub!");
    }

    public int getVerticalFadingEdgeLength() {
        throw new RuntimeException("Stub!");
    }

    public void setFadingEdgeLength(int length) {
        throw new RuntimeException("Stub!");
    }

    public void clearPendingCredentialRequest() {
        throw new RuntimeException("Stub!");
    }

    // public void setPendingCredentialRequest(@NonNull GetCredentialRequest request,
    //         @NonNull OutcomeReceiver<GetCredentialResponse, GetCredentialException> callback) {
    //     throw new RuntimeException("Stub!");
    // }

    // @Nullable
    // public ViewCredentialHandler getViewCredentialHandler() {
    //     throw new RuntimeException("Stub!");
    // }

    public int getHorizontalFadingEdgeLength() {
        throw new RuntimeException("Stub!");
    }

    public int getVerticalScrollbarWidth() {
        throw new RuntimeException("Stub!");
    }

    protected int getHorizontalScrollbarHeight() {
        throw new RuntimeException("Stub!");
    }

    protected void initializeScrollbars(TypedArray a) {
        throw new RuntimeException("Stub!");
    }

    protected void initializeScrollbarsInternal(TypedArray a) {
        throw new RuntimeException("Stub!");
    }

    public void setVerticalScrollbarThumbDrawable(@Nullable Drawable drawable) {
        throw new RuntimeException("Stub!");
    }

    public void setVerticalScrollbarTrackDrawable(@Nullable Drawable drawable) {
        throw new RuntimeException("Stub!");
    }

    public void setHorizontalScrollbarThumbDrawable(@Nullable Drawable drawable) {
        throw new RuntimeException("Stub!");
    }

    public void setHorizontalScrollbarTrackDrawable(@Nullable Drawable drawable) {
        throw new RuntimeException("Stub!");
    }

    public @Nullable Drawable getVerticalScrollbarThumbDrawable() {
        throw new RuntimeException("Stub!");
    }

    public @Nullable Drawable getVerticalScrollbarTrackDrawable() {
        throw new RuntimeException("Stub!");
    }

    public @Nullable Drawable getHorizontalScrollbarThumbDrawable() {
        throw new RuntimeException("Stub!");
    }

    public @Nullable Drawable getHorizontalScrollbarTrackDrawable() {
        throw new RuntimeException("Stub!");
    }

    public void setVerticalScrollbarPosition(int position) {
        throw new RuntimeException("Stub!");
    }

    public int getVerticalScrollbarPosition() {
        throw new RuntimeException("Stub!");
    }

    boolean isOnScrollbar(float x, float y) {
        throw new RuntimeException("Stub!");
    }

    boolean isOnScrollbarThumb(float x, float y) {
        throw new RuntimeException("Stub!");
    }

    boolean isDraggingScrollBar() {
        throw new RuntimeException("Stub!");
    }

    public void setScrollIndicators(@ScrollIndicators int indicators) {
        throw new RuntimeException("Stub!");
    }

    public void setScrollIndicators(@ScrollIndicators int indicators, @ScrollIndicators int mask) {
        throw new RuntimeException("Stub!");
    }

    @ScrollIndicators
    public int getScrollIndicators() {
        throw new RuntimeException("Stub!");
    }

    // ListenerInfo getListenerInfo() {
    //     throw new RuntimeException("Stub!");
    // }

    public void setOnScrollChangeListener(OnScrollChangeListener l) {
        throw new RuntimeException("Stub!");
    }

    public void setOnFocusChangeListener(OnFocusChangeListener l) {
        throw new RuntimeException("Stub!");
    }

    public void addOnLayoutChangeListener(OnLayoutChangeListener listener) {
        throw new RuntimeException("Stub!");
    }

    public void removeOnLayoutChangeListener(OnLayoutChangeListener listener) {
        throw new RuntimeException("Stub!");
    }

    public void addOnAttachStateChangeListener(OnAttachStateChangeListener listener) {
        throw new RuntimeException("Stub!");
    }

    public void removeOnAttachStateChangeListener(OnAttachStateChangeListener listener) {
        throw new RuntimeException("Stub!");
    }

    public OnFocusChangeListener getOnFocusChangeListener() {
        throw new RuntimeException("Stub!");
    }

    public void setOnClickListener(@Nullable OnClickListener l) {
        throw new RuntimeException("Stub!");
    }

    public boolean hasOnClickListeners() {
        throw new RuntimeException("Stub!");
    }

    public void setOnLongClickListener(@Nullable OnLongClickListener l) {
        throw new RuntimeException("Stub!");
    }

    public boolean hasOnLongClickListeners() {
        throw new RuntimeException("Stub!");
    }

    @Nullable
    public OnLongClickListener getOnLongClickListener() {
        throw new RuntimeException("Stub!");
    }

    public void setOnContextClickListener(@Nullable OnContextClickListener l) {
        throw new RuntimeException("Stub!");
    }

    public void setOnCreateContextMenuListener(OnCreateContextMenuListener l) {
        throw new RuntimeException("Stub!");
    }

    public void addFrameMetricsListener(Window window,
            Window.OnFrameMetricsAvailableListener listener,
            Handler handler) {
        throw new RuntimeException("Stub!");
    }

    public void removeFrameMetricsListener(
            Window.OnFrameMetricsAvailableListener listener) {
        throw new RuntimeException("Stub!");
    }

    /** @hide */
    public void setNotifyAutofillManagerOnClick(boolean notify) {
        throw new RuntimeException("Stub!");
    }

    // NOTE: other methods on View should not call this method directly, but performClickInternal()
    // instead, to guarantee that the autofill manager is notified when necessary (as subclasses
    // could extend this method without calling super.performClick()).
    public boolean performClick() {
        throw new RuntimeException("Stub!");
    }

    public boolean callOnClick() {
        throw new RuntimeException("Stub!");
    }

    public boolean performLongClick() {
        throw new RuntimeException("Stub!");
    }

    public boolean performLongClick(float x, float y) {
        throw new RuntimeException("Stub!");
    }

    public boolean performContextClick(float x, float y) {
        throw new RuntimeException("Stub!");
    }

    public boolean performContextClick() {
        throw new RuntimeException("Stub!");
    }

    protected boolean performButtonActionOnTouchDown(MotionEvent event) {
        throw new RuntimeException("Stub!");
    }

    public boolean showContextMenu() {
        throw new RuntimeException("Stub!");
    }

    public boolean showContextMenu(float x, float y) {
        throw new RuntimeException("Stub!");
    }

    public ActionMode startActionMode(ActionMode.Callback callback) {
        throw new RuntimeException("Stub!");
    }

    public ActionMode startActionMode(ActionMode.Callback callback, int type) {
        throw new RuntimeException("Stub!");
    }

    public void startActivityForResult(Intent intent, int requestCode) {
        throw new RuntimeException("Stub!");
    }

    public boolean dispatchActivityResult(
            String who, int requestCode, int resultCode, Intent data) {
        throw new RuntimeException("Stub!");
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Do nothing.
    }

    public void setOnKeyListener(OnKeyListener l) {
        throw new RuntimeException("Stub!");
    }

    public void setOnTouchListener(OnTouchListener l) {
        throw new RuntimeException("Stub!");
    }

    public void setOnGenericMotionListener(OnGenericMotionListener l) {
        throw new RuntimeException("Stub!");
    }

    public void setOnHoverListener(OnHoverListener l) {
        throw new RuntimeException("Stub!");
    }

    public void setOnDragListener(OnDragListener l) {
        throw new RuntimeException("Stub!");
    }

    void handleFocusGainInternal(@FocusRealDirection int direction, Rect previouslyFocusedRect) {
        throw new RuntimeException("Stub!");
    }

    public final void setRevealOnFocusHint(boolean revealOnFocus) {
        throw new RuntimeException("Stub!");
    }

    public final boolean getRevealOnFocusHint() {
        throw new RuntimeException("Stub!");
    }

    public void getHotspotBounds(Rect outRect) {
        throw new RuntimeException("Stub!");
    }

    public boolean requestRectangleOnScreen(Rect rectangle) {
        throw new RuntimeException("Stub!");
    }

    public boolean requestRectangleOnScreen(Rect rectangle, boolean immediate) {
        throw new RuntimeException("Stub!");
    }

    public void clearFocus() {
        throw new RuntimeException("Stub!");
    }

    public void clearFocusInternal(View focused, boolean propagate, boolean refocus) {
        throw new RuntimeException("Stub!");
    }

    void notifyGlobalFocusCleared(View oldFocus) {
        throw new RuntimeException("Stub!");
    }

    boolean rootViewRequestFocus() {
        throw new RuntimeException("Stub!");
    }

    void unFocus(View focused) {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "focus")
    public boolean hasFocus() {
        throw new RuntimeException("Stub!");
    }

    public boolean hasFocusable() {
        throw new RuntimeException("Stub!");
    }

    public boolean hasExplicitFocusable() {
        throw new RuntimeException("Stub!");
    }

    boolean hasFocusable(boolean allowAutoFocus, boolean dispatchExplicit) {
        throw new RuntimeException("Stub!");
    }

    @CallSuper
    protected void onFocusChanged(boolean gainFocus, @FocusDirection int direction,
            @Nullable Rect previouslyFocusedRect) {
        throw new RuntimeException("Stub!");
    }

    /** @hide */
    public void notifyEnterOrExitForAutoFillIfNeeded(boolean enter) {
        throw new RuntimeException("Stub!");
    }

    public void setAccessibilityPaneTitle(@Nullable CharSequence accessibilityPaneTitle) {
        throw new RuntimeException("Stub!");
    }

    @Nullable
    public CharSequence getAccessibilityPaneTitle() {
        throw new RuntimeException("Stub!");
    }

    public void sendAccessibilityEvent(int eventType) {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public void announceForAccessibility(CharSequence text) {
        throw new RuntimeException("Stub!");
    }

    public void sendAccessibilityEventInternal(int eventType) {
        throw new RuntimeException("Stub!");
    }

    public void sendAccessibilityEventUnchecked(AccessibilityEvent event) {
        throw new RuntimeException("Stub!");
    }

    public void sendAccessibilityEventUncheckedInternal(AccessibilityEvent event) {
        throw new RuntimeException("Stub!");
    }

    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        throw new RuntimeException("Stub!");
    }

    public boolean dispatchPopulateAccessibilityEventInternal(AccessibilityEvent event) {
        throw new RuntimeException("Stub!");
    }

    @CallSuper
    public void onPopulateAccessibilityEvent(AccessibilityEvent event) {
        throw new RuntimeException("Stub!");
    }

    public void onPopulateAccessibilityEventInternal(AccessibilityEvent event) {
        throw new RuntimeException("Stub!");
    }

    @CallSuper
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        throw new RuntimeException("Stub!");
    }

    public void onInitializeAccessibilityEventInternal(AccessibilityEvent event) {
        throw new RuntimeException("Stub!");
    }

    public AccessibilityNodeInfo createAccessibilityNodeInfo() {
        throw new RuntimeException("Stub!");
    }

    public @Nullable AccessibilityNodeInfo createAccessibilityNodeInfoInternal() {
        throw new RuntimeException("Stub!");
    }

    @CallSuper
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        throw new RuntimeException("Stub!");
    }

    public void getBoundsOnScreen(Rect outRect) {
        throw new RuntimeException("Stub!");
    }

    @TestApi
    public void getBoundsOnScreen(@NonNull Rect outRect, boolean clipToParent) {
        throw new RuntimeException("Stub!");
    }

    public void getBoundsOnScreen(RectF outRect, boolean clipToParent) {
        throw new RuntimeException("Stub!");
    }

    public void getBoundsInWindow(Rect outRect, boolean clipToParent) {
        throw new RuntimeException("Stub!");
    }

    public void mapRectFromViewToScreenCoords(RectF rect, boolean clipToParent) {
        throw new RuntimeException("Stub!");
    }

    public void mapRectFromViewToWindowCoords(RectF rect, boolean clipToParent) {
        throw new RuntimeException("Stub!");
    }

    public CharSequence getAccessibilityClassName() {
        throw new RuntimeException("Stub!");
    }

    public void onProvideStructure(ViewStructure structure) {
        throw new RuntimeException("Stub!");
    }

    public void onProvideAutofillStructure(ViewStructure structure, @AutofillFlags int flags) {
        throw new RuntimeException("Stub!");
    }

    public void onProvideContentCaptureStructure(@NonNull ViewStructure structure, int flags) {
        throw new RuntimeException("Stub!");
    }

    /** @hide */
    protected void onProvideStructure(@NonNull ViewStructure structure,
            @ViewStructureType int viewFor, int flags) {
        throw new RuntimeException("Stub!");
    }

    public void onProvideVirtualStructure(ViewStructure structure) {
        throw new RuntimeException("Stub!");
    }

    public void onProvideAutofillVirtualStructure(ViewStructure structure, int flags) {
        throw new RuntimeException("Stub!");
    }

    // public void setOnReceiveContentListener(
    //         @SuppressLint("NullableCollection") @Nullable String[] mimeTypes,
    //         @Nullable OnReceiveContentListener listener) {
    //     throw new RuntimeException("Stub!");
    // }

    // @Nullable
    // public ContentInfo performReceiveContent(@NonNull ContentInfo payload) {
    //     throw new RuntimeException("Stub!");
    // }

    // @Nullable
    // public ContentInfo onReceiveContent(@NonNull ContentInfo payload) {
    //     throw new RuntimeException("Stub!");
    // }

    @SuppressLint("NullableCollection")
    @Nullable
    public String[] getReceiveContentMimeTypes() {
        throw new RuntimeException("Stub!");
    }

    public void autofill(@SuppressWarnings("unused") AutofillValue value) {
    }

    public void autofill(@NonNull @SuppressWarnings("unused") SparseArray<AutofillValue> values) {
        throw new RuntimeException("Stub!");
    }

    // public void onGetCredentialResponse(GetCredentialResponse response) {
    //     throw new RuntimeException("Stub!");
    // }

    public void onGetCredentialException(String errorType, String errorMsg) {
        throw new RuntimeException("Stub!");
    }

    public final AutofillId getAutofillId() {
        throw new RuntimeException("Stub!");
    }

    // @Nullable
    // public final GetCredentialRequest getPendingCredentialRequest() {
    //     throw new RuntimeException("Stub!");
    // }

    // @Nullable
    // public final OutcomeReceiver<GetCredentialResponse,
    //         GetCredentialException> getPendingCredentialCallback() {
    //     throw new RuntimeException("Stub!");
    // }

    public void setAutofillId(@Nullable AutofillId id) {
        throw new RuntimeException("Stub!");
    }

    public void resetSubtreeAutofillIds() {
        throw new RuntimeException("Stub!");
    }

    public @AutofillType int getAutofillType() {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty()
    @Nullable public String[] getAutofillHints() {
        throw new RuntimeException("Stub!");
    }

    @TestApi
    public boolean isAutofilled() {
        throw new RuntimeException("Stub!");
    }

    public boolean hideAutofillHighlight() {
        throw new RuntimeException("Stub!");
    }

    @Nullable
    public AutofillValue getAutofillValue() {
        throw new RuntimeException("Stub!");
    }

    public @AutofillImportance int getImportantForAutofill() {
        throw new RuntimeException("Stub!");
    }

    public void setImportantForAutofill(@AutofillImportance int mode) {
        throw new RuntimeException("Stub!");
    }

    public final boolean isImportantForAutofill() {
        throw new RuntimeException("Stub!");
    }

    public final void setContentSensitivity(@ContentSensitivity int mode)  {
        throw new RuntimeException("Stub!");
    }

    public @ContentSensitivity final int getContentSensitivity() {
        throw new RuntimeException("Stub!");
    }

    public final boolean isContentSensitive() {
        throw new RuntimeException("Stub!");
    }

    public @ContentCaptureImportance int getImportantForContentCapture() {
        throw new RuntimeException("Stub!");
    }

    public void setImportantForContentCapture(@ContentCaptureImportance int mode) {
        throw new RuntimeException("Stub!");
    }

    public final boolean isImportantForContentCapture() {
        throw new RuntimeException("Stub!");
    }
    /** @hide */
    protected boolean getNotifiedContentCaptureAppeared() {
        throw new RuntimeException("Stub!");
    }


    public void setContentCaptureSession(@Nullable ContentCaptureSession contentCaptureSession) {
        throw new RuntimeException("Stub!");
    }

    @Nullable
    public final ContentCaptureSession getContentCaptureSession() {
        throw new RuntimeException("Stub!");
    }

    final boolean isActivityDeniedForAutofillForUnimportantView() {
        throw new RuntimeException("Stub!");
    }

    final boolean isMatchingAutofillableHeuristics() {
        throw new RuntimeException("Stub!");
    }
    /** @hide */
    public boolean canNotifyAutofillEnterExitEvent() {
        throw new RuntimeException("Stub!");
    }

    public void dispatchProvideStructure(ViewStructure structure) {
        throw new RuntimeException("Stub!");
    }

    public void dispatchProvideAutofillStructure(@NonNull ViewStructure structure,
            @AutofillFlags int flags) {
        throw new RuntimeException("Stub!");
    }

    public void dispatchInitialProvideContentCaptureStructure() {
        throw new RuntimeException("Stub!");
    }

    /** @hide */
    void dispatchProvideContentCaptureStructure() {
        throw new RuntimeException("Stub!");
    }

    public void onInitializeAccessibilityNodeInfoInternal(AccessibilityNodeInfo info) {
        throw new RuntimeException("Stub!");
    }


    public void addExtraDataToAccessibilityNodeInfo(
            @NonNull AccessibilityNodeInfo info, @NonNull String extraDataKey,
            @Nullable Bundle arguments) {
    }

    public boolean isVisibleToUserForAutofill(int virtualId) {
        throw new RuntimeException("Stub!");
    }

    public boolean isVisibleToUser() {
        throw new RuntimeException("Stub!");
    }

    protected boolean isVisibleToUser(Rect boundInView) {
        throw new RuntimeException("Stub!");
    }

    public AccessibilityDelegate getAccessibilityDelegate() {
        throw new RuntimeException("Stub!");
    }

    public void setAccessibilityDelegate(@Nullable AccessibilityDelegate delegate) {
        throw new RuntimeException("Stub!");
    }

    public AccessibilityNodeProvider getAccessibilityNodeProvider() {
        throw new RuntimeException("Stub!");
    }

    public int getAccessibilityViewId() {
        throw new RuntimeException("Stub!");
    }

    public int getAutofillViewId() {
        throw new RuntimeException("Stub!");
    }

    public int getAccessibilityWindowId() {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "accessibility")
    public final @Nullable CharSequence getStateDescription() {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "accessibility")
    public CharSequence getContentDescription() {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "accessibility")
    @Nullable
    public CharSequence getSupplementalDescription() {
        throw new RuntimeException("Stub!");
    }

    public void setStateDescription(@Nullable CharSequence stateDescription) {
        throw new RuntimeException("Stub!");
    }

    public void setContentDescription(CharSequence contentDescription) {
        throw new RuntimeException("Stub!");
    }

    public void setSupplementalDescription(@Nullable CharSequence supplementalDescription) {
        throw new RuntimeException("Stub!");
    }

    public void setAccessibilityTraversalBefore(@IdRes int beforeId) {
        throw new RuntimeException("Stub!");
    }

    @IdRes
    public int getAccessibilityTraversalBefore() {
        throw new RuntimeException("Stub!");
    }

    public void setAccessibilityTraversalAfter(@IdRes int afterId) {
        throw new RuntimeException("Stub!");
    }

    @IdRes
    public int getAccessibilityTraversalAfter() {
        throw new RuntimeException("Stub!");
    }

    @IdRes
    @ViewDebug.ExportedProperty(category = "accessibility")
    public int getLabelFor() {
        throw new RuntimeException("Stub!");
    }

    public void setLabelFor(@IdRes int id) {
        throw new RuntimeException("Stub!");
    }

    @CallSuper
    protected void onFocusLost() {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "focus")
    public boolean isFocused() {
        throw new RuntimeException("Stub!");
    }

    public View findFocus() {
        throw new RuntimeException("Stub!");
    }

    public boolean isScrollContainer() {
        throw new RuntimeException("Stub!");
    }

    public void setScrollContainer(boolean isScrollContainer) {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    @DrawingCacheQuality
    public int getDrawingCacheQuality() {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public void setDrawingCacheQuality(@DrawingCacheQuality int quality) {
        throw new RuntimeException("Stub!");
    }

    public boolean getKeepScreenOn() {
        throw new RuntimeException("Stub!");
    }

    public void setKeepScreenOn(boolean keepScreenOn) {
        throw new RuntimeException("Stub!");
    }

    @IdRes
    public int getNextFocusLeftId() {
        throw new RuntimeException("Stub!");
    }

    public void setNextFocusLeftId(@IdRes int nextFocusLeftId) {
        throw new RuntimeException("Stub!");
    }

    @IdRes
    public int getNextFocusRightId() {
        throw new RuntimeException("Stub!");
    }

    public void setNextFocusRightId(@IdRes int nextFocusRightId) {
        throw new RuntimeException("Stub!");
    }

    @IdRes
    public int getNextFocusUpId() {
        throw new RuntimeException("Stub!");
    }

    public void setNextFocusUpId(@IdRes int nextFocusUpId) {
        throw new RuntimeException("Stub!");
    }

    @IdRes
    public int getNextFocusDownId() {
        throw new RuntimeException("Stub!");
    }

    public void setNextFocusDownId(@IdRes int nextFocusDownId) {
        throw new RuntimeException("Stub!");
    }

    @IdRes
    public int getNextFocusForwardId() {
        throw new RuntimeException("Stub!");
    }

    public void setNextFocusForwardId(@IdRes int nextFocusForwardId) {
        throw new RuntimeException("Stub!");
    }

    @IdRes
    public int getNextClusterForwardId() {
        return mNextClusterForwardId;
    }

    public void setNextClusterForwardId(@IdRes int nextClusterForwardId) {
        throw new RuntimeException("Stub!");
    }

    public boolean isShown() {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    protected boolean fitSystemWindows(Rect insets) {
        throw new RuntimeException("Stub!");
    }

    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        throw new RuntimeException("Stub!");
    }

    public void setOnApplyWindowInsetsListener(OnApplyWindowInsetsListener listener) {
        throw new RuntimeException("Stub!");
    }

    public WindowInsets dispatchApplyWindowInsets(WindowInsets insets) {
        throw new RuntimeException("Stub!");
    }

    public void setWindowInsetsAnimationCallback(
            @Nullable WindowInsetsAnimation.Callback callback) {
        throw new RuntimeException("Stub!");
    }

    public boolean hasWindowInsetsAnimationCallback() {
        throw new RuntimeException("Stub!");
    }

    public void dispatchWindowInsetsAnimationPrepare(
            @NonNull WindowInsetsAnimation animation) {
        throw new RuntimeException("Stub!");
    }

    @NonNull
    public Bounds dispatchWindowInsetsAnimationStart(
            @NonNull WindowInsetsAnimation animation, @NonNull Bounds bounds) {
        throw new RuntimeException("Stub!");
    }

    @NonNull
    public WindowInsets dispatchWindowInsetsAnimationProgress(@NonNull WindowInsets insets,
            @NonNull List<WindowInsetsAnimation> runningAnimations) {
        throw new RuntimeException("Stub!");
    }

    public void dispatchWindowInsetsAnimationEnd(@NonNull WindowInsetsAnimation animation) {
        throw new RuntimeException("Stub!");
    }

    public void setSystemGestureExclusionRects(@NonNull List<Rect> rects) {
        throw new RuntimeException("Stub!");
    }

    void updateSystemGestureExclusionRects() {
        throw new RuntimeException("Stub!");
    }

    @NonNull
    public List<Rect> getSystemGestureExclusionRects() {
        throw new RuntimeException("Stub!");
    }

    public final void setPreferKeepClear(boolean preferKeepClear) {
        throw new RuntimeException("Stub!");
    }

    public final boolean isPreferKeepClear() {
        throw new RuntimeException("Stub!");
    }

    public final void setPreferKeepClearRects(@NonNull List<Rect> rects) {
        throw new RuntimeException("Stub!");
    }

    @NonNull
    public final List<Rect> getPreferKeepClearRects() {
        throw new RuntimeException("Stub!");
    }

    @SystemApi
    public final void setUnrestrictedPreferKeepClearRects(@NonNull List<Rect> rects) {
        throw new RuntimeException("Stub!");
    }

    @SystemApi
    @NonNull
    public final List<Rect> getUnrestrictedPreferKeepClearRects() {
        throw new RuntimeException("Stub!");
    }

    void updateKeepClearRects() {
        throw new RuntimeException("Stub!");
    }

    @NonNull
    List<Rect> collectPreferKeepClearRects() {
        throw new RuntimeException("Stub!");
    }

    @NonNull
    List<Rect> collectUnrestrictedPreferKeepClearRects() {
        throw new RuntimeException("Stub!");
    }

    public void setHandwritingBoundsOffsets(float offsetLeft, float offsetTop,
            float offsetRight, float offsetBottom) {
        throw new RuntimeException("Stub!");
    }

    public float getHandwritingBoundsOffsetLeft() {
        throw new RuntimeException("Stub!");
    }

    public float getHandwritingBoundsOffsetTop() {
        throw new RuntimeException("Stub!");
    }

    public float getHandwritingBoundsOffsetRight() {
        throw new RuntimeException("Stub!");
    }

    public float getHandwritingBoundsOffsetBottom() {
        throw new RuntimeException("Stub!");
    }


    public void setHandwritingArea(@Nullable Rect rect) {
        throw new RuntimeException("Stub!");
    }

    @Nullable
    public Rect getHandwritingArea() {
        throw new RuntimeException("Stub!");
    }

    void updateHandwritingArea() {
        throw new RuntimeException("Stub!");
    }

    boolean shouldInitiateHandwriting() {
        throw new RuntimeException("Stub!");
    }

    public boolean shouldTrackHandwritingArea() {
        throw new RuntimeException("Stub!");
    }

    public void setHandwritingDelegatorCallback(@Nullable Runnable callback) {
        throw new RuntimeException("Stub!");
    }

    @Nullable
    public Runnable getHandwritingDelegatorCallback() {
        throw new RuntimeException("Stub!");
    }

    public void setAllowedHandwritingDelegatePackage(@Nullable String allowedPackageName) {
        throw new RuntimeException("Stub!");
    }

    @Nullable
    public String getAllowedHandwritingDelegatePackageName() {
        throw new RuntimeException("Stub!");
    }

    public void setIsHandwritingDelegate(boolean isHandwritingDelegate) {
        throw new RuntimeException("Stub!");
    }

    public boolean isHandwritingDelegate() {
        throw new RuntimeException("Stub!");
    }

    public void setAllowedHandwritingDelegatorPackage(@Nullable String allowedPackageName) {
        throw new RuntimeException("Stub!");
    }

    @Nullable
    public String getAllowedHandwritingDelegatorPackageName() {
        throw new RuntimeException("Stub!");
    }

    public void setHandwritingDelegateFlags(
            int flags) {
        throw new RuntimeException("Stub!");
    }

    public int getHandwritingDelegateFlags() {
        throw new RuntimeException("Stub!");
    }

    public void getLocationInSurface(@NonNull @Size(2) int[] location) {
        throw new RuntimeException("Stub!");
    }

    public WindowInsets getRootWindowInsets() {
        throw new RuntimeException("Stub!");
    }

    public @Nullable WindowInsetsController getWindowInsetsController() {
        throw new RuntimeException("Stub!");
    }

    // @Nullable
    // public final OnBackInvokedDispatcher findOnBackInvokedDispatcher() {
    //     throw new RuntimeException("Stub!");
    // }

    @Deprecated
    protected boolean computeFitSystemWindows(Rect inoutInsets, Rect outLocalInsets) {
        throw new RuntimeException("Stub!");
    }

    public WindowInsets computeSystemWindowInsets(WindowInsets in, Rect outLocalInsets) {
        throw new RuntimeException("Stub!");
    }

    protected boolean hasContentOnApplyWindowInsetsListener() {
        throw new RuntimeException("Stub!");
    }

    public void setFitsSystemWindows(boolean fitSystemWindows) {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty
    public boolean getFitsSystemWindows() {
        throw new RuntimeException("Stub!");
    }

    /** @hide */
    public boolean fitsSystemWindows() {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public void requestFitSystemWindows() {
        throw new RuntimeException("Stub!");
    }

    public void requestApplyInsets() {
        throw new RuntimeException("Stub!");
    }

    public void makeOptionalFitsSystemWindows() {
        throw new RuntimeException("Stub!");
    }

    public void makeFrameworkOptionalFitsSystemWindows() {
        throw new RuntimeException("Stub!");
    }

    public boolean isFrameworkOptionalFitsSystemWindows() {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(mapping = {
        @ViewDebug.IntToString(from = VISIBLE,   to = "VISIBLE"),
        @ViewDebug.IntToString(from = INVISIBLE, to = "INVISIBLE"),
        @ViewDebug.IntToString(from = GONE,      to = "GONE")
    })
    @Visibility
    public int getVisibility() {
        throw new RuntimeException("Stub!");
    }

    public void setVisibility(@Visibility int visibility) {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty
    public boolean isEnabled() {
        throw new RuntimeException("Stub!");
    }

    public void setEnabled(boolean enabled) {
        throw new RuntimeException("Stub!");
    }

    public void setFocusable(boolean focusable) {
        throw new RuntimeException("Stub!");
    }

    public void setFocusable(@Focusable int focusable) {
        throw new RuntimeException("Stub!");
    }

    public void setFocusableInTouchMode(boolean focusableInTouchMode) {
        throw new RuntimeException("Stub!");
    }

    public void setAutofillHints(@Nullable String... autofillHints) {
        throw new RuntimeException("Stub!");
    }

    @TestApi
    public void setAutofilled(boolean isAutofilled, boolean hideHighlight) {
        throw new RuntimeException("Stub!");
    }

    public void setSoundEffectsEnabled(boolean soundEffectsEnabled) {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty
    public boolean isSoundEffectsEnabled() {
        throw new RuntimeException("Stub!");
    }

    public void setHapticFeedbackEnabled(boolean hapticFeedbackEnabled) {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty
    public boolean isHapticFeedbackEnabled() {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "layout", mapping = {
        @ViewDebug.IntToString(from = LAYOUT_DIRECTION_LTR,     to = "LTR"),
        @ViewDebug.IntToString(from = LAYOUT_DIRECTION_RTL,     to = "RTL"),
        @ViewDebug.IntToString(from = LAYOUT_DIRECTION_INHERIT, to = "INHERIT"),
        @ViewDebug.IntToString(from = LAYOUT_DIRECTION_LOCALE,  to = "LOCALE")
    })
    @LayoutDir
    public int getRawLayoutDirection() {
        throw new RuntimeException("Stub!");
    }

    public void setLayoutDirection(@LayoutDir int layoutDirection) {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "layout", mapping = {
        @ViewDebug.IntToString(from = LAYOUT_DIRECTION_LTR, to = "RESOLVED_DIRECTION_LTR"),
        @ViewDebug.IntToString(from = LAYOUT_DIRECTION_RTL, to = "RESOLVED_DIRECTION_RTL")
    })
    @ResolvedLayoutDir
    public int getLayoutDirection() {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "layout")
    public boolean isLayoutRtl() {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "layout")
    public boolean hasTransientState() {
        throw new RuntimeException("Stub!");
    }

    public void setHasTransientState(boolean hasTransientState) {
        throw new RuntimeException("Stub!");
    }

    public void setHasTranslationTransientState(boolean hasTranslationTransientState) {
        throw new RuntimeException("Stub!");
    }

    public boolean hasTranslationTransientState() {
        throw new RuntimeException("Stub!");
    }

    public void clearTranslationState() {
        throw new RuntimeException("Stub!");
    }

    public boolean isAttachedToWindow() {
        throw new RuntimeException("Stub!");
    }

    public boolean isLaidOut() {
        throw new RuntimeException("Stub!");
    }

    boolean isLayoutValid() {
        throw new RuntimeException("Stub!");
    }

    public void setWillNotDraw(boolean willNotDraw) {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "drawing")
    public boolean willNotDraw() {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public void setWillNotCacheDrawing(boolean willNotCacheDrawing) {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "drawing")
    @Deprecated
    public boolean willNotCacheDrawing() {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty
    public boolean isClickable() {
        throw new RuntimeException("Stub!");
    }

    public void setClickable(boolean clickable) {
        throw new RuntimeException("Stub!");
    }

    public void setAllowClickWhenDisabled(boolean clickableWhenDisabled) {
        throw new RuntimeException("Stub!");
    }

    public boolean isLongClickable() {
        throw new RuntimeException("Stub!");
    }

    public void setLongClickable(boolean longClickable) {
        throw new RuntimeException("Stub!");
    }

    public boolean isContextClickable() {
        throw new RuntimeException("Stub!");
    }

    public void setContextClickable(boolean contextClickable) {
        throw new RuntimeException("Stub!");
    }

    public void setPressed(boolean pressed) {
        throw new RuntimeException("Stub!");
    }

    protected void dispatchSetPressed(boolean pressed) {
    }

    @ViewDebug.ExportedProperty
    public boolean isPressed() {
        throw new RuntimeException("Stub!");
    }

    public boolean isAssistBlocked() {
        throw new RuntimeException("Stub!");
    }

    public void setAssistBlocked(boolean enabled) {
        throw new RuntimeException("Stub!");
    }

    public boolean isSaveEnabled() {
        throw new RuntimeException("Stub!");
    }

    public void setSaveEnabled(boolean enabled) {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty
    public boolean getFilterTouchesWhenObscured() {
        throw new RuntimeException("Stub!");
    }

    public void setFilterTouchesWhenObscured(boolean enabled) {
        throw new RuntimeException("Stub!");
    }

    public boolean isSaveFromParentEnabled() {
        throw new RuntimeException("Stub!");
    }

    public void setSaveFromParentEnabled(boolean enabled) {
        throw new RuntimeException("Stub!");
    }


    @ViewDebug.ExportedProperty(category = "focus")
    public final boolean isFocusable() {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(mapping = {
            @ViewDebug.IntToString(from = NOT_FOCUSABLE, to = "NOT_FOCUSABLE"),
            @ViewDebug.IntToString(from = FOCUSABLE, to = "FOCUSABLE"),
            @ViewDebug.IntToString(from = FOCUSABLE_AUTO, to = "FOCUSABLE_AUTO")
            }, category = "focus")
    @Focusable
    public int getFocusable() {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "focus")
    public final boolean isFocusableInTouchMode() {
        throw new RuntimeException("Stub!");
    }

    public boolean isScreenReaderFocusable() {
        throw new RuntimeException("Stub!");
    }

    public void setScreenReaderFocusable(boolean screenReaderFocusable) {
        throw new RuntimeException("Stub!");
    }

    public boolean isAccessibilityHeading() {
        throw new RuntimeException("Stub!");
    }

    public void setAccessibilityHeading(boolean isHeading) {
        throw new RuntimeException("Stub!");
    }

    public View focusSearch(@FocusRealDirection int direction) {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "focus")
    public final boolean isKeyboardNavigationCluster() {
        throw new RuntimeException("Stub!");
    }

    View findKeyboardNavigationCluster() {
        throw new RuntimeException("Stub!");
    }

    public void setKeyboardNavigationCluster(boolean isCluster) {
        throw new RuntimeException("Stub!");
    }

    @TestApi
    public final void setFocusedInCluster() {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "focus")
    public final boolean isFocusedByDefault() {
        throw new RuntimeException("Stub!");
    }

    public void setFocusedByDefault(boolean isFocusedByDefault) {
        throw new RuntimeException("Stub!");
    }

    boolean hasDefaultFocus() {
        throw new RuntimeException("Stub!");
    }

    public View keyboardNavigationClusterSearch(View currentCluster,
            @FocusDirection int direction) {
        throw new RuntimeException("Stub!");
    }

    public boolean dispatchUnhandledMove(View focused, @FocusRealDirection int direction) {
        throw new RuntimeException("Stub!");
    }

    public void setDefaultFocusHighlightEnabled(boolean defaultFocusHighlightEnabled) {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "focus")
    public final boolean getDefaultFocusHighlightEnabled() {
        throw new RuntimeException("Stub!");
    }

    View findUserSetNextFocus(View root, @FocusDirection int direction) {
        throw new RuntimeException("Stub!");
    }

    View findUserSetNextKeyboardNavigationCluster(View root, @FocusDirection int direction) {
        throw new RuntimeException("Stub!");
    }

    public ArrayList<View> getFocusables(@FocusDirection int direction) {
        throw new RuntimeException("Stub!");
    }

    public void addFocusables(ArrayList<View> views, @FocusDirection int direction) {
        throw new RuntimeException("Stub!");
    }

    public void addFocusables(ArrayList<View> views, @FocusDirection int direction,
            @FocusableMode int focusableMode) {
        throw new RuntimeException("Stub!");
    }

    public void addKeyboardNavigationClusters(
            @NonNull Collection<View> views,
            int direction) {
        throw new RuntimeException("Stub!");
    }

    public void findViewsWithText(ArrayList<View> outViews, CharSequence searched,
            @FindViewFlags int flags) {
        throw new RuntimeException("Stub!");
    }

    public ArrayList<View> getTouchables() {
        throw new RuntimeException("Stub!");
    }

    public void addTouchables(ArrayList<View> views) {
        throw new RuntimeException("Stub!");
    }

    public boolean isAccessibilityFocused() {
        throw new RuntimeException("Stub!");
    }

    public boolean requestAccessibilityFocus() {
        throw new RuntimeException("Stub!");
    }

    public void clearAccessibilityFocus() {
        throw new RuntimeException("Stub!");
    }

    void clearAccessibilityFocusNoCallbacks(int action) {
        throw new RuntimeException("Stub!");
    }

    public final boolean requestFocus() {
        return requestFocus(View.FOCUS_DOWN);
    }

    @TestApi
    public boolean restoreFocusInCluster(@FocusRealDirection int direction) {
        throw new RuntimeException("Stub!");
    }

    @TestApi
    public boolean restoreFocusNotInCluster() {
        throw new RuntimeException("Stub!");
    }

    public boolean restoreDefaultFocus() {
        throw new RuntimeException("Stub!");
    }

    public final boolean requestFocus(int direction) {
        throw new RuntimeException("Stub!");
    }

    public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
        throw new RuntimeException("Stub!");
    }

    void clearParentsWantFocus() {
        throw new RuntimeException("Stub!");
    }

    public final boolean requestFocusFromTouch() {
        throw new RuntimeException("Stub!");
    }

    public int getImportantForAccessibility() {
        throw new RuntimeException("Stub!");
    }

    public void setAccessibilityLiveRegion(int mode) {
        throw new RuntimeException("Stub!");
    }

    public int getAccessibilityLiveRegion() {
        throw new RuntimeException("Stub!");
    }

    public void setImportantForAccessibility(int mode) {
        throw new RuntimeException("Stub!");
    }

    public boolean isImportantForAccessibility() {
        throw new RuntimeException("Stub!");
    }

    public ViewParent getParentForAccessibility() {
        throw new RuntimeException("Stub!");
    }

    /** @hide */
    @Nullable
    View getSelfOrParentImportantForA11y() {
        throw new RuntimeException("Stub!");
    }

    public void addChildrenForAccessibility(ArrayList<View> outChildren) {

    }

    public boolean includeForAccessibility() {
        throw new RuntimeException("Stub!");
    }

    public boolean includeForAccessibility(boolean forNodeTree) {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "accessibility")
    public boolean isAccessibilityDataSensitive() {
        throw new RuntimeException("Stub!");
    }

    void calculateAccessibilityDataSensitive() {
        throw new RuntimeException("Stub!");
    }

    public void setAccessibilityDataSensitive(
            @AccessibilityDataSensitive int accessibilityDataSensitive) {
        throw new RuntimeException("Stub!");
    }

    public boolean isActionableForAccessibility() {
        throw new RuntimeException("Stub!");
    }

    public void notifyViewAccessibilityStateChangedIfNeeded(int changeType) {
        throw new RuntimeException("Stub!");
    }

    public void notifySubtreeAccessibilityStateChangedIfNeeded() {
        throw new RuntimeException("Stub!");
    }

    public void setTransitionVisibility(@Visibility int visibility) {
        throw new RuntimeException("Stub!");
    }

    void resetSubtreeAccessibilityStateChanged() {
        throw new RuntimeException("Stub!");
    }

    public boolean dispatchNestedPrePerformAccessibilityAction(int action,
            @Nullable Bundle arguments) {
        throw new RuntimeException("Stub!");
    }

    public boolean performAccessibilityAction(int action, @Nullable Bundle arguments) {
        throw new RuntimeException("Stub!");
    }

    public boolean performAccessibilityActionInternal(int action, @Nullable Bundle arguments) {
        throw new RuntimeException("Stub!");
    }

    public CharSequence getIterableTextForAccessibility() {
        throw new RuntimeException("Stub!");
    }

    public boolean isAccessibilitySelectionExtendable() {
        throw new RuntimeException("Stub!");
    }

    public void prepareForExtendedAccessibilitySelection() {
        throw new RuntimeException("Stub!");
    }

    public int getAccessibilitySelectionStart() {
        throw new RuntimeException("Stub!");
    }

    public int getAccessibilitySelectionEnd() {
        throw new RuntimeException("Stub!");
    }

    public void setAccessibilitySelection(int start, int end) {
        throw new RuntimeException("Stub!");
    }

    // public TextSegmentIterator getIteratorForGranularity(int granularity) {
    //     throw new RuntimeException("Stub!");
    // }

    public final boolean isTemporarilyDetached() {
        throw new RuntimeException("Stub!");
    }

    @CallSuper
    public void dispatchStartTemporaryDetach() {
        throw new RuntimeException("Stub!");
    }

    public void onStartTemporaryDetach() {
        throw new RuntimeException("Stub!");
    }

    @CallSuper
    public void dispatchFinishTemporaryDetach() {
        throw new RuntimeException("Stub!");
    }

    public void onFinishTemporaryDetach() {
    }

    public KeyEvent.DispatcherState getKeyDispatcherState() {
        throw new RuntimeException("Stub!");
    }

    public boolean dispatchKeyEventPreIme(KeyEvent event) {
        throw new RuntimeException("Stub!");
    }

    public boolean dispatchKeyEvent(KeyEvent event) {
        throw new RuntimeException("Stub!");
    }

    public boolean dispatchKeyShortcutEvent(KeyEvent event) {
        throw new RuntimeException("Stub!");
    }

    public boolean dispatchTouchEvent(MotionEvent event) {
        throw new RuntimeException("Stub!");
    }

    boolean isAccessibilityFocusedViewOrHost() {
        throw new RuntimeException("Stub!");
    }

    protected boolean canReceivePointerEvents() {
        throw new RuntimeException("Stub!");
    }

    public boolean onFilterTouchEventForSecurity(MotionEvent event) {
        throw new RuntimeException("Stub!");
    }

    public boolean dispatchTrackballEvent(MotionEvent event) {
        throw new RuntimeException("Stub!");
    }

    public boolean dispatchCapturedPointerEvent(MotionEvent event) {
        throw new RuntimeException("Stub!");
    }

    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        throw new RuntimeException("Stub!");
    }

    protected boolean dispatchHoverEvent(MotionEvent event) {
        throw new RuntimeException("Stub!");
    }

    protected boolean hasHoveredChild() {
        return false;
    }

    protected boolean pointInHoveredChild(MotionEvent event) {
        return false;
    }

    protected boolean dispatchGenericPointerEvent(MotionEvent event) {
        return false;
    }

    protected boolean dispatchGenericFocusedEvent(MotionEvent event) {
        return false;
    }

    public final boolean dispatchPointerEvent(MotionEvent event) {
        throw new RuntimeException("Stub!");
    }

    public void dispatchWindowFocusChanged(boolean hasFocus) {
        throw new RuntimeException("Stub!");
    }

    public void onWindowFocusChanged(boolean hasWindowFocus) {
        throw new RuntimeException("Stub!");
    }

    public boolean hasWindowFocus() {
        throw new RuntimeException("Stub!");
    }

    public boolean hasImeFocus() {
        throw new RuntimeException("Stub!");
    }

    protected void dispatchVisibilityChanged(@NonNull View changedView,
            @Visibility int visibility) {
        throw new RuntimeException("Stub!");
    }

    protected void onVisibilityChanged(@NonNull View changedView, @Visibility int visibility) {
    }

    public void dispatchDisplayHint(@Visibility int hint) {
        throw new RuntimeException("Stub!");
    }

    protected void onDisplayHint(@Visibility int hint) {
    }

    public void dispatchWindowVisibilityChanged(@Visibility int visibility) {
        throw new RuntimeException("Stub!");
    }

    protected void onWindowVisibilityChanged(@Visibility int visibility) {
        throw new RuntimeException("Stub!");
    }

    public boolean isAggregatedVisible() {
        throw new RuntimeException("Stub!");
    }

    boolean dispatchVisibilityAggregated(boolean isVisible) {
        throw new RuntimeException("Stub!");
    }

    @CallSuper
    public void onVisibilityAggregated(boolean isVisible) {
        throw new RuntimeException("Stub!");
    }

    @Visibility
    public int getWindowVisibility() {
        throw new RuntimeException("Stub!");
    }

    public void getWindowVisibleDisplayFrame(Rect outRect) {
        throw new RuntimeException("Stub!");
    }

    @TestApi
    public void getWindowDisplayFrame(@NonNull Rect outRect) {
        throw new RuntimeException("Stub!");
    }

    public void dispatchConfigurationChanged(Configuration newConfig) {
        throw new RuntimeException("Stub!");
    }

    protected void onConfigurationChanged(Configuration newConfig) {
    }

    // void dispatchCollectViewAttributes(AttachInfo attachInfo, int visibility) {
    //     throw new RuntimeException("Stub!");
    // }

    // void performCollectViewAttributes(AttachInfo attachInfo, int visibility) {
    //     throw new RuntimeException("Stub!");
    // }

    void needGlobalAttributesUpdate(boolean force) {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty
    public boolean isInTouchMode() {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.CapturedViewProperty
    public final Context getContext() {
        return mContext;
    }

    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        throw new RuntimeException("Stub!");
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        throw new RuntimeException("Stub!");
    }

    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        throw new RuntimeException("Stub!");
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        throw new RuntimeException("Stub!");
    }

    public boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event) {
        return false;
    }

    public boolean onKeyShortcut(int keyCode, KeyEvent event) {
        return false;
    }

    public boolean onCheckIsTextEditor() {
        return false;
    }

    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        return null;
    }

    public void onInputConnectionOpenedInternal(@NonNull InputConnection inputConnection,
            @NonNull EditorInfo editorInfo, @Nullable Handler handler) {}

    public void onInputConnectionClosedInternal() {}

    public boolean checkInputConnectionProxy(View view) {
        return false;
    }

    public void createContextMenu(ContextMenu menu) {
        throw new RuntimeException("Stub!");
    }

    protected ContextMenuInfo getContextMenuInfo() {
        return null;
    }

    protected void onCreateContextMenu(ContextMenu menu) {
    }

    public boolean onTrackballEvent(MotionEvent event) {
        return false;
    }

    public boolean onGenericMotionEvent(MotionEvent event) {
        return false;
    }

    public boolean onHoverEvent(MotionEvent event) {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty
    public boolean isHovered() {
        throw new RuntimeException("Stub!");
    }

    public void setHovered(boolean hovered) {
        throw new RuntimeException("Stub!");
    }

    public void onHoverChanged(boolean hovered) {
    }

    protected boolean handleScrollBarDragging(MotionEvent event) {
        throw new RuntimeException("Stub!");
    }

    public boolean onTouchEvent(MotionEvent event) {
        throw new RuntimeException("Stub!");
    }

    public boolean isInScrollingContainer() {
        throw new RuntimeException("Stub!");
    }

    public void cancelLongPress() {
        throw new RuntimeException("Stub!");
    }

    public void setTouchDelegate(TouchDelegate delegate) {
        throw new RuntimeException("Stub!");
    }

    public TouchDelegate getTouchDelegate() {
        throw new RuntimeException("Stub!");
    }

    public final void requestUnbufferedDispatch(MotionEvent event) {
        throw new RuntimeException("Stub!");
    }

    public final void requestUnbufferedDispatch(int source) {
        throw new RuntimeException("Stub!");
    }

    void setFlags(int flags, int mask) {
        throw new RuntimeException("Stub!");
    }

    public void bringToFront() {
        throw new RuntimeException("Stub!");
    }

    void disableRotaryScrollFeedback() {
        throw new RuntimeException("Stub!");
    }

    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        throw new RuntimeException("Stub!");
    }

    public interface OnScrollChangeListener {
        void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY);
    }

    public interface OnLayoutChangeListener {
        void onLayoutChange(View v, int left, int top, int right, int bottom,
            int oldLeft, int oldTop, int oldRight, int oldBottom);
    }

    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    }

    protected void dispatchDraw(@NonNull Canvas canvas) {

    }

    public final ViewParent getParent() {
        throw new RuntimeException("Stub!");
    }

    public void setScrollX(int value) {
        throw new RuntimeException("Stub!");
    }

    public void setScrollY(int value) {
        throw new RuntimeException("Stub!");
    }

    public final int getScrollX() {
        throw new RuntimeException("Stub!");
    }

    public final int getScrollY() {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "layout")
    public final int getWidth() {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "layout")
    public final int getHeight() {
        throw new RuntimeException("Stub!");
    }

    public void getDrawingRect(Rect outRect) {
        throw new RuntimeException("Stub!");
    }

    public final int getMeasuredWidth() {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "measurement", flagMapping = {
            @ViewDebug.FlagToString(mask = MEASURED_STATE_MASK, equals = MEASURED_STATE_TOO_SMALL,
                    name = "MEASURED_STATE_TOO_SMALL"),
    })
    public final int getMeasuredWidthAndState() {
        throw new RuntimeException("Stub!");
    }

    public final int getMeasuredHeight() {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "measurement", flagMapping = {
            @ViewDebug.FlagToString(mask = MEASURED_STATE_MASK, equals = MEASURED_STATE_TOO_SMALL,
                    name = "MEASURED_STATE_TOO_SMALL"),
    })
    public final int getMeasuredHeightAndState() {
        throw new RuntimeException("Stub!");
    }

    public final int getMeasuredState() {
        throw new RuntimeException("Stub!");
    }

    public Matrix getMatrix() {
        throw new RuntimeException("Stub!");
    }

    public final boolean hasIdentityMatrix() {
        throw new RuntimeException("Stub!");
    }

    void ensureTransformationInfo() {
        throw new RuntimeException("Stub!");
    }

    public final Matrix getInverseMatrix() {
        throw new RuntimeException("Stub!");
    }

    public float getCameraDistance() {
        throw new RuntimeException("Stub!");
    }

    public void setCameraDistance(float distance) {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "drawing")
    public float getRotation() {
        throw new RuntimeException("Stub!");
    }

    public void setRotation(float rotation) {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "drawing")
    public float getRotationY() {
        throw new RuntimeException("Stub!");
    }

    public void setRotationY(float rotationY) {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "drawing")
    public float getRotationX() {
        throw new RuntimeException("Stub!");
    }

    public void setRotationX(float rotationX) {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "drawing")
    public float getScaleX() {
        throw new RuntimeException("Stub!");
    }

    public void setScaleX(float scaleX) {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "drawing")
    public float getScaleY() {
        throw new RuntimeException("Stub!");
    }

    public void setScaleY(float scaleY) {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "drawing")
    public float getPivotX() {
        throw new RuntimeException("Stub!");
    }

    public void setPivotX(float pivotX) {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "drawing")
    public float getPivotY() {
        throw new RuntimeException("Stub!");
    }

    public void setPivotY(float pivotY) {
        throw new RuntimeException("Stub!");
    }

    public boolean isPivotSet() {
        throw new RuntimeException("Stub!");
    }

    public void resetPivot() {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "drawing")
    public float getAlpha() {
        throw new RuntimeException("Stub!");
    }

    public void forceHasOverlappingRendering(boolean hasOverlappingRendering) {
        throw new RuntimeException("Stub!");
    }

    public final boolean getHasOverlappingRendering() {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "drawing")
    public boolean hasOverlappingRendering() {
        throw new RuntimeException("Stub!");
    }

    public void setAlpha(@FloatRange(from=0.0, to=1.0) float alpha) {
        throw new RuntimeException("Stub!");
    }

    boolean setAlphaNoInvalidation(float alpha) {
        throw new RuntimeException("Stub!");
    }

    void setAlphaInternal(float alpha) {
        throw new RuntimeException("Stub!");
    }

    public void setTransitionAlpha(float alpha) {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "drawing")
    public float getTransitionAlpha() {
        throw new RuntimeException("Stub!");
    }

    public void setForceDarkAllowed(boolean allow) {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "drawing")
    public boolean isForceDarkAllowed() {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.CapturedViewProperty
    public final int getTop() {
        throw new RuntimeException("Stub!");
    }

    public final void setTop(int top) {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.CapturedViewProperty
    public final int getBottom() {
        throw new RuntimeException("Stub!");
    }

    public boolean isDirty() {
        throw new RuntimeException("Stub!");
    }

    public final void setBottom(int bottom) {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.CapturedViewProperty
    public final int getLeft() {
        throw new RuntimeException("Stub!");
    }

    public final void setLeft(int left) {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.CapturedViewProperty
    public final int getRight() {
        throw new RuntimeException("Stub!");
    }

    public final void setRight(int right) {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "drawing")
    public float getX() {
        throw new RuntimeException("Stub!");
    }

    public void setX(float x) {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "drawing")
    public float getY() {
        throw new RuntimeException("Stub!");
    }

    public void setY(float y) {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "drawing")
    public float getZ() {
        throw new RuntimeException("Stub!");
    }

    public void setZ(float z) {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "drawing")
    public float getElevation() {
        throw new RuntimeException("Stub!");
    }

    public void setElevation(float elevation) {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "drawing")
    public float getTranslationX() {
        throw new RuntimeException("Stub!");
    }

    public void setTranslationX(float translationX) {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "drawing")
    public float getTranslationY() {
        throw new RuntimeException("Stub!");
    }

    public void setTranslationY(float translationY) {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "drawing")
    public float getTranslationZ() {
        throw new RuntimeException("Stub!");
    }

    public void setTranslationZ(float translationZ) {
        throw new RuntimeException("Stub!");
    }

    public void setAnimationMatrix(@Nullable Matrix matrix) {
        throw new RuntimeException("Stub!");
    }

    @Nullable
    public Matrix getAnimationMatrix() {
        throw new RuntimeException("Stub!");
    }

    public StateListAnimator getStateListAnimator() {
        throw new RuntimeException("Stub!");
    }

    public void setStateListAnimator(StateListAnimator stateListAnimator) {
        throw new RuntimeException("Stub!");
    }

    public final boolean getClipToOutline() {
        throw new RuntimeException("Stub!");
    }

    public void setClipToOutline(boolean clipToOutline) {
        throw new RuntimeException("Stub!");
    }


    public void setOutlineProvider(ViewOutlineProvider provider) {
        throw new RuntimeException("Stub!");
    }

    public ViewOutlineProvider getOutlineProvider() {
        throw new RuntimeException("Stub!");
    }

    public void invalidateOutline() {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "drawing")
    public boolean hasShadow() {
        throw new RuntimeException("Stub!");
    }

    public void setOutlineSpotShadowColor(@ColorInt int color) {
        throw new RuntimeException("Stub!");
    }

    public @ColorInt int getOutlineSpotShadowColor() {
        throw new RuntimeException("Stub!");
    }

    public void setOutlineAmbientShadowColor(@ColorInt int color) {
        throw new RuntimeException("Stub!");
    }

    public @ColorInt int getOutlineAmbientShadowColor() {
        throw new RuntimeException("Stub!");
    }


    /** @hide */
    public void setRevealClip(boolean shouldClip, float x, float y, float radius) {
        throw new RuntimeException("Stub!");
    }

    public void getHitRect(Rect outRect) {
        throw new RuntimeException("Stub!");
    }

    /*package*/ final boolean pointInView(float localX, float localY) {
        throw new RuntimeException("Stub!");
    }

    public boolean pointInView(float localX, float localY, float slop) {
        throw new RuntimeException("Stub!");
    }

    public void getFocusedRect(Rect r) {
        throw new RuntimeException("Stub!");
    }

    public boolean getGlobalVisibleRect(Rect r, Point globalOffset) {
        throw new RuntimeException("Stub!");
    }

    public final boolean getGlobalVisibleRect(Rect r) {
        throw new RuntimeException("Stub!");
    }

    public final boolean getLocalVisibleRect(Rect r) {
        throw new RuntimeException("Stub!");
    }

    public void offsetTopAndBottom(int offset) {
        throw new RuntimeException("Stub!");
    }

    public void offsetLeftAndRight(int offset) {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(deepExport = true, prefix = "layout_")
    public ViewGroup.LayoutParams getLayoutParams() {
        throw new RuntimeException("Stub!");
    }

    public void setLayoutParams(ViewGroup.LayoutParams params) {
        throw new RuntimeException("Stub!");
    }

    public void resolveLayoutParams() {
        throw new RuntimeException("Stub!");
    }

    public void scrollTo(int x, int y) {
        throw new RuntimeException("Stub!");
    }

    public void scrollBy(int x, int y) {
        throw new RuntimeException("Stub!");
    }

    protected boolean awakenScrollBars() {
        throw new RuntimeException("Stub!");
    }

    protected boolean awakenScrollBars(int startDelay) {
        throw new RuntimeException("Stub!");
    }

    protected boolean awakenScrollBars(int startDelay, boolean invalidate) {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public void invalidate(Rect dirty) {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public void invalidate(int l, int t, int r, int b) {
        throw new RuntimeException("Stub!");
    }

    public void invalidate() {
        throw new RuntimeException("Stub!");
    }

    public void invalidate(boolean invalidateCache) {
        throw new RuntimeException("Stub!");
    }

    void invalidateInternal(int l, int t, int r, int b, boolean invalidateCache,
            boolean fullInvalidate) {
        throw new RuntimeException("Stub!");
    }

    void invalidateViewProperty(boolean invalidateParent, boolean forceRedraw) {
        throw new RuntimeException("Stub!");
    }

    protected void damageInParent() {
        throw new RuntimeException("Stub!");
    }

    protected void invalidateParentCaches() {
        throw new RuntimeException("Stub!");
    }

    protected void invalidateParentIfNeeded() {
        throw new RuntimeException("Stub!");
    }

    protected void invalidateParentIfNeededAndWasQuickRejected() {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "drawing")
    public boolean isOpaque() {
        throw new RuntimeException("Stub!");
    }

    protected void computeOpaqueFlags() {
        throw new RuntimeException("Stub!");
    }

    protected boolean hasOpaqueScrollbars() {
        throw new RuntimeException("Stub!");
    }

    public Handler getHandler() {
        throw new RuntimeException("Stub!");
    }

    // public ViewRootImpl getViewRootImpl() {
    //     throw new RuntimeException("Stub!");
    // }

    // public ThreadedRenderer getThreadedRenderer() {
    //     throw new RuntimeException("Stub!");
    // }

    public boolean post(Runnable action) {
        throw new RuntimeException("Stub!");
    }

    public boolean postDelayed(Runnable action, long delayMillis) {
        throw new RuntimeException("Stub!");
    }

    public void postOnAnimation(Runnable action) {
        throw new RuntimeException("Stub!");
    }

    public void postOnAnimationDelayed(Runnable action, long delayMillis) {
        throw new RuntimeException("Stub!");
    }

    public boolean removeCallbacks(Runnable action) {
        throw new RuntimeException("Stub!");
    }

    public void postInvalidate() {
        throw new RuntimeException("Stub!");
    }

    public void postInvalidate(int left, int top, int right, int bottom) {
        throw new RuntimeException("Stub!");
    }

    public void postInvalidateDelayed(long delayMilliseconds) {
        throw new RuntimeException("Stub!");
    }

    public void postInvalidateDelayed(long delayMilliseconds, int left, int top,
            int right, int bottom) {
        throw new RuntimeException("Stub!");
    }

    public void postInvalidateOnAnimation() {
        throw new RuntimeException("Stub!");
    }

    public void postInvalidateOnAnimation(int left, int top, int right, int bottom) {
        throw new RuntimeException("Stub!");
    }

    public void computeScroll() {
    }

    public boolean isHorizontalFadingEdgeEnabled() {
        throw new RuntimeException("Stub!");
    }

    public void setHorizontalFadingEdgeEnabled(boolean horizontalFadingEdgeEnabled) {
        throw new RuntimeException("Stub!");
    }

    public boolean isVerticalFadingEdgeEnabled() {
        throw new RuntimeException("Stub!");
    }

    public void setVerticalFadingEdgeEnabled(boolean verticalFadingEdgeEnabled) {
        throw new RuntimeException("Stub!");
    }

    public int getFadingEdge() {
        throw new RuntimeException("Stub!");
    }

    public int getFadingEdgeLength() {
        throw new RuntimeException("Stub!");
    }

    protected float getTopFadingEdgeStrength() {
        throw new RuntimeException("Stub!");
    }

    protected float getBottomFadingEdgeStrength() {
        throw new RuntimeException("Stub!");
    }

    protected float getLeftFadingEdgeStrength() {
        throw new RuntimeException("Stub!");
    }

    protected float getRightFadingEdgeStrength() {
        throw new RuntimeException("Stub!");
    }

    public boolean isHorizontalScrollBarEnabled() {
        throw new RuntimeException("Stub!");
    }

    public void setHorizontalScrollBarEnabled(boolean horizontalScrollBarEnabled) {
        throw new RuntimeException("Stub!");
    }

    public boolean isVerticalScrollBarEnabled() {
        throw new RuntimeException("Stub!");
    }

    public void setVerticalScrollBarEnabled(boolean verticalScrollBarEnabled) {
        throw new RuntimeException("Stub!");
    }

    protected void recomputePadding() {
        throw new RuntimeException("Stub!");
    }

    public void setScrollbarFadingEnabled(boolean fadeScrollbars) {
        throw new RuntimeException("Stub!");
    }

    public boolean isScrollbarFadingEnabled() {
        throw new RuntimeException("Stub!");
    }

    public int getScrollBarDefaultDelayBeforeFade() {
        throw new RuntimeException("Stub!");
    }

    public void setScrollBarDefaultDelayBeforeFade(int scrollBarDefaultDelayBeforeFade) {
        throw new RuntimeException("Stub!");
    }

    public int getScrollBarFadeDuration() {
        throw new RuntimeException("Stub!");
    }

    public void setScrollBarFadeDuration(int scrollBarFadeDuration) {
        throw new RuntimeException("Stub!");
    }

    public int getScrollBarSize() {
        throw new RuntimeException("Stub!");
    }

    public void setScrollBarSize(int scrollBarSize) {
        throw new RuntimeException("Stub!");
    }

    public void setScrollBarStyle(@ScrollBarStyle int style) {
        throw new RuntimeException("Stub!");
    }

    @ScrollBarStyle
    public int getScrollBarStyle() {
        throw new RuntimeException("Stub!");
    }

    protected int computeHorizontalScrollRange() {
        throw new RuntimeException("Stub!");
    }

    protected int computeHorizontalScrollOffset() {
        throw new RuntimeException("Stub!");
    }

    protected int computeHorizontalScrollExtent() {
        throw new RuntimeException("Stub!");
    }

    protected int computeVerticalScrollRange() {
        throw new RuntimeException("Stub!");
    }

    protected int computeVerticalScrollOffset() {
        throw new RuntimeException("Stub!");
    }

    protected int computeVerticalScrollExtent() {
        throw new RuntimeException("Stub!");
    }

    public boolean canScrollHorizontally(int direction) {
        throw new RuntimeException("Stub!");
    }

    public boolean canScrollVertically(int direction) {
        throw new RuntimeException("Stub!");
    }

    void getScrollIndicatorBounds(@NonNull Rect out) {
        throw new RuntimeException("Stub!");
    }

    protected final void onDrawScrollBars(@NonNull Canvas canvas) {
        throw new RuntimeException("Stub!");
    }

    protected boolean isVerticalScrollBarHidden() {
        return false;
    }

    protected void onDrawHorizontalScrollBar(@NonNull Canvas canvas, Drawable scrollBar,
            int l, int t, int r, int b) {
        throw new RuntimeException("Stub!");
    }

    protected void onDrawVerticalScrollBar(@NonNull Canvas canvas, Drawable scrollBar,
            int l, int t, int r, int b) {
        throw new RuntimeException("Stub!");
    }

    protected void onDraw(@NonNull Canvas canvas) {
    }

    /*
     * Caller is responsible for calling requestLayout if necessary.
     * (This allows addViewInLayout to not request a new layout.)
     */
    void assignParent(ViewParent parent) {
        throw new RuntimeException("Stub!");
    }

    @CallSuper
    protected void onAttachedToWindow() {
        throw new RuntimeException("Stub!");
    }

    public boolean resolveRtlPropertiesIfNeeded() {
        throw new RuntimeException("Stub!");
    }

    @TestApi
    public void resetRtlProperties() {
        throw new RuntimeException("Stub!");
    }

    void dispatchScreenStateChanged(int screenState) {
        throw new RuntimeException("Stub!");
    }

    public void onScreenStateChanged(int screenState) {
    }

    void dispatchMovedToDisplay(Display display, Configuration config) {
        throw new RuntimeException("Stub!");
    }

    public void onMovedToDisplay(int displayId, Configuration config) {
    }

    public void onRtlPropertiesChanged(@ResolvedLayoutDir int layoutDirection) {
    }

    public boolean resolveLayoutDirection() {
        throw new RuntimeException("Stub!");
    }

    public boolean canResolveLayoutDirection() {
        throw new RuntimeException("Stub!");
    }

    @TestApi
    public void resetResolvedLayoutDirection() {
        throw new RuntimeException("Stub!");
    }

    public boolean isLayoutDirectionInherited() {
        throw new RuntimeException("Stub!");
    }

    public boolean isLayoutDirectionResolved() {
        throw new RuntimeException("Stub!");
    }

    boolean isPaddingResolved() {
        throw new RuntimeException("Stub!");
    }

    public void resolvePadding() {
        throw new RuntimeException("Stub!");
    }

    @TestApi
    public void resetResolvedPadding() {
        throw new RuntimeException("Stub!");
    }

    void resetResolvedPaddingInternal() {
        throw new RuntimeException("Stub!");
    }

    @CallSuper
    protected void onDetachedFromWindow() {
    }

    @CallSuper
    protected void onDetachedFromWindowInternal() {
        throw new RuntimeException("Stub!");
    }

    public IBinder getWindowToken() {
        throw new RuntimeException("Stub!");
    }

    public WindowId getWindowId() {
        throw new RuntimeException("Stub!");
    }

    public IBinder getApplicationWindowToken() {
        throw new RuntimeException("Stub!");
    }

    public Display getDisplay() {
        throw new RuntimeException("Stub!");
    }

    // /*package*/ IWindowSession getWindowSession() {
    //     throw new RuntimeException("Stub!");
    // }

    // protected IWindow getWindow() {
    //     throw new RuntimeException("Stub!");
    // }

    int combineVisibility(int vis1, int vis2) {
        throw new RuntimeException("Stub!");
    }

    public void fakeFocusAfterAttachingToWindow() {
        throw new RuntimeException("Stub!");
    }

    // void dispatchAttachedToWindow(AttachInfo info, int visibility) {
    //     throw new RuntimeException("Stub!");
    // }

    void dispatchDetachedFromWindow() {
        throw new RuntimeException("Stub!");
    }

    public final void cancelPendingInputEvents() {
        throw new RuntimeException("Stub!");
    }

    void dispatchCancelPendingInputEvents() {
        throw new RuntimeException("Stub!");
    }

    public void onCancelPendingInputEvents() {
        throw new RuntimeException("Stub!");
    }

    public void saveHierarchyState(SparseArray<Parcelable> container) {
        throw new RuntimeException("Stub!");
    }

    protected void dispatchSaveInstanceState(SparseArray<Parcelable> container) {
        throw new RuntimeException("Stub!");
    }

    @CallSuper
    @Nullable protected Parcelable onSaveInstanceState() {
        throw new RuntimeException("Stub!");
    }

    public void restoreHierarchyState(SparseArray<Parcelable> container) {
        throw new RuntimeException("Stub!");
    }

    protected void dispatchRestoreInstanceState(SparseArray<Parcelable> container) {
        throw new RuntimeException("Stub!");
    }

    @CallSuper
    protected void onRestoreInstanceState(Parcelable state) {
        throw new RuntimeException("Stub!");
    }

    public long getDrawingTime() {
        throw new RuntimeException("Stub!");
    }

    public void setDuplicateParentStateEnabled(boolean enabled) {
        throw new RuntimeException("Stub!");
    }

    public boolean isDuplicateParentStateEnabled() {
        throw new RuntimeException("Stub!");
    }

    public void setLayerType(@LayerType int layerType, @Nullable Paint paint) {
        throw new RuntimeException("Stub!");
    }

    // public void setRenderEffect(@Nullable RenderEffect renderEffect) {
    //     throw new RuntimeException("Stub!");
    // }

    // public void setBackdropRenderEffect(@Nullable RenderEffect renderEffect) {
    //     throw new RuntimeException("Stub!");
    // }

    public void setLayerPaint(@Nullable Paint paint) {
        throw new RuntimeException("Stub!");
    }

    @LayerType
    public int getLayerType() {
        throw new RuntimeException("Stub!");
    }

    public void buildLayer() {
        throw new RuntimeException("Stub!");
    }

    public boolean probablyHasInput() {
        throw new RuntimeException("Stub!");
    }

    @CallSuper
    protected void destroyHardwareResources() {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public void setDrawingCacheEnabled(boolean enabled) {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    @ViewDebug.ExportedProperty(category = "drawing")
    public boolean isDrawingCacheEnabled() {
        throw new RuntimeException("Stub!");
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void outputDirtyFlags(String indent, boolean clear, int clearMask) {
        throw new RuntimeException("Stub!");
    }

    protected void dispatchGetDisplayList() {}

    public boolean canHaveDisplayList() {
        throw new RuntimeException("Stub!");
    }

    @NonNull
    public RenderNode updateDisplayListIfDirty() {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public Bitmap getDrawingCache() {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public Bitmap getDrawingCache(boolean autoScale) {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public void destroyDrawingCache() {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public void setDrawingCacheBackgroundColor(@ColorInt int color) {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    @ColorInt
    public int getDrawingCacheBackgroundColor() {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public void buildDrawingCache() {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public void buildDrawingCache(boolean autoScale) {
        throw new RuntimeException("Stub!");
    }

    // public Bitmap createSnapshot(ViewDebug.CanvasProvider canvasProvider, boolean skipChildren) {
    //     throw new RuntimeException("Stub!");
    // }

    public boolean isInEditMode() {
        return false;
    }

    protected boolean isPaddingOffsetRequired() {
        return false;
    }

    protected int getLeftPaddingOffset() {
        return 0;
    }

    protected int getRightPaddingOffset() {
        return 0;
    }

    protected int getTopPaddingOffset() {
        return 0;
    }

    protected int getBottomPaddingOffset() {
        return 0;
    }

    protected int getFadeTop(boolean offsetRequired) {
        throw new RuntimeException("Stub!");
    }

    protected int getFadeHeight(boolean offsetRequired) {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "drawing")
    public boolean isHardwareAccelerated() {
        throw new RuntimeException("Stub!");
    }

    public void setClipBounds(Rect clipBounds) {
        throw new RuntimeException("Stub!");
    }

    public Rect getClipBounds() {
        throw new RuntimeException("Stub!");
    }


    public boolean getClipBounds(Rect outRect) {
        throw new RuntimeException("Stub!");
    }

    void setDisplayListProperties(RenderNode renderNode) {
        throw new RuntimeException("Stub!");
    }

    protected final boolean drawsWithRenderNode(@NonNull Canvas canvas) {
        throw new RuntimeException("Stub!");
    }

    boolean draw(@NonNull Canvas canvas, ViewGroup parent, long drawingTime) {
        throw new RuntimeException("Stub!");
    }

    static Paint getDebugPaint() {
        throw new RuntimeException("Stub!");
    }

    final int dipsToPixels(int dips) {
        throw new RuntimeException("Stub!");
    }

    @CallSuper
    public void draw(@NonNull Canvas canvas) {
        throw new RuntimeException("Stub!");
    }

    void setBackgroundBounds() {
        throw new RuntimeException("Stub!");
    }

    public ViewOverlay getOverlay() {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "drawing")
    @ColorInt
    public int getSolidColor() {
        return 0;
    }

    public boolean isLayoutRequested() {
        throw new RuntimeException("Stub!");
    }

    public static boolean isLayoutModeOptical(Object o) {
        throw new RuntimeException("Stub!");
    }

    public static void setTraceLayoutSteps(boolean traceLayoutSteps) {
        throw new RuntimeException("Stub!");
    }

    public static void setTracedRequestLayoutClassClass(String s) {
        throw new RuntimeException("Stub!");
    }

    @SuppressWarnings({"unchecked"})
    public void layout(int l, int t, int r, int b) {
        throw new RuntimeException("Stub!");
    }

    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    }

    protected boolean setFrame(int left, int top, int right, int bottom) {
        throw new RuntimeException("Stub!");
    }

    public final void setLeftTopRightBottom(int left, int top, int right, int bottom) {
        throw new RuntimeException("Stub!");
    }

    @CallSuper
    protected void onFinishInflate() {
    }

    public Resources getResources() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void invalidateDrawable(@NonNull Drawable drawable) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
        throw new RuntimeException("Stub!");
    }

    public void unscheduleDrawable(Drawable who) {
        throw new RuntimeException("Stub!");
    }

    protected void resolveDrawables() {
        throw new RuntimeException("Stub!");
    }

    boolean areDrawablesResolved() {
        throw new RuntimeException("Stub!");
    }

    public void onResolveDrawables(@ResolvedLayoutDir int layoutDirection) {
    }

    @TestApi
    protected void resetResolvedDrawables() {
        throw new RuntimeException("Stub!");
    }

    void resetResolvedDrawablesInternal() {
        throw new RuntimeException("Stub!");
    }

    @CallSuper
    protected boolean verifyDrawable(@NonNull Drawable who) {
        throw new RuntimeException("Stub!");
    }

    @CallSuper
    protected void drawableStateChanged() {
        throw new RuntimeException("Stub!");
    }

    @CallSuper
    public void drawableHotspotChanged(float x, float y) {
        throw new RuntimeException("Stub!");
    }

    public void dispatchDrawableHotspotChanged(float x, float y) {
    }

    public void refreshDrawableState() {
        throw new RuntimeException("Stub!");
    }

    @TestApi
    public boolean isDefaultFocusHighlightNeeded(Drawable background, Drawable foreground) {
        throw new RuntimeException("Stub!");
    }

    public final int[] getDrawableState() {
        throw new RuntimeException("Stub!");
    }

    protected int[] onCreateDrawableState(int extraSpace) {
        throw new RuntimeException("Stub!");
    }

    protected static int[] mergeDrawableStates(int[] baseState, int[] additionalState) {
        throw new RuntimeException("Stub!");
    }

    @CallSuper
    public void jumpDrawablesToCurrentState() {
        throw new RuntimeException("Stub!");
    }

    public void setBackgroundColor(@ColorInt int color) {
        throw new RuntimeException("Stub!");
    }

    public void setBackgroundResource(@DrawableRes int resid) {
        throw new RuntimeException("Stub!");
    }

    public void setBackground(Drawable background) {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public void setBackgroundDrawable(Drawable background) {
        throw new RuntimeException("Stub!");
    }

    public Drawable getBackground() {
        throw new RuntimeException("Stub!");
    }

    public void setBackgroundTintList(@Nullable ColorStateList tint) {
        throw new RuntimeException("Stub!");
    }

    @Nullable
    public ColorStateList getBackgroundTintList() {
        throw new RuntimeException("Stub!");
    }

    public void setBackgroundTintMode(@Nullable PorterDuff.Mode tintMode) {
        throw new RuntimeException("Stub!");
    }

    public void setBackgroundTintBlendMode(@Nullable BlendMode blendMode) {
        throw new RuntimeException("Stub!");
    }

    @Nullable
    public PorterDuff.Mode getBackgroundTintMode() {
        throw new RuntimeException("Stub!");
    }

    public @Nullable BlendMode getBackgroundTintBlendMode() {
        throw new RuntimeException("Stub!");
    }

    public Drawable getForeground() {
        throw new RuntimeException("Stub!");
    }

    public void setForeground(Drawable foreground) {
        throw new RuntimeException("Stub!");
    }

    public boolean isForegroundInsidePadding() {
        throw new RuntimeException("Stub!");
    }

    public int getForegroundGravity() {
        throw new RuntimeException("Stub!");
    }

    public void setForegroundGravity(int gravity) {
        throw new RuntimeException("Stub!");
    }

    public void setForegroundTintList(@Nullable ColorStateList tint) {
        throw new RuntimeException("Stub!");
    }

    @Nullable
    public ColorStateList getForegroundTintList() {
        throw new RuntimeException("Stub!");
    }

    public void setForegroundTintMode(@Nullable PorterDuff.Mode tintMode) {
        throw new RuntimeException("Stub!");
    }

    public void setForegroundTintBlendMode(@Nullable BlendMode blendMode) {
        throw new RuntimeException("Stub!");
    }

    @Nullable
    public PorterDuff.Mode getForegroundTintMode() {
        throw new RuntimeException("Stub!");
    }

    public @Nullable BlendMode getForegroundTintBlendMode() {
        throw new RuntimeException("Stub!");
    }

    public void onDrawForeground(@NonNull Canvas canvas) {
        throw new RuntimeException("Stub!");
    }

    public void setPadding(int left, int top, int right, int bottom) {
        throw new RuntimeException("Stub!");
    }

    protected void internalSetPadding(int left, int top, int right, int bottom) {
        throw new RuntimeException("Stub!");
    }

    public void setPaddingRelative(int start, int top, int end, int bottom) {
        throw new RuntimeException("Stub!");
    }

    @LayoutRes
    public int getSourceLayoutResId() {
        throw new RuntimeException("Stub!");
    }

    public int getPaddingTop() {
        throw new RuntimeException("Stub!");
    }

    public int getPaddingBottom() {
        throw new RuntimeException("Stub!");
    }

    public int getPaddingLeft() {
        throw new RuntimeException("Stub!");
    }

    public int getPaddingStart() {
        throw new RuntimeException("Stub!");
    }

    public int getPaddingRight() {
        throw new RuntimeException("Stub!");
    }

    public int getPaddingEnd() {
        throw new RuntimeException("Stub!");
    }

    public boolean isPaddingRelative() {
        throw new RuntimeException("Stub!");
    }

    Insets computeOpticalInsets() {
        throw new RuntimeException("Stub!");
    }

    public void resetPaddingToInitialValues() {
        throw new RuntimeException("Stub!");
    }

    public Insets getOpticalInsets() {
        throw new RuntimeException("Stub!");
    }

    public void setOpticalInsets(Insets insets) {
        throw new RuntimeException("Stub!");
    }

    public void setSelected(boolean selected) {
        throw new RuntimeException("Stub!");
    }

    protected void dispatchSetSelected(boolean selected) {
    }

    @ViewDebug.ExportedProperty
    public boolean isSelected() {
        throw new RuntimeException("Stub!");
    }

    public void setActivated(boolean activated) {
        throw new RuntimeException("Stub!");
    }

    protected void dispatchSetActivated(boolean activated) {
    }

    @ViewDebug.ExportedProperty
    public boolean isActivated() {
        throw new RuntimeException("Stub!");
    }

    public ViewTreeObserver getViewTreeObserver() {
        throw new RuntimeException("Stub!");
    }

    public View getRootView() {
        throw new RuntimeException("Stub!");
    }

    public boolean toGlobalMotionEvent(MotionEvent ev) {
        throw new RuntimeException("Stub!");
    }

    public boolean toLocalMotionEvent(MotionEvent ev) {
        throw new RuntimeException("Stub!");
    }

    public void transformMatrixToGlobal(@NonNull Matrix matrix) {
        throw new RuntimeException("Stub!");
    }

    public void transformMatrixToLocal(@NonNull Matrix matrix) {
        throw new RuntimeException("Stub!");
    }

    public void transformMatrixRootToLocal(@NonNull Matrix matrix) {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "layout", indexMapping = {
            @ViewDebug.IntToString(from = 0, to = "x"),
            @ViewDebug.IntToString(from = 1, to = "y")
    })
    public int[] getLocationOnScreen() {
        throw new RuntimeException("Stub!");
    }

    public void getLocationOnScreen(@Size(2) int[] outLocation) {
        throw new RuntimeException("Stub!");
    }

    public void getLocationInWindow(@Size(2) int[] outLocation) {
        throw new RuntimeException("Stub!");
    }

    /** @hide */
    public void transformFromViewToWindowSpace(@Size(2) int[] inOutLocation) {
        throw new RuntimeException("Stub!");
    }

    protected <T extends View> T findViewTraversal(@IdRes int id) {
        throw new RuntimeException("Stub!");
    }

    protected <T extends View> T findViewWithTagTraversal(Object tag) {
        throw new RuntimeException("Stub!");
    }

    protected <T extends View> T findViewByPredicateTraversal(Predicate<View> predicate,
            View childToSkip) {
        throw new RuntimeException("Stub!");
    }

    // Strictly speaking this should be marked as @Nullable but the nullability of the return value
    // is deliberately left unspecified as idiomatically correct code can make assumptions either
    // way based on local context, e.g. layout specification.
    public final <T extends View> T findViewById(@IdRes int id) {
        throw new RuntimeException("Stub!");
    }

    @NonNull
    public final <T extends View> T requireViewById(@IdRes int id) {
        throw new RuntimeException("Stub!");
    }

    public <T extends View> T findViewByAccessibilityIdTraversal(int accessibilityId) {
        throw new RuntimeException("Stub!");
    }

    public <T extends View> T findViewByAutofillIdTraversal(int autofillId) {
        throw new RuntimeException("Stub!");
    }


    public void findAutofillableViewsByTraversal(@NonNull List<View> autofillableViews) {
        throw new RuntimeException("Stub!");
    }

    public final <T extends View> T findViewWithTag(Object tag) {
        throw new RuntimeException("Stub!");
    }

    public final <T extends View> T findViewByPredicate(Predicate<View> predicate) {
        throw new RuntimeException("Stub!");
    }

    public final <T extends View> T findViewByPredicateInsideOut(
            View start, Predicate<View> predicate) {
        throw new RuntimeException("Stub!");
    }

    public void setId(@IdRes int id) {
        throw new RuntimeException("Stub!");
    }

    @TestApi
    public void setIsRootNamespace(boolean isRoot) {
        throw new RuntimeException("Stub!");
    }

    public boolean isRootNamespace() {
        throw new RuntimeException("Stub!");
    }

    @IdRes
    @ViewDebug.CapturedViewProperty
    public int getId() {
        throw new RuntimeException("Stub!");
    }

    public long getUniqueDrawingId() {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty
    public Object getTag() {
        throw new RuntimeException("Stub!");
    }

    public void setTag(final Object tag) {
        throw new RuntimeException("Stub!");
    }

    public Object getTag(int key) {
        throw new RuntimeException("Stub!");
    }

    public void setTag(int key, final Object tag) {
        throw new RuntimeException("Stub!");
    }

    public void setTagInternal(int key, Object tag) {
        throw new RuntimeException("Stub!");
    }

    public void debug() {
        throw new RuntimeException("Stub!");
    }

    protected void debug(int depth) {
        throw new RuntimeException("Stub!");
    }

    protected static String debugIndent(int depth) {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "layout")
    public int getBaseline() {
        throw new RuntimeException("Stub!");
    }

    public boolean isInLayout() {
        throw new RuntimeException("Stub!");
    }

    @CallSuper
    public void requestLayout() {
        throw new RuntimeException("Stub!");
    }

    public void forceLayout() {
        throw new RuntimeException("Stub!");
    }

    public final void measure(int widthMeasureSpec, int heightMeasureSpec) {
        throw new RuntimeException("Stub!");
    }

    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        throw new RuntimeException("Stub!");
    }

    protected final void setMeasuredDimension(int measuredWidth, int measuredHeight) {
        throw new RuntimeException("Stub!");
    }

    public static int combineMeasuredStates(int curState, int newState) {
        throw new RuntimeException("Stub!");
    }

    public static int resolveSize(int size, int measureSpec) {
        throw new RuntimeException("Stub!");
    }

    public static int resolveSizeAndState(int size, int measureSpec, int childMeasuredState) {
        throw new RuntimeException("Stub!");
    }

    public static int getDefaultSize(int size, int measureSpec) {
        throw new RuntimeException("Stub!");
    }

    protected int getSuggestedMinimumHeight() {
        throw new RuntimeException("Stub!");
    }

    protected int getSuggestedMinimumWidth() {
        throw new RuntimeException("Stub!");
    }

    public int getMinimumHeight() {
        throw new RuntimeException("Stub!");
    }

    public void setMinimumHeight(int minHeight) {
        throw new RuntimeException("Stub!");
    }

    public int getMinimumWidth() {
        throw new RuntimeException("Stub!");
    }

    public void setMinimumWidth(int minWidth) {
        throw new RuntimeException("Stub!");
    }

    public Animation getAnimation() {
        throw new RuntimeException("Stub!");
    }

    public void startAnimation(Animation animation) {
        throw new RuntimeException("Stub!");
    }

    public void clearAnimation() {
        throw new RuntimeException("Stub!");
    }

    public void setAnimation(Animation animation) {
        throw new RuntimeException("Stub!");
    }

    @CallSuper
    protected void onAnimationStart() {
        throw new RuntimeException("Stub!");
    }

    @CallSuper
    protected void onAnimationEnd() {
        throw new RuntimeException("Stub!");
    }

    protected boolean onSetAlpha(int alpha) {
        return false;
    }

    public boolean gatherTransparentRegion(@Nullable Region region) {
        throw new RuntimeException("Stub!");
    }

    public void playSoundEffect(int soundConstant) {
        throw new RuntimeException("Stub!");
    }

    public boolean performHapticFeedback(int feedbackConstant) {
        throw new RuntimeException("Stub!");
    }

    public boolean performHapticFeedback(int feedbackConstant, int flags) {
        throw new RuntimeException("Stub!");
    }

    public void performHapticFeedbackForInputDevice(int feedbackConstant, int inputDeviceId,
            int inputSource, int flags) {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public void setSystemUiVisibility(int visibility) {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public int getSystemUiVisibility() {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public int getWindowSystemUiVisibility() {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public void onWindowSystemUiVisibilityChanged(int visible) {
    }

    @Deprecated
    public void dispatchWindowSystemUiVisiblityChanged(int visible) {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public void setOnSystemUiVisibilityChangeListener(OnSystemUiVisibilityChangeListener l) {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public void dispatchSystemUiVisibilityChanged(int visibility) {
        throw new RuntimeException("Stub!");
    }

    boolean updateLocalSystemUiVisibility(int localValue, int localChanges) {
        throw new RuntimeException("Stub!");
    }

    /** @hide */
    public void setDisabledSystemUiVisibility(int flags) {
        throw new RuntimeException("Stub!");
    }

    public void onSystemBarAppearanceChanged(int appearance) {
    }

    public static class DragShadowBuilder {
        public DragShadowBuilder(View view) {
            throw new RuntimeException("Stub!");
        }

        public DragShadowBuilder() {
            throw new RuntimeException("Stub!");
        }

        @SuppressWarnings({"JavadocReference"})
        final public View getView() {
            throw new RuntimeException("Stub!");
        }

        public void onProvideShadowMetrics(Point outShadowSize, Point outShadowTouchPoint) {
            throw new RuntimeException("Stub!");
        }

        public void onDrawShadow(@NonNull Canvas canvas) {
            throw new RuntimeException("Stub!");
        }
    }

    @Deprecated
    public final boolean startDrag(ClipData data, DragShadowBuilder shadowBuilder,
                                   Object myLocalState, int flags) {
        throw new RuntimeException("Stub!");
    }

    public final boolean startDragAndDrop(ClipData data, DragShadowBuilder shadowBuilder,
            Object myLocalState, int flags) {
        throw new RuntimeException("Stub!");
    }

    static boolean hasActivityPendingIntents(ClipData data) {
        throw new RuntimeException("Stub!");
    }

    static void cleanUpPendingIntents(ClipData data) {
        throw new RuntimeException("Stub!");
    }

    void setAccessibilityDragStarted(boolean started) {
        throw new RuntimeException("Stub!");
    }

    public final void cancelDragAndDrop() {
        throw new RuntimeException("Stub!");
    }

    public final void updateDragShadow(DragShadowBuilder shadowBuilder) {
        throw new RuntimeException("Stub!");
    }

    public final boolean startMovingTask(float startX, float startY) {
        throw new RuntimeException("Stub!");
    }

    public void finishMovingTask() {
        throw new RuntimeException("Stub!");
    }

    public boolean onDragEvent(DragEvent event) {
        throw new RuntimeException("Stub!");
    }

    // Dispatches ACTION_DRAG_ENTERED and ACTION_DRAG_EXITED events for pre-Nougat apps.
    boolean dispatchDragEnterExitInPreN(DragEvent event) {
        throw new RuntimeException("Stub!");
    }

    public boolean dispatchDragEvent(DragEvent event) {
        throw new RuntimeException("Stub!");
    }

    final boolean callDragEventHandler(DragEvent event) {
        throw new RuntimeException("Stub!");
    }

    boolean canAcceptDrag() {
        throw new RuntimeException("Stub!");
    }

    void sendWindowContentChangedAccessibilityEvent(int changeType) {
        throw new RuntimeException("Stub!");
    }

    public void onCloseSystemDialogs(String reason) {
    }

    public void applyDrawableToTransparentRegion(Drawable dr, Region region) {
        throw new RuntimeException("Stub!");
    }

    public static View inflate(Context context, @LayoutRes int resource, ViewGroup root) {
        throw new RuntimeException("Stub!");
    }

    @SuppressWarnings({"UnusedParameters"})
    protected boolean overScrollBy(int deltaX, int deltaY,
            int scrollX, int scrollY,
            int scrollRangeX, int scrollRangeY,
            int maxOverScrollX, int maxOverScrollY,
            boolean isTouchEvent) {
        throw new RuntimeException("Stub!");
    }

    protected void onOverScrolled(int scrollX, int scrollY,
            boolean clampedX, boolean clampedY) {
        // Intentionally empty.
    }

    public int getOverScrollMode() {
        throw new RuntimeException("Stub!");
    }

    public void setOverScrollMode(int overScrollMode) {
        throw new RuntimeException("Stub!");
    }

    public void setNestedScrollingEnabled(boolean enabled) {
        throw new RuntimeException("Stub!");
    }

    public boolean isNestedScrollingEnabled() {
        throw new RuntimeException("Stub!");
    }

    public boolean startNestedScroll(int axes) {
        throw new RuntimeException("Stub!");
    }

    public void stopNestedScroll() {
        throw new RuntimeException("Stub!");
    }

    public boolean hasNestedScrollingParent() {
        throw new RuntimeException("Stub!");
    }

    public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed,
            int dxUnconsumed, int dyUnconsumed, @Nullable @Size(2) int[] offsetInWindow) {
        throw new RuntimeException("Stub!");
    }

    public boolean dispatchNestedPreScroll(int dx, int dy,
            @Nullable @Size(2) int[] consumed, @Nullable @Size(2) int[] offsetInWindow) {
        throw new RuntimeException("Stub!");
    }

    public boolean dispatchNestedFling(float velocityX, float velocityY, boolean consumed) {
        throw new RuntimeException("Stub!");
    }

    public boolean dispatchNestedPreFling(float velocityX, float velocityY) {
        throw new RuntimeException("Stub!");
    }

    protected float getVerticalScrollFactor() {
        throw new RuntimeException("Stub!");
    }

    protected float getHorizontalScrollFactor() {
        throw new RuntimeException("Stub!");
    }

    public int getRawTextDirection() {
        throw new RuntimeException("Stub!");
    }

    public void setTextDirection(int textDirection) {
        throw new RuntimeException("Stub!");
    }

    public int getTextDirection() {
        throw new RuntimeException("Stub!");
    }

    public boolean resolveTextDirection() {
        throw new RuntimeException("Stub!");
    }

    public boolean canResolveTextDirection() {
        throw new RuntimeException("Stub!");
    }

    @TestApi
    public void resetResolvedTextDirection() {
        throw new RuntimeException("Stub!");
    }

    public boolean isTextDirectionInherited() {
        throw new RuntimeException("Stub!");
    }

    public boolean isTextDirectionResolved() {
        throw new RuntimeException("Stub!");
    }

    @TextAlignment
    public int getRawTextAlignment() {
        throw new RuntimeException("Stub!");
    }

    public void setTextAlignment(@TextAlignment int textAlignment) {
        throw new RuntimeException("Stub!");
    }

    @TextAlignment
    public int getTextAlignment() {
        throw new RuntimeException("Stub!");
    }

    public boolean resolveTextAlignment() {
        throw new RuntimeException("Stub!");
    }

    public boolean canResolveTextAlignment() {
        throw new RuntimeException("Stub!");
    }

    @TestApi
    public void resetResolvedTextAlignment() {
        throw new RuntimeException("Stub!");
    }

    public boolean isTextAlignmentInherited() {
        throw new RuntimeException("Stub!");
    }

    public boolean isTextAlignmentResolved() {
        throw new RuntimeException("Stub!");
    }

    public static int generateViewId() {
        throw new RuntimeException("Stub!");
    }

    public void captureTransitioningViews(List<View> transitioningViews) {
        throw new RuntimeException("Stub!");
    }

    public void findNamedViews(Map<String, View> namedElements) {
        throw new RuntimeException("Stub!");
    }

    public PointerIcon onResolvePointerIcon(MotionEvent event, int pointerIndex) {
        throw new RuntimeException("Stub!");
    }

    public void setPointerIcon(PointerIcon pointerIcon) {
        throw new RuntimeException("Stub!");
    }

    public PointerIcon getPointerIcon() {
        throw new RuntimeException("Stub!");
    }

    public boolean hasPointerCapture() {
        throw new RuntimeException("Stub!");
    }

    public void requestPointerCapture() {
        throw new RuntimeException("Stub!");
    }


    public void releasePointerCapture() {
        throw new RuntimeException("Stub!");
    }

    @CallSuper
    public void onPointerCaptureChange(boolean hasCapture) {
    }

    public void dispatchPointerCaptureChanged(boolean hasCapture) {
        throw new RuntimeException("Stub!");
    }

    public boolean onCapturedPointerEvent(MotionEvent event) {
        return false;
    }

    public interface OnCapturedPointerListener {
        boolean onCapturedPointer(View view, MotionEvent event);
    }

    public void setOnCapturedPointerListener(OnCapturedPointerListener l) {
        throw new RuntimeException("Stub!");
    }

    // Properties
    //
    public static final Property<View, Float> ALPHA = new FloatProperty<View>("alpha") {
        @Override
        public void setValue(View object, float value) {
            object.setAlpha(value);
        }

        @Override
        public Float get(View object) {
            return object.getAlpha();
        }
    };

    public static final Property<View, Float> TRANSLATION_X = new FloatProperty<View>("translationX") {
        @Override
        public void setValue(View object, float value) {
            object.setTranslationX(value);
        }

                @Override
        public Float get(View object) {
            return object.getTranslationX();
        }
    };

    public static final Property<View, Float> TRANSLATION_Y = new FloatProperty<View>("translationY") {
        @Override
        public void setValue(View object, float value) {
            object.setTranslationY(value);
        }

        @Override
        public Float get(View object) {
            return object.getTranslationY();
        }
    };

    public static final Property<View, Float> TRANSLATION_Z = new FloatProperty<View>("translationZ") {
        @Override
        public void setValue(View object, float value) {
            object.setTranslationZ(value);
        }

        @Override
        public Float get(View object) {
            return object.getTranslationZ();
        }
    };

    public static final Property<View, Float> X = new FloatProperty<View>("x") {
        @Override
        public void setValue(View object, float value) {
            object.setX(value);
        }

        @Override
        public Float get(View object) {
            return object.getX();
        }
    };

    public static final Property<View, Float> Y = new FloatProperty<View>("y") {
        @Override
        public void setValue(View object, float value) {
            object.setY(value);
        }

        @Override
        public Float get(View object) {
            return object.getY();
        }
    };

    public static final Property<View, Float> Z = new FloatProperty<View>("z") {
        @Override
        public void setValue(View object, float value) {
            object.setZ(value);
        }

        @Override
        public Float get(View object) {
            return object.getZ();
        }
    };

    public static final Property<View, Float> ROTATION = new FloatProperty<View>("rotation") {
        @Override
        public void setValue(View object, float value) {
            object.setRotation(value);
        }

        @Override
        public Float get(View object) {
            return object.getRotation();
        }
    };

    public static final Property<View, Float> ROTATION_X = new FloatProperty<View>("rotationX") {
        @Override
        public void setValue(View object, float value) {
            object.setRotationX(value);
        }

        @Override
        public Float get(View object) {
            return object.getRotationX();
        }
    };

    public static final Property<View, Float> ROTATION_Y = new FloatProperty<View>("rotationY") {
        @Override
        public void setValue(View object, float value) {
            object.setRotationY(value);
        }

        @Override
        public Float get(View object) {
            return object.getRotationY();
        }
    };

    public static final Property<View, Float> SCALE_X = new FloatProperty<View>("scaleX") {
        @Override
        public void setValue(View object, float value) {
            object.setScaleX(value);
        }

        @Override
        public Float get(View object) {
            return object.getScaleX();
        }
    };

    public static final Property<View, Float> SCALE_Y = new FloatProperty<View>("scaleY") {
        @Override
        public void setValue(View object, float value) {
            object.setScaleY(value);
        }

        @Override
        public Float get(View object) {
            return object.getScaleY();
        }
    };

    public static class MeasureSpec {
        private static final int MODE_SHIFT = 30;

        /** @hide */
        @IntDef({UNSPECIFIED, EXACTLY, AT_MOST})
        @Retention(RetentionPolicy.SOURCE)
        public @interface MeasureSpecMode {}

        public static final int UNSPECIFIED = 0 << MODE_SHIFT;

        public static final int EXACTLY     = 1 << MODE_SHIFT;

        public static final int AT_MOST     = 2 << MODE_SHIFT;

        public static int makeMeasureSpec(@IntRange(from = 0, to = (1 << MeasureSpec.MODE_SHIFT) - 1) int size,
                                          @MeasureSpecMode int mode) {
            throw new RuntimeException("Stub!");
        }

        public static int makeSafeMeasureSpec(int size, int mode) {
            throw new RuntimeException("Stub!");
        }

        @MeasureSpecMode
        public static int getMode(int measureSpec) {
            throw new RuntimeException("Stub!");
        }

        public static int getSize(int measureSpec) {
            throw new RuntimeException("Stub!");
        }

        static int adjust(int measureSpec, int delta) {
            throw new RuntimeException("Stub!");
        }

        public static String toString(int measureSpec) {
            throw new RuntimeException("Stub!");
        }
    }

    public ViewPropertyAnimator animate() {
        throw new RuntimeException("Stub!");
    }

    public final void setTransitionName(String transitionName) {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty
    public String getTransitionName() {
        throw new RuntimeException("Stub!");
    }

    public void requestKeyboardShortcuts(List<KeyboardShortcutGroup> data, int deviceId) {
        // Do nothing.
    }

    public interface OnKeyListener {
        boolean onKey(View v, int keyCode, KeyEvent event);
    }

    public interface OnUnhandledKeyEventListener {
        boolean onUnhandledKeyEvent(View v, KeyEvent event);
    }

    public interface OnTouchListener {
        boolean onTouch(View v, MotionEvent event);
    }

    public interface OnHoverListener {
        boolean onHover(View v, MotionEvent event);
    }

    public interface OnGenericMotionListener {
        boolean onGenericMotion(View v, MotionEvent event);
    }

    public interface OnLongClickListener {
        boolean onLongClick(View v);

        default boolean onLongClickUseDefaultHapticFeedback(@NonNull View v) {
            return true;
        }
    }

    public interface OnDragListener {
        boolean onDrag(View v, DragEvent event);
    }

    public interface OnFocusChangeListener {
        void onFocusChange(View v, boolean hasFocus);
    }

    public interface OnClickListener {
        void onClick(View v);
    }

    public interface OnContextClickListener {
        boolean onContextClick(View v);
    }

    public interface OnCreateContextMenuListener {
        void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo);
    }

    @Deprecated
    public interface OnSystemUiVisibilityChangeListener {
        public void onSystemUiVisibilityChange(int visibility);
    }

    public interface OnAttachStateChangeListener {
        public void onViewAttachedToWindow(@NonNull View v);
        public void onViewDetachedFromWindow(@NonNull View v);
    }

    public interface OnApplyWindowInsetsListener {
        public @NonNull WindowInsets onApplyWindowInsets(@NonNull View v,
                @NonNull WindowInsets insets);
    }

    public static class BaseSavedState extends AbsSavedState {
        static final int START_ACTIVITY_REQUESTED_WHO_SAVED = 0b1;
        static final int IS_AUTOFILLED = 0b10;
        static final int AUTOFILL_ID = 0b100;

        // Flags that describe what data in this state is valid
        int mSavedData;
        String mStartActivityRequestWhoSaved;
        boolean mIsAutofilled;
        boolean mHideHighlight;
        int mAutofillViewId;

        public BaseSavedState(Parcel source) {
            this(source, null);
        }

        public BaseSavedState(Parcel source, ClassLoader loader) {
            super(source, loader);
            mSavedData = source.readInt();
            mStartActivityRequestWhoSaved = source.readString();
            mIsAutofilled = source.readBoolean();
            mHideHighlight = source.readBoolean();
            mAutofillViewId = source.readInt();
        }

        public BaseSavedState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);

            out.writeInt(mSavedData);
            out.writeString(mStartActivityRequestWhoSaved);
            out.writeBoolean(mIsAutofilled);
            out.writeBoolean(mHideHighlight);
            out.writeInt(mAutofillViewId);
        }

        public static final @android.annotation.NonNull Parcelable.Creator<BaseSavedState> CREATOR
                = new Parcelable.ClassLoaderCreator<BaseSavedState>() {
            @Override
            public BaseSavedState createFromParcel(Parcel in) {
                return new BaseSavedState(in);
            }

            @Override
            public BaseSavedState createFromParcel(Parcel in, ClassLoader loader) {
                return new BaseSavedState(in, loader);
            }

            @Override
            public BaseSavedState[] newArray(int size) {
                return new BaseSavedState[size];
            }
        };
    }

    public static class AccessibilityDelegate {

        public void sendAccessibilityEvent(@NonNull View host, int eventType) {
            throw new RuntimeException("Stub!");
        }

        public boolean performAccessibilityAction(@NonNull View host, int action,
                @Nullable Bundle args) {
            throw new RuntimeException("Stub!");
        }

        public void sendAccessibilityEventUnchecked(@NonNull View host,
                @NonNull AccessibilityEvent event) {
            throw new RuntimeException("Stub!");
        }

        public boolean dispatchPopulateAccessibilityEvent(@NonNull View host,
                @NonNull AccessibilityEvent event) {
            throw new RuntimeException("Stub!");
        }

        public void onPopulateAccessibilityEvent(@NonNull View host,
                @NonNull AccessibilityEvent event) {
            throw new RuntimeException("Stub!");
        }

        public void onInitializeAccessibilityEvent(@NonNull View host,
                @NonNull AccessibilityEvent event) {
            throw new RuntimeException("Stub!");
        }

        public void onInitializeAccessibilityNodeInfo(@NonNull View host,
                @NonNull AccessibilityNodeInfo info) {
            throw new RuntimeException("Stub!");
        }

        public void addExtraDataToAccessibilityNodeInfo(@NonNull View host,
                @NonNull AccessibilityNodeInfo info, @NonNull String extraDataKey,
                @Nullable Bundle arguments) {
            throw new RuntimeException("Stub!");
        }

        public boolean onRequestSendAccessibilityEvent(@NonNull ViewGroup host, @NonNull View child,
                @NonNull AccessibilityEvent event) {
            throw new RuntimeException("Stub!");
        }

        public @Nullable AccessibilityNodeProvider getAccessibilityNodeProvider(
                @NonNull View host) {
            return null;
        }

        public AccessibilityNodeInfo createAccessibilityNodeInfo(@NonNull View host) {
            throw new RuntimeException("Stub!");
        }
    }

    @ScrollCaptureHint
    public int getScrollCaptureHint() {
        throw new RuntimeException("Stub!");
    }

    public void setScrollCaptureHint(@ScrollCaptureHint int hint) {
        throw new RuntimeException("Stub!");
    }

    // public final void setScrollCaptureCallback(@Nullable ScrollCaptureCallback callback) {
    //     throw new RuntimeException("Stub!");
    // }

    // /** {@hide} */
    // @Nullable
    // public ScrollCaptureCallback createScrollCaptureCallbackInternal(@NonNull Rect localVisibleRect,
    //         @NonNull Point windowOffset) {
    //     throw new RuntimeException("Stub!");
    // }

    // public void dispatchScrollCaptureSearch(
    //         @NonNull Rect localVisibleRect, @NonNull Point windowOffset,
    //         @NonNull Consumer<ScrollCaptureTarget> targets) {
    //     throw new RuntimeException("Stub!");
    // }

    // public void onScrollCaptureSearch(@NonNull Rect localVisibleRect,
    //         @NonNull Point windowOffset, @NonNull Consumer<ScrollCaptureTarget> targets) {
    //     throw new RuntimeException("Stub!");
    // }

    // /** {@hide} */
    // public void encode(@NonNull ViewHierarchyEncoder stream) {
    //     throw new RuntimeException("Stub!");
    // }

    // /** {@hide} */
    // @CallSuper
    // protected void encodeProperties(@NonNull ViewHierarchyEncoder stream) {
    //     throw new RuntimeException("Stub!");
    // }

    boolean shouldDrawRoundScrollbar() {
        throw new RuntimeException("Stub!");
    }

    public void setTooltipText(@Nullable CharSequence tooltipText) {
        throw new RuntimeException("Stub!");
    }

    public void setTooltip(@Nullable CharSequence tooltipText) {
        throw new RuntimeException("Stub!");
    }

    @Nullable
    public CharSequence getTooltipText() {
        throw new RuntimeException("Stub!");
    }

    @Nullable
    public CharSequence getTooltip() {
        throw new RuntimeException("Stub!");
    }

    void hideTooltip() {
        throw new RuntimeException("Stub!");
    }

    boolean dispatchTooltipHoverEvent(MotionEvent event) {
        throw new RuntimeException("Stub!");
    }

    void handleTooltipKey(KeyEvent event) {
        throw new RuntimeException("Stub!");
    }

    @TestApi
    public View getTooltipView() {
        throw new RuntimeException("Stub!");
    }

    @TestApi
    public static boolean isDefaultFocusHighlightEnabled() {
        throw new RuntimeException("Stub!");
    }

    View dispatchUnhandledKeyEvent(KeyEvent evt) {
        throw new RuntimeException("Stub!");
    }

    boolean onUnhandledKeyEvent(@NonNull KeyEvent event) {
        throw new RuntimeException("Stub!");
    }

    boolean hasUnhandledKeyListener() {
        throw new RuntimeException("Stub!");
    }

    public void addOnUnhandledKeyEventListener(OnUnhandledKeyEventListener listener) {
        throw new RuntimeException("Stub!");
    }

    public void removeOnUnhandledKeyEventListener(OnUnhandledKeyEventListener listener) {
        throw new RuntimeException("Stub!");
    }

    protected void setDetached(boolean detached) {
        throw new RuntimeException("Stub!");
    }

    public void setIsCredential(boolean isCredential) {
        throw new RuntimeException("Stub!");
    }

    public boolean isCredential() {
        throw new RuntimeException("Stub!");
    }

    // TODO(316208691): Revive following removed API docs.
    // @see EditorInfo#setStylusHandwritingEnabled(boolean)
    public void setAutoHandwritingEnabled(boolean enabled) {
        throw new RuntimeException("Stub!");
    }

    public boolean isAutoHandwritingEnabled() {
        throw new RuntimeException("Stub!");
    }

    public boolean isStylusHandwritingAvailable() {
        throw new RuntimeException("Stub!");
    }

    // public void onCreateViewTranslationRequest(@NonNull int[] supportedFormats,
    //         @NonNull Consumer<ViewTranslationRequest> requestsCollector) {
    // }

    // @SuppressLint("NullableCollection")
    // public void onCreateVirtualViewTranslationRequests(@NonNull long[] virtualIds,
    //         @NonNull int[] supportedFormats,
    //         @NonNull Consumer<ViewTranslationRequest> requestsCollector) {
    //     // no-op
    // }

    // @Nullable
    // public ViewTranslationCallback getViewTranslationCallback() {
    //     throw new RuntimeException("Stub!");
    // }

    // public void setViewTranslationCallback(@NonNull ViewTranslationCallback callback) {
    //     throw new RuntimeException("Stub!");
    // }

    // public void clearViewTranslationCallback() {
    //     throw new RuntimeException("Stub!");
    // }

    // @Nullable
    // public ViewTranslationResponse getViewTranslationResponse() {
    //     throw new RuntimeException("Stub!");
    // }

    // public void onViewTranslationResponse(@NonNull ViewTranslationResponse response) {
    //     throw new RuntimeException("Stub!");
    // }

    // public void clearViewTranslationResponse() {
    //     throw new RuntimeException("Stub!");
    // }

    // public void onVirtualViewTranslationResponses(
    //         @NonNull LongSparseArray<ViewTranslationResponse> response) {
    //     // no-op
    // }

    // public void dispatchCreateViewTranslationRequest(@NonNull Map<AutofillId, long[]> viewIds,
    //         @NonNull int[] supportedFormats,
    //         @NonNull TranslationCapability capability,
    //         @NonNull List<ViewTranslationRequest> requests) {
    //     throw new RuntimeException("Stub!");
    // }

    // public void generateDisplayHash(@NonNull String hashAlgorithm,
    //         @Nullable Rect bounds, @NonNull Executor executor,
    //         @NonNull DisplayHashResultCallback callback) {
    //     throw new RuntimeException("Stub!");
    // }

    /**
     * The AttachedSurfaceControl itself is not a View, it is just the interface to the
     * windowing-system object that contains the entire view hierarchy.
     * For the root View of a given hierarchy see {@link #getRootView}.

     * @return The {@link android.view.AttachedSurfaceControl} interface for this View.
     * This will only return a non-null value when called between {@link #onAttachedToWindow}
     * and {@link #onDetachedFromWindow}.
     */
    // public @Nullable AttachedSurfaceControl getRootSurfaceControl() {
    //     throw new RuntimeException("Stub!");
    // }

    protected int calculateFrameRateCategory() {
        throw new RuntimeException("Stub!");
    }

    protected void votePreferredFrameRate() {
        throw new RuntimeException("Stub!");
    }

    public void setFrameContentVelocity(float pixelsPerSecond) {
        throw new RuntimeException("Stub!");
    }

    public float getFrameContentVelocity() {
        throw new RuntimeException("Stub!");
    }

    public void setRequestedFrameRate(float frameRate) {
        throw new RuntimeException("Stub!");
    }

    public float getRequestedFrameRate() {
        throw new RuntimeException("Stub!");
    }

    void overrideFrameRate(float frameRate, boolean forceOverride) {
        throw new RuntimeException("Stub!");
    }

    void setForcedOverrideFrameRateFlag(boolean forcedOverride) {
        throw new RuntimeException("Stub!");
    }

    boolean getForcedOverrideFrameRateFlag() {
        throw new RuntimeException("Stub!");
    }

    void setSelfRequestedFrameRateFlag(boolean forcedOverride) {
        throw new RuntimeException("Stub!");
    }

    boolean getSelfRequestedFrameRateFlag() {
        throw new RuntimeException("Stub!");
    }

    // public void reportAppJankStats(@NonNull AppJankStats appJankStats) {
    //     throw new RuntimeException("Stub!");
    // }

    // public @Nullable JankTracker getJankTracker() {
    //     throw new RuntimeException("Stub!");
    // }
}
