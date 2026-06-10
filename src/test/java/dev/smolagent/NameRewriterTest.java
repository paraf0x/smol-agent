package dev.smolagent;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NameRewriterTest {

    @Test
    void capitalAgentBecomesSmol() {
        assertEquals("SmolCool", NameRewriter.rewrite("AgentCool"));
    }

    @Test
    void lowercaseAgentBecomesSmol() {
        assertEquals("smolX", NameRewriter.rewrite("agentX"));
    }

    @Test
    void capitalBeforeLowercaseOrderPreserved() {
        // Without ordered replacement, "Agent" → "Smolent" — guard against that.
        assertEquals("Smol", NameRewriter.rewrite("Agent"));
        assertEquals("smol", NameRewriter.rewrite("agent"));
    }

    @Test
    void multipleOccurrencesAllReplaced() {
        assertEquals("smolSmol", NameRewriter.rewrite("agentAgent"));
    }

    @Test
    void substringInSentenceReplaced() {
        assertEquals("secret smol Smol", NameRewriter.rewrite("secret agent Agent"));
    }

    @Test
    void unrelatedNameUntouched() {
        assertEquals("BobThePlumber", NameRewriter.rewrite("BobThePlumber"));
    }

    @Test
    void emptyStringReturnsEmpty() {
        assertEquals("", NameRewriter.rewrite(""));
    }

    @Test
    void allCapsNotReplacedInV1() {
        // Spec explicitly excludes AGENT case; only "Agent" and "agent" replaced.
        assertEquals("AGENTX", NameRewriter.rewrite("AGENTX"));
    }
}
