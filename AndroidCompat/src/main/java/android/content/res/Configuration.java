/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.content.res;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.pm.ActivityInfo;
import android.graphics.Typeface;
import android.os.Build;
import android.os.LocaleList;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Slog;
import android.view.View;

import com.android.internal.util.XmlUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.IllformedLocaleException;
import java.util.List;
import java.util.Locale;

/**
 * This class describes all device configuration information that can
 * impact the resources the application retrieves.  This includes both
 * user-specified configuration options (locale list and scaling) as well
 * as device configurations (such as input modes, screen size and screen orientation).
 * <p>You can acquire this object from {@link Resources}, using {@link
 * Resources#getConfiguration}. Thus, from an activity, you can get it by chaining the request
 * with {@link android.app.Activity#getResources}:</p>
 * <pre>Configuration config = getResources().getConfiguration();</pre>
 */
public final class Configuration implements Parcelable, Comparable<Configuration> {
    /** @hide */
    public static final Configuration EMPTY = new Configuration();

    private static final String TAG = "Configuration";

    /**
     * Current user preference for the scaling factor for fonts, relative
     * to the base density scaling.
     *
     * <p>Note: Please do not use this to hardcode font size equations. The equation for font
     * scaling is now non-linear; this coefficient is no longer used as a direct multiplier to
     * determine font size. It exists for informational purposes only.
     *
     * <p>Please use {@link android.util.TypedValue#applyDimension(int, float, DisplayMetrics)} or
     * {@link android.util.TypedValue#deriveDimension(int, float, DisplayMetrics)} to convert
     * between scaled font size dimensions and pixels.
     */
    public float fontScale;

    /**
     * IMSI MCC (Mobile Country Code), corresponding to
     * <a href="{@docRoot}guide/topics/resources/providing-resources.html#MccQualifier">mcc</a>
     * resource qualifier.  0 if undefined.
     */
    public int mcc;

    /**
     * IMSI MNC (Mobile Network Code), corresponding to
     * <a href="{@docRoot}guide/topics/resources/providing-resources.html#MccQualifier">mnc</a>
     * resource qualifier.  0 if undefined. Note that the actual MNC may be 0; in order to check
     * for this use the {@link #MNC_ZERO} symbol.
     */
    public int mnc;

    /**
     * Constant used to to represent MNC (Mobile Network Code) zero.
     * 0 cannot be used, since it is used to represent an undefined MNC.
     */
    public static final int MNC_ZERO = 0xffff;

    /**
     * Current user preference for the locale, corresponding to
     * <a href="{@docRoot}guide/topics/resources/providing-resources.html#LocaleQualifier">locale</a>
     * resource qualifier.
     *
     * @deprecated Do not set or read this directly. Use {@link #getLocales()} and
     * {@link #setLocales(LocaleList)}. If only the primary locale is needed,
     * <code>getLocales().get(0)</code> is now the preferred accessor.
     */
    @Deprecated public Locale locale;

    private LocaleList mLocaleList;

    /**
     * Constant for grammatical gender: to indicate that the grammatical gender is undefined.
     * Only for internal usage.
     * @hide
     */
    public static final int GRAMMATICAL_GENDER_UNDEFINED = -1;

    /**
     * Constant for grammatical gender: to indicate the user has not specified the terms
     * of address for the application.
     */
    public static final int GRAMMATICAL_GENDER_NOT_SPECIFIED = 0;

    /**
     * Constant for grammatical gender: to indicate the terms of address the user
     * preferred in an application is neuter.
     */
    public static final int GRAMMATICAL_GENDER_NEUTRAL = 1;

    /**
     * Constant for grammatical gender: to indicate the terms of address the user
         * preferred in an application is feminine.
     */
    public static final int GRAMMATICAL_GENDER_FEMININE = 2;

    /**
     * Constant for grammatical gender: to indicate the terms of address the user
     * preferred in an application is masculine.
     */
    public static final int GRAMMATICAL_GENDER_MASCULINE = 3;

    /** Constant for {@link #colorMode}: bits that encode whether the screen is wide gamut. */
    public static final int COLOR_MODE_WIDE_COLOR_GAMUT_MASK = 0x3;
    /**
     * Constant for {@link #colorMode}: a {@link #COLOR_MODE_WIDE_COLOR_GAMUT_MASK} value
     * indicating that it is unknown whether or not the screen is wide gamut.
     */
    public static final int COLOR_MODE_WIDE_COLOR_GAMUT_UNDEFINED = 0x0;
    /**
     * Constant for {@link #colorMode}: a {@link #COLOR_MODE_WIDE_COLOR_GAMUT_MASK} value
     * indicating that the screen is not wide gamut.
     * <p>Corresponds to the <code>-nowidecg</code> resource qualifier.</p>
     */
    public static final int COLOR_MODE_WIDE_COLOR_GAMUT_NO = 0x1;
    /**
     * Constant for {@link #colorMode}: a {@link #COLOR_MODE_WIDE_COLOR_GAMUT_MASK} value
     * indicating that the screen is wide gamut.
     * <p>Corresponds to the <code>-widecg</code> resource qualifier.</p>
     */
    public static final int COLOR_MODE_WIDE_COLOR_GAMUT_YES = 0x2;

    /** Constant for {@link #colorMode}: bits that encode the dynamic range of the screen. */
    public static final int COLOR_MODE_HDR_MASK = 0xc;
    /** Constant for {@link #colorMode}: bits shift to get the screen dynamic range. */
    public static final int COLOR_MODE_HDR_SHIFT = 2;
    /**
     * Constant for {@link #colorMode}: a {@link #COLOR_MODE_HDR_MASK} value
     * indicating that it is unknown whether or not the screen is HDR.
     */
    public static final int COLOR_MODE_HDR_UNDEFINED = 0x0;
    /**
     * Constant for {@link #colorMode}: a {@link #COLOR_MODE_HDR_MASK} value
     * indicating that the screen is not HDR (low/standard dynamic range).
     * <p>Corresponds to the <code>-lowdr</code> resource qualifier.</p>
     */
    public static final int COLOR_MODE_HDR_NO = 0x1 << COLOR_MODE_HDR_SHIFT;
    /**
     * Constant for {@link #colorMode}: a {@link #COLOR_MODE_HDR_MASK} value
     * indicating that the screen is HDR (dynamic range).
     * <p>Corresponds to the <code>-highdr</code> resource qualifier.</p>
     */
    public static final int COLOR_MODE_HDR_YES = 0x2 << COLOR_MODE_HDR_SHIFT;

    /** Constant for {@link #colorMode}: a value indicating that the color mode is undefined */
    @SuppressWarnings("PointlessBitwiseExpression")
    public static final int COLOR_MODE_UNDEFINED = COLOR_MODE_WIDE_COLOR_GAMUT_UNDEFINED |
            COLOR_MODE_HDR_UNDEFINED;

    /**
     * Bit mask of color capabilities of the screen. Currently there are two fields:
     * <p>The {@link #COLOR_MODE_WIDE_COLOR_GAMUT_MASK} bits define the color gamut of
     * the screen. They may be one of
     * {@link #COLOR_MODE_WIDE_COLOR_GAMUT_NO} or {@link #COLOR_MODE_WIDE_COLOR_GAMUT_YES}.</p>
     *
     * <p>The {@link #COLOR_MODE_HDR_MASK} defines the dynamic range of the screen. They may be
     * one of {@link #COLOR_MODE_HDR_NO} or {@link #COLOR_MODE_HDR_YES}.</p>
     *
     * <p>See <a href="{@docRoot}guide/practices/screens_support.html">Supporting
     * Multiple Screens</a> for more information.</p>
     */
    public int colorMode;

    /** Constant for {@link #screenLayout}: bits that encode the size. */
    public static final int SCREENLAYOUT_SIZE_MASK = 0x0f;
    /** Constant for {@link #screenLayout}: a {@link #SCREENLAYOUT_SIZE_MASK}
     * value indicating that no size has been set. */
    public static final int SCREENLAYOUT_SIZE_UNDEFINED = 0x00;
    /** Constant for {@link #screenLayout}: a {@link #SCREENLAYOUT_SIZE_MASK}
     * value indicating the screen is at least approximately 320x426 dp units,
     * corresponds to the
     * <a href="{@docRoot}guide/topics/resources/providing-resources.html#ScreenSizeQualifier">small</a>
     * resource qualifier.
     * See <a href="{@docRoot}guide/practices/screens_support.html">Supporting
     * Multiple Screens</a> for more information. */
    public static final int SCREENLAYOUT_SIZE_SMALL = 0x01;
    /** Constant for {@link #screenLayout}: a {@link #SCREENLAYOUT_SIZE_MASK}
     * value indicating the screen is at least approximately 320x470 dp units,
     * corresponds to the
     * <a href="{@docRoot}guide/topics/resources/providing-resources.html#ScreenSizeQualifier">normal</a>
     * resource qualifier.
     * See <a href="{@docRoot}guide/practices/screens_support.html">Supporting
     * Multiple Screens</a> for more information. */
    public static final int SCREENLAYOUT_SIZE_NORMAL = 0x02;
    /** Constant for {@link #screenLayout}: a {@link #SCREENLAYOUT_SIZE_MASK}
     * value indicating the screen is at least approximately 480x640 dp units,
     * corresponds to the
     * <a href="{@docRoot}guide/topics/resources/providing-resources.html#ScreenSizeQualifier">large</a>
     * resource qualifier.
     * See <a href="{@docRoot}guide/practices/screens_support.html">Supporting
     * Multiple Screens</a> for more information. */
    public static final int SCREENLAYOUT_SIZE_LARGE = 0x03;
    /** Constant for {@link #screenLayout}: a {@link #SCREENLAYOUT_SIZE_MASK}
     * value indicating the screen is at least approximately 720x960 dp units,
     * corresponds to the
     * <a href="{@docRoot}guide/topics/resources/providing-resources.html#ScreenSizeQualifier">xlarge</a>
     * resource qualifier.
     * See <a href="{@docRoot}guide/practices/screens_support.html">Supporting
     * Multiple Screens</a> for more information.*/
    public static final int SCREENLAYOUT_SIZE_XLARGE = 0x04;

    /** Constant for {@link #screenLayout}: bits that encode the aspect ratio. */
    public static final int SCREENLAYOUT_LONG_MASK = 0x30;
    /** Constant for {@link #screenLayout}: a {@link #SCREENLAYOUT_LONG_MASK}
     * value indicating that no size has been set. */
    public static final int SCREENLAYOUT_LONG_UNDEFINED = 0x00;
    /** Constant for {@link #screenLayout}: a {@link #SCREENLAYOUT_LONG_MASK}
     * value that corresponds to the
     * <a href="{@docRoot}guide/topics/resources/providing-resources.html#ScreenAspectQualifier">notlong</a>
     * resource qualifier. */
    public static final int SCREENLAYOUT_LONG_NO = 0x10;
    /** Constant for {@link #screenLayout}: a {@link #SCREENLAYOUT_LONG_MASK}
     * value that corresponds to the
     * <a href="{@docRoot}guide/topics/resources/providing-resources.html#ScreenAspectQualifier">long</a>
     * resource qualifier. */
    public static final int SCREENLAYOUT_LONG_YES = 0x20;

    /** Constant for {@link #screenLayout}: bits that encode the layout direction. */
    public static final int SCREENLAYOUT_LAYOUTDIR_MASK = 0xC0;
    /** Constant for {@link #screenLayout}: bits shift to get the layout direction. */
    public static final int SCREENLAYOUT_LAYOUTDIR_SHIFT = 6;
    /** Constant for {@link #screenLayout}: a {@link #SCREENLAYOUT_LAYOUTDIR_MASK}
     * value indicating that no layout dir has been set. */
    public static final int SCREENLAYOUT_LAYOUTDIR_UNDEFINED = 0x00;
    /** Constant for {@link #screenLayout}: a {@link #SCREENLAYOUT_LAYOUTDIR_MASK}
     * value indicating that a layout dir has been set to LTR. */
    public static final int SCREENLAYOUT_LAYOUTDIR_LTR = 0x01 << SCREENLAYOUT_LAYOUTDIR_SHIFT;
    /** Constant for {@link #screenLayout}: a {@link #SCREENLAYOUT_LAYOUTDIR_MASK}
     * value indicating that a layout dir has been set to RTL. */
    public static final int SCREENLAYOUT_LAYOUTDIR_RTL = 0x02 << SCREENLAYOUT_LAYOUTDIR_SHIFT;

    /** Constant for {@link #screenLayout}: bits that encode roundness of the screen. */
    public static final int SCREENLAYOUT_ROUND_MASK = 0x300;
    /** @hide Constant for {@link #screenLayout}: bit shift to get to screen roundness bits */
    public static final int SCREENLAYOUT_ROUND_SHIFT = 8;
    /**
     * Constant for {@link #screenLayout}: a {@link #SCREENLAYOUT_ROUND_MASK} value indicating
     * that it is unknown whether or not the screen has a round shape.
     */
    public static final int SCREENLAYOUT_ROUND_UNDEFINED = 0x00;
    /**
     * Constant for {@link #screenLayout}: a {@link #SCREENLAYOUT_ROUND_MASK} value indicating
     * that the screen does not have a rounded shape.
     */
    public static final int SCREENLAYOUT_ROUND_NO = 0x1 << SCREENLAYOUT_ROUND_SHIFT;
    /**
     * Constant for {@link #screenLayout}: a {@link #SCREENLAYOUT_ROUND_MASK} value indicating
     * that the screen has a rounded shape. Corners may not be visible to the user;
     * developers should pay special attention to the {@link android.view.WindowInsets} delivered
     * to views for more information about ensuring content is not obscured.
     *
     * <p>Corresponds to the <code>-round</code> resource qualifier.</p>
     */
    public static final int SCREENLAYOUT_ROUND_YES = 0x2 << SCREENLAYOUT_ROUND_SHIFT;

    /** Constant for {@link #screenLayout}: a value indicating that screenLayout is undefined */
    public static final int SCREENLAYOUT_UNDEFINED = SCREENLAYOUT_SIZE_UNDEFINED |
            SCREENLAYOUT_LONG_UNDEFINED | SCREENLAYOUT_LAYOUTDIR_UNDEFINED |
            SCREENLAYOUT_ROUND_UNDEFINED;

    /**
     * Special flag we generate to indicate that the screen layout requires
     * us to use a compatibility mode for apps that are not modern layout
     * aware.
     * @hide
     */
    public static final int SCREENLAYOUT_COMPAT_NEEDED = 0x10000000;

    /**
     * Bit mask of overall layout of the screen.  Currently there are four
     * fields:
     * <p>The {@link #SCREENLAYOUT_SIZE_MASK} bits define the overall size
     * of the screen.  They may be one of
     * {@link #SCREENLAYOUT_SIZE_SMALL}, {@link #SCREENLAYOUT_SIZE_NORMAL},
     * {@link #SCREENLAYOUT_SIZE_LARGE}, or {@link #SCREENLAYOUT_SIZE_XLARGE}.</p>
     *
     * <p>The {@link #SCREENLAYOUT_LONG_MASK} defines whether the screen
     * is wider/taller than normal.  They may be one of
     * {@link #SCREENLAYOUT_LONG_NO} or {@link #SCREENLAYOUT_LONG_YES}.</p>
     *
     * <p>The {@link #SCREENLAYOUT_LAYOUTDIR_MASK} defines whether the screen layout
     * is either LTR or RTL.  They may be one of
     * {@link #SCREENLAYOUT_LAYOUTDIR_LTR} or {@link #SCREENLAYOUT_LAYOUTDIR_RTL}.</p>
     *
     * <p>The {@link #SCREENLAYOUT_ROUND_MASK} defines whether the screen has a rounded
     * shape. They may be one of {@link #SCREENLAYOUT_ROUND_NO} or {@link #SCREENLAYOUT_ROUND_YES}.
     * </p>
     *
     * <p>See <a href="{@docRoot}guide/practices/screens_support.html">Supporting
     * Multiple Screens</a> for more information.</p>
     */
    public int screenLayout;

    /**
     * An undefined fontWeightAdjustment.
     */
    public static final int FONT_WEIGHT_ADJUSTMENT_UNDEFINED = Integer.MAX_VALUE;

    /**
     * Adjustment in text font weight. Used to reflect the current user preference for increasing
     * font weight.
     *
     * <p> If the text font weight is less than the minimum of 1, 1 will be used. If the font weight
     * exceeds the maximum of 1000, 1000 will be used.
     *
     * @see android.graphics.Typeface#create(Typeface, int, boolean)
     * @see android.graphics.fonts.FontStyle#FONT_WEIGHT_MIN
     * @see android.graphics.fonts.FontStyle#FONT_WEIGHT_MAX
     */
    public int fontWeightAdjustment;

    /**
     * Check if the Configuration's current {@link #screenLayout} is at
     * least the given size.
     *
     * @param size The desired size, either {@link #SCREENLAYOUT_SIZE_SMALL},
     * {@link #SCREENLAYOUT_SIZE_NORMAL}, {@link #SCREENLAYOUT_SIZE_LARGE}, or
     * {@link #SCREENLAYOUT_SIZE_XLARGE}.
     * @return Returns true if the current screen layout size is at least
     * the given size.
     */
    public boolean isLayoutSizeAtLeast(int size) {
        throw new RuntimeException("Stub!");
    }

    /** Constant for {@link #touchscreen}: a value indicating that no value has been set. */
    public static final int TOUCHSCREEN_UNDEFINED = 0;
    /** Constant for {@link #touchscreen}, value corresponding to the
     * <a href="{@docRoot}guide/topics/resources/providing-resources.html#TouchscreenQualifier">notouch</a>
     * resource qualifier. */
    public static final int TOUCHSCREEN_NOTOUCH = 1;
    /** @deprecated Not currently supported or used. */
    @Deprecated public static final int TOUCHSCREEN_STYLUS = 2;
    /** Constant for {@link #touchscreen}, value corresponding to the
     * <a href="{@docRoot}guide/topics/resources/providing-resources.html#TouchscreenQualifier">finger</a>
     * resource qualifier. */
    public static final int TOUCHSCREEN_FINGER = 3;

    /**
     * The kind of touch screen attached to the device.
     * One of: {@link #TOUCHSCREEN_NOTOUCH}, {@link #TOUCHSCREEN_FINGER}.
     */
    public int touchscreen;

    /** Constant for {@link #keyboard}: a value indicating that no value has been set. */
    public static final int KEYBOARD_UNDEFINED = 0;
    /** Constant for {@link #keyboard}, value corresponding to the
     * <a href="{@docRoot}guide/topics/resources/providing-resources.html#ImeQualifier">nokeys</a>
     * resource qualifier. */
    public static final int KEYBOARD_NOKEYS = 1;
    /** Constant for {@link #keyboard}, value corresponding to the
     * <a href="{@docRoot}guide/topics/resources/providing-resources.html#ImeQualifier">qwerty</a>
     * resource qualifier. */
    public static final int KEYBOARD_QWERTY = 2;
    /** Constant for {@link #keyboard}, value corresponding to the
     * <a href="{@docRoot}guide/topics/resources/providing-resources.html#ImeQualifier">12key</a>
     * resource qualifier. */
    public static final int KEYBOARD_12KEY = 3;

    /**
     * The kind of keyboard attached to the device.
     * One of: {@link #KEYBOARD_NOKEYS}, {@link #KEYBOARD_QWERTY},
     * {@link #KEYBOARD_12KEY}.
     */
    public int keyboard;

    /** Constant for {@link #keyboardHidden}: a value indicating that no value has been set. */
    public static final int KEYBOARDHIDDEN_UNDEFINED = 0;
    /** Constant for {@link #keyboardHidden}, value corresponding to the
     * <a href="{@docRoot}guide/topics/resources/providing-resources.html#KeyboardAvailQualifier">keysexposed</a>
     * resource qualifier. */
    public static final int KEYBOARDHIDDEN_NO = 1;
    /** Constant for {@link #keyboardHidden}, value corresponding to the
     * <a href="{@docRoot}guide/topics/resources/providing-resources.html#KeyboardAvailQualifier">keyshidden</a>
     * resource qualifier. */
    public static final int KEYBOARDHIDDEN_YES = 2;
    /**
     * Constant matching actual resource implementation.
     * @hide
     */
    public static final int KEYBOARDHIDDEN_SOFT = 3;

    /**
     * A flag indicating whether any keyboard is available.  Unlike
     * {@link #hardKeyboardHidden}, this also takes into account a soft
     * keyboard, so if the hard keyboard is hidden but there is soft
     * keyboard available, it will be set to NO.  Value is one of:
     * {@link #KEYBOARDHIDDEN_NO}, {@link #KEYBOARDHIDDEN_YES}.
     */
    public int keyboardHidden;

    /** Constant for {@link #hardKeyboardHidden}: a value indicating that no value has been set. */
    public static final int HARDKEYBOARDHIDDEN_UNDEFINED = 0;
    /** Constant for {@link #hardKeyboardHidden}, value corresponding to the
     * physical keyboard being exposed. */
    public static final int HARDKEYBOARDHIDDEN_NO = 1;
    /** Constant for {@link #hardKeyboardHidden}, value corresponding to the
     * physical keyboard being hidden. */
    public static final int HARDKEYBOARDHIDDEN_YES = 2;

    /**
     * A flag indicating whether the hard keyboard has been hidden.  This will
     * be set on a device with a mechanism to hide the keyboard from the
     * user, when that mechanism is closed.  One of:
     * {@link #HARDKEYBOARDHIDDEN_NO}, {@link #HARDKEYBOARDHIDDEN_YES}.
     */
    public int hardKeyboardHidden;

    /** Constant for {@link #navigation}: a value indicating that no value has been set. */
    public static final int NAVIGATION_UNDEFINED = 0;
    /** Constant for {@link #navigation}, value corresponding to the
     * <a href="{@docRoot}guide/topics/resources/providing-resources.html#NavigationQualifier">nonav</a>
     * resource qualifier. */
    public static final int NAVIGATION_NONAV = 1;
    /** Constant for {@link #navigation}, value corresponding to the
     * <a href="{@docRoot}guide/topics/resources/providing-resources.html#NavigationQualifier">dpad</a>
     * resource qualifier. */
    public static final int NAVIGATION_DPAD = 2;
    /** Constant for {@link #navigation}, value corresponding to the
     * <a href="{@docRoot}guide/topics/resources/providing-resources.html#NavigationQualifier">trackball</a>
     * resource qualifier. */
    public static final int NAVIGATION_TRACKBALL = 3;
    /** Constant for {@link #navigation}, value corresponding to the
     * <a href="{@docRoot}guide/topics/resources/providing-resources.html#NavigationQualifier">wheel</a>
     * resource qualifier. */
    public static final int NAVIGATION_WHEEL = 4;

    /**
     * The kind of navigation method available on the device.
     * One of: {@link #NAVIGATION_NONAV}, {@link #NAVIGATION_DPAD},
     * {@link #NAVIGATION_TRACKBALL}, {@link #NAVIGATION_WHEEL}.
     */
    public int navigation;

    /** Constant for {@link #navigationHidden}: a value indicating that no value has been set. */
    public static final int NAVIGATIONHIDDEN_UNDEFINED = 0;
    /** Constant for {@link #navigationHidden}, value corresponding to the
     * <a href="{@docRoot}guide/topics/resources/providing-resources.html#NavAvailQualifier">navexposed</a>
     * resource qualifier. */
    public static final int NAVIGATIONHIDDEN_NO = 1;
    /** Constant for {@link #navigationHidden}, value corresponding to the
     * <a href="{@docRoot}guide/topics/resources/providing-resources.html#NavAvailQualifier">navhidden</a>
     * resource qualifier. */
    public static final int NAVIGATIONHIDDEN_YES = 2;

    /**
     * A flag indicating whether any 5-way or DPAD navigation available.
     * This will be set on a device with a mechanism to hide the navigation
     * controls from the user, when that mechanism is closed.  One of:
     * {@link #NAVIGATIONHIDDEN_NO}, {@link #NAVIGATIONHIDDEN_YES}.
     */
    public int navigationHidden;

    /** @hide **/
    @IntDef(prefix = {"ORIENTATION_"}, value = {
            ORIENTATION_UNDEFINED,
            ORIENTATION_PORTRAIT,
            ORIENTATION_LANDSCAPE,
            ORIENTATION_SQUARE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Orientation {
    }

    /** Constant for {@link #orientation}: a value indicating that no value has been set. */
    public static final int ORIENTATION_UNDEFINED = 0;
    /** Constant for {@link #orientation}, value corresponding to the
     * <a href="{@docRoot}guide/topics/resources/providing-resources.html#OrientationQualifier">port</a>
     * resource qualifier. */
    public static final int ORIENTATION_PORTRAIT = 1;
    /** Constant for {@link #orientation}, value corresponding to the
     * <a href="{@docRoot}guide/topics/resources/providing-resources.html#OrientationQualifier">land</a>
     * resource qualifier. */
    public static final int ORIENTATION_LANDSCAPE = 2;
    /** @deprecated Not currently supported or used. */
    @Deprecated public static final int ORIENTATION_SQUARE = 3;

    /**
     * Overall orientation of the screen.  May be one of
     * {@link #ORIENTATION_LANDSCAPE}, {@link #ORIENTATION_PORTRAIT}.
     */
    @Orientation
    public int orientation;

    /** Constant for {@link #uiMode}: bits that encode the mode type. */
    public static final int UI_MODE_TYPE_MASK = 0x0f;
    /** Constant for {@link #uiMode}: a {@link #UI_MODE_TYPE_MASK}
     * value indicating that no mode type has been set. */
    public static final int UI_MODE_TYPE_UNDEFINED = 0x00;
    /** Constant for {@link #uiMode}: a {@link #UI_MODE_TYPE_MASK}
     * value that corresponds to
     * <a href="{@docRoot}guide/topics/resources/providing-resources.html#UiModeQualifier">no
     * UI mode</a> resource qualifier specified. */
    public static final int UI_MODE_TYPE_NORMAL = 0x01;
    /** Constant for {@link #uiMode}: a {@link #UI_MODE_TYPE_MASK}
     * value that corresponds to the
     * <a href="{@docRoot}guide/topics/resources/providing-resources.html#UiModeQualifier">desk</a>
     * resource qualifier. */
    public static final int UI_MODE_TYPE_DESK = 0x02;
    /** Constant for {@link #uiMode}: a {@link #UI_MODE_TYPE_MASK}
     * value that corresponds to the
     * <a href="{@docRoot}guide/topics/resources/providing-resources.html#UiModeQualifier">car</a>
     * resource qualifier. */
    public static final int UI_MODE_TYPE_CAR = 0x03;
    /** Constant for {@link #uiMode}: a {@link #UI_MODE_TYPE_MASK}
     * value that corresponds to the
     * <a href="{@docRoot}guide/topics/resources/providing-resources.html#UiModeQualifier">television</a>
     * resource qualifier. */
    public static final int UI_MODE_TYPE_TELEVISION = 0x04;
    /** Constant for {@link #uiMode}: a {@link #UI_MODE_TYPE_MASK}
     * value that corresponds to the
     * <a href="{@docRoot}guide/topics/resources/providing-resources.html#UiModeQualifier">appliance</a>
     * resource qualifier. */
    public static final int UI_MODE_TYPE_APPLIANCE = 0x05;
    /** Constant for {@link #uiMode}: a {@link #UI_MODE_TYPE_MASK}
     * value that corresponds to the
     * <a href="{@docRoot}guide/topics/resources/providing-resources.html#UiModeQualifier">watch</a>
     * resource qualifier. */
    public static final int UI_MODE_TYPE_WATCH = 0x06;
    /** Constant for {@link #uiMode}: a {@link #UI_MODE_TYPE_MASK}
     * value that corresponds to the
     * <a href="{@docRoot}guide/topics/resources/providing-resources.html#UiModeQualifier">vrheadset</a>
     * resource qualifier. */
    public static final int UI_MODE_TYPE_VR_HEADSET = 0x07;

    /** Constant for {@link #uiMode}: bits that encode the night mode. */
    public static final int UI_MODE_NIGHT_MASK = 0x30;
    /** Constant for {@link #uiMode}: a {@link #UI_MODE_NIGHT_MASK}
     * value indicating that no mode type has been set. */
    public static final int UI_MODE_NIGHT_UNDEFINED = 0x00;
    /** Constant for {@link #uiMode}: a {@link #UI_MODE_NIGHT_MASK}
     * value that corresponds to the
     * <a href="{@docRoot}guide/topics/resources/providing-resources.html#NightQualifier">notnight</a>
     * resource qualifier. */
    public static final int UI_MODE_NIGHT_NO = 0x10;
    /** Constant for {@link #uiMode}: a {@link #UI_MODE_NIGHT_MASK}
     * value that corresponds to the
     * <a href="{@docRoot}guide/topics/resources/providing-resources.html#NightQualifier">night</a>
     * resource qualifier. */
    public static final int UI_MODE_NIGHT_YES = 0x20;

    /**
     * Bit mask of the ui mode.  Currently there are two fields:
     * <p>The {@link #UI_MODE_TYPE_MASK} bits define the overall ui mode of the
     * device. They may be one of {@link #UI_MODE_TYPE_UNDEFINED},
     * {@link #UI_MODE_TYPE_NORMAL}, {@link #UI_MODE_TYPE_DESK},
     * {@link #UI_MODE_TYPE_CAR}, {@link #UI_MODE_TYPE_TELEVISION},
     * {@link #UI_MODE_TYPE_APPLIANCE}, {@link #UI_MODE_TYPE_WATCH},
     * or {@link #UI_MODE_TYPE_VR_HEADSET}.
     *
     * <p>The {@link #UI_MODE_NIGHT_MASK} defines whether the screen
     * is in a special mode. They may be one of {@link #UI_MODE_NIGHT_UNDEFINED},
     * {@link #UI_MODE_NIGHT_NO} or {@link #UI_MODE_NIGHT_YES}.
     */
    public int uiMode;

    /**
     * Default value for {@link #screenWidthDp} indicating that no width
     * has been specified.
     */
    public static final int SCREEN_WIDTH_DP_UNDEFINED = 0;

    /**
     * The width of the available screen space in dp units.
     *
     * <aside class="note"><b>Note:</b> If the app targets
     * {@link android.os.Build.VERSION_CODES#VANILLA_ICE_CREAM}
     * or after, the width measurement reflects the window size without excluding insets.
     * Otherwise, the measurement excludes window insets even when the app is displayed edge to edge
     * using {@link android.view.Window#setDecorFitsSystemWindows(boolean)
     * Window#setDecorFitsSystemWindows(boolean)}.</aside>
     *
     * Use {@link android.view.WindowMetrics#getBounds()} to always obtain the horizontal
     * display area available to an app or embedded activity including the area
     * occupied by window insets. A version of the API is also available for use on older platforms
     * through {@link androidx.window.layout.WindowMetrics}.
     *
     * <p>Corresponds to the
     * <a href="{@docRoot}guide/topics/resources/providing-resources.html#AvailableWidthHeightQualifier">
     * available width</a> resource qualifier. Defaults to
     * {@link #SCREEN_WIDTH_DP_UNDEFINED} if no width is specified.
     *
     * <p>In multi-window mode, equals the width of the available display area
     * of the app window, not the available display area of the device screen
     * (for example, when apps are displayed side by side in split-screen mode
     * in landscape orientation).
     *
     * <p>For embedded activities, equals the width of the individual
     * activities, not the width of the app window or the device screen.
     *
     * <p>In multiple-screen scenarios, the width measurement can span screens.
     * For example, if the app is spanning both screens of a dual-screen device
     * (with the screens side by side), {@code screenWidthDp} represents the
     * width of both screens excluding the area occupied by window insets. When
     * the app is restricted to a single screen in a multiple-screen
     * environment, {@code screenWidthDp} is the width of the screen on which
     * the app is displayed excluding window insets.
     *
     * <p>If the app targets {@link android.os.Build.VERSION_CODES#VANILLA_ICE_CREAM} or after,
     * it is the same as {@link android.view.WindowMetrics}, but is expressed rounded to the nearest
     * dp rather than px.
     *
     * <p>Otherwise, differs from {@link android.view.WindowMetrics} by not including
     * window insets in the width measurement and by expressing the measurement
     * in dp rather than px. Use {@code screenWidthDp} to obtain the width of
     * the display area available to an app or embedded activity excluding the
     * area occupied by window insets.
     */
    public int screenWidthDp;

    /**
     * Default value for {@link #screenHeightDp} indicating that no width
     * has been specified.
     */
    public static final int SCREEN_HEIGHT_DP_UNDEFINED = 0;

    /**
     * The height of the available screen space in dp units.
     *
     * <aside class="note"><b>Note:</b> If the app targets
     * {@link android.os.Build.VERSION_CODES#VANILLA_ICE_CREAM}
     * or after, the height measurement reflects the window size without excluding insets.
     * Otherwise, the measurement excludes window insets even when the app is displayed edge to edge
     * using {@link android.view.Window#setDecorFitsSystemWindows(boolean)
     * Window#setDecorFitsSystemWindows(boolean)}.</aside>
     *
     * Use {@link android.view.WindowMetrics#getBounds()} to always obtain the vertical
     * display area available to an app or embedded activity including the area
     * occupied by window insets. A version of the API is also available for use on older platforms
     * through {@link androidx.window.layout.WindowMetrics}.
     *
     * <p>Corresponds to the
     * <a href="{@docRoot}guide/topics/resources/providing-resources.html#AvailableWidthHeightQualifier">
     * available height</a> resource qualifier. Defaults to
     * {@link #SCREEN_HEIGHT_DP_UNDEFINED} if no height is specified.
     *
     * <p>In multi-window mode, equals the height of the available display area
     * of the app window, not the available display area of the device screen
     * (for example, when apps are displayed one above another in split-screen
     * mode in portrait orientation).
     *
     * <p>For embedded activities, equals the height of the individual
     * activities, not the height of the app window or the device screen.
     *
     * <p>In multiple-screen scenarios, the height measurement can span screens.
     * For example, if the app is spanning both screens of a dual-screen device
     * rotated 90 degrees (one screen above the other), {@code screenHeightDp}
     * represents the height of both screens excluding the area occupied by
     * window insets. When the app is restricted to a single screen in a
     * multiple-screen environment, {@code screenHeightDp} is the height of the
     * screen on which the app is displayed excluding window insets.
     *
     * <p>If the app targets {@link android.os.Build.VERSION_CODES#VANILLA_ICE_CREAM} or after,
     * it is the same as {@link android.view.WindowMetrics}, but is expressed rounded to the nearest
     * dp rather than px.
     *
     * <p>Otherwise, differs from {@link android.view.WindowMetrics} by not including
     * window insets in the height measurement and by expressing the measurement
     * in dp rather than px. Use {@code screenHeightDp} to obtain the height of
     * the display area available to an app or embedded activity excluding the
     * area occupied by window insets.
     */
    public int screenHeightDp;

    /**
     * Default value for {@link #smallestScreenWidthDp} indicating that no width
     * has been specified.
     */
    public static final int SMALLEST_SCREEN_WIDTH_DP_UNDEFINED = 0;

    /**
     * The smallest screen size an application will see in normal operation.
     * Corresponds to the
     * <a href="{@docRoot}guide/topics/resources/providing-resources.html#SmallestScreenWidthQualifier">
     * smallest width</a> resource qualifier. This is the smallest value of
     * {@link #screenWidthDp} and {@link #screenHeightDp} in both portrait and
     * landscape orientations. Defaults to
     * {@link #SMALLEST_SCREEN_WIDTH_DP_UNDEFINED} if no width is specified.
     */
    public int smallestScreenWidthDp;

    /**
     * Default value for {@link #densityDpi} indicating that no width
     * has been specified.
     */
    public static final int DENSITY_DPI_UNDEFINED = 0;

    /**
     * Value for {@link #densityDpi} for resources that scale to any density (vector drawables).
     * @hide
     */
    public static final int DENSITY_DPI_ANY = 0xfffe;

    /**
     * Value for {@link #densityDpi} for resources that are not meant to be scaled.
     * @hide
     */
    public static final int DENSITY_DPI_NONE = 0xffff;

    /**
     * The target screen density being rendered to,
     * corresponding to
     * <a href="{@docRoot}guide/topics/resources/providing-resources.html#DensityQualifier">density</a>
     * resource qualifier.  Set to
     * {@link #DENSITY_DPI_UNDEFINED} if no density is specified.
     */
    public int densityDpi;

    /** @hide Hack to get this information from WM to app running in compat mode. */
    public int compatScreenWidthDp;
    /** @hide Hack to get this information from WM to app running in compat mode. */
    public int compatScreenHeightDp;
    /** @hide Hack to get this information from WM to app running in compat mode. */
    public int compatSmallestScreenWidthDp;

    /**
     * An undefined assetsSeq. This will not override an existing assetsSeq.
     * @hide
     */
    public static final int ASSETS_SEQ_UNDEFINED = 0;

    /**
     * Internal counter that allows us to piggyback off the configuration change mechanism to
     * signal to apps that the the assets for an Application have changed. A difference in these
     * between two Configurations will yield a diff flag of
     * {@link ActivityInfo#CONFIG_ASSETS_PATHS}.
     * @hide
     */
    public int assetsSeq;

    /**
     * @hide Internal book-keeping.
     */
    public int seq;

    /** @hide */
    @IntDef(flag = true, prefix = { "NATIVE_CONFIG_" }, value = {
            NATIVE_CONFIG_MCC,
            NATIVE_CONFIG_MNC,
            NATIVE_CONFIG_LOCALE,
            NATIVE_CONFIG_TOUCHSCREEN,
            NATIVE_CONFIG_KEYBOARD,
            NATIVE_CONFIG_KEYBOARD_HIDDEN,
            NATIVE_CONFIG_NAVIGATION,
            NATIVE_CONFIG_ORIENTATION,
            NATIVE_CONFIG_DENSITY,
            NATIVE_CONFIG_SCREEN_SIZE,
            NATIVE_CONFIG_VERSION,
            NATIVE_CONFIG_SCREEN_LAYOUT,
            NATIVE_CONFIG_UI_MODE,
            NATIVE_CONFIG_SMALLEST_SCREEN_SIZE,
            NATIVE_CONFIG_LAYOUTDIR,
            NATIVE_CONFIG_COLOR_MODE,
            NATIVE_CONFIG_GRAMMATICAL_GENDER,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface NativeConfig {}

    /** @hide Native-specific bit mask for MCC config; DO NOT USE UNLESS YOU ARE SURE. */
    public static final int NATIVE_CONFIG_MCC = 0x0001;
    /** @hide Native-specific bit mask for MNC config; DO NOT USE UNLESS YOU ARE SURE. */
    public static final int NATIVE_CONFIG_MNC = 0x0002;
    /** @hide Native-specific bit mask for LOCALE config; DO NOT USE UNLESS YOU ARE SURE. */
    public static final int NATIVE_CONFIG_LOCALE = 0x0004;
    /** @hide Native-specific bit mask for TOUCHSCREEN config; DO NOT USE UNLESS YOU ARE SURE. */
    public static final int NATIVE_CONFIG_TOUCHSCREEN = 0x0008;
    /** @hide Native-specific bit mask for KEYBOARD config; DO NOT USE UNLESS YOU ARE SURE. */
    public static final int NATIVE_CONFIG_KEYBOARD = 0x0010;
    /** @hide Native-specific bit mask for KEYBOARD_HIDDEN config; DO NOT USE UNLESS YOU
     * ARE SURE. */
    public static final int NATIVE_CONFIG_KEYBOARD_HIDDEN = 0x0020;
    /** @hide Native-specific bit mask for NAVIGATION config; DO NOT USE UNLESS YOU ARE SURE. */
    public static final int NATIVE_CONFIG_NAVIGATION = 0x0040;
    /** @hide Native-specific bit mask for ORIENTATION config; DO NOT USE UNLESS YOU ARE SURE. */
    public static final int NATIVE_CONFIG_ORIENTATION = 0x0080;
    /** @hide Native-specific bit mask for DENSITY config; DO NOT USE UNLESS YOU ARE SURE. */
    public static final int NATIVE_CONFIG_DENSITY = 0x0100;
    /** @hide Native-specific bit mask for SCREEN_SIZE config; DO NOT USE UNLESS YOU ARE SURE. */
    public static final int NATIVE_CONFIG_SCREEN_SIZE = 0x0200;
    /** @hide Native-specific bit mask for VERSION config; DO NOT USE UNLESS YOU ARE SURE. */
    public static final int NATIVE_CONFIG_VERSION = 0x0400;
    /** @hide Native-specific bit mask for SCREEN_LAYOUT config; DO NOT USE UNLESS YOU ARE SURE. */
    public static final int NATIVE_CONFIG_SCREEN_LAYOUT = 0x0800;
    /** @hide Native-specific bit mask for UI_MODE config; DO NOT USE UNLESS YOU ARE SURE. */
    public static final int NATIVE_CONFIG_UI_MODE = 0x1000;
    /** @hide Native-specific bit mask for SMALLEST_SCREEN_SIZE config; DO NOT USE UNLESS YOU
     * ARE SURE. */
    public static final int NATIVE_CONFIG_SMALLEST_SCREEN_SIZE = 0x2000;
    /** @hide Native-specific bit mask for LAYOUTDIR config ; DO NOT USE UNLESS YOU ARE SURE.*/
    public static final int NATIVE_CONFIG_LAYOUTDIR = 0x4000;
    /** @hide Native-specific bit mask for COLOR_MODE config ; DO NOT USE UNLESS YOU ARE SURE.*/
    public static final int NATIVE_CONFIG_COLOR_MODE = 0x10000;
    /** @hide Native-specific bit mask for GRAMMATICAL_GENDER config; DO NOT USE UNLESS YOU
     * ARE SURE.*/
    public static final int NATIVE_CONFIG_GRAMMATICAL_GENDER = 0x20000;

    /**
     * <p>Construct an invalid Configuration. This state is only suitable for constructing a
     * Configuration delta that will be applied to some valid Configuration object. In order to
     * create a valid standalone Configuration, you must call {@link #setToDefaults}. </p>
     *
     * <p>Example:</p>
     * <pre class="prettyprint">
     *     Configuration validConfig = new Configuration();
     *     validConfig.setToDefaults();
     *
     *     Configuration deltaOnlyConfig = new Configuration();
     *     deltaOnlyConfig.orientation = Configuration.ORIENTATION_LANDSCAPE;
     *
     *     validConfig.updateFrom(deltaOnlyConfig);
     * </pre>
     */
    public Configuration() {
        unset();
    }

    /**
     * Makes a deep copy suitable for modification.
     */
    public Configuration(Configuration o) {
        throw new RuntimeException("Stub!");
    }

    /* This brings mLocaleList in sync with locale in case a user of the older API who doesn't know
     * about setLocales() has changed locale directly. */
    private void fixUpLocaleList() {
        if ((locale == null && !mLocaleList.isEmpty()) ||
                (locale != null && !locale.equals(mLocaleList.get(0)))) {
            mLocaleList = locale == null ? LocaleList.getEmptyLocaleList() : new LocaleList(locale);
        }
    }

    /**
     * Sets the fields in this object to those in the given Configuration.
     *
     * @param o The Configuration object used to set the values of this Configuration's fields.
     */
    public void setTo(Configuration o) {
        throw new RuntimeException("Stub!");
    }

    public String toString() {
        throw new RuntimeException("Stub!");
    }

    /**
     * Set this object to the system defaults.
     */
    public void setToDefaults() {
        fontScale = 1;
        mcc = mnc = 0;
        mLocaleList = null;
        locale = null;
        touchscreen = TOUCHSCREEN_UNDEFINED;
        keyboard = KEYBOARD_UNDEFINED;
        keyboardHidden = KEYBOARDHIDDEN_UNDEFINED;
        hardKeyboardHidden = HARDKEYBOARDHIDDEN_UNDEFINED;
        navigation = NAVIGATION_UNDEFINED;
        navigationHidden = NAVIGATIONHIDDEN_UNDEFINED;
        orientation = ORIENTATION_UNDEFINED;
        screenLayout = SCREENLAYOUT_UNDEFINED;
        colorMode = COLOR_MODE_UNDEFINED;
        uiMode = UI_MODE_TYPE_UNDEFINED;
        screenWidthDp = compatScreenWidthDp = SCREEN_WIDTH_DP_UNDEFINED;
        screenHeightDp = compatScreenHeightDp = SCREEN_HEIGHT_DP_UNDEFINED;
        smallestScreenWidthDp = compatSmallestScreenWidthDp = SMALLEST_SCREEN_WIDTH_DP_UNDEFINED;
        densityDpi = DENSITY_DPI_UNDEFINED;
        assetsSeq = ASSETS_SEQ_UNDEFINED;
        seq = 0;
        fontWeightAdjustment = FONT_WEIGHT_ADJUSTMENT_UNDEFINED;
    }

    public void unset() {
        setToDefaults();
        fontScale = 0;
    }

    /**
     * Copies the fields from delta into this Configuration object, keeping
     * track of which ones have changed. Any undefined fields in {@code delta}
     * are ignored and not copied in to the current Configuration.
     *
     * @return a bit mask of the changed fields, as per {@link #diff}
     */
    public int updateFrom(@NonNull Configuration delta) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Return a bit mask of the differences between this Configuration
     * object and the given one.  Does not change the values of either.  Any
     * undefined fields in <var>delta</var> are ignored.
     * @return Returns a bit mask indicating which configuration
     * values have changed.
     */
    public int diff(Configuration delta) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Determines if a new resource needs to be loaded from the bit set of
     * configuration changes returned by {@link #updateFrom(Configuration)}.
     *
     * @param configChanges the mask of changes configurations as returned by
     *                      {@link #updateFrom(Configuration)}
     * @param interestingChanges the configuration changes that the resource
     *                           can handle as given in
     *                           {@link android.util.TypedValue#changingConfigurations}
     * @return {@code true} if the resource needs to be loaded, {@code false}
     *         otherwise
     */
    public static boolean needNewResources(int configChanges,
            int interestingChanges) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Parcelable methods
     */
    public int describeContents() {
        throw new RuntimeException("Stub!");
    }

    public void writeToParcel(Parcel dest, int flags) {
        throw new RuntimeException("Stub!");
    }

    public void readFromParcel(Parcel source) {
        throw new RuntimeException("Stub!");
    }

    public static final @android.annotation.NonNull Parcelable.Creator<Configuration> CREATOR
            = new Parcelable.Creator<Configuration>() {
        public Configuration createFromParcel(Parcel source) {
            throw new RuntimeException("Stub!");
        }

        public Configuration[] newArray(int size) {
            throw new RuntimeException("Stub!");
        }
    };


    /**
     * Retuns whether the configuration is in night mode
     * @return true if night mode is active and false otherwise
     */
    public boolean isNightModeActive() {
        throw new RuntimeException("Stub!");
    }

    public int compareTo(Configuration that) {
        int n;
        float a = this.fontScale;
        float b = that.fontScale;
        if (a < b) return -1;
        if (a > b) return 1;
        n = this.mcc - that.mcc;
        if (n != 0) return n;
        n = this.mnc - that.mnc;
        if (n != 0) return n;

        fixUpLocaleList();
        that.fixUpLocaleList();
        // for backward compatibility, we consider an empty locale list to be greater
        // than any non-empty locale list.
        if (this.mLocaleList.isEmpty()) {
            if (!that.mLocaleList.isEmpty()) return 1;
        } else if (that.mLocaleList.isEmpty()) {
            return -1;
        } else {
            final int minSize = Math.min(this.mLocaleList.size(), that.mLocaleList.size());
            for (int i = 0; i < minSize; ++i) {
                final Locale thisLocale = this.mLocaleList.get(i);
                final Locale thatLocale = that.mLocaleList.get(i);
                n = thisLocale.getLanguage().compareTo(thatLocale.getLanguage());
                if (n != 0) return n;
                n = thisLocale.getCountry().compareTo(thatLocale.getCountry());
                if (n != 0) return n;
                n = thisLocale.getVariant().compareTo(thatLocale.getVariant());
                if (n != 0) return n;
                n = thisLocale.toLanguageTag().compareTo(thatLocale.toLanguageTag());
                if (n != 0) return n;
            }
            n = this.mLocaleList.size() - that.mLocaleList.size();
            if (n != 0) return n;
        }

        n = this.touchscreen - that.touchscreen;
        if (n != 0) return n;
        n = this.keyboard - that.keyboard;
        if (n != 0) return n;
        n = this.keyboardHidden - that.keyboardHidden;
        if (n != 0) return n;
        n = this.hardKeyboardHidden - that.hardKeyboardHidden;
        if (n != 0) return n;
        n = this.navigation - that.navigation;
        if (n != 0) return n;
        n = this.navigationHidden - that.navigationHidden;
        if (n != 0) return n;
        n = this.orientation - that.orientation;
        if (n != 0) return n;
        n = this.colorMode - that.colorMode;
        if (n != 0) return n;
        n = this.screenLayout - that.screenLayout;
        if (n != 0) return n;
        n = this.uiMode - that.uiMode;
        if (n != 0) return n;
        n = this.screenWidthDp - that.screenWidthDp;
        if (n != 0) return n;
        n = this.screenHeightDp - that.screenHeightDp;
        if (n != 0) return n;
        n = this.smallestScreenWidthDp - that.smallestScreenWidthDp;
        if (n != 0) return n;
        n = this.densityDpi - that.densityDpi;
        if (n != 0) return n;
        n = this.assetsSeq - that.assetsSeq;
        if (n != 0) return n;
        n = this.fontWeightAdjustment - that.fontWeightAdjustment;
        return n;
    }

    public boolean equals(Configuration that) {
        throw new RuntimeException("Stub!");
    }

    public boolean equals(@Nullable Object that) {
        throw new RuntimeException("Stub!");
    }

    public int hashCode() {
        throw new RuntimeException("Stub!");
    }

    /**
     * Returns the user preference for the grammatical gender. Will be
     * {@link #GRAMMATICAL_GENDER_NOT_SPECIFIED} or
     * {@link #GRAMMATICAL_GENDER_NEUTRAL} or
     * {@link #GRAMMATICAL_GENDER_FEMININE} or
     * {@link #GRAMMATICAL_GENDER_MASCULINE}.
     *
     * @return The preferred grammatical gender.
     */
    public int getGrammaticalGender() {
        throw new RuntimeException("Stub!");
    }

    /**
     * Get the locale list. This is the preferred way for getting the locales (instead of using
     * the direct accessor to {@link #locale}, which would only provide the primary locale).
     *
     * @return The locale list.
     */
    public @NonNull LocaleList getLocales() {
        throw new RuntimeException("Stub!");
    }

    /**
     * Set the locale list. This is the preferred way for setting up the locales (instead of using
     * the direct accessor or {@link #setLocale(Locale)}). This will also set the layout direction
     * according to the first locale in the list.
     *
     * Note that the layout direction will always come from the first locale in the locale list,
     * even if the locale is not supported by the resources (the resources may only support
     * another locale further down the list which has a different direction).
     *
     * @param locales The locale list. If null, an empty LocaleList will be assigned.
     */
    public void setLocales(@Nullable LocaleList locales) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Set the locale list to a list of just one locale. This will also set the layout direction
     * according to the locale.
     *
     * Note that after this is run, calling <code>.equals()</code> on the input locale and the
     * {@link #locale} attribute would return <code>true</code> if they are not null, but there is
     * no guarantee that they would be the same object.
     *
     * See also the note about layout direction in {@link #setLocales(LocaleList)}.
     *
     * @param loc The locale. Can be null.
     */
    public void setLocale(@Nullable Locale loc) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Return the layout direction. Will be either {@link View#LAYOUT_DIRECTION_LTR} or
     * {@link View#LAYOUT_DIRECTION_RTL}.
     *
     * @return Returns {@link View#LAYOUT_DIRECTION_RTL} if the configuration
     * is {@link #SCREENLAYOUT_LAYOUTDIR_RTL}, otherwise {@link View#LAYOUT_DIRECTION_LTR}.
     */
    public int getLayoutDirection() {
        throw new RuntimeException("Stub!");
    }

    /**
     * Set the layout direction from a Locale.
     *
     * @param loc The Locale. If null will set the layout direction to
     * {@link View#LAYOUT_DIRECTION_LTR}. If not null will set it to the layout direction
     * corresponding to the Locale.
     *
     * @see View#LAYOUT_DIRECTION_LTR
     * @see View#LAYOUT_DIRECTION_RTL
     */
    public void setLayoutDirection(Locale loc) {
        throw new RuntimeException("Stub!");
    }

    private static int getScreenLayoutNoDirection(int screenLayout) {
        return screenLayout&~SCREENLAYOUT_LAYOUTDIR_MASK;
    }

    /**
     * Return whether the screen has a round shape. Apps may choose to change styling based
     * on this property, such as the alignment or layout of text or informational icons.
     *
     * @return true if the screen is rounded, false otherwise
     */
    public boolean isScreenRound() {
        throw new RuntimeException("Stub!");
    }

    /**
     * Return whether the screen has a wide color gamut and wide color gamut rendering
     * is supported by this device.
     *
     * When true, it implies the screen is colorspace aware but not
     * necessarily color-managed. The final colors may still be changed by the
     * screen depending on user settings.
     *
     * @return true if the screen has a wide color gamut and wide color gamut rendering
     * is supported, false otherwise
     */
    public boolean isScreenWideColorGamut() {
        throw new RuntimeException("Stub!");
    }

    /**
     * Return whether the screen has a high dynamic range.
     *
     * @return true if the screen has a high dynamic range, false otherwise
     */
    public boolean isScreenHdr() {
        throw new RuntimeException("Stub!");
    }

    /**
     * Generate a delta Configuration between <code>base</code> and <code>change</code>. The
     * resulting delta can be used with {@link #updateFrom(Configuration)}.
     * <p />
     * Caveat: If the any of the Configuration's members becomes undefined, then
     * {@link #updateFrom(Configuration)} will treat it as a no-op and not update that member.
     *
     * This is fine for device configurations as no member is ever undefined.
     */
    @NonNull
    public static Configuration generateDelta(
            @NonNull Configuration base, @NonNull Configuration change) {
        throw new RuntimeException("Stub!");
    }
}
