package com.webshopx.payments.backend.payment;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.webshopx.payments.backend.AbstractPaymentServer;
import com.webshopx.payments.backend.Configuration;
import com.webshopx.payments.backend.PaymentOrderRequest;
import com.webshopx.payments.backend.PaymentOrderResponse;
import com.webshopx.payments.backend.data.ClientInfo;
import com.webshopx.payments.backend.util.ProxySupport;
import com.webshopx.payments.packets.plugin.PacketPluginQueryPayment;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TimerTask;

public class PaymentMercadoPago<C extends ClientInfo<C>> {
    private final AbstractPaymentServer<C> server;

    public PaymentMercadoPago(AbstractPaymentServer<C> server) {
        this.server = server;
    }

    public PaymentOrderResponse handleCreateOrder(PaymentOrderRequest request, C client, Configuration config) {
        String orderId = client.nextOrderId();
        if (orderId == null) {
            return new PaymentOrderResponse("payment.can-not-create-id");
        }
        try {
            MercadoPagoPreference preference = createPreference(orderId, request, config);
            if (config.isDebug()) {
                server.getLogger().info("[DEBUG] MercadoPago create preference response: {}", preference.raw);
            }
            ClientInfo.Order<C> order = client.createOrder(orderId, "mercadopago", request.getPlayerName(), request.getPrice(), request.getCurrency());
            order.setCancelAction(() -> {
                // Checkout Pro preferences are left to expire upstream.
            });
            order.setTask(new TimerTask() {
                @Override
                public void run() {
                    checkOrder(client, this, orderId);
                }
            });
            server.getTimer().schedule(order.getTask(), 1000L, 5000L);
            server.getLogger().info("MercadoPago order created: merchantOrderId={}, preferenceId={}", orderId, preference.id);
            return new PaymentOrderResponse("checkout-pro", orderId, order.getMoney(), preference.paymentUrl);
        } catch (Exception e) {
            client.removeOrder(orderId);
            server.getLogger().warn("MercadoPago create preference failed: merchantOrderId={}", orderId, e);
            return new PaymentOrderResponse("payment.internal-error");
        }
    }

    public PacketPluginQueryPayment.Response handleQueryPayment(PacketPluginQueryPayment packet, Configuration config) {
        String providerOrderId = packet.getProviderOrderId();
        if (providerOrderId == null || providerOrderId.trim().isEmpty()) {
            return new PacketPluginQueryPayment.Response("payment.cancel.not-found");
        }
        try {
            MercadoPagoPayment payment = searchPayment(providerOrderId, config);
            Map<String, String> extra = new LinkedHashMap<>();
            extra.put("source", "mercadopago-api");
            if (payment.id != null) extra.put("paymentId", payment.id);
            if (payment.rawStatus != null) extra.put("rawStatus", payment.rawStatus);
            return new PacketPluginQueryPayment.Response(
                    packet.getMerchantOrderId(),
                    providerOrderId,
                    toPaymentStatus(payment.rawStatus),
                    "mercadopago",
                    "mercadopago",
                    null,
                    null,
                    priceToAmountMinor(payment.amount),
                    payment.currency == null ? config.getMercadoPago().getCurrency() : payment.currency,
                    0L,
                    payment.paidAt,
                    extra
            );
        } catch (Exception e) {
            server.getLogger().warn("MercadoPago query payment failed: providerOrderId={}", providerOrderId, e);
            return new PacketPluginQueryPayment.Response("payment.internal-error");
        }
    }

    private void checkOrder(C client, TimerTask task, String orderId) {
        Configuration config = server.getConfig();
        ClientInfo.Order<C> order = client.getOrder(orderId);
        if (order == null || !client.isOpen()) {
            task.cancel();
            if (order != null) {
                order.setTask(null);
                client.removeOrder(order);
            }
            return;
        }
        try {
            MercadoPagoPayment payment = searchPayment(orderId, config);
            if (config.isDebug()) {
                server.getLogger().info("[DEBUG] MercadoPago query payment response: {}", payment.raw);
            }
            String status = toPaymentStatus(payment.rawStatus);
            if ("SUCCESS".equals(status)) {
                completeOrder(client, order, payment);
            } else if ("CANCELLED".equals(status) || "REFUNDED".equals(status)) {
                client.removeOrder(order);
                server.sendPaymentCancel(client, order, "payment.mercadopago." + normalizeStatus(payment.rawStatus));
            }
        } catch (Exception e) {
            server.getLogger().warn("MercadoPago poll payment failed: merchantOrderId={}", orderId, e);
        }
    }

    private MercadoPagoPreference createPreference(String orderId, PaymentOrderRequest request, Configuration config) throws Exception {
        Configuration.MercadoPago mercadoPago = config.getMercadoPago();
        JsonObject body = new JsonObject();

        JsonObject item = new JsonObject();
        item.addProperty("id", orderId);
        item.addProperty("title", nonEmpty(request.getSubject(), orderId));
        item.addProperty("description", nonEmpty(request.getDescription(), request.getSubject()));
        item.addProperty("quantity", 1);
        item.addProperty("currency_id", mercadoPago.getCurrency());
        item.addProperty("unit_price", new BigDecimal(request.getPrice()).setScale(2, RoundingMode.HALF_UP));
        JsonArray items = new JsonArray();
        items.add(item);
        body.add("items", items);

        body.addProperty("external_reference", orderId);
        body.addProperty("statement_descriptor", "WEBSHOPX");

        String notificationUrl = nonEmpty(request.getNotifyUrl(), mercadoPago.getNotificationUrl());
        if (notificationUrl != null) {
            body.addProperty("notification_url", notificationUrl);
        }

        String backUrl = nonEmpty(request.getReturnUrl(), mercadoPago.getBackUrl());
        if (backUrl != null) {
            JsonObject backUrls = new JsonObject();
            backUrls.addProperty("success", backUrl);
            backUrls.addProperty("pending", backUrl);
            backUrls.addProperty("failure", backUrl);
            body.add("back_urls", backUrls);
            body.addProperty("auto_return", "approved");
        }

        if (request.getExpiresAt() > 0L) {
            body.addProperty("expires", true);
            body.addProperty("expiration_date_to", java.time.Instant.ofEpochMilli(request.getExpiresAt()).toString());
        }

        String raw = ProxySupport.call(config.resolveProxy(mercadoPago.getProxy()), server.getLogger(),
                () -> requestJson("POST", mercadoPago.getHost() + "/checkout/preferences", body.toString(), mercadoPago.getAccessToken()));
        JsonObject response = JsonParser.parseString(raw).getAsJsonObject();
        MercadoPagoPreference preference = new MercadoPagoPreference();
        preference.raw = raw;
        preference.id = getString(response, "id");
        preference.paymentUrl = mercadoPago.isSandbox() ? getString(response, "sandbox_init_point") : getString(response, "init_point");
        if (preference.paymentUrl == null || preference.paymentUrl.trim().isEmpty()) {
            preference.paymentUrl = getString(response, "init_point");
        }
        if (preference.paymentUrl == null || preference.paymentUrl.trim().isEmpty()) {
            throw new IllegalStateException("MercadoPago preference response has no payment URL");
        }
        return preference;
    }

    private MercadoPagoPayment searchPayment(String orderId, Configuration config) throws Exception {
        Configuration.MercadoPago mercadoPago = config.getMercadoPago();
        String url = mercadoPago.getHost()
                + "/v1/payments/search?sort=date_created&criteria=desc&limit=10&external_reference="
                + urlEncode(orderId);
        String raw = ProxySupport.call(config.resolveProxy(mercadoPago.getProxy()), server.getLogger(),
                () -> requestJson("GET", url, null, mercadoPago.getAccessToken()));
        JsonObject response = JsonParser.parseString(raw).getAsJsonObject();
        MercadoPagoPayment best = new MercadoPagoPayment();
        best.raw = raw;
        best.currency = mercadoPago.getCurrency();

        JsonArray results = getArray(response, "results");
        if (results == null || results.size() == 0) {
            best.rawStatus = "pending";
            return best;
        }

        JsonObject fallback = null;
        for (JsonElement element : results) {
            if (!element.isJsonObject()) continue;
            JsonObject payment = element.getAsJsonObject();
            if (fallback == null) fallback = payment;
            if ("approved".equalsIgnoreCase(getString(payment, "status"))) {
                return parsePayment(payment, raw);
            }
        }
        return parsePayment(fallback, raw);
    }

    private void completeOrder(C client, ClientInfo.Order<C> order, MercadoPagoPayment payment) {
        String money = payment.amount;
        if (money == null || money.trim().isEmpty()) {
            money = order.getMoney();
        }
        client.removeOrder(order);

        if (priceToAmountMinor(money) != priceToAmountMinor(order.getMoney())) {
            server.getLogger().warn("MercadoPago payment amount mismatch: paymentId={}, paid={}, expected={}; cancelling order",
                    payment.id, money, order.getMoney());
            server.sendPaymentCancel(client, order, "payment.cancel.not-the-agreed-price");
            return;
        }
        String currency = payment.currency == null ? server.getConfig().getMercadoPago().getCurrency() : payment.currency;
        server.getLogger().info("MercadoPago payment completed: paymentId={}, amount={} {}", payment.id, currency, money);
        server.sendPaymentSuccess(client, order, money);
    }

    private static MercadoPagoPayment parsePayment(JsonObject object, String raw) {
        MercadoPagoPayment payment = new MercadoPagoPayment();
        payment.raw = raw;
        if (object == null) {
            payment.rawStatus = "pending";
            return payment;
        }
        JsonElement id = object.get("id");
        payment.id = id == null || id.isJsonNull() ? null : id.getAsString();
        payment.rawStatus = getString(object, "status");
        payment.amount = getDecimalString(object, "transaction_amount");
        payment.currency = getString(object, "currency_id");
        payment.paidAt = parseTime(getString(object, "date_approved"));
        return payment;
    }

    private static String requestJson(String method, String url, String body, String accessToken) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + accessToken);
        if (body != null) {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Content-Length", String.valueOf(bytes.length));
            try (OutputStream out = connection.getOutputStream()) {
                out.write(bytes);
            }
        }
        int status = connection.getResponseCode();
        String response = read(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("MercadoPago API request failed: HTTP " + status + ", body=" + response);
        }
        return response;
    }

    private static String read(InputStream stream) throws Exception {
        if (stream == null) return "";
        try (InputStream in = stream;
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
             BufferedReader buffered = new BufferedReader(reader)) {
            StringJoiner joiner = new StringJoiner("\n");
            String line;
            while ((line = buffered.readLine()) != null) {
                joiner.add(line);
            }
            return joiner.toString();
        }
    }

    private static String urlEncode(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }

    private static String toPaymentStatus(String status) {
        String normalized = normalizeStatus(status);
        if ("approved".equals(normalized)) return "SUCCESS";
        if ("cancelled".equals(normalized) || "canceled".equals(normalized) || "charged_back".equals(normalized)) return "CANCELLED";
        if ("refunded".equals(normalized)) return "REFUNDED";
        if ("rejected".equals(normalized)) return "FAILED";
        if ("pending".equals(normalized) || "in_process".equals(normalized) || "authorized".equals(normalized)) return "PAYING";
        return "UNKNOWN";
    }

    private static String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
    }

    private static long priceToAmountMinor(String price) {
        try {
            if (price == null || price.trim().isEmpty()) return 0L;
            return new BigDecimal(price).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValue();
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static long parseTime(String value) {
        if (value == null || value.trim().isEmpty()) return 0L;
        try {
            return OffsetDateTime.parse(value).toInstant().toEpochMilli();
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static String getDecimalString(JsonObject object, String name) {
        JsonElement element = object == null ? null : object.get(name);
        if (element == null || element.isJsonNull()) return null;
        try {
            return element.getAsBigDecimal().setScale(2, RoundingMode.HALF_UP).toPlainString();
        } catch (Throwable ignored) {
            return element.getAsString();
        }
    }

    private static JsonArray getArray(JsonObject object, String name) {
        JsonElement element = object == null ? null : object.get(name);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static String getString(JsonObject object, String name) {
        JsonElement element = object == null ? null : object.get(name);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    private static String nonEmpty(String value, String fallback) {
        if (value != null && !value.trim().isEmpty()) return value.trim();
        return fallback == null || fallback.trim().isEmpty() ? null : fallback.trim();
    }

    private static final class MercadoPagoPreference {
        String raw;
        String id;
        String paymentUrl;
    }

    private static final class MercadoPagoPayment {
        String raw;
        String id;
        String rawStatus;
        String amount;
        String currency;
        long paidAt;
    }
}
