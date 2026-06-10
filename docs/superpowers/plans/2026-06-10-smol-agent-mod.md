# Smol Agent Mod Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a client-only Fabric mod for Minecraft 26.1.2 that cosmetically rewrites the substring `Agent` to `Smol` (and `agent` to `smol`) in the tab list, in nametags above players' heads, and in incoming chat messages.

**Architecture:** A small ClientModInitializer plus three targeted Mixins — `PlayerTabOverlay` for the tab list, `EntityRenderer.renderNameTag` for the overhead nametag, and `ChatComponent.addMessage` for chat. All three Mixins delegate to a shared utility class `NameRewriter` that walks a `Component` tree and rewrites literal string content while preserving `Style` and click/hover events.

**Tech Stack:**
- Minecraft 26.1.2 (Mojang-mapped, unobfuscated — no yarn dependency)
- Fabric Loader 0.19.2, Fabric API 0.149.0+26.1.2
- Loom 1.16-SNAPSHOT
- Java 25 (Homebrew openjdk)
- JUnit 5 for unit tests of the pure-string rewriter

**Spec:** `docs/superpowers/specs/2026-06-10-smol-agent-mod-design.md`

---

## File Structure

Files to be created under `/Users/maksymvasyukov/git/sandbox/mods/smol-agent-mod/`:

```
smol-agent-mod/
├── build.gradle                    # cloned from afk-filter-mod, names changed, JUnit added
├── settings.gradle                 # cloned from afk-filter-mod
├── gradle.properties               # cloned, names + version changed
├── gradlew, gradlew.bat            # cloned verbatim
├── gradle/wrapper/                 # cloned verbatim
├── LICENSE                         # cloned (MIT)
├── README.md                       # one-paragraph readme
├── src/main/java/dev/smolagent/
│   ├── SmolAgentMod.java           # no-op ClientModInitializer
│   ├── NameRewriter.java           # pure utility: String + Component rewriting
│   └── mixin/
│       ├── PlayerTabOverlayMixin.java     # tab list
│       ├── EntityRendererMixin.java       # overhead nametag
│       └── ChatComponentMixin.java        # chat
├── src/main/resources/
│   ├── fabric.mod.json
│   └── smol-agent.mixins.json
└── src/test/java/dev/smolagent/
    └── NameRewriterTest.java
```

---

## Task 1: Scaffold project from afk-filter-mod template

**Files:**
- Create: `/Users/maksymvasyukov/git/sandbox/mods/smol-agent-mod/` (whole tree, cloned)

- [ ] **Step 1: Clone the afk-filter-mod skeleton, drop its src/**

```bash
cd /Users/maksymvasyukov/git/sandbox/mods
cp -R afk-filter-mod smol-agent-mod
rm -rf smol-agent-mod/src smol-agent-mod/build smol-agent-mod/.gradle
rm -f smol-agent-mod/screenshot.jpg smol-agent-mod/icon_256.png smol-agent-mod/icon_512.png smol-agent-mod/image.png smol-agent-mod/2026-03-11_10.41.33.png smol-agent-mod/README.md
ls smol-agent-mod
```

Expected output includes: `LICENSE  build.gradle  gradle  gradle.properties  gradlew  gradlew.bat  settings.gradle`

- [ ] **Step 2: Update gradle.properties with new mod identity**

Replace the entire contents of `smol-agent-mod/gradle.properties` with:

```properties
org.gradle.jvmargs=-Xmx1G
org.gradle.parallel=true

# --- Minecraft 26.1.2 ---
minecraft_version=26.1.2
loader_version=0.19.2
loom_version=1.16-SNAPSHOT
fabric_version=0.149.0+26.1.2

mod_version=1.0.0
maven_group=dev.smolagent
archives_base_name=smol-agent
```

- [ ] **Step 3: Update settings.gradle**

Replace contents of `smol-agent-mod/settings.gradle` with:

```groovy
pluginManagement {
    repositories {
        maven { url 'https://maven.fabricmc.net/' }
        gradlePluginPortal()
    }
}

rootProject.name = "smol-agent"
```

- [ ] **Step 4: Update build.gradle (add JUnit 5 test deps)**

Replace contents of `smol-agent-mod/build.gradle` with:

```groovy
plugins {
    id 'net.fabricmc.fabric-loom' version "${loom_version}"
    id 'maven-publish'
}

version = project.mod_version
group = project.maven_group

base {
    archivesName = project.archives_base_name
}

repositories {
    mavenCentral()
}

dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    implementation "net.fabricmc:fabric-loader:${project.loader_version}"
    implementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_version}"

    testImplementation platform("org.junit:junit-bom:5.10.2")
    testImplementation "org.junit.jupiter:junit-jupiter"
}

processResources {
    inputs.property "version", project.version
    filesMatching("fabric.mod.json") {
        expand "version": project.version
    }
}

tasks.withType(JavaCompile).configureEach {
    it.options.encoding = "UTF-8"
    it.options.release = 25
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

test {
    useJUnitPlatform()
}

jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}
```

- [ ] **Step 5: Init git repo + initial commit**

```bash
cd /Users/maksymvasyukov/git/sandbox/mods/smol-agent-mod
echo "build/" > .gitignore
echo ".gradle/" >> .gitignore
echo ".idea/" >> .gitignore
echo "*.iml" >> .gitignore
git init -b main
git add .
git commit -m "chore: scaffold from afk-filter-mod template"
```

Expected: `[main (root-commit) ...] chore: scaffold from afk-filter-mod template`

- [ ] **Step 6: Verify Gradle can resolve dependencies**

```bash
cd /Users/maksymvasyukov/git/sandbox/mods/smol-agent-mod
JAVA_HOME=$(brew --prefix openjdk)/libexec/openjdk.jdk/Contents/Home ./gradlew tasks --no-daemon
```

Expected: prints the Gradle task list including `build`, `test`, `compileJava`. No errors.

---

## Task 2: TDD — `NameRewriter.rewrite(String)` pure function

**Files:**
- Create: `src/main/java/dev/smolagent/NameRewriter.java`
- Create: `src/test/java/dev/smolagent/NameRewriterTest.java`

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/dev/smolagent/NameRewriterTest.java`:

```java
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
```

- [ ] **Step 2: Run tests to verify they fail to compile**

```bash
cd /Users/maksymvasyukov/git/sandbox/mods/smol-agent-mod
JAVA_HOME=$(brew --prefix openjdk)/libexec/openjdk.jdk/Contents/Home ./gradlew test --no-daemon
```

Expected: compilation failure — `NameRewriter` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `src/main/java/dev/smolagent/NameRewriter.java`:

```java
package dev.smolagent;

public final class NameRewriter {

    private NameRewriter() {}

    public static String rewrite(String s) {
        if (s == null || s.isEmpty()) return s;
        // Order matters: capital first so "Agent" doesn't get mangled to "Smolent"
        // by the lowercase pass picking up the lowercased "gent" tail.
        return s.replace("Agent", "Smol").replace("agent", "smol");
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd /Users/maksymvasyukov/git/sandbox/mods/smol-agent-mod
JAVA_HOME=$(brew --prefix openjdk)/libexec/openjdk.jdk/Contents/Home ./gradlew test --no-daemon
```

Expected: `BUILD SUCCESSFUL`, all 8 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/smolagent/NameRewriter.java src/test/java/dev/smolagent/NameRewriterTest.java
git commit -m "feat: NameRewriter.rewrite(String) with unit tests"
```

---

## Task 3: Mod entry point + Fabric metadata

**Files:**
- Create: `src/main/java/dev/smolagent/SmolAgentMod.java`
- Create: `src/main/resources/fabric.mod.json`
- Create: `src/main/resources/smol-agent.mixins.json`

- [ ] **Step 1: Create the mod entry class**

Create `src/main/java/dev/smolagent/SmolAgentMod.java`:

```java
package dev.smolagent;

import net.fabricmc.api.ClientModInitializer;

public class SmolAgentMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // All behavior is in Mixins; entry point intentionally empty.
    }
}
```

- [ ] **Step 2: Create fabric.mod.json**

Create `src/main/resources/fabric.mod.json`:

```json
{
  "schemaVersion": 1,
  "id": "smol-agent",
  "version": "${version}",
  "name": "Smol Agent",
  "description": "Cosmetically rewrites 'Agent'/'agent' substrings to 'Smol'/'smol' in tab, nametag, and chat. Client-side only.",
  "authors": ["paraf0x"],
  "license": "MIT",
  "environment": "client",
  "entrypoints": {
    "client": [
      "dev.smolagent.SmolAgentMod"
    ]
  },
  "mixins": [
    "smol-agent.mixins.json"
  ],
  "depends": {
    "fabricloader": ">=0.19.2",
    "fabric-api": "*",
    "java": ">=25",
    "minecraft": "~26.1.2"
  }
}
```

- [ ] **Step 3: Create mixins.json (empty mixin list — fills up in later tasks)**

Create `src/main/resources/smol-agent.mixins.json`:

```json
{
  "required": true,
  "package": "dev.smolagent.mixin",
  "compatibilityLevel": "JAVA_25",
  "client": [],
  "injectors": {
    "defaultRequire": 1
  }
}
```

- [ ] **Step 4: Verify it builds**

```bash
cd /Users/maksymvasyukov/git/sandbox/mods/smol-agent-mod
JAVA_HOME=$(brew --prefix openjdk)/libexec/openjdk.jdk/Contents/Home ./gradlew build --no-daemon
```

Expected: `BUILD SUCCESSFUL`. Jar produced under `build/libs/smol-agent-1.0.0.jar`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/smolagent/SmolAgentMod.java src/main/resources/
git commit -m "feat: mod entry point + Fabric metadata + empty mixin manifest"
```

---

## Task 4: `NameRewriter.rewrite(Component)` walker

**Files:**
- Modify: `src/main/java/dev/smolagent/NameRewriter.java`

This walker cannot be unit-tested without the Minecraft runtime (the `Component` API requires a registered text-serializer registry). Correctness is verified manually in Task 8.

- [ ] **Step 1: Add the Component walker**

Replace the contents of `src/main/java/dev/smolagent/NameRewriter.java` with:

```java
package dev.smolagent;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.LiteralContents;

public final class NameRewriter {

    private NameRewriter() {}

    public static String rewrite(String s) {
        if (s == null || s.isEmpty()) return s;
        // Order matters: capital first so "Agent" doesn't get mangled to "Smolent".
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
        if (contents instanceof LiteralContents literal) {
            rewritten = Component.literal(rewrite(literal.text()));
        } else {
            // Translatable / score / selector / keybind — leave content as-is.
            rewritten = in.copy().withStyle(s -> s); // shallow copy without siblings
            // Drop siblings of the copy; we re-add rewritten siblings below.
            rewritten.getSiblings().clear();
        }

        rewritten.setStyle(in.getStyle());

        for (Component sibling : in.getSiblings()) {
            rewritten.append(rewrite(sibling));
        }

        return rewritten;
    }
}
```

- [ ] **Step 2: Verify it compiles (and existing String tests still pass)**

```bash
cd /Users/maksymvasyukov/git/sandbox/mods/smol-agent-mod
JAVA_HOME=$(brew --prefix openjdk)/libexec/openjdk.jdk/Contents/Home ./gradlew build --no-daemon
```

Expected: `BUILD SUCCESSFUL`, the 8 `NameRewriterTest` tests still pass. If compilation of the Component walker fails because the actual 26.1.2 class names differ slightly from what is written (`LiteralContents`, `getContents`), generate Minecraft sources to verify:

```bash
JAVA_HOME=$(brew --prefix openjdk)/libexec/openjdk.jdk/Contents/Home ./gradlew genSources --no-daemon
```

Then inspect `~/.gradle/caches/fabric-loom/.../minecraft-merged-named-sources.jar` for the correct names of `Component`, `ComponentContents`, and `LiteralContents`. Update the imports and the `LiteralContents.text()` accessor name accordingly.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/smolagent/NameRewriter.java
git commit -m "feat: NameRewriter.rewrite(Component) walker preserving style and siblings"
```

---

## Task 5: `PlayerTabOverlayMixin` — rewrite tab-list names

**Files:**
- Create: `src/main/java/dev/smolagent/mixin/PlayerTabOverlayMixin.java`
- Modify: `src/main/resources/smol-agent.mixins.json`

- [ ] **Step 1: Identify the right injection point**

In 26.1.2, `PlayerTabOverlay.render` builds a per-row `Component` from `PlayerInfo.getTabListDisplayName()` (or falls back to a literal of the profile name). The simplest hook is on `PlayerInfo.getTabListDisplayName()` itself — rewriting its return value affects only the tab path.

However that method can return `null` (signal to use profile-name fallback). To cover BOTH paths cleanly, we instead `@ModifyVariable` the `Component name` local inside `PlayerTabOverlay.renderTablistScore`/`renderTablistPing`/row render — whichever method receives the final `Component`. The simplest single hook in current Minecraft snapshots is the `renderNames` (or similarly-named) method that takes a `Component` parameter and is called once per row.

Inspect the decompiled `PlayerTabOverlay.class` from `./gradlew genSources` and pick the method whose signature includes a `Component` carrying the row name. If unclear, default to `@ModifyVariable(method = "render", at = @At(value = "STORE"), name = "component")` on the row-Component local, falling back to `ordinal` if the local is unnamed.

- [ ] **Step 2: Write the Mixin**

Create `src/main/java/dev/smolagent/mixin/PlayerTabOverlayMixin.java`:

```java
package dev.smolagent.mixin;

import dev.smolagent.NameRewriter;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public class PlayerTabOverlayMixin {

    /**
     * PlayerTabOverlay.getNameForDisplay(PlayerInfo) returns the Component shown
     * for a row — either the tab-list display name set by the server, or a
     * fallback built from the player's profile name. Rewriting its return value
     * covers both paths with a single hook.
     *
     * If the exact method name in 26.1.2 differs (e.g. "getDisplayName" or
     * "getNameForRow"), inspect the genSources output for PlayerTabOverlay and
     * adjust the `method` selector below. The injection signature is otherwise
     * stable.
     */
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void smolagent$rewriteTabName(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        Component original = cir.getReturnValue();
        if (original == null) return;
        cir.setReturnValue(NameRewriter.rewrite(original));
    }
}
```

- [ ] **Step 3: Register the Mixin in smol-agent.mixins.json**

Replace the file with:

```json
{
  "required": true,
  "package": "dev.smolagent.mixin",
  "compatibilityLevel": "JAVA_25",
  "client": [
    "PlayerTabOverlayMixin"
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

Expected: `BUILD SUCCESSFUL`. If the build fails with `Cannot resolve method 'getNameForDisplay' in target class`, re-run `./gradlew genSources --no-daemon`, open the generated `PlayerTabOverlay.java`, identify the actual method that returns the per-row `Component`, and update the `method` selector. Common candidate names: `getNameForDisplay`, `getDisplayName`, `getNameComponent`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/smolagent/mixin/PlayerTabOverlayMixin.java src/main/resources/smol-agent.mixins.json
git commit -m "feat: PlayerTabOverlayMixin rewrites tab-list names"
```

---

## Task 6: `ChatComponentMixin` — rewrite incoming chat messages

**Files:**
- Create: `src/main/java/dev/smolagent/mixin/ChatComponentMixin.java`
- Modify: `src/main/resources/smol-agent.mixins.json`

- [ ] **Step 1: Write the Mixin**

Create `src/main/java/dev/smolagent/mixin/ChatComponentMixin.java`:

```java
package dev.smolagent.mixin;

import dev.smolagent.NameRewriter;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {

    /**
     * Rewrite the incoming chat Component before it is queued for display.
     * Targets the single-arg overload addMessage(Component). The other overload
     * addMessage(Component, MessageSignature, ChatType.Bound, GuiMessageTag)
     * is called BY this one, so hooking the entry point is enough.
     *
     * If 26.1.2 inlined the overload, switch the `method` selector to the
     * full-arg variant — the Component is always argv[0].
     */
    @ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component smolagent$rewriteChat(Component original) {
        if (original == null) return null;
        return NameRewriter.rewrite(original);
    }
}
```

- [ ] **Step 2: Add to mixins.json**

Replace `src/main/resources/smol-agent.mixins.json` with:

```json
{
  "required": true,
  "package": "dev.smolagent.mixin",
  "compatibilityLevel": "JAVA_25",
  "client": [
    "PlayerTabOverlayMixin",
    "ChatComponentMixin"
  ],
  "injectors": {
    "defaultRequire": 1
  }
}
```

- [ ] **Step 3: Build**

```bash
cd /Users/maksymvasyukov/git/sandbox/mods/smol-agent-mod
JAVA_HOME=$(brew --prefix openjdk)/libexec/openjdk.jdk/Contents/Home ./gradlew build --no-daemon
```

Expected: `BUILD SUCCESSFUL`. If the descriptor `(Lnet/minecraft/network/chat/Component;)V` doesn't match a method in 26.1.2's `ChatComponent`, inspect the genSources output and update the descriptor to match (e.g. the method may be named `enqueueMessage`).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/smolagent/mixin/ChatComponentMixin.java src/main/resources/smol-agent.mixins.json
git commit -m "feat: ChatComponentMixin rewrites incoming chat messages"
```

---

## Task 7: `EntityRendererMixin` — rewrite overhead nametag

**Files:**
- Create: `src/main/java/dev/smolagent/mixin/EntityRendererMixin.java`
- Modify: `src/main/resources/smol-agent.mixins.json`

- [ ] **Step 1: Write the Mixin**

Create `src/main/java/dev/smolagent/mixin/EntityRendererMixin.java`:

```java
package dev.smolagent.mixin;

import dev.smolagent.NameRewriter;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Rewrite the overhead nametag for ALL entities. In practice this fires only
 * for entities with a non-null name — for non-player entities the substring
 * "Agent"/"agent" is essentially never present, so this is effectively a
 * player-only hook in normal play.
 */
@Mixin(EntityRenderer.class)
public class EntityRendererMixin {

    @ModifyVariable(
        method = "renderNameTag",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private Component smolagent$rewriteNameTag(Component original) {
        if (original == null) return null;
        return NameRewriter.rewrite(original);
    }
}
```

- [ ] **Step 2: Add to mixins.json**

Replace `src/main/resources/smol-agent.mixins.json` with:

```json
{
  "required": true,
  "package": "dev.smolagent.mixin",
  "compatibilityLevel": "JAVA_25",
  "client": [
    "PlayerTabOverlayMixin",
    "ChatComponentMixin",
    "EntityRendererMixin"
  ],
  "injectors": {
    "defaultRequire": 1
  }
}
```

- [ ] **Step 3: Build**

```bash
cd /Users/maksymvasyukov/git/sandbox/mods/smol-agent-mod
JAVA_HOME=$(brew --prefix openjdk)/libexec/openjdk.jdk/Contents/Home ./gradlew build --no-daemon
```

Expected: `BUILD SUCCESSFUL`. If `renderNameTag` isn't found, inspect the genSources `EntityRenderer.java` for the method that takes a `Component` parameter and renders it above the entity. Candidate alternatives: `renderName`, `renderLabel`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/smolagent/mixin/EntityRendererMixin.java src/main/resources/smol-agent.mixins.json
git commit -m "feat: EntityRendererMixin rewrites overhead nametag"
```

---

## Task 8: Manual integration test + ship

**Files:**
- Create: `README.md`

- [ ] **Step 1: Final build to produce the shippable jar**

```bash
cd /Users/maksymvasyukov/git/sandbox/mods/smol-agent-mod
JAVA_HOME=$(brew --prefix openjdk)/libexec/openjdk.jdk/Contents/Home ./gradlew clean build --no-daemon
ls -lh build/libs/
```

Expected: `build/libs/smol-agent-1.0.0.jar` (and `-sources.jar`).

- [ ] **Step 2: Install jar into a Fabric 26.1.2 client and launch**

Copy the jar to your Fabric 26.1.2 instance's `mods/` folder. Required co-mods in the same folder: Fabric API 0.149.0+26.1.2. Launch the client.

Expected: client starts. Check `<instance>/logs/latest.log` for the line:

```
[Render thread/INFO] (Mixin) ... PlayerTabOverlayMixin applied
```

— and equivalent for the other two mixins. If any mixin fails to apply, the log shows a clear `MixinTargetAlreadyClassLoadedException` or `InvalidInjectionException` — fix per the troubleshooting notes inside that mixin's task.

- [ ] **Step 3: Run the manual test cases from the spec**

Join a server (or run a local LAN world with a second client) and verify the spec's six manual test cases:

1. Tab list: a player `AgentCool` shows as `SmolCool`; a player `BobThePlumber` is unchanged.
2. Nametag: a player `agentX` shows nametag `smolX`.
3. Chat: player `SomeAgent` chats; line shows sender `SomeSmol`. Content `"hello agent"` shows as `"hello smol"`.
4. Team prefix: with scoreboard team prefix `[T]` on player `Agent01`, nametag shows `[T] Smol01` (prefix preserved).
5. Build verification: jar drops in cleanly, client launches, no mixin errors in latest.log.
6. Negative test: an item named `Agent Sword` in a creative inventory is NOT rewritten (mod only hooks player-display surfaces).

If any test fails, debug per the troubleshooting notes in the relevant Mixin task, fix, rebuild, re-test.

- [ ] **Step 4: Write a short README**

Create `README.md`:

```markdown
# Smol Agent

A client-only Fabric mod for Minecraft 26.1.2 that cosmetically rewrites
`Agent` → `Smol` and `agent` → `smol` in the tab list, in overhead nametags,
and in chat messages.

Purely cosmetic. Only your own client sees the rewritten names — the server,
other players, and gameplay are unaffected.

## Install

Drop `smol-agent-1.0.0.jar` into your Fabric 26.1.2 client's `mods/` folder
alongside Fabric API.

## Build

```sh
JAVA_HOME=$(brew --prefix openjdk)/libexec/openjdk.jdk/Contents/Home \
  ./gradlew build
```

Output jar is in `build/libs/`.

## License

MIT — see `LICENSE`.
```

- [ ] **Step 5: Commit**

```bash
git add README.md
git commit -m "docs: add README"
```

- [ ] **Step 6: Tag v1.0.0**

```bash
git tag -a v1.0.0 -m "Smol Agent v1.0.0 — initial release"
git log --oneline
```

Expected: shows the 8 commits plus the tag.

---

## Notes for the implementer

- **No yarn:** Minecraft 26.x ships unobfuscated. Every class reference uses the Mojang-style names (`net.minecraft.network.chat.Component`, `net.minecraft.client.gui.components.PlayerTabOverlay`, etc.). If you have prior Fabric experience using yarn names, ignore that habit — the names in this codebase are the Mojang names directly.
- **`genSources`:** When a Mixin `method` selector doesn't resolve, run `./gradlew genSources` and inspect the actual Minecraft source for the target class. The names given in this plan are best-effort based on 26.1.2 conventions and may need small adjustments.
- **Mixin failures are loud:** A mixin that fails to apply prints a clear `MixinTargetClassMismatch` or `InvalidInjectionException` at client startup. Don't ignore mixin warnings — they mean the injection didn't happen and the feature won't work in-game.
- **Don't chase abstractions:** This mod is intentionally five small classes. Resist the urge to add a config system, a keybind, or a runtime toggle. Those are noted as out-of-scope in the spec.
