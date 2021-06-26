package xyz.nulldev.androidcompat.replace;

import com.ibm.icu.text.DateFormatSymbols;
import com.ibm.icu.text.DisplayContext;
import com.ibm.icu.text.NumberFormat;
import com.ibm.icu.text.TimeZoneFormat;
import com.ibm.icu.util.Calendar;
import com.ibm.icu.util.TimeZone;
import com.ibm.icu.util.ULocale;

import java.text.*;
import java.util.Date;
import java.util.Locale;

/**
 * Overridden to switch to Android implementation
 */
public class SimpleDateFormat extends DateFormat {
    private com.ibm.icu.text.SimpleDateFormat delegate;

    public SimpleDateFormat() {
        delegate = new com.ibm.icu.text.SimpleDateFormat();
    }

    private SimpleDateFormat(com.ibm.icu.text.SimpleDateFormat delegate) {
        this.delegate = delegate;
    }

    public SimpleDateFormat(String pattern) {
        delegate = new com.ibm.icu.text.SimpleDateFormat(pattern);
    }

    public SimpleDateFormat(String pattern, Locale loc) {
        delegate = new com.ibm.icu.text.SimpleDateFormat(pattern, loc);
    }

    public SimpleDateFormat(String pattern, ULocale loc) {
        delegate = new com.ibm.icu.text.SimpleDateFormat(pattern, loc);
    }

    public SimpleDateFormat(String pattern, String override, ULocale loc) {
        delegate = new com.ibm.icu.text.SimpleDateFormat(pattern, override, loc);
    }

    public SimpleDateFormat(String pattern, DateFormatSymbols formatData) {
        delegate = new com.ibm.icu.text.SimpleDateFormat(pattern, formatData);
    }

    public SimpleDateFormat(String pattern, DateFormatSymbols formatData, ULocale loc) {
        delegate = new com.ibm.icu.text.SimpleDateFormat(pattern, formatData, loc);
    }

    @Deprecated
    public static SimpleDateFormat getInstance(Calendar.FormatConfiguration formatConfig) {
        return new SimpleDateFormat(com.ibm.icu.text.SimpleDateFormat.getInstance(formatConfig));
    }

    public void set2DigitYearStart(Date startDate) {
        delegate.set2DigitYearStart(startDate);
    }

    public Date get2DigitYearStart() {
        return delegate.get2DigitYearStart();
    }

    public void setContext(DisplayContext context) {
        delegate.setContext(context);
    }

    public StringBuffer format(Calendar cal, StringBuffer toAppendTo, FieldPosition pos) {
        return delegate.format(cal, toAppendTo, pos);
    }

    public void setNumberFormat(NumberFormat newNumberFormat) {
        delegate.setNumberFormat(newNumberFormat);
    }

    public void parse(String text, Calendar cal, ParsePosition parsePos) {
        delegate.parse(text, cal, parsePos);
    }

    public String toPattern() {
        return delegate.toPattern();
    }

    public String toLocalizedPattern() {
        return delegate.toLocalizedPattern();
    }

    public void applyPattern(String pat) {
        delegate.applyPattern(pat);
    }

    public void applyLocalizedPattern(String pat) {
        delegate.applyLocalizedPattern(pat);
    }

    public DateFormatSymbols getDateFormatSymbols() {
        return delegate.getDateFormatSymbols();
    }

    public void setDateFormatSymbols(DateFormatSymbols newFormatSymbols) {
        delegate.setDateFormatSymbols(newFormatSymbols);
    }

    public TimeZoneFormat getTimeZoneFormat() {
        return delegate.getTimeZoneFormat();
    }

    public void setTimeZoneFormat(TimeZoneFormat tzfmt) {
        delegate.setTimeZoneFormat(tzfmt);
    }

    @Override
    public Object clone() {
        return delegate.clone();
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return delegate.equals(obj);
    }

    @Override
    public AttributedCharacterIterator formatToCharacterIterator(Object obj) {
        return delegate.formatToCharacterIterator(obj);
    }

    @Deprecated
    public StringBuffer intervalFormatByAlgorithm(Calendar fromCalendar, Calendar toCalendar, StringBuffer appendTo, FieldPosition pos) throws IllegalArgumentException {
        return delegate.intervalFormatByAlgorithm(fromCalendar, toCalendar, appendTo, pos);
    }

    public void setNumberFormat(String fields, NumberFormat overrideNF) {
        delegate.setNumberFormat(fields, overrideNF);
    }

    public NumberFormat getNumberFormat(char field) {
        return delegate.getNumberFormat(field);
    }

    @Override
    public StringBuffer format(Date date, StringBuffer toAppendTo, FieldPosition fieldPosition) {
        return delegate.format(date, toAppendTo, fieldPosition);
    }

    @Override
    public Date parse(String text) throws ParseException {
        return delegate.parse(text);
    }

    @Override
    public Date parse(String text, ParsePosition pos) {
        return delegate.parse(text, pos);
    }

    @Override
    public Object parseObject(String source, ParsePosition pos) {
        return delegate.parseObject(source, pos);
    }

    public static com.ibm.icu.text.DateFormat getTimeInstance(int style, ULocale locale) {
        return com.ibm.icu.text.DateFormat.getTimeInstance(style, locale);
    }

    public static com.ibm.icu.text.DateFormat getDateInstance(int style, ULocale locale) {
        return com.ibm.icu.text.DateFormat.getDateInstance(style, locale);
    }

    public static com.ibm.icu.text.DateFormat getDateTimeInstance(int dateStyle, int timeStyle, ULocale locale) {
        return com.ibm.icu.text.DateFormat.getDateTimeInstance(dateStyle, timeStyle, locale);
    }

    public static Locale[] getAvailableLocales() {
        return com.ibm.icu.text.DateFormat.getAvailableLocales();
    }

    public static ULocale[] getAvailableULocales() {
        return com.ibm.icu.text.DateFormat.getAvailableULocales();
    }

    @Override
    public void setCalendar(java.util.Calendar newCalendar) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone(newCalendar.getTimeZone().getID()));
        cal.setTimeInMillis(newCalendar.getTimeInMillis());
        delegate.setCalendar(cal);
    }

    @Override
    public java.util.Calendar getCalendar() {
        return new CalendarDelegate(delegate.getCalendar());
    }

    @Override
    public java.text.NumberFormat getNumberFormat() {
        return new NumberFormatDelegate(delegate.getNumberFormat());
    }

    @Override
    public void setTimeZone(java.util.TimeZone zone) {
        delegate.setTimeZone(TimeZone.getTimeZone(zone.getID()));
    }

    @Override
    public java.util.TimeZone getTimeZone() {
        return new TimeZoneDelegate(delegate.getTimeZone());
    }

    @Override
    public void setLenient(boolean lenient) {
        delegate.setLenient(lenient);
    }

    @Override
    public boolean isLenient() {
        return delegate.isLenient();
    }

    public void setCalendarLenient(boolean lenient) {
        delegate.setCalendarLenient(lenient);
    }

    public boolean isCalendarLenient() {
        return delegate.isCalendarLenient();
    }

    public com.ibm.icu.text.DateFormat setBooleanAttribute(com.ibm.icu.text.DateFormat.BooleanAttribute key, boolean value) {
        return delegate.setBooleanAttribute(key, value);
    }

    public boolean getBooleanAttribute(com.ibm.icu.text.DateFormat.BooleanAttribute key) {
        return delegate.getBooleanAttribute(key);
    }

    public DisplayContext getContext(DisplayContext.Type type) {
        return delegate.getContext(type);
    }

    public static com.ibm.icu.text.DateFormat getDateInstance(Calendar cal, int dateStyle, Locale locale) {
        return com.ibm.icu.text.DateFormat.getDateInstance(cal, dateStyle, locale);
    }

    public static com.ibm.icu.text.DateFormat getDateInstance(Calendar cal, int dateStyle, ULocale locale) {
        return com.ibm.icu.text.DateFormat.getDateInstance(cal, dateStyle, locale);
    }

    public static com.ibm.icu.text.DateFormat getTimeInstance(Calendar cal, int timeStyle, Locale locale) {
        return com.ibm.icu.text.DateFormat.getTimeInstance(cal, timeStyle, locale);
    }

    public static com.ibm.icu.text.DateFormat getTimeInstance(Calendar cal, int timeStyle, ULocale locale) {
        return com.ibm.icu.text.DateFormat.getTimeInstance(cal, timeStyle, locale);
    }

    public static com.ibm.icu.text.DateFormat getDateTimeInstance(Calendar cal, int dateStyle, int timeStyle, Locale locale) {
        return com.ibm.icu.text.DateFormat.getDateTimeInstance(cal, dateStyle, timeStyle, locale);
    }

    public static com.ibm.icu.text.DateFormat getDateTimeInstance(Calendar cal, int dateStyle, int timeStyle, ULocale locale) {
        return com.ibm.icu.text.DateFormat.getDateTimeInstance(cal, dateStyle, timeStyle, locale);
    }

    public static com.ibm.icu.text.DateFormat getInstance(Calendar cal, Locale locale) {
        return com.ibm.icu.text.DateFormat.getInstance(cal, locale);
    }

    public static com.ibm.icu.text.DateFormat getInstance(Calendar cal, ULocale locale) {
        return com.ibm.icu.text.DateFormat.getInstance(cal, locale);
    }

    public static com.ibm.icu.text.DateFormat getInstance(Calendar cal) {
        return com.ibm.icu.text.DateFormat.getInstance(cal);
    }

    public static com.ibm.icu.text.DateFormat getDateInstance(Calendar cal, int dateStyle) {
        return com.ibm.icu.text.DateFormat.getDateInstance(cal, dateStyle);
    }

    public static com.ibm.icu.text.DateFormat getTimeInstance(Calendar cal, int timeStyle) {
        return com.ibm.icu.text.DateFormat.getTimeInstance(cal, timeStyle);
    }

    public static com.ibm.icu.text.DateFormat getDateTimeInstance(Calendar cal, int dateStyle, int timeStyle) {
        return com.ibm.icu.text.DateFormat.getDateTimeInstance(cal, dateStyle, timeStyle);
    }

    public static com.ibm.icu.text.DateFormat getInstanceForSkeleton(String skeleton) {
        return com.ibm.icu.text.DateFormat.getInstanceForSkeleton(skeleton);
    }

    public static com.ibm.icu.text.DateFormat getInstanceForSkeleton(String skeleton, Locale locale) {
        return com.ibm.icu.text.DateFormat.getInstanceForSkeleton(skeleton, locale);
    }

    public static com.ibm.icu.text.DateFormat getInstanceForSkeleton(String skeleton, ULocale locale) {
        return com.ibm.icu.text.DateFormat.getInstanceForSkeleton(skeleton, locale);
    }

    public static com.ibm.icu.text.DateFormat getInstanceForSkeleton(Calendar cal, String skeleton, Locale locale) {
        return com.ibm.icu.text.DateFormat.getInstanceForSkeleton(cal, skeleton, locale);
    }

    public static com.ibm.icu.text.DateFormat getInstanceForSkeleton(Calendar cal, String skeleton, ULocale locale) {
        return com.ibm.icu.text.DateFormat.getInstanceForSkeleton(cal, skeleton, locale);
    }

    public static com.ibm.icu.text.DateFormat getPatternInstance(String skeleton) {
        return com.ibm.icu.text.DateFormat.getPatternInstance(skeleton);
    }

    public static com.ibm.icu.text.DateFormat getPatternInstance(String skeleton, Locale locale) {
        return com.ibm.icu.text.DateFormat.getPatternInstance(skeleton, locale);
    }

    public static com.ibm.icu.text.DateFormat getPatternInstance(String skeleton, ULocale locale) {
        return com.ibm.icu.text.DateFormat.getPatternInstance(skeleton, locale);
    }

    public static com.ibm.icu.text.DateFormat getPatternInstance(Calendar cal, String skeleton, Locale locale) {
        return com.ibm.icu.text.DateFormat.getPatternInstance(cal, skeleton, locale);
    }

    public static com.ibm.icu.text.DateFormat getPatternInstance(Calendar cal, String skeleton, ULocale locale) {
        return com.ibm.icu.text.DateFormat.getPatternInstance(cal, skeleton, locale);
    }

    public ULocale getLocale(ULocale.Type type) {
        return delegate.getLocale(type);
    }

    @Override
    public Object parseObject(String source) throws ParseException {
        return delegate.parseObject(source);
    }
}