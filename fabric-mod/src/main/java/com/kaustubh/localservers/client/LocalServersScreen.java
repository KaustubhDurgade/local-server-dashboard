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
            runAction(() -> {
                status = "Creating server...";
                createServer();
                status = "Installing " + projectSlug.getText() + "...";
                installPlugin();
                status = "Starting server...";
                return startServer();
            });
        }).dimensions(x, y + 136, 300, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Create Server"), button -> {
            runAction(this::createServer);
        }).dimensions(x, y + 162, 145, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Start"), button -> {
            runAction(this::startServer);
        }).dimensions(x + 155, y + 162, 70, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Stop"), button -> {
            runAction(this::stopServer);
        }).dimensions(x + 230, y + 162, 70, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Refresh"), button -> refresh())
                .dimensions(x, y + 188, 95, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Install Plugin"), button -> {
            runAction(this::installPlugin);
        }).dimensions(x + 105, y + 188, 95, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Start Tunnel"), button -> {
            runAction(this::startTunnel);
        }).dimensions(x + 205, y + 188, 95, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Stop Tunnel"), button -> {
            runAction(this::stopTunnel);
        }).dimensions(x, y + 214, 145, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> close())
                .dimensions(x + 155, y + 214, 145, 20).build());
        refresh();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int x = width / 2 - 150;
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFFFF);
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
        context.drawTextWithShadow(textRenderer, Text.literal("Status:"), x + 6, height - 42, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.literal(shortStatus()), x + 6, height - 28, 0xFFA0FFA0);
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    private void refresh() {
        call(this::refreshStatus);
    }

    private TextFieldWidget field(int x, int y, int width, String value) {
        TextFieldWidget field = new TextFieldWidget(textRenderer, x, y, width, 18, Text.empty());
        field.setText(value);
        addDrawableChild(field);
        return field;
    }

    private void label(DrawContext context, String text, int x, int y) {
        context.drawTextWithShadow(textRenderer, Text.literal(text), x, y, 0xFFD0D0D0);
    }

    private String q(String value) {
        return value.replace("\\", "\\\\").replace("\"", "");
    }

    private String createServer() throws Exception {
        String id = serverIdValue();
        ManagerClient.post("/servers/create", "{\"id\":\"" + id + "\",\"minecraftVersion\":\"1.21.11\",\"ramMb\":"
                + ramMb.getText() + ",\"cpuCores\":" + cpuCores.getText() + ",\"serverPort\":" + serverPort.getText() + "}");
        return "Created server " + id;
    }

    private String installPlugin() throws Exception {
        String id = serverIdValue();
        String project = q(projectSlug.getText());
        ManagerClient.post("/servers/" + id + "/modrinth/install", "{\"project\":\"" + project + "\",\"loader\":\"paper\"}");
        return "Installed " + project + " on " + id;
    }

    private String startServer() throws Exception {
        String id = serverIdValue();
        ManagerClient.post("/servers/" + id + "/start", "{}");
        for (int i = 0; i < 45; i++) {
            String servers = ManagerClient.get("/servers");
            if (hasStatus(servers, id, "running")) {
                return "Server " + id + " is running";
            }
            if (hasStatus(servers, id, "stopped")) {
                return "Server " + id + " stopped while starting. Check server.log.";
            }
            status = "Still starting " + id + "...";
            Thread.sleep(1000);
        }
        return "Server " + id + " is still starting.";
    }

    private String stopServer() throws Exception {
        String id = serverIdValue();
        ManagerClient.post("/servers/" + id + "/stop", "{}");
        for (int i = 0; i < 25; i++) {
            String servers = ManagerClient.get("/servers");
            if (hasStatus(servers, id, "stopped")) {
                return "Server " + id + " is stopped";
            }
            status = "Stopping " + id + "...";
            Thread.sleep(1000);
        }
        return "Server " + id + " is still stopping.";
    }

    private String startTunnel() throws Exception {
        String id = serverIdValue();
        ManagerClient.post("/servers/" + id + "/tunnel/start", "{\"relayHost\":\"" + q(relayHost.getText())
                + "\",\"controlPort\":" + controlPort.getText() + ",\"dataPort\":" + dataPort.getText()
                + ",\"publicPort\":" + publicPort.getText() + ",\"localPort\":" + serverPort.getText()
                + ",\"relayToken\":\"" + q(relayToken.getText()) + "\"}");
        return "Tunnel starting for " + id + " on player port " + publicPort.getText();
    }

    private String stopTunnel() throws Exception {
        String id = serverIdValue();
        ManagerClient.post("/servers/" + id + "/tunnel/stop", "{}");
        return "Tunnel stopped for " + id;
    }

    private String refreshStatus() throws Exception {
        String id = serverIdValue();
        String servers = ManagerClient.get("/servers");
        if (servers.equals("[]")) {
            return "No servers yet.";
        }
        if (hasStatus(servers, id, "running")) {
            return "Server " + id + " is running";
        }
        if (hasStatus(servers, id, "stopped")) {
            return "Server " + id + " is stopped";
        }
        return "Servers updated";
    }

    private boolean hasStatus(String servers, String id, String expectedStatus) {
        return servers.contains("{\"id\":\"" + id + "\",\"status\":\"" + expectedStatus + "\"}");
    }

    private String serverIdValue() {
        return q(serverId.getText());
    }

    private void call(ThrowingSupplier supplier) {
        status = "Working...";
        new Thread(() -> {
            try {
                status = supplier.get();
            } catch (Exception ex) {
                status = "Error: " + ManagerClient.errorText(ex);
            }
        }, "localservers-ui").start();
    }

    private void runAction(ThrowingSupplier supplier) {
        setFocused(null);
        call(supplier);
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
