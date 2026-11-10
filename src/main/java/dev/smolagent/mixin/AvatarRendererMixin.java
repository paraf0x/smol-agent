package dev.smolagent.mixin;

import dev.smolagent.NameRules;
import dev.smolagent.SmolAlphaHolder;
import dev.smolagent.SmolConfig;
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

import java.util.List;

/**
 * Decides per frame whether a player is drawn translucent. Matches the configured name parts
 * against the nickname the server shows in the tab list (as sent, before rename rules).
 * Players without a server nickname are matched by their account name instead.
 */
@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {

    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
        at = @At("TAIL")
    )
    private void smolagent$computeAlpha(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        SmolConfig config = SmolConfig.get();
        float alpha = 1f;
        if (config.opacity < SmolConfig.MAX_OPACITY && !config.translucent.isEmpty()
                && NameRules.matchesAny(namesOf(avatar), config.translucent)) {
            alpha = config.alpha();
        }
        ((SmolAlphaHolder) state).smolagent$setAlpha(alpha);
    }

    /** The server nickname if there is one; only without a nickname the account name counts. */
    private static List<String> namesOf(Avatar avatar) {
        Component nick = tabListName(avatar);
        return List.of(nick != null ? nick.getString() : avatar.getName().getString());
    }

    private static Component tabListName(Avatar avatar) {
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if (conn == null) return null;
        PlayerInfo info = conn.getPlayerInfo(avatar.getUUID());
        return info == null ? null : info.getTabListDisplayName();
    }
}
