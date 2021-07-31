package androidx.preference;

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.content.Context;

import java.util.Set;

public class MultiSelectListPreference extends DialogPreference {

    public MultiSelectListPreference(Context context) { super(context); }

    public void setEntries(CharSequence[] entries) { throw new RuntimeException("Stub!"); }


    public CharSequence[] getEntries() { throw new RuntimeException("Stub!"); }

    public void setEntryValues(CharSequence[] entryValues) { throw new RuntimeException("Stub!"); }

    public CharSequence[] getEntryValues() { throw new RuntimeException("Stub!"); }

    public void setValues(Set<String> values) { throw new RuntimeException("Stub!"); }

    public Set<String> getValues() { throw new RuntimeException("Stub!"); }

    public int findIndexOfValue(String value) { throw new RuntimeException("Stub!"); }
}