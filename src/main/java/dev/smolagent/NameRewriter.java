package dev.smolagent;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.List;

public final class NameRewriter {

    private NameRewriter() {}

    public static String rewrite(String s) {
        return NameRules.rewrite(s, SmolConfig.get().renameRules());
    }

    /**
     * Returns a Component with all literal text rewritten by the configured rename rules.
     * Translatable content keeps its key, its arguments are rewritten. Other content (scores,
     * keybinds) is kept. Style and siblings are preserved, siblings rewritten recursively.
     * Returns the input itself when there are no rules.
     */
    public static Component rewrite(Component in) {
        if (in == null) return null;
        List<NameRules.Rename> rules = SmolConfig.get().renameRules();
        if (rules.isEmpty()) return in;
        return rewrite(in, rules);
    }

    private static Component rewrite(Component in, List<NameRules.Rename> rules) {
        ComponentContents contents = in.getContents();
        MutableComponent rewritten;
        if (contents instanceof PlainTextContents plain) {
            rewritten = Component.literal(NameRules.rewrite(plain.text(), rules));
        } else if (contents instanceof TranslatableContents tr) {
            // Vanilla chat ("<%s> %s", "[%s] %s", whispers) carries the player name as an argument.
            Object[] args = tr.getArgs().clone();
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Component c) args[i] = rewrite(c, rules);
                else if (args[i] instanceof String str) args[i] = NameRules.rewrite(str, rules);
            }
            rewritten = MutableComponent.create(new TranslatableContents(tr.getKey(), tr.getFallback(), args));
        } else {
            rewritten = MutableComponent.create(contents);
        }
        rewritten.setStyle(in.getStyle());
        for (Component sibling : in.getSiblings()) {
            rewritten.append(rewrite(sibling, rules));
        }
        return rewritten;
    }
}
