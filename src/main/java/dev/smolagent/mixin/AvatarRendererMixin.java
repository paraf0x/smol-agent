package dev.smolagent.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Populates {@code AvatarRenderState.nameTag} for the local player so that
 * smol-agent's detection has a name to match against. Vanilla leaves
 * {@code nameTag} null for the local player (their own nametag is never
 * displayed above their own head).
 *
 * <p>Crucially, we prefer the server-side display name from
 * {@link PlayerInfo#getTabListDisplayName()} over the raw Mojang username
 * from {@link Avatar#getName()}. On servers that use nickname plugins (which
 * set the displayed nickname via {@code UPDATE_DISPLAY_NAME} packets), the
 * Mojang name does not contain the agent-relevant substring — only the
 * server-set nickname does. Without this PlayerInfo lookup, a user whose
 * Mojang name is e.g. "UnicornBeef" but whose server nickname is "brsmolity"
 * would not be detected as an agent.
 *
 * <p>Setting {@code nameTag} does NOT make vanilla display the local player's
 * tag — display is gated separately by {@code shouldShowName(...)}. The field
 * is only read by smol-agent's detection.
 */
@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {

    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
        at = @At("TAIL")
    )
    private void smolagent$populateNameTagWithServerNickname(
            Avatar avatar,
            AvatarRenderState state,
            float partialTick,
            CallbackInfo ci) {
        if (state.nameTag != null) {
            // Vanilla already set the nametag (other-player case). Leave it —
            // it will have been rewritten by the rename mixin (Agent → Smol),
            // and AgentDetector recognises both forms.
            return;
        }

        // Vanilla left nameTag null (local-player case). Prefer the server-side
        // nickname over the raw Mojang username.
        Component nickname = resolveServerNickname(avatar);
        state.nameTag = nickname != null ? nickname : avatar.getName();
    }

    private static Component resolveServerNickname(Avatar avatar) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return null;
        ClientPacketListener conn = mc.getConnection();
        if (conn == null) return null;
        PlayerInfo info = conn.getPlayerInfo(avatar.getUUID());
        if (info == null) return null;
        return info.getTabListDisplayName();
    }
}
