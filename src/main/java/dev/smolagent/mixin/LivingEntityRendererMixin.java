package dev.smolagent.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.smolagent.SmolAlphaHolder;
import dev.smolagent.alpha.AlphaSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;

/**
 * For players whose {@link SmolAlphaHolder} alpha is below 1 (set by {@link AvatarRendererMixin}):
 * multiplies the model tint alpha via {@link AlphaSubmitNodeCollector} and swaps the cutout
 * RenderType for {@link RenderTypes#entityTranslucent} so the alpha is actually blended.
 */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    @WrapMethod(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V")
    private void smolagent$applyAlpha(
            LivingEntityRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState camera,
            Operation<Void> original) {
        float alpha = alphaOf(state);
        SubmitNodeCollector effective = alpha < 1f
                ? new AlphaSubmitNodeCollector(submitNodeCollector, alpha)
                : submitNodeCollector;
        original.call(state, poseStack, effective, camera);
    }

    @WrapMethod(method = "getRenderType")
    @SuppressWarnings({"rawtypes", "unchecked"})
    private RenderType smolagent$translucent(
            LivingEntityRenderState state,
            boolean visible,
            boolean shouldShowBody,
            boolean glowing,
            Operation<RenderType> original) {
        if (visible && alphaOf(state) < 1f) {
            Identifier texture = ((LivingEntityRenderer) (Object) this).getTextureLocation(state);
            if (texture != null) {
                return RenderTypes.entityTranslucent(texture);
            }
        }
        return original.call(state, visible, shouldShowBody, glowing);
    }

    private static float alphaOf(LivingEntityRenderState state) {
        return state instanceof SmolAlphaHolder holder ? holder.smolagent$getAlpha() : 1f;
    }
}
