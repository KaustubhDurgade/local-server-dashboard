package com.kaustubh.localservers.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class LocalServersScreen extends Screen {
    private final Screen parent;
    private String status = "Loading...";
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
        int y = 42;
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

        addDrawableChild(ButtonWidget.builder(Text.literal("Create Paper Server"), button -> {
            call(() -> ManagerClient.post("/servers/create", "{\"id\":\"" + q(serverId.getText()) + "\",\"minecraftVersion\":\"1.21.11\",\"ramMb\":"
                    + ramMb.getText() + ",\"cpuCores\":" + cpuCores.getText() + ",\"serverPort\":" + serverPort.getText() + "}"));
        }).dimensions(x, y + 136, 145, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Start"), button -> {
            call(() -> ManagerClient.post("/servers/" + q(serverId.getText()) + "/start", "{}"));
        }).dimensions(x + 155, y + 136, 70, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Stop"), button -> {
            call(() -> ManagerClient.post("/servers/" + q(serverId.getText()) + "/stop", "{}"));
        }).dimensions(x + 230, y + 136, 70, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Refresh"), button -> refresh())
                .dimensions(x, y + 162, 95, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Install Plugin"), button -> {
            call(() -> ManagerClient.post("/servers/" + q(serverId.getText()) + "/modrinth/install", "{\"project\":\"" + q(projectSlug.getText()) + "\",\"loader\":\"paper\"}"));
        }).dimensions(x + 105, y + 162, 95, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Start Tunnel"), button -> {
            call(() -> ManagerClient.post("/servers/" + q(serverId.getText()) + "/tunnel/start", "{\"relayHost\":\"" + q(relayHost.getText())
                    + "\",\"controlPort\":" + controlPort.getText() + ",\"dataPort\":" + dataPort.getText()
                    + ",\"publicPort\":" + publicPort.getText() + ",\"localPort\":" + serverPort.getText()
                    + ",\"relayToken\":\"" + q(relayToken.getText()) + "\"}"));
        }).dimensions(x + 205, y + 162, 95, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Stop Tunnel"), button -> {
            call(() -> ManagerClient.post("/servers/" + q(serverId.getText()) + "/tunnel/stop", "{}"));
        }).dimensions(x, y + 188, 145, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> close())
                .dimensions(x + 155, y + 188, 145, 20).build());
        refresh();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int x = width / 2 - 150;
        label(context, "Server", x, 30);
        label(context, "RAM", x + 100, 30);
        label(context, "CPU", x + 170, 30);
        label(context, "Port", x + 230, 30);
        label(context, "Modrinth slug", x, 64);
        label(context, "Relay host", x + 155, 64);
        label(context, "Public", x, 98);
        label(context, "Control", x + 105, 98);
        label(context, "Data", x + 210, 98);
        label(context, "Relay token", x, 132);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(status), width / 2, height - 28, 0xA0FFA0);
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
                status = ex.getMessage();
            }
        }, "localservers-ui").start();
    }

    private interface ThrowingSupplier {
        String get() throws Exception;
    }
}
