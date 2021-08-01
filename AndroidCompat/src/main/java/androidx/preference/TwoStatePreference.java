package androidx.preference;

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.content.Context;

public class TwoStatePreference extends Preference {

    public TwoStatePreference(Context context) { super(context); }

    public boolean isChecked() { throw new RuntimeException("Stub!"); }

    public void setChecked(boolean checked) { throw new RuntimeException("Stub!"); }

    public CharSequence getSummaryOn() { throw new RuntimeException("Stub!"); }

    public void setSummaryOn(CharSequence summary) { throw new RuntimeException("Stub!"); }

    public CharSequence getSummaryOff() { throw new RuntimeException("Stub!"); }

    public void setSummaryOff(CharSequence summary) { throw new RuntimeException("Stub!"); }

    public boolean getDisableDependentsState() { throw new RuntimeException("Stub!"); }

    public void setDisableDependentsState(boolean disableDependentsState) { throw new RuntimeException("Stub!"); }

    /** Tachidesk specific API */
    @Override
    public String getDefaultValueType() {
        return "Boolean";
    }
}