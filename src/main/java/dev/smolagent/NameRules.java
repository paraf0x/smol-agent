package dev.smolagent;

import java.util.List;
import java.util.Locale;

/** Pure string logic for renames and translucency matching. No Minecraft classes, so it is unit-testable. */
public final class NameRules {

    public record Rename(String from, String to) {}

    private NameRules() {}

    /**
     * Replaces every occurrence of each rule's {@code from} (case-insensitive) in one left-to-right pass,
     * so a replacement is never matched again by another rule. At a position where several rules match,
     * the longest {@code from} wins. The replacement's case follows the matched text: an all-caps match
     * gives an all-caps replacement, otherwise the first letter follows the match's first letter and the
     * rest stays as typed.
     */
    public static String rewrite(String s, List<Rename> rules) {
        if (s == null || s.isEmpty() || rules.isEmpty()) return s;
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.length() != s.length()) return s; // exotic case-mapping changed indices; leave untouched
        StringBuilder out = null;
        int i = 0;
        int copiedUpTo = 0;
        while (i < s.length()) {
            Rename best = null;
            for (Rename r : rules) {
                String from = r.from();
                if (from == null || from.isEmpty()) continue;
                if (best != null && from.length() <= best.from().length()) continue;
                if (lower.startsWith(from.toLowerCase(Locale.ROOT), i)) best = r;
            }
            if (best == null) {
                i++;
                continue;
            }
            if (out == null) out = new StringBuilder(s.length() + 16);
            out.append(s, copiedUpTo, i);
            String matched = s.substring(i, i + best.from().length());
            out.append(adaptCase(matched, best.to() == null ? "" : best.to()));
            i += best.from().length();
            copiedUpTo = i;
        }
        if (out == null) return s;
        out.append(s, copiedUpTo, s.length());
        return out.toString();
    }

    static String adaptCase(String matched, String replacement) {
        if (replacement.isEmpty()) return replacement;
        if (isAllCaps(matched)) return replacement.toUpperCase(Locale.ROOT);
        char m = matched.charAt(0);
        char r = replacement.charAt(0);
        if (!Character.isLetter(m) || !Character.isLetter(r)) return replacement;
        char adapted = Character.isUpperCase(m) ? Character.toUpperCase(r) : Character.toLowerCase(r);
        return adapted + replacement.substring(1);
    }

    private static boolean isAllCaps(String s) {
        int letters = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetter(c)) {
                if (!Character.isUpperCase(c)) return false;
                letters++;
            }
        }
        return letters >= 2;
    }

    /** True if any non-empty part occurs (case-insensitive) somewhere in any of the names. */
    public static boolean matchesAny(List<String> names, List<String> parts) {
        for (String part : parts) {
            if (part == null || part.isBlank()) continue;
            String p = part.toLowerCase(Locale.ROOT);
            for (String name : names) {
                if (name != null && name.toLowerCase(Locale.ROOT).contains(p)) return true;
            }
        }
        return false;
    }
}
