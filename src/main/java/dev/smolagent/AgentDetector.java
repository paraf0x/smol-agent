package dev.smolagent;

public final class AgentDetector {

    private AgentDetector() {}

    public static boolean isAgent(String name) {
        if (name == null) return false;
        // Match BOTH the original Agent/agent substring AND the rewritten Smol/smol
        // form. The transparency mixin runs in the render pipeline AFTER v1.0.x's
        // rename mixin has already rewritten the displayed name, so by the time
        // we read state.nameTag it has typically been mangled to "Smol...". We
        // accept either form as evidence of an agent player. Tradeoff: a player
        // whose actual Mojang name happens to contain "Smol" would also be
        // rendered transparent — acceptable for a fun cosmetic mod.
        return name.contains("Agent") || name.contains("agent")
            || name.contains("Smol") || name.contains("smol");
    }
}
