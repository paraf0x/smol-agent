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
 * Wraps LivingEntityRenderer.submit so that whenever the rendered entity's
 * displayed name contains "Agent" or "agent", every sub-render that runs
 * during this call (skin, cape, armor feature renderers, glints, nametag)
 * receives a SubmitNodeCollector whose tintedColor alpha is multiplied by 0.3.
 *
 * <p>MC 26.1.2 uses submit(state, poseStack, SubmitNodeCollector, CameraRenderState)
 * instead of the older render(state, poseStack, MultiBufferSource, packedLight).
 * The alpha effect is achieved by wrapping the SubmitNodeCollector with
 * {@link AlphaSubmitNodeCollector}, which multiplies the tintedColor ARGB alpha
 * on every submitModel and submitModelPart call.
 *
 * <p>The name is read from {@code EntityRenderState.nameTag}, which is the
 * public Component field set by extractRenderState before submit is called.
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

    /**
     * Swap the player's main-body RenderType from cutout (opaque pipeline,
     * GL blending disabled) to entityTranslucent (translucent pipeline,
     * blending enabled) for agent players. Without this swap, the alpha byte
     * written by {@link AlphaSubmitNodeCollector} is silently ignored — the
     * body skin renders fully opaque even with alpha=77/255.
     *
     * <p>Only the BASE MODEL surface goes through this hook. Armor and cape
     * feature renderers use their own RenderType-picking code paths and are
     * unaffected; if those need transparency too, they will require their own
     * mixins.
     */
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

    /**
     * Extracts the entity's displayed name as a plain String.
     * In MC 26.1.2, EntityRenderState.nameTag is a public Component field
     * populated by EntityRenderer.extractRenderState before submit is called.
     */
    private static String extractName(LivingEntityRenderState state) {
        Component nameTag = state.nameTag;
        return nameTag != null ? nameTag.getString() : null;
    }
}
