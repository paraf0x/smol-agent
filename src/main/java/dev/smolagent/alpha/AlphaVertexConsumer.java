package dev.smolagent.alpha;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Delegating VertexConsumer that multiplies the alpha component of every
 * {@code setColor} call by a constant factor in (0.0, 1.0]. Used to render
 * an entity (and everything that goes through its MultiBufferSource — armor,
 * cape, glints, nametag) at reduced opacity.
 */
public class AlphaVertexConsumer implements VertexConsumer {

    private final VertexConsumer delegate;
    private final float alpha;

    public AlphaVertexConsumer(VertexConsumer delegate, float alpha) {
        this.delegate = delegate;
        this.alpha = alpha;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        int scaled = Math.round(a * alpha);
        if (scaled < 0) scaled = 0;
        if (scaled > 255) scaled = 255;
        delegate.setColor(r, g, b, scaled);
        return this;
    }

    @Override
    public VertexConsumer setColor(int rgba) {
        int a = (rgba >>> 24) & 0xFF;
        int rgb = rgba & 0x00FFFFFF;
        int scaled = Math.round(a * alpha);
        if (scaled < 0) scaled = 0;
        if (scaled > 255) scaled = 255;
        delegate.setColor((scaled << 24) | rgb);
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        delegate.setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        delegate.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        delegate.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        delegate.setNormal(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(float lineWidth) {
        delegate.setLineWidth(lineWidth);
        return this;
    }
}
