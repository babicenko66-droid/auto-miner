package com.example.miner;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;

public class AutoMinerMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(AutoMinerLogic::tick);

        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            String text = message.trim();
            if (text.equalsIgnoreCase(".miner") || text.toLowerCase().startsWith(".miner ")) {
                String arg = text.length() > 6 ? text.substring(6).trim() : "";
                AutoMinerLogic.command(Minecraft.getInstance(), arg);
                return false;
            }
            return true;
        });

        MinerHud.register();
    }
}
