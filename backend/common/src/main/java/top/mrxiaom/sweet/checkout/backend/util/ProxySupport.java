package top.mrxiaom.sweet.checkout.backend.util;

import org.slf4j.Logger;
import top.mrxiaom.sweet.checkout.backend.Configuration;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

public final class ProxySupport {
    private static final Object LOCK = new Object();
    private static final String[] PROPERTIES = {
            "http.proxyHost",
            "http.proxyPort",
            "http.proxyUser",
            "http.proxyPassword",
            "https.proxyHost",
            "https.proxyPort",
            "https.proxyUser",
            "https.proxyPassword",
            "socksProxyHost",
            "socksProxyPort",
            "java.net.socks.username",
            "java.net.socks.password"
    };

    private ProxySupport() {
    }

    public static <T> T call(Configuration.ProxySettings proxy, Logger logger, Callable<T> action) throws Exception {
        if (proxy == null || !proxy.isEnable()) {
            return action.call();
        }
        if (proxy.getHost().trim().isEmpty() || proxy.getPort() <= 0) {
            if (logger != null) {
                logger.warn("支付 API 代理已启用，但 host 或 port 无效，将按直连请求");
            }
            return action.call();
        }
        synchronized (LOCK) {
            Map<String, String> previous = snapshot();
            try {
                apply(proxy);
                if (logger != null) {
                    logger.info("已为本次支付 API 请求启用 {} 代理: {}:{}", proxy.getType(), proxy.getHost(), proxy.getPort());
                }
                return action.call();
            } finally {
                restore(previous);
            }
        }
    }

    public static void run(Configuration.ProxySettings proxy, Logger logger, ThrowingRunnable action) throws Exception {
        call(proxy, logger, () -> {
            action.run();
            return null;
        });
    }

    private static Map<String, String> snapshot() {
        Map<String, String> values = new LinkedHashMap<>();
        for (String property : PROPERTIES) {
            values.put(property, System.getProperty(property));
        }
        return values;
    }

    private static void apply(Configuration.ProxySettings proxy) {
        clear();
        String type = proxy.getType().trim().toUpperCase(Locale.ROOT);
        if ("SOCKS".equals(type) || "SOCKS5".equals(type)) {
            set("socksProxyHost", proxy.getHost());
            set("socksProxyPort", String.valueOf(proxy.getPort()));
            set("java.net.socks.username", proxy.getUsername());
            set("java.net.socks.password", proxy.getPassword());
            return;
        }
        set("http.proxyHost", proxy.getHost());
        set("http.proxyPort", String.valueOf(proxy.getPort()));
        set("https.proxyHost", proxy.getHost());
        set("https.proxyPort", String.valueOf(proxy.getPort()));
        set("http.proxyUser", proxy.getUsername());
        set("http.proxyPassword", proxy.getPassword());
        set("https.proxyUser", proxy.getUsername());
        set("https.proxyPassword", proxy.getPassword());
    }

    private static void restore(Map<String, String> previous) {
        for (Map.Entry<String, String> entry : previous.entrySet()) {
            if (entry.getValue() == null) {
                System.clearProperty(entry.getKey());
            } else {
                System.setProperty(entry.getKey(), entry.getValue());
            }
        }
    }

    private static void clear() {
        for (String property : PROPERTIES) {
            System.clearProperty(property);
        }
    }

    private static void set(String key, String value) {
        if (value == null || value.trim().isEmpty()) return;
        System.setProperty(key, value);
    }

    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
