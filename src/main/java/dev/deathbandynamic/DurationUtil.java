package dev.deathbandynamic;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationUtil {
    public static final long PERMANENT = -1;
    private static final Pattern PART = Pattern.compile("(\\d+)\\s*([smhdw])");

    private DurationUtil() {}

    /** s, m, h, d, w (combinable: "1d12h"). "perm"/"permanent" = -1, "0"/"none" = 0 (no ban). Throws on garbage. */
    public static long parse(String s) {
        String v = s == null ? "" : s.trim().toLowerCase();
        if (v.equals("perm") || v.equals("permanent")) return PERMANENT;
        if (v.equals("0") || v.equals("none")) return 0;
        Matcher m = PART.matcher(v);
        long total = 0;
        boolean any = false;
        while (m.find()) {
            any = true;
            long n = Long.parseLong(m.group(1));
            total += n * switch (m.group(2).charAt(0)) {
                case 's' -> 1_000L;
                case 'm' -> 60_000L;
                case 'h' -> 3_600_000L;
                case 'd' -> 86_400_000L;
                default -> 604_800_000L;
            };
        }
        if (!any) throw new IllegalArgumentException("Bad duration: " + s);
        return total;
    }

    public static String format(long ms) {
        if (ms < 0 || ms >= Long.MAX_VALUE / 2) return "permanent";
        long s = (ms + 999) / 1000;
        if (s <= 0) return "0s";
        long d = s / 86400, h = s % 86400 / 3600, m = s % 3600 / 60, sec = s % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (sec > 0 && d == 0) sb.append(sec).append("s ");
        return sb.toString().trim();
    }
}
