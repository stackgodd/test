package com.ahs;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parsing and formatting helpers for durations and money. */
public final class Fmt {
    private Fmt() {}

    private static final Pattern DURATION =
            Pattern.compile("^(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+(?:\\.\\d+)?)s?)?$");

    /** "30s", "2m", "1m30s", "1h", "45" (seconds). Returns -1 if invalid. */
    public static long parseDuration(String raw) {
        if (raw == null) return -1;
        String s = raw.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        if (s.isEmpty()) return -1;
        Matcher m = DURATION.matcher(s);
        if (!m.matches()) return -1;
        double ms = 0;
        if (m.group(1) != null) ms += Long.parseLong(m.group(1)) * 3_600_000.0;
        if (m.group(2) != null) ms += Long.parseLong(m.group(2)) * 60_000.0;
        if (m.group(3) != null) ms += Double.parseDouble(m.group(3)) * 1000.0;
        if (ms <= 0) return -1;
        return (long) Math.min(ms, 6 * 3_600_000.0);
    }

    /** "1m30s" style, used in the settings GUI and chat feedback. */
    public static String compactDuration(long ms) {
        long total = ms / 1000;
        long h = total / 3600, m = (total / 60) % 60, s = total % 60;
        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(h).append('h');
        if (m > 0) sb.append(m).append('m');
        if (s > 0 || sb.length() == 0) {
            if (ms % 1000 != 0 && h == 0 && m == 0) sb.append(String.format(Locale.ROOT, "%.1f", ms / 1000.0));
            else sb.append(s);
            sb.append('s');
        }
        return sb.toString();
    }

    /** Countdown text: 1:05, 42, or 7.3 when tenths are on and under 10s. */
    public static String clock(long ms, boolean tenths) {
        if (ms < 10_000 && tenths) return String.format(Locale.ROOT, "%.1f", Math.max(0, ms) / 1000.0);
        long total = (Math.max(0, ms) + 999) / 1000;
        if (total >= 3600) return String.format(Locale.ROOT, "%d:%02d:%02d", total / 3600, (total / 60) % 60, total % 60);
        if (total >= 60) return String.format(Locale.ROOT, "%d:%02d", total / 60, total % 60);
        return String.valueOf(total);
    }

    /** "1.25m", "$5,000", "750k" -> number. NaN if invalid. */
    public static double parseAmount(String raw) {
        if (raw == null) return Double.NaN;
        String s = raw.trim().toLowerCase(Locale.ROOT).replace(",", "").replace("$", "").replace(" ", "");
        if (s.isEmpty()) return Double.NaN;
        double mult = 1;
        char last = s.charAt(s.length() - 1);
        switch (last) {
            case 'k' -> mult = 1e3;
            case 'm' -> mult = 1e6;
            case 'b' -> mult = 1e9;
            case 't' -> mult = 1e12;
            default -> {}
        }
        if (mult != 1) s = s.substring(0, s.length() - 1);
        try {
            return Double.parseDouble(s) * mult;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** 1250000 -> "1.25M" */
    public static String money(double v) {
        String suf = "";
        double d = v;
        if (v >= 1e12) { d = v / 1e12; suf = "T"; }
        else if (v >= 1e9) { d = v / 1e9; suf = "B"; }
        else if (v >= 1e6) { d = v / 1e6; suf = "M"; }
        else if (v >= 1e3) { d = v / 1e3; suf = "K"; }
        String s = String.format(Locale.ROOT, "%.2f", d);
        if (s.contains(".")) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s + suf;
    }
}
