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
import io.github.eealba.payper.core.client.ResponseSpec;
import io.github.eealba.payper.orders.v2.api.CheckoutOrdersApiClient;
import io.github.eealba.payper.orders.v2.model.AmountWithBreakdown;
import io.github.eealba.payper.orders.v2.model.CheckoutPaymentIntent;
import io.github.eealba.payper.orders.v2.model.CurrencyCode;
import io.github.eealba.payper.orders.v2.model.ErrorDefault;
import io.github.eealba.payper.orders.v2.model.Order;
import io.github.eealba.payper.orders.v2.model.OrderRequest;
import io.github.eealba.payper.orders.v2.model.OrderStatus;
import io.github.eealba.payper.orders.v2.model.PurchaseUnitRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimerTask;

public class PaymentPaypal<C extends ClientInfo<C>> {
    AbstractPaymentServer<C> server;

    public PaymentPaypal(AbstractPaymentServer<C> server) {
        this.server = server;
    }

    public PaymentOrderResponse handleCreateOrder(PaymentOrderRequest request, C client, Configuration config) {
        String orderId = client.nextOrderId();
        if (orderId == null) {
            return new PaymentOrderResponse("payment.can-not-create-id");
        }
        try {
            PayPalOrderData createdOrder = ProxySupport.call(config.resolveProxy(config.getPaypal().getProxy()), server.getLogger(), () -> {
                CheckoutOrdersApiClient api = CheckoutOrdersApiClient.create(config.getPaypal().getConfig());
                ResponseSpec.Response<Order, ErrorDefault> createResponse = api.orders().create().withBody(OrderRequest.builder()
                                .purchaseUnits(Collections.singletonList(PurchaseUnitRequest.builder()
                                        .amount(AmountWithBreakdown.builder()
                                                .value(request.getPrice())
                                                .currencyCode(paypalCurrency(config))
                                                .build())
                                        .build()))
                                .intent(CheckoutPaymentIntent.CAPTURE)
                                .build())
                        .retrieve()
                        .toResponse();
                return requireSuccessful("create order", createResponse);
            });
            if (config.isDebug()) {
                server.getLogger().info("[DEBUG] PayPal create order response: {}", createdOrder.raw);
            }

            ClientInfo.Order<C> order = client.createOrder(orderId, "paypal", request.getPlayerName(), request.getPrice(), request.getCurrency());
            String outTradeNo = createdOrder.id;
            order.setCancelAction(() -> cancelOrder(outTradeNo));
            order.setTask(new TimerTask() {
                @Override
                public void run() {
                    checkOrder(client, this, orderId, outTradeNo);
                }
            });
            server.getTimer().schedule(order.getTask(), 1000L, 3000L);
            server.getLogger().info("PayPal order created: merchantOrderId={}, providerOrderId={}", orderId, outTradeNo);
            return new PaymentOrderResponse("face2face", orderId, order.getMoney(), createdOrder.approvalUrl);
        } catch (Exception e) {
            client.removeOrder(orderId);
            server.getLogger().warn("PayPal create order failed", e);
            return new PaymentOrderResponse("payment.internal-error");
        }
    }

    public PacketPluginQueryPayment.Response handleQueryPayment(PacketPluginQueryPayment packet, Configuration config) {
        String providerOrderId = packet.getProviderOrderId();
        if (providerOrderId == null || providerOrderId.trim().isEmpty()) {
            return new PacketPluginQueryPayment.Response("payment.cancel.not-found");
        }
        try {
            PayPalOrderData response = queryOrder(providerOrderId, config);
            Map<String, String> extra = new LinkedHashMap<>();
            extra.put("source", "paypal-api");
            extra.put("rawStatus", response.status.name());
            return new PacketPluginQueryPayment.Response(
                    packet.getMerchantOrderId(),
                    providerOrderId,
                    toPaymentStatus(response.status),
                    "paypal",
                    "paypal",
                    response.approvalUrl,
                    null,
                    priceToAmountMinor(response.amount),
                    config.getPaypal().getCurrency(),
                    0L,
                    response.status == OrderStatus.COMPLETED ? System.currentTimeMillis() : 0L,
                    extra
            );
        } catch (Exception e) {
            server.getLogger().warn("PayPal query order failed: providerOrderId={}", providerOrderId, e);
            return new PacketPluginQueryPayment.Response("payment.internal-error");
        }
    }

    private void checkOrder(C client, TimerTask task, String orderId, String outTradeNo) {
        Configuration config = server.getConfig();
        ClientInfo.Order<C> order = client.getOrder(orderId);
        if (order == null || !client.isOpen()) {
            task.cancel();
            if (order != null) {
                order.setTask(null);
                client.removeOrder(order);
            }
            cancelOrder(outTradeNo);
            return;
        }
        try {
            PayPalOrderData response = queryOrder(outTradeNo, config);
            if (config.isDebug()) {
                server.getLogger().info("[DEBUG] PayPal query order response: {}", response.raw);
            }
            switch (response.status) {
                case CREATED:
                case SAVED:
                case PAYER_ACTION_REQUIRED:
                    break;
                case APPROVED:
                    response = ProxySupport.call(config.resolveProxy(config.getPaypal().getProxy()), server.getLogger(), () -> {
                        CheckoutOrdersApiClient api = CheckoutOrdersApiClient.create(config.getPaypal().getConfig());
                        ResponseSpec.Response<Order, ErrorDefault> capture = api.orders().capture().withId(outTradeNo).retrieve().toResponse();
                        return requireSuccessful("capture order", capture);
                    });
                    if (config.isDebug()) {
                        server.getLogger().info("[DEBUG] PayPal capture order response: {}", response.raw);
                    }
                    if (response.status == OrderStatus.COMPLETED) {
                        completeOrder(client, order, response);
                    }
                    break;
                case VOIDED:
                    client.removeOrder(order);
                    server.sendPaymentCancel(client, order, "payment.voided");
                    break;
                case COMPLETED:
                    completeOrder(client, order, response);
                    break;
            }
        } catch (Exception e) {
            server.getLogger().warn("PayPal poll order failed: merchantOrderId={}, providerOrderId={}", orderId, outTradeNo, e);
        }
    }

    private PayPalOrderData queryOrder(String outTradeNo, Configuration config) throws Exception {
        return ProxySupport.call(config.resolveProxy(config.getPaypal().getProxy()), server.getLogger(), () -> {
            CheckoutOrdersApiClient api = CheckoutOrdersApiClient.create(config.getPaypal().getConfig());
            ResponseSpec.Response<Order, ErrorDefault> queryResponse = api.orders().get().withId(outTradeNo).retrieve().toResponse();
            return requireSuccessful("query order", queryResponse);
        });
    }

    private PayPalOrderData requireSuccessful(String action, ResponseSpec.Response<Order, ErrorDefault> response) {
        if (response.isSuccessful()) {
            return parseOrder(response.toRawString());
        }
        logPayPalError(action, response);
        throw new IllegalStateException("PayPal " + action + " failed: HTTP " + response.statusCode());
    }

    private void logPayPalError(String action, ResponseSpec.Response<Order, ErrorDefault> response) {
        String raw = "";
        try {
            raw = response.toRawString();
        } catch (Throwable ignored) {
        }
        try {
            JsonObject error = JsonParser.parseString(raw).getAsJsonObject();
            server.getLogger().warn("PayPal {} failed: HTTP {}, name={}, message={}, debug_id={}",
                    action, response.statusCode(), getString(error, "name"), getString(error, "message"), getString(error, "debug_id"));
            JsonArray details = getArray(error, "details");
            if (details != null) {
                for (JsonElement element : details) {
                    if (!element.isJsonObject()) continue;
                    JsonObject detail = element.getAsJsonObject();
                    server.getLogger().warn("PayPal error detail: field={}, issue={}, description={}",
                            getString(detail, "field"), getString(detail, "issue"), getString(detail, "description"));
                }
            }
            return;
        } catch (Throwable ignored) {
        }
        server.getLogger().warn("PayPal {} failed: HTTP {}, body={}", action, response.statusCode(), raw);
    }

    private CurrencyCode paypalCurrency(Configuration config) {
        String currency = config.getPaypal().getCurrency();
        try {
            return CurrencyCode.valueOf(currency);
        } catch (IllegalArgumentException e) {
            server.getLogger().warn("Invalid PayPal currency config: {}; fallback to USD", currency);
            return CurrencyCode.USD;
        }
    }

    private void completeOrder(C client, ClientInfo.Order<C> order, PayPalOrderData response) {
        String money = response.amount;
        if (money == null || money.trim().isEmpty()) {
            money = order.getMoney();
        }
        client.removeOrder(order);

        if (priceToAmountMinor(money) != priceToAmountMinor(order.getMoney())) {
            server.getLogger().warn("PayPal payment amount mismatch: payer={}, paid={}, expected={}; cancelling order",
                    response.payerName, money, order.getMoney());
            server.sendPaymentCancel(client, order, "payment.cancel.not-the-agreed-price");
            return;
        }
        String currency = server.getConfig().getPaypal().getCurrency();
        server.getLogger().info("PayPal payment completed: payer={}, amount={} {}", response.payerName, currency, money);
        server.sendPaymentSuccess(client, order, money);
    }

    private static PayPalOrderData parseOrder(String raw) {
        JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
        PayPalOrderData data = new PayPalOrderData();
        data.raw = raw;
        data.id = getString(root, "id");
        data.status = parseStatus(getString(root, "status"));
        data.approvalUrl = findApprovalUrl(root);
        data.amount = findAmount(root);
        data.payerName = findPayerName(root);
        return data;
    }

    private static OrderStatus parseStatus(String status) {
        if (status == null || status.trim().isEmpty()) return OrderStatus.CREATED;
        try {
            return OrderStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return OrderStatus.CREATED;
        }
    }

    private static String findApprovalUrl(JsonObject root) {
        JsonArray links = getArray(root, "links");
        String fallback = null;
        if (links == null) return null;
        for (JsonElement element : links) {
            if (!element.isJsonObject()) continue;
            JsonObject link = element.getAsJsonObject();
            String href = getString(link, "href");
            String rel = getString(link, "rel");
            if (fallback == null) fallback = href;
            if ("approve".equalsIgnoreCase(rel) || "payer-action".equalsIgnoreCase(rel)) {
                return href;
            }
        }
        return fallback;
    }

    private static String findAmount(JsonObject root) {
        JsonObject unit = firstObject(getArray(root, "purchase_units"));
        if (unit == null) return null;
        JsonObject amount = getObject(unit, "amount");
        if (amount != null && getString(amount, "value") != null) {
            return getString(amount, "value");
        }
        JsonObject payments = getObject(unit, "payments");
        JsonObject capture = payments == null ? null : firstObject(getArray(payments, "captures"));
        JsonObject captureAmount = capture == null ? null : getObject(capture, "amount");
        return captureAmount == null ? null : getString(captureAmount, "value");
    }

    private static String findPayerName(JsonObject root) {
        JsonObject payer = getObject(root, "payer");
        if (payer == null) return "unknown";
        JsonObject name = getObject(payer, "name");
        String fullName = name == null ? null : getString(name, "full_name");
        if (fullName != null && !fullName.trim().isEmpty()) return fullName;
        String email = getString(payer, "email_address");
        return email == null || email.trim().isEmpty() ? "unknown" : email;
    }

    private static String toPaymentStatus(OrderStatus status) {
        if (status == OrderStatus.COMPLETED) return "SUCCESS";
        if (status == OrderStatus.VOIDED) return "CANCELLED";
        if (status == OrderStatus.CREATED
                || status == OrderStatus.SAVED
                || status == OrderStatus.APPROVED
                || status == OrderStatus.PAYER_ACTION_REQUIRED) {
            return "PAYING";
        }
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

    private static JsonObject firstObject(JsonArray array) {
        if (array == null || array.size() == 0) return null;
        JsonElement element = array.get(0);
        return element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static JsonObject getObject(JsonObject object, String name) {
        JsonElement element = object == null ? null : object.get(name);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static JsonArray getArray(JsonObject object, String name) {
        JsonElement element = object == null ? null : object.get(name);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static String getString(JsonObject object, String name) {
        JsonElement element = object == null ? null : object.get(name);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    private static final class PayPalOrderData {
        String raw;
        String id;
        OrderStatus status;
        String approvalUrl;
        String amount;
        String payerName = "unknown";
    }

    private void cancelOrder(String outTradeNo) {
        // PayPal Orders API does not provide a useful cancel action for this flow.
        // Unapproved orders expire upstream.
    }
}
