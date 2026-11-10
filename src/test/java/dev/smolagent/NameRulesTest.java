package dev.smolagent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NameRulesTest {

    private static final List<NameRules.Rename> AGENT = List.of(new NameRules.Rename("Agent", "Smol"));

    @Test
    void keepsOldBehaviourForAgent() {
        assertEquals("SmolCool", NameRules.rewrite("AgentCool", AGENT));
        assertEquals("xsmolY", NameRules.rewrite("xagentY", AGENT));
        assertEquals("smolSmol", NameRules.rewrite("agentAgent", AGENT));
        assertEquals("SMOLX", NameRules.rewrite("AGENTX", AGENT));
    }

    @Test
    void oneRuleHitsSeveralPlayers() {
        List<NameRules.Rename> rules = List.of(new NameRules.Rename("beef", "Tofu"));
        assertEquals("UnicornTofu", NameRules.rewrite("UnicornBeef", rules));
        assertEquals("tofuLover", NameRules.rewrite("beefLover", rules));
        assertEquals("Bob", NameRules.rewrite("Bob", rules));
    }

    @Test
    void severalRulesSinglePassNoChaining() {
        List<NameRules.Rename> rules = List.of(new NameRules.Rename("a", "b"), new NameRules.Rename("b", "c"));
        assertEquals("bc", NameRules.rewrite("ab", rules));
    }

    @Test
    void longestMatchWins() {
        List<NameRules.Rename> rules = List.of(new NameRules.Rename("ag", "X"), new NameRules.Rename("agent", "smol"));
        assertEquals("smol", NameRules.rewrite("agent", rules));
        assertEquals("xe", NameRules.rewrite("age", rules));
    }

    @Test
    void emptyFromIgnoredEmptyToDeletes() {
        List<NameRules.Rename> rules = List.of(new NameRules.Rename("", "zzz"), new NameRules.Rename("_YT", ""));
        assertEquals("Steve", NameRules.rewrite("Steve_YT", rules));
    }

    @Test
    void replacementKeepsNonLetterStart() {
        assertEquals("x123", NameRules.rewrite("xAgent", List.of(new NameRules.Rename("agent", "123"))));
    }

    @Test
    void matchesAnyCaseInsensitiveSubstring() {
        List<String> parts = List.of("beef", "agent");
        assertTrue(NameRules.matchesAny(List.of("UnicornBEEF"), parts));
        assertTrue(NameRules.matchesAny(List.of("Steve", "SecretAgent007"), parts));
        assertFalse(NameRules.matchesAny(List.of("Steve", "Alex"), parts));
        assertFalse(NameRules.matchesAny(List.of("Steve"), List.of("", " ")));
    }
}
