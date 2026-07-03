package com.kaustubh.localservers.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class LocalServersClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ManagerClient.ensureRunning();
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof TitleScreen) {
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("Local Servers"), button -> {
                    ManagerClient.ensureRunning();
                    client.setScreen(new LocalServersScreen(screen));
                }).dimensions(width / 2 - 100, height / 4 + 120, 200, 20).build());
            }
        });
    }
}
