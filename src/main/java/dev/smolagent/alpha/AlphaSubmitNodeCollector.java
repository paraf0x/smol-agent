package dev.smolagent.alpha;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * SubmitNodeCollector wrapper that multiplies the alpha of every
 * {@code tintedColor} passed to {@link #submitModel} and
 * {@link #submitCrumblingOverlay} by a constant factor. All other methods
 * delegate unchanged to the wrapped collector.
 *
 * <p>MC 26.3: the {@code submitModelPart} overloads became interface defaults
 * that funnel into the abstract {@code submitModel}, so tinting that one method
 * covers the model-part path too. Sprites are {@code UvMapping} now, and the
 * crumbling overlay moved out of {@code submitModel} into its own method.
 *
 * <p>Used by {@link dev.smolagent.mixin.LivingEntityRendererMixin} to render
 * agent players at 30% opacity.
 */
public class AlphaSubmitNodeCollector implements SubmitNodeCollector {

    private final SubmitNodeCollector delegate;
    private final float alpha;

    public AlphaSubmitNodeCollector(SubmitNodeCollector delegate, float alpha) {
        this.delegate = delegate;
        this.alpha = alpha;
    }

    // -----------------------------------------------------------------------
    // Alpha-modifying overrides
    // -----------------------------------------------------------------------

    @Override
    public <S> void submitModel(
            Model<? super S> model,
            S state,
            PoseStack poseStack,
            RenderType renderType,
            int lightCoords,
            int overlayCoords,
            int tintedColor,
            @Nullable UvMapping uvMapping,
            int outlineColor) {
        delegate.submitModel(model, state, poseStack, renderType, lightCoords, overlayCoords,
                ARGB.multiplyAlpha(tintedColor, alpha), uvMapping, outlineColor);
    }

    @Override
    public <S> void submitCrumblingOverlay(
            Model<? super S> model,
            S state,
            PoseStack poseStack,
            RenderType renderType,
            int lightCoords,
            int overlayCoords,
            int tintedColor,
            ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        delegate.submitCrumblingOverlay(model, state, poseStack, renderType, lightCoords, overlayCoords,
                ARGB.multiplyAlpha(tintedColor, alpha), crumblingOverlay);
    }

    // -----------------------------------------------------------------------
    // Pass-through delegates
    // -----------------------------------------------------------------------

    @Override
    public OrderedSubmitNodeCollector order(int order) {
        return delegate.order(order);
    }

    @Override
    public void submitShadow(PoseStack poseStack, float radius, List<EntityRenderState.ShadowPiece> pieces) {
        delegate.submitShadow(poseStack, radius, pieces);
    }

    @Override
    public void submitNameTag(
            PoseStack poseStack,
            @Nullable Vec3 nameTagAttachment,
            int offset,
            Component name,
            boolean seeThrough,
            int lightCoords,
            CameraRenderState camera) {
        delegate.submitNameTag(poseStack, nameTagAttachment, offset, name, seeThrough, lightCoords, camera);
    }

    @Override
    public void submitText(
            PoseStack poseStack,
            float x,
            float y,
            FormattedCharSequence string,
            boolean dropShadow,
            Font.DisplayMode displayMode,
            int lightCoords,
            int color,
            int backgroundColor,
            int outlineColor) {
        delegate.submitText(poseStack, x, y, string, dropShadow, displayMode, lightCoords, color, backgroundColor, outlineColor);
    }

    @Override
    public void submitTextBackground(
            PoseStack poseStack,
            float minX,
            float minY,
            float maxX,
            float maxY,
            int lightCoords,
            Font.DisplayMode displayMode,
            int color) {
        delegate.submitTextBackground(poseStack, minX, minY, maxX, maxY, lightCoords, displayMode, color);
    }

    @Override
    public void submitFlame(PoseStack poseStack, EntityRenderState renderState, Quaternionf rotation) {
        delegate.submitFlame(poseStack, renderState, rotation);
    }

    @Override
    public void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leashState) {
        delegate.submitLeash(poseStack, leashState);
    }

    @Override
    public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState movingBlockRenderState, int outlineColor) {
        delegate.submitMovingBlock(poseStack, movingBlockRenderState, outlineColor);
    }

    @Override
    public void submitBlockModel(
            PoseStack poseStack,
            RenderType renderType,
            List<BlockStateModelPart> parts,
            int[] tintLayers,
            int lightCoords,
            int overlayCoords,
            int outlineColor) {
        delegate.submitBlockModel(poseStack, renderType, parts, tintLayers, lightCoords, overlayCoords, outlineColor);
    }

    @Override
    public void submitBreakingBlockModel(
            PoseStack poseStack,
            List<BlockStateModelPart> parts,
            int progress,
            boolean shaded) {
        delegate.submitBreakingBlockModel(poseStack, parts, progress, shaded);
    }

    @Override
    public void submitShapeOutline(
            PoseStack poseStack,
            VoxelShape shape,
            RenderType renderType,
            int color,
            float lineWidth,
            boolean depthTest) {
        delegate.submitShapeOutline(poseStack, shape, renderType, color, lineWidth, depthTest);
    }

    @Override
    public void submitItem(
            PoseStack poseStack,
            ItemDisplayContext displayContext,
            int lightCoords,
            int overlayCoords,
            int outlineColor,
            int[] tintLayers,
            ItemQuads quads,
            ItemStackRenderState.FoilType foilType) {
        delegate.submitItem(poseStack, displayContext, lightCoords, overlayCoords, outlineColor, tintLayers, quads, foilType);
    }

    @Override
    public void submitCustomGeometry(
            PoseStack poseStack,
            RenderType renderType,
            SubmitNodeCollector.CustomGeometryRenderer customGeometryRenderer) {
        delegate.submitCustomGeometry(poseStack, renderType, customGeometryRenderer);
    }

    @Override
    public void submitQuadParticleGroup(QuadParticleRenderState particleRenderState) {
        delegate.submitQuadParticleGroup(particleRenderState);
    }

    @Override
    public void submitGizmoPrimitives(
            DrawableGizmoPrimitives.Group group,
            CameraRenderState camera,
            boolean depthTest) {
        delegate.submitGizmoPrimitives(group, camera, depthTest);
    }
}
