# Smol Agent Mod — Design

**Date:** 2026-06-10
**Author:** paraf0x
**Status:** Approved (pending spec review)

## Summary

A small client-only Fabric mod for Minecraft 26.1.2 that cosmetically rewrites the substring `Agent` to `Smol` (and `agent` to `smol`) wherever the client displays player names: the tab list, the nametag above each player's head, and incoming chat messages. The replacement is purely visual on the local client — no networking, persistence, or gameplay impact.

## Goals

- When a player named e.g. `AgentCool` is on the server, the local client shows `SmolCool` in tab, over their head, and in chat.
- Cover the three display surfaces: tab list, in-world nametag, chat.
- Keep the mod small and obvious — easy to enable/disable, easy to remove if it causes regressions.

## Non-Goals

- Changing the player's actual name on the server, sending modified chat to the server, or affecting other clients.
- A configuration UI, keybind, or runtime toggle. (May be added later if there is demand.)
- Replacing `AGENT` (all-caps) or other case variants — only the literal substrings `Agent` and `agent` are replaced in v1.
- Replacing `Agent`/`agent` in places that are not player names (item names, GUI labels, tooltips, etc.). The mod hooks only the three surfaces above.

## Architecture

```
┌─────────────────────────────────────────────────────┐
│  Smol Agent Mod (client-only Fabric mod, MC 26.1.2) │
├─────────────────────────────────────────────────────┤
│  Entry point: dev.smolagent.SmolAgentMod            │
│      └─ no-op ClientModInitializer                  │
│         (all behavior comes via Mixins)             │
│                                                     │
│  Shared utility: dev.smolagent.NameRewriter         │
│      ├─ rewrite(String) -> String                   │
│      └─ rewrite(Text)   -> Text                     │
│                                                     │
│  Mixins (all client-side):                          │
│      ├─ PlayerListHudMixin        (tab list)        │
│      ├─ PlayerEntityRendererMixin (nametag overhead)│
│      └─ ChatHudMixin              (incoming chat)   │
└─────────────────────────────────────────────────────┘
```

### Data flow

Vanilla Minecraft produces a `Text` (Component) for each display surface. Each Mixin intercepts at the relevant render or message-add site, routes the `Text` through `NameRewriter.rewrite(Text)`, and hands the rewritten `Text` back to vanilla. The rewriter walks the `Text` tree, preserves styling and click/hover events, and only mutates the literal string content of each leaf.

## Components

### `NameRewriter` (utility)

Pure function, no state, no Minecraft dependencies beyond the `Text` API.

```
String rewrite(String s):
    return s.replace("Agent", "Smol").replace("agent", "smol")

Text rewrite(Text in):
    Walk siblings/children recursively.
    For each leaf with literal string content, replace the string via rewrite(String) above
    while preserving the leaf's Style and any click/hover events.
    Return a reconstructed Text tree.
```

**Replacement order matters.** `Agent` must be replaced before `agent`. Otherwise the first pass would turn `Agent` into `Smolent` (because lowercased `gent` is left behind), which is wrong. With the order above:

- `AgentCool` → `SmolCool` ✓
- `secret agent` → `secret smol` ✓
- `agentAgent` → `smolSmol` ✓

### Mixins

All three mixins live under `dev.smolagent.mixin` and are listed in `smol-agent.mixins.json` under the `client` array.

**`PlayerListHudMixin`** — Mixin into `PlayerListHud` at the point where each row's display `Text` is computed. Most likely an `@ModifyVariable` on the `Text` local in the per-player render path (exact yarn signature for 26.1.2 to be confirmed in the implementation plan).

**`PlayerEntityRendererMixin`** — Mixin into the player entity renderer's label-rendering method (yarn typically `renderLabelIfPresent` on `EntityRenderer` or `LivingEntityRenderer`). `@ModifyVariable` on the `Text` parameter rewrites the label before it is glyph-rendered.

**`ChatHudMixin`** — `@ModifyVariable` on the `Text` parameter of `ChatHud#addMessage(Text)` (yarn) rewrites the message before it is appended to the chat queue.

All three mixins delegate to `NameRewriter.rewrite(Text)` and are otherwise trivial.

## Edge cases

- **Team prefix on nametag** (e.g. `[Team] AgentX`): the combined `Text` carries the prefix as a sibling and the player name as the main `Text`. The rewriter walks all siblings, so the player-name sibling is rewritten while the prefix is untouched.
- **Chat messages with click/hover events**: the rewriter preserves `Style` and events when reconstructing the rewritten leaf.
- **Self-name**: the local player's own name (e.g. in the F1-hidden tab list) is rewritten like everyone else. This is acceptable for v1 — no exclusion list.
- **Empty `Text`**: rewriter returns the input untouched.
- **Non-string `Text` content** (e.g. translatable keys, score values): only the literal string portions are rewritten; translatable/score components pass through unchanged.

## Mod scaffold

- Directory: `/Users/maksymvasyukov/git/sandbox/mods/smol-agent-mod`
- Cloned from `afk-filter-mod` (same Fabric / yarn / Loom / Java 25 stack for MC 26.1.2)
- `gradle.properties`:
  - `minecraft_version=26.1.2`
  - `loader_version=0.19.2`
  - `loom_version=1.16-SNAPSHOT`
  - `fabric_version=0.149.0+26.1.2`
  - `mod_version=1.0.0`
  - `maven_group=dev.smolagent`
  - `archives_base_name=smol-agent`
- `fabric.mod.json`:
  - `id`: `smol-agent`
  - `name`: `Smol Agent`
  - `environment`: `client`
  - `entrypoints.client`: `["dev.smolagent.SmolAgentMod"]`
  - `mixins`: `["smol-agent.mixins.json"]`
  - `depends`: `fabricloader >=0.19.2`, `fabric-api *`, `java >=25`, `minecraft ~26.1.2`
- `smol-agent.mixins.json`: lists the three client mixins under `"client"`.
- No icon for v1 (placeholder or omitted).

## Testing

Manual testing on a Fabric 26.1.2 server with at least two players (or on singleplayer with NPC/fake-name mods such as the existing TBI Create dev workflow):

1. **Build verification**: `JAVA_HOME=$(brew --prefix openjdk)/libexec/openjdk.jdk/Contents/Home ./gradlew build` produces a working jar in `build/libs/`. Drop it into a Fabric 26.1.2 client mods folder and confirm the client launches with no mixin-apply errors in `latest.log`.
2. **Tab list**: with at least one player on the server whose Mojang name contains `Agent` (e.g. `AgentCool`) and one without (e.g. `BobThePlumber`), press Tab. Verify `AgentCool` shows as `SmolCool` and `BobThePlumber` is unchanged.
3. **Nametag overhead**: have a second player named e.g. `agentX` walk into view. Verify nametag shows `smolX`.
4. **Chat**: have a player chat as `SomeAgent`. Verify the chat line shows `SomeSmol` as the sender. Also verify chat content `"hello agent"` becomes `"hello smol"` (expected behavior — see Non-Goals).
5. **Edge case — team prefix**: with a scoreboard team prefix `[T]` and player `Agent01`, verify the prefix `[T]` is preserved and only the player-name portion is rewritten.
6. **Negative test**: verify `Agent`/`agent` substrings in NON-name surfaces (item names, GUI labels, advancement titles) are NOT rewritten — this protects against accidentally hooking too broadly.

## Build & run

Per the existing toolchain memory: build with JDK 25 from Homebrew openjdk. The `afk-filter-mod` `build.gradle` already encodes the right Loom + Fabric settings — reuse verbatim with the name swaps above.

## Out of scope (potential follow-ups)

- Config file to add custom replacement pairs (`From → To`).
- Exclude-list (skip the local player, skip ops).
- Replace `AGENT` and other case variants smartly.
- Replace only in player-name components, not in arbitrary chat text.
