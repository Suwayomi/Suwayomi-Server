/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.graphics;

import android.annotation.IntRange;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.Size;
import android.annotation.SuppressLint;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.function.DoubleUnaryOperator;

public abstract class ColorSpace {
    public static final float[] ILLUMINANT_A   = { 0.44757f, 0.40745f };
    public static final float[] ILLUMINANT_B   = { 0.34842f, 0.35161f };
    public static final float[] ILLUMINANT_C   = { 0.31006f, 0.31616f };
    public static final float[] ILLUMINANT_D50 = { 0.34567f, 0.35850f };
    public static final float[] ILLUMINANT_D55 = { 0.33242f, 0.34743f };
    public static final float[] ILLUMINANT_D60 = { 0.32168f, 0.33767f };
    public static final float[] ILLUMINANT_D65 = { 0.31271f, 0.32902f };
    public static final float[] ILLUMINANT_D75 = { 0.29902f, 0.31485f };
    public static final float[] ILLUMINANT_E   = { 0.33333f, 0.33333f };

    public static final int MIN_ID = -1; // Do not change
    public static final int MAX_ID = 63; // Do not change, used to encode in longs

    private static final float[] SRGB_PRIMARIES = { 0.640f, 0.330f, 0.300f, 0.600f, 0.150f, 0.060f };
    private static final float[] NTSC_1953_PRIMARIES = { 0.67f, 0.33f, 0.21f, 0.71f, 0.14f, 0.08f };
    private static final float[] DCI_P3_PRIMARIES =
            { 0.680f, 0.320f, 0.265f, 0.690f, 0.150f, 0.060f };
    private static final float[] BT2020_PRIMARIES =
            { 0.708f, 0.292f, 0.170f, 0.797f, 0.131f, 0.046f };
    private static final float[] GRAY_PRIMARIES = { 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f };

    private static final float[] ILLUMINANT_D50_XYZ = { 0.964212f, 1.0f, 0.825188f };

    private static final Rgb.TransferParameters SRGB_TRANSFER_PARAMETERS =
            new Rgb.TransferParameters(1 / 1.055, 0.055 / 1.055, 1 / 12.92, 0.04045, 2.4);

    private static final Rgb.TransferParameters SMPTE_170M_TRANSFER_PARAMETERS =
            new Rgb.TransferParameters(1 / 1.099, 0.099 / 1.099, 1 / 4.5, 0.081, 1 / 0.45);

    // HLG transfer with an SDR whitepoint of 203 nits
    private static final Rgb.TransferParameters BT2020_HLG_TRANSFER_PARAMETERS =
            new Rgb.TransferParameters(2.0, 2.0, 1 / 0.17883277, 0.28466892, 0.55991073,
                    -0.685490157, Rgb.TransferParameters.TYPE_HLGish);

    // PQ transfer with an SDR whitepoint of 203 nits
    private static final Rgb.TransferParameters BT2020_PQ_TRANSFER_PARAMETERS =
            new Rgb.TransferParameters(-1.555223, 1.860454, 32 / 2523.0, 2413 / 128.0,
                    -2392 / 128.0, 8192 / 1305.0, Rgb.TransferParameters.TYPE_PQish);

    // See static initialization block next to #get(Named)
    private static final HashMap<Integer, ColorSpace> sNamedColorSpaceMap =
            new HashMap<>();

    @NonNull private final String mName;
    @NonNull private final Model mModel;
    @IntRange(from = MIN_ID, to = MAX_ID) private final int mId;

    public enum Named {
        // NOTE: Do NOT change the order of the enum
        SRGB,
        LINEAR_SRGB,
        EXTENDED_SRGB,
        LINEAR_EXTENDED_SRGB,
        BT709,
        BT2020,
        DCI_P3,
        DISPLAY_P3,
        NTSC_1953,
        SMPTE_C,
        ADOBE_RGB,
        PRO_PHOTO_RGB,
        ACES,
        ACESCG,
        CIE_XYZ,
        CIE_LAB,
        BT2020_HLG,
        BT2020_PQ,

        OK_LAB,

        DISPLAY_BT2020
        // Update the initialization block next to #get(Named) when adding new values
    }

    public enum RenderIntent {
        PERCEPTUAL,
        RELATIVE,
        SATURATION,
        ABSOLUTE
    }

    public enum Adaptation {
        BRADFORD(new float[] {
                 0.8951f, -0.7502f,  0.0389f,
                 0.2664f,  1.7135f, -0.0685f,
                -0.1614f,  0.0367f,  1.0296f
        }),
        VON_KRIES(new float[] {
                 0.40024f, -0.22630f, 0.00000f,
                 0.70760f,  1.16532f, 0.00000f,
                -0.08081f,  0.04570f, 0.91822f
        }),
        CIECAT02(new float[] {
                 0.7328f, -0.7036f,  0.0030f,
                 0.4296f,  1.6975f,  0.0136f,
                -0.1624f,  0.0061f,  0.9834f
        });

        final float[] mTransform;

        Adaptation(@NonNull @Size(9) float[] transform) {
            mTransform = transform;
        }
    }

    public enum Model {
        RGB(3),
        XYZ(3),
        LAB(3),
        CMYK(4);

        private final int mComponentCount;

        Model(@IntRange(from = 1, to = 4) int componentCount) {
            mComponentCount = componentCount;
        }

        @IntRange(from = 1, to = 4)
        public int getComponentCount() {
            return mComponentCount;
        }
    }

    /*package*/ ColorSpace(
            @NonNull String name,
            @NonNull Model model,
            @IntRange(from = MIN_ID, to = MAX_ID) int id) {

        if (name == null || name.length() < 1) {
            throw new IllegalArgumentException("The name of a color space cannot be null and " +
                    "must contain at least 1 character");
        }

        if (model == null) {
            throw new IllegalArgumentException("A color space must have a model");
        }

        if (id < MIN_ID || id > MAX_ID) {
            throw new IllegalArgumentException("The id must be between " +
                    MIN_ID + " and " + MAX_ID);
        }

        mName = name;
        mModel = model;
        mId = id;
    }

    @NonNull
    public String getName() {
        return mName;
    }

    @IntRange(from = MIN_ID, to = MAX_ID)
    public int getId() {
        return mId;
    }

    @NonNull
    public Model getModel() {
        return mModel;
    }

    @IntRange(from = 1, to = 4)
    public int getComponentCount() {
        return mModel.getComponentCount();
    }

    public abstract boolean isWideGamut();

    public boolean isSrgb() {
        return false;
    }

    public abstract float getMinValue(@IntRange(from = 0, to = 3) int component);

    public abstract float getMaxValue(@IntRange(from = 0, to = 3) int component);

    @NonNull
    @Size(3)
    public float[] toXyz(float r, float g, float b) {
        return toXyz(new float[] { r, g, b });
    }

    @NonNull
    @Size(min = 3)
    public abstract float[] toXyz(@NonNull @Size(min = 3) float[] v);

    @NonNull
    @Size(min = 3)
    public float[] fromXyz(float x, float y, float z) {
        float[] xyz = new float[mModel.getComponentCount()];
        xyz[0] = x;
        xyz[1] = y;
        xyz[2] = z;
        return fromXyz(xyz);
    }

    @NonNull
    @Size(min = 3)
    public abstract float[] fromXyz(@NonNull @Size(min = 3) float[] v);

    @Override
    @NonNull
    public String toString() {
        return mName + " (id=" + mId + ", model=" + mModel + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ColorSpace that = (ColorSpace) o;

        if (mId != that.mId) return false;
        //noinspection SimplifiableIfStatement
        if (!mName.equals(that.mName)) return false;
        return mModel == that.mModel;

    }

    @Override
    public int hashCode() {
        int result = mName.hashCode();
        result = 31 * result + mModel.hashCode();
        result = 31 * result + mId;
        return result;
    }

    @NonNull
    public static Connector connect(@NonNull ColorSpace source, @NonNull ColorSpace destination) {
        return connect(source, destination, RenderIntent.PERCEPTUAL);
    }

    @NonNull
    @SuppressWarnings("ConstantConditions")
    public static Connector connect(@NonNull ColorSpace source, @NonNull ColorSpace destination,
            @NonNull RenderIntent intent) {
        if (source.equals(destination)) return Connector.identity(source);

        if (source.getModel() == Model.RGB && destination.getModel() == Model.RGB) {
            return new Connector.Rgb((Rgb) source, (Rgb) destination, intent);
        }

        return new Connector(source, destination, intent);
    }

    @NonNull
    public static Connector connect(@NonNull ColorSpace source) {
        return connect(source, RenderIntent.PERCEPTUAL);
    }

    @NonNull
    public static Connector connect(@NonNull ColorSpace source, @NonNull RenderIntent intent) {
        if (source.isSrgb()) return Connector.identity(source);

        if (source.getModel() == Model.RGB) {
            return new Connector.Rgb((Rgb) source, (Rgb) get(Named.SRGB), intent);
        }

        return new Connector(source, get(Named.SRGB), intent);
    }

    @NonNull
    public static ColorSpace adapt(@NonNull ColorSpace colorSpace,
            @NonNull @Size(min = 2, max = 3) float[] whitePoint) {
        return adapt(colorSpace, whitePoint, Adaptation.BRADFORD);
    }

    @NonNull
    public static ColorSpace adapt(@NonNull ColorSpace colorSpace,
            @NonNull @Size(min = 2, max = 3) float[] whitePoint,
            @NonNull Adaptation adaptation) {
        if (colorSpace.getModel() == Model.RGB) {
            ColorSpace.Rgb rgb = (ColorSpace.Rgb) colorSpace;
            if (compare(rgb.mWhitePoint, whitePoint)) return colorSpace;

            float[] xyz = whitePoint.length == 3 ?
                    Arrays.copyOf(whitePoint, 3) : xyYToXyz(whitePoint);
            float[] adaptationTransform = chromaticAdaptation(adaptation.mTransform,
                    xyYToXyz(rgb.getWhitePoint()), xyz);
            float[] transform = mul3x3(adaptationTransform, rgb.mTransform);

            return new ColorSpace.Rgb(rgb, transform, whitePoint);
        }
        return colorSpace;
    }

    @NonNull @Size(9)
    private static float[] adaptToIlluminantD50(
            @NonNull @Size(2) float[] origWhitePoint,
            @NonNull @Size(9) float[] origTransform) {
        float[] desired = ILLUMINANT_D50;
        if (compare(origWhitePoint, desired)) return origTransform;

        float[] xyz = xyYToXyz(desired);
        float[] adaptationTransform = chromaticAdaptation(Adaptation.BRADFORD.mTransform,
                    xyYToXyz(origWhitePoint), xyz);
        return mul3x3(adaptationTransform, origTransform);
    }

    @NonNull
    static ColorSpace get(@IntRange(from = MIN_ID, to = MAX_ID) int index) {
        ColorSpace colorspace = sNamedColorSpaceMap.get(index);
        if (colorspace == null) {
            throw new IllegalArgumentException("Invalid ID: " + index);
        }
        return colorspace;
    }

    @SuppressLint("MethodNameUnits")
    @Nullable
    public static ColorSpace getFromDataSpace(int dataSpace) {
        return null;
    }

    @SuppressLint("MethodNameUnits")
    public int getDataSpace() {
        return -1;
    }

    @NonNull
    public static ColorSpace get(@NonNull Named name) {
        ColorSpace colorSpace = sNamedColorSpaceMap.get(name.ordinal());
        if (colorSpace == null) {
            return sNamedColorSpaceMap.get(Named.SRGB.ordinal());
        }
        return colorSpace;
    }

    @Nullable
    public static ColorSpace match(
            @NonNull @Size(9) float[] toXYZD50,
            @NonNull Rgb.TransferParameters function) {

        Collection<ColorSpace> colorspaces = sNamedColorSpaceMap.values();
        for (ColorSpace colorSpace : colorspaces) {
            if (colorSpace.getModel() == Model.RGB) {
                ColorSpace.Rgb rgb = (ColorSpace.Rgb) adapt(colorSpace, ILLUMINANT_D50_XYZ);
                if (compare(toXYZD50, rgb.mTransform) &&
                        compare(function, rgb.mTransferParameters)) {
                    return colorSpace;
                }
            }
        }

        return null;
    }

    static {
        sNamedColorSpaceMap.put(Named.SRGB.ordinal(), new ColorSpace.Rgb(
                "sRGB IEC61966-2.1",
                SRGB_PRIMARIES,
                ILLUMINANT_D65,
                null,
                SRGB_TRANSFER_PARAMETERS,
                Named.SRGB.ordinal()
        ));
        sNamedColorSpaceMap.put(Named.LINEAR_SRGB.ordinal(), new ColorSpace.Rgb(
                "sRGB IEC61966-2.1 (Linear)",
                SRGB_PRIMARIES,
                ILLUMINANT_D65,
                1.0,
                0.0f, 1.0f,
                Named.LINEAR_SRGB.ordinal()
        ));
        sNamedColorSpaceMap.put(Named.EXTENDED_SRGB.ordinal(), new ColorSpace.Rgb(
                "scRGB-nl IEC 61966-2-2:2003",
                SRGB_PRIMARIES,
                ILLUMINANT_D65,
                null,
                x -> absRcpResponse(x, 1 / 1.055, 0.055 / 1.055, 1 / 12.92, 0.04045, 2.4),
                x -> absResponse(x, 1 / 1.055, 0.055 / 1.055, 1 / 12.92, 0.04045, 2.4),
                -0.799f, 2.399f,
                SRGB_TRANSFER_PARAMETERS,
                Named.EXTENDED_SRGB.ordinal()
        ));
        sNamedColorSpaceMap.put(Named.LINEAR_EXTENDED_SRGB.ordinal(), new ColorSpace.Rgb(
                "scRGB IEC 61966-2-2:2003",
                SRGB_PRIMARIES,
                ILLUMINANT_D65,
                1.0,
                -0.5f, 7.499f,
                Named.LINEAR_EXTENDED_SRGB.ordinal()
        ));
        sNamedColorSpaceMap.put(Named.BT709.ordinal(), new ColorSpace.Rgb(
                "Rec. ITU-R BT.709-5",
                SRGB_PRIMARIES,
                ILLUMINANT_D65,
                null,
                SMPTE_170M_TRANSFER_PARAMETERS,
                Named.BT709.ordinal()
        ));
        sNamedColorSpaceMap.put(Named.BT2020.ordinal(), new ColorSpace.Rgb(
                "Rec. ITU-R BT.2020-1",
                BT2020_PRIMARIES,
                ILLUMINANT_D65,
                null,
                new Rgb.TransferParameters(1 / 1.0993, 0.0993 / 1.0993, 1 / 4.5, 0.08145, 1 / 0.45),
                Named.BT2020.ordinal()
        ));

        sNamedColorSpaceMap.put(Named.DCI_P3.ordinal(), new ColorSpace.Rgb(
                "SMPTE RP 431-2-2007 DCI (P3)",
                DCI_P3_PRIMARIES,
                new float[] { 0.314f, 0.351f },
                2.6,
                0.0f, 1.0f,
                Named.DCI_P3.ordinal()
        ));
        sNamedColorSpaceMap.put(Named.DISPLAY_P3.ordinal(), new ColorSpace.Rgb(
                "Display P3",
                DCI_P3_PRIMARIES,
                ILLUMINANT_D65,
                null,
                SRGB_TRANSFER_PARAMETERS,
                Named.DISPLAY_P3.ordinal()
        ));
        sNamedColorSpaceMap.put(Named.NTSC_1953.ordinal(), new ColorSpace.Rgb(
                "NTSC (1953)",
                NTSC_1953_PRIMARIES,
                ILLUMINANT_C,
                null,
                SMPTE_170M_TRANSFER_PARAMETERS,
                Named.NTSC_1953.ordinal()
        ));
        sNamedColorSpaceMap.put(Named.SMPTE_C.ordinal(), new ColorSpace.Rgb(
                "SMPTE-C RGB",
                new float[] { 0.630f, 0.340f, 0.310f, 0.595f, 0.155f, 0.070f },
                ILLUMINANT_D65,
                null,
                SMPTE_170M_TRANSFER_PARAMETERS,
                Named.SMPTE_C.ordinal()
        ));
        sNamedColorSpaceMap.put(Named.ADOBE_RGB.ordinal(), new ColorSpace.Rgb(
                "Adobe RGB (1998)",
                new float[] { 0.64f, 0.33f, 0.21f, 0.71f, 0.15f, 0.06f },
                ILLUMINANT_D65,
                2.2,
                0.0f, 1.0f,
                Named.ADOBE_RGB.ordinal()
        ));
        sNamedColorSpaceMap.put(Named.PRO_PHOTO_RGB.ordinal(), new ColorSpace.Rgb(
                "ROMM RGB ISO 22028-2:2013",
                new float[] { 0.7347f, 0.2653f, 0.1596f, 0.8404f, 0.0366f, 0.0001f },
                ILLUMINANT_D50,
                null,
                new Rgb.TransferParameters(1.0, 0.0, 1 / 16.0, 0.031248, 1.8),
                Named.PRO_PHOTO_RGB.ordinal()
        ));
        sNamedColorSpaceMap.put(Named.ACES.ordinal(), new ColorSpace.Rgb(
                "SMPTE ST 2065-1:2012 ACES",
                new float[] { 0.73470f, 0.26530f, 0.0f, 1.0f, 0.00010f, -0.0770f },
                ILLUMINANT_D60,
                1.0,
                -65504.0f, 65504.0f,
                Named.ACES.ordinal()
        ));
        sNamedColorSpaceMap.put(Named.ACESCG.ordinal(), new ColorSpace.Rgb(
                "Academy S-2014-004 ACEScg",
                new float[] { 0.713f, 0.293f, 0.165f, 0.830f, 0.128f, 0.044f },
                ILLUMINANT_D60,
                1.0,
                -65504.0f, 65504.0f,
                Named.ACESCG.ordinal()
        ));
        sNamedColorSpaceMap.put(Named.CIE_XYZ.ordinal(), new Xyz(
                "Generic XYZ",
                Named.CIE_XYZ.ordinal()
        ));
        sNamedColorSpaceMap.put(Named.CIE_LAB.ordinal(), new ColorSpace.Lab(
                "Generic L*a*b*",
                Named.CIE_LAB.ordinal()
        ));
        sNamedColorSpaceMap.put(Named.BT2020_HLG.ordinal(), new ColorSpace.Rgb(
                "Hybrid Log Gamma encoding",
                BT2020_PRIMARIES,
                ILLUMINANT_D65,
                null,
                x -> transferHLGOETF(BT2020_HLG_TRANSFER_PARAMETERS, x),
                x -> transferHLGEOTF(BT2020_HLG_TRANSFER_PARAMETERS, x),
                0.0f, 1.0f,
                BT2020_HLG_TRANSFER_PARAMETERS,
                Named.BT2020_HLG.ordinal()
        ));
        sNamedColorSpaceMap.put(Named.BT2020_PQ.ordinal(), new ColorSpace.Rgb(
                "Perceptual Quantizer encoding",
                BT2020_PRIMARIES,
                ILLUMINANT_D65,
                null,
                x -> transferST2048OETF(BT2020_PQ_TRANSFER_PARAMETERS, x),
                x -> transferST2048EOTF(BT2020_PQ_TRANSFER_PARAMETERS, x),
                0.0f, 1.0f,
                BT2020_PQ_TRANSFER_PARAMETERS,
                Named.BT2020_PQ.ordinal()
        ));
    }

    private static double transferHLGOETF(Rgb.TransferParameters params, double x) {
        double sign = x < 0 ? -1.0 : 1.0;
        x *= sign;

        // Unpack the transfer params matching skia's packing & invert R, G, and a
        final double R = 1.0 / params.a;
        final double G = 1.0 / params.b;
        final double a = 1.0 / params.c;
        final double b = params.d;
        final double c = params.e;
        final double K = params.f + 1.0;

        x /= K;
        return sign * (x <= 1 ? R * Math.pow(x, G) : a * Math.log(x - b) + c);
    }

    private static double transferHLGEOTF(Rgb.TransferParameters params, double x) {
        double sign = x < 0 ? -1.0 : 1.0;
        x *= sign;

        // Unpack the transfer params matching skia's packing
        final double R = params.a;
        final double G = params.b;
        final double a = params.c;
        final double b = params.d;
        final double c = params.e;
        final double K = params.f + 1.0;

        return K * sign * (x * R <= 1 ? Math.pow(x * R, G) : Math.exp((x - c) * a) + b);
    }

    private static double transferST2048OETF(Rgb.TransferParameters params, double x) {
        double sign = x < 0 ? -1.0 : 1.0;
        x *= sign;

        double a = -params.a;
        double b = params.d;
        double c = 1.0 / params.f;
        double d = params.b;
        double e = -params.e;
        double f = 1.0 / params.c;

        double tmp = Math.max(a + b * Math.pow(x, c), 0);
        return sign * Math.pow(tmp / (d + e * Math.pow(x, c)), f);
    }

    private static double transferST2048EOTF(Rgb.TransferParameters pq, double x) {
        double sign = x < 0 ? -1.0 : 1.0;
        x *= sign;

        double tmp = Math.max(pq.a + pq.b * Math.pow(x, pq.c), 0);
        return sign * Math.pow(tmp / (pq.d + pq.e * Math.pow(x, pq.c)), pq.f);
    }

    // Reciprocal piecewise gamma response
    private static double rcpResponse(double x, double a, double b, double c, double d, double g) {
        return x >= d * c ? (Math.pow(x, 1.0 / g) - b) / a : x / c;
    }

    // Piecewise gamma response
    private static double response(double x, double a, double b, double c, double d, double g) {
        return x >= d ? Math.pow(a * x + b, g) : c * x;
    }

    // Reciprocal piecewise gamma response
    private static double rcpResponse(double x, double a, double b, double c, double d,
            double e, double f, double g) {
        return x >= d * c ? (Math.pow(x - e, 1.0 / g) - b) / a : (x - f) / c;
    }

    // Piecewise gamma response
    private static double response(double x, double a, double b, double c, double d,
            double e, double f, double g) {
        return x >= d ? Math.pow(a * x + b, g) + e : c * x + f;
    }

    // Reciprocal piecewise gamma response, encoded as sign(x).f(abs(x)) for color
    // spaces that allow negative values
    @SuppressWarnings("SameParameterValue")
    private static double absRcpResponse(double x, double a, double b, double c, double d, double g) {
        return Math.copySign(rcpResponse(x < 0.0 ? -x : x, a, b, c, d, g), x);
    }

    // Piecewise gamma response, encoded as sign(x).f(abs(x)) for color spaces that
    // allow negative values
    @SuppressWarnings("SameParameterValue")
    private static double absResponse(double x, double a, double b, double c, double d, double g) {
        return Math.copySign(response(x < 0.0 ? -x : x, a, b, c, d, g), x);
    }

    private static boolean compare(
            @Nullable Rgb.TransferParameters a,
            @Nullable Rgb.TransferParameters b) {
        //noinspection SimplifiableIfStatement
        if (a == null && b == null) return true;
        return a != null && b != null &&
                Math.abs(a.a - b.a) < 1e-3 &&
                Math.abs(a.b - b.b) < 1e-3 &&
                Math.abs(a.c - b.c) < 1e-3 &&
                Math.abs(a.d - b.d) < 2e-3 && // Special case for variations in sRGB OETF/EOTF
                Math.abs(a.e - b.e) < 1e-3 &&
                Math.abs(a.f - b.f) < 1e-3 &&
                Math.abs(a.g - b.g) < 1e-3;
    }

    private static boolean compare(@NonNull float[] a, @NonNull float[] b) {
        if (a == b) return true;
        for (int i = 0; i < a.length; i++) {
            if (Float.compare(a[i], b[i]) != 0 && Math.abs(a[i] - b[i]) > 1e-3f) return false;
        }
        return true;
    }

    @NonNull
    @Size(9)
    private static float[] inverse3x3(@NonNull @Size(9) float[] m) {
        float a = m[0];
        float b = m[3];
        float c = m[6];
        float d = m[1];
        float e = m[4];
        float f = m[7];
        float g = m[2];
        float h = m[5];
        float i = m[8];

        float A = e * i - f * h;
        float B = f * g - d * i;
        float C = d * h - e * g;

        float det = a * A + b * B + c * C;

        float inverted[] = new float[m.length];
        inverted[0] = A / det;
        inverted[1] = B / det;
        inverted[2] = C / det;
        inverted[3] = (c * h - b * i) / det;
        inverted[4] = (a * i - c * g) / det;
        inverted[5] = (b * g - a * h) / det;
        inverted[6] = (b * f - c * e) / det;
        inverted[7] = (c * d - a * f) / det;
        inverted[8] = (a * e - b * d) / det;
        return inverted;
    }

    @NonNull
    @Size(9)
    private static float[] mul3x3(@NonNull @Size(9) float[] lhs, @NonNull @Size(9) float[] rhs) {
        float[] r = new float[9];
        r[0] = lhs[0] * rhs[0] + lhs[3] * rhs[1] + lhs[6] * rhs[2];
        r[1] = lhs[1] * rhs[0] + lhs[4] * rhs[1] + lhs[7] * rhs[2];
        r[2] = lhs[2] * rhs[0] + lhs[5] * rhs[1] + lhs[8] * rhs[2];
        r[3] = lhs[0] * rhs[3] + lhs[3] * rhs[4] + lhs[6] * rhs[5];
        r[4] = lhs[1] * rhs[3] + lhs[4] * rhs[4] + lhs[7] * rhs[5];
        r[5] = lhs[2] * rhs[3] + lhs[5] * rhs[4] + lhs[8] * rhs[5];
        r[6] = lhs[0] * rhs[6] + lhs[3] * rhs[7] + lhs[6] * rhs[8];
        r[7] = lhs[1] * rhs[6] + lhs[4] * rhs[7] + lhs[7] * rhs[8];
        r[8] = lhs[2] * rhs[6] + lhs[5] * rhs[7] + lhs[8] * rhs[8];
        return r;
    }

    @NonNull
    @Size(min = 3)
    private static float[] mul3x3Float3(
            @NonNull @Size(9) float[] lhs, @NonNull @Size(min = 3) float[] rhs) {
        float r0 = rhs[0];
        float r1 = rhs[1];
        float r2 = rhs[2];
        rhs[0] = lhs[0] * r0 + lhs[3] * r1 + lhs[6] * r2;
        rhs[1] = lhs[1] * r0 + lhs[4] * r1 + lhs[7] * r2;
        rhs[2] = lhs[2] * r0 + lhs[5] * r1 + lhs[8] * r2;
        return rhs;
    }

    @NonNull
    @Size(9)
    private static float[] mul3x3Diag(
            @NonNull @Size(3) float[] lhs, @NonNull @Size(9) float[] rhs) {
        return new float[] {
                lhs[0] * rhs[0], lhs[1] * rhs[1], lhs[2] * rhs[2],
                lhs[0] * rhs[3], lhs[1] * rhs[4], lhs[2] * rhs[5],
                lhs[0] * rhs[6], lhs[1] * rhs[7], lhs[2] * rhs[8]
        };
    }

    @NonNull
    @Size(3)
    private static float[] xyYToXyz(@NonNull @Size(2) float[] xyY) {
        return new float[] { xyY[0] / xyY[1], 1.0f, (1 - xyY[0] - xyY[1]) / xyY[1] };
    }

    @NonNull
    @Size(9)
    private static float[] chromaticAdaptation(@NonNull @Size(9) float[] matrix,
            @NonNull @Size(3) float[] srcWhitePoint, @NonNull @Size(3) float[] dstWhitePoint) {
        float[] srcLMS = mul3x3Float3(matrix, srcWhitePoint);
        float[] dstLMS = mul3x3Float3(matrix, dstWhitePoint);
        // LMS is a diagonal matrix stored as a float[3]
        float[] LMS = { dstLMS[0] / srcLMS[0], dstLMS[1] / srcLMS[1], dstLMS[2] / srcLMS[2] };
        return mul3x3(inverse3x3(matrix), mul3x3Diag(LMS, matrix));
    }

    @NonNull
    @Size(3)
    public static float[] cctToXyz(@IntRange(from = 1) int cct) {
        if (cct < 1) {
            throw new IllegalArgumentException("Temperature must be greater than 0");
        }

        final float icct = 1e3f / cct;
        final float icct2 = icct * icct;
        final float x = cct <= 4000.0f ?
            0.179910f + 0.8776956f * icct - 0.2343589f * icct2 - 0.2661239f * icct2 * icct :
            0.240390f + 0.2226347f * icct + 2.1070379f * icct2 - 3.0258469f * icct2 * icct;

        final float x2 = x * x;
        final float y = cct <= 2222.0f ?
            -0.20219683f + 2.18555832f * x - 1.34811020f * x2 - 1.1063814f * x2 * x :
            cct <= 4000.0f ?
            -0.16748867f + 2.09137015f * x - 1.37418593f * x2 - 0.9549476f * x2 * x :
            -0.37001483f + 3.75112997f * x - 5.8733867f * x2 + 3.0817580f * x2 * x;

        return xyYToXyz(new float[] {x, y});
    }

    @NonNull
    @Size(9)
    public static float[] chromaticAdaptation(@NonNull Adaptation adaptation,
            @NonNull @Size(min = 2, max = 3) float[] srcWhitePoint,
            @NonNull @Size(min = 2, max = 3) float[] dstWhitePoint) {
        if ((srcWhitePoint.length != 2 && srcWhitePoint.length != 3)
                || (dstWhitePoint.length != 2 && dstWhitePoint.length != 3)) {
            throw new IllegalArgumentException("A white point array must have 2 or 3 floats");
        }
        float[] srcXyz = srcWhitePoint.length == 3 ?
            Arrays.copyOf(srcWhitePoint, 3) : xyYToXyz(srcWhitePoint);
        float[] dstXyz = dstWhitePoint.length == 3 ?
            Arrays.copyOf(dstWhitePoint, 3) : xyYToXyz(dstWhitePoint);

        if (compare(srcXyz, dstXyz)) {
            return new float[] {
                1.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f,
                0.0f, 0.0f, 1.0f
            };
        }
        return chromaticAdaptation(adaptation.mTransform, srcXyz, dstXyz);
    }

    private static final class Xyz extends ColorSpace {
        private Xyz(@NonNull String name, @IntRange(from = MIN_ID, to = MAX_ID) int id) {
            super(name, Model.XYZ, id);
        }

        @Override
        public boolean isWideGamut() {
            return true;
        }

        @Override
        public float getMinValue(@IntRange(from = 0, to = 3) int component) {
            return -2.0f;
        }

        @Override
        public float getMaxValue(@IntRange(from = 0, to = 3) int component) {
            return 2.0f;
        }

        @Override
        public float[] toXyz(@NonNull @Size(min = 3) float[] v) {
            v[0] = clamp(v[0]);
            v[1] = clamp(v[1]);
            v[2] = clamp(v[2]);
            return v;
        }

        @Override
        public float[] fromXyz(@NonNull @Size(min = 3) float[] v) {
            v[0] = clamp(v[0]);
            v[1] = clamp(v[1]);
            v[2] = clamp(v[2]);
            return v;
        }

        private static float clamp(float x) {
            return x < -2.0f ? -2.0f : x > 2.0f ? 2.0f : x;
        }
    }

    private static final class Lab extends ColorSpace {
        private static final float A = 216.0f / 24389.0f;
        private static final float B = 841.0f / 108.0f;
        private static final float C = 4.0f / 29.0f;
        private static final float D = 6.0f / 29.0f;

        private Lab(@NonNull String name, @IntRange(from = MIN_ID, to = MAX_ID) int id) {
            super(name, Model.LAB, id);
        }

        @Override
        public boolean isWideGamut() {
            return true;
        }

        @Override
        public float getMinValue(@IntRange(from = 0, to = 3) int component) {
            return component == 0 ? 0.0f : -128.0f;
        }

        @Override
        public float getMaxValue(@IntRange(from = 0, to = 3) int component) {
            return component == 0 ? 100.0f : 128.0f;
        }

        @Override
        public float[] toXyz(@NonNull @Size(min = 3) float[] v) {
            v[0] = clamp(v[0], 0.0f, 100.0f);
            v[1] = clamp(v[1], -128.0f, 128.0f);
            v[2] = clamp(v[2], -128.0f, 128.0f);

            float fy = (v[0] + 16.0f) / 116.0f;
            float fx = fy + (v[1] * 0.002f);
            float fz = fy - (v[2] * 0.005f);
            float X = fx > D ? fx * fx * fx : (1.0f / B) * (fx - C);
            float Y = fy > D ? fy * fy * fy : (1.0f / B) * (fy - C);
            float Z = fz > D ? fz * fz * fz : (1.0f / B) * (fz - C);

            v[0] = X * ILLUMINANT_D50_XYZ[0];
            v[1] = Y * ILLUMINANT_D50_XYZ[1];
            v[2] = Z * ILLUMINANT_D50_XYZ[2];

            return v;
        }

        @Override
        public float[] fromXyz(@NonNull @Size(min = 3) float[] v) {
            float X = v[0] / ILLUMINANT_D50_XYZ[0];
            float Y = v[1] / ILLUMINANT_D50_XYZ[1];
            float Z = v[2] / ILLUMINANT_D50_XYZ[2];

            float fx = X > A ? (float) Math.pow(X, 1.0 / 3.0) : B * X + C;
            float fy = Y > A ? (float) Math.pow(Y, 1.0 / 3.0) : B * Y + C;
            float fz = Z > A ? (float) Math.pow(Z, 1.0 / 3.0) : B * Z + C;

            float L = 116.0f * fy - 16.0f;
            float a = 500.0f * (fx - fy);
            float b = 200.0f * (fy - fz);

            v[0] = clamp(L, 0.0f, 100.0f);
            v[1] = clamp(a, -128.0f, 128.0f);
            v[2] = clamp(b, -128.0f, 128.0f);

            return v;
        }
    }

    private static float clamp(float x, float min, float max) {
        return x < min ? min : x > max ? max : x;
    }

    private static final class OkLab extends ColorSpace {

        private OkLab(@NonNull String name, @IntRange(from = MIN_ID, to = MAX_ID) int id) {
            super(name, Model.LAB, id);
        }

        @Override
        public boolean isWideGamut() {
            return true;
        }

        @Override
        public float getMinValue(@IntRange(from = 0, to = 3) int component) {
            return component == 0 ? 0.0f : -0.5f;
        }

        @Override
        public float getMaxValue(@IntRange(from = 0, to = 3) int component) {
            return component == 0 ? 1.0f : 0.5f;
        }

        @Override
        public float[] toXyz(@NonNull @Size(min = 3) float[] v) {
            v[0] = clamp(v[0], 0.0f, 1.0f);
            v[1] = clamp(v[1], -0.5f, 0.5f);
            v[2] = clamp(v[2], -0.5f, 0.5f);

            mul3x3Float3(INVERSE_M2, v);
            v[0] = v[0] * v[0] * v[0];
            v[1] = v[1] * v[1] * v[1];
            v[2] = v[2] * v[2] * v[2];

            mul3x3Float3(INVERSE_M1, v);

            return v;
        }

        @Override
        public float[] fromXyz(@NonNull @Size(min = 3) float[] v) {
            mul3x3Float3(M1, v);

            v[0] = (float) Math.cbrt(v[0]);
            v[1] = (float) Math.cbrt(v[1]);
            v[2] = (float) Math.cbrt(v[2]);

            mul3x3Float3(M2, v);
            return v;
        }

        private static final float[] M1TMP = {
                0.8189330101f, 0.0329845436f, 0.0482003018f,
                0.3618667424f, 0.9293118715f, 0.2643662691f,
                -0.1288597137f, 0.0361456387f, 0.6338517070f
        };

        private static final float[] M1 = mul3x3(
                M1TMP,
                chromaticAdaptation(Adaptation.BRADFORD, ILLUMINANT_D50, ILLUMINANT_D65)
        );

        private static final float[] M2 = {
                0.2104542553f, 1.9779984951f, 0.0259040371f,
                0.7936177850f, -2.4285922050f, 0.7827717662f,
                -0.0040720468f, 0.4505937099f, -0.8086757660f
        };

        private static final float[] INVERSE_M1 = inverse3x3(M1);

        private static final float[] INVERSE_M2 = inverse3x3(M2);
    }

    public static class Rgb extends ColorSpace {
        public static class TransferParameters {

            private static final double TYPE_PQish = -2.0;
            private static final double TYPE_HLGish = -3.0;

            /** Variable \(a\) in the equation of the EOTF described above. */
            public final double a;
            /** Variable \(b\) in the equation of the EOTF described above. */
            public final double b;
            /** Variable \(c\) in the equation of the EOTF described above. */
            public final double c;
            /** Variable \(d\) in the equation of the EOTF described above. */
            public final double d;
            /** Variable \(e\) in the equation of the EOTF described above. */
            public final double e;
            /** Variable \(f\) in the equation of the EOTF described above. */
            public final double f;
            /** Variable \(g\) in the equation of the EOTF described above. */
            public final double g;

            private static boolean isSpecialG(double g) {
                return g == TYPE_PQish || g == TYPE_HLGish;
            }

            public TransferParameters(double a, double b, double c, double d, double g) {
                this(a, b, c, d, 0.0, 0.0, g);
            }

            public TransferParameters(double a, double b, double c, double d, double e,
                    double f, double g) {
                if (Double.isNaN(a) || Double.isNaN(b) || Double.isNaN(c)
                        || Double.isNaN(d) || Double.isNaN(e) || Double.isNaN(f)
                        || Double.isNaN(g)) {
                    throw new IllegalArgumentException("Parameters cannot be NaN");
                }
                if (!isSpecialG(g)) {
                    // Next representable float after 1.0
                    // We use doubles here but the representation inside our native code
                    // is often floats
                    if (!(d >= 0.0 && d <= 1.0f + Math.ulp(1.0f))) {
                        throw new IllegalArgumentException(
                                "Parameter d must be in the range [0..1], " + "was " + d);
                    }

                    if (d == 0.0 && (a == 0.0 || g == 0.0)) {
                        throw new IllegalArgumentException(
                                "Parameter a or g is zero, the transfer function is constant");
                    }

                    if (d >= 1.0 && c == 0.0) {
                        throw new IllegalArgumentException(
                                "Parameter c is zero, the transfer function is constant");
                    }

                    if ((a == 0.0 || g == 0.0) && c == 0.0) {
                        throw new IllegalArgumentException("Parameter a or g is zero,"
                                + " and c is zero, the transfer function is constant");
                    }

                    if (c < 0.0) {
                        throw new IllegalArgumentException(
                                "The transfer function must be increasing");
                    }

                    if (a < 0.0 || g < 0.0) {
                        throw new IllegalArgumentException(
                                "The transfer function must be positive or increasing");
                    }
                }
                this.a = a;
                this.b = b;
                this.c = c;
                this.d = d;
                this.e = e;
                this.f = f;
                this.g = g;
            }

            @SuppressWarnings("SimplifiableIfStatement")
            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;

                TransferParameters that = (TransferParameters) o;

                if (Double.compare(that.a, a) != 0) return false;
                if (Double.compare(that.b, b) != 0) return false;
                if (Double.compare(that.c, c) != 0) return false;
                if (Double.compare(that.d, d) != 0) return false;
                if (Double.compare(that.e, e) != 0) return false;
                if (Double.compare(that.f, f) != 0) return false;
                return Double.compare(that.g, g) == 0;
            }

            @Override
            public int hashCode() {
                int result;
                long temp;
                temp = Double.doubleToLongBits(a);
                result = (int) (temp ^ (temp >>> 32));
                temp = Double.doubleToLongBits(b);
                result = 31 * result + (int) (temp ^ (temp >>> 32));
                temp = Double.doubleToLongBits(c);
                result = 31 * result + (int) (temp ^ (temp >>> 32));
                temp = Double.doubleToLongBits(d);
                result = 31 * result + (int) (temp ^ (temp >>> 32));
                temp = Double.doubleToLongBits(e);
                result = 31 * result + (int) (temp ^ (temp >>> 32));
                temp = Double.doubleToLongBits(f);
                result = 31 * result + (int) (temp ^ (temp >>> 32));
                temp = Double.doubleToLongBits(g);
                result = 31 * result + (int) (temp ^ (temp >>> 32));
                return result;
            }

            private boolean isHLGish() {
                return g == TYPE_HLGish;
            }

            private boolean isPQish() {
                return g == TYPE_PQish;
            }
        }

        @NonNull private final float[] mWhitePoint;
        @NonNull private final float[] mPrimaries;
        @NonNull private final float[] mTransform;
        @NonNull private final float[] mInverseTransform;

        @NonNull private final DoubleUnaryOperator mOetf;
        @NonNull private final DoubleUnaryOperator mEotf;
        @NonNull private final DoubleUnaryOperator mClampedOetf;
        @NonNull private final DoubleUnaryOperator mClampedEotf;

        private final float mMin;
        private final float mMax;

        private final boolean mIsWideGamut;
        private final boolean mIsSrgb;

        @Nullable private final TransferParameters mTransferParameters;


        private static DoubleUnaryOperator generateOETF(TransferParameters function) {
            if (function.isHLGish()) {
                return x -> transferHLGOETF(function, x);
            } else if (function.isPQish()) {
                return x -> transferST2048OETF(function, x);
            } else {
                return function.e == 0.0 && function.f == 0.0
                    ? x -> rcpResponse(x, function.a, function.b,
                    function.c, function.d, function.g)
                    : x -> rcpResponse(x, function.a, function.b, function.c,
                        function.d, function.e, function.f, function.g);
            }
        }

        private static DoubleUnaryOperator generateEOTF(TransferParameters function) {
            if (function.isHLGish()) {
                return x -> transferHLGEOTF(function, x);
            } else if (function.isPQish()) {
                return x -> transferST2048OETF(function, x);
            } else {
                return function.e == 0.0 && function.f == 0.0
                    ? x -> response(x, function.a, function.b,
                    function.c, function.d, function.g)
                    : x -> response(x, function.a, function.b, function.c,
                        function.d, function.e, function.f, function.g);
            }
        }

        public Rgb(
                @NonNull @Size(min = 1) String name,
                @NonNull @Size(9) float[] toXYZ,
                @NonNull DoubleUnaryOperator oetf,
                @NonNull DoubleUnaryOperator eotf) {
            this(name, computePrimaries(toXYZ), computeWhitePoint(toXYZ), null,
                    oetf, eotf, 0.0f, 1.0f, null, MIN_ID);
        }

        public Rgb(
                @NonNull @Size(min = 1) String name,
                @NonNull @Size(min = 6, max = 9) float[] primaries,
                @NonNull @Size(min = 2, max = 3) float[] whitePoint,
                @NonNull DoubleUnaryOperator oetf,
                @NonNull DoubleUnaryOperator eotf,
                float min,
                float max) {
            this(name, primaries, whitePoint, null, oetf, eotf, min, max, null, MIN_ID);
        }

        public Rgb(
                @NonNull @Size(min = 1) String name,
                @NonNull @Size(9) float[] toXYZ,
                @NonNull TransferParameters function) {
            // Note: when isGray() returns false, this passes null for the transform for
            // consistency with other constructors, which compute the transform from the primaries
            // and white point.
            this(name, isGray(toXYZ) ? GRAY_PRIMARIES : computePrimaries(toXYZ),
                    computeWhitePoint(toXYZ), isGray(toXYZ) ? toXYZ : null, function, MIN_ID);
        }

        public Rgb(
                @NonNull @Size(min = 1) String name,
                @NonNull @Size(min = 6, max = 9) float[] primaries,
                @NonNull @Size(min = 2, max = 3) float[] whitePoint,
                @NonNull TransferParameters function) {
            this(name, primaries, whitePoint, null, function, MIN_ID);
        }

        private Rgb(
                @NonNull @Size(min = 1) String name,
                @NonNull @Size(min = 6, max = 9) float[] primaries,
                @NonNull @Size(min = 2, max = 3) float[] whitePoint,
                @Nullable @Size(9) float[] transform,
                @NonNull TransferParameters function,
                @IntRange(from = MIN_ID, to = MAX_ID) int id) {
            this(name, primaries, whitePoint, transform,
                    generateOETF(function),
                    generateEOTF(function),
                    0.0f, 1.0f, function, id);
        }

        public Rgb(
                @NonNull @Size(min = 1) String name,
                @NonNull @Size(9) float[] toXYZ,
                double gamma) {
            this(name, computePrimaries(toXYZ), computeWhitePoint(toXYZ), gamma, 0.0f, 1.0f, MIN_ID);
        }

        public Rgb(
                @NonNull @Size(min = 1) String name,
                @NonNull @Size(min = 6, max = 9) float[] primaries,
                @NonNull @Size(min = 2, max = 3) float[] whitePoint,
                double gamma) {
            this(name, primaries, whitePoint, gamma, 0.0f, 1.0f, MIN_ID);
        }

        private Rgb(
                @NonNull @Size(min = 1) String name,
                @NonNull @Size(min = 6, max = 9) float[] primaries,
                @NonNull @Size(min = 2, max = 3) float[] whitePoint,
                double gamma,
                float min,
                float max,
                @IntRange(from = MIN_ID, to = MAX_ID) int id) {
            this(name, primaries, whitePoint, null,
                    gamma == 1.0 ? DoubleUnaryOperator.identity() :
                            x -> Math.pow(x < 0.0 ? 0.0 : x, 1 / gamma),
                    gamma == 1.0 ? DoubleUnaryOperator.identity() :
                            x -> Math.pow(x < 0.0 ? 0.0 : x, gamma),
                    min, max, new TransferParameters(1.0, 0.0, 0.0, 0.0, gamma), id);
        }

        private Rgb(
                @NonNull @Size(min = 1) String name,
                @NonNull @Size(min = 6, max = 9) float[] primaries,
                @NonNull @Size(min = 2, max = 3) float[] whitePoint,
                @Nullable @Size(9) float[] transform,
                @NonNull DoubleUnaryOperator oetf,
                @NonNull DoubleUnaryOperator eotf,
                float min,
                float max,
                @Nullable TransferParameters transferParameters,
                @IntRange(from = MIN_ID, to = MAX_ID) int id) {

            super(name, Model.RGB, id);

            if (primaries == null || (primaries.length != 6 && primaries.length != 9)) {
                throw new IllegalArgumentException("The color space's primaries must be " +
                        "defined as an array of 6 floats in xyY or 9 floats in XYZ");
            }

            if (whitePoint == null || (whitePoint.length != 2 && whitePoint.length != 3)) {
                throw new IllegalArgumentException("The color space's white point must be " +
                        "defined as an array of 2 floats in xyY or 3 float in XYZ");
            }

            if (oetf == null || eotf == null) {
                throw new IllegalArgumentException("The transfer functions of a color space " +
                        "cannot be null");
            }

            if (min >= max) {
                throw new IllegalArgumentException("Invalid range: min=" + min + ", max=" + max +
                        "; min must be strictly < max");
            }

            mWhitePoint = xyWhitePoint(whitePoint);
            mPrimaries =  xyPrimaries(primaries);

            if (transform == null) {
                mTransform = computeXYZMatrix(mPrimaries, mWhitePoint);
            } else {
                if (transform.length != 9) {
                    throw new IllegalArgumentException("Transform must have 9 entries! Has "
                            + transform.length);
                }
                mTransform = transform;
            }
            mInverseTransform = inverse3x3(mTransform);

            mOetf = oetf;
            mEotf = eotf;

            mMin = min;
            mMax = max;

            DoubleUnaryOperator clamp = this::clamp;
            mClampedOetf = oetf.andThen(clamp);
            mClampedEotf = clamp.andThen(eotf);

            mTransferParameters = transferParameters;

            // A color space is wide-gamut if its area is >90% of NTSC 1953 and
            // if it entirely contains the Color space definition in xyY
            mIsWideGamut = isWideGamut(mPrimaries, min, max);
            mIsSrgb = isSrgb(mPrimaries, mWhitePoint, oetf, eotf, min, max, id);
        }

        private Rgb(Rgb colorSpace,
                @NonNull @Size(9) float[] transform,
                @NonNull @Size(min = 2, max = 3) float[] whitePoint) {
            this(colorSpace.getName(), colorSpace.mPrimaries, whitePoint, transform,
                    colorSpace.mOetf, colorSpace.mEotf, colorSpace.mMin, colorSpace.mMax,
                    colorSpace.mTransferParameters, MIN_ID);
        }

        @NonNull
        @Size(min = 2)
        public float[] getWhitePoint(@NonNull @Size(min = 2) float[] whitePoint) {
            whitePoint[0] = mWhitePoint[0];
            whitePoint[1] = mWhitePoint[1];
            return whitePoint;
        }

        @NonNull
        @Size(2)
        public float[] getWhitePoint() {
            return Arrays.copyOf(mWhitePoint, mWhitePoint.length);
        }

        @NonNull
        @Size(min = 6)
        public float[] getPrimaries(@NonNull @Size(min = 6) float[] primaries) {
            System.arraycopy(mPrimaries, 0, primaries, 0, mPrimaries.length);
            return primaries;
        }

        @NonNull
        @Size(6)
        public float[] getPrimaries() {
            return Arrays.copyOf(mPrimaries, mPrimaries.length);
        }

        @NonNull
        @Size(min = 9)
        public float[] getTransform(@NonNull @Size(min = 9) float[] transform) {
            System.arraycopy(mTransform, 0, transform, 0, mTransform.length);
            return transform;
        }

        @NonNull
        @Size(9)
        public float[] getTransform() {
            return Arrays.copyOf(mTransform, mTransform.length);
        }

        @NonNull
        @Size(min = 9)
        public float[] getInverseTransform(@NonNull @Size(min = 9) float[] inverseTransform) {
            System.arraycopy(mInverseTransform, 0, inverseTransform, 0, mInverseTransform.length);
            return inverseTransform;
        }

        @NonNull
        @Size(9)
        public float[] getInverseTransform() {
            return Arrays.copyOf(mInverseTransform, mInverseTransform.length);
        }

        @NonNull
        public DoubleUnaryOperator getOetf() {
            return mClampedOetf;
        }

        @NonNull
        public DoubleUnaryOperator getEotf() {
            return mClampedEotf;
        }

        @Nullable
        public TransferParameters getTransferParameters() {
            if (mTransferParameters != null
                    && !mTransferParameters.equals(BT2020_PQ_TRANSFER_PARAMETERS)
                    && !mTransferParameters.equals(BT2020_HLG_TRANSFER_PARAMETERS)) {
                return mTransferParameters;
            }
            return null;
        }

        @Override
        public boolean isSrgb() {
            return mIsSrgb;
        }

        @Override
        public boolean isWideGamut() {
            return mIsWideGamut;
        }

        @Override
        public float getMinValue(int component) {
            return mMin;
        }

        @Override
        public float getMaxValue(int component) {
            return mMax;
        }

        @NonNull
        @Size(3)
        public float[] toLinear(float r, float g, float b) {
            return toLinear(new float[] { r, g, b });
        }

        @NonNull
        @Size(min = 3)
        public float[] toLinear(@NonNull @Size(min = 3) float[] v) {
            v[0] = (float) mClampedEotf.applyAsDouble(v[0]);
            v[1] = (float) mClampedEotf.applyAsDouble(v[1]);
            v[2] = (float) mClampedEotf.applyAsDouble(v[2]);
            return v;
        }

        @NonNull
        @Size(3)
        public float[] fromLinear(float r, float g, float b) {
            return fromLinear(new float[] { r, g, b });
        }

        @NonNull
        @Size(min = 3)
        public float[] fromLinear(@NonNull @Size(min = 3) float[] v) {
            v[0] = (float) mClampedOetf.applyAsDouble(v[0]);
            v[1] = (float) mClampedOetf.applyAsDouble(v[1]);
            v[2] = (float) mClampedOetf.applyAsDouble(v[2]);
            return v;
        }

        @Override
        @NonNull
        @Size(min = 3)
        public float[] toXyz(@NonNull @Size(min = 3) float[] v) {
            v[0] = (float) mClampedEotf.applyAsDouble(v[0]);
            v[1] = (float) mClampedEotf.applyAsDouble(v[1]);
            v[2] = (float) mClampedEotf.applyAsDouble(v[2]);
            return mul3x3Float3(mTransform, v);
        }

        @Override
        @NonNull
        @Size(min = 3)
        public float[] fromXyz(@NonNull @Size(min = 3) float[] v) {
            mul3x3Float3(mInverseTransform, v);
            v[0] = (float) mClampedOetf.applyAsDouble(v[0]);
            v[1] = (float) mClampedOetf.applyAsDouble(v[1]);
            v[2] = (float) mClampedOetf.applyAsDouble(v[2]);
            return v;
        }

        private double clamp(double x) {
            return x < mMin ? mMin : x > mMax ? mMax : x;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;

            Rgb rgb = (Rgb) o;

            if (Float.compare(rgb.mMin, mMin) != 0) return false;
            if (Float.compare(rgb.mMax, mMax) != 0) return false;
            if (!Arrays.equals(mWhitePoint, rgb.mWhitePoint)) return false;
            if (!Arrays.equals(mPrimaries, rgb.mPrimaries)) return false;
            if (mTransferParameters != null) {
                return mTransferParameters.equals(rgb.mTransferParameters);
            } else if (rgb.mTransferParameters == null) {
                return true;
            }
            //noinspection SimplifiableIfStatement
            if (!mOetf.equals(rgb.mOetf)) return false;
            return mEotf.equals(rgb.mEotf);
        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + Arrays.hashCode(mWhitePoint);
            result = 31 * result + Arrays.hashCode(mPrimaries);
            result = 31 * result + (mMin != +0.0f ? Float.floatToIntBits(mMin) : 0);
            result = 31 * result + (mMax != +0.0f ? Float.floatToIntBits(mMax) : 0);
            result = 31 * result +
                    (mTransferParameters != null ? mTransferParameters.hashCode() : 0);
            if (mTransferParameters == null) {
                result = 31 * result + mOetf.hashCode();
                result = 31 * result + mEotf.hashCode();
            }
            return result;
        }

        @SuppressWarnings("RedundantIfStatement")
        private static boolean isSrgb(
                @NonNull @Size(6) float[] primaries,
                @NonNull @Size(2) float[] whitePoint,
                @NonNull DoubleUnaryOperator OETF,
                @NonNull DoubleUnaryOperator EOTF,
                float min,
                float max,
                @IntRange(from = MIN_ID, to = MAX_ID) int id) {
            if (id == 0) return true;
            if (!ColorSpace.compare(primaries, SRGB_PRIMARIES)) {
                return false;
            }
            if (!ColorSpace.compare(whitePoint, ILLUMINANT_D65)) {
                return false;
            }

            if (min != 0.0f) return false;
            if (max != 1.0f) return false;

            // We would have already returned true if this was SRGB itself, so
            // it is safe to reference it here.
            ColorSpace.Rgb srgb = (ColorSpace.Rgb) get(Named.SRGB);

            for (double x = 0.0; x <= 1.0; x += 1 / 255.0) {
                if (!compare(x, OETF, srgb.mOetf)) return false;
                if (!compare(x, EOTF, srgb.mEotf)) return false;
            }

            return true;
        }

        private static boolean isGray(@NonNull @Size(9) float[] toXYZ) {
            return toXYZ.length == 9 && toXYZ[1] == 0 && toXYZ[2] == 0 && toXYZ[3] == 0
                    && toXYZ[5] == 0 && toXYZ[6] == 0 && toXYZ[7] == 0;
        }

        private static boolean compare(double point, @NonNull DoubleUnaryOperator a,
                @NonNull DoubleUnaryOperator b) {
            double rA = a.applyAsDouble(point);
            double rB = b.applyAsDouble(point);
            return Math.abs(rA - rB) <= 1e-3;
        }

        private static boolean isWideGamut(@NonNull @Size(6) float[] primaries,
                float min, float max) {
            return (area(primaries) / area(NTSC_1953_PRIMARIES) > 0.9f &&
                            contains(primaries, SRGB_PRIMARIES)) || (min < 0.0f && max > 1.0f);
        }

        private static float area(@NonNull @Size(6) float[] primaries) {
            float Rx = primaries[0];
            float Ry = primaries[1];
            float Gx = primaries[2];
            float Gy = primaries[3];
            float Bx = primaries[4];
            float By = primaries[5];
            float det = Rx * Gy + Ry * Bx + Gx * By - Gy * Bx - Ry * Gx - Rx * By;
            float r = 0.5f * det;
            return r < 0.0f ? -r : r;
        }

        private static float cross(float ax, float ay, float bx, float by) {
            return ax * by - ay * bx;
        }

        @SuppressWarnings("RedundantIfStatement")
        private static boolean contains(@NonNull @Size(6) float[] p1, @NonNull @Size(6) float[] p2) {
            // Translate the vertices p1 in the coordinates system
            // with the vertices p2 as the origin
            float[] p0 = new float[] {
                    p1[0] - p2[0], p1[1] - p2[1],
                    p1[2] - p2[2], p1[3] - p2[3],
                    p1[4] - p2[4], p1[5] - p2[5],
            };
            // Check the first vertex of p1
            if (cross(p0[0], p0[1], p2[0] - p2[4], p2[1] - p2[5]) < 0 ||
                    cross(p2[0] - p2[2], p2[1] - p2[3], p0[0], p0[1]) < 0) {
                return false;
            }
            // Check the second vertex of p1
            if (cross(p0[2], p0[3], p2[2] - p2[0], p2[3] - p2[1]) < 0 ||
                    cross(p2[2] - p2[4], p2[3] - p2[5], p0[2], p0[3]) < 0) {
                return false;
            }
            // Check the third vertex of p1
            if (cross(p0[4], p0[5], p2[4] - p2[2], p2[5] - p2[3]) < 0 ||
                    cross(p2[4] - p2[0], p2[5] - p2[1], p0[4], p0[5]) < 0) {
                return false;
            }
            return true;
        }

        @NonNull
        @Size(6)
        private static float[] computePrimaries(@NonNull @Size(9) float[] toXYZ) {
            float[] r = mul3x3Float3(toXYZ, new float[] { 1.0f, 0.0f, 0.0f });
            float[] g = mul3x3Float3(toXYZ, new float[] { 0.0f, 1.0f, 0.0f });
            float[] b = mul3x3Float3(toXYZ, new float[] { 0.0f, 0.0f, 1.0f });

            float rSum = r[0] + r[1] + r[2];
            float gSum = g[0] + g[1] + g[2];
            float bSum = b[0] + b[1] + b[2];

            return new float[] {
                    r[0] / rSum, r[1] / rSum,
                    g[0] / gSum, g[1] / gSum,
                    b[0] / bSum, b[1] / bSum,
            };
        }

        @NonNull
        @Size(2)
        private static float[] computeWhitePoint(@NonNull @Size(9) float[] toXYZ) {
            float[] w = mul3x3Float3(toXYZ, new float[] { 1.0f, 1.0f, 1.0f });
            float sum = w[0] + w[1] + w[2];
            return new float[] { w[0] / sum, w[1] / sum };
        }

        @NonNull
        @Size(6)
        private static float[] xyPrimaries(@NonNull @Size(min = 6, max = 9) float[] primaries) {
            float[] xyPrimaries = new float[6];

            // XYZ to xyY
            if (primaries.length == 9) {
                float sum;

                sum = primaries[0] + primaries[1] + primaries[2];
                xyPrimaries[0] = primaries[0] / sum;
                xyPrimaries[1] = primaries[1] / sum;

                sum = primaries[3] + primaries[4] + primaries[5];
                xyPrimaries[2] = primaries[3] / sum;
                xyPrimaries[3] = primaries[4] / sum;

                sum = primaries[6] + primaries[7] + primaries[8];
                xyPrimaries[4] = primaries[6] / sum;
                xyPrimaries[5] = primaries[7] / sum;
            } else {
                System.arraycopy(primaries, 0, xyPrimaries, 0, 6);
            }

            return xyPrimaries;
        }

        @NonNull
        @Size(2)
        private static float[] xyWhitePoint(@Size(min = 2, max = 3) float[] whitePoint) {
            float[] xyWhitePoint = new float[2];

            // XYZ to xyY
            if (whitePoint.length == 3) {
                float sum = whitePoint[0] + whitePoint[1] + whitePoint[2];
                xyWhitePoint[0] = whitePoint[0] / sum;
                xyWhitePoint[1] = whitePoint[1] / sum;
            } else {
                System.arraycopy(whitePoint, 0, xyWhitePoint, 0, 2);
            }

            return xyWhitePoint;
        }

        @NonNull
        @Size(9)
        private static float[] computeXYZMatrix(
                @NonNull @Size(6) float[] primaries,
                @NonNull @Size(2) float[] whitePoint) {
            float Rx = primaries[0];
            float Ry = primaries[1];
            float Gx = primaries[2];
            float Gy = primaries[3];
            float Bx = primaries[4];
            float By = primaries[5];
            float Wx = whitePoint[0];
            float Wy = whitePoint[1];

            float oneRxRy = (1 - Rx) / Ry;
            float oneGxGy = (1 - Gx) / Gy;
            float oneBxBy = (1 - Bx) / By;
            float oneWxWy = (1 - Wx) / Wy;

            float RxRy = Rx / Ry;
            float GxGy = Gx / Gy;
            float BxBy = Bx / By;
            float WxWy = Wx / Wy;

            float BY =
                    ((oneWxWy - oneRxRy) * (GxGy - RxRy) - (WxWy - RxRy) * (oneGxGy - oneRxRy)) /
                    ((oneBxBy - oneRxRy) * (GxGy - RxRy) - (BxBy - RxRy) * (oneGxGy - oneRxRy));
            float GY = (WxWy - RxRy - BY * (BxBy - RxRy)) / (GxGy - RxRy);
            float RY = 1 - GY - BY;

            float RYRy = RY / Ry;
            float GYGy = GY / Gy;
            float BYBy = BY / By;

            return new float[] {
                    RYRy * Rx, RY, RYRy * (1 - Rx - Ry),
                    GYGy * Gx, GY, GYGy * (1 - Gx - Gy),
                    BYBy * Bx, BY, BYBy * (1 - Bx - By)
            };
        }
    }

    public static class Connector {
        @NonNull private final ColorSpace mSource;
        @NonNull private final ColorSpace mDestination;
        @NonNull private final ColorSpace mTransformSource;
        @NonNull private final ColorSpace mTransformDestination;
        @NonNull private final RenderIntent mIntent;
        @NonNull @Size(3) private final float[] mTransform;

        Connector(@NonNull ColorSpace source, @NonNull ColorSpace destination,
                @NonNull RenderIntent intent) {
            this(source, destination,
                    source.getModel() == Model.RGB ? adapt(source, ILLUMINANT_D50_XYZ) : source,
                    destination.getModel() == Model.RGB ?
                            adapt(destination, ILLUMINANT_D50_XYZ) : destination,
                    intent, computeTransform(source, destination, intent));
        }

        private Connector(
                @NonNull ColorSpace source, @NonNull ColorSpace destination,
                @NonNull ColorSpace transformSource, @NonNull ColorSpace transformDestination,
                @NonNull RenderIntent intent, @Nullable @Size(3) float[] transform) {
            mSource = source;
            mDestination = destination;
            mTransformSource = transformSource;
            mTransformDestination = transformDestination;
            mIntent = intent;
            mTransform = transform;
        }

        @Nullable
        private static float[] computeTransform(@NonNull ColorSpace source,
                @NonNull ColorSpace destination, @NonNull RenderIntent intent) {
            if (intent != RenderIntent.ABSOLUTE) return null;

            boolean srcRGB = source.getModel() == Model.RGB;
            boolean dstRGB = destination.getModel() == Model.RGB;

            if (srcRGB && dstRGB) return null;

            if (srcRGB || dstRGB) {
                ColorSpace.Rgb rgb = (ColorSpace.Rgb) (srcRGB ? source : destination);
                float[] srcXYZ = srcRGB ? xyYToXyz(rgb.mWhitePoint) : ILLUMINANT_D50_XYZ;
                float[] dstXYZ = dstRGB ? xyYToXyz(rgb.mWhitePoint) : ILLUMINANT_D50_XYZ;
                return new float[] {
                        srcXYZ[0] / dstXYZ[0],
                        srcXYZ[1] / dstXYZ[1],
                        srcXYZ[2] / dstXYZ[2],
                };
            }

            return null;
        }

        @NonNull
        public ColorSpace getSource() {
            return mSource;
        }

        @NonNull
        public ColorSpace getDestination() {
            return mDestination;
        }

        public RenderIntent getRenderIntent() {
            return mIntent;
        }

        @NonNull
        @Size(3)
        public float[] transform(float r, float g, float b) {
            return transform(new float[] { r, g, b });
        }

        @NonNull
        @Size(min = 3)
        public float[] transform(@NonNull @Size(min = 3) float[] v) {
            float[] xyz = mTransformSource.toXyz(v);
            if (mTransform != null) {
                xyz[0] *= mTransform[0];
                xyz[1] *= mTransform[1];
                xyz[2] *= mTransform[2];
            }
            return mTransformDestination.fromXyz(xyz);
        }

        private static class Rgb extends Connector {
            @NonNull private final ColorSpace.Rgb mSource;
            @NonNull private final ColorSpace.Rgb mDestination;
            @NonNull private final float[] mTransform;

            Rgb(@NonNull ColorSpace.Rgb source, @NonNull ColorSpace.Rgb destination,
                    @NonNull RenderIntent intent) {
                super(source, destination, source, destination, intent, null);
                mSource = source;
                mDestination = destination;
                mTransform = computeTransform(source, destination, intent);
            }

            @Override
            public float[] transform(@NonNull @Size(min = 3) float[] rgb) {
                rgb[0] = (float) mSource.mClampedEotf.applyAsDouble(rgb[0]);
                rgb[1] = (float) mSource.mClampedEotf.applyAsDouble(rgb[1]);
                rgb[2] = (float) mSource.mClampedEotf.applyAsDouble(rgb[2]);
                mul3x3Float3(mTransform, rgb);
                rgb[0] = (float) mDestination.mClampedOetf.applyAsDouble(rgb[0]);
                rgb[1] = (float) mDestination.mClampedOetf.applyAsDouble(rgb[1]);
                rgb[2] = (float) mDestination.mClampedOetf.applyAsDouble(rgb[2]);
                return rgb;
            }

            @NonNull
            @Size(9)
            private static float[] computeTransform(
                    @NonNull ColorSpace.Rgb source,
                    @NonNull ColorSpace.Rgb destination,
                    @NonNull RenderIntent intent) {
                if (compare(source.mWhitePoint, destination.mWhitePoint)) {
                    // RGB->RGB using the PCS of both color spaces since they have the same
                    return mul3x3(destination.mInverseTransform, source.mTransform);
                } else {
                    // RGB->RGB using CIE XYZ D50 as the PCS
                    float[] transform = source.mTransform;
                    float[] inverseTransform = destination.mInverseTransform;

                    float[] srcXYZ = xyYToXyz(source.mWhitePoint);
                    float[] dstXYZ = xyYToXyz(destination.mWhitePoint);

                    if (!compare(source.mWhitePoint, ILLUMINANT_D50)) {
                        float[] srcAdaptation = chromaticAdaptation(
                                Adaptation.BRADFORD.mTransform, srcXYZ,
                                Arrays.copyOf(ILLUMINANT_D50_XYZ, 3));
                        transform = mul3x3(srcAdaptation, source.mTransform);
                    }

                    if (!compare(destination.mWhitePoint, ILLUMINANT_D50)) {
                        float[] dstAdaptation = chromaticAdaptation(
                                Adaptation.BRADFORD.mTransform, dstXYZ,
                                Arrays.copyOf(ILLUMINANT_D50_XYZ, 3));
                        inverseTransform = inverse3x3(mul3x3(dstAdaptation, destination.mTransform));
                    }

                    if (intent == RenderIntent.ABSOLUTE) {
                        transform = mul3x3Diag(
                                new float[] {
                                        srcXYZ[0] / dstXYZ[0],
                                        srcXYZ[1] / dstXYZ[1],
                                        srcXYZ[2] / dstXYZ[2],
                                }, transform);
                    }

                    return mul3x3(inverseTransform, transform);
                }
            }
        }

        static Connector identity(ColorSpace source) {
            return new Connector(source, source, RenderIntent.RELATIVE) {
                @Override
                public float[] transform(@NonNull @Size(min = 3) float[] v) {
                    return v;
                }
            };
        }
    }
}

