package dev.smolagent;

public final class AgentDetector {

    private AgentDetector() {}

    public static boolean isAgent(String name) {
        if (name == null) return false;
        return name.contains("Agent") || name.contains("agent");
    }
}
