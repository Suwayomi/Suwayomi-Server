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

This file has been modified after being copied from it's original source.
 */
package com.f2prateek.rx.preferences;

import android.content.SharedPreferences;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import rx.Observable;
import rx.functions.Action1;

/** A preference of type {@link T}. Instances can be created from {@link RxSharedPreferences}. */
public final class Preference<T> {
  /** Stores and retrieves instances of {@code T} in {@link SharedPreferences}. */
  public interface Adapter<T> {
    /** Retrieve the value for {@code key} from {@code preferences}. */
    T get(@NonNull String key, @NonNull SharedPreferences preferences);

    /**
     * Store non-null {@code value} for {@code key} in {@code editor}.
     * <p>
     * Note: Implementations <b>must not</b> call {@code commit()} or {@code apply()} on
     * {@code editor}.
     */
    void set(@NonNull String key, @NonNull T value, @NonNull SharedPreferences.Editor editor);
  }

  private final SharedPreferences preferences;
  private final String key;
  private final T defaultValue;
  private final Adapter<T> adapter;
  private final Observable<T> values;

  Preference(SharedPreferences preferences, final String key, T defaultValue, Adapter<T> adapter,
      Observable<String> keyChanges) {
    this.preferences = preferences;
    this.key = key;
    this.defaultValue = defaultValue;
    this.adapter = adapter;
    this.values = keyChanges
        .filter(key::equals)
        .startWith("<init>") // Dummy value to trigger initial load.
        .onBackpressureLatest()
        .map(ignored -> get());
  }

  /** The key for which this preference will store and retrieve values. */
  @NonNull
  public String key() {
    return key;
  }

  /** The value used if none is stored. May be {@code null}. */
  @Nullable
  public T defaultValue() {
    return defaultValue;
  }

  /**
   * Retrieve the current value for this preference. Returns {@link #defaultValue()} if no value is
   * set.
   */
  @Nullable
  public T get() {
    if (!preferences.contains(key)) {
      return defaultValue;
    }
    return adapter.get(key, preferences);
  }

  /**
   * Change this preference's stored value to {@code value}. A value of {@code null} will delete the
   * preference.
   */
  public void set(@Nullable T value) {
    SharedPreferences.Editor editor = preferences.edit();
    if (value == null) {
      editor.remove(key);
    } else {
      adapter.set(key, value, editor);
    }
    editor.apply();
  }

  /** Returns true if this preference has a stored value. */
  public boolean isSet() {
    return preferences.contains(key);
  }

  /** Delete the stored value for this preference, if any. */
  public void delete() {
    set(null);
  }

  /**
   * Observe changes to this preference. The current value or {@link #defaultValue()} will be
   * emitted on first subscribe.
   */
  @CheckResult @NonNull
  public Observable<T> asObservable() {
    return values;
  }

  /**
   * An action which stores a new value for this preference. Passing {@code null} will delete the
   * preference.
   */
  @CheckResult @NonNull
  public Action1<? super T> asAction() {
    return (Action1<T>) this::set;
  }
}
