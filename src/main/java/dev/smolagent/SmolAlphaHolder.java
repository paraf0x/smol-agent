package dev.smolagent;

/** Added to AvatarRenderState by mixin: the alpha computed for that player this frame (1 = opaque). */
public interface SmolAlphaHolder {
    float smolagent$getAlpha();

    void smolagent$setAlpha(float alpha);
}
