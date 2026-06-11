package dev.smolagent.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.smolagent.AgentDetector;
import dev.smolagent.alpha.AlphaSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Wraps two methods on {@link LivingEntityRenderer}:
 *
 * <ul>
 *   <li>{@code submit} — replaces the {@link SubmitNodeCollector} with an
 *       {@link AlphaSubmitNodeCollector} for agent players, multiplying the
 *       tintedColor alpha by 0.3 on every submitModel / submitModelPart call.
 *   <li>{@code getRenderType} — swaps the cutout (opaque, blend-off) RenderType
 *       for {@link RenderTypes#entityTranslucent} (blend-on) so the reduced
 *       alpha actually shows up on screen.
 * </ul>
 *
 * <p>Detection reads {@code state.nameTag}, which is populated by
 * {@link AvatarRendererMixin#smolagent$populateNameTagWithServerNickname}
 * for the local player (otherwise vanilla leaves it null in third-person view)
 * or by vanilla for other players. {@code AgentDetector} matches both
 * {@code Agent}/{@code agent} (pre-rename) and {@code Smol}/{@code smol}
 * (post-rename).
 */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    private static final float AGENT_ALPHA = 0.3f;

    @WrapMethod(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V")
    private void smolagent$applyAgentAlpha(
            LivingEntityRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState camera,
            Operation<Void> original) {
        SubmitNodeCollector effective = submitNodeCollector;
        if (state != null && AgentDetector.isAgent(extractName(state))) {
            effective = new AlphaSubmitNodeCollector(submitNodeCollector, AGENT_ALPHA);
        }
        original.call(state, poseStack, effective, camera);
    }

    @WrapMethod(method = "getRenderType")
    @SuppressWarnings({"rawtypes", "unchecked"})
    private RenderType smolagent$translucentForAgent(
            LivingEntityRenderState state,
            boolean visible,
            boolean shouldShowBody,
            boolean glowing,
            Operation<RenderType> original) {
        if (visible && state != null && AgentDetector.isAgent(extractName(state))) {
            Identifier texture = ((LivingEntityRenderer) (Object) this).getTextureLocation(state);
            if (texture != null) {
                return RenderTypes.entityTranslucent(texture);
            }
        }
        return original.call(state, visible, shouldShowBody, glowing);
    }

    private static String extractName(LivingEntityRenderState state) {
        Component nameTag = state.nameTag;
        return nameTag != null ? nameTag.getString() : null;
    }
}
