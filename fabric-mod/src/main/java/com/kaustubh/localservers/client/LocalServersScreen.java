package com.kaustubh.localservers.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class LocalServersScreen extends Screen {
    private final Screen parent;
    private volatile String status = "Ready";
    private TextFieldWidget serverId;
    private TextFieldWidget ramMb;
    private TextFieldWidget cpuCores;
    private TextFieldWidget serverPort;
    private TextFieldWidget projectSlug;
    private TextFieldWidget relayHost;
    private TextFieldWidget publicPort;
    private TextFieldWidget controlPort;
    private TextFieldWidget dataPort;
    private TextFieldWidget relayToken;

    LocalServersScreen(Screen parent) {
        super(Text.literal("Local Servers"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = width / 2 - 150;
        int y = 54;
        serverId = field(x, y, 90, "main");
        ramMb = field(x + 100, y, 60, "4096");
        cpuCores = field(x + 170, y, 50, "2");
        serverPort = field(x + 230, y, 70, "25565");
        projectSlug = field(x, y + 34, 145, "chunky");
        relayHost = field(x + 155, y + 34, 145, "127.0.0.1");
        publicPort = field(x, y + 68, 90, "25577");
        controlPort = field(x + 105, y + 68, 90, "45640");
        dataPort = field(x + 210, y + 68, 90, "45641");
        relayToken = field(x, y + 102, 300, "");

        addDrawableChild(ButtonWidget.builder(Text.literal("Quick Start Local Server"), button -> {
            call(() -> {
                status = "Creating server...";
                ManagerClient.post("/servers/create", "{\"id\":\"" + q(serverId.getText()) + "\",\"minecraftVersion\":\"1.21.11\",\"ramMb\":"
                        + ramMb.getText() + ",\"cpuCores\":" + cpuCores.getText() + ",\"serverPort\":" + serverPort.getText() + "}");
                status = "Installing " + projectSlug.getText() + "...";
                ManagerClient.post("/servers/" + q(serverId.getText()) + "/modrinth/install", "{\"project\":\"" + q(projectSlug.getText()) + "\",\"loader\":\"paper\"}");
                status = "Starting server...";
                return ManagerClient.post("/servers/" + q(serverId.getText()) + "/start", "{}");
            });
        }).dimensions(x, y + 136, 300, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Create Server"), button -> {
            call(() -> ManagerClient.post("/servers/create", "{\"id\":\"" + q(serverId.getText()) + "\",\"minecraftVersion\":\"1.21.11\",\"ramMb\":"
                    + ramMb.getText() + ",\"cpuCores\":" + cpuCores.getText() + ",\"serverPort\":" + serverPort.getText() + "}"));
        }).dimensions(x, y + 162, 145, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Start"), button -> {
            call(() -> ManagerClient.post("/servers/" + q(serverId.getText()) + "/start", "{}"));
        }).dimensions(x + 155, y + 162, 70, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Stop"), button -> {
            call(() -> ManagerClient.post("/servers/" + q(serverId.getText()) + "/stop", "{}"));
        }).dimensions(x + 230, y + 162, 70, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Refresh"), button -> refresh())
                .dimensions(x, y + 188, 95, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Install Plugin"), button -> {
            call(() -> ManagerClient.post("/servers/" + q(serverId.getText()) + "/modrinth/install", "{\"project\":\"" + q(projectSlug.getText()) + "\",\"loader\":\"paper\"}"));
        }).dimensions(x + 105, y + 188, 95, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Start Tunnel"), button -> {
            call(() -> ManagerClient.post("/servers/" + q(serverId.getText()) + "/tunnel/start", "{\"relayHost\":\"" + q(relayHost.getText())
                    + "\",\"controlPort\":" + controlPort.getText() + ",\"dataPort\":" + dataPort.getText()
                    + ",\"publicPort\":" + publicPort.getText() + ",\"localPort\":" + serverPort.getText()
                    + ",\"relayToken\":\"" + q(relayToken.getText()) + "\"}"));
        }).dimensions(x + 205, y + 188, 95, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Stop Tunnel"), button -> {
            call(() -> ManagerClient.post("/servers/" + q(serverId.getText()) + "/tunnel/stop", "{}"));
        }).dimensions(x, y + 214, 145, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> close())
                .dimensions(x + 155, y + 214, 145, 20).build());
        refresh();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int x = width / 2 - 150;
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);
        label(context, "Server", x, 42);
        label(context, "RAM MB", x + 100, 42);
        label(context, "CPU", x + 170, 42);
        label(context, "MC Port", x + 230, 42);
        label(context, "Modrinth plugin slug", x, 76);
        label(context, "Relay host/IP", x + 155, 76);
        label(context, "Player port", x, 110);
        label(context, "Control", x + 105, 110);
        label(context, "Data", x + 210, 110);
        label(context, "Relay token", x, 144);
        context.fill(x - 2, height - 48, x + 302, height - 12, 0xAA000000);
        context.drawTextWithShadow(textRenderer, Text.literal("Status:"), x + 6, height - 42, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.literal(shortStatus()), x + 6, height - 28, 0xA0FFA0);
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    private void refresh() {
        call(() -> ManagerClient.get("/servers"));
    }

    private TextFieldWidget field(int x, int y, int width, String value) {
        TextFieldWidget field = new TextFieldWidget(textRenderer, x, y, width, 18, Text.empty());
        field.setText(value);
        addDrawableChild(field);
        return field;
    }

    private void label(DrawContext context, String text, int x, int y) {
        context.drawTextWithShadow(textRenderer, Text.literal(text), x, y, 0xD0D0D0);
    }

    private String q(String value) {
        return value.replace("\\", "\\\\").replace("\"", "");
    }

    private void call(ThrowingSupplier supplier) {
        status = "Working...";
        new Thread(() -> {
            try {
                status = supplier.get();
            } catch (Exception ex) {
                status = "Error: " + ex.getMessage();
            }
        }, "localservers-ui").start();
    }

    private String shortStatus() {
        if (status == null) {
            return "";
        }
        return status.length() > 92 ? status.substring(0, 92) : status;
    }

    private interface ThrowingSupplier {
        String get() throws Exception;
    }
}
