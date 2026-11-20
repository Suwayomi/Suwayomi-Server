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

import android.animation.LayoutTransition;
import android.annotation.CallSuper;
import android.annotation.IdRes;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.TestApi;
import android.annotation.UiThread;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.WindowInsetsAnimation.Bounds;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.view.animation.Transformation;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillManager;
import android.webkit.WebView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

@UiThread
public abstract class ViewGroup extends View implements ViewParent, ViewManager {
    protected ArrayList<View> mDisappearingChildren;

    protected OnHierarchyChangeListener mOnHierarchyChangeListener;

    // The last child of this ViewGroup which held focus within the current cluster
    View mFocusedInCluster;

    RectF mInvalidateRegion;

    Transformation mInvalidationTransformation;

    protected int mGroupFlags;


    // When set, ViewGroup invalidates only the child's rectangle
    // Set by default
    static final int FLAG_CLIP_CHILDREN = 0x1;

    // When set, dispatchDraw() will invoke invalidate(); this is set by drawChild() when
    // a child needs to be invalidated and FLAG_OPTIMIZE_INVALIDATE is set
    static final int FLAG_INVALIDATE_REQUIRED  = 0x4;

    // When set, there is either no layout animation on the ViewGroup or the layout
    // animation is over
    // Set by default
    static final int FLAG_ANIMATION_DONE = 0x10;

    // When set, this ViewGroup converts calls to invalidate(Rect) to invalidate() during a
    // layout animation; this avoid clobbering the hierarchy
    // Automatically set when the layout animation starts, depending on the animation's
    // characteristics
    static final int FLAG_OPTIMIZE_INVALIDATE = 0x80;

    // When set, the next call to drawChild() will clear mChildTransformation's matrix
    static final int FLAG_CLEAR_TRANSFORMATION = 0x100;

    protected static final int FLAG_USE_CHILD_DRAWING_ORDER = 0x400;

    protected static final int FLAG_SUPPORT_STATIC_TRANSFORMATIONS = 0x800;

    // UNUSED FLAG VALUE: 0x1000;

    public static final int FOCUS_BEFORE_DESCENDANTS = 0x20000;

    public static final int FOCUS_AFTER_DESCENDANTS = 0x40000;

    public static final int FOCUS_BLOCK_DESCENDANTS = 0x60000;

    protected static final int FLAG_DISALLOW_INTERCEPT = 0x80000;

    static final int FLAG_IS_TRANSITION_GROUP = 0x1000000;

    static final int FLAG_IS_TRANSITION_GROUP_SET = 0x2000000;

    static final int FLAG_TOUCHSCREEN_BLOCKS_FOCUS = 0x4000000;

    protected int mPersistentDrawingCache;

    @Deprecated
    public static final int PERSISTENT_NO_CACHE = 0x0;

    @Deprecated
    public static final int PERSISTENT_ANIMATION_CACHE = 0x1;

    @Deprecated
    public static final int PERSISTENT_SCROLLING_CACHE = 0x2;

    @Deprecated
    public static final int PERSISTENT_ALL_CACHES = 0x3;

    // Layout Modes

    public static final int LAYOUT_MODE_CLIP_BOUNDS = 0;

    public static final int LAYOUT_MODE_OPTICAL_BOUNDS = 1;

    /** @hide */
    public static int LAYOUT_MODE_DEFAULT = LAYOUT_MODE_CLIP_BOUNDS;

    protected static final int CLIP_TO_PADDING_MASK = 0;

    // Whether layout calls are currently being suppressed, controlled by calls to
    // suppressLayout()
    boolean mSuppressLayout = false;

    // Used to draw cached views
    Paint mCachePaint;

    int mChildUnhandledKeyListeners = 0;

    public ViewGroup(Context context) {
        this(context, null);
    }

    public ViewGroup(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ViewGroup(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public ViewGroup(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public int getDescendantFocusability() {
        throw new RuntimeException("Stub!");
    }

    public void setDescendantFocusability(int focusability) {
        throw new RuntimeException("Stub!");
    }

    @Override
    void handleFocusGainInternal(int direction, Rect previouslyFocusedRect) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        throw new RuntimeException("Stub!");
    }

    void setDefaultFocus(View child) {
        throw new RuntimeException("Stub!");
    }

    void clearDefaultFocus(View child) {
        throw new RuntimeException("Stub!");
    }

    @Override
    boolean hasDefaultFocus() {
        throw new RuntimeException("Stub!");
    }

    void clearFocusedInCluster(View child) {
        throw new RuntimeException("Stub!");
    }

    void clearFocusedInCluster() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void focusableViewAvailable(View v) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public boolean showContextMenuForChild(View originalView) {
        throw new RuntimeException("Stub!");
    }

    public final boolean isShowingContextMenuWithCoords() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public boolean showContextMenuForChild(View originalView, float x, float y) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public ActionMode startActionModeForChild(View originalView, ActionMode.Callback callback) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public ActionMode startActionModeForChild(
            View originalView, ActionMode.Callback callback, int type) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public boolean dispatchActivityResult(
            String who, int requestCode, int resultCode, Intent data) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public View focusSearch(View focused, int direction) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
        return false;
    }

    @Override
    public boolean requestSendAccessibilityEvent(View child, AccessibilityEvent event) {
        throw new RuntimeException("Stub!");
    }

    public boolean onRequestSendAccessibilityEvent(View child, AccessibilityEvent event) {
        throw new RuntimeException("Stub!");
    }

    public boolean onRequestSendAccessibilityEventInternal(View child, AccessibilityEvent event) {
        return true;
    }

    @Override
    public void childHasTransientStateChanged(View child, boolean childHasTransientState) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public boolean hasTransientState() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public boolean dispatchUnhandledMove(View focused, int direction) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void clearChildFocus(View child) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void clearFocus() {
        throw new RuntimeException("Stub!");
    }

    @Override
    void unFocus(View focused) {
        throw new RuntimeException("Stub!");
    }

    public View getFocusedChild() {
        throw new RuntimeException("Stub!");
    }

    View getDeepestFocusedChild() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public boolean hasFocus() {
        throw new RuntimeException("Stub!");
    }

    /*
     * (non-Javadoc)
     *
     * @see android.view.View#findFocus()
     */
    @Override
    public View findFocus() {
        throw new RuntimeException("Stub!");
    }

    @Override
    boolean hasFocusable(boolean allowAutoFocus, boolean dispatchExplicit) {
        throw new RuntimeException("Stub!");
    }

    boolean hasFocusableChild(boolean dispatchExplicit) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void addFocusables(ArrayList<View> views, int direction, int focusableMode) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void addKeyboardNavigationClusters(Collection<View> views, int direction) {
        throw new RuntimeException("Stub!");
    }

    public void setTouchscreenBlocksFocus(boolean touchscreenBlocksFocus) {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "focus")
    public boolean getTouchscreenBlocksFocus() {
        throw new RuntimeException("Stub!");
    }

    boolean shouldBlockFocusForTouchscreen() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void findViewsWithText(ArrayList<View> outViews, CharSequence text, int flags) {
        throw new RuntimeException("Stub!");
    }

    /** @hide */
    @Override
    public View findViewByAccessibilityIdTraversal(int accessibilityId) {
        throw new RuntimeException("Stub!");
    }

    /** @hide */
    @Override
    public View findViewByAutofillIdTraversal(int autofillId) {
        throw new RuntimeException("Stub!");
    }

    /** @hide */
    @Override
    public void findAutofillableViewsByTraversal(@NonNull List<View> autofillableViews) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void dispatchWindowFocusChanged(boolean hasFocus) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void addTouchables(ArrayList<View> views) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void makeOptionalFitsSystemWindows() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void makeFrameworkOptionalFitsSystemWindows() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void dispatchDisplayHint(int hint) {
        throw new RuntimeException("Stub!");
    }

    protected void onChildVisibilityChanged(View child, int oldVisibility, int newVisibility) {
        throw new RuntimeException("Stub!");
    }

    @Override
    protected void dispatchVisibilityChanged(View changedView, int visibility) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void dispatchWindowVisibilityChanged(int visibility) {
        throw new RuntimeException("Stub!");
    }

    @Override
    boolean dispatchVisibilityAggregated(boolean isVisible) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void dispatchConfigurationChanged(Configuration newConfig) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void recomputeViewAttributes(View child) {
        throw new RuntimeException("Stub!");
    }

    // @Override
    // void dispatchCollectViewAttributes(AttachInfo attachInfo, int visibility) {
    //     throw new RuntimeException("Stub!");
    // }

    @Override
    public void bringChildToFront(View child) {
        throw new RuntimeException("Stub!");
    }

    @Override
    boolean dispatchDragEnterExitInPreN(DragEvent event) {
        throw new RuntimeException("Stub!");
    }

    // TODO: Write real docs
    @Override
    public boolean dispatchDragEvent(DragEvent event) {
        throw new RuntimeException("Stub!");
    }

    // Find the frontmost child view that lies under the given point, and calculate
    // the position within its own local coordinate system.
    View findFrontmostDroppableChildAt(float x, float y, PointF outLocalPoint) {
        throw new RuntimeException("Stub!");
    }

    boolean notifyChildOfDragStart(View child) {
        throw new RuntimeException("Stub!");
    }

    @Override
    @Deprecated
    public void dispatchWindowSystemUiVisiblityChanged(int visible) {
        throw new RuntimeException("Stub!");
    }

    @Override
    @Deprecated
    public void dispatchSystemUiVisibilityChanged(int visible) {
        throw new RuntimeException("Stub!");
    }

    @Override
    boolean updateLocalSystemUiVisibility(int localValue, int localChanges) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public boolean dispatchKeyEventPreIme(KeyEvent event) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public boolean dispatchKeyShortcutEvent(KeyEvent event) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public boolean dispatchTrackballEvent(MotionEvent event) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public boolean dispatchCapturedPointerEvent(MotionEvent event) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void dispatchPointerCaptureChanged(boolean hasCapture) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public PointerIcon onResolvePointerIcon(MotionEvent event, int pointerIndex) {
        throw new RuntimeException("Stub!");
    }

    @SuppressWarnings({"ConstantConditions"})
    @Override
    protected boolean dispatchHoverEvent(MotionEvent event) {
        throw new RuntimeException("Stub!");
    }

    @Override
    boolean dispatchTooltipHoverEvent(MotionEvent event) {
        throw new RuntimeException("Stub!");
    }

    /** @hide */
    @Override
    protected boolean hasHoveredChild() {
        throw new RuntimeException("Stub!");
    }

    /** @hide */
    @Override
    protected boolean pointInHoveredChild(MotionEvent event) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void addChildrenForAccessibility(ArrayList<View> outChildren) {
        throw new RuntimeException("Stub!");
    }

    public boolean onInterceptHoverEvent(MotionEvent event) {
        throw new RuntimeException("Stub!");
    }

    @Override
    protected boolean dispatchGenericPointerEvent(MotionEvent event) {
        throw new RuntimeException("Stub!");
    }

    @Override
    protected boolean dispatchGenericFocusedEvent(MotionEvent event) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        throw new RuntimeException("Stub!");
    }

    public ArrayList<View> buildTouchDispatchChildList() {
        throw new RuntimeException("Stub!");
    }

    protected boolean isTransformedTouchPointInView(float x, float y, View child,
            PointF outLocalPoint) {
        throw new RuntimeException("Stub!");
    }

    public void transformPointToViewLocal(float[] point, View child) {
        throw new RuntimeException("Stub!");
    }

    public void setMotionEventSplittingEnabled(boolean split) {
        throw new RuntimeException("Stub!");
    }

    public boolean isMotionEventSplittingEnabled() {
        throw new RuntimeException("Stub!");
    }

    public boolean isTransitionGroup() {
        throw new RuntimeException("Stub!");
    }

    public void setTransitionGroup(boolean isTransitionGroup) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        throw new RuntimeException("Stub!");
    }

    public boolean onInterceptTouchEvent(MotionEvent ev) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
        throw new RuntimeException("Stub!");
    }

    @SuppressWarnings({"ConstantConditions"})
    protected boolean onRequestFocusInDescendants(int direction,
            Rect previouslyFocusedRect) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public boolean restoreDefaultFocus() {
        throw new RuntimeException("Stub!");
    }

    @TestApi
    @Override
    public boolean restoreFocusInCluster(@FocusRealDirection int direction) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public boolean restoreFocusNotInCluster() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void dispatchStartTemporaryDetach() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void dispatchFinishTemporaryDetach() {
        throw new RuntimeException("Stub!");
    }

    // @Override
    // void dispatchAttachedToWindow(AttachInfo info, int visibility) {
    //     throw new RuntimeException("Stub!");
    // }

    @Override
    void dispatchScreenStateChanged(int screenState) {
        throw new RuntimeException("Stub!");
    }

    @Override
    void dispatchMovedToDisplay(Display display, Configuration config) {
        throw new RuntimeException("Stub!");
    }

    /** @hide */
    @Override
    public boolean dispatchPopulateAccessibilityEventInternal(AccessibilityEvent event) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void dispatchProvideStructure(ViewStructure structure) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void dispatchProvideAutofillStructure(ViewStructure structure,
            @AutofillFlags int flags) {
        throw new RuntimeException("Stub!");
    }

    /** @hide */
    @Override
    public void dispatchProvideContentCaptureStructure() {
        throw new RuntimeException("Stub!");
    }

    /** @hide */
    @Override
    public void resetSubtreeAutofillIds() {
        throw new RuntimeException("Stub!");
    }

    /** @hide */
    @Override
    public void onInitializeAccessibilityNodeInfoInternal(AccessibilityNodeInfo info) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void addExtraDataToAccessibilityNodeInfo(@NonNull AccessibilityNodeInfo info,
            @NonNull String extraDataKey, @Nullable Bundle arguments) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void notifySubtreeAccessibilityStateChanged(View child, View source, int changeType) {
        throw new RuntimeException("Stub!");
    }

    /** @hide */
    @Override
    public void notifySubtreeAccessibilityStateChangedIfNeeded() {
        throw new RuntimeException("Stub!");
    }

    @Override
    void resetSubtreeAccessibilityStateChanged() {
        throw new RuntimeException("Stub!");
    }

    int getNumChildrenForAccessibility() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public boolean onNestedPrePerformAccessibilityAction(View target, int action, Bundle args) {
        return false;
    }

    @Override
    void calculateAccessibilityDataSensitive() {
        throw new RuntimeException("Stub!");
    }

    @Override
    void dispatchDetachedFromWindow() {
        throw new RuntimeException("Stub!");
    }

    @Override
    protected void internalSetPadding(int left, int top, int right, int bottom) {
        throw new RuntimeException("Stub!");
    }

    @Override
    protected void dispatchSaveInstanceState(SparseArray<Parcelable> container) {
        throw new RuntimeException("Stub!");
    }

    protected void dispatchFreezeSelfOnly(SparseArray<Parcelable> container) {
        throw new RuntimeException("Stub!");
    }

    @Override
    protected void dispatchRestoreInstanceState(SparseArray<Parcelable> container) {
        throw new RuntimeException("Stub!");
    }

    protected void dispatchThawSelfOnly(SparseArray<Parcelable> container) {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    protected void setChildrenDrawingCacheEnabled(boolean enabled) {
        throw new RuntimeException("Stub!");
    }

    // @Override
    // public Bitmap createSnapshot(ViewDebug.CanvasProvider canvasProvider, boolean skipChildren) {
    //     throw new RuntimeException("Stub!");
    // }

    /** Return true if this ViewGroup is laying out using optical bounds. */
    boolean isLayoutModeOptical() {
        throw new RuntimeException("Stub!");
    }

    @Override
    Insets computeOpticalInsets() {
        throw new RuntimeException("Stub!");
    }

    protected void onDebugDrawMargins(@NonNull Canvas canvas, Paint paint) {
        throw new RuntimeException("Stub!");
    }

    protected void onDebugDraw(@NonNull Canvas canvas) {
        throw new RuntimeException("Stub!");
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public ViewGroupOverlay getOverlay() {
        throw new RuntimeException("Stub!");
    }

    protected int getChildDrawingOrder(int childCount, int drawingPosition) {
        throw new RuntimeException("Stub!");
    }

    public final int getChildDrawingOrder(int drawingPosition) {
        throw new RuntimeException("Stub!");
    }

    ArrayList<View> buildOrderedChildList() {
        throw new RuntimeException("Stub!");
    }

    @Override
    protected void dispatchGetDisplayList() {
        throw new RuntimeException("Stub!");
    }

    protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
        throw new RuntimeException("Stub!");
    }

    @Override
    void getScrollIndicatorBounds(@NonNull Rect out) {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "drawing")
    public boolean getClipChildren() {
        throw new RuntimeException("Stub!");
    }

    public void setClipChildren(boolean clipChildren) {
        throw new RuntimeException("Stub!");
    }

    public void setClipToPadding(boolean clipToPadding) {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "drawing")
    public boolean getClipToPadding() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void dispatchSetSelected(boolean selected) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void dispatchSetActivated(boolean activated) {
        throw new RuntimeException("Stub!");
    }

    @Override
    protected void dispatchSetPressed(boolean pressed) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void dispatchDrawableHotspotChanged(float x, float y) {
        throw new RuntimeException("Stub!");
    }

    @Override
    void dispatchCancelPendingInputEvents() {
        throw new RuntimeException("Stub!");
    }

    protected void setStaticTransformationsEnabled(boolean enabled) {
        throw new RuntimeException("Stub!");
    }

    protected boolean getChildStaticTransformation(View child, Transformation t) {
        return false;
    }

    Transformation getChildTransformation() {
        throw new RuntimeException("Stub!");
    }

    @Override
    protected <T extends View> T findViewTraversal(@IdRes int id) {
        throw new RuntimeException("Stub!");
    }

    @Override
    protected <T extends View> T findViewWithTagTraversal(Object tag) {
        throw new RuntimeException("Stub!");
    }

    @Override
    protected <T extends View> T findViewByPredicateTraversal(Predicate<View> predicate,
            View childToSkip) {
        throw new RuntimeException("Stub!");
    }

    public void addTransientView(View view, int index) {
        throw new RuntimeException("Stub!");
    }

    public void removeTransientView(View view) {
        throw new RuntimeException("Stub!");
    }

    public int getTransientViewCount() {
        throw new RuntimeException("Stub!");
    }

    public int getTransientViewIndex(int position) {
        throw new RuntimeException("Stub!");
    }

    public View getTransientView(int position) {
        throw new RuntimeException("Stub!");
    }

    public void addView(View child) {
        throw new RuntimeException("Stub!");
    }

    public void addView(View child, int index) {
        throw new RuntimeException("Stub!");
    }

    public void addView(View child, int width, int height) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void addView(View child, LayoutParams params) {
        throw new RuntimeException("Stub!");
    }

    public void addView(View child, int index, LayoutParams params) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void updateViewLayout(View view, ViewGroup.LayoutParams params) {
        throw new RuntimeException("Stub!");
    }

    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        throw new RuntimeException("Stub!");
    }

    public interface OnHierarchyChangeListener {
        void onChildViewAdded(View parent, View child);

        void onChildViewRemoved(View parent, View child);
    }

    public void setOnHierarchyChangeListener(OnHierarchyChangeListener listener) {
        throw new RuntimeException("Stub!");
    }

    void dispatchViewAdded(View child) {
        throw new RuntimeException("Stub!");
    }

    public void onViewAdded(View child) {
    }

    void dispatchViewRemoved(View child) {
        throw new RuntimeException("Stub!");
    }

    public void onViewRemoved(View child) {
    }

    @Override
    protected void onAttachedToWindow() {
        throw new RuntimeException("Stub!");
    }

    @Override
    protected void onDetachedFromWindow() {
        throw new RuntimeException("Stub!");
    }

    /** @hide */
    @Override
    protected void destroyHardwareResources() {
        throw new RuntimeException("Stub!");
    }

    protected boolean addViewInLayout(View child, int index, LayoutParams params) {
        throw new RuntimeException("Stub!");
    }

    protected boolean addViewInLayout(View child, int index, LayoutParams params,
            boolean preventRequestLayout) {
        throw new RuntimeException("Stub!");
    }

    protected void cleanupLayoutState(View child) {
        throw new RuntimeException("Stub!");
    }

    protected void attachLayoutAnimationParameters(View child,
            LayoutParams params, int index, int count) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void removeView(View view) {
        throw new RuntimeException("Stub!");
    }

    public void removeViewInLayout(View view) {
        throw new RuntimeException("Stub!");
    }

    public void removeViewsInLayout(int start, int count) {
        throw new RuntimeException("Stub!");
    }

    public void removeViewAt(int index) {
        throw new RuntimeException("Stub!");
    }

    public void removeViews(int start, int count) {
        throw new RuntimeException("Stub!");
    }

    public void setLayoutTransition(LayoutTransition transition) {
        throw new RuntimeException("Stub!");
    }

    public LayoutTransition getLayoutTransition() {
        throw new RuntimeException("Stub!");
    }

    public void removeAllViews() {
        throw new RuntimeException("Stub!");
    }

    public void removeAllViewsInLayout() {
        throw new RuntimeException("Stub!");
    }

    protected void removeDetachedView(View child, boolean animate) {
        throw new RuntimeException("Stub!");
    }

    protected void attachViewToParent(View child, int index, LayoutParams params) {
        throw new RuntimeException("Stub!");
    }

    protected void detachViewFromParent(View child) {
        throw new RuntimeException("Stub!");
    }

    protected void detachViewFromParent(int index) {
        throw new RuntimeException("Stub!");
    }

    protected void detachViewsFromParent(int start, int count) {
        throw new RuntimeException("Stub!");
    }

    protected void detachAllViewsFromParent() {
        throw new RuntimeException("Stub!");
    }

    @Override
    @CallSuper
    public void onDescendantInvalidated(@NonNull View child, @NonNull View target) {
        throw new RuntimeException("Stub!");
    }


    @Deprecated
    @Override
    public final void invalidateChild(View child, final Rect dirty) {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    @Override
    public ViewParent invalidateChildInParent(final int[] location, final Rect dirty) {
        throw new RuntimeException("Stub!");
    }

    public final void offsetDescendantRectToMyCoords(View descendant, Rect rect) {
        throw new RuntimeException("Stub!");
    }

    public final void offsetRectIntoDescendantCoords(View descendant, Rect rect) {
        throw new RuntimeException("Stub!");
    }

    void offsetRectBetweenParentAndChild(View descendant, Rect rect,
            boolean offsetFromChildToParent, boolean clipToBounds) {
        throw new RuntimeException("Stub!");
    }

    public void offsetChildrenTopAndBottom(int offset) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public boolean getChildVisibleRect(View child, Rect r, android.graphics.Point offset) {
        throw new RuntimeException("Stub!");
    }

    public boolean getChildVisibleRect(
            View child, Rect r, android.graphics.Point offset, boolean forceParentCheck) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public final void layout(int l, int t, int r, int b) {
        throw new RuntimeException("Stub!");
    }

    @Override
    protected abstract void onLayout(boolean changed,
            int l, int t, int r, int b);

    protected boolean canAnimate() {
        throw new RuntimeException("Stub!");
    }

    public void startLayoutAnimation() {
        throw new RuntimeException("Stub!");
    }

    public void scheduleLayoutAnimation() {
        throw new RuntimeException("Stub!");
    }

    public void setLayoutAnimation(LayoutAnimationController controller) {
        throw new RuntimeException("Stub!");
    }

    public LayoutAnimationController getLayoutAnimation() {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public boolean isAnimationCacheEnabled() {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public void setAnimationCacheEnabled(boolean enabled) {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public boolean isAlwaysDrawnWithCacheEnabled() {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public void setAlwaysDrawnWithCacheEnabled(boolean always) {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    protected boolean isChildrenDrawnWithCacheEnabled() {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    protected void setChildrenDrawnWithCacheEnabled(boolean enabled) {
        throw new RuntimeException("Stub!");
    }

    @ViewDebug.ExportedProperty(category = "drawing")
    protected boolean isChildrenDrawingOrderEnabled() {
        throw new RuntimeException("Stub!");
    }

    protected void setChildrenDrawingOrderEnabled(boolean enabled) {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    @ViewDebug.ExportedProperty(category = "drawing", mapping = {
        @ViewDebug.IntToString(from = PERSISTENT_NO_CACHE,        to = "NONE"),
        @ViewDebug.IntToString(from = PERSISTENT_ANIMATION_CACHE, to = "ANIMATION"),
        @ViewDebug.IntToString(from = PERSISTENT_SCROLLING_CACHE, to = "SCROLLING"),
        @ViewDebug.IntToString(from = PERSISTENT_ALL_CACHES,      to = "ALL")
    })
    public int getPersistentDrawingCache() {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public void setPersistentDrawingCache(int drawingCacheToKeep) {
        throw new RuntimeException("Stub!");
    }

    // @Override
    void invalidateInheritedLayoutMode(int layoutModeOfRoot) {
        throw new RuntimeException("Stub!");
    }

    public int getLayoutMode() {
        throw new RuntimeException("Stub!");
    }

    public void setLayoutMode(int layoutMode) {
        throw new RuntimeException("Stub!");
    }

    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        throw new RuntimeException("Stub!");
    }

    protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        throw new RuntimeException("Stub!");
    }

    protected LayoutParams generateDefaultLayoutParams() {
        throw new RuntimeException("Stub!");
    }

    @Override
    protected void debug(int depth) {
        throw new RuntimeException("Stub!");
    }

    public int indexOfChild(View child) {
        throw new RuntimeException("Stub!");
    }

    public int getChildCount() {
        throw new RuntimeException("Stub!");
    }

    public View getChildAt(int index) {
        throw new RuntimeException("Stub!");
    }

    protected void measureChildren(int widthMeasureSpec, int heightMeasureSpec) {
        throw new RuntimeException("Stub!");
    }

    protected void measureChild(View child, int parentWidthMeasureSpec,
            int parentHeightMeasureSpec) {
        throw new RuntimeException("Stub!");
    }

    protected void measureChildWithMargins(View child,
            int parentWidthMeasureSpec, int widthUsed,
            int parentHeightMeasureSpec, int heightUsed) {
        throw new RuntimeException("Stub!");
    }

    public static int getChildMeasureSpec(int spec, int padding, int childDimension) {
        throw new RuntimeException("Stub!");
    }


    public void clearDisappearingChildren() {
        throw new RuntimeException("Stub!");
    }

    void finishAnimatingView(final View view, Animation animation) {
        throw new RuntimeException("Stub!");
    }

    boolean isViewTransitioning(View view) {
        throw new RuntimeException("Stub!");
    }

    public void startViewTransition(View view) {
        throw new RuntimeException("Stub!");
    }

    public void endViewTransition(View view) {
        throw new RuntimeException("Stub!");
    }

    public void suppressLayout(boolean suppress) {
        throw new RuntimeException("Stub!");
    }

    public boolean isLayoutSuppressed() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public boolean gatherTransparentRegion(Region region) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void requestTransparentRegion(View child) {
        throw new RuntimeException("Stub!");
    }

    // @Override
    public void subtractObscuredTouchableRegion(Region touchableRegion, View view) {
        throw new RuntimeException("Stub!");
    }

    // @Override
    public boolean getChildLocalHitRegion(@NonNull View child, @NonNull Region region,
            @NonNull Matrix matrix, boolean isHover) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public WindowInsets dispatchApplyWindowInsets(WindowInsets insets) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void setWindowInsetsAnimationCallback(
            @Nullable WindowInsetsAnimation.Callback callback) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public boolean hasWindowInsetsAnimationCallback() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void dispatchWindowInsetsAnimationPrepare(
            @NonNull WindowInsetsAnimation animation) {
        throw new RuntimeException("Stub!");
    }

    @Override
    @NonNull
    public Bounds dispatchWindowInsetsAnimationStart(
            @NonNull WindowInsetsAnimation animation, @NonNull Bounds bounds) {
        throw new RuntimeException("Stub!");
    }

    @Override
    @NonNull
    public WindowInsets dispatchWindowInsetsAnimationProgress(@NonNull WindowInsets insets,
            @NonNull List<WindowInsetsAnimation> runningAnimations) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void dispatchWindowInsetsAnimationEnd(@NonNull WindowInsetsAnimation animation) {
        throw new RuntimeException("Stub!");
    }

    // @Override
    // public void dispatchScrollCaptureSearch(
    //         @NonNull Rect localVisibleRect, @NonNull Point windowOffset,
    //         @NonNull Consumer<ScrollCaptureTarget> targets) {
    //     throw new RuntimeException("Stub!");
    // }

    public Animation.AnimationListener getLayoutAnimationListener() {
        throw new RuntimeException("Stub!");
    }

    @Override
    protected void drawableStateChanged() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        throw new RuntimeException("Stub!");
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        throw new RuntimeException("Stub!");
    }

    public void setAddStatesFromChildren(boolean addsStates) {
        throw new RuntimeException("Stub!");
    }

    public boolean addStatesFromChildren() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void childDrawableStateChanged(View child) {
        throw new RuntimeException("Stub!");
    }

    public void setLayoutAnimationListener(Animation.AnimationListener animationListener) {
        throw new RuntimeException("Stub!");
    }

    public void requestTransitionStart(LayoutTransition transition) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public boolean resolveRtlPropertiesIfNeeded() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public boolean resolveLayoutDirection() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public boolean resolveTextDirection() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public boolean resolveTextAlignment() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void resolvePadding() {
        throw new RuntimeException("Stub!");
    }

    @Override
    protected void resolveDrawables() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void resolveLayoutParams() {
        throw new RuntimeException("Stub!");
    }

    @TestApi
    @Override
    public void resetResolvedLayoutDirection() {
        throw new RuntimeException("Stub!");
    }

    @TestApi
    @Override
    public void resetResolvedTextDirection() {
        throw new RuntimeException("Stub!");
    }

    @TestApi
    @Override
    public void resetResolvedTextAlignment() {
        throw new RuntimeException("Stub!");
    }

    @TestApi
    @Override
    public void resetResolvedPadding() {
        throw new RuntimeException("Stub!");
    }

    @TestApi
    @Override
    protected void resetResolvedDrawables() {
        throw new RuntimeException("Stub!");
    }

    public boolean shouldDelayChildPressedState() {
        return true;
    }

    @Override
    public boolean onStartNestedScroll(View child, View target, int nestedScrollAxes) {
        return false;
    }

    @Override
    public void onNestedScrollAccepted(View child, View target, int axes) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void onStopNestedScroll(View child) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void onNestedScroll(View target, int dxConsumed, int dyConsumed,
            int dxUnconsumed, int dyUnconsumed) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public boolean onNestedFling(View target, float velocityX, float velocityY, boolean consumed) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public boolean onNestedPreFling(View target, float velocityX, float velocityY) {
        throw new RuntimeException("Stub!");
    }

    public int getNestedScrollAxes() {
        throw new RuntimeException("Stub!");
    }

    /** @hide */
    protected void onSetLayoutParams(View child, LayoutParams layoutParams) {
        throw new RuntimeException("Stub!");
    }

    /** @hide */
    @Override
    public void captureTransitioningViews(List<View> transitioningViews) {
        throw new RuntimeException("Stub!");
    }

    /** @hide */
    @Override
    public void findNamedViews(Map<String, View> namedElements) {
        throw new RuntimeException("Stub!");
    }

    @Override
    boolean hasUnhandledKeyListener() {
        throw new RuntimeException("Stub!");
    }

    void incrementChildUnhandledKeyListeners() {
        throw new RuntimeException("Stub!");
    }

    void decrementChildUnhandledKeyListeners() {
        throw new RuntimeException("Stub!");
    }

    @Override
    View dispatchUnhandledKeyEvent(KeyEvent evt) {
        throw new RuntimeException("Stub!");
    }

    public static class LayoutParams {
        @SuppressWarnings({"UnusedDeclaration"})
        @Deprecated
        public static final int FILL_PARENT = -1;

        public static final int MATCH_PARENT = -1;

        public static final int WRAP_CONTENT = -2;

        public int width;

        public int height;

        public LayoutAnimationController.AnimationParameters layoutAnimationParameters;

        public LayoutParams(Context c, AttributeSet attrs) {
            throw new RuntimeException("Stub!");
        }

        public LayoutParams(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public LayoutParams(LayoutParams source) {
            this.width = source.width;
            this.height = source.height;
        }

        LayoutParams() {
        }

        protected void setBaseAttributes(TypedArray a, int widthAttr, int heightAttr) {
            throw new RuntimeException("Stub!");
        }

        public void resolveLayoutDirection(int layoutDirection) {
        }

        public String debug(String output) {
            return output + "ViewGroup.LayoutParams={ width="
                    + sizeToString(width) + ", height=" + sizeToString(height) + " }";
        }

        public void onDebugDraw(View view, Canvas canvas, Paint paint) {
        }

        protected static String sizeToString(int size) {
            if (size == WRAP_CONTENT) {
                return "wrap-content";
            }
            if (size == MATCH_PARENT) {
                return "match-parent";
            }
            return String.valueOf(size);
        }

        // /** @hide */
        // void encode(@NonNull ViewHierarchyEncoder encoder) {
        //     throw new RuntimeException("Stub!");
        // }

        // /** @hide */
        // protected void encodeProperties(@NonNull ViewHierarchyEncoder encoder) {
        //     throw new RuntimeException("Stub!");
        // }
    }

    public static class MarginLayoutParams extends ViewGroup.LayoutParams {
        @ViewDebug.ExportedProperty(category = "layout")
        public int leftMargin;

        @ViewDebug.ExportedProperty(category = "layout")
        public int topMargin;

        @ViewDebug.ExportedProperty(category = "layout")
        public int rightMargin;

        @ViewDebug.ExportedProperty(category = "layout")
        public int bottomMargin;

        byte mMarginFlags;

        public MarginLayoutParams(Context c, AttributeSet attrs) {
            throw new RuntimeException("Stub!");
        }

        public MarginLayoutParams(int width, int height) {
            super(width, height);
            throw new RuntimeException("Stub!");
        }

        public MarginLayoutParams(MarginLayoutParams source) {
            throw new RuntimeException("Stub!");
        }

        public MarginLayoutParams(LayoutParams source) {
            super(source);
            throw new RuntimeException("Stub!");
        }

        public final void copyMarginsFrom(MarginLayoutParams source) {
            throw new RuntimeException("Stub!");
        }

        public void setMargins(int left, int top, int right, int bottom) {
            throw new RuntimeException("Stub!");
        }

        public void setMarginsRelative(int start, int top, int end, int bottom) {
            throw new RuntimeException("Stub!");
        }

        public void setMarginStart(int start) {
            throw new RuntimeException("Stub!");
        }

        public int getMarginStart() {
            throw new RuntimeException("Stub!");
        }

        public void setMarginEnd(int end) {
            throw new RuntimeException("Stub!");
        }

        public int getMarginEnd() {
            throw new RuntimeException("Stub!");
        }

        public boolean isMarginRelative() {
            throw new RuntimeException("Stub!");
        }

        public void setLayoutDirection(int layoutDirection) {
            throw new RuntimeException("Stub!");
        }

        public int getLayoutDirection() {
            throw new RuntimeException("Stub!");
        }

        @Override
        public void resolveLayoutDirection(int layoutDirection) {
            throw new RuntimeException("Stub!");
        }

        public boolean isLayoutRtl() {
            throw new RuntimeException("Stub!");
        }

        @Override
        public void onDebugDraw(View view, Canvas canvas, Paint paint) {
            throw new RuntimeException("Stub!");
        }

        // /** @hide */
        // @Override
        // protected void encodeProperties(@NonNull ViewHierarchyEncoder encoder) {
        //     throw new RuntimeException("Stub!");
        // }
    }

    static class ChildListForAccessibility {

        public static ChildListForAccessibility obtain(ViewGroup parent, boolean sort) {
            throw new RuntimeException("Stub!");
        }

        public void recycle() {
            throw new RuntimeException("Stub!");
        }

        public int getChildCount() {
            throw new RuntimeException("Stub!");
        }

        public View getChildAt(int index) {
            throw new RuntimeException("Stub!");
        }
    }

    static class ViewLocationHolder implements Comparable<ViewLocationHolder> {

        public static final int COMPARISON_STRATEGY_STRIPE = 1;

        public static final int COMPARISON_STRATEGY_LOCATION = 2;

        public View mView;

        public static ViewLocationHolder obtain(ViewGroup root, View view) {
            throw new RuntimeException("Stub!");
        }

        public static void setComparisonStrategy(int strategy) {
            throw new RuntimeException("Stub!");
        }

        public void recycle() {
            throw new RuntimeException("Stub!");
        }

        @Override
        public int compareTo(ViewLocationHolder another) {
            throw new RuntimeException("Stub!");
        }
    }

    // /** @hide */
    // @Override
    // protected void encodeProperties(@NonNull ViewHierarchyEncoder encoder) {
    //     throw new RuntimeException("Stub!");
    // }

    /** @hide */
    // @Override
    public final void onDescendantUnbufferedRequested() {
        throw new RuntimeException("Stub!");
    }

    // @Override
    // public void dispatchCreateViewTranslationRequest(@NonNull Map<AutofillId, long[]> viewIds,
    //         @NonNull int[] supportedFormats,
    //         @Nullable TranslationCapability capability,
    //         @NonNull List<ViewTranslationRequest> requests) {
    //     throw new RuntimeException("Stub!");
    // }

    // @Nullable
    // @Override
    // public OnBackInvokedDispatcher findOnBackInvokedDispatcherForChild(@NonNull View child,
    //         @NonNull View requester) {
    //     throw new RuntimeException("Stub!");
    // }

    @Override
    public void setRequestedFrameRate(float frameRate) {
        throw new RuntimeException("Stub!");
    }

    public void propagateRequestedFrameRate(float frameRate, boolean forceOverride) {
        throw new RuntimeException("Stub!");
    }

    @Override
    void overrideFrameRate(float frameRate, boolean forceOverride) {
        throw new RuntimeException("Stub!");
    }
}
