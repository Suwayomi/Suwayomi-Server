package android.content.res;

import android.util.DisplayMetrics;
import android.view.DisplayAdjustments;

/**
 * The implementation of Resource access. This class contains the AssetManager and all caches
 * associated with it.
 *
 * {@link Resources} is just a thing wrapper around this class. When a configuration change
 * occurs, clients can retain the same {@link Resources} reference because the underlying
 * {@link ResourcesImpl} object will be updated or re-created.
 *
 * @hide
 */
public class ResourcesImpl {
    private final AssetManager mAssets;
    private final DisplayMetrics mMetrics;
    private final Configuration mConfiguration;
    private final DisplayAdjustments mAdjustments;

    public ResourcesImpl(AssetManager assets, DisplayMetrics metrics, Configuration config, DisplayAdjustments adjustments) {
        mAssets = assets;
        mMetrics = metrics;
        mConfiguration = config;
        mAdjustments = adjustments;
    }

    public AssetManager getAssets() {
        return mAssets;
    }

    public DisplayMetrics getDisplayMetrics() {
        return mMetrics;
    }

    public Configuration getConfiguration() {
        return mConfiguration;
    }
}
