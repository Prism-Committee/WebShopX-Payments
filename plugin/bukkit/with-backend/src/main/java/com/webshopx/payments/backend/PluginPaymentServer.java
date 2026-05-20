package com.webshopx.payments.backend;

import com.sun.net.httpserver.HttpServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import com.webshopx.payments.backend.data.LocalClientInfo;
import com.webshopx.payments.packets.common.IPacket;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.Supplier;

@SuppressWarnings({"rawtypes"})
public class PluginPaymentServer extends AbstractPaymentServer<LocalClientInfo> {
    private final BukkitMain main;
    private HttpServer server;
    private Supplier<List<String>> java9ProcessGetter;

    public PluginPaymentServer(BukkitMain main, Logger logger) {
        super(logger);
        this.main = main;
        this.logger = logger;
    }

    public void setJava9ProcessGetter(Supplier<List<String>> java9ProcessGetter) {
        this.java9ProcessGetter = java9ProcessGetter;
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    protected void restart() {
        stop();
        Configuration config = getConfig();
        // 仅在开启 hook 时，运行 HTTP 服务器
        if (config.getHook().isEnable()) {
            try {
                server = HttpServer.create(new InetSocketAddress(config.getPort()), 0);
                server.createContext(config.getHook().getEndPoint(), exchange -> {
                    if (!exchange.getRequestMethod().equals("POST")) {
                        exchange.sendResponseHeaders(405, 0);
                        exchange.close();
                        return;
                    }
                    String body;
                    try (InputStream in = exchange.getRequestBody();
                         InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
                         BufferedReader buffered = new BufferedReader(reader)) {
                        StringJoiner joiner = new StringJoiner("\n");
                        String line;
                        while ((line = buffered.readLine()) != null) {
                            joiner.add(line);
                        }
                        body = joiner.toString();
                    } catch (IOException e) {
                        exchange.sendResponseHeaders(500, 0);
                        OutputStream os = exchange.getResponseBody();
                        os.write(e.toString().getBytes());
                        os.close();
                        return;
                    }
                    receiveHook(body);
                    exchange.sendResponseHeaders(200, 0);
                    OutputStream os = exchange.getResponseBody();
                    os.write("OK".getBytes());
                    os.close();
                });
                server.start();
                logger.info("[Hook] HTTP 服务器已在 {} 端口启动", config.getPort());
            } catch (Throwable t) {
                logger.warn("开启 HTTP 服务器时出现一个异常", t);
            }
        }
    }

    @Override
    public List<String> getAllProcess() {
        // 在 Java 9 及以上获取所有进程
        if (java9ProcessGetter != null) {
            return java9ProcessGetter.get();
        }
        // 在 Java 8 获取所有进程
        List<String> list = new ArrayList<>();
        String os = System.getProperty("os.name").toLowerCase();
        // 由于目前只有 Windows 的 Hook，故暂时不对其它系统进行支持
        if (os.contains("windows")) {
            try {
                ProcessBuilder builder = new ProcessBuilder("wmic", "process", "get", "ExecutablePath");
                Process process = builder.start();
                try (InputStream in = process.getInputStream();
                     InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
                     BufferedReader buffered = new BufferedReader(reader)) {
                    String line;
                    while ((line = buffered.readLine()) != null) {
                        String trim = line.replace("\t", "").trim();
                        if (trim.isEmpty() || trim.equals("ExecutablePath")) continue;
                        list.add(trim);
                        System.out.println("\"" + trim + "\"");
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return list;
    }

    @Override
    public Configuration getConfig() {
        return main.getConfig();
    }

    @Override
    public void send(@NotNull LocalClientInfo client, @NotNull IPacket packet, @Nullable Long echo) {
        main.getClient().getParent().onMessage(packet, echo);
    }
}
