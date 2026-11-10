package dev.smolagent;

import dev.smolagent.ui.SmolScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmolAgentMod implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("smol-agent");

    private static boolean openMenuNextTick;

    @Override
    public void onInitializeClient() {
        SmolConfig.get();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
                dispatcher.register(ClientCommands.literal("smolpp").executes(ctx -> {
                    // Runs inside ChatScreen's Enter handling, which closes the screen afterwards;
                    // open the menu on the next tick instead.
                    openMenuNextTick = true;
                    return 1;
                })));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (openMenuNextTick) {
                openMenuNextTick = false;
                mc.gui.setScreen(new SmolScreen());
            }
        });
        LOG.info("smol-agent client initialized");
    }
}
