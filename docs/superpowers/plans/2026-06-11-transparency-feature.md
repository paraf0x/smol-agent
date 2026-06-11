# Smol Agent — Transparency Feature (v1.1.0) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend Smol Agent to render any player whose name contains `Agent`/`agent` at 30% opacity, covering skin, cape, armor, glints, and nametag. Shipped as v1.1.0.

**Architecture:** A single new `@WrapMethod` mixin on `LivingEntityRenderer.render` replaces the `MultiBufferSource` argument with an alpha-multiplying wrapper whenever the rendered entity's name matches the agent rule. The wrapper produces `VertexConsumer`s that multiply the alpha byte on every `setColor` call, so every sub-renderer (armor feature renderers, cape, glints, nametag) that goes through the same buffer source inherits the alpha cut automatically. A shared `AgentDetector` utility holds the substring rule.

**Tech Stack:**
- Minecraft 26.1.2 (Mojang-mapped, unobfuscated — no yarn)
- Fabric Loader 0.19.2, Fabric API 0.149.0+26.1.2
- MixinExtras 0.5.4 (bundled via Fabric Loader) — provides `@WrapMethod`
- Loom 1.16-SNAPSHOT, Java 25
- JUnit 5 for the pure unit tests of `AgentDetector`

**Spec:** `docs/superpowers/specs/2026-06-11-transparency-feature-design.md`

---

## File Structure

Files to create or modify under `/Users/maksymvasyukov/git/sandbox/mods/smol-agent-mod/`:

```
smol-agent-mod/
├── gradle.properties                            (modify: mod_version 1.0.1 → 1.1.0)
├── README.md                                    (modify: mention transparency)
├── src/main/java/dev/smolagent/
│   ├── AgentDetector.java                       (CREATE)
│   ├── NameRewriter.java                        (unchanged)
│   ├── SmolAgentMod.java                        (unchanged)
│   ├── alpha/
│   │   ├── AlphaBufferSource.java               (CREATE)
│   │   └── AlphaVertexConsumer.java             (CREATE)
│   └── mixin/
│       ├── ChatComponentMixin.java              (unchanged)
│       ├── EntityRendererMixin.java             (unchanged)
│       ├── LivingEntityRendererMixin.java       (CREATE)
│       └── PlayerTabOverlayMixin.java           (unchanged)
├── src/main/resources/
│   ├── fabric.mod.json                          (modify: description)
│   └── smol-agent.mixins.json                   (modify: add LivingEntityRendererMixin)
└── src/test/java/dev/smolagent/
    └── AgentDetectorTest.java                   (CREATE)
```

---

## Task 1: TDD `AgentDetector` pure utility

**Files:**
- Create: `src/main/java/dev/smolagent/AgentDetector.java`
- Create: `src/test/java/dev/smolagent/AgentDetectorTest.java`

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/dev/smolagent/AgentDetectorTest.java`:

```java
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
```

- [ ] **Step 2: Run tests to verify they fail to compile**

```bash
cd /Users/maksymvasyukov/git/sandbox/mods/smol-agent-mod
JAVA_HOME=$(brew --prefix openjdk)/libexec/openjdk.jdk/Contents/Home ./gradlew test --no-daemon
```

Expected: compilation failure — `AgentDetector` symbol not found.

- [ ] **Step 3: Write minimal implementation**

Create `src/main/java/dev/smolagent/AgentDetector.java`:

```java
package dev.smolagent;

public final class AgentDetector {

    private AgentDetector() {}

    public static boolean isAgent(String name) {
        if (name == null) return false;
        return name.contains("Agent") || name.contains("agent");
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd /Users/maksymvasyukov/git/sandbox/mods/smol-agent-mod
JAVA_HOME=$(brew --prefix openjdk)/libexec/openjdk.jdk/Contents/Home ./gradlew test --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all NameRewriterTest (8) + AgentDetectorTest (7) = 15 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/smolagent/AgentDetector.java src/test/java/dev/smolagent/AgentDetectorTest.java
git commit -m "feat: AgentDetector utility with unit tests"
```

---

## Task 2: `AlphaVertexConsumer` delegating wrapper

**Files:**
- Create: `src/main/java/dev/smolagent/alpha/AlphaVertexConsumer.java`

This wrapper cannot be unit-tested without the Minecraft runtime (the `VertexConsumer` interface is part of Blaze3D). Correctness is verified in the manual test in Task 6.

- [ ] **Step 1: Discover the VertexConsumer interface shape**

```bash
cd /Users/maksymvasyukov/git/sandbox/mods/smol-agent-mod
JAVA_HOME=$(brew --prefix openjdk)/libexec/openjdk.jdk/Contents/Home ./gradlew genSources --no-daemon
```

Find the merged Minecraft jar (already present from prior work) and inspect `VertexConsumer`:

```bash
find ~/.gradle/caches/fabric-loom -name "*minecraft-merged*.jar" ! -name "*sources*" 2>/dev/null | head -1
# Then:
javap -p <that-jar-path> 'com.mojang.blaze3d.vertex.VertexConsumer'
```

You will see roughly these abstract methods (names may vary slightly in 26.1.2 — use the actual output as the source of truth):

```
VertexConsumer addVertex(float x, float y, float z)
VertexConsumer setColor(int r, int g, int b, int a)
VertexConsumer setColor(int rgba)                // packed-int overload, if present
VertexConsumer setUv(float u, float v)
VertexConsumer setUv1(int u, int v)              // overlay coords
VertexConsumer setUv2(int u, int v)              // lightmap coords
VertexConsumer setNormal(float x, float y, float z)
```

(In 26.1.2 there may be 7-9 abstract methods total. Anything that returns `VertexConsumer` is a fluent setter that must be overridden to delegate.)

- [ ] **Step 2: Write the wrapper**

Create `src/main/java/dev/smolagent/alpha/AlphaVertexConsumer.java`. Override every abstract method to forward to `delegate`, modifying only the alpha component on `setColor`:

```java
package dev.smolagent.alpha;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Delegating VertexConsumer that multiplies the alpha component of every
 * {@link #setColor(int, int, int, int)} call by a constant factor in (0.0, 1.0].
 * Used to render an entity (and everything that goes through its
 * MultiBufferSource — armor, cape, glints, nametag) at reduced opacity.
 */
public class AlphaVertexConsumer implements VertexConsumer {

    private final VertexConsumer delegate;
    private final float alpha;

    public AlphaVertexConsumer(VertexConsumer delegate, float alpha) {
        this.delegate = delegate;
        this.alpha = alpha;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        int scaled = Math.round(a * alpha);
        if (scaled < 0) scaled = 0;
        if (scaled > 255) scaled = 255;
        delegate.setColor(r, g, b, scaled);
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        delegate.setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        delegate.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        delegate.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        delegate.setNormal(x, y, z);
        return this;
    }
}
```

**If `javap` shows additional abstract methods that aren't in this template**, override each of them in the same pattern: forward to `delegate`, return `this`. If `VertexConsumer` has a packed `setColor(int rgba)` overload, override it too:

```java
@Override
public VertexConsumer setColor(int rgba) {
    int a = (rgba >>> 24) & 0xFF;
    int rgb = rgba & 0x00FFFFFF;
    int scaled = Math.round(a * alpha);
    if (scaled < 0) scaled = 0;
    if (scaled > 255) scaled = 255;
    delegate.setColor((scaled << 24) | rgb);
    return this;
}
```

The principle: every abstract method delegates. Only `setColor` overloads modify alpha. **`this` is returned (not `delegate`)** so the fluent chain keeps going through the alpha wrapper.

- [ ] **Step 3: Verify it compiles**

```bash
cd /Users/maksymvasyukov/git/sandbox/mods/smol-agent-mod
JAVA_HOME=$(brew --prefix openjdk)/libexec/openjdk.jdk/Contents/Home ./gradlew build --no-daemon
```

Expected: `BUILD SUCCESSFUL`. If a compile error says `AlphaVertexConsumer is not abstract and does not override abstract method X`, add an override for method X following the same delegate-and-return-this pattern.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/smolagent/alpha/AlphaVertexConsumer.java
git commit -m "feat: AlphaVertexConsumer delegating wrapper with alpha multiplier"
```

---

## Task 3: `AlphaBufferSource` wrapper

**Files:**
- Create: `src/main/java/dev/smolagent/alpha/AlphaBufferSource.java`

- [ ] **Step 1: Write the wrapper**

Create `src/main/java/dev/smolagent/alpha/AlphaBufferSource.java`:

```java
package dev.smolagent.alpha;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/**
 * MultiBufferSource that wraps every VertexConsumer it produces in an
 * {@link AlphaVertexConsumer}, multiplying the alpha component on every
 * {@code setColor} call. Used to render an entity (and everything that
 * goes through this buffer source — armor, cape, glints, nametag) at
 * reduced opacity.
 *
 * <p>Note: this wrapper passes the {@link RenderType} through unchanged.
 * Render types that already use an alpha-blending pipeline (translucent
 * variants) will visibly fade. Render types that use opaque pipelines
 * (cutout variants) will write the modified alpha byte into vertices,
 * but the final visual blending depends on the render type's GL state.
 * For v1.1.0 this is acceptable — most player render surfaces in modern
 * Minecraft do honor the alpha byte. If a specific surface (e.g. armor
 * cutout) does not visibly fade in manual testing, a v1.2.0 follow-up can
 * add a RenderType-swap layer here.
 */
public class AlphaBufferSource implements MultiBufferSource {

    private final MultiBufferSource delegate;
    private final float alpha;

    public AlphaBufferSource(MultiBufferSource delegate, float alpha) {
        this.delegate = delegate;
        this.alpha = alpha;
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        return new AlphaVertexConsumer(delegate.getBuffer(type), alpha);
    }
}
```

- [ ] **Step 2: Verify it compiles**

```bash
cd /Users/maksymvasyukov/git/sandbox/mods/smol-agent-mod
JAVA_HOME=$(brew --prefix openjdk)/libexec/openjdk.jdk/Contents/Home ./gradlew build --no-daemon
```

Expected: `BUILD SUCCESSFUL`. If `MultiBufferSource` has additional abstract methods in 26.1.2 (it should not — vanilla MultiBufferSource only declares `getBuffer`), inspect via `javap -p` on the merged jar and add overrides that delegate.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/smolagent/alpha/AlphaBufferSource.java
git commit -m "feat: AlphaBufferSource wraps VertexConsumers in AlphaVertexConsumer"
```

---

## Task 4: `LivingEntityRendererMixin` — apply alpha to matched players

**Files:**
- Create: `src/main/java/dev/smolagent/mixin/LivingEntityRendererMixin.java`
- Modify: `src/main/resources/smol-agent.mixins.json` (add the new mixin)

- [ ] **Step 1: Discover the LivingEntityRenderer.render signature and the LivingEntityRenderState name field**

```bash
cd /Users/maksymvasyukov/git/sandbox/mods/smol-agent-mod
find ~/.gradle/caches/fabric-loom -name "*minecraft-merged*.jar" ! -name "*sources*" 2>/dev/null | head -1
# Then:
javap -p <that-jar-path> 'net.minecraft.client.renderer.entity.LivingEntityRenderer' | grep -E "render\s*\("
javap -p <that-jar-path> 'net.minecraft.client.renderer.entity.state.LivingEntityRenderState'
```

You should see roughly:
```
public void render(LivingEntityRenderState, PoseStack, MultiBufferSource, int)
```

And `LivingEntityRenderState` should have a field accessor for the rendered name. The likely field is one of:
- `public String name`
- `public Component nameTag`
- `public String nameTagText`

Use the actual field name from `javap` in the mixin. If the rendered name is stored as `Component nameTag` (not a raw String), convert via `.getString()` before passing to `AgentDetector.isAgent`.

- [ ] **Step 2: Write the mixin**

Create `src/main/java/dev/smolagent/mixin/LivingEntityRendererMixin.java`:

```java
package dev.smolagent.mixin;

import com.llamalad7.mixinextras.injector.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.smolagent.AgentDetector;
import dev.smolagent.alpha.AlphaBufferSource;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Wraps LivingEntityRenderer.render so that whenever the rendered entity's
 * displayed name contains "Agent" or "agent", every sub-render that runs
 * during this call (skin, cape, armor feature renderers, glints, nametag)
 * receives a MultiBufferSource whose VertexConsumers multiply the alpha
 * component by 0.3.
 *
 * <p>The detection reads the raw state name BEFORE smol-agent's rename
 * rewrite has been applied — so a player called "AgentCool" gets detected
 * as agent (transparency on) and ALSO has their displayed name rewritten
 * to "SmolCool" by the existing EntityRendererMixin. The two features
 * compose cleanly.
 */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    private static final float AGENT_ALPHA = 0.3f;

    @WrapMethod(method = "render")
    private void smolagent$applyAgentAlpha(
            LivingEntityRenderState state,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            Operation<Void> original) {
        MultiBufferSource effective = bufferSource;
        if (state != null && AgentDetector.isAgent(extractName(state))) {
            effective = new AlphaBufferSource(bufferSource, AGENT_ALPHA);
        }
        original.call(state, poseStack, effective, packedLight);
    }

    /**
     * Extracts the rendered name as a plain String from the state. The exact
     * field on LivingEntityRenderState in 26.1.2 is verified during Step 1.
     * Adjust this body to read the correct field.
     */
    private static String extractName(LivingEntityRenderState state) {
        // Likely candidates — pick whichever exists in 26.1.2:
        //   return state.name;
        //   return state.nameTag != null ? state.nameTag.getString() : null;
        //   return state.nameTagText;
        // Verified during Step 1 via javap on LivingEntityRenderState.
        return state.name;
    }
}
```

If `javap` in Step 1 showed a different field name (e.g. `nameTag` of type `Component`), replace the body of `extractName` with the appropriate access pattern. Example for a Component field:

```java
private static String extractName(LivingEntityRenderState state) {
    return state.nameTag != null ? state.nameTag.getString() : null;
}
```

- [ ] **Step 3: Register the mixin in `smol-agent.mixins.json`**

Replace `src/main/resources/smol-agent.mixins.json` with:

```json
{
  "required": true,
  "package": "dev.smolagent.mixin",
  "compatibilityLevel": "JAVA_25",
  "client": [
    "PlayerTabOverlayMixin",
    "ChatComponentMixin",
    "EntityRendererMixin",
    "LivingEntityRendererMixin"
  ],
  "injectors": {
    "defaultRequire": 1
  }
}
```

- [ ] **Step 4: Build**

```bash
cd /Users/maksymvasyukov/git/sandbox/mods/smol-agent-mod
JAVA_HOME=$(brew --prefix openjdk)/libexec/openjdk.jdk/Contents/Home ./gradlew build --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

If a compile error says `cannot find symbol state.name` (or whichever field name was used in `extractName`), Step 1 picked the wrong field name. Re-run `javap` on `LivingEntityRenderState` and use the actual field. If the field is private (no direct access), an accessor mixin is needed:

Create `src/main/java/dev/smolagent/mixin/LivingEntityRenderStateAccessor.java`:

```java
package dev.smolagent.mixin;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntityRenderState.class)
public interface LivingEntityRenderStateAccessor {
    @Accessor("name") String smolagent$getName();
}
```

And register it in mixins.json alongside the others. Then update `extractName`:

```java
private static String extractName(LivingEntityRenderState state) {
    return ((LivingEntityRenderStateAccessor) state).smolagent$getName();
}
```

(Most fields on render state classes in 26.1.2 are public final, so the accessor mixin is typically NOT needed. Only fall back to it if direct field access fails to compile.)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/smolagent/mixin/ src/main/resources/smol-agent.mixins.json
git commit -m "feat: LivingEntityRendererMixin applies 30% alpha to agent players"
```

---

## Task 5: Bump version + update metadata + README

**Files:**
- Modify: `gradle.properties`
- Modify: `src/main/resources/fabric.mod.json`
- Modify: `README.md`

- [ ] **Step 1: Bump mod_version to 1.1.0**

Edit `gradle.properties`, replacing the existing `mod_version` line:

```properties
mod_version=1.1.0
```

The rest of the file is unchanged.

- [ ] **Step 2: Update fabric.mod.json description**

Edit `src/main/resources/fabric.mod.json`. Replace the `description` line with:

```json
  "description": "Cosmetically rewrites 'Agent'/'agent' substrings to 'Smol'/'smol' in tab, nametag, and chat, AND renders matching players at 30% opacity. Client-side only.",
```

The rest of the file is unchanged.

- [ ] **Step 3: Update README.md**

Edit `README.md`. Replace the first paragraph (the one-line description starting with "A client-only Fabric mod") with:

```markdown
A client-only Fabric mod for Minecraft 26.1.2 with two cosmetic effects for
players whose name contains `Agent` or `agent`:

- their displayed name is rewritten (`Agent` → `Smol`, `agent` → `smol`) in
  the tab list, in overhead nametags, and in chat messages
- they are rendered at 30% opacity — skin, cape, armor, glints, and nametag
```

The rest of the README (Install, Build, License) is unchanged. Bump the install instruction's jar filename to `smol-agent-1.1.0.jar`.

- [ ] **Step 4: Commit**

```bash
git add gradle.properties src/main/resources/fabric.mod.json README.md
git commit -m "chore: bump version to 1.1.0, document transparency"
```

---

## Task 6: Final build + manual smoke test + tag + release

**Files:**
- No new files

- [ ] **Step 1: Final clean build**

```bash
cd /Users/maksymvasyukov/git/sandbox/mods/smol-agent-mod
JAVA_HOME=$(brew --prefix openjdk)/libexec/openjdk.jdk/Contents/Home ./gradlew clean build --no-daemon
ls -lh build/libs/
```

Expected: `build/libs/smol-agent-1.1.0.jar` and `smol-agent-1.1.0-sources.jar`. 15 tests pass (8 NameRewriter + 7 AgentDetector).

- [ ] **Step 2: Install and launch test**

Copy `build/libs/smol-agent-1.1.0.jar` into a Fabric 26.1.2 client's `mods/` folder, alongside Fabric API 0.149.0+26.1.2. Replace any existing `smol-agent-1.0.x.jar` to avoid duplicate loading.

Launch the client. Check `<instance>/logs/latest.log` for:

```
[mixin/INFO]: Mixing PlayerTabOverlayMixin from smol-agent.mixins.json
[mixin/INFO]: Mixing ChatComponentMixin from smol-agent.mixins.json
[mixin/INFO]: Mixing EntityRendererMixin from smol-agent.mixins.json
[mixin/INFO]: Mixing LivingEntityRendererMixin from smol-agent.mixins.json
```

No `InvalidInjectionException` or `MixinApplyError`. If a mixin error appears for `LivingEntityRendererMixin`, the failure message in the log identifies the issue (most likely a mismatch in `@WrapMethod`'s method selector or in the `state` field access) — fix per the troubleshooting in Task 4 step 4, rebuild, swap the jar, relaunch.

- [ ] **Step 3: Run the 10 manual integration tests from the spec**

For each test, observe the in-game result. If any fails, fix in code, rebuild (`./gradlew build`), swap the jar in the instance, relaunch, and re-test.

1. **Build verification** — already done in Step 1.
2. **Rename still works** — join a server with a player whose name contains `Agent`. Press Tab. Verify tab/chat/nametag rename behavior identical to v1.0.x (`AgentCool` → `SmolCool`).
3. **Transparency — base body** — player `AgentCool` in third-person view: skin model is visibly translucent (~30%, landscape visible through them).
4. **Transparency — armor** — `agentX` wearing iron armor: armor is also translucent.
5. **Transparency — cape** — `AgentMaria` with a cape: cape is translucent.
6. **Transparency — enchant glint** — `AgentMaria` with enchanted netherite armor: glint visible but at reduced opacity (not full-bright on a translucent body).
7. **Transparency — nametag** — nametag label and text floating above `AgentCool` are at 30% opacity.
8. **Negative — non-agent unaffected** — `BobThePlumber` standing next to `AgentCool` is fully opaque.
9. **Negative — items/GUI unaffected** — open inventory; no inventory icons or tooltips are translucent.
10. **First-person hand** — switch to first-person view as `AgentSelf`; own hand and held items are fully opaque (different render path).

**If a surface (armor cutout, glint) does not visibly fade** despite the alpha byte being modified, that is the known RenderType limitation described in `AlphaBufferSource.java`'s class Javadoc. Document the affected surface and either:
- accept it as a v1.1.0 limitation (note in README and ship as-is), OR
- defer to a v1.2.0 follow-up that adds the RenderType-swap layer in `AlphaBufferSource.getBuffer`.

For v1.1.0, accepting limitations is the recommended path — `git tag v1.1.0` and ship.

- [ ] **Step 4: Tag v1.1.0**

```bash
cd /Users/maksymvasyukov/git/sandbox/mods/smol-agent-mod
git tag -a v1.1.0 -m "Smol Agent v1.1.0 — agent players rendered at 30% opacity"
git log --oneline | head -10
git tag -l
```

Expected: tag list shows `v1.0.0`, `v1.0.1`, `v1.1.0`.

- [ ] **Step 5: Push to GitHub + create release**

```bash
cd /Users/maksymvasyukov/git/sandbox/mods/smol-agent-mod
git push origin main
git push origin v1.1.0
gh release create v1.1.0 \
  "build/libs/smol-agent-1.1.0.jar" \
  "build/libs/smol-agent-1.1.0-sources.jar" \
  --title "Smol Agent v1.1.0" \
  --notes "Second cosmetic effect on top of the v1.0.x rename: players whose name contains \`Agent\` or \`agent\` are rendered at 30% opacity (skin, cape, armor, glints, nametag).

Drop \`smol-agent-1.1.0.jar\` into your Fabric 26.1.2 client's \`mods/\` folder alongside Fabric API \`0.149.0+26.1.2\` or newer. Requires Java 25."
```

Expected: GitHub release published at `https://github.com/paraf0x/smol-agent/releases/tag/v1.1.0`.

---

## Notes for the implementer

- **MixinExtras `@WrapMethod`** is the chosen pattern because it has clean access to ALL of the wrapped method's args (state + buffer source) in one annotation. Sponge Mixin's `@ModifyVariable` only sees the variable being modified and can't easily read sibling args. MixinExtras 0.5.4 is bundled by Fabric Loader 0.19.2, so no extra Gradle dependency is needed.

- **No yarn.** Minecraft 26.x ships unobfuscated with Mojang names. Every class reference uses the Mojang names directly (`net.minecraft.client.renderer.entity.LivingEntityRenderer`, `LivingEntityRenderState`, `MultiBufferSource`, etc.).

- **`genSources` is your friend.** When in doubt about a method signature, run `./gradlew genSources` and `javap -p` on the merged jar. The names given in this plan are best-effort based on 26.1.2 conventions and may need small adjustments.

- **RenderType swap is intentionally NOT included in v1.1.0.** The spec mentions it as a desirable transformation but a faithful implementation requires accessor mixins on internal classes (`RenderType.CompositeRenderType`, `CompositeState`, `TextureStateShard`) to extract the texture, plus a swap table. That complexity is deferred to v1.2.0 as a follow-up if v1.1.0's pure alpha-multiplier approach proves visually insufficient for some surfaces.

- **Resist scope creep.** v1.1.0 ships ONE new effect on the same detection rule. No config UI, no per-player alpha, no keybind, no in-game commands. Save those for later versions if there's actually demand.
