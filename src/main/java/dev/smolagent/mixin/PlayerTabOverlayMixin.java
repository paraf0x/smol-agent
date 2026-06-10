package dev.smolagent.mixin;

import dev.smolagent.NameRewriter;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public class PlayerTabOverlayMixin {

    /**
     * PlayerTabOverlay.getNameForDisplay(PlayerInfo) returns the Component shown
     * for a row — either the tab-list display name set by the server, or a
     * fallback built from the player's profile name. Rewriting its return value
     * covers both paths with a single hook.
     */
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void smolagent$rewriteTabName(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        Component original = cir.getReturnValue();
        if (original == null) return;
        cir.setReturnValue(NameRewriter.rewrite(original));
    }
}
