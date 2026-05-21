package com.webshopx.payments.backend.payment;

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
import java.util.*;

public class PaymentStripe<C extends ClientInfo<C>> {
    private final AbstractPaymentServer<C> server;

    public PaymentStripe(AbstractPaymentServer<C> server) {
        this.server = server;
    }

    public PaymentOrderResponse handleCreateOrder(PaymentOrderRequest request, C client, Configuration config) {
        String orderId = client.nextOrderId();
        if (orderId == null) {
            return new PaymentOrderResponse("payment.can-not-create-id");
        }
        try {
            StripeSession session = createCheckoutSession(orderId, request, config);
            if (config.isDebug()) {
                server.getLogger().info("[DEBUG] Stripe create session response: {}", session.raw);
            }
            ClientInfo.Order<C> order = client.createOrder(orderId, "stripe", request.getPlayerName(), request.getPrice(), request.getCurrency());
            
            // Set polling task
            order.setTask(new TimerTask() {
                @Override
                public void run() {
                    checkOrder(client, this, orderId, session.id);
                }
            });
            server.getTimer().schedule(order.getTask(), 2000L, 5000L); // Poll every 5s after 2s
            
            server.getLogger().info("Stripe order created: merchantOrderId={}, sessionId={}", orderId, session.id);
            return new PaymentOrderResponse("checkout", orderId, order.getMoney(), session.url);
        } catch (Exception e) {
            client.removeOrder(orderId);
            server.getLogger().warn("Stripe create session failed: merchantOrderId={}", orderId, e);
            return new PaymentOrderResponse("payment.internal-error");
        }
    }

    public PacketPluginQueryPayment.Response handleQueryPayment(PacketPluginQueryPayment packet, Configuration config) {
        String providerOrderId = packet.getProviderOrderId(); // Stripe Session ID
        if (providerOrderId == null || providerOrderId.trim().isEmpty()) {
            return new PacketPluginQueryPayment.Response("payment.cancel.not-found");
        }
        try {
            StripeSession session = retrieveSession(providerOrderId, config);
            Map<String, String> extra = new LinkedHashMap<>();
            extra.put("source", "stripe-api");
            extra.put("status", session.status);
            extra.put("paymentStatus", session.paymentStatus);
            return new PacketPluginQueryPayment.Response(
                    packet.getMerchantOrderId(),
                    providerOrderId,
                    toPaymentStatus(session.paymentStatus),
                    "stripe",
                    "stripe",
                    session.url,
                    null,
                    priceToAmountMinor(session.amount),
                    config.getStripe().getCurrency(),
                    0L,
                    "paid".equals(session.paymentStatus) ? System.currentTimeMillis() : 0L,
                    extra
            );
        } catch (Exception e) {
            server.getLogger().warn("Stripe query session failed: sessionId={}", providerOrderId, e);
            return new PacketPluginQueryPayment.Response("payment.internal-error");
        }
    }

    private void checkOrder(C client, TimerTask task, String orderId, String sessionId) {
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
            StripeSession session = retrieveSession(sessionId, config);
            if (config.isDebug()) {
                server.getLogger().info("[DEBUG] Stripe poll session response: status={}, paymentStatus={}", session.status, session.paymentStatus);
            }
            if ("paid".equals(session.paymentStatus)) {
                completeOrder(client, order, session);
            } else if ("expired".equals(session.status)) {
                client.removeOrder(order);
                server.sendPaymentCancel(client, order, "payment.stripe.expired");
            }
        } catch (Exception e) {
            server.getLogger().warn("Stripe poll session failed: merchantOrderId={}, sessionId={}", orderId, sessionId, e);
        }
    }

    private StripeSession createCheckoutSession(String orderId, PaymentOrderRequest request, Configuration config) throws Exception {
        Configuration.Stripe stripe = config.getStripe();
        
        // Stripe amounts are in minor units (e.g., cents for USD)
        long amountInCents = new BigDecimal(request.getPrice()).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValue();
        
        String successUrl = request.getReturnUrl();
        if (successUrl != null && !successUrl.trim().isEmpty()) {
            successUrl = successUrl + (successUrl.contains("?") ? "&" : "?") + "status=success";
        } else {
            successUrl = stripe.getSuccessUrl();
        }
        String cancelUrl = request.getReturnUrl();
        if (cancelUrl != null && !cancelUrl.trim().isEmpty()) {
            cancelUrl = cancelUrl + (cancelUrl.contains("?") ? "&" : "?") + "status=cancel";
        } else {
            cancelUrl = stripe.getCancelUrl();
        }

        // Build application/x-www-form-urlencoded parameters
        Map<String, String> params = new LinkedHashMap<>();
        params.put("success_url", successUrl);
        params.put("cancel_url", cancelUrl);
        params.put("mode", "payment");
        params.put("line_items[0][price_data][currency]", stripe.getCurrency().toLowerCase(Locale.ROOT));
        params.put("line_items[0][price_data][unit_amount]", String.valueOf(amountInCents));
        params.put("line_items[0][price_data][product_data][name]", request.getSubject());
        params.put("line_items[0][quantity]", "1");
        params.put("client_reference_id", orderId);
        params.put("metadata[merchant_order_id]", orderId);

        String formBody = buildFormBody(params);
        String raw = ProxySupport.call(config.resolveProxy(stripe.getProxy()), server.getLogger(),
                () -> requestForm("POST", "https://api.stripe.com/v1/checkout/sessions", formBody, stripe.getSecretKey()));
        
        JsonObject response = JsonParser.parseString(raw).getAsJsonObject();
        StripeSession session = new StripeSession();
        session.raw = raw;
        session.id = response.get("id").getAsString();
        session.url = response.get("url").getAsString();
        session.status = response.get("status").getAsString();
        session.paymentStatus = response.get("payment_status").getAsString();
        session.amount = request.getPrice();
        return session;
    }

    private StripeSession retrieveSession(String sessionId, Configuration config) throws Exception {
        Configuration.Stripe stripe = config.getStripe();
        String raw = ProxySupport.call(config.resolveProxy(stripe.getProxy()), server.getLogger(),
                () -> requestForm("GET", "https://api.stripe.com/v1/checkout/sessions/" + urlEncode(sessionId), null, stripe.getSecretKey()));
        
        JsonObject response = JsonParser.parseString(raw).getAsJsonObject();
        StripeSession session = new StripeSession();
        session.raw = raw;
        session.id = response.get("id").getAsString();
        session.url = response.has("url") && !response.get("url").isJsonNull() ? response.get("url").getAsString() : "";
        session.status = response.get("status").getAsString();
        session.paymentStatus = response.get("payment_status").getAsString();
        
        long rawAmount = response.get("amount_total").getAsLong();
        session.amount = BigDecimal.valueOf(rawAmount, 2).setScale(2, RoundingMode.UNNECESSARY).toPlainString();
        return session;
    }

    private void completeOrder(C client, ClientInfo.Order<C> order, StripeSession session) {
        String money = session.amount;
        client.removeOrder(order);

        if (priceToAmountMinor(money) != priceToAmountMinor(order.getMoney())) {
            server.getLogger().warn("Stripe payment amount mismatch: sessionId={}, paid={}, expected={}; cancelling order",
                    session.id, money, order.getMoney());
            server.sendPaymentCancel(client, order, "payment.cancel.not-the-agreed-price");
            return;
        }
        server.getLogger().info("Stripe payment completed: sessionId={}, amount={}", session.id, money);
        server.sendPaymentSuccess(client, order, money);
    }

    private static String requestForm(String method, String urlStr, String formBody, String secretKey) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlStr).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + secretKey);
        
        if (formBody != null && "POST".equals(method)) {
            byte[] bytes = formBody.getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            connection.setRequestProperty("Content-Length", String.valueOf(bytes.length));
            try (OutputStream out = connection.getOutputStream()) {
                out.write(bytes);
            }
        }
        int status = connection.getResponseCode();
        InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String response = readStream(stream);
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("Stripe API request failed: HTTP " + status + ", body=" + response);
        }
        return response;
    }

    private static String readStream(InputStream stream) throws Exception {
        if (stream == null) return "";
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
             BufferedReader buffered = new BufferedReader(reader)) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = buffered.readLine()) != null) {
                builder.append(line).append("\n");
            }
            return builder.toString().trim();
        }
    }

    private static String buildFormBody(Map<String, String> params) throws Exception {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (body.length() > 0) body.append("&");
            body.append(urlEncode(entry.getKey()))
                .append("=")
                .append(urlEncode(entry.getValue()));
        }
        return body.toString();
    }

    private static String urlEncode(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }

    private static String toPaymentStatus(String paymentStatus) {
        if ("paid".equalsIgnoreCase(paymentStatus)) return "SUCCESS";
        if ("unpaid".equalsIgnoreCase(paymentStatus)) return "PAYING";
        return "UNKNOWN";
    }

    private static long priceToAmountMinor(String price) {
        try {
            if (price == null || price.trim().isEmpty()) return 0L;
            return new BigDecimal(price).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValue();
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static final class StripeSession {
        String raw;
        String id;
        String url;
        String status;
        String paymentStatus;
        String amount;
    }
}
