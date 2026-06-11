package dev.smolagent.alpha;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;

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
