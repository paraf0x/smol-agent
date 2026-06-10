package dev.smolagent;

public final class NameRewriter {

    private NameRewriter() {}

    public static String rewrite(String s) {
        if (s == null || s.isEmpty()) return s;
        // Order matters: capital first so "Agent" doesn't get mangled to "Smolent"
        // by the lowercase pass picking up the lowercased "gent" tail.
        return s.replace("Agent", "Smol").replace("agent", "smol");
    }
}
