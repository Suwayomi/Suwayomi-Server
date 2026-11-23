package android.media;

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.os.Handler;
import android.view.Surface;

/**
 * There is no screen to read frames from, so this reader never produces an image. It exists so code that renders a
 * view offscreen (e.g. a WebView on a virtual display) can run.
 */
public class ImageReader implements AutoCloseable {
    public interface OnImageAvailableListener {
        void onImageAvailable(ImageReader reader);
    }

    private final int width;
    private final int height;
    private final int format;
    private final int maxImages;

    private ImageReader(int width, int height, int format, int maxImages) {
        this.width = width;
        this.height = height;
        this.format = format;
        this.maxImages = maxImages;
    }

    public static ImageReader newInstance(int width, int height, int format, int maxImages) {
        return new ImageReader(width, height, format, maxImages);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getImageFormat() {
        return format;
    }

    public int getMaxImages() {
        return maxImages;
    }

    public Surface getSurface() {
        return null;
    }

    public Image acquireLatestImage() {
        return null;
    }

    public Image acquireNextImage() {
        return null;
    }

    public void setOnImageAvailableListener(OnImageAvailableListener listener, Handler handler) {
    }

    @Override
    public void close() {
    }
}
