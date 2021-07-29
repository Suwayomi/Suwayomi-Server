package xyz.nulldev.androidcompat.replace.java.util;

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import com.ibm.icu.text.DateFormat;
import com.ibm.icu.util.ULocale;

import java.util.Date;
import java.util.Locale;

public class Calendar extends java.util.Calendar {
    private com.ibm.icu.util.Calendar delegate;

    public Calendar(com.ibm.icu.util.Calendar delegate) {
        this.delegate = delegate;
    }

    public static java.util.Calendar getInstance() {
        return new Calendar(com.ibm.icu.util.Calendar.getInstance());
    }

    public static com.ibm.icu.util.Calendar getInstance(com.ibm.icu.util.TimeZone zone) {
        return com.ibm.icu.util.Calendar.getInstance(zone);
    }

    public static java.util.Calendar getInstance(Locale aLocale) {
        return new Calendar(com.ibm.icu.util.Calendar.getInstance(aLocale));
    }

    public static com.ibm.icu.util.Calendar getInstance(ULocale locale) {
        return com.ibm.icu.util.Calendar.getInstance(locale);
    }

    public static com.ibm.icu.util.Calendar getInstance(com.ibm.icu.util.TimeZone zone, Locale aLocale) {
        return com.ibm.icu.util.Calendar.getInstance(zone, aLocale);
    }

    public static com.ibm.icu.util.Calendar getInstance(com.ibm.icu.util.TimeZone zone, ULocale locale) {
        return com.ibm.icu.util.Calendar.getInstance(zone, locale);
    }

    public static Locale[] getAvailableLocales() {
        return com.ibm.icu.util.Calendar.getAvailableLocales();
    }

    @Override
    protected void computeTime() {}

    @Override
    protected void computeFields() {}

    public static ULocale[] getAvailableULocales() {
        return com.ibm.icu.util.Calendar.getAvailableULocales();
    }

    public static String[] getKeywordValuesForLocale(String key, ULocale locale, boolean commonlyUsed) {
        return com.ibm.icu.util.Calendar.getKeywordValuesForLocale(key, locale, commonlyUsed);
    }

    @Override
    public long getTimeInMillis() {
        return delegate.getTimeInMillis();
    }

    @Override
    public void setTimeInMillis(long millis) {
        delegate.setTimeInMillis(millis);
    }

    @Deprecated
    public int getRelatedYear() {
        return delegate.getRelatedYear();
    }

    @Deprecated
    public void setRelatedYear(int year) {
        delegate.setRelatedYear(year);
    }

    @Override
    public boolean equals(Object obj) {
        return delegate.equals(obj);
    }

    public boolean isEquivalentTo(com.ibm.icu.util.Calendar other) {
        return delegate.isEquivalentTo(other);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public boolean before(Object when) {
        return delegate.before(when);
    }

    @Override
    public boolean after(Object when) {
        return delegate.after(when);
    }

    @Override
    public int getActualMaximum(int field) {
        return delegate.getActualMaximum(field);
    }

    @Override
    public int getActualMinimum(int field) {
        return delegate.getActualMinimum(field);
    }

    @Override
    public void roll(int field, int amount) {
        delegate.roll(field, amount);
    }

    @Override
    public void add(int field, int amount) {
        delegate.add(field, amount);
    }

    @Override
    public void roll(int field, boolean up) {
        roll(field, up ? 1 : -1);
    }

    public String getDisplayName(Locale loc) {
        return delegate.getDisplayName(loc);
    }

    public String getDisplayName(ULocale loc) {
        return delegate.getDisplayName(loc);
    }

    public int compareTo(com.ibm.icu.util.Calendar that) {
        return delegate.compareTo(that);
    }

    public DateFormat getDateTimeFormat(int dateStyle, int timeStyle, Locale loc) {
        return delegate.getDateTimeFormat(dateStyle, timeStyle, loc);
    }

    public DateFormat getDateTimeFormat(int dateStyle, int timeStyle, ULocale loc) {
        return delegate.getDateTimeFormat(dateStyle, timeStyle, loc);
    }

    @Deprecated
    public static String getDateTimePattern(com.ibm.icu.util.Calendar cal, ULocale uLocale, int dateStyle) {
        return com.ibm.icu.util.Calendar.getDateTimePattern(cal, uLocale, dateStyle);
    }

    public int fieldDifference(Date when, int field) {
        return delegate.fieldDifference(when, field);
    }

    public void setTimeZone(com.ibm.icu.util.TimeZone value) {
        delegate.setTimeZone(value);
    }

    @Override
    public java.util.TimeZone getTimeZone() {
        return new TimeZone(delegate.getTimeZone());
    }

    @Override
    public void setLenient(boolean lenient) {
        delegate.setLenient(lenient);
    }

    @Override
    public boolean isLenient() {
        return delegate.isLenient();
    }

    public void setRepeatedWallTimeOption(int option) {
        delegate.setRepeatedWallTimeOption(option);
    }

    public int getRepeatedWallTimeOption() {
        return delegate.getRepeatedWallTimeOption();
    }

    public void setSkippedWallTimeOption(int option) {
        delegate.setSkippedWallTimeOption(option);
    }

    public int getSkippedWallTimeOption() {
        return delegate.getSkippedWallTimeOption();
    }

    @Override
    public void setFirstDayOfWeek(int value) {
        delegate.setFirstDayOfWeek(value);
    }

    @Override
    public int getFirstDayOfWeek() {
        return delegate.getFirstDayOfWeek();
    }

    @Override
    public void setMinimalDaysInFirstWeek(int value) {
        delegate.setMinimalDaysInFirstWeek(value);
    }

    @Override
    public int getMinimalDaysInFirstWeek() {
        return delegate.getMinimalDaysInFirstWeek();
    }

    @Override
    public int getMinimum(int field) {
        return delegate.getMinimum(field);
    }

    @Override
    public int getMaximum(int field) {
        return delegate.getMaximum(field);
    }

    @Override
    public int getGreatestMinimum(int field) {
        return delegate.getGreatestMinimum(field);
    }

    @Override
    public int getLeastMaximum(int field) {
        return delegate.getLeastMaximum(field);
    }

    @Deprecated
    public int getDayOfWeekType(int dayOfWeek) {
        return delegate.getDayOfWeekType(dayOfWeek);
    }

    @Deprecated
    public int getWeekendTransition(int dayOfWeek) {
        return delegate.getWeekendTransition(dayOfWeek);
    }

    public boolean isWeekend(Date date) {
        return delegate.isWeekend(date);
    }

    public boolean isWeekend() {
        return delegate.isWeekend();
    }

    @Override
    public Object clone() {
        return delegate.clone();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    public static com.ibm.icu.util.Calendar.WeekData getWeekDataForRegion(String region) {
        return com.ibm.icu.util.Calendar.getWeekDataForRegion(region);
    }

    public com.ibm.icu.util.Calendar.WeekData getWeekData() {
        return delegate.getWeekData();
    }

    public com.ibm.icu.util.Calendar setWeekData(com.ibm.icu.util.Calendar.WeekData wdata) {
        return delegate.setWeekData(wdata);
    }

    public int getFieldCount() {
        return delegate.getFieldCount();
    }

    public String getType() {
        return delegate.getType();
    }

    @Deprecated
    public boolean haveDefaultCentury() {
        return delegate.haveDefaultCentury();
    }

    public ULocale getLocale(ULocale.Type type) {
        return delegate.getLocale(type);
    }
}