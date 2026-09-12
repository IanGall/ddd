package cn.iantech.context.core;

import java.util.regex.Pattern;

/** 上下文字段的长度和字符安全校验。 */
public final class ContextValidator {

    public static final int MAX_VALUE_LENGTH = 128;
    public static final int MAX_LOCALE_LENGTH = 32;
    private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9._:-]+");
    private static final Pattern SAFE_LOCALE = Pattern.compile("[A-Za-z0-9_-]+");

    private ContextValidator() {
    }

    public static String validOrNull(String value) {
        return validate(value, MAX_VALUE_LENGTH, SAFE_VALUE);
    }

    public static String validLocaleOrNull(String value) {
        return validate(value, MAX_LOCALE_LENGTH, SAFE_LOCALE);
    }

    private static String validate(String value, int maxLength, Pattern pattern) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength || !pattern.matcher(normalized).matches()) {
            return null;
        }
        return normalized;
    }
}
