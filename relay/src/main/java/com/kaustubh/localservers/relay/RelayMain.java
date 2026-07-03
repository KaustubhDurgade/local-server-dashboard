package com.kaustubh.localservers.relay;

import java.io.IOException;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class RelayMain {
    public static void main(String[] args) throws Exception {
        if (has(args, "--self-check")) {
            assert port(args, "--public-port", 25577) > 0;
            System.out.println("relay self-check passed");
            return;
        }
        new RelayMain().run(
                port(args, "--public-port", 25577),
                port(args, "--control-port", 45640),
                port(args, "--data-port", 45641),
                value(args, "--token", "")
        );
    }

    private void run(int publicPort, int controlPort, int dataPort, String token) throws IOException {
        try (ServerSocket controlServer = new ServerSocket(controlPort);
             ServerSocket dataServer = new ServerSocket(dataPort);
             ServerSocket publicServer = new ServerSocket(publicPort)) {
            dataServer.setSoTimeout((int) Duration.ofSeconds(20).toMillis());
            System.out.println("Relay public=:" + publicPort + " control=:" + controlPort + " data=:" + dataPort);
            Socket control = acceptControl(controlServer, token);
            OutputStream controlOut = control.getOutputStream();
            while (!publicServer.isClosed()) {
                Socket player = publicServer.accept();
                new Thread(() -> connect(player, dataServer, controlOut), "relay-player").start();
            }
        }
    }

    private Socket acceptControl(ServerSocket controlServer, String token) throws IOException {
        while (true) {
            Socket control = controlServer.accept();
            BufferedReader reader = new BufferedReader(new InputStreamReader(control.getInputStream(), StandardCharsets.UTF_8));
            String line = reader.readLine();
            if (line != null && line.equals("TOKEN " + token)) {
                return control;
            }
            control.close();
        }
    }

    private void connect(Socket player, ServerSocket dataServer, OutputStream controlOut) {
        try (player) {
            synchronized (controlOut) {
                controlOut.write("OPEN\n".getBytes(StandardCharsets.UTF_8));
                controlOut.flush();
            }
            try (Socket data = dataServer.accept()) {
                pipeBoth(player, data);
            }
        } catch (IOException ignored) {
        }
    }

    private static void pipeBoth(Socket a, Socket b) throws IOException {
        Thread left = new Thread(() -> pipe(a, b), "relay-pipe-left");
        Thread right = new Thread(() -> pipe(b, a), "relay-pipe-right");
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

    private static boolean has(String[] args, String flag) {
        for (String arg : args) {
            if (arg.equals(flag)) {
                return true;
            }
        }
        return false;
    }

    private static int port(String[] args, String flag, int fallback) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(flag)) {
                return Integer.parseInt(args[i + 1]);
            }
        }
        return fallback;
    }

    private static String value(String[] args, String flag, String fallback) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(flag)) {
                return args[i + 1];
            }
        }
        return fallback;
    }
}
