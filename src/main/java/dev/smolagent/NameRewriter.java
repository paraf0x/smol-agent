package dev.smolagent;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;

public final class NameRewriter {

    private NameRewriter() {}

    public static String rewrite(String s) {
        if (s == null || s.isEmpty()) return s;
        // Order matters: capital first so "Agent" doesn't get mangled to "Smolent"
        // by the lowercase pass picking up the lowercased "gent" tail.
        return s.replace("Agent", "Smol").replace("agent", "smol");
    }

    /**
     * Returns a new Component with all literal string content rewritten via
     * {@link #rewrite(String)}. Non-literal content (translatable keys, scores,
     * keybinds, etc.) is preserved unchanged. Style and siblings (children)
     * are preserved, with each sibling rewritten recursively.
     */
    public static Component rewrite(Component in) {
        if (in == null) return null;

        ComponentContents contents = in.getContents();
        MutableComponent rewritten;
        if (contents instanceof PlainTextContents plain) {
            rewritten = Component.literal(rewrite(plain.text()));
        } else {
            // Translatable / score / selector / keybind — leave content as-is.
            rewritten = MutableComponent.create(contents);
        }

        rewritten.setStyle(in.getStyle());

        for (Component sibling : in.getSiblings()) {
            rewritten.append(rewrite(sibling));
        }

        return rewritten;
    }
}
