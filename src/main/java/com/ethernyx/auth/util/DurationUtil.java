package com.ethernyx.auth.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses ban duration strings like "1h", "1d", "1w", "1m" (months), "1y", "permanent".
 */
public final class DurationUtil {

    private static final Pattern PATTERN = Pattern.compile("^(\\d+)(s|m|h|d|w|mo|y)$", Pattern.CASE_INSENSITIVE);

    private DurationUtil() {}

    /**
     * Returns the duration in milliseconds, or -1 if permanent, or 0 if invalid.
     */
    public static long parseToMillis(String input) {
        if (input == null) return 0;
        String trimmed = input.trim().toLowerCase();
        if (trimmed.equals("permanent") || trimmed.equals("perm") || trimmed.equals("forever")) {
            return -1;
        }

        Matcher matcher = PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            return 0;
        }

        long amount = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2);

        long unitMillis;
        switch (unit) {
            case "s": unitMillis = 1000L; break;
            case "m": unitMillis = 60_000L; break;
            case "h": unitMillis = 3_600_000L; break;
            case "d": unitMillis = 86_400_000L; break;
            case "w": unitMillis = 7L * 86_400_000L; break;
            case "mo": unitMillis = 30L * 86_400_000L; break;
            case "y": unitMillis = 365L * 86_400_000L; break;
            default: return 0;
        }

        return amount * unitMillis;
    }

    public static boolean isValid(String input) {
        if (input == null) return false;
        String trimmed = input.trim().toLowerCase();
        if (trimmed.equals("permanent") || trimmed.equals("perm") || trimmed.equals("forever")) {
            return true;
        }
        return PATTERN.matcher(trimmed).matches();
    }

    /**
     * Formats a millis duration into a human-readable string, e.g. "3 days, 2 hours".
     */
    public static String format(long millis) {
        if (millis < 0) return "Permanent";
        if (millis == 0) return "0 seconds";

        long seconds = millis / 1000;
        long days = seconds / 86400;
        seconds %= 86400;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append(days == 1 ? " day, " : " days, ");
        if (hours > 0) sb.append(hours).append(hours == 1 ? " hour, " : " hours, ");
        if (minutes > 0) sb.append(minutes).append(minutes == 1 ? " minute, " : " minutes, ");
        if (days == 0 && hours == 0) sb.append(seconds).append(seconds == 1 ? " second" : " seconds");

        String result = sb.toString();
        if (result.endsWith(", ")) {
            result = result.substring(0, result.length() - 2);
        }
        return result;
    }
}
