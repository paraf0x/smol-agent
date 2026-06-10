package dev.smolagent.mixin;

import dev.smolagent.NameRewriter;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Rewrite the overhead nametag for ALL entities. In practice this fires only
 * for entities with a non-null name — for non-player entities the substring
 * "Agent"/"agent" is essentially never present, so this is effectively a
 * player-only hook in normal play.
 *
 * MC 26.1.2 does not have renderNameTag. The nametag Component is produced by
 * getNameTag(T entity), which is called from extractRenderState and whose
 * return value is stored into EntityRenderState.nameTag before rendering.
 * Hooking getNameTag at RETURN rewrites the Component for all entity types.
 *
 * Descriptor used (adjusted from plan):
 *   getNameTag(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/network/chat/Component;
 */
@Mixin(EntityRenderer.class)
public class EntityRendererMixin {

    @Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true)
    private void smolagent$rewriteNameTag(Object entity, CallbackInfoReturnable<Component> cir) {
        Component original = cir.getReturnValue();
        if (original == null) return;
        cir.setReturnValue(NameRewriter.rewrite(original));
    }
}
