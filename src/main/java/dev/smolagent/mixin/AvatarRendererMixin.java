package dev.smolagent.mixin;

import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forces {@code AvatarRenderState.nameTag} to be populated for every player,
 * including the local one. Vanilla leaves {@code nameTag} null for the local
 * player (since their tag is never displayed above their own head), which made
 * {@link LivingEntityRendererMixin}'s detection skip the local player when in
 * third-person view.
 *
 * <p>Setting {@code nameTag} does NOT make vanilla display the tag — display
 * is gated separately by {@code shouldShowName(...)}. The field is only read
 * by smol-agent's detection here.
 */
@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {

    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
        at = @At("TAIL")
    )
    private void smolagent$forcePopulateNameTag(
            Avatar avatar,
            AvatarRenderState state,
            float partialTick,
            CallbackInfo ci) {
        if (state.nameTag == null) {
            state.nameTag = avatar.getName();
        }
    }
}
