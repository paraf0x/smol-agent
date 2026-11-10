package dev.smolagent.mixin;

import dev.smolagent.SmolAlphaHolder;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public class AvatarRenderStateMixin implements SmolAlphaHolder {

    @Unique
    private float smolagent$alpha = 1f;

    @Override
    public float smolagent$getAlpha() {
        return smolagent$alpha;
    }

    @Override
    public void smolagent$setAlpha(float alpha) {
        smolagent$alpha = alpha;
    }
}
