/*
Copyright 2014 Prateek Srivastava

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

This file may have been modified after being copied from it's original source.
 */
package com.f2prateek.rx.preferences;

import android.content.SharedPreferences;
import android.support.annotation.NonNull;

final class BooleanAdapter implements Preference.Adapter<Boolean> {
  static final BooleanAdapter INSTANCE = new BooleanAdapter();

  @Override public Boolean get(@NonNull String key, @NonNull SharedPreferences preferences) {
    return preferences.getBoolean(key, false);
  }

  @Override public void set(@NonNull String key, @NonNull Boolean value,
      @NonNull SharedPreferences.Editor editor) {
    editor.putBoolean(key, value);
  }
}
