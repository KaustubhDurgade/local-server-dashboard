package com.kaustubh.localservers.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LocalServersScreen extends Screen {
    private static final Pattern SERVER_PATTERN = Pattern.compile("\\{\"id\":\"([^\"]+)\",\"status\":\"([^\"]+)\"}");

    private final Screen parent;
    private final List<ServerRow> servers = new ArrayList<>();
    private volatile String status = "Ready";
    private View view = View.DASHBOARD;
    private int createStep;
    private String selectedServer = "main";
    private String draftServerId = "main";
    private String draftRamMb = "4096";
    private String draftCpuCores = "2";
    private String draftServerPort = "25565";
    private String draftProjectSlug = "chunky";
    private String draftRelayHost = "127.0.0.1";
    private String draftPublicPort = "25577";
    private String draftControlPort = "45640";
    private String draftDataPort = "45641";
    private String draftRelayToken = "";
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
        rebuild();
        reloadServers();
    }

    private void rebuild() {
        clearChildren();
        int x = width / 2 - 188;
        int y = 42;
        addDrawableChild(ButtonWidget.builder(Text.literal("Dashboard"), button -> show(View.DASHBOARD))
                .dimensions(x, 14, 90, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("New Server"), button -> showCreate(0))
                .dimensions(x + 96, 14, 96, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Refresh"), button -> reloadServers())
                .dimensions(x + 198, 14, 82, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> close())
                .dimensions(x + 286, 14, 90, 20).build());
        if (view == View.CREATE) {
            initCreate(x, y);
        } else {
            initDashboard(x, y);
        }
    }

    private void initDashboard(int x, int y) {
        int rowY = y + 35;
        if (servers.isEmpty()) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Create first server"), button -> showCreate(0))
                    .dimensions(x + 16, rowY + 36, 128, 20).build());
        } else {
            for (ServerRow server : servers) {
                ButtonWidget row = ButtonWidget.builder(Text.literal(server.id + "  " + server.status), button -> {
                    selectedServer = server.id;
                    rebuild();
                }).dimensions(x + 10, rowY, 150, 20).build();
                row.active = !server.id.equals(selectedServer);
                addDrawableChild(row);
                rowY += 24;
            }
        }

        int panelX = x + 178;
        projectSlug = field(panelX, y + 93, 180, draftProjectSlug);
        relayHost = field(panelX, y + 136, 86, draftRelayHost);
        publicPort = field(panelX + 94, y + 136, 86, draftPublicPort);
        controlPort = field(panelX, y + 176, 86, draftControlPort);
        dataPort = field(panelX + 94, y + 176, 86, draftDataPort);
        relayToken = field(panelX, y + 216, 180, draftRelayToken);
        addDrawableChild(ButtonWidget.builder(Text.literal("Start"), button -> runAction(this::startServer))
                .dimensions(panelX, y + 38, 86, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Stop"), button -> runAction(this::stopServer))
                .dimensions(panelX + 94, y + 38, 86, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Install"), button -> runAction(this::installPlugin))
                .dimensions(panelX, y + 113, 86, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Start Tunnel"), button -> runAction(this::startTunnel))
                .dimensions(panelX, y + 237, 86, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Stop Tunnel"), button -> runAction(this::stopTunnel))
                .dimensions(panelX + 94, y + 237, 86, 20).build());
    }

    private void initCreate(int x, int y) {
        if (createStep == 0) {
            serverId = field(x + 18, y + 62, 146, draftServerId);
            ramMb = field(x + 182, y + 62, 78, draftRamMb);
            cpuCores = field(x + 278, y + 62, 78, draftCpuCores);
            addDrawableChild(ButtonWidget.builder(Text.literal("Next"), button -> showCreate(1))
                    .dimensions(x + 244, y + 222, 112, 20).build());
        } else if (createStep == 1) {
            projectSlug = field(x + 18, y + 84, 160, draftProjectSlug);
            addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> showCreate(0))
                    .dimensions(x + 18, y + 222, 112, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Next"), button -> showCreate(2))
                    .dimensions(x + 244, y + 222, 112, 20).build());
        } else {
            serverPort = field(x + 18, y + 70, 86, draftServerPort);
            relayHost = field(x + 122, y + 70, 110, draftRelayHost);
            publicPort = field(x + 250, y + 70, 86, draftPublicPort);
            controlPort = field(x + 18, y + 126, 86, draftControlPort);
            dataPort = field(x + 122, y + 126, 86, draftDataPort);
            relayToken = field(x + 18, y + 182, 318, draftRelayToken);
            addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> showCreate(1))
                    .dimensions(x + 18, y + 222, 112, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Create"), button -> runAction(this::createServer))
                    .dimensions(x + 136, y + 222, 100, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Create + Start"), button -> runAction(this::quickStart))
                    .dimensions(x + 244, y + 222, 112, 20).build());
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int x = width / 2 - 188;
        int y = 42;
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Local Servers"), width / 2, 18, 0xFFFFFFFF);
        if (view == View.CREATE) {
            renderCreate(context, x, y);
        } else {
            renderDashboard(context, x, y);
        }
        context.fill(x, height - 46, x + 376, height - 12, 0xCC101018);
        context.drawTextWithShadow(textRenderer, Text.literal("Status"), x + 10, height - 40, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.literal(shortStatus()), x + 10, height - 27, 0xFF8DFF96);
    }

    private void renderDashboard(DrawContext context, int x, int y) {
        panel(context, x, y, 168, 256, 0xCC171717);
        label(context, "Servers", x + 10, y + 10, 0xFFFFFFFF);
        if (servers.isEmpty()) {
            label(context, "No local servers yet", x + 18, y + 42, 0xFFD6D6D6);
            label(context, "Start with a Paper server", x + 18, y + 56, 0xFF9E9E9E);
        }
        int panelX = x + 178;
        panel(context, panelX - 10, y, 208, 272, 0xCC121822);
        label(context, selectedServer, panelX, y + 10, 0xFFFFFFFF);
        label(context, statusFor(selectedServer), panelX, y + 23, statusColor(selectedServer));
        label(context, "Plugin slug", panelX, y + 78, 0xFFD6D6D6);
        label(context, "Relay host", panelX, y + 121, 0xFFD6D6D6);
        label(context, "Public port", panelX + 94, y + 121, 0xFFD6D6D6);
        label(context, "Control", panelX, y + 161, 0xFFD6D6D6);
        label(context, "Data", panelX + 94, y + 161, 0xFFD6D6D6);
        label(context, "Relay token", panelX, y + 201, 0xFFD6D6D6);
    }

    private void renderCreate(DrawContext context, int x, int y) {
        panel(context, x, y, 376, 290, 0xCC121822);
        label(context, "Create Paper Server", x + 18, y + 16, 0xFFFFFFFF);
        label(context, "Step " + (createStep + 1) + " of 3", x + 18, y + 32, 0xFF8DFF96);
        if (createStep == 0) {
            label(context, "Server", x + 18, y + 46, 0xFFD6D6D6);
            label(context, "RAM MB", x + 182, y + 46, 0xFFD6D6D6);
            label(context, "CPU", x + 278, y + 46, 0xFFD6D6D6);
        } else if (createStep == 1) {
            label(context, "Starter plugin", x + 18, y + 68, 0xFFD6D6D6);
            label(context, "chunky is a good first test", x + 18, y + 112, 0xFF9E9E9E);
        } else {
            label(context, "MC port", x + 18, y + 54, 0xFFD6D6D6);
            label(context, "Relay host", x + 122, y + 54, 0xFFD6D6D6);
            label(context, "Player port", x + 250, y + 54, 0xFFD6D6D6);
            label(context, "Control", x + 18, y + 110, 0xFFD6D6D6);
            label(context, "Data", x + 122, y + 110, 0xFFD6D6D6);
            label(context, "Relay token", x + 18, y + 166, 0xFFD6D6D6);
        }
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    private void reloadServers() {
        status = "Refreshing servers...";
        new Thread(() -> {
            try {
                List<ServerRow> loaded = parseServers(ManagerClient.get("/servers"));
                MinecraftClient.getInstance().execute(() -> {
                    servers.clear();
                    servers.addAll(loaded);
                    if (!servers.isEmpty() && servers.stream().noneMatch(server -> server.id.equals(selectedServer))) {
                        selectedServer = servers.get(0).id;
                    }
                    status = servers.isEmpty() ? "No servers yet." : "Loaded " + servers.size() + " server" + (servers.size() == 1 ? "" : "s");
                    if (client != null && client.currentScreen == this) {
                        rebuild();
                    }
                });
            } catch (Exception ex) {
                status = "Error: " + ManagerClient.errorText(ex);
            }
        }, "localservers-refresh").start();
    }

    private TextFieldWidget field(int x, int y, int width, String value) {
        TextFieldWidget field = new TextFieldWidget(textRenderer, x, y, width, 18, Text.empty());
        field.setText(value);
        addDrawableChild(field);
        return field;
    }

    private void label(DrawContext context, String text, int x, int y, int color) {
        context.drawTextWithShadow(textRenderer, Text.literal(text), x, y, color);
    }

    private void panel(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + height, color);
        context.fill(x, y, x + width, y + 1, 0x55FFFFFF);
    }

    private String q(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "");
    }

    private String quickStart() throws Exception {
        captureFields();
        status = "Creating server...";
        createServer();
        status = "Installing " + q(draftProjectSlug) + "...";
        installPlugin();
        status = "Starting server...";
        return startServer();
    }

    private String createServer() throws Exception {
        captureFields();
        String id = createServerId();
        ManagerClient.post("/servers/create", "{\"id\":\"" + id + "\",\"minecraftVersion\":\"1.21.11\",\"ramMb\":"
                + draftRamMb + ",\"cpuCores\":" + draftCpuCores + ",\"serverPort\":" + draftServerPort + "}");
        selectedServer = id;
        view = View.DASHBOARD;
        reloadServers();
        return "Created server " + id;
    }

    private String installPlugin() throws Exception {
        captureFields();
        String id = selectedServerValue();
        String project = q(draftProjectSlug);
        ManagerClient.post("/servers/" + id + "/modrinth/install", "{\"project\":\"" + project + "\",\"loader\":\"paper\"}");
        return "Installed " + project + " on " + id;
    }

    private String startServer() throws Exception {
        String id = selectedServerValue();
        ManagerClient.post("/servers/" + id + "/start", "{}");
        for (int i = 0; i < 45; i++) {
            String json = ManagerClient.get("/servers");
            if (hasStatus(json, id, "running")) {
                reloadServers();
                return "Server " + id + " is running";
            }
            status = "Still starting " + id + "...";
            Thread.sleep(1000);
        }
        reloadServers();
        return "Server " + id + " is still starting.";
    }

    private String stopServer() throws Exception {
        String id = selectedServerValue();
        ManagerClient.post("/servers/" + id + "/stop", "{}");
        for (int i = 0; i < 25; i++) {
            String json = ManagerClient.get("/servers");
            if (hasStatus(json, id, "stopped")) {
                reloadServers();
                return "Server " + id + " is stopped";
            }
            status = "Stopping " + id + "...";
            Thread.sleep(1000);
        }
        reloadServers();
        return "Server " + id + " is still stopping.";
    }

    private String startTunnel() throws Exception {
        captureFields();
        String id = selectedServerValue();
        ManagerClient.post("/servers/" + id + "/tunnel/start", "{\"relayHost\":\"" + q(draftRelayHost)
                + "\",\"controlPort\":" + draftControlPort + ",\"dataPort\":" + draftDataPort
                + ",\"publicPort\":" + draftPublicPort + ",\"localPort\":" + draftServerPort
                + ",\"relayToken\":\"" + q(draftRelayToken) + "\"}");
        return "Tunnel starting for " + id + " on player port " + draftPublicPort;
    }

    private String stopTunnel() throws Exception {
        String id = selectedServerValue();
        ManagerClient.post("/servers/" + id + "/tunnel/stop", "{}");
        return "Tunnel stopped for " + id;
    }

    private boolean hasStatus(String json, String id, String expectedStatus) {
        return json.contains("{\"id\":\"" + id + "\",\"status\":\"" + expectedStatus + "\"}");
    }

    private String statusFor(String id) {
        for (ServerRow server : servers) {
            if (server.id.equals(id)) {
                return server.status.equals("running") ? "Running" : "Stopped";
            }
        }
        return "Not created";
    }

    private int statusColor(String id) {
        return statusFor(id).equals("Running") ? 0xFF8DFF96 : 0xFFFFC16B;
    }

    private String createServerId() {
        return q(draftServerId).replaceAll("[^a-zA-Z0-9_-]", "-");
    }

    private String selectedServerValue() {
        return q(selectedServer);
    }

    private void show(View next) {
        captureFields();
        view = next;
        rebuild();
    }

    private void showCreate(int step) {
        captureFields();
        view = View.CREATE;
        createStep = step;
        rebuild();
    }

    private void captureFields() {
        draftServerId = value(serverId, draftServerId);
        draftRamMb = value(ramMb, draftRamMb);
        draftCpuCores = value(cpuCores, draftCpuCores);
        draftServerPort = value(serverPort, draftServerPort);
        draftProjectSlug = value(projectSlug, draftProjectSlug);
        draftRelayHost = value(relayHost, draftRelayHost);
        draftPublicPort = value(publicPort, draftPublicPort);
        draftControlPort = value(controlPort, draftControlPort);
        draftDataPort = value(dataPort, draftDataPort);
        draftRelayToken = value(relayToken, draftRelayToken);
    }

    private String value(TextFieldWidget field, String fallback) {
        return field == null || field.getText().isBlank() ? fallback : field.getText();
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
        return status.length() > 84 ? status.substring(0, 84) : status;
    }

    private static List<ServerRow> parseServers(String json) {
        List<ServerRow> rows = new ArrayList<>();
        Matcher matcher = SERVER_PATTERN.matcher(json);
        while (matcher.find()) {
            rows.add(new ServerRow(matcher.group(1), matcher.group(2)));
        }
        return rows;
    }

    private enum View {
        DASHBOARD,
        CREATE
    }

    private record ServerRow(String id, String status) {
    }

    private interface ThrowingSupplier {
        String get() throws Exception;
    }
}
