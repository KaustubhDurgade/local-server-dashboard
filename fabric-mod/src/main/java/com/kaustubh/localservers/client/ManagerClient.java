package com.kaustubh.localservers.client;

import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

final class ManagerClient {
    static final String BASE_URL = "http://127.0.0.1:45631";
    private static final String VERSION = "0.1.0";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(700)).build();

    private ManagerClient() {
    }

    static void ensureRunning() {
        try {
            Path managerJar = installManagerJar();
            if (isCurrent()) {
                return;
            }
            try {
                post("/shutdown", "{}");
                Thread.sleep(500);
            } catch (Exception ignored) {
            }
            new ProcessBuilder(javaBin(), "-jar", managerJar.toString(), "--root", managerJar.getParent().toString())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(managerJar.getParent().resolve("manager.log").toFile()))
                    .start();
        } catch (Exception ex) {
            System.err.println("Could not start local server manager: " + ex.getMessage());
        }
    }

    static String get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        return checked(HTTP.send(request, HttpResponse.BodyHandlers.ofString()));
    }

    static String post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return checked(HTTP.send(request, HttpResponse.BodyHandlers.ofString()));
    }

    private static String checked(HttpResponse<String> response) throws IOException {
        if (response.statusCode() / 100 != 2) {
            throw new IOException(response.body());
        }
        return response.body();
    }

    private static boolean isHealthy() {
        try {
            return get("/health").contains("\"ok\":true");
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isCurrent() {
        try {
            return isHealthy() && get("/version").contains("\"version\":\"" + VERSION + "\"");
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Path installManagerJar() throws IOException {
        Path dir = MinecraftClient.getInstance().runDirectory.toPath().resolve("local-server-dashboard");
        Files.createDirectories(dir);
        Path target = dir.resolve("manager.jar");
        try (InputStream in = ManagerClient.class.getResourceAsStream("/manager/manager.jar")) {
            if (in == null) {
                throw new IOException("Bundled manager.jar missing");
            }
            byte[] bundled = in.readAllBytes();
            if (!Files.exists(target) || Files.size(target) != bundled.length) {
                Files.write(target, bundled);
                Files.writeString(dir.resolve("manager.version"), "0.1.0\n", StandardCharsets.UTF_8);
            }
        }
        return target;
    }

    private static String javaBin() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }
}
