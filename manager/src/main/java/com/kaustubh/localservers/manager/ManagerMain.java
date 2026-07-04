package com.kaustubh.localservers.manager;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.Socket;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.jar.JarFile;

public final class ManagerMain {
    private static final String VERSION = "0.1.0";
    private static final String USER_AGENT = "local-server-dashboard/0.1.0 (https://example.invalid/local-server-dashboard)";
    private static final int DEFAULT_PORT = 45631;

    private final Path root;
    private final int port;
    private final Map<String, ManagedServer> servers = new HashMap<>();
    private final Map<String, TunnelClient> tunnels = new HashMap<>();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private HttpServer httpServer;

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--self-check")) {
            selfCheck();
            return;
        }
        new ManagerMain(rootFrom(args), portFrom(args)).run();
    }

    private ManagerMain(Path root, int port) {
        this.root = root;
        this.port = port;
    }

    private void run() throws Exception {
        Files.createDirectories(root.resolve("servers"));
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        httpServer.createContext("/", this::handle);
        httpServer.start();
        System.out.println("Local server manager " + VERSION + " listening on http://127.0.0.1:" + port);
        Thread.currentThread().join();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            if (method.equals("GET") && path.equals("/health")) {
                json(exchange, 200, "{\"ok\":true}");
            } else if (method.equals("GET") && path.equals("/version")) {
                json(exchange, 200, "{\"version\":\"" + VERSION + "\"}");
            } else if (method.equals("POST") && path.equals("/shutdown")) {
                json(exchange, 200, "{\"status\":\"stopping\"}");
                new Thread(() -> {
                    httpServer.stop(0);
                    System.exit(0);
                }, "localservers-shutdown").start();
            } else if (method.equals("GET") && path.equals("/servers")) {
                json(exchange, 200, listServers());
            } else if (method.equals("POST") && path.equals("/servers/create")) {
                json(exchange, 200, createServer(read(exchange)));
            } else if (method.equals("POST") && path.matches("/servers/[^/]+/start")) {
                json(exchange, 200, startServer(segment(path, 1)));
            } else if (method.equals("POST") && path.matches("/servers/[^/]+/stop")) {
                json(exchange, 200, stopServer(segment(path, 1)));
            } else if (method.equals("GET") && path.matches("/servers/[^/]+/logs")) {
                text(exchange, 200, logs(segment(path, 1)));
            } else if (method.equals("POST") && path.matches("/servers/[^/]+/modrinth/install")) {
                json(exchange, 200, installModrinth(segment(path, 1), read(exchange)));
            } else if (method.equals("POST") && path.matches("/servers/[^/]+/tunnel/start")) {
                json(exchange, 200, startTunnel(segment(path, 1), read(exchange)));
            } else if (method.equals("POST") && path.matches("/servers/[^/]+/tunnel/stop")) {
                json(exchange, 200, stopTunnel(segment(path, 1)));
            } else if (method.equals("GET") && path.matches("/servers/[^/]+/tunnel")) {
                json(exchange, 200, tunnelStatus(segment(path, 1)));
            } else if (method.equals("GET") && path.equals("/modrinth/search")) {
                json(exchange, 200, searchModrinth(exchange.getRequestURI()));
            } else {
                json(exchange, 404, "{\"error\":\"not found\"}");
            }
        } catch (Exception ex) {
            json(exchange, 500, "{\"error\":\"" + escape(ex.getMessage()) + "\"}");
        } finally {
            exchange.close();
        }
    }

    private String createServer(String body) throws Exception {
        String id = value(body, "id").orElse("server-" + System.currentTimeMillis()).replaceAll("[^a-zA-Z0-9_-]", "-");
        String minecraftVersion = value(body, "minecraftVersion").orElse("1.21.11");
        int ramMb = value(body, "ramMb").map(Integer::parseInt).orElse(4096);
        int cpuCores = value(body, "cpuCores").map(Integer::parseInt).orElse(0);
        int serverPort = value(body, "serverPort").map(Integer::parseInt).orElse(25565);
        Path dir = root.resolve("servers").resolve(id);
        Files.createDirectories(dir.resolve("plugins"));
        Files.writeString(dir.resolve("eula.txt"), "eula=true\n");
        Files.writeString(dir.resolve("local-server.json"), "{\"id\":\"" + escape(id) + "\",\"minecraftVersion\":\""
                + escape(minecraftVersion) + "\",\"ramMb\":" + ramMb + ",\"cpuCores\":" + cpuCores + ",\"serverPort\":" + serverPort + "}\n");
        writeIfMissing(dir.resolve("server.properties"), "motd=Local Server Dashboard\nserver-port=" + serverPort + "\nonline-mode=true\n");
        installBridgePlugin(dir);
        Path paperJar = dir.resolve("paper-" + minecraftVersion + ".jar");
        if (!validJar(paperJar)) {
            downloadPaper(minecraftVersion, paperJar);
        }
        servers.put(id, new ManagedServer(id, dir, paperJar, ramMb, cpuCores, serverPort));
        return "{\"id\":\"" + id + "\",\"status\":\"created\"}";
    }

    private void installBridgePlugin(Path serverDir) throws IOException {
        Path target = serverDir.resolve("plugins").resolve("localservers-bridge.jar");
        try (InputStream in = ManagerMain.class.getResourceAsStream("/bridge/localservers-bridge.jar")) {
            if (in == null) {
                throw new IOException("Bundled bridge plugin missing");
            }
            Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void writeIfMissing(Path path, String value) throws IOException {
        if (!Files.exists(path)) {
            Files.writeString(path, value);
        }
    }

    private void downloadPaper(String version, Path target) throws Exception {
        String versionJson = get("https://fill.papermc.io/v3/projects/paper/versions/" + version);
        Matcher buildMatcher = Pattern.compile("\"builds\"\\s*:\\s*\\[\\s*(\\d+)").matcher(versionJson);
        if (!buildMatcher.find()) {
            throw new IllegalStateException("No Paper build found for " + version);
        }
        String build = buildMatcher.group(1);
        String buildJson = get("https://fill.papermc.io/v3/projects/paper/versions/" + version + "/builds/" + build);
        Matcher urlMatcher = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+\\.jar[^\"]*)\"").matcher(buildJson);
        if (!urlMatcher.find()) {
            throw new IllegalStateException("No Paper download URL found for " + version + " build " + build);
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(urlMatcher.group(1).replace("\\/", "/")))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofMinutes(3))
                .build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Paper download failed: HTTP " + response.statusCode());
        }
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(tmp)) {
            in.transferTo(out);
        }
        if (!validJar(tmp)) {
            throw new IOException("Downloaded Paper jar is invalid");
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private void download(String url, Path target) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofMinutes(3))
                .build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() / 100 != 2) {
            throw new IOException(url + " failed: HTTP " + response.statusCode());
        }
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(tmp)) {
            in.transferTo(out);
        }
        if (!validJar(tmp)) {
            throw new IOException("Downloaded jar is invalid: " + target.getFileName());
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private String get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(30))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException(url + " failed: HTTP " + response.statusCode());
        }
        return response.body();
    }

    private String startServer(String id) throws IOException {
        ManagedServer server = server(id);
        if (server.process != null && server.process.isAlive()) {
            return "{\"id\":\"" + id + "\",\"status\":\"running\"}";
        }
        if (!Files.exists(server.paperJar)) {
            throw new IOException("Create server first; missing " + server.paperJar.getFileName());
        }
        List<String> command = new ArrayList<>();
        command.add("java");
        command.add("-Xmx" + server.ramMb + "M");
        if (server.cpuCores > 0) {
            command.add("-XX:ActiveProcessorCount=" + server.cpuCores);
        }
        command.add("-jar");
        command.add(server.paperJar.getFileName().toString());
        command.add("nogui");
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(server.dir.toFile())
                .redirectInput(ProcessBuilder.Redirect.PIPE)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(server.dir.resolve("server.log").toFile()));
        server.process = builder.start();
        return "{\"id\":\"" + id + "\",\"status\":\"starting\"}";
    }

    private String stopServer(String id) throws IOException {
        ManagedServer server = server(id);
        if (server.process == null || !server.process.isAlive()) {
            return "{\"id\":\"" + id + "\",\"status\":\"stopped\"}";
        }
        try (OutputStream out = server.process.getOutputStream()) {
            out.write("stop\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException ignored) {
            // Paper may already be closing stdin while it shuts down.
        }
        try {
            if (!server.process.waitFor(20, TimeUnit.SECONDS)) {
                server.process.destroy();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        return "{\"id\":\"" + id + "\",\"status\":\"stopping\"}";
    }

    private String logs(String id) throws IOException {
        Path log = server(id).dir.resolve("server.log");
        if (!Files.exists(log)) {
            return "";
        }
        String content = Files.readString(log);
        int start = Math.max(0, content.length() - 8000);
        return content.substring(start);
    }

    private String installModrinth(String serverId, String body) throws Exception {
        ManagedServer server = server(serverId);
        String project = value(body, "project").orElseThrow(() -> new IllegalArgumentException("project is required"));
        String loader = value(body, "loader").orElse("paper");
        String versionJson = get("https://api.modrinth.com/v2/project/" + enc(project)
                + "/version?loaders=%5B%22" + enc(loader) + "%22%5D&game_versions=%5B%221.21.11%22%5D");
        Matcher urlMatcher = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+\\.jar[^\"]*)\"").matcher(versionJson);
        Matcher nameMatcher = Pattern.compile("\"filename\"\\s*:\\s*\"([^\"]+\\.jar)\"").matcher(versionJson);
        if (!urlMatcher.find() || !nameMatcher.find()) {
            throw new IllegalStateException("No compatible Modrinth file found for " + project);
        }
        Path folder = loader.equals("fabric") ? server.dir.resolve("mods") : server.dir.resolve("plugins");
        Files.createDirectories(folder);
        Path target = folder.resolve(nameMatcher.group(1));
        download(urlMatcher.group(1).replace("\\/", "/"), target);
        return "{\"server\":\"" + escape(serverId) + "\",\"installed\":\"" + escape(target.getFileName().toString()) + "\"}";
    }

    private String startTunnel(String serverId, String body) throws IOException {
        stopTunnel(serverId);
        ManagedServer server = server(serverId);
        String host = value(body, "relayHost").orElse("127.0.0.1");
        int controlPort = value(body, "controlPort").map(Integer::parseInt).orElse(45640);
        int dataPort = value(body, "dataPort").map(Integer::parseInt).orElse(45641);
        int publicPort = value(body, "publicPort").map(Integer::parseInt).orElse(25577);
        int localPort = value(body, "localPort").map(Integer::parseInt).orElse(server.serverPort);
        String token = value(body, "relayToken").orElse("");
        TunnelClient tunnel = new TunnelClient(host, controlPort, dataPort, localPort, publicPort, token);
        tunnels.put(serverId, tunnel);
        tunnel.start();
        return "{\"server\":\"" + escape(serverId) + "\",\"status\":\"starting\",\"address\":\""
                + escape(host) + ":" + publicPort + "\"}";
    }

    private String stopTunnel(String serverId) {
        TunnelClient tunnel = tunnels.remove(serverId);
        if (tunnel != null) {
            tunnel.close();
        }
        return "{\"server\":\"" + escape(serverId) + "\",\"status\":\"stopped\"}";
    }

    private String tunnelStatus(String serverId) {
        TunnelClient tunnel = tunnels.get(serverId);
        if (tunnel == null) {
            return "{\"server\":\"" + escape(serverId) + "\",\"status\":\"stopped\"}";
        }
        return "{\"server\":\"" + escape(serverId) + "\",\"status\":\"" + tunnel.status()
                + "\",\"address\":\"" + escape(tunnel.host) + ":" + tunnel.publicPort + "\"}";
    }

    private String listServers() throws IOException {
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        try (var stream = Files.list(root.resolve("servers"))) {
            for (Path dir : stream.filter(Files::isDirectory).toList()) {
                String id = dir.getFileName().toString();
                ManagedServer known = servers.get(id);
                String status = known != null && known.process != null && known.process.isAlive() ? "running" : "stopped";
                if (!first) {
                    json.append(',');
                }
                first = false;
                json.append("{\"id\":\"").append(escape(id)).append("\",\"status\":\"").append(status).append("\"}");
            }
        }
        return json.append(']').toString();
    }

    private String searchModrinth(URI uri) throws Exception {
        String query = Optional.ofNullable(uri.getQuery()).orElse("");
        String search = query.replaceFirst("(^|.*&)q=", "");
        if (search.contains("&")) {
            search = search.substring(0, search.indexOf('&'));
        }
        String facets = "%5B%5B%22versions%3A1.21.11%22%5D%2C%5B%22project_type%3Amod%22%2C%22project_type%3Aplugin%22%5D%5D";
        return get("https://api.modrinth.com/v2/search?query=" + enc(URLDecoder.decode(search, StandardCharsets.UTF_8)) + "&facets=" + facets + "&limit=10");
    }

    private ManagedServer server(String id) throws IOException {
        ManagedServer server = servers.get(id);
        if (server != null) {
            return server;
        }
        Path dir = root.resolve("servers").resolve(id);
        Path config = dir.resolve("local-server.json");
        String json = Files.exists(config) ? Files.readString(config) : "{}";
        String version = value(json, "minecraftVersion").orElse("1.21.11");
        int ramMb = value(json, "ramMb").map(Integer::parseInt).orElse(4096);
        int cpuCores = value(json, "cpuCores").map(Integer::parseInt).orElse(0);
        int serverPort = value(json, "serverPort").map(Integer::parseInt).orElse(25565);
        ManagedServer loaded = new ManagedServer(id, dir, dir.resolve("paper-" + version + ".jar"), ramMb, cpuCores, serverPort);
        servers.put(id, loaded);
        return loaded;
    }

    private static String read(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static Optional<String> value(String json, String key) {
        Matcher quoted = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        if (quoted.find()) {
            return Optional.of(quoted.group(1));
        }
        Matcher number = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)").matcher(json);
        if (number.find()) {
            return Optional.of(number.group(1));
        }
        return Optional.empty();
    }

    private static String segment(String path, int index) {
        return path.split("/")[index + 1];
    }

    private static void json(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        send(exchange, status, body);
    }

    private static void text(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        send(exchange, status, body);
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static boolean validJar(Path path) {
        if (!Files.exists(path)) {
            return false;
        }
        try (JarFile ignored = new JarFile(path.toFile())) {
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static Path rootFrom(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--root")) {
                return Path.of(args[i + 1]);
            }
        }
        return Path.of(System.getProperty("user.home"), ".minecraft", "local-server-dashboard");
    }

    private static int portFrom(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--port")) {
                return Integer.parseInt(args[i + 1]);
            }
        }
        return DEFAULT_PORT;
    }

    private static void selfCheck() {
        String json = "{\"id\":\"main server\",\"minecraftVersion\":\"1.21.11\",\"ramMb\":4096}";
        assert value(json, "id").orElseThrow().equals("main server");
        assert value(json, "minecraftVersion").orElseThrow().equals("1.21.11");
        assert value(json, "ramMb").orElseThrow().equals("4096");
        assert value("{\"serverPort\":25566}", "serverPort").orElseThrow().equals("25566");
        assert rootFrom(new String[]{"--root", "/tmp/lsd"}).equals(Path.of("/tmp/lsd"));
        assert portFrom(new String[]{"--port", "45632"}) == 45632;
        assert segment("/servers/main/start", 1).equals("main");
        assert escape("a\"b\\c").equals("a\\\"b\\\\c");
        assert Pattern.compile("\"filename\"\\s*:\\s*\"([^\"]+\\.jar)\"").matcher("{\"filename\":\"Chunky.jar\"}").find();
        System.out.println("manager self-check passed");
    }

    private static final class ManagedServer {
        final String id;
        final Path dir;
        final Path paperJar;
        final int ramMb;
        final int cpuCores;
        final int serverPort;
        Process process;

        ManagedServer(String id, Path dir, Path paperJar, int ramMb, int cpuCores, int serverPort) {
            this.id = id;
            this.dir = dir;
            this.paperJar = paperJar;
            this.ramMb = ramMb;
            this.cpuCores = cpuCores;
            this.serverPort = serverPort;
        }
    }

    private static final class TunnelClient implements AutoCloseable {
        final String host;
        final int controlPort;
        final int dataPort;
        final int localPort;
        final int publicPort;
        final String token;
        volatile Socket control;
        volatile boolean closed;
        volatile String state = "starting";

        TunnelClient(String host, int controlPort, int dataPort, int localPort, int publicPort, String token) {
            this.host = host;
            this.controlPort = controlPort;
            this.dataPort = dataPort;
            this.localPort = localPort;
            this.publicPort = publicPort;
            this.token = token;
        }

        void start() {
            Thread thread = new Thread(this::run, "localservers-tunnel");
            thread.setDaemon(true);
            thread.start();
        }

        String status() {
            return closed ? "stopped" : state;
        }

        private void run() {
            try (Socket socket = new Socket(host, controlPort);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                control = socket;
                socket.getOutputStream().write(("TOKEN " + token + "\n").getBytes(StandardCharsets.UTF_8));
                socket.getOutputStream().flush();
                state = "online";
                String line;
                while (!closed && (line = reader.readLine()) != null) {
                    if (line.equals("OPEN")) {
                        new Thread(this::openData, "localservers-tunnel-data").start();
                    }
                }
            } catch (IOException ex) {
                if (!closed) {
                    state = "error";
                }
            }
        }

        private void openData() {
            try (Socket relay = new Socket(host, dataPort);
                 Socket local = new Socket("127.0.0.1", localPort)) {
                pipeBoth(relay, local);
            } catch (IOException ignored) {
            }
        }

        @Override
        public void close() {
            closed = true;
            state = "stopped";
            try {
                if (control != null) {
                    control.close();
                }
            } catch (IOException ignored) {
            }
        }
    }

    private static void pipeBoth(Socket a, Socket b) throws IOException {
        Thread left = new Thread(() -> pipe(a, b), "localservers-pipe-left");
        Thread right = new Thread(() -> pipe(b, a), "localservers-pipe-right");
        left.start();
        right.start();
        try {
            left.join();
            right.join();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static void pipe(Socket from, Socket to) {
        try {
            from.getInputStream().transferTo(to.getOutputStream());
        } catch (IOException ignored) {
        } finally {
            try {
                to.close();
            } catch (IOException ignored) {
            }
        }
    }
}
