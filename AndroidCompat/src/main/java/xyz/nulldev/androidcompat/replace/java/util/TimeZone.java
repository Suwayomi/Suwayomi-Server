package xyz.nulldev.androidcompat.replace.java.util;

import com.ibm.icu.util.ULocale;

import java.util.Date;
import java.util.Locale;
import java.util.Set;

public class TimeZone extends java.util.TimeZone {
    private com.ibm.icu.util.TimeZone delegate;

    public TimeZone(com.ibm.icu.util.TimeZone delegate) {
        this.delegate = delegate;
    }

    @Override
    public int getOffset(int era, int year, int month, int day, int dayOfWeek, int milliseconds) {
        return delegate.getOffset(era, year, month, day, dayOfWeek, milliseconds);
    }

    @Override
    public int getOffset(long date) {
        return delegate.getOffset(date);
    }

    public void getOffset(long date, boolean local, int[] offsets) {
        delegate.getOffset(date, local, offsets);
    }

    @Override
    public void setRawOffset(int offsetMillis) {
        delegate.setRawOffset(offsetMillis);
    }

    @Override
    public int getRawOffset() {
        return delegate.getRawOffset();
    }

    @Override
    public String getID() {
        return delegate.getID();
    }

    @Override
    public void setID(String ID) {
        delegate.setID(ID);
    }

    public String getDisplayName(ULocale locale) {
        return delegate.getDisplayName(locale);
    }

    @Override
    public String getDisplayName(boolean daylight, int style, Locale locale) {
        return delegate.getDisplayName(daylight, style, locale);
    }

    public String getDisplayName(boolean daylight, int style, ULocale locale) {
        return delegate.getDisplayName(daylight, style, locale);
    }

    @Override
    public int getDSTSavings() {
        return delegate.getDSTSavings();
    }

    @Override
    public boolean useDaylightTime() {
        return delegate.useDaylightTime();
    }

    @Override
    public boolean observesDaylightTime() {
        return delegate.observesDaylightTime();
    }

    @Override
    public boolean inDaylightTime(Date date) {
        return delegate.inDaylightTime(date);
    }

    public static java.util.TimeZone getTimeZone(String ID) {
        return new TimeZone(com.ibm.icu.util.TimeZone.getTimeZone(ID));
    }

    public static com.ibm.icu.util.TimeZone getFrozenTimeZone(String ID) {
        return com.ibm.icu.util.TimeZone.getFrozenTimeZone(ID);
    }

    public static com.ibm.icu.util.TimeZone getTimeZone(String ID, int type) {
        return com.ibm.icu.util.TimeZone.getTimeZone(ID, type);
    }

    public static void setDefaultTimeZoneType(int type) {
        com.ibm.icu.util.TimeZone.setDefaultTimeZoneType(type);
    }

    public static int getDefaultTimeZoneType() {
        return com.ibm.icu.util.TimeZone.getDefaultTimeZoneType();
    }

    public static Set<String> getAvailableIDs(com.ibm.icu.util.TimeZone.SystemTimeZoneType zoneType, String region, Integer rawOffset) {
        return com.ibm.icu.util.TimeZone.getAvailableIDs(zoneType, region, rawOffset);
    }

    public static String[] getAvailableIDs(int rawOffset) {
        return com.ibm.icu.util.TimeZone.getAvailableIDs(rawOffset);
    }

    public static String[] getAvailableIDs(String country) {
        return com.ibm.icu.util.TimeZone.getAvailableIDs(country);
    }

    public static String[] getAvailableIDs() {
        return com.ibm.icu.util.TimeZone.getAvailableIDs();
    }

    public static int countEquivalentIDs(String id) {
        return com.ibm.icu.util.TimeZone.countEquivalentIDs(id);
    }

    public static String getEquivalentID(String id, int index) {
        return com.ibm.icu.util.TimeZone.getEquivalentID(id, index);
    }

    public static java.util.TimeZone getDefault() {
        return new TimeZone(com.ibm.icu.util.TimeZone.getDefault());
    }

    public static void setDefault(com.ibm.icu.util.TimeZone tz) {
        com.ibm.icu.util.TimeZone.setDefault(tz);
    }

    public boolean hasSameRules(com.ibm.icu.util.TimeZone other) {
        return delegate.hasSameRules(other);
    }

    @Override
    public Object clone() {
        return delegate.clone();
    }

    @Override
    public boolean equals(Object obj) {
        return delegate.equals(obj);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    public static String getTZDataVersion() {
        return com.ibm.icu.util.TimeZone.getTZDataVersion();
    }

    public static String getCanonicalID(String id) {
        return com.ibm.icu.util.TimeZone.getCanonicalID(id);
    }

    public static String getCanonicalID(String id, boolean[] isSystemID) {
        return com.ibm.icu.util.TimeZone.getCanonicalID(id, isSystemID);
    }

    public static String getRegion(String id) {
        return com.ibm.icu.util.TimeZone.getRegion(id);
    }

    public static String getWindowsID(String id) {
        return com.ibm.icu.util.TimeZone.getWindowsID(id);
    }

    public static String getIDForWindowsID(String winid, String region) {
        return com.ibm.icu.util.TimeZone.getIDForWindowsID(winid, region);
    }

    public boolean isFrozen() {
        return delegate.isFrozen();
    }

    public com.ibm.icu.util.TimeZone freeze() {
        return delegate.freeze();
    }

    public com.ibm.icu.util.TimeZone cloneAsThawed() {
        return delegate.cloneAsThawed();
    }
}