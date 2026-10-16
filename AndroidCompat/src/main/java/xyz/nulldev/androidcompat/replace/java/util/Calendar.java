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

/**
 * java.util.Calendar replacement backed by an ICU calendar.
 *
 * All field state lives in the ICU {@code delegate}; the base class' fields[]
 * array is intentionally bypassed (the previous implementation left
 * computeTime()/computeFields() empty which produced "get(YEAR) == 0" style
 * bugs). The two compute* hooks still mirror state into the base fields[] for
 * any residual base-class code path.
 */
public class Calendar extends java.util.Calendar {
    private com.ibm.icu.util.Calendar delegate;

    public Calendar(com.ibm.icu.util.Calendar delegate) {
        this.delegate = delegate;
    }

    /** Returns the underlying ICU calendar. */
    public com.ibm.icu.util.Calendar getDelegate() {
        return delegate;
    }

    private static com.ibm.icu.util.Calendar unwrap(java.util.Calendar cal) {
        return cal instanceof Calendar ? ((Calendar) cal).getDelegate() : null;
    }

    public static java.util.Calendar getInstance() {
        return new Calendar(com.ibm.icu.util.Calendar.getInstance());
    }

    public static java.util.Calendar getInstance(java.util.TimeZone zone) {
        return new Calendar(com.ibm.icu.util.Calendar.getInstance(toIcuTimeZone(zone)));
    }

    public static java.util.Calendar getInstance(com.ibm.icu.util.TimeZone zone) {
        return new Calendar(com.ibm.icu.util.Calendar.getInstance(zone));
    }

    public static java.util.Calendar getInstance(Locale aLocale) {
        return new Calendar(com.ibm.icu.util.Calendar.getInstance(aLocale));
    }

    public static java.util.Calendar getInstance(ULocale locale) {
        return new Calendar(com.ibm.icu.util.Calendar.getInstance(locale));
    }

    public static java.util.Calendar getInstance(java.util.TimeZone zone, Locale aLocale) {
        return new Calendar(com.ibm.icu.util.Calendar.getInstance(toIcuTimeZone(zone), aLocale));
    }

    public static java.util.Calendar getInstance(com.ibm.icu.util.TimeZone zone, Locale aLocale) {
        return new Calendar(com.ibm.icu.util.Calendar.getInstance(zone, aLocale));
    }

    public static java.util.Calendar getInstance(java.util.TimeZone zone, ULocale locale) {
        return new Calendar(com.ibm.icu.util.Calendar.getInstance(toIcuTimeZone(zone), locale));
    }

    public static java.util.Calendar getInstance(com.ibm.icu.util.TimeZone zone, ULocale locale) {
        return new Calendar(com.ibm.icu.util.Calendar.getInstance(zone, locale));
    }

    private static com.ibm.icu.util.TimeZone toIcuTimeZone(java.util.TimeZone zone) {
        if (zone instanceof TimeZone) {
            return ((TimeZone) zone).getDelegate();
        }
        return com.ibm.icu.util.TimeZone.getTimeZone(zone.getID());
    }

    public static Locale[] getAvailableLocales() {
        return com.ibm.icu.util.Calendar.getAvailableLocales();
    }

    /**
     * Mirrors the delegate's computed field values into the base class
     * fields[] so residual base-class reads observe real values. All public
     * read paths go straight to the delegate.
     */
    @Override
    protected void computeFields() {
        for (int i = 0; i < FIELD_COUNT; i++) {
            fields[i] = delegate.get(i);
            isSet[i] = true;
        }
        areFieldsSet = true;
        // areAllFieldsSet is package-private in java.util.Calendar; the base
        // class computes it lazily from isSet[]/fields[] anyway.
        isTimeSet = true;
    }

    /**
     * Pushes explicitly-set base fields into the delegate and adopts the
     * delegate's computed time. All public write paths go straight to the
     * delegate, so this only matters for residual base-class write paths.
     */
    @Override
    protected void computeTime() {
        for (int i = 0; i < FIELD_COUNT; i++) {
            if (isSet[i]) {
                delegate.set(i, fields[i]);
            }
        }
        time = delegate.getTimeInMillis();
        isTimeSet = true;
    }

    @Override
    public int get(int field) {
        return delegate.get(field);
    }

    @Override
    public void set(int field, int value) {
        delegate.set(field, value);
    }

    // NOTE: java.util.Calendar#clear(), #clear(int) and #isSet(int) are final
    // in the JDK and cannot be overridden. The delegate keeps its own state,
    // so all public accessor paths implemented here stay consistent.

    @Override
    public boolean isLenient() {
        return delegate.isLenient();
    }

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
        if (this == obj) {
            return true;
        }
        if (obj instanceof Calendar) {
            return delegate.equals(((Calendar) obj).delegate);
        }
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
        return when instanceof java.util.Calendar
                && getTimeInMillis() < ((java.util.Calendar) when).getTimeInMillis();
    }

    @Override
    public boolean after(Object when) {
        return when instanceof java.util.Calendar
                && getTimeInMillis() > ((java.util.Calendar) when).getTimeInMillis();
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

    /**
     * ICU calendars only expose a calendar-type display name, so field
     * names are resolved through {@link com.ibm.icu.text.DateFormatSymbols}
     * mirroring the JDK contract (short names for SHORT_/NARROW_ styles).
     */
    @Override
    public String getDisplayName(int field, int style, Locale locale) {
        com.ibm.icu.text.DateFormatSymbols symbols =
                com.ibm.icu.text.DateFormatSymbols.getInstance(ULocale.forLocale(locale));
        boolean shortStyle =
                style == SHORT_FORMAT
                        || style == SHORT_STANDALONE
                        || style == NARROW_FORMAT
                        || style == NARROW_STANDALONE;
        String[] names;
        int index;
        switch (field) {
            case MONTH:
                names = shortStyle ? symbols.getShortMonths() : symbols.getMonths();
                index = delegate.get(MONTH);
                break;
            case DAY_OF_WEEK:
                names = shortStyle ? symbols.getShortWeekdays() : symbols.getWeekdays();
                index = delegate.get(DAY_OF_WEEK);
                break;
            case AM_PM:
                names = symbols.getAmPmStrings();
                index = delegate.get(AM_PM);
                break;
            case ERA:
                names = shortStyle ? symbols.getEras() : symbols.getEraNames();
                index = delegate.get(ERA);
                break;
            default:
                return null;
        }
        return index >= 0 && index < names.length ? names[index] : null;
    }

    public String getDisplayName(ULocale loc) {
        return delegate.getDisplayName(loc);
    }

    public int compareTo(com.ibm.icu.util.Calendar that) {
        return delegate.compareTo(that);
    }

    @Override
    public int compareTo(java.util.Calendar anotherCalendar) {
        com.ibm.icu.util.Calendar unwrapped = unwrap(anotherCalendar);
        if (unwrapped != null) {
            return delegate.compareTo(unwrapped);
        }
        return Long.compare(getTimeInMillis(), anotherCalendar.getTimeInMillis());
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
    public void setTimeZone(java.util.TimeZone value) {
        delegate.setTimeZone(toIcuTimeZone(value));
    }

    @Override
    public java.util.TimeZone getTimeZone() {
        return new TimeZone(delegate.getTimeZone());
    }

    @Override
    public void setLenient(boolean lenient) {
        delegate.setLenient(lenient);
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
        return new Calendar((com.ibm.icu.util.Calendar) delegate.clone());
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
