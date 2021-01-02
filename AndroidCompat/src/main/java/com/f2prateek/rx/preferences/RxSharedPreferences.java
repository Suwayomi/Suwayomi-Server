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

import android.annotation.TargetApi;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import java.util.Collections;
import java.util.Set;
import rx.Observable;
import rx.subscriptions.Subscriptions;

import static android.os.Build.VERSION_CODES.HONEYCOMB;
import static com.f2prateek.rx.preferences.Preconditions.checkNotNull;

/** A factory for reactive {@link Preference} objects. */
public final class RxSharedPreferences {
  private static final Float DEFAULT_FLOAT = 0f;
  private static final Integer DEFAULT_INTEGER = 0;
  private static final Boolean DEFAULT_BOOLEAN = Boolean.FALSE;
  private static final Long DEFAULT_LONG = 0L;

  /** Create an instance of {@link RxSharedPreferences} for {@code preferences}. */
  @CheckResult @NonNull
  public static RxSharedPreferences create(@NonNull SharedPreferences preferences) {
    checkNotNull(preferences, "preferences == null");
    return new RxSharedPreferences(preferences);
  }

  private final SharedPreferences preferences;
  private final Observable<String> keyChanges;

  private RxSharedPreferences(final SharedPreferences preferences) {
    this.preferences = preferences;
    this.keyChanges = Observable.create((Observable.OnSubscribe<String>) subscriber -> {
      final OnSharedPreferenceChangeListener listener = (preferences1, key) -> subscriber.onNext(key);

      preferences.registerOnSharedPreferenceChangeListener(listener);

      subscriber.add(Subscriptions.create(() -> preferences.unregisterOnSharedPreferenceChangeListener(listener)));
    }).share();
  }

  /** Create a boolean preference for {@code key}. Default is {@code false}. */
  @CheckResult @NonNull
  public Preference<Boolean> getBoolean(@NonNull String key) {
    return getBoolean(key, DEFAULT_BOOLEAN);
  }

  /** Create a boolean preference for {@code key} with a default of {@code defaultValue}. */
  @CheckResult @NonNull
  public Preference<Boolean> getBoolean(@NonNull String key, @Nullable Boolean defaultValue) {
    checkNotNull(key, "key == null");
    return new Preference<>(preferences, key, defaultValue, BooleanAdapter.INSTANCE, keyChanges);
  }

  /** Create an enum preference for {@code key}. Default is {@code null}. */
  @CheckResult @NonNull
  public <T extends Enum<T>> Preference<T> getEnum(@NonNull String key,
      @NonNull Class<T> enumClass) {
    return getEnum(key, null, enumClass);
  }

  /** Create an enum preference for {@code key} with a default of {@code defaultValue}. */
  @CheckResult @NonNull
  public <T extends Enum<T>> Preference<T> getEnum(@NonNull String key, @Nullable T defaultValue,
      @NonNull Class<T> enumClass) {
    checkNotNull(key, "key == null");
    checkNotNull(enumClass, "enumClass == null");
    Preference.Adapter<T> adapter = new EnumAdapter<>(enumClass);
    return new Preference<>(preferences, key, defaultValue, adapter, keyChanges);
  }

  /** Create a float preference for {@code key}. Default is {@code 0}. */
  @CheckResult @NonNull
  public Preference<Float> getFloat(@NonNull String key) {
    return getFloat(key, DEFAULT_FLOAT);
  }

  /** Create a float preference for {@code key} with a default of {@code defaultValue}. */
  @CheckResult @NonNull
  public Preference<Float> getFloat(@NonNull String key, @Nullable Float defaultValue) {
    checkNotNull(key, "key == null");
    return new Preference<>(preferences, key, defaultValue, FloatAdapter.INSTANCE, keyChanges);
  }

  /** Create an integer preference for {@code key}. Default is {@code 0}. */
  @CheckResult @NonNull
  public Preference<Integer> getInteger(@NonNull String key) {
    //noinspection UnnecessaryBoxing
    return getInteger(key, DEFAULT_INTEGER);
  }

  /** Create an integer preference for {@code key} with a default of {@code defaultValue}. */
  @CheckResult @NonNull
  public Preference<Integer> getInteger(@NonNull String key, @Nullable Integer defaultValue) {
    checkNotNull(key, "key == null");
    return new Preference<>(preferences, key, defaultValue, IntegerAdapter.INSTANCE, keyChanges);
  }

  /** Create a long preference for {@code key}. Default is {@code 0}. */
  @CheckResult @NonNull
  public Preference<Long> getLong(@NonNull String key) {
    //noinspection UnnecessaryBoxing
    return getLong(key, DEFAULT_LONG);
  }

  /** Create a long preference for {@code key} with a default of {@code defaultValue}. */
  @CheckResult @NonNull
  public Preference<Long> getLong(@NonNull String key, @Nullable Long defaultValue) {
    checkNotNull(key, "key == null");
    return new Preference<>(preferences, key, defaultValue, LongAdapter.INSTANCE, keyChanges);
  }

  /** Create a preference of type {@code T} for {@code key}. Default is {@code null}. */
  @CheckResult @NonNull
  public <T> Preference<T> getObject(@NonNull String key, @NonNull Preference.Adapter<T> adapter) {
    return getObject(key, null, adapter);
  }

  /**
   * Create a preference for type {@code T} for {@code key} with a default of {@code defaultValue}.
   */
  @CheckResult @NonNull
  public <T> Preference<T> getObject(@NonNull String key, @Nullable T defaultValue,
      @NonNull Preference.Adapter<T> adapter) {
    checkNotNull(key, "key == null");
    checkNotNull(adapter, "adapter == null");
    return new Preference<>(preferences, key, defaultValue, adapter, keyChanges);
  }

  /** Create a string preference for {@code key}. Default is {@code null}. */
  @CheckResult @NonNull
  public Preference<String> getString(@NonNull String key) {
    return getString(key, null);
  }

  /** Create a string preference for {@code key} with a default of {@code defaultValue}. */
  @CheckResult @NonNull
  public Preference<String> getString(@NonNull String key, @Nullable String defaultValue) {
    checkNotNull(key, "key == null");
    return new Preference<>(preferences, key, defaultValue, StringAdapter.INSTANCE, keyChanges);
  }

  /** Create a string set preference for {@code key}. Default is an empty set. */
  @TargetApi(HONEYCOMB)
  @CheckResult @NonNull
  public Preference<Set<String>> getStringSet(@NonNull String key) {
    return getStringSet(key, Collections.emptySet());
  }

  /** Create a string set preference for {@code key} with a default of {@code defaultValue}. */
  @TargetApi(HONEYCOMB)
  @CheckResult @NonNull
  public Preference<Set<String>> getStringSet(@NonNull String key,
      @NonNull Set<String> defaultValue) {
    checkNotNull(key, "key == null");
    return new Preference<>(preferences, key, defaultValue, StringSetAdapter.INSTANCE, keyChanges);
  }
}
