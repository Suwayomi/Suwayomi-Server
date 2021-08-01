package androidx.preference;

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.content.Context;

import java.util.LinkedList;
import java.util.List;

public class PreferenceScreen extends Preference {
    /** Tachidesk specific API */
    private List<Preference> preferences = new LinkedList<>();

    public PreferenceScreen(Context context) {
        super(context);
    }

    public boolean addPreference(Preference preference) {
        preferences.add(preference);

        return true;
    }

    /** Tachidesk specific API */
    public List<Preference> getPreferences(){
        return preferences;
    }
}
