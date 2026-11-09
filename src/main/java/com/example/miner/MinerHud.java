package com.example.miner;

import java.util.List;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * \u041f\u0430\u043d\u0435\u043b\u044c \u0441\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043a\u0438 \u043d\u0430 \u044d\u043a\u0440\u0430\u043d\u0435 (\u0441\u043b\u0435\u0432\u0430 \u043f\u043e \u0446\u0435\u043d\u0442\u0440\u0443). \u0412\u043a\u043b/\u0432\u044b\u043a\u043b: .miner hud
 *
 * \u0415\u0441\u043b\u0438 \u0438\u0437-\u0437\u0430 \u044d\u0442\u043e\u0433\u043e \u0444\u0430\u0439\u043b\u0430 \u043d\u0435 \u0441\u043e\u0431\u0438\u0440\u0430\u0435\u0442\u0441\u044f \u043f\u0440\u043e\u0435\u043a\u0442 \u2014 \u0443\u0434\u0430\u043b\u0438\u0442\u0435 MinerHud.java
 * \u0438 \u0441\u0442\u0440\u043e\u043a\u0443 MinerHud.register(); \u0432 AutoMinerMod.java. \u041e\u0441\u0442\u0430\u043b\u044c\u043d\u043e\u0435 \u0431\u0443\u0434\u0435\u0442 \u0440\u0430\u0431\u043e\u0442\u0430\u0442\u044c,
 * \u0441\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043a\u0430 \u043e\u0441\u0442\u0430\u043d\u0435\u0442\u0441\u044f \u0434\u043e\u0441\u0442\u0443\u043f\u043d\u043e\u0439 \u043a\u043e\u043c\u0430\u043d\u0434\u043e\u0439 .miner stats.
 */
public final class MinerHud {
    private MinerHud() {
    }

    public static void register() {
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("autominer", "stats"),
                MinerHud::extract);
    }

    private static void extract(GuiGraphicsExtractor graphics, DeltaTracker tickCounter) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !AutoMinerLogic.isHudVisible()) return;

        List<String> lines = AutoMinerLogic.hudLines(mc);
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, mc.font.width(line));
        }

        int pad = 4;
        int lineHeight = 10;
        int boxW = width + pad * 2;
        int boxH = lines.size() * lineHeight + pad * 2;
        int x = 6;
        int y = (mc.getWindow().getGuiScaledHeight() - boxH) / 2;

        graphics.fill(x, y, x + boxW, y + boxH, 0x90000000);
        for (int i = 0; i < lines.size(); i++) {
            graphics.text(mc.font, lines.get(i), x + pad, y + pad + i * lineHeight, 0xFFFFFFFF, true);
        }
    }
}
