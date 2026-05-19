package top.mrxiaom.sweet.checkout.backend.payment;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.eealba.payper.core.client.ResponseSpec;
import io.github.eealba.payper.orders.v2.api.CheckoutOrdersApiClient;
import io.github.eealba.payper.orders.v2.model.*;
import top.mrxiaom.sweet.checkout.backend.AbstractPaymentServer;
import top.mrxiaom.sweet.checkout.backend.Configuration;
import top.mrxiaom.sweet.checkout.backend.data.ClientInfo;
import top.mrxiaom.sweet.checkout.backend.util.ProxySupport;
import top.mrxiaom.sweet.checkout.packets.plugin.PacketPluginQueryPayment;
import top.mrxiaom.sweet.checkout.packets.plugin.PacketPluginRequestOrder;

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

    public PacketPluginRequestOrder.Response handleCreateOrder(PacketPluginRequestOrder packet, C client, Configuration config) {
        String orderId = client.nextOrderId();
        if (orderId == null) {
            return new PacketPluginRequestOrder.Response("payment.can-not-create-id");
        }
        try {
            PayPalOrderData createdOrder = ProxySupport.call(config.resolveProxy(config.getPaypal().getProxy()), server.getLogger(), () -> {
                CheckoutOrdersApiClient api = CheckoutOrdersApiClient.create(config.getPaypal().getConfig());
                // 创建订单
                ResponseSpec.Response<Order, ErrorDefault> createResponse = api.orders().create().withBody(OrderRequest.builder()
                                // 添加一个订单采购单位
                                .purchaseUnits(Collections.singletonList(PurchaseUnitRequest.builder()
                                        // 设置金额
                                        .amount(AmountWithBreakdown.builder()
                                                .value(packet.getPrice())
                                                .currencyCode(paypalCurrency(config))
                                                .build())
                                        .build()))
                                // 设置订单付款意图
                                // CAPTURE: 商家打算在客户付款后立即捕获付款。
                                .intent(CheckoutPaymentIntent.CAPTURE)
                                .build())
                        .retrieve()
                        .toResponse();
                return requireSuccessful("下单", createResponse);
            });
            if (config.isDebug()) {
                server.getLogger().info("[DEBUG] Paypal 官方接口 下单结果: {}", createdOrder.raw);
            }
            String url = createdOrder.approvalUrl;

            ClientInfo.Order<C> order = client.createOrder(orderId, "paypal", packet.getPlayerName(), packet.getPrice());
            String outTradeNo = createdOrder.id;
            order.setCancelAction(() -> cancelOrder(outTradeNo));
            // 轮询检查是否交易成功
            order.setTask(new TimerTask() {
                @Override
                public void run() {
                    checkOrder(client, this, orderId, outTradeNo);
                }
            });
            // 每3秒检查一次是否支付成功
            server.getTimer().schedule(order.getTask(), 1000L, 3000L);
            server.getLogger().info("PayPal 官方接口 下单成功 : {}", outTradeNo);
            return new PacketPluginRequestOrder.Response("face2face", orderId, order.getMoney(), url);
        } catch (Exception e) {
            client.removeOrder(orderId);
            server.getLogger().warn("Paypal 官方接口 API执行错误", e);
            return new PacketPluginRequestOrder.Response("payment.internal-error");
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
            server.getLogger().warn("Paypal 官方接口 API查询订单时执行错误", e);
            return new PacketPluginQueryPayment.Response("payment.internal-error");
        }
    }

    private void checkOrder(C client, TimerTask task, String orderId, String outTradeNo) {
        Configuration config = server.getConfig();
        ClientInfo.Order<C> order = client.getOrder(orderId);
        if (order == null || !client.isOpen()) { // 插件连接断开时、任务不存在时取消任务，并关闭交易
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
                server.getLogger().info("[DEBUG] Paypal 官方接口 检查结果: {}", response.raw);
            }
            switch (response.status) {
                // 订单是使用指定的上下文创建的。
                case CREATED:
                    // 该订单被保存并保留。订单状态将继续处于进行中，直到对订单中的所有采购单位进行捕获，并使用 final_capture = true。
                case SAVED:
                    break;
                // 客户已通过 PayPal 钱包或其他形式的访客或无品牌付款批准付款。例如，银行卡、银行账户等。
                case APPROVED:
                    response = ProxySupport.call(config.resolveProxy(config.getPaypal().getProxy()), server.getLogger(), () -> {
                        CheckoutOrdersApiClient api = CheckoutOrdersApiClient.create(config.getPaypal().getConfig());
                        ResponseSpec.Response<Order, ErrorDefault> capture = api.orders().capture().withId(outTradeNo).retrieve().toResponse();
                        return requireSuccessful("捕获订单", capture);
                    });
                    if (config.isDebug()) {
                        server.getLogger().info("[DEBUG] Paypal 官方接口 捕获结果: {}", response.raw);
                    }
                    if (response.status == OrderStatus.COMPLETED) {
                        completeOrder(client, order, response);
                    }
                    break;
                // 订单中的所有采购单位都将作废。
                case VOIDED:
                    client.removeOrder(order);
                    server.sendPaymentCancel(client, order, "payment.voided");
                    break;
                // 订单意图已完成，并创建了付款资源。
                // 为了避免麻烦，只添加一个付款单位，无需进行额外检查
                case COMPLETED:
                    completeOrder(client, order, response);
                    break;
                // 订单需要付款人执行作（e.g. 3DS 身份验证）。
                // 将付款人重定向到在授权或捕获订单之前作为响应的一部分返回的“rel”：“payer-action”HATEOAS 链接。
                // 某些支付来源可能不会返回付款人作 HATEOAS 链接（例如 MB WAY）。
                // 对于这些支付来源，付款人作由计划本身管理（例如，通过短信、电子邮件、应用内通知等）。
                case PAYER_ACTION_REQUIRED:
                    break;
            }
        } catch (Exception e) {
            server.getLogger().warn("Paypal 官方接口 API检查订单时执行错误", e);
        }
    }

    private PayPalOrderData queryOrder(String outTradeNo, Configuration config) throws Exception {
        return ProxySupport.call(config.resolveProxy(config.getPaypal().getProxy()), server.getLogger(), () -> {
            CheckoutOrdersApiClient api = CheckoutOrdersApiClient.create(config.getPaypal().getConfig());
            // 查询订单
            ResponseSpec.Response<Order, ErrorDefault> queryResponse = api.orders().get().withId(outTradeNo).retrieve().toResponse();
            return requireSuccessful("查询订单", queryResponse);
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
            server.getLogger().warn("PayPal 官方接口 {} 失败: HTTP {}, name={}, message={}, debug_id={}",
                    action, response.statusCode(), getString(error, "name"), getString(error, "message"), getString(error, "debug_id"));
            JsonArray details = getArray(error, "details");
            if (details != null) {
                for (JsonElement element : details) {
                    if (!element.isJsonObject()) continue;
                    JsonObject detail = element.getAsJsonObject();
                    server.getLogger().warn("PayPal 错误详情: field={}, issue={}, description={}",
                            getString(detail, "field"), getString(detail, "issue"), getString(detail, "description"));
                }
            }
            return;
        } catch (Throwable ignored) {
        }
        server.getLogger().warn("PayPal 官方接口 {} 失败: HTTP {}, body={}", action, response.statusCode(), raw);
    }

    private CurrencyCode paypalCurrency(Configuration config) {
        String currency = config.getPaypal().getCurrency();
        try {
            return CurrencyCode.valueOf(currency);
        } catch (IllegalArgumentException e) {
            server.getLogger().warn("PayPal currency 配置无效: {}，已回退为 USD", currency);
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
            server.getLogger().warn("[收款] 从 PayPal 收款，来自 {} 的 {}，但支付金额与订单金额 {} 不一致，自动取消订单",
                    response.payerName, money, order.getMoney());
            server.sendPaymentCancel(client, order, "payment.cancel.not-the-agreed-price");
            return;
        }
        String currency = server.getConfig().getPaypal().getCurrency();
        server.getLogger().info("[收款] 从 PayPal 收款，来自 {} 的 {} {}", response.payerName, currency, money);
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
        // Paypal 无法取消或者作废订单，取消订单这里什么都不用做
        // 让它自动过期就完事了
    }
}
