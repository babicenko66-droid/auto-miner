package com.example.miner;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;

public class AutoMinerMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(AutoMinerLogic::tick);

        // Включение/выключение: напишите в чат .miner (сообщение на сервер не уходит)
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            if (message.trim().equalsIgnoreCase(".miner")) {
                AutoMinerLogic.toggle(Minecraft.getInstance());
                return false;
            }
            return true;
        });
    }
}
