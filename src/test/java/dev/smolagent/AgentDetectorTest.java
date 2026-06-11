package dev.smolagent;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDetectorTest {

    @Test
    void containsAgentCapital() {
        assertTrue(AgentDetector.isAgent("AgentCool"));
    }

    @Test
    void containsAgentLowercase() {
        assertTrue(AgentDetector.isAgent("xagentY"));
    }

    @Test
    void bothPresent() {
        assertTrue(AgentDetector.isAgent("AgentMrAgent"));
    }

    @Test
    void noMatch() {
        assertFalse(AgentDetector.isAgent("BobThePlumber"));
    }

    @Test
    void nullInput() {
        assertFalse(AgentDetector.isAgent(null));
    }

    @Test
    void emptyInput() {
        assertFalse(AgentDetector.isAgent(""));
    }

    @Test
    void allCapsNotDetected() {
        // Consistent with v1.0.x NameRewriter behavior — AGENT is not in scope.
        assertFalse(AgentDetector.isAgent("AGENT"));
    }
}
