package com.kaustubh.localservers.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LocalServersScreen extends Screen {
    private static final Pattern SERVER_PATTERN = Pattern.compile("\\{\"id\":\"([^\"]+)\",\"status\":\"([^\"]+)\"}");
    private static final int WIDTH = 398;

    private final Screen parent;
    private final List<ServerRow> servers = new ArrayList<>();
    private final List<InfoTip> infoTips = new ArrayList<>();
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
        int x = originX();
        int y = originY();
        addDrawableChild(ButtonWidget.builder(Text.literal("Dashboard"), button -> show(View.DASHBOARD))
                .dimensions(x, y - 28, 92, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("New Server"), button -> showCreate(0))
                .dimensions(x + 100, y - 28, 104, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Refresh"), button -> reloadServers())
                .dimensions(x + 212, y - 28, 86, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> close())
                .dimensions(x + 306, y - 28, 92, 20).build());
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
                    .dimensions(x + 18, rowY + 36, 144, 20).build());
        } else {
            for (ServerRow server : servers) {
                String label = (server.id.equals(selectedServer) ? "> " : "") + server.id + "  " + server.status;
                ButtonWidget row = ButtonWidget.builder(Text.literal(label), button -> {
                    selectedServer = server.id;
                    rebuild();
                }).dimensions(x + 14, rowY, 154, 20).build();
                addDrawableChild(row);
                rowY += 24;
            }
        }

        int panelX = x + 190;
        relayHost = field(panelX, y + 136, 186, draftRelayHost);
        addDrawableChild(ButtonWidget.builder(Text.literal("Start"), button -> runAction(this::startServer))
                .dimensions(panelX, y + 48, 90, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Stop"), button -> runAction(this::stopServer))
                .dimensions(panelX + 96, y + 48, 90, 20).build());
        int tunnelY = Math.min(y + 166, statusTop() - 28);
        addDrawableChild(ButtonWidget.builder(Text.literal("Share Online"), button -> runAction(this::startTunnel))
                .dimensions(panelX, tunnelY, 90, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Stop Share"), button -> runAction(this::stopTunnel))
                .dimensions(panelX + 96, tunnelY, 90, 20).build());
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
        infoTips.clear();
        int x = originX();
        int y = originY();
        if (view == View.CREATE) {
            renderCreate(context, x, y);
        } else {
            renderDashboard(context, x, y);
        }
        super.render(context, mouseX, mouseY, delta);
        context.fill(x, statusTop(), x + WIDTH, height - 12, 0xEE101018);
        infoLabel(context, "Status", x + 10, statusTop() + 6, 0xFFFFFFFF, "What the manager is doing right now.");
        context.drawTextWithShadow(textRenderer, Text.literal(shortStatus()), x + 10, statusTop() + 19, 0xFF8DFF96);
        drawInfoTip(context, mouseX, mouseY);
    }

    private void renderDashboard(DrawContext context, int x, int y) {
        int panelHeight = Math.max(226, statusTop() - y - 8);
        panel(context, x, y, 182, panelHeight, 0xEE171717);
        infoLabel(context, "Servers", x + 14, y + 12, 0xFFFFFFFF, "Local Paper servers this manager knows about.");
        if (servers.isEmpty()) {
            label(context, "No local servers yet", x + 18, y + 42, 0xFFD6D6D6);
            label(context, "Start with a Paper server", x + 18, y + 56, 0xFF9E9E9E);
        }
        int panelX = x + 190;
        panel(context, panelX - 12, y, 220, panelHeight, 0xEE121822);
        label(context, selectedServer, panelX, y + 12, 0xFFFFFFFF);
        label(context, statusFor(selectedServer), panelX, y + 27, statusColor(selectedServer));
        infoLabel(context, "Same Wi-Fi: " + sameWifiAddress(), panelX, y + 78, 0xFF8DFF96, "Give this to friends on your Wi-Fi.");
        infoLabel(context, "Internet: " + internetAddress(), panelX, y + 94, 0xFF8DFF96, "Give this to friends outside your Wi-Fi.");
        infoLabel(context, "Internet relay address", panelX, y + 121, 0xFFD6D6D6, "Only needed for friends outside your Wi-Fi.");
    }

    private void renderCreate(DrawContext context, int x, int y) {
        panel(context, x, y, WIDTH, Math.min(290, statusTop() - y - 8), 0xEE121822);
        label(context, "Create Paper Server", x + 18, y + 16, 0xFFFFFFFF);
        label(context, "Step " + (createStep + 1) + " of 3", x + 18, y + 32, 0xFF8DFF96);
        if (createStep == 0) {
            infoLabel(context, "Server", x + 18, y + 46, 0xFFD6D6D6, "Folder/name for this local server.");
            infoLabel(context, "RAM MB", x + 182, y + 46, 0xFFD6D6D6, "Memory for Paper. 4096 means 4 GB.");
            infoLabel(context, "CPU", x + 278, y + 46, 0xFFD6D6D6, "CPU cores Paper is allowed to use.");
        } else if (createStep == 1) {
            infoLabel(context, "Plugin name", x + 18, y + 68, 0xFFD6D6D6, "Optional. Example: chunky from Modrinth.");
            label(context, "Optional. chunky preloads world chunks.", x + 18, y + 112, 0xFF9E9E9E);
        } else {
            infoLabel(context, "Minecraft port", x + 18, y + 54, 0xFFD6D6D6, "Friends on your Wi-Fi connect to this port.");
            infoLabel(context, "Relay address", x + 122, y + 54, 0xFFD6D6D6, "Only needed for friends outside your Wi-Fi.");
            infoLabel(context, "Internet port", x + 250, y + 54, 0xFFD6D6D6, "Port friends use on the relay.");
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

    private void infoLabel(DrawContext context, String text, int x, int y, int color, String help) {
        label(context, text, x, y, color);
        int infoX = x + textRenderer.getWidth(text) + 6;
        context.drawTextWithShadow(textRenderer, Text.literal("(i)"), infoX, y, 0xFFB8C7FF);
        infoTips.add(new InfoTip(infoX, y, help));
    }

    private void drawInfoTip(DrawContext context, int mouseX, int mouseY) {
        for (InfoTip tip : infoTips) {
            if (mouseX >= tip.x && mouseX <= tip.x + 15 && mouseY >= tip.y && mouseY <= tip.y + 10) {
                int boxWidth = Math.min(300, textRenderer.getWidth(tip.text) + 12);
                int boxX = Math.min(mouseX + 10, width - boxWidth - 8);
                int boxY = Math.max(8, mouseY - 24);
                context.fill(boxX, boxY, boxX + boxWidth, boxY + 20, 0xF0101018);
                context.fill(boxX, boxY, boxX + boxWidth, boxY + 1, 0x99FFFFFF);
                context.drawTextWithShadow(textRenderer, Text.literal(tip.text), boxX + 6, boxY + 6, 0xFFFFFFFF);
                return;
            }
        }
    }

    private void panel(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + height, color);
        context.fill(x, y, x + width, y + 1, 0x55FFFFFF);
    }

    private int originX() {
        return Math.max(8, (width - WIDTH) / 2);
    }

    private int originY() {
        return height < 360 ? 42 : 52;
    }

    private int statusTop() {
        return height - 46;
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
        if (!hasRelayHost()) {
            return "Internet sharing needs a relay host. Wi-Fi friends can use " + sameWifiAddress();
        }
        String id = selectedServerValue();
        ManagerClient.post("/servers/" + id + "/tunnel/start", "{\"relayHost\":\"" + q(draftRelayHost)
                + "\",\"controlPort\":" + draftControlPort + ",\"dataPort\":" + draftDataPort
                + ",\"publicPort\":" + draftPublicPort + ",\"localPort\":" + draftServerPort
                + ",\"relayToken\":\"" + q(draftRelayToken) + "\"}");
        return "Tunnel starting. Internet friends use " + internetAddress();
    }

    private String stopTunnel() throws Exception {
        String id = selectedServerValue();
        ManagerClient.post("/servers/" + id + "/tunnel/stop", "{}");
        return "Tunnel stopped for " + id;
    }

    private String internetAddress() {
        if (!hasRelayHost()) {
            return "set relay host";
        }
        return q(draftRelayHost) + ":" + draftPublicPort;
    }

    private boolean hasRelayHost() {
        String host = q(draftRelayHost);
        return !host.isBlank() && !host.equals("127.0.0.1") && !host.equals("localhost");
    }

    private String sameWifiAddress() {
        try {
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                for (var address : Collections.list(networkInterface.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        return address.getHostAddress() + ":" + draftServerPort;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "this-computer:" + draftServerPort;
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
        return status.length() > 90 ? status.substring(0, 90) : status;
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

    private record InfoTip(int x, int y, String text) {
    }

    private interface ThrowingSupplier {
        String get() throws Exception;
    }
}
