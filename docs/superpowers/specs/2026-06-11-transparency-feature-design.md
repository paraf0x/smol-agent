# Smol Agent — Transparency Feature (v1.1.0) — Design

**Date:** 2026-06-11
**Author:** paraf0x
**Status:** Approved (pending spec review)
**Builds on:** [Smol Agent v1.0.x design](2026-06-10-smol-agent-mod-design.md)

## Summary

Extend the existing Smol Agent mod (client-only Fabric mod for Minecraft 26.1.2) with a second cosmetic effect: render any player whose name contains the substring `Agent` or `agent` at 30% opacity. The transparency covers everything the player renderer draws — skin/body model, cape, armor (including enchant glints), and the overhead nametag. Released as Smol Agent v1.1.0.

## Goals

- For every player whose name contains `Agent` or `agent`, draw their model, cape, armor, glints, and nametag at alpha 0.3 on the local client.
- Reuse the existing rename detection rule (same substring match), through a single shared utility so future changes to the detection rule update both features simultaneously.
- Keep the implementation client-only. The server, networking, and other players' clients are unaffected.

## Non-Goals

- A per-player alpha value or configurable opacity. v1.1.0 hardcodes `0.3` for everyone matched, same as v1.0.x hardcodes the `Agent → Smol` rewrite.
- A configuration UI, keybind, or runtime toggle.
- Excluding the local player from transparency (e.g., F5 self-view will also show the local player transparent if their name contains `agent`). Acceptable for v1.1.0.
- Excluding non-player entities. The hook targets `LivingEntityRenderer`, which covers all living entities — but the detection is name-based, so non-player entities with `Agent`/`agent` in their custom name would also get the effect. In practice non-player names containing `agent` are essentially nonexistent; not worth a special case.
- Replacing the rename feature. v1.1.0 ships both: rename AND transparency, with no flag to disable either.

## Architecture

```
smol-agent (v1.1.0)
├── dev.smolagent.NameRewriter                       (unchanged from v1.0.x)
├── dev.smolagent.AgentDetector                      (NEW — extracted shared helper)
│   └── isAgent(String name): boolean                — substring match Agent/agent
├── dev.smolagent.alpha.AlphaBufferSource            (NEW)
│   └── implements MultiBufferSource, swaps opaque RenderTypes to translucent
├── dev.smolagent.alpha.AlphaVertexConsumer          (NEW)
│   └── delegating VertexConsumer that multiplies the alpha byte on setColor
└── dev.smolagent.mixin.LivingEntityRendererMixin    (NEW — 4th mixin)

Mixins manifest (client array — adds one new entry):
├── PlayerTabOverlayMixin        (unchanged)
├── ChatComponentMixin           (unchanged)
├── EntityRendererMixin          (unchanged)
└── LivingEntityRendererMixin    (NEW)
```

### Data flow

Vanilla calls `LivingEntityRenderer.render(state, poseStack, bufferSource, packedLight)`. The new `LivingEntityRendererMixin` runs at HEAD with `@ModifyVariable` on the `bufferSource` argument:

```
if AgentDetector.isAgent(state.name):
    bufferSource = new AlphaBufferSource(originalBufferSource, 0.3f)
```

The rest of the vanilla render runs unchanged. Every `bufferSource.getBuffer(renderType)` call produced during this render now returns an `AlphaVertexConsumer` wrapping the original consumer. Each `setColor` call that goes through this consumer has its alpha byte multiplied by 0.3.

Because all sub-renderers — feature renderers for armor, the cape feature renderer, the enchant-glint pass, and the nametag rendering — funnel through the same `MultiBufferSource`, they all inherit the alpha multiplier without any further hooks.

### Detection runs against the pre-rewrite name

The hook reads `state.name`, which is the raw player name as Mojang produces it for the render state — NOT the rewritten version that the v1.0.x mixins produce for tab/nametag/chat. This is intentional: a player named `AgentCool` is detected by the substring `Agent`, gets transparency applied, and also has their displayed name rewritten to `SmolCool` by the existing `EntityRendererMixin`. The two features are independent and compose cleanly.

## Components

### `AgentDetector` (shared utility)

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

Pure function, no Minecraft dependencies, easy to unit test.

`NameRewriter.rewrite(String)` does NOT need to change for v1.1.0 — its existing `String.replace` is correct and behaves the same as before. (A future refactor could route through `AgentDetector` as a fast-path; not required.)

### `AlphaVertexConsumer`

Delegating `VertexConsumer`. Every method forwards to the inner consumer. Only `setColor` calls modify the alpha component.

```java
package dev.smolagent.alpha;

import com.mojang.blaze3d.vertex.VertexConsumer;
// ...other imports...

public class AlphaVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final float alpha;

    public AlphaVertexConsumer(VertexConsumer delegate, float alpha) {
        this.delegate = delegate;
        this.alpha = alpha;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        return delegate.setColor(r, g, b, Math.round(a * alpha));
    }

    // All other methods (addVertex, setUv, setLight, setNormal, setOverlay, etc.)
    // forward directly to delegate with no modification.
}
```

The exact method count of `VertexConsumer` in 26.1.2 is verified via `genSources` during implementation. Any additional `setColor` overloads (e.g. one that takes a packed ARGB `int`) are also overridden to apply the alpha multiplier.

### `AlphaBufferSource`

Implements `MultiBufferSource`. One method.

```java
package dev.smolagent.alpha;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.vertex.VertexConsumer;

public class AlphaBufferSource implements MultiBufferSource {
    private final MultiBufferSource delegate;
    private final float alpha;

    public AlphaBufferSource(MultiBufferSource delegate, float alpha) {
        this.delegate = delegate;
        this.alpha = alpha;
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        RenderType translucentType = toTranslucent(type);
        return new AlphaVertexConsumer(delegate.getBuffer(translucentType), alpha);
    }

    private static RenderType toTranslucent(RenderType type) {
        // Map common opaque player render types to their translucent equivalents.
        // The exact mapping is verified against the 26.1.2 RenderType registry
        // during implementation. Typical case: entityCutoutNoCull → entityTranslucent.
        // RenderTypes already translucent pass through unchanged.
        // ...
    }
}
```

The RenderType swap is **load-bearing**: without it, alpha bytes are written into vertices but the pipeline does not enable alpha-blending, so the result is either invisible (alpha test cutoff) or fully opaque. The swap routes the geometry through a blending pipeline that respects the alpha byte.

### `LivingEntityRendererMixin`

Use `@WrapMethod` from MixinExtras to wrap the entire `LivingEntityRenderer.render(state, poseStack, bufferSource, packedLight)` call. MixinExtras is already on the classpath via Fabric Loader 0.19.2 (bundles `mixinextras 0.5.4`), so no extra dependency is needed.

The wrapper receives all four args and an `Operation` handle. It reads `state.name`, decides whether to wrap `bufferSource`, and invokes the original operation with the (possibly wrapped) buffer:

```java
package dev.smolagent.mixin;

import com.llamalad7.mixinextras.injector.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.smolagent.AgentDetector;
import dev.smolagent.alpha.AlphaBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    @WrapMethod(method = "render")
    private void smolagent$applyAgentAlpha(
            LivingEntityRenderState state,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            Operation<Void> original) {
        MultiBufferSource effective = AgentDetector.isAgent(state.name)
                ? new AlphaBufferSource(bufferSource, 0.3f)
                : bufferSource;
        original.call(state, poseStack, effective, packedLight);
    }
}
```

The exact `render(...)` method descriptor in 26.1.2 (and the exact name of the `name` field on `LivingEntityRenderState`) is verified via `genSources` during implementation. If `LivingEntityRenderState` exposes the name under a different accessor (e.g. `nameTag` returning a `Component` rather than a raw String), the detector call is adjusted accordingly — e.g. `AgentDetector.isAgent(state.nameTag != null ? state.nameTag.getString() : null)`.

## Edge cases

- **`state.name` is null:** `AgentDetector.isAgent(null)` returns false; no wrap. Safe.
- **Spectator alpha already applied:** Vanilla sets alpha < 1.0 for spectator-mode players. Our 0.3 multiplier stacks (e.g., 0.4 * 0.3 = 0.12). Visually a darker semitransparent ghost. Acceptable.
- **Custom-named non-player entities:** A villager renamed to `agent_smith` would also be drawn at 0.3. Edge case at the human-language level, not technically a bug. Not handled in v1.1.0.
- **Local player in F5:** Drawn transparent like everyone else if their name matches. Acceptable.
- **First-person view:** First-person hand/arm rendering uses a different path than `LivingEntityRenderer` for the hand, so it is unaffected. Held items shown in first person are also unaffected. The mod only changes the third-person rendering pipeline. Acceptable — matches user expectation ("see other players as ghosts").

## Testing

### Unit tests (JUnit 5, in src/test)

- `AgentDetectorTest`:
  - `containsAgentCapital`: `isAgent("AgentCool")` → true
  - `containsAgentLowercase`: `isAgent("xagentY")` → true
  - `bothPresent`: `isAgent("AgentMrAgent")` → true
  - `noMatch`: `isAgent("BobThePlumber")` → false
  - `null`: `isAgent(null)` → false
  - `empty`: `isAgent("")` → false
  - `allCaps`: `isAgent("AGENT")` → false (v1.0.x rename also excludes this; we stay consistent)

The wrappers (`AlphaBufferSource`, `AlphaVertexConsumer`) cannot be unit-tested without the Minecraft runtime — verified in manual testing.

### Manual integration tests (Minecraft 26.1.2 client)

1. **Build verification**: `JAVA_HOME=$(brew --prefix openjdk)/libexec/openjdk.jdk/Contents/Home ./gradlew clean build` produces `smol-agent-1.1.0.jar`. Drop into Fabric 26.1.2 client. Launch. Check `latest.log` for `LivingEntityRendererMixin applied` and no mixin errors.
2. **Tab list / chat / nametag still work**: rename behavior from v1.0.x must continue to function exactly as before for `AgentCool` → tab shows `SmolCool`, chat shows `SmolCool`, nametag shows `SmolCool`.
3. **Transparency — base case**: player `AgentCool` joins, observed in third person. Skin model is rendered at ~30% opacity (clearly translucent — landscape visible behind them).
4. **Transparency — armor**: equip iron armor on `agentX`. Armor model is also transparent.
5. **Transparency — cape**: equip cape on `AgentMaria`. Cape is also transparent.
6. **Transparency — enchant glint**: equip enchanted netherite armor. Glint shimmer is also alpha-multiplied (not fully opaque on a transparent body).
7. **Transparency — nametag**: nametag floating above `AgentCool` is also at 30% opacity (label background and text).
8. **Negative — non-agent unaffected**: `BobThePlumber` standing next to `AgentCool` is rendered fully opaque.
9. **Negative — items / GUI unaffected**: open inventory while viewing `AgentCool` — none of the inventory item icons, tooltips, or GUI text are transparent.
10. **First-person hand**: switch to first person on a client logged in as `AgentSelf`. Own hand and held items render fully opaque (different render path).

## Mod metadata updates

- `gradle.properties`: `mod_version=1.1.0`
- `fabric.mod.json` description: extend to mention transparency (`Cosmetically rewrites 'Agent'/'agent' substrings AND renders matching players at 30% opacity. Client-side only.`)
- `smol-agent.mixins.json` `client` array: add `LivingEntityRendererMixin`
- `README.md`: one paragraph describing the new transparency effect

## Out of scope (potential follow-ups)

- Configurable alpha value (per-player or global).
- Excluding self / specific players from transparency.
- Honoring `AGENT` (all caps) for either feature.
- Switching the detection rule to be configurable via a JSON file.
