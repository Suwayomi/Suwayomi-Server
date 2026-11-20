package androidx.preference;

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.widget.EditText;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class EditTextPreference extends DialogPreference {
    // reference: https://android.googlesource.com/platform/frameworks/support/+/996971f962fcd554339a7cb2859cef9ca89dbcb7/preference/preference/src/main/java/androidx/preference/EditTextPreference.java

    private String text;

    @JsonIgnore
    private OnBindEditTextListener onBindEditTextListener;

    public EditTextPreference(Context context) {
        super(context);
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public OnBindEditTextListener getOnBindEditTextListener() {
        return onBindEditTextListener;
    }

    public void setOnBindEditTextListener(@Nullable OnBindEditTextListener onBindEditTextListener) {
        this.onBindEditTextListener = onBindEditTextListener;
    }

    public interface OnBindEditTextListener {
        void onBindEditText(@NonNull EditText editText);
    }

    /** Tachidesk specific API */
    @Override
    public String getDefaultValueType() {
        return "String";
    }
}
