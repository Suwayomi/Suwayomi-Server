package com.f2prateek.rx.preferences;

import android.annotation.TargetApi;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import java.util.Set;

import static android.os.Build.VERSION_CODES.HONEYCOMB;

@TargetApi(HONEYCOMB)
final class StringSetAdapter implements Preference.Adapter<Set<String>> {
  static final StringSetAdapter INSTANCE = new StringSetAdapter();

  @Override public Set<String> get(@NonNull String key, @NonNull SharedPreferences preferences) {
    return preferences.getStringSet(key, null);
  }

  @Override public void set(@NonNull String key, @NonNull Set<String> value,
      @NonNull SharedPreferences.Editor editor) {
    editor.putStringSet(key, value);
  }
}
