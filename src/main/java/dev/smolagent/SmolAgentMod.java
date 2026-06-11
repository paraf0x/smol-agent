package dev.smolagent;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmolAgentMod implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("smol-agent");

    @Override
    public void onInitializeClient() {
        LOG.info("smol-agent client initialized");
    }
}
