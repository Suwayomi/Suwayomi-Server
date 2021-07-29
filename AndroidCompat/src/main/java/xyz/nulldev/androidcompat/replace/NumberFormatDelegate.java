package xyz.nulldev.androidcompat.replace;

import com.ibm.icu.text.DisplayContext;
import com.ibm.icu.text.NumberFormat;
import com.ibm.icu.util.Currency;
import com.ibm.icu.util.CurrencyAmount;
import com.ibm.icu.util.ULocale;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.AttributedCharacterIterator;
import java.text.FieldPosition;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.Locale;

public class NumberFormatDelegate extends java.text.NumberFormat {
    private com.ibm.icu.text.NumberFormat delegate;

    public NumberFormatDelegate(com.ibm.icu.text.NumberFormat delegate) {
        this.delegate = delegate;
    }

    public StringBuffer format(Object number, StringBuffer toAppendTo, FieldPosition pos) {
        return delegate.format(number, toAppendTo, pos);
    }

    public String format(BigInteger number) {
        return delegate.format(number);
    }

    public String format(BigDecimal number) {
        return delegate.format(number);
    }

    public String format(com.ibm.icu.math.BigDecimal number) {
        return delegate.format(number);
    }

    public String format(CurrencyAmount currAmt) {
        return delegate.format(currAmt);
    }

    public StringBuffer format(double number, StringBuffer toAppendTo, FieldPosition pos) {
        return delegate.format(number, toAppendTo, pos);
    }

    public StringBuffer format(long number, StringBuffer toAppendTo, FieldPosition pos) {
        return delegate.format(number, toAppendTo, pos);
    }

    public StringBuffer format(BigInteger number, StringBuffer toAppendTo, FieldPosition pos) {
        return delegate.format(number, toAppendTo, pos);
    }

    public StringBuffer format(BigDecimal number, StringBuffer toAppendTo, FieldPosition pos) {
        return delegate.format(number, toAppendTo, pos);
    }

    public StringBuffer format(com.ibm.icu.math.BigDecimal number, StringBuffer toAppendTo, FieldPosition pos) {
        return delegate.format(number, toAppendTo, pos);
    }

    public StringBuffer format(CurrencyAmount currAmt, StringBuffer toAppendTo, FieldPosition pos) {
        return delegate.format(currAmt, toAppendTo, pos);
    }

    public Number parse(String text, ParsePosition parsePosition) {
        return delegate.parse(text, parsePosition);
    }

    public Number parse(String text) throws ParseException {
        return delegate.parse(text);
    }

    public CurrencyAmount parseCurrency(CharSequence text, ParsePosition pos) {
        return delegate.parseCurrency(text, pos);
    }

    public boolean isParseIntegerOnly() {
        return delegate.isParseIntegerOnly();
    }

    public void setParseIntegerOnly(boolean value) {
        delegate.setParseIntegerOnly(value);
    }

    public void setParseStrict(boolean value) {
        delegate.setParseStrict(value);
    }

    public boolean isParseStrict() {
        return delegate.isParseStrict();
    }

    public void setContext(DisplayContext context) {
        delegate.setContext(context);
    }

    public DisplayContext getContext(DisplayContext.Type type) {
        return delegate.getContext(type);
    }

    public static java.text.NumberFormat getInstance(Locale inLocale) {
        return new NumberFormatDelegate(NumberFormat.getInstance(inLocale));
    }

    public static NumberFormat getInstance(ULocale inLocale) {
        return NumberFormat.getInstance(inLocale);
    }

    public static NumberFormat getInstance(int style) {
        return NumberFormat.getInstance(style);
    }

    public static NumberFormat getInstance(Locale inLocale, int style) {
        return NumberFormat.getInstance(inLocale, style);
    }

    public static NumberFormat getNumberInstance(ULocale inLocale) {
        return NumberFormat.getNumberInstance(inLocale);
    }

    public static NumberFormat getIntegerInstance(ULocale inLocale) {
        return NumberFormat.getIntegerInstance(inLocale);
    }

    public static NumberFormat getCurrencyInstance(ULocale inLocale) {
        return NumberFormat.getCurrencyInstance(inLocale);
    }

    public static NumberFormat getPercentInstance(ULocale inLocale) {
        return NumberFormat.getPercentInstance(inLocale);
    }

    public static NumberFormat getScientificInstance(ULocale inLocale) {
        return NumberFormat.getScientificInstance(inLocale);
    }

    public static Locale[] getAvailableLocales() {
        return NumberFormat.getAvailableLocales();
    }

    public static ULocale[] getAvailableULocales() {
        return NumberFormat.getAvailableULocales();
    }

    public static Object registerFactory(NumberFormat.NumberFormatFactory factory) {
        return NumberFormat.registerFactory(factory);
    }

    public static boolean unregister(Object registryKey) {
        return NumberFormat.unregister(registryKey);
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
    public Object clone() {
        return delegate.clone();
    }

    public boolean isGroupingUsed() {
        return delegate.isGroupingUsed();
    }

    public void setGroupingUsed(boolean newValue) {
        delegate.setGroupingUsed(newValue);
    }

    public int getMaximumIntegerDigits() {
        return delegate.getMaximumIntegerDigits();
    }

    public void setMaximumIntegerDigits(int newValue) {
        delegate.setMaximumIntegerDigits(newValue);
    }

    public int getMinimumIntegerDigits() {
        return delegate.getMinimumIntegerDigits();
    }

    public void setMinimumIntegerDigits(int newValue) {
        delegate.setMinimumIntegerDigits(newValue);
    }

    public int getMaximumFractionDigits() {
        return delegate.getMaximumFractionDigits();
    }

    public void setMaximumFractionDigits(int newValue) {
        delegate.setMaximumFractionDigits(newValue);
    }

    public int getMinimumFractionDigits() {
        return delegate.getMinimumFractionDigits();
    }

    public void setMinimumFractionDigits(int newValue) {
        delegate.setMinimumFractionDigits(newValue);
    }

    public void setCurrency(Currency theCurrency) {
        delegate.setCurrency(theCurrency);
    }

    public java.util.Currency getCurrency() {
        return java.util.Currency.getInstance(delegate.getCurrency().getCurrencyCode());
    }

    public void setRoundingMode(int roundingMode) {
        delegate.setRoundingMode(roundingMode);
    }

    public static NumberFormat getInstance(ULocale desiredLocale, int choice) {
        return NumberFormat.getInstance(desiredLocale, choice);
    }

    @Deprecated
    public static String getPatternForStyle(ULocale forLocale, int choice) {
        return NumberFormat.getPatternForStyle(forLocale, choice);
    }

    public ULocale getLocale(ULocale.Type type) {
        return delegate.getLocale(type);
    }

    public AttributedCharacterIterator formatToCharacterIterator(Object obj) {
        return delegate.formatToCharacterIterator(obj);
    }

    public Object parseObject(String source) throws ParseException {
        return delegate.parseObject(source);
    }
}