package dev.smolagent.mixin;

import dev.smolagent.NameRewriter;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {

    /**
     * Rewrite the incoming chat Component before it is queued for display.
     *
     * In MC 26.1.2, the single-arg addMessage(Component) overload from earlier
     * snapshots does not exist. All public entry points converge on the private
     * addMessage(Component, MessageSignature, GuiMessageSource, GuiMessageTag).
     * Hooking that method with ordinal=0 captures the Component (argv[0]) for
     * all chat types: system, server, and player messages.
     *
     * Descriptor used (adjusted from plan):
     *   (Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;
     *    Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;
     *    Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V
     */
    @ModifyVariable(
        method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private Component smolagent$rewriteChat(Component original) {
        if (original == null) return null;
        return NameRewriter.rewrite(original);
    }
}
